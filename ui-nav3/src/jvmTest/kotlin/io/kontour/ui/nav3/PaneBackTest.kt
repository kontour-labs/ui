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
import io.kontour.ui.adaptive.PaneScaffoldDefaults
import io.kontour.ui.foundation.Text
import io.kontour.ui.motion.BackStyle
import io.kontour.ui.motion.LocalBackStyle
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Back from a pane that `NavDisplay` cannot animate, because the scene it is in
 * keeps its key: a detail beside its list, a supporting pane beside its main
 * one. The scene answers first, moves the pane with the hand, and pops once.
 */
@OptIn(ExperimentalTestApi::class)
class PaneBackTest {

    private object Stops
    private data class Stop(val name: String)
    private object Run
    private object Conditions

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
                        sceneStrategies = listOf(
                            ListDetailSceneStrategy(twoPane = true, 0.4f, resizable = false, showDivider = true),
                            SupportingPaneSceneStrategy(
                                twoPane = true, PaneScaffoldDefaults.SupportingWeight,
                                resizable = false, showDivider = true,
                            ),
                        ),
                        sceneDecoratorStrategies = listOf(rememberPageTransitionStrategy()),
                        entryProvider = entryProvider {
                            entry<Stops>(metadata = listPane()) { Text("Stops", Modifier.fillMaxSize()) }
                            entry<Stop>(metadata = detailPane()) {
                                Text(it.name, Modifier.fillMaxSize().testTag(it.name))
                            }
                            entry<Run>(metadata = mainPane()) { Text("the run", Modifier.fillMaxSize()) }
                            entry<Conditions>(metadata = supportingPane()) {
                                Text("the conditions", Modifier.fillMaxSize().testTag("conditions"))
                            }
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
    fun theDetailFollowsTheFingerAndPopsOnce() = runDesktopComposeUiTest(width = 1000, height = 700) {
        val back = Back()
        val backStack = mutableStateListOf<Any>(Stops, Stop("Perth"), Stop("McIver"))
        setContent { Display(back, BackStyle.Swipe, backStack) }
        val rest = bounds("McIver")

        dragTo(back, 0.5f)
        val mid = bounds("McIver")
        assertEquals(
            rest.left + rest.width / 2, mid.left, rest.width * 0.03f,
            "halfway through, the detail should be half its width towards the trailing edge",
        )

        runOnUiThread { back.input.backCancelled() }
        waitForIdle()
        assertEquals(rest, bounds("McIver"), "an abandoned gesture left the detail where the hand let go")
        assertEquals(3, backStack.size)

        dragTo(back, 0.7f)
        runOnUiThread { back.input.backCompleted() }
        waitForIdle()
        assertEquals(listOf<Any>(Stops, Stop("Perth")), backStack.toList(), "a gesture let go should pop one detail")
        assertEquals(rest, bounds("Perth"), "the detail below came up at rest")
    }

    @Test
    fun underPredictiveBackTheDetailLiftsInItsPane() = runDesktopComposeUiTest(width = 1000, height = 700) {
        val back = Back()
        val backStack = mutableStateListOf<Any>(Stops, Stop("Perth"))
        setContent { Display(back, BackStyle.Predictive, backStack) }
        val rest = bounds("Perth")

        dragTo(back, 0.5f)
        val mid = bounds("Perth")
        assertTrue(mid.width < rest.width, "the detail should shrink as it is lifted")

        runOnUiThread { back.input.backCompleted() }
        waitForIdle()
        assertEquals(listOf<Any>(Stops), backStack.toList())
    }

    @Test
    fun aKeyOrAButtonPopsTheDetailAtOnce() = runDesktopComposeUiTest(width = 1000, height = 700) {
        val back = Back()
        val backStack = mutableStateListOf<Any>(Stops, Stop("Perth"), Stop("McIver"))
        setContent { Display(back, BackStyle.Predictive, backStack) }
        waitForIdle()
        runOnUiThread { back.input.backCompleted() }
        waitForIdle()
        assertEquals(listOf<Any>(Stops, Stop("Perth")), backStack.toList())
        runOnUiThread { back.input.backCompleted() }
        waitForIdle()
        assertEquals(listOf<Any>(Stops), backStack.toList())
    }

    @Test
    fun theSupportingPaneGoesOnceAndItsMainPaneStays() = runDesktopComposeUiTest(width = 1200, height = 700) {
        val back = Back()
        val backStack = mutableStateListOf<Any>(Run, Conditions)
        setContent { Display(back, BackStyle.Swipe, backStack) }
        val rest = bounds("conditions")

        dragTo(back, 0.5f)
        val mid = bounds("conditions")
        assertTrue(mid.left > rest.left, "the supporting pane did not move towards its edge")

        runOnUiThread { back.input.backCompleted() }
        waitForIdle()
        assertEquals(listOf<Any>(Run), backStack.toList(), "back should close the supporting pane, once")
        onNodeWithTag("conditions", useUnmergedTree = true).assertDoesNotExist()
    }
}
