package io.kontour.ui.components.display

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The arc never vanishes, and never runs backwards.
 *
 * Reported as "it shrinks and reappears". The cause was that `drawArc` sweeps
 * *clockwise from* `startAngle`, so pinning `startAngle` to the rotation pinned
 * the tail and left the head at `rotation + sweep` — retreating for the whole
 * shrinking half of every cycle.
 *
 * A screenshot cannot see any of this. A golden is one frame, and one frame of a
 * spinner running backwards looks exactly like one frame of a spinner running
 * forwards.
 */
class SpinnerMotionTest {

    /** Enough of them to cover several full cycles of both periods. */
    private val samples = (0..4000).map { it * 5L }

    private fun rotation(ms: Long): Float =
        360f * (ms % SpinnerDefaults.RotationMillis) / SpinnerDefaults.RotationMillis

    private fun sweep(ms: Long): Float =
        spinnerSweep((ms % SpinnerDefaults.BreatheMillis).toFloat() / SpinnerDefaults.BreatheMillis)

    @Test
    fun theArcIsNeverAPointAndNeverAClosedRing() {
        for (ms in samples) {
            val s = sweep(ms)
            // Absolute bounds, not `SpinnerDefaults.MinSweep` — asserting a
            // constant against itself passes however the constant is set, which
            // is exactly the case this is here to fail on.
            assertTrue(
                s >= VisibleFloor,
                "the arc collapsed to $s° at ${ms}ms — at some point in the cycle " +
                    "there is nothing on screen",
            )
            assertTrue(
                s <= ClosedCeiling,
                "the arc closed to $s° at ${ms}ms — a full ring does not read as " +
                    "moving at all",
            )
        }
    }

    /**
     * The head is `rotation`, which is linear by construction. The tail is
     * `rotation − sweep`, and it is the one that can reverse: its velocity is
     * `rotation' − sweep'`, so an amplitude too large for the rotation rate
     * makes the arc's back end walk backwards while its front end advances.
     */
    @Test
    fun bothEndsOfTheArcAlwaysAdvance() {
        var worst = 0f
        var worstAt = 0L
        for (i in 1 until samples.size) {
            val previous = samples[i - 1]
            val now = samples[i]
            val delta = unwrap((rotation(now) - sweep(now)) - (rotation(previous) - sweep(previous)))
            if (delta < worst) {
                worst = delta
                worstAt = now
            }
        }
        assertTrue(
            worst >= -Tolerance,
            "the tail went backwards by ${-worst}° at ${worstAt}ms — the breathe " +
                "amplitude outruns the rotation rate, so the arc eats itself",
        )
    }

    /** Brings a step across the 360° wrap back into `-180..180`. */
    private fun unwrap(degrees: Float): Float = when {
        degrees > 180f -> degrees - 360f
        degrees < -180f -> degrees + 360f
        else -> degrees
    }

    private companion object {
        const val Tolerance = 0.01f

        /** Below this an arc at a normal spinner size is a dot or nothing. */
        const val VisibleFloor = 15f

        /** Above this the gap closes up and the ring stops reading as moving. */
        const val ClosedCeiling = 330f
    }
}
