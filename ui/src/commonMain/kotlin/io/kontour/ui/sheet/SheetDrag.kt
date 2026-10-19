package io.kontour.ui.sheet

import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.OverscrollEffect
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.Velocity
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs
import kotlin.math.sign
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.NonCancellable

/**
 * The sheet's own drag: a finger, or a mouse, on the sheet moves it up and down
 * between its detents, and a release throws it to one through [fling].
 *
 * **Not `anchoredDraggable`, and the difference is the point.** Compose's
 * draggables find each other up the tree: a horizontal one — a carousel, a
 * `LazyRow`, a pager — that crosses its slop on a swipe more than thirty degrees
 * from across, with a vertical draggable round it still waiting, yields to that
 * one, *whatever its slop*. Inside a sheet that is every carousel, so a swipe
 * across one had to be almost flat to be the carousel's: *"horizontal dragging
 * in a sheet is sometimes a bit difficult to do"*. This drag is not one of those
 * draggables, so nothing inside a sheet yields to it.
 *
 * What it does instead is wait longer: [SheetDragSlop] times the platform's
 * touch slop up or down before it takes a finger, on the main pass, after
 * everything inside the sheet has had the event. A control inside that sets off
 * first — a carousel at its own slop across, a list at its own slop down — has
 * the gesture, and this one lets it go. A swipe mostly up or down still moves
 * the sheet, from over a carousel too, a few dp later than it would have.
 *
 * Everything else is `anchoredDraggable`'s, and done the same way: the drag in
 * the state's own drag scope at the default priority — so a finger catches a
 * settling sheet, as `startDragImmediately` did, and the app can still move the
 * sheet while a finger is on it — the stretch past the ends through
 * [overscroll], the release through the same [fling] in the same scope, and
 * [interactions] told when a drag starts and stops.
 *
 * **The default priority, as Compose's own draggable takes it.** It was the
 * user's, which no move the app makes can interrupt: a modal closed in code
 * while a finger was on it — a timer, a back press — refused to go, and stayed
 * up and draggable after its caller had closed it.
 */
