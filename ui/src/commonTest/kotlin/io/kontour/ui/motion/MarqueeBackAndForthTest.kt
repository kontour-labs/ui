package io.kontour.ui.motion

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.kontour.ui.foundation.Text
import io.kontour.ui.theme.KontourTheme
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A marquee that goes back and forth goes exactly as far as the text's end,
 * comes back, and rests at its start.
 *
 * Asked for as *"in marquees, can we have an option for them to scroll back and
 * forth, not just one-direction"*.
 *
 * Most of these read [MarqueeStrip.offset] frame by frame on the test clock,
 * because the claims are about exact places: the end and not a pixel short of
 * it, the start and not part-way. A pixel test says the same only roughly, so
 * the two that are about what is drawn, an end against its edge in either
 * layout direction, draw a strip with a block of colour at each end and look.
 */
@OptIn(ExperimentalTestApi::class)
class MarqueeBackAndForthTest {

    /**
     * Out until the text's end meets the box's end edge, exactly; a rest there;
     * then back to the start. It never wraps: no frame jumps further than a
     * frame of travel, which a loop does every pass as its next copy arrives.
     */
    @Test
    fun itReachesExactlyTheEndAndComesBack() = runComposeUiTest {
        val strip = bouncing(TooWide)
        val reach = strip.reach.toFloat()
        assertEquals(strip.content - strip.container, strip.reach, "back and forth goes further than the overflow")
        val offsets = track(strip, passMillis(strip))

        assertTrue(offsets.all { it in 0f..reach }, "the text went outside 0..$reach: ${offsets.filter { it !in 0f..reach }}")
        val arrived = offsets.indexOf(reach)
        assertTrue(arrived > 0, "the text never reached its end at $reach; the furthest it went was ${offsets.max()}")
        val rested = offsets.drop(arrived).takeWhile { it == reach }.size
        assertTrue(
            rested * FrameMillis >= Pause - FrameMillis * 2,
            "it waited ${rested * FrameMillis}ms at the end, not the pause of ${Pause}ms",
        )
        val home = offsets.drop(arrived).indexOf(0f)
        assertTrue(home > 0, "it never came back from the end to its start")

        val step = with(density) { Fast.toPx() } * FrameMillis / 1000f + 1f
        val jump = offsets.zipWithNext { a, b -> abs(b - a) }.max()
        assertTrue(jump <= step, "it jumped $jump px in a frame, where a frame of travel is at most $step: it wrapped")
    }

    /** A pass is there and back, so a counted one stops at its start, and stays. */
    @Test
    fun itSettlesAtItsStartWhenThePassesRunOut() = runComposeUiTest {
        val strip = bouncing(TooWide, iterations = 2)
        val reach = strip.reach.toFloat()
        val offsets = track(strip, passMillis(strip) * 3)
        val trips = offsets.zipWithNext().count { (a, b) -> a != reach && b == reach }
        assertEquals(2, trips, "two passes went to the end $trips times")
        assertEquals(0f, offsets.last(), "it settled at ${offsets.last()}, not its start")
        assertTrue(offsets.takeLast(passMillis(strip).toInt() / FrameMillis).all { it == 0f }, "it moved again after settling")
    }

    /** `Int.MAX_VALUE` is a ticker that never stops, back and forth as round and round. */
    @Test
    fun anEndlessOneIsStillGoingWhereACountedOneHasSettled() = runComposeUiTest {
        val strip = bouncing(TooWide, iterations = Int.MAX_VALUE)
        val reach = strip.reach.toFloat()
        val offsets = track(strip, passMillis(strip) * 5)
        val trips = offsets.zipWithNext().count { (a, b) -> a != reach && b == reach }
        assertTrue(trips >= 4, "an endless marquee went to its end $trips times in five passes' time")
    }

    @Test
    fun textThatFitsDoesNotMove() = runComposeUiTest {
        val strip = bouncing(Fits, draggable = true)
        assertEquals(0, strip.reach, "text that fits has somewhere to go")
        onNodeWithTag(Label).performTouchInput {
            down(center)
            moveBy(Offset(-200f, 0f))
            moveBy(Offset(-200f, 0f))
            up()
        }
        val offsets = track(strip, 6_000L)
        assertTrue(offsets.all { it == 0f }, "text that fits moved: ${offsets.filter { it != 0f }.take(5)}")
    }

