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
 * A horizontal drag that keeps the pointer once it has it.
 *
 * ### Why not `Modifier.draggable`
 *
 * `draggable(Orientation.Horizontal)` consumes only the horizontal component of
 * each pointer change and lets the vertical part through. Inside a vertical
 * scroller — which is nearly everywhere a slider actually lives — the parent
 * accumulates that vertical movement, passes its own touch slop, claims the
 * gesture, and the child's drag is cancelled underneath it.
 *
 * The symptom is precise and was reported precisely: dragging a slider and
 * letting your finger wander off the track stops the drag *without you lifting
 * it*, and on a desktop the same thing happens if you press, drag, and scroll a
 * little. The finger is still down and the control has stopped listening.
 *
 * So this consumes **every** change for the whole gesture, both axes. The parent
 * never sees movement, never accumulates slop, and never has anything to claim.
 * A slider is an absolute control — it maps a position to a value — so there is
 * no case where a vertical movement part-way through means something else and
 * the parent should get it.
 *
 * ### It claims on the down, not after slop
 *
 * There is no slop to wait for: pressing a slider is already a value change,
 * which is what [onStart] emits. Waiting would give the parent scroller a window
 * in which it could take the gesture first, which is the bug arriving by a
 * different route.
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
 * A control whose handlers only touch snapshot state — the plain [Slider] is one
 * — was never affected, which is exactly why this went unnoticed for as long as
 * it did.
 *
 * @param onStart Called with the down position, in this node's coordinates.
 * @param onDelta Called with the horizontal movement since the last change.
 * @param onEnd Called when the pointer lifts or the gesture is cancelled.
 */
@Composable
internal fun Modifier.horizontalDragOwning(
    enabled: Boolean,
    interactionSource: MutableInteractionSource?,
    scope: CoroutineScope,
    onStart: (Offset) -> Unit,
    onDelta: (Float) -> Unit,
    onEnd: () -> Unit,
): Modifier {
    val currentStart by rememberUpdatedState(onStart)
    val currentDelta by rememberUpdatedState(onDelta)
    val currentEnd by rememberUpdatedState(onEnd)
    return if (!enabled) this else this.pointerInput(enabled, interactionSource) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        down.consume()

        val press = DragInteraction.Start()
        interactionSource?.let { source -> scope.launch { source.emit(press) } }
        currentStart(down.position)

        var cancelled = false
        while (true) {
            val event = awaitPointerEvent()
            val change: PointerInputChange = event.changes.firstOrNull { it.id == down.id } ?: break
            if (!change.pressed) break
            val delta = change.positionChange()
            // Both axes, deliberately. See above: the vertical half is what the
            // parent would otherwise use to take the gesture away.
            change.consume()
            if (delta.x != 0f) currentDelta(delta.x)
        }

        interactionSource?.let { source ->
            scope.launch {
                source.emit(
                    if (cancelled) DragInteraction.Cancel(press) else DragInteraction.Stop(press)
                )
            }
        }
        currentEnd()
    }
    }
}
