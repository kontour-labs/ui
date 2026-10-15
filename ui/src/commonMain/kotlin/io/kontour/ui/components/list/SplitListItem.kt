package io.kontour.ui.components.list

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.foundation.layout.LayoutScopeMarker
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import io.kontour.ui.foundation.GroupPosition
import io.kontour.ui.foundation.shape
import io.kontour.ui.theme.Theme
import kotlin.math.roundToInt

/**
 * Two [ListItem]s side by side, as one row of a list.
 *
 * ```kotlin
 * SplitListItem(split = ListItemSplit.Halves, position = GroupPosition.First) {
 *     start(onClick = { open(wifi) }) {
 *         leading { +Tabler.Outline.Wifi }
 *         +"Wi-Fi"
 *         supporting { +"Home" }
 *     }
 *     end(onClick = { open(bluetooth) }) {
 *         leading { +Tabler.Outline.Bluetooth }
 *         +"Bluetooth"
 *         supporting { +"On" }
 *     }
 * }
 * ```
 *
 * Each half is a row in its own right — its own press, its own selection, its
 * own focus and its own stop for a screen reader — and the two are joined as
 * the rows of a group are: the hairline between them, and the corners that
 * face each other cut square. The pair takes its outside corners from
 * [position], so a split row sits in a [ListSection] or a [ListGroup] with its
 * neighbours as any row does, and lines up with them: the section's title
 * starts where the start half's words do.
 *
 * **How the width is shared is [split]**: halves, two thirds and one, or a
 * square beside the rest — a tile as wide as the row is tall, for the one
 * action that belongs to the row beside it. A square half holds its content
 * in its middle, which is for a glyph or a word or two, and grows with the
 * row: a start half that wraps to two lines makes a taller row and a wider
 * square.
 *
 * Both halves are always as tall as the taller one, so the seam runs the full
 * height of the row.
 *
 * @param split How the row's width is shared between the halves.
 * @param position Which of the pair's outside corners round, as on a
 *   [ListItem]: the pair is one row of the group.
 * @param content Says what each half holds: [SplitListItemScope.start] and
 *   [SplitListItemScope.end], each once.
 */
@Composable
fun SplitListItem(
    modifier: Modifier = Modifier,
    split: ListItemSplit = ListItemSplit.Halves,
    position: GroupPosition = GroupPosition.Only,
    content: SplitListItemScope.() -> Unit,
) {
    val parts = SplitListItemScope().apply(content)
    val start = requireNotNull(parts.start) { "A SplitListItem needs a start half: call start { … } in its builder" }
    val end = requireNotNull(parts.end) { "A SplitListItem needs an end half: call end { … } in its builder" }

    // The pair's corners are the group's; between the halves they are the
    // inner corner, as between two rows.
    val outer = position.shape(ListItemDefaults.Shape, ListItemDefaults.InnerCorner, Orientation.Vertical)
    val startShape = GroupPosition.First.shape(outer, ListItemDefaults.InnerCorner, Orientation.Horizontal)
    val endShape = GroupPosition.Last.shape(outer, ListItemDefaults.InnerCorner, Orientation.Horizontal)
    val gap = ListItemDefaults.Spacing
    val squareFloor = ListItemDefaults.MinHeight

    Layout(
        // Reports its top and bottom as a row does, so a section beside it
        // finds a row and lines its title up with the words.
        modifier = ListRowEdges.then(modifier),
        content = {
            SplitHalf(start, startShape, centred = split.start == SplitSize.Square)
            SplitHalf(end, endShape, centred = split.end == SplitSize.Square)
        },
    ) { measurables, constraints ->
        val (first, second) = measurables
        val gapPx = gap.roundToPx()
        // A row of a list is given a width. Where it is not, it is as wide as
        // the two halves' words want to be.
        val width = if (constraints.hasBoundedWidth) {
            constraints.maxWidth
        } else {
            first.maxIntrinsicWidth(constraints.maxHeight) + gapPx + second.maxIntrinsicWidth(constraints.maxHeight)
        }
        val across = (width - gapPx).coerceAtLeast(0)
        val floor = maxOf(constraints.minHeight, squareFloor.roundToPx())

        val startWidth: Int
        val height: Int
        when {
            split.start == SplitSize.Square || split.end == SplitSize.Square -> {
                val (square, rest) = if (split.start == SplitSize.Square) first to second else second to first
                // The square is as wide as the row is tall, and the row is as
                // tall as the other half is at what the square leaves it — so
                // a few rounds, each from the last, settle it. A start half
                // wrapping to two lines takes the square from 56 to 72 or so,
                // which takes a little more width from the words, and that is
                // where it rests. Never past half the row.
                var side = floor
                repeat(SquareRounds) {
                    side = maxOf(floor, rest.minIntrinsicHeight(across - side), square.minIntrinsicHeight(side))
                        .coerceAtMost(across / 2)
                }
                height = side
                startWidth = if (split.start == SplitSize.Square) side else across - side
            }

            else -> {
                val startWeight = (split.start as SplitSize.Weight).weight
                val endWeight = (split.end as SplitSize.Weight).weight
                startWidth = (across * startWeight / (startWeight + endWeight)).roundToInt()
                height = maxOf(floor, first.tallestAt(startWidth), second.tallestAt(across - startWidth))
            }
        }
        val endWidth = across - startWidth
        val tall = height.coerceIn(constraints.minHeight, constraints.maxHeight)
        val placedStart = first.measure(Constraints.fixed(startWidth, tall))
        val placedEnd = second.measure(Constraints.fixed(endWidth, tall))
        layout(width, tall) {
            placedStart.placeRelative(0, 0)
            placedEnd.placeRelative(startWidth + gapPx, 0)
        }
    }
}

