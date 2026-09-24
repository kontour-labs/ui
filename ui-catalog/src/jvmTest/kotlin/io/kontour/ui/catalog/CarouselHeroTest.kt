package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.display.Carousel
import io.kontour.ui.components.display.CarouselState
import io.kontour.ui.components.display.CarouselStyle
import io.kontour.ui.components.display.rememberCarouselState
import java.awt.image.BufferedImage
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Two rounded boxes side by side, trading width as you swipe.
 *
 * Asked for in those words, with a picture of Material's hero carousel attached,
 * against the style that was there before — two pages meeting along one moving
 * edge, which "is getting closer" and was not it.
 *
 * ### The geometry, and why it is asserted rather than photographed
 *
 * With `V` the frame, `S` the peek, `G` the gap and `L = V - G - S` the hero, the
 * boxes tile `[0, V]` at every point of a swipe: widths summing to `L + S`, with a
 * `(1-f)G` gap between the first pair and an `fG` gap between the second. A still
 * of that is a still of two boxes, and a still of the old wipe halfway through a
 * drag looks much the same — so what is measured here is the arithmetic, frame by
 * frame, from each page's own flat colour.
 *
 * The scene runs at a density of 1 so that a dp is a pixel and the numbers in the
 * assertions are the numbers in the design.
 */
class CarouselHeroTest {

    /**
     * **The first arm written, because it is the one that could have sunk the
     * design.**
     *
     * Every box's width and shift comes from `CarouselState.pagePosition`, which is
     * derived from the `LazyRow`'s `layoutInfo` — and that is written at the *end*
     * of the row's own measure pass, while the boxes are measured *during* it. A
     * naive reading says the geometry is therefore one frame behind the slots the
     * row put the pages in, which would show as the whole group sliding off one
     * edge during a fling.
     *
     * It is not, and this is what says so rather than an assumption about
     * Compose's internals: the shift is measured *against the slot*, so a stale
     * position would leave the boxes displaced — a hole at one edge of the frame
     * and an overhang at the other. Asserted on every frame of a slow drag, where
     * a one-frame error is a whole pixel of hole.
     */
    @Test
    fun theGeometryIsNotAFrameBehindTheScroll() {
        val faults = mutableListOf<String>()

        drag { frame, at, position ->
            val spans = frame.spans()
            Pages.forEachIndexed { index, colour ->
                val box = expected(index - position, index == Pages.lastIndex)
                val span = spans[colour]
                when {
                    box == null && span != null && span.last - span.first > Slack ->
                        faults += "frame $at drew page $index at $span, expecting nothing"

                    box == null || span == null -> Unit

                    abs(span.first - box.left) > Slack ->
                        faults += "frame $at put page $index at ${span.first}, " +
                            "expecting ${box.left}"

                    abs((span.last - span.first + 1) - box.width) > Slack ->
                        faults += "frame $at drew page $index " +
                            "${span.last - span.first + 1}px wide, expecting ${box.width}"
                }
            }
        }

        assertTrue(
            faults.isEmpty(),
            "the boxes are not where this frame's scroll position puts them: " +
                "${faults.take(4)}. Every box's width and shift comes from " +
                "`pagePosition`, which is derived from a `layoutInfo` written at the " +
                "end of the row's own measure — so a geometry that lags reads the " +
                "position the row has already moved past",
        )
    }

    /** At rest: one hero, one peek, one gap, and nothing else drawn. */
    @Test
    fun atRestTheHeroAndThePeekTileTheFrame() {
        var spans: Map<Int, IntRange> = emptyMap()
        carousel { scene, _, _ -> spans = scene.frames(8).spans() }

        assertEquals(
            listOf(Pages[0], Pages[1]),
            spans.keys.sortedBy { spans.getValue(it).first },
            "the frame held ${spans.size} page(s) at rest, where a hero carousel " +
                "shows the page and a peek of the next one",
        )
        val hero = spans.getValue(Pages[0])
        val peek = spans.getValue(Pages[1])
        assertEquals(0, hero.first, "the hero does not start at the frame's edge")
        assertEquals(Hero, hero.last - hero.first + 1, "the hero is not ${Hero}px wide")
        assertEquals(Hero + Gap, peek.first, "the peek does not start a gap past the hero")
        assertEquals(Width - 1, peek.last, "the peek does not reach the frame's edge")
    }

