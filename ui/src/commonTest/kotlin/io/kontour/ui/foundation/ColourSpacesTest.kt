package io.kontour.ui.foundation

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The conversions a colour picker is built on, and the edges they fall off.
 *
 * Round-trips are the bulk of it, and they are worth more than they look: a sign
 * error or a wrong sixth of the wheel survives a spot check on red and green and
 * fails on cyan, so the sweep goes all the way round rather than testing the
 * three primaries.
 */
class ColourSpacesTest {

    /**
     * Every hue survives a round trip, **compared as colours**.
     *
     * Not as three numbers, and that distinction is the test rather than a
     * detail of it. A `Color` holds eight bits per channel, so a trip through
     * one quantises — and at low chroma a whole channel step is several degrees
     * of hue. `Hsv(110, 0.2, 0.2)` comes back four degrees off, which is not an
     * error in the conversion: it is the only hue those eight-bit channels can
     * still express, and asserting otherwise would be asserting that a `Color`
     * has more precision than it has.
     *
     * What the picker actually needs is that a colour put through the cylinder
     * and back is the same colour, which is what this asks. The hue's own
     * accuracy is the test below, where there is chroma enough to carry it.
     */
    @Test
    fun everyColourSurvivesARoundTripThroughHsv() {
        var worst = 0f
        var worstAt = ""
        for (degrees in 0 until 360 step 5) {
            for (saturation in listOf(0.2f, 0.6f, 1f)) {
                for (value in listOf(0.2f, 0.6f, 1f)) {
                    val start = Hsv(degrees.toFloat(), saturation, value).toColour()
                    val drift = distance(start, start.toHsv().toColour())
                    if (drift > worst) {
                        worst = drift
                        worstAt = "hue $degrees, s $saturation, v $value"
                    }
                }
            }
        }
        assertTrue(
            worst <= ChannelStep,
            "HSV did not survive a round trip: the worst channel drifted $worst " +
                "at $worstAt, against one eight-bit step of $ChannelStep. A whole " +
                "sixth of the wheel that is wrong shows up here and passes a spot " +
                "check on red.",
        )
    }

    @Test
    fun everyColourSurvivesARoundTripThroughHsl() {
        var worst = 0f
        var worstAt = ""
        for (degrees in 0 until 360 step 5) {
            for (saturation in listOf(0.2f, 0.6f, 1f)) {
                for (lightness in listOf(0.2f, 0.5f, 0.8f)) {
                    val start = Hsl(degrees.toFloat(), saturation, lightness).toColour()
                    val drift = distance(start, start.toHsl().toColour())
                    if (drift > worst) {
                        worst = drift
                        worstAt = "hue $degrees, s $saturation, l $lightness"
                    }
                }
            }
        }
        assertTrue(
            worst <= ChannelStep,
            "HSL did not survive a round trip: the worst channel drifted $worst " +
                "at $worstAt, against one eight-bit step of $ChannelStep.",
        )
    }

    /**
     * At full chroma the hue itself round-trips, to within a degree.
     *
     * The other half of the pair above. With saturation and value at 1 there are
     * 255 steps carrying the hue rather than 50, so the quantisation that makes
     * a low-chroma hue imprecise has nothing to hide behind — a sixth of the
     * wheel written the wrong way round fails here by sixty degrees.
     */
    @Test
    fun aFullyChromaticHueRoundTripsToWithinADegree() {
        var worst = 0f
        var worstHue = 0f
        for (degrees in 0 until 360) {
            val hue = degrees.toFloat()
            val back = Hsv(hue, 1f, 1f).toColour().toHsv().hue
            val drift = hueDistance(hue, back)
            if (drift > worst) {
                worst = drift
                worstHue = hue
            }
        }
        assertTrue(
            worst < 1f,
            "a fully saturated hue came back ${worst} degrees off, worst at " +
                "$worstHue. At full chroma there is nothing for a wrong sixth of " +
                "the wheel to hide behind.",
        )
    }

