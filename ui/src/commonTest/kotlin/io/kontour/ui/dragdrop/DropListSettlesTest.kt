package io.kontour.ui.dragdrop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A drop list that has been laid out stays still: it does not recompose each
 * frame, whoever composes it.
 *
 * `Modifier.dropList` read the list's layout info as it composed, so every
 * layout invalidated the composition that called it. A caller that skips
 * recomposing hid that; one that cannot — here, a test writing the state out to
 * a variable, in an app anything that is not stable — recomposed the list every
 * frame, laid it out again, and recomposed it again, and a test waiting for
 * idle waited for ever.
 */
@OptIn(ExperimentalTestApi::class)
class DropListSettlesTest {

    @Test
    fun aDropListDoesNotRecomposeEveryFrame() = runComposeUiTest {
        lateinit var state: DropListState<String>
        var compositions = 0
        mainClock.autoAdvance = false
        setContent {
            KontourTheme {
                OverlayHost {
                    compositions++
                    val scroll = rememberLazyListState()
                    state = rememberDropListState(
                        listState = scroll,
                        type = Card,
                        items = listOf("Timetable", "Fares", "Signage"),
                        key = { it },
                        label = "To do",
                        itemLabel = { it },
                        onDrop = {},
                    )
                    LazyColumn(state = scroll, modifier = Modifier.fillMaxSize().dropList(state)) {
                        dragItems(state) { _, _ -> Box(Modifier.height(48.dp)) }
                    }
                }
            }
        }
        repeat(20) { mainClock.advanceTimeByFrame() }
        val settled = compositions
        repeat(20) { mainClock.advanceTimeByFrame() }
        assertEquals(settled, compositions, "20 more frames with nothing happening recomposed the list ${compositions - settled} times")
    }

    /** A row says it is one once it is laid out, and a gap there opens sideways. */
    @Test
    fun aRowKnowsItIsARow() = runComposeUiTest {
        lateinit var state: DropListState<String>
        setContent {
            KontourTheme {
                OverlayHost {
                    val scroll = rememberLazyListState()
                    state = rememberDropListState(
                        listState = scroll,
                        type = Card,
                        items = listOf("Timetable", "Fares"),
                        key = { it },
                        label = "To do",
                        itemLabel = { it },
                        onDrop = {},
                    )
                    LazyRow(state = scroll, modifier = Modifier.fillMaxSize().dropList(state)) {
                        dragItems(state) { _, _ -> Box(Modifier.width(96.dp)) }
                    }
                }
            }
        }
        waitForIdle()
        assertTrue(state.orientation == androidx.compose.foundation.gestures.Orientation.Horizontal)
    }

    private companion object {
        val Card = DragType<String>("card")
    }
}
