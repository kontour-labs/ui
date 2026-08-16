package io.kontour.ui.overlay

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun entry(
    key: Any,
    layer: OverlayLayer,
    dismissOnBack: Boolean = true,
    scrim: ScrimStyle = ScrimStyle.Dimmed,
    onDismiss: (() -> Unit)? = null,
) = OverlayEntry(
    key = key,
    layer = layer,
    scrim = scrim,
    dismissOnBack = dismissOnBack,
    onDismiss = onDismiss,
    content = {},
)

/**
 * The overlay *stack* — things that legitimately coexist.
 */
class OverlayHostStateTest {

    @Test
    fun layerOrderBeatsPushOrder() {
        val host = OverlayHostState()
        // Pushed sheet-last, but a menu opened from inside a sheet must still
        // draw above it. This is the whole reason layers are declared.
        host.show(entry("menu", OverlayLayer.Menu))
        host.show(entry("sheet", OverlayLayer.Sheet))

        assertEquals(listOf("sheet", "menu"), host.visible.map { it.key })
    }

    @Test
    fun sameKeyReplacesRatherThanStacks() {
        val host = OverlayHostState()
        host.show(entry("dialog", OverlayLayer.Dialog))
        host.show(entry("dialog", OverlayLayer.Dialog))

        // A double-tap must not open two identical dialogs.
        assertEquals(1, host.visible.size)
    }

    @Test
    fun dismissTopTakesTheHighestLayer() {
        val host = OverlayHostState()
        host.show(entry("sheet", OverlayLayer.Sheet))
        host.show(entry("dialog", OverlayLayer.Dialog))

        assertTrue(host.dismissTop())
        assertEquals(listOf("sheet"), host.visible.map { it.key })
    }

    @Test
    fun dismissTopSkipsEntriesThatOptOut() {
        val host = OverlayHostState()
        host.show(entry("sheet", OverlayLayer.Sheet))
        // A toast is above the sheet but must not swallow a back press.
        host.show(entry("toast", OverlayLayer.Toast, dismissOnBack = false))

        assertTrue(host.dismissTop())
        assertEquals(listOf("toast"), host.visible.map { it.key })
    }

    @Test
    fun dismissTopReportsFalseWhenNothingCanBeDismissed() {
        val host = OverlayHostState()
        // False lets the caller pass the back event on to navigation. A back
        // press that silently does nothing is worse than one that leaves.
        assertFalse(host.dismissTop())

        host.show(entry("toast", OverlayLayer.Toast, dismissOnBack = false))
        assertFalse(host.dismissTop())
    }

    @Test
    fun dismissTopInvokesTheCallback() {
        val host = OverlayHostState()
        var dismissed = false
        host.show(entry("dialog", OverlayLayer.Dialog, onDismiss = { dismissed = true }))

        host.dismissTop()
        assertTrue(dismissed)
    }

    @Test
    fun onlyTheTopmostDimmingEntryDrawsColour() {
        val host = OverlayHostState()
        host.show(entry("sheet", OverlayLayer.Sheet, scrim = ScrimStyle.Dimmed))
        host.show(entry("dialog", OverlayLayer.Dialog, scrim = ScrimStyle.Dimmed))

        // Both still get a scrim — each has to block input to what is under it
        // — but two dimmed scrims would composite and the background would go
        // near-black under the second overlay.
        assertEquals("dialog", topDimmedEntry(host.visible)?.key)
    }

    @Test
    fun aTransparentOverlayOnTopDoesNotUndimTheOneBelow() {
        val host = OverlayHostState()
        host.show(entry("sheet", OverlayLayer.Sheet, scrim = ScrimStyle.Dimmed))
        // A menu opened from inside a modal sheet blocks but does not dim.
        host.show(entry("menu", OverlayLayer.Menu, scrim = ScrimStyle.Transparent))

        assertEquals("sheet", topDimmedEntry(host.visible)?.key)
    }

    @Test
    fun nothingDimsWhenNothingAsksTo() {
        val host = OverlayHostState()
        host.show(entry("toast", OverlayLayer.Toast, scrim = ScrimStyle.None))
        assertNull(topDimmedEntry(host.visible))
    }

    @Test
    fun isEmptyTracksTheStack() {
        val host = OverlayHostState()
        assertTrue(host.isEmpty)
        host.show(entry("a", OverlayLayer.Dialog))
        assertFalse(host.isEmpty)
        host.hide("a")
        assertTrue(host.isEmpty)
    }

    @Test
    fun isBusyIgnoresTooltipsAndToasts() {
        val host = OverlayHostState()
        assertFalse(host.isBusy)

        // A coach mark renders into the tooltip layer *through* the queue. If
        // this counted, the queue would suppress itself the instant it showed
        // something and flicker it straight back off.
        host.show(entry("coachMark", OverlayLayer.Tooltip))
        host.show(entry("toast", OverlayLayer.Toast))
        assertFalse(host.isBusy)

        host.show(entry("sheet", OverlayLayer.Sheet))
        assertTrue(host.isBusy)
    }

    @Test
    fun isBusyForEveryModalLayer() {
        for (layer in listOf(
            OverlayLayer.Sheet,
            OverlayLayer.Dialog,
            OverlayLayer.Menu,
            OverlayLayer.Critical,
        )) {
            val host = OverlayHostState()
            host.show(entry("x", layer))
            assertTrue(host.isBusy, "$layer should suppress the overlay queue")
        }
    }
}

/**
 * The overlay *queue* — things that must never coexist.
 */
