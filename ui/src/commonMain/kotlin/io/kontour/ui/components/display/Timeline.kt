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
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.kontour.ui.theme.Theme
import kotlin.math.roundToInt

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
     * on each end of it rather than to a fixed pitch — see `dottedRun` for why
     * that is not the same thing as a dash pattern of zero-length dashes, which
     * is what this was.
     */
    Dotted,

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
                val nodeGap = TimelineDefaults.NodeGap.toPx()
                val nodeCentreY = nodeRadius + nodeGap

                val top = nodeCentreY + nodeRadius + nodeGap
                if (connector != ConnectorStyle.None && top < size.height) {
                    // **A connector ends where the row ends, whatever it is made
                    // of.** All three used to be one line with a dash pattern
                    // over it, and a dash pattern is walked from the start of the
                    // path and abandoned wherever it has got to — so the run
                    // finished at the last whole period and the remainder was
                    // blank. Reported of the dots, which lose a whole dot and can
                    // lose it even when the pitch divides the run exactly, since
                    // a zero-length dash sitting on the path's own end is not
                    // drawn at all. The dashes lose up to one gap the same way.
                    //
                    // Both are now spaced to the run they have rather than to a
                    // multiple of the stroke. See [dottedRun] and [dashes].
                    val run = size.height - top
                    when (connector) {
                        ConnectorStyle.Dotted ->
                            dottedRun(centreX, top, run, stroke, connectorColour)
                        ConnectorStyle.Solid, ConnectorStyle.Dashed -> drawLine(
                            color = connectorColour,
                            start = Offset(centreX, top),
                            end = Offset(centreX, size.height),
                            strokeWidth = stroke,
                            cap = StrokeCap.Round,
                            pathEffect = if (connector == ConnectorStyle.Dashed) {
                                dashes(run, stroke)
                            } else {
                                null
                            },
                        )
                        ConnectorStyle.None -> Unit
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

/**
 * A run of round dots down [run] pixels from [top], landing on both ends of it.
 *
 * [ConnectorStyle.Dotted] used to be a dash of length zero with a round cap
 * drawn over the same line the other two styles use — which is a neat way to get
 * a dot of the stroke's own diameter, and the wrong way to finish a run. Skia
 * walks a dash pattern from the start of the path and stops when the path does,
 * so the last dot landed on the last whole multiple of the pitch and the
 * remainder of the gutter was empty. Worse, a zero-length dash that falls on the
 * path's own endpoint is not drawn at all, so a row whose height *did* divide by
 * the pitch still came up one dot short. That is the reported "stops just a bit
 * short of the actual timeline point": between one and two dot diameters of
 * nothing above the node below.
 *
 * Placing the dots fixes the end because the end is one of them. The pitch is
 * nominally two diameters — a dot and a gap of its own size, which is what makes
 * this read quieter than a dash at the same weight — and is then stretched or
 * squeezed by less than half of one so a whole number of them spans the run. At a
 * 2dp connector that is under 2dp of difference spread over the whole row, which
 * nothing can see; a gap at one end is the thing that was reported.
 *
 * One `drawPoints` rather than a circle each, so this is still one draw call for
 * the run.
 */
private fun DrawScope.dottedRun(
    x: Float,
    top: Float,
    run: Float,
    stroke: Float,
    colour: Color,
) {
    val steps = (run / (stroke * 2f)).roundToInt().coerceAtLeast(1)
    val pitch = run / steps
    drawPoints(
        points = List(steps + 1) { Offset(x, top + it * pitch) },
        pointMode = PointMode.Points,
        color = colour,
        strokeWidth = stroke,
        cap = StrokeCap.Round,
    )
}

/**
 * [ConnectorStyle.Dashed]'s pattern, sized so the last dash ends on the run's end.
 *
 * The same fault as the dots and a milder version of it: the pattern is a dash
 * then a gap, so a run that happens to finish inside a gap finishes with up to a
 * whole gap of nothing. The dash length is left alone — it is the thing that says
 * "dashed" — and the gap takes the adjustment, so `n` dashes and `n − 1` gaps
 * span the run exactly and the last dash lands on the bottom of the gutter.
 *
 * A run too short for one dash and one gap is drawn as one unbroken dash, because
 * the alternative is a single mark that does not reach either end of a gutter it
 * barely fits in.
 */
private fun dashes(run: Float, stroke: Float): PathEffect? {
    val on = stroke * DashLength
    val gaps = ((run - on) / (on + stroke * DashGap)).roundToInt()
    if (gaps < 1) return null
    val pitch = (run - on) / gaps
    return PathEffect.dashPathEffect(floatArrayOf(on, pitch - on))
}

/** A dash is a stroke and a half long, and the gap after it two. */
private const val DashLength = 1.5f
private const val DashGap = 2f