    /**
     * One page of scroll moves the carousel exactly one page.
     *
     * The slot is the hero's width, so the pitch is `L + G` — and that is the
     * decisive choice rather than a geometric one: `CarouselDefaults.SnapThreshold`
     * is a quarter of the pitch, so a pitch of `S + G` would put the threshold at
     * about eighteen pixels and the carousel would turn a page on a brush.
     */
    @Test
    fun onePitchOfScrollAdvancesExactlyOnePage() {
        var position = 0f

        carousel { scene, state, bounds ->
            scene.frames(8)
            scene.drag(
                from = Offset(bounds.center.x + Slop, bounds.center.y),
                to = Offset(bounds.center.x + Slop - (Hero + Gap), bounds.center.y),
                steps = 30,
                release = false,
            )
            scene.frames(2)
            position = state.pagePosition
            scene.release(bounds.center)
        }

        assertTrue(
            abs(position - 1f) < 0.08f,
            "dragging one pitch — ${Hero + Gap}px — left the carousel at page " +
                "$position, where it should be at 1.0",
        )
    }

    /**
     * The last page can become the hero, and fills the frame when it does.
     *
     * Two things at once, and the first is why the carousel needs `S + G` of extra
     * room at the end of its strip: with uniform slots the maximum scroll falls
     * exactly a gap and a peek short of putting the last slot at the start, so
     * without that padding the last page can never be the hero and the fling
     * fights a position it cannot reach.
     *
     * And there is no page after it to fill the gap and the peek, so it grows to
     * the whole frame rather than to a hero's width. The end of a row of pictures
     * being one picture is the only honest thing for it to look like.
     */
    @Test
    fun theLastPageBecomesTheHeroAndFillsTheFrame() {
        var spans: Map<Int, IntRange> = emptyMap()
        var page = 0
        var position = 0f

        carousel(goTo = Pages.size - 1) { scene, state, _ ->
            spans = scene.frames(80).spans()
            page = state.currentPage
            position = state.pagePosition
        }

        assertEquals(
            Pages.size - 1,
            page,
            "the carousel would not settle on its last page — it is on $page",
        )
        assertTrue(
            abs(position - (Pages.size - 1)) < 0.02f,
            "the carousel settled at page $position rather than at " +
                "${Pages.size - 1}. The strip needs a gap and a peek of extra room " +
                "at its end for the last slot to reach the start of the viewport",
        )
        val last = spans[Pages.last()]
        assertTrue(last != null, "the last page drew nothing at all")
        assertEquals(0, last!!.first, "the last page does not reach the frame's start")
        assertEquals(
            Width - 1,
            last.last,
            "the last page does not reach the frame's end — it stopped at " +
                "${last.last} with the carousel at page $position",
        )
        assertEquals(
            1,
            spans.size,
            "the frame held ${spans.size} pages with the last one settled, where the " +
                "last page has nothing to peek at and takes the whole frame",
        )
    }

    /**
     * A tap on the hero hits the hero, a tap on the peek hits the peek, and a tap
     * in the gap hits nothing.
     *
     * The peek page's content is measured at the *hero's* width and masked down, so
     * it physically extends past the box a reader can see. The mask is a clipping
     * layer rather than a draw-time clip for exactly this: a clip that only cut
     * pixels would leave a tap landing on a page that is not there.
     */
    @Test
    fun aTapLandsOnTheBoxItLooksLikeItLandedOn() {
        val tapped = mutableListOf<Int>()

        carousel(onTap = { tapped += it }) { scene, _, bounds ->
            scene.frames(8)
            scene.tap(Offset(Hero / 2f, bounds.center.y))
            scene.frames(4)
            scene.tap(Offset(Hero + Gap + Peek / 2f, bounds.center.y))
            scene.frames(4)
            scene.tap(Offset(Hero + Gap / 2f, bounds.center.y))
            scene.frames(4)
        }

        assertEquals(
            listOf(0, 1),
            tapped,
            "the three taps — on the hero, on the peek, and in the gap between them " +
                "— landed on $tapped",
        )
    }

