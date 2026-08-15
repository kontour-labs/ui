package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.kontour.ui.contract.ComponentSpec
import io.kontour.ui.contract.componentRegistry
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.KontourTheme
import io.kontour.ui.theme.Theme
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A picture of every component, one per specimen, light and dark.
 *
 * The page goldens prove a *showcase* still renders. They cannot be shown to
 * anybody: `nav-light.png` is 4160×4380 and holds eleven arrangements. What a
 * doc page needs is one component on its own, and that is what this writes —
 * `screenshots/components/<slug>-{light,dark}.png`.
 *
 * ### Driven from the registry, which is the point
 *
 * The same list the contract suite runs over. A component absent from it is a
 * component nothing checks *and* nothing draws, so one omission is visible
 * twice — and a component added for its render gets the contract for free if it
 * is operable.
 *
 * ### One canvas for all of them
 *
 * Rather than a size per specimen. A gallery of components photographed at the
 * same distance can be compared; one where each was framed to fit cannot, and
 * the sizes would be a second thing to keep current. One specimen overrides it —
 * see `ComponentSpec.renderHeight`, and the reason there.
 *
 * ### Every render is checked for having drawn something
 *
 * A specimen that renders nothing produces a clean card of background colour,
 * and the harness would pin it as a golden without complaint — the blank would
 * then be *defended* by the very test meant to catch it. So each render is read
 * back and required to differ from its own background. It is the cheapest
 * possible check and it is the one this project keeps needing.
 */
class ComponentRenderTest {

    @AfterTest
    fun assertGoldensMatched() = Screenshot.assertAllMatched()

    @Test
    fun everySpecimenRendersLight() = renderAll(dark = false)

    @Test
    fun everySpecimenRendersDark() = renderAll(dark = true)

    private fun renderAll(dark: Boolean) {
        val suffix = if (dark) "dark" else "light"
        val drawn = mutableListOf<String>()
        val blank = mutableListOf<String>()

        for (spec in componentRegistry) {
            val file = Screenshot.render(
                name = "components/${spec.slug}-$suffix",
                width = CanvasWidth,
                height = spec.renderHeight?.times(2) ?: CanvasHeight,
            ) {
                Frame(dark = dark, spec = spec)
            }
            if (inkedFraction(file) < MinimumInk) blank += spec.name
            drawn += spec.slug
        }

        assertTrue(
            blank.isEmpty(),
            "${blank.size} specimens rendered a blank card in $suffix — a " +
                "component that draws nothing still produces a golden, and the " +
                "golden then defends the blank:\n" +
                blank.joinToString("\n") { "  · $it" },
        )
        assertTrue(drawn.isNotEmpty(), "the registry is empty, so nothing was drawn")
        assertTrue(
            drawn.size == drawn.distinct().size,
            "two specimens share a slug, so one render overwrote the other: " +
                drawn.groupBy { it }.filterValues { it.size > 1 }.keys.joinToString(),
        )
    }

    /**
     * One specimen, centred on the page's own background.
     *
     * Enabled and inert: the state on show is the resting one, and a specimen
     * that needed a press to look like itself would be a specimen whose doc page
     * needs a second picture rather than a different harness.
     *
     * `reduceMotion` because a render is one moment and an animation mid-flight
     * is not a moment worth pinning. `OverlayHost` because several specimens
     * compose one whether or not they show anything in it.
     */
    @androidx.compose.runtime.Composable
    private fun Frame(dark: Boolean, spec: ComponentSpec) {
        KontourTheme(darkTheme = dark, reduceMotion = true) {
            OverlayHost(Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Theme.colors.background)
                        .padding(Theme.spacing.md),
                    contentAlignment = Alignment.Center,
                ) {
                    spec.content(Modifier, true) {}
                }
            }
        }
    }

    /**
     * How much of the card is not its own background.
     *
     * The corner is the background by construction — the specimen is centred and
     * the frame is padded — so it needs no knowledge of the palette and works
     * the same in both themes.
     */
    private fun inkedFraction(file: File): Double {
        val image = ImageIO.read(file) ?: return 0.0
        val background = image.getRGB(0, 0)
        var inked = 0
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                if (image.getRGB(x, y) != background) inked++
            }
        }
        return inked.toDouble() / (image.width * image.height)
    }

    private companion object {
        /**
         * 300×120dp at the goldens' 2×.
         *
         * Wide enough for a full-width row — `ListItem` and `SettingRow` are the
         * widest specimens — and short enough that a page of these reads as a
         * contact sheet rather than a stack of screenshots.
         */
        const val CanvasWidth = 600
        const val CanvasHeight = 240

        /**
         * Below this, the card is blank.
         *
         * A tenth of a percent of 600×240 is 144 pixels — less than a single
         * character of the smallest type, so nothing that drew anything at all
         * can fall under it, and a card that drew a hairline still passes.
         */
        const val MinimumInk = 0.001
    }
}
