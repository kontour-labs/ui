package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.list.IndexScroller
import io.kontour.ui.components.list.IndexSection
import io.kontour.ui.components.list.scrollbar
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A lazy list's thumb keeps its length wherever the list is.
 *
 * Reported as the scrollbar *jumping around in height at different parts of the
 * page*: a lazy list's length was the average row on screen times the number of
 * rows, so over a run of short headers the list seemed short and the thumb long,
 * and over tall cards the other way about. Its length is now from every row the
 * list has measured, so once the list has been seen through, the thumb is the
 * same length at the headers as at the cards.
 */
class ScrollbarStableLengthTest {

    @Test
    fun theThumbIsAsLongOverShortRowsAsOverTallOnes() {
        var list: LazyListState? = null
        Scene(width = 120, height = 400) {
            val state = rememberLazyListState()
            list = state
            Box(Modifier.fillMaxSize().background(Color.White)) {
                LazyColumn(state = state, modifier = Modifier.fillMaxSize().scrollbar(state, alwaysVisible = true)) {
                    items(Rows.size) { Box(Modifier.fillMaxWidth().height(Rows[it].dp)) }
                }
            }
        }.use { scene ->
            scene.frames(2)
            val state = requireNotNull(list)
            // Once through, as a reader would have been.
            for (index in Rows.indices step 2) {
                state.requestScrollToItem(index)
                scene.frames(2)
            }
            val lengths = Stops.associateWith { index ->
                state.requestScrollToItem(index)
                scene.frames(3).thumbLength()
            }
            assertTrue(lengths.values.all { it > 0 }, "the thumb was not drawn: $lengths")
            val spread = lengths.values.max() - lengths.values.min()
            assertTrue(spread <= Tolerance, "the thumb's length changed by ${spread}px as the list scrolled: $lengths")
        }
    }

    @Test
    fun aListWithAnIndexHasNoScrollbar() {
        Scene(width = 240, height = 400) {
            val state = rememberLazyListState()
            Row(Modifier.fillMaxSize().background(Color.White)) {
                LazyColumn(
                    state = state,
                    modifier = Modifier.weight(1f).fillMaxHeight().scrollbar(state, alwaysVisible = true),
                ) {
                    items(Rows.size) { Box(Modifier.fillMaxWidth().height(Rows[it].dp)) }
                }
                // Its own half of the scene, so its letters are not the ink looked for.
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.CenterEnd) {
                    IndexScroller(
                        state = state,
                        sections = listOf(IndexSection("A", 0), IndexSection("B", 20)),
                        modifier = Modifier.width(40.dp),
                    )
                }
            }
        }.use { scene ->
            val frame = scene.frames(10)
            assertTrue(frame.thumbLength(columns = 0 until 120) == 0, "a scrollbar was drawn beside the index")
        }
    }

    /** How many rows of [columns] hold anything but the white page. */
    private fun BufferedImage.thumbLength(columns: IntRange = 0 until width): Int {
        var first = -1
        var last = -1
        for (y in 0 until height) {
            if (columns.any { x -> (getRGB(x, y) and 0xFFFFFF) != 0xFFFFFF }) {
                if (first < 0) first = y
                last = y
            }
        }
        return if (first < 0) 0 else last - first + 1
    }

    private companion object {
        /** Ten 20dp headers, ten 60dp cards, and again: a list whose rows are anything but even. */
        val Rows = List(40) { if ((it / 10) % 2 == 0) 20 else 60 }

        /** Among the headers, among the cards, and the same again. */
        val Stops = listOf(0, 12, 20, 30)

        /** Antialiasing on the thumb's rounded caps, and nothing more. */
        const val Tolerance = 3
    }
}
