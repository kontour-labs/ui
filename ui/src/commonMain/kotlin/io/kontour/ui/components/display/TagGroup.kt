package io.kontour.ui.components.display

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.LayoutScopeMarker
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import io.kontour.ui.a11y.LocalTouchTargetOwnedByParent
import io.kontour.ui.components.action.ButtonGroupDefaults
import io.kontour.ui.a11y.minimumTouchTarget
import io.kontour.ui.foundation.GroupPosition
import io.kontour.ui.foundation.RowContentScope
import io.kontour.ui.foundation.shape
import io.kontour.ui.theme.Theme
import io.kontour.ui.theme.Tone

/** How the parts of a [TagGroup] meet. */
enum class TagJoin {
    /** Square across, as the parts of a button group meet. */
    Straight,

    /**
     * On a slant, leaning as a forward slash does in the reading direction:
     * `T1 ╱ Fremantle`. Mirrored right to left.
     */
    Diagonal,
}

/**
 * Tags joined into one: a route and where it goes, a version and its state.
 *
 * ```kotlin
 * TagGroup(join = TagJoin.Diagonal) {
 *     item(containerColour = routeColour) { +"T1" }
 *     item { +"Fremantle" }
 * }
 * ```
 *
 * Each part is a [Tag] in all but name, with its own tone or colour, its own
 * text colour derived from that, and its own `onClick` if it has one. The group
 * does the shaping: only the outside corners round, and where two parts meet
 * they are cut square across or, with [TagJoin.Diagonal], on a slant. The
 * parts are told nothing about where they sit, the same way a [io.kontour.ui.components.action.ButtonGroup]'s
 * buttons are not.
 *
 * Asked for for 0.5.3 as "multi-tags", in two kinds that turned out to be one
 * component and a number. With [spacing] at its default, 0, the parts are one
 * tag split into pieces. With a little room between them,
 * [TagGroupDefaults.Gap], they are separate tags standing together, with
 * tighter corners where they face each other.
 *
 * Two neighbours of the same colour would run together into one wider tag, so
 * the group leaves a hairline between them, [TagGroupDefaults.Seam], however
 * small [spacing] is. Give the parts different tones, or different colours,
 * for a tag that reads as one shape.
 *
 * ### When to use it
 *
 * When the labels belong to each other, and reading them apart would lose
 * something: a line and its terminus, a platform and its side, a key and its
 * value. Labels that only happen to sit in the same row are a row of [Tag]s.
 *
 * ### Reading it
 *
 * A group of plain tags is one thing to a screen reader, its parts read in
 * order as one announcement: "T1, Fremantle". A group with a part that can be
 * pressed, or one with its own `contentDescription`, is a group of separate
 * items, each read as its own tag would be.
 *
 * @param enabled Applied to every part that can be pressed, beside its own.
 * @param join Square across, the default, or on a slant.
 * @param spacing The room between neighbours. 0, the default, joins them into
 *   one tag; [TagGroupDefaults.Gap] stands them apart as a group.
 * @param shape The group's outside corners. Where parts meet the corners are
 *   square, or no rounder than [TagGroupDefaults.InnerCorner] when there is
 *   room between them.
 */
