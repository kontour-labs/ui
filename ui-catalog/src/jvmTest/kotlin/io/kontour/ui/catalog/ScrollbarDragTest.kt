package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.list.Scrollbar
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Dragging the scrollbar scrolls the list.
 *
 * It used to be an indicator and nothing else, and the file argued for that: a
 * 6dp target is not how anyone scrolls a list, and widening it enough to grab
 * would make it compete with the content. Both halves were about the wrong
 * device. The bar is drawn only where there is a pointer to draw it for, 6dp is
 * what every scrollbar on such a machine measures, and putting the drag on the
 * whole bar rather than on the thumb means there is nothing to widen.
 *
 * ### The rate matters as much as the direction
 *
 * A scrollbar that scrolls the right way at the wrong rate is a scrollbar whose
 * thumb does not stay under the finger, which is worse than one that does not
 * move: the thumb is the thing being dragged. So this asserts on where the list
 * ends up as a *fraction* of its travel, against the fraction of the track the
 * drag crossed, rather than merely that the list moved.
 */
class ScrollbarDragTest {

    @Test
    fun draggingTheBarScrollsTheListByTheTrackFraction() {
        var index = -1
        var offsetIntoItem = 0
        var totalItems = 0

        Scene(width = 400, height = 600) {
            val listState = rememberLazyListState()
            Box(Modifier.fillMaxSize().background(Color.White)) {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    items((1..Rows).toList()) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(RowHeight.dp)
                                .background(if (it % 2 == 0) Color.LightGray else Color.White)
                        )
                    }
                }
                // `alwaysVisible`, because the scene reports no pointer that can
                // hover and the bar hides itself without one.
                Scrollbar(
                    listState,
                    Modifier.align(Alignment.CenterEnd),
                    alwaysVisible = true,
                )
            }
            index = listState.firstVisibleItemIndex
            offsetIntoItem = listState.firstVisibleItemScrollOffset
            totalItems = listState.layoutInfo.totalItemsCount
        }.use { scene ->
            scene.frames(10)
            // Down the middle of the bar, a quarter of the window's height. The
            // bar sits at the trailing edge and is `hoveredThickness` wide, so
            // five pixels in from the right is on it wherever the thumb is.
            val x = 400f - 5f
            scene.drag(
                from = Offset(x, 100f),
                to = Offset(x, 100f + Travelled),
                steps = 20,
                pointer = PointerType.Mouse,
            )
            scene.frames(30)
        }

        assertTrue(totalItems == Rows, "the list did not build: $totalItems rows")
        assertTrue(
            index > 0,
            "the list is still at the top after a ${Travelled.toInt()}px drag " +
                "down the scrollbar — dragging it does nothing",
        )

        // Where the list got to, as a fraction of everything it could scroll.
        val rowPx = RowHeight * SceneDensity
        val scrolled = index * rowPx + offsetIntoItem
        val scrollable = Rows * rowPx - 600f
        val reached = scrolled / scrollable

        // Where the drag got to, as a fraction of the thumb's travel. The track
        // is the window less the bar's own padding at each end, and the thumb is
        // as long as the visible share of the list — so the travel is the track
        // minus the thumb, and the drag crossed `Travelled` of it.
        val track = 600f - 2f * PaddingPx
        val thumb = (track * (600f / (Rows * rowPx))).coerceAtLeast(MinThumbPx)
        val expected = Travelled / (track - thumb)

        assertTrue(
            kotlin.math.abs(reached - expected) < Tolerance,
            "the drag crossed ${(expected * 100).toInt()}% of the thumb's travel " +
                "and the list moved ${(reached * 100).toInt()}% of its own — the " +
                "thumb does not stay under the pointer",
        )
    }

    private companion object {
        const val Rows = 60
        const val RowHeight = 40
        const val SceneDensity = 2f
        const val Travelled = 120f

        /** `Theme.spacing.xxs` at this density, top and bottom of the track. */
        const val PaddingPx = 4f

        /** `ScrollbarDefaults.MinThumbLength` at this density. */
        const val MinThumbPx = 64f

        /**
         * How far off the rate may be.
         *
         * A tenth. `LazyListState` reports items rather than pixels, so the
         * scrollbar estimates the content length from the average visible item
         * — exact here, where every row is the same height, but the drag also
         * starts after a touch slop the arithmetic above does not model.
         */
        const val Tolerance = 0.1f
    }
}
