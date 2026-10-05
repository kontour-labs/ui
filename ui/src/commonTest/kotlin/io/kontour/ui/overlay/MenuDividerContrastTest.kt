package io.kontour.ui.overlay

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import io.kontour.ui.a11y.contrastRatio
import io.kontour.ui.theme.ContrastLevel
import io.kontour.ui.theme.KontourTheme
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A menu's divider can be seen on the menu.
 *
 * Asked for as *"can we please take a look at the contrast for the divider
 * colour in the dropdown menu, especially in dark mode"*. The divider was
 * `outlineSubtle`, the faintest rule the scheme has, chosen for dense lists on
 * `surface` and for a menu's border on the page. Inside a menu it sits on
 * `surfaceRaised` instead: 1.15:1 in light, and in dark 1.06:1 and *darker*
 * than the panel, which is a rule nobody can find.
 *
 * Measured on the pixels, so it holds whatever the rule is drawn with. The
 * floor is the library's ordinary divider on its usual ground — `outline` on
 * white is 1.26:1 — so a menu's groups are as plainly apart as a list's rows.
 */
@OptIn(ExperimentalTestApi::class)
class MenuDividerContrastTest {

    /** How far the rule stands from the panel round it: its pixel against the row above it. */
    private fun ComposeUiTest.rule(dark: Boolean, contrast: ContrastLevel): Float {
        setContent {
            KontourTheme(darkTheme = dark, contrast = contrast, reduceMotion = true) {
                OverlayHost(Modifier.fillMaxSize()) {
                    DropdownMenu(visible = true, onDismissRequest = {}) {
                        item("Copy") {}
                        MenuDivider(Modifier.testTag("rule"))
                        item("Delete") {}
                    }
                }
            }
        }
        waitForIdle()
        val pixels = onNodeWithTag("rule").captureToImage().toPixelMap()
        val x = pixels.width / 2
        val ground = pixels[x, 0]
        return (0 until pixels.height).maxOf { y -> contrastRatio(pixels[x, y], ground) }
    }

    private fun Float.twoPlaces() = (this * 100).roundToInt() / 100f

    /** Both schemes measured before either is judged, so a failure reports the pair. */
    private fun ratios(contrast: ContrastLevel): Map<String, Float> =
        listOf("light" to false, "dark" to true).associate { (scheme, dark) ->
            var ratio = 0f
            runComposeUiTest { ratio = rule(dark, contrast) }
            scheme to ratio
        }

    @Test
    fun theRuleShowsOnTheMenu() {
        val ratios = ratios(ContrastLevel.Standard)
        assertTrue(
            ratios.values.all { it >= 1.25f },
            "a menu's divider stands ${ratios.mapValues { it.value.twoPlaces() }}:1 from the panel; a list's is 1.26 or more",
        )
    }

    /** The enhanced tier's rule is `outline`, near ink, and stays so. */
    @Test
    fun atHighContrastItIsTheTiersRule() {
        val ratios = ratios(ContrastLevel.High)
        assertTrue(ratios.values.all { it >= 3f }, "high contrast: ${ratios.mapValues { it.value.twoPlaces() }}:1")
    }
}
