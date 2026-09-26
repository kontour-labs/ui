package io.kontour.ui.theme

import androidx.compose.ui.graphics.Color
import io.kontour.ui.a11y.ContrastThreshold
import io.kontour.ui.a11y.brandIsSafeForText
import io.kontour.ui.a11y.contrastFailures
import io.kontour.ui.a11y.contrastRatio
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Walks every foreground/background pairing a component is allowed to produce,
 * in every built-in scheme, and asserts it clears WCAG.
 *
 * This is the load-bearing accessibility test. Contrast is not something you can
 * eyeball — three of the values originally proposed for these schemes looked
 * fine and failed by a tenth of a point, and one of them (`#BB86FC` on white,
 * at 2.1:1) is shipping on the marketing site today. Anyone adding a token or
 * retuning a palette will be told immediately, by name, which pairing broke.
 *
 * The walk is [io.kontour.ui.a11y.contrastFailures], which is public so an app
 * authoring its own palette can run it. What is deliberately excluded, and why,
 * is documented there. In summary:
 *  - `contentDisabled`, `outline`, `outlineSubtle`, and the status `border`
 *    tones. WCAG 1.4.3 exempts disabled controls, and 1.4.11 exempts purely
 *    decorative rules. Holding them to a ratio would force dividers so dark
 *    they read as borders.
 *  - `brand`, which exists precisely because it *cannot* pass in light mode.
 *    That is the token's documented contract; [BrandIsDecorativeOnlyTest]
 *    pins it so nobody promotes it to a text colour by accident.
 */
class ColourSchemeContrastTest {

    private data class Scheme(val name: String, val colours: ColourScheme, val contrast: ContrastLevel)

    private val schemes = listOf(
        Scheme("light", lightColourScheme(), ContrastLevel.Standard),
        Scheme("dark", darkColourScheme(), ContrastLevel.Standard),
        Scheme("light/high-contrast", highContrastLightColourScheme(), ContrastLevel.High),
        Scheme("dark/high-contrast", highContrastDarkColourScheme(), ContrastLevel.High),
    )

    private class Failure(val scheme: String, val pair: String, val actual: Float, val required: Float)

    private fun ColourScheme.grounds(): List<Pair<String, Color>> = listOf(
        "background" to background,
        "surface" to surface,
        "surfaceSunken" to surfaceSunken,
        "surfaceRaised" to surfaceRaised,
    )

    @Test
    fun everyBuiltInSchemeMeetsItsContrastTier() {
        // The walk itself is `contrastFailures`, in `a11y`, because a consumer
        // authoring a palette needs exactly this and used to be told to copy it
        // out of a test they cannot see. What stays here is the part that is a
        // *test*: which schemes get walked, and a message grouped by scheme.
        val failures = schemes.flatMap { (name, colours, tier) ->
            contrastFailures(colours, tier).map { Failure(name, it.pair, it.ratio, it.required) }
        }

        if (failures.isNotEmpty()) {
            fail(
                buildString {
                    appendLine("${failures.size} colour pairing(s) fail their contrast requirement:")
                    appendLine()
                    failures
                        .groupBy { it.scheme }
                        .forEach { (scheme, group) ->
                            appendLine("  [$scheme]")
                            group.forEach {
                                appendLine(
                                    "    ${it.pair}: ${format(it.actual)}:1, needs ${format(it.required)}:1"
                                )
                            }
                        }
                    appendLine()
                    appendLine("Adjust the value in Palette.kt, not the threshold here.")
                }
            )
        }
    }

    @Test
    fun aFilledSwitchTrackSeparatesFromEveryGroundItSitsOn() {
        // `Switch` fills its off track with `outlineStrong` rather than leaving it
        // outlined and empty. The old note against filling it was that "a grey
        // track sits too close in tone to the surfaces it is toggled on top of",
        // which is true of the surface ramp — `surfaceSunken` is a hair off
        // `surface` in light mode — and is exactly why the fill is the token that
        // exists to bound an interactive control instead.
        //
        // The thumb rides on that track and has to separate from it too.
        val failures = mutableListOf<Failure>()

        for ((name, c, tier) in schemes) {
            val required = when (tier) {
                ContrastLevel.Standard -> ContrastThreshold.NonText
                ContrastLevel.High -> ContrastThreshold.LargeTextEnhanced
            }

            for ((groundName, ground) in c.grounds()) {
                val ratio = contrastRatio(c.outlineStrong, ground)
                if (ratio < required) {
                    failures += Failure(name, "switch track on $groundName", ratio, required)
                }
            }

            for ((trackName, track) in listOf("off" to c.outlineStrong, "on" to c.primary)) {
                val ratio = contrastRatio(c.onPrimary, track)
                if (ratio < required) {
                    failures += Failure(name, "switch thumb on the $trackName track", ratio, required)
                }
            }
        }

        if (failures.isNotEmpty()) {
            fail(
                failures.joinToString(prefix = "the switch does not separate from what it sits on:\n") {
                    "  [${it.scheme}] ${it.pair}: ${format(it.actual)}:1, needs ${format(it.required)}:1"
                }
            )
        }
    }

