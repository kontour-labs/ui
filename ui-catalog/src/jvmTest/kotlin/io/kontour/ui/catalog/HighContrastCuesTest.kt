package io.kontour.ui.catalog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.kontour.ui.a11y.contrastRatio
import io.kontour.ui.components.selection.FilterChip
import io.kontour.ui.components.selection.Switch
import io.kontour.ui.foundation.Surface
import io.kontour.ui.theme.ContrastLevel
import io.kontour.ui.theme.KontourTheme
import io.kontour.ui.theme.Theme
import io.kontour.ui.theme.kontourColourScheme
import org.jetbrains.skia.Bitmap
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The marks the enhanced tier adds, and that the standard tier does not.
 *
 * A switch that says on or off only by where its thumb is and what colour the
 * track is — two carriers a reader who cannot see colour has one of — gets a
 * check or a dash in the thumb at the enhanced tier. A selected chip, a tint
 * 1.25:1 from the page there, gets an outline in the accent. Both are measured
 * on the pixels, at both tiers, because the promise is two-sided: the mark is
 * there when asked for and is not there when it was not.
 */
class HighContrastCuesTest {

    @Test
    fun aSwitchCarriesACheckOrADashOnlyAtTheEnhancedTier() {
        for (checked in listOf(true, false)) {
            val standard = marksInThumb(ContrastLevel.Standard, checked)
            val enhanced = marksInThumb(ContrastLevel.High, checked)
            val state = if (checked) "on" else "off"
            assertEquals(0, standard, "a switch $state drew a mark in its thumb at the standard tier")
            assertTrue(enhanced > 8, "a switch $state drew no mark in its thumb at the enhanced tier ($enhanced pixels)")
        }
    }

    @Test
    fun aSelectedChipIsOutlinedInTheAccentOnlyAtTheEnhancedTier() {
        val light = kontourColourScheme(dark = false, contrast = ContrastLevel.High)
        val (edge, page) = chipEdge(ContrastLevel.High)
        assertTrue(
            contrastRatio(edge, page) >= 3f,
            "a selected chip's edge is ${contrastRatio(edge, page)}:1 from the page at the enhanced tier",
        )
        assertTrue(close(edge, light.accent.solid), "the selected chip's edge is $edge, not the accent ${light.accent.solid}")

        val standard = kontourColourScheme(dark = false, contrast = ContrastLevel.Standard)
        val (resting, _) = chipEdge(ContrastLevel.Standard)
        assertTrue(
            close(resting, standard.accent.container),
            "a selected chip at the standard tier gained an edge ($resting) where its tint was",
        )
    }

    /** Pixels in the middle of the thumb that are not the thumb's own colour. */
    private fun marksInThumb(contrast: ContrastLevel, checked: Boolean): Int {
        var thumb = Color.Unspecified
        val bitmap = render(contrast) {
            thumb = Theme.colours.onPrimary
            Switch(checked = checked, onCheckedChange = {}, modifier = Modifier.padding(Margin.dp))
        }
        val box = inkBounds(bitmap)
        val height = box.bottom - box.top
        // The thumb sits at the end its state names; its middle is a square half
        // its height, well inside the circle.
        val centreX = if (checked) box.right - height / 2 else box.left + height / 2
        val centreY = (box.top + box.bottom) / 2
        val half = height / 5
        var marks = 0
        for (y in centreY - half..centreY + half) {
            for (x in centreX - half..centreX + half) {
                if (!close(Color(bitmap.getColor(x, y)), thumb)) marks++
            }
        }
        return marks
    }

    /** The colour one pixel inside a selected chip's leading edge, and the page's. */
    private fun chipEdge(contrast: ContrastLevel): Pair<Color, Color> {
        val bitmap = render(contrast) {
            FilterChip(selected = true, onSelectedChange = {}, modifier = Modifier.padding(Margin.dp)) { +"Step-free" }
        }
        val box = inkBounds(bitmap)
        val y = (box.top + box.bottom) / 2
        return Color(bitmap.getColor(box.left + 1, y)) to Color(bitmap.getColor(0, 0))
    }

    private fun render(contrast: ContrastLevel, content: @Composable () -> Unit): Bitmap {
        val scene = ImageComposeScene(width = 240, height = 120, density = Density(2f)) {
            KontourTheme(darkTheme = false, contrast = contrast, reduceMotion = true) {
                Surface(Modifier.fillMaxSize(), containerColour = Theme.colours.background) {
                    Box { content() }
                }
            }
        }
        return try {
            var image = scene.render(0L)
            repeat(10) { image = scene.render(16_000_000L * (it + 1)) }
            Bitmap.makeFromImage(image)
        } finally {
            scene.close()
        }
    }

    private class Bounds(val left: Int, val top: Int, val right: Int, val bottom: Int)

    /** The smallest box holding every pixel that is not the page's. */
    private fun inkBounds(bitmap: Bitmap): Bounds {
        val page = bitmap.getColor(0, 0)
        var left = bitmap.width
        var top = bitmap.height
        var right = 0
        var bottom = 0
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                if (bitmap.getColor(x, y) != page) {
                    left = minOf(left, x)
                    top = minOf(top, y)
                    right = maxOf(right, x)
                    bottom = maxOf(bottom, y)
                }
            }
        }
        check(right > left && bottom > top) { "nothing was drawn" }
        return Bounds(left, top, right, bottom)
    }

    private fun close(a: Color, b: Color): Boolean =
        abs(a.red - b.red) < 0.08f && abs(a.green - b.green) < 0.08f && abs(a.blue - b.blue) < 0.08f

    private companion object {
        const val Margin = 8
    }
}
