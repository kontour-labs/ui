package io.kontour.ui.components.selection

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.graphics.Shape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import io.kontour.ui.interaction.rememberTapFeedback
import io.kontour.ui.foundation.ProvideTextStyle
import io.kontour.ui.foundation.IndicatorSizing
import io.kontour.ui.foundation.SelectionIndicatorBox
import io.kontour.ui.foundation.Surface
import io.kontour.ui.foundation.rememberSelectionIndicatorState
import io.kontour.ui.foundation.selectionIndicatorItem
import io.kontour.ui.foundation.Text
import io.kontour.ui.input.focusRing
import io.kontour.ui.input.pointerCursor
import io.kontour.ui.interaction.rememberRubberBand
import io.kontour.ui.interaction.DragClaim
import io.kontour.ui.interaction.horizontalDragOwning
import io.kontour.ui.interaction.rememberDetentTicker
import io.kontour.ui.theme.Shadow
import io.kontour.ui.a11y.contrastEdge
import io.kontour.ui.theme.Theme
import io.kontour.ui.theme.inset
import kotlin.math.abs
import kotlinx.coroutines.launch

object SegmentedControlDefaults {
    /**
     * The inset between the track and its thumb.
     *
     * The thumb's shape is the track's stepped down by exactly this much — see
     * [io.kontour.ui.theme.inset], which derives it.
     *
     * The note that used to be here claimed 6dp was "one rung of the shape
     * scale", which it is not — 6dp is the *step between* rungs — and that the
     * derivation above was already happening, which it was not. `inset` resolved
     * the track's capsule against the thumb's own shorter box, so the gap came
     * off twice and the thumb was drawn at 10dp where the track is 22. The "at
     * 3dp the thumb read one dp too square" recorded here was that skew being
     * tuned around rather than found: at 3dp the error is 3dp, and moving to 6
     * made the error worse while making the visible ring better, so both halves
     * of the observation were true and the conclusion drawn from them was not.
     *
     * Six stays, on its own merits — it is a ring you can see — and the radius
     * it produces is now the one it always claimed.
     */
    val TrackPadding: Dp
        @Composable @ReadOnlyComposable get() = Theme.componentDefaults.segmentedTrackPadding
}

/**
 * The furthest the thumb elongates while straining toward the next segment.
 *
 * A fifth again its own width. The thumb is a whole segment wide rather than a
 * 22dp circle, so it needs far less proportional stretch than a slider's does
 * before it reads as give.
 */
private const val MaxSegmentStretch = 0.2f

/**
 * A row of mutually exclusive options, presented as one control.
 *
 * ```
 * SegmentedControl(
 *     options = listOf("Depart", "Arrive"),
 *     selected = mode,
 *     onSelectedChange = viewModel::setMode,
 * )
 * ```
 *
 * Best for two to four short options that the user switches between often — a
 * depart/arrive toggle, a day/week/month range. Beyond four, or with long
 * labels, use a [io.kontour.ui.components.selection.RadioGroup] or a `Select`;
 * segments get too narrow to read and too narrow to hit.
 *
 * The selected indicator is a single surface that **slides** between positions
 * rather than each segment fading its own background. That is what makes the
 * control read as one physical thing with a moving part, and it is why the
 * indicator is drawn behind the labels rather than per segment.
 */
