package io.kontour.ui.interaction

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.list.ReorderableItem
import io.kontour.ui.components.list.rememberReorderableState
import io.kontour.ui.dragdrop.DragType
import io.kontour.ui.dragdrop.dragSource
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A press held until it picks something up, and let go without moving, is not
 * also a tap on what was held.
 *
 * The pick-up put the release down on the Main pass, which runs from the
 * innermost node out — so a row's own `clickable`, inside the node holding the
 * row, heard the release first and clicked. Moving hid it, because a tap gives
 * up on movement something else has consumed; holding still and letting go did
 * not. Reported as a draggable list item that "doesn't consume the tap
 * sometimes".
 */
@OptIn(ExperimentalTestApi::class)
class HoldIsNotATapTest {

    @Test
    fun aRowHeldAndLetGoIsNotClicked() = runComposeUiTest {
        var clicks = 0
        setContent {
            KontourTheme {
                val list = rememberLazyListState()
                val state = rememberReorderableState(list) { _, _ -> }
                LazyColumn(Modifier.fillMaxSize(), state = list) {
                    items(3) { index ->
                        ReorderableItem(state = state, index = index, itemCount = 3) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .then(if (index == 0) Modifier.testTag("row") else Modifier)
                                    .clickable { if (index == 0) clicks++ }
                            )
                        }
                    }
                }
            }
        }
        onNodeWithTag("row").performTouchInput {
            down(center)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 200)
            up()
        }
        waitForIdle()
        assertEquals(0, clicks, "a row held until it lifted, and let go where it was, was also clicked")

        // And a tap is still a tap.
        onNodeWithTag("row").performTouchInput {
            down(center)
            advanceEventTime(50)
            up()
        }
        waitForIdle()
        assertEquals(1, clicks, "a quick tap on the row was not a click")
    }

    @Test
    fun aDragSourceHeldAndLetGoIsNotClicked() = runComposeUiTest {
        val card = DragType<String>("card")
        var clicks = 0
        setContent {
            KontourTheme {
                OverlayHost {
                    Box(
                        Modifier
                            .size(120.dp)
                            .testTag("source")
                            .dragSource(card, "Timetable", label = "Timetable")
                            .clickable { clicks++ }
                    )
                }
            }
        }
        onNodeWithTag("source").performTouchInput {
            down(center)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 200)
            up()
        }
        waitForIdle()
        assertEquals(0, clicks, "a card held until it was picked up, and let go where it was, was also clicked")

        onNodeWithTag("source").performTouchInput {
            down(center)
            advanceEventTime(50)
            up()
        }
        waitForIdle()
        assertEquals(1, clicks, "a quick tap on the card was not a click")
    }
}
