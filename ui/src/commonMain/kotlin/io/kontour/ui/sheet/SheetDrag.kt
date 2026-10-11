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
 * the state's own drag scope at the user's priority — so a finger catches a
 * settling sheet, as `startDragImmediately` did — the stretch past the ends
 * through [overscroll], the release through the same [fling] in the same scope,
 * and [interactions] told when a drag starts and stops.
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
        return left
    }
    coroutineScope {
        val scope = this
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
            if (anchored.isAnimationRunning) {
                // Caught on its way: held where it is, at once.
                down.consume()
            } else {
                var total = Offset.Zero
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
                    // Something inside set off first, or the pointer let go: not the sheet's.
                    if (change.isConsumed || !change.pressed) return@awaitEachGesture
                    total += change.positionChange()
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
                anchored.anchoredDrag(MutatePriority.UserInput) {
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
            }
            if (overSlop != 0f) deltas.trySend(overSlop)
            var lifted = false
            try {
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) {
                        // **Let go, or taken away.** A pointer the system took —
                        // the notification shade pulled down over the app, focus
                        // gone elsewhere — arrives as an up already consumed, at
                        // the time of the last move: a cancel, not a release, and
                        // the sheet goes to the nearest detent rather than being
                        // thrown at the speed of whatever moved last. Read as a
                        // release, a sheet nudged a notch at full height closed.
                        val cancelled = change.isConsumed
                        change.consume()
                        if (!cancelled) {
                            lifted = true
                            // When it let go, as well as where: a finger that
                            // stopped and then lifted threw nothing, where the
                            // tracker, told only of the moves, read the last of them.
                            tracker.addPosition(change.uptimeMillis, Offset(0f, travelled))
                        }
                        break
                    }
                    val dy = change.positionChange().y
                    change.consume()
                    if (dy != 0f) {
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
            val velocity = if (lifted) tracker.calculateVelocity().y.coerceIn(-most, most) else 0f
            interactions.tryEmit(if (lifted) DragInteraction.Stop(start) else DragInteraction.Cancel(start))
            scope.launch {
                dragging.join()
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
            }
        }
    }
}

/**
 * How much further than the platform's touch slop a finger goes up or down
 * before it moves the sheet: see [sheetDrag]. About sixty degrees from across,
 * rather than forty-five, is where a swipe over a carousel stops being the
 * carousel's and starts being the sheet's.
 */
internal const val SheetDragSlop = 1.75f

/** A mouse's slop against a finger's, as Compose's own draggables take it. */
private const val MouseSlopRatio = 0.125f / 18f
