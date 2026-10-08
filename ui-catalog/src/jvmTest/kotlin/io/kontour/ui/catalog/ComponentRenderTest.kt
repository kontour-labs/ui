package io.kontour.ui.catalog

import androidx.compose.foundation.background
import java.awt.image.BufferedImage
import androidx.compose.ui.unit.dp
import io.kontour.ui.foundation.elevation
import androidx.compose.foundation.layout.size
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
     * The control: the walk can fail. A raised panel with a shadow and no edge —
     * the defect this exists for — scores under 3:1 at the enhanced light tier,
     * where its ground is the page's own white; a black button with the same
     * shadow scores far over it.
     */
    @Test
    fun theBoundaryWalkTellsAShadowFromAnEdge() {
        fun panel(fill: @Composable () -> Color): Float = Scene(width = 600, height = 240, density = 2f) {
            KontourTheme(darkTheme = false, contrast = ContrastLevel.High, reduceMotion = true) {
                Box(Modifier.fillMaxSize().background(Theme.colours.background), contentAlignment = Alignment.Center) {
                    val shape = Theme.shapes.container
                    Box(Modifier.size(160.dp, 80.dp).elevation(Theme.elevation.overlay, shape).background(fill(), shape))
                }
            }
        }.use { boundaryContrast(it.frames(4)) }

        val quiet = panel { Theme.colours.surfaceRaised }
        val solid = panel { Theme.colours.primary }
        assertTrue(quiet < ContrastThreshold.NonText, "a raised panel with no edge scored ${quiet}:1 — the walk cannot see the defect")
        assertTrue(solid >= ContrastThreshold.NonText, "a solid button behind a shadow scored ${solid}:1 — the walk stops in the shadow")
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
     * boundary: an edge, a fill, the ink of its text. A shadow's tail is a few
     * levels off the page for many pixels and is skipped; the walk starts where
     * the shadow darkens, and it stops at the first run of one colour, which is
     * the component's own ground: whatever edge it has is behind that point, and
     * the text further in is not an edge. So a container that is a quiet fill
     * with a shadow for an edge — the defect this exists for — is the thing
     * that fails, and a black button with a long soft shadow is not.
     */
    private fun boundaryContrast(file: File): Float = ImageIO.read(file)?.let(::boundaryContrast) ?: 0f

    private fun boundaryContrast(image: BufferedImage): Float {
        val page = Color(image.getRGB(0, 0))
        fun score(points: Sequence<Pair<Int, Int>>): Float {
            var started = -1
            var best = 1f
            var flat = 0
            var previous = 0
            for ((i, point) in points.withIndex()) {
                val (x, y) = point
                val pixel = image.getRGB(x, y)
                val ratio = contrastRatio(Color(pixel), page)
                if (started < 0) {
                    if (ratio < ShadowTail) continue
                    started = i
                }
                best = maxOf(best, ratio)
                // Only a run off the page counts as a ground: the page itself
                // between two glyphs is a gap to walk across, not an end.
                flat = if (i > started && ratio >= ShadowTail && pixel == previous) flat + 1 else 0
                previous = pixel
                if (flat >= FlatRun || i - started >= BoundaryReach) break
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
         * The most a walk goes past where it began: 24dp at 2×, past the longest
         * shadow the scale has. The flat run below usually ends it first.
         */
        const val BoundaryReach = 48

        /** Below this against the page, a pixel is a shadow's tail, not an edge. */
        const val ShadowTail = 1.05f

        /**
         * This many pixels of exactly one colour is a ground: 8dp at 2×. A soft
         * shadow changes a level every few pixels as it fades, so it never
         * holds one colour this long — and "within a level" would call the
         * whole gradient flat.
         */
        const val FlatRun = 16

        /**
         * Specimens that have no edge to find, with the reason.
         *
         * A walk along the middle row or column of a picture meets the component
         * only if the component is there; these are ones where it meets a gap
         * by design, or where the thing drawn is deliberately faint.
         */
        val EdgeExempt = mapOf(
            "Skeleton" to "a placeholder, deliberately faint, and announced as loading",
            "Redacted" to "a placeholder, as `Skeleton` is; the cards around its bars keep their edges",
            "Modifier.marquee" to "text scrolling in a clipped line; the text is the component",
            // Text or marks on the page, with no container: there is no ground to
            // tell from the page, and a walk down the middle crosses the gaps
            // between words and marks. Their ink is held to text contrast by the
            // scheme instead.
            "Stat" to "text on the page, no container",
            "KeyValueList" to "text on the page, no container",
            "RelativeTimeText" to "text on the page, no container",
            "EmptyState" to "text on the page, no container",
            "LoadingState" to "a spinner and text on the page, no container",
            "RadioGroup" to "rows of text and marks on the page, no container",
            "Timeline" to "marks and text on the page, no container",
            "HorizontalTimeline" to "marks and text on the page, no container",
            "Badge" to "a mark on an icon, ringed in the page's colour at this tier by design",
            "PageIndicator" to "a row of dots with gaps; the others are `inactive`, 4.5:1 on the page",
            "ColourSwatchPicker" to "a row of swatches with gaps; each carries its own ring",
            "ColourPicker" to "a picture of colour, not a container; its cursors carry a double ring",
            "Avatar" to "a row of discs with gaps; each is a fill, or edged when it has neither name nor picture",
            "Table" to "rows of text divided by rules; it draws no frame unless it is outlined",
            "DatePicker" to "a grid of dates on the page, no container; its buttons carry their own edges",
            "DateRangePicker" to "a grid of dates on the page, no container; its buttons carry their own edges",
            "DateTimePicker" to "a grid of dates and wheels on the page, no container; its buttons carry their own edges",
            "TimePicker" to "wheels and a Now button on the page, no container; the band and the button carry their own edges, and the middle row runs between them",
            "IndexScroller" to "a rail of letters beside a list, no container; its labels are held to text contrast",
            "Carousel" to "its pages are the caller's pictures; the carousel draws no ground of its own",
            "TopBar (Hero)" to "its top is the caller's picture, run to the window's edges; its controls carry their own rings",
            "PullToRefresh" to "its list runs off the foot of the card; the disc and each row carry their own edges",
        )

        /**
         * 300×120dp at the goldens' 2×.
         *
         * Wide enough for a full-width row — `ListItem` and `SelectionRow` are the
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
