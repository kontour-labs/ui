package io.kontour.ui.interaction

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * When [horizontalDragOwning] takes the gesture away from everything else.
 *
 * The two answers are not a preference. They follow from what the control does
 * with a press that never moves, and getting it wrong costs a real gesture in
 * either direction.
 */
internal enum class DragClaim {

    /**
     * On the down, before anything has moved.
     *
     * For a control where pressing is already a value change — a slider jumps
     * its thumb to the finger, so there is nothing to wait and see about. There
     * is also nothing to lose: the press has no other meaning to preserve.
     */
    Press,

    /**
     * On the first pixel of movement, before any touch slop.
     *
     * For a control that is also tappable. A press that never moves is left
     * entirely alone, so the tap underneath still arbitrates normally and still
     * shows its press indication; the moment the finger travels at all, the
     * gesture is a drag and this takes it.
     *
     * "Before any touch slop" is the whole point rather than an optimisation —
     * see the note on the race below.
     */
    Movement,
}

/**
 * A horizontal drag that keeps the pointer once it has it.
 *
 * ### Why not `Modifier.draggable`
 *
 * `draggable(Orientation.Horizontal)` waits for horizontal touch slop before it
 * claims anything. A vertical scroller above it — which is nearly everywhere a
 * control actually lives — is waiting for vertical slop at the same time, and
 * whichever axis crosses first takes the gesture and cancels the other.
 *
 * That is a race, and its outcome is an angle. Measured in a browser at phone
 * size on the built docs site, dragging a control 40px along its axis and a
 * varying distance across it: at 20px across the control tracked, at 40px across
 * it did not, and above that it never did. The threshold is a slope of one.
 * **A drag more than 45° off a control's axis belongs to the scroller.**
 *
 * The symptom is precise and was reported precisely, four rounds running:
 * dragging a control and letting your finger wander stops the drag *without you
 * lifting it*, and on a desktop the same thing happens if you press, drag, and
 * scroll a little. The finger is still down and the control has stopped
 * listening. What makes it hard to see is that the ordinary gesture is fine —
 * once a control has claimed, current Compose consumes the whole change rather
 * than one axis of it, so the scroller can never come back for it. Only the
 * first few pixels decide, and only when they go the wrong way.
 *
 * So this never enters the race. It claims at [DragClaim.Press] or at
 * [DragClaim.Movement], both of which are before any slop, and from then on
 * consumes **every** change in **every** event — both axes, and every pointer on
 * the control. The parent never sees movement, never accumulates slop, and never
 * has anything to claim.
 *
 * ### What it costs, and who should pay it
 *
 * A control that owns its drag cannot be scrolled through. Put a finger on it,
 * drag down, and the page stays where it is.
 *
 * That is the right trade for a control whose drag *is* the control — a slider,
 * a segmented control's thumb — and the wrong one for almost everything else. A
 * `Switch`, a `TabBar` swipe, a `Carousel`: those are things you tap or flick
 * past, and every platform lets a diagonal drag from one scroll the page
 * instead. They keep `draggable` on purpose. `DragOwnershipTest`'s KDoc has the
 * inventory and the reasoning per component.
 *
 * ### The handlers are read, not captured
 *
 * `pointerInput` is keyed on [enabled] and [interactionSource], and neither of
 * those changes over a control's life — which is the point, because restarting
 * the node mid-gesture cancels the gesture. It also means the block runs *once*
 * and keeps whatever lambdas it closed over on the first composition, forever.
 *
 * That is a stale capture with a real symptom, reported as "move one end of a
 * range slider, then the other, and the first one goes back where it started".
 * `RangeSlider`'s emit rebuilds the pair from its `value` parameter on the first
 * emission of each gesture, and the `value` inside a first-composition lambda is
 * the range the slider was born with. Every gesture after the first undid the
 * one before it.
 *
 * So the three handlers go through [rememberUpdatedState] and are read at call
 * time. The node still never restarts, and it still calls the current lambdas.
 * A control whose handlers only touch snapshot state — the plain
 * [io.kontour.ui.components.selection.Slider] is one — was never affected,
 * which is exactly why this went unnoticed for as long as it did.
 *
 * @param claimsOn Whether the down itself is the gesture or only the movement
 *   after it. See [DragClaim]; it decides whether a tap survives.
 * @param onStart Called with the position the drag is taken at, in this node's
 *   coordinates — the down for [DragClaim.Press], the first move for
 *   [DragClaim.Movement].
 * @param onDelta Called with the horizontal movement since the last change.
 * @param onEnd Called when the pointer lifts or the gesture is cancelled. Not
 *   called at all for a press that never became a drag.
 */
@Composable
internal fun Modifier.horizontalDragOwning(
    enabled: Boolean,
    interactionSource: MutableInteractionSource?,
    scope: CoroutineScope,
    claimsOn: DragClaim = DragClaim.Press,
    onStart: (Offset) -> Unit,
    onDelta: (Float) -> Unit,
    onEnd: () -> Unit,
): Modifier {
    val currentStart by rememberUpdatedState(onStart)
    val currentDelta by rememberUpdatedState(onDelta)
    val currentEnd by rememberUpdatedState(onEnd)
    return if (!enabled) this else this.pointerInput(enabled, interactionSource, claimsOn) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)

            var press: DragInteraction.Start? = null
            fun claim(at: Offset) {
                val started = DragInteraction.Start()
                press = started
                interactionSource?.let { source -> scope.launch { source.emit(started) } }
                currentStart(at)
            }

            if (claimsOn == DragClaim.Press) {
                down.consume()
                claim(down.position)
            }

            // A gesture that ends without an up is a cancelled one — the pointer
            // was taken out from under us, the node was detached mid-drag, or the
            // platform withdrew the finger. Emitting `Stop` for that would tell
            // every ripple and every press state that the user finished, which is
            // the opposite of what happened.
            var cancelled = false
            while (true) {
                val event = awaitPointerEvent()
                val change: PointerInputChange? = event.changes.firstOrNull { it.id == down.id }
                if (change == null) {
                    cancelled = true
                    break
                }
                if (!change.pressed) {
                    // The up as well, but only once this is genuinely a drag.
                    // Leaving it unconsumed let a clickable parent count the
                    // whole gesture as a tap on release; consuming it for a
                    // press that never moved would eat the tap this deliberately
                    // stayed out of the way of.
                    if (press != null) change.consume()
                    break
                }
                val delta = change.positionChange()
                if (press == null) {
                    // Nothing has happened yet, so nothing is claimed and the
                    // event is left for whoever else wants it.
                    if (delta == Offset.Zero) continue
                    claim(change.position)
                }
                // Every change, both axes. See above: the cross-axis half is what
                // the scroller would otherwise use to win the race, and a second
                // finger on the same control is a second way to lose it.
                event.changes.forEach { it.consume() }
                if (delta.x != 0f) currentDelta(delta.x)
            }

            press?.let { started ->
                interactionSource?.let { source ->
                    scope.launch {
                        source.emit(
                            if (cancelled) DragInteraction.Cancel(started) else DragInteraction.Stop(started)
                        )
                    }
                }
                currentEnd()
            }
        }
    }
}
