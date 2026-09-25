package io.kontour.ui.components.display

import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.FloatState
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.isSpecified
import io.kontour.ui.components.list.ListItemImpl
import io.kontour.ui.components.list.ListItemScope
import io.kontour.ui.components.list.listItemSlots
import io.kontour.ui.theme.Theme
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/*
 * The one rail every timeline draws.
 *
 * `Timeline`, `HorizontalTimeline`, `TimelineList` and `BranchTimeline` each turn
 * what they were given into the same description of a row's rail — a node, and
 * the legs running into it, out of it and past it — and [drawRail] draws that,
 * whichever of them it came from. So a leg's dashes, the colour it turns as a
 * journey passes along it, the band that travels up the leg ahead and the pulse
 * on the stop it has reached are each decided here, once.
 */

/** Where one end of a leg is: at the row's node, or at the row's start or end edge along the rail. */
internal enum class LegAt { Node, Start, End }

/** One end of a leg: which lane it is in, where along the row, and how its run finishes there. */
@Immutable
internal class LegEnd(val lane: Int, val at: LegAt, val run: RunEnd)

/**
 * The part of a whole connector a leg is, for progress along it.
 *
 * A connector runs from one node to the next, 0 to 1. A row draws a piece of it:
 * [from] is where the piece's drawn-from end sits on that scale and [to] its
 * other end — a `TimelineList` row draws 0 to ½ below its node and 1 to ½ above
 * it. The connector is travelled up to [passed]; while [band] is set, the band
 * runs through the part not yet travelled. Both are drawn in [colour].
 */
@Immutable
internal class LegTravel(
    val from: Float,
    val to: Float,
    val passed: Float,
    val band: Boolean,
    val colour: Color,
)

/** A leg of a row's rail: drawn from [start] to [end], in [style], [colour] and [width]. */
@Immutable
internal class RailLeg(
    val start: LegEnd,
    val end: LegEnd,
    val style: ConnectorStyle,
    val colour: Color,
    val width: Dp,
    val travel: LegTravel? = null,
)

/**
 * A row's node. [here] is the stop a journey is at, which gets the halo; a
 * [loading] node is not drawn at all, because its composable puts a spinner there.
 */
@Immutable
internal class RailNode(
    val lane: Int,
    val colour: Color,
    val filled: Boolean,
    val loading: Boolean,
    val ringWidth: Dp,
    val here: Boolean = false,
)

/** Everything one row draws of the rail, legs in the order they are drawn. */
@Immutable
internal class RailRow(val node: RailNode?, val legs: List<RailLeg>) {
    /** Whether anything in the row moves: a pulse on the stop reached, or a band on the leg ahead. */
    val animates: Boolean
        get() = (node != null && node.here && !node.loading) || legs.any { it.travel?.band == true }
}

/** Where a stop stands against a journey's progress, counted in stops. */
internal class StopProgress(
    /** The journey has got this far. */
    val reached: Boolean,
    /** The journey is at this stop, not between two. */
    val here: Boolean,
    /** How much of the leg after this stop has been travelled, 0 to 1. */
    val legPassed: Float,
    /** The journey is on the leg after this stop, part of the way along. */
    val legBand: Boolean,
)

/** Where the stop at [index] stands against [progress]; null for a timeline that is not being travelled. */
internal fun stopProgress(progress: Float?, index: Int): StopProgress? {
    if (progress == null) return null
    return StopProgress(
        reached = index <= progress + ProgressSlack,
        here = abs(progress - index) <= ProgressSlack,
        legPassed = (progress - index).coerceIn(0f, 1f),
        legBand = progress > index + ProgressSlack && progress < index + 1 - ProgressSlack,
    )
}

/**
 * Where the band is along a connector travelled up to [passed], at [phase] of
 * its loop: a stretch [BandFraction] of the part not travelled, entering at the
 * traveller and leaving at the next stop — the `working` band of a
 * [StepProgress] segment, laid along the leg ahead. Null while it is out of sight.
 */
