package io.kontour.ui.components.display

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Where a bubble's tail goes, and which bubbles of a run have one.
 *
 * Read off the outline's bounds: every bubble keeps the tail's width free on its
 * sender's side, so a bubble with a tail reaches that edge and one without stops
 * the tail's width short of it.
 */
class ChatBubbleTest {

    private val density = Density(2f)
    private val size = Size(300f, 80f)
    private val reach = 12f // the 6dp tail at density 2

    private fun bounds(onEnd: Boolean, tail: Boolean, direction: LayoutDirection) =
        (ChatBubbleShape(RoundedCornerShape(18.dp), onEnd, tail, 6.dp)
            .createOutline(size, direction, density) as Outline.Generic).path.getBounds()

    @Test
    fun anOutgoingTailPointsToTheEndInEitherDirection() {
        val ltr = bounds(onEnd = true, tail = true, LayoutDirection.Ltr)
        assertTrue(abs(ltr.right - size.width) < 1f, "left to right, the tail should reach the right edge: $ltr")
        assertTrue(abs(ltr.left - 0f) < 1f, "and the bubble should start at the left one: $ltr")

        val rtl = bounds(onEnd = true, tail = true, LayoutDirection.Rtl)
        assertTrue(abs(rtl.left) < 1f, "right to left, the end is the left and the tail should reach it: $rtl")
        assertTrue(abs(rtl.right - size.width) < 1f, "with the body against the right: $rtl")
    }

    @Test
    fun withoutATailTheSendersSideStaysFree() {
        val outgoing = bounds(onEnd = true, tail = false, LayoutDirection.Ltr)
        assertTrue(
            abs(outgoing.right - (size.width - reach)) < 1f,
            "a bubble without a tail should stop the tail's width short of its sender's edge: $outgoing",
        )
        val incoming = bounds(onEnd = false, tail = false, LayoutDirection.Ltr)
        assertTrue(
            abs(incoming.left - reach) < 1f,
            "and an incoming one the same on the other side: $incoming",
        )
    }

    @Test
    fun anIncomingTailPointsToTheStart() {
        val ltr = bounds(onEnd = false, tail = true, LayoutDirection.Ltr)
        assertTrue(abs(ltr.left) < 1f, "an incoming tail should reach the left edge: $ltr")
    }

    @Test
    fun aRunIsWorkedOutFromWhoSentWhat() {
        val senders = listOf("sam", "sam", "sam", "me", "sam", "me", "me")
        val positions = senders.indices.map { BubblePosition.of(senders, it) { who -> who } }
        assertEquals(
            listOf(
                BubblePosition.First, BubblePosition.Middle, BubblePosition.Last,
                BubblePosition.Only, BubblePosition.Only,
                BubblePosition.First, BubblePosition.Last,
            ),
            positions,
        )
        assertEquals(BubblePosition.Only, BubblePosition.of(0, 1))
        assertEquals(BubblePosition.Middle, BubblePosition.of(1, 3))
    }
}
