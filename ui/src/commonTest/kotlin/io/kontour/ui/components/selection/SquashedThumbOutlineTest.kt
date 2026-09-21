package io.kontour.ui.components.selection

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The free side of a squashed thumb leaves the join on the cap's own radius.
 *
 * Which is the whole of *"the join between those two halves needs to be smooth"*,
 * and the one thing a semicircle butted against a half ellipse cannot do. The two
 * share a tangent at the widest row and nothing else: the outline's radius of
 * curvature steps from the cap's straight to the ellipse's, which at a full
 * squash is a factor of four in one pixel and reads as a kink.
 *
 * So the free half is a swept family of ellipses rather than one — see
 * [squashedOutlinePoint] — weighted so that at the join it *is* the cap's own
 * ellipse and at the tip it is the shallow one. Asserted here as arithmetic
 * because that is what it is; what it looks like is `EndStopSquashTest`'s.
 *
 * ### Measured as an osculating radius, from the drawn samples
 *
 * A point `(x, y)` a little way round from a join at `(0, half)` lies on a circle
 * through that join of radius `(x² + Δ²) / 2|Δ|`, with `Δ = y - half`. Read off the
 * **first drawn segment** rather than a limit, so it is the shape the path really
 * has and not the one it approaches.
 *
 * | | switch, 18x24 | slider, 18x30 |
 * |---|---|---|
 * | the cap's own radius | 12.00 | 9.60 |
 * | this shape, first sample | 7.81 | 6.25 |
 * | a plain half ellipse | 3.01 | 2.42 |
 *
 * The same 0.65 and 0.25 of the cap for both, which is the point: the ratio is a
 * property of the curve and not of the thumb it is drawn on.
 *
 * It reads 0.65 rather than 1.00 because the transition is deliberately **short**
 * — see `SquashEase`. The curvature is the cap's *at* the join and the free side
 * is the ellipse a dp later, which is what keeps the shoulder out of the
 * silhouette; a weighting that held the cap's radius further round would read
 * closer to 1 here and worse on a screen.
 */
class SquashedThumbOutlineTest {

    @Test
    fun theFreeSideLeavesTheJoinOnTheCapsOwnRadius() {
        Cases.forEach { (name, geometry) ->
            val (cap, far, half) = geometry
            val own = cap * cap / half
            val measured = joinRadius(cap, far, half)

            assertTrue(
                measured > own * MinJoinShare,
                "$name: the free side leaves the join on a radius of $measured " +
                    "against the cap's own $own. A half ellipse butted onto the cap " +
                    "leaves on ${joinRadius(cap, far, half, plainEllipse = true)}, " +
                    "and that step is the kink this shape exists to remove",
            )
        }
    }

    /** And it still arrives as the shallow ellipse, so the free end is not a point. */
    @Test
    fun theFreeSideEndsExactlyAsDeepAsItWasAsked() {
        Cases.forEach { (name, geometry) ->
            val (cap, far, half) = geometry
            val tip = squashedOutlinePoint(cap, far, half, SquashSteps)
            assertEquals(far, tip.x, Tolerance, "$name: the free tip reached ${tip.x}, not $far")
            assertEquals(0f, tip.y, Tolerance, "$name: the free tip sits ${tip.y} off the centre")

            val join = squashedOutlinePoint(cap, far, half, 0)
            assertEquals(0f, join.x, Tolerance, "$name: the join is ${join.x} off the cap's centre")
            assertEquals(half, join.y, Tolerance, "$name: the join is at ${join.y}, not $half")
        }
    }

    /**
     * And never bulges past the tip on the way, which is what the square is for.
     *
     * A weighting that holds the cap's radius for longer — a smoothstep, or
     * `(1 - sin²θ)` — reaches further out at the shoulder than it does at the tip
     * and has to come back, which draws a waist in the free end. Measured at the
     * deepest squash this library asks for, the first version of this curve
     * bulged 0.04 of the cap past its own tip.
     */
    @Test
    fun theFreeSideNeverBulgesPastItsTip() {
        Cases.forEach { (name, geometry) ->
            val (cap, far, half) = geometry
            var previous = -1f
            for (i in 0..SquashSteps) {
                val reach = squashedOutlinePoint(cap, far, half, i).x
                assertTrue(
                    reach >= previous - Tolerance,
                    "$name: sample $i reaches $reach against $previous the sample " +
                        "before, so the free side comes back on itself",
                )
                previous = reach
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
            val radius = point.x * point.x + point.y * point.y
            assertEquals(
                r * r, radius, 1e-2f,
                "sample $i sits ${point.x}, ${point.y} — off the circle of radius $r",
            )
        }
    }

    /** The osculating radius at the join, read off the first drawn segment. */
    private fun joinRadius(
        cap: Float,
        far: Float,
        half: Float,
        plainEllipse: Boolean = false,
    ): Float {
        val point = squashedOutlinePoint(cap, far, half, 1)
        // What the same sample would be on an ellipse of the free side's depth,
        // which is the shape this replaces.
        val x = if (plainEllipse) far * (point.x / (far + (cap - far) * Ease1)) else point.x
        val drop = abs(point.y - half)
        return (x * x + drop * drop) / (2f * drop)
    }

    private companion object {
        /** `(1 - sin θ)²` at the first sample, for re-deriving the plain ellipse. */
        val Ease1: Float = run {
            val join = squashedOutlinePoint(cap = 1f, far = 0f, half = 1f, i = 1)
            val tip = squashedOutlinePoint(cap = 1f, far = 1f, half = 1f, i = 1)
            join.x / tip.x
        }

        /** Measured at 0.651 on both controls; a plain half ellipse gives 0.251. */
        const val MinJoinShare = 0.5f
        const val Tolerance = 1e-3f

        val Cases = listOf(
            "switch 18x24" to Triple(12f, 6f, 12f),
            "slider 18x30" to Triple(12f, 6f, 15f),
        )
    }
}
