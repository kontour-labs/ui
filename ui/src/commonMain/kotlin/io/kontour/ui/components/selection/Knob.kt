package io.kontour.ui.components.selection

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.kontour.ui.a11y.minimumTouchTarget
import io.kontour.ui.components.display.DialColours
import io.kontour.ui.components.display.DialGeometry
import io.kontour.ui.components.display.dialArcs
import io.kontour.ui.components.display.dialTicks
import io.kontour.ui.input.Cursor
import io.kontour.ui.input.focusRing
import io.kontour.ui.input.pointerCursor
import io.kontour.ui.interaction.DragClaim
import io.kontour.ui.interaction.freeDragOwning
import io.kontour.ui.interaction.rememberDetentTicker
import io.kontour.ui.interaction.rememberEndStopLatch
import io.kontour.ui.theme.Theme
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.roundToInt

/**
 * A dial the user turns: a value on an arc, set by turning a face with a notch on
 * it.
 *
 * ```kotlin
 * var volume by remember { mutableStateOf(0.4f) }
 * Knob(
 *     value = volume,
 *     onValueChange = { volume = it },
 *     contentDescription = "Volume",
 * ) {
 *     Text("${(volume * 100).roundToInt()}")
 * }
 * ```
 *
 * The input half of [io.kontour.ui.components.display.Gauge], drawing the same
 * scale. Where a [Slider] wants a line's worth of room, a knob wants a square, and
 * it earns it where a row of settings sits in a grid — an equaliser, a mixer, a
 * synth's panel — or where the thing being set is itself a turn.
 *
 * ### Turning it
 *
 * **Round, the way a knob turns.** The value follows the angle of the finger
 * around the centre, and keeps following it however many times the finger goes
 * round, up to either end. Near the centre, where an angle means nothing, an up
 * or down drag turns it instead.
 *
 * **Thrown, it spins.** Let go while turning quickly and it carries on, slowing,
 * through the steps — the spinning-wheel feel — and stops at an end if it reaches
 * one. Not under reduced motion, where it stops where it was let go.
 *
 * **A tick per step it passes**, turned or spinning, and one report on running
 * into either end.
 *
 * The drag belongs to the dial's round face and track only. A finger landing in
 * the square's corners is the page's, so a knob in a scrolling column does not
 * stop the column scrolling.
 *
 * @param steps How many values between the two ends it stops at, as for [Slider].
 *   Zero turns smoothly. Each stop gets a tick mark outside the track.
 * @param sweepAngle How far round the scale goes, in degrees, centred on the gap
 *   at the bottom.
 * @param thickness The track's width.
 * @param colours The fill along the track is `indicator`; the face is `thumb`
 *   ringed in `thumbRing`, and the notch on it is `needle`.
 * @param stateDescription What a screen reader says for the value, from it.
 * @param content Centred on the face — the value, a unit, an icon.
 */
