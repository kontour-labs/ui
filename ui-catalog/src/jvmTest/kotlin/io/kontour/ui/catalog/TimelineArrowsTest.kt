package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.display.TimelineColours
import io.kontour.ui.components.display.TimelineList
import io.kontour.ui.components.display.TimelineMotion
import io.kontour.ui.foundation.Text
import java.awt.image.BufferedImage
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * `TimelineMotion.Arrows`: chevrons along the leg the journey is on, marching
 * towards the next stop, standing still under reduced motion, and spaced evenly
 * across the seam where one row hands the leg to the next.
 *
 * A chevron is wider than the rail, so its arms are the only red a little off
 * the rail's line. That is the column these tests read: anything red there is
 * an arrow, and the band, which is only as wide as the rail, never is.
 */
class TimelineArrowsTest {

    private val red = Color(0xFFD00000)
    private val grey = Color(0xFFB0B0B0)
    private val colours = TimelineColours(node = Color.Black, rail = grey, progress = red)

    @Test
    fun arrowsShowBesideTheRailAndTheBandDoesNot() {
        trip(TimelineMotion.Arrows, reduceMotion = false) { scene, leg ->
            assertTrue(
                (0 until LoopFrames).any { arms(scene.frame(), leg).isNotEmpty() },
                "no arrow was drawn along the leg being travelled",
            )
        }
        trip(TimelineMotion.Band, reduceMotion = false) { scene, leg ->
            assertTrue(
                (0 until LoopFrames).none { arms(scene.frame(), leg).isNotEmpty() },
                "the band drew something wider than the rail",
            )
        }
    }

    /** Down the page, towards the next stop: the arrows' pattern moves down. */
    @Test
    fun theArrowsMarchTowardsTheNextStop() {
        trip(TimelineMotion.Arrows, reduceMotion = false) { scene, leg ->
            val before = scene.frame()
            val after = scene.frames(StepFrames)
            val shift = bestShift(column(before, leg), column(after, leg))
            assertTrue(shift > 0, "the arrows moved $shift px along the leg, not on towards the next stop")
        }
    }

    /** Under reduced motion they stand still, and are still there to say the way. */
    @Test
    fun underReducedMotionTheArrowsStandStill() {
        trip(TimelineMotion.Arrows, reduceMotion = true) { scene, leg ->
            val first = arms(scene.frame(), leg)
            assertTrue(first.isNotEmpty(), "under reduced motion the arrows are gone, and with them the way")
            repeat(3) {
                assertTrue(
                    arms(scene.frames(StepFrames), leg) == first,
                    "under reduced motion the arrows moved",
                )
            }
        }
    }

    /**
     * Each row spaces a whole number of arrows along its half of the leg, so the
     * pattern carries on over the seam: no two arrows bunched there, and no gap.
     */
    @Test
    fun theArrowsAreEvenlySpacedAcrossTheSeam() {
        trip(TimelineMotion.Arrows, reduceMotion = true, progress = 0f + Ahead) { scene, leg ->
            val centres = runs(arms(scene.frame(), leg)).map { (it.first + it.last) / 2 }
            assertTrue(centres.size >= 4, "too few arrows to judge their spacing: $centres")
            val gaps = centres.zipWithNext { a, b -> b - a }
            val typical = gaps.sorted()[gaps.size / 2]
            assertTrue(
                gaps.all { abs(it - typical) <= typical / 3 },
                "the arrows' spacing jumps along the leg, $gaps, where one row hands it to the next",
            )
        }
    }

    /**
     * A two-stop list a little way along its leg, with [leg] the rows of the
     * rail between the two nodes, clear of both.
     */
    private fun trip(
        motion: TimelineMotion,
        reduceMotion: Boolean,
        progress: Float = Ahead,
        check: (Scene, IntRange) -> Unit,
    ) {
        val labels = arrayOfNulls<Rect>(2)
        Scene(width = 360, height = 600, reduceMotion = reduceMotion) {
            Box(Modifier.fillMaxSize().background(Color.White)) {
                TimelineList(progress = progress, progressMotion = motion, colours = colours) {
                    repeat(2) { i ->
                        item {
                            label { Text("Stop $i", Modifier.reportBounds { labels[i] = it }) }
                            // A tall row, so the leg is long enough for several arrows.
                            trailing { Box(Modifier.height(TallRow)) }
                        }
                    }
                }
            }
        }.use { scene ->
            scene.frames(4)
            val nodes = labels.map { it!!.center.y.toInt() }
            check(scene, nodes[0] + ClearPx until nodes[1] - ClearPx)
        }
    }

    /** The rows along [leg] where the column beside the rail is red: an arrow's arm. */
    private fun arms(image: BufferedImage, leg: IntRange): List<Int> =
        leg.filter { isRed(image.getRGB(RailX + ArmProbePx, it)) }

    private fun column(image: BufferedImage, leg: IntRange): List<Boolean> =
        leg.map { isRed(image.getRGB(RailX + ArmProbePx, it)) }

    /** The shift, in pixels down the leg, that best lays [before]'s arrows over [after]'s. */
    private fun bestShift(before: List<Boolean>, after: List<Boolean>): Int =
        (-MaxShift..MaxShift).maxBy { shift ->
            before.indices.count { i -> before[i] && after.getOrNull(i + shift) == true }
        }

    /** Consecutive rows, run together. */
    private fun runs(rows: List<Int>): List<IntRange> {
        val out = ArrayList<IntRange>()
        var start = -1
        var last = -1
        for (y in rows) {
            if (start < 0) start = y else if (y != last + 1) {
                out += start..last
                start = y
            }
            last = y
        }
        if (start >= 0) out += start..last
        return out
    }

    private fun isRed(p: Int) = (p shr 16 and 0xFF) > 160 && (p shr 8 and 0xFF) < 90 && (p and 0xFF) < 90

    private companion object {
        /** At density two: the rail 22dp in, a 12dp node, and 8dp clear round it. */
        const val RailX = 44
        const val ClearPx = 16

        /**
         * 2.5dp off the rail's centre: past the 2dp rail's edge, inside a
         * chevron's 4dp half width.
         */
        const val ArmProbePx = 5

        /** Just past the first stop, so nearly all of the leg is still ahead. */
        const val Ahead = 0.02f

        /** One loop of the rail's motion at 16ms a frame, and a little over. */
        const val LoopFrames = 96

        /** Long enough for the arrows to move a few pixels, short of a whole spacing. */
        const val StepFrames = 8

        const val MaxShift = 12

        val TallRow = 120.dp
    }
}
