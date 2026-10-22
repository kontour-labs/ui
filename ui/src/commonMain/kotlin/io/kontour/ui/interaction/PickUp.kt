package io.kontour.ui.interaction

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.awaitVerticalPointerSlopOrCancellation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.isTertiaryPressed
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
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
 * How much of the touch slop a mouse travels on a whole row before it is a
 * pick-up: as much as a swipe takes to decide it is one, so the two decide on
 * the same distance and the row takes what is mostly up or down.
 */
internal const val MouseDecisionShare = 0.4f

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
 * @param onPointer Which pointer took hold, just before [onPickUp]: for a
 *   carry that something above this node follows — an event calendar's, which
 *   goes on into a period paged in after the node holding it has left.
 * @param onPickUp Where, in this node, the thing was taken hold of.
 * @param onCarry Each movement while carried. Vertical carries only report the
 *   vertical part of the first one.
 * @param onPutDown The pointer let go.
 * @param onCancel Something else took the pointer, or it went away.
 * @param decidesForMouse For a whole row with things of its own inside it — a
 *   click, a swipe — rather than a dedicated grip: a mouse travels
 *   [MouseDecisionShare] of the touch slop first, mostly along [axis], before
 *   this takes it, as a swipe decides its direction. At a mouse's own slop, an
 *   eighth of a dp, a pixel's jitter in a click picked the row up and lost the
 *   click, and a sideways drag lifted the row before the swipe inside it could
 *   tell it was sideways — *"it interferes sometimes with swipe actions and
 *   even click actions"*.
 * @param onHeld A hold was what picked it up, just before [onPointer]: for the
 *   long press's own feedback, which a mouse, a grip or a row a mouse decided
 *   on reaches no threshold to be told of.
 * @param takes Whether the thing is still there to be taken, asked at the
 *   moment this would take it — after the hold, the slop or the mouse's
 *   decision, and before anything is said of it. For two of these over one
 *   thing: a reorderable row and the grip inside it. A carry by the grip moves
 *   the row with the finger, so in the row's own terms the finger never left
 *   the hold's budget, and half a second in the row's hold came true under a
 *   carry already going: it took the moves on the initial pass, before the
 *   grip could, and the grip read that as its pointer taken and put the row
 *   down. Reported as *"with drag handles in the reorderable item, it just
 *   drops it sometimes after like 1 or 2 positions"*.
 * @param exclusive Whether, once picked up, the carry is this detector's alone:
 *   each move taken on the initial pass, before anything inside the node hears
 *   it, so nothing in it — a row's swipe actions — can claim a carry already
 *   under way. For a reorderable row. Not for a node something above follows,
 *   as an event calendar follows its carries, which takes the moves first.
 */
internal suspend fun PointerInputScope.detectPickUp(
    axis: PickUpAxis,
    immediate: () -> Boolean,
    accepts: (Offset) -> Boolean = { true },
    givesWay: Boolean = false,
    onPointer: (PointerId) -> Unit = {},
    onPickUp: (Offset) -> Unit,
    onCarry: (PointerInputChange, Offset) -> Unit,
    onPutDown: () -> Unit,
    onCancel: () -> Unit,
    exclusive: Boolean = false,
    decidesForMouse: Boolean = false,
    onHeld: () -> Unit = {},
    takes: () -> Boolean = { true },
) {
    val budget = PickUpHoldSlop.toPx()
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        // A mouse's own button only. A right press is a context menu's and a
        // middle one closes a document tab, and either dragged a row away with
        // it. A touch reports no buttons at all, so it is asked of a mouse.
        val buttons = currentEvent.buttons
        if (down.type == PointerType.Mouse && (buttons.isSecondaryPressed || buttons.isTertiaryPressed)) return@awaitEachGesture
        if (!accepts(down.position)) return@awaitEachGesture
        // A mouse on a whole row with things of its own in it — a click, a
        // swipe: see [decidesForMouse]. Taken by the pointer that went down,
        // not by the input last used, which a touch before it left reading
        // `Touch`: a mouse then waited for a hold, dragged straight past it,
        // and picked nothing up.
        if (decidesForMouse && down.type == PointerType.Mouse) {
            val decideAt = viewConfiguration.touchSlop * MouseDecisionShare
            val decided = awaitMouseDecision(down.id, axis, decideAt) ?: return@awaitEachGesture
            val gathered = decided.position - down.position
            if (!takes()) return@awaitEachGesture
            onPointer(down.id)
            onPickUp(decided.position)
            // The whole way it came, so the row is under the pointer where it
            // was taken hold of rather than the decision's distance behind it.
            onCarry(decided, if (axis == PickUpAxis.Vertical) Offset(0f, gathered.y) else gathered)
        } else if (immediate()) {
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
            if (!takes()) return@awaitEachGesture
            onPointer(down.id)
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
            if (gaveUp != null || !takes()) return@awaitEachGesture
            onHeld()
            onPointer(down.id)
            onPickUp(down.position)
        }

        while (true) {
            // **Let go of, it is put down before anything under it hears the
            // release.** Main runs from the innermost node out, so a row's own
            // `clickable` — inside this node, which is on the row — saw the
            // release first, unconsumed, and clicked: a hold put straight back
            // down was also a tap. Moving hid it, because a tap gives up on a
            // movement consumed by the time Final comes round; holding still and
            // letting go did not, which is why it was "sometimes". On Initial,
            // outermost first, the release is spent before the click looks.
            val first = awaitPointerEvent(PointerEventPass.Initial)
                .changes.firstOrNull { it.id == down.id }
            if (first != null && !first.pressed && !first.isConsumed) {
                first.consume()
                onPutDown()
                break
            }
            if (exclusive && first != null && first.pressed) {
                // Taken from above before it reached here: someone else's now.
                if (first.isConsumed) {
                    onCancel()
                    break
                }
                val delta = first.positionChange()
                if (delta != Offset.Zero) {
                    first.consume()
                    onCarry(first, delta)
                }
                continue
            }
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
 * A mouse's move on a row decided: the change that took it past [decideAt],
 * mostly along [axis], consumed. `null` for anything else — a click, something
 * inside that set off first, or a move mostly across a row that goes up and
 * down, which is a swipe's or nobody's. See `detectPickUp`'s `decidesForMouse`.
 */
private suspend fun AwaitPointerEventScope.awaitMouseDecision(
    id: PointerId,
    axis: PickUpAxis,
    decideAt: Float,
): PointerInputChange? {
    var gathered = Offset.Zero
    while (true) {
        val change = awaitPointerEvent().changes.firstOrNull { it.id == id } ?: return null
        if (change.isConsumed || !change.pressed) return null
        gathered += change.positionChange()
        if (gathered.getDistance() < decideAt) continue
        if (axis == PickUpAxis.Vertical && abs(gathered.x) > abs(gathered.y)) return null
        change.consume()
        return change
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
