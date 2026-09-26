package io.kontour.ui.components.display

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.kontour.ui.theme.Theme
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * A reading on a straight scale: the [Gauge], laid flat.
 *
 * ```kotlin
 * Meter(
 *     value = charge,
 *     valueRange = 0f..100f,
 *     indicator = GaugeIndicator.Thumb,
 *     majorTicks = 5,
 *     tickLabel = { "${it.roundToInt()}%" },
 *     contentDescription = "Battery",
 * ) {
 *     Text("Battery ${charge.roundToInt()}%")
 * }
 * ```
 *
 * **Display only**, like the gauge: it says what something *is*, and a screen
 * reader hears it as a progress reading. For a value the user sets, use a
 * [io.kontour.ui.components.selection.Slider].
 *
 * Everything the gauge does, it does, with the same parameters meaning the same
 * things wherever they can: a fill from [origin] to the reading, coloured along the
 * scale by a [ScaleColours] — one colour, a gradient or bands — a needle, a thumb or
 * both, ticks with labels on either side, a translucent capsule behind the content,
 * and a reading that travels. Two things are its own, because a bar has them and a
 * dial does not: which way it runs, [orientation], and whether its content stays put
 * or rides along with the reading, [contentPlacement].
 *
 * ### It mirrors
 *
 * A horizontal meter fills from the start of the line, so right to left it fills
 * from the right — a bar is read in the direction the text runs, which is the one
 * thing a dial is not. A vertical meter fills upward either way, and its content
 * and ticks swap sides with the text.
 *
 * @param value The reading, in [valueRange]. Values outside it are drawn at the
 *   nearer end.
 * @param valueRange The scale, from its start to its end.
 * @param origin Where the fill starts from, in [valueRange]. The start of the range
 *   unless moved; a reading either side of a centre fills outward from it.
 * @param orientation Across the page, filling from the start of the line, or up it,
 *   filling from the bottom.
 * @param thickness The track's width. The thumb and the needle scale with it.
 * @param cap How the track's ends and the fill's ends are cut.
 * @param colours The fill is `indicator`, a [ScaleColours] laid along the scale;
 *   the rest are the gauge's — see [DialColours].
 * @param indicator What marks the value besides the fill: nothing, a needle — a
 *   small triangle beside the track pointing at the reading, from the side away
 *   from the ticks — a thumb on the track, or both, the needle pointing at the
 *   thumb.
 * @param needleLength How tall the needle's triangle is, in track thicknesses: how
 *   far it stands off the track.
 * @param needleMatchesFill Whether the needle takes the fill's colour at the
 *   reading — the band it is in — rather than `colours.needle`.
 * @param majorTicks How many labelled marks, counting both ends. Zero for none.
 * @param minorTicks How many shorter marks between each pair of major ones.
 * @param tickLabel The text at each major tick, from its value. Null for marks
 *   without labels.
 * @param tickPlacement Which side of the track the ticks and their labels sit on:
 *   [GaugeTickPlacement.Inside] is the content's side, between it and the track;
 *   [GaugeTickPlacement.Outside], the default, is the far side, which leaves the
 *   content next to the track.
 * @param contentPlacement Whether [content] sits in one place — above a horizontal
 *   meter, beside a vertical one — or rides along with the reading.
 * @param contentBackground Whether [content] sits on a translucent capsule of
 *   `colours.contentBackground`. Most use riding along with the reading, where it
 *   reads as a tag.
 * @param animated Whether a new [value] travels there or is simply drawn there.
 *   Reduced motion does not travel either way.
 * @param contentDescription What is being measured, for a screen reader.
 * @param stateDescription What a screen reader says for the value, from it — "62
 *   percent charged" rather than a share of the range.
 * @param content Above a horizontal meter, full width; beside a vertical one, on
 *   the end side and centred along it; or, with [MeterContentPlacement.AtValue],
 *   centred on the reading and kept inside the meter's ends.
 */
