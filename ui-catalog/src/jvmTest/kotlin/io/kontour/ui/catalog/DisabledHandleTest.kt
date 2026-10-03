package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.kontour.ui.a11y.contrastRatio
import io.kontour.ui.components.display.ScaleColours
import io.kontour.ui.components.selection.Knob
import io.kontour.ui.components.selection.KnobDefaults
import io.kontour.ui.components.selection.RangeSlider
import io.kontour.ui.components.selection.Slider
import io.kontour.ui.components.selection.Switch
import io.kontour.ui.theme.ContrastLevel
import io.kontour.ui.theme.KontourTheme
import io.kontour.ui.theme.Theme
import io.kontour.ui.theme.kontourColourScheme
import java.awt.image.BufferedImage
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A disabled control's handle is the page's own colour: a hole in the grey, not a
 * mark on it.
 *
 * The switch's was the content colour for the disabled grey — near-black on the
 * light scheme's, white on the dark one's — and that was the report: *"the head
 * of the switch when it's disabled is a very strong colour"*. The slider's was
 * the grey itself, a solid disc, and the knob was its enabled self at 38%. Read
 * at the handle's centre in all four schemes, and, for the slider, whose handle
 * overhangs its track onto the page, at its ring too: there the ring is all that
 * draws it, and at the enhanced tier it holds 3:1.
 */
class DisabledHandleTest {

    @Test
    fun aDisabledSwitchesThumbIsThePagesColour() = everyScheme { dark, contrast ->
        for (checked in listOf(false, true)) {
            var bounds = Rect.Zero
            val frame = render(dark, contrast) {
                Switch(checked = checked, onCheckedChange = {}, enabled = false, modifier = Modifier.reportBounds { bounds = it })
            }
            // The thumb rests 10dp either side of the track's middle. Read above
            // its centre, where the enhanced tier draws a check or a dash.
            val x = bounds.center.x + (if (checked) 1 else -1) * ThumbOffset * Density
            val y = bounds.center.y - AboveGlyph * Density
            assertSurface(frame, x, y, dark, contrast, "a disabled switch, ${if (checked) "on" else "off"}")
        }
    }

    @Test
    fun aDisabledSlidersThumbIsThePagesColourRingedInTheGrey() = everyScheme { dark, contrast ->
        var bounds = Rect.Zero
        val frame = render(dark, contrast) {
            Slider(value = 0.5f, onValueChange = {}, enabled = false, modifier = Modifier.width(240.dp).reportBounds { bounds = it })
        }
        assertSurface(frame, bounds.center.x, bounds.center.y, dark, contrast, "a disabled slider")
        if (contrast == ContrastLevel.High) assertRing(frame, bounds.center.x, bounds.center.y, dark, contrast, "a disabled slider")
    }

    @Test
    fun aDisabledRangeSlidersThumbsAreThePagesColour() = everyScheme { dark, contrast ->
        var bounds = Rect.Zero
        val frame = render(dark, contrast) {
            RangeSlider(value = 0.5f..0.5f, onValueChange = {}, enabled = false, modifier = Modifier.width(240.dp).reportBounds { bounds = it })
        }
        assertSurface(frame, bounds.center.x, bounds.center.y, dark, contrast, "a disabled range slider")
        if (contrast == ContrastLevel.High) assertRing(frame, bounds.center.x, bounds.center.y, dark, contrast, "a disabled range slider")
    }

    /** And a knob's face, with nothing of a coloured scale left on it. */
    @Test
    fun aDisabledKnobIsGreyWhateverItsScale() = everyScheme { dark, contrast ->
        var bounds = Rect.Zero
        val frame = render(dark, contrast) {
            Knob(
                value = 0.7f,
                onValueChange = {},
                enabled = false,
                colours = KnobDefaults.colours(indicator = ScaleColours.gradient(listOf(Green, Amber, Red))),
                modifier = Modifier.reportBounds { bounds = it },
            )
        }
        assertSurface(frame, bounds.center.x, bounds.center.y, dark, contrast, "a disabled knob's face")
        val grey = greyCeiling(kontourColourScheme(dark, contrast))
        var coloured = 0
        for (y in bounds.top.toInt() until bounds.bottom.toInt()) {
            for (x in bounds.left.toInt() until bounds.right.toInt()) {
                if (chroma(Color(frame.getRGB(x, y))) > grey) coloured++
            }
        }
        assertTrue(
            coloured == 0,
            "a disabled knob with a green-to-red scale drew $coloured coloured pixels in ${scheme(dark, contrast)}",
        )
    }

    private fun assertSurface(frame: BufferedImage, x: Float, y: Float, dark: Boolean, contrast: ContrastLevel, what: String) {
        val surface = kontourColourScheme(dark, contrast).surface
        val drawn = Color(frame.getRGB(x.toInt(), y.toInt()))
        assertTrue(
            close(drawn, surface),
            "$what has a handle of $drawn at its centre in ${scheme(dark, contrast)}, not the page's $surface",
        )
    }

    /** Just inside the handle's edge, where its ring is drawn. */
    private fun assertRing(frame: BufferedImage, x: Float, y: Float, dark: Boolean, contrast: ContrastLevel, what: String) {
        val surface = kontourColourScheme(dark, contrast).surface
        val ring = Color(frame.getRGB((x + RingAt * Density).toInt(), y.toInt()))
        assertTrue(
            contrastRatio(ring, surface) >= 3f,
            "$what's ring is ${contrastRatio(ring, surface)}:1 from the page in ${scheme(dark, contrast)}: " +
                "with a handle the page's colour, the ring is all that draws it",
        )
    }

    private fun render(dark: Boolean, contrast: ContrastLevel, content: @Composable () -> Unit): BufferedImage =
        Scene(width = 600, height = 240, reduceMotion = true, darkTheme = dark) {
            KontourTheme(darkTheme = dark, contrast = contrast, reduceMotion = true) {
                Box(Modifier.fillMaxSize().background(Theme.colours.background), contentAlignment = Alignment.Center) {
                    content()
                }
            }
        }.use { it.frames(20) }

    private fun everyScheme(check: (dark: Boolean, contrast: ContrastLevel) -> Unit) {
        for (dark in listOf(false, true)) for (contrast in ContrastLevel.entries) check(dark, contrast)
    }

    private fun scheme(dark: Boolean, contrast: ContrastLevel) =
        (if (contrast == ContrastLevel.High) "high-contrast " else "") + if (dark) "dark" else "light"

    private fun close(a: Color, b: Color) =
        abs(a.red - b.red) < 0.02f && abs(a.green - b.green) < 0.02f && abs(a.blue - b.blue) < 0.02f

    private companion object {
        const val Density = 2f

        /** A switch's thumb from the track's middle: (48 - 24 - 2·2) / 2. */
        const val ThumbOffset = 10f

        /** Clear of the check or dash in the thumb, and inside its 12dp radius. */
        const val AboveGlyph = 7f

        /** 12dp of thumb radius, less half of a 3dp ring at the enhanced tier. */
        const val RingAt = 10.5f

        val Green = Color(0xFF22AA44)
        val Amber = Color(0xFFEEAA22)
        val Red = Color(0xFFDD3322)
    }
}
