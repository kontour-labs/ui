package io.kontour.ui.components.display

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.kontour.ui.foundation.LocalContentColour
import io.kontour.ui.foundation.ProvideTextStyle
import io.kontour.ui.foundation.Surface
import io.kontour.ui.theme.Theme

/**
 * One message in a conversation.
 *
 * ```kotlin
 * Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
 *     messages.forEachIndexed { index, message ->
 *         ChatBubble(
 *             side = if (message.mine) BubbleSide.Outgoing else BubbleSide.Incoming,
 *             position = BubblePosition.of(messages, index) { it.sender },
 *             meta = { Text(message.time) },
 *         ) {
 *             Text(message.text)
 *         }
 *     }
 * }
 * ```
 *
 * Outgoing messages sit at the end of the line in the accent colour, incoming ones
 * at the start on a quiet ground — the arrangement every messaging app has taught
 * its users, and mirrored right to left, where the sender's side is the left.
 *
 * ### A run reads as one
 *
 * Consecutive messages from one sender are a run, and [position] is where this
 * one sits in it. The corners on the sender's side tighten where two bubbles of a
 * run meet, and **only the last of a run has a tail** — so a run reads as one
 * turn in the conversation and the tail marks where it ends. [BubblePosition.of]
 * works the positions out from a list and who sent each item. Put a couple of dp
 * between the bubbles of a run and more between runs.
 *
 * Every bubble keeps the tail's width free on its sender's side, tail or not, so a
 * run's bubbles line up edge to edge.
 *
 * @param side Whose message it is, which decides the side it sits on, its colour
 *   and where its tail is.
 * @param position Where this bubble sits in a run from one sender.
 * @param tail Whether the last bubble of a run points at its sender. Off for a
 *   quieter thread, or one where each message is already set apart.
 * @param shape The bubble's corners. The ones between bubbles of a run take
 *   [joinedCorner] instead.
 * @param joinedCorner The radius on the sender's side where two bubbles of a run
 *   meet.
 * @param maxWidthFraction The widest a bubble grows, as a share of the width it is
 *   given — so a short reply is short and a long one still leaves the other side of
 *   the conversation visibly the other side.
 * @param meta Under the message at its end: a time, "Read", a tick. Drawn smaller
 *   and fainter than the message.
 */
@Composable
fun ChatBubble(
    side: BubbleSide,
    modifier: Modifier = Modifier,
    position: BubblePosition = BubblePosition.Only,
    tail: Boolean = true,
    colour: Color = ChatBubbleDefaults.colour(side),
    contentColour: Color = ChatBubbleDefaults.contentColour(side),
    shape: CornerBasedShape = Theme.shapes.capsule,
    joinedCorner: Dp = ChatBubbleDefaults.JoinedCorner,
    maxWidthFraction: Float = ChatBubbleDefaults.MaxWidthFraction,
    meta: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val tailWidth = ChatBubbleDefaults.TailWidth
    val bubble = remember(side, position, tail, shape, joinedCorner, tailWidth) {
        ChatBubbleShape(
            body = bodyShape(shape, side, position, CornerSize(joinedCorner)),
            onEnd = side == BubbleSide.Outgoing,
            tail = tail && (position == BubblePosition.Last || position == BubblePosition.Only),
            tailWidth = tailWidth,
        )
    }
    val outgoing = side == BubbleSide.Outgoing
    val sideways = Theme.spacing.sm
    val metaColour = contentColour.copy(alpha = contentColour.alpha * MetaAlpha)

    BoxWithConstraints(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = if (outgoing) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = maxWidth * maxWidthFraction.coerceIn(0f, 1f))
                .semantics(mergeDescendants = true) {},
            shape = bubble,
            colour = colour,
            contentColour = contentColour,
        ) {
            Column(
                Modifier.padding(
                    start = sideways + if (outgoing) 0.dp else tailWidth,
                    end = sideways + if (outgoing) tailWidth else 0.dp,
                    top = Theme.spacing.xs,
                    bottom = Theme.spacing.xs,
                )
            ) {
                ProvideTextStyle(Theme.typography.bodyMedium) { content() }
                if (meta != null) {
                    Column(Modifier.align(Alignment.End)) {
                        CompositionLocalProvider(LocalContentColour provides metaColour) {
                            ProvideTextStyle(Theme.typography.labelSmall.copy(color = metaColour)) { meta() }
                        }
                    }
                }
            }
        }
    }
}

/** Whose message a [ChatBubble] is. */
enum class BubbleSide {
    /** Someone else's: at the start of the line, on a quiet ground. */
    Incoming,

    /** The user's own: at the end of the line, in the accent colour. */
    Outgoing,
}

/** Where a [ChatBubble] sits in a run of messages from one sender. */
enum class BubblePosition {
    /** A run of one. */
    Only,

    /** The first of a longer run. */
    First,

    /** Neither end. */
    Middle,

    /** The last of a run, which carries the tail. */
    Last,

    ;