    /**
     * The two cylinders disagree, and that is the point of having both.
     *
     * A pure hue is `value = 1, saturation = 1` in HSV and `lightness = 0.5,
     * saturation = 1` in HSL. Anything that returned the same three numbers for
     * both would be one conversion wearing two names.
     */
    @Test
    fun hsvAndHslAreNotTheSameNumbers() {
        val pure = Color(1f, 0f, 0f)
        assertEquals(1f, pure.toHsv().value, Tolerance)
        assertEquals(0.5f, pure.toHsl().lightness, Tolerance)
    }

    /**
     * A grey has no hue, and both ends of the scale are unsaturated.
     *
     * White is the one that catches a missing guard: HSL's saturation divides by
     * the room left either side of the lightness, which is zero at both ends.
     */
    @Test
    fun greysHaveNoHueAndNoSaturation() {
        for (grey in listOf(Color.Black, Color(0.5f, 0.5f, 0.5f), Color.White)) {
            val hsv = grey.toHsv()
            val hsl = grey.toHsl()
            assertEquals(0f, hsv.saturation, Tolerance, "$grey reported HSV saturation")
            assertEquals(0f, hsl.saturation, Tolerance, "$grey reported HSL saturation")
            assertEquals(0f, hsv.hue, Tolerance, "$grey reported a hue")
        }
    }

    /**
     * A hue just anticlockwise of red comes back as 359, not as -1.
     *
     * The wrap is the one arithmetic bug in a hue conversion that a round trip
     * cannot catch, because it is symmetric: both directions agree on the wrong
     * answer. A slider driven by a negative hue jumps to the far end.
     */
    @Test
    fun theHueJustBelowRedIsNearThreeSixty() {
        val hue = Color(1f, 0f, 0.02f).toHsv().hue
        assertTrue(
            hue > 350f,
            "a colour a shade anticlockwise of red reported a hue of $hue. " +
                "Negative or near-zero means the wheel does not wrap.",
        )
    }

    @Test
    fun hexRoundTripsAndTakesEveryShorthand() {
        assertEquals("#3355AA", Color(0x33 / 255f, 0x55 / 255f, 0xAA / 255f).toHex())
        assertEquals(
            "#3355AA80",
            Color(0x33 / 255f, 0x55 / 255f, 0xAA / 255f, 0x80 / 255f).toHex(includeAlpha = true),
        )
        // The shorthand doubles each digit rather than padding.
        assertEquals(colourFromHex("#00FF88"), colourFromHex("#0f8"))
        assertEquals(colourFromHex("#3355AA"), colourFromHex("3355aa"))
        assertEquals(colourFromHex("#3355AA"), colourFromHex("  #3355AA  "))
        assertEquals(1f, colourFromHex("#3355AA")!!.alpha, Tolerance)
        assertEquals(0f, colourFromHex("#3355AA00")!!.alpha, Tolerance)
    }

    /**
     * Half-typed input is not an error, and not black.
     *
     * This sits behind a text field somebody is still typing into. A parse that
     * threw would need a try at every call site; one that fell back to black
     * would repaint the picker on the way to every colour starting with a zero.
     */
    @Test
    fun anythingThatIsNotAColourIsNull() {
        for (bad in listOf("", "#", "#3", "#33", "#33559", "#zzz", "#3355AAFF00", "rebeccapurple")) {
            assertNull(colourFromHex(bad), "`$bad` parsed as a colour")
        }
    }

    /** The largest single-channel difference between two colours. */
    private fun distance(a: Color, b: Color): Float = maxOf(
        abs(a.red - b.red),
        abs(a.green - b.green),
        abs(a.blue - b.blue),
        abs(a.alpha - b.alpha),
    )

    private fun hueDistance(a: Float, b: Float): Float {
        val gap = abs(a - b) % 360f
        return minOf(gap, 360f - gap)
    }

    private companion object {
        /** A shade under half a step of an 8-bit channel. */
        const val Tolerance = 0.002f

        /**
         * One step of an eight-bit channel, which is what a `Color` holds.
         *
         * The floor on anything that goes through one, and the reason the
         * round-trip tests compare colours rather than the three numbers that
         * made them.
         */
        const val ChannelStep = 1f / 255f + 1e-5f
    }
}