internal fun bandRange(passed: Float, phase: Float): ClosedFloatingPointRange<Float>? {
    val window = 1f - passed
    if (window <= 0f) return null
    val length = window * BandFraction
    val start = passed - length + (window + length) * phase
    val from = max(start, passed)
    val to = min(start + length, 1f)
    return if (to > from) from..to else null
}

/**
 * The loop the rail's motion runs on: 0 to 1 every [BandTravel], or NaN while
 * nothing moves.
 *
 * Read from the frame clock rather than started per row, so two rows drawing
 * halves of one leg — which may be two items of a `LazyColumn` composed at
 * different times — put the band in the same place on the same frame. Only runs
 * while [running], and never under reduced motion, so a settled timeline asks
 * for no frames.
 */
@Composable
internal fun rememberRailPhase(running: Boolean): FloatState {
    val phase = remember { mutableFloatStateOf(Float.NaN) }
    val on = running && !Theme.motion.reduceMotion
    LaunchedEffect(on) {
        if (!on) {
            phase.floatValue = Float.NaN
        } else {
            while (true) {
                withInfiniteAnimationFrameMillis { time ->
                    phase.floatValue = (time % BandTravel).toFloat() / BandTravel
                }
            }
        }
    }
    return phase
}

/**
 * Draws [row]'s rail.
 *
 * Positions are along the rail ([axis]: down the page, or across it from the
 * start edge) and across it: lane `j` runs at [firstLane] `+ j ×` [laneWidth]
 * from the start edge, and the node sits [nodeAlong] along. Right to left is
 * handled here, for both axes. [phase] is [rememberRailPhase]'s; NaN draws the
 * rail still.
 */
internal fun DrawScope.drawRail(
    row: RailRow,
    axis: Orientation,
    firstLane: Float,
    laneWidth: Float,
    nodeAlong: Float,
    nodeRadius: Float,
    phase: Float = Float.NaN,
) {
    val space = RailSpace(axis, layoutDirection == LayoutDirection.Rtl, size)
    val clear = nodeRadius + TimelineNodeGap.toPx()
    fun cross(lane: Int) = firstLane + lane * laneWidth
    fun along(end: LegEnd, towards: LegEnd): Float = when (end.at) {
        LegAt.Start -> 0f
        LegAt.End -> space.extent
        // Clear of the node, on the side the leg leaves it.
        LegAt.Node -> if (towards.at == LegAt.Start) nodeAlong - clear else nodeAlong + clear
    }

    for (leg in row.legs) {
        if (leg.style == ConnectorStyle.None) continue
        val a0 = along(leg.start, leg.end)
        val a1 = along(leg.end, leg.start)
        // A leg runs toward its row's edge; one that would have to run backwards
        // — the node is closer to the edge than the gap round it — is not drawn.
        val forward = leg.end.at == LegAt.End || leg.start.at == LegAt.Start
        if ((if (forward) a1 - a0 else a0 - a1) <= 0f) continue
        val stroke = leg.width.toPx()
        val c0 = cross(leg.start.lane)
        val c1 = cross(leg.end.lane)
        fun draw(colour: Color) {
            if (leg.start.lane == leg.end.lane) {
                drawConnectorRun(leg.style, space.point(a0, c0), space.point(a1, c1), stroke, colour, leg.end.run, leg.start.run)
            } else {
                drawConnectorCurve(leg.style, space.curve(a0, c0, a1, c1), stroke, colour, leg.start.run, leg.end.run)
            }
        }
        draw(leg.colour)

        val travel = leg.travel ?: continue
        // The same run again, clipped to the stretch of it in [lo, hi] of the
        // connector — so its dots and dashes land exactly on the ones under it.
        // A stretch reaching an end of this piece runs a stroke past it, over
        // the cap there.
        fun overlay(lo: Float, hi: Float) {
            if (travel.to == travel.from) return
            val a = max(lo, min(travel.from, travel.to))
            val b = min(hi, max(travel.from, travel.to))
            if (b <= a) return
            val sa = (a - travel.from) / (travel.to - travel.from)
            val sb = (b - travel.from) / (travel.to - travel.from)
            val s0 = min(sa, sb)
            val s1 = max(sa, sb)
            val out = if (a1 >= a0) stroke else -stroke
            val p0 = a0 + (a1 - a0) * s0 - if (s0 <= 0f) out else 0f
            val p1 = a0 + (a1 - a0) * s1 + if (s1 >= 1f) out else 0f
            space.clipAlong(this, min(p0, p1), max(p0, p1)) { draw(travel.colour) }
        }
        if (travel.passed > 0f) overlay(0f, travel.passed)
        if (travel.band && !phase.isNaN()) bandRange(travel.passed, phase)?.let { overlay(it.start, it.endInclusive) }
    }

    val node = row.node ?: return
    if (node.loading) return
    val centre = space.point(nodeAlong, cross(node.lane))
    if (node.here) {
        drawCircle(node.colour.copy(alpha = HaloAlpha), radius = clear, center = centre)
        if (!phase.isNaN()) {
            // A ping off the halo: out to [PulseReach] of it, easing off as it
            // goes, and fading to nothing on the way — then again.
            val eased = 1f - (1f - phase).let { it * it * it }
            drawCircle(
                node.colour.copy(alpha = HaloAlpha * (1f - phase)),
                radius = clear * (1f + (PulseReach - 1f) * eased),
                center = centre,
            )
        }
    }
    drawTimelineNode(centre, nodeRadius, node.ringWidth.toPx(), node.colour, node.filled)
}

