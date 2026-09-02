package io.kontour.ui.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.kontour.ui.a11y.contrastRatio
import io.kontour.ui.components.display.Card
import io.kontour.ui.components.display.CardVariant
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The contrast tier reaches the components, not only the palette.
 *
 * `ContrastLevel` used to reach the UI through exactly one mechanism —
 * `kontourColourScheme(dark, contrast)` picking a different `ColourScheme` — and
 * `LocalContrastLevel` was provided by the theme and **read by nothing in the
 * repo**. What a user got for asking for high contrast was `outline` jumping
 * from `#E5E5E5` to `#767676`, so every bordered thing leapt and every
 * borderless one stayed exactly as it was.
 *
 * The two halves of the fix are asserted separately below, because they fail
 * separately: the tokens are a `remember` key, and the edge is a per-component
 * call. Both are the kind of thing that gets quietly dropped in a rewrite of the
 * component that owns them.
 *
 * The goldens are the other half of this — `actions-`, `display-`, `lists-`,
 * `selection-` and `text-*-high-contrast` — and they are what a human looks at.
 * These are what fails in CI without anyone looking.
 */
@OptIn(ExperimentalTestApi::class)
class ContrastLevelReachesComponentsTest {

    /**
     * Read through `KontourTheme` rather than from `kontourSizing` directly, so
     * this covers the wiring and not just the table. `sizing` was
     * `remember { Sizing() }` — no contrast key, no dark key — and a table with
     * the right numbers in it that nothing is keyed on is the bug this had.
     */
    @Test
    fun everyLineGetsThickerAtHighContrast() {
        val standard = sizingThroughTheme(ContrastLevel.Standard)
        val high = sizingThroughTheme(ContrastLevel.High)

        val lines = listOf(
            "borderWidth" to (standard.borderWidth to high.borderWidth),
            "borderWidthStrong" to (standard.borderWidthStrong to high.borderWidthStrong),
            "dividerThickness" to (standard.dividerThickness to high.dividerThickness),
            "focusRingWidth" to (standard.focusRingWidth to high.focusRingWidth),
            "selectionIndicator" to (standard.selectionIndicator to high.selectionIndicator),
        )
        for ((name, pair) in lines) {
            val (std, hc) = pair
            assertTrue(
                hc > std,
                "$name is $hc at both tiers — a hairline is a hairline whatever " +
                    "colour the scheme paints it",
            )
        }
    }

    private fun sizingThroughTheme(contrast: ContrastLevel): Sizing {
        lateinit var sizing: Sizing
        runComposeUiTest {
            setContent {
                KontourTheme(darkTheme = false, contrast = contrast, reduceMotion = true) {
                    sizing = Theme.sizing
                }
            }
            waitForIdle()
        }
        return sizing
    }

    /**
     * An elevated card is `surface` on `background`, which the high-contrast
     * light scheme makes the *same colour*, and its only edge is a shadow that
     * does not change between tiers. Sampling the boundary is the only way to
     * see that: the card is there in the semantics tree either way.
     */
    @Test
    fun aBorderlessContainerGainsAnEdgeAtHighContrast() {
        assertTrue(
            edgeContrast(ContrastLevel.High) > edgeContrast(ContrastLevel.Standard) + 1f,
            "an elevated card's edge did not change between the two tiers — " +
                "`LocalContrastLevel` is back to being read by nothing",
        )
    }

    /** The strongest ratio found along the card's left boundary, against the page. */
    private fun edgeContrast(contrast: ContrastLevel): Float {
        var best = 1f
        runComposeUiTest {
            setContent {
                KontourTheme(darkTheme = false, contrast = contrast, reduceMotion = true) {
                    Box(Modifier.testTag(Tag).size(120.dp)) {
                        Card(variant = CardVariant.Elevated, modifier = Modifier.size(80.dp)) {}
                    }
                }
            }
            waitForIdle()
            val pixels = onNodeWithTag(Tag).captureToImage().toPixelMap()
            // Across the card's left edge, halfway down it. The page is at x = 0,
            // the card's interior is well inside; whatever separates them is in
            // between.
            val y = pixels.height / 4
            val page = pixels[0, y]
            for (x in 0 until pixels.width / 2) {
                best = maxOf(best, contrastRatio(pixels[x, y], page.opaque()))
            }
        }
        return best
    }

    private fun Color.opaque(): Color = copy(alpha = 1f)

    private companion object {
        const val Tag = "page"
    }
}
