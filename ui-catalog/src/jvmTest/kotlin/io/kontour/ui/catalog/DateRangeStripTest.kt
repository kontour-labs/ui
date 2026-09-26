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
                    onRangeChange = { s, e -> start = s; end = e },
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
                    onRangeChange = { s, e -> start = s; end = e },
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

    /**
     * Only the head under the finger travels. The one the drag started from stays
     * where it was put down.
     *
     * Both ends of a range being dragged are caps, and both read the one travel the
     * moving end springs on — so on every day crossed, the anchor jumped a cell the
     * way the moving end had come from and sprang home beside it. Reported as the
     * drag "animating both heads".
     *
     * Counted the way [aCapCrossingWeeksTravelsThroughTheRowItLeaves] counts: the
     * cap's own colour in a cell outside the range, next to the anchor on the side a
     * crossing would push it. At rest that cell holds its digit and nothing else;
     * while the moving end crosses a day, an anchor that travelled puts most of a
     * cell of cap there.
     *
     * Both ways round, because the anchor is the `Start` of a range dragged forwards
     * and the `End` of one dragged backwards.
     */
    @Test
    fun onlyTheDraggedHeadTravels() {
        // Forwards along the third week: anchored on the 11th, out to the 14th, then
        // across onto the 15th. A crossing from the left pushes the anchor into the
        // 10th's cell.
        val forwards = anchorDrift(anchor = 11, along = 14, crossTo = 15, beside = 10)
        // Backwards: anchored on the 13th, back to the 11th, then onto the 10th. A
        // crossing from the right pushes the anchor into the 14th's.
        val backwards = anchorDrift(anchor = 13, along = 11, crossTo = 10, beside = 14)

        val travelled = listOf("forwards" to forwards, "backwards" to backwards)
            .filter { (_, drift) -> drift.second - drift.first > Substantial }
        assertTrue(
            travelled.isEmpty(),
            travelled.joinToString("; ") { (name, drift) ->
                "dragged $name, the cell beside the anchor held ${drift.second}px of the " +
                    "cap's colour while the moving end crossed a day, against " +
                    "${drift.first}px of its own digit at rest"
            } + " — the anchor left its cell and travelled with the head being dragged",
        )
    }

    /**
     * The head being dragged never moves against the drag.
     *
     * Reported: "when you drag it across the detent, it snaps back to where it was
     * before animating across. It should just animate across, like the stepped
     * slider." The head was drawn at an animated journey from the last cell *plus*
     * a pull toward the finger. At a crossing the pull flipped from half a cell
     * ahead to half a cell behind while the journey snapped a whole cell back, so
     * the head jumped backwards and then sprang the whole way forward. The stepped
     * slider had the same bug once and its fix is the one used here: the pull is
     * part of where the spring is going, not something added to where it is.
     *
     * Dragged slowly rightwards along the third week, one frame per step, reading
     * the head's right edge in that row each frame. The anchor is in the week above,
     * so the only cap in the row is the head.
     */
    @Test
    fun theDraggedHeadNeverMovesAgainstTheDrag() {
        var start by mutableStateOf<LocalDate?>(null)
        var end by mutableStateOf<LocalDate?>(null)
        var bounds = Rect.Zero
        val edges = mutableListOf<Int>()

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
            scene.drag(from = cell(bounds, 3), to = cell(bounds, 11), steps = 20, release = false)
            val settled = scene.frames(20)
            val sample = capSample(bounds, 11)
            val cap = settled.getRGB(sample.x.toInt(), sample.y.toInt())

            val from = cell(bounds, 11)
            val to = cell(bounds, 14)
            val steps = 60
            for (step in 1..steps) {
                scene.move(from + (to - from) * (step / steps.toFloat()))
                edges += scene.frame().rightmostIn(bounds, week = 2, colour = cap)
            }
            scene.release(to)
        }

        val backwards = edges.zipWithNext().withIndex()
            .filter { (_, pair) -> pair.second < pair.first - 3 }
            .map { (index, pair) -> "frame ${index + 1}: ${pair.first} → ${pair.second}" }
        assertTrue(edges.any { it > 0 }, "the head was never found in the third week")
        assertTrue(
            backwards.isEmpty(),
            "dragged rightwards, the head's right edge moved left: ${backwards.joinToString()} " +
                "— it jumped back at the crossing before springing forward",
        )
    }

    /** The rightmost column holding [colour] in the lower half of [week]'s row. */
    private fun java.awt.image.BufferedImage.rightmostIn(bounds: Rect, week: Int, colour: Int): Int {
        val size = cellSize(bounds)
        val gridTop = bounds.bottom - Rows * size
        val top = (gridTop + (week + 0.7f) * size).toInt()
        val bottom = (gridTop + (week + 0.95f) * size).toInt()
        var rightmost = 0
        for (y in top until bottom) {
            for (x in bounds.left.toInt() until bounds.right.toInt()) {
                if (getRGB(x, y) == colour && x > rightmost) rightmost = x
            }
        }
        return rightmost
    }

    /**
     * The cap's colour in [beside]'s cell at rest, and the most of it seen while the
     * moving end crosses from [along] to [crossTo], with the drag anchored on
     * [anchor]. All four days are in the third week of August 2026.
     */
    private fun anchorDrift(anchor: Int, along: Int, crossTo: Int, beside: Int): Pair<Int, Int> {
        var start by mutableStateOf<LocalDate?>(null)
        var end by mutableStateOf<LocalDate?>(null)
        var bounds = Rect.Zero
        var baseline = 0
        var travelling = 0

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

            scene.drag(from = cell(bounds, anchor), to = cell(bounds, along), steps = 20, release = false)
            val settled = scene.frames(20)
            val cap = settled.getRGB(capSample(bounds, along).x.toInt(), capSample(bounds, along).y.toInt())
            baseline = settled.capIn(bounds, beside, cap)

            // One crossing, in one move.
            scene.move(cell(bounds, crossTo))
            repeat(TravelFrames) {
                travelling = maxOf(travelling, scene.frame().capIn(bounds, beside, cap))
            }
            scene.release(cell(bounds, crossTo))
        }
        return baseline to travelling
    }

    /** How much of [cap]'s colour is in the lower part of [day]'s cell, below its digit. */
    private fun java.awt.image.BufferedImage.capIn(bounds: Rect, day: Int, cap: Int): Int {
        val size = cellSize(bounds)
        val centre = cell(bounds, day)
        var found = 0
        for (y in (centre.y + size * 0.2f).toInt() until (centre.y + size / 2f).toInt() - 2) {
            for (x in (centre.x - size / 2f).toInt() + 2 until (centre.x + size / 2f).toInt() - 2) {
                if (getRGB(x, y) == cap) found++
            }
        }
        return found
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
