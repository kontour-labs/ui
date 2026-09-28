package io.kontour.ui.sheet

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A sheet's stretch is a function of how far the finger went, not of the frame rate.
 *
 * Reported as *"the slightly different rubber-banding animations between each
 * platform"*, and the sheet owned one of them. [SheetState.stretch] kept a private
 * copy of [io.kontour.ui.interaction.RubberBand]'s arithmetic in the form the
 * primitive was rewritten to get rid of — a **single Euler step** of
 * `do/dt = 1 - o/limit`, evaluated once for the whole of a frame's delta:
 *
 * ```
 * resistance = 1 - |overshoot| / max
 * gained     = by * resistance
 * ```
 *
 * One Euler step is accurate only while the step is small against the limit, and
 * the limit here is a twelfth of the window — about 100px on a phone, which a
 * finger crosses in a couple of frames. So halving the frame interval halves each
 * step's error and the curve comes out different: the same drag, at 120Hz on an
 * iPhone, stretched the sheet further than it did at 60. The closed form
 * [io.kontour.ui.interaction.RubberBand.pull] uses is exact for any delta.
 *
 * ### Why this is a unit test and not a rendered one
 *
 * `Scene`'s frame clock is a private `FrameNanos = 16_000_000L`, so no test in this
 * repository can render at 8.3ms a frame. The claim does not need one: chopping the
 * same travel into a different number of deltas *is* the frame rate, as far as this
 * arithmetic can tell, and that is expressible directly.
 */
class SheetStretchTest {

    /** A twelfth of 1200px, so [SheetState.maxOvershoot] is exactly 100. */
    private fun state(): SheetState = SheetState(
        detents = listOf(SheetDetent.Hidden, SheetDetent.Expanded),
        initialDetent = SheetDetent.Expanded,
        confirmDetentChange = { true },
    ).apply { containerHeight = 1200f }

    private fun stretched(steps: Int, total: Float): Float {
        val state = state()
        repeat(steps) { state.stretch(total / steps) }
        return state.overshoot
    }

    private fun pushed(steps: Int, total: Float): Float {
        val state = state()
        repeat(steps) { state.stretchDown(total / steps) }
        return state.overshoot
    }

    @Test
    fun theSameTravelStretchesTheSameHoweverManyFramesItArrivesIn() {
        val once = stretched(steps = 1, total = Travel)
        val sixty = stretched(steps = 6, total = Travel)
        val at120 = stretched(steps = 12, total = Travel)

        assertEquals(
            once, sixty, Tolerance,
            "${Travel.toInt()}px of finger stretched the sheet ${once}px when it " +
                "arrived in one delta and ${sixty}px in six. The stretch has to be a " +
                "function of how far the finger travelled, not of how that travel was " +
                "chopped into frames",
        )
        assertEquals(
            sixty, at120, Tolerance,
            "the same travel stretched the sheet ${sixty}px at six deltas and " +
                "${at120}px at twelve — which is 60Hz against 120Hz, and the library " +
                "runs at 120 on an iPhone",
        )
    }

    /** The floor end, which is the same band and has to be the same curve. */
    @Test
    fun andTheSameGoingDown() {
        val once = pushed(steps = 1, total = Travel)
        val many = pushed(steps = 12, total = Travel)

        assertTrue(once < 0f, "a downward push left the overshoot at $once, not below zero")
        assertEquals(
            once, many, Tolerance,
            "pushed ${Travel.toInt()}px below its floor the sheet stretched ${once}px " +
                "in one delta and ${many}px in twelve",
        )
    }

    /**
     * It approaches the limit and never arrives, which is what removed the clamp.
     *
     * The Euler form needed a `coerceIn(-max, max)` because a delta the size of the
     * limit took it from nothing to the clamp in one frame — and a boundary that
     * arrives at a hard stop is the rigid boundary again a few pixels further on.
     */
    @Test
    fun oneEnormousDeltaApproachesTheLimitRatherThanClampingAtIt() {
        val state = state()
        val max = state.maxOvershoot
        state.stretch(max * 3f)

        // The Euler form's answer here was the clamp, exactly: resistance is 1
        // while the band is closed, so `gained` was the whole 300px and `coerceIn`
        // caught it. Three limits of travel is 95.02% of the way out.
        assertTrue(
            state.overshoot < max,
            "one delta of ${(max * 3f).toInt()}px stretched the sheet " +
                "${state.overshoot}px against a limit of ${max}px. A delta the size " +
                "of the limit taking the band from nothing to the clamp in one frame " +
                "is the two-state deformation `RubberBand.pull` was rewritten for",
        )
        assertTrue(
            state.overshoot > max * 0.9f,
            "three limits of travel stretched the sheet only ${state.overshoot}px of " +
                "${max}px, so the give is not the give it says",
        )
    }

    /**
     * The sign convention the two frames meet in, which is the one hazard here.
     *
     * [SheetState.overshoot] counts **upward**, because that is the direction a
     * sheet stretches and the layout subtracts it. A scroll delta counts
     * **downward**, because that is the direction a finger drags. `payBackOvershoot`
     * is where the two meet, and it is now one line over a primitive that works in
     * one frame throughout — so the negation going in and coming out is asserted
     * rather than read.
     */
    @Test
    fun payingBackTakesAFingerDeltaAndReturnsOne() {
        val state = state()
        state.stretch(40f)
        val open = state.overshoot
        assertTrue(open > 0f, "an upward pull left the overshoot at $open")

        // A finger travelling *down* closes a stretch that was pulled *up*.
        val paid = state.payBackOvershoot(10f)
        assertEquals(
            10f, paid, Tolerance,
            "a 10px downward delta against a ${open}px upward stretch paid back " +
                "${paid}px, signed the way the finger is",
        )
        assertEquals(
            open - 10f, state.overshoot, Tolerance,
            "the stretch went from ${open}px to ${state.overshoot}px on a 10px payback",
        )

        // And a finger going further up is opening it wider, which is `stretch`'s half.
        assertEquals(
            0f, state.payBackOvershoot(-10f), Tolerance,
            "an upward delta against an upward stretch paid something back, so a " +
                "finger pulling harder would close the gap it is opening",
        )
    }

    /** Both ends, and the same number from either: a band is symmetric. */
    @Test
    fun theTwoEndsGiveAlike() {
        val up = abs(stretched(steps = 6, total = Travel))
        val down = abs(pushed(steps = 6, total = Travel))
        assertEquals(
            up, down, Tolerance,
            "the same travel gave ${up}px upward and ${down}px downward",
        )
    }

    private companion object {
        /** Comfortably past the 100px limit, where the Euler error is largest. */
        const val Travel = 120f
        const val Tolerance = 1e-2f
    }
}