/** How tall a half is at [width]: its words wrapped to that, and its row's floor. */
private fun IntrinsicMeasurable.tallestAt(width: Int): Int = minIntrinsicHeight(width.coerceAtLeast(0))

/**
 * How many times a square half and the row beside it are measured against each
 * other: a row's height moves the square once, and the square's width moves the
 * row's words once more at most.
 */
private const val SquareRounds = 3

@Composable
private fun SplitHalf(part: SplitListPart, shape: androidx.compose.ui.graphics.Shape, centred: Boolean) {
    ListItemImpl(
        modifier = part.contentDescription?.let { name -> Modifier.semantics { contentDescription = name } } ?: Modifier,
        enabled = part.enabled,
        onClick = part.onClick,
        selected = part.selected,
        role = part.role,
        shape = shape,
        containerColour = part.containerColour ?: Theme.colours.surfaceSunken,
        selectedContainerColour = Theme.colours.accent.container,
        contentColour = Theme.colours.content,
        minHeight = Dp.Unspecified,
        interactionSource = part.interactionSource,
        slots = listItemSlots(part.content),
        centred = centred,
    )
}

/**
 * How a [SplitListItem]'s width is shared between its halves.
 *
 * ```kotlin
 * SplitListItem(split = ListItemSplit.AutoSquare) { … }
 * SplitListItem(split = ListItemSplit.ratio(3f, 1f)) { … }
 * ```
 */
@Immutable
class ListItemSplit internal constructor(
    internal val start: SplitSize,
    internal val end: SplitSize,
) {
    override fun equals(other: Any?): Boolean =
        other is ListItemSplit && other.start == start && other.end == end

    override fun hashCode(): Int = start.hashCode() * 31 + end.hashCode()

    override fun toString(): String = "ListItemSplit($start, $end)"

    companion object {
        /** The width shared evenly: two rows of the same weight, side by side. */
        val Halves: ListItemSplit = ListItemSplit(SplitSize.Weight(1f), SplitSize.Weight(1f))

        /**
         * The start half takes what the end half leaves it, and the end half is
         * square: a tile as wide as the row is tall, for an action of the row
         * beside it — the call beside a contact, the details beside a network.
         */
        val AutoSquare: ListItemSplit = ListItemSplit(SplitSize.Weight(1f), SplitSize.Square)

        /** [AutoSquare] the other way round: the square at the start. */
        val SquareAuto: ListItemSplit = ListItemSplit(SplitSize.Square, SplitSize.Weight(1f))

        /** Two thirds at the start and one at the end. */
        val TwoToOne: ListItemSplit = ListItemSplit(SplitSize.Weight(2f), SplitSize.Weight(1f))

        /** One third at the start and two at the end. */
        val OneToTwo: ListItemSplit = ListItemSplit(SplitSize.Weight(1f), SplitSize.Weight(2f))

        /**
         * The width shared in the ratio [start] to [end], each above zero:
         * `ratio(3f, 1f)` is three quarters and a quarter.
         */
        fun ratio(start: Float, end: Float): ListItemSplit {
            require(start > 0f && end > 0f) { "A SplitListItem's halves both need a share above zero, not $start and $end" }
            return ListItemSplit(SplitSize.Weight(start), SplitSize.Weight(end))
        }
    }
}

/** One half's share of a [ListItemSplit]. */
@Immutable
internal sealed interface SplitSize {
    /** A share of the width the halves have between them. */
    data class Weight(val weight: Float) : SplitSize

    /** As wide as the row is tall. */
    data object Square : SplitSize
}

/**
 * What a [SplitListItem]'s halves hold: [start] and [end], each once.
 *
 * Each takes what a [ListItem] takes for its own press and selection, and a
 * [ListItem]'s builder for what it says. Not composable, as a [ListItem]'s
 * builder is not: it says what goes where, and the row draws it.
 */
@Stable
@LayoutScopeMarker
class SplitListItemScope internal constructor() {
    internal var start: SplitListPart? = null
    internal var end: SplitListPart? = null

    /**
     * The half at the start: the left one, or the right one in a language
     * read the other way.
     *
     * @param contentDescription What a screen reader calls the half, where its
     *   words do not say it: a square half of nothing but a glyph needs one.
     */
    fun start(
        onClick: (() -> Unit)? = null,
        enabled: Boolean = true,
        selected: Boolean = false,
        role: Role = Role.Button,
        contentDescription: String? = null,
        containerColour: Color? = null,
        interactionSource: MutableInteractionSource? = null,
        content: ListItemScope.() -> Unit,
    ) {
        check(start == null) { "A SplitListItem has one start half: start { … } was called twice" }
        start = SplitListPart(onClick, enabled, selected, role, contentDescription, containerColour, interactionSource, content)
    }

    /**
     * The half at the end. Takes what [start] takes.
     */
    fun end(
        onClick: (() -> Unit)? = null,
        enabled: Boolean = true,
        selected: Boolean = false,
        role: Role = Role.Button,
        contentDescription: String? = null,
        containerColour: Color? = null,
        interactionSource: MutableInteractionSource? = null,
        content: ListItemScope.() -> Unit,
    ) {
        check(end == null) { "A SplitListItem has one end half: end { … } was called twice" }
        end = SplitListPart(onClick, enabled, selected, role, contentDescription, containerColour, interactionSource, content)
    }
}

/** One half of a [SplitListItem], as its builder described it. */
internal class SplitListPart(
    val onClick: (() -> Unit)?,
    val enabled: Boolean,
    val selected: Boolean,
    val role: Role,
    val contentDescription: String?,
    val containerColour: Color?,
    val interactionSource: MutableInteractionSource?,
    val content: ListItemScope.() -> Unit,
)
