package io.kontour.ui.components.list

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.Orientation
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
        assertEquals(ListItemPosition.Only, ListItemPosition.of(0, 1))
    }

    @Test
    fun theEndsOfAGroupRoundOutward() {
        assertEquals(ListItemPosition.First, ListItemPosition.of(0, 3))
        assertEquals(ListItemPosition.Middle, ListItemPosition.of(1, 3))
        assertEquals(ListItemPosition.Last, ListItemPosition.of(2, 3))
    }

    @Test
    fun aPairHasNoMiddle() {
        assertEquals(ListItemPosition.First, ListItemPosition.of(0, 2))
        assertEquals(ListItemPosition.Last, ListItemPosition.of(1, 2))
    }

    @Test
    fun anEmptyGroupProducesNoPositions() {
        assertEquals(emptyList(), listPositions(0))
    }

    @Test
    fun listPositionsMatchesTheIndexedForm() {
        val count = 5
        assertEquals(
            List(count) { ListItemPosition.of(it, count) },
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

    @Test
    fun anUnmeasuredContainerIsTreatedAsFull() {
        val geometry = scrollbarGeometry(viewport = 0f, contentLength = 0f, scrolled = 0f)
        assertEquals(1f, geometry.fraction)
        assertTrue(!geometry.isUseful)
    }
}
