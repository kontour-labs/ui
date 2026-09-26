package io.kontour.ui.nav3

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import androidx.navigationevent.DirectNavigationEventInput
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import io.kontour.ui.foundation.Text
import io.kontour.ui.motion.BackStyle
import io.kontour.ui.motion.LocalBackStyle
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pages under `rememberPageTransitionStrategy` go back in the platform's feel,
 * driven through a real `NavDisplay` by the input every platform's back
 * arrives through.
 */
@OptIn(ExperimentalTestApi::class)
class PageBackTest {

    private object Stops
    private object Stop

    private class Back {
        val dispatcher = NavigationEventDispatcher()
        val input = DirectNavigationEventInput().also { dispatcher.addInput(it) }
        val owner = object : NavigationEventDispatcherOwner {
            override val navigationEventDispatcher = dispatcher
        }

        fun start() = input.backStarted(NavigationEvent(NavigationEvent.EDGE_LEFT, 0f, 0f, 0f))

        fun drag(progress: Float) = input.backProgressed(NavigationEvent(NavigationEvent.EDGE_LEFT, progress, 0f, 0f))
    }

    @Composable
    private fun Display(back: Back, style: BackStyle, backStack: SnapshotStateList<Any>) {
        CompositionLocalProvider(
            LocalNavigationEventDispatcherOwner provides back.owner,
            LocalBackStyle provides style,
        ) {
            KontourTheme(darkTheme = false) {
                OverlayHost {
                    NavDisplay(
                        backStack = backStack,
                        onBack = { backStack.removeLastOrNull() },
                        sceneDecoratorStrategies = listOf(rememberPageTransitionStrategy()),
                        entryProvider = entryProvider {
                            entry<Stops> { Text("Stops", Modifier.fillMaxSize().testTag("stops")) }
                            entry<Stop> { Text("Perth Busport", Modifier.fillMaxSize().testTag("stop")) }
                        },
                    )
                }
            }
        }
    }

    /**
     * A gesture's start and its first movement on different frames, as a real
     * one's are: the page's animations are made on the frame after the start,
     * and a seek that ran before them would begin them late.
     */
    private fun ComposeUiTest.dragTo(back: Back, progress: Float) {
        runOnUiThread { back.start() }
        waitForIdle()
        runOnUiThread { back.drag(progress) }
        waitForIdle()
    }

    private fun ComposeUiTest.bounds(tag: String): Rect {
        waitForIdle()
        return onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
    }

    @Test
    fun backPopsOnePageAndThenLeavesTheStackAlone() = runDesktopComposeUiTest(width = 400, height = 800) {
        val back = Back()
        val backStack = mutableStateListOf<Any>(Stops, Stop)
        setContent { Display(back, BackStyle.Predictive, backStack) }
        waitForIdle()

        runOnUiThread { back.input.backCompleted() }
        waitForIdle()
        assertEquals(listOf<Any>(Stops), backStack.toList())
        onNodeWithTag("stop", useUnmergedTree = true).assertDoesNotExist()
        onNodeWithTag("stops", useUnmergedTree = true).assertExists()
    }

    @Test
    fun underSwipeBackTheTopPageFollowsTheFinger() = runDesktopComposeUiTest(width = 400, height = 800) {
        val back = Back()
        val backStack = mutableStateListOf<Any>(Stops, Stop)
        setContent { Display(back, BackStyle.Swipe, backStack) }
        val rest = bounds("stop")

        dragTo(back, 0.5f)
        val mid = bounds("stop")
        onNodeWithTag("stops", useUnmergedTree = true).assertExists()
        assertEquals(rest.width, mid.width, 1f, "under swipe back the page moves; it does not shrink")
        assertEquals(
            rest.left + rest.width / 2, mid.left, rest.width * 0.03f,
            "halfway through the gesture, the page should be half the width across",
        )

        runOnUiThread { back.input.backCancelled() }
        waitForIdle()
        assertEquals(listOf<Any>(Stops, Stop), backStack.toList(), "an abandoned gesture popped the page")
        assertEquals(rest, bounds("stop"), "an abandoned gesture left the page where the hand let go")
    }

    @Test
    fun underPredictiveBackTheTopPageLiftsAndShrinks() = runDesktopComposeUiTest(width = 400, height = 800) {
        val back = Back()
        val backStack = mutableStateListOf<Any>(Stops, Stop)
        setContent { Display(back, BackStyle.Predictive, backStack) }
        val rest = bounds("stop")

        dragTo(back, 0.5f)
        val mid = bounds("stop")
        onNodeWithTag("stops", useUnmergedTree = true).assertExists()
        assertTrue(mid.width < rest.width, "under predictive back the page should shrink as it is lifted")

        runOnUiThread { back.input.backCompleted() }
        waitForIdle()
        assertEquals(listOf<Any>(Stops), backStack.toList(), "a gesture let go did not pop the page")
    }
}
