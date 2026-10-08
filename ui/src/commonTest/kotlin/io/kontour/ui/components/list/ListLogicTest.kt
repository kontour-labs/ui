package io.kontour.ui.components.list

import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.ScrollIndicatorState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.Orientation
import io.kontour.ui.foundation.GroupPosition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Where an item sits in a group, which decides which of its corners round.
 */
class ListItemPositionTest {

    @Test
    fun aLoneItemRoundsEveryCorner() {
        // The case a three-item catalog example never exercises, and the one
        // every settings screen with a single row hits immediately.
        assertEquals(GroupPosition.Only, GroupPosition.of(0, 1))
    }

    @Test
    fun theEndsOfAGroupRoundOutward() {
        assertEquals(GroupPosition.First, GroupPosition.of(0, 3))
        assertEquals(GroupPosition.Middle, GroupPosition.of(1, 3))
        assertEquals(GroupPosition.Last, GroupPosition.of(2, 3))
    }

    @Test
    fun aPairHasNoMiddle() {
        assertEquals(GroupPosition.First, GroupPosition.of(0, 2))
        assertEquals(GroupPosition.Last, GroupPosition.of(1, 2))
    }

    @Test
    fun anEmptyGroupProducesNoPositions() {
        assertEquals(emptyList(), listPositions(0))
    }

    @Test
    fun listPositionsMatchesTheIndexedForm() {
        val count = 5
        assertEquals(
            List(count) { GroupPosition.of(it, count) },
            listPositions(count),
        )
    }
}

/**
 * How far each edge of a scrolling container is faded.
 *
 * The ramp is the part worth pinning: a fade that switches on and off snaps at
 * the ends of a list, which reads as a rendering fault rather than a hint.
 */
class ScrollFadeTest {

    private val fadeLength = 24f

    @Test
    fun aFadeLengthOfZeroFadesNothing() {
        assertEquals(ScrollFade(0f, 0f), scrollFade(ScrollState(0), 0f, Orientation.Vertical))
    }

    @Test
    fun theLeadingEdgeRampsOverTheFirstFadeLength() {
        assertEquals(0f, leadingFade(firstIndex = 0, firstOffset = 0, fadeLengthPx = fadeLength))
        assertEquals(0.5f, leadingFade(0, 12, fadeLength))
        assertEquals(1f, leadingFade(0, 24, fadeLength))
        // Past the first item there is definitely more above.
        assertEquals(1f, leadingFade(firstIndex = 3, firstOffset = 0, fadeLengthPx = fadeLength))
    }

    @Test
    fun theTrailingEdgeRampsOverTheLastFadeLength() {
        // The last item's end exactly at the viewport edge: nothing more below.
        assertEquals(0f, trailingFade(lastItemEnd = 500, viewportEnd = 500, fadeLengthPx = fadeLength))
        assertEquals(0.5f, trailingFade(512, 500, fadeLength))
        assertEquals(1f, trailingFade(524, 500, fadeLength))
        // Clamped, not unbounded.
        assertEquals(1f, trailingFade(9000, 500, fadeLength))
    }

    @Test
    fun anUnmeasuredContainerFadesNeitherEdge() {
        // A fresh ScrollState reports `maxValue = Int.MAX_VALUE` until it has
        // been laid out, which reads as "miles more content below" and flashes a
        // full trailing gradient on the first frame of every list.
        val fade = scrollFade(ScrollState(0), fadeLength, Orientation.Vertical)
        assertEquals(0f, fade.start)
        assertEquals(0f, fade.end)
        assertTrue(!fade.isVisible)
    }

    @Test
    fun isVisibleReportsWhetherAnythingIsDrawn() {
        assertTrue(!ScrollFade(0f, 0f).isVisible)
        assertTrue(ScrollFade(0.1f, 0f).isVisible)
        assertTrue(ScrollFade(0f, 0.1f).isVisible)
    }
}

/**
 * A scrollbar's thumb size and position.
 */
class ScrollbarGeometryTest {

    @Test
    fun aContainerWithNothingToScrollReportsNoThumb() {
        // `isUseful` is false, so the scrollbar draws nothing at all rather than
        // a full-length thumb that cannot move.
        val geometry = scrollbarGeometry(viewport = 400f, contentLength = 400f, scrolled = 0f)
        assertEquals(1f, geometry.fraction)
        assertTrue(!geometry.isUseful)
    }

    @Test
    fun theThumbCoversTheVisibleFraction() {
        // A 400px viewport over 1000px of content: 40% visible.
        val geometry = scrollbarGeometry(viewport = 400f, contentLength = 1000f, scrolled = 0f)
        assertEquals(0.4f, geometry.fraction)
        assertEquals(0f, geometry.position)
        assertTrue(geometry.isUseful)
    }

    @Test
    fun thePositionRunsFromZeroToOne() {
        // 600px of travel.
        assertEquals(0.5f, scrollbarGeometry(400f, 1000f, 300f).position)
        // Exactly 1 at the end, so the thumb reaches the end of its track rather
        // than stopping a few pixels short — the error a screenshot never shows.
        assertEquals(1f, scrollbarGeometry(400f, 1000f, 600f).position)
    }

