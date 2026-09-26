package io.kontour.ui.adaptive

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.navigationevent.DirectNavigationEventInput
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import io.kontour.ui.foundation.Text
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `ListDetailPaneScaffold` on one pane answers back with its `onBack` — which
 * it declared and never called — and follows a gesture while it runs.
 */
@OptIn(ExperimentalTestApi::class)
class ListDetailBackTest {

    private object ScreenInfo : NavigationEventInfo()

    private class Back {
        val dispatcher = NavigationEventDispatcher()
        val input = DirectNavigationEventInput().also { dispatcher.addInput(it) }
        val owner = object : NavigationEventDispatcherOwner {
            override val navigationEventDispatcher = dispatcher
        }
    }

    private class Fixture {
        var selected by mutableStateOf<String?>(null)
        var screenBacks = 0
        var paneBacks = 0
        var twoPane by mutableStateOf(false)
    }

    @Composable
    private fun Harness(back: Back, fixture: Fixture) {
        CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides back.owner) {
            KontourTheme(darkTheme = false) {
                // The screen the scaffold is on, which has its own back.
                NavigationBackHandler(
                    state = rememberNavigationEventState(ScreenInfo),
                    isBackEnabled = true,
                    onBackCompleted = { fixture.screenBacks++ },
                )
                Box(Modifier.size(400.dp, 700.dp)) {
                    ListDetailPaneScaffold(
                        focus = if (fixture.selected == null) PaneFocus.List else PaneFocus.Detail,
                        onBack = {
                            fixture.paneBacks++
                            fixture.selected = null
                        },
                        list = { Text("Stops", Modifier.fillMaxSize().testTag("list")) },
                        detail = { Text(fixture.selected ?: "Pick a stop", Modifier.fillMaxSize().testTag("detail")) },
                        twoPane = fixture.twoPane,
                    )
                }
            }
        }
    }

    @Test
    fun backFromTheDetailGoesToTheListAndNoFurther() = runComposeUiTest {
        val back = Back()
        val fixture = Fixture()
        setContent { Harness(back, fixture) }
        runOnUiThread { fixture.selected = "Perth Busport" }
        waitForIdle()

        runOnUiThread { back.input.backCompleted() }
        waitForIdle()
        assertEquals(1, fixture.paneBacks, "back from the detail did not reach onBack")
        assertEquals(0, fixture.screenBacks, "back from the detail went past it to the screen")
        onNodeWithTag("list", useUnmergedTree = true).assertExists()

        runOnUiThread { back.input.backCompleted() }
        waitForIdle()
        assertEquals(1, fixture.paneBacks, "back on the list was taken by the scaffold")
        assertEquals(1, fixture.screenBacks, "with the list showing, back belongs to the screen")
    }

    @Test
    fun aGestureUncoversTheListAndAnAbandonedOneLeavesTheDetail() = runComposeUiTest {
        val back = Back()
        val fixture = Fixture()
        setContent { Harness(back, fixture) }
        runOnUiThread { fixture.selected = "Perth Busport" }
        waitForIdle()
        onNodeWithTag("list", useUnmergedTree = true).assertDoesNotExist()

        runOnUiThread {
            back.input.backStarted(NavigationEvent(NavigationEvent.EDGE_LEFT, 0f, 0f, 0f))
            back.input.backProgressed(NavigationEvent(NavigationEvent.EDGE_LEFT, 0.4f, 0f, 0f))
        }
        waitForIdle()
        onNodeWithTag("list", useUnmergedTree = true).assertExists()
        onNodeWithTag("detail", useUnmergedTree = true).assertExists()

        runOnUiThread { back.input.backCancelled() }
        waitForIdle()
        assertEquals(0, fixture.paneBacks, "an abandoned gesture called onBack")
        onNodeWithTag("list", useUnmergedTree = true).assertDoesNotExist()
        onNodeWithTag("detail", useUnmergedTree = true).assertExists()

        runOnUiThread {
            back.input.backStarted(NavigationEvent(NavigationEvent.EDGE_LEFT, 0f, 0f, 0f))
            back.input.backProgressed(NavigationEvent(NavigationEvent.EDGE_LEFT, 0.7f, 0f, 0f))
            back.input.backCompleted()
        }
        waitForIdle()
        assertEquals(1, fixture.paneBacks, "a gesture let go did not call onBack")
        onNodeWithTag("detail", useUnmergedTree = true).assertDoesNotExist()
        onNodeWithTag("list", useUnmergedTree = true).assertExists()
    }

    @Test
    fun onTwoPanesThereIsNothingToGoBackFrom() = runComposeUiTest {
        val back = Back()
        val fixture = Fixture()
        fixture.twoPane = true
        setContent { Harness(back, fixture) }
        runOnUiThread { fixture.selected = "Perth Busport" }
        waitForIdle()

        runOnUiThread { back.input.backCompleted() }
        waitForIdle()
        assertEquals(0, fixture.paneBacks, "two panes took back, with both panes already on screen")
        assertEquals(1, fixture.screenBacks)
    }
}
