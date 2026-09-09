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
 * The visual counterpart to `ColourSchemeContrastTest`: that one proves a pairing
 * is *legal*, this one lets a human see whether it is *right*. Both are needed —
 * a palette can clear every ratio and still look muddy.
 *
 * ### What used to be here, and where it went
 *
 * This also rendered nine family showcases in four schemes each — roughly
 * thirty-two goldens of whole pages. They went with the panels they drew, in the
 * change that made the gallery generate its pages from the demos.
 *
 * The reason they existed was real and is not lost. `ContrastLevel` reached the
 * UI through exactly one mechanism — a different `ColourScheme` — so the
 * high-contrast tier showed `outline` jumping while every filled and borderless
 * container stayed as it was, and that went unnoticed for as long as it did
 * because the tier was only ever screenshotted as a *palette*, never as
 * components. The five places it lived were containers leaning on a shadow for
 * their edge, buttons whose variants have no border, the selection controls, and
 * a filled text field whose border was transparent at every tier.
 *
 * Those five now sit inside `ThemeShowcase` itself, so the four goldens below
 * carry what twenty carried — in an image a person will actually scroll through,
 * rather than nine that nobody opened.
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
            val file = Screenshot.render(name = name, width = 1620, height = 2800) {
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
}
