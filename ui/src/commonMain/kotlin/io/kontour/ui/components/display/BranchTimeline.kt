package io.kontour.ui.components.display

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import io.kontour.ui.components.list.ListItemDefaults
import io.kontour.ui.components.list.ListItemImpl
import io.kontour.ui.components.list.ListItemScope
import io.kontour.ui.components.list.listItemSlots
import io.kontour.ui.theme.Theme

object BranchTimelineDefaults {
    /**
     * The lanes' colours, taken in turn as branches open: the accent for the
     * first, then the status colours. Six is plenty — a history needing more at
     * once is past what a phone can show legibly anyway.
     */
    @Composable
    fun palette(): List<Color> = listOf(
        Theme.colours.primary,
        Theme.colours.info.solid,
        Theme.colours.success.solid,
        Theme.colours.warning.solid,
        Theme.colours.danger.solid,
        Theme.colours.contentMuted,
    )

    /** The width of one lane, which fits a node with a little room either side. */
    val LaneWidth: Dp get() = BranchLaneWidth

    /** A commit's diameter: the same as a [TimelineItem]'s node. */
    val NodeSize: Dp get() = TimelineNodeSize
}

/**
 * A history that branches and merges, drawn the way `git log --graph` draws it:
 * each commit a list row, with lanes beside the rows for the branches.
 *
 * ```kotlin
 * BranchTimeline(
 *     items = commits,              // newest first
 *     id = { it.sha },
 *     parents = { it.parents },     // the first parent continues the lane
 *     onItemClick = { open(it) },
 * ) { commit ->
 *     +commit.message
 *     supporting { +"${commit.author} · ${commit.date}" }
 * }
 * ```
 *
 * For a history where things fork and come back together: a repository's
 * commits, a document's versions, a plan's revisions. The caller says only which
 * items come from which — **the lanes are laid out for you**. A commit continues
 * the lane of its first parent; each further parent is a merge, and the branch it
 * brings in runs in a lane of its own, in the next colour, until it closes into
 * the commit it forked from.
 *
 * ### What the order has to be
 *
 * Newest first, and every commit above its parents — the order `git log` prints.
 * A parent that is not in the list keeps its lane running to the bottom, which is
 * right for a history cut off at a page boundary and wrong for one out of order.
 *
 * ### Merges are rings
 *
 * A commit with more than one parent is drawn hollow, so a merge reads as a join
 * rather than as work. Every other commit is a solid dot.
 *
 * @param items The commits, newest first, each above its parents.
 * @param id Each commit's identity, as its children name it in [parents].
 * @param parents The commits each one comes from, first parent first.
 * @param enabled Whether the rows can be used.
 * @param isSelected Whether a commit is the current one, which tints its row.
 * @param onItemClick Makes each row a button. Without it the rows are not controls.
 * @param laneColour A colour for a commit's lane from that commit down — a
 *   release branch always green, say. Null takes the palette's next.
 * @param palette The colours lanes take in turn.
 * @param laneWidth The width of each lane beside the rows.
 * @param content Each commit's row, filled like a `ListItem`.
 */
@Composable
fun <T> BranchTimeline(
    items: List<T>,
    id: (item: T) -> Any,
    parents: (item: T) -> List<Any>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isSelected: (item: T) -> Boolean = { false },
    onItemClick: ((item: T) -> Unit)? = null,
    laneColour: ((item: T) -> Color?)? = null,
    palette: List<Color> = BranchTimelineDefaults.palette(),
    laneWidth: Dp = BranchTimelineDefaults.LaneWidth,
    content: ListItemScope.(item: T) -> Unit,
) {
    val graph = remember(items, id, parents, laneColour) { branchGraph(items, id, parents, laneColour) }
    Column(modifier.fillMaxWidth()) {
        items.forEachIndexed { index, item ->
            key(id(item)) {
                BranchRow(
                    row = graph.rows[index],
                    lanes = graph.width,
                    merge = parents(item).distinct().size > 1,
                    palette = palette,
                    laneWidth = laneWidth,
                    enabled = enabled,
                    selected = isSelected(item),
                    onClick = onItemClick?.let { click -> { click(item) } },
                    content = { content(item) },
                )
            }
        }
    }
}

/**
 * The same history, inside a `LazyColumn`, with [id] as each row's key.
 *
 * ```kotlin
 * LazyColumn {
 *     branchTimeline(commits, id = { it.sha }, parents = { it.parents }) { commit ->
 *         +commit.message
 *     }
 * }
 * ```
 *
 * The lanes are laid out for the whole list up front — a row cannot know which
 * lanes pass it without knowing every commit above — and the rows themselves
 * stay lazy. **Leave the column's arrangement alone**: the lanes are unbroken
 * because the rows touch.
 *
 * @param palette Null takes [BranchTimelineDefaults.palette], which needs the
 *   theme and so is read inside each row.
 */
