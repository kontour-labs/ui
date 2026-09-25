package io.kontour.ui.components.display

import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.kontour.ui.theme.CapsuleCap
import io.kontour.ui.theme.CapsuleCornerSize
import io.kontour.ui.theme.SquircleShape
import io.kontour.ui.theme.cornerReaches
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

    /**
     * The underside of the tail is the bubble's bottom edge carried on, dead flat,
     * from where the far corner lets go of it out under the tail.
     *
     * "The tail shape on the chat bubble still doesn't look right. It doesn't quite
     * line up with the bottom of the chat bubble, so you can sort of see the
     * bubble's curve start on that bottom corner." The tail met the body exactly
     * where the squircle's bottom corner starts to curve, and hooked back onto the
     * bottom from a little above it, so for a stretch the underside was neither the
     * body's straight edge nor the tail's: the corner showed through as a dip.
     *
     * Read as a thin band along the bottom at every pixel between the far corner and
     * the edge: the outline has to fill it down to the bottom, and from half a pixel
     * above it.
     */
    @Test
    fun theTailsUndersideRunsFlatIntoTheBubblesBottom() {
        for (position in listOf(BubblePosition.Only, BubblePosition.Last)) {
            for (size in listOf(Size(300f, 72f), Size(300f, 112f), Size(120f, 72f))) {
                for (direction in LayoutDirection.entries) {
                    val (outline, body) = squircleBubble(position, size, direction)
                    val h = size.height
                    val bodySize = Size(size.width - reach, h)
                    val reaches = body.cornerReaches(bodySize, density, direction)
                    val right = direction == LayoutDirection.Ltr
                    val far = if (right) reaches.bottomLeft.x else reaches.bottomRight.x
                    val edge = size.width - reach
                    val arm = "$position $size $direction"
                    assertTrue(outline.getBounds().bottom <= h + 0.1f, "$arm: the tail hangs below the bubble")
                    // On past the body's edge, under the tail, to halfway to its tip.
                    var x = far + 1f
                    while (x <= edge + reach / 2f) {
                        val at = if (right) x else size.width - x
                        val band = Path().apply { addRect(Rect(at - 0.25f, h - 0.6f, at + 0.25f, h)) }
                        val ink = Path.combine(PathOperation.Intersect, outline, band).getBounds()
                        assertTrue(
                            !ink.isEmpty && ink.bottom >= h - 0.1f && ink.top <= h - 0.5f,
                            "$arm: at ${x}px of a bubble whose edge is at ${edge}px the bottom is $ink, " +
                                "not flat along $h",
                        )
                        x += 1f
                    }
                }
            }
        }
    }

    /**
     * The side on the tail's side runs straight down from the top corner into the
     * tail, whatever that corner is.
     *
     * The last bubble of a run has a tight top corner on the sender's side, which
     * leaves its bottom corner room to start curving far up the side — above where
     * the tail began, so the side pinched in and out again on the way down.
     */
    @Test
    fun aLastBubblesSideRunsStraightIntoTheTail() {
        for (size in listOf(Size(300f, 72f), Size(300f, 112f))) {
            for (direction in LayoutDirection.entries) {
                val (outline, body) = squircleBubble(BubblePosition.Last, size, direction)
                val right = direction == LayoutDirection.Ltr
                val reaches = body.cornerReaches(Size(size.width - reach, size.height), density, direction)
                val top = if (right) reaches.topRight.y else reaches.topLeft.y
                val edge = size.width - reach
                var y = top + 1f
                while (y <= size.height / 2f) {
                    val left = if (right) edge - 0.6f else size.width - edge
                    val sliver = Rect(left, y - 0.25f, left + 0.6f, y + 0.25f)
                    val ink = Path.combine(
                        PathOperation.Intersect, outline, Path().apply { addRect(sliver) },
                    ).getBounds()
                    assertTrue(
                        !ink.isEmpty && ink.left <= sliver.left + 0.05f && ink.right >= sliver.right - 0.05f,
                        "$size $direction: ${y}px down, the side should reach the edge; ink there is $ink",
                    )
                    y += 1f
                }
            }
        }
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

    /** An outgoing bubble on the library's own capsule, as [ChatBubble] builds it. */
    private fun squircleBubble(
        position: BubblePosition,
        size: Size,
        direction: LayoutDirection,
    ): Pair<Path, SquircleShape> {
        val body = bodyShape(
            SquircleShape(CapsuleCornerSize(cap = CapsuleCap)), BubbleSide.Outgoing, position, CornerSize(4.dp),
        ) as SquircleShape
        val outline = ChatBubbleShape(body, onEnd = true, tail = true, 6.dp)
            .createOutline(size, direction, density) as Outline.Generic
        return outline.path to body
    }
}
