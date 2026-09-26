package io.kontour.ui.nav3

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
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

/**
 * iOS 26's back from anywhere on the page: a sideways pan towards the trailing
 * edge goes back, and yields to anything on the page that pans sideways first.
 */
@OptIn(ExperimentalTestApi::class)
class ContentSwipeBackTest {

    private object Stops
    private object Stop

    private val owner = object : NavigationEventDispatcherOwner {
        override val navigationEventDispatcher = NavigationEventDispatcher()
    }

    @Composable
    private fun Display(style: BackStyle, backStack: SnapshotStateList<Any>, contentSwipe: Boolean = true) {
        CompositionLocalProvider(
            LocalNavigationEventDispatcherOwner provides owner,
            LocalBackStyle provides style,
        ) {
            KontourTheme(darkTheme = false) {
                OverlayHost {
                    NavDisplay(
                        backStack = backStack,
                        onBack = { backStack.removeLastOrNull() },
                        sceneDecoratorStrategies = listOf(rememberPageTransitionStrategy(contentSwipe)),
                        entryProvider = entryProvider {
                            entry<Stops> { Text("Stops", Modifier.fillMaxSize().testTag("stops")) }
                            entry<Stop> {
                                Column(Modifier.fillMaxSize().testTag("stop")) {
                                    // Departures in a row that scrolls sideways,
                                    // as a carousel or a chip row does.
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .height(120.dp)
                                            .testTag("row")
                                            .horizontalScroll(rememberScrollState(initial = 400)),
                                    ) {
                                        repeat(12) { Box(Modifier.width(120.dp).height(120.dp)) { Text("12:0$it") } }
                                    }
                                    Text("Perth Busport", Modifier.fillMaxSize())
                                }
                            }
                        },
                    )
                }
            }
        }
    }

    @Test
    fun aPanTowardsTheTrailingEdgeFromTheMiddleGoesBack() = runDesktopComposeUiTest(width = 400, height = 800) {
        val backStack = mutableStateListOf<Any>(Stops, Stop)
        setContent { Display(BackStyle.Swipe, backStack) }
        waitForIdle()
        onNodeWithTag("stop", useUnmergedTree = true).performTouchInput {
            swipe(Offset(width * 0.3f, height * 0.6f), Offset(width * 0.95f, height * 0.6f), durationMillis = 300)
        }
        waitForIdle()
        assertEquals(listOf<Any>(Stops), backStack.toList(), "a pan across the page did not go back")
    }

    @Test
    fun aShortSlowPanIsAbandoned() = runDesktopComposeUiTest(width = 400, height = 800) {
        val backStack = mutableStateListOf<Any>(Stops, Stop)
        setContent { Display(BackStyle.Swipe, backStack) }
        waitForIdle()
        onNodeWithTag("stop", useUnmergedTree = true).performTouchInput {
            swipe(Offset(width * 0.3f, height * 0.6f), Offset(width * 0.45f, height * 0.6f), durationMillis = 1500)
        }
        waitForIdle()
        assertEquals(listOf<Any>(Stops, Stop), backStack.toList(), "a short, slow pan went back")
    }

    @Test
    fun aRowThatScrollsSidewaysTakesThePanFirst() = runDesktopComposeUiTest(width = 400, height = 800) {
        val backStack = mutableStateListOf<Any>(Stops, Stop)
        setContent { Display(BackStyle.Swipe, backStack) }
        waitForIdle()
        onNodeWithTag("row", useUnmergedTree = true).performTouchInput {
            swipe(Offset(width * 0.3f, centerY), Offset(width * 0.95f, centerY), durationMillis = 300)
        }
        waitForIdle()
        assertEquals(listOf<Any>(Stops, Stop), backStack.toList(), "a pan the row scrolled with also went back")
    }

    @Test
    fun aPanTowardsTheLeadingEdgeOrMostlyDownIsNotBack() = runDesktopComposeUiTest(width = 400, height = 800) {
        val backStack = mutableStateListOf<Any>(Stops, Stop)
        setContent { Display(BackStyle.Swipe, backStack) }
        waitForIdle()
        onNodeWithTag("stop", useUnmergedTree = true).performTouchInput {
            swipe(Offset(width * 0.9f, height * 0.6f), Offset(width * 0.1f, height * 0.6f), durationMillis = 300)
        }
        onNodeWithTag("stop", useUnmergedTree = true).performTouchInput {
            swipe(Offset(width * 0.3f, height * 0.4f), Offset(width * 0.5f, height * 0.9f), durationMillis = 300)
        }
        waitForIdle()
        assertEquals(listOf<Any>(Stops, Stop), backStack.toList())
    }

    @Test
    fun onlyUnderSwipeBackAndOnlyWhenAskedFor() = runDesktopComposeUiTest(width = 400, height = 800) {
        val predictive = mutableStateListOf<Any>(Stops, Stop)
        setContent { Display(BackStyle.Predictive, predictive) }
        waitForIdle()
        onNodeWithTag("stop", useUnmergedTree = true).performTouchInput {
            swipe(Offset(width * 0.3f, height * 0.6f), Offset(width * 0.95f, height * 0.6f), durationMillis = 300)
        }
        waitForIdle()
        assertEquals(listOf<Any>(Stops, Stop), predictive.toList(), "Android's back is the system's edge gesture only")
    }

    @Test
    fun turnedOffTheContentIsLeftAlone() = runDesktopComposeUiTest(width = 400, height = 800) {
        val backStack = mutableStateListOf<Any>(Stops, Stop)
        setContent { Display(BackStyle.Swipe, backStack, contentSwipe = false) }
        waitForIdle()
        onNodeWithTag("stop", useUnmergedTree = true).performTouchInput {
            swipe(Offset(width * 0.3f, height * 0.6f), Offset(width * 0.95f, height * 0.6f), durationMillis = 300)
        }
        waitForIdle()
        assertEquals(listOf<Any>(Stops, Stop), backStack.toList())
    }
}
