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
import androidx.compose.ui.input.pointer.util.VelocityTracker
import kotlin.math.abs
import kotlin.coroutines.cancellation.CancellationException
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

    /**
     * Once the finger has moved a little, and only if it moved mostly along the
     * control's axis — otherwise not at all.
     *
     * For a control that lives inside a scroller running the other way and has to
     * share the finger with it: a swipe row in a list. [Movement] would take every
     * gesture that starts on the row, so the list could never be scrolled from one;
     * `draggable` waits for a full touch slop on its own axis while the list waits
     * for one on the other, and a thumb's arc loses that race to the list more
     * often than not — which was the swipe being "way too hard" on iOS.
     *
     * This decides early and by direction. Movement is gathered, unconsumed, until
     * it is [DirectionDecisionShare] of the touch slop — well before the list can
     * claim — and then the gesture is the control's if it is within 45° of the
     * control's axis, and the list's if it is not. The travel gathered while
     * deciding is handed on with the claim, so none of it is lost.
     */
    Direction,
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
 * ### It happened again, and the second time says what the first could not
 *
 * `ColourPicker` lost a hue the same way: set one on the track, press in the
 * spectrum, and the hue reverted to the colour the picker was born with — then
 * the rest of that gesture faithfully continued from the reverted value. One
 * bad emission, always on the press, always the birth value.
 *
 * **The `rememberUpdatedState` here could not prevent it**, and that is the part
 * worth understanding. It stores the latest *instance* it is handed. The picker
 * handed it `onStart = ::report` — a reference to a *local* function — which the
 * compiler memoizes with no captures at the reference site, so it is the same
 * first-composition instance on every recomposition. This local was faithfully
 * storing a stale closure.
 *
 * A lambda literal would not have been: the compiler records the transitive
 * captures of a lambda's body, including the parameters of any local function it
 * calls, so `{ report(it) }` is rebuilt when they change. The two spellings read
 * as the same thing. `check-components.py`'s rule 25 now bans the one that is
 * not, because a reviewer cannot see a missing `rememberUpdatedState` and can see
 * a `::`.
 *
 * The honest summary of both rounds: **a drag handler outlives the composition
 * that built it, so anything it reads from a parameter has to be read live.**
 * This local does that for the handlers; each handler has to do it for its own.
 *
 * @param claimsOn Whether the down itself is the gesture or only the movement
 *   after it. See [DragClaim]; it decides whether a tap survives.
 * @param onStart Called with the position the drag is taken at, in this node's
 *   coordinates — the down for [DragClaim.Press], the first move for
 *   [DragClaim.Movement].
 * @param onDelta Called with the horizontal movement since the last change.
 * @param onEnd Called when the pointer lifts or the gesture is cancelled. Not
 *   called at all for a press that never became a drag.
 * @param accepts Whether a gesture starting here belongs to this node at all.
 *   See [ownedDrag]; a horizontal owner rarely needs it, because the thing it is
 *   racing usually scrolls the other way.
 */
@Composable
internal fun Modifier.horizontalDragOwning(
    enabled: Boolean,
    interactionSource: MutableInteractionSource?,
    scope: CoroutineScope,
    claimsOn: DragClaim = DragClaim.Press,
    accepts: (Offset) -> Boolean = { true },
    onStart: (Offset) -> Unit,
    onDelta: (Float) -> Unit,
    onEnd: () -> Unit,
    /**
     * Called with the horizontal velocity, in pixels a second, when the finger
     * lifts at the end of a drag — before [onEnd], and not for a cancelled one.
     */
    onRelease: (Float) -> Unit = {},
): Modifier {
    val currentDelta by rememberUpdatedState(onDelta)
    val currentRelease by rememberUpdatedState(onRelease)
    return ownedDrag(
        enabled, interactionSource, scope, claimsOn, accepts, onStart, onEnd,
        onRelease = { velocity -> currentRelease(velocity.x) },
        along = Axis.Horizontal,
    ) { delta ->
        if (delta.x != 0f) currentDelta(delta.x)
    }
}

/** Which way a drag with a [DragClaim.Direction] is looking for. */
internal enum class Axis { Horizontal, Vertical, Both }

