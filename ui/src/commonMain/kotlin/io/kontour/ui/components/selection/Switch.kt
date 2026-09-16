package io.kontour.ui.components.selection

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import io.kontour.ui.interaction.rememberTapFeedback
import io.kontour.ui.a11y.contentColourFor
import io.kontour.ui.a11y.minimumTouchTarget
import io.kontour.ui.input.focusRing
import io.kontour.ui.input.pointerCursor
import io.kontour.ui.interaction.rememberDetentTicker
import io.kontour.ui.interaction.rememberRubberBand
import io.kontour.ui.interaction.FeedbackIntent
import io.kontour.ui.interaction.LocalRowInteractionSource
import io.kontour.ui.interaction.LocalRowToggle
import io.kontour.ui.theme.Theme
import io.kontour.ui.theme.invisible
import kotlin.math.abs
import kotlinx.coroutines.launch

private val TrackWidth = 48.dp
private val TrackHeight = 28.dp
private val ThumbSize = 24.dp
private val ThumbPadding = 2.dp

/** How much wider than round the thumb gets while it is moving. */
private const val ThumbStretch = 1.25f

/**
 * How much of itself the thumb gives up at an end stop.
 *
 * A sixth, which is plainly visible on a 24dp thumb and still leaves something
 * recognisably round. More reads as the thumb deflating.
 */
private const val ThumbSquash = 0.16f

/**
 * The speed, in track-fractions per second, at which the stretch is full.
 *
 * `springSnappy` carries the thumb across its whole travel in about a fifth of
 * a second, so it peaks somewhere near six fractions per second — this is a
 * little under that, so the stretch reaches its full extent for the middle of
 * a flip rather than only grazing it, and is proportional either side.
 *
 * A number rather than a token because it is not a design decision anybody
 * would tune independently: it is the units conversion between the position
 * spring already chosen above and [ThumbStretch] already chosen here.
 */
private const val StretchAtSpeed = 5f

/**
 * A switch.
 *
 * ```
 * Switch(checked = liveVehicles, onCheckedChange = viewModel::setLiveVehicles)
 * ```
 *
 * Use a switch for a setting that takes effect *immediately*, and a
 * [Checkbox] for one that is part of a form and takes effect on submit. The
 * distinction matters: a user who flips a switch expects the thing to have
 * happened, and a user who ticks a box expects to press Save.
 *
 * The thumb stretches as it travels — wider mid-flight, round at rest — the way
 * a physical toggle would if it had any give in it. It costs one animated value
 * and it is what stops the control feeling like a rectangle sliding in a slot.
 * Under reduced motion the thumb simply moves.
 *
 * The track is [io.kontour.ui.theme.ColourScheme.primary] when on and
 * [io.kontour.ui.theme.ColourScheme.outlineStrong] when off — **filled** in both
 * states, and the thumb is the same colour throughout, so the only thing that
 * changes is the track behind it. That is a switch: one moving part.
 *
 * It used to be an unfilled, stroked capsule when off, on the reasoning that a
 * grey track sits too close in tone to the surfaces it is toggled on top of to
 * read as a distinct control. The reasoning was right and the conclusion was
 * not — the answer is not *no* fill, it is a fill dark enough. `outlineStrong`
 * is the token that exists for exactly this: the boundary of anything
 * interactive, held at the 3:1 WCAG 1.4.11 asks for. It clears that against
 * `surface` and `surfaceRaised` in both schemes, which the surface ramp itself
 * cannot: `surfaceSunken` is 1.08:1 against `surface` in light mode, and even
 * `surfaceIndicator` — the fill tuned so a segmented thumb shows without a
 * border — only reaches 1.59, and only in dark. That ramp separates fills from
 * each other; it does not bound controls.
 */