fun <T> LazyListScope.branchTimeline(
    items: List<T>,
    id: (item: T) -> Any,
    parents: (item: T) -> List<Any>,
    enabled: Boolean = true,
    isSelected: (item: T) -> Boolean = { false },
    onItemClick: ((item: T) -> Unit)? = null,
    laneColour: ((item: T) -> Color?)? = null,
    palette: List<Color>? = null,
    laneWidth: Dp = BranchTimelineDefaults.LaneWidth,
    content: ListItemScope.(item: T) -> Unit,
) {
    val graph = branchGraph(items, id, parents, laneColour)
    items.forEachIndexed { index, item ->
        item(key = id(item), contentType = "branchTimelineRow") {
            BranchRow(
                row = graph.rows[index],
                lanes = graph.width,
                merge = parents(item).distinct().size > 1,
                palette = palette ?: BranchTimelineDefaults.palette(),
                laneWidth = laneWidth,
                enabled = enabled,
                selected = isSelected(item),
                onClick = onItemClick?.let { click -> { click(item) } },
                content = { content(item) },
            )
        }
    }
}

private fun <T> branchGraph(
    items: List<T>,
    id: (item: T) -> Any,
    parents: (item: T) -> List<Any>,
    laneColour: ((item: T) -> Color?)?,
): LaneGraph = layOutLanes(
    ids = items.map(id),
    parents = items.map(parents),
    explicit = if (laneColour == null) emptyList() else items.map { laneColour(it) ?: Color.Unspecified },
).bentEarly()

@Composable
private fun BranchRow(
    row: LaneRow,
    lanes: Int,
    merge: Boolean,
    palette: List<Color>,
    laneWidth: Dp,
    enabled: Boolean,
    selected: Boolean,
    onClick: (() -> Unit)?,
    content: ListItemScope.() -> Unit,
) {
    val start = Theme.spacing.xs
    val stroke = Theme.sizing.borderWidthStrong
    val nodeSize = BranchTimelineDefaults.NodeSize
    val label = Theme.typography.bodyMedium
    val lineHeight = with(LocalDensity.current) {
        if (label.lineHeight.isSpecified) label.lineHeight.toPx() else label.fontSize.toPx() * FallbackLeading
    }
    val nodeY = remember { mutableFloatStateOf(0f) }

    Layout(
        content = {
            ListItemImpl(
                modifier = Modifier,
                enabled = enabled,
                onClick = onClick,
                selected = selected,
                role = Role.Button,
                shape = ListItemDefaults.Shape,
                containerColour = Color.Transparent,
                selectedContainerColour = Theme.colours.accent.container,
                contentColour = Theme.colours.content,
                minHeight = Dp.Unspecified,
                interactionSource = null,
                slots = listItemSlots(content),
                startPadding = start + laneWidth * lanes.coerceAtLeast(1),
                labelModifier = Modifier.timelineNodeLine(lineHeight),
                edged = false,
                disabledContainerColour = Color.Transparent,
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .drawWithContent {
                drawContent()
                val rtl = layoutDirection == LayoutDirection.Rtl
                val lane = laneWidth.toPx()
                val inset = start.toPx()
                fun x(j: Int): Float = (inset + (j + 0.5f) * lane).let { if (rtl) size.width - it else it }
                val width = stroke.toPx()
                val radius = nodeSize.toPx() / 2f
                val clear = radius + TimelineNodeGap.toPx()
                val y = nodeY.floatValue
                val h = size.height
                val node = Offset(x(row.node), y)
                val lineStyle = Stroke(width = width, cap = StrokeCap.Butt)

                // Everything but the commit's own dot, which sits in a gap cut
                // out of the lines meeting it — the same gap as a timeline's.
                // A line from lane [from] at one height to lane [to] at another,
                // leaving and arriving straight down so it meets the rows above
                // and below where they expect it.
                fun lane(from: Offset, to: Offset, colour: Color) {
                    if (from.x == to.x) {
                        drawLine(colour, from, to, width)
                    } else {
                        val middle = (from.y + to.y) / 2f
                        drawPath(
                            Path().apply {
                                moveTo(from.x, from.y)
                                cubicTo(from.x, middle, to.x, middle, to.x, to.y)
                            },
                            colour,
                            style = lineStyle,
                        )
                    }
                }

                // Everything but the commit's own dot, which sits in a gap cut
                // out of the lines meeting it — the same gap as a timeline's.
                clipPath(Path().apply { addOval(Rect(node, clear)) }, ClipOp.Difference) {
                    row.passing.forEach { edge ->
                        lane(Offset(x(edge.from), 0f), Offset(x(edge.to), h), edge.ink.resolve(palette))
                    }
                    // Lines closing into this commit arrive in its lane; its own
                    // colour is drawn last, over theirs.
                    row.incoming.sortedBy { it.ink == row.ink }.forEach { edge ->
                        lane(Offset(x(edge.from), 0f), node, edge.ink.resolve(palette))
                    }
                    row.outgoing.forEach { edge ->
                        lane(node, Offset(x(edge.to), h), edge.ink.resolve(palette))
                    }
                }
                drawTimelineNode(node, radius, width, row.ink.resolve(palette), filled = !merge)
            },
    ) { measurables, constraints ->
        val card = measurables[0].measure(constraints.copy(minHeight = 0))
        val line = card[TimelineNodeLine]
        nodeY.floatValue = (if (line == AlignmentLine.Unspecified) card.height / 2 else line).toFloat()
        layout(card.width, card.height) { card.placeRelative(0, 0) }
    }
}

private val BranchLaneWidth: Dp = 20.dp

/** A line's height against its type size, for a style that does not set one. */
private const val FallbackLeading: Float = 1.4f
