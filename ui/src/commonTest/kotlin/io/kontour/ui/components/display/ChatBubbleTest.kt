package io.kontour.ui.components.display

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
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

    /**
     * The tail takes the bottom corner and nothing above it, so a one-line bubble
     * keeps its round end over the tail.
     *
     * "The tail shape of the chat message is a bit of a weird shape, and it doesn't
     * really blend with the actual message bubble." The tail was joined to the body
     * by a rectangle reaching the corner's radius *plus the tail's width* up the
     * side, which on a one-line bubble — 36dp tall, an 18dp corner — is past the
     * middle: the end was squared off into a flat wall and the tail stood on its
     * foot. Measured as how far a thin band 11px above the middle reaches toward
     * the sender's edge: the round end there stops about 1.7px short of it.
     */
    @Test
    fun aOneLineBubbleKeepsItsRoundEndAboveTheTail() {
        val line = Size(300f, 72f)
        val outline = ChatBubbleShape(RoundedCornerShape(18.dp), onEnd = true, tail = true, 6.dp)
            .createOutline(line, LayoutDirection.Ltr, density) as Outline.Generic
        val band = Path().apply { addRect(Rect(0f, 25f, line.width, 25.5f)) }
        val across = Path.combine(PathOperation.Intersect, outline.path, band).getBounds()
        val edge = line.width - reach
        assertTrue(
            across.right < edge - 1f,
            "11px above the middle of a one-line bubble the outline reaches ${across.right}, " +
                "against a round end that stops short of $edge — the tail has squared off the end",
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
