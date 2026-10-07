package io.kontour.ui.interaction

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.awaitVerticalPointerSlopOrCancellation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Which way a picked-up thing may travel: a row along its list, or anything
 * anywhere, as a card carried to another list is.
 */
internal enum class PickUpAxis { Vertical, Free }

/**
 * How far a finger may wander during the hold and still be holding still.
 *
 * `detectDragGesturesAfterLongPress` cancels on `viewConfiguration.touchSlop`,
 * which is the threshold for "this is a scroll" — and it is the wrong question
 * to ask of a finger that has not started moving anywhere yet. A fingertip is a
 * centimetre wide and rolls as it presses; on a phone browser, where frames are
 * slower and every event is sampled coarsely, holding inside eight dp for half a
 * second is a skill rather than a gesture.
 *
 * Reproduced with the hold intact and ten dp of wander in the middle of it: the
 * row was never picked up, and the drag that followed went to whatever scrolls
 * behind it — which is the report, "cannot drag downwards, the page scrolls
 * instead".
 *
 * Twenty-four dp, which is about a fingertip. A scroll does not fit inside it:
 * anything that is going to scroll has left this circle long before the timeout,
 * because a scroll that travels 24dp in half a second is 48dp a second, which is
 * slower than a page moves under a finger that means to move it.
 */
internal val PickUpHoldSlop: Dp = 24.dp

/**
 * A pick-up, after a long press or after the pointer's slop depending on
 * [immediate], and the carry that follows it until the pointer lets go.
 *
 * Shared by a reorderable row and a drag-and-drop source, which differ in where
 * the thing may go — [axis] — and in nothing about how it is taken hold of.
 *
 * One detector rather than two, and that is the point rather than a tidy-up:
 * **which** of the two a gesture wants is decided when the gesture starts, not
 * when the modifier is built. It used to be a `pointerInput` key, and a key that
 * moves mid-gesture restarts the node underneath the finger holding it.
 *
 * The hold half is `detectDragGesturesAfterLongPress` with one number changed —
 * see [PickUpHoldSlop] — written out rather than wrapped because the movement
 * budget `awaitLongPressOrCancellation` allows is fixed and not a parameter.
 *
 * @param immediate A mouse, or a dedicated grip: neither has a scroll to steal,
 *   so neither waits half a second for one. Read once per gesture.
 * @param accepts Whether a gesture pressed down here is this detector's at all,
 *   asked once at the down. For a node with something under it that has its own
 *   pick-up: on a touchscreen both wait out the same long press, and both took
 *   hold — an event calendar's empty time began a new event under the event a
 *   finger was holding, then cancelled the shared edit when the event carried.
 * @param givesWay Whether the hold gives up once something else takes the
 *   pointer — a scroll that has started moving, a pager that has claimed a
 *   swipe — as foundation's own long press does. For a surface that scrolls
 *   under the thing held: in a list that scrolls, the content moves with the
 *   finger, so the finger stays where it was *in the content* and the distance
 *   budget cannot see a scroll at all. An event calendar's hours, scrolled and
 *   stopped with a finger still down, drew out a new event from under it.
 *   Off for a reorderable row, whose settling fingertip may nudge its list a
 *   little and should still pick the row up.
 * @param onPickUp Where, in this node, the thing was taken hold of.
 * @param onCarry Each movement while carried. Vertical carries only report the
 *   vertical part of the first one.
 * @param onPutDown The pointer let go.
 * @param onCancel Something else took the pointer, or it went away.
 */