    companion object {
        /** The position of item [index] in a run of [count]. */
        fun of(index: Int, count: Int): BubblePosition = when {
            count <= 1 -> Only
            index == 0 -> First
            index == count - 1 -> Last
            else -> Middle
        }

        /**
         * The position of item [index] of [items], where a run is consecutive items
         * that [sender] says came from the same person.
         */
        fun <T> of(items: List<T>, index: Int, sender: (T) -> Any?): BubblePosition {
            val who = sender(items[index])
            val before = index > 0 && sender(items[index - 1]) == who
            val after = index < items.lastIndex && sender(items[index + 1]) == who
            return when {
                before && after -> Middle
                before -> Last
                after -> First
                else -> Only
            }
        }
    }
}

object ChatBubbleDefaults {
    /** The accent for the user's own messages, a quiet ground for everyone else's. */
    @Composable
    fun colour(side: BubbleSide): Color = when (side) {
        BubbleSide.Outgoing -> Theme.colours.primary
        BubbleSide.Incoming -> Theme.colours.surfaceSunken
    }

    /** The text colour that goes with [colour]. */
    @Composable
    fun contentColour(side: BubbleSide): Color = when (side) {
        BubbleSide.Outgoing -> Theme.colours.onPrimary
        BubbleSide.Incoming -> Theme.colours.content
    }

    /** The radius where two bubbles of a run meet, on the sender's side. */
    val JoinedCorner: Dp get() = BubbleJoinedCorner

    /** The widest a bubble grows, against the width it is given. */
    val MaxWidthFraction: Float get() = BubbleMaxWidth

    /** How far the tail reaches past the bubble, which every bubble keeps free. */
    val TailWidth: Dp get() = BubbleTailWidth
}

/** [shape] with the sender's corners tightened where this bubble meets others of its run. */
private fun bodyShape(
    shape: CornerBasedShape,
    side: BubbleSide,
    position: BubblePosition,
    joined: CornerSize,
): CornerBasedShape {
    val above = position == BubblePosition.Middle || position == BubblePosition.Last
    val below = position == BubblePosition.First || position == BubblePosition.Middle
    return when (side) {
        BubbleSide.Outgoing -> shape.copy(
            topEnd = if (above) joined else shape.topEnd,
            bottomEnd = if (below) joined else shape.bottomEnd,
        )
        BubbleSide.Incoming -> shape.copy(
            topStart = if (above) joined else shape.topStart,
            bottomStart = if (below) joined else shape.bottomStart,
        )
    }
}

/**
 * A bubble's outline: [body] inset by [tailWidth] on its sender's side, and, with
 * [tail], a tail from its bottom corner on that side out to the edge.
 *
 * The side is logical — [onEnd] is the end in either direction — and resolved here
 * against the layout direction, so a right-to-left thread's tails point left.
 */
internal class ChatBubbleShape(
    private val body: CornerBasedShape,
    private val onEnd: Boolean,
    private val tail: Boolean,
    private val tailWidth: Dp,
) : Shape {

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val reach = with(density) { tailWidth.toPx() }.coerceAtMost(size.width / 2f)
        val right = onEnd == (layoutDirection == LayoutDirection.Ltr)
        val bodySize = Size((size.width - reach).coerceAtLeast(0f), size.height)
        val path = Path()
        path.addOutline(body.createOutline(bodySize, layoutDirection, density))
        if (!right) path.translate(Offset(reach, 0f))
        if (!tail || bodySize.width <= 0f) return Outline.Generic(path)

        // The corner the tail comes out of, so the tail covers its rounding.
        val cornerSize = if (onEnd) body.bottomEnd else body.bottomStart
        val corner = cornerSize.toPx(bodySize, density).coerceAtLeast(reach)
        val w = size.width
        val h = size.height
        val rise = (corner + reach).coerceAtMost(h)
        val back = corner.coerceAtMost(bodySize.width)
        // Drawn for a tail on the right, then mirrored if it belongs on the left.
        fun x(at: Float) = if (right) at else w - at
        val edge = w - reach
        val tailPath = Path().apply {
            moveTo(x(edge), h - rise)
            // Down the outside of the tail to its tip at the bottom corner.
            cubicTo(x(edge), h - rise * TailBulge, x(edge + reach * TailFlare), h, x(w), h)
            // Back along the bottom, curling up a little from the tip.
            cubicTo(x(w - reach * TailCurl), h - reach * TailLift, x(edge - back * TailReturn), h, x(edge - back), h)
            lineTo(x(edge - back), h - rise)
            close()
        }
        return Outline.Generic(Path.combine(PathOperation.Union, path, tailPath))
    }

    override fun equals(other: Any?): Boolean =
        other is ChatBubbleShape && other.body == body && other.onEnd == onEnd &&
            other.tail == tail && other.tailWidth == tailWidth

    override fun hashCode(): Int {
        var result = body.hashCode()
        result = 31 * result + onEnd.hashCode()
        result = 31 * result + tail.hashCode()
        result = 31 * result + tailWidth.hashCode()
        return result
    }
}

private val BubbleJoinedCorner: Dp = 4.dp
private const val BubbleMaxWidth: Float = 0.8f
private val BubbleTailWidth: Dp = 6.dp

/** How faint the meta line is against the message. */
private const val MetaAlpha: Float = 0.7f

// The tail's curves, as shares of its own reach and the corner it covers.
private const val TailBulge: Float = 0.35f
private const val TailFlare: Float = 0.35f
private const val TailCurl: Float = 1.2f
private const val TailLift: Float = 0.25f
private const val TailReturn: Float = 0.5f