@Composable
fun Switch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
) {
    val tap = rememberTapFeedback()
    val crossing = rememberDetentTicker(FeedbackIntent.DragThreshold)
    val interactions = interactionSource ?: remember { MutableInteractionSource() }
    val colours = Theme.colours
    val motion = Theme.motion
    // `pill` rather than `control`, and the reason is the thumb below.
    //
    // The thumb is drawn at `thumbPx / 2f` — half its own height, with no cap,
    // because a `drawRoundRect` has no token to consult. The track was reading
    // `Theme.shapes.control`, which *is* capped, so a theme that lowers
    // `capsuleCap` lowers one of the two and not the other: at `capsuleCap =
    // 10.dp` the 28dp track came out at 10 against a 24dp thumb at 12, where
    // concentricity wants `thumb + ThumbPadding` = 14. Reported as switches no
    // longer being concentric, and it was exactly that.
    //
    // `pill` is the same `CapsuleCornerSize` with the cap taken off, so the two
    // now track each other by construction at every `capsuleCap` a theme picks.
    // In the default theme nothing moves: `CapsuleCap` is 18dp and half of 28 is
    // 14, so `control` was never reaching the cap here anyway.
    val shape = Theme.shapes.pill
    val scope = rememberCoroutineScope()

    // A switch inside a `SelectionRow` has no callback of its own — the row owns
    // the tap — so its own source never sees a press and the thumb never
    // stretched when the row was tapped. Borrow the row's, but only when this
    // switch is genuinely a passenger: an explicit source or a callback of its
    // own both mean it is the target.
    val row = LocalRowInteractionSource.current
    val pressSource: InteractionSource =
        if (onCheckedChange == null && interactionSource == null && row != null) row else interactions
    val pressed by pressSource.collectIsPressedAsState()

    /**
     * The track's two ends. **The travel between them is the thumb's position**,
     * read in the draw block, not a tween of its own.
     *
     * It used to be one `animateColorAsState` keyed on [checked], and that is a
     * fixed-duration crossfade fired by the commit. On a tap the two happened to
     * roughly coincide; on a *drag* they could not. The thumb follows the finger
     * one to one, so a slow drag holds it anywhere it likes — and the track
     * behind it stayed one colour, then repainted itself over 150ms the instant
     * the midpoint went by, whatever the finger was doing. Two events for one
     * movement, which is the same mistake the thumb's stretch already had taken
     * out of it.
     *
     * Interpolated from the position instead, a drag half way across is a track
     * half way across, and a tap crossfades on the position spring's own timing
     * because that is what is driving it. `Color.lerp` blends through Oklab, so
     * the midpoint of grey to a saturated primary is not the muddy step that
     * interpolating sRGB channels gives.
     *
     * What still animates here is the one axis the position cannot express:
     * enabled to disabled, where both ends move at once and the thumb does not.
     */
    val trackOff by animateColorAsState(
        targetValue = if (enabled) colours.outlineStrong else colours.contentDisabled,
        animationSpec = motion.tweenFast(),
        label = "switchTrackOff",
    )
    val trackOn by animateColorAsState(
        targetValue = if (enabled) colours.primary else colours.contentDisabled,
        animationSpec = motion.tweenFast(),
        label = "switchTrackOn",
    )
    // The thumb does not change colour, in either direction. A switch has one
    // moving part and one thing that changes behind it; recolouring the thumb as
    // well makes the flip read as two events.
    val thumbColour by animateColorAsState(
        targetValue = if (enabled) colours.onPrimary else contentColourFor(colours.contentDisabled),
        animationSpec = motion.tweenFast(),
        label = "switchThumb",
    )

    /**
     * What a drag on this switch would toggle, or `null` if nothing.
     *
     * Its own callback first; failing that, the row's. A switch inside a
     * `SelectionRow` is handed `onCheckedChange = null` because the row owns the
     * tap — but a thumb dragged across its track is not a tap, and it means one
     * unambiguous thing. See [LocalRowToggle].
     */
    val dragTarget = onCheckedChange ?: LocalRowToggle.current

    /**
     * Where the thumb is: `0f` off, `1f` on. **The only source of truth.**
     *
     * It used to be two — a drag fraction the finger wrote to, and an
     * `animateDpAsState` keyed on [checked] — and they were never reconciled.
     * The animation sat parked at the *pre-drag* end for the whole gesture, so
     * the frame after the finger lifted drew the thumb back where it started:
     * a committed drag jumped backwards by up to the full travel before setting
     * off again, and a drag that did not carry far enough teleported home with
     * no animation at all, because its target had never changed.
     *
     * One `Animatable` cannot do that. The drag snaps it; the release springs it
     * *from wherever the finger left it* to wherever the value lands — including
     * back, which is now a spring rather than a jump cut.
     *
     * Held as a fraction rather than as pixels so the release threshold is the
     * middle of the travel whatever the density, and so the drawn position needs
     * no second source of truth about how far the thumb can go.
     */
    val fraction = remember { Animatable(if (checked) 1f else 0f) }

    /** True between `onDragStarted` and `onDragStopped`. Suspends the spring. */
    var dragging by remember { mutableStateOf(false) }

    /**
     * Which side the *gesture* has decided on, which is not [checked].
     *
     * The switch used to report on release: you dragged the thumb the whole way
     * across, nothing changed under your finger, and the state flipped once you
     * let go. That reads as a control that does not answer a drag — which is
     * how it was reported — and it is also the wrong model. A physical toggle
     * goes over at the midpoint and is over from then on, whether or not you
     * have lifted your hand.
     *
     * So the crossing is the commit. This is what the crossing is measured
     * against, kept locally rather than read from [checked], because [checked]
     * comes back through the caller and a recomposition: reading it here would
     * let one gesture cross the same midpoint twice before the answer arrived.
     */
    var committed by remember { mutableStateOf(checked) }

    /**
     * The gesture's running total, mirrored into [fraction] and never drawn from.
     *
     * `Animatable.snapTo` suspends, so a drag delta reaches it through a
     * coroutine; accumulating here first means no delta can be lost to two
     * updates reading the same stale value.
     */
    var dragAccumulator by remember { mutableFloatStateOf(0f) }

    val travel = TrackWidth - ThumbSize - ThumbPadding * 2
    val travelPx = with(LocalDensity.current) { travel.toPx() }

    /**
     * What a drag pushing past either end does instead of nothing.
     *
     * The accumulator is clamped to `0..1`, so a finger that carries on past the
     * end used to be spending pixels on a control that had stopped answering.
     * The rejected part goes here and comes out as the thumb squashing in the
     * direction it is being pushed, then springing back on release.
     *
     * **Scaled to the thumb, not to the travel**, which is the thing
     * `SliderDefaults.DetentPull` got wrong when it was tried here: a switch's
     * track is 20dp, so anything sized to the travel is either invisible or
     * throws the thumb off the finger. The limit below is the thumb's own
     * maximum deformation, so a 20dp track and a 300dp one both give a thumb
     * that visibly compresses by the same proportion of itself.
     */
    val band = rememberRubberBand()

    /** The thumb's own maximum deformation, which is what the squash is scaled to. */
    val thumbSquashPx = with(LocalDensity.current) { ThumbSize.toPx() } * (ThumbStretch - 1f)

    // Springs to wherever `checked` now is, starting from wherever the thumb now
    // is. Keyed on `dragging` as well as on `checked`, so it also runs when a
    // drag ends without changing anything — a short drag, or a caller that
    // declined the change — which is the case that used to teleport.
    LaunchedEffect(checked, dragging) {
        if (dragging) return@LaunchedEffect
        val target = if (checked) 1f else 0f
        if (fraction.value != target) {
            fraction.animateTo(target, motion.springOrTween(motion.springSnappy))
        }
    }

    /**
     * The half of the stretch that a *press* is responsible for.
     *
     * A press is a discrete event with no velocity of its own, so this half is
     * still a spring. It covers a finger resting on the thumb and a drag, where
     * the position is snapped rather than animated and has no velocity to read.
     */
    val pressStretch by animateFloatAsState(
        targetValue = if ((pressed || dragging) && !motion.reduceMotion) ThumbStretch else 1f,
        animationSpec = motion.springOrTween(motion.springBouncy),
        label = "switchThumbPress",
    )

    Canvas(
        modifier = modifier
            .minimumTouchTarget()
            .focusRing(interactions, shape)
            .then(
                if (onCheckedChange != null) {
                    Modifier.pointerCursor(enabled = enabled).toggleable(
                        value = checked,
                        onValueChange = {
                            // A tapped switch reports like the checkbox beside
                            // it. It used to be silent on the argument that a
                            // thumb crossing a 20dp track is obvious enough —
                            // which is true of the switch alone and wrong in a
                            // settings list, where the control above it ticks
                            // and this one does not.
                            tap()
                            onCheckedChange(it)
                        },
                        enabled = enabled,
                        role = Role.Switch,
                        interactionSource = interactions,
                        indication = null,
                    )
                } else {
                    // Not interactive, but still *state*. A switch handed a null
                    // callback is showing what a row decided, and if it says
                    // nothing a screen reader reads the row as a button with a
                    // name and no on or off. The row publishes this too when it
                    // is `toggleable`; the same value merged twice is harmless,
                    // and a `SettingRow` — which is `clickable`, not
                    // `toggleable` — publishes nothing at all without it.
                    Modifier.semantics {
                        role = Role.Switch
                        toggleableState = ToggleableState(checked)
                    }
                }
            )
            // The drag goes *after* the toggle, so the tap is arbitrated first
            // and only a pointer that travels past the slop becomes a drag. A
            // switch is small enough that a tap and a short drag are the same
            // gesture from the user's side, and this is the order that keeps
            // them from fighting.
            .then(
                if (dragTarget != null && enabled) {
                    Modifier.draggable(
                        state = rememberDraggableState { delta ->
                            // A finger coming back closes the stretch it opened
                            // before the thumb itself moves again, or one
                            // gesture reads as two motions.
                            val offered = delta - band.payBack(delta)
                            val raw = dragAccumulator + offered / travelPx
                            dragAccumulator = raw.coerceIn(0f, 1f)
                            if (!motion.reduceMotion) {
                                band.pull(
                                    (raw - dragAccumulator) * travelPx,
                                    thumbSquashPx,
                                )
                            }

                            val side = dragAccumulator >= 0.5f
                            val crossed = side != committed
                            if (crossed) {
                                committed = side
                                // The one thing here the eye is not already
                                // being told: what letting go will do has just
                                // changed. `DragThreshold` is the intent for
                                // exactly that.
                                //
                                // Through the ticker rather than performed
                                // directly, because a midpoint is a two-sided
                                // threshold — the same guard, doing the same
                                // job, and it shares the rate floor with every
                                // other light haptic instead of keeping its own.
                                crossing.at(if (side) 1f else 0f)
                                dragTarget(side)
                            }

                            // The thumb tracks the finger, one to one.
                            //
                            // It briefly did not: it sat on the detent it had
                            // committed to and was pulled part of the way
                            // toward the finger by `SliderDefaults.DetentPull`,
                            // which is the ticked slider's mechanism and is
                            // right there and wrong here. A slider's thumb
                            // crosses most of a screen, so travelling 45% of
                            // the way reads as strain. This track is **20dp**.
                            // The same ratio moves the thumb 9dp while the
                            // finger moves 20 and then jumps it when the
                            // midpoint goes over, which does not read as
                            // resistance at that size — it reads as a control
                            // that is not keeping up, and then a glitch.
                            //
                            // What survives from that attempt is the part that
                            // was about behaviour rather than feel: the commit
                            // below happens at the midpoint rather than on
                            // release.
                            scope.launch { fraction.snapTo(dragAccumulator) }
                        },
                        orientation = Orientation.Horizontal,
                        interactionSource = interactions,
                        onDragStarted = {
                            // From where it *is*, not from where `checked` says
                            // it should be — grabbing a thumb still in flight
                            // used to snap it to an end before it would move.
                            dragAccumulator = fraction.value
                            committed = checked
                            dragging = true
                            // Armed at the side the thumb starts on, so the
                            // first crossing *reports* rather than being taken
                            // as the ticker's starting index. Every other
                            // `DetentTicker` is armed by a gesture that begins
                            // on a detent and reports the ones after it; this
                            // one has exactly one crossing to give, so arming it
                            // on that crossing would silence the whole gesture.
                            crossing.at(if (checked) 1f else 0f)
                        },
                        onDragStopped = {
                            // Nothing to report here any more: the midpoint did
                            // it, possibly several times if the finger went back
                            // and forth. Clearing `dragging` releases the spring
                            // above, which picks the thumb up from wherever it
                            // was left and carries it to whatever the caller
                            // settled on — including back, if the caller
                            // declined the change.
                            dragging = false
                            crossing.reset()
                            // Bouncy rather than snappy, matching `pressStretch`
                            // above: a switch is small enough that a little
                            // overshoot reads as rubber rather than as wobble.
                            band.release(motion.springOrTween(motion.springBouncy))
                        },
                    )
                } else {
                    Modifier
                }
            )
            .size(width = TrackWidth, height = TrackHeight)
    ) {
        val f = fraction.value.coerceIn(0f, 1f)

        // One filled capsule, no outline over it. An outline on a filled track
        // would have to be a third colour to be visible at all, and a switch
        // does not need a third colour.
        //
        // **A shape's own outline, not a `drawRoundRect`.** The shape scale is a
        // squircle scale, and this was the one pill in the library drawing plain
        // circular corners while naming the same token as the buttons beside it
        // — reported as switches not having "that same smoothing factor as
        // things like buttons which are pill-shaped", which is exactly what it
        // was. `SquircleShape` saturates the short edge of a capsule and eases
        // into the long one, so the change is at the ends of a 48dp track and it
        // is the thing that makes a switch and a `Button` read as one system.
        //
        // Still a squircle now that it reads `Theme.shapes.pill` rather than
        // `Theme.shapes.control` — `pill` is one too. See `shape` above for why
        // the uncapped one is the right of the two.
        //
        // It costs a path lookup per frame and no path *building*: the track is
        // a fixed 48x28 and the shape is one remembered instance, so every call
        // after the first hits `SquircleShape`'s ring cache. See
        // `SquirclePathCacheTest`.
        drawOutline(
            outline = shape.createOutline(size, layoutDirection, this),
            color = lerp(trackOff, trackOn, f),
        )

        // Drawn to the box it was *given*, not the box it asked for.
        //
        // `Modifier.size` states a preference, and a `Row` that has run out of
        // width hands out less — or nothing. The thumb used to be clamped with
        // `coerceIn(0f, size.width - thumbWidth)`, and `coerceIn` **throws**
        // on an inverted range: a switch measured narrower than its own thumb
        // took the frame down with it, from inside draw, where there is nothing
        // to catch it. A switch squeezed to nothing should look squeezed.
        val paddingPx = ThumbPadding.toPx()
        val thumbPx = ThumbSize.toPx().coerceAtMost(size.width)
        val interior = (size.width - paddingPx * 2f).coerceAtLeast(0f)
        val room = (interior - thumbPx).coerceAtLeast(0f)

        /**
         * The other half of the stretch, read from the thumb's own speed.
         *
         * It used to be a second spring driven by `dragging ||
         * fraction.isRunning` — a boolean about whether the position was
         * animating, which structurally cannot overlap the animation it
         * describes. So a tap played three separate movements: the stretch
         * spring grew the thumb, *then* the position spring carried it across,
         * *then* the stretch spring shrank it back. Three phases for one
         * gesture, and the middle one is the only one anybody asked for.
         *
         * Taken from the velocity instead, the stretch **is** the travel: it
         * grows as the thumb accelerates away, peaks where the thumb is moving
         * fastest, and is back to nothing as it arrives — one movement, with no
         * second animation to get out of step with. Read here rather than in
         * composition because `fraction.value` is already read here and changes
         * every frame the thumb moves, so this costs no invalidation of its own.
         */
        val speed = (abs(fraction.velocity) / StretchAtSpeed).coerceIn(0f, 1f)
        val travelStretch = if (motion.reduceMotion) 1f else 1f + (ThumbStretch - 1f) * speed
        val thumbStretch = maxOf(pressStretch, travelStretch)

        // The stretch grows *into the padding it has room for*, split between
        // the two sides in proportion to how much room each has.
        //
        // A 22dp thumb stretched to 27.5dp in a 48dp track cannot keep 3dp clear
        // at both ends and stay centred on the finger, so the old code let the
        // leading edge run into the track's wall and clamped it there: the gap
        // it was supposed to hold went to **zero** for the last eighth of an
        // off-to-on drag, and the thumb stopped tracking the finger while it did
        // it. Splitting the growth by the room available instead keeps both gaps
        // at exactly [ThumbPadding] wherever the thumb is, with no clamp, no
        // discontinuity — and at either end, where all the spare room is behind
        // it, all the growth goes behind it too. Which is the trailing stretch
        // the give was always meant to be.
        val stretchedWidth = (thumbPx * thumbStretch).coerceAtMost(maxOf(interior, thumbPx))
        val grow = stretchedWidth - thumbPx
        val reached = paddingPx + room * f - grow * f

        // **The end stop compresses the thumb; it does not lengthen it.**
        //
        // The first version fed the band into `thumbStretch` above, on the
        // reasoning that all the spare room at either end is behind the thumb so
        // the growth would go there by itself. It does — and growing *backwards*
        // away from the wall is a stretch, which is the opposite of what pushing
        // into a wall looks like. It read as the thumb getting fat.
        //
        // A squash is shorter along the axis of the push with the leading edge
        // pinned, so the thumb visibly shortens against the end it has run into
        // and springs back out of it. Applied to the width here rather than to
        // the stretch above, because it has to move the edge that is *not*
        // against the wall and the stretch has no way to say which one that is.
        val squeeze = if (thumbSquashPx <= 0f) {
            0f
        } else {
            (abs(band.offset) / thumbSquashPx).coerceIn(0f, 1f) * ThumbSquash
        }
        val thumbWidth = stretchedWidth * (1f - squeeze)
        // Pinned against whichever end was pushed into: on the right, the lost
        // width comes off the left edge.
        val left = reached + if (band.offset > 0f) stretchedWidth - thumbWidth else 0f
        val top = (size.height - thumbPx).coerceAtLeast(0f) / 2f

        // **The thumb stays a circular capsule, and that is the shape scale's own
        // answer rather than an exception to it.** At rest it is a 24dp square at
        // capsule radius, which saturates on *both* edges — so `SquircleShape`
        // returns a true circle for it, the same as an `IconButton`, an `Avatar`
        // or a radio ring. There is nothing to smooth and nothing to match.
        //
        // In flight it is 24 by 30 and a squircle would have a little room on the
        // long edge, worth about half a pixel. It would also be a fresh path
        // every frame, under a finger, past every cache: `SliderThumb` refused
        // that trade for the same reason and at the same size, and refusing it
        // twice is more consistent than paying for it once.
        drawRoundRect(
            color = thumbColour,
            topLeft = Offset(left.coerceIn(0f, (size.width - thumbWidth).coerceAtLeast(0f)), top),
            size = Size(thumbWidth, thumbPx),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(thumbPx / 2f),
        )
    }
}
