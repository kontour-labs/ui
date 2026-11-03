package io.kontour.ui.components.list

import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.OverscrollEffect
import androidx.compose.foundation.OverscrollFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.down
import androidx.compose.ui.test.moveBy
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.up
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import io.kontour.ui.input.InputModality
import io.kontour.ui.input.LocalInputModality
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A scrollbar's thumb gives up some of its length while the content under it
 * is pulled past its end, keeps its place at the edge it is pressed against,
 * and has its length back once let go.
 *
 * Asked for for 0.5.2 as *"if a scrollbar is in a container with overscroll,
 * it shrinks at the bottom slightly as the overscroll happens"*.
 */
@OptIn(ExperimentalTestApi::class)
class ScrollbarStretchTest {

    /** An overscroll that takes what it is offered and shows nothing: enough that something overscrolls. */
    private object Swallows : OverscrollFactory {
        override fun createOverscrollEffect(): OverscrollEffect = object : OverscrollEffect {
            override val isInProgress: Boolean get() = false
            override fun applyToScroll(delta: Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource, performScroll: (Offset) -> Offset): Offset {
                performScroll(delta)
                return delta
            }
            override suspend fun applyToFling(velocity: Velocity, performFling: suspend (Velocity) -> Velocity) {
                performFling(velocity)
            }
            override val node: DelegatableNode get() = object : Modifier.Node() {}
        }
        override fun hashCode(): Int = 1
        override fun equals(other: Any?): Boolean = other === this
    }

    /**
     * An overscroll like iOS's bounce: once the content is past its end, it
     * takes the drag itself and offers none of it to the scroll, so nothing
     * above the list sees it. The first version of the thumb's give read the
     * drag the list declined, and on this it hardly moved — *"the overscroll
     * thing on the scrollbar doesn't seem to work on ios"*.
     */
    private object Bounces : OverscrollFactory {
        override fun createOverscrollEffect(): OverscrollEffect = object : OverscrollEffect {
            var offset = 0f
            override val isInProgress: Boolean get() = offset != 0f
            override fun applyToScroll(delta: Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource, performScroll: (Offset) -> Offset): Offset {
                if (offset != 0f) {
                    offset += delta.y
                    return delta
                }
                val left = delta - performScroll(delta)
                offset += left.y
                return delta
            }
            override suspend fun applyToFling(velocity: Velocity, performFling: suspend (Velocity) -> Velocity) {
                offset = 0f
                performFling(velocity)
            }
            override val node: DelegatableNode get() = object : Modifier.Node() {}
        }
        override fun hashCode(): Int = 2
        override fun equals(other: Any?): Boolean = other === this
    }

    /**
     * An overscroll like Android's stretch, which reports as consumed only what
     * it gave back and what the scroll took: the drag it pulls the stretch out
     * with is taken, and said to be left over. Read as what the effect said it
     * took, the thumb gave nothing on Android until the finger lifted and the
     * fling's bump came — *"it's not shrinking, until i let it go"*. iOS's
     * bounce says the same of the frame a pull first goes past the end.
     */
    private object Stretches : OverscrollFactory {
        override fun createOverscrollEffect(): OverscrollEffect = object : OverscrollEffect {
            var stretch = 0f
            override val isInProgress: Boolean get() = stretch != 0f
            override fun applyToScroll(delta: Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource, performScroll: (Offset) -> Offset): Offset {
                // Back toward the content first, as far as the stretch goes.
                val relaxed = if (stretch != 0f && delta.y * stretch < 0f) {
                    if (kotlin.math.abs(delta.y) >= kotlin.math.abs(stretch)) -stretch else delta.y
                } else {
                    0f
                }
                stretch += relaxed
                val left = Offset(0f, delta.y - relaxed)
                val scrolled = performScroll(left)
                if (source == androidx.compose.ui.input.nestedscroll.NestedScrollSource.UserInput) stretch += (left - scrolled).y
                return Offset(0f, relaxed) + scrolled
            }
            override suspend fun applyToFling(velocity: Velocity, performFling: suspend (Velocity) -> Velocity) {
                stretch = 0f
                performFling(velocity)
            }
            override val node: DelegatableNode get() = object : Modifier.Node() {}
        }
        override fun hashCode(): Int = 3
        override fun equals(other: Any?): Boolean = other === this
    }

    /** The thumb's top and bottom in the strip at the list's right edge: anything not the white page. */
    private fun ComposeUiTest.thumb(): Pair<Int, Int> {
        val image = onNodeWithTag("list").captureToImage().toPixelMap()
        val rows = (0 until image.height).filter { y ->
            (image.width - 12 until image.width).any { x -> image[x, y].let { it.green < 0.9f } }
        }
        return (rows.minOrNull() ?: -1) to (rows.maxOrNull() ?: -1)
    }

