package io.kontour.ui.components.display

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.kontour.ui.theme.Theme
import kotlin.math.max

/**
 * A reading on a dial: a value on an arc, open at the bottom.
 *
 * ```kotlin
 * Gauge(
 *     value = rpm,
 *     valueRange = 0f..10_000f,
 *     indicator = GaugeIndicator.Needle,
 *     majorTicks = 6,
 *     tickLabel = { "${(it / 1000).toInt()}K" },
 *     colours = GaugeDefaults.colours(indicator = ScaleColours.gradient(listOf(pink, purple))),
 * ) {
 *     Stat { value("8.5k"); +"RPM" }
 * }
 * ```
 *
 * **Display only.** A gauge says what something *is*; it does not take input,
 * and a screen reader hears it as a progress reading. For a dial the user turns,
 * use [io.kontour.ui.components.selection.Knob], which draws the same scale.
 *
 * Everything about the picture is a parameter, because gauges are the one place
 * an app's personality shows up in a number: how far round the scale goes, how
 * thick the arc is and how its ends are cut, whether the fill is one colour, a
 * gradient or bands along the scale ([ScaleColours]), whether it points with a
 * needle — how long, in what colour — or marks the value with a thumb, how many
 * ticks there are and whether they are labelled, inside the arc or out. The middle
 * is a slot — a number and a unit, an icon, nothing.
 *
 * ### The fill starts at [origin]
 *
 * Which is the start of the range unless it is told otherwise. A reading that can
 * go either side of a centre — a balance, a trim, a temperature against a target —
 * fills from the centre out in both directions when [origin] is put there.
 *
 * ### It does not mirror
 *
 * A dial reads clockwise everywhere, the way a clock does, so right to left leaves
 * it as it is. A linear bar mirrors; a dial is not a bar bent round.
 *
 * @param value The reading, in [valueRange]. Values outside it are drawn at the
 *   nearer end.
 * @param origin Where the fill starts from, in [valueRange].
 * @param sweepAngle How far round the scale goes, in degrees, centred on the gap
 *   at the bottom. 270 by default; 180 is a half-dial, 360 a ring.
 * @param thickness The arc's width. The thumb and the needle scale with it.
 * @param cap How the arc's two ends and the fill's ends are cut.
 * @param colours The fill is `indicator`, a [ScaleColours]: one colour, a gradient
 *   along the scale, or bands at values on it in the gauge's own units. The needle
 *   is `needle`.
 * @param indicator What marks the value besides the fill: nothing, a needle from
 *   the centre, a thumb on the arc, or both.
 * @param needleLength How far the needle reaches, as a share of the room between
 *   the hub and the innermost thing drawn on the scale — the tick labels, the ticks,
 *   or the arc when neither is inside it. 0.8, the default, stops clear of them;
 *   1 reaches them; more crosses them, as far as the arc's outer edge.
 * @param needleMatchesFill Whether the needle, and its hub, take the fill's colour at
 *   the value — the band the reading is in, or the gradient there — rather than
 *   `colours.needle`. Crossing a hard band edge it changes with the band; through
 *   a smoothed one it blends.
 * @param majorTicks How many labelled marks, counting both ends — six for 0, 2K, …
 *   10K. Zero for none.
 * @param minorTicks How many shorter marks between each pair of major ones.
 * @param tickLabel The text at each major tick, from its value. Null for marks
 *   without labels.
 * @param tickPlacement Whether the ticks and their labels sit inside the arc or
 *   outside it. Outside leaves the middle to [content] and takes room from the arc.
 * @param contentBackground Whether [content] sits on a translucent capsule of
 *   `colours.contentBackground`, so a needle passing behind the reading does not run
 *   through it. Off by default, where nothing crosses the middle; worth turning on
 *   with a needle that sweeps past the label.
 * @param animated Whether a new [value] travels there or is simply drawn there.
 *   Reduced motion does not travel either way.
 * @param stateDescription What a screen reader says for the value, from it — "8,500
 *   revolutions a minute" rather than a percentage of the range.
 * @param content Centred in the dial, or just below the needle's hub when there is one.
 */
