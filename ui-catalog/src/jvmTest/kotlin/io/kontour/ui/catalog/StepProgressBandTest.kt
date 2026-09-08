package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.display.StepProgress
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The working band keeps its round ends, and an indeterminate row is one bar.
 *
 * Two reports about `StepProgress`, both about a row drawn as segments that
 * should not always look like one.
 *
 * ### Neither of these could have been a golden
 *
 * `ComponentRenderTest` renders every component with `reduceMotion = true`,
 * deliberately, so a golden is a picture of a state rather than of one frame of
 * an animation. `stepprogress-working-*.png` therefore photographs the *static*
 * stub that reduced motion substitutes for the travelling band, and would not
 * move if the band's caps were sliced off with an axe. The band has to be caught
 * in motion, which is what these do.
 */
class StepProgressBandTest {

    /** Dark enough to be ink rather than the antialiasing at its edge. */
    private fun BufferedImage.isInk(x: Int, y: Int): Boolean {
        val p = getRGB(x, y)
        return ((p shr 16 and 0xFF) + (p shr 8 and 0xFF) + (p and 0xFF)) / 3 < 128
    }

    private fun BufferedImage.inkedRows(column: Int, rows: IntRange): Int =
        rows.count { y -> isInk(column, y) }

    private fun BufferedImage.inkedColumns(columns: IntRange, rows: IntRange): List<Int> =
        columns.filter { x -> inkedRows(x, rows) > 0 }

    /** Every inked pixel inside [bounds], which is the row's whole area. */
    private fun BufferedImage.inkArea(bounds: Rect): Int {
        var area = 0
        for (y in bounds.top.toInt() until bounds.bottom.toInt()) {
            for (x in bounds.left.toInt() until bounds.right.toInt()) {
                if (isInk(x, y)) area++
            }
        }
        return area
    }

    @Test
    fun theWorkingBandNeverShowsASquaredOffEnd() {
        var bounds = Rect.Zero
        var cut = 0
        var frames = 0
        var worst = 0

        Scene(width = 500, height = 60) {
            Box(Modifier.fillMaxSize().background(Color.White)) {
                StepProgress(
                    current = 2,
                    total = 5,
                    working = true,
                    // The band is `colour` and the track is `trackColour`. White
                    // on white makes the track invisible, so every dark pixel in
                    // the busy segment belongs to the band and nothing else.
                    colour = Color.Black,
                    trackColour = Color.White,
                    // Taller than the 4dp default, so a round tip and a straight
                    // cut are dozens of rows apart rather than three.
                    height = 12.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp)
                        .reportBounds { bounds = it },
                )
            }
        }.use { scene ->
            scene.frames(4)
            val rows = bounds.top.toInt()..bounds.bottom.toInt()
            val height = rows.last - rows.first

            // Half a gap wider than the busy segment on each side, not narrower.
            //
            // A first draft took a 2px margin *inside* the segment and reported
            // blunt ends of 14 rows out of 24 — which was the band's cap
            // measured two pixels in from its tip rather than at it. A capsule of
            // radius 12 has already risen to 13 rows two pixels along. The window
            // has to contain the band's true ends; the gaps either side carry no
            // ink of their own, and the neighbouring segments stop a full gap
            // away.
            val gapPx = 4f * 2f
            val segmentWidth = (bounds.width - gapPx * 4f) / 5f
            val segLeft = (bounds.left + segmentWidth + gapPx / 2f).toInt()
            val segRight = (bounds.left + 2f * segmentWidth + gapPx * 1.5f).toInt()

            repeat(90) {
                val image = scene.frame()
                val lit = image.inkedColumns(segLeft..segRight, rows)
                if (lit.isEmpty()) return@repeat
                // Only while the band is at least as wide as it is tall.
                //
                // Not a fudge, and worth saying because it looks like one: a
                // capsule narrower than its own height has no room for a round
                // end at all — Skia scales both radii down to fit, so a sliver
                // four pixels wide and twenty-four tall is a rectangle whatever
                // corner radius it was asked for. That is true of this band, of
                // `LinearProgress`, and of every rounded rect, and it is not what
                // was reported. The report is a band at full width with a cap
                // sliced off, and those are the frames counted here.
                if (lit.last() - lit.first() < height) return@repeat
                frames++
                val ends = listOf(lit.first(), lit.last())
                val tallest = ends.maxOf { image.inkedRows(it, rows) }
                worst = maxOf(worst, tallest)
                // Half the band's height is far above a round tip and far below
                // a straight cut, so it needs no tuning.
                if (tallest > height / 2) cut++
            }
        }