/**
 * Hiding an overlay is a two-step: mark it, animate it, then drop it.
 *
 * `hide` used to remove the entry from the list synchronously, so the subtree
 * was torn out on the next frame and nothing had anywhere to animate. `Dialog`
 * declared `AnimatedVisibility(visible = true, exit = fadeOut() + scaleOut())`
 * — unreachable code, because `visible` was a literal and the removal beat it.
 *
 * These are the invariants that make the exit possible without an overlay on its
 * way out still counting as showing.
 */
class OverlayHostExitTest {

    @Test
    fun hidingKeepsTheEntryRenderedButNotVisible() {
        val state = OverlayHostState()
        state.show(entry(key = "dialog", layer = OverlayLayer.Dialog))

        state.hide("dialog")

        assertTrue(state.isEmpty, "a dismissed overlay is not showing any more")
        assertFalse(state.isShowing("dialog"))
        assertEquals(1, state.rendered.size, "but it is still on screen, animating out")
        assertTrue(state.isLeaving("dialog"))
    }

    @Test
    fun anOverlayOnItsWayOutDoesNotHoldTheBackGestureOrTheQueue() {
        val state = OverlayHostState()
        state.show(entry(key = "sheet", layer = OverlayLayer.Sheet, dismissOnBack = true))

        state.hide("sheet")

        // Both would otherwise stay true for the length of the exit animation:
        // back would be swallowed by something already dismissed, and a queued
        // prompt would wait on it.
        assertFalse(state.canDismissOnBack)
        assertFalse(state.isBusy)
    }

    @Test
    fun finishingTheAnimationIsWhatActuallyRemovesIt() {
        val state = OverlayHostState()
        state.show(entry(key = "dialog", layer = OverlayLayer.Dialog))
        state.hide("dialog")

        state.finishHiding("dialog")

        assertTrue(state.rendered.isEmpty())
        assertFalse(state.isLeaving("dialog"))
    }

    @Test
    fun reopeningSomethingMidExitTurnsItAroundRatherThanStacking() {
        val state = OverlayHostState()
        state.show(entry(key = "menu", layer = OverlayLayer.Menu))
        state.hide("menu")

        state.show(entry(key = "menu", layer = OverlayLayer.Menu))

        assertEquals(1, state.rendered.size, "a second copy would sit behind the one leaving")
        assertFalse(state.isLeaving("menu"))
        assertTrue(state.isShowing("menu"))
    }
}

class OverlayQueueTest {

    private fun queue(sessions: Int = 100) = OverlayQueue(sessions)

    @Test
    fun highestPriorityWins() {
        val q = queue()
        q.request(OverlayRequest("review", priority = 10)) { true }
        q.request(OverlayRequest("force-update", priority = 100)) { true }
        q.request(OverlayRequest("onboarding", priority = 90)) { true }

        assertEquals("force-update", q.current?.id)
    }

    @Test
    fun dismissingRevealsTheNext() {
        val q = queue()
        q.request(OverlayRequest("force-update", priority = 100)) { true }
        q.request(OverlayRequest("onboarding", priority = 90)) { true }

        assertEquals("force-update", q.current?.id)
        q.dismiss("force-update")
        assertEquals("onboarding", q.current?.id)
        q.dismiss("onboarding")
        assertNull(q.current)
    }

    @Test
    fun aFailingConditionIsSkippedEntirely() {
        val q = queue()
        q.request(OverlayRequest("force-update", priority = 100)) { false }
        q.request(OverlayRequest("onboarding", priority = 90)) { true }

        assertEquals("onboarding", q.current?.id)
    }

    @Test
    fun prerequisitesMustBeDismissedNotMerelyShown() {
        val q = queue()
        q.request(OverlayRequest("onboarding", priority = 90)) { true }
        q.request(
            OverlayRequest("review", priority = 10, prerequisites = setOf("onboarding")),
        ) { true }

        // Onboarding is showing, so review is not eligible even though its own
        // condition holds — otherwise it could appear over the thing it follows.
        assertEquals("onboarding", q.current?.id)
        q.dismiss("onboarding")
        assertEquals("review", q.current?.id)
    }

    @Test
    fun minSessionsSuppressesUntilTheThreshold() {
        val early = OverlayQueue(sessionCount = 2)
        early.request(OverlayRequest("review", priority = 10, minSessions = 5)) { true }
        assertNull(early.current)

        val later = OverlayQueue(sessionCount = 5)
        later.request(OverlayRequest("review", priority = 10, minSessions = 5)) { true }
        assertEquals("review", later.current?.id)
    }

    @Test
    fun nonRepeatableRequestsDoNotComeBack() {
        val q = queue()
        q.request(OverlayRequest("onboarding", priority = 90)) { true }
        q.dismiss("onboarding")
        assertNull(q.current)
    }

    @Test
    fun repeatableRequestsDo() {
        val q = queue()
        q.request(OverlayRequest("offline", priority = 50, repeatable = true)) { true }
        q.dismiss("offline")
        assertEquals("offline", q.current?.id)
    }

    @Test
    fun suppressionHidesEverythingWithoutLosingIt() {
        val q = queue()
        q.request(OverlayRequest("onboarding", priority = 90)) { true }

        // This is what stops a coach mark firing over an open dialog.
        q.suppressed = true
        assertNull(q.current)

        q.suppressed = false
        assertEquals("onboarding", q.current?.id)
    }

    @Test
    fun reRegisteringAnIdReplacesItsCondition() {
        val q = queue()
        q.request(OverlayRequest("banner", priority = 1)) { true }
        q.request(OverlayRequest("banner", priority = 1)) { false }

        // Safe to call on every composition without duplicating.
        assertNull(q.current)
    }

    @Test
    fun withdrawRemovesARequestOutright() {
        val q = queue()
        q.request(OverlayRequest("paywall", priority = 20)) { true }
        q.withdraw("paywall")
        assertNull(q.current)
    }
}
