package io.kontour.ui.components.selection

import androidx.compose.animation.core.AnimationState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import kotlin.math.abs
import kotlin.math.roundToLong
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
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
import io.kontour.ui.a11y.trackAtTier
import io.kontour.ui.components.display.DialColours
import io.kontour.ui.components.display.DialGeometry
import io.kontour.ui.components.display.DialReadout
import io.kontour.ui.components.display.ReadoutScope
import io.kontour.ui.components.display.ScaleColours
import io.kontour.ui.components.display.dialArcs
import io.kontour.ui.components.display.dialFill
import io.kontour.ui.components.display.dialTicks
import io.kontour.ui.components.display.readoutSlots
import io.kontour.ui.input.Cursor
import io.kontour.ui.input.focusRing
import io.kontour.ui.input.pointerCursor
import io.kontour.ui.interaction.DragClaim
import io.kontour.ui.interaction.freeDragOwning
import io.kontour.ui.interaction.rememberDetentTicker
import io.kontour.ui.interaction.rememberDragTexture
import io.kontour.ui.interaction.rememberEndStopLatch
import io.kontour.ui.theme.Theme
import io.kontour.ui.theme.lines
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * A dial the user turns: a value on an arc, set by turning a face with a notch on
 * it.
 *
 * ```kotlin
 * var volume by remember { mutableStateOf(40f) }
 * Knob(
 *     value = volume,
 *     onValueChange = { volume = it },
 *     valueRange = 0f..100f,
 *     contentDescription = "Volume",
 * ) {
 *     +"${volume.roundToInt()}"
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
 * ### Typing a value
 *
 * **Tap the face and type**, where a turn is too coarse for the number wanted —
 * asked for from a phone as "tap value to edit". The readout becomes a field in
 * its own style, holding the value and selected, with a decimal keyboard. Done,
 * Enter or tapping away sets it: read in the reader's own digits and separator,
 * kept inside [valueRange] and put on the nearest step. Something that is not a
 * number leaves the value as it was, and Escape puts it back. From the keyboard,
 * Enter or Space on the focused knob opens the field, and assistive technology
 * gets it as an action.
 *
 * Only a knob showing its figure opens: the field takes the figure's place, and
 * a bare face gives no sign that a tap would do anything.
 *
 * The field holds **the value itself**, in [valueRange]'s units. A readout that
 * shows something else — a 0 to 1 volume written as a percentage — would offer a
 * number the reader has not seen, so give such a knob the range it shows, `0f..100f`,
 * or `editable = false`.
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
 * @param editable Whether a tap on the face opens the value for typing, where
 *   [content] shows a figure. See "Typing a value" above.
 * @param content The readout, centred on the face: `+"40"` is the figure,
 *   `label("dB")` what it is of, and `value { +icon }` an icon. See
 *   [ReadoutScope].
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
    editable: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
    content: ReadoutScope.() -> Unit = {},
) {
    val readout = readoutSlots(content)
    require(valueRange.start <= valueRange.endInclusive) {
        "Knob was given an inverted valueRange (${valueRange.start}..${valueRange.endInclusive})."
    }
    require(steps >= 0) { "Knob was given $steps steps; it takes zero or more." }

    val interactions = interactionSource ?: remember { MutableInteractionSource() }
    val scope = rememberCoroutineScope()
    val motion = Theme.motion
    val density = LocalDensity.current
    // The face's rim and the ticks are lines, thickening with the tier.
    val faceRing = Theme.sizing.borderWidth
    val tickLine = Theme.sizing.lines(1.5f)
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentFinished by rememberUpdatedState(onValueChangeFinished)
    val ticker = rememberDetentTicker()
    val endStop = rememberEndStopLatch()
    // A knob without steps turns with the slider's texture — see `DragTexture` —
    // and so does its spin, which fades as it slows.
    val texture = rememberDragTexture()
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
    val formats = Theme.formats
    val editLabel = Theme.strings.editValue
    // The readout, open for typing. Not saved across a configuration change: a
    // half-typed number is not worth restoring into a control the hand has left.
    var editing by remember { mutableStateOf(false) }
    // Only with a figure showing: the field takes the figure's place, and a
    // face with nothing on it gives no sign that a tap would open anything.
    val canEdit = editable && enabled && readout.value != null
    val knobFocus = remember { FocusRequester() }

    fun emit(fraction: Float, fromHand: Boolean) {
        val landed = snapped(fraction.coerceIn(0f, 1f))
        if (steps > 0 && fromHand) ticker.at((landed * intervals).roundToInt())
        if (steps == 0 && fromHand) texture.at(landed)
        currentOnValueChange(valueRange.start + span * landed)
    }

    /** What the field holds: the value, to as many places as its steps need. */
    fun editText(): String = formats.decimal(value.toDouble(), knobEditDigits(span, steps))

    /**
     * Sets what was typed, if it is a number, kept in range and on a step. Focus
     * comes back to the knob when the field was answered from the keyboard, and
     * stays wherever it went when the reader tapped away.
     */
    fun commitEdit(typed: String, fromKeys: Boolean) {
        editing = false
        if (fromKeys) knobFocus.requestFocus()
        val number = formats.parseDecimal(typed.trim())?.toFloat()?.coerceIn(valueRange) ?: return
        // Taken as typed where it can be: through a fraction and back, 15 came
        // out as 15.000001. A stepped knob puts it on its step.
        currentOnValueChange(if (steps == 0) number else valueRange.start + span * snapped(fractionOf(number)))
        currentFinished?.invoke()
    }

    fun finish() {
        raw = Float.NaN
        ticker.reset()
        endStop.reset()
        texture.reset()
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
    // Read by the drawing through a state rather than captured by it. Captured,
    // every new value was a new draw-cache lambda, and every one of those rebuilt
    // the arc's gradient — once per drag event, for a brush that never changes.
    val drawnValue = rememberUpdatedState(value)

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
                if (canEdit && !editing) {
                    customActions = listOf(CustomAccessibilityAction(editLabel) { editing = true; true })
                }
            }
            .onKeyEvent { event ->
                if (!enabled || editing || event.type != KeyEventType.KeyDown) return@onKeyEvent false
                if (canEdit && (event.key == Key.Enter || event.key == Key.NumPadEnter || event.key == Key.Spacebar)) {
                    editing = true
                    return@onKeyEvent true
                }
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
            .focusRequester(knobFocus)
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
                        endStop.at(raw)
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
                                        endStop.reached(if (at >= 1f) 1 else -1)
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
                // A tap on the face, which the drag above leaves alone: it claims on
                // movement, and a tap never moves far enough to be one.
                .pointerInput(canEdit, radius) {
                    if (!canEdit) return@pointerInput
                    detectTapGestures { at ->
                        if ((at - centre).getDistance() <= radius) {
                            spin?.cancel()
                            editing = true
                        }
                    }
                }
                .drawWithCache {
                    val geometry = DialGeometry(centre, radius, DialGeometry.startFor(sweep), sweep)
                    val face = (radius - thicknessPx / 2f - KnobFaceGap.toPx()).coerceAtLeast(1f)
                    val ring = faceRing.toPx()
                    val notchWidth = KnobNotch.toPx()
                    val tickWidth = tickLine.toPx()
                    val tickLength = KnobTick.toPx()
                    val tickEdge = radius + thicknessPx / 2f + KnobTickGap.toPx()
                    val fill = dialFill(geometry, thicknessPx, StrokeCap.Round, colours.indicator.stops(valueRange, scaleDefault))
                    onDrawBehind {
                        // Coerced: `springSnappy` overshoots, and a notch past the end
                        // of its own scale reads as a fault rather than a bounce.
                        val at = if (detented) detentDrawn.value.coerceIn(0f, 1f) else fractionOf(drawnValue.value)
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
        ) {
            if (editing) {
                KnobEditor(
                    initial = remember { editText() },
                    width = with(density) { (radius * EditorWidth).toDp() },
                    onCommit = ::commitEdit,
                    onCancel = {
                        editing = false
                        knobFocus.requestFocus()
                    },
                )
            } else if (!readout.isEmpty) {
                DialReadout(readout, Theme.typography.titleMedium)
            }
        }
    }
}

/**
 * A knob's readout, open for typing: the value, selected, in the figure's style,
 * set on Done, Enter or leaving it, and put back on Escape. Commits once however
 * it is left — Done takes the focus away too, and that is not a second answer.
 */
@Composable
private fun KnobEditor(
    initial: String,
    width: Dp,
    onCommit: (text: String, fromKeys: Boolean) -> Unit,
    onCancel: () -> Unit,
) {
    val state = remember { TextFieldState(initial, TextRange(0, initial.length)) }
    val focus = remember { FocusRequester() }
    var answered by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    fun commit(fromKeys: Boolean) {
        if (answered) return
        answered = true
        onCommit(state.text.toString(), fromKeys)
    }
    fun cancel() {
        if (answered) return
        answered = true
        onCancel()
    }
    val colours = Theme.colours
    BasicTextField(
        state = state,
        modifier = Modifier
            .width(width)
            .focusRequester(focus)
            .onFocusChanged { now ->
                if (focused && !now.isFocused) commit(fromKeys = false)
                focused = now.isFocused
            }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.Escape -> { cancel(); true }
                    Key.Enter, Key.NumPadEnter -> { commit(fromKeys = true); true }
                    else -> false
                }
            },
        textStyle = Theme.typography.titleMedium.copy(color = colours.content, textAlign = TextAlign.Center),
        cursorBrush = SolidColor(colours.accent.solid),
        lineLimits = TextFieldLineLimits.SingleLine,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
        onKeyboardAction = { commit(fromKeys = true) },
    )
    LaunchedEffect(Unit) { focus.requestFocus() }
}

/**
 * How many places a knob's value is typed to: enough to write a step exactly — a
 * step of a tenth is one place — or, turning smoothly, a hundredth of the range.
 * Never more than three, for a step like an eleventh that no number of places
 * writes exactly.
 */
internal fun knobEditDigits(span: Float, steps: Int): Int {
    if (span <= 0f) return 0
    var unit = (if (steps > 0) span / (steps + 1) else span / 100f).toDouble()
    var digits = 0
    while (digits < MaxEditDigits && abs(unit - unit.roundToLong()) > EditTolerance * maxOf(1.0, abs(unit))) {
        unit *= 10
        digits++
    }
    return digits
}

/**
 * How much of the range a drag of [delta] turns the knob, with [travel] pixels for
 * the whole of it: right and up are more, left and down are less, and the two axes
 * add — so a drag up and to the right is quicker than either, and one down and to
 * the right is the finger not meaning either.
 */
internal fun knobDragTurn(delta: Offset, travel: Float): Float =
    if (travel <= 0f) 0f else (delta.x - delta.y) / travel

/** What a [Knob] takes by default. */
object KnobDefaults {
    /** The knob's width and height. */
    val Size: Dp get() = KnobSize

    /** The track's width. */
    val Thickness: Dp get() = KnobThickness

    /** Three quarters of a turn, open at the bottom, as on a gauge. */
    val SweepAngle: Float get() = KnobSweep

    /**
     * The theme's colours for a knob: a raised face with a notch, over a track.
     *
     * Named for the [DialColours] fields they fill, as `GaugeDefaults.colours`'
     * are: the [thumb] is the knob's face, [thumbRing] its rim, and the [needle]
     * the notch cut into it.
     */
    @Composable
    @ReadOnlyComposable
    fun colours(
        indicator: ScaleColours = ScaleColours.solid(Theme.colours.primary),
        track: Color = trackAtTier(Theme.colours.surfaceSunken),
        tick: Color = Theme.colours.outline,
        thumb: Color = Theme.colours.surfaceRaised,
        thumbRing: Color = Theme.colours.outline,
        needle: Color = Theme.colours.content,
    ): DialColours = DialColours(
        indicator = indicator,
        track = track,
        tick = tick,
        tickLabel = Theme.colours.contentMuted,
        needle = needle,
        thumb = thumb,
        thumbRing = thumbRing,
    )
}

private val KnobSize: Dp = 96.dp
private val KnobThickness: Dp = 6.dp
private const val KnobSweep: Float = 270f
private val KnobFaceGap: Dp = 6.dp
private val KnobNotch: Dp = 3.dp
private val KnobTick: Dp = 4.dp
private val KnobTickGap: Dp = 3.dp

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

/** The field's width, as a share of the dial's radius: most of the face across. */
private const val EditorWidth: Float = 1.3f
private const val MaxEditDigits: Int = 3
private const val EditTolerance: Double = 1e-3
private const val MinSweep: Float = 30f
private const val FullTurn: Float = 360f