        assertTrue(
            frames > 20,
            "only $frames of 90 frames had a band at least as wide as it is tall " +
                "in the busy segment, so this run says nothing about its ends",
        )
        assertEquals(
            0,
            cut,
            "$cut of $frames frames ended the working band with a straight edge " +
                "— the tallest end column carried $worst rows of ink out of a " +
                "band ${bounds.bottom - bounds.top} rows tall. The band was drawn " +
                "at full width and `clipRect` cut whichever cap overhung the " +
                "segment, so it was square exactly while entering or leaving one.",
        )
    }

    /**
     * Runs of ink along the row's middle, one per visible segment.
     *
     * Band and track are the same colour here on purpose: what is counted is the
     * row's *shape*, and a band punching a differently-coloured hole in a segment
     * would split a run without the row having changed.
     */
    private fun rowRuns(current: Int?, reduceMotion: Boolean): Int {
        var bounds = Rect.Zero
        var runs = 0
        Scene(width = 500, height = 60, reduceMotion = reduceMotion) {
            Box(Modifier.fillMaxSize().background(Color.White)) {
                StepProgress(
                    current = current,
                    total = 5,
                    colour = Color.Black,
                    trackColour = Color.Black,
                    height = 12.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp)
                        .reportBounds { bounds = it },
                )
            }
        }.use { scene ->
            val image = scene.frames(6)
            runs = image.runsIn(bounds.center.y.toInt()).size
        }
        return runs
    }

    @Test
    fun aRowWithAKnownStepIsSegmented() {
        // The control. Five segments with four gaps is what a step row *is*, and
        // the melt must not touch it.
        assertEquals(
            5,
            rowRuns(current = 2, reduceMotion = true),
            "a row on a known step should read as five separate segments",
        )
    }

    @Test
    fun anIndeterminateRowIsOneContinuousBar() {
        assertEquals(
            1,
            rowRuns(current = null, reduceMotion = true),
            "an indeterminate row should melt into one continuous bar, but it " +
                "still reads as separate segments. A row whose whole meaning is " +
                "'this has no steps' should not be drawn as steps.",
        )
    }

    @Test
    fun theMeltIsAnimatedRatherThanASnap() {
        // "Animated both ways" — the gaps close on the way into indeterminate
        // and open again on the way out, rather than the row changing shape
        // between two frames.
        var current by mutableStateOf<Int?>(2)
        var bounds = Rect.Zero
        val areas = mutableListOf<Int>()

        Scene(width = 500, height = 60) {
            Box(Modifier.fillMaxSize().background(Color.White)) {
                StepProgress(
                    current = current,
                    total = 5,
                    colour = Color.Black,
                    trackColour = Color.Black,
                    height = 12.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp)
                        .reportBounds { bounds = it },
                )
            }
        }.use { scene ->
            scene.frames(6)
            current = null
            // Ink over the whole row's area, not along one line of it.
            //
            // Two drafts got this wrong in instructive ways. Counting *runs*
            // reported `[5, 1]`: a run count is binary, reading five segments
            // until the gaps are under a pixel and one bar immediately after.
            // Counting ink along the centre line reported `[432, 460]`, because
            // the centre line is the widest row and sees only the gaps closing —
            // which they do early, as soon as the segments overlap by half a gap.
            //
            // What takes the rest of the animation is the *pinch* at each join
            // filling in as the caps slide further into one another, and that
            // happens away from the centre line. Area sees all of it.
            repeat(40) { areas += scene.frame().inkArea(bounds) }
        }

        // A melted row is very nearly the most ink the row can carry — every gap
        // and every pinch turned into bar.
        //
        // "Very nearly" rather than exactly, and the slack is measured rather
        // than guessed: settling lands 38px below the peak out of about 10,900,
        // which is 0.35% and is the antialiased edge of a capsule crossing the
        // ink threshold as the shape comes to rest. A whole gap is two orders of
        // magnitude larger than that, so 1% separates "finished melting" from
        // "stopped part way" with nothing in between.
        assertTrue(
            areas.last() > areas.max() * 0.99f,
            "the row never finished melting: it settled at ${areas.last()}px of " +
                "ink, having reached ${areas.max()}px on the way",
        )
        assertTrue(
            areas.distinct().size > 3,
            "the row went straight from segmented to continuous — it took " +
                "${areas.distinct().size} distinct ink areas to get there " +
                "(${areas.distinct().take(6)}…). The melt is supposed to be a " +
                "movement, not a substitution.",
        )
    }
}