@Composable
fun Knob(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    size: Dp = KnobDefaults.Size,
    sweepAngle: Float = KnobDefaults.SweepAngle,
    thickness: Dp = KnobDefaults.Thickness,
    colours: DialColours = KnobDefaults.colours(),
    contentDescription: String? = null,
    stateDescription: ((Float) -> String)? = null,
    onValueChangeFinished: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable BoxScope.() -> Unit = {},
) {
    require(valueRange.start <= valueRange.endInclusive) {
        "Knob was given an inverted valueRange (${valueRange.start}..${valueRange.endInclusive})."
    }
    require(steps >= 0) { "Knob was given $steps steps; it takes zero or more." }

    val interactions = interactionSource ?: remember { MutableInteractionSource() }
    val scope = rememberCoroutineScope()
    val motion = Theme.motion
    val density = LocalDensity.current
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentFinished by rememberUpdatedState(onValueChangeFinished)
    val ticker = rememberDetentTicker()
    val endStop = rememberEndStopLatch()
    val sweep = sweepAngle.coerceIn(MinSweep, FullTurn)
    val span = valueRange.endInclusive - valueRange.start

    fun fractionOf(v: Float): Float = if (span <= 0f) 0f else ((v - valueRange.start) / span).coerceIn(0f, 1f)
    val intervals = steps + 1
    fun snapped(fraction: Float): Float =
        if (steps == 0) fraction else (fraction * intervals).roundToInt().toFloat() / intervals

    // The gesture's own position, unclamped, so a turn past an end has to come back
    // the way it went before the value moves again.
    var raw by remember { mutableStateOf(Float.NaN) }
    var pointer by remember { mutableStateOf(Offset.Zero) }
    var pointerFresh by remember { mutableStateOf(false) }
    var spin by remember { mutableStateOf<Job?>(null) }

    fun emit(fraction: Float, fromHand: Boolean) {
        val landed = snapped(fraction.coerceIn(0f, 1f))
        if (steps > 0 && fromHand) ticker.at((landed * intervals).roundToInt())
        currentOnValueChange(valueRange.start + span * landed)
    }

    fun finish() {
        raw = Float.NaN
        ticker.reset()
        endStop.reset()
        currentFinished?.invoke()
    }

    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        // Square, and no bigger than it is given.
        val side = minOf(size, maxWidth, maxHeight)
        val sizePx = with(density) { side.toPx() }
        val thicknessPx = with(density) { thickness.toPx() }
        val tickRoom = if (steps > 0) with(density) { (KnobTickGap + KnobTick).toPx() } else 0f
        val radius = (sizePx / 2f - tickRoom - thicknessPx / 2f).coerceAtLeast(1f)
        val centre = Offset(sizePx / 2f, sizePx / 2f)

        Box(
            modifier = Modifier
                .semantics {
                    if (!enabled) disabled()
                    if (contentDescription != null) this.contentDescription = contentDescription
                    progressBarRangeInfo = ProgressBarRangeInfo(
                        current = value.coerceIn(valueRange),
                        range = valueRange,
                        steps = steps,
                    )
                    if (stateDescription != null) this.stateDescription = stateDescription(value)
                    // Withheld when disabled, as on `Slider`: an inert-looking control
                    // that assistive tech can still move is worse than either.
                    if (enabled) {
                        setProgress { target ->
                            currentOnValueChange(target.coerceIn(valueRange))
                            true
                        }
                    }
                }
                .minimumTouchTarget()
                .focusRing(interactions, Theme.shapes.pill)
                .size(side)
                .alpha(if (enabled) 1f else DisabledAlpha)
                .onKeyEvent { event ->
                    if (!enabled || event.type != KeyEventType.KeyDown) return@onKeyEvent false
                    val step = if (steps > 0) 1f / intervals else KeyStep
                    val now = fractionOf(value)
                    val next = when (event.key) {
                        Key.DirectionUp, Key.DirectionRight -> now + step
                        Key.DirectionDown, Key.DirectionLeft -> now - step
                        Key.PageUp -> now + maxOf(step, PageStep)
                        Key.PageDown -> now - maxOf(step, PageStep)
                        Key.MoveHome -> 0f
                        Key.MoveEnd -> 1f
                        else -> return@onKeyEvent false
                    }
                    emit(next, fromHand = false)
                    currentFinished?.invoke()
                    true
                }
                .focusable(enabled, interactions)
                .pointerCursor(Cursor.Grab, enabled = enabled)
                .freeDragOwning(
                    enabled = enabled,
                    interactionSource = interactions,
                    scope = scope,
                    claimsOn = DragClaim.Movement,
                    // The round face and track, not the square's corners.
                    accepts = { at -> (at - centre).getDistance() <= radius + thicknessPx },
                    onStart = { at ->
                        spin?.cancel()
                        raw = fractionOf(value)
                        pointer = at
                        pointerFresh = true
                        ticker.reset()
                        ticker.at((snapped(raw) * intervals).roundToInt())
                        endStop.arm()
                    },
                    onDelta = { delta ->
                        // The claim hands over where the finger *is*, and then the move
                        // that got it there: the first delta ends at the start point.
                        val fresh = pointerFresh
                        val from = if (fresh) pointer - delta else pointer
                        val to = from + delta
                        pointer = to
                        pointerFresh = false
                        val base = if (raw.isNaN()) fractionOf(value) else raw
                        raw = if ((to - centre).getDistance() < radius * CentreShare) {
                            // Near the middle an angle is noise: up is more.
                            base - delta.y / (radius * VerticalTravel)
                        } else {
                            base + turnBetween(from - centre, to - centre) / sweep
                        }
                        endStop.at(if (raw > 1f) 1 else if (raw < 0f) -1 else 0)
                        emit(raw, fromHand = true)
                    },
                    onRelease = { velocity ->
                        if (motion.reduceMotion || raw.isNaN()) return@freeDragOwning
                        val r = pointer - centre
                        val reach = r.getDistance()
                        if (reach < radius * CentreShare) return@freeDragOwning
                        // Degrees a second, from the part of the release across the radius.
                        val cross = r.x * velocity.y - r.y * velocity.x
                        val turning = cross / (reach * reach) * 180f / PI.toFloat()
                        if (abs(turning) < FlickTurn) return@freeDragOwning
                        val startAt = raw.coerceIn(0f, 1f)
                        spin = scope.launch {
                            try {
                                AnimationState(startAt, turning / sweep).animateDecay(exponentialDecay(SpinFriction)) {
                                    // `this.value`: the spin's, not the knob's parameter.
                                    val at = this.value
                                    if (at >= 1f || at <= 0f) {
                                        endStop.at(if (at >= 1f) 1 else -1)
                                        emit(at, fromHand = true)
                                        cancelAnimation()
                                    } else {
                                        emit(at, fromHand = true)
                                    }
                                }
                            } finally {
                                spin = null
                                finish()
                            }
                        }
                    },
                    onEnd = { if (spin?.isActive != true) finish() },
                )
                .drawWithCache {
                    val geometry = DialGeometry(centre, radius, DialGeometry.startFor(sweep), sweep)
                    val face = (radius - thicknessPx / 2f - KnobFaceGap.toPx()).coerceAtLeast(1f)
                    val ring = KnobFaceRing.toPx()
                    val notchWidth = KnobNotch.toPx()
                    val tickWidth = KnobTickWidth.toPx()
                    val tickLength = KnobTick.toPx()
                    val tickEdge = radius + thicknessPx / 2f + KnobTickGap.toPx()
                    onDrawBehind {
                        val at = fractionOf(value)
                        dialArcs(geometry, thicknessPx, StrokeCap.Round, colours.track, colours.indicator, 0f, at)
                        if (steps > 0) {
                            dialTicks(
                                geometry, majors = steps + 2, minorsBetween = 0, edge = tickEdge,
                                majorLength = tickLength, minorLength = tickLength, width = tickWidth,
                                colour = colours.tick, inward = false,
                            )
                        }
                        drawCircle(colours.thumbRing, radius = face, center = centre)
                        drawCircle(colours.thumb, radius = face - ring, center = centre)
                        drawLine(
                            color = colours.needle,
                            start = geometry.pointAt(at, face * NotchFrom),
                            end = geometry.pointAt(at, face * NotchTo),
                            strokeWidth = notchWidth,
                            cap = StrokeCap.Round,
                        )
                    }
                },
            contentAlignment = Alignment.Center,
            content = content,
        )
    }
}

