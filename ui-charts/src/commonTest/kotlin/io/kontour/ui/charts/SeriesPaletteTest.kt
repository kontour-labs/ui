package io.kontour.ui.charts

import androidx.compose.ui.graphics.Color
import io.kontour.ui.a11y.contrastRatio
import io.kontour.ui.theme.ColourScheme
import io.kontour.ui.theme.darkColourScheme
import io.kontour.ui.theme.highContrastDarkColourScheme
import io.kontour.ui.theme.highContrastLightColourScheme
import io.kontour.ui.theme.lightColourScheme
import kotlin.math.cbrt
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The series palettes hold to what they were chosen for.
 *
 * Every colour stands 3:1 off each ground of its scheme — 4.5:1 at the high
 * tier — which is WCAG's floor for anything that is not text and has to be
 * seen. And every two colours stay apart, in OKLab, under normal vision and
 * under simulated protanopia, deuteranopia and tritanopia: the palette was
 * picked to keep the worst of the four as far apart as it could, and a colour
 * changed by hand that undid that would fail here rather than on a reader's
 * screen.
 */
class SeriesPaletteTest {

    private class Tier(val name: String, val palette: List<Color>, val scheme: ColourScheme, val floor: Float)

    private val tiers = listOf(
        Tier("light", SeriesPalettes.Light, lightColourScheme(), 3f),
        Tier("dark", SeriesPalettes.Dark, darkColourScheme(), 3f),
        Tier("high contrast light", SeriesPalettes.HighContrastLight, highContrastLightColourScheme(), 4.5f),
        Tier("high contrast dark", SeriesPalettes.HighContrastDark, highContrastDarkColourScheme(), 4.5f),
    )

    @Test
    fun everyColourStandsOffEveryGroundItIsDrawnOn() {
        tiers.forEach { tier ->
            val grounds = listOf(tier.scheme.background, tier.scheme.surface, tier.scheme.surfaceRaised)
            tier.palette.forEachIndexed { slot, colour ->
                grounds.forEach { ground ->
                    val ratio = contrastRatio(colour, ground)
                    assertTrue(ratio >= tier.floor, "${tier.name} slot $slot is $ratio:1 on $ground, under ${tier.floor}:1")
                }
            }
        }
    }

    @Test
    fun everyTwoColoursStayApartUnderEachKindOfColourBlindness() {
        tiers.forEach { tier ->
            val palette = tier.palette.map { linear(it) }
            for (i in palette.indices) for (j in i + 1 until palette.size) {
                val worst = Vision.entries.minOf { vision -> distance(vision.see(palette[i]), vision.see(palette[j])) }
                assertTrue(worst >= MinimumDistance, "${tier.name} slots $i and $j are only $worst apart in OKLab")
            }
        }
    }

    @Test
    fun thereAreSixInEveryTier() {
        tiers.forEach { assertTrue(it.palette.size == 6, "${it.name} has ${it.palette.size}") }
    }

    /** Machado, Oliveira and Fernandes (2009), at full severity, applied in linear sRGB. */
    private enum class Vision(private val matrix: List<Double>?) {
        Normal(null),
        Protanopia(listOf(0.152286, 1.052583, -0.204868, 0.114503, 0.786281, 0.099216, -0.003882, -0.048116, 1.051998)),
        Deuteranopia(listOf(0.367322, 0.860646, -0.227968, 0.280085, 0.672501, 0.047413, -0.011820, 0.042940, 0.968881)),
        Tritanopia(listOf(1.255528, -0.076749, -0.178779, -0.078411, 0.930809, 0.147602, 0.004733, 0.691367, 0.303900)),
        ;

        fun see(rgb: DoubleArray): DoubleArray {
            val m = matrix ?: return rgb
            return DoubleArray(3) { row ->
                (m[row * 3] * rgb[0] + m[row * 3 + 1] * rgb[1] + m[row * 3 + 2] * rgb[2]).coerceIn(0.0, 1.0)
            }
        }
    }

    private fun linear(colour: Color): DoubleArray = doubleArrayOf(colour.red, colour.green, colour.blue).map { c ->
        val v = c.toDouble()
        if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    }.toDoubleArray()

    private fun oklab(rgb: DoubleArray): DoubleArray {
        val l = cbrt(0.4122214708 * rgb[0] + 0.5363325363 * rgb[1] + 0.0514459929 * rgb[2])
        val m = cbrt(0.2119034982 * rgb[0] + 0.6806995451 * rgb[1] + 0.1073969566 * rgb[2])
        val s = cbrt(0.0883024619 * rgb[0] + 0.2817188376 * rgb[1] + 0.6299787005 * rgb[2])
        return doubleArrayOf(
            0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
            1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
            0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s,
        )
    }

    private fun distance(a: DoubleArray, b: DoubleArray): Double {
        val p = oklab(a)
        val q = oklab(b)
        return sqrt((p[0] - q[0]).pow(2) + (p[1] - q[1]).pow(2) + (p[2] - q[2]).pow(2))
    }

    private companion object {
        /**
         * The least OKLab distance any two colours may have under any of the
         * four kinds of vision. The palettes as chosen manage 0.08 at worst;
         * 0.07 leaves room for a rounding in a colour's last digit and none for
         * a second violet.
         */
        const val MinimumDistance = 0.07
    }
}
