package io.kontour.ui.demo.theme

import io.kontour.ui.a11y.contrastFailures
import io.kontour.ui.theme.ContrastLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Every demo theme, held to the check the built-in schemes are held to.
 *
 * A theme added to [demoThemes] is a theme nobody has eyeballed at every
 * pairing, which is exactly the case `contrastFailures` exists for — three of
 * the values originally proposed for the library's own schemes looked fine and
 * failed by a tenth of a point. Adding a theme is one line; being gated for it
 * should be free, and this is what makes it so.
 *
 * It walks **every combination the theme claims**, which is what gives
 * [DemoTheme.modes] and [DemoTheme.tiers] teeth: declaring a tier is a promise
 * to have authored a palette for it, and this is where that promise is kept.
 */
class DemoThemeContrastTest {

    @Test
    fun everyThemeClearsEveryTierItClaims() {
        val failures = buildList {
            for (theme in demoThemes) {
                for (mode in theme.modes) {
                    for (tier in theme.tiers) {
                        val dark = mode == ThemeMode.Dark
                        contrastFailures(theme.colours(dark, tier), tier).forEach {
                            add("[${theme.name} $mode/$tier] $it")
                        }
                    }
                }
            }
        }

        if (failures.isNotEmpty()) {
            fail(
                "${failures.size} colour pairing(s) in the demo themes fail their " +
                    "contrast requirement:\n\n" + failures.joinToString("\n") { "  $it" } +
                    "\n\nAdjust the theme's value, not the threshold.",
            )
        }
    }

    @Test
    fun aThemeDrawsOnlyWhatItClaims() {
        // The resolution order is the reader's preference, then the theme's
        // policy — so a dark-only theme asked for light gives dark rather than
        // an empty palette or a crash.
        for (theme in demoThemes) {
            for (requested in listOf(true, false)) {
                val resolved = if (theme.resolveDark(requested)) ThemeMode.Dark else ThemeMode.Light
                assertTrue(
                    resolved in theme.modes,
                    "${theme.name} resolved a request for dark=$requested to $resolved, " +
                        "which is not among the modes it claims (${theme.modes})",
                )
            }
            for (requested in ContrastLevel.entries) {
                assertTrue(
                    theme.resolveTier(requested) in theme.tiers,
                    "${theme.name} resolved a request for $requested outside its " +
                        "declared tiers (${theme.tiers})",
                )
            }
        }
    }

    @Test
    fun aThemeThatOffersOneModeSaysSo() {
        // The switch the reader sees is drawn from these, so a theme whose
        // `modes` disagreed with what it actually draws would render a live
        // control that does nothing.
        assertEquals(
            false, gTurboDemoTheme.offersBothModes,
            "GTurbo is a near-black product and claims one mode; if that changes, " +
                "the dark switch has to stop being drawn disabled",
        )
        assertEquals(
            true, kontourDemoTheme.offersBothModes,
            "the library's own theme has both modes and always has",
        )
    }

    @Test
    fun theKontourThemeIsStillTheBuiltInOne() {
        // Cheap, and it catches the mistake where a demo theme is edited into
        // the default's slot: the first entry is what a reader gets before they
        // have chosen anything.
        assertEquals(
            "Kontour", demoThemes.first().name,
            "the first demo theme is what the gallery and the site show by default",
        )
    }
}
