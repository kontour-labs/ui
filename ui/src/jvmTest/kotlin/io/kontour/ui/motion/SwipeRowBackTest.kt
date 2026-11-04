package io.kontour.ui.motion

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.NavigationEventHandler
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import io.kontour.ui.components.list.ReorderableItem
import io.kontour.ui.components.list.SwipeAction
import io.kontour.ui.components.list.SwipeActions
import io.kontour.ui.components.list.SwipeActionsState
import io.kontour.ui.components.list.SwipeValue
import io.kontour.ui.components.list.rememberReorderableState
import io.kontour.ui.components.list.rememberSwipeActionsState
import io.kontour.ui.foundation.SystemIcons
import io.kontour.ui.foundation.Text
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A swipe row held to reorder, on a page that swipes back from its content:
 * the row waits out a fingertip's wander before it decides a press is a swipe,
 * later than the page's back claims, and an app reported the page sliding back
 * as such a row opened. Not reproduced here on 0.5.2 or since — the hold takes
 * the moves it waits on — and kept as the guard that it stays so.
 */
@OptIn(ExperimentalTestApi::class)
class SwipeRowBackTest {

    private object Info : NavigationEventInfo()

    private class Recorder : NavigationEventHandler<NavigationEventInfo>(Info, true) {
        var started = 0
        var completed = 0

        override fun onBackStarted(event: NavigationEvent) {
            started++
        }

        override fun onBackCompleted() {
            completed++
        }
    }

    private val dispatcher = NavigationEventDispatcher()
    private val recorder = Recorder().also { dispatcher.addHandler(it) }
    private val owner = object : NavigationEventDispatcherOwner {
        override val navigationEventDispatcher = dispatcher
    }

    private val follow: @Composable () -> SwipeAction = { SwipeAction("Follow", SystemIcons.Default.close, {}, Color.Blue) }
    private val remove: @Composable () -> SwipeAction = { SwipeAction("Remove", SystemIcons.Default.close, {}, Color.Red) }

    /** A page that swipes back, with three rows; [held] puts them in a list held to reorder. */
    @Composable
    private fun Page(held: Boolean, start: List<@Composable () -> SwipeAction>, end: List<@Composable () -> SwipeAction>, rows: List<SwipeActionsState>) {
        CompositionLocalProvider(
            LocalNavigationEventDispatcherOwner provides owner,
            LocalBackStyle provides BackStyle.Swipe,
        ) {
            KontourTheme(darkTheme = false) {
                Box(Modifier.fillMaxSize().contentSwipeBack()) {
                    val list = rememberLazyListState()
                    val reorder = rememberReorderableState(list) { _, _ -> }
                    LazyColumn(state = list, modifier = Modifier.fillMaxSize()) {
                        items(rows.size) { index ->
                            val row = @Composable {
                                SwipeActions(start = start.map { it() }, end = end.map { it() }, state = rows[index]) {
                                    Text("Stop $index", Modifier.fillMaxWidth().height(64.dp).testTag("row$index"))
                                }
                            }
                            if (held) ReorderableItem(reorder, index) { row() } else row()
                        }
                    }
                }
            }
        }
    }

    /**
     * A swipe across [share] of the first row towards the trailing edge, a frame
     * at a time: a little opens a row, most of it is a page's back.
     */
    private fun ComposeUiTest.swipeAcrossTheFirstRow(share: Float = 0.6f) {
        mainClock.autoAdvance = false
        val width = onRoot().fetchSemanticsNode().size.width
        val step = width * share / 12
        onRoot().performTouchInput { down(Offset(width * 0.2f, 30f)) }
        repeat(12) {
            onRoot().performTouchInput { moveTo(Offset(width * 0.2f + step * (it + 1), 30f)) }
            mainClock.advanceTimeByFrame()
        }
        onRoot().performTouchInput { up() }
        mainClock.autoAdvance = true
        waitForIdle()
    }

    @Test
    fun aRowHeldToReorderTakesItsSwipeFromThePage() = runComposeUiTest {
        lateinit var rows: List<SwipeActionsState>
        setContent {
            KontourTheme(darkTheme = false) {
                rows = List(3) { rememberSwipeActionsState() }
                Page(held = true, start = listOf(follow), end = listOf(remove), rows = rows)
            }
        }
        waitForIdle()
        swipeAcrossTheFirstRow(share = 0.15f)
        assertEquals(0, recorder.started, "the page went back as the row was swiped")
        assertEquals(SwipeValue.Start, rows[0].currentValue, "the row did not open")
    }
}