    @Test
    fun overscrollDoesNotPushTheThumbPastItsTrack() {
        assertEquals(1f, scrollbarGeometry(400f, 1000f, 5000f).position)
        assertEquals(0f, scrollbarGeometry(400f, 1000f, -50f).position)
    }

    /**
     * Any scroller that reports a `scrollIndicatorState` is read from it: a
     * grid, a staggered grid or a pager used to fall to the end of a `when` and
     * draw nothing at all.
     */
    @Test
    fun aStateThatReportsItsIndicatorIsReadFromIt() {
        val geometry = scrollbarGeometry(Reporting(offset = 300, content = 1000, viewport = 400))
        assertEquals(0.4f, geometry.fraction)
        assertEquals(0.5f, geometry.position)
        assertEquals(600f, geometry.scrollable)
    }

    @Test
    fun anIndicatorNotYetKnownDrawsNothing() {
        val geometry = scrollbarGeometry(Reporting(offset = Int.MAX_VALUE, content = Int.MAX_VALUE, viewport = 400))
        assertTrue(!geometry.isUseful)
    }

    private class Reporting(offset: Int, content: Int, viewport: Int) : ScrollableState {
        override val scrollIndicatorState: ScrollIndicatorState = object : ScrollIndicatorState {
            override val scrollOffset = offset
            override val contentSize = content
            override val viewportSize = viewport
        }
        override fun dispatchRawDelta(delta: Float): Float = 0f
        override val isScrollInProgress: Boolean = false
        override suspend fun scroll(scrollPriority: MutatePriority, block: suspend ScrollScope.() -> Unit) = Unit
    }

    @Test
    fun anUnmeasuredContainerIsTreatedAsFull() {
        val geometry = scrollbarGeometry(viewport = 0f, contentLength = 0f, scrolled = 0f)
        assertEquals(1f, geometry.fraction)
        assertTrue(!geometry.isUseful)
    }
}

/**
 * Where the drawn thumb is: it moves only as the content moves, and reaches
 * its track's ends exactly as the content reaches its own, whatever the
 * estimate of the length in between says.
 */
class ScrollbarSourceTest {

    /** A scroller whose length can change under it, as a lazy list's estimate does. */
    private class Content(var value: Int, var max: Int, val viewport: Int = 400) : ScrollableState, ScrollExtent {
        override val extentValue: Int get() = value
        override val extentMax: Int get() = max
        override val extentViewport: Int get() = viewport
        override val canScrollForward: Boolean get() = value < max
        override val canScrollBackward: Boolean get() = value > 0
        override fun dispatchRawDelta(delta: Float): Float {
            val was = value
            value = (value + delta.toInt()).coerceIn(0, max)
            return (value - was).toFloat()
        }
        override val isScrollInProgress: Boolean = false
        override suspend fun scroll(scrollPriority: MutatePriority, block: suspend ScrollScope.() -> Unit) = Unit
    }

    @Test
    fun anExactAccountIsFollowedExactly() {
        val content = Content(value = 0, max = 600)
        val source = ScrollbarSource(content)
        assertEquals(0f, source.geometry().position)
        content.value = 150
        assertEquals(0.25f, source.geometry().position, 0.0001f)
        content.value = 450
        assertEquals(0.75f, source.geometry().position, 0.0001f)
    }

    @Test
    fun aLengthThatChangesWhileNothingMovesLeavesTheThumbWhereItIs() {
        val content = Content(value = 300, max = 600)
        val source = ScrollbarSource(content)
        source.geometry()
        // Scrolled a little, so the thumb is the scroll's rather than the first estimate's.
        content.value = 330
        val at = source.geometry().position
        assertEquals(0.55f, at, 0.0001f)
        // Found to be twice as long, standing still: the thumb stays.
        content.max = 1200
        assertEquals(at, source.geometry().position, 0.0001f)
    }

    @Test
    fun itReachesTheEndsWithTheContentWhateverTheLengthSaid() {
        val content = Content(value = 300, max = 600)
        val source = ScrollbarSource(content)
        source.geometry()
        content.value = 330
        source.geometry()
        content.max = 1200
        source.geometry()
        // Forward: on toward the end, never back.
        var last = source.geometry().position
        for (value in listOf(400, 600, 900, 1100)) {
            content.value = value
            val now = source.geometry().position
            assertTrue(now > last, "scrolling on moved the thumb back: $last to $now")
            last = now
        }
        content.value = 1200
        assertEquals(1f, source.geometry().position)
        // And back to the top.
        for (value in listOf(900, 500, 100)) {
            content.value = value
            val now = source.geometry().position
            assertTrue(now < last, "scrolling back moved the thumb on: $last to $now")
            last = now
        }
        content.value = 0
        assertEquals(0f, source.geometry().position)
    }

    @Test
    fun aDraggedThumbStaysUnderThePointerAndTakesTheContentToItsEnd() {
        val content = Content(value = 0, max = 600)
        val source = ScrollbarSource(content)
        source.geometry()
        source.hold(true)
        source.dragBy(along = 100f, travel = 400f)
        assertEquals(0.25f, source.geometry().position, 0.0001f)
        assertEquals(150, content.value)
        source.dragBy(along = 1000f, travel = 400f)
        assertEquals(1f, source.geometry().position)
        assertEquals(600, content.value, "the thumb at the end of its track left the content short of its end")
        source.hold(false)
    }
}

