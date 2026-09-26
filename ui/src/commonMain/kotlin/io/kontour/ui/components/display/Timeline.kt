package io.kontour.ui.components.display

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.FloatState
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.ParentDataModifierNode
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.kontour.ui.theme.Theme

/** How the connector below a [TimelineItem] is drawn. */
enum class ConnectorStyle {
    /** A solid line. The default — a leg of a journey, a completed step. */
    Solid,

    /** Dashed. For a gap: a walk between stops, an interruption, an estimate. */
    Dashed,

    /**
     * Dotted. A lighter gap than [Dashed].
     *
     * Round dots the width of the connector, one gap of their own diameter
     * apart — which is why it reads as quieter than a dash without being a
     * different weight. For the part of an itinerary that is not a leg at all:
     * a wait, a transfer window, an estimate nobody has committed to.
     *
     * A dot is as wide as [TimelineItem]'s `connectorWidth`, so a 4dp train
     * segment and a 2dp walk get dots in proportion. The run is spaced to put one
     * on each end of it rather than to a fixed pitch — see `drawConnectorRun` for
     * why that is not the same thing as a dash pattern of zero-length dashes,
     * which is what this was.
     */
    Dotted,

    /** Nothing. For the last item, or a deliberate break. */
    None,
}

/** Where a [HorizontalTimeline]'s labels go against its rail. */
enum class TimelineLabelPlacement {
    /** Under the rail, each starting at its node. */
    Below,

    /** Over the rail, for a timeline that sits along the bottom of something. */
    Above,

    /**
     * Taking turns, the first under the rail and the next over it. Each label
     * only has to clear the next one on its own side, two nodes on, so the
     * stages can sit closer than their names are long — more of them across a
     * phone before any has to wrap.
     */
    Alternating,
}

/**
 * The colours of a [Timeline], a [HorizontalTimeline] or a [TimelineList].
 *
 * @param node A stop's node, unless the stop names its own.
 * @param rail The connectors, unless a stop names its own — and, while there is
 *   a `progress`, the nodes not reached yet.
 * @param progress The rail and nodes already passed, the halo round the stop
 *   the journey is at, and the band on the leg it is travelling.
 */
@Immutable
data class TimelineColours(
    val node: Color,
    val rail: Color,
    val progress: Color,
)

/**
 * The geometry the timeline family shares — [Timeline], [HorizontalTimeline],
 * [TimelineList] and [BranchTimeline] — so a rail drawn by one lines up with
 * a rail drawn by another.
 */
object TimelineDefaults {
    /** The theme's accent for nodes and progress, a strong outline for the rail. */
    @Composable
    @ReadOnlyComposable
    fun colours(
        node: Color = Theme.colours.primary,
        rail: Color = Theme.colours.outlineStrong,
        progress: Color = Theme.colours.primary,
    ): TimelineColours = TimelineColours(node, rail, progress)

    /** Space above the node, and between it and the line leaving it. */
    val NodeGap: Dp get() = TimelineNodeGap

    /** A node's diameter: a [TimelineItem]'s, a [TimelineList] stop's and a [BranchTimeline] commit's. */
    val NodeSize: Dp get() = TimelineNodeSize

    /**
     * The column the rail runs down, beside the content — a [TimelineList]'s
     * rail, and a [BranchTimeline]'s first lane.
     */
    val GutterWidth: Dp get() = TimelineGutterWidth

    /**
     * A leg's weight, and a ring node's: the strong border width. What a row
     * in a [TimelineList] or a [BranchTimeline] means by `Dp.Unspecified`.
     */
    val ConnectorWidth: Dp
        @Composable @ReadOnlyComposable get() = Theme.sizing.borderWidthStrong
}