/**
 * The same drag, down the other axis.
 *
 * **[accepts] is not optional in practice here, and that is the difference.** A
 * horizontal owner races a *vertical* scroller, so the two want different
 * directions and taking the gesture at 45 degrees is a judgement about slope.
 * A vertical owner inside a vertical scroller has no such tell: every drag it
 * could take is a drag the page wanted, and a control that claimed them all
 * would make the page unscrollable wherever it sits.
 *
 * So the question becomes *where the finger went down* rather than *which way it
 * went*, and a vertical owner answers it by owning a handle rather than a
 * surface. `SegmentedControl` stacked is the case this was written for: a drag
 * that starts on the thumb carries it, and a drag that starts anywhere else is
 * the page's, which is what the control's own comment used to give as the reason
 * it had no vertical drag at all.
 */
@Composable
internal fun Modifier.verticalDragOwning(
    enabled: Boolean,
    interactionSource: MutableInteractionSource?,
    scope: CoroutineScope,
    claimsOn: DragClaim = DragClaim.Press,
    accepts: (Offset) -> Boolean = { true },
    onStart: (Offset) -> Unit,
    onDelta: (Float) -> Unit,
    onEnd: () -> Unit,
): Modifier {
    val currentDelta by rememberUpdatedState(onDelta)
    return ownedDrag(enabled, interactionSource, scope, claimsOn, accepts, onStart, onEnd, along = Axis.Vertical) { delta ->
        if (delta.y != 0f) currentDelta(delta.y)
    }
}

/**
 * The same drag, both axes.
 *
 * For a control that can be sent away in more than one direction — a toast,
 * which dismisses toward the edge it is anchored to *or* sideways. Everything in
 * [horizontalDragOwning]'s note applies unchanged, including the cost: a control
 * that owns its drag cannot be scrolled through.
 *
 * The horizontal one is the common case and keeps the shorter name; this exists
 * because the loop underneath was already consuming both axes and forwarding
 * one of them.
 */
@Composable
internal fun Modifier.freeDragOwning(
    enabled: Boolean,
    interactionSource: MutableInteractionSource?,
    scope: CoroutineScope,
    claimsOn: DragClaim = DragClaim.Press,
    /** See [horizontalDragOwning]: whether a gesture starting here is this node's. */
    accepts: (Offset) -> Boolean = { true },
    onStart: (Offset) -> Unit,
    onDelta: (Offset) -> Unit,
    onEnd: () -> Unit,
    /** The velocity the finger lifted with, before [onEnd]. See [horizontalDragOwning]. */
    onRelease: (Offset) -> Unit = {},
): Modifier {
    val currentDelta by rememberUpdatedState(onDelta)
    val currentRelease by rememberUpdatedState(onRelease)
    return ownedDrag(
        enabled, interactionSource, scope, claimsOn, accepts, onStart, onEnd,
        onRelease = { currentRelease(it) },
        along = Axis.Both,
    ) { delta ->
        if (delta != Offset.Zero) currentDelta(delta)
    }
}

/**
 * The loop both of the above are.
 *
 * Takes the whole [Offset] and lets the caller decide what to do with it, which
 * is the only difference between them.
 */
