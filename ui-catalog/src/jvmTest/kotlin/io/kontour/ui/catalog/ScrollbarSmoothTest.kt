package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.list.scrollbar
import java.awt.image.BufferedImage
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A lazy list's thumb never jumps, however wrong its first guess at the list's
 * length.
 *
 * A lazy list knows only the rows it has laid out, so the scrollbar estimates
 * the rest, and the estimate changes as they come into view. Drawn straight
 * from it, the thumb stepped: reported as one that *will jump to full height
 * once you scroll all the way down* when it started too small, and that *jumps
 * around* as the showcase's "Date and time" page, a dozen cards of every
 * height, is scrolled back up. It now moves only as the content moves, and
 * eases to a new length rather than stepping to it.
 */
class ScrollbarSmoothTest {

    /** One card taller than the window over a run of short rows: the first guess is far too long. */
    @Test
    fun aThumbDraggedToTheEndGrowsIntoItsLengthRatherThanJumping() {
        val heights = listOf(600) + List(60) { 10 }
        Scene(width = Width, height = Height) { UnevenList(heights) { } }.use { scene ->
            scene.frames(5)
            val x = Width - 5f
            // A mouse arrives over the bar, takes the thumb at the top and
            // drags it to the foot of its track.
            scene.move(Offset(x, 30f), PointerType.Mouse)
            scene.frames(4)
            // Every frame from the press to well after letting go.
            val lengths = mutableListOf<Int>()
            fun look(frame: BufferedImage) {
                val (top, bottom) = frame.thumb()
                if (top >= 0) lengths += bottom - top
            }
            look(scene.frame())
            scene.drag(
                from = Offset(x, 30f),
                to = Offset(x, Height - 2f),
                steps = 20,
                release = false,
                pointer = PointerType.Mouse,
                onFrame = { _, frame -> look(frame) },
            )
            scene.release(Offset(x, Height - 2f), PointerType.Mouse)
            repeat(40) { look(scene.frame()) }
            val last = lengths.lastOrNull() ?: -1
            val biggest = lengths.zipWithNext { a, b -> abs(b - a) }.maxOrNull() ?: 0
            assertTrue(last > 0, "the thumb was not drawn")
            assertTrue(biggest <= MaxStep, "the thumb's length jumped by ${biggest}px in a frame on its way to the end: $lengths")
        }
    }

    /** As the "Date and time" page: cards of every height, jumped to the end and scrolled back up. */
    @Test
    fun scrolledBackUpTheThumbRisesWithoutJumping() {
        val heights = listOf(300, 80, 500, 120, 700, 60, 400, 900, 150, 350, 600, 100)
        var list: LazyListState? = null
        Scene(width = Width, height = Height) { UnevenList(heights) { list = it } }.use { scene ->
            scene.frames(3)
            val state = requireNotNull(list)
            // To the end at once, as a drag down the bar or a fling gets there:
            // the cards between are never laid out on the way.
            state.requestScrollToItem(heights.lastIndex)
            scene.frames(5)
            var last = scene.frame().thumb().first
            assertTrue(last >= 0, "the thumb was not drawn at the end")
            var biggest = 0
            var backwards = 0
            repeat(400) {
                onComposeUiThread { state.dispatchRawDelta(-40f) }
                val top = scene.frame().thumb().first
                if (top < 0) return@repeat
                // Up the track is a smaller top: a larger one is the thumb going the wrong way.
                if (top > last) backwards = maxOf(backwards, top - last)
                biggest = maxOf(biggest, abs(top - last))
                last = top
            }
            // A few pixels' give as its length eases — a shorter thumb has
            // further to travel — and never a step back down the track.
            assertTrue(backwards <= 5, "scrolling up, the thumb went down its track by ${backwards}px")
            assertTrue(biggest <= MaxStep, "scrolling up 20dp a frame, the thumb jumped ${biggest}px")
            assertTrue(last <= 12, "back at the top, the thumb is ${last}px down its track")
        }
    }

    /** The first and last rows of the thumb's ink at the bar's edge; -1 for none. */
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

        /** The most the thumb may move, or change length, in a frame: a spring's ease, not a step. */
        const val MaxStep = 30
    }
}

@androidx.compose.runtime.Composable
private fun UnevenList(heights: List<Int>, onState: (LazyListState) -> Unit) {
    val state = rememberLazyListState()
    onState(state)
    Box(Modifier.fillMaxSize().background(Color.White)) {
        LazyColumn(state = state, modifier = Modifier.fillMaxSize().scrollbar(state, alwaysVisible = true)) {
            items(heights.size) { Box(Modifier.fillMaxWidth().height(heights[it].dp)) }
        }
    }
}
