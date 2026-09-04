package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.kontour.ui.foundation.Text
import io.kontour.ui.overlay.Dialog
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.Theme
import java.io.File
import kotlin.math.abs
import kotlin.math.cbrt
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The scrim and the dialog arrive together, to the eye.
 *
 * Reported as the background not showing up at the same rate as the dialog, for
 * every dialog-style overlay, and suspected to be an `OverlayHost` problem.
 *
 * It is not one, and that is worth writing down: `EntryHost` hands the *same*
 * `progress` to the scrim's fraction and to the panel's `overlayAppearance`, and
 * both are linear in it. There is no second animation, no second spec, no frame
 * of lag — so no amount of reading the code can find a disagreement, because in
 * the code there is not one.
 *
 * Any disagreement is therefore between that number and the eye, which is where
 * this looks.
 *
 * ### What each of them is measured by
 *
 * Not the same thing, because they are not doing the same thing. A scrim's job
 * is to **darken**, so it is measured by how far a corner pixel — the page, seen
 * through the scrim and nothing else — has travelled in lightness. A dialog's
 * job is to become **distinct**, so it is measured by the contrast between its
 * middle and that same corner.
 *
 * The first version of this measured the dialog's centre pixel directly and was
 * useless for the commonest case in the library: a near-white panel on a
 * near-white page starts and ends at the same colour, and what makes it visible
 * is its shadow and its edge rather than its fill. Contrast against the page is
 * both the honest measure and the one that works everywhere.
 *
 * ### Swept, because a veil has no rate of its own
 *
 * How fast a scrim *looks* like it is arriving is a property of what is behind
 * it as much as of the animation. A conclusion drawn from one background would
 * be a conclusion about that background, so this runs over five.
 *
 * Both curves for every case land in `build/dialog-rate.txt`, as measured and as
 * seen, so the gap can be read rather than argued about.
 */
class DialogBackdropRateTest {

    @Test
    fun theScrimAndThePanelArriveTogether() {
        val readings = Cases.map { it to read(it) }
        File("build/dialog-rate.txt").writeText(
            readings.joinToString("") { (case, reading) -> reading.report(case) }
        )

        val worst = readings.maxBy { it.second.worstSeen }
        assertTrue(
            worst.second.worstSeen < Tolerance,
            "the scrim and the dialog are ${worst.second.worstSeen} apart at their " +
                "worst frame on ${worst.first.name}, which is further than two things " +
                "driven by one number should ever look:\n" +
                worst.second.report(worst.first),
        )
    }

    private class Reading(private val corner: List<Int>, private val centre: List<Int>) {
        /** How dark the page has gone. */
        private val scrimSeen = progress(corner.map { lightness(it) })

        /** How far the panel stands out from the page around it. */
        private val panelSeen = progress(
            corner.indices.map { abs(lightness(centre[it]) - lightness(corner[it])) }
        )

        private val scrimRaw = progress(corner.map { luminance(it) })
        private val panelRaw = progress(
            corner.indices.map { abs(luminance(centre[it]) - luminance(corner[it])) }
        )

        /**
         * The panel's own alpha, recovered from the composite.
         *
         * The panel's colour is whatever the last frame shows, the page's is the
         * corner, and every frame between is the one over the other at some
         * opacity — so the opacity can be solved for. That is what makes it
         * possible to ask what a *different* alpha curve would look like without
         * rendering it: the answer is arithmetic on colours already measured.
         *
         * Solved on whichever channel separates the panel from the page most, so
         * the division is never by a number near zero — except on the frames
         * where the panel and the page really are the same colour, which is a
         * white dialog over a white page before the scrim has done anything.
         * There the opacity is genuinely unrecoverable and genuinely does not
         * matter: whatever it is, the panel contributes no contrast, so those
         * frames are read as zero rather than dropped.
         */
        private val alpha: List<Double> = run {
            val panel = centre.last()
            val channel = (0..2).maxBy { abs(band(panel, it) - band(corner.last(), it)) }
            corner.indices.map { i ->
                val p = band(panel, channel)
                val c = band(corner[i], channel)
                val m = band(centre[i], channel)
                if (abs(p - c) < 1.0) 0.0 else ((m - c) / (p - c)).coerceIn(0.0, 1.0)
            }
        }

        val worstSeen: Double = worstGap(scrimSeen, panelSeen)
        private val worstRaw: Double = worstGap(scrimRaw, panelRaw)

        /**
         * What the gap would be if the panel's alpha were raised to [exponent].
         *
         * Below 1 the panel fades in ahead of its own linear curve, which is the
         * only lever that moves this: the panel's *visible* arrival is its alpha
         * times how far the page has darkened beneath it, so on a page the panel
         * needs the scrim to stand out from, a linear alpha gives a curve that
         * goes as the square.
         */
        fun gapAt(exponent: Double): Double {
            val contrast = corner.indices.map { i ->
                val a = alpha[i].pow(exponent)
                val recomposited = (0..2).map { ch ->
                    band(centre.last(), ch) * a + band(corner[i], ch) * (1 - a)
                }
                abs(lightness(recomposited) - lightness(corner[i]))
            }
            return worstGap(scrimSeen, progress(contrast))
        }