@Composable
fun SegmentedControl(
    options: List<String>,
    selected: Int,
    onSelectedChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val tap = rememberTapFeedback()
    if (options.isEmpty()) return

    // Only here to put `constraints` in scope for the fit decision below — the
    // control has to know how wide it is *before* it decides which way to run,
    // and that is a measurement no modifier can hand it. The caller's modifier
    // goes on this node, so the width it establishes is the one being divided.
    BoxWithConstraints(modifier) {

    val colours = Theme.colours
    val motion = Theme.motion
    val outerShape = Theme.shapes.field
    // Concentric by construction rather than by picking the token one rung down
    // and trusting the padding to match the step.
    val innerShape = outerShape.inset(SegmentedControlDefaults.TrackPadding)
    // At least a fingertip tall, whatever the control height token says.
    //
    // A segmented control is one control made of parts, so it owns the touch
    // target for all of them — the same bargain `ButtonGroup` strikes. It used
    // to pin `controlHeightMedium` and opt out of `minimumTouchTarget`
    // altogether, which on Android left it 4dp shorter than any `Button` beside
    // it and quietly broke the promise in `Sizing`'s KDoc that a row of mixed
    // controls lines up. Invisible on desktop, where the minimum is 24dp.
    val height = maxOf(Theme.sizing.controlHeightMedium, Theme.sizing.minTouchTarget)
    val indicator = rememberSelectionIndicatorState()

    var trackWidth by remember { mutableFloatStateOf(0f) }
    val currentSelected by rememberUpdatedState(selected)
    val currentChange by rememberUpdatedState(onSelectedChange)
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val ticker = rememberDetentTicker()
    val scope = rememberCoroutineScope()

    /**
     * What a drag pushing past either end of the track does instead of nothing.
     *
     * Scaled to a **segment**, which is what the thumb is: the same limit the
     * lean's stretch is already capped at, so the squash and the lean are one
     * deformation with two sources rather than two that can disagree.
     */
    val band = rememberRubberBand()

    /**
     * Where the finger is along the track, or `NaN` before the first drag.
     *
     * Kept after the finger lifts rather than cleared, so the thumb relaxes back
     * out of its lean instead of losing the number it was leaning by. [engaged]
     * is what says whether there is a finger; this only says where it was.
     */
    var fingerX by remember { mutableFloatStateOf(Float.NaN) }
    var dragging by remember { mutableStateOf(false) }

    /**
     * How much of the lean toward the finger is applied — 1 while dragging, 0 at
     * rest, and springing between the two.
     *
     * The lean itself is worked out in the thumb's `graphicsLayer` from the
     * finger and from where the indicator has actually reached, because both of
     * those change every frame and neither is worth a recomposition.
     *
     * It leans by [SliderDefaults.DetentPull], the same fraction the sliders
     * use and for the same reason: far enough to read as the thumb straining
     * toward where you are going, never so far that it is closer to the next
     * segment than to its own.
     *
     * Off under reduced motion, as the sliders' detent pull is. The thumb still
     * travels to the segment you picked; it just stops reaching for you.
     */
    val engaged by animateFloatAsState(
        targetValue = if (dragging && !motion.reduceMotion) 1f else 0f,
        animationSpec = motion.springOrTween(motion.springSnappy),
        label = "segmentStrain",
    )

    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val labelStyle = Theme.typography.labelMedium

    /**
     * Whether the labels have to stop sharing one row.
     *
     * **Measured, not guessed**, and measured against the same number the
     * ellipsis decision is made from: the widest label at `labelMedium` against
     * the width one segment would get.
     *
     * At 200% type "Keyboard" is 124dp against an 84dp segment, and `Standard` is
     * 119.5dp against the same — so two of the settings panel's three segmented
     * controls cut their own labels, on the one accessibility setting whose whole
     * purpose is to make text readable. Wrapping to a second line does not help:
     * `Keyboard`, `Standard` and `200` are single unbreakable words, so
     * `maxLines = 2` changes nothing at all.
     *
     * It cannot oscillate. Stacking changes the height and never the width, so
     * the number the decision is made from is the same before and after it.
     */
    // Read out here: `TrackPadding` is a `@ReadOnlyComposable` property off the
    // theme, and `remember`'s calculation is not a composable context.
    val trackPadding = SegmentedControlDefaults.TrackPadding
    val stacked = remember(options, measurer, constraints.maxWidth, density, labelStyle, trackPadding) {
        val track = with(density) { constraints.maxWidth.toDp() } - trackPadding * 2
        val each = track / options.size
        val widest = options.maxOf { measurer.measure(it, labelStyle).size.width }
        with(density) { widest.toDp() } > each
    }

    SelectionIndicatorBox(
        state = indicator,
        // The thumb is exactly the segment it marks. Sized from the measured
        // segment rather than `maxWidth / options.size`, so segments no longer
        // have to be equal width — which the previous implementation required.
        sizing = IndicatorSizing.Fill,
        modifier = Modifier
            .selectableGroup()
            // `heightIn`, not `height`. An exact height is a promise the type
            // cannot keep: the content box is this less 12dp of track padding —
            // 36dp on Android, 32dp elsewhere — while a 14sp label with a 1.20
            // line height grows linearly, crossing 32dp at about 1.9x and 36dp at
            // about 2.14x. Past that the clip on each segment cut the glyphs top
            // and bottom, with no vertical equivalent of an ellipsis to mark it.
            // Nothing moves at the default scale, where the label fits with room
            // to spare.
            .heightIn(min = if (stacked) height * options.size else height)
            .clip(outerShape)
            .background(colours.surfaceSunken, outerShape)
            .then(
                contrastEdge()?.let { Modifier.border(it, outerShape) } ?: Modifier
            )
            .padding(SegmentedControlDefaults.TrackPadding),
        indicator = {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        // Everything here is read inside the layer rather than
                        // in composition: the finger moves every frame and so
                        // does the indicator underneath, and neither of them
                        // changes anything but this transform.
                        val segments = options.size
                        val here = indicator.drawn
                        val lean = if (
                            fingerX.isNaN() || trackWidth <= 0f ||
                            segments == 0 || here.width <= 0f
                        ) {
                            0f
                        } else {
                            val segment = trackWidth / segments
                            // From where the thumb *is*, not from where the
                            // segment it belongs to would put it. Crossing a
                            // boundary moves the selection and starts the
                            // indicator travelling; measured from the new
                            // segment's centre the lean would flip sign on that
                            // same frame and throw the thumb backwards past the
                            // segment it just left. See `drawn`.
                            val base = here.center.x
                            val pulled = (fingerX - base) * SliderDefaults.DetentPull * engaged
                            // Never off the track: at either end the wall is the
                            // answer.
                            pulled.coerceIn(
                                segment / 2f - base,
                                (trackWidth - segment / 2f - base).coerceAtLeast(segment / 2f - base),
                            )
                        }

                        translationX = lean
                        // The end stop, which the clamp above turns into a wall.
                        // The wall stays — the thumb must not leave the track —
                        // and the part of the push it refused comes back as
                        // deformation instead.
                        val squash = band.offset * engaged
                        // Anchored on the edge it is leaving, so the thumb
                        // elongates toward the segment it is heading for rather
                        // than swelling in place. The slider's thumb does the
                        // same thing with the same signal.
                        //
                        // `lean` is zero at a stop, because the wall took it —
                        // so the squash has to carry the direction or a push off
                        // the left end pivots the wrong way and grows out of the
                        // track.
                        transformOrigin = TransformOrigin(
                            pivotFractionX = if (lean + squash >= 0f) 0f else 1f,
                            pivotFractionY = 0.5f,
                        )
                        val reach = if (trackWidth <= 0f || options.isEmpty()) {
                            0f
                        } else {
                            (abs(lean) + abs(squash)) / (trackWidth / options.size)
                        }
                        scaleX = 1f + reach.coerceAtMost(MaxSegmentStretch)
                    },
                shape = innerShape,
                // One token, no branch on the scheme.
                //
                // This used to read `isDark -> surfaceRaised else -> surface`,
                // because the two schemes were solving the problem from
                // opposite ends: light darkened the *ground* under a white
                // thumb, dark could not (its ground was already black) and
                // raised the thumb instead. `surfaceIndicator` is the raise,
                // done once, for both.
                //
                // The ground is `surfaceSunken` — the well a filled text field
                // uses. That was the report: a segmented control and a text
                // field on one screen were two greys apart and read as two
                // design systems. The cost is paid per scheme and it is not the
                // same cost:
                //
                //   dark      1.28:1 if the thumb had stayed put -> **1.59:1**
                //   dark/high 1.43:1 -> **1.94:1**
                //   light     **1.08:1**, and nothing can change it
                //
                // Dark ends up further apart than the arrangement it replaces.
                // Light cannot: white is the top of the ramp and the well is
                // `#F6F6F6`. So in light the fill carries nothing and the other
                // two carriers do the work — the shadow, raised a step to
                // `medium` for exactly this, and the label going `contentMuted`
                // -> `content` below. It is the trade iOS makes, whose own
                // segmented control measures 1.15:1, and it is a real loss for
                // a reader who could find the old ground and cannot find this
                // shadow. What they have not lost is the label.
                //
                // A shadow is no help in dark — `kontourElevation(dark = true)`
                // draws black, and black on a near-black well has nothing to
                // darken — which is the whole reason dark had to move the thumb
                // rather than lean on elevation like light does.
                // Disabled is `surface`, not `surfaceSunken`: the ground is
                // `surfaceSunken` now, so the old disabled fill would be the
                // ground exactly and the thumb would vanish rather than grey
                // out. WCAG exempts a disabled control from the ratio, but it
                // does not excuse a control you cannot see the state of.
                colour = if (enabled) colours.surfaceIndicator else colours.surface,
                shadow = if (enabled) Theme.elevation.medium else Shadow.None,
                // No border, in any scheme, and light pays for that.
                //
                // Dark does not need one: `surfaceIndicator` is 1.59:1 off the
                // well and 1.94:1 at the enhanced tier, both further apart than
                // the separate-track arrangement managed.
                //
                // Light cannot have one on those terms. White is the top of the
                // ramp and the ground is `#F6F6F6`, so the fill is 1.08:1
                // whatever token it reads, and the shadow — `medium` rather
                // than `low`, raised for exactly this — is worth **0.09** of a
                // ratio on a ground that pale. Measured end to end the light
                // thumb comes to **1.17:1**, against the 1.45 this control is
                // held to in dark and the 1.54 it used to have.
                //
                // A 1dp `outlineStrong` hairline was built and measured and
                // reaches **3.60:1**, clearing WCAG 1.4.11 outright. It was
                // rejected on sight, and the rendering is the argument: on a
                // near-white ground that line is a hard dark stroke around the
                // selected segment, which is the "visible apology for a fill
                // that is not doing its job" an earlier round already removed
                // once. The number was better and the control was worse.
                //
                // So light is carried by the shadow and by the label going
                // `contentMuted` -> `content` below — the same two carriers
                // iOS's own segmented control has, at a measured 1.15:1. The
                // label is the one that survives a reader who cannot separate
                // the greys at all, and it is the reason this is a defensible
                // trade rather than a quiet loss. `IndicatorVisibilityTest`
                // holds light to what light can actually do and dark to what
                // dark can, which is two floors because there are two
                // situations.
                content = {},
            )
        },
    ) {
        /**
         * Drag across the segments and the thumb comes with you.
         *
         * The thumb slides, so a finger put on it and moved sideways should
         * carry it — and until now the whole control could only be tapped, which
         * is the one gesture that does not use the thing that makes it a
         * segmented control rather than three buttons.
         *
         * On the track rather than on each segment: a drag that starts on
         * "Depart" and ends on "Arrive" leaves the segment it began in, and a
         * per-segment gesture loses the pointer at the boundary. The taps stay
         * per-segment, and `detectHorizontalDragGestures` waits for touch slop,
         * so a press that never travels is still a tap on the segment under it.
         */
        fun selectAt(x: Float) {
            if (trackWidth <= 0f) return
            val fraction = (x / trackWidth).coerceIn(0f, 1f)
            val raw = (fraction * options.size).toInt().coerceIn(options.indices)
            val index = if (isRtl) options.size - 1 - raw else raw
            // Once per segment crossed, the way a stepped slider ticks: a user
            // dragging without looking can feel where the boundaries are. The
            // ticker owns the guard now — it used to be "the index changed",
            // which is the same thing said once per component rather than once.
            ticker.at(index)
            if (index == currentSelected) return
            currentChange(index)
        }

        // The drag belongs to a row and only to a row. `selectAt` quantises
        // `x / trackWidth` into equal buckets along one axis, and stacked there
        // is no such axis — a vertical drag over stacked segments would also be
        // competing with the page scroller for its own direction. The taps are
        // per-segment and untouched, which is the whole interaction at a text
        // size where this fires.
        val track: Modifier = if (stacked) {
            Modifier.fillMaxWidth()
        } else {
            Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .onSizeChanged { trackWidth = it.width.toFloat() }
                // Not `detectHorizontalDragGestures`, which waits for its own
                // horizontal touch slop and therefore races the page scroller's
                // vertical one. A drag more than 45 degrees off the track was
                // losing that race and the thumb never moved at all — see
                // `horizontalDragOwning`, which has the measurement.
                //
                // `Movement` rather than `Press`: the taps are per-segment,
                // below this, and claiming the down would eat them. A press that
                // never travels is left entirely alone; the first pixel that
                // does travel is a drag and this takes it, before any slop.
                .horizontalDragOwning(
                    enabled = enabled,
                    interactionSource = null,
                    scope = scope,
                    claimsOn = DragClaim.Movement,
                    onStart = { offset ->
                        fingerX = offset.x
                        dragging = true
                        selectAt(offset.x)
                    },
                    // Accumulated rather than read off the change, because this
                    // reports movement rather than position. It comes to the same
                    // number: every change of the gesture is delivered here and
                    // the deltas of a pointer's whole path sum to its path.
                    onDelta = { dx ->
                        // A finger coming back closes the stretch it opened
                        // before the thumb moves again, or one gesture reads as
                        // two motions.
                        val offered = dx - band.payBack(dx)
                        fingerX += offered
                        selectAt(fingerX)
                        // Past the track is the only wall a segmented control
                        // has. `selectAt` already quantises, so this is the part
                        // of the finger the control cannot answer.
                        val past = when {
                            fingerX > trackWidth -> fingerX - trackWidth
                            fingerX < 0f -> fingerX
                            else -> 0f
                        }
                        if (past != 0f && !motion.reduceMotion && options.isNotEmpty()) {
                            band.pull(past, trackWidth / options.size * MaxSegmentStretch)
                        }
                    },
                    onEnd = {
                        dragging = false
                        ticker.reset()
                        // The spring `engaged` already runs on, so the lean's
                        // collapse and the squash's recovery are one motion.
                        scope.launch {
                            band.release(motion.springOrTween(motion.springSnappy))
                        }
                    },
                )
        }

        // `weight` is a `RowScope` member and `fillMaxWidth` is what a column
        // wants, so the per-segment modifier cannot be hoisted out of the
        // container — which is why [SegmentedOption] takes it rather than
        // building it. Everything else about a segment is identical either way.
        if (stacked) {
            Column(track) {
                options.forEachIndexed { index, option ->
                    SegmentedOption(
                        // Stacked, each segment is its own row and therefore its
                        // own touch target: `height` less the track's padding
                        // would be 36dp on Android, under the minimum. Side by
                        // side they share the control's, which is the bargain the
                        // control's own height already strikes.
                        modifier = Modifier.fillMaxWidth().heightIn(min = height),
                        option = option,
                        selected = index == selected,
                        enabled = enabled,
                        shape = innerShape,
                        onClick = { tap(); onSelectedChange(index) },
                    )
                }
            }
        } else {
            Row(track) {
                options.forEachIndexed { index, option ->
                    SegmentedOption(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        option = option,
                        selected = index == selected,
                        enabled = enabled,
                        shape = innerShape,
                        onClick = { tap(); onSelectedChange(index) },
                    )
                }
            }
        }
    }
    }
}

