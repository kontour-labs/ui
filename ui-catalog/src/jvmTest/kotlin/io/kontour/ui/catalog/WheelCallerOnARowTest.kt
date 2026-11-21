package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import io.kontour.ui.components.datetime.WheelPicker
import java.awt.image.BufferedImage
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A drum whose value its caller sets as it moves, or as it comes to rest, ends
 * on a row, showing that value.
 *
 * Reported for 0.5.6 from Anyways' planner on iOS, a `TimePicker` whose
 * `onValueChangeFinished` set a later time: *"the hour wheel ends up halfway
 * between 6 and 7, not on any row. From then on every value the caller sets
 * lands half a row out the same way."* A value that arrived while the drum was
 * still moving was skipped for good, and a turn to one was counted in whole
 * rows from wherever the drum was, so a drum once between two rows stayed
 * between them. Both wheels, the one that goes round and the one with ends.
 */
class WheelCallerOnARowTest {

    private val hours = (0..23).map { it.toString().padStart(2, '0') }

    private class Run(val scene: Scene, val bounds: () -> Rect, val selected: () -> Int, val set: (Int) -> Unit)

    private fun wheel(infinite: Boolean, onFinished: ((Int) -> Int?)? = null, test: Run.(rest: Float) -> Unit) {
        var selected by mutableIntStateOf(6)
        var bounds = Rect.Zero
        Scene(width = 400, height = 500, density = 3f) {
            Box(Modifier.fillMaxSize().background(Color.White)) {
                WheelPicker(
                    items = hours,
                    selectedIndex = selected,
                    onSelectedIndexChange = { selected = it },
                    itemLabel = { it },
                    infinite = infinite,
                    modifier = Modifier.reportBounds { bounds = it },
                    onSelectedIndexChangeFinished = onFinished?.let { next -> { next(selected)?.let { selected = it } } },
                )
            }
        }.use { scene ->
            val rest = scene.frames(10).centredInk(bounds)
            Run(scene, { bounds }, { selected }, { selected = it }).test(rest)
        }
    }

    private fun Run.assertOnARow(rest: Float, expected: Int, what: String) {
        val ink = scene.frames(90).centredInk(bounds())
        assertTrue(abs(ink - rest) <= 1f, "$what: the drum came to rest ${ink - rest}px off a row")
        assertEquals(expected, selected(), "$what: the drum rests on ${hours[selected()]}, not the ${hours[expected]} it was set to")
    }

    /** Set while a finger still holds the drum part-way between two rows, then let go. */
    private fun setMidDrag(infinite: Boolean) = wheel(infinite) { rest ->
        val from = bounds().alongY(0.5f)
        // A row and a half up, held there.
        val to = Offset(from.x, from.y - 1.5f * bounds().height / 5f)
        scene.drag(from, to, steps = 12, release = false)
        set(15)
        scene.frames(2)
        scene.release(to)
        assertOnARow(rest, 15, if (infinite) "going round" else "with ends")
    }

    @Test fun aValueSetMidDragLandsOnARow() = setMidDrag(infinite = true)

    @Test fun aValueSetMidDragLandsOnARowWithEnds() = setMidDrag(infinite = false)

    /** Thrown, and set later as it comes to rest, as the planner's earliest time was. */
    private fun setAtRest(infinite: Boolean) {
        var asked = -1
        wheel(infinite, onFinished = { at -> (at + 5).coerceAtMost(23).also { asked = it } }) { rest ->
            val from = bounds().alongY(0.5f)
            scene.drag(from, Offset(from.x, from.y + 220f), steps = 4)
            val ink = scene.frames(90).centredInk(bounds())
            assertTrue(asked >= 0, "the drum never said it had come to rest")
            assertTrue(abs(ink - rest) <= 1f, "${if (infinite) "going round" else "with ends"}: the drum came to rest ${ink - rest}px off a row")
            assertEquals(asked, selected(), "the drum rests on ${hours[selected()]}, not the ${hours[asked]} it was set to as it came to rest")
        }
    }

    @Test fun aValueSetAsItComesToRestLandsOnARow() = setAtRest(infinite = true)

    @Test fun aValueSetAsItComesToRestLandsOnARowWithEnds() = setAtRest(infinite = false)

    /** The middle of the run of ink nearest the middle of [within]: the row in the band. */
    private fun BufferedImage.centredInk(within: Rect): Float {
        val background = getRGB(1, 1)
        val inked = (within.top.toInt().coerceAtLeast(0) until within.bottom.toInt().coerceAtMost(height)).filter { y ->
            (within.left.toInt().coerceAtLeast(0) until within.right.toInt().coerceAtMost(width)).any { x ->
                abs((getRGB(x, y) shr 16 and 0xFF) - (background shr 16 and 0xFF)) > 64
            }
        }
        val runs = mutableListOf<IntRange>()
        for (y in inked) {
            val last = runs.lastOrNull()
            if (last != null && y == last.last + 1) runs[runs.lastIndex] = last.first..y else runs += y..y
        }
        val middle = (within.top + within.bottom) / 2f
        val run = runs.minByOrNull { abs((it.first + it.last) / 2f - middle) } ?: return Float.NaN
        return (run.first + run.last) / 2f
    }
}
