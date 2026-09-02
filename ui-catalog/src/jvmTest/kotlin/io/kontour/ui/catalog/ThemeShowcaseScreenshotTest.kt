package io.kontour.ui.catalog

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.ContrastLevel
import io.kontour.ui.theme.KontourTheme
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Renders the token showcase in all four built-in schemes.
 *
 * The visual counterpart to `ColorSchemeContrastTest`: that one proves a pairing
 * is *legal*, this one lets a human see whether it is *right*. Both are needed —
 * a palette can clear every ratio and still look muddy.
 *
 * ### Why five of these render at high contrast too
 *
 * Because nothing ever had. `ContrastLevel` reached the UI through exactly one
 * mechanism — picking a different `ColorScheme` — and `LocalContrastLevel` was
 * read by nothing in the repo. What that looked like on screen was `outline`
 * jumping while every filled and borderless container stayed exactly as it was,
 * and it went unnoticed for as long as it did because the high-contrast tier was
 * only ever screenshotted as a palette, never as components.
 *
 * The five below are where the gap lived: containers that lean on a shadow for
 * their edge (`Card`, `Chip`, `ListItem`), buttons whose variants have no
 * border, the selection controls, and `TextFieldVariant.Filled`, whose border
 * was transparent at every tier.
 */
class ThemeShowcaseScreenshotTest {

    /** Reports every golden that moved, not just the first one in a loop. */
    @AfterTest
    fun allGoldensMatched() = Screenshot.assertAllMatched()

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
            val file = Screenshot.render(name = name, width = 1620, height = 2080) {
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
        for ((name, dark, contrast) in variants) {
            val file = Screenshot.render(
                name = name.replace("theme-", "actions-"),
                width = 1100,
                height = 2200,
            ) {
                KontourTheme(darkTheme = dark, contrast = contrast, reduceMotion = true) {
                    // `FabMenu` renders its items into the host, so it asks for
                    // one at composition whether or not it is open — the same
                    // as every other overlay-backed component on these pages.
                    OverlayHost(Modifier.fillMaxSize()) { ButtonShowcase() }
                }
            }
            assertTrue(file.length() > 0, "$name actions rendered an empty file")
        }
    }

    @Test
    fun rendersSelectionControls() {
        for ((name, dark, contrast) in variants) {
            val file = Screenshot.render(
                name = name.replace("theme-", "selection-"),
                width = 1200,
                height = 2380,
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
        for ((name, dark, contrast) in variants) {
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
                    // The time field opens its picker in a modal sheet, and a
                    // sheet demands a host at composition rather than when it is
                    // first shown — which is the right way round: the alternative
                    // is a crash the first time a user taps it.
                    OverlayHost(Modifier.fillMaxSize()) { DateTimeShowcase() }
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
                // Four panels per row. The panels flow, and a width that
                // leaves slack for a fifth to start without room to finish
                // draws it as a column of single letters.
                // Taller since the command palette's panel grew: the palette is
                // the one overlay in here that wants a window rather than a
                // card, and a 300dp stand-in clipped it.
                width = 3120,
                height = 2440,
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
                width = 3440,
                height = 1240,
                frames = 40,
            ) {
                KontourTheme(darkTheme = dark, contrast = contrast, reduceMotion = true) {
                    // A root host, as `Catalog` gives the real page. The panels
                    // used to install one each, which is what made a select
                    // dismissable only inside its own column.
                    OverlayHost(Modifier.fillMaxSize()) { SelectShowcase() }
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
                // Five panels of 340dp at 2x, plus their gaps. Grown when the
                // header-sizes panel arrived, and the harness is why: it measured
                // the page at 3688 against a 2960 canvas and refused, rather than
                // quietly clipping a panel off the edge and passing.
                width = 3760,
                height = 1280,
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
        for ((name, dark, contrast) in variants) {
            val file = Screenshot.render(
                name = name.replace("theme-", "lists-"),
                width = 2480,
                height = 1500,
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
                height = 4380,
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
    fun rendersAdaptiveLayouts() {
        for ((name, dark, contrast) in variants.filter { it.contrast == ContrastLevel.Standard }) {
            val file = Screenshot.render(
                name = name.replace("theme-", "adaptive-"),
                width = 4560,
                height = 2100,
                frames = 30,
            ) {
                KontourTheme(darkTheme = dark, contrast = contrast, reduceMotion = false) {
                    AdaptiveShowcase()
                }
            }
            assertTrue(file.length() > 0, "$name adaptive rendered an empty file")
        }
    }

    @Test
    fun rendersDisplayComponents() {
        for ((name, dark, contrast) in variants) {
            val file = Screenshot.render(
                name = name.replace("theme-", "display-"),
                width = 2640,
                height = 3060,
            ) {
                KontourTheme(darkTheme = dark, contrast = contrast, reduceMotion = true) {
                    DisplayShowcase()
                }
            }
            assertTrue(file.length() > 0, "$name display rendered an empty file")
        }
    }
}
