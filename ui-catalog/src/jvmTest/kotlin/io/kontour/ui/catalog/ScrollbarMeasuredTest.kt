package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.list.rememberScrollbarListState
import io.kontour.ui.components.list.scrollbar
import java.awt.image.BufferedImage
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A list made with `rememberScrollbarListState` has a thumb as long as the
 * list says, from the start, and keeps it.
 *
 * Asked for after three rounds of a thumb that was a guess: *often smaller than
 * it thinks it is*, *when you scroll to the bottom, it just jumps to the
 * bottom*, and *when you scroll back up, it decides to get a bit taller or
 * shorter*. A lazy list cannot know the rows it has not laid out; made with
 * this, it measures them ahead, and the scrollbar knows the list's length.
 */
class ScrollbarMeasuredTest {

    /**
     * One card taller than the window, then a run of rows each a different
     * height: the worst a first guess can be, and rows that cannot pass for one
     * another — a row measured in the composition of the one before reported
     * that one's height, which rows all alike never showed.
     */
    private val heights = listOf(600) + List(40) { 10 + (it * 37) % 90 }

    @Test
    fun theThumbIsTheListsLengthFromTheStart() {
        val exact = thumbs { ExactColumn(heights) }.first
        val measured = thumbs { MeasuredList(heights, measured = true) { } }.first
        assertTrue(exact > 0 && measured > 0, "a thumb was not drawn: exact $exact, measured $measured")
        assertTrue(abs(exact - measured) <= 2, "the measured list's thumb is ${measured}px where the list's length makes it ${exact}px")
    }

    @Test
    fun scrolledToTheEndAndBackItNeitherJumpsNorChangesLength() {
        var list: LazyListState? = null
        Scene(width = Width, height = Height) { MeasuredList(heights, measured = true) { list = it } }.use { scene ->
            // The rows measured, a few a frame, before anything moves.
            scene.settle()
            val state = requireNotNull(list)
            val tops = mutableListOf<Int>()
            val lengths = mutableListOf<Int>()
            fun look() {
                val (top, bottom) = scene.frame().thumb()
                if (top >= 0) {
                    tops += top
                    lengths += bottom - top
                }
            }
            look()
            repeat(80) {
                onComposeUiThread { state.dispatchRawDelta(20f) }
                look()
            }
            repeat(80) {
                onComposeUiThread { state.dispatchRawDelta(-20f) }
                look()
            }
            val steps = tops.zipWithNext { a, b -> abs(b - a) }
            val ordinary = steps.sorted()[steps.size * 9 / 10]
            assertTrue(steps.max() <= ordinary + 3, "the thumb jumped ${steps.max()}px in a frame where it moves about $ordinary: $tops")
            assertTrue(lengths.max() - lengths.min() <= 1, "the thumb's length changed from ${lengths.min()} to ${lengths.max()}px while scrolling: $lengths")
        }
    }

    /** The thumb's length after a few frames at rest. */
    private fun thumbs(content: @Composable () -> Unit): Pair<Int, Int> {
        var result = -1 to -1
        Scene(width = Width, height = Height) { content() }.use { scene ->
            val (top, bottom) = scene.settle().thumb()
            result = (if (top < 0) -1 else bottom - top) to top
        }
        return result
    }

    /**
     * Frames until the thumb has held its length for a while: the rows are
     * measured in a slice of each frame's time, so how many frames that takes
     * depends on the machine. An estimate that never comes right holds still
     * too, at the wrong length, which is what the assertions then catch.
     */
    private fun Scene.settle(): BufferedImage {
        var frame = frames(5)
        var last = -1
        var still = 0
        repeat(900) {
            val (top, bottom) = frame.thumb()
            val length = if (top < 0) -1 else bottom - top
            still = if (length == last) still + 1 else 0
            last = length
            // A second's stillness: the first few rows measured can leave a
            // thumb at its shortest for a while before it starts to grow.
            if (still >= 60) return frame
            frame = frame()
        }
        return frame
    }

    private fun BufferedImage.thumb(): Pair<Int, Int> {
        var first = -1
        var last = -1
        for (y in 0 until height) {
            if ((width - 20 until width).any { x -> (getRGB(x, y) and 0xFFFFFF) != 0xFFFFFF }) {
                if (first < 0) first = y
                last = y
            }
        }
        return first to last
    }

    private companion object {
        const val Width = 200
        const val Height = 800
    }
}

@Composable
private fun MeasuredList(heights: List<Int>, measured: Boolean, onState: (LazyListState) -> Unit) {
    val state = if (measured) rememberScrollbarListState() else rememberLazyListState()
    onState(state)
    Box(Modifier.fillMaxSize().background(Color.White)) {
        LazyColumn(state = state, modifier = Modifier.fillMaxSize().scrollbar(state, alwaysVisible = true)) {
            // Keyed, and something in each, as a page of cards is.
            items(heights.size, key = { "row$it" }) {
                Column(Modifier.fillMaxWidth()) {
                    Box(Modifier.fillMaxWidth().height(heights[it].dp))
                }
            }
        }
    }
}

/** The same rows in a plain column: a scroll state knows its length exactly. */
@Composable
private fun ExactColumn(heights: List<Int>) {
    val scroll = rememberScrollState()
    Box(Modifier.fillMaxSize().background(Color.White)) {
        Column(Modifier.fillMaxSize().scrollbar(scroll, alwaysVisible = true).verticalScroll(scroll)) {
            for (h in heights) Box(Modifier.fillMaxWidth().height(h.dp))
        }
    }
}
