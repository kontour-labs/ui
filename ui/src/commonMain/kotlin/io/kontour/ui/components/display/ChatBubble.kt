package io.kontour.ui.components.display

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
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
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.constrain
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.kontour.ui.foundation.GroupPosition
import io.kontour.ui.foundation.LocalContentColour
import io.kontour.ui.foundation.ProvideTextStyle
import io.kontour.ui.foundation.Surface
import io.kontour.ui.theme.Theme
import io.kontour.ui.theme.cornerReaches

/**
 * One message in a conversation.
 *
 * ```kotlin
 * Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
 *     messages.forEachIndexed { index, message ->
 *         ChatBubble(
 *             side = if (message.mine) BubbleSide.Outgoing else BubbleSide.Incoming,
 *             position = GroupPosition.of(messages, index) { it.sender },
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
 * turn in the conversation and the tail marks where it ends. [GroupPosition.of]
 * works the positions out from a list and who sent each item. Put a couple of dp
 * between the bubbles of a run and more between runs.
 *
 * Every bubble keeps the tail's width free on its sender's side, tail or not, so a
 * run's bubbles line up edge to edge.
 *
 * @param side Whose message it is, which decides the side it sits on, its colour
 *   and where its tail is.
 * @param position Where this bubble sits in a run from one sender.
 * @param showTail Whether the last bubble of a run points at its sender. Off for a
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
    position: GroupPosition = GroupPosition.Only,
    showTail: Boolean = true,
    containerColour: Color = ChatBubbleDefaults.containerColour(side),
    contentColour: Color = ChatBubbleDefaults.contentColour(side),
    shape: CornerBasedShape = Theme.shapes.capsule,
    joinedCorner: Dp = ChatBubbleDefaults.JoinedCorner,
    maxWidthFraction: Float = ChatBubbleDefaults.MaxWidthFraction,
    meta: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val tailWidth = ChatBubbleDefaults.TailWidth
    val bubble = remember(side, position, showTail, shape, joinedCorner, tailWidth) {
        ChatBubbleShape(
            body = bodyShape(shape, side, position, CornerSize(joinedCorner)),
            onEnd = side == BubbleSide.Outgoing,
            tail = showTail && (position == GroupPosition.Last || position == GroupPosition.Only),
            tailWidth = tailWidth,
        )
    }
    val outgoing = side == BubbleSide.Outgoing
    val sideways = Theme.spacing.sm
    val metaColour = contentColour.copy(alpha = contentColour.alpha * MetaAlpha)

    val share = maxWidthFraction.coerceIn(0f, 1f)
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = if (outgoing) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Surface(
            modifier = Modifier
                // The share of the row, taken in the bubble's own measure rather
                // than through a `BoxWithConstraints` — a subcomposition per
                // message, in a list of hundreds of them, to read one number the
                // measure pass already has. The arithmetic is `widthIn`'s, down
                // to the rounding: the row's width in dp, scaled, back to pixels.
                .layout { measurable, constraints ->
                    val cap = if (constraints.hasBoundedWidth) {
                        (constraints.maxWidth.toDp() * share).roundToPx().coerceAtLeast(0)
                    } else {
                        Constraints.Infinity
                    }
                    val placeable = measurable.measure(
                        constraints.constrain(Constraints(maxWidth = cap)),
                    )
                    layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
                }
                .semantics(mergeDescendants = true) {},
            shape = bubble,
            containerColour = containerColour,
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

object ChatBubbleDefaults {
    /** The accent for the user's own messages, a quiet ground for everyone else's. */
    @Composable
    @ReadOnlyComposable
    fun containerColour(side: BubbleSide): Color = when (side) {
        BubbleSide.Outgoing -> Theme.colours.primary
        BubbleSide.Incoming -> Theme.colours.surfaceSunken
    }

    /** The text colour that goes with [containerColour]. */
    @Composable
    @ReadOnlyComposable
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
internal fun bodyShape(
    shape: CornerBasedShape,
    side: BubbleSide,
    position: GroupPosition,
    joined: CornerSize,
): CornerBasedShape {
    val above = position == GroupPosition.Middle || position == GroupPosition.Last
    val below = position == GroupPosition.First || position == GroupPosition.Middle
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
 * [tail], its bottom corner on that side drawn out into a tail.
 *
 * **The tail replaces the corner, it is not added to it.** The corner is filled
 * square from just before its curve starts on either edge — how far that is comes
 * from the body itself, [cornerReaches], since a squircle's curve starts well past
 * its radius. The side runs straight down into the tail, which bends out to a tip
 * on the bottom edge, so the bottom is one flat line from the far corner's curve
 * to the tip.
 *
 * Earlier tails met the body where its corner curve began and hooked back onto
 * the bottom from a little above it, so the body's curve showed through at the
 * join — "you can sort of see the bubble's curve start on that bottom corner" —
 * and before that were a spike on a rectangle reaching up the whole side, which
 * squared off a one-line bubble's round end.
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
        if (!tail || bodySize.width <= 0f || bodySize.height <= 0f) return Outline.Generic(path)

        // Where the body's own curves let go of its edges — the squircle's reach,
        // which is further than its radius — named for a tail on the right.
        val reaches = body.cornerReaches(bodySize, density, layoutDirection)
        val replaced = if (right) reaches.bottomRight else reaches.bottomLeft
        val far = if (right) reaches.bottomLeft else reaches.bottomRight
        val above = if (right) reaches.topRight else reaches.topLeft
        val w = size.width
        val h = size.height
        val edge = w - reach
        val seam = with(density) { TailSeam.toPx() }
        // The whole corner is filled square, from a little before its curve starts
        // on each edge. Along the bottom no further than the far corner's curve,
        // and up the side no further than the top corner's — on a one-line bubble,
        // the middle of its round end, which stays round.
        val junction = maxOf(edge - replaced.x - seam, far.x)
        val sideStart = maxOf(h - replaced.y - seam, above.y).coerceIn(0f, h)
        // The tail bends away from the side about as high as the corner it
        // replaces would have started, and never above the middle.
        val rise = maxOf(replaced.y, reach * TailClearance).coerceAtMost(h / 2f)
        val flareStart = maxOf(h - rise, sideStart)
        // Drawn for a tail on the right, then mirrored if it belongs on the left.
        fun x(at: Float) = if (right) at else w - at
        val tailPath = Path().apply {
            moveTo(x(junction), sideStart)
            lineTo(x(edge), sideStart)
            // Straight on down the side, then out to the tip.
            lineTo(x(edge), flareStart)
            cubicTo(
                x(edge), flareStart + (h - flareStart) * TailDrop,
                x(edge + reach * TailFlare), h - reach * TailFlareLift,
                x(w), h,
            )
            // And straight back along the bottom from the tip: one flat line from
            // the far corner's curve to the point.
            lineTo(x(junction), h)
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

// The tail's curves: shares of how far it rises and of its own reach.
// [TailClearance] is the least it rises, in reaches, for a bubble whose corner is
// smaller than the tail.
private const val TailClearance: Float = 1.6f
private const val TailDrop: Float = 0.5f
private const val TailFlare: Float = 0.15f
private const val TailFlareLift: Float = 1.3f

/** How far before a corner's curve starts the tail takes over the edge. */
private val TailSeam: Dp = 0.5.dp