        fun report(case: Case): String = buildString {
            appendLine("== ${case.name} ==")
            appendLine("        ---- as measured ----   ------ as seen ------")
            appendLine("frame     scrim     panel         scrim     panel      gap")
            // Only while something is moving; the tail is thirty identical ones.
            val last = scrimSeen.indices.lastOrNull {
                scrimSeen[it] < 0.999 || panelSeen[it] < 0.999
            } ?: 0
            for (i in 0..minOf(last + 1, scrimSeen.lastIndex)) {
                appendLine(
                    "%5d     %5.3f     %5.3f         %5.3f     %5.3f   %+6.3f".format(
                        i, scrimRaw[i], panelRaw[i], scrimSeen[i], panelSeen[i],
                        scrimSeen[i] - panelSeen[i],
                    )
                )
            }
            appendLine("worst gap as measured: %.3f".format(worstRaw))
            appendLine("worst gap as seen:     %.3f".format(worstSeen))
            appendLine(
                "if the panel's alpha were eased: " +
                    Exponents.joinToString("  ") { "%.2f->%.3f".format(it, gapAt(it)) }
            )
            appendLine()
        }
    }

    /** Opens a dialog over [case]'s page and reads two pixels a frame. */
    private fun read(case: Case): Reading {
        var open by mutableStateOf(false)
        val corner = mutableListOf<Int>()
        val centre = mutableListOf<Int>()

        Scene(width = 600, height = 400, darkTheme = case.dark) {
            OverlayHost {
                Box(Modifier.fillMaxSize().background(case.page)) {
                    Dialog(visible = open, onDismissRequest = {}) {
                        Box(Modifier.size(220.dp, 120.dp)) {
                            Text("Discard changes?", style = Theme.typography.titleMedium)
                        }
                    }
                }
            }
        }.use { scene ->
            scene.frames(4)
            open = true
            repeat(Frames) {
                val image = scene.frame()
                corner += image.getRGB(6, 6)
                centre += image.getRGB(image.width / 2, image.height / 2)
            }
        }
        return Reading(corner, centre)
    }

    /** One page to open a dialog over. */
    data class Case(val name: String, val page: Color, val dark: Boolean)

    private companion object {

        /**
         * The backgrounds that bracket what an app actually has behind a dialog:
         * a near-white page, a mid-tone map or photograph, a saturated one, and
         * a dark page under the dark theme, whose scrim is deeper.
         *
         * Not a *dark-theme* mid-tone page, which is the one combination the
         * contrast probe cannot read. A dark panel over a light-grey page starts
         * far apart, and the scrim then drags the page down *past* the panel — so
         * contrast rises, peaks, and falls back, and "how far along is it" has no
         * answer. That is a property of the probe rather than of the animation,
         * and it describes a screen nobody ships: a dark-theme app does not have
         * a light-grey page behind its dialogs.
         */
        val Cases = listOf(
            Case("light theme, white page", Color(0xFFFFFFFF), dark = false),
            Case("light theme, mid page", Color(0xFF7A7A7A), dark = false),
            Case("light theme, red page", Color(0xFFB00020), dark = false),
            Case("dark theme, dark page", Color(0xFF121212), dark = true),
        )

        const val Frames = 40

        /** Candidate curves for the panel's alpha; 1.0 is what it does today. */
        val Exponents = listOf(1.0, 0.85, 0.75, 0.7, 0.65, 0.6, 0.5)

        /**
         * How far apart the two may look at their worst frame.
         *
         * Not zero: they are a veil and a surface, and no easing makes two
         * different physical things track each other exactly.
         */
        const val Tolerance = 0.18

        /** How far a curve has travelled from where it started to where it ends. */
        fun progress(values: List<Double>): List<Double> {
            val span = values.last() - values.first()
            check(abs(span) > 1.0) { "nothing moved: $values" }
            return values.map { (it - values.first()) / span }
        }

        fun worstGap(a: List<Double>, b: List<Double>): Double =
            a.indices.maxOf { abs(a[it] - b[it]) }

        /** One channel of a packed colour, 0..255. */
        fun band(argb: Int, channel: Int): Double =
            ((argb shr (16 - 8 * channel)) and 0xFF).toDouble()

        /** Relative luminance, straight off the encoded channels. */
        fun luminance(argb: Int): Double {
            val r = (argb shr 16) and 0xFF
            val g = (argb shr 8) and 0xFF
            val b = argb and 0xFF
            return 0.2126 * r + 0.7152 * g + 0.0722 * b
        }

        /**
         * CIE L*, which is what "how far has it arrived" means to an eye.
         *
         * Channels are linearised out of sRGB first — the encoding is already a
         * rough perceptual curve, and applying a second one on top of it would
         * overstate the very effect this is here to measure.
         */
        fun lightness(argb: Int): Double =
            lightness(listOf(band(argb, 0), band(argb, 1), band(argb, 2)))

        fun lightness(rgb: List<Double>): Double {
            fun linear(channel: Double): Double {
                val c = channel / 255.0
                return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
            }
            val y = 0.2126 * linear(rgb[0]) + 0.7152 * linear(rgb[1]) + 0.0722 * linear(rgb[2])
            return if (y > 0.008856) 116.0 * cbrt(y) - 16.0 else 903.3 * y
        }
    }
}
