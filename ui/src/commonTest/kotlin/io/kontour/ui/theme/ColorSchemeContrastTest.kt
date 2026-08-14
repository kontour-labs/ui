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
class ColorSchemeContrastTest {

    private data class Scheme(val name: String, val colors: ColorScheme, val contrast: ContrastLevel)

    private val schemes = listOf(
        Scheme("light", lightColorScheme(), ContrastLevel.Standard),
        Scheme("dark", darkColorScheme(), ContrastLevel.Standard),
        Scheme("light/high-contrast", highContrastLightColorScheme(), ContrastLevel.High),
        Scheme("dark/high-contrast", highContrastDarkColorScheme(), ContrastLevel.High),
    )

    private class Failure(val scheme: String, val pair: String, val actual: Float, val required: Float)

    private fun ColorScheme.grounds(): List<Pair<String, Color>> = listOf(
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
                Triple("accent", c.accent, c.onAccent),
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
                Triple("accentContainer", c.accentContainer, c.onAccentContainer),
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

    private fun format(value: Float): String {
        val scaled = (value * 100).toInt()
        return "${scaled / 100}.${(scaled % 100).toString().padStart(2, '0')}"
    }
}

/**
 * Pins the contract that makes [ColorScheme.brand] and [ColorScheme.accent] two
 * separate tokens rather than one.
 */
class BrandIsDecorativeOnlyTest {

    @Test
    fun brandPurpleCannotCarryTextInLightMode() {
        val light = lightColorScheme()
        val ratio = contrastRatio(light.brand, light.background)
        if (ratio >= ContrastThreshold.BODY_TEXT) {
            fail(
                "brand now clears body-text contrast (${ratio}:1) against the light background. " +
                    "If that is intentional, brand and accent can be merged — but update " +
                    "the KDoc on ColorScheme.brand and this test together."
            )
        }
    }

    @Test
    fun accentCarriesTextEverywhereBrandCannot() {
        for ((name, colors) in listOf("light" to lightColorScheme(), "dark" to darkColorScheme())) {
            val ratio = contrastRatio(colors.accent, colors.background)
            if (ratio < ContrastThreshold.NON_TEXT) {
                fail("accent fails non-text contrast in $name: $ratio:1")
            }
        }
    }
}
