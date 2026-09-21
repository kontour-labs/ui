package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import io.kontour.ui.components.display.Carousel
import io.kontour.ui.components.display.CarouselStyle
import io.kontour.ui.components.display.rememberCarouselState
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The page holds still and the edge moves over it.
 *
 * [CarouselStyle.Wipe] is two boxes trading width: neither page goes anywhere,
 * the one being left keeps the near part of the frame and the one arriving takes
 * the far part. A still of it halfway through a drag looks like a still of
 * [CarouselStyle.Slide] halfway through one — half of each page, side by side —
 * which is exactly why this cannot be a golden.
 *
 * ### What tells them apart
 *
 * **Where each page's own content ended up.** Each page here is a flat colour
 * with a stripe painted down its physical left edge, so:
 *
 * | halfway through, going forward | `Slide` | `Wipe` |
 * |---|---|---|
 * | page 1's stripe | gone — the page is half off screen to the left | at `x = 0`, because the page did not move |
 * | page 2's stripe | at the middle, arriving with its page | clipped away — page 2's share is the *far* half |
 *
 * So the stripe is at one end under a wipe and in the middle under a slide, and
 * the assertions are about where the only stripe on screen is. The colours are
 * checked too, but they are the part the two styles agree on: both put page 1 on
 * the near side and page 2 on the far side.
 *
 * `parallax` is the dial between the two, and `1` is asserted to reach the
 * slide's answer — which is what makes it a dial rather than a second style.
 */
class CarouselWipeTest {

    @Test
    fun theLeavingPageHoldsStillUnderAWipe() {
        val frame = halfway(CarouselStyle.Wipe, parallax = 0f, direction = LayoutDirection.Ltr)

        assertEquals(
            listOf(NearStripe),
            frame.stripes(),
            "the stripes were at ${frame.stripes()} on a $Width-wide frame. Under a " +
                "wipe the page being left does not move, so its own left edge is " +
                "still at zero and the arriving page's is clipped off — one stripe, " +
                "at the near end",
        )
        assertEquals(First, frame.colourAt(0.15f), "the near side is not the page being left")
        assertEquals(Second, frame.colourAt(0.85f), "the far side is not the page arriving")
    }

    /**
     * The same gesture on the default style, which is where the stripe moves.
     *
     * A ratchet rather than a report — this is what the carousel has always done
     * — and it is here because the wipe's assertion above is only meaningful
     * against it. If a later change quietly pinned every page, both of these
     * would have to be rewritten together rather than one of them going green on
     * its own.
     */
    @Test
    fun theLeavingPageTravelsUnderASlide() {
        val frame = halfway(CarouselStyle.Slide, parallax = 0f, direction = LayoutDirection.Ltr)

        assertEquals(
            listOf(MiddleStripe),
            frame.stripes(),
            "the stripes were at ${frame.stripes()}. A slide carries each page's own " +
                "left edge with it, so halfway along the only one on screen is the " +
                "arriving page's, in the middle",
        )
    }

    /**
     * **`parallax = 1` is the slide, through the wipe's window.**
     *
     * The dial's far end. The clip is still the wipe's — one hard edge, no gap —
     * and the content underneath travels the whole page width, which puts every
     * stripe exactly where [theLeavingPageTravelsUnderASlide] found it. A
     * `parallax` that did nothing, or that moved the clip as well, fails here
     * and passes everything else.
     */
    @Test
    fun fullParallaxPutsTheContentWhereASlideWouldHaveIt() {
        val wiped = halfway(CarouselStyle.Wipe, parallax = 1f, direction = LayoutDirection.Ltr)
        val slid = halfway(CarouselStyle.Slide, parallax = 0f, direction = LayoutDirection.Ltr)

        assertEquals(
            slid.stripes(),
            wiped.stripes(),
            "a full parallax left the stripes at ${wiped.stripes()} where a slide " +
                "puts them at ${slid.stripes()}",
        )
    }

    /**
     * Right to left, the near end is the right one.
     *
     * A ratchet rather than a report: `pagePosition` counts pages in reading
     * order and the row lays them out the other way, so a wipe written only for
     * the left-hand case sends both pages to the same end of the frame and one of
     * them wins. Mirrored, the page being left keeps the *right* half.
     */
    @Test
    fun theWipeMirrors() {
        val frame = halfway(CarouselStyle.Wipe, parallax = 0f, direction = LayoutDirection.Rtl)

        assertEquals(First, frame.colourAt(0.85f), "the page being left is not on the right")
        assertEquals(Second, frame.colourAt(0.15f), "the page arriving is not on the left")
        assertEquals(
            listOf(NearStripe),
            frame.stripes(),
            "the stripes were at ${frame.stripes()}. Both pages are pinned to the " +
                "same frame either way, so whichever one owns the frame's left edge " +
                "shows its stripe there and the other's is clipped",
        )
    }

