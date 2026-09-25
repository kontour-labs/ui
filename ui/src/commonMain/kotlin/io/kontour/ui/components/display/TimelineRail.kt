package io.kontour.ui.components.display

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.HorizontalAlignmentLine
import androidx.compose.ui.layout.LastBaseline
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/*
 * The rail every timeline draws: the connector runs and the nodes on them.
 *
 * `Timeline`, `HorizontalTimeline`, `TimelineList` and `BranchTimeline` all join
 * their nodes with the same three connector styles at the same weights, so the
 * spacing of a dot or a dash is decided once, here, and a dotted walk looks the
 * same in any of them.
 */

/** A node's diameter, unless the caller says otherwise. */
internal val TimelineNodeSize: Dp = 12.dp

/** The width of the column the rail runs down, beside the content. */
internal val TimelineGutterWidth: Dp = 28.dp

/** The space between a node and the line leaving it, and above the node. */
internal val TimelineNodeGap: Dp = 2.dp

/**
 * How a connector's run ends, which decides how its dots or dashes are spaced.
 *
 * Both runs start with a mark: a dot or a dash sits on the node end of every
 * connector, so a connector leaves its node the same way whatever else is true.
 */
internal enum class RunEnd {
    /**
     * A mark on the far end too. For a run that ends at the next node's gap, as
     * a [TimelineItem]'s does: both ends of the connector are visibly attached.
     */
    Mark,

    /**
     * Half a gap at the far end. For a run that stops at a seam between two rows
     * that each draw half of one connector — [TimelineList]'s — so the two halves
     * meet with one whole gap between their last marks, as if drawn in one go,
     * rather than two marks touching or a gap twice the size.
     */
    Seam,
}

/**
 * Where the dots of a [ConnectorStyle.Dotted] run sit, as distances from its
 * node end.
 *
 * The pitch is nominally two dot diameters — a dot and a gap of its own size,
 * which is what makes this read quieter than a dash at the same weight — then
 * stretched or squeezed by less than half of one so a whole number of them
 * spans the run: from a dot on the node end to a dot on the far end ([RunEnd.Mark])
 * or to half a pitch short of it ([RunEnd.Seam]).
 *
 * The dots are placed rather than patterned. They were once a dash of length
 * zero with a round cap, and Skia walks a dash pattern from the start of the path
 * and stops when the path does, so the last dot landed on the last whole pitch
 * and the rest of the run was empty. Worse, a zero-length dash on the path's own
 * endpoint is not drawn at all, so a run that *did* divide by the pitch still
 * came up one dot short: "stops just a bit short of the actual timeline point".
 */
internal fun dotOffsets(run: Float, stroke: Float, end: RunEnd): FloatArray {
    if (run <= 0f || stroke <= 0f) return floatArrayOf(0f)
    val nominal = stroke * 2f
    return when (end) {
        RunEnd.Mark -> {
            val steps = (run / nominal).roundToInt().coerceAtLeast(1)
            val pitch = run / steps
            FloatArray(steps + 1) { it * pitch }
        }
        RunEnd.Seam -> {
            // n dots, n - 1 whole pitches and a half one to the seam.
            val dots = (run / nominal + 0.5f).roundToInt().coerceAtLeast(1)
            val pitch = run / (dots - 0.5f)
            FloatArray(dots) { it * pitch }
        }
    }
}

/**
 * A [ConnectorStyle.Dashed] run's dash and gap, spaced so the run ends where it
 * should: on the end of a dash ([RunEnd.Mark]) or half a gap past one
 * ([RunEnd.Seam]).
 *
 * The dash length is left alone — it is the thing that says "dashed" — and the
 * gap takes the adjustment. A run too short for one dash and one gap is null,
 * drawn as one unbroken line, because the alternative is a single mark that does
 * not reach either end of a gutter it barely fits in.
 */
internal fun dashIntervals(run: Float, stroke: Float, end: RunEnd): FloatArray? {
    val on = stroke * DashLength
    val nominal = stroke * DashGap
    return when (end) {
        RunEnd.Mark -> {
            // n dashes and n - 1 gaps.
            val gaps = ((run - on) / (on + nominal)).roundToInt()
            if (gaps < 1) return null
            val pitch = (run - on) / gaps
            floatArrayOf(on, pitch - on)
        }
        RunEnd.Seam -> {
            // n dashes, n - 1 gaps and a half one to the seam.
            val dashes = ((run + nominal / 2f) / (on + nominal)).roundToInt()
            if (dashes < 1) return null
            val gap = (run - dashes * on) / (dashes - 0.5f)
            if (gap <= 0f) return null
            floatArrayOf(on, gap)
        }
    }
}