    /**
     * Pulled, it goes only as far as either end of the text, and stops there:
     * no wrapping round to the start, in either layout direction.
     */
    @Test
    fun aDragIsClampedToTheText() {
        for (rtl in listOf(false, true)) {
            runComposeUiTest {
                val strip = bouncing(TooWide, draggable = true, rtl = rtl)
                val reach = strip.reach.toFloat()
                // Towards the text's end: leftwards in a left-to-right layout.
                val onwards = if (rtl) 1f else -1f
                onNodeWithTag(Label).performTouchInput {
                    down(center)
                    repeat(20) { moveBy(Offset(onwards * 40f, 0f)) }
                }
                mainClock.advanceTimeByFrame()
                assertEquals(reach, strip.offset, "pulled past its end (rtl = $rtl), it went to ${strip.offset}")
                onNodeWithTag(Label).performTouchInput {
                    repeat(40) { moveBy(Offset(-onwards * 40f, 0f)) }
                }
                mainClock.advanceTimeByFrame()
                assertEquals(0f, strip.offset, "pulled past its start (rtl = $rtl), it went to ${strip.offset}")
                onNodeWithTag(Label).performTouchInput { up() }
            }
        }
    }

    /**
     * Let go on its way out with its one pass still to run, it finishes the
     * journey: out to the end, back home, and then it rests.
     */
    @Test
    fun letGoOnItsWayOutItFinishesTheJourney() = runComposeUiTest {
        val strip = bouncing(TooWide, iterations = 1, draggable = true)
        val reach = strip.reach.toFloat()
        pullPartWay()
        assertTrue(strip.offset > 0f && strip.offset < reach, "the pull left it at ${strip.offset}, not part-way out")
        val offsets = track(strip, passMillis(strip) * 2)
        val out = offsets.indexOf(reach)
        assertTrue(out >= 0, "let go part-way out, it never went on to its end")
        assertTrue(offsets.drop(out).contains(0f), "having reached its end, it never came home")
        assertEquals(0f, offsets.last(), "it settled at ${offsets.last()}, not its start")
    }

    /** With no passes left, a nudge goes home rather than starting another pass. */
    @Test
    fun nudgedAfterItHasSettledItGoesHome() = runComposeUiTest {
        val strip = bouncing(TooWide, iterations = 1, draggable = true)
        val reach = strip.reach.toFloat()
        track(strip, passMillis(strip) * 2)
        assertEquals(0f, strip.offset)
        pullPartWay()
        val offsets = track(strip, passMillis(strip))
        assertTrue(offsets.none { it == reach }, "nudged with no passes left, it went out to its end again")
        assertEquals(0f, offsets.last(), "nudged with no passes left, it settled at ${offsets.last()}")
    }

    /**
     * What is drawn: the text's start against the box's start edge at rest, its
     * end against the box's end edge at the far end, to the pixel, and its
     * start back again. The start edge is the right one in a right-to-left
     * layout, and it is the public modifier, not the strip, that is drawn.
     */
    @Test
    fun theEndsMeetTheEdgesLeftToRight() = endsMeetTheEdges(rtl = false)

    @Test
    fun theEndsMeetTheEdgesRightToLeft() = endsMeetTheEdges(rtl = true)

