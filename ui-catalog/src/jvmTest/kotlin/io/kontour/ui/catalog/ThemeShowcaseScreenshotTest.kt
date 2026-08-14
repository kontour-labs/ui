package io.kontour.ui.catalog

import io.kontour.ui.theme.ContrastLevel
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Renders the token showcase in all four built-in schemes.
 *
 * The visual counterpart to `ColorSchemeContrastTest`: that one proves a pairing
 * is *legal*, this one lets a human see whether it is *right*. Both are needed —
 * a palette can clear every ratio and still look muddy.
 */
class ThemeShowcaseScreenshotTest {

    private data class Variant(val name: String, val dark: Boolean, val contrast: ContrastLevel)

    private val variants = listOf(
        Variant("theme-light", dark = false, contrast = ContrastLevel.Standard),
        Variant("theme-dark", dark = true, contrast = ContrastLevel.Standard),
        Variant("theme-light-high-contrast", dark = false, contrast = ContrastLevel.High),
        Variant("theme-dark-high-contrast", dark = true, contrast = ContrastLevel.High),
    )

    @Test
    fun rendersEveryBuiltInScheme() {
        for ((name, dark, contrast) in variants) {
            val file = Screenshot.render(name = name, width = 1100, height = 2080) {
                KontourTheme(
                    darkTheme = dark,
                    contrast = contrast,
                    // Pinned, not inherited: a golden that depends on the host's
                    // accessibility settings is not a golden.
                    reduceMotion = true,
                ) {
                    ThemeShowcase()
                }
            }
            assertTrue(file.length() > 0, "$name rendered an empty file")
        }
    }

    @Test
    fun rendersActionComponents() {
        for ((name, dark, contrast) in variants.filter { it.contrast == ContrastLevel.Standard }) {
            val file = Screenshot.render(
                name = name.replace("theme-", "actions-"),
                width = 1100,
                height = 1420,
            ) {
                KontourTheme(darkTheme = dark, contrast = contrast, reduceMotion = true) {
                    ButtonShowcase()
                }
            }
            assertTrue(file.length() > 0, "$name actions rendered an empty file")
        }
    }

    @Test
    fun rendersSelectionControls() {
        for ((name, dark, contrast) in variants.filter { it.contrast == ContrastLevel.Standard }) {
            val file = Screenshot.render(
                name = name.replace("theme-", "selection-"),
                width = 1100,
                height = 1720,
            ) {
                KontourTheme(darkTheme = dark, contrast = contrast, reduceMotion = true) {
                    SelectionShowcase()
                }
            }
            assertTrue(file.length() > 0, "$name selection rendered an empty file")
        }
    }
}