    private fun format(value: Float): String {
        val scaled = (value * 100).toInt()
        return "${scaled / 100}.${(scaled % 100).toString().padStart(2, '0')}"
    }
}

/**
 * Pins the contract that makes [ColourScheme.brand] and [ColourScheme.accent] two
 * separate tokens rather than one.
 */
class BrandIsDecorativeOnlyTest {

    /**
     * A brand colour is allowed to fail contrast — that is the whole reason
     * [ColourScheme.brand] is not [ColourScheme.accent] — but only where nothing
     * has to be read on it.
     *
     * The default is Kontour Labs' violet, `#BB86FC`, in both schemes. In dark it
     * is the accent as well: 7.07:1 on ink carries text and labels. In light it
     * is a mark only — 2.65:1 on white — and the accent is the deeper violet of
     * the same hue beside it. That split is the contract this pins: if the light
     * brand ever became safe for text, the two tokens would have converged and
     * one of them would be wrong.
     */
    @Test
    fun theDefaultBrandIsKontourLabsViolet() {
        val light = lightColourScheme()
        val dark = darkColourScheme()
        for ((name, colours) in listOf("light" to light, "dark" to dark)) {
            if (colours.brand != Color(0xFFBB86FC)) {
                fail("the default $name scheme's brand is ${colours.brand}, not Kontour Labs' #BB86FC")
            }
        }
        if (brandIsSafeForText(light)) {
            fail("the light brand now carries text, so it and the light accent have converged")
        }
        if (light.brand == light.accent.solid) {
            fail("the light accent is the brand again, and the brand cannot carry a white label")
        }
        if (dark.brand != dark.accent.solid) {
            fail("the dark accent has drifted from the brand it is meant to be")
        }
    }

    @Test
    fun accentCarriesTextEverywhereBrandCannot() {
        for ((name, colours) in listOf("light" to lightColourScheme(), "dark" to darkColourScheme())) {
            val ratio = contrastRatio(colours.accent.solid, colours.background)
            if (ratio < ContrastThreshold.NonText) {
                fail("accent fails non-text contrast in $name: $ratio:1")
            }
        }
    }
}

/**
 * Pins the other half of that contract: [ColourScheme.primary] is **structural**.
 *
 * Three roles carry a product's colour and they are not interchangeable.
 * [ColourScheme.accent] is the brand as a tone and is under every contrast
 * obligation. [ColourScheme.brand] is the literal mark and is under none — it is
 * the one role `contrastFailures` does not walk. [ColourScheme.primary] is
 * neither: its own KDoc defines it as "the solid call-to-action fill: near-black
 * on light, near-white on dark", and all four built-in schemes are `Palette.Ink`,
 * `Palette.Paper`, `Palette.Black` and `Palette.White`.
 *
 * ### Why this needed an assertion and `brand` did not
 *
 * `brand == accent.solid` is the *documented* default — the library ships no
 * product, so brand resolves to the accent until an app sets one, and
 * `ThemeShowcase` labels that swatch "brand — unset" when it happens. Collapse
 * there is expected and visible.
 *
 * `primary == accent.solid` is a defect and is **invisible**. A theme that wires
 * both to its brand colour draws two identical swatches under different names
 * and, more to the point, drags all 34 sites that read `primary` along with the
 * accent — both floating action buttons, `Slider` and `RangeSlider`'s active
 * track, `RadioButton`'s mark, all three `Progress` forms, `Timeline`'s nodes,
 * `Carousel`'s indicator, `CalendarMonth`'s selected day. Nothing errors and
 * every contrast check still passes, because each colour is fine on its own.
 *
 * The GTurbo demo theme did exactly that for two stages, and the goldens showed
 * it plainly the whole time. A picture is not a check.
 */
class PrimaryIsStructuralTest {

    @Test
    fun noBuiltInSchemeUsesItsAccentAsItsPrimary() {
        val collapsed = listOf(
            "light" to lightColourScheme(),
            "dark" to darkColourScheme(),
            "light/high-contrast" to highContrastLightColourScheme(),
            "dark/high-contrast" to highContrastDarkColourScheme(),
        ).filter { (_, scheme) -> scheme.primary == scheme.accent.solid }

        assertTrue(
            collapsed.isEmpty(),
            "${collapsed.size} built-in scheme(s) have primary == accent.solid: " +
                collapsed.joinToString(", ") { it.first } + ". They are separate " +
                "roles — primary is the structural call-to-action fill and accent " +
                "is the brand as a tone — and a scheme that gives them one value " +
                "leaves no component able to tell them apart.",
        )
    }
}