    private fun endsMeetTheEdges(rtl: Boolean) = runComposeUiTest {
        mainClock.autoAdvance = false
        setContent {
            KontourTheme(darkTheme = false, reduceMotion = false) {
                CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                    Box(Modifier.width(BoxWidth.dp).testTag(Label)) {
                        Row(
                            Modifier
                                .marquee(velocity = Fast, travel = MarqueeTravel.BackAndForth)
                                .width(StripWidth.dp)
                                .height(20.dp),
                        ) {
                            Box(Modifier.width(MarkWidth.dp).fillMaxHeight().background(StartInk))
                            Spacer(Modifier.weight(1f))
                            Box(Modifier.width(MarkWidth.dp).fillMaxHeight().background(EndInk))
                        }
                    }
                }
            }
        }
        repeat(2) { mainClock.advanceTimeByFrame() }
        // Half a second of travel at Fast, for the 200dp the strip overflows by.
        val travel = 500L
        assertEdges(rtl, StartInk, "at rest")
        mainClock.advanceTimeBy(Pause + travel + Pause / 2)
        assertEdges(rtl, EndInk, "at the far end")
        mainClock.advanceTimeBy(Pause / 2 + travel + Pause / 2)
        assertEdges(rtl, StartInk, "back at its start")
    }

    /**
     * The block of [ink] fills the [MarkWidth] at the edge where it should be
     * and not a pixel more, and the other edge does not have it.
     */
    private fun ComposeUiTest.assertEdges(rtl: Boolean, ink: Color, where: String) {
        val image = onNodeWithTag(Label).captureToImage().toPixelMap()
        val mark = with(density) { MarkWidth.dp.roundToPx() }
        val y = image.height / 2
        val atStart = ink == StartInk
        // The edge the ink should be against, as columns counted from it.
        val fromRight = atStart == rtl
        fun column(fromEdge: Int) = if (fromRight) image.width - 1 - fromEdge else fromEdge
        val direction = if (rtl) "rtl" else "ltr"
        for (x in 0 until mark) {
            assertEquals(ink, image[column(x), y], "$where ($direction), ${x}px in from the edge is not the text's ${if (atStart) "start" else "end"}")
        }
        assertTrue(image[column(mark), y] != ink, "$where ($direction), the text's ${if (atStart) "start" else "end"} is past its edge")
        assertTrue(image[image.width - 1 - column(0), y] != ink, "$where ($direction), the other edge has it too")
    }

    /** A label going back and forth in a box too narrow for it, on a clock that only moves when told. */
    private fun ComposeUiTest.bouncing(
        text: String,
        iterations: Int = 3,
        draggable: Boolean = false,
        rtl: Boolean = false,
    ): MarqueeStrip {
        val strip = MarqueeStrip(bounces = true)
        mainClock.autoAdvance = false
        setContent {
            KontourTheme(darkTheme = false, reduceMotion = false) {
                CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                    Box(Modifier.width(BoxWidth.dp)) {
                        Text(
                            text = text,
                            maxLines = 1,
                            modifier = Modifier
                                .testTag(Label)
                                .scrollingMarquee(
                                    strip,
                                    iterations = iterations,
                                    pause = MarqueeDefaults.Pause,
                                    velocity = Fast,
                                    gap = 0.dp,
                                    draggable = draggable,
                                    runs = true,
                                ),
                        )
                    }
                }
            }
        }
        // Two frames: the first measures the text, and only the second knows
        // there is anything to scroll.
        repeat(2) { mainClock.advanceTimeByFrame() }
        return strip
    }

    /**
     * Pulls the text some way towards its end and holds it there before
     * letting go, so it is let go still rather than thrown, and the test is
     * about where it goes next and not about a coast.
     */
    private fun ComposeUiTest.pullPartWay() {
        onNodeWithTag(Label).performTouchInput {
            down(center)
            repeat(3) { moveBy(Offset(-30f, 0f)) }
            advanceEventTime(300)
            up()
        }
        mainClock.advanceTimeByFrame()
    }

    /** Where the text is, frame by frame, for [millis]. */
    private fun ComposeUiTest.track(strip: MarqueeStrip, millis: Long): List<Float> =
        List((millis / FrameMillis).toInt()) {
            mainClock.advanceTimeByFrame()
            strip.offset
        }

    /** One whole pass, there and back with a pause at each end, and some over. */
    private fun ComposeUiTest.passMillis(strip: MarqueeStrip): Long {
        val travel = strip.reach / with(density) { Fast.toPx() } * 1000f
        return (2 * (Pause + travel) + 500).toLong()
    }

    private companion object {
        const val Label = "label"
        const val BoxWidth = 120
        const val TooWide = "Elizabeth Quay Bus Station, Stand E"
        const val Fits = "Stand E"
        const val FrameMillis = 16

        val Pause = MarqueeDefaults.Pause.inWholeMilliseconds

        /** Ten times reading pace, so a pass is a few seconds and not ten. */
        val Fast = 400.dp

        /** 200dp wider than [BoxWidth], which is half a second of travel at [Fast]. */
        const val StripWidth = 320
        const val MarkWidth = 10
        val StartInk = Color.Red
        val EndInk = Color.Blue
    }
}
