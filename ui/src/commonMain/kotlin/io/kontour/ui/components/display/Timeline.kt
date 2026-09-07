package io.kontour.ui.components.display

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.kontour.ui.theme.Theme

/** How the connector below a [TimelineItem] is drawn. */
enum class ConnectorStyle {
    /** A solid line. The default — a leg of a journey, a completed step. */
    Solid,

    /** Dashed. For a gap: a walk between stops, an interruption, an estimate. */
    Dashed,

    /** Nothing. For the last item, or a deliberate break. */
    None,
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
 */
object TimelineDefaults {
    /** Space above the node, and between it and the line leaving it. */
    val NodeGap: Dp = 2.dp
}

@Composable
fun Timeline(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth(), content = content)
}

/**
 * One event in a [Timeline].
 *
 * @param connector How to join this item to the next. The last item should pass
 *   [ConnectorStyle.None].
 * @param nodeColour The dot's colour. Takes a route colour straight from a feed.
 * @param filled A solid dot for a place the traveller actually stops; a hollow
 *   one for a point they pass through.
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
    /**
     * Whether this step is happening now, drawn as a spinner in place of the dot.
     *
     * For the step a timeline is waiting on — a train that has not been assigned
     * a platform, a payment being taken. The connector below it is unchanged: the
     * itinerary still runs on, and only the node says which part of it is in
     * flight.
     *
     * **Say it in the words as well.** The node is drawn, not announced — the
     * same rule [filled] carries — so a row that is only a spinner tells a screen
     * reader nothing at all. Put "in progress" in the item's own text.
     */
    loading: Boolean = false,
    nodeSize: Dp = 12.dp,
    gutterWidth: Dp = 28.dp,
    connectorWidth: Dp = Theme.sizing.borderWidthStrong,
    content: @Composable ColumnScope.() -> Unit,
) {
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
                        .padding(top = TimelineDefaults.NodeGap),
                    size = nodeSize,
                    colour = nodeColour,
                )
            }
            Canvas(Modifier.fillMaxHeight().width(gutterWidth)) {
                val centreX = size.width / 2f
                val nodeRadius = nodeSize.toPx() / 2f
                val stroke = connectorWidth.toPx()
                // The gap above and below the node is its own measure, not the
                // stroke's — a thick segment should not shove its dot down the
                // gutter and out of line with the ones above it.
                val nodeGap = TimelineDefaults.NodeGap.toPx()
                val nodeCentreY = nodeRadius + nodeGap

                if (connector != ConnectorStyle.None) {
                    val top = nodeCentreY + nodeRadius + nodeGap
                    if (top < size.height) {
                        drawLine(
                            color = connectorColour,
                            start = Offset(centreX, top),
                            end = Offset(centreX, size.height),
                            strokeWidth = stroke,
                            cap = StrokeCap.Round,
                            pathEffect = if (connector == ConnectorStyle.Dashed) {
                                PathEffect.dashPathEffect(
                                    floatArrayOf(stroke * 1.5f, stroke * 2f),
                                )
                            } else {
                                null
                            },
                        )
                    }
                }

                // The spinner above is the node while this is loading, so the
                // dot is not drawn at all rather than drawn under it. The
                // connector is: a step in flight still leads somewhere.
                if (loading) return@Canvas

                if (filled) {
                    drawCircle(
                        color = nodeColour,
                        radius = nodeRadius,
                        center = Offset(centreX, nodeCentreY),
                    )
                } else {
                    drawCircle(
                        color = nodeColour,
                        radius = nodeRadius - stroke / 2f,
                        center = Offset(centreX, nodeCentreY),
                        style = Stroke(width = stroke),
                    )
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
