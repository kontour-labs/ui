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
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The range stays drawn while the finger is still down.
 *
 * It did not. Every cell the finger had passed over stopped being an endpoint,
 * became `Middle`, and a Middle cell was told to slide like a cap — so it scaled
 * its fill along the track to **nothing** and stayed there until the gesture
 * ended. The band emptied out behind the drag and came back on release, which is
 * the reported "they revert to the background colour until the finger lifts",
 * and the same cause as "it animates each date individually rather than as one
 * continuous strip": fourteen cells each running their own spring to zero is not
 * a strip.
 *
 * Only a *cap* is an edge that is moving. Everything behind it is band, and band
 * is drawn.
 *
 * ### Sampled below the digit, not at the cell's centre
 *
 * A day number is dark on the tint, so the middle of a cell is the one place in
 * it that is the wrong colour to ask about. The sample is a third of a cell
 * lower, which is inside the fill and clear of the glyph.
 */
class DateRangeStripTest {

    @Test
    fun theBandBehindTheFingerStaysDrawn() {
        var start by mutableStateOf<LocalDate?>(null)
        var end by mutableStateOf<LocalDate?>(null)
        var bounds = Rect.Zero
        val blank = mutableListOf<Int>()

        Scene(width = 700, height = 800) {
            Box(Modifier.fillMaxSize().background(Color.White)) {
                DateRangePicker(
                    start = start,
                    end = end,
                    onRangeSelected = { s, e -> start = s; end = e },
                    today = LocalDate(2026, 8, 1),
                    modifier = Modifier.reportBounds { bounds = it },
                )
            }
        }.use { scene ->
            scene.frames(6)
            assertTrue(bounds.width > 0f, "the picker never reported a size")

            // 10 to 15 August 2026 is one row of the grid, left to right.
            scene.drag(
                from = cell(bounds, 10),
                to = cell(bounds, 15),
                steps = 24,
                release = false,
            )
            // Still pressed: whatever the interior is doing, it has had long
            // enough to finish doing it.
            val held = scene.frames(24)
            scene.release(cell(bounds, 15))

            for (day in 11..14) {
                val at = cell(bounds, day) + Offset(0f, cellSize(bounds) / 3f)
                if (!held.isRangeTint(at)) blank += day
            }
        }

        assertTrue(
            blank.isEmpty(),
            "with the finger still down on the 15th, August ${blank.joinToString()} " +
                "of the range behind it had no fill left — the band empties out as it " +
                "is dragged and comes back only on release",
        )
    }

    /** Bluer than it is red: the range tint, and nothing else this picker draws. */
    private fun java.awt.image.BufferedImage.isRangeTint(at: Offset): Boolean {
        val rgb = getRGB(at.x.toInt(), at.y.toInt())
        return (rgb and 0xFF) - (rgb shr 16 and 0xFF) > 4
    }

    private fun cellSize(bounds: Rect): Float = bounds.width / 7f

    /**
     * The centre of a day of August 2026, the way `CalendarMonth` lays it out:
     * seven equal columns, every row one cell tall and square, five leading
     * blanks before the 1st and six rows in all.
     */
    private fun cell(bounds: Rect, day: Int): Offset {
        val size = cellSize(bounds)
        val gridTop = bounds.bottom - Rows * size
        val index = day - 1 + LeadingBlanks
        return Offset(
            bounds.left + (index % 7 + 0.5f) * size,
            gridTop + (index / 7 + 0.5f) * size,
        )
    }

    private companion object {
        const val LeadingBlanks = 5
        const val Rows = 6
    }
}
