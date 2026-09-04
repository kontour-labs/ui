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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import io.kontour.ui.a11y.contentColourFor
import io.kontour.ui.a11y.minimumTouchTarget
import io.kontour.ui.input.focusRing
import io.kontour.ui.input.pointerCursor
import io.kontour.ui.interaction.Feedback
import io.kontour.ui.interaction.FeedbackIntent
import io.kontour.ui.interaction.LocalRowInteractionSource
import io.kontour.ui.interaction.LocalRowToggle
import io.kontour.ui.theme.Theme
import io.kontour.ui.theme.invisible
import kotlinx.coroutines.launch

private val TrackWidth = 48.dp
private val TrackHeight = 28.dp
private val ThumbSize = 24.dp
private val ThumbPadding = 2.dp

/** How much wider than round the thumb gets while it is moving. */
private const val ThumbStretch = 1.25f

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
 * cannot — `surfaceSunken` is 1.03:1 against `surface` in light mode, and that
 * is the grey the old note was really about.
 */
@Composable
fun Switch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
) {
    val interactions = interactionSource ?: remember { MutableInteractionSource() }
    val colours = Theme.colours
    val motion = Theme.motion
    val shape = Theme.shapes.control
    val feedback = Feedback
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

    val trackColour by animateColorAsState(
        targetValue = when {
            !enabled -> colours.contentDisabled
            checked -> colours.primary
            else -> colours.outlineStrong
        },
        animationSpec = motion.tweenFast(),
        label = "switchTrack",
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
     * The gesture's running total, mirrored into [fraction] and never drawn from.
     *
     * `Animatable.snapTo` suspends, so a drag delta reaches it through a
     * coroutine; accumulating here first means no delta can be lost to two
     * updates reading the same stale value.
     */
    var dragAccumulator by remember { mutableFloatStateOf(0f) }

    val travel = TrackWidth - ThumbSize - ThumbPadding * 2
    val travelPx = with(LocalDensity.current) { travel.toPx() }

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

    // Squash-and-stretch: the thumb elongates while pressed or in transit —
    // including during the release spring, which is most of the transit.
    val moving = dragging || fraction.isRunning
    val thumbStretch by animateFloatAsState(
        targetValue = if ((pressed || moving) && !motion.reduceMotion) ThumbStretch else 1f,
        animationSpec = motion.springOrTween(motion.springBouncy),
        label = "switchThumbStretch",
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
                            feedback.perform(FeedbackIntent.Selection)
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
                            dragAccumulator = (dragAccumulator + delta / travelPx).coerceIn(0f, 1f)
                            scope.launch { fraction.snapTo(dragAccumulator) }
                        },
                        orientation = Orientation.Horizontal,
                        interactionSource = interactions,
                        onDragStarted = {
                            // From where it *is*, not from where `checked` says
                            // it should be — grabbing a thumb still in flight
                            // used to snap it to an end before it would move.
                            dragAccumulator = fraction.value
                            dragging = true
                        },
                        onDragStopped = {
                            // Whichever half it was let go in. Clearing
                            // `dragging` releases the spring above, which picks
                            // the thumb up from here and carries it to whatever
                            // the caller settles on.
                            val now = fraction.value >= 0.5f
                            dragging = false
                            if (now != checked) {
                                feedback.perform(FeedbackIntent.Selection)
                                dragTarget(now)
                            }
                        },
                    )
                } else {
                    Modifier
                }
            )
            .size(width = TrackWidth, height = TrackHeight)
    ) {
        val trackRadius = size.height / 2f

        // One filled capsule, no outline over it. An outline on a filled track
        // would have to be a third colour to be visible at all, and a switch
        // does not need a third colour.
        drawRoundRect(
            color = trackColour,
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackRadius),
        )

        // Drawn to the box it was *given*, not the box it asked for.
        //
        // `Modifier.size` states a preference, and a `Row` that has run out of
        // width hands out less — or nothing. The thumb used to be clamped with
        // `coerceIn(0f, size.width - stretchedWidth)`, and `coerceIn` **throws**
        // on an inverted range: a switch measured narrower than its own thumb
        // took the frame down with it, from inside draw, where there is nothing
        // to catch it. A switch squeezed to nothing should look squeezed.
        val paddingPx = ThumbPadding.toPx()
        val thumbPx = ThumbSize.toPx().coerceAtMost(size.width)
        val interior = (size.width - paddingPx * 2f).coerceAtLeast(0f)
        val room = (interior - thumbPx).coerceAtLeast(0f)
        val f = fraction.value.coerceIn(0f, 1f)

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
        val left = paddingPx + room * f - grow * f
        val top = (size.height - thumbPx).coerceAtLeast(0f) / 2f

        drawRoundRect(
            color = thumbColour,
            topLeft = Offset(left.coerceIn(0f, (size.width - stretchedWidth).coerceAtLeast(0f)), top),
            size = Size(stretchedWidth, thumbPx),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(thumbPx / 2f),
        )
    }
}
