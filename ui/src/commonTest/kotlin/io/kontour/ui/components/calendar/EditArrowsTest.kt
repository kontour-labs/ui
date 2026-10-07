package io.kontour.ui.components.calendar

import androidx.compose.ui.geometry.Offset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Where a carry's edge arrows go: against the days' outer edges, level with
 * the pointer, and showing only as it nears that edge — by the same distance
 * in every view. Asked for as *it needs to be vertically in line with your
 * finger at all times*, and *it's showing up when your finger is too far from
 * the edge*, and in the day view *right at the edge, not in the middle*.
 */
class EditArrowsTest {

    private fun places(pointer: Offset, dwellSide: Int = 0) = arrowPlaces(
        left = 100f,
        right = 900f,
        pointer = pointer,
        dwellSide = dwellSide,
        inset = 10f,
        fadeFrom = 72f,
        full = 24f,
    ).associateBy { it.side }

    @Test
    fun theyAreAgainstTheOuterEdgesLevelWithThePointer() {
        val at = places(Offset(880f, 333f))
        assertEquals(Offset(110f, 333f), at.getValue(-1).centre)
        assertEquals(Offset(890f, 333f), at.getValue(1).centre)
    }

    @Test
    fun theyShowOnlyNearTheirEdge() {
        // A day view's one wide day: the middle of the screen is far from both.
        val middle = places(Offset(500f, 300f))
        assertEquals(0f, middle.getValue(-1).presence)
        assertEquals(0f, middle.getValue(1).presence)
        // A hundred pixels off: nothing. Within the dwell's zone: all of it.
        assertEquals(0f, places(Offset(800f, 300f)).getValue(1).presence)
        assertEquals(1f, places(Offset(880f, 300f)).getValue(1).presence)
        // Between, in proportion.
        val halfway = places(Offset(900f - 48f, 300f)).getValue(1).presence
        assertTrue(halfway > 0.4f && halfway < 0.6f, "halfway in, $halfway of it showed")
        // Past the edge is as near as it gets.
        assertEquals(1f, places(Offset(950f, 300f)).getValue(1).presence)
    }

    @Test
    fun theSideHeldAtShowsWholeWhereverThePointerIs() {
        assertEquals(1f, places(Offset(500f, 300f), dwellSide = 1).getValue(1).presence)
        assertEquals(0f, places(Offset(500f, 300f), dwellSide = 1).getValue(-1).presence)
    }
}
