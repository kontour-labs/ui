package io.kontour.ui.sheet

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import kotlin.math.abs

/**
 * Where the finger on a sheet has been lately, so a release can be judged by what
 * the finger did and not only by what the platform's velocity tracker reports.
 *
 * ### Why a sheet keeps its own
 *
 * Reported from an iPhone, twice, as closing a sheet taking "such an effort",
 * and "still too difficult", where the same sheet on Android felt natural. The
 * sheet's code is the same on both; what it is handed is not. Compose's UIKit
 * velocity tracker resets to zero when a gesture longer than 100ms saw a gap of
 * more than 40ms between touches within 50ms of the lift — and it counts the time
 * from touch-down to the first movement as such a gap. So the commonest way to
 * throw a sheet off a phone, a thumb laid on it, a moment's rest, and a flick of
 * a few tens of milliseconds, reported no speed at all: the sheet settled by
 * position and sprang back unless it had been hauled past half way. A slow frame
 * mid-drag does the same, since the tracker is only handed one touch a frame.
 *
 * The rule is the platform's and stays the platform's. This only answers the one
 * case it gets wrong for a sheet: the platform reports **exactly nothing**, and
 * the finger was still moving when it lifted. Then the sheet reads the throw off
 * the last tenth of a second of the finger's own path, across any gap in it.
 * A finger that stopped before it lifted is still a finger that stopped.
 */
internal class SheetFinger {
    private val times = LongArray(Capacity)
    private val along = FloatArray(Capacity)
    private var count = 0
    private var next = 0
    private var travelled = 0f
    private var run = 0f
    private var tracked = false
    private var lastMoveAt = 0L
    private var liftedAt = -1L

    fun down(at: Long) {
        count = 0
        next = 0
        travelled = 0f
        run = 0f
        tracked = true
        liftedAt = -1L
        lastMoveAt = at
        add(at)
    }

    fun move(at: Long, dy: Float) {
        travelled += dy
        // The finger's current run one way: it starts again where it turns.
        run = if (run == 0f || (run > 0f) == (dy > 0f)) run + dy else dy
        lastMoveAt = at
        add(at)
    }

    /**
     * What a release counts as, as [releaseVelocity] says — or no throw at all
     * where the finger's last run one way was shorter than [atLeast].
     *
     * **A finger rolling off the glass is not a throw.** It leaves with a few
     * pixels of travel at a few hundred pixels a second, which is fast enough to
     * count as a flick and to move the sheet a detent — measured, a full-height
     * sheet let go after twenty-four pixels of roll-off fell to half. What a
     * roll-off does not have is distance: a deliberate flick of a sheet, even a
     * gentle one, runs further than touch slop and a little more. And the run
     * starts again where the finger turns, so a roll-off after pulling the sheet
     * up against its top is judged on its own few pixels, not on the pull.
     */
    fun throwOf(reported: Float, atLeast: Float): Float {
        if (tracked && abs(run) < atLeast) return 0f
        return releaseVelocity(reported)
    }

    fun lift(at: Long) {
        liftedAt = at
    }

    /**
     * What a release reported as [reported], in pixels a second along the sheet's
     * axis, should count as.
     */
    fun releaseVelocity(reported: Float): Float {
        if (reported != 0f) return reported
        // Stopped before lifting: the platform was right.
        if (liftedAt < 0L || liftedAt - lastMoveAt > StoppedMillis) return reported
        return ownVelocity() ?: reported
    }

    /** Pixels a second over the last [WindowMillis] of the path, or null with too little of it. */
    internal fun ownVelocity(): Float? {
        if (count < 2) return null
        val newest = (next - 1 + Capacity) % Capacity
        val newestAt = times[newest]
        var oldest = newest
        for (step in 1 until count) {
            val index = (newest - step + Capacity) % Capacity
            if (newestAt - times[index] > WindowMillis) break
            oldest = index
        }
        if (oldest == newest) return null
        val millis = newestAt - times[oldest]
        if (millis <= 0L) return null
        return (along[newest] - along[oldest]) * 1000f / millis
    }

    private fun add(at: Long) {
        times[next] = at
        along[next] = travelled
        next = (next + 1) % Capacity
        if (count < Capacity) count++
    }

    private companion object {
        const val Capacity = 32

        /** How much of the path a release is read from. */
        const val WindowMillis = 100L

        /** A finger still this long before it lifted had stopped: the platforms' own figure. */
        const val StoppedMillis = 40L
    }
}

/**
 * Follows the finger on the sheet for [SheetFinger], before anything inside the
 * sheet sees it and without taking anything from it.
 *
 * By the finger's own movement — the change in each event, not its position —
 * because the sheet moves under the finger as it is dragged, and a position in
 * the sheet's coordinates would read a finger dragging it as a finger still.
 */
internal fun Modifier.followSheetFinger(finger: SheetFinger): Modifier =
    pointerInput(finger) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            finger.down(down.uptimeMillis)
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (change.changedToUpIgnoreConsumed()) {
                    finger.lift(change.uptimeMillis)
                    break
                }
                val dy = change.positionChangeIgnoreConsumed().y
                if (dy != 0f) finger.move(change.uptimeMillis, dy)
            }
        }
    }