@Composable
private fun Modifier.ownedDrag(
    enabled: Boolean,
    interactionSource: MutableInteractionSource?,
    scope: CoroutineScope,
    claimsOn: DragClaim,
    accepts: (Offset) -> Boolean,
    onStart: (Offset) -> Unit,
    onEnd: () -> Unit,
    onRelease: (Offset) -> Unit = {},
    along: Axis = Axis.Both,
    onDelta: (Offset) -> Unit,
): Modifier {
    val currentStart by rememberUpdatedState(onStart)
    val currentDelta by rememberUpdatedState(onDelta)
    val currentEnd by rememberUpdatedState(onEnd)
    val currentRelease by rememberUpdatedState(onRelease)
    val currentAccepts by rememberUpdatedState(accepts)

    return if (!enabled) this else this.pointerInput(enabled, interactionSource, claimsOn) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)

            // **Declined before anything is consumed, which is the whole point of
            // asking here.** Everything below this line takes both axes off
            // whoever else wanted them, so a node that is not going to use a
            // gesture has to say so before the first move rather than by ignoring
            // the deltas — an ignored delta is still a consumed one, and the
            // scroller underneath sees a finger that moved nothing.
            //
            // Returning leaves the down unconsumed and the gesture untouched.
            // `awaitEachGesture` waits for every pointer to lift before it starts
            // another, so the rest of this one is not picked up again here.
            if (!currentAccepts(down.position)) return@awaitEachGesture

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
            // From the claim onward, for the release velocity.
            val velocity = VelocityTracker()
            // What a `Direction` claim has seen while it was still deciding.
            var gathered = Offset.Zero
            val decideAt = viewConfiguration.touchSlop * DirectionDecisionShare
            try {
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
                        if (press != null) {
                            change.consume()
                            velocity.addPosition(change.uptimeMillis, change.position)
                            currentRelease(velocity.calculateVelocity().let { Offset(it.x, it.y) })
                        }
                        break
                    }
                    var delta = change.positionChange()
                    if (press == null) {
                        // Nothing has happened yet, so nothing is claimed and the
                        // event is left for whoever else wants it.
                        if (delta == Offset.Zero) continue
                        if (claimsOn == DragClaim.Direction) {
                            // Somebody nearer the pointer, or a scroller that has
                            // already made up its mind, has the gesture.
                            if (change.isConsumed) return@awaitEachGesture
                            gathered += delta
                            if (gathered.getDistance() < decideAt) continue
                            val mostlyAlong = when (along) {
                                Axis.Horizontal -> abs(gathered.x) >= abs(gathered.y)
                                Axis.Vertical -> abs(gathered.y) >= abs(gathered.x)
                                Axis.Both -> true
                            }
                            // The other way: the scroller's, all of it.
                            if (!mostlyAlong) return@awaitEachGesture
                            // Ours, including the travel spent deciding.
                            delta = gathered
                        }
                        claim(change.position)
                        velocity.addPosition(change.uptimeMillis, change.position)
                    } else {
                        velocity.addPosition(change.uptimeMillis, change.position)
                    }
                    // Every change, both axes. See above: the cross-axis half is what
                    // the scroller would otherwise use to win the race, and a second
                    // finger on the same control is a second way to lose it.
                    event.changes.forEach { it.consume() }
                    currentDelta(delta)
                }
            } catch (stopped: CancellationException) {
                // **The coroutine itself going away, which the loop cannot see.**
                //
                // The `break` above covers a gesture that ends badly. It does not
                // cover this function not being here any more, and Compose has a
                // reason to do that which has nothing to do with pointers:
                // `SuspendingPointerInputModifierNode` resets its handler when
                // the node's `Density` changes, and a `Density` carries the font
                // scale. So a **Text size screen** — the one screen in an app
                // where using a control changes the type scale the control is
                // laid out with — cancels this mid-drag, every time, while the
                // finger is still down.
                //
                // Reported as controls going weird when the text size changes,
                // "but only sometimes", with a picture of a segmented control's
                // thumb a fifth too wide and sitting between two segments. That
                // is a control whose `dragging` is still true: the end of the
                // gesture was the line after the loop, and the loop never
                // returned. `StrandedThumbTest` is the reproduction.
                //
                // What this cannot do is carry the gesture on. The handler
                // restarts waiting for a fresh `awaitFirstDown`, and a finger
                // that is already down never sends another one — so the drag
                // ends where the finger was when the scale changed. That is the
                // honest outcome and it is a settled control, which is the whole
                // difference from the report.
                cancelled = true
                throw stopped
            } finally {
                press?.let { started ->
                    interactionSource?.let { source ->
                        scope.launch {
                            source.emit(
                                if (cancelled) {
                                    DragInteraction.Cancel(started)
                                } else {
                                    DragInteraction.Stop(started)
                                }
                            )
                        }
                    }
                    currentEnd()
                }
            }
        }
    }
}

/**
 * How much of the touch slop a [DragClaim.Direction] gathers before it decides.
 *
 * Less than all of it, so it decides before a scroller running the other way can
 * claim; enough that the direction is the finger's and not the jitter of a thumb
 * settling onto the glass.
 */
private const val DirectionDecisionShare = 0.4f