@Composable
fun Meter(
    value: Float,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    origin: Float = valueRange.start,
    orientation: MeterOrientation = MeterOrientation.Horizontal,
    thickness: Dp = MeterDefaults.Thickness,
    cap: StrokeCap = StrokeCap.Round,
    colours: DialColours = MeterDefaults.colours(),
    indicator: GaugeIndicator = GaugeIndicator.None,
    needleLength: Float = MeterDefaults.NeedleLength,
    needleMatchesFill: Boolean = false,
    majorTicks: Int = 0,
    minorTicks: Int = 0,
    tickLabel: ((Float) -> String)? = null,
    tickPlacement: GaugeTickPlacement = GaugeTickPlacement.Outside,
    contentPlacement: MeterContentPlacement = MeterContentPlacement.Fixed,
    contentBackground: Boolean = false,
    animated: Boolean = true,
    contentDescription: String? = null,
    stateDescription: ((Float) -> String)? = null,
    content: @Composable BoxScope.() -> Unit = {},
) {
    require(valueRange.start <= valueRange.endInclusive) {
        "Meter was given an inverted valueRange (${valueRange.start}..${valueRange.endInclusive})."
    }
    val motion = Theme.motion
    val density = LocalDensity.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val labelStyle = Theme.typography.labelSmall.copy(color = colours.tickLabel)
    val measurer = rememberTextMeasurer()
    // A band's gaps, and an unbanded scale, are the meter's own colour.
    val scaleDefault = Theme.colours.primary
    val defaultContentBackground = Theme.colours.surface.copy(alpha = ContentBackgroundAlpha)
    val gapPx = with(density) { Theme.spacing.xs.toPx() }
    val lengthPx = with(density) { MeterDefaults.Length.toPx() }

    // Read in draw and in placement and nowhere else, so a meter at rest does no
    // work and a moving one only redraws and re-places its content.
    val shown = remember { Animatable(value) }
    LaunchedEffect(value, animated, motion.reduceMotion) {
        if (animated && !motion.reduceMotion) {
            shown.animateTo(value, motion.springOrTween(motion.springGentle))
        } else {
            shown.snapTo(value)
        }
    }

    val horizontal = orientation == MeterOrientation.Horizontal
    val hasNeedle = indicator == GaugeIndicator.Needle || indicator == GaugeIndicator.NeedleAndThumb
    val hasThumb = indicator == GaugeIndicator.Thumb || indicator == GaugeIndicator.NeedleAndThumb
    val range = valueRange
    fun fractionOf(v: Float): Float {
        val span = range.endInclusive - range.start
        return if (span <= 0f) 0f else ((v - range.start) / span).coerceIn(0f, 1f)
    }

    val labels = if (majorTicks >= 2 && tickLabel != null) {
        List(majorTicks) { index ->
            val at = range.start + (range.endInclusive - range.start) * index / (majorTicks - 1)
            measurer.measure(tickLabel(at), labelStyle)
        }
    } else {
        emptyList()
    }

    // **The scale's cross-section**, worked out once: how far the track, thumb and
    // needle reach either side of the track's centre line, and how far the ticks
    // and their labels reach on theirs. The side away from the ticks only has to
    // hold the track and what sits on it.
    val thicknessPx = with(density) { thickness.toPx() }
    val tickGapPx = with(density) { GaugeTickGap.toPx() }
    val majorPx = if (majorTicks >= 2) with(density) { GaugeMajorTick.toPx() } else 0f
    val minorPx = with(density) { GaugeMinorTick.toPx() }
    val tickWidthPx = with(density) { GaugeTickWidth.toPx() }
    val ringPx = with(density) { GaugeThumbRing.toPx() }
    val thumbPx = if (hasThumb) thicknessPx * ThumbShare else 0f
    // The needle is a small triangle beside the track, pointing at the reading
    // from the side the ticks are not on: a caret on a ruler. It was a stripe
    // across the track, and read as part of the fill rather than as something
    // marking it.
    val needleHeightPx = if (hasNeedle) max(needleLength * thicknessPx, with(density) { NeedleMinimum.toPx() }) else 0f
    val needleHalfBasePx = needleHeightPx * NeedleBaseShare / 2f
    val needleGapPx = with(density) { NeedleGap.toPx() }
    val reach = maxOf(thicknessPx / 2f, thumbPx)
    val pointerSide = if (hasNeedle) reach + needleGapPx + needleHeightPx else reach
    val tickEdge = thicknessPx / 2f + tickGapPx
    val labelCross = labels.maxOfOrNull { if (horizontal) it.size.height else it.size.width }?.toFloat() ?: 0f
    val tickSide = if (majorTicks >= 2) {
        max(reach, tickEdge + majorPx + (if (labels.isEmpty()) 0f else tickGapPx + labelCross))
    } else {
        reach
    }
    val scaleCross = tickSide + pointerSide

    // **Along it**: room at each end for what overhangs the track's ends — a round
    // cap, a thumb or needle at the very end, and half the end labels, which are
    // centred on their ticks.
    val capOver = if (cap == StrokeCap.Butt) 0f else thicknessPx / 2f
    fun labelHalf(index: Int): Float =
        labels.getOrNull(index)?.let { (if (horizontal) it.size.width else it.size.height) / 2f } ?: 0f
    val endsOver = maxOf(capOver, thumbPx, needleHalfBasePx)
    val startInset = max(endsOver, labelHalf(0))
    val endInset = max(endsOver, labelHalf(labels.lastIndex))

    // Which way is the content, physically, across the track: up from a horizontal
    // meter, and to the end side of a vertical one. Inside ticks face it.
    val contentSide = if (horizontal) -1f else if (rtl) -1f else 1f
    val tickSign = if (tickPlacement == GaugeTickPlacement.Inside) contentSide else -contentSide
    val atValue = contentPlacement == MeterContentPlacement.AtValue

    Layout(
        modifier = modifier.semantics(mergeDescendants = true) {
            if (contentDescription != null) this.contentDescription = contentDescription
            progressBarRangeInfo = ProgressBarRangeInfo(current = value.coerceIn(range), range = range)
            if (stateDescription != null) this.stateDescription = stateDescription(value)
        },
        content = {
            Spacer(
                Modifier.drawWithCache {
                    val w = size.width
                    val h = size.height
                    val length = if (horizontal) w else h
                    val span = (length - startInset - endInset).coerceAtLeast(0f)
                    val centre = if (tickSign > 0f) pointerSide else tickSide
                    fun point(fraction: Float, across: Float = 0f, alongBy: Float = 0f): Offset {
                        val along = startInset + span * fraction + alongBy
                        return if (horizontal) {
                            Offset(if (rtl) w - along else along, centre + across)
                        } else {
                            Offset(centre + across, h - along)
                        }
                    }
                    val stops = colours.indicator.stops(range, scaleDefault)
                    val first = stops.first().second
                    // Laid along the whole scale, not the fill, as the gauge's is: a
                    // colour means a value. Past the ends the gradient clamps, so a
                    // round cap is the end's own colour.
                    val brush = if (stops.all { it.second == first }) {
                        null
                    } else {
                        Brush.linearGradient(*stops.toTypedArray(), start = point(0f), end = point(1f))
                    }
                    val divisions = if (majorTicks >= 2) (majorTicks - 1) * (minorTicks.coerceAtLeast(0) + 1) else 0
                    val needle = Path()
                    val needleRoundPx = NeedleRounding.toPx()
                    onDrawBehind {
                        val at = fractionOf(shown.value)
                        drawLine(colours.track, point(0f), point(1f), thicknessPx, cap)
                        val from = fractionOf(origin)
                        val low = min(from, at)
                        val high = max(from, at)
                        if (high > low) {
                            if (brush == null) {
                                drawLine(first, point(low), point(high), thicknessPx, cap)
                            } else {
                                drawLine(brush, point(low), point(high), thicknessPx, cap)
                            }
                        }
                        for (index in 0..divisions) {
                            if (divisions == 0) break
                            val major = index % (minorTicks.coerceAtLeast(0) + 1) == 0
                            val tick = if (major) majorPx else minorPx
                            val f = index.toFloat() / divisions
                            drawLine(
                                colours.tick,
                                point(f, tickSign * tickEdge),
                                point(f, tickSign * (tickEdge + tick)),
                                tickWidthPx,
                                StrokeCap.Round,
                            )
                        }
                        labels.forEachIndexed { index, label ->
                            val anchor = point(index.toFloat() / (labels.size - 1), tickSign * (tickEdge + majorPx + tickGapPx))
                            val lw = label.size.width.toFloat()
                            val lh = label.size.height.toFloat()
                            val topLeft = if (horizontal) {
                                Offset(anchor.x - lw / 2f, if (tickSign > 0f) anchor.y else anchor.y - lh)
                            } else {
                                Offset(if (tickSign > 0f) anchor.x else anchor.x - lw, anchor.y - lh / 2f)
                            }
                            drawText(label, topLeft = topLeft)
                        }
                        // The needle: its tip just clear of the track (or the thumb), its
                        // base further out, on the side away from the ticks. Filled and
                        // traced with a round join, which softens its corners.
                        if (hasNeedle) {
                            val side = -tickSign
                            val tip = reach + needleGapPx
                            needle.rewind()
                            needle.moveTo(point(at, side * tip))
                            needle.lineTo(point(at, side * (tip + needleHeightPx), -needleHalfBasePx))
                            needle.lineTo(point(at, side * (tip + needleHeightPx), needleHalfBasePx))
                            needle.close()
                            val colour = if (needleMatchesFill) colourAlong(stops, at) else colours.needle
                            drawPath(needle, colour)
                            drawPath(needle, colour, style = Stroke(needleRoundPx, join = StrokeJoin.Round))
                        }
                        if (hasThumb) {
                            val c = point(at)
                            drawCircle(colours.thumbRing, radius = thumbPx, center = c)
                            drawCircle(colours.thumb, radius = thumbPx - ringPx, center = c)
                        }
                    }
                }
            )
            Box(
                contentAlignment = when {
                    atValue -> Alignment.Center
                    horizontal -> Alignment.TopStart
                    else -> Alignment.CenterStart
                },
            ) {
                if (contentBackground) {
                    // Out round the content, not into it, as on the gauge — and drawn
                    // behind it alone, so a full-width slot is not a full-width capsule.
                    val shape = Theme.shapes.capsule
                    val colour = colours.contentBackground.takeOrElse { defaultContentBackground }
                    val across = Theme.spacing.sm
                    val down = Theme.spacing.xxs
                    Box(
                        modifier = Modifier.drawBehind {
                            val x = across.toPx()
                            val y = down.toPx()
                            val outline = shape.createOutline(
                                Size(this.size.width + x * 2f, this.size.height + y * 2f),
                                layoutDirection,
                                this,
                            )
                            translate(left = -x, top = -y) { drawOutline(outline, colour) }
                        },
                        contentAlignment = Alignment.Center,
                        content = content,
                    )
                } else {
                    content()
                }
            }
        },
    ) { measurables, constraints ->
        val cross = scaleCross.roundToInt()
        // A horizontal meter fills the width it is given; a vertical one is its
        // default length unless it is told otherwise.
        val length = if (horizontal) {
            if (constraints.hasBoundedWidth) constraints.maxWidth else max(lengthPx.roundToInt(), constraints.minWidth)
        } else {
            lengthPx.roundToInt().coerceIn(constraints.minHeight, constraints.maxHeight)
        }
        val slotRoom = if (horizontal) {
            if (constraints.hasBoundedHeight) (constraints.maxHeight - cross).coerceAtLeast(0) else Constraints.Infinity
        } else {
            if (constraints.hasBoundedWidth) (constraints.maxWidth - cross).coerceAtLeast(0) else Constraints.Infinity
        }
        val slot = measurables[1].measure(
            when {
                horizontal && !atValue -> Constraints(minWidth = length, maxWidth = length, maxHeight = slotRoom)
                horizontal -> Constraints(maxWidth = length, maxHeight = slotRoom)
                !atValue -> Constraints(minHeight = length, maxHeight = length, maxWidth = slotRoom)
                else -> Constraints(maxHeight = length, maxWidth = slotRoom)
            }
        )
        val scale = measurables[0].measure(
            if (horizontal) Constraints.fixed(length, cross) else Constraints.fixed(cross, length)
        )
        val hasSlot = if (horizontal) slot.height > 0 else slot.width > 0
        val gap = if (hasSlot) gapPx.roundToInt() else 0
        val width = if (horizontal) length else cross + gap + slot.width
        val height = if (horizontal) slot.height + gap + cross else length
        layout(
            width.coerceIn(constraints.minWidth, constraints.maxWidth),
            height.coerceIn(constraints.minHeight, constraints.maxHeight),
        ) {
            // Where the reading is along the meter, from its start; placed
            // relative, so right to left mirrors it with the drawing.
            val span = (length - startInset - endInset).coerceAtLeast(0f)
            val along = startInset + span * fractionOf(shown.value)
            if (horizontal) {
                val x = if (atValue) {
                    (along - slot.width / 2f).roundToInt().coerceIn(0, (length - slot.width).coerceAtLeast(0))
                } else {
                    0
                }
                slot.placeRelative(x, 0)
                scale.placeRelative(0, slot.height + gap)
            } else {
                val y = if (atValue) {
                    (length - along - slot.height / 2f).roundToInt().coerceIn(0, (length - slot.height).coerceAtLeast(0))
                } else {
                    0
                }
                scale.placeRelative(0, 0)
                slot.placeRelative(cross + gap, y)
            }
        }
    }
}

