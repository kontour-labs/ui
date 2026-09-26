package io.kontour.ui.components.display

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.LayoutScopeMarker
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.takeOrElse
import io.kontour.ui.components.list.ListItemDefaults
import io.kontour.ui.theme.Theme

/**
 * How far a [BranchTimeline]'s history has got: to the commit [reached] — a
 * deploy, a review, whatever is being tracked — and, while it moves on, towards
 * the commit [towards], one of [reached]'s children.
 *
 * [reached] pulses, and it and everything it descends from keep their lanes'
 * colours; the commits it has not reached, and their lines, are muted. The line
 * from [reached] up to [towards] carries a band travelling along it. An id the
 * history does not contain is ignored, as is a [towards] that is not a child of
 * [reached].
 */
@Immutable
data class BranchProgress(val reached: Any, val towards: Any? = null)

/**
 * The colours of a [BranchTimeline].
 *
 * @param lanes The colours lanes take in turn as branches open.
 * @param muted The commits and lines a [BranchProgress] has not reached.
 */
@Immutable
data class BranchTimelineColours(
    val lanes: List<Color>,
    val muted: Color,
)

object BranchTimelineDefaults {
    /**
     * The lanes' colours, taken in turn as branches open: the accent for the
     * first, then the status colours. Six is plenty — a history needing more at
     * once is past what a phone can show legibly anyway.
     */
    @Composable
    @ReadOnlyComposable
    fun palette(): List<Color> = listOf(
        Theme.colours.primary,
        Theme.colours.info.solid,
        Theme.colours.success.solid,
        Theme.colours.warning.solid,
        Theme.colours.danger.solid,
        Theme.colours.contentMuted,
    )

    /** The [palette] for the lanes, and a strong outline for what progress has not reached. */
    @Composable
    @ReadOnlyComposable
    fun colours(
        lanes: List<Color> = palette(),
        muted: Color = Theme.colours.outlineStrong,
    ): BranchTimelineColours = BranchTimelineColours(lanes, muted)

    /** The width of one lane beyond the first, which fits a node with a little room either side. */
    val LaneWidth: Dp get() = BranchLaneWidth

    /** A commit's diameter: the same as a [TimelineItem]'s node. */
    val NodeSize: Dp get() = TimelineNodeSize

    /** The column the first lane runs down, as a [TimelineList]'s rail does. */
    val GutterWidth: Dp get() = TimelineGutterWidth
}

/**
 * A commit's row in a [BranchTimeline], declared with [item] exactly as a
 * [TimelineList] stop is — a merge's node is a ring unless it says otherwise.
 */
@Stable
@LayoutScopeMarker
class BranchTimelineScope internal constructor(merge: Boolean) : TimelineRowScope(filledByDefault = !merge)

/**
 * A history that branches and merges, drawn the way `git log --graph` draws it:
 * each commit a list row, with lanes beside the rows for the branches.
 *
 * ```kotlin
 * BranchTimeline(
 *     items = commits,              // newest first
 *     id = { it.sha },
 *     parents = { it.parents },     // the first parent continues the lane
 * ) { commit ->
 *     item(onClick = { open(commit) }) {
 *         +commit.message
 *         supporting { +"${commit.author} · ${commit.date}" }
 *     }
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
 * Each commit's row is declared with [BranchTimelineScope.item], the same item a
 * [TimelineList] takes, so a commit's node and lines take everything a
 * timeline's stops do: a colour, a ring, a spinner while it is `loading`, and a
 * `connector` style, colour and weight for its lines down to its parents — a
 * dashed line for a branch that only exists locally, say.
 *
 * ### What the order has to be
 *
 * Newest first, and every commit above its parents — the order `git log` prints.
 * A parent that is not in the list keeps its lane running to the bottom, which is
 * right for a history cut off at a page boundary and wrong for one out of order.
 *
 * @param items The commits, newest first, each above its parents.
 * @param id Each commit's identity, as its children name it in [parents].
 * @param parents The commits each one comes from, first parent first.
 * @param enabled Whether the rows can be used.
 * @param progress How far the history has got, if it is being tracked through.
 * @param nodeSize The commits' diameter.
 * @param laneWidth The distance from one lane to the next.
 * @param gutterWidth The column the first lane runs down; the text starts half
 *   of one past the last lane.
 * @param content Declares each commit's row, with one [BranchTimelineScope.item].
 */
