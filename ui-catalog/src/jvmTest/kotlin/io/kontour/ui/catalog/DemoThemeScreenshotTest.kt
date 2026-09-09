package io.kontour.ui.catalog

import io.kontour.ui.demo.theme.DemoThemeProvider
import io.kontour.ui.demo.theme.ThemeMode
import io.kontour.ui.demo.theme.demoThemes
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The token showcase under every demo theme.
 *
 * `ThemeShowcaseScreenshotTest` does this for the four built-in schemes; this is
 * the same picture for a theme that is not the library's own, and it is the one
 * place a palette gets *looked* at rather than only measured.
 * `DemoThemeContrastTest` proves the ratios; only an image says whether the
 * result is any good.
 */
class DemoThemeScreenshotTest {

    @AfterTest
    fun allGoldensMatched() = Screenshot.assertAllMatched()

    @Test
    fun rendersEveryDemoTheme() {
        for (theme in demoThemes.filter { it !== demoThemes.first() }) {
            for (mode in theme.modes) {
                val settings = CatalogSettings().apply {
                    this.theme = theme
                    dark = mode == ThemeMode.Dark
                    highContrast = false
                    reduceMotion = true
                }
                val name = "theme-${theme.name.lowercase()}-${mode.name.lowercase()}"
                val file = Screenshot.render(name = name, width = 1620, height = 2800) {
                    DemoThemeProvider(
                        settings = settings,
                        systemDark = true,
                        systemHighContrast = false,
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
