package io.kontour.ui.adaptive

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.navigationevent.DirectNavigationEventInput
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import io.kontour.ui.foundation.Text
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * The list keeps its place while the detail has the screen.
 *
 * On one pane the list and the detail take turns, and the one not showing
 * leaves the composition: the list was movable content, which keeps its state
 * while it *moves*, and nothing when it is put down. Opening a stop and coming
 * back put the list at its top, however far down the stop had been — and a
 * window narrowing to one pane with the detail open did the same, a step later.
 *
 * The list's own `rememberLazyListState` is what is asserted, because that is
 * the state an app has and expects to be kept: saveable state, which the
 * scaffold now saves for the length of the list's absence.
 */
@OptIn(ExperimentalTestApi::class)
class ListDetailStateTest {

    private class Back {
        val dispatcher = NavigationEventDispatcher()
        val input = DirectNavigationEventInput().also { dispatcher.addInput(it) }
        val owner = object : NavigationEventDispatcherOwner {
            override val navigationEventDispatcher = dispatcher
        }
    }

    private class Fixture {
        var focus by mutableStateOf(PaneFocus.List)
        var twoPane by mutableStateOf(false)
        var list: LazyListState? = null
    }

    @Composable
    private fun Harness(back: Back, fixture: Fixture) {
        CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides back.owner) {
            KontourTheme(darkTheme = false, reduceMotion = true) {
                Box(Modifier.size(1000.dp, 700.dp)) {
                    ListDetailPaneScaffold(
                        focus = fixture.focus,
                        onBack = { fixture.focus = PaneFocus.List },
                        list = {
                            val state = rememberLazyListState()
                            SideEffect { fixture.list = state }
                            LazyColumn(Modifier.fillMaxSize().testTag("list"), state = state) {
                                items(100) { Text("Stop $it", Modifier.fillMaxWidth().height(48.dp)) }
                            }
                        },
                        detail = { Text("Perth Busport", Modifier.fillMaxSize().testTag("detail")) },
                        twoPane = fixture.twoPane,
                    )
                }
            }
        }
    }

    @Test
    fun backFromTheDetailFindsTheListWhereItWasLeft() = runComposeUiTest {
        val fixture = Fixture()
        setContent { Harness(Back(), fixture) }
        waitForIdle()
        onNodeWithTag("list").performScrollToIndex(40)
        waitForIdle()
        assertEquals(40, assertNotNull(fixture.list).firstVisibleItemIndex, "the list did not scroll")

        runOnUiThread { fixture.focus = PaneFocus.Detail }
        waitForIdle()
        // Gone, not hidden: what follows is the list coming back, not staying.
        onNodeWithTag("list").assertDoesNotExist()

        runOnUiThread { fixture.focus = PaneFocus.List }
        waitForIdle()
        onNodeWithTag("list").assertExists()
        assertEquals(
            40,
            assertNotNull(fixture.list).firstVisibleItemIndex,
            "back from the detail, the list was at its top again",
        )
    }

    @Test
    fun narrowingToOnePaneOnTheDetailKeepsTheListsPlace() = runComposeUiTest {
        val fixture = Fixture()
        fixture.twoPane = true
        fixture.focus = PaneFocus.Detail
        setContent { Harness(Back(), fixture) }
        waitForIdle()
        onNodeWithTag("list").performScrollToIndex(40)
        waitForIdle()
        assertEquals(40, assertNotNull(fixture.list).firstVisibleItemIndex, "the list did not scroll")

        // The window narrows with a stop open: one pane, and it is the detail.
        runOnUiThread { fixture.twoPane = false }
        waitForIdle()
        onNodeWithTag("list").assertDoesNotExist()

        runOnUiThread { fixture.focus = PaneFocus.List }
        waitForIdle()
        assertEquals(
            40,
            assertNotNull(fixture.list).firstVisibleItemIndex,
            "after narrowing to one pane, back to the list found it at its top",
        )
    }
}