@Composable
fun <T> BranchTimeline(
    items: List<T>,
    id: (item: T) -> Any,
    parents: (item: T) -> List<Any>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    progress: BranchProgress? = null,
    colours: BranchTimelineColours = BranchTimelineDefaults.colours(),
    nodeSize: Dp = BranchTimelineDefaults.NodeSize,
    laneWidth: Dp = BranchTimelineDefaults.LaneWidth,
    gutterWidth: Dp = BranchTimelineDefaults.GutterWidth,
    content: BranchTimelineScope.(item: T) -> Unit,
) {
    val shape = remember(items, id, parents, progress) { BranchShape.of(items, id, parents, progress) }
    val stops = branchStops(items, parents, content)
    Column(modifier.fillMaxWidth()) {
        items.forEachIndexed { index, item ->
            key(id(item)) {
                BranchRow(shape, stops, index, enabled, colours, nodeSize, laneWidth, gutterWidth)
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
 *         item { +commit.message }
 *     }
 * }
 * ```
 *
 * The lanes are laid out for the whole list up front — a row cannot know which
 * lanes pass it without knowing every commit above — and the builder runs for
 * every commit, since a row draws other commits' lines past it. The rows
 * themselves stay lazy. **Leave the column's arrangement alone**: the lanes are
 * unbroken because the rows touch.
 *
 * @param colours Null takes [BranchTimelineDefaults.colours], which needs the
 *   theme and so is read inside each row.
 */
fun <T> LazyListScope.branchTimeline(
    items: List<T>,
    id: (item: T) -> Any,
    parents: (item: T) -> List<Any>,
    enabled: Boolean = true,
    progress: BranchProgress? = null,
    colours: BranchTimelineColours? = null,
    nodeSize: Dp = BranchTimelineDefaults.NodeSize,
    laneWidth: Dp = BranchTimelineDefaults.LaneWidth,
    gutterWidth: Dp = BranchTimelineDefaults.GutterWidth,
    content: BranchTimelineScope.(item: T) -> Unit,
) {
    val shape = BranchShape.of(items, id, parents, progress)
    val stops = branchStops(items, parents, content)
    items.forEachIndexed { index, item ->
        item(key = id(item), contentType = "branchTimelineRow") {
            BranchRow(
                shape, stops, index, enabled, colours ?: BranchTimelineDefaults.colours(),
                nodeSize, laneWidth, gutterWidth,
            )
        }
    }
}

/** A history's lanes, and how far a [BranchProgress] has got through them, by row. */
internal class BranchShape(
    val graph: LaneGraph,
    /** Rows [BranchProgress.reached] descends from, itself included; null without progress. */
    val reached: BooleanArray?,
    val reachedRow: Int,
    /** The child being moved on to, or -1. */
    val towardsRow: Int,
) {
    companion object {
        fun <T> of(items: List<T>, id: (item: T) -> Any, parents: (item: T) -> List<Any>, progress: BranchProgress?): BranchShape {
            val ids = items.map(id)
            val parentIds = items.map(parents)
            val graph = layOutLanes(ids, parentIds).bentEarly()
            val reached = progress?.let { reachedRows(ids, parentIds, it.reached) }
            val reachedRow = if (progress != null && reached != null) ids.indexOf(progress.reached) else -1
            val towardsRow = progress?.towards?.let { towards ->
                ids.indexOf(towards).takeIf { it >= 0 && progress.reached in parentIds[it] }
            } ?: -1
            return BranchShape(graph, reached, reachedRow, towardsRow)
        }
    }
}

/** Each commit's row as its builder declares it; a commit that declares none is a bare row. */
private fun <T> branchStops(
    items: List<T>,
    parents: (item: T) -> List<Any>,
    content: BranchTimelineScope.(item: T) -> Unit,
): List<TimelineStop> = items.map { item ->
    val merge = parents(item).distinct().size > 1
    BranchTimelineScope(merge).apply { content(item) }.stops.firstOrNull() ?: TimelineStop(
        nodeColour = Color.Unspecified,
        filled = !merge,
        loading = false,
        connector = ConnectorStyle.Solid,
        connectorColour = Color.Unspecified,
        connectorWidth = Dp.Unspecified,
        enabled = true,
        selected = false,
        role = Role.Button,
        onClick = null,
        content = {},
    )
}

@Composable
private fun BranchRow(
    shape: BranchShape,
    stops: List<TimelineStop>,
    index: Int,
    enabled: Boolean,
    colours: BranchTimelineColours,
    nodeSize: Dp,
    laneWidth: Dp,
    gutterWidth: Dp,
) {
    RailListRow(
        stop = stops[index],
        rail = branchRail(shape, index, stops, colours, Theme.sizing.borderWidthStrong),
        listEnabled = enabled,
        lanes = shape.graph.width,
        laneWidth = laneWidth,
        gutterWidth = gutterWidth,
        nodeSize = nodeSize,
        shape = ListItemDefaults.Shape,
        containerColour = Color.Transparent,
        disabledContainerColour = Color.Transparent,
        edged = false,
        gapBelow = 0.dp,
    )
}

/**
 * Row [index]'s rail: the lanes passing it, the ones closing into its commit and
 * the ones leaving it, each styled by the commit whose line to a parent it is.
 *
 * A line two commits share — both waiting on one parent down one lane — is
 * styled by the first of them progress has reached, or else by the one that
 * opened the lane. A band runs along the line from the reached commit up to
 * the one it is moving towards, counted from the parent's node in half rows,
 * so a line one row long is split at its seam exactly as a [TimelineList]'s is.
 */
internal fun branchRail(
    shape: BranchShape,
    index: Int,
    stops: List<TimelineStop>,
    colours: BranchTimelineColours,
    defaultWidth: Dp,
): RailRow {
    val row = shape.graph.rows[index]
    val reached = shape.reached
    fun ink(turn: Int) = if (colours.lanes.isEmpty()) colours.muted else colours.lanes[turn % colours.lanes.size]
    fun muted(edge: LaneEdge) = reached != null && edge.owners.none { reached[it] }
    fun leg(edge: LaneEdge, start: LegEnd, end: LegEnd, at: (span: Float) -> Pair<Float, Float>): RailLeg {
        val owner = reached?.let { r -> edge.owners.firstOrNull { r[it] } } ?: edge.owners.firstOrNull()
        val stop = owner?.let { stops[it] }
        val own = stop?.connectorColour ?: Color.Unspecified
        val colour = when {
            own.isSpecified -> own
            muted(edge) -> colours.muted
            else -> ink(edge.ink)
        }
        val moving = shape.towardsRow >= 0 && shape.towardsRow in edge.owners && edge.parent == shape.reachedRow
        val travel = if (moving) {
            val (from, to) = at((edge.parent - shape.towardsRow).toFloat())
            LegTravel(from, to, passed = 0f, band = true, colour = if (own.isSpecified) own else ink(edge.ink))
        } else {
            null
        }
        return RailLeg(
            start = start,
            end = end,
            style = stop?.connector ?: ConnectorStyle.Solid,
            colour = colour,
            width = stop?.connectorWidth?.takeOrElse { defaultWidth } ?: defaultWidth,
            travel = travel,
        )
    }
    // Positions along a line to a parent, in half rows up from the parent's node.
    fun top(edge: LaneEdge) = edge.parent - index + 0.5f
    fun bottom(edge: LaneEdge) = edge.parent - index - 0.5f

    val legs = ArrayList<RailLeg>()
    row.passing.forEach { edge ->
        legs += leg(edge, LegEnd(edge.from, LegAt.Start, RunEnd.Seam), LegEnd(edge.to, LegAt.End, RunEnd.Seam)) { span ->
            top(edge) / span to bottom(edge) / span
        }
    }
    // Lines closing into this commit arrive in its lane; the muted go first, and
    // its own colour is drawn last, over the rest.
    row.incoming.sortedWith(compareBy<LaneEdge>({ !muted(it) }, { it.ink == row.ink })).forEach { edge ->
        legs += leg(edge, LegEnd(edge.to, LegAt.Node, RunEnd.Mark), LegEnd(edge.from, LegAt.Start, RunEnd.Seam)) { span ->
            0f to 0.5f / span
        }
    }
    row.outgoing.forEach { edge ->
        legs += leg(edge, LegEnd(edge.from, LegAt.Node, RunEnd.Mark), LegEnd(edge.to, LegAt.End, RunEnd.Seam)) { span ->
            1f to (span - 0.5f) / span
        }
    }

    val stop = stops[index]
    val nodeColour = when {
        stop.nodeColour.isSpecified -> stop.nodeColour
        reached != null && !reached[index] -> colours.muted
        else -> ink(row.ink)
    }
    return RailRow(
        node = RailNode(
            lane = row.node,
            colour = nodeColour,
            filled = stop.filled,
            loading = stop.loading,
            ringWidth = stop.connectorWidth.takeOrElse { defaultWidth },
            here = index == shape.reachedRow,
        ),
        legs = legs,
    )
}

private val BranchLaneWidth: Dp = 20.dp