/**
 * A vertical sequence of events joined by a connector.
 *
 * Built for the journey itinerary — board at one stop, ride, alight, walk, board
 * again — which is why the connector has a [ConnectorStyle.Dashed] mode for
 * walking legs and takes a per-item colour for route branding.
 *
 * ```
 * Timeline {
 *     TimelineItem(nodeColour = routeColour, connector = ConnectorStyle.Solid) {
 *         Text("Perth Station"); Text("Platform 3", …)
 *     }
 *     TimelineItem(connector = ConnectorStyle.Dashed) { Text("Walk 4 min") }
 *     TimelineItem(connector = ConnectorStyle.None) { Text("Elizabeth Quay") }
 * }
 * ```
 *
 * The connector is drawn to the **full height of its row**, so an item with
 * three lines of content gets a longer line than one with one. That is the
 * detail that makes a timeline look built rather than assembled: a fixed-height
 * connector leaves gaps against tall rows and overshoots short ones.
 *
 * For the same events across the page — a delivery's stages, a short trip —
 * see [HorizontalTimeline], which takes the same [TimelineItem]s. For an
 * itinerary whose stops are list rows, with trailing content and a tap each,
 * see [TimelineList].
 *
 * @param progress How far along the journey is, in items: `0f` at the first,
 *   `1.5f` halfway between the second and the third. The rail and nodes up to
 *   there take the progress colour and the rest the rail's; the item it is at
 *   pulses, and the leg it is on carries a band travelling towards the next.
 *   Null for a timeline that is not being travelled. Counted in the order the
 *   items are laid out, so an item wrapped in something else still counts.
 * @param colours The nodes', rail's and progress's colours, for items that do
 *   not name their own.
 */
@Composable
fun Timeline(
    modifier: Modifier = Modifier,
    progress: Float? = null,
    colours: TimelineColours = TimelineDefaults.colours(),
    content: @Composable ColumnScope.() -> Unit,
) {
    val registry = remember { TimelineRegistry() }
    CompositionLocalProvider(
        LocalTimelineContext provides TimelineContext(Orientation.Vertical, progress, colours, registry),
    ) {
        Column(modifier.fillMaxWidth(), content = content)
    }
}

/**
 * One event in a [Timeline] or a [HorizontalTimeline].
 *
 * Down a [Timeline] the node sits in a gutter beside the content, level with its
 * first line, and the connector runs down the gutter to the bottom of the row.
 * Across a [HorizontalTimeline] the node sits above the content, at its start,
 * and the connector runs along to the item's end edge — where the next item's
 * node begins, the same gap on from it as down the page.
 *
 * @param connector How to join this item to the next. The last item should pass
 *   [ConnectorStyle.None].
 * @param nodeColour The dot's colour. Takes a route colour straight from a feed.
 *   Unspecified takes the timeline's — or, while the timeline is being
 *   travelled, its progress colour once reached and its rail's before.
 * @param connectorColour The line's colour, to the next item. Unspecified takes
 *   the timeline's rail.
 * @param filled A solid dot for a place the traveller actually stops; a hollow
 *   one for a point they pass through.
 * @param loading Whether this step is happening now, drawn as a spinner in place
 *   of the dot. For the step a timeline is waiting on — a train that has not been
 *   assigned a platform, a payment being taken. The connector is unchanged: the
 *   itinerary still runs on, and only the node says which part of it is in
 *   flight. **Say it in the words as well**: the node is drawn, not announced —
 *   the same rule [filled] carries — so put "in progress" in the item's own text.
 * @param nodeSize The dot's diameter.
 * @param gutterWidth The width of the column the node and connector run down,
 *   beside the content. Down a [Timeline] only; across a [HorizontalTimeline] the
 *   node sits above the content and there is no gutter.
 * @param connectorWidth How thick the line to the next item is. Per item, not
 *   per timeline, because a leg's weight is part of what it *is*: a 4dp train
 *   segment and a 2dp walk between two stations says which part of the journey
 *   is the journey. Also sets the hollow node's ring, so a dot and the line
 *   leaving it stay the same weight.
 */