internal suspend fun PointerInputScope.detectPickUp(
    axis: PickUpAxis,
    immediate: () -> Boolean,
    accepts: (Offset) -> Boolean = { true },
    givesWay: Boolean = false,
    onPickUp: (Offset) -> Unit,
    onCarry: (PointerInputChange, Offset) -> Unit,
    onPutDown: () -> Unit,
    onCancel: () -> Unit,
) {
    val budget = PickUpHoldSlop.toPx()
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        if (!accepts(down.position)) return@awaitEachGesture
        if (immediate()) {
            // Slop rather than a hold. Without it a plain click on a row would
            // pick it up, which is what the long press is for on a touchscreen
            // and what slop is for with a pointer.
            //
            // Vertically, **the pointer's own slop**, which is an eighth of a dp
            // for a mouse and the touch slop for a finger. This used to be
            // `awaitTouchSlopOrCancellation`, which assumes a finger whatever the
            // pointer is, and dropped the distance past the slop as well — so a
            // row followed the mouse 18dp behind for the whole of a desktop drag
            // and slid out from under the grip that had been taken hold of.
            // Freely, the touch slop: foundation keeps the two-axis pointer slop
            // to itself, and a carried card is drawn under the pointer however
            // far it went before it was picked up, so nothing lags behind it.
            var overSlop = Offset.Zero
            val moved = when (axis) {
                PickUpAxis.Vertical -> awaitVerticalPointerSlopOrCancellation(down.id, down.type) { change, over ->
                    change.consume()
                    overSlop = Offset(0f, over)
                }
                PickUpAxis.Free -> awaitTouchSlopOrCancellation(down.id) { change, over ->
                    change.consume()
                    overSlop = over
                }
            } ?: return@awaitEachGesture
            onPickUp(moved.position)
            // Delivered as `detectDragGestures` delivers it: the part of the
            // first movement past the slop is movement, not the price of
            // starting.
            if (overSlop != Offset.Zero) onCarry(moved, overSlop)
        } else {
            // `withTimeoutOrNull` returning null is the *success* here: the loop
            // inside it only ever returns early, so reaching the deadline means
            // the finger stayed put for the whole of it.
            val gaveUp = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Main)
                    val change = event.changes.firstOrNull { it.id == down.id }
                        ?: return@withTimeoutOrNull
                    if (!change.pressed) return@withTimeoutOrNull
                    if ((change.position - down.position).getDistance() > budget) {
                        return@withTimeoutOrNull
                    }
                    // Taken by something else: see [givesWay].
                    if (givesWay) {
                        val after = awaitPointerEvent(PointerEventPass.Final)
                        if (after.changes.any { it.id == down.id && it.isConsumed }) {
                            return@withTimeoutOrNull
                        }
                    }
                }
            }
            if (gaveUp != null) return@awaitEachGesture
            onPickUp(down.position)
        }

        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Main)
            val change = event.changes.firstOrNull { it.id == down.id }
            if (change == null || change.isConsumed) {
                onCancel()
                break
            }
            if (!change.pressed) {
                change.consume()
                onPutDown()
                break
            }
            val delta = change.positionChange()
            if (delta != Offset.Zero) onCarry(change, delta)
        }
    }
}

/**
 * The end of a carry, felt: [landed] when what was carried has been put down
 * somewhere new, [refused] when where it was let go would not take it.
 *
 * One helper for the places that put something down — a reorderable row, a
 * drag-and-drop source — for the reason the tap and long-press helpers give:
 * one call site, rather than one each. Nothing for a thing put straight back
 * where it was picked up from: it has not landed anywhere.
 */
@Stable
internal class LandingFeedback(private val feedback: FeedbackDispatcher) {
    /**
     * Softer than the snaps it follows: a drop confirms the thing has landed; the
     * news already happened, once per gap crossed. `GestureEnd` — a soft impact —
     * and not rate-limited, so the landing is never the report a floor drops.
     */
    fun landed() = put(FeedbackIntent.GestureEnd)

    /** Let go over somewhere that would not take it, and on its way home. */
    fun refused() = put(FeedbackIntent.Reject)

    private fun put(intent: FeedbackIntent) = feedback.perform(intent)
}

/** A [LandingFeedback] through the theme's dispatcher. */
@Composable
internal fun rememberLandingFeedback(): LandingFeedback {
    val feedback = LocalFeedback.current
    return remember(feedback) { LandingFeedback(feedback) }
}
