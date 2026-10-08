package io.kontour.ui.catalog

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.navigationevent.DirectNavigationEventInput
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import io.kontour.ui.catalog.apps.AppHost
import io.kontour.ui.catalog.apps.AppsPage
import io.kontour.ui.catalog.apps.LocalOpenApp
import io.kontour.ui.catalog.apps.MiniApp
import io.kontour.ui.theme.KontourTheme
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The gallery's apps: that each one opens, goes down its stack and back, and
 * leaves when back reaches its root.
 *
 * Asked for as *"a 'mini-app' within the app that allows users to navigate
 * between a heap of pages."* What is held here is the navigation, not the
 * pictures: on a phone a detail page covers the bar — *"we probably want to be
 * careful in the chat 'detail' screen with showing the navigation bar. same
 * goes in the transit app"* — while on a desktop it stays beside the sidebar;
 * back goes down the stack one page at a time, and at the root it leaves the
 * app — the one handler the app's host registers before anything inside it,
 * and so the last one back reaches.
 */
@OptIn(ExperimentalTestApi::class)
class AppsTest {

    /** A phone's back, as `BackPageTest` gives the gallery one. */
    private class Phone {
        val dispatcher = NavigationEventDispatcher()
        val input = DirectNavigationEventInput().also { dispatcher.addInput(it) }
        val owner = object : NavigationEventDispatcherOwner {
            override val navigationEventDispatcher = dispatcher
        }
    }

    private fun ComposeUiTest.back(phone: Phone) {
        runOnUiThread { phone.input.backCompleted() }
        waitForIdle()
    }

    private fun ComposeUiTest.tabs(): Int =
        onAllNodesWithTag("tab-Stops", useUnmergedTree = true).fetchSemanticsNodes().size

    @Test
    fun theAppsPageOpensEachApp() = runDesktopComposeUiTest(width = 400, height = 860) {
        val opened = mutableListOf<MiniApp>()
        setContent {
            KontourTheme(darkTheme = false) {
                CompositionLocalProvider(LocalOpenApp provides { app: MiniApp -> opened += app }) {
                    AppsPage()
                }
            }
        }
        onNodeWithText("Open Transit").performScrollTo().performClick()
        onNodeWithText("Open Messages & Notes").performScrollTo().performClick()
        waitForIdle()
        assertEquals(listOf(MiniApp.Transit, MiniApp.Messages), opened.toList())
    }

    @Test
    fun transitOnAPhoneGoesDownItsStackAndBackOut() = runDesktopComposeUiTest(width = 400, height = 860) {
        val phone = Phone()
        var left = false
        setContent {
            CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides phone.owner) {
                KontourTheme(darkTheme = false) { AppHost(MiniApp.Transit, onLeave = { left = true }) }
            }
        }
        waitForIdle()
        onNodeWithTag("departures").assertExists()
        assertEquals(1, tabs(), "no nav bar at the app's root")

        // A tab, then a stop on it: a detail page, over the bar as well.
        onNodeWithTag("tab-Stops", useUnmergedTree = true).performClick()
        waitForIdle()
        onNodeWithTag("stops").assertExists()
        onNodeWithTag("stops").performScrollToNode(hasTestTag("stop-Leederville"))
        onNodeWithTag("stop-Leederville").performClick()
        waitForIdle()
        onNodeWithText("At this stop").assertExists()
        assertEquals(0, tabs(), "on a phone the stop's page has the nav bar under it")

        // The ticket covers it too.
        onNodeWithText("Show my ticket").performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag("ticket").assertExists()
        assertEquals(0, tabs(), "the bar is drawn under the ticket, which covers it")