/**
 * Positions along a rail and across it, turned into the draw scope's own: down
 * or across, and mirrored for right to left.
 */
private class RailSpace(val axis: Orientation, val mirror: Boolean, val size: Size) {
    val extent: Float get() = if (axis == Orientation.Vertical) size.height else size.width

    fun point(along: Float, cross: Float): Offset = if (axis == Orientation.Vertical) {
        Offset(if (mirror) size.width - cross else cross, along)
    } else {
        Offset(if (mirror) size.width - along else along, cross)
    }

    /**
     * From one lane to another, leaving and arriving straight along the rail so
     * it meets the rows either side where they expect it.
     */
    fun curve(a0: Float, c0: Float, a1: Float, c1: Float): Path {
        val middle = (a0 + a1) / 2f
        val start = point(a0, c0)
        val first = point(middle, c0)
        val second = point(middle, c1)
        val end = point(a1, c1)
        return Path().apply {
            moveTo(start.x, start.y)
            cubicTo(first.x, first.y, second.x, second.y, end.x, end.y)
        }
    }

    /** Draws [block] clipped to the stretch of the rail from [lo] to [hi] along it, the full width across. */
    inline fun clipAlong(scope: DrawScope, lo: Float, hi: Float, block: DrawScope.() -> Unit) {
        if (axis == Orientation.Vertical) {
            scope.clipRect(top = lo, bottom = hi, block = block)
        } else if (mirror) {
            scope.clipRect(left = size.width - hi, right = size.width - lo, block = block)
        } else {
            scope.clipRect(left = lo, right = hi, block = block)
        }
    }
}

/** One stop as declared, in a `TimelineList` or a `BranchTimeline`. */
internal class TimelineStop(
    val nodeColour: Color,
    val filled: Boolean,
    val loading: Boolean,
    val connector: ConnectorStyle,
    val connectorColour: Color,
    val connectorWidth: Dp,
    val enabled: Boolean,
    val selected: Boolean,
    val role: Role,
    val onClick: (() -> Unit)?,
    val content: ListItemScope.() -> Unit,
)