private fun Path.moveTo(to: Offset) = moveTo(to.x, to.y)

private fun Path.lineTo(to: Offset) = lineTo(to.x, to.y)

/** Which way a [Meter] runs. */
enum class MeterOrientation {
    /** Across the page, filling from the start of the line — so from the right, right to left. */
    Horizontal,

    /** Up the page, filling from the bottom — the thermometer, the tank. */
    Vertical,
}

/** Where a [Meter]'s content sits. */
enum class MeterContentPlacement {
    /** In one place: above a horizontal meter, beside a vertical one. */
    Fixed,

    /** Centred on the reading, travelling with it, and kept inside the meter's ends. */
    AtValue,
}

object MeterDefaults {
    /** The track's width. */
    val Thickness: Dp get() = MeterThickness

    /**
     * A vertical meter's height when nothing else says, and a horizontal one's
     * width somewhere with no width to fill.
     */
    val Length: Dp get() = MeterLength

    /** A needle a little taller than the track is thick. */
    val NeedleLength: Float get() = MeterNeedleShare

    /**
     * The theme's colours for a meter: a gauge's. The fill is the accent; pass
     * [ScaleColours.gradient] or [ScaleColours.bands] as [indicator] for more.
     */
    @Composable
    fun colours(
        indicator: ScaleColours = ScaleColours.solid(Theme.colours.primary),
        track: Color = Theme.colours.surfaceSunken,
        tick: Color = Theme.colours.outline,
        tickLabel: Color = Theme.colours.contentMuted,
        needle: Color = Theme.colours.content,
        thumb: Color = Theme.colours.surfaceRaised,
        thumbRing: Color = Theme.colours.outline,
        contentBackground: Color = Theme.colours.surface.copy(alpha = ContentBackgroundAlpha),
    ): DialColours = GaugeDefaults.colours(
        indicator = indicator,
        track = track,
        tick = tick,
        tickLabel = tickLabel,
        needle = needle,
        thumb = thumb,
        thumbRing = thumbRing,
        contentBackground = contentBackground,
    )
}

private val MeterThickness: Dp = 8.dp
private val MeterLength: Dp = 160.dp
private const val MeterNeedleShare: Float = 1.25f

/** The needle's base against its height: a little wider than tall. */
private const val NeedleBaseShare: Float = 1.2f

/** Between the needle's tip and the track or thumb it points at. */
private val NeedleGap: Dp = 2.dp

/** However thin the track, a needle you can see. */
private val NeedleMinimum: Dp = 6.dp

/** How much the needle's corners are softened. */
private val NeedleRounding: Dp = 1.5.dp