        // Back to the stop, still covered; back to the tab's root, which has
        // the bar again; then out.
        back(phone)
        onNodeWithTag("ticket").assertDoesNotExist()
        onNodeWithText("At this stop").assertExists()
        assertEquals(0, tabs(), "back from the ticket uncovered the bar under the stop")
        back(phone)
        onNodeWithTag("stops").assertExists()
        assertEquals(1, tabs(), "back at the stops, the bar did not come back")
        assertFalse(left, "back from a stop left the app instead of going to the stops")
        back(phone)
        assertTrue(left, "back at the app's root did not leave it")
    }

    /**
     * A pull at the top of the departures brings the list down under the bar,
     * and the bar stays where it is.
     *
     * Reported as *"you've put the pull-to-refresh in a weird spot. it sort of
     * drags the whole page down, instead of the content beneath the top bar"*:
     * the refresh was round the page, its hero bar and all.
     */
    @Test
    fun aPullBringsTheListDownAndLeavesTheBar() = runDesktopComposeUiTest(width = 400, height = 860) {
        setContent {
            KontourTheme(darkTheme = false) { AppHost(MiniApp.Transit, onLeave = {}) }
        }
        waitForIdle()
        fun top(text: String) = onAllNodesWithText(text).fetchSemanticsNodes().first().boundsInRoot.top
        val title = top("Perth Underground")
        val list = onNodeWithTag("departures").fetchSemanticsNode().boundsInRoot
        val firstRow = top("Trains")

        mainClock.autoAdvance = false
        onNodeWithTag("departures").performTouchInput {
            // On the rows: the list runs under the hero, which takes a press on itself.
            down(Offset(centerX, list.height * 0.6f))
            repeat(12) { moveBy(Offset(0f, 20f)); }
        }
        mainClock.advanceTimeBy(100)
        val pulledTitle = top("Perth Underground")
        val pulledRow = top("Trains")
        onNodeWithTag("departures").performTouchInput { up() }
        mainClock.autoAdvance = true

        assertEquals(title, pulledTitle, 0.5f, "the bar came down with the pull")
        assertTrue(pulledRow > firstRow + 20f, "the list did not come down: ${pulledRow - firstRow}px")
    }

    /**
     * The departures' hero collapses as the list goes up, from the first move
     * of the finger, with the list moving exactly as far as the finger does.
     *
     * Reported from an iPad: *"sometimes it shops, sometimes it doesn't"* — the
     * hero, laid out above its list, shared each drag with it, and the start of
     * one sometimes went to the system's stretch instead. Drawn over the list
     * and following its scroll, there is nothing to share.
     */
    @Test
    fun theHeroCollapsesFromTheFirstMoveAndTheListMovesWithTheFinger() = runDesktopComposeUiTest(width = 400, height = 860) {
        setContent {
            KontourTheme(darkTheme = false) { AppHost(MiniApp.Transit, onLeave = {}) }
        }
        waitForIdle()
        fun top(text: String) = onAllNodesWithText(text).fetchSemanticsNodes().first().boundsInRoot.top
        val title = top("Perth Underground")
        val row = top("Trains")
        val list = onNodeWithTag("departures").fetchSemanticsNode().boundsInRoot
        mainClock.autoAdvance = false
        onNodeWithTag("departures").performTouchInput {
            down(Offset(centerX, list.height * 0.6f))
            // Past the slop, then three moves more.
            repeat(6) { moveBy(Offset(0f, -10f)) }
        }
        mainClock.advanceTimeByFrame()
        val movedRow = row - top("Trains")
        val movedTitle = title - top("Perth Underground")
        onNodeWithTag("departures").performTouchInput { up() }
        mainClock.autoAdvance = true
        assertTrue(movedTitle > 4f, "the hero did not start collapsing with the drag: ${movedTitle}px")
        assertTrue(movedRow > 20f, "the list did not move with the finger: ${movedRow}px")
    }

    /** Pulled at the top, the spinner comes in below the hero, in the gap the list leaves, not under the bar. */
    @Test
    fun aPullsSpinnerIsBelowTheHero() = runDesktopComposeUiTest(width = 400, height = 860) {
        setContent {
            KontourTheme(darkTheme = false) { AppHost(MiniApp.Transit, onLeave = {}) }
        }
        waitForIdle()
        val subtitle = onAllNodesWithText("Next departures").fetchSemanticsNodes().first().boundsInRoot.bottom
        val list = onNodeWithTag("departures").fetchSemanticsNode().boundsInRoot
        mainClock.autoAdvance = false
        onNodeWithTag("departures").performTouchInput {
            down(Offset(centerX, list.height * 0.6f))
            repeat(12) { moveBy(Offset(0f, 20f)) }
        }
        mainClock.advanceTimeBy(100)
        val spinner = onAllNodes(hasContentDescription("Pull to refresh") or hasContentDescription("Release to refresh"))
            .fetchSemanticsNodes().first().boundsInRoot
        onNodeWithTag("departures").performTouchInput { up() }
        mainClock.autoAdvance = true
        assertTrue(spinner.top >= subtitle, "the spinner was under the hero: its top ${spinner.top}, the hero's subtitle ends at $subtitle")
    }

    /**
     * The tour's first step — the welcome card in the middle of the screen —
     * has its Skip, whole, beside the way on, on the narrowest of phones as
     * well: *"please also have the coachmark tour skip button on the first
     * one"*.
     */
    @Test
    fun theTourOffersSkipOnItsFirstStep() {
        for (width in listOf(400, 320)) {
            runDesktopComposeUiTest(width = width, height = 760) {
                setContent {
                    KontourTheme(darkTheme = false) { AppHost(MiniApp.Transit, onLeave = {}) }
                }
                waitForIdle()
                onNodeWithContentDescription("More").performClick()
                waitForIdle()
                onNodeWithText("Show me around").performClick()
                waitForIdle()
                val skip = onAllNodesWithText("Skip").fetchSemanticsNodes()
                assertEquals(1, skip.size, "no Skip on the tour's first step at ${width}dp")
                val next = onAllNodesWithText("Next").fetchSemanticsNodes().single().boundsInRoot
                val skipBox = skip.single().boundsInRoot
                assertTrue(skipBox.width > 20f && skipBox.right <= next.left, "Skip is squeezed or under Next at ${width}dp: $skipBox, $next")
                assertTrue(next.right <= width.toFloat(), "Next runs off a ${width}dp screen: $next")
            }
        }
    }

    @Test
    fun theLeaveButtonLeaves() = runDesktopComposeUiTest(width = 400, height = 860) {
        var left = false
        setContent {
            KontourTheme(darkTheme = false) { AppHost(MiniApp.Transit, onLeave = { left = true }) }
        }
        waitForIdle()
        onNodeWithContentDescription("Leave Transit").performClick()
        waitForIdle()
        assertTrue(left)
    }

    @Test
    fun theTicketTopsUpAfterAsking() = runDesktopComposeUiTest(width = 400, height = 860) {
        val phone = Phone()
        setContent {
            CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides phone.owner) {
                KontourTheme(darkTheme = false) { AppHost(MiniApp.Transit, onLeave = {}) }
            }
        }
        waitForIdle()
        onNodeWithContentDescription("My ticket").performClick()
        waitForIdle()
        onNodeWithText("Top up $20").performScrollTo().performClick()
        waitForIdle()
        onNodeWithText("Top up $20?").assertExists()
        // By the clock from here: the toast says it is done, and then goes.
        mainClock.autoAdvance = false
        onNodeWithText("Top up").performClick()
        mainClock.advanceTimeBy(PayAndSettle)
        assertTrue(
            onAllNodesWithText("Topped up $20", substring = true).fetchSemanticsNodes().isNotEmpty(),
            "no toast after topping up",
        )
    }

    /**
     * On an iPad held upright the navigation is a collapsed rail, and what is
     * in it fits it: the way out and the planner as icons on the destinations'
     * line, not a header and a labelled button squeezed to its width — reported
     * as *"in the nav rail, you try to cram text and buttons in there that just
     * don't fit"*. Grown, the rail has them whole.
     */
    @Test
    fun onAnUprightTabletTheRailHoldsOnlyWhatFitsIt() = runDesktopComposeUiTest(width = 800, height = 1100) {
        setContent {
            KontourTheme(darkTheme = false) { AppHost(MiniApp.Transit, onLeave = {}) }
        }
        waitForIdle()
        val rail = onNodeWithTag("tab-Departures", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        // The rail's edge: past its icons, short of the page.
        val edge = 88f
        for (label in listOf("Leave Transit", "Plan a trip")) {
            val node = onNodeWithContentDescription(label).fetchSemanticsNode().boundsInRoot
            assertTrue(node.right <= edge + 0.5f, "$label is wider than the rail: it ends at ${node.right}, the rail at $edge")
            assertTrue(abs(node.center.x - rail.center.x) <= 4f, "$label is not on the destinations' line: ${node.center.x} against ${rail.center.x}")
        }
        // No words in the collapsed rail beside the way out.
        assertTrue(
            onAllNodesWithText("Transit").fetchSemanticsNodes().none { it.boundsInRoot.left < edge },
            "the app's name is crammed into the collapsed rail",
        )
        // Grown, the header and the planner are whole.
        onNodeWithContentDescription("Expand navigation", substring = true, ignoreCase = true).performClick()
        waitForIdle()
        onNodeWithText("Plan a trip").assertExists()
        assertTrue(onAllNodesWithText("Transit").fetchSemanticsNodes().any { it.boundsInRoot.left < 280f })
    }

    @Test
    fun onADesktopTheNavigationIsASidebarWithTheLeaveButtonAtItsHead() =
        runDesktopComposeUiTest(width = 1280, height = 800) {
            setContent {
                KontourTheme(darkTheme = false) { AppHost(MiniApp.Transit, onLeave = {}) }
            }
            waitForIdle()
            assertEquals(1, tabs(), "no navigation on a desktop window")
            // The way out once, at the sidebar's head, not again in the page's bar.
            assertEquals(1, onAllNodesWithContentDescription("Leave Transit").fetchSemanticsNodes().size)
            assertTrue(
                onAllNodesWithText("Plan a trip").fetchSemanticsNodes().isNotEmpty(),
                "the sidebar has no planner action",
            )
            // A stop stays beside the sidebar: at the side, it is in nothing's way.
            onNodeWithTag("tab-Stops", useUnmergedTree = true).performClick()
            waitForIdle()
            onNodeWithTag("stops").performScrollToNode(hasTestTag("stop-Leederville"))
            onNodeWithTag("stop-Leederville").performClick()
            waitForIdle()
            onNodeWithText("At this stop").assertExists()
            assertEquals(1, tabs(), "on a desktop the stop's page covered the sidebar")
        }

    private companion object {
        /** The top-up's pretend payment and a little over, in milliseconds. */
        const val PayAndSettle = 1_600L
    }
}
