package io.kontour.ui.catalog

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A frame's budget is the display's, not sixty a second.
 *
 * For the life of `FrameReadout` the worst-frame colour came from two literals —
 * 167 and 333 tenths of a millisecond, sixty and thirty a second — written when
 * every phone in reach refreshed at sixty. A reader running the gallery on a
 * ProMotion iPhone said they did not think it was adapting to the phone's frame
 * rate, and the instrument they would have used to find out had the answer baked
 * into it: **a solid 16ms is every second frame dropped at 120Hz, and it was
 * coloured green.**
 *
 * This is the arithmetic of the replacement, tested where it can be — the
 * per-platform half is four one-line reads of `UIScreen`, `Display`,
 * `GraphicsEnvironment` and a constant, none of which a JVM test can exercise
 * and each of which says what it does.
 *
 * ### The 60Hz row is the compatibility claim
 *
 * `budgetTenths(60)` has to be exactly 167 — the constant it replaced — or this
 * change moved the goalposts on every display that was being scored correctly
 * already. The rounding is written to make that true rather than approximately
 * true.
 */
class FrameBudgetTest {

    @Test
    fun theBudgetIsTheDisplaysFramePeriod() {
        val budgets = listOf(
            24 to 417,
            30 to 333,
            60 to 167,
            90 to 111,
            120 to 83,
            144 to 69,
        )
        for ((hz, expected) in budgets) {
            assertEquals(
                expected,
                budgetTenths(hz),
                "a frame at ${hz}Hz is 1000/$hz ms; this is the number the " +
                    "worst-frame colour is compared against, and at 60 it has " +
                    "to be exactly the 167 that used to be hardcoded",
            )
        }
    }

    @Test
    fun aRateTheDisplayWillNotGiveFallsBackRatherThanDividingByZero() {
        assertEquals(budgetTenths(FallbackHz), budgetTenths(0))
        assertEquals(budgetTenths(FallbackHz), budgetTenths(-1))
    }

    /**
     * The reading that started this: sixteen and a half milliseconds.
     *
     * Green on a 60Hz display, because every frame arrived. Amber on a 120Hz
     * one, because every *second* frame did — the same measurement, two
     * verdicts, and the old code could only give the first.
     */
    @Test
    fun sixteenMillisecondsMeansDifferentThingsOnDifferentDisplays() {
        assertEquals(
            FrameVerdict.Smooth,
            verdictFor(worstTenths = 165, displayHz = 60),
            "16.5ms on a 60Hz display is every frame arriving",
        )
        assertEquals(
            FrameVerdict.Halved,
            verdictFor(worstTenths = 165, displayHz = 120),
            "16.5ms on a 120Hz display is every *second* frame arriving, and " +
                "reporting it as smooth is the whole defect this replaced — " +
                "the budget there is 8.3ms, not the 16.7ms that used to be a " +
                "literal in FrameReadout",
        )
    }

    @Test
    fun theThreeBandsAreEveryFrameEverySecondFrameAndWorse() {
        val hz = 120
        val one = budgetTenths(hz)
        val two = budgetTenths(hz, frames = 2)
        assertEquals(FrameVerdict.Smooth, verdictFor(one, hz), "one frame at ${hz}Hz is $one tenths")
        assertEquals(FrameVerdict.Halved, verdictFor(one + 1, hz), "a tenth over one frame")
        assertEquals(FrameVerdict.Halved, verdictFor(two, hz), "two frames at ${hz}Hz is $two tenths")
        assertEquals(FrameVerdict.Worse, verdictFor(two + 1, hz), "a tenth over two frames")
    }

    /**
     * Two frames is two frame periods, not twice a rounded one.
     *
     * At sixty those differ — 333 against 334 — and the difference is the
     * rounding of 16.67 being carried through the doubling. This is the row
     * that caught it.
     */
    @Test
    fun twoFramesRoundsOnceRatherThanTwice() {
        assertEquals(333, budgetTenths(60, frames = 2))
        assertEquals(167, budgetTenths(120, frames = 2))
        assertEquals(222, budgetTenths(90, frames = 2))
    }

    /**
     * Scoring a 60Hz display is unchanged, boundary for boundary.
     *
     * The old code read `worst <= 167 -> green`, `worst <= 333 -> amber`, and
     * this has to agree with it at every one of those edges or the change is not
     * the change it claims to be.
     */
    @Test
    fun aSixtyHertzDisplayIsScoredExactlyAsItWasBefore() {
        for ((worst, expected) in listOf(
            166 to FrameVerdict.Smooth,
            167 to FrameVerdict.Smooth,
            168 to FrameVerdict.Halved,
            333 to FrameVerdict.Halved,
            334 to FrameVerdict.Worse,
        )) {
            assertEquals(
                expected,
                verdictFor(worst, displayHz = 60),
                "$worst tenths of a millisecond on a 60Hz display",
            )
        }
    }
}
