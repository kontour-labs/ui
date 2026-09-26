package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import io.kontour.ui.components.datetime.DateRangePicker
import java.awt.image.BufferedImage
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The moving end of a dragged range leans toward the finger.
 *
 * Reported: *"in the date range picker, the animation for swiping between dates
 * feels a bit off. I think we should take some inspiration from other animations in
 * the project (like the one in the range slider) and have it so dragging is a bit of
 * a 'detent'"*.
 *
 * The cap sat exactly on a cell boundary and moved a whole cell at a time, because
 * the gesture threw its sub-cell position away on the way to an integer day. The
 * finger is continuous and the thing following it was not, which is the whole of
 * "feels a bit off". `SliderDefaults.DetentPull` is the fraction of the overshoot a
 * range slider's thumb follows a finger by, and it is the same number here.
 *
 * ### What is measured
 *
 * Two drags that select **the same day** and differ only in where in that day's cell
 * the finger stopped: a twentieth of the way in, and nineteen twentieths. A cap that
 * snaps draws identically for both. A cap that leans draws about four tenths of a
 * cell further right in the second — `(0.95 - 0.05) * 0.45`.
 *
 * Read on one scanline a third of a cell below the row's centre, which is inside the
 * fill and clear of the day numbers, and from the right: the band's own right edge
 * is the last ink before the empty cells beyond it.
 */
class CalendarDetentTest {

    @Test
    fun theCapLeansTowardTheFingerWithinADaysCell() {
        val near = bandEdge(withinCell = 0.05f)
        val far = bandEdge(withinCell = 0.95f)

        assertTrue(near > 0 && far > 0, "no band was drawn at all: $near and $far")
        assertTrue(
            far - near > MinimumLean,
            "the band's right edge is at $near with the finger just inside the " +
                "15th's cell and at $far with it almost across — ${far - near}px " +
                "apart, where the cap is supposed to follow the finger across the " +
                "cell. A cap that snaps to the boundary draws both the same.",
        )
    }

    /**
     * Where the band's right edge is, with the finger held [withinCell] of the way
     * across the 15th's cell.
     */
    private fun bandEdge(withinCell: Float): Int {
        var start by mutableStateOf<LocalDate?>(null)
        var end by mutableStateOf<LocalDate?>(null)
        var bounds = Rect.Zero
        var edge = -1

        Scene(width = 700, height = 800) {
            Box(Modifier.fillMaxSize().background(Color.White)) {
                DateRangePicker(
                    start = start,
                    end = end,
                    onRangeChange = { s, e -> start = s; end = e },
                    today = LocalDate(2026, 8, 1),
                    modifier = Modifier.reportBounds { bounds = it },
                )
            }
        }.use { scene ->
            scene.frames(6)
            assertTrue(bounds.width > 0f, "the picker never reported a size")

            val to = cellStart(bounds, 15) + Offset(cellSize(bounds) * withinCell, 0f)
            scene.drag(from = cell(bounds, 10), to = to, steps = 24, release = false)
            // Held: the snappy spring the cap slides on has had long enough.
            val held = scene.frames(24)
            scene.release(to)

            val line = (to.y + cellSize(bounds) / 3f).toInt()
            edge = held.rightmostInk(line, bounds)
        }
        return edge
    }

    /** The last column of ink on [line], scanning in from the picker's right edge. */
    private fun BufferedImage.rightmostInk(line: Int, bounds: Rect): Int {
        for (x in bounds.right.toInt() - 1 downTo bounds.left.toInt()) {
            val rgb = getRGB(x, line)
            val r = rgb shr 16 and 0xFF
            val g = rgb shr 8 and 0xFF
            val b = rgb and 0xFF
            if (r < 245 || g < 245 || b < 245) return x
        }
        return -1
    }

    private fun cellSize(bounds: Rect): Float = bounds.width / 7f

    /** The centre of a day of August 2026, as `DateRangeStripTest` computes it. */
    private fun cell(bounds: Rect, day: Int): Offset {
        val size = cellSize(bounds)
        val gridTop = bounds.bottom - Rows * size
        val index = day - 1 + LeadingBlanks
        return Offset(
            bounds.left + (index % 7 + 0.5f) * size,
            gridTop + (index / 7 + 0.5f) * size,
        )
    }

    /** The leading edge of a day's cell, at the same height as its centre. */
    private fun cellStart(bounds: Rect, day: Int): Offset =
        cell(bounds, day) - Offset(cellSize(bounds) / 2f, 0f)

    private companion object {
        const val LeadingBlanks = 5
        const val Rows = 6

        /**
         * The lean is `0.9 * DetentPull` of a cell across these two drags — about
         * 40px on a 700px picker. Half of that is comfortably past antialiasing and
         * comfortably short of a whole cell, which is the thing a snapping cap would
         * have to move to pass this.
         */
        const val MinimumLean = 20
    }
}
