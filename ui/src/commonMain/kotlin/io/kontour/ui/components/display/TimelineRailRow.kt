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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import io.kontour.ui.a11y.contentColourFor
import io.kontour.ui.a11y.highContrast
import io.kontour.ui.components.list.ListItemImpl
import io.kontour.ui.components.list.ListItemScope
import io.kontour.ui.components.list.listItemSlots
import io.kontour.ui.theme.Theme
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

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
 * it. The connector is travelled up to [passed]; while [band] is set and the
 * rail is moving, the leg is drawn faint instead and the band sweeps all of it.
 * Both are drawn in [colour].
 *
 * With [arrows], the leg [band] marks shows chevrons instead of a band: one at
 * a time travelling the whole leg, or with [stream] a moving run of them the
 * length of it, each growing in at the stop behind and fading out at the stop
 * ahead, over the leg drawn faint, as the band sweeps it. Still, the part
 * travelled is drawn in full and the chevrons stand on the rest, which says
 * the way the journey is going without moving.
 */
@Immutable
internal class LegTravel(
    val from: Float,
    val to: Float,
    val passed: Float,
    val band: Boolean,
    val colour: Color,
    val arrows: Boolean = false,
    val stream: Boolean = false,
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
 * An [icon] makes the node [TimelineIconNodeSize] and is drawn inside it, from
 * the painter [drawRail] is handed for it.
 */
@Immutable
internal class RailNode(
    val lane: Int,
    val colour: Color,
    val filled: Boolean,
    val loading: Boolean,
    val ringWidth: Dp,
    val here: Boolean = false,
    /** Whether a journey has got this far. Only a travelled timeline has stops that are not. */
    val reached: Boolean = true,
    val icon: ImageVector? = null,
    /** How many nodes are bunched along the rail for a node group; 1 for a stop. */
    val stack: Int = 1,
) {
    /**
     * Whether the node is drawn solid. Not yet reached is hollow at the enhanced
     * tier, so reached and not reached differ in shape as well as colour.
     */
    fun drawnFilled(highContrast: Boolean): Boolean = filled && (reached || !highContrast)
}

/**
 * A node's diameter: [size], or [TimelineIconNodeSize] if that is bigger and the
 * node carries an icon — a 12dp dot has no room inside it for one.
 */
internal fun nodeSizeFor(size: Dp, icon: ImageVector?): Dp =
    if (icon != null) maxOf(size, TimelineIconNodeSize) else size


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
 * Where the band is along a connector at [phase] of its loop: a stretch
 * [BandFraction] of it long, entering at the node behind and leaving at the
 * node ahead — the `working` band of a [StepProgress] segment, laid along the
 * leg. The whole leg, however far along it the journey is: the fraction is the
 * still picture's, under reduced motion. Null while it is out of sight.
 */
internal fun bandRange(phase: Float): ClosedFloatingPointRange<Float>? {
    val start = -BandFraction + (1f + BandFraction) * phase
    val from = max(start, 0f)
    val to = min(start + BandFraction, 1f)
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
 * rail still. [icon] is the node's [RailNode.icon], painted inside it.
 */
internal fun DrawScope.drawRail(
    row: RailRow,
    axis: Orientation,
    firstLane: Float,
    laneWidth: Float,
    nodeAlong: Float,
    nodeRadius: Float,
    phase: Float = Float.NaN,
    highContrast: Boolean = false,
    icon: Painter? = null,
) {
    val space = RailSpace(axis, layoutDirection == LayoutDirection.Rtl, size)
    val stack = row.node?.stack ?: 1
    // A node group's stack is longer along the rail than a node, and its legs
    // leave it from its ends; across the rail it is one node wide.
    val half = stackHalfLength(nodeRadius, stack)
    val clear = half + TimelineNodeGap.toPx()
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
        // The leg the journey is on, while its band runs, is drawn faint and
        // the band sweeps it at full strength — so the band shows whatever
        // colour the leg is, its own or the rail's. Its arrows, moving or
        // still, are drawn over it faint the same way.
        val arrows = leg.travel?.let { it.band && it.arrows } == true
        val sweeping = !arrows && leg.travel?.band == true && !phase.isNaN()
        draw(if (sweeping || arrows) leg.colour.copy(alpha = leg.colour.alpha * FaintAlpha) else leg.colour)

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
        if (arrows) {
            // Moving, the arrows travel the whole leg, as the band sweeps it:
            // drawn up to where the journey is, they ran along half of it.
            if (phase.isNaN() && travel.passed > 0f) overlay(0f, travel.passed)
            val curve = if (leg.start.lane != leg.end.lane) space.curve(a0, c0, a1, c1) else null
            drawLegArrows(travel, space.point(a0, c0), space.point(a1, c1), curve, stroke, phase)
        } else if (sweeping) {
            bandRange(phase)?.let { overlay(it.start, it.endInclusive) }
        } else if (travel.passed > 0f) {
            overlay(0f, travel.passed)
        } else if (travel.band) {
            // Still — reduced motion — and nothing known of how far along: a
            // stub the band's length at the leg's start, as a step bar shows one,
            // so the leg still reads as the one being travelled rather than as
            // one not yet reached.
            overlay(0f, BandFraction)
        }
    }

    val node = row.node ?: return
    if (node.loading) return
    val centre = space.point(nodeAlong, cross(node.lane))
    val ring = node.ringWidth.toPx()
    val gap = TimelineNodeGap.toPx()
    if (node.here && stack > 1) {
        // Round the whole of a node group, which is one stop to the journey: a
        // capsule, one node and its gap wide, as long as the stack and its gap.
        val haloStyle = if (highContrast) Stroke(ring) else Fill
        val haloAlpha = if (highContrast) 1f else HaloAlpha
        val inset = if (highContrast) ring / 2f else 0f
        fun capsule(grow: Float, alpha: Float) {
            val across = nodeRadius + gap - inset + grow
            val along = half + gap - inset + grow
            val a = space.point(nodeAlong - along, cross(node.lane) - across)
            val b = space.point(nodeAlong + along, cross(node.lane) + across)
            drawRoundRect(
                node.colour.copy(alpha = alpha),
                topLeft = Offset(min(a.x, b.x), min(a.y, b.y)),
                size = Size(abs(b.x - a.x), abs(b.y - a.y)),
                cornerRadius = CornerRadius(across),
                style = haloStyle,
            )
        }
        capsule(0f, haloAlpha)
        if (!phase.isNaN()) {
            val eased = 1f - (1f - phase).let { it * it * it }
            capsule((nodeRadius + gap - inset) * (PulseReach - 1f) * eased, haloAlpha * (1f - phase))
        }
    } else if (node.here) {
        // At the enhanced tier the halo is a ring rather than a wash: 30% of the
        // node's colour is a shape the tier's reader may not see at all, and the
        // stop the journey is *at* is the one thing on a timeline they came for.
        val haloStyle = if (highContrast) Stroke(ring) else Fill
        val haloAlpha = if (highContrast) 1f else HaloAlpha
        val haloRadius = if (highContrast) clear - ring / 2f else clear
        drawCircle(node.colour.copy(alpha = haloAlpha), radius = haloRadius, center = centre, style = haloStyle)
        if (!phase.isNaN()) {
            // A ping off the halo: out to [PulseReach] of it, easing off as it
            // goes, and fading to nothing on the way — then again.
            val eased = 1f - (1f - phase).let { it * it * it }
            drawCircle(
                node.colour.copy(alpha = haloAlpha * (1f - phase)),
                radius = haloRadius * (1f + (PulseReach - 1f) * eased),
                center = centre,
                style = haloStyle,
            )
        }
    }
    val solid = node.drawnFilled(highContrast)
    if (stack > 1) {
        // Back to front, so the last along the rail is whole and each before it
        // is cut back where the next one covers it, a stroke's gap round it —
        // drawn, rather than painted over in the ground's colour, so a stack
        // reads the same on a page, on a grouped row or on anything else.
        val centres = stackCentres(nodeAlong, nodeRadius, stack)
        centres.forEachIndexed { i, along ->
            val at = space.point(along, cross(node.lane))
            val next = centres.getOrNull(i + 1)
            if (next == null) {
                drawTimelineNode(at, nodeRadius, ring, node.colour, solid)
            } else {
                val cover = space.point(next, cross(node.lane))
                val cut = Path().apply { addOval(Rect(cover, nodeRadius + ring)) }
                clipPath(cut, ClipOp.Difference) { drawTimelineNode(at, nodeRadius, ring, node.colour, solid) }
            }
        }
        return
    }
    drawTimelineNode(centre, nodeRadius, ring, node.colour, solid)
    if (icon != null) {
        // On a solid node, whichever of light or dark reads on it — a route
        // colour straight from a feed included; inside a ring, the ring's own.
        val side = TimelineNodeIconSize.toPx()
        translate(centre.x - side / 2f, centre.y - side / 2f) {
            with(icon) {
                draw(Size(side, side), colorFilter = ColorFilter.tint(if (solid) contentColourFor(node.colour) else node.colour))
            }
        }
    }
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
    val icon: ImageVector?,
    val connector: ConnectorStyle,
    val connectorColour: Color,
    val connectorWidth: Dp,
    val enabled: Boolean,
    val selected: Boolean,
    val role: Role,
    val onClick: (() -> Unit)?,
    val content: ListItemScope.() -> Unit,
    /** How many nodes the row's node bunches, for a node group; 1 for a stop. */
    val stack: Int = 1,
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
    val enhanced = highContrast()
    val lineHeight = with(LocalDensity.current) {
        if (label.lineHeight.isSpecified) label.lineHeight.toPx() else label.fontSize.toPx() * FallbackLeading
    }
    // Written by the measure pass below, read by the draw: where the label's
    // first line landed, which is where the node goes.
    val nodeY = remember { mutableFloatStateOf(0f) }
    val phase = rememberRailPhase(rail.animates)
    val node = rail.node
    val size = nodeSizeFor(nodeSize, node?.icon)
    val icon = node?.icon?.let { rememberVectorPainter(it) }

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
            if (node != null && node.loading) Spinner(size = size, colour = node.colour, strokeWidth = node.ringWidth)
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
                    nodeRadius = size.toPx() / 2f,
                    phase = phase.floatValue,
                    highContrast = enhanced,
                    icon = icon,
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

/** Half a node group's length along the rail: a node, and the steps to the rest of the stack. */
internal fun stackHalfLength(radius: Float, stack: Int): Float = radius * (1f + (stack - 1) * GroupStep)

/** Where each node of a stack centred [along] the rail sits, back to front. */
internal fun stackCentres(along: Float, radius: Float, stack: Int): FloatArray {
    val first = along - stackHalfLength(radius, stack) + radius
    return FloatArray(stack) { first + it * radius * 2f * GroupStep }
}

/**
 * The chevrons along the piece of a leg from [p0] to [p1] — along [curve] when
 * it bends to another lane — that [travel] says is being travelled.
 *
 * **Moving**, they travel the whole leg towards the stop ahead: one at a time,
 * once a loop of [phase], or with [LegTravel.stream] a run of them spaced to the
 * piece's own length, a whole number of them, so the piece in the next row
 * picks the run up at the seam where this one leaves it, whatever the two rows'
 * heights. Each grows and fades in over its first [ArrowFade] of the leg and out
 * over its last, so none appears or vanishes at a stop. Asked for for 0.5.1 as
 * *"they should travel the entire leg, not just half of it"*, *"fade/scale in
 * and out … so it doesn't appear, then disappear"* and *"multiple, continuously
 * moving down. like an escalator"*: they had run only along the part still to
 * cover, appearing where the journey was and vanishing at the stop.
 *
 * **Still** — [phase] NaN, under reduced motion — they stand on the part still
 * to cover: one halfway along it, or the run of them.
 *
 * A chevron belongs to the piece its middle is in, and is drawn whole there.
 */
private fun DrawScope.drawLegArrows(
    travel: LegTravel,
    p0: Offset,
    p1: Offset,
    curve: Path?,
    stroke: Float,
    phase: Float,
) {
    if (travel.to == travel.from) return
    val lo = min(travel.from, travel.to)
    val hi = max(travel.from, travel.to)
    val measure = curve?.let { PathMeasure().apply { setPath(it, forceClosed = false) } }
    val straight = p1 - p0
    val length = measure?.length ?: sqrt(straight.x * straight.x + straight.y * straight.y)
    if (length <= 0f) return
    val moving = !phase.isNaN()
    // The whole leg, in this piece's pixels, for the fade at its ends.
    val legLength = length / (hi - lo)
    val fade = min(ArrowFade.toPx(), legLength * MaxFadeShare)
    // The journey runs towards [hi], so towards [p1] when that is where [to] is.
    val towards = if (travel.to > travel.from) 1f else -1f
    val fullHalf = max(ArrowHalfWidth.toPx(), stroke * 2f)
    val style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)

    fun chevron(at: Float) {
        // How far it is into the leg from the nearer stop, eased in and out.
        val fromEnd = min(at, 1f - at) * legLength
        val weight = if (fade <= 0f) 1f else (fromEnd / fade).coerceIn(0f, 1f).let { it * it * (3f - 2f * it) }
        if (weight <= 0f) return
        val half = fullHalf * (ArrowSmallest + (1f - ArrowSmallest) * weight)
        val depth = half * ArrowDepth
        val along = (at - travel.from) / (travel.to - travel.from) * length
        val centre: Offset
        val direction: Offset
        if (measure != null) {
            centre = measure.getPosition(along)
            direction = measure.getTangent(along) * towards
        } else {
            centre = p0 + straight * (along / length)
            direction = straight * (towards / length)
        }
        val across = Offset(-direction.y, direction.x) * half
        val tip = centre + direction * (depth / 2f)
        val back = centre - direction * (depth / 2f)
        val path = Path().apply {
            moveTo(back.x + across.x, back.y + across.y)
            lineTo(tip.x, tip.y)
            lineTo(back.x - across.x, back.y - across.y)
        }
        drawPath(path, travel.colour.copy(alpha = travel.colour.alpha * weight), style = style)
    }

    if (!travel.stream) {
        // One: across the whole leg once a loop, or halfway along what is left.
        val at = if (moving) phase else (max(travel.passed, 0f) + 1f) / 2f
        if (at in lo..hi && (at < hi || hi >= 1f)) chevron(at)
        return
    }
    val count = max(1, (length / ArrowSpacing.toPx()).roundToInt())
    val shift = if (moving) (phase * ArrowsPerLoop) % 1f else 0f
    for (k in 0 until count) {
        val at = lo + (hi - lo) * (k + shift) / count
        if (!moving && at < travel.passed) continue
        chevron(at)
    }
}

/** How far apart a travelled leg's chevrons are, before they are spaced to fit it. */
private val ArrowSpacing: Dp = 11.dp

/** Half a chevron's width across the leg, at the least. Twice the leg's weight, if that is more. */
private val ArrowHalfWidth: Dp = 4.dp

/** A chevron's depth along the leg, against its half width. */
private const val ArrowDepth: Float = 0.8f

/** How many chevron spacings they move on in each loop of the rail's motion. */
private const val ArrowsPerLoop: Int = 2

/** How far into the leg from either stop a chevron grows and fades in, or out. */
private val ArrowFade: Dp = 16.dp

/** The most of a short leg the fade at each end takes, so a chevron is whole somewhere along it. */
private const val MaxFadeShare: Float = 0.3f

/** A chevron's size at a stop, against its own, before it has grown in. */
private const val ArrowSmallest: Float = 0.4f

/** How close to a stop `progress` has to be to count as at it. */
private const val ProgressSlack: Float = 0.001f

/** The ring round the stop the journey is at. */
private const val HaloAlpha: Float = 0.3f

/** The leg under a running band, against its own colour. */
private const val FaintAlpha: Float = 0.35f

/** How far the pulse off that ring spreads, against the ring's own radius. */
internal const val PulseReach: Float = 1.75f

/** A line's height against its type size, for a style that does not set one. */
private const val FallbackLeading: Float = 1.4f
