package io.kontour.ui.demo.theme

import io.kontour.ui.a11y.contrastFailures
import io.kontour.ui.a11y.contrastRatio
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

    /**
     * `primary` is structural, in every combination every theme claims.
     *
     * The library's counterpart is `PrimaryIsStructuralTest` in `:ui`, which
     * walks the four built-in schemes. This walks the demo themes, and it is the
     * one that has teeth: the built-ins have always held to the definition and
     * GTurbo did not, for two stages, while every contrast check passed and both
     * its goldens showed two identical red swatches under different names.
     *
     * `primary == accent.solid` is invisible to everything else here. Each
     * colour clears its own thresholds; what fails is the *distinction*, and a
     * ratio cannot see a distinction.
     */
    @Test
    fun everyThemeKeepsPrimaryDistinctFromItsAccent() {
        val collapsed = buildList {
            for (theme in demoThemes) {
                for (mode in theme.modes) {
                    for (tier in theme.tiers) {
                        val scheme = theme.colours(mode == ThemeMode.Dark, tier)
                        if (scheme.primary == scheme.accent.solid) {
                            add("${theme.name} $mode/$tier")
                        }
                    }
                }
            }
        }

        if (collapsed.isNotEmpty()) {
            fail(
                "${collapsed.size} scheme(s) give `primary` and `accent.solid` the " +
                    "same value:\n\n" + collapsed.joinToString("\n") { "  $it" } +
                    "\n\nThey are separate roles. `primary` is the structural " +
                    "call-to-action fill — near-black on light, near-white on dark " +
                    "— and is read by 34 sites in :ui including both floating " +
                    "action buttons, the slider tracks, the selected radio and " +
                    "every progress form. `accent` is the brand as a tone. A theme " +
                    "whose call to action is its brand colour wants " +
                    "`ButtonVariant.Accent`, not `primary`.",
            )
        }
    }

    /**
     * The selected tint can be read on, and at the standard tier it can be seen.
     *
     * `contrastFailures` walks the tint only for its own `onContainer`, and for
     * the edge `outlineStrong` can give it. What else lands on it — a selected
     * row's supporting line, a focused field's placeholder, a hovered
     * `TextButton`'s label in the accent — was checked by nothing, and neither
     * was how far the tint stands off the page. GTurbo's stood 1.11:1 off it,
     * with a selected chip told apart by its red alone.
     *
     * `SelectedTintTest` in `:ui` holds the built-in schemes to the same, with
     * the reasoning. The separation is asked of the standard tier only: the
     * enhanced tier rings every selected tint in the accent.
     */
    @Test
    fun everyThemesSelectedTintIsReadableAndSeen() {
        val weak = buildList {
            for (theme in demoThemes) {
                for (mode in theme.modes) {
                    for (tier in theme.tiers) {
                        val s = theme.colours(mode == ThemeMode.Dark, tier)
                        val tint = s.accent.container
                        val where = "${theme.name} $mode/$tier"
                        for ((name, colour) in listOf(
                            "content" to s.content,
                            "contentMuted" to s.contentMuted,
                            "contentSubtle" to s.contentSubtle,
                            "accent.solid" to s.accent.solid,
                            "accent.onContainer" to s.accent.onContainer,
                        )) {
                            val ratio = contrastRatio(colour, tint)
                            if (ratio < 4.5f) add("[$where] $name on accent.container: $ratio:1, needs 4.5")
                        }
                        if (tier == ContrastLevel.Standard) {
                            val ratio = contrastRatio(tint, s.background)
                            if (ratio < 1.3f) add("[$where] accent.container on background: $ratio:1, needs 1.3")
                        }
                    }
                }
            }
        }

        if (weak.isNotEmpty()) {
            fail(
                "${weak.size} pairing(s) with the selected tint fall short:\n\n" +
                    weak.joinToString("\n") { "  $it" } +
                    "\n\nA tint is held from both ends: far enough off the page to be " +
                    "seen, near enough that what is drawn on it can be read.",
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

    /**
     * And the contrast switch is live under both, which it was not.
     *
     * These two booleans are read straight into the `enabled` of a switch, in
     * the gallery and on the documentation site — `Catalog.kt:391` and
     * `Settings.kt:67`. GTurbo declared one tier, so its high-contrast toggle was
     * drawn inert; authoring an enhanced palette turned it on with no wiring,
     * which is what `DemoTheme` promises and worth an assertion rather than a
     * hope.
     */
    @Test
    fun everyThemeOffersTheContrastSwitchSomethingToDo() {
        assertEquals(
            true, gTurboDemoTheme.offersBothTiers,
            "GTurbo authored an enhanced palette, so the high-contrast switch has " +
                "to stop being drawn disabled",
        )
        assertEquals(
            true, kontourDemoTheme.offersBothTiers,
            "the library's own theme has both tiers and always has",
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
