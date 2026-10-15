package io.kontour.ui.overlay

import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.OverscrollEffect
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * A menu's list keeps what is done to it: a drag that starts on it scrolls it
 * and nothing else, whether or not it has anywhere to scroll, and so does the
 * wheel.
 *
 * **Before the scroll modifier**, on the scroller itself, and after its
 * `scrollbar`, whose thumb a finger takes before anything here sees it.
 *
 * ### Why the list takes a finger itself
 *
 * A scroller offers what it does not use to the scrollers it is inside, through
 * nested scrolling, both before it scrolls and after — and a menu's list is
 * inside whatever its `OverlayHost` is inside. With the host at the root of the
 * window that is nothing, and the page is beside the list rather than round it.
 * With the host on a card, a pane or a stage in a page that scrolls, the page was
 * the list's parent: a list that fit gave it every pixel of a drag, a long one
 * the rest of a drag past its end, and the page moved under the menu. Reported
 * of the combobox, decided as *"consuming the drag with the combobox … only in
 * the list though, dragging the field should act like everything else"*.
 *
 * Nested scrolling cannot be cut short from below: a scroller asks its parents
 * before it scrolls itself, and nothing between them can stop the asking. So a
 * finger on the list is not the scroller's to dispatch. Once it has travelled a
 * touch slop it is taken here, ahead of the scroller, and the list is scrolled
 * directly — through the list's own overscroll, so a drag past the end still
 * stretches or bounces, and flung with the list's own fling when it lifts.
 * Before the slop nothing is taken, so a tap is still a row's. A mouse drag is
 * left alone, as a scroller leaves it.
 *
 * The wheel is still the scroller's. What it does not use stops here, rather
 * than going on to scroll the page: the turn it had no use for at all, which
 * would go on as a pointer event, and the rest of one that ran it to its end,
 * which would go on through nested scrolling.
 */
internal fun Modifier.keepsItsScroll(
    state: ScrollableState,
    overscroll: OverscrollEffect?,
    fling: FlingBehavior,
): Modifier = this
    .nestedScroll(KeepsWhatIsLeft)
    .pointerInput(state, overscroll, fling) {
        coroutineScope {
            val scope = this
            // The wheel, after the scroller has had it: a turn it had no use
            // for went on to the scrollers round it as a pointer event.
            launch {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == PointerEventType.Scroll) event.changes.forEach { it.consume() }
                    }
                }
            }
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                if (down.type == PointerType.Mouse || down.isConsumed) return@awaitEachGesture
                val velocity = VelocityTracker()
                velocity.addPointerInputChange(down)
                var finger = down.id
                var travelled = Offset.Zero
                val slop = viewConfiguration.touchSlop
                // A list still moving is caught by the press, as a scroller's
                // is, and the drag is the list's from the first move.
                var moving = state.isScrollInProgress || overscroll?.isInProgress == true
                var drag: ListDrag? = null

                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == finger } ?: break
                    // Taken by something round the menu before it reached here.
                    if (change.isConsumed) break
                    if (!change.pressed) {
                        // Another finger still down carries on the drag.
                        val next = event.changes.firstOrNull { it.pressed && it.id != finger }
                        if (next != null) {
                            finger = next.id
                            continue
                        }
                        velocity.addPointerInputChange(change)
                        if (drag != null) {
                            change.consume()
                            val maximum = viewConfiguration.maximumFlingVelocity
                            drag.release(velocity.calculateVelocity().y.coerceIn(-maximum, maximum))
                        }
                        return@awaitEachGesture
                    }
                    velocity.addPointerInputChange(change)
                    val moved = change.positionChangeIgnoreConsumed()
                    var by = moved.y
                    if (!moving) {
                        travelled += moved
                        val distance = travelled.getDistance()
                        if (distance < slop) continue
                        moving = true
                        // What it travelled beyond the slop, as a scroller moves by.
                        by = travelled.y * (1f - slop / distance)
                    }
                    if (drag == null) drag = ListDrag(state, overscroll, fling).also { it.start(scope) }
                    drag.by(by)
                    change.consume()
                }
                drag?.release(0f)
            }
        }
    }

/**
 * One drag of a menu's list: its moves, in the order they came, scrolling the
 * list in one user scroll, and then its fling.
 */
private class ListDrag(
    private val state: ScrollableState,
    private val overscroll: OverscrollEffect?,
    private val fling: FlingBehavior,
) {
    private val moves = Channel<Float>(Channel.UNLIMITED)
    private val thrown = CompletableDeferred<Float>()

    fun start(scope: CoroutineScope) {
        // Undispatched, so the list's user scroll is running before the next
        // move arrives: it takes over from a fling the press caught, and from
        // the scroller's own drag if it had started one.
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            state.scroll(MutatePriority.UserInput) {
                for (pixels in moves) dragBy(pixels)
            }
            val velocity = thrown.await()
            if (velocity != 0f) flingBy(velocity)
        }
    }

    fun by(pixels: Float) {
        moves.trySend(pixels)
    }

    fun release(velocity: Float) {
        thrown.complete(velocity)
        moves.close()
    }

    /**
     * Whether the list moves at all — or is still stretched from a moment ago.
     * A list that fits takes the drag and does nothing with it: no stretch or
     * bounce, as a scroller with nowhere to go has none.
     */
    private val scrolls: Boolean
        get() = state.canScrollForward || state.canScrollBackward || overscroll?.isInProgress == true

    /** [pixels] down the screen, which scrolls the list back towards its start. */
    private fun ScrollScope.dragBy(pixels: Float) {
        if (!scrolls) return
        val effect = overscroll
        if (effect == null) {
            scrollBy(-pixels)
        } else {
            effect.applyToScroll(Offset(0f, pixels), NestedScrollSource.UserInput) { offered ->
                Offset(0f, -scrollBy(-offered.y))
            }
        }
    }

    private suspend fun flingBy(velocity: Float) {
        if (!scrolls) return
        // Velocity down the screen in, what of it the list used out.
        val throwWith: suspend (Velocity) -> Velocity = { offered ->
            var left = 0f
            state.scroll { left = with(fling) { performFling(-offered.y) } }
            Velocity(0f, offered.y + left)
        }
        val effect = overscroll
        if (effect == null) throwWith(Velocity(0f, velocity)) else effect.applyToFling(Velocity(0f, velocity), throwWith)
    }
}

/** What a menu's list leaves of a wheel's scroll: kept, rather than handed to the page. */
private object KeepsWhatIsLeft : NestedScrollConnection {
    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset = available

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity = available
}
