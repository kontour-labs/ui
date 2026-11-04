package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.dp
import io.kontour.icons.tabler.Tabler
import io.kontour.icons.tabler.outline.Trash
import io.kontour.ui.components.list.SwipeAction
import io.kontour.ui.components.list.SwipeActions
import io.kontour.ui.components.list.SwipeActionsState
import io.kontour.ui.components.list.rememberSwipeActionsState
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.sheet.ModalBottomSheet
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A swipe row in a bottom sheet takes a thumb's swipe, and the sheet keeps the
 * drags that are really its own.
 *
 * Reported for 0.5.3: swipe actions in a bottom sheet were hard to drag
 * sideways, *"because the sheet often just takes over"*. A row decided its
 * direction on the first few pixels and gave the gesture away for good if they
 * were steep, and a thumb lands steeply before it flattens into a swipe. In a
 * list the list took those; in a sheet the sheet did, and moved. The row now
 * keeps watching until something else claims the gesture, and a sheet waits
 * longer than a list before it does.
 */
class SwipeInSheetTest {

    @Test
    fun aThumbsSwipeThatLandsSteeplyOpensTheRowAndLeavesTheSheet() {
        val (row, sheet) = gesture(
            // Down and a little across as the thumb lands, then across.
            listOf(Offset(-2f, 4f), Offset(-3f, 4f), Offset(-5f, 3f)) + List(17) { Offset(-9f, 1f) },
        )
        assertTrue(row < -120f, "a thumb's swipe moved the row only $row px")
        assertTrue(abs(sheet) < 1f, "the sheet moved $sheet px under a swipe the row took")
    }

    @Test
    fun aDragDownIsStillTheSheets() {
        val (row, sheet) = gesture(List(20) { Offset(-1f, 10f) })
        assertTrue(abs(row) < 1f, "a drag down moved the row $row px")
        assertTrue(sheet > 60f, "a drag down moved the sheet only $sheet px")
    }

    @Test
    fun aDragThatStartsDownAndTurnsLateIsStillTheSheets() {
        // Down past the sheet's slop before it turns: the sheet has it by then,
        // and the row must not take it back.
        val (row, sheet) = gesture(List(8) { Offset(0f, 8f) } + List(12) { Offset(-10f, 0f) })
        assertTrue(abs(row) < 1f, "a drag the sheet had already taken moved the row $row px")
        assertTrue(sheet > 40f, "the sheet let go of its own drag, moving only $sheet px")
    }

    /**
     * Presses on a swipe row near the top of a modal sheet's list, moves by
     * [steps] one frame apart, and reports how far the row and the sheet moved
     * with the finger still down.
     */
    private fun gesture(steps: List<Offset>): Pair<Float, Float> {
        var row = Rect.Zero
        var state: SwipeActionsState? = null
        var top = Float.NaN
        Scene(width = 700, height = 1400) {
            val swipe = rememberSwipeActionsState()
            state = swipe
            OverlayHost(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().background(Color.White))
                ModalBottomSheet(visible = true, onDismissRequest = {}) { padding ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(padding)
                            .onGloballyPositioned { top = it.positionInRoot().y },
                    ) {
                        Box(Modifier.fillMaxWidth().height(40.dp))
                        SwipeActions(
                            end = listOf(SwipeAction("Remove", Tabler.Outline.Trash, {}, Color.Red)),
                            state = swipe,
                            modifier = Modifier.reportBounds { row = it },
                        ) {
                            Box(Modifier.fillMaxWidth().height(64.dp).background(Color.LightGray).clickable { })
                        }
                        repeat(3) { Box(Modifier.fillMaxWidth().height(64.dp)) }
                    }
                }
            }
        }.use { scene ->
            // Opened and settled: a sheet still on its way catches a finger.
            scene.frames(90)
            val startTop = top
            var at = Offset(row.center.x + 100f, row.center.y)
            scene.press(at)
            for (step in steps) {
                at += step
                scene.move(at)
                scene.frame()
            }
            scene.frames(2)
            val offset = requireNotNull(state).offset.let { if (it.isNaN()) 0f else it }
            return offset to (top - startTop)
        }
    }
}