/**
 * One connector run, from its node end [from] to [to], in [style].
 *
 * **A connector ends where its run ends, whatever it is made of.** The dots and
 * dashes were once a dash pattern over the solid line, and a dash pattern is
 * walked from the start of the path and abandoned wherever it has got to — so the
 * run finished at the last whole period and the rest was blank. Both are now
 * spaced to the run they have: see [dotOffsets] and [dashIntervals].
 */
internal fun DrawScope.drawConnectorRun(
    style: ConnectorStyle,
    from: Offset,
    to: Offset,
    stroke: Float,
    colour: Color,
    end: RunEnd = RunEnd.Mark,
) {
    val dx = to.x - from.x
    val dy = to.y - from.y
    val run = sqrt(dx * dx + dy * dy)
    if (run <= 0f || style == ConnectorStyle.None) return
    when (style) {
        ConnectorStyle.Dotted -> {
            val offsets = dotOffsets(run, stroke, end)
            drawPoints(
                points = List(offsets.size) { Offset(from.x + dx * offsets[it] / run, from.y + dy * offsets[it] / run) },
                pointMode = PointMode.Points,
                color = colour,
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
        ConnectorStyle.Dashed -> drawLine(
            color = colour,
            start = from,
            end = to,
            strokeWidth = stroke,
            cap = StrokeCap.Round,
            pathEffect = dashIntervals(run, stroke, end)?.let { PathEffect.dashPathEffect(it) },
        )
        ConnectorStyle.Solid -> if (end == RunEnd.Mark) {
            drawLine(colour, from, to, strokeWidth = stroke, cap = StrokeCap.Round)
        } else {
            // Square at the seam, so the half drawn by the next row butts
            // against it rather than overlapping a round cap — which would show
            // wherever the two halves are different colours. Round at the node.
            drawLine(colour, from, to, strokeWidth = stroke, cap = StrokeCap.Butt)
            drawCircle(colour, radius = stroke / 2f, center = from)
        }
        ConnectorStyle.None -> Unit
    }
}

/**
 * A node: a solid dot for a place the traveller stops, a ring for one they pass
 * through. The ring is drawn at [stroke], the connector's own width, so a dot and
 * the line leaving it are the same weight.
 */
internal fun DrawScope.drawTimelineNode(
    centre: Offset,
    radius: Float,
    stroke: Float,
    colour: Color,
    filled: Boolean,
) {
    if (filled) {
        drawCircle(color = colour, radius = radius, center = centre)
    } else {
        drawCircle(color = colour, radius = radius - stroke / 2f, center = centre, style = Stroke(width = stroke))
    }
}

/**
 * Which way a [TimelineItem] lays itself out: down a [Timeline], or across a
 * [HorizontalTimeline].
 */
internal val LocalTimelineOrientation = staticCompositionLocalOf { Orientation.Vertical }

/**
 * The centre of a row's first line of label, where the node on a list row's rail
 * lines up — published by [timelineNodeLine] and read by the row that draws the
 * rail, through whatever the label is nested in.
 */
internal val TimelineNodeLine = HorizontalAlignmentLine(::min)

/**
 * Publishes [TimelineNodeLine] at the middle of this layout's first line, for text
 * whose lines are [lineHeight] px apart.
 *
 * The library's text trims the leading above its first line and below its last,
 * equally, so a label's first line is not simply its top half-line. The trim is
 * worked back out from the label's height and its first and last baselines,
 * which say how many lines it has: one line is its own middle, and a second line
 * does not move the node off the first. A label clipped by its constraints is
 * measured again for the height it wanted, since its own height no longer says. Anything without baselines — a label that
 * is not text — gets its middle, or its first line's worth of it.
 */
internal fun Modifier.timelineNodeLine(lineHeight: Float): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val height = placeable.height.toFloat()
    val first = placeable[FirstBaseline]
    val last = placeable[LastBaseline]
    val centre = if (first != AlignmentLine.Unspecified && last != AlignmentLine.Unspecified && lineHeight > 0f) {
        val lines = ((last - first) / lineHeight).roundToInt() + 1
        // A label cut short by its constraints — the last row of a list pressed
        // against the bottom of a fixed-height box — has baselines past its own
        // height; its trim has to come from the height it wanted.
        val natural = if (last > height) measurable.minIntrinsicHeight(placeable.width).toFloat() else height
        val trim = (lines * lineHeight - natural) / 2f
        lineHeight / 2f - trim
    } else {
        minOf(height, lineHeight) / 2f
    }
    layout(placeable.width, placeable.height, mapOf(TimelineNodeLine to centre.roundToInt())) {
        placeable.place(0, 0)
    }
}

/** A dash is a stroke and a half long, and the gap after it two. */
private const val DashLength = 1.5f
private const val DashGap = 2f
