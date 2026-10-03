package io.kontour.ui.catalog

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
