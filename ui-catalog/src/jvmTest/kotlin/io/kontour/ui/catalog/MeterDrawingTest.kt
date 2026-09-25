package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.display.DialColours
import io.kontour.ui.components.display.GaugeIndicator
import io.kontour.ui.components.display.Meter
import io.kontour.ui.components.display.MeterContentPlacement
import io.kontour.ui.components.display.MeterOrientation
import io.kontour.ui.components.display.ScaleColours
import java.awt.image.BufferedImage
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A meter's fill, read off its pixels: from where to where, which way round, and
 * in what colour.
 *
 * A 200dp horizontal meter at density 2 is 400px wide, and with flat ends and
 * nothing overhanging them the track runs the whole width, so a fraction of the
 * scale is that fraction of 400px. A 20dp track is 40px thick, centred 20px down.
 */
class MeterDrawingTest {

    private val red = Color(0xFFD00000)
    private val blue = Color(0xFF0040D0)
    private val grey = Color(0xFFB0B0B0)
    private val green = Color(0xFF00A000)

    private fun colours(fill: ScaleColours = ScaleColours.solid(red)) = DialColours(
        indicator = fill,
        track = grey,
        tick = Color.Black,
        tickLabel = Color.Black,
        needle = Color.Black,
        thumb = green,
        thumbRing = Color.Black,
    )

    @Test
    fun aHorizontalMeterFillsFromTheStartToTheReading() {
        val image = across(value = 0.5f)
        assertColour(red, image, 100, CentrePx, "a quarter of the way along, inside the fill")
        assertColour(grey, image, 300, CentrePx, "three quarters of the way along, past the reading")
    }

    @Test
    fun rightToLeftItFillsFromTheRight() {
        val image = across(value = 0.5f, direction = LayoutDirection.Rtl)
        assertColour(red, image, 300, CentrePx, "a quarter of the way from the right, inside the fill")
        assertColour(grey, image, 100, CentrePx, "a quarter of the way from the left, past the reading")
    }

    @Test
    fun aVerticalMeterFillsUpward() {
        val image = render {
            Meter(
                value = 0.25f,
                modifier = Modifier.height(200.dp),
                orientation = MeterOrientation.Vertical,
                thickness = 20.dp,
                cap = StrokeCap.Butt,
                colours = colours(),
            )
        }
        assertColour(red, image, CentrePx, 350, "an eighth of the way up")
        assertColour(grey, image, CentrePx, 50, "an eighth of the way down from the top")
    }

    /** Either side of a centre: the fill runs from the origin to the reading, whichever way. */
    @Test
    fun theFillRunsFromTheOrigin() {
        val image = across(value = -0.5f, range = -1f..1f, origin = 0f)
        assertColour(red, image, 150, CentrePx, "between the reading and the origin")
        assertColour(grey, image, 50, CentrePx, "before the reading")
        assertColour(grey, image, 300, CentrePx, "past the origin on the other side")
    }

    /** A band is a place on the scale: the part at 70% is blue whether or not the fill ends there. */
    @Test
    fun bandsColourByPlaceOnTheScale() {
        val bands = ScaleColours.bands {
            band(from = 0f, colour = red)
            band(from = 0.5f, colour = blue)
        }
        for (reading in listOf(0.75f, 1f)) {
            val image = across(value = reading, fill = bands)
            assertColour(red, image, 100, CentrePx, "at 25%, reading $reading")
            assertColour(blue, image, 280, CentrePx, "at 70%, reading $reading")
        }
    }

    /**
     * The thumb sits on the track at the reading and the needle runs across it there.
     * With both, the ends are inset by the thumb's 34px radius, so 0.5 is still the
     * middle, at 200px; the needle is 100px long, so the meter's middle is 50px down.
     */
    @Test
    fun theThumbAndTheNeedleMarkTheReading() {
        val image = across(value = 0.5f, indicator = GaugeIndicator.NeedleAndThumb)
        assertColour(green, image, 200, 50, "the thumb's face at the reading")
        assertColour(Color.Black, image, 200, 50 - 44, "the needle, past the thumb, at the reading")
        assertColour(grey, image, 300, 50, "the track past the reading")
    }

    /** At the reading, the content is centred on it; at an end, it stops at the edge. */
    @Test
    fun theContentRidesToTheReadingAndStopsAtTheEnds() {
        for ((reading, expected) in listOf(0.25f to 100f, 0f to 20f, 1f to 380f)) {
            var tag = Rect.Zero
            render {
                Meter(
                    value = reading,
                    modifier = Modifier.width(200.dp),
                    thickness = 20.dp,
                    cap = StrokeCap.Butt,
                    colours = colours(),
                    contentPlacement = MeterContentPlacement.AtValue,
                ) {
                    Box(Modifier.size(20.dp).reportBounds { tag = it })
                }
            }
            assertTrue(
                abs(tag.center.x - expected) <= 2f,
                "at $reading the content's middle should be at ${expected}px, was ${tag.center.x}",
            )
        }
    }

    private fun across(
        value: Float,
        range: ClosedFloatingPointRange<Float> = 0f..1f,
        origin: Float = range.start,
        fill: ScaleColours = ScaleColours.solid(red),
        indicator: GaugeIndicator = GaugeIndicator.None,
        direction: LayoutDirection = LayoutDirection.Ltr,
    ): BufferedImage = render(direction) {
        Meter(
            value = value,
            valueRange = range,
            origin = origin,
            modifier = Modifier.width(200.dp),
            thickness = 20.dp,
            cap = StrokeCap.Butt,
            colours = colours(fill),
            indicator = indicator,
        )
    }

    private fun render(
        direction: LayoutDirection = LayoutDirection.Ltr,
        content: @Composable () -> Unit,
    ): BufferedImage {
        lateinit var image: BufferedImage
        Scene(width = 400, height = 400, reduceMotion = true) {
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                Box(Modifier.fillMaxSize().background(Color.White)) { content() }
            }
        }.use { image = it.frames(4) }
        return image
    }

    private fun assertColour(expected: Color, image: BufferedImage, x: Int, y: Int, what: String) {
        val p = image.getRGB(x, y)
        val want = listOf((expected.red * 255).toInt(), (expected.green * 255).toInt(), (expected.blue * 255).toInt())
        val got = listOf(p shr 16 and 0xFF, p shr 8 and 0xFF, p and 0xFF)
        assertTrue(got.zip(want).all { (a, b) -> abs(a - b) < 40 }, "$what: expected $want at ($x, $y), was $got")
    }

    private companion object {
        /** Half the 20dp track, at density two. */
        const val CentrePx = 20
    }
}
