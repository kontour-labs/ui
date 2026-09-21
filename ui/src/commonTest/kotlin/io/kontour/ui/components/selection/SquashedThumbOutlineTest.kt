package io.kontour.ui.components.selection

import androidx.compose.ui.geometry.Offset
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A squashed thumb's outline leaves its cap **gently**, not merely continuously.
 *
 * *"It needs to be G2 continuous."* The shape before this one already was, by the
 * letter: it matched the cap's curvature exactly at the join. What it then did
 * was leave it at a rate nothing could follow — measured sample by sample, the
 * curvature went 1.53, 2.26, 3.14, 4.02 times the cap's own over the first few
 * percent of the outline. Continuous at a point and a kink to look at.
 *
 * So the ease begins **before** the join and runs on a smootherstep, whose first
 * and second derivatives are zero at both ends — see `SquashEase`. Measured the
 * same way:
 *
 * ```
 * ease at the join   1.53  2.26  3.14  4.02  4.62  4.72
 * ease before it     1.04  1.03  1.00  0.97  0.95  0.94
 * ```
 *
 * The second row is the curvature *staying on the cap's own value* for the first
 * eight samples and only then rising. That is what the eye reads as a smooth
 * join, and it is what this file asserts: not that the curvature matches at a
 * point, which is cheap, but that it **leaves** the match slowly.
 */
class SquashedThumbOutlineTest {

    /** The ease starts on the cap, so the first sample is on the cap's own ellipse. */
    @Test
    fun theEaseBeginsOnTheCapItself() {
        Cases.forEach { (name, geometry) ->
            val (cap, far, half) = geometry
            val start = squashedOutlinePoint(cap, far, half, 0)
            val onIt = (start.x / cap) * (start.x / cap) + (start.y / half) * (start.y / half)
            assertEquals(
                1f, onIt, 1e-4f,
                "$name: the ease begins at ${start.x}, ${start.y}, which is off the " +
                    "cap's own ellipse. Everything before that sample is drawn as the " +
                    "cap's arc, so a sample that does not sit on it is a step in the " +
                    "outline itself",
            )
            assertTrue(
                start.x > 0f,
                "$name: the ease begins at x=${start.x}, on the free side of the " +
                    "cap's centre. It has to begin on the **wall** side or there is " +
                    "no room for the curvature to ramp before the join",
            )
        }
    }

    /** And ends as the shallow ellipse, so the free end is not a point. */
    @Test
    fun theFreeSideEndsExactlyAsDeepAsItWasAsked() {
        Cases.forEach { (name, geometry) ->
            val (cap, far, half) = geometry
            val tip = squashedOutlinePoint(cap, far, half, SquashSteps)
            assertEquals(
                -far, tip.x, Tolerance,
                "$name: the free tip reached ${tip.x}, not ${-far}",
            )
            assertEquals(0f, tip.y, Tolerance, "$name: the free tip sits ${tip.y} off the centre")
        }
    }

    /**
     * The curvature leaves the cap's own value far more slowly than it later moves.
     *
     * Which is the G2 claim as something that can fail. The first step measures
     * 0.07 of the largest step the curvature later takes; with the ease beginning
     * at the join instead it measures 0.82 — nearly all of it, immediately.
     */
    @Test
    fun theCurvatureLeavesTheCapWithoutAStep() {
        Cases.forEach { (name, geometry) ->
            val (cap, far, half) = geometry
            val curvatures = (1 until SquashSteps).map { curvatureAt(cap, far, half, it) }
            val steps = curvatures.zipWithNext { a, b -> b - a }
            val first = abs(steps.first())
            val largest = steps.maxOf { abs(it) }

            assertTrue(
                first <= largest * MaxFirstStep,
                "$name: the curvature's first step off the cap is $first against a " +
                    "largest of $largest — ${first / largest} of it. An ease that " +
                    "begins at the join reads 0.82 there, and that is the kink",
            )

            // Against the cap's **own arc at the same three angles**, so the
            // discrete reading's bias is on both sides of the comparison and
            // cancels: what is left is the outline's real departure from it.
            for (i in 1..3) {
                val ratio = curvatureAt(cap, far, half, i) / capCurvatureAt(cap, far, half, i)
                assertTrue(
                    abs(ratio - 1f) <= NearTheCap,
                    "$name: at sample $i the outline curves $ratio times as hard as " +
                        "the cap's own arc does there. The ease begins on the cap, so " +
                        "the first samples have to still be on it — an ease that " +
                        "begins at the join reads 1.53 here",
                )
            }
        }
    }

