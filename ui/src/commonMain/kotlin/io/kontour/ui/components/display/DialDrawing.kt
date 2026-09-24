package io.kontour.ui.components.display

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The drawing a [Gauge] and a [io.kontour.ui.components.selection.Knob] share: an
 * arc open at the bottom, a fill along it, tick marks and their labels, a needle
 * and a thumb.
 *
 * Plain functions over a [DialGeometry] rather than a composable, so each caller
 * draws in its own `drawWithCache` and reads its own animated value at draw time —
 * a dial that is not moving costs nothing.
 */
internal class DialGeometry(
    /** The middle of the dial, which is also the needle's pivot. */
    val centre: Offset,
    /** The radius of the arc's centre line. */
    val radius: Float,
    /** Degrees, clockwise from three o'clock, where the scale starts. */
    val start: Float,
    /** How many degrees the scale covers. */
    val sweep: Float,
) {
    /** The angle a [fraction] of the way along the scale sits at. */
    fun angleAt(fraction: Float): Float = start + sweep * fraction

    /** The point [fraction] of the way along the scale, at [at] from the centre. */
    fun pointAt(fraction: Float, at: Float = radius): Offset {
        val radians = angleAt(fraction) * PI.toFloat() / 180f
        return Offset(centre.x + cos(radians) * at, centre.y + sin(radians) * at)
    }

    internal companion object {
        /**
         * Where a scale of [sweep] degrees starts so that its gap is centred at the
         * bottom: 135° for the usual 270, which is the bottom-left.
         */
        fun startFor(sweep: Float): Float = 90f + (360f - sweep) / 2f
    }
}

/**
 * The track the whole length of the scale, and the fill between [from] and [to]
 * (fractions of the scale, in either order).
 *
 * **Several [indicator] colours are a gradient along the scale**, not along the
 * fill: a colour means a value, so the part of the arc at 80% is the same colour
 * whether the fill ends there or carries on past it. The gradient is a sweep
 * turned to start where the scale does — offset by the round cap's own angle, so
 * the cap at the start of the scale is the first colour rather than the last one
 * wrapping round to meet it.
 */
internal fun DrawScope.dialArcs(
    geometry: DialGeometry,
    thickness: Float,
    cap: StrokeCap,
    track: Color,
    indicator: List<Color>,
    from: Float,
    to: Float,
) {
    val topLeft = Offset(geometry.centre.x - geometry.radius, geometry.centre.y - geometry.radius)
    val arcSize = Size(geometry.radius * 2f, geometry.radius * 2f)
    drawArc(
        color = track,
        startAngle = geometry.start,
        sweepAngle = geometry.sweep,
        useCenter = false,
        topLeft = topLeft,
        size = arcSize,
        style = Stroke(width = thickness, cap = cap),
    )
    val low = minOf(from, to).coerceIn(0f, 1f)
    val high = maxOf(from, to).coerceIn(0f, 1f)
    if (high - low <= 0f || indicator.isEmpty()) return

    if (indicator.size == 1) {
        drawArc(
            color = indicator.first(),
            startAngle = geometry.angleAt(low),
            sweepAngle = geometry.sweep * (high - low),
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = thickness, cap = cap),
        )
        return
    }

    val capDegrees = if (cap == StrokeCap.Butt) 0f else thickness / 2f / geometry.radius * 180f / PI.toFloat()
    val lead = (capDegrees / 360f).coerceAtMost(0.5f)
    val span = (geometry.sweep / 360f).coerceAtMost(1f - lead * 2f)
    val stops = buildList {
        add(0f to indicator.first())
        indicator.forEachIndexed { index, colour ->
            add(lead + span * index / (indicator.size - 1) to colour)
        }
    }.toTypedArray()
    rotate(degrees = geometry.start - capDegrees, pivot = geometry.centre) {
        drawArc(
            brush = Brush.sweepGradient(colorStops = stops, center = geometry.centre),
            startAngle = capDegrees + geometry.sweep * low,
            sweepAngle = geometry.sweep * (high - low),
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = thickness, cap = cap),
        )
    }
}

/**
 * [majors] marks from one end of the scale to the other, [minorsBetween] shorter
 * ones between each pair, starting at [edge] from the centre and running [inward]
 * or outward from it.
 */
internal fun DrawScope.dialTicks(
    geometry: DialGeometry,
    majors: Int,
    minorsBetween: Int,
    edge: Float,
    majorLength: Float,
    minorLength: Float,
    width: Float,
    colour: Color,
    inward: Boolean,
) {
    if (majors < 2) return
    val divisions = (majors - 1) * (minorsBetween + 1)
    for (index in 0..divisions) {
        val major = index % (minorsBetween + 1) == 0
        val length = if (major) majorLength else minorLength
        val fraction = index.toFloat() / divisions
        val inner = if (inward) edge - length else edge
        drawLine(
            color = colour,
            start = geometry.pointAt(fraction, inner),
            end = geometry.pointAt(fraction, inner + length),
            strokeWidth = width,
            cap = StrokeCap.Round,
        )
    }
}

/** Each of [labels], one per major tick, centred [at] from the dial's centre. */
internal fun DrawScope.dialTickLabels(geometry: DialGeometry, labels: List<TextLayoutResult>, at: Float) {
    if (labels.size < 2) return
    labels.forEachIndexed { index, label ->
        val point = geometry.pointAt(index.toFloat() / (labels.size - 1), at)
        drawText(
            textLayoutResult = label,
            topLeft = Offset(point.x - label.size.width / 2f, point.y - label.size.height / 2f),
        )
    }
}

/**
 * A tapered needle from the centre to [length], pointing [fraction] of the way
 * along the scale, on a hub.
 *
 * [path] is the caller's, built once and reused: the needle is the same shape at
 * every value and only turns.
 */
internal fun DrawScope.dialNeedle(
    geometry: DialGeometry,
    path: Path,
    fraction: Float,
    length: Float,
    width: Float,
    colour: Color,
) {
    path.rewind()
    val half = width / 2f
    path.moveTo(0f, -half)
    path.lineTo(length, -half * NeedleTipShare)
    path.quadraticTo(length + half * NeedleTipShare, 0f, length, half * NeedleTipShare)
    path.lineTo(0f, half)
    path.close()
    rotate(degrees = geometry.angleAt(fraction), pivot = geometry.centre) {
        translate(left = geometry.centre.x, top = geometry.centre.y) {
            drawPath(path, colour)
        }
    }
    drawCircle(colour, radius = half * NeedleHubShare, center = geometry.centre)
}

/** A disc on the arc at [fraction], ringed, for a gauge read as "you are here". */
internal fun DrawScope.dialThumb(
    geometry: DialGeometry,
    fraction: Float,
    radius: Float,
    ring: Float,
    fill: Color,
    ringColour: Color,
) {
    val at = geometry.pointAt(fraction)
    drawCircle(ringColour, radius = radius, center = at)
    drawCircle(fill, radius = radius - ring, center = at)
}

/** How much of its width a needle keeps at its tip. */
private const val NeedleTipShare: Float = 0.3f

/** How far past the needle's width its hub reaches. */
internal const val NeedleHubShare: Float = 1.6f