/**
 * The turn from [from] to [to] about the origin, in degrees, the short way round —
 * so a finger crossing the gap at the bottom, where the angle jumps from 180 to
 * −180, reads as the few degrees it moved.
 */
internal fun turnBetween(from: Offset, to: Offset): Float {
    val a = atan2(from.y, from.x) * 180f / PI.toFloat()
    val b = atan2(to.y, to.x) * 180f / PI.toFloat()
    var d = b - a
    while (d > HalfTurn) d -= FullTurn
    while (d < -HalfTurn) d += FullTurn
    return d
}

object KnobDefaults {
    /** The knob's width and height. */
    val Size: Dp get() = KnobSize

    /** The track's width. */
    val Thickness: Dp get() = KnobThickness

    /** Three quarters of a turn, open at the bottom, as on a gauge. */
    val SweepAngle: Float get() = KnobSweep

    /** The theme's colours for a knob: a raised face with a notch, over a track. */
    @Composable
    fun colours(
        indicator: List<Color> = listOf(Theme.colours.primary),
        track: Color = Theme.colours.surfaceSunken,
        tick: Color = Theme.colours.outline,
        face: Color = Theme.colours.surfaceRaised,
        faceRing: Color = Theme.colours.outline,
        notch: Color = Theme.colours.content,
    ): DialColours = DialColours(
        indicator = indicator,
        track = track,
        tick = tick,
        tickLabel = Theme.colours.contentMuted,
        needle = notch,
        thumb = face,
        thumbRing = faceRing,
    )
}

private val KnobSize: Dp = 96.dp
private val KnobThickness: Dp = 6.dp
private const val KnobSweep: Float = 270f
private val KnobFaceGap: Dp = 6.dp
private val KnobFaceRing: Dp = 1.dp
private val KnobNotch: Dp = 3.dp
private val KnobTick: Dp = 4.dp
private val KnobTickGap: Dp = 3.dp
private val KnobTickWidth: Dp = 1.5.dp

/** Inside this share of the radius a drag is up-and-down rather than round. */
private const val CentreShare: Float = 0.3f

/** How many radii an up-and-down drag travels for the whole range. */
private const val VerticalTravel: Float = 4f

/** Degrees a second a release has to be turning at to spin on. */
private const val FlickTurn: Float = 90f

/** How quickly a spin slows: `exponentialDecay`'s friction. */
private const val SpinFriction: Float = 2f

/** A key press without steps: a hundredth of the range. */
private const val KeyStep: Float = 0.01f

/** Page Up and Down: a tenth of the range, or a step if that is more. */
private const val PageStep: Float = 0.1f

private const val NotchFrom: Float = 0.45f
private const val NotchTo: Float = 0.85f
private const val DisabledAlpha: Float = 0.38f
private const val MinSweep: Float = 30f
private const val HalfTurn: Float = 180f
private const val FullTurn: Float = 360f