    /**
     * And the outline never doubles back, which is what the ease's length is for.
     *
     * A weight that holds the cap's radius too long reaches further out at the
     * shoulder than it does at the tip and has to come back, which draws a waist
     * in the free end. An ease beginning exactly at the join does it.
     */
    @Test
    fun theOutlineNeverDoublesBackOnItself() {
        Cases.forEach { (name, geometry) ->
            val (cap, far, half) = geometry
            var previous = Float.MAX_VALUE
            for (i in 0..SquashSteps) {
                val x = squashedOutlinePoint(cap, far, half, i).x
                assertTrue(
                    x <= previous + Tolerance,
                    "$name: sample $i sits at $x against $previous the sample before, " +
                        "so the outline comes back on itself",
                )
                previous = x
            }
        }
    }

    /**
     * With nothing squashed it is the circle it rests as, to the last decimal.
     *
     * The branch above this one hands over at exactly `width == height`, so the
     * two have to agree there or a thumb would jump as it crossed.
     */
    @Test
    fun anUnsquashedThumbIsExactlyItsOwnCircle() {
        val r = 12f
        for (i in 0..SquashSteps) {
            val point = squashedOutlinePoint(cap = r, far = r, half = r, i = i)
            assertEquals(
                r, hypot(point.x, point.y), 1e-2f,
                "sample $i sits ${point.x}, ${point.y} — off the circle of radius $r",
            )
        }
    }

    /** The circumradius of three consecutive samples, which is the curvature there. */
    private fun curvatureAt(cap: Float, far: Float, half: Float, i: Int): Float {
        val a = squashedOutlinePoint(cap, far, half, i - 1)
        val b = squashedOutlinePoint(cap, far, half, i)
        val c = squashedOutlinePoint(cap, far, half, i + 1)
        val area = abs((b.x - a.x) * (c.y - a.y) - (c.x - a.x) * (b.y - a.y)) / 2f
        if (area == 0f) return 0f
        return 4f * area / (distance(a, b) * distance(b, c) * distance(a, c))
    }

    /** The same reading, taken of the cap's own arc at the same three angles. */
    private fun capCurvatureAt(cap: Float, far: Float, half: Float, i: Int): Float {
        fun onCap(j: Int): Offset {
            val point = squashedOutlinePoint(cap, far, half, j)
            // `y` is `half · sin φ` whatever the ease has done to the width, so
            // the angle comes back out of it.
            val sin = (point.y / half).coerceIn(-1f, 1f)
            return Offset(cap * sqrt(1f - sin * sin), half * sin)
        }
        val a = onCap(i - 1)
        val b = onCap(i)
        val c = onCap(i + 1)
        val area = abs((b.x - a.x) * (c.y - a.y) - (c.x - a.x) * (b.y - a.y)) / 2f
        if (area == 0f) return 0f
        return 4f * area / (distance(a, b) * distance(b, c) * distance(a, c))
    }

    private fun distance(a: Offset, b: Offset) = hypot(b.x - a.x, b.y - a.y)

    private companion object {
        /** Measured at 0.07; an ease beginning at the join reads 0.82. */
        const val MaxFirstStep = 0.25f

        /** Measured at 0.035 over the first three samples; at the join it is 0.53. */
        const val NearTheCap = 0.06f
        const val Tolerance = 1e-3f

        val Cases = listOf(
            "switch 18x24" to Triple(12f, 6f, 12f),
            "slider 18x30" to Triple(12f, 6f, 15f),
        )
    }
}