    /**
     * The default style still slides, and every page still moves with it.
     *
     * Moved here from the wipe's own test when that style was replaced. It is a
     * ratchet rather than a report: the hero style pins two of its boxes to the
     * frame's edges, and if a later change pinned *every* page the hero's own arms
     * would still pass. Halfway through a forward drag on a `Slide`, the arriving
     * page's left edge is in the middle of the frame — it travelled there.
     */
    @Test
    fun theLeavingPageTravelsUnderASlide() {
        var spans: Map<Int, IntRange> = emptyMap()

        carousel(style = CarouselStyle.Slide) { scene, _, bounds ->
            scene.frames(8)
            scene.drag(
                from = Offset(bounds.center.x + Slop, bounds.center.y),
                to = Offset(bounds.center.x + Slop - Width / 2f, bounds.center.y),
                steps = 24,
                release = false,
            )
            spans = scene.frames(2).spans()
            scene.release(bounds.center)
        }

        val arriving = spans[Pages[1]]
        assertTrue(arriving != null, "the arriving page is not on screen at all")
        assertTrue(
            abs(arriving!!.first - Width / 2) < Width / 8,
            "halfway through a slide the arriving page's left edge is at " +
                "${arriving.first}, where a strip being pulled past the window puts " +
                "it near the middle at ${Width / 2}",
        )
    }

    /**
     * A zero peek is one page at a time, and the gap only exists in flight.
     *
     * Asked for as "zero peek, just one visible at a time". The frame reserves room
     * for a gap only when there is a peek for it to separate — otherwise the hero is
     * the whole frame, and what would have been a strip of background standing at
     * the frame's end becomes the gap that opens between two boxes while a swipe is
     * in flight. Both halves are asserted, because the first draft of this drew a
     * hero one gap short of the frame and left the gap at the edge.
     */
    @Test
    fun aZeroPeekShowsOnePageAtATime() {
        var settled: Map<Int, IntRange> = emptyMap()
        var moving: Map<Int, IntRange> = emptyMap()

        carousel(peek = 0) { scene, _, bounds ->
            settled = scene.frames(8).spans()
            scene.drag(
                from = Offset(bounds.center.x + Slop, bounds.center.y),
                to = Offset(bounds.center.x + Slop - Width / 2f, bounds.center.y),
                steps = 24,
                release = false,
            )
            moving = scene.frames(2).spans()
            scene.release(bounds.center)
        }

        assertEquals(
            listOf(Pages[0]),
            settled.keys.toList(),
            "the frame held ${settled.size} pages at rest with a zero peek, where " +
                "one page at a time is the whole of what a zero peek means",
        )
        val only = settled.getValue(Pages[0])
        assertEquals(0, only.first, "the page does not start at the frame's edge")
        assertEquals(Width - 1, only.last, "the page does not reach the frame's edge")

        assertEquals(
            2,
            moving.size,
            "mid-swipe the frame held ${moving.size} box(es), where two are trading " +
                "width: $moving",
        )
        val leaving = moving.getValue(Pages[0])
        val arriving = moving.getValue(Pages[1])
        assertEquals(0, leaving.first, "the leaving box left the frame's start edge")
        assertEquals(Width - 1, arriving.last, "the arriving box left the frame's end edge")
        assertTrue(
            abs((arriving.first - leaving.last - 1) - Gap) <= Slack,
            "the two boxes were ${arriving.first - leaving.last - 1}px apart, where " +
                "the gap is ${Gap}px — with no peek to reserve it for, the gap is " +
                "what opens between them",
        )
    }

    /**
     * Parallax hands the leaving page's content its share of the strip's travel.
     *
     * The box of a page on its way out is pinned to the frame's start and closes over
     * its own content, so at `0` the picture holds still and is taken away — which is
     * what the stripe at the page's start edge still being there proves. At `1` the
     * content travels with the strip instead and slides out under the shrinking
     * window, taking the stripe off the frame with it.
     *
     * The same dial the wipe this style replaced carried, and the same polarity.
     */
    @Test
    fun parallaxTakesTheLeavingContentWithTheStrip() {
        val held = stripeAtTheStart(parallax = 0f)
        val travelled = stripeAtTheStart(parallax = 1f)
        val asked = stripeAtTheStart(parallax = 1f, reduceMotion = true)

        assertTrue(
            held > 0,
            "with no parallax the leaving page's own start edge is not at the frame's " +
                "start: its mark drew ${held}px there. The box is pinned and closes " +
                "over content that holds still",
        )
        assertTrue(
            travelled == 0,
            "with a full parallax the leaving page's mark still drew ${travelled}px at " +
                "the frame's start, where the content is meant to have travelled out " +
                "with the strip",
        )
        assertTrue(
            asked > 0,
            "under reduced motion a full parallax still took the content away: its " +
                "mark drew ${asked}px at the frame's start. The boxes trading width " +
                "are the style; a picture drifting underneath is the embellishment",
        )
    }

