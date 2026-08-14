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

    @Test
    fun rendersTextFields() {
        for ((name, dark, contrast) in variants.filter { it.contrast == ContrastLevel.Standard }) {
            val file = Screenshot.render(
                name = name.replace("theme-", "text-"),
                // Canvas is in pixels at 2x density, so this is ~920dp of layout width.
                width = 1840,
                height = 1560,
            ) {
                KontourTheme(darkTheme = dark, contrast = contrast, reduceMotion = true) {
                    TextShowcase()
                }
            }
            assertTrue(file.length() > 0, "$name text rendered an empty file")
        }
    }

    @Test
    fun rendersDateAndTime() {
        for ((name, dark, contrast) in variants.filter { it.contrast == ContrastLevel.Standard }) {
            val file = Screenshot.render(
                name = name.replace("theme-", "datetime-"),
                width = 2280,
                height = 1420,
            ) {
                KontourTheme(darkTheme = dark, contrast = contrast, reduceMotion = true) {
                    DateTimeShowcase()
                }
            }
            assertTrue(file.length() > 0, "$name datetime rendered an empty file")
        }
    }

    @Test
    fun rendersOverlays() {
        for ((name, dark, contrast) in variants.filter { it.contrast == ContrastLevel.Standard }) {
            val file = Screenshot.render(
                name = name.replace("theme-", "overlays-"),
                width = 2320,
                height = 2060,
                // Overlays need more than the usual handful. Each one has to be
                // laid out before its anchor is known, pushed into the host on
                // the recomposition after that, and then animated in — three
                // frames deep before anything is drawn at its final size.
                frames = 40,
            ) {
                KontourTheme(darkTheme = dark, contrast = contrast, reduceMotion = true) {
                    OverlayShowcase()
                }
            }
            assertTrue(file.length() > 0, "$name overlays rendered an empty file")
        }
    }

    @Test
    fun rendersFormControls() {
        for ((name, dark, contrast) in variants.filter { it.contrast == ContrastLevel.Standard }) {
            val file = Screenshot.render(
                name = name.replace("theme-", "forms-"),
                width = 3400,
                height = 1240,
                frames = 40,
            ) {
                KontourTheme(darkTheme = dark, contrast = contrast, reduceMotion = true) {
                    SelectShowcase()
                }
            }
            assertTrue(file.length() > 0, "$name forms rendered an empty file")
        }
    }

    @Test
    fun rendersSheets() {
        for ((name, dark, contrast) in variants.filter { it.contrast == ContrastLevel.Standard }) {
            val file = Screenshot.render(
                name = name.replace("theme-", "sheets-"),
                width = 2960,
                height = 1240,
                frames = 60,
            ) {
                KontourTheme(darkTheme = dark, contrast = contrast, reduceMotion = true) {
                    SheetShowcase()
                }
            }
            assertTrue(file.length() > 0, "$name sheets rendered an empty file")
        }
    }

    @Test
    fun rendersCollections() {
        for ((name, dark, contrast) in variants.filter { it.contrast == ContrastLevel.Standard }) {
            val file = Screenshot.render(
                name = name.replace("theme-", "lists-"),
                width = 2440,
                height = 1080,
                frames = 20,
            ) {
                KontourTheme(darkTheme = dark, contrast = contrast, reduceMotion = true) {
                    ListShowcase()
                }
            }
            assertTrue(file.length() > 0, "$name collections rendered an empty file")
        }
    }

    @Test
    fun rendersNavigation() {
        for ((name, dark, contrast) in variants.filter { it.contrast == ContrastLevel.Standard }) {
            val file = Screenshot.render(
                name = name.replace("theme-", "nav-"),
                width = 4160,
                height = 2200,
                frames = 30,
            ) {
                KontourTheme(darkTheme = dark, contrast = contrast, reduceMotion = true) {
                    NavShowcase()
                }
            }
            assertTrue(file.length() > 0, "$name navigation rendered an empty file")
        }
    }

    @Test
    fun rendersDisplayComponents() {
        for ((name, dark, contrast) in variants.filter { it.contrast == ContrastLevel.Standard }) {
            val file = Screenshot.render(
                name = name.replace("theme-", "display-"),
                width = 2560,
                height = 1560,
            ) {
                KontourTheme(darkTheme = dark, contrast = contrast, reduceMotion = true) {
                    DisplayShowcase()
                }
            }
            assertTrue(file.length() > 0, "$name display rendered an empty file")
        }
    }
}