internal fun Modifier.sheetDrag(
    state: SheetState,
    fling: FlingBehavior,
    interactions: MutableInteractionSource,
    overscroll: OverscrollEffect?,
): Modifier = pointerInput(state, fling, interactions, overscroll) {
    val anchored = state.anchoredState
    fun to(delta: Float): Float {
        val anchors = anchored.anchors
        val from = anchored.offset.let { if (it.isNaN()) 0f else it }
        return (from + delta).coerceIn(anchors.minPosition(), anchors.maxPosition())
    }
    suspend fun throwBy(velocity: Float): Float {
        var left = velocity
        // Counted, so a finger laid on the sheet while it lands is a catch: see
        // [SheetState.isMoving].
        state.throwsInFlight++
        try {
            anchored.anchoredDrag {
                val scroll = object : ScrollScope {
                    override fun scrollBy(pixels: Float): Float {
                        val next = to(pixels)
                        val used = next - anchored.offset
                        dragTo(next)
                        return used
                    }
                }
                with(fling) { left = scroll.performFling(velocity) }
            }
            // Landed: say so, and where. See [SheetState.restAtNearest].
            state.restAtNearest()
        } finally {
            state.throwsInFlight--
        }
        return left
    }
    coroutineScope {
        val scope = this
        // **Ended from outside: settled where it is.** The drag and the throw run
        // in this handler's scope, which a change of density, of motion or of
        // anything else it is keyed on cancels mid-gesture. Foundation's drag
        // settled then; this left the sheet between detents, with a target the
        // anchors may have left behind that no later settle cleared. An
        // interruption from inside — a new finger catching the throw — is not
        // this, and is left alone.
        suspend fun restIfEndedFromOutside() {
            if (scope.isActive) return
            withContext(NonCancellable) {
                try {
                    state.restAtNearest()
                } catch (_: CancellationException) {
                    // Something else is moving it, which settles it too.
                }
            }
        }
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val slop = viewConfiguration.touchSlop * if (down.type == PointerType.Mouse) MouseSlopRatio else SheetDragSlop
            // How far the pointer has gone, up or down, since it went down: the
            // throw is read from all of it, the slop included, as Compose's own
            // draggables read it.
            var travelled = 0f
            var overSlop = 0f
            val tracker = VelocityTracker()
            tracker.addPosition(down.uptimeMillis, Offset.Zero)
            var movedAt = down.uptimeMillis
            // **Caught on its way: held where it is, at once**, and the rest of
            // the gesture is the sheet's. Read before anything inside the sheet
            // sees it, on the initial pass, so a list under the finger does not
            // set off too and move the sheet a second time through its nested
            // scroll.
            //
            // On its way includes a throw of the sheet's own surface, which
            // `isAnimationRunning` alone does not count. That was the report: a
            // full-height sheet pulled down, let go and pulled again before it
            // was back was not caught — the new finger had to travel this drag's
            // whole slop first, and the throw carried the sheet to the top while
            // it did.
            //
            // **Held at once, but taken only once it moves.** The rest of the
            // gesture was the sheet's from the down, every change consumed on
            // this pass before anything inside saw it — the lift included. A
            // control inside had seen the down and then saw its up consumed, which
            // is a cancel, so a button tapped while the sheet was opening, landing
            // or being thrown did nothing at all: every open is a sheet moving.
            // The catch is the down's, and a still press and release is left to
            // whatever is under it. A finger that travels [CatchSlop] up or down
            // is a drag of the sheet, and from there it is the sheet's.
            val caught = state.isMoving
            val pass = if (caught) PointerEventPass.Initial else PointerEventPass.Main
            val catchSlop = viewConfiguration.touchSlop * if (down.type == PointerType.Mouse) MouseSlopRatio else CatchSlop
            var taken = !caught
            var held = 0f
            if (caught) {
                down.consume()
            } else {
                var total = Offset.Zero
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
                    // Something inside set off first, or the pointer let go: not the sheet's.
                    if (change.isConsumed || !change.pressed) return@awaitEachGesture
                    tracker.addHistory(change, total.y)
                    total += change.positionChange()
                    movedAt = change.uptimeMillis
                    tracker.addPosition(change.uptimeMillis, Offset(0f, total.y))
                    if (abs(total.y) >= slop) {
                        change.consume()
                        travelled = total.y
                        overSlop = total.y - sign(total.y) * slop
                        break
                    }
                }
            }

            val start = DragInteraction.Start()
            interactions.tryEmit(start)
            val deltas = Channel<Float>(Channel.UNLIMITED)
            val dragging = scope.launch {
                try {
                    anchored.anchoredDrag(MutatePriority.Default) {
                        for (delta in deltas) {
                            if (overscroll == null) {
                                dragTo(to(delta))
                            } else {
                                overscroll.applyToScroll(Offset(0f, delta), NestedScrollSource.UserInput) { available ->
                                    val next = to(available.y)
                                    val used = next - anchored.requireOffset()
                                    dragTo(next)
                                    Offset(0f, used)
                                }
                            }
                        }
                    }
                } catch (ended: CancellationException) {
                    restIfEndedFromOutside()
                    throw ended
                }
            }
            if (overSlop != 0f) deltas.trySend(overSlop)
            var lifted = false
            var lastMoveAt = movedAt
            var stoppedFor = 0L
            try {
                while (true) {
                    val event = awaitPointerEvent(pass)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) {
                        // **Let go, or taken away.** A pointer the system took —
                        // the notification shade pulled down over the app, focus
                        // gone elsewhere — arrives as an up already consumed, at
                        // the time of the last move: a cancel, not a release, and
                        // the sheet goes to the nearest detent rather than being
                        // thrown at the speed of whatever moved last. Read as a
                        // release, a sheet nudged a notch at full height closed.
                        //
                        // Read off the finger, which saw the up before anything
                        // inside the sheet did: by this pass a bar inside it
                        // that keeps its presses has consumed a real lift too.
                        //
                        // **And off the change itself**, because for a cancel
                        // the finger has not always seen it yet. Compose cancels
                        // each pointer node in turn rather than pass by pass, and
                        // logged on a phone (2026-10-19) this node had the cancel
                        // first every time: the finger still read pressed, the
                        // up was taken for a release, and the sheet was thrown at
                        // up to 2,600px/s by a drag Android had taken away. See
                        // [takenAway].
                        val cancelled = state.finger.cancelled || change.takenAway()
                        // A caught press that never moved lifts for whatever
                        // is under it: see `caught`.
                        if (taken) change.consume()
                        if (!cancelled) {
                            lifted = true
                            // When it let go, as well as where: a finger still
                            // for a moment before it lifted threw nothing, where
                            // the tracker, told only of the moves, read the last
                            // of them. Not as a sample — a still point at the end
                            // of a quadratic fit bent it into a throw back the
                            // other way — but by the time since the last move.
                            stoppedFor = change.uptimeMillis - lastMoveAt
                        }
                        break
                    }
                    val dy = change.positionChange().y
                    if (!taken) {
                        held += dy
                        taken = abs(held) >= catchSlop
                    }
                    if (taken) change.consume()
                    if (dy != 0f) {
                        lastMoveAt = change.uptimeMillis
                        tracker.addHistory(change, travelled)
                        travelled += dy
                        tracker.addPosition(change.uptimeMillis, Offset(0f, travelled))
                        deltas.trySend(dy)
                    }
                }
            } catch (cancelled: CancellationException) {
                deltas.close()
                interactions.tryEmit(DragInteraction.Cancel(start))
                throw cancelled
            }
            deltas.close()
            val most = viewConfiguration.maximumFlingVelocity
            val velocity = if (lifted && stoppedFor <= StoppedMillis) tracker.calculateVelocity().y.coerceIn(-most, most) else 0f
            interactions.tryEmit(if (lifted) DragInteraction.Stop(start) else DragInteraction.Cancel(start))
            scope.launch {
                try {
                    dragging.join()
                    // Taken over while the finger was on it — the app moved the
                    // sheet, closing it say — so the release is not a throw:
                    // throwing it would undo what the app did.
                    if (dragging.isCancelled) return@launch
                    if (overscroll == null) {
                        throwBy(velocity)
                    } else {
                        overscroll.applyToFling(Velocity(0f, velocity)) { available ->
                            val left = throwBy(available.y)
                            val at = anchored.requireOffset()
                            val anchors = anchored.anchors
                            // Spent at an end, the rest goes to the stretch.
                            if (at >= anchors.maxPosition() || at <= anchors.minPosition()) Velocity(0f, left) else available
                        }
                    }
                } catch (ended: CancellationException) {
                    restIfEndedFromOutside()
                    throw ended
                }
            }
        }
    }
}

