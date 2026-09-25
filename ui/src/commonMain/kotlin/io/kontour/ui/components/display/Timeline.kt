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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
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
    if (LocalTimelineOrientation.current == Orientation.Horizontal) {
        AcrossItem(modifier, connector, nodeColour, connectorColour, filled, loading, nodeSize, connectorWidth, content)
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
                val centreX = size.width / 2f
                val nodeRadius = nodeSize.toPx() / 2f
                val stroke = connectorWidth.toPx()
                // The gap above and below the node is its own measure, not the
                // stroke's — a thick segment should not shove its dot down the
                // gutter and out of line with the ones above it.
                val nodeGap = TimelineNodeGap.toPx()
                val nodeCentreY = nodeRadius + nodeGap

                val top = nodeCentreY + nodeRadius + nodeGap
                if (top < size.height) {
                    drawConnectorRun(
                        connector,
                        from = Offset(centreX, top),
                        to = Offset(centreX, size.height),
                        stroke = stroke,
                        colour = connectorColour,
                    )
                }

                // The spinner above is the node while this is loading, so the
                // dot is not drawn at all rather than drawn under it. The
                // connector is: a step in flight still leads somewhere.
                if (!loading) {
                    drawTimelineNode(Offset(centreX, nodeCentreY), nodeRadius, stroke, nodeColour, filled)
                }
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
 * A [TimelineItem] across a [HorizontalTimeline]: a band along its top with the
 * node at its start and the connector to its end edge, and the content under it.
 */
@Composable
private fun AcrossItem(
    modifier: Modifier,
    connector: ConnectorStyle,
    nodeColour: Color,
    connectorColour: Color,
    filled: Boolean,
    loading: Boolean,
    nodeSize: Dp,
    connectorWidth: Dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val band = nodeSize + TimelineNodeGap * 2
    Column(
        modifier = modifier
            // Never so narrow that the connector has nowhere to run.
            .widthIn(min = band + AcrossMinimumRun)
            .drawBehind {
                val nodeRadius = nodeSize.toPx() / 2f
                val stroke = connectorWidth.toPx()
                val nodeGap = TimelineNodeGap.toPx()
                val y = nodeGap + nodeRadius
                // Laid out from the start edge, whichever side that is.
                val rtl = layoutDirection == LayoutDirection.Rtl
                fun x(fromStart: Float) = if (rtl) size.width - fromStart else fromStart
                val nodeX = nodeGap + nodeRadius
                val runStart = nodeX + nodeRadius + nodeGap
                if (runStart < size.width) {
                    drawConnectorRun(
                        connector,
                        from = Offset(x(runStart), y),
                        to = Offset(x(size.width), y),
                        stroke = stroke,
                        colour = connectorColour,
                    )
                }
                if (!loading) drawTimelineNode(Offset(x(nodeX), y), nodeRadius, stroke, nodeColour, filled)
            }
            // The content stops short of the next node, as a row's content stops
            // short of the next row down the page.
            .padding(end = Theme.spacing.md),
    ) {
        Box(Modifier.height(band)) {
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
            // Under the node, starting where it does.
            modifier = Modifier.padding(start = TimelineNodeGap, top = Theme.spacing.xs),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            content = content,
        )
    }
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
    CompositionLocalProvider(LocalTimelineOrientation provides Orientation.Horizontal) {
        BoxWithConstraints(modifier.fillMaxWidth()) {
            val viewport = if (constraints.hasBoundedWidth) constraints.maxWidth else 0
            Layout(content = content, modifier = Modifier.horizontalScroll(scrollState)) { measurables, outer ->
                val widest = AcrossMaximumWidth.roundToPx()
                val height = outer.maxHeight
                val even = if (equalWidths && measurables.isNotEmpty()) {
                    maxOf(
                        measurables.maxOf { it.maxIntrinsicWidth(height) }.coerceAtMost(widest),
                        viewport / measurables.size,
                    )
                } else {
                    null
                }
                val placeables = measurables.map {
                    it.measure(
                        if (even != null) {
                            Constraints(minWidth = even, maxWidth = even, maxHeight = height)
                        } else {
                            Constraints(maxWidth = widest, maxHeight = height)
                        },
                    )
                }
                layout(placeables.sumOf { it.width }, placeables.maxOfOrNull { it.height } ?: 0) {
                    var x = 0
                    placeables.forEach {
                        it.placeRelative(x, 0)
                        x += it.width
                    }
                }
            }
        }
    }
}

/** The shortest connector an item across a [HorizontalTimeline] draws. */
private val AcrossMinimumRun: Dp = 24.dp

/** The widest an item across a [HorizontalTimeline] grows before its text wraps. */
private val AcrossMaximumWidth: Dp = 200.dp
