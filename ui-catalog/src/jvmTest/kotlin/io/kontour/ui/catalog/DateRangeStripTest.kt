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

    /**
     * A cap crossing into another week **travels** there, on both axes.
     *
     * The head used to arrive by growing out of one edge of its new cell — a
     * `scaleX` from nothing, which is a wipe, and reported as one: it should *move*
     * between the two dates with the pull a range slider's thumb has. Within a row
     * that move is invisible to a test, because everything behind the cap is band
     * and the travel happens over ink that is already there. Across a row boundary
     * it is not: the cap comes from the week it is leaving, so for as long as the
     * spring is running its ink is *outside its own row*, over days the range has
     * nothing to do with.
     *
     * That is what this counts. The anchor is the 10th and the finger ends on the
     * 9th — the last cell of the week above — so the range is two days and the whole
     * of the row below is empty. Any tint there is the cap on its way.
     *
     * The control is the same region once the spring has settled: the cap is on its
     * own cell, the row below is clean again, and a cap that simply drew itself one
     * row down would fail that.
     */
    @Test
    fun aCapCrossingWeeksTravelsThroughTheRowItLeaves() {
        var start by mutableStateOf<LocalDate?>(null)
        var end by mutableStateOf<LocalDate?>(null)
        var bounds = Rect.Zero
        var travelling = 0
        var settled = 0
        var baseline = 0

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
            val clean = scene.frames(6)
            assertTrue(bounds.width > 0f, "the picker never reported a size")

            // Out along the third week first, stepped so the gesture's own slop does
            // not swallow the anchor.
            scene.drag(
                from = cell(bounds, 10),
                to = cell(bounds, 14),
                steps = 20,
                release = false,
            )
            val along = scene.frames(20)
            // A cap's own colour, taken from a settled one rather than named: the
            // endpoints are drawn in the primary and the band between them in a
            // tint, and it is the *cap* this has to follow.
            val cap = along.getRGB(capSample(bounds, 14).x.toInt(), capSample(bounds, 14).y.toInt())
            // The cap's colour is the *content* colour, which is also the day
            // numbers' — a near-black primary is the point of this design system, not
            // an accident. So the digits in that week are a constant baseline to
            // measure against rather than something to tell apart pixel by pixel.
            baseline = clean.capBelowTheThirdWeek(bounds, cap)

            // And one jump onto the 9th: a single crossing, into the week above.
            scene.move(cell(bounds, 9))
            repeat(TravelFrames) {
                travelling = maxOf(travelling, scene.frame().capBelowTheThirdWeek(bounds, cap))
            }
            settled = scene.frames(40).capBelowTheThirdWeek(bounds, cap)
            scene.release(cell(bounds, 9))
        }

        assertTrue(
            travelling - baseline > Substantial,
            "the week below the cap held ${travelling}px of the cap's own colour while " +
                "it was crossing into the week above, against ${baseline}px of day " +
                "numbers there at rest. It should hold a good part of a cell more than " +
                "that: the cap comes from the week it is leaving, so it is briefly " +
                "outside its own row. The baseline alone is a cap that appeared in place",
        )
        assertTrue(
            settled <= baseline,
            "once settled, the week below held ${settled}px against a baseline of " +
                "${baseline}px — the cap has arrived and has no business outside its " +
                "own cell",
        )
    }

    /** Inside a cap and clear of the digit, which is dark on it. */
    private fun capSample(bounds: Rect, day: Int): Offset =
        cell(bounds, day) + Offset(0f, cellSize(bounds) / 3f)

    /**
     * How much of the cap's own colour is in the third week, outside the range.
     *
     * The range is the 9th and the 10th. The 10th is the first cell of that week and
     * is skipped; the 11th to the 16th are empty, so the cap's colour there is the
     * cap on its way and nothing else — the band's tint is a different colour, and
     * this does not count it.
     */
    private fun java.awt.image.BufferedImage.capBelowTheThirdWeek(bounds: Rect, cap: Int): Int {
        val size = cellSize(bounds)
        val gridTop = bounds.bottom - Rows * size
        val top = (gridTop + 2 * size).toInt() + 2
        var found = 0
        for (y in top until (top + (size * 0.4f).toInt())) {
            for (x in (bounds.left + size).toInt() until (bounds.left + 7 * size).toInt() - 1) {
                if (getRGB(x, y) == cap) found++
            }
        }
        return found
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

        /** Long enough for a snappy spring to be somewhere other than home. */
        const val TravelFrames = 8

        /**
         * More tint than an antialiased edge and far less than a cell.
         *
         * A cell is a hundred pixels across in this scene, so a cap a third of a
         * cell out of its row puts thousands of pixels below it. A thousand is
         * plainly the cap and plainly not a rounding error.
         */
        const val Substantial = 1_000
    }
}