/**
 * A list row with the rail beside it: the row a `TimelineList` stop and a
 * `BranchTimeline` commit are both drawn as.
 *
 * The rail runs down [lanes] lanes at the row's start — one for a timeline, one
 * per branch for a history — the first centred in a [gutterWidth] column like a
 * [TimelineItem]'s, the rest [laneWidth] apart, and the row's content starts
 * half a gutter past the last. The node sits on the label's first line
 * ([timelineNodeLine]), and a loading stop's spinner on the same point.
 */
@Composable
internal fun RailListRow(
    stop: TimelineStop,
    rail: RailRow,
    listEnabled: Boolean,
    lanes: Int,
    laneWidth: Dp,
    gutterWidth: Dp,
    nodeSize: Dp,
    shape: Shape,
    containerColour: Color,
    disabledContainerColour: Color,
    edged: Boolean,
    gapBelow: Dp,
) {
    val start = Theme.spacing.xs
    val label = Theme.typography.bodyMedium
    val lineHeight = with(LocalDensity.current) {
        if (label.lineHeight.isSpecified) label.lineHeight.toPx() else label.fontSize.toPx() * FallbackLeading
    }
    // Written by the measure pass below, read by the draw: where the label's
    // first line landed, which is where the node goes.
    val nodeY = remember { mutableFloatStateOf(0f) }
    val phase = rememberRailPhase(rail.animates)
    val node = rail.node

    Layout(
        content = {
            ListItemImpl(
                modifier = Modifier,
                enabled = listEnabled && stop.enabled,
                onClick = stop.onClick,
                selected = stop.selected,
                role = stop.role,
                shape = shape,
                containerColour = containerColour,
                selectedContainerColour = Theme.colours.accent.container,
                contentColour = Theme.colours.content,
                minHeight = Dp.Unspecified,
                interactionSource = null,
                slots = listItemSlots(stop.content),
                startPadding = start + gutterWidth + laneWidth * (lanes - 1).coerceAtLeast(0),
                labelModifier = Modifier.timelineNodeLine(lineHeight),
                edged = edged,
                disabledContainerColour = disabledContainerColour,
            )
            if (node != null && node.loading) Spinner(size = nodeSize, colour = node.colour, strokeWidth = node.ringWidth)
        },
        modifier = Modifier
            .fillMaxWidth()
            .drawWithContent {
                drawContent()
                drawRail(
                    rail,
                    Orientation.Vertical,
                    firstLane = (start + gutterWidth / 2).toPx(),
                    laneWidth = laneWidth.toPx(),
                    nodeAlong = nodeY.floatValue,
                    nodeRadius = nodeSize.toPx() / 2f,
                    phase = phase.floatValue,
                )
            },
    ) { measurables, constraints ->
        val card = measurables[0].measure(constraints.copy(minHeight = 0))
        val line = card[TimelineNodeLine]
        val y = if (line == AlignmentLine.Unspecified) card.height / 2 else line
        nodeY.floatValue = y.toFloat()
        val spinner = measurables.getOrNull(1)?.measure(Constraints())
        val railFromStart = (start + gutterWidth / 2 + laneWidth * (node?.lane ?: 0)).roundToPx()
        layout(card.width, card.height + gapBelow.roundToPx()) {
            card.placeRelative(0, 0)
            spinner?.placeRelative(railFromStart - spinner.width / 2, y - spinner.height / 2)
        }
    }
}

/** A node colour: the stop's own, else the timeline's, else — while it is travelled — reached or not. */
internal fun nodeColourFor(own: Color, progress: StopProgress?, node: Color, progressColour: Color, rail: Color): Color = when {
    own.isSpecified -> own
    progress == null -> node
    progress.reached -> progressColour
    else -> rail
}

/** How close to a stop `progress` has to be to count as at it. */
private const val ProgressSlack: Float = 0.001f

/** The ring round the stop the journey is at. */
private const val HaloAlpha: Float = 0.3f

/** How far the pulse off that ring spreads, against the ring's own radius. */
private const val PulseReach: Float = 1.75f

/** A line's height against its type size, for a style that does not set one. */
private const val FallbackLeading: Float = 1.4f