    /**
     * The picture does not move; its box wipes over it.
     *
     * Reported: "it still sometimes looks like the photo slides in and out in hero
     * mode. It should look like the photo doesn't move, but instead the boxes just
     * wipe in their current animation to reveal the new photo." The leaving page's
     * picture already held still — its box is pinned to the frame's start — but the
     * *arriving* page's picture rode in with its box, and swiping back, the page
     * going out rode out with its. Each page now holds its picture where it will sit
     * at rest and only the box's edge moves.
     *
     * Read by a mark at each page's **end** edge: at rest a page's end edge is the
     * frame's end, so a picture that holds still keeps its mark there all the way
     * through the swipe, and one that travels takes it past the frame's edge, where
     * it is clipped away. The leaving page's own end mark is inside its shrinking
     * box's clip, so a mark at the frame's end mid-swipe can only be the arriving
     * page's. Forwards and backwards, since backwards is the page going out.
     */
    @Test
    fun thePictureHoldsStillWhileItsBoxWipesOverIt() {
        val forwards = endMarkMidSwipe(startAt = 0, towardStart = true)
        val backwards = endMarkMidSwipe(startAt = 1, towardStart = false)
        assertTrue(
            forwards > 0,
            "halfway into a forward swipe the arriving picture's end mark was not at the " +
                "frame's end — the picture is travelling in with its box",
        )
        assertTrue(
            backwards > 0,
            "halfway into a backward swipe the outgoing picture's end mark was not at the " +
                "frame's end — the picture is travelling out with its box",
        )
    }

    /** How much of an end-edge mark is at the frame's end, halfway through a swipe. */
    private fun endMarkMidSwipe(startAt: Int, towardStart: Boolean): Int {
        var found = 0
        carousel(peek = 0, endStripe = true, goTo = startAt) { scene, _, bounds ->
            scene.frames(12)
            val direction = if (towardStart) -1f else 1f
            val from = Offset(bounds.center.x - direction * Slop, bounds.center.y)
            scene.drag(
                from = from,
                to = Offset(from.x + direction * (Width + Gap) / 2f, from.y),
                steps = 20,
                release = false,
            )
            val frame = scene.frames(2)
            val row = frame.height / 2
            for (x in Width - StripeWidth.toInt() * 2 until Width) {
                if ((frame.getRGB(x, row) and 0xFFFFFF) == 0) found++
            }
            scene.release(bounds.center)
        }
        return found
    }

    /** How much of the leaving page's start mark is at the frame's start, mid-swipe. */
    private fun stripeAtTheStart(parallax: Float, reduceMotion: Boolean = false): Int {
        var found = 0
        carousel(parallax = parallax, stripe = true, reduceMotion = reduceMotion) { scene, _, bounds ->
            scene.frames(8)
            scene.drag(
                from = Offset(bounds.center.x + Slop, bounds.center.y),
                to = Offset(bounds.center.x + Slop - (Hero + Gap) / 2f, bounds.center.y),
                steps = 20,
                release = false,
            )
            val frame = scene.frames(2)
            // Only the leaving box reaches this far into the frame at half a pitch,
            // so a dark pixel here is its mark and nothing else.
            val row = frame.height / 2
            for (x in 0 until StripeWidth.toInt() * 2) {
                if ((frame.getRGB(x, row) and 0xFFFFFF) == 0) found++
            }
            scene.release(bounds.center)
        }
        return found
    }

    /**
     * Runs the block over every frame of a forward drag, with the position the
     * carousel reports *after* that frame.
     *
     * Which is the whole of the lag question: if the boxes were laid out from a
     * stale `pagePosition`, the geometry on the frame would be the geometry for
     * the position before it. Big steps for the same reason — a lag has to be
     * worth more than the tolerance, and one frame at thirty-odd pixels is.
     */
    private fun drag(check: (BufferedImage, Int, Float) -> Unit) {
        carousel { scene, state, bounds ->
            scene.frames(8)
            val from = Offset(bounds.center.x + Slop, bounds.center.y)
            var at = 0
            // By hand rather than through `drag`, so every frame of the gesture can
            // be looked at rather than only the last.
            scene.press(from)
            repeat(Steps) { step ->
                val travelled = (step + 1) * (Hero + Gap) / Steps
                scene.move(Offset(from.x - travelled, from.y))
                val frame = scene.frame()
                check(frame, at++, state.pagePosition)
            }
            scene.release(Offset(from.x - (Hero + Gap), from.y))
        }
    }

    /** One box, as the table in `heroBoxOf` has it. */
    private class Box(val left: Int, val width: Int)

