package io.kontour.ui.overlay

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration

/**
 * Holding the stack sends it away whole: held still on the front toast for a
 * long press, every toast the reader can dismiss is gathered into one pill and
 * dragged away as one. A drag that stops is never a hold, a hold let go in
 * place sends nothing away, and a toast the reader cannot dismiss stays.
 */
@OptIn(ExperimentalTestApi::class)
class ToastGatherTest {

    /** A bottom stack of [messages], oldest first, and its state; [fixed] are shown undismissible. */
    private fun ComposeUiTest.stack(messages: List<String>, fixed: Set<String> = emptySet()): ToastHostState {
        val toasts = ToastHostState()
        mainClock.autoAdvance = false
        setContent {
            KontourTheme(darkTheme = false) {
                OverlayHost(Modifier.fillMaxSize()) {
                    ToastHost(toasts)
                }
            }
        }
        messages.forEach { toasts.show(it, duration = Duration.INFINITE, dismissible = it !in fixed) }
        settle()
        return toasts
    }

    private fun ComposeUiTest.settle(frames: Int = 60) = repeat(frames) { mainClock.advanceTimeByFrame() }

    private fun ToastHostState.left(): List<String> = toasts.filter { it.presence.targetState }.map { it.message }

    /** Down on [front], still for [holdMillis], then [travel] downward in steps, a frame each, and up. */
    private fun ComposeUiTest.press(front: String, holdMillis: Long, travel: Float, before: Float = 0f) {
        // From the root, at where the front toast's words are: gathered, the
        // card shows the count instead, and its words leave the tree.
        val at = onNodeWithText(front, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.center
        val root = onRoot()
        var finger = at
        root.performTouchInput { down(at) }
        mainClock.advanceTimeByFrame()
        // Moved first, past any slop, where asked: a drag that then stops.
        if (before > 0f) {
            repeat(8) {
                finger += Offset(0f, before / 8)
                root.performTouchInput { moveTo(finger) }
                mainClock.advanceTimeByFrame()
            }
        }
        mainClock.advanceTimeBy(holdMillis)
        repeat(12) {
            finger += Offset(0f, travel / 12)
            root.performTouchInput { moveTo(finger) }
            mainClock.advanceTimeByFrame()
        }
        root.performTouchInput { up() }
        settle()
    }

    @Test
    fun heldStillThenDraggedAwayTheWholeStackGoes() = runComposeUiTest {
        // Five, so two are queued off screen behind the three drawn.
        val toasts = stack(listOf("One", "Two", "Three", "Four", "Five"))
        press("Five", holdMillis = 800, travel = 240f)
        assertEquals(emptyList(), toasts.left(), "the stack was held and dragged away, and some stayed")
    }

    @Test
    fun aDragThatStopsIsStillOneToast() = runComposeUiTest {
        val toasts = stack(listOf("One", "Two", "Three"))
        // Moving first, past the slop, then still for longer than a long press.
        press("Three", holdMillis = 800, travel = 200f, before = 40f)
        assertEquals(listOf("One", "Two"), toasts.left(), "a drag that stopped and went on took more than its own toast")
    }

    @Test
    fun aHoldLetGoInPlaceSendsNothingAway() = runComposeUiTest {
        val toasts = stack(listOf("One", "Two", "Three"))
        press("Three", holdMillis = 800, travel = 0f)
        assertEquals(listOf("One", "Two", "Three"), toasts.left(), "a hold let go where it was sent toasts away")
        assertTrue(onAllNodesWithText("Three", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty(), "the front toast is not back")
    }

    @Test
    fun aToastTheReaderCannotDismissStaysWhenTheStackGoes() = runComposeUiTest {
        val toasts = stack(listOf("Offline", "Two", "Three"), fixed = setOf("Offline"))
        press("Three", holdMillis = 800, travel = 240f)
        assertEquals(listOf("Offline"), toasts.left(), "the stack went, and the toast that cannot be dismissed should have stayed")
    }

    @Test
    fun aToastTheReaderCannotDismissComesBackFromASwipe() = runComposeUiTest {
        val toasts = stack(listOf("Offline"), fixed = setOf("Offline"))
        press("Offline", holdMillis = 0, travel = 240f)
        assertEquals(listOf("Offline"), toasts.left(), "a swipe sent away a toast the reader cannot dismiss")
    }

    @Test
    fun aScreenReaderCanDismissTheWholeStack() = runComposeUiTest {
        val toasts = stack(listOf("One", "Two", "Three"))
        val actions = onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions), useUnmergedTree = true)
            .fetchSemanticsNodes().flatMap { it.config[SemanticsActions.CustomActions] }
        val dismissAll = actions.single { it.label == "Dismiss all" }
        runOnUiThread { dismissAll.action() }
        settle()
        assertEquals(emptyList(), toasts.left(), "Dismiss all left some")
    }

    @Test
    fun heldTheStackShowsHowManyAreGoing() = runComposeUiTest {
        stack(listOf("One", "Two", "Three"))
        val at = onNodeWithText("Three", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.center
        onRoot().performTouchInput { down(at) }
        mainClock.advanceTimeBy(800)
        settle(30)
        assertTrue(
            onAllNodesWithText("3 notifications", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty(),
            "the stack held to go was not drawn as one pill saying how many",
        )
        onRoot().performTouchInput { up() }
        settle()
    }

    @Test
    fun aHoldStartedOnTheActionDoesNotPressIt() = runComposeUiTest {
        var undone = 0
        val toasts = ToastHostState()
        mainClock.autoAdvance = false
        setContent {
            KontourTheme(darkTheme = false) {
                OverlayHost(Modifier.fillMaxSize()) { ToastHost(toasts) }
            }
        }
        toasts.show("Saved", duration = Duration.INFINITE)
        toasts.show("Removed", actionLabel = "Undo", onAction = { undone++ }, duration = Duration.INFINITE)
        settle()
        val at = onNodeWithText("Undo", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.center
        onRoot().performTouchInput { down(at) }
        mainClock.advanceTimeBy(800)
        settle(10)
        onRoot().performTouchInput { up() }
        settle()
        assertEquals(0, undone, "a hold that gathered the stack also pressed the action it started on")
        assertEquals(listOf("Saved", "Removed"), toasts.left(), "a hold let go in place sent toasts away")
    }
}