    private fun ComposeUiTest.list(overscroll: OverscrollFactory?) {
        mainClock.autoAdvance = false
        setContent {
            // The platform's, outside the theme, which keeps its account of it.
            CompositionLocalProvider(LocalOverscrollFactory provides overscroll) {
            KontourTheme(reduceMotion = true) {
                // A pointer's bar, which `alwaysVisible` keeps drawn; a finger's shows
                // only while the list moves.
                CompositionLocalProvider(LocalInputModality provides InputModality.Mouse) {
                    val state = rememberLazyListState(initialFirstVisibleItemIndex = 60)
                    LazyColumn(
                        state = state,
                        modifier = Modifier.size(200.dp, 400.dp).background(Color.White).testTag("list")
                            .scrollbar(state, colour = Color.Red, alwaysVisible = true),
                    ) {
                        items(60) { Box(Modifier.fillMaxWidth().height(40.dp)) }
                    }
                }
            }
            }
        }
        mainClock.advanceTimeBy(500)
    }

    @Test
    fun aPullPastTheEndShortensTheThumbFromItsFreeEnd() = pullShortensTheThumb(Swallows)

    /** The same under a bounce that, past the end, takes the drag before the scroll is offered it. */
    @Test
    fun aPullPastTheEndShortensTheThumbUnderABounceToo() = pullShortensTheThumb(Bounces)

    /** The same under a stretch that says the pull was left over: Android's. */
    @Test
    fun aPullPastTheEndShortensTheThumbUnderAStretchToo() = pullShortensTheThumb(Stretches)

    private fun pullShortensTheThumb(overscroll: OverscrollFactory) = runComposeUiTest {
        list(overscroll)
        val (top, bottom) = thumb()
        assertTrue(top >= 0, "no thumb was drawn")
        // A frame a step, as a finger is: shorter on every one of them, while
        // the finger is still down.
        onNodeWithTag("list").performTouchInput { down(center); moveBy(Offset(0f, -40f)) }
        mainClock.advanceTimeByFrame()
        var last = thumb().first
        repeat(4) { step ->
            onNodeWithTag("list").performTouchInput { moveBy(Offset(0f, -60f)) }
            mainClock.advanceTimeByFrame()
            val now = thumb().first
            assertTrue(now > last, "the thumb did not shorten on step $step of the pull past the end: $last, then $now")
            last = now
        }
        val (pulledTop, pulledBottom) = thumb()
        assertTrue(pulledTop > top + 4, "the thumb did not shorten while the list was pulled past its end: $top–$bottom, then $pulledTop–$pulledBottom")
        assertTrue(kotlin.math.abs(pulledBottom - bottom) <= 1, "the thumb left the end it was pressed against: $bottom, then $pulledBottom")

        onNodeWithTag("list").performTouchInput { up() }
        mainClock.advanceTimeBy(1500)
        assertEquals(top to bottom, thumb(), "the thumb did not have its length back once let go")
    }

    /** Where nothing overscrolls — a desktop — a drag against the end leaves the thumb as it is. */
    @Test
    fun withNothingOverscrollingTheThumbStaysWhole() = runComposeUiTest {
        list(null)
        val before = thumb()
        onNodeWithTag("list").performTouchInput { down(center); moveBy(Offset(0f, -40f)); moveBy(Offset(0f, -240f)) }
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeByFrame()
        assertEquals(before, thumb())
        onNodeWithTag("list").performTouchInput { up() }
    }

    /** How much of a 100px thumb at the foot of a [span]px track a [pull] takes, at [share]. */
    private fun given(pull: Float, span: Float, share: Float = 1f): Float {
        val thumb = Rect(0f, span - 100f, 6f, span)
        return 100f - thumb.stretched(pull, Size(6f, span), vertical = true, share = share).height
    }

    /**
     * The give is the share of the scroller the content has visibly moved, as
     * UIKit's indicator's is: a pull a third of the scroller takes about a
     * sixth of the thumb, not the third 0.5.2's fixed 160dp curve took —
     * *"the scrollbar shrink with the overscroll effect is very strong"*.
     */
    @Test
    fun aPullAThirdOfTheScrollerTakesAboutASixthOfTheThumb() {
        val give = given(pull = 700f / 3, span = 700f)
        assertTrue(give in 14f..17f, "a third of the scroller took $give of a 100px thumb")
    }

    /** *"i don't think it scales with the size of the list"*: the same pull takes more of a short one. */
    @Test
    fun theSamePullTakesMoreOfAShortListThanALongOne() {
        val short = given(pull = 150f, span = 300f)
        val long = given(pull = 150f, span = 900f)
        assertTrue(short > long * 1.8f, "150px past the end took $short of a 300px list's thumb and $long of a 900px one's")
    }

    /** *"it should be stronger on ios"*: Android's stretch moves no content, and its thumb gives less. */
    @Test
    fun underAStretchItGivesLessThanUnderABounce() {
        val bounce = given(pull = 300f, span = 700f, share = 1f)
        val stretch = given(pull = 300f, span = 700f, share = StretchShare)
        assertTrue(stretch < bounce * 0.5f && stretch > 0f, "a stretch took $stretch where a bounce took $bounce")
    }

    /** However far it is pulled, a thumb keeps half its length. */
    @Test
    fun aLongPullLeavesHalfTheThumb() {
        assertEquals(50f, given(pull = 100_000f, span = 300f), 0.01f)
    }
}
