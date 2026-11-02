package io.kontour.ui.nav3

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.NavigationEventTransitionState
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import io.kontour.ui.components.action.Button
import io.kontour.ui.foundation.Text
import io.kontour.ui.motion.BackStyle
import io.kontour.ui.motion.LocalBackStyle
import io.kontour.ui.motion.pageBack
import io.kontour.ui.overlay.Dialog
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * A back swipe always ends, however it is interrupted. The dispatchers of one
 * app share one processor, which drops every back while a gesture is under
 * way, so one left under way is back gone for good: reported from a phone as
 * a page's back button doing nothing at all, then every back after it.
 */
@OptIn(ExperimentalTestApi::class)
class BackGestureEndsTest {

    private object Route
    private object Trip
    private object Vehicle

    private val owner = object : NavigationEventDispatcherOwner {
        override val navigationEventDispatcher = NavigationEventDispatcher()
    }

    private val prompt = mutableStateOf(false)

    @Composable
    private fun Display(backStack: SnapshotStateList<Any>, outside: @Composable () -> Unit = {}) {
        CompositionLocalProvider(
            LocalNavigationEventDispatcherOwner provides owner,
            LocalBackStyle provides BackStyle.Swipe,
        ) {
            KontourTheme(darkTheme = false) {
                OverlayHost {
                    NavDisplay(
                        backStack = backStack,
                        onBack = { backStack.removeLastOrNull() },
                        sceneDecoratorStrategies = listOf(rememberPageTransitionStrategy()),
                        entryProvider = entryProvider {
                            entry<Route> { Text("Route", Modifier.fillMaxSize().testTag("route")) }
                            entry<Trip> { Page("trip") }
                            entry<Vehicle> { Page("vehicle") }
                        },
                    )
                    outside()
                    Dialog(visible = prompt.value, onDismissRequest = { prompt.value = false }) { Text("Rate the app?") }
                }
            }
        }
    }

    @Composable
    private fun Page(tag: String) {
        val back = pageBack()
        Column(Modifier.fillMaxSize().testTag(tag)) {
            Button(onClick = { back?.invoke() }, modifier = Modifier.testTag("$tag-back")) { +"Back" }
        }
    }

    private fun ComposeUiTest.left(tag: String): Float? =
        onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()?.boundsInRoot?.left

    /** A pan from the middle of the page towards the trailing edge, [steps] of [step]px, held. */
    private fun ComposeUiTest.pan(steps: Int, step: Float) {
        onRoot().performTouchInput { down(Offset(120f, 500f)) }
        repeat(steps) {
            onRoot().performTouchInput { moveTo(Offset(120f + step * (it + 1), 500f)) }
            mainClock.advanceTimeByFrame()
        }
    }

    /**
     * The report: a dialog came up as a short swipe sprang the page back into
     * place, the overlay host turned the page's dispatcher off under it, and
     * the cancel was dropped. The page stayed a little way across, and its
     * back button, the edge and every back after it did nothing.
     */
    @Test
    fun aDialogOpeningAsASwipeSpringsBackLeavesBackWorking() = runDesktopComposeUiTest(width = 400, height = 800) {
        val backStack = mutableStateListOf<Any>(Route, Trip, Vehicle)
        setContent { Display(backStack) }
        waitForIdle()
        mainClock.autoAdvance = false
        pan(steps = 4, step = 10f)
        onRoot().performTouchInput { up() }
        mainClock.advanceTimeByFrame()
        prompt.value = true
        repeat(60) { mainClock.advanceTimeByFrame() }
        prompt.value = false
        mainClock.autoAdvance = true
        waitForIdle()

        assertIs<NavigationEventTransitionState.Idle>(owner.navigationEventDispatcher.transitionState.value, "the swipe was left under way")
        assertEquals(0f, left("vehicle"), "the page was left part of the way across")
        onAllNodesWithTag("vehicle-back", useUnmergedTree = true)[0].performClick()
        waitForIdle()
        assertEquals(listOf<Any>(Route, Trip), backStack.toList(), "the back button did nothing")
    }

    /** The same, with the finger still on the page as the dialog opens: it is ended, and the pan with it. */
    @Test
    fun aDialogOpeningUnderAHeldSwipeEndsIt() = runDesktopComposeUiTest(width = 400, height = 800) {
        val backStack = mutableStateListOf<Any>(Route, Trip, Vehicle)
        setContent { Display(backStack) }
        waitForIdle()
        mainClock.autoAdvance = false
        pan(steps = 6, step = 15f)
        prompt.value = true
        mainClock.advanceTimeByFrame()
        // Carried on and let go past half way under the dialog: not back.
        repeat(10) {
            onRoot().performTouchInput { moveTo(Offset(210f + 15f * (it + 1), 500f)) }
            mainClock.advanceTimeByFrame()
        }
        onRoot().performTouchInput { up() }
        repeat(60) { mainClock.advanceTimeByFrame() }
        prompt.value = false
        mainClock.autoAdvance = true
        waitForIdle()

        assertEquals(listOf<Any>(Route, Trip, Vehicle), backStack.toList(), "a swipe ended by the dialog still went back")
        assertIs<NavigationEventTransitionState.Idle>(owner.navigationEventDispatcher.transitionState.value, "the swipe was left under way")
        onAllNodesWithTag("vehicle-back", useUnmergedTree = true)[0].performClick()
        waitForIdle()
        assertEquals(listOf<Any>(Route, Trip), backStack.toList(), "the back button did nothing")
    }

    /**
     * A page the app takes away mid-swipe — closed, or replaced by a live
     * update — removed its input with the gesture still under way. Where the
     * handler the gesture went to outlives the page, as a sheet's own does,
     * nothing ended it.
     */
    @Test
    fun aPageTakenAwayMidSwipeLeavesBackWorking() = runDesktopComposeUiTest(width = 400, height = 800) {
        val backStack = mutableStateListOf<Any>(Route, Trip, Vehicle)
        val cancelled = mutableIntStateOf(0)
        val completed = mutableIntStateOf(0)
        setContent {
            Display(backStack) {
                // Composed after the pages, so it is the one back reaches.
                NavigationBackHandler(
                    state = rememberNavigationEventState(NavigationEventInfo.None),
                    isBackEnabled = true,
                    onBackCancelled = { cancelled.intValue++ },
                    onBackCompleted = { completed.intValue++ },
                )
            }
        }
        waitForIdle()
        mainClock.autoAdvance = false
        pan(steps = 6, step = 15f)
        backStack.removeAt(backStack.lastIndex)
        repeat(30) { mainClock.advanceTimeByFrame() }
        onRoot().performTouchInput { up() }
        mainClock.autoAdvance = true
        waitForIdle()

        assertIs<NavigationEventTransitionState.Idle>(owner.navigationEventDispatcher.transitionState.value, "the swipe was left under way")
        assertEquals(1, cancelled.intValue, "the handler was never told the swipe ended")
        onAllNodesWithTag("trip-back", useUnmergedTree = true)[0].performClick()
        waitForIdle()
        assertEquals(1, completed.intValue, "the back button did nothing")
    }
}
