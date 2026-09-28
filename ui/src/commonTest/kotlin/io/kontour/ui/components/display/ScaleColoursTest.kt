package io.kontour.ui.components.display

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Where a dial's colours fall on its scale.
 *
 * Read as the stops a dial draws with: fractions of the scale, ascending, with two at
 * one place for a hard edge. [accent] stands for the dial's own colour, which is what
 * any stretch no band covers takes.
 */
class ScaleColoursTest {

    private val green = Color(0xFF00AA00)
    private val amber = Color(0xFFFFAA00)
    private val red = Color(0xFFDD0000)
    private val accent = Color(0xFF3355FF)

    private fun tachometer(smoothing: Float = 0f) = ScaleColours.bands(smoothing = smoothing) {
        band(from = 0f, colour = green)
        band(from = 6_000f, colour = amber)
        band(from = 8_000f, colour = red)
    }

    /**
     * Hard edges by default, at the values given — in the dial's own units, placed
     * against the range the dial reads.
     */
    @Test
    fun bandsAreHardEdgedAtTheirValuesOnTheDialsOwnScale() {
        assertStops(
            listOf(0f to green, 0.6f to green, 0.6f to amber, 0.8f to amber, 0.8f to red, 1f to red),
            tachometer().stops(0f..10_000f, accent),
        )
        // The same bands on a scale that starts somewhere else land somewhere else:
        // the dial's range is the one the values are read against, and a band from
        // before the scale starts is held at its start.
        assertStops(
            listOf(0f to green, 0.2f to green, 0.2f to amber, 0.6f to amber, 0.6f to red, 1f to red),
            tachometer().stops(5_000f..10_000f, accent),
        )
    }

    @Test
    fun bandsCanBeGivenInAnyOrder() {
        val shuffled = ScaleColours.bands {
            band(from = 8_000f, colour = red)
            band(from = 0f, colour = green)
            band(from = 6_000f, colour = amber)
        }
        assertStops(tachometer().stops(0f..10_000f, accent), shuffled.stops(0f..10_000f, accent))
    }

    /**
     * The scale before the first band is the dial's own colour, and so is the gap a
     * band's `until` leaves before the next one.
     *
     * "I don't like how the start colour is separated from the other bands. Can we
     * just do a band(from = 0f, ...), and have just a 'default' colour from the theme
     * for bands that aren't specified."
     */
    @Test
    fun whatNoBandCoversIsTheDialsOwnColour() {
        val redline = ScaleColours.bands { band(from = 8_000f, colour = red) }
        assertStops(
            listOf(0f to accent, 0.8f to accent, 0.8f to red, 1f to red),
            redline.stops(0f..10_000f, accent),
        )
        val comfort = ScaleColours.bands { band(from = 20f, until = 24f, colour = green) }
        assertStops(
            listOf(0f to accent, 0.5f to accent, 0.5f to green, 0.7f to green, 0.7f to accent, 1f to accent),
            comfort.stops(10f..30f, accent),
        )
        assertStops(listOf(0f to accent, 1f to accent), ScaleColours.bands {}.stops(0f..1f, accent))
    }

    /** A band's `until` never reaches past the next band's start: the later start wins. */
    @Test
    fun aLaterBandCutsAnEarlierOneShort() {
        val overlapping = ScaleColours.bands {
            band(from = 0f, until = 80f, colour = green)
            band(from = 50f, colour = red)
        }
        assertStops(
            listOf(0f to green, 0.5f to green, 0.5f to red, 1f to red),
            overlapping.stops(0f..100f, accent),
        )
    }

    /**
     * At full smoothing a band is its own colour only at its middle: amber, from 0.6
     * to 0.8, is pure amber at 0.7 and blends from there each way. The green-amber
     * blend reaches no further back than amber's width does forward, so it spans 0.5
     * to 0.7 rather than eating into the whole green band.
     */
    @Test
    fun smoothingBlendsEachEdgeAsFarAsHalfwayIntoTheNarrowerSide() {
        assertStops(
            listOf(0f to green, 0.5f to green, 0.7f to amber, 0.7f to amber, 0.9f to red, 1f to red),
            tachometer(smoothing = 1f).stops(0f..10_000f, accent),
        )
        assertStops(
            listOf(0f to green, 0.55f to green, 0.65f to amber, 0.75f to amber, 0.85f to red, 1f to red),
            tachometer(smoothing = 0.5f).stops(0f..10_000f, accent),
        )
    }

    @Test
    fun aBandPastEitherEndIsHeldAtIt() {
        val past = ScaleColours.bands {
            band(from = -50f, colour = amber)
            band(from = 150f, colour = red)
        }
        assertStops(listOf(0f to amber, 1f to amber), past.stops(0f..100f, accent))
    }

    @Test
    fun aGradientIsSpreadEvenlyAndASolidIsOneColour() {
        assertStops(
            listOf(0f to green, 0.5f to amber, 1f to red),
            ScaleColours.gradient(listOf(green, amber, red)).stops(0f..1f, accent),
        )
        assertStops(listOf(0f to green), ScaleColours.solid(green).stops(0f..10f, accent))
    }

    /** The colour at a point is the one there — the band that starts at an edge. */
    @Test
    fun theColourAlongTheScaleIsTheBandThere() {
        val stops = tachometer().stops(0f..10_000f, accent)
        assertEquals(green, colourAlong(stops, 0.3f))
        assertEquals(amber, colourAlong(stops, 0.6f), "at an edge, the band that starts there")
        assertEquals(amber, colourAlong(stops, 0.7f))
        assertEquals(red, colourAlong(stops, 1f))
        val blended = colourAlong(ScaleColours.gradient(listOf(Color.Black, Color.White)).stops(0f..1f, accent), 0.5f)
        assertTrue(abs(blended.red - 0.5f) < 0.01f, "halfway along a gradient is halfway between, was $blended")
    }

    @Test
    fun theSameBandsAreEqual() {
        assertEquals(tachometer(), tachometer())
        assertTrue(tachometer() != tachometer(smoothing = 0.5f), "a different smoothing is a different fill")
    }

    private fun assertStops(expected: List<Pair<Float, Color>>, actual: List<Pair<Float, Color>>) {
        assertEquals(expected.size, actual.size, "stops $actual, expected $expected")
        expected.zip(actual).forEach { (e, a) ->
            assertTrue(
                abs(e.first - a.first) < 0.0001f && e.second == a.second,
                "stops $actual, expected $expected",
            )
        }
    }
}