    /**
     * **Reduced motion takes the parallax and leaves the wipe.**
     *
     * The one thing that setting can sensibly remove here. The edge is the style
     * — taking it away is not less movement, it is a `Slide`, which pulls the
     * whole strip past the window and is *more* — and the content drifting
     * underneath the edge is the decoration on top of it. So a reader who has
     * asked for less gets the plain wipe whatever `parallax` says, which is the
     * same frame `parallax = 0` draws.
     */
    @Test
    fun reducedMotionDropsTheParallaxAndKeepsTheEdge() {
        val asked = halfway(
            CarouselStyle.Wipe,
            parallax = 1f,
            direction = LayoutDirection.Ltr,
            reduceMotion = true,
        )
        val plain = halfway(CarouselStyle.Wipe, parallax = 0f, direction = LayoutDirection.Ltr)

        assertEquals(
            plain.stripes(),
            asked.stripes(),
            "under reduced motion a full parallax left the stripes at " +
                "${asked.stripes()}, where a plain wipe puts them at ${plain.stripes()}",
        )
        assertEquals(First, asked.colourAt(0.15f), "the edge went with the parallax")
        assertEquals(Second, asked.colourAt(0.85f), "the edge went with the parallax")
    }

    /** A frame from the middle of a forward drag, with the finger still down. */
    private fun halfway(
        style: CarouselStyle,
        parallax: Float,
        direction: LayoutDirection,
        reduceMotion: Boolean = false,
    ): BufferedImage {
        var pages = Rect.Zero
        var frame: BufferedImage? = null

        Scene(width = Width, height = 200, density = 1f, reduceMotion = reduceMotion) {
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                val carousel = rememberCarouselState { 3 }
                Box(Modifier.fillMaxSize().background(Color.White)) {
                    Carousel(
                        state = carousel,
                        contentDescription = "Pages",
                        style = style,
                        parallax = parallax,
                        modifier = Modifier.fillMaxSize().reportBounds { pages = it },
                    ) { page -> Page(if (page == 0) First else Second) }
                }
            }
        }.use { scene ->
            scene.frames(4)
            // Forward is leftward in a left-to-right row and rightward in a
            // mirrored one, and the drag has to clear touch slop before any of
            // it counts — so it starts a slop's width past where it means to.
            val forward = if (direction == LayoutDirection.Rtl) 1f else -1f
            val centre = pages.center
            scene.drag(
                from = androidx.compose.ui.geometry.Offset(centre.x - forward * Slop, centre.y),
                to = androidx.compose.ui.geometry.Offset(
                    centre.x - forward * Slop + forward * Width / 2f,
                    centre.y,
                ),
                steps = 24,
                release = false,
            )
            frame = scene.frames(2)
            scene.release(centre)
        }
        return requireNotNull(frame)
    }

    /**
     * One page: a flat colour, and a stripe down its own left edge.
     *
     * `drawBehind` rather than a `Box` aligned to the start, because "the start"
     * is the right-hand side in a mirrored row and the whole point of the stripe
     * is to be somewhere fixed *in the page* that can be found again on screen.
     */
    @Composable
    private fun Page(colour: Color) {
        Box(
            Modifier
                .fillMaxSize()
                .background(colour)
                .drawBehind { drawRect(Color.Black, size = Size(StripeWidth, size.height)) }
        )
    }

    /** Where each run of near-black pixels starts, across the middle of the frame. */
    private fun BufferedImage.stripes(): List<String> {
        val row = height / 2
        val found = mutableListOf<String>()
        var x = 0
        while (x < width) {
            val rgb = getRGB(x, row)
            val dark = (rgb shr 16 and 0xFF) < 60 &&
                (rgb shr 8 and 0xFF) < 60 &&
                (rgb and 0xFF) < 60
            if (dark) {
                found += when {
                    x < Tolerance -> NearStripe
                    x > width - Tolerance - StripeWidth.toInt() -> FarStripe
                    else -> MiddleStripe
                }
                while (x < width && (getRGB(x, row) shr 16 and 0xFF) < 60) x++
            }
            x++
        }
        return found
    }

    /** Which page's colour is [fraction] of the way across, as a name. */
    private fun BufferedImage.colourAt(fraction: Float): Color {
        val rgb = getRGB((width * fraction).toInt(), height / 2)
        val red = rgb shr 16 and 0xFF
        val blue = rgb and 0xFF
        return if (red > blue) First else Second
    }

    private companion object {
        const val Width = 400

        /** Flat, saturated and far apart, so one channel separates them. */
        val First: Color = Color(0xFFCC2200)
        val Second: Color = Color(0xFF0022CC)

        const val StripeWidth = 20f

        /**
         * How far from an edge a stripe still counts as being *at* it.
         *
         * The drag lands near the middle rather than on it — `steps` are whole
         * pixels and a fling behaviour is not involved, but touch slop is only
         * approximately compensated — so "the middle" is a band rather than a
         * point, and the two ends have to be clear of it. A quarter of the frame
         * either side leaves half of it as the middle.
         */
        const val Tolerance = Width / 4

        /** Compose's touch slop, which the first pixels of any drag go to. */
        const val Slop = 20f

        const val NearStripe = "near"
        const val MiddleStripe = "middle"
        const val FarStripe = "far"
    }
}
