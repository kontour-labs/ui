package io.kontour.ui.motion

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import androidx.compose.ui.unit.Velocity
import kotlin.math.abs
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * Where the finger on the screen has been lately, so a scroller's release can be
 * judged by what the finger did when the platform reports that it did nothing.
 *
 * The sheet has had its own for a round — see `SheetFinger`, whose rule this is,
 * for the whole story. Compose's UIKit velocity tracker resets to zero when a
 * gesture longer than 100ms saw a gap of more than 40ms between touches within
 * 50ms of the lift, and it counts touch-down to first movement as such a gap. So
 * the commonest way to throw a list on a phone — a thumb laid on it, a moment's
 * rest, a flick — reported no speed, and the list stopped dead where the thumb
 * left it. A frame that came late mid-drag did the same. Reported as flings on
 * iOS that "still feel like there is a bit of friction".
 *
 * The rule stays the platform's. This answers only the case it gets wrong: the
 * platform reports **exactly nothing**, and the finger was still moving when it
 * lifted. A finger that stopped before it lifted is still a finger that stopped.
 */
internal class FingerTrail(private val clock: TimeSource = TimeSource.Monotonic) {
    private val times = LongArray(Capacity)
    private val xs = FloatArray(Capacity)
    private val ys = FloatArray(Capacity)
    private var count = 0
    private var next = 0
    private var x = 0f
    private var y = 0f
    private var lastMoveAt = 0L
    private var liftedAt = -1L
    private var lifted: TimeMark? = null

    fun down(at: Long) {
        count = 0
        next = 0
        x = 0f
        y = 0f
        liftedAt = -1L
        lifted = null
        lastMoveAt = at
        add(at)
    }

    fun move(at: Long, by: Offset) {
        x += by.x
        y += by.y
        lastMoveAt = at
        add(at)
    }

    fun lift(at: Long) {
        liftedAt = at
        lifted = clock.markNow()
    }

    /** Forgets the trail: a gesture this does not follow, such as a mouse's. */
    fun clear() {
        count = 0
        liftedAt = -1L
        lifted = null
    }

    /**
     * The finger's own speed as it lifted, in pixels a second in the pointer's
     * directions: null if it had stopped, if the lift was not just now, or if
     * there is too little of the trail to read.
     */
    fun throwAtLift(): Velocity? {
        val since = lifted?.elapsedNow() ?: return null
        if (since.inWholeMilliseconds > FreshMillis) return null
        if (liftedAt - lastMoveAt > StoppedMillis) return null
        if (count < 2) return null
        val newest = (next - 1 + Capacity) % Capacity
        val newestAt = times[newest]
        var oldest = newest
        for (step in 1 until count) {
            val index = (newest - step + Capacity) % Capacity
            if (newestAt - times[index] > WindowMillis) break
            oldest = index
        }
        val millis = newestAt - times[oldest]
        if (oldest == newest || millis <= 0L) return null
        return Velocity(
            (xs[newest] - xs[oldest]) * 1000f / millis,
            (ys[newest] - ys[oldest]) * 1000f / millis,
        )
    }

    private fun add(at: Long) {
        times[next] = at
        xs[next] = x
        ys[next] = y
        next = (next + 1) % Capacity
        if (count < Capacity) count++
    }

    private companion object {
        const val Capacity = 32

        /** How much of the trail a release is read from. */
        const val WindowMillis = 100L

        /** A finger still this long before it lifted had stopped: the platforms' own figure. */
        const val StoppedMillis = 40L

        /** A release is read straight after its lift; one read later belongs to something else. */
        const val FreshMillis = 150L
    }
}

/**
 * Gives every scroller inside it the throw the platform lost: installed once, at
 * the root of a [io.kontour.ui.theme.KontourTheme], where the platform needs it.
 *
 * At the root because the lost throw is not one scroller's problem. An app's own
 * `LazyColumn` loses it exactly as the library's do, and Compose has no hook for
 * the fling of every list — but every list asks its nested-scroll ancestors
 * about a fling before it starts one, and this is the outermost of them.
 *
 * ### How it hands the speed on
 *
 * `onPreFling` returns what it *consumed*, and whatever is left is the fling. So
 * where a release reports nothing and the finger was moving, this consumes the
 * finger's speed **negated**, and what is left for the scroller is the finger's
 * speed. The velocity a scroller's ancestors are asked about is in the pointer's
 * directions, before a reversed layout flips it for itself, so a reversed list is
 * thrown the right way without knowing it is one.
 *
 * Only along the axis the gesture scrolled on, read from the scroll it asked
 * about as the finger moved. A vertical list in a horizontal pager, thrown with a
 * little sideways drift, would otherwise hand the pager a sideways fling of its
 * own.
 */
internal fun Modifier.catchRestedFlicks(trail: FingerTrail): Modifier = this
    .pointerInput(trail) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (down.type != PointerType.Touch) {
                trail.clear()
                return@awaitEachGesture
            }
            trail.down(down.uptimeMillis)
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (change.changedToUpIgnoreConsumed()) {
                    trail.lift(change.uptimeMillis)
                    break
                }
                val moved = change.positionChangeIgnoreConsumed()
                if (moved != Offset.Zero) trail.move(change.uptimeMillis, moved)
            }
        }
    }
    .nestedScroll(RestedFlicks(trail))

internal class RestedFlicks(private val trail: FingerTrail) : NestedScrollConnection {
    /** The axis this gesture has scrolled on, or null before it has. */
    private var axis: Orientation? = null

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (source == NestedScrollSource.UserInput && available != Offset.Zero) {
            axis = if (abs(available.y) >= abs(available.x)) Orientation.Vertical else Orientation.Horizontal
        }
        return Offset.Zero
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        val along = axis
        axis = null
        if (along == null || available != Velocity.Zero) return Velocity.Zero
        val thrown = trail.throwAtLift() ?: return Velocity.Zero
        val lost = when (along) {
            Orientation.Vertical -> Velocity(0f, thrown.y)
            Orientation.Horizontal -> Velocity(thrown.x, 0f)
        }
        return -lost
    }
}
