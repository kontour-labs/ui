package io.kontour.ui.components.selection

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.animateFloatAsState
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
import io.kontour.ui.components.display.ScaleColours
import io.kontour.ui.components.display.dialArcs
import io.kontour.ui.components.display.dialFill
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
 * **Turned or dragged, the way GarageBand's knobs are.** Go round the knob and it
 * turns with the finger's angle, so a finger that grabbed the notch keeps it. Drag
 * it in a line and it becomes a slider along that line's axis — up and down, or
 * across — and **which way is more depends on the notch**: the drag pulls the notch
 * the way it moves it round the arc. At the end of the scale, low on the right, up
 * pulls it back towards zero; where a drag runs straight across the arc at the notch,
 * or would pull into the end it is already at, up or right is more. Once chosen, the
 * direction holds for the gesture, so a drag carried on keeps turning the knob the
 * same way, past the top and round. Which of the two a gesture is gets read from its
 * shape — a circle curves as it sweeps round the middle and a line does not — within
 * the first few dp, without going the wrong way while it tells. See
 * `KnobTurnReader`.
 *
 * **Thrown, it spins.** Let go while moving quickly and it carries on, slowing,
 * through the steps — the spinning-wheel feel — and stops at an end if it reaches
 * one. Not under reduced motion, where it stops where it was let go.
 *
 * **Stepped, it has detents**, the way a stepped [Slider] does. Dragged between
 * two steps, the notch leans from the step it is on toward the finger — never all
 * the way, so it reads as held by the step — and crossing to the next one it
 * carries on from where it had got to rather than jumping. Let go, or moved from
 * the keyboard, it springs onto its step. Under reduced motion it sits on its step.
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
 * @param colours The fill along the track is `indicator`, a [ScaleColours] — one
 *   colour, a gradient, or bands at values in the knob's own units; the face is
 *   `thumb` ringed in `thumbRing`, and the notch on it is `needle`.
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
    // A band's gaps, and an unbanded scale, are the dial's own colour.
    val scaleDefault = Theme.colours.primary
    val span = valueRange.endInclusive - valueRange.start

    fun fractionOf(v: Float): Float = if (span <= 0f) 0f else ((v - valueRange.start) / span).coerceIn(0f, 1f)
    val intervals = steps + 1
    fun snapped(fraction: Float): Float =
        if (steps == 0) fraction else (fraction * intervals).roundToInt().toFloat() / intervals

    // The gesture's own position, unclamped, so a drag past an end has to come back
    // the way it went before the value moves again.
    var raw by remember { mutableStateOf(Float.NaN) }
    var spin by remember { mutableStateOf<Job?>(null) }
    val travelPx = with(density) { KnobDragTravel.toPx() }
    val decidePx = with(density) { KnobDecideAfter.toPx() }
    val flickPx = with(density) { KnobFlick.toPx() }
    val reader = remember { KnobTurnReader() }

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

    // Only a stepped knob has detents to lean against, as on `Slider`. A continuous
    // one draws its value straight through: easing a value that already tracks the
    // finger would only be lag.
    val detented = steps > 0 && !motion.reduceMotion

    /**
     * Where the notch and the fill are going: the step, pulled part of the way
     * toward where the hand has turned it, by the slider's own
     * [SliderDefaults.DetentPull].
     *
     * The pull is inside the spring's **target**, for the reason `Slider` gives:
     * added to the spring's output instead, the lean flips sides the instant a step
     * is crossed while the spring is still on the old step, and the notch jumps back
     * before it goes forward.
     */
    val detentTarget = fractionOf(value).let { at ->
        if (detented && !raw.isNaN()) at + (raw.coerceIn(0f, 1f) - at) * SliderDefaults.DetentPull else at
    }
    val detentDrawn = animateFloatAsState(
        targetValue = detentTarget,
        animationSpec = motion.springOrTween(motion.springSnappy),
        label = "knobDetent",
    )

    // The control's semantics, keys and focus on the node the caller's `modifier`
    // lands on, as `Slider` puts its semantics: one node that is the knob, which a
    // caller's tag and assistive technology both find. On an inner node, the node
    // they found did not say it was disabled, and the one that took focus had no
    // value to announce.
    BoxWithConstraints(
        modifier = modifier
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
            .focusable(enabled, interactions),
        contentAlignment = Alignment.Center,
    ) {
        // Square, and no bigger than it is given.
        val side = minOf(size, maxWidth, maxHeight)
        val sizePx = with(density) { side.toPx() }
        val thicknessPx = with(density) { thickness.toPx() }
        val tickRoom = if (steps > 0) with(density) { (KnobTickGap + KnobTick).toPx() } else 0f
        val radius = (sizePx / 2f - tickRoom - thicknessPx / 2f).coerceAtLeast(1f)
        val centre = Offset(sizePx / 2f, sizePx / 2f)

        Box(
            modifier = Modifier
                .minimumTouchTarget()
                .focusRing(interactions, Theme.shapes.pill)
                .size(side)
                .alpha(if (enabled) 1f else DisabledAlpha)
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
                        reader.start(
                            at = at,
                            centre = centre,
                            radius = radius,
                            start = DialGeometry.startFor(sweep),
                            sweep = sweep,
                            fraction = raw,
                            travel = travelPx,
                            decideAfter = decidePx,
                        )
                        ticker.reset()
                        ticker.at((snapped(raw) * intervals).roundToInt())
                        endStop.arm()
                    },
                    onDelta = { delta ->
                        val base = if (raw.isNaN()) fractionOf(value) else raw
                        raw = base + reader.move(delta)
                        endStop.at(if (raw > 1f) 1 else if (raw < 0f) -1 else 0)
                        emit(raw, fromHand = true)
                    },
                    onRelease = { velocity ->
                        if (motion.reduceMotion || raw.isNaN()) return@freeDragOwning
                        // The range a second, read the way the gesture was: round the
                        // middle for a turn, along the axes for a drag.
                        val turning = reader.release(velocity, flickPx) ?: return@freeDragOwning
                        val startAt = raw.coerceIn(0f, 1f)
                        spin = scope.launch {
                            try {
                                AnimationState(startAt, turning).animateDecay(exponentialDecay(SpinFriction)) {
                                    // `this.value`: the spin's, not the knob's parameter.
                                    val at = this.value
                                    // The spin is the hand, for the lean.
                                    raw = at
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
                    val fill = dialFill(geometry, thicknessPx, StrokeCap.Round, colours.indicator.stops(valueRange, scaleDefault))
                    onDrawBehind {
                        // Coerced: `springSnappy` overshoots, and a notch past the end
                        // of its own scale reads as a fault rather than a bounce.
                        val at = if (detented) detentDrawn.value.coerceIn(0f, 1f) else fractionOf(value)
                        dialArcs(geometry, thicknessPx, StrokeCap.Round, colours.track, fill, 0f, at)
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
 * How much of the range a drag of [delta] turns the knob, with [travel] pixels for
 * the whole of it: right and up are more, left and down are less, and the two axes
 * add — so a drag up and to the right is quicker than either, and one down and to
 * the right is the finger not meaning either.
 */
internal fun knobDragTurn(delta: Offset, travel: Float): Float =
    if (travel <= 0f) 0f else (delta.x - delta.y) / travel

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
        indicator: ScaleColours = ScaleColours.solid(Theme.colours.primary),
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

/**
 * How far a drag goes for the whole range, whatever the knob's size: a step of ten
 * is 20dp, a comfortable distance to feel each detent, and a full sweep is a
 * thumb's length rather than the width of the screen.
 */
private val KnobDragTravel: Dp = 200.dp

/** How fast, along the drag or round the knob, a release has to be moving to spin on. */
private val KnobFlick: Dp = 400.dp

/** How far a gesture travels before the knob decides whether it is a turn or a drag. */
private val KnobDecideAfter: Dp = 12.dp

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
private const val FullTurn: Float = 360f
