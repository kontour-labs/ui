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
/**
 * The range a segmented control's thumb centre can occupy, which is where refusal
 * begins.
 *
 * **Not the track.** The finger's accumulator was clamped to `0..trackWidth` and
 * the band was handed whatever fell outside it, so nothing was refused until the
 * finger reached the track's *edge*. The thumb had stopped half a segment earlier,
 * at the last segment's centre — and the control already knew, because the lean is
 * clamped to these same two bounds and is exactly zero once the thumb has arrived.
 * So from the last centre to the track's edge, 30dp on a 60dp segment, the lean
 * was pinned, the thumb was still, and the band got nothing.
 *
 * Reported as *"I still feel like I have to drag it a bit further once it hits its
 * end stop before it starts squashing"*, and that is the whole of it: half a
 * segment of finger that bought nothing at all.
 *
 * Clamping to this instead puts the two limits back together. The squash begins on
 * the first pixel past the point the thumb stopped, and because the lean is
 * already zero there the handoff from stretching to squashing leaves no step in
 * the drawn width. `Slider`, `RangeSlider` and `Switch` have always clamped to the
 * travel they actually have; this control clamped to the track.
 *
 * Selection is unaffected: `selectAt` buckets `x / trackWidth`, and the last centre
 * still falls in the last bucket.
 *
 * A file-level function rather than a local one so it can be asserted without a
 * gesture — the deformation it drives is a `scaleX` under a drop shadow, and a
 * rendered width at these depths is ±8px of gradient against about 5px of signal.
 * `SegmentedThumbTravelTest` has the numbers.
 *
 * Degenerate widths fall back to the track: with one option the centre is the only
 * place the thumb can be, and `0f..trackWidth` at least lets a press land.
 */
internal fun segmentedThumbTravel(
    trackWidth: Float,
    options: Int,
): ClosedFloatingPointRange<Float> {
    val track = trackWidth.coerceAtLeast(0f)
    if (options <= 0) return 0f..track
    val half = track / options / 2f
    if (half * 2f >= track) return 0f..track
    return half..(track - half)
}

private const val MaxSegmentStretch = 0.2f

/**
 * How much of its width the thumb loses pushing into an end of the track.
 *
 * **The one place that does not take the library's 0.25.** `Switch` and
 * `SliderThumb` both squash a quarter off a 24dp thumb, which is 6dp; this thumb
 * is a whole segment wide, so 0.16 of it is already several times that in
 * pixels. A squash worth having is worth being the same size everywhere, and on
 * a control this much larger "the same size" is not the same fraction.
 *
 * Smaller than [MaxSegmentStretch] rather than equal to it, and the asymmetry is
 * the point: a stretch has a whole segment of empty track to grow into and a
 * squash is eating the thumb's own label. A fifth off the width of "Keyboard"
 * would be an ellipsis, which is a different message.
 *
 * The report that the squash reads as two states rather than as a pull was about
 * this control above all, and it was not the depth: the band's limit was a fifth
 * of a segment, which a finger crosses inside a frame. That is
 * `SliderThumb.EndStopTravel`'s to fix and it is fixed there.
 */
