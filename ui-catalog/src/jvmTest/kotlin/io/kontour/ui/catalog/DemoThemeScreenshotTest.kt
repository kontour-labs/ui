package io.kontour.ui.catalog

import io.kontour.ui.demo.theme.DemoThemeProvider
import io.kontour.ui.demo.theme.ThemeMode
import io.kontour.ui.theme.ContrastLevel
import io.kontour.ui.demo.theme.demoThemes
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The token showcase under every demo theme.
 *
 * `ThemeShowcaseScreenshotTest` does this for the four built-in schemes; this is
 * the same picture for a theme that is not the library's own, and it is the one
 * place a palette gets *looked* at rather than only measured. The contrast suite
 * proves the ratios; only an image says whether the result is any good.
 *
 * ### It walks modes **and** tiers, which it did not
 *
 * `DemoTheme`'s one rule is that a theme answers for every combination in
 * `modes` × `tiers` and claims none it cannot serve, and the contrast suite has
 * always walked exactly that. This walked `modes` and pinned `highContrast =
 * false`, which was invisible while every demo theme declared one tier. The
 * moment GTurbo authored an enhanced palette, the two suites disagreed about
 * what "every combination the theme claims" means — and the tier that is hardest
 * to get right would have been the one with no picture.
 */
class DemoThemeScreenshotTest {

    @AfterTest
    fun allGoldensMatched() = Screenshot.assertAllMatched()

    @Test
    fun rendersEveryDemoTheme() {
        for (theme in demoThemes.filter { it !== demoThemes.first() }) {
            for (mode in theme.modes) {
                for (tier in theme.tiers) {
                    val enhanced = tier == ContrastLevel.High
                    val settings = CatalogSettings().apply {
                        this.theme = theme
                        dark = mode == ThemeMode.Dark
                        highContrast = enhanced
                        reduceMotion = true
                    }
                    // The standard tier keeps the name it had, so its golden does
                    // not move for a reason that is only about naming.
                    val name = "theme-${theme.name.lowercase()}-${mode.name.lowercase()}" +
                        if (enhanced) "-high-contrast" else ""
                    val file = Screenshot.render(name = name, width = 1620, height = 2800) {
                        DemoThemeProvider(
                            settings = settings,
                            systemDark = true,
                            systemHighContrast = enhanced,
                            systemReduceMotion = true,
                        ) {
                            ThemeShowcase()
                        }
                    }
                    assertTrue(file.length() > 0, "$name rendered an empty file")
                }
            }
        }
    }
}