@Composable
fun TimelineItem(
    modifier: Modifier = Modifier,
    connector: ConnectorStyle = ConnectorStyle.Solid,
    nodeColour: Color = Color.Unspecified,
    connectorColour: Color = Color.Unspecified,
    filled: Boolean = true,
    loading: Boolean = false,
    nodeSize: Dp = TimelineDefaults.NodeSize,
    gutterWidth: Dp = TimelineDefaults.GutterWidth,
    connectorWidth: Dp = TimelineDefaults.ConnectorWidth,
    content: @Composable ColumnScope.() -> Unit,
) {
    val context = LocalTimelineContext.current
    val colours = context?.colours ?: TimelineDefaults.colours()
    val progress = context?.progress
    val slot = remember { TimelineSlot() }
    // Where the journey stands against this item. Read in composition only to
    // start and stop the motion and to colour a spinner; the rail itself reads
    // the index as it draws, after the timeline has laid its items out.
    val here = slot.index.let { if (it >= 0) stopProgress(progress, it) else null }
    val phase = rememberRailPhase(running = here != null && ((here.here && !loading) || here.legBand))
    val spinnerColour = nodeColourFor(nodeColour, here, colours.node, colours.progress, colours.rail)
    // One leg, from the node to the end of the item, where the next one's node
    // begins: the same rail a list row draws, with one lane and one leg.
    fun rail(): RailRow {
        val at = slot.index.let { if (it >= 0) stopProgress(progress, it) else null }
        return RailRow(
            node = RailNode(
                lane = 0,
                colour = nodeColourFor(nodeColour, at, colours.node, colours.progress, colours.rail),
                filled = filled,
                loading = loading,
                ringWidth = connectorWidth,
                here = at?.here == true,
            ),
            legs = listOf(
                RailLeg(
                    start = LegEnd(0, LegAt.Node, RunEnd.Mark),
                    end = LegEnd(0, LegAt.End, RunEnd.Mark),
                    style = connector,
                    colour = connectorColour.takeOrElse { colours.rail },
                    width = connectorWidth,
                    travel = at?.let { LegTravel(0f, 1f, it.legPassed, it.legBand, colours.progress) },
                ),
            ),
        )
    }
    if (context?.axis == Orientation.Horizontal) {
        AcrossItem(modifier, slot, ::rail, phase, spinnerColour, loading, nodeSize, connectorWidth, content)
        return
    }
    if (context != null) {
        DisposableEffect(context.registry, slot) {
            context.registry.slots += slot
            onDispose { context.registry.slots -= slot }
        }
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .onPlaced {
                slot.coordinates = it
                context?.registry?.reindex()
            }
            // Intrinsic height is what lets the connector match the row's real
            // height instead of a guess.
            .height(IntrinsicSize.Min),
    ) {
        Box(Modifier.width(gutterWidth).fillMaxHeight()) {
            // The gutter's one composable slot, and the node's centre is *not*
            // this box's centre: the dot sits `nodeSize / 2 + NodeGap` from the
            // top so it lines up with the first line of the content beside it,
            // whatever height the row turns out to be. A spinner standing in for
            // the dot has to land on the same point, which is what the top
            // padding buys — `Alignment.Center` would drop it halfway down a tall
            // row and break the rail.
            if (loading) {
                Spinner(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = TimelineNodeGap),
                    size = nodeSize,
                    colour = spinnerColour,
                    // The same weight as the ring it stands in for.
                    //
                    // `Spinner` derives a stroke from its size — `size / 9`,
                    // floored at 1.5dp — which at the 12dp default node works
                    // out at 1.5dp against the hollow dot's 2dp. So a step
                    // going into progress got visibly thinner, and the rail
                    // above and below it did not. `connectorWidth` already
                    // sets the hollow node's ring, and its own KDoc promises
                    // "a dot and the line leaving it stay the same weight" —
                    // this is the third drawing that promise has to cover.
                    strokeWidth = connectorWidth,
                )
            }
            Canvas(Modifier.fillMaxHeight().width(gutterWidth)) {
                val nodeRadius = nodeSize.toPx() / 2f
                // The gap above the node is its own measure, not the stroke's —
                // a thick segment should not shove its dot down the gutter and
                // out of line with the ones above it. The spinner above is the
                // node while this is loading, so the rail leaves the dot out;
                // the connector stays: a step in flight still leads somewhere.
                drawRail(
                    rail(),
                    Orientation.Vertical,
                    firstLane = size.width / 2f,
                    laneWidth = 0f,
                    nodeAlong = nodeRadius + TimelineNodeGap.toPx(),
                    nodeRadius = nodeRadius,
                    phase = phase.floatValue,
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = Theme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            content = content,
        )
    }
}

/**
 * A [TimelineItem] across a [HorizontalTimeline], as two parts the timeline lays
 * out: a band with the node at its start and the connector to its end edge, and
 * the content — the label — which the timeline puts under the band.
 *
 * Two parts rather than one item so the timeline can space the nodes by the
 * labels and put every band on one line, whatever the labels do. The caller's
 * [modifier] is the label's: it is the part with something to click or measure.
 */
@Composable
private fun AcrossItem(
    modifier: Modifier,
    slot: TimelineSlot,
    rail: () -> RailRow,
    phase: FloatState,
    nodeColour: Color,
    loading: Boolean,
    nodeSize: Dp,
    connectorWidth: Dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val band = nodeSize + TimelineNodeGap * 2
    Box(
        Modifier
            .then(TimelinePartElement(TimelinePart(slot, TimelinePart.Kind.Band, band)))
            .drawBehind {
                // The same rail, turned across: the node a gap in from the start
                // edge, whichever side that is, and the leg to the end edge —
                // where the next item's band begins.
                val nodeRadius = nodeSize.toPx() / 2f
                val centre = TimelineNodeGap.toPx() + nodeRadius
                drawRail(
                    rail(),
                    Orientation.Horizontal,
                    firstLane = centre,
                    laneWidth = 0f,
                    nodeAlong = centre,
                    nodeRadius = nodeRadius,
                    phase = phase.floatValue,
                )
            },
    ) {
        if (loading) {
            Spinner(
                modifier = Modifier.align(Alignment.CenterStart).padding(start = TimelineNodeGap),
                size = nodeSize,
                colour = nodeColour,
                strokeWidth = connectorWidth,
            )
        }
    }
    val index = slot.index
    Column(
        modifier = Modifier
            .then(TimelinePartElement(TimelinePart(slot, TimelinePart.Kind.Label, band)))
            .semantics { traversalIndex = index.toFloat() }
            .then(modifier)
            // Never so narrow that the connector has nowhere to run.
            .widthIn(min = band + AcrossMinimumRun)
            // Starting where the node does, and stopping short of the next
            // node, as a row's content stops short of the next row down.
            .padding(start = TimelineNodeGap, end = Theme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        content = content,
    )
}

/**
 * One [TimelineItem]'s place in its timeline: which item it is, counted in the
 * order the timeline lays them out. Shared, across, by an item's two parts.
 */
internal class TimelineSlot {
    /** The item's index, or -1 until its timeline has laid it out. */
    var index by mutableIntStateOf(-1)

    /** Where the item was last placed, for a [Timeline] to put its items in order. */
    var coordinates: LayoutCoordinates? = null
}

/**
 * The [TimelineItem]s of one [Timeline], numbered in the order they sit down the
 * page. A `Column`'s children cannot be counted as they are laid out, so each
 * item says where it was placed and the timeline ranks them — which also counts
 * an item wrapped in something else, or one inserted between two others.
 */
internal class TimelineRegistry {
    val slots = mutableListOf<TimelineSlot>()

    fun reindex() {
        slots
            .mapNotNull { slot -> slot.coordinates?.takeIf { it.isAttached }?.let { slot to it.positionInRoot().y } }
            .sortedBy { it.second }
            .forEachIndexed { index, (slot, _) -> if (slot.index != index) slot.index = index }
    }
}

/** What a [TimelineItem] needs from the timeline it is in: which way it runs, and how it is being travelled. */
internal class TimelineContext(
    val axis: Orientation,
    val progress: Float?,
    val colours: TimelineColours,
    val registry: TimelineRegistry,
)

/** The timeline a [TimelineItem] is in, or null for one standing on its own. */
internal val LocalTimelineContext = compositionLocalOf<TimelineContext?> { null }

/** Which part of which item a layout node is, for the [HorizontalTimeline] laying it out. */
internal class TimelinePart(val slot: TimelineSlot, val kind: Kind, val band: Dp) {
    enum class Kind { Band, Label }
}

/**
 * Tags a node as a [TimelinePart]. Put first in the chain, so it is what the
 * timeline reads whatever the caller's modifier says.
 */
private class TimelinePartElement(val part: TimelinePart) : ModifierNodeElement<TimelinePartNode>() {
    override fun create() = TimelinePartNode(part)
    override fun update(node: TimelinePartNode) {
        node.part = part
    }
    override fun equals(other: Any?) = other is TimelinePartElement && other.part.slot === part.slot &&
        other.part.kind == part.kind && other.part.band == part.band
    override fun hashCode() = 31 * (31 * part.slot.hashCode() + part.kind.hashCode()) + part.band.hashCode()
}

private class TimelinePartNode(var part: TimelinePart) : Modifier.Node(), ParentDataModifierNode {
    override fun Density.modifyParentData(parentData: Any?): Any = part
}

/**
 * [TimelineItem]s across the page, joined left to right — right to left in a
 * right-to-left layout — and scrolled sideways when there are more than fit.
 *
 * ```kotlin
 * HorizontalTimeline {
 *     TimelineItem { Text("Ordered"); Text("Mon", style = Theme.typography.bodySmall) }
 *     TimelineItem { Text("Packed"); Text("Tue", style = Theme.typography.bodySmall) }
 *     TimelineItem(filled = false, connector = ConnectorStyle.None) { Text("Delivered") }
 * }
 * ```
 *
 * For a handful of stages read at a glance — an order's progress, a short trip —
 * where [Timeline] down the page would spend a screen on four words. The items
 * are the same [TimelineItem]s with the same connectors and nodes; laid out
 * across, each puts its node at its start with the content under it, and its
 * connector runs to where the next one begins.
 *
 * Each item is as wide as its content, up to a limit past which its text wraps,
 * so a long stage name does not push the rest off the page.
 *
 * **Not a `Row`.** Its items are measured by the timeline, inside a scroller, so
 * a `weight` would be asked to share an unbounded width; [equalWidths] is how to
 * ask for even spacing instead.
 *
 * @param progress How far along the stages are, counted in items, as a
 *   [Timeline]'s is.
 * @param labelPlacement Each item's content under the rail, over it, or
 *   taking turns.
 * @param equalWidths Every item as wide as the widest — or, if they would not
 *   fill the width available, as wide as an even share of it. Evenly spaced
 *   nodes, for stages whose spacing should not say anything about their names.
 * @param colours The nodes', rail's and progress's colours, for items that do
 *   not name their own.
 * @param scrollState Where the timeline is scrolled to, for a caller that wants
 *   to bring the current stage into view.
 * @param content The [TimelineItem]s, in order.
 */
@Composable
fun HorizontalTimeline(
    modifier: Modifier = Modifier,
    progress: Float? = null,
    labelPlacement: TimelineLabelPlacement = TimelineLabelPlacement.Below,
    equalWidths: Boolean = false,
    colours: TimelineColours = TimelineDefaults.colours(),
    scrollState: ScrollState = rememberScrollState(),
    content: @Composable () -> Unit,
) {
    val labelGap = Theme.spacing.xs
    val registry = remember { TimelineRegistry() }
    CompositionLocalProvider(
        LocalTimelineContext provides TimelineContext(Orientation.Horizontal, progress, colours, registry),
    ) {
        BoxWithConstraints(modifier.fillMaxWidth()) {
            val viewport = if (constraints.hasBoundedWidth) constraints.maxWidth else 0
            Layout(
                content = content,
                // Read in item order, whichever side of the rail each label is on.
                modifier = Modifier.horizontalScroll(scrollState).semantics { isTraversalGroup = true },
            ) { measurables, outer ->
                val items = acrossItems(measurables)
                items.forEachIndexed { index, item -> item.slot?.let { if (it.index != index) it.index = index } }
                fun above(index: Int) = when (labelPlacement) {
                    TimelineLabelPlacement.Below -> false
                    TimelineLabelPlacement.Above -> true
                    TimelineLabelPlacement.Alternating -> index % 2 == 1
                }
                val alternating = labelPlacement == TimelineLabelPlacement.Alternating
                // Room before the first node for its pulse, while there is one to show.
                val inset = if (progress != null && items.isNotEmpty()) {
                    (items[0].bandHeight / 2 * (PulseReach - 1f)).roundToPx()
                } else {
                    0
                }
                val widest = AcrossMaximumWidth.roundToPx()
                val gap = labelGap.roundToPx()
                val height = outer.maxHeight
                val maxBand = items.maxOfOrNull { it.bandHeight.roundToPx() } ?: 0
                val labelHeight = if (height == Constraints.Infinity) height else (height - maxBand - gap).coerceAtLeast(0)
                val minPitches = items.map { (it.bandHeight + AcrossMinimumRun).roundToPx() }
                val even = if (equalWidths && items.isNotEmpty() && !alternating) {
                    maxOf(
                        items.maxOf { it.label?.maxIntrinsicWidth(height) ?: 0 }.coerceAtMost(widest),
                        viewport / items.size,
                    )
                } else {
                    null
                }
                val labels = items.map {
                    it.label?.measure(
                        if (even != null) {
                            Constraints(minWidth = even, maxWidth = even, maxHeight = labelHeight)
                        } else {
                            Constraints(maxWidth = widest, maxHeight = labelHeight)
                        },
                    )
                }
                val widths = items.indices.map { labels[it]?.width ?: minPitches[it] }
                // Where each node is. In a row of labels, as far along as the
                // labels before it are wide; taking turns, far enough on that a
                // label clears the one two before it, on its own side.
                val x = IntArray(items.size + 1)
                for (i in items.indices) {
                    x[i + 1] = when {
                        !alternating -> x[i] + widths[i]
                        equalWidths -> 0
                        i == 0 -> x[i] + minPitches[i]
                        else -> maxOf(x[i] + minPitches[i], x[i - 1] + widths[i - 1])
                    }
                }
                if (alternating && equalWidths && items.isNotEmpty()) {
                    val pitch = maxOf(
                        minPitches.max(),
                        (widths.max() + 1) / 2,
                        viewport / items.size,
                    )
                    for (i in 1..items.size) x[i] = i * pitch
                }
                // The last item's band runs as far as its own label, or its
                // shortest run; taking turns, the next label on may be wider.
                if (alternating && items.isNotEmpty()) {
                    x[items.size] = x[items.size - 1] + maxOf(minPitches.last(), if (equalWidths) x[1] else widths.last())
                }
                val bands = items.mapIndexed { i, item ->
                    item.band?.measure(Constraints.fixed(x[i + 1] - x[i], item.bandHeight.roundToPx()))
                }
                val overHeight = items.indices.filter { above(it) }.maxOfOrNull { labels[it]?.height ?: 0 }
                val underHeight = items.indices.filter { !above(it) }.maxOfOrNull { labels[it]?.height ?: 0 }
                val railTop = if (overHeight != null) overHeight + gap else 0
                val width = maxOf(x[items.size], items.indices.maxOfOrNull { x[it] + widths[it] } ?: 0)
                layout(inset + width, railTop + maxBand + if (underHeight != null) gap + underHeight else 0) {
                    items.indices.forEach { i ->
                        // Every node on one line, whatever size each one is.
                        bands[i]?.let { it.placeRelative(inset + x[i], railTop + (maxBand - it.height) / 2) }
                        labels[i]?.let {
                            val y = if (above(i)) railTop - gap - it.height else railTop + maxBand + gap
                            it.placeRelative(inset + x[i], y)
                        }
                    }
                }
            }
        }
    }
}

/** One item across a [HorizontalTimeline]: its band and its label, as the timeline measures them. */
private class AcrossParts(
    var band: Measurable? = null,
    var label: Measurable? = null,
    var bandHeight: Dp = 0.dp,
    var slot: TimelineSlot? = null,
)

/**
 * The timeline's children paired up by item. A child that is not part of a
 * [TimelineItem] is an item of its own with no band — a label on the page.
 */
private fun acrossItems(measurables: List<Measurable>): List<AcrossParts> {
    val items = ArrayList<AcrossParts>()
    val bySlot = HashMap<TimelineSlot, AcrossParts>()
    measurables.forEach { measurable ->
        val part = measurable.parentData as? TimelinePart
        if (part == null) {
            items += AcrossParts(label = measurable)
            return@forEach
        }
        val item = bySlot.getOrPut(part.slot) { AcrossParts(slot = part.slot).also { items += it } }
        when (part.kind) {
            TimelinePart.Kind.Band -> {
                item.band = measurable
                item.bandHeight = part.band
            }
            TimelinePart.Kind.Label -> item.label = measurable
        }
    }
    return items
}

/** The shortest connector an item across a [HorizontalTimeline] draws. */
private val AcrossMinimumRun: Dp = 24.dp

/** The widest an item across a [HorizontalTimeline] grows before its text wraps. */
private val AcrossMaximumWidth: Dp = 200.dp