private const val SegmentSquash = 0.16f

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
    // Remembered rather than derived per composition: `inset` returns a fresh
    // `SquircleShape` through `copy`, and a new instance is a new modifier for
    // every node that takes it. See `SquirclePaths`.
    val outerPadding = SegmentedControlDefaults.TrackPadding
    val innerShape = remember(outerShape, outerPadding) { outerShape.inset(outerPadding) }
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
     * Scaled to [EndStopTravel], which is how far a *finger* goes past a stop
     * rather than anything about this control. It used to be a segment's own
     * stretch cap — about 17dp — on the argument that the squash and the lean
     * should be one deformation with two sources; they still are, and the cap on
     * the lean is still a segment's. What that reasoning missed is that the two
     * are measured in different things: the lean is a distance on the *track*
     * and the band is a distance the *hand* travels.
     */
    val band = rememberRubberBand()

    /** See [EndStopTravel]. Read once; both the pull and the squash use it. */
    val endStopTravelPx = with(LocalDensity.current) { EndStopTravel.toPx() }

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

    /**
     * How tall one segment's content has to be, which the type decides.
     *
     * The same measurement the fit decision above is made from, read on the other
     * axis. `height` less the track's padding is the box a label used to be given
     * whatever the text size — 36dp on Android, 32dp elsewhere — and a 14sp line
     * with a 1.20 line height passes that at about 2.14x, after which the
     * segment's own clip cut the glyphs top and bottom with nothing marking it.
     */
    val labelHeight = remember(options, measurer, density, labelStyle) {
        with(density) { options.maxOf { measurer.measure(it, labelStyle).size.height }.toDp() }
    }

    /** One segment's content box: at least what it used to be, and at least the type. */
    val segmentHeight = maxOf(height - trackPadding * 2, labelHeight)

    /**
     * A stacked segment is its own row and therefore its own touch target.
     *
     * Side by side they share the control's, which is the bargain `height`
     * already strikes — but `segmentHeight` alone would be 36dp on Android, under
     * the minimum, once each option is a row of its own.
     */
    val stackedRow = maxOf(segmentHeight, Theme.sizing.minTouchTarget)

    /**
     * The control's height, **exact** rather than a minimum.
     *
     * `heightIn(min = …)` was the obvious spelling and is wrong here: it leaves
     * the incoming maximum in place, and the track inside carries `fillMaxHeight`
     * — which used to fill an exact 44dp and would instead fill whatever the
     * caller happened to offer. A specimen card 120dp tall drew a 120dp control.
     *
     * So the height is still exact and is *computed* instead of pinned, from the
     * same measurement everything else here is derived from.
     */
    val outerHeight = if (stacked) {
        stackedRow * options.size + trackPadding * 2
    } else {
        maxOf(height, segmentHeight + trackPadding * 2)
    }

    SelectionIndicatorBox(
        state = indicator,
        // The thumb is exactly the segment it marks. Sized from the measured
        // segment rather than `maxWidth / options.size`, so segments no longer
        // have to be equal width — which the previous implementation required.
        sizing = IndicatorSizing.Fill,
        modifier = Modifier
            .selectableGroup()
            // Exact, and computed — see `outerHeight`. A constant height is the
            // promise the type could not keep; a *minimum* is a different fault,
            // because the track inside fills whatever it is given.
            .height(outerHeight)
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
                        val segmentWidth =
                            if (segments == 0) 0f else trackWidth / segments

                        // The end stop, which the clamp above turns into a wall.
                        // The wall stays — the thumb must not leave the track —
                        // and the part of the push it refused comes back as
                        // deformation instead.
                        //
                        // **A shortening, not a stretch.** It was a stretch, on
                        // the reasoning that the thumb should elongate toward the
                        // segment it is heading for; at an end stop there is no
                        // segment it is heading for, and what a push into an
                        // immovable wall does to the thing pushing is squash it.
                        // The first version grew the thumb backwards off the end
                        // of the track it had just been stopped by.
                        val squash = band.offset * engaged
                        // Normalised against the finger's travel past the stop,
                        // not against a fifth of a segment. The old limit was
                        // about 17dp and a flick crosses that in one frame, so
                        // the thumb arrived at its full squash immediately and
                        // sat there — which is what "there are only two states"
                        // was describing, and this control is where it showed.
                        val squashLimit = endStopTravelPx
                        val squeeze = if (squashLimit <= 0f) {
                            0f
                        } else {
                            (abs(squash) / squashLimit).coerceIn(0f, 1f) * SegmentSquash
                        }

                        // **The lean and the squash are never both live, which is
                        // what lets one pivot serve them both.** `lean` is the
                        // pull toward the finger *after* the `coerceIn` above,
                        // and the band only stretches when that same clamp
                        // refused a delta — so at a wall the lean is pinned to
                        // zero and the squash carries everything, and anywhere
                        // else the band is at rest and the squash is zero.
                        //
                        // They want opposite pivots, which is why it matters. A
                        // stretch is anchored on the edge it is leaving so the
                        // thumb reaches toward where it is going. A squash is
                        // anchored on the edge against the wall so the thumb
                        // shortens into it. Reading them off one signed sum
                        // picked the wrong one for whichever was non-zero.
                        transformOrigin = TransformOrigin(
                            pivotFractionX = when {
                                squash > 0f -> 1f
                                squash < 0f -> 0f
                                lean >= 0f -> 0f
                                else -> 1f
                            },
                            pivotFractionY = 0.5f,
                        )
                        val reach = if (segmentWidth <= 0f) {
                            0f
                        } else {
                            abs(lean) / segmentWidth
                        }
                        scaleX =
                            (1f + reach.coerceAtMost(MaxSegmentStretch)) * (1f - squeeze)
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
                        // Clamped like every later frame is: a press can land in
                        // the track's own padding, and an accumulator that
                        // starts outside the track is the thing this control
                        // used to get wrong for the whole gesture.
                        fingerX = offset.x.coerceIn(0f, trackWidth.coerceAtLeast(0f))
                        dragging = true
                        selectAt(fingerX)
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

                        // **The band is handed the part of *this* delta the
                        // track refused, not the total distance past it.**
                        //
                        // `fingerX` used to run on unclamped, and the overshoot
                        // was recomputed from it every frame and passed whole to
                        // `band.pull` — which is incremental, so it compounded.
                        // At a limit's distance past the stop a single frame
                        // absorbs about 63% of the remaining room, so the band
                        // reached its limit in two or three frames and stayed
                        // there however much further the finger went.
                        //
                        // Worse, it could not be undone. `payBack` closes the
                        // band against the finger coming home, and then the very
                        // same frame re-pulled it to the limit, because
                        // `fingerX` was still far past the track. Reported as
                        // not being able to drag back the other way without
                        // letting go first — which was literally the only way
                        // out, since lifting is what resets `fingerX`.
                        //
                        // `Switch`, `Slider` and `RangeSlider` all do it this
                        // way already: clamp the accumulator, hand the band the
                        // remainder. This control was the one that did not.
                        val before = fingerX
                        val raw = fingerX + offered
                        fingerX = raw.coerceIn(0f, trackWidth.coerceAtLeast(0f))
                        selectAt(fingerX)
                        if (!motion.reduceMotion && options.isNotEmpty()) {
                            // **Refused against the thumb's reach, not the
                            // track's edge.** See [segmentedThumbTravel]: the
                            // thumb stops at the last segment's centre, half a
                            // segment before the track ends, and refusing from
                            // the edge meant half a segment of finger bought
                            // nothing at all.
                            //
                            // `fingerX` itself stays clamped to the **track**,
                            // and that is not an oversight. It means "where the
                            // finger is", which is what `selectAt` buckets and
                            // what the lean measures from — clamping it to the
                            // thumb's travel instead loses the difference for the
                            // rest of the gesture, so a press in the outer half
                            // of the first segment shifted every later frame by
                            // that much and put the two holds of
                            // `SegmentedThumbDragTest`'s lean case on different
                            // segments.
                            //
                            // The *increase* in how far past the reach the finger
                            // has got, because `pull` is incremental — handing it
                            // a running total is the compounding that made this
                            // control impossible to drag back.
                            val travel = segmentedThumbTravel(trackWidth, options.size)
                            val was = before - before.coerceIn(travel)
                            val now = fingerX - fingerX.coerceIn(travel)
                            band.pull(now - was, endStopTravelPx)
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
                        modifier = Modifier.fillMaxWidth().height(stackedRow),
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