@Composable
fun TagGroup(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    join: TagJoin = TagJoin.Straight,
    spacing: Dp = 0.dp,
    shape: CornerBasedShape = Theme.shapes.control,
    content: TagGroupScope.() -> Unit,
) {
    val parts = TagGroupScope().apply(content).parts
    if (parts.isEmpty()) return

    // Read apart when anything in it is a control, or says something its text
    // does not: a merged node reads its descriptions in place of its text, so
    // one part's description would silence every other part.
    val apart = parts.any { it.onClick != null || it.contentDescription != null }
    val pressable = parts.any { it.onClick != null }
    val containers = parts.map { tagContainer(it.tone, it.containerColour) }
    val slant = if (join == TagJoin.Diagonal) TagGroupDefaults.Slant else 0.dp
    val gaps = List(parts.size - 1) { index ->
        if (containers[index] == containers[index + 1]) max(spacing, TagGroupDefaults.Seam) else spacing
    }
    val inner = if (spacing > 0.dp) TagGroupDefaults.InnerCorner else 0.dp

    Layout(
        modifier = modifier
            // The group owns the target, as a button group does: a part that
            // reserved its own would push its neighbours apart. See
            // [LocalTouchTargetOwnedByParent].
            .then(if (pressable) Modifier.minimumTouchTarget() else Modifier)
            .semantics(mergeDescendants = !apart) {
                if (apart) isTraversalGroup = true
            },
        content = {
            CompositionLocalProvider(LocalTouchTargetOwnedByParent provides true) {
                parts.forEachIndexed { index, part ->
                    val position = GroupPosition.of(index, parts.size)
                    val joinsBefore = index > 0
                    val joinsAfter = index < parts.lastIndex
                    val partShape = when (join) {
                        TagJoin.Straight -> position.shape(shape, inner, Orientation.Horizontal)
                        TagJoin.Diagonal -> DiagonalPartShape(
                            outer = position.shape(shape, 0.dp, Orientation.Horizontal),
                            joinsBefore = joinsBefore,
                            joinsAfter = joinsAfter,
                            slant = slant,
                        )
                    }
                    TagBody(
                        modifier = Modifier,
                        enabled = enabled && part.enabled,
                        tone = part.tone,
                        containerColour = part.containerColour,
                        shape = partShape,
                        onClick = part.onClick,
                        contentDescription = part.contentDescription,
                        interactionSource = part.interactionSource,
                        // A slant takes the corner off one end of the part's
                        // side, nothing off the other: half of it, on average,
                        // so the content keeps its room from the cut's middle.
                        startPadding = TagPadding + if (joinsBefore) slant / 2 else 0.dp,
                        endPadding = TagPadding + if (joinsAfter) slant / 2 else 0.dp,
                        merge = apart,
                        content = part.content,
                    )
                }
            }
        },
    ) { measurables, constraints ->
        val slantPx = slant.roundToPx()
        // Every part as tall as the tallest, so the joins run the full height:
        // an icon in one part is a pixel or two taller than text in the next.
        val height = measurables.maxOf { it.maxIntrinsicHeight(Constraints.Infinity) }
            .coerceIn(constraints.minHeight, constraints.maxHeight)
        // Where each part starts. Joined on a slant, a part starts under the
        // one before it by the slant's width, less any room between them.
        //
        // And a pixel more where they touch. Two shapes meeting on a slant
        // both soften the same pixels along it, and where neither covers the
        // whole of a pixel the page shows through between them, a faint line
        // down a tag that should be one. The earlier part reaching a pixel
        // under the later one leaves only the later one's edge, softened over
        // the colour beside it.
        val starts = IntArray(measurables.size)
        val placeables = ArrayList<Placeable>(measurables.size)
        var x = 0
        measurables.forEachIndexed { index, measurable ->
            starts[index] = x
            val room = if (constraints.hasBoundedWidth) (constraints.maxWidth - x).coerceAtLeast(0) else Constraints.Infinity
            val placeable = measurable.measure(Constraints(maxWidth = room, minHeight = height, maxHeight = height))
            placeables += placeable
            if (index < gaps.size) {
                val gap = gaps[index].roundToPx()
                val bleed = if (slantPx > 0 && gap == 0) 1 else 0
                x += placeable.width - slantPx + gap - bleed
            }
        }
        val width = (starts.last() + placeables.last().width).coerceIn(constraints.minWidth, constraints.maxWidth)
        layout(width, height) {
            // In order, so each part draws over the edge of the one before it.
            placeables.forEachIndexed { index, placeable -> placeable.placeRelative(starts[index], 0) }
        }
    }
}

/** One collected part. */
internal class TagGroupPart(
    val tone: Tone,
    val containerColour: Color,
    val onClick: (() -> Unit)?,
    val enabled: Boolean,
    val contentDescription: String?,
    val interactionSource: MutableInteractionSource?,
    val content: @Composable RowContentScope.() -> Unit,
)