/**
 * One option, in whichever direction the track is running.
 *
 * Extracted when the control learned to stack, for a reason that is Kotlin's
 * rather than the design's: `Modifier.weight` belongs to `RowScope` and has no
 * meaning in a column, so the two containers cannot share a modifier and the
 * segment has to take one. Nothing else about an option depends on the
 * direction.
 */
@Composable
private fun SegmentedOption(
    modifier: Modifier,
    option: String,
    selected: Boolean,
    enabled: Boolean,
    shape: Shape,
    onClick: () -> Unit,
) {
    val colours = Theme.colours
    val motion = Theme.motion
    val interactions = remember { MutableInteractionSource() }

    val labelColour by animateColorAsState(
        targetValue = when {
            !enabled -> colours.contentDisabled
            selected -> colours.content
            else -> colours.contentMuted
        },
        animationSpec = motion.tweenFast(),
        label = "segmentLabel",
    )

    Box(
        modifier = modifier
            .selectionIndicatorItem(option, selected)
            .focusRing(interactions, shape)
            .clip(shape)
            .pointerCursor(enabled = enabled)
            .selectable(
                selected = selected,
                onClick = onClick,
                enabled = enabled,
                role = Role.RadioButton,
                interactionSource = interactions,
                // The sliding thumb is the feedback; a wash on top of it would
                // fight with the movement.
                indication = null,
            ),
        contentAlignment = Alignment.Center,
    ) {
        ProvideTextStyle(Theme.typography.labelMedium) {
            // The floor under the stacking above rather than the answer to it.
            //
            // A track that cannot fit its labels lays them out in rows now, so
            // this is reached only by a single word wider than the *whole*
            // control — where there is nothing else to do. It matters that it is
            // an ellipsis and not `Text`'s default clip, for the reason `TabBar`
            // gives: a word cut mid-stroke reads as a different word rather than
            // a shortened one, and "Keyboard" arriving as "Keyboar" is a label
            // that lies rather than one that is short.
            Text(
                text = option,
                colour = labelColour,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