@Composable
fun Gauge(
    value: Float,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    origin: Float = valueRange.start,
    size: Dp = GaugeDefaults.Size,
    sweepAngle: Float = GaugeDefaults.SweepAngle,
    thickness: Dp = GaugeDefaults.Thickness,
    cap: StrokeCap = StrokeCap.Round,
    colours: DialColours = GaugeDefaults.colours(),
    indicator: GaugeIndicator = GaugeIndicator.None,
    needleLength: Float = GaugeDefaults.NeedleLength,
    needleMatchesFill: Boolean = false,
    majorTicks: Int = 0,
    minorTicks: Int = 0,
    tickLabel: ((Float) -> String)? = null,
    tickPlacement: GaugeTickPlacement = GaugeTickPlacement.Inside,
    contentBackground: Boolean = false,
    animated: Boolean = true,
    contentDescription: String? = null,
    stateDescription: ((Float) -> String)? = null,
    content: @Composable BoxScope.() -> Unit = {},
) {
    require(valueRange.start <= valueRange.endInclusive) {
        "Gauge was given an inverted valueRange (${valueRange.start}..${valueRange.endInclusive})."
    }
    val motion = Theme.motion
    val density = LocalDensity.current
    val labelStyle = Theme.typography.labelSmall.copy(color = colours.tickLabel)
    val measurer = rememberTextMeasurer()
    val sweep = sweepAngle.coerceIn(MinSweep, FullTurn)
    // A band's gaps, and an unbanded scale, are the dial's own colour.
    val scaleDefault = Theme.colours.primary
    val defaultContentBackground = Theme.colours.surface.copy(alpha = ContentBackgroundAlpha)

    // Read in draw and nowhere else, so a gauge at rest does no work and a moving
    // one only redraws.
    val shown = remember { Animatable(value) }
    LaunchedEffect(value, animated, motion.reduceMotion) {
        if (animated && !motion.reduceMotion) {
            shown.animateTo(value, motion.springOrTween(motion.springGentle))
        } else {
            shown.snapTo(value)
        }
    }

    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val thicknessPx = with(density) { thickness.toPx() }
        val tickGapPx = with(density) { GaugeTickGap.toPx() }
        val majorPx = if (majorTicks >= 2) with(density) { GaugeMajorTick.toPx() } else 0f
        // Remembered: a new value changes none of them, and a new list each time
        // was also a new draw cache below, arc brush and all, for every reading.
        val labels = remember(majorTicks, tickLabel, valueRange, labelStyle, measurer) {
            if (majorTicks >= 2 && tickLabel != null) {
                List(majorTicks) { index ->
                    val at = valueRange.start + (valueRange.endInclusive - valueRange.start) * index / (majorTicks - 1)
                    measurer.measure(tickLabel(at), labelStyle)
                }
            } else {
                emptyList()
            }
        }
        val labelExtent = labels.maxOfOrNull { max(it.size.width, it.size.height) }?.toFloat() ?: 0f
        val outside = tickPlacement == GaugeTickPlacement.Outside
        val hasNeedle = indicator == GaugeIndicator.Needle || indicator == GaugeIndicator.NeedleAndThumb
        val hasThumb = indicator == GaugeIndicator.Thumb || indicator == GaugeIndicator.NeedleAndThumb
        val thumbPx = if (hasThumb) thicknessPx * ThumbShare else thicknessPx / 2f
        // Square, and no bigger than it is given: a narrow column gets a smaller dial
        // rather than one drawn past its edges.
        val side = minOf(size, maxWidth, maxHeight)
        val sizePx = with(density) { side.toPx() }
        val outerReserve = if (outside) majorPx + tickGapPx + (if (labels.isEmpty()) 0f else labelExtent + tickGapPx) else 0f
        val radius = (sizePx / 2f - outerReserve - max(thumbPx, thicknessPx / 2f)).coerceAtLeast(1f)
        // The innermost thing drawn, which is where the middle starts.
        val inner = if (outside) {
            radius - thicknessPx / 2f
        } else {
            radius - thicknessPx / 2f - (if (majorPx > 0f) tickGapPx + majorPx else 0f) -
                (if (labels.isEmpty()) 0f else tickGapPx + labelExtent)
        }.coerceAtLeast(0f)
        val contentInset = with(density) { (sizePx / 2f - inner * InscribedShare).coerceAtLeast(0f).toDp() }
        // Under a needle the middle is the hub, so the content starts below it — the
        // reading under the pivot, as on a speedometer.
        val belowHub = if (hasNeedle) {
            with(density) {
                (sizePx / 2f + thicknessPx * NeedleWidthShare / 2f * NeedleHubShare + tickGapPx).toDp()
            }
        } else {
            contentInset
        }

        val range = valueRange
        fun fractionOf(v: Float): Float {
            val span = range.endInclusive - range.start
            return if (span <= 0f) 0f else ((v - range.start) / span).coerceIn(0f, 1f)
        }
        val needle = remember { Path() }

        Box(
            modifier = Modifier
                .size(side)
                .semantics(mergeDescendants = true) {
                    if (contentDescription != null) this.contentDescription = contentDescription
                    progressBarRangeInfo = ProgressBarRangeInfo(current = value.coerceIn(range), range = range)
                    if (stateDescription != null) this.stateDescription = stateDescription(value)
                }
                .drawWithCache {
                    val geometry = DialGeometry(
                        centre = Offset(this.size.width / 2f, this.size.height / 2f),
                        radius = radius,
                        start = DialGeometry.startFor(sweep),
                        sweep = sweep,
                    )
                    val tickEdge = if (outside) radius + thicknessPx / 2f + tickGapPx else radius - thicknessPx / 2f - tickGapPx
                    val labelAt = if (outside) {
                        tickEdge + majorPx + tickGapPx + labelExtent / 2f
                    } else {
                        tickEdge - majorPx - tickGapPx - labelExtent / 2f
                    }
                    val tickWidth = GaugeTickWidth.toPx()
                    val minorPx = GaugeMinorTick.toPx()
                    val stops = colours.indicator.stops(range, scaleDefault)
                    val fill = dialFill(geometry, thicknessPx, cap, stops)
                    // No further than the arc's outer edge, and never shorter than the
                    // arc is thick — in that order, because a dial squeezed to nothing
                    // has an outer edge nearer than that.
                    val needleReach = (inner * needleLength)
                        .coerceAtMost(radius + thicknessPx / 2f)
                        .coerceAtLeast(thicknessPx)
                    onDrawWithContent {
                        val at = fractionOf(shown.value)
                        dialArcs(
                            geometry, thicknessPx, cap, colours.track, fill,
                            from = fractionOf(origin), to = at,
                        )
                        dialTicks(
                            geometry, majorTicks, minorTicks, tickEdge, majorPx, minorPx,
                            tickWidth, colours.tick, inward = !outside,
                        )
                        // Under the needle, as on a speedometer — unless the reading has
                        // a background, which reaches out over the scale in a narrow
                        // middle; then the labels go on top of it, below.
                        if (!contentBackground) dialTickLabels(geometry, labels, labelAt)
                        // The needle first, so with both the thumb sits on top of
                        // the arc and the needle points at it from underneath.
                        if (hasNeedle) {
                            dialNeedle(
                                geometry, needle, at,
                                length = needleReach,
                                width = thicknessPx * NeedleWidthShare,
                                colour = if (needleMatchesFill) colourAlong(stops, at) else colours.needle,
                            )
                        }
                        if (hasThumb) {
                            dialThumb(
                                geometry, at, radius = thumbPx,
                                ring = GaugeThumbRing.toPx(),
                                fill = colours.thumb, ringColour = colours.thumbRing,
                            )
                        }
                        drawContent()
                        if (contentBackground) dialTickLabels(geometry, labels, labelAt)
                    }
                }
        ) {
            Box(
                modifier = Modifier.matchParentSize().padding(
                    start = contentInset,
                    end = contentInset,
                    top = maxOf(contentInset, belowHub),
                    // The bottom of the dial is its open gap, which is room.
                    bottom = if (hasNeedle) 0.dp else contentInset,
                ),
                contentAlignment = if (hasNeedle) Alignment.TopCenter else Alignment.Center,
            ) {
                if (contentBackground) {
                    // Drawn after the needle, like the content it is behind, so the
                    // needle passes under the reading rather than through it. **Out
                    // round the content, not into it**: the middle of a dial with
                    // labelled ticks inside is narrow, and padding taken out of it
                    // wrapped a reading of "0.9k" one character to a line.
                    val shape = Theme.shapes.capsule
                    val colour = colours.contentBackground.takeOrElse { defaultContentBackground }
                    val across = Theme.spacing.sm
                    val down = Theme.spacing.xxs
                    Box(
                        modifier = Modifier.drawBehind {
                            val x = across.toPx()
                            val y = down.toPx()
                            val outline = shape.createOutline(
                                // `this.size`: the content's, not the gauge's parameter.
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
        }
    }
}

/** What marks a [Gauge]'s value besides the fill. */
enum class GaugeIndicator {
    /** The fill alone. */
    None,

    /** A tapered needle from the centre, on a hub — the speedometer. */
    Needle,

    /** A disc on the arc at the value — the thermostat. */
    Thumb,

    /**
     * The needle pointing at a thumb on the arc: the value marked where it is read
     * and pointed at from the middle, for a dial read at a glance from across a
     * room.
     */
    NeedleAndThumb,
}

/** Which side of a [Gauge]'s arc its ticks and their labels sit on. */
enum class GaugeTickPlacement {
    /** Between the arc and the middle, which the labels share with the content. */
    Inside,

    /** Beyond the arc, which leaves the middle to the content and shrinks the arc. */
    Outside,
}

/**
 * The colours of a dial — a [Gauge] or a [io.kontour.ui.components.selection.Knob].
 *
 * @param indicator The fill's colours along the scale: one colour, a gradient, or
 *   bands at values in the dial's own units. See [ScaleColours].
 * @param track The arc behind the fill.
 * @param tick The tick marks.
 * @param tickLabel The text at the major ticks.
 * @param needle A gauge's needle, or the notch on a knob.
 * @param thumb The disc of a gauge's thumb, or a knob's face.
 * @param thumbRing The ring round the thumb, or round a knob's face.
 * @param contentBackground Behind a gauge's content, when it asks for one — a
 *   translucent surface by default, so the dial shows through it. Unspecified takes
 *   that default.
 */
@Immutable
data class DialColours(
    val indicator: ScaleColours,
    val track: Color,
    val tick: Color,
    val tickLabel: Color,
    val needle: Color,
    val thumb: Color,
    val thumbRing: Color,
    val contentBackground: Color = Color.Unspecified,
)

object GaugeDefaults {
    /** The dial's width and height. */
    val Size: Dp get() = GaugeSize

    /** The arc's width. */
    val Thickness: Dp get() = GaugeThickness

    /** Three quarters of a turn, open at the bottom. */
    val SweepAngle: Float get() = GaugeSweep

    /** A needle that stops clear of the scale's ticks and labels. */
    val NeedleLength: Float get() = NeedleShare

    /**
     * The theme's colours for a dial. The fill is the accent; pass
     * [ScaleColours.gradient] or [ScaleColours.bands] as [indicator] for more.
     */
    @Composable
    @ReadOnlyComposable
    fun colours(
        indicator: ScaleColours = ScaleColours.solid(Theme.colours.primary),
        track: Color = Theme.colours.surfaceSunken,
        tick: Color = Theme.colours.outline,
        tickLabel: Color = Theme.colours.contentMuted,
        needle: Color = Theme.colours.content,
        thumb: Color = Theme.colours.surfaceRaised,
        thumbRing: Color = Theme.colours.outline,
        contentBackground: Color = Theme.colours.surface.copy(alpha = ContentBackgroundAlpha),
    ): DialColours = DialColours(indicator, track, tick, tickLabel, needle, thumb, thumbRing, contentBackground)
}

private val GaugeSize: Dp = 160.dp
private val GaugeThickness: Dp = 12.dp
private const val GaugeSweep: Float = 270f
internal val GaugeTickGap: Dp = 4.dp
internal val GaugeMajorTick: Dp = 6.dp
internal val GaugeMinorTick: Dp = 3.dp
internal val GaugeTickWidth: Dp = 1.5.dp
internal val GaugeThumbRing: Dp = 2.dp

/** How far past the arc's half-width a thumb reaches. */
internal const val ThumbShare: Float = 0.85f

/** A needle's length against the room inside the ticks. */
private const val NeedleShare: Float = 0.8f

/** How opaque the capsule behind a gauge's content is, by default: enough to read over a needle. */
internal const val ContentBackgroundAlpha: Float = 0.85f

/** A needle's width against the arc's. */
internal const val NeedleWidthShare: Float = 0.5f

/** The half-side of the square inside a circle, against its radius: 1/√2. */
private const val InscribedShare: Float = 0.707f

private const val MinSweep: Float = 30f
private const val FullTurn: Float = 360f