/**
 * Collects a [TagGroup]'s parts.
 *
 * A builder rather than a row of [Tag]s, for the reason a button group's is
 * one: each part's shape depends on how many there are and where it sits, and
 * that is not known until they have all been declared.
 */
@LayoutScopeMarker
@Stable
class TagGroupScope internal constructor() {
    internal val parts = mutableListOf<TagGroupPart>()

    /**
     * A part, which takes what a [Tag] takes.
     *
     * @param containerColour An explicit background, overriding [tone]. The
     *   text colour is derived from it, as a tag's is.
     * @param onClick What a press does, or `null` for a part that is only a
     *   label.
     * @param contentDescription Overrides what the part announces, for text
     *   that is an abbreviation.
     */
    fun item(
        tone: Tone = Tone.Neutral,
        containerColour: Color = Color.Unspecified,
        onClick: (() -> Unit)? = null,
        enabled: Boolean = true,
        contentDescription: String? = null,
        interactionSource: MutableInteractionSource? = null,
        content: @Composable RowContentScope.() -> Unit,
    ) {
        parts += TagGroupPart(tone, containerColour, onClick, enabled, contentDescription, interactionSource, content)
    }
}

/** What a [TagGroup] takes by default. */
object TagGroupDefaults {
    /**
     * Room between parts that stand apart rather than join: enough to see the
     * page between them, little enough that they are still one group.
     */
    val Gap: Dp = 2.dp

    /**
     * The least room between two parts of the same colour. Flush, they would
     * be one wider tag with no sign of where one part ends: a button group's
     * [io.kontour.ui.components.action.ButtonGroupDefaults.Seam], for the same
     * reason.
     */
    val Seam: Dp = ButtonGroupDefaults.Seam

    /**
     * How far a diagonal join leans: the distance across, top to bottom. About
     * fifteen degrees on a tag's 22dp, enough to read as a slant and not so
     * much that it takes the corner off a short label.
     */
    val Slant: Dp = 6.dp

    /**
     * The radius where parts that stand apart face each other, as a button
     * group's buttons do: [io.kontour.ui.components.action.ButtonGroupDefaults.InnerCorner],
     * for the same reason.
     */
    val InnerCorner: Dp = ButtonGroupDefaults.InnerCorner
}

/**
 * A part of a diagonally joined group: [outer], its outside corners, with each
 * joined side cut on a slant.
 *
 * Cut from the outer shape rather than drawn, so a squircle's corners stay a
 * squircle's and a square theme's stay square.
 */
private class DiagonalPartShape(
    private val outer: Shape,
    private val joinsBefore: Boolean,
    private val joinsAfter: Boolean,
    private val slant: Dp,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val lean = with(density) { slant.toPx() }
        val w = size.width
        val h = size.height
        // In the reading direction, mirrored for right to left: the start side
        // leans from `lean` along the top to 0 along the bottom, the end side
        // from `w` along the top to `w - lean` along the bottom, so every join
        // in a group leans the same way.
        val rtl = layoutDirection == LayoutDirection.Rtl
        fun x(at: Float) = if (rtl) w - at else at
        val cut = Path().apply {
            moveTo(x(if (joinsBefore) lean else 0f), 0f)
            lineTo(x(w), 0f)
            lineTo(x(if (joinsAfter) w - lean else w), h)
            lineTo(x(0f), h)
            close()
        }
        val whole = Path().apply { addOutline(outer.createOutline(size, layoutDirection, density)) }
        return Outline.Generic(Path().apply { op(whole, cut, PathOperation.Intersect) })
    }

    override fun equals(other: Any?): Boolean =
        other is DiagonalPartShape && other.outer == outer && other.joinsBefore == joinsBefore &&
            other.joinsAfter == joinsAfter && other.slant == slant

    override fun hashCode(): Int {
        var result = outer.hashCode()
        result = 31 * result + joinsBefore.hashCode()
        result = 31 * result + joinsAfter.hashCode()
        result = 31 * result + slant.hashCode()
        return result
    }
}
