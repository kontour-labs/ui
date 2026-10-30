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
 * across the seam where one row hands the leg to the next. And
 * `TimelineMotion.Arrow`, one at a time: both travel the whole leg, and grow
 * in and fade out at its stops rather than appearing and vanishing there.
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
     * One arrow at a time, over the whole leg: halfway along it, the arrow is
     * seen near the stop behind as well as near the one ahead. Asked for as
     * *"they should travel the entire leg, not just half of it"*: they ran only
     * along the part still to cover.
     */
    @Test
    fun oneArrowTravelsTheWholeLeg() {
        trip(TimelineMotion.Arrow, reduceMotion = false, progress = Halfway) { scene, leg ->
            val seen = (0 until LoopFrames).map { runs(arms(scene.frame(), leg)) }
            assertTrue(seen.all { it.size <= 1 }, "more than one arrow at a time: ${seen.filter { it.size > 1 }}")
            val at = seen.flatten().map { (it.first + it.last) / 2 - leg.first }
            val span = leg.last - leg.first
            assertTrue(at.isNotEmpty(), "no arrow was drawn along the leg")
            assertTrue(at.min() < span * 0.3, "the arrow never came near the stop behind: nearest ${at.min()} of $span px")
            assertTrue(at.max() > span * 0.7, "the arrow never came near the stop ahead: furthest ${at.max()} of $span px")
        }
    }

    /** The run of them, halfway along, covers the half already travelled too. */
    @Test
    fun theRunOfArrowsCoversTheLegTravelledToo() {
        trip(TimelineMotion.Arrows, reduceMotion = false, progress = Halfway) { scene, leg ->
            val span = leg.last - leg.first
            val behind = (0 until LoopFrames).flatMap { arms(scene.frame(), leg) }.filter { it - leg.first < span * 0.4 }
            assertTrue(behind.isNotEmpty(), "halfway along the leg, no arrow ran along the half already travelled")
        }
    }

    /**
     * An arrow grows in at the stop behind and fades out at the one ahead: its
     * weight, the red beside the rail, never jumps from one frame to the next,
     * and is slight where it is nearest either stop. Asked for as *"so it
     * doesn't appear, then disappear"*.
     */
    @Test
    fun anArrowGrowsInAndFadesOutAtTheStops() {
        trip(TimelineMotion.Arrow, reduceMotion = false, progress = Halfway) { scene, leg ->
            val weights = ArrayList<Float>()
            val places = ArrayList<Float>()
            repeat(LoopFrames * 2) {
                val image = scene.frame()
                var weight = 0f
                var moment = 0f
                for (y in leg) for (dx in BesideRail) for (x in listOf(RailX - dx, RailX + dx)) {
                    val p = image.getRGB(x, y)
                    val red = ((p shr 16 and 0xFF) - (p shr 8 and 0xFF)).coerceAtLeast(0).toFloat()
                    weight += red
                    moment += red * (y - leg.first)
                }
                weights += weight
                places += if (weight > 0f) moment / weight else Float.NaN
            }
            val most = weights.max()
            assertTrue(most > 0f, "no arrow was drawn along the leg")
            val jumps = weights.zipWithNext { a, b -> abs(b - a) }
            assertTrue(
                jumps.max() < most * 0.5f,
                "an arrow's weight jumped by ${jumps.max()} of its most, $most, between two frames: it appeared or vanished",
            )
            val span = (leg.last - leg.first).toFloat()
            val atStops = weights.indices.filter { !places[it].isNaN() && (places[it] < span * 0.06f || places[it] > span * 0.94f) }
            assertTrue(atStops.isNotEmpty(), "the arrow was never seen near a stop")
            assertTrue(
                atStops.all { weights[it] < most * 0.6f },
                "an arrow near a stop was drawn at ${atStops.maxOf { weights[it] } / most} of its full weight",
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

        /** Halfway along the leg. */
        const val Halfway = 0.5f

        /** Columns either side of the rail, past its 2dp, where only an arrow's arms are. */
        val BesideRail = 4..12

        /** One loop of the rail's motion at 16ms a frame, and a little over. */
        const val LoopFrames = 96

        /** Long enough for the arrows to move a few pixels, short of a whole spacing. */
        const val StepFrames = 8

        const val MaxShift = 12

        val TallRow = 120.dp
    }
}
