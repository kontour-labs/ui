package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import io.kontour.ui.a11y.ContrastThreshold
import io.kontour.ui.a11y.contrastRatio
import io.kontour.ui.contract.componentRegistry
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.ContrastLevel
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
 * ### One canvas, then cropped to the ink
 *
 * Every specimen draws into the same generous card at the same density, and the
 * card is then trimmed to what was actually drawn. Both halves matter, and they
 * used to be in tension:
 *
 * - **Same density for all of them** is what makes a gallery comparable — a
 *   `Button` really is smaller than a `ListItem`, and a set of images each
 *   scaled to fill its own frame hides exactly that.
 * - **Cropping** is what makes each one a picture of a component rather than a
 *   picture of a card. `iconbutton` was 1.1% button and 98.9% background, which
 *   on a documentation page is a large empty rectangle with a star in it.
 *
 * Choosing a canvas per specimen would have got the second without the first,
 * and would have been a second set of numbers to keep current. Measuring the
 * drawn pixels gets both and keeps itself current.
 *
 * A specimen may still ask for a taller card — see `ComponentSpec.renderHeight`
 * and `RenderState.height` — but only ever to *fit*, never to frame: anything
 * it does not use is cropped off again.
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

    /**
     * Every specimen again at the enhanced tier, where a component that is a
     * quiet fill with a shadow for an edge disappears into the page.
     *
     * No render had ever been taken at this tier: the contract, the demos and
     * these pictures all ran at the standard one, so a chat bubble, a nav bar or
     * a knob's face could lose its edge and nothing would look. Resting only —
     * the extra states are about motion and pressing, which the tier does not
     * change the shape of — and each is also held to a boundary, below.
     */
    @Test
    fun everySpecimenRendersHighContrastLight() = renderAll(dark = false, contrast = ContrastLevel.High)

    @Test
    fun everySpecimenRendersHighContrastDark() = renderAll(dark = true, contrast = ContrastLevel.High)

    private fun renderAll(dark: Boolean, contrast: ContrastLevel = ContrastLevel.Standard) {
        val high = contrast == ContrastLevel.High
        val suffix = (if (high) "hc-" else "") + if (dark) "dark" else "light"
        val drawn = mutableListOf<String>()
        val blank = mutableListOf<String>()
        val edgeless = mutableListOf<String>()

        for (spec in componentRegistry) {
            val file = Screenshot.render(
                name = "components/${spec.slug}-$suffix",
                width = CanvasWidth,
                height = spec.renderHeight?.times(2) ?: CanvasHeight,
                trim = InkMargin,
            ) {
                Frame(dark = dark, contrast = contrast) { spec.content(Modifier, true) {} }
            }
            if (inkedFraction(file) < MinimumInk) blank += spec.name
            drawn += spec.slug
            if (high && spec.name !in EdgeExempt) {
                val weakest = boundaryContrast(file)
                if (weakest < ContrastThreshold.NonText) {
                    edgeless += "${spec.name}: ${"%.2f".format(weakest)}:1"
                }
            }
            if (high) continue

            // The states a component cannot be caught in at rest. Same frame and
            // same density, so the extra picture is comparable with the resting
            // one rather than being a differently-scaled photograph of the same
            // thing — the crop changes where the edges are, never how big the
            // component is drawn.
            for (state in spec.states) {
                val stateFile = Screenshot.render(
                    name = "components/${spec.slug}-${state.name}-$suffix",
                    width = CanvasWidth,
                    height = state.height?.times(2) ?: spec.renderHeight?.times(2) ?: CanvasHeight,
                    trim = InkMargin,
                ) {
                    Frame(dark = dark, contrast = contrast) { state.content(Modifier) }
                }
                if (inkedFraction(stateFile) < MinimumInk) blank += "${spec.name} (${state.name})"
                drawn += "${spec.slug}-${state.name}"
            }
        }

        assertTrue(
            edgeless.isEmpty(),
            "${edgeless.size} specimens have no boundary at 3:1 from the page at the " +
                "enhanced tier ($suffix) — the strongest step found walking in from " +
                "each side is below what WCAG 1.4.11 asks of a component's edge. " +
                "Give it `contrastEdge()`, or exempt it by name with the reason:\n" +
                edgeless.joinToString("\n") { "  · $it" },
        )
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
    private fun Frame(dark: Boolean, contrast: ContrastLevel, content: @Composable () -> Unit) {
        KontourTheme(darkTheme = dark, contrast = contrast, reduceMotion = true) {
            OverlayHost(Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Theme.colours.background)
                        .padding(Theme.spacing.md),
                    contentAlignment = Alignment.Center,
                ) {
                    content()
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

    /**
     * The weakest of four walks in from the edges of the picture, each scored by
     * the strongest step it meets against the page.
     *
     * Each walk starts on the page — the render keeps a margin — and goes in
     * along the middle row or column, through whatever the component draws at its
     * boundary: an edge, a fill, the ink of its text. A shadow is a gradient of
     * small steps and scores low; the line or fill behind it scores high, as long
     * as it is within [BoundaryReach] of where the ink began. So a container
     * that is a quiet fill with a shadow for an edge — the defect this exists
     * for — is the thing that fails.
     */
    private fun boundaryContrast(file: File): Float {
        val image = ImageIO.read(file) ?: return 0f
        val page = Color(image.getRGB(0, 0))
        fun score(points: Sequence<Pair<Int, Int>>): Float {
            var started = -1
            var best = 1f
            for ((i, point) in points.withIndex()) {
                val (x, y) = point
                val pixel = image.getRGB(x, y)
                if (started < 0 && pixel != image.getRGB(0, 0)) started = i
                if (started >= 0) {
                    best = maxOf(best, contrastRatio(Color(pixel), page))
                    if (i - started >= BoundaryReach) break
                }
            }
            return best
        }
        val w = image.width
        val h = image.height
        val midX = w / 2
        val midY = h / 2
        return minOf(
            score((0 until w).asSequence().map { it to midY }),
            score((w - 1 downTo 0).asSequence().map { it to midY }),
            score((0 until h).asSequence().map { midX to it }),
            score((h - 1 downTo 0).asSequence().map { midX to it }),
        )
    }

    private companion object {
        /**
         * How far past the first drawn pixel a walk looks for the edge: 12dp at
         * 2×, past the widest soft shadow the scale has.
         */
        const val BoundaryReach = 24

        /**
         * Specimens that have no edge to find, with the reason.
         *
         * A walk along the middle row or column of a picture meets the component
         * only if the component is there; these are ones where it meets a gap
         * by design, or where the thing drawn is deliberately faint.
         */
        val EdgeExempt = mapOf(
            "Skeleton" to "a placeholder, deliberately faint, and announced as loading",
            "Modifier.marquee" to "text scrolling in a clipped line; the text is the component",
        )

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
         * How much background to leave round the drawn component, in pixels.
         *
         * 12dp at the goldens' 2×. Enough that a focus ring or a shadow is not
         * shaved by a pixel and the component does not sit flush against the
         * image edge; little enough that the picture is the component.
         */
        const val InkMargin = 24

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
