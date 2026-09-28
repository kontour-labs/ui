package io.kontour.ui.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The curve a device is drawn with, and the device that was not catered for.
 *
 * The first version of the smoothing table listed every *current* family and let
 * everything else fall to a per-manufacturer number. It was wrong within a
 * fortnight, and reported in those words: *"I'm on a Pixel 11 Pro XL, so you
 * haven't catered for it."* The Pixel 11 is `kodiak`, the list stopped at
 * `comet`, and the roundest corners Android ships were being drawn with the
 * cautious number meant for a Pixel 4.
 *
 * So the table was inverted — a manufacturer's present treatment is the default
 * and the devices that predate it are the ones named — and this is the file that
 * says what that buys. The rule and the tables live in commonMain, away from
 * `Build`, precisely so it can: a `Build.DEVICE` cannot be set in a test, and
 * this repository has no Android test source set.
 *
 * The codenames are real. `docs/pull-device-corners.py` checks every one in the
 * table against Google's own device registry — 37,082 of them — so an invented
 * name fails a gate rather than silently matching nothing.
 */
class DeviceCurvesTest {

    @Test
    fun theDeviceThatWasNotCateredForIsCateredFor() {
        assertEquals(
            TensorEra,
            deviceSmoothingFor("Google", "kodiak"),
            "a Pixel 11 Pro XL is drawn with something other than the corner " +
                "Google has shipped since the Pixel 6",
        )
    }

    /**
     * And so is a phone nobody has heard of yet, which is the actual fix.
     *
     * A list of current devices is a list that is wrong every autumn. Naming the
     * past instead makes the next phone right before it exists — the property
     * that was missing, rather than the row that was missing.
     */
    @Test
    fun aPixelNobodyHasHeardOfTakesTheCurrentCurve() {
        assertEquals(TensorEra, deviceSmoothingFor("Google", "someunreleasedpixel"))
        assertEquals(TensorEra, deviceSmoothingFor("Google", "frankel"), "Pixel 10")
        assertEquals(TensorEra, deviceSmoothingFor("Google", "tegu"), "Pixel 9a")
    }

    @Test
    fun theNamedPastKeepsItsOwnCurve() {
        assertEquals(Snapdragon, deviceSmoothingFor("Google", "walleye"), "Pixel 2")
        assertEquals(Snapdragon, deviceSmoothingFor("Google", "redfin"), "Pixel 5")
        assertEquals(Snapdragon, deviceSmoothingFor("Google", "barbet"), "Pixel 5a")
    }

    @Test
    fun theLookupIgnoresCaseOnBothHalves() {
        assertEquals(TensorEra, deviceSmoothingFor("GOOGLE", "KODIAK"))
        assertEquals(Snapdragon, deviceSmoothingFor("Google", "Walleye"))
    }

    /**
     * A manufacturer with no row gets null, which is the library's own curve.
     *
     * The table can improve a device it names and can never make one worse: null
     * leaves `SquircleShape.DefaultSmoothing` in place, which is precisely what
     * every Android device was drawn with before any of this existed.
     */
    @Test
    fun anUnlistedManufacturerSaysNothing() {
        assertNull(deviceSmoothingFor("Fairphone", "FP5"))
        assertNull(deviceSmoothingFor("", ""))
    }

    /** Samsung is the comparison the whole report was drawn from. */
    @Test
    fun theFlattestAndTheRoundestAreActuallyDifferent() {
        val samsung = deviceSmoothingFor("samsung", "dm3q")
        assertEquals(0.35f, samsung)
        assertEquals(
            true,
            TensorEra > samsung!!,
            "a Pixel is supposed to be the squirclier of the two, which is the " +
                "distinction the table exists to draw",
        )
    }

    private companion object {
        const val TensorEra = 0.62f
        const val Snapdragon = 0.45f
    }
}
