package io.kontour.ui.theme

import androidx.compose.ui.graphics.Color
import io.kontour.ui.a11y.ContrastThreshold
import io.kontour.ui.a11y.contrastRatio
import kotlin.test.Test
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
 * Deliberately excluded:
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
        val failures = mutableListOf<Failure>()

        for ((name, c, tier) in schemes) {
            val bodyText = when (tier) {
                ContrastLevel.Standard -> ContrastThreshold.BODY_TEXT
                ContrastLevel.High -> ContrastThreshold.BODY_TEXT_ENHANCED
            }
            val nonText = when (tier) {
                ContrastLevel.Standard -> ContrastThreshold.NON_TEXT
                ContrastLevel.High -> ContrastThreshold.LARGE_TEXT_ENHANCED
            }

            fun check(fgName: String, fg: Color, bgName: String, bg: Color, required: Float) {
                val ratio = contrastRatio(fg, bg)
                if (ratio < required) {
                    failures += Failure(name, "$fgName on $bgName", ratio, required)
                }
            }

            // Text and control boundaries against every ground they can land on.
            for ((groundName, ground) in c.grounds()) {
                check("content", c.content, groundName, ground, bodyText)
                check("contentMuted", c.contentMuted, groundName, ground, bodyText)
                check("contentSubtle", c.contentSubtle, groundName, ground, bodyText)
                check("outlineStrong", c.outlineStrong, groundName, ground, nonText)
                check("focusRing", c.focusRing, groundName, ground, nonText)
            }

            // Labels on solid fills, and the fills themselves against the page.
            val solids = listOf(
                Triple("primary", c.primary, c.onPrimary),
                Triple("accent", c.accent.solid, c.accent.onSolid),
                Triple("success", c.success.solid, c.success.onSolid),
                Triple("warning", c.warning.solid, c.warning.onSolid),
                Triple("danger", c.danger.solid, c.danger.onSolid),
                Triple("info", c.info.solid, c.info.onSolid),
            )
            for ((toneName, solid, onSolid) in solids) {
                check("on$toneName", onSolid, toneName, solid, bodyText)
                check(toneName, solid, "background", c.background, nonText)
            }

            // Text on tinted containers.
            val containers = listOf(
                Triple("accent.container", c.accent.container, c.accent.onContainer),
                Triple("successContainer", c.success.container, c.success.onContainer),
                Triple("warningContainer", c.warning.container, c.warning.onContainer),
                Triple("dangerContainer", c.danger.container, c.danger.onContainer),
                Triple("infoContainer", c.info.container, c.info.onContainer),
            )
            for ((containerName, container, onContainer) in containers) {
                check("on$containerName", onContainer, containerName, container, bodyText)
            }

            check("onSurfaceInverse", c.onSurfaceInverse, "surfaceInverse", c.surfaceInverse, bodyText)
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
                ContrastLevel.Standard -> ContrastThreshold.NON_TEXT
                ContrastLevel.High -> ContrastThreshold.LARGE_TEXT_ENHANCED
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
     * A brand colour an app supplies is allowed to fail contrast — that is the
     * whole reason [ColourScheme.brand] is not [ColourScheme.accent] — but only
     * where nothing has to be read on it.
     *
     * This used to assert the opposite: that brand *must* fail, because brand
     * was the literal Kontour purple and 2.1:1 on white. The library has no
     * product colour now, so there is nothing to assert about the default. What
     * is worth keeping is the check itself, as something an app can run against
     * its own scheme — which is what [brandIsSafeForText] is for, and what
     * `anyways` calls on `KontourBrandTheme`.
     */
    @Test
    fun theDefaultBrandIsTheAccentUntilAnAppSetsOne() {
        for ((name, colours) in listOf("light" to lightColourScheme(), "dark" to darkColourScheme())) {
            if (colours.brand != colours.accent.solid) {
                fail(
                    "the default $name scheme's brand ($name) has drifted from its accent. " +
                        "A library with no product in it should have nothing to say about " +
                        "brand — if that changed on purpose, say so here."
                )
            }
        }
    }

    @Test
    fun accentCarriesTextEverywhereBrandCannot() {
        for ((name, colours) in listOf("light" to lightColourScheme(), "dark" to darkColourScheme())) {
            val ratio = contrastRatio(colours.accent.solid, colours.background)
            if (ratio < ContrastThreshold.NON_TEXT) {
                fail("accent fails non-text contrast in $name: $ratio:1")
            }
        }
    }
}