/**
 * Whether this up is a cancel rather than a lift: already consumed, and at the
 * time of the last move, which is how Compose sends a pointer the system took.
 * A real lift carries its own time, later than the move before it, so a lift
 * that something inside consumed — a bar that keeps its presses — is still a
 * lift.
 */
private fun PointerInputChange.takenAway(): Boolean =
    !pressed && previousPressed && isConsumed && uptimeMillis == previousUptimeMillis

/**
 * The touches [change] batched up since the last event — Android reports several
 * a frame — as positions along the drag, from [before], where the drag stood at
 * the last event: what Compose's own draggables hand their tracker. Fitted to one
 * point a frame, a short flick has few points and the fit overshoots at its end.
 *
 * Each from the change's previous position rather than as a position in the
 * sheet, which moves under the finger between events; both are in the node's
 * coordinates as they are now.
 */
private fun VelocityTracker.addHistory(change: PointerInputChange, before: Float) {
    change.historical.forEach { past ->
        addPosition(past.uptimeMillis, Offset(0f, before + past.position.y - change.previousPosition.y))
    }
}

/**
 * How much further than the platform's touch slop a finger goes up or down
 * before it moves the sheet: see [sheetDrag]. About sixty degrees from across,
 * rather than forty-five, is where a swipe over a carousel stops being the
 * carousel's and starts being the sheet's.
 */
internal const val SheetDragSlop = 1.75f

/**
 * How far a finger that caught a moving sheet goes up or down before the gesture
 * is the sheet's rather than a press of whatever is under it: half the platform's
 * touch slop. Under the slop a list or a pager inside would take, so a pull is
 * still never handed to them; over the few pixels a finger rolls as it presses.
 */
private const val CatchSlop = 0.5f

/** A finger still this long before it lifted had stopped, and threw nothing: the platforms' own figure. */
private const val StoppedMillis = 40L

/** A mouse's slop against a finger's, as Compose's own draggables take it. */
private const val MouseSlopRatio = 0.125f / 18f