    /** Where a page `d` pages ahead of the viewport should be, in whole pixels. */
    private fun expected(d: Float, last: Boolean): Box? {
        val pitch = (Hero + Gap).toFloat()
        val width = if (d < 0f) Hero + d * pitch else Width - d * pitch
        val capped = if (d < 0f || last) width else minOf(width, Hero.toFloat())
        if (capped <= Slack) return null
        val left = if (d < 0f) 0f else d * pitch
        return Box(left.roundToInt(), capped.roundToInt())
    }

    /** A hero carousel of four flat colours, and the scene it lives in. */
    private fun carousel(
        style: CarouselStyle = CarouselStyle.Hero,
        goTo: Int = -1,
        onTap: ((Int) -> Unit)? = null,
        peek: Int = Peek,
        parallax: Float = 0f,
        stripe: Boolean = false,
        endStripe: Boolean = false,
        reduceMotion: Boolean = false,
        body: (Scene, CarouselState, Rect) -> Unit,
    ) {
        var bounds = Rect.Zero
        lateinit var state: CarouselState
        var target by mutableIntStateOf(goTo)

        Scene(width = Width, height = 160, density = 1f, reduceMotion = reduceMotion) {
            val carousel = rememberCarouselState { Pages.size }
            state = carousel
            LaunchedEffect(target) { if (target >= 0) carousel.scrollToPage(target) }
            Box(Modifier.fillMaxSize().background(Color.White)) {
                Carousel(
                    state = carousel,
                    contentDescription = "Pages",
                    style = style,
                    peek = peek.dp,
                    parallax = parallax,
                    pageSpacing = Gap.dp,
                    modifier = Modifier.fillMaxSize().reportBounds { bounds = it },
                ) { page ->
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color(Pages[page] or ALPHA))
                            // A mark at the page's own start edge, for the arms that
                            // ask where a page's *content* ended up rather than
                            // where its box did. `drawBehind` rather than an aligned
                            // child, so it is somewhere fixed in the page whichever
                            // way the row reads.
                            .then(
                                if (stripe) {
                                    Modifier.drawBehind {
                                        drawRect(Color.Black, size = Size(StripeWidth, size.height))
                                    }
                                } else {
                                    Modifier
                                }
                            )
                            .then(
                                if (endStripe) {
                                    Modifier.drawBehind {
                                        drawRect(
                                            Color.Black,
                                            topLeft = Offset(size.width - StripeWidth, 0f),
                                            size = Size(StripeWidth, size.height),
                                        )
                                    }
                                } else {
                                    Modifier
                                }
                            )
                            .then(
                                if (onTap != null) {
                                    Modifier.clickable { onTap(page) }
                                } else {
                                    Modifier
                                }
                            )
                    )
                }
            }
        }.use { scene -> body(scene, state, bounds) }
        target = goTo
    }

    /**
     * Where each page's own colour begins and ends across the middle of the frame.
     *
     * By colour rather than by runs of ink, because two boxes with a gap of a
     * fraction of a pixel between them are one run and three boxes are what is
     * being counted. The rounded corners are why the row is the middle one: at the
     * very top of the frame a box is narrower than it is.
     */
    private fun BufferedImage.spans(): Map<Int, IntRange> {
        val row = height / 2
        val found = mutableMapOf<Int, IntRange>()
        for (x in 0 until width) {
            val rgb = getRGB(x, row) and 0xFFFFFF
            if (rgb !in Pages) continue
            val seen = found[rgb]
            found[rgb] = if (seen == null) x..x else seen.first..x
        }
        return found
    }

    private companion object {
        const val Width = 400
        const val Peek = 100
        const val Gap = 20

        /** `V - G - S`, the width of the box the carousel is about. */
        const val Hero = Width - Gap - Peek

        /** Flat and far apart, so one pixel names its page. */
        val Pages = listOf(0xCC2200, 0x0022CC, 0x00AA33, 0xAA00AA)
        const val ALPHA = 0xFF000000.toInt()

        /** Wide enough to count and narrow enough to stay inside the leaving box. */
        const val StripeWidth = 12f

        /** Compose's touch slop, which the first pixels of any drag go to. */
        const val Slop = 20f

        /** Enough frames of the gesture that a one-frame lag has somewhere to show. */
        const val Steps = 20

        /**
         * Rounding, and nothing else.
         *
         * Every width is a float rounded to a whole pixel, and three of them are
         * added up — so the total can be a pixel or two off the exact `L + S`
         * without anything being wrong. A one-frame lag is tens of pixels.
         */
        const val Slack = 3
    }
}
