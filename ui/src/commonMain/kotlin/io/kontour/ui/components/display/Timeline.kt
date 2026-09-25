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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.ParentDataModifierNode
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

object TimelineDefaults {
    /** Space above the node, and between it and the line leaving it. */
    val NodeGap: Dp get() = TimelineNodeGap
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
 *     TimelineItem(nodeColour = routeColor, connector = ConnectorStyle.Solid) {
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
 */
@Composable
fun Timeline(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    CompositionLocalProvider(LocalTimelineOrientation provides Orientation.Vertical) {
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
 * @param connectorColour The line's colour, to the next item.
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
    nodeColour: Color = Theme.colours.primary,
    connectorColour: Color = Theme.colours.outlineStrong,
    filled: Boolean = true,
    loading: Boolean = false,
    nodeSize: Dp = 12.dp,
    gutterWidth: Dp = 28.dp,
    connectorWidth: Dp = Theme.sizing.borderWidthStrong,
    content: @Composable ColumnScope.() -> Unit,
) {
    // One leg, from the node to the end of the item, where the next one's node
    // begins: the same rail a list row draws, with one lane and one leg.
    val rail = remember(connector, nodeColour, connectorColour, filled, loading, connectorWidth) {
        RailRow(
            node = RailNode(lane = 0, colour = nodeColour, filled = filled, loading = loading, ringWidth = connectorWidth),
            legs = listOf(
                RailLeg(
                    start = LegEnd(0, LegAt.Node, RunEnd.Mark),
                    end = LegEnd(0, LegAt.End, RunEnd.Mark),
                    style = connector,
                    colour = connectorColour,
                    width = connectorWidth,
                ),
            ),
        )
    }
    if (LocalTimelineOrientation.current == Orientation.Horizontal) {
        AcrossItem(modifier, rail, nodeColour, loading, nodeSize, connectorWidth, content)
        return
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
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
                    colour = nodeColour,
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
                    rail,
                    Orientation.Vertical,
                    firstLane = size.width / 2f,
                    laneWidth = 0f,
                    nodeAlong = nodeRadius + TimelineNodeGap.toPx(),
                    nodeRadius = nodeRadius,
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
    rail: RailRow,
    nodeColour: Color,
    loading: Boolean,
    nodeSize: Dp,
    connectorWidth: Dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val slot = remember { TimelineSlot() }
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
                    rail,
                    Orientation.Horizontal,
                    firstLane = centre,
                    laneWidth = 0f,
                    nodeAlong = centre,
                    nodeRadius = nodeRadius,
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
    Column(
        modifier = Modifier
            .then(TimelinePartElement(TimelinePart(slot, TimelinePart.Kind.Label, band)))
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

/** One item's place in a [HorizontalTimeline], shared by its two parts. */
internal class TimelineSlot

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
 * @param equalWidths Every item as wide as the widest — or, if they would not
 *   fill the width available, as wide as an even share of it. Evenly spaced
 *   nodes, for stages whose spacing should not say anything about their names.
 * @param scrollState Where the timeline is scrolled to, for a caller that wants
 *   to bring the current stage into view.
 * @param content The [TimelineItem]s, in order.
 */
@Composable
fun HorizontalTimeline(
    modifier: Modifier = Modifier,
    equalWidths: Boolean = false,
    scrollState: ScrollState = rememberScrollState(),
    content: @Composable () -> Unit,
) {
    val labelGap = Theme.spacing.xs
    CompositionLocalProvider(LocalTimelineOrientation provides Orientation.Horizontal) {
        BoxWithConstraints(modifier.fillMaxWidth()) {
            val viewport = if (constraints.hasBoundedWidth) constraints.maxWidth else 0
            Layout(content = content, modifier = Modifier.horizontalScroll(scrollState)) { measurables, outer ->
                val items = acrossItems(measurables)
                val widest = AcrossMaximumWidth.roundToPx()
                val gap = labelGap.roundToPx()
                val height = outer.maxHeight
                val maxBand = items.maxOfOrNull { it.bandHeight.roundToPx() } ?: 0
                val labelHeight = if (height == Constraints.Infinity) height else (height - maxBand - gap).coerceAtLeast(0)
                val even = if (equalWidths && items.isNotEmpty()) {
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
                // Each node is as far along as the labels before it are wide,
                // and each band runs to the next node.
                val pitches = items.mapIndexed { i, item ->
                    labels[i]?.width ?: (item.bandHeight + AcrossMinimumRun).roundToPx()
                }
                val bands = items.mapIndexed { i, item ->
                    item.band?.measure(Constraints.fixed(pitches[i], item.bandHeight.roundToPx()))
                }
                val tallest = labels.maxOfOrNull { it?.height ?: 0 } ?: 0
                layout(pitches.sum(), maxBand + gap + tallest) {
                    var x = 0
                    items.indices.forEach { i ->
                        // Every node on one line, whatever size each one is.
                        bands[i]?.let { it.placeRelative(x, (maxBand - it.height) / 2) }
                        labels[i]?.placeRelative(x, maxBand + gap)
                        x += pitches[i]
                    }
                }
            }
        }
    }
}

/** One item across a [HorizontalTimeline]: its band and its label, as the timeline measures them. */
private class AcrossParts(var band: Measurable? = null, var label: Measurable? = null, var bandHeight: Dp = 0.dp)

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
        val item = bySlot.getOrPut(part.slot) { AcrossParts().also { items += it } }
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
