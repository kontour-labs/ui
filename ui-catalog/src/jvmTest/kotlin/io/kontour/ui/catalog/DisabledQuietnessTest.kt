package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import io.kontour.ui.a11y.contrastRatio
import io.kontour.ui.contract.ComponentSpec
import io.kontour.ui.contract.componentRegistry
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.ContrastLevel
import io.kontour.ui.theme.KontourTheme
import io.kontour.ui.theme.Theme
import io.kontour.ui.theme.kontourColourScheme
import java.awt.image.BufferedImage
import kotlin.math.cbrt
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A disabled control is quieter than the same control enabled, everywhere on it.
 *
 * Reported for the switch — *"the head of the switch when it's disabled is a
 * very strong colour. this might apply to other components too. can we please
 * tone it down a bit?"* — whose thumb was the content colour *for* the disabled
 * grey: near-black on the light one, white on the dark. The rule it broke is
 * general, so this holds every control in the registry to it, in all four
 * schemes:
 *
 * - **Nothing louder than the disabled grey.** No pixel of a disabled control
 *   stands out from the page more than `contentDisabled` does — or, at the
 *   enhanced tier, than the scheme's `outline`, which is what draws the edges
 *   that tier keeps. The old thumb was 18:1 against a page where the grey is
 *   2.5:1.
 *
 *   Not "no louder than the same pixel enabled", which was the first draft and
 *   is wrong in an instructive way: an enabled button's white label is *the
 *   page's colour*, knocked out of a black fill, so a grey label on a pale
 *   disabled fill measured as louder at every pixel of every letter. So was the
 *   page-coloured ring round an enabled slider's thumb. Loudness is not a
 *   property of one pixel against the page; the ceiling is.
 * - **No colour.** Disabled is the grey of `contentDisabled`, not a faded
 *   version of the control's own colours, which is what the knob was: drawn at
 *   38%, a green-to-red scale stayed green-to-red. Measured as OKLCH chroma,
 *   against the scheme's own greys rather than zero: the dark schemes' are
 *   slate, tinted violet, and a disabled control is allowed to be as violet as
 *   the page it sits on. [KeepsItsColours] names the controls whose colours are
 *   their content.
 */
class DisabledQuietnessTest {

    @Test
    fun light() = sweep(dark = false, contrast = ContrastLevel.Standard)

    @Test
    fun dark() = sweep(dark = true, contrast = ContrastLevel.Standard)

    @Test
    fun highContrastLight() = sweep(dark = false, contrast = ContrastLevel.High)

    @Test
    fun highContrastDark() = sweep(dark = true, contrast = ContrastLevel.High)

    private fun sweep(dark: Boolean, contrast: ContrastLevel) {
        val scheme = kontourColourScheme(dark, contrast)
        val ground = scheme.background
        val ceiling = maxOf(
            contrastRatio(scheme.contentDisabled, ground),
            if (contrast == ContrastLevel.High) contrastRatio(scheme.outline, ground) else 1f,
        )
        val grey = greyCeiling(scheme)
        val louder = mutableListOf<String>()
        val colourful = mutableListOf<String>()
        for (spec in componentRegistry.filter { it.underContract && it.name !in PartlyDisabled }) {
            val disabled = render(spec, enabled = false, dark, contrast)
            var loud = 0
            var chromatic = 0
            for (y in 0 until disabled.height) {
                for (x in 0 until disabled.width) {
                    val d = Color(disabled.getRGB(x, y))
                    if (contrastRatio(d, ground) > ceiling + Louder) loud++
                    if (chroma(d) > grey) chromatic++
                }
            }
            if (loud > Allowance) louder += "${spec.name}: $loud px"
            if (chromatic > Allowance && spec.name !in KeepsItsColours) colourful += "${spec.name}: $chromatic px"
        }
        val name = (if (contrast == ContrastLevel.High) "high-contrast " else "") + if (dark) "dark" else "light"
        assertTrue(
            louder.isEmpty(),
            "${louder.size} disabled controls draw something louder than the disabled grey in $name, " +
                "whose ${"%.2f".format(ceiling)}:1 from the page is as loud as disabled gets:\n" +
                louder.joinToString("\n") { "  · $it" },
        )
        assertTrue(
            colourful.isEmpty(),
            "${colourful.size} disabled controls keep their colours in $name. Disabled is the " +
                "disabled grey, not the control's own colours faded:\n" +
                colourful.joinToString("\n") { "  · $it" },
        )
    }

    private fun render(spec: ComponentSpec, enabled: Boolean, dark: Boolean, contrast: ContrastLevel): BufferedImage =
        Scene(width = Width, height = spec.renderHeight?.times(2) ?: Height, reduceMotion = true, darkTheme = dark) {
            KontourTheme(darkTheme = dark, contrast = contrast, reduceMotion = true) {
                OverlayHost(Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Theme.colours.background)
                            .padding(Theme.spacing.md),
                        contentAlignment = Alignment.Center,
                    ) {
                        spec.content(Modifier, enabled) {}
                    }
                }
            }
        }.use { it.frames(Settle) }

    private companion object {
        const val Width = 600
        const val Height = 240

        /** Long enough for anything a control animates on first showing to have arrived. */
        const val Settle = 40

        /** How far past the ceiling a pixel may go, as a contrast ratio: rounding, not leeway. */
        const val Louder = 0.1f

        /** Pixels allowed past either line: the antialiased edges of a shape that moved by a fraction. */
        const val Allowance = 24

        /**
         * Controls whose colours are what they show, and so keep them disabled:
         * a disabled colour picker that turned its swatches grey would no longer
         * say which colours it offers.
         */
        val KeepsItsColours = setOf("ColourSwatchPicker", "ColourPicker")

        /**
         * Specimens whose `enabled = false` disables one part and not the whole:
         * a bar or rail of destinations with one of them disabled. The rest are
         * enabled and keep their colours, as they should.
         */
        val PartlyDisabled = setOf("NavBar", "NavRail")
    }
}

/** OKLCH chroma of an sRGB colour: how far it is from a grey. */
internal fun chroma(colour: Color): Float {
    fun linear(c: Float) = if (c <= 0.04045f) c / 12.92f else ((c + 0.055f) / 1.055f).pow(2.4f)
    val r = linear(colour.red)
    val g = linear(colour.green)
    val b = linear(colour.blue)
    val l = cbrt(0.4122214708f * r + 0.5363325363f * g + 0.0514459929f * b)
    val m = cbrt(0.2119034982f * r + 0.6806995451f * g + 0.1073969566f * b)
    val s = cbrt(0.0883024619f * r + 0.2817188376f * g + 0.6299787005f * b)
    val a = 1.9779984951f * l - 2.4285922050f * m + 0.4505937099f * s
    val bb = 0.0259040371f * l + 0.7827717662f * m - 0.8086757660f * s
    return sqrt(a * a + bb * bb)
}

/**
 * The most colourful a grey can be in [scheme]: as colourful as its most
 * colourful neutral, and a little more for the antialiasing between two of
 * them. Never below 0.035, which no one reads as a colour.
 */
internal fun greyCeiling(scheme: io.kontour.ui.theme.ColourScheme): Float = maxOf(
    0.035f,
    listOf(
        scheme.background, scheme.surface, scheme.surfaceSunken, scheme.surfaceRaised,
        scheme.content, scheme.contentMuted, scheme.contentSubtle, scheme.contentDisabled,
        scheme.outline, scheme.outlineStrong, scheme.outlineSubtle, scheme.inactive,
    ).maxOf(::chroma) + 0.012f,
)
