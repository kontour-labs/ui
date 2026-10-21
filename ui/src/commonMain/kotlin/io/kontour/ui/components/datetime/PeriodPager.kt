package io.kontour.ui.components.datetime

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.structuralEqualityPolicy
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Where a calendar's periods are — months, weeks, days, years, each counted by
 * an index of its own — as a strip of them and a position along it.
 *
 * Slot 0 is [origin] and each slot on is a period on, so the position is how
 * many periods from the origin the view is, to a fraction. A far jump — to
 * today from a year away — does not travel through every period between: the
 * one it is going to stands in at the slot beside the view, a bridge, and the
 * pager moves one page. When a move lands the strip is counted from where it
 * landed.
 *
 * Plain state rather than an `Animatable`, so a finger can take it over from a
 * gesture callback without waiting on an animation's lock.
 *
 * The date picker's month pager and the event calendar's are this one model —
 * a swipe and a jump the same motion — over a month index in one and whichever
 * period the calendar is showing in the other.
 */
@Stable
internal class PeriodPager(start: Int) {
    var origin by mutableIntStateOf(start)
        private set
    private var shift by mutableFloatStateOf(0f)
    private var bridge by mutableStateOf<Int?>(null)
    private var bridgeSlot by mutableIntStateOf(0)

    /** Whether a finger has it, and how far along it was when it did. */
    var held by mutableStateOf(false)
        private set
    var heldFrom by mutableFloatStateOf(0f)
        private set
    private var pulled by mutableFloatStateOf(0f)

    /** The slot a swipe counts from: where the view was, or where it was going. */
    var startSlot = 0
        private set

    /** Whether the finger found it at rest, rather than moving. */
    var fromRest = true
        private set

    /** The period the running move is going to, or last went to. */
    var headedFor: Int? = start
    private var headedSlot = 0
    private var speed = 0f
    private var job: Job? = null

    /** How far along the strip the view is, in periods from [origin]. */
    val position: Float get() = if (held) heldFrom + pulled else shift

    /** The periods any of which is in view: one at rest, two while moving. */
    val inView: List<Int> by derivedStateOf(structuralEqualityPolicy()) {
        val at = position
        val low = floor(at).toInt()
        val high = ceil(at).toInt()
        if (low == high) listOf(periodAt(low)) else listOf(periodAt(low), periodAt(high))
    }

    /** The period at [slot]: past a bridge, counted on from the period it stands for. */
    fun periodAt(slot: Int): Int {
        val far = bridge
        return if (far != null && slot != 0 && (slot > 0) == (bridgeSlot > 0)) {
            far + (slot - bridgeSlot)
        } else {
            origin + slot
        }
    }

    /** The slot [period] is at, or null where a bridge stands in its place. */
    fun slotOf(period: Int): Int? {
        val far = bridge
        if (far != null) {
            val beyond = bridgeSlot + (period - far)
            if (beyond != 0 && (beyond > 0) == (bridgeSlot > 0)) return beyond
        }
        val here = period - origin
        return if (far != null && here != 0 && (here > 0) == (bridgeSlot > 0)) null else here
    }

    /** Pages from the middle of the view to [period]: positive towards later ones. */
    fun placeOf(period: Int): Float = slotOf(period)?.minus(position) ?: Float.NaN

    fun take() {
        fromRest = job?.isActive != true
        job?.cancel()
        job = null
        heldFrom = shift
        pulled = 0f
        startSlot = if (fromRest) shift.roundToInt() else headedSlot
        held = true
    }

    fun pull(pages: Float) {
        pulled += pages
    }

    fun letGo() {
        shift = position
        held = false
        pulled = 0f
    }

    /**
     * Every period now [pages] further on than it was, the same ones on screen:
     * for periods counted afresh from a later start, as three days are by a
     * swipe that comes to rest part-way through a page. The slots keep their
     * periods, and the view moves back along them by as much as each moved on.
     */
    fun slide(pages: Float) {
        shift -= pages
    }

    /**
     * Goes to [period] from wherever the pager is, at [velocity] pages a second
     * towards later periods, or at the speed of the move it takes over.
     */
    fun moveTo(
        period: Int,
        scope: CoroutineScope,
        stiffness: Float,
        cut: Boolean,
        velocity: Float = speed,
    ) {
        job?.cancel()
        job = null
        headedFor = period
        if (cut) {
            land(period)
            return
        }
        var slot = slotOf(period)
        if (slot == null || abs(slot - shift) > 1.5f) {
            // Too far to travel: counted from the period most in view, with the
            // one it is going to standing beside it.
            rebase()
            bridge = period
            bridgeSlot = if (period > origin) 1 else -1
            slot = bridgeSlot
        }
        headedSlot = slot
        job = scope.launch {
            animate(
                initialValue = shift,
                targetValue = slot.toFloat(),
                initialVelocity = velocity,
                // Critically damped: an overshoot would show the period beyond,
                // which is not where it is going.
                animationSpec = spring(dampingRatio = 1f, stiffness = stiffness),
            ) { value, moving ->
                shift = value
                speed = moving
            }
            land(period)
        }
    }

    /** Arrived at [period]: the strip is counted from it, all in one write. */
    private fun land(period: Int) {
        origin = period
        shift = 0f
        bridge = null
        headedSlot = 0
        speed = 0f
    }

    /** Counts the strip from the period most in view, without moving it. */
    private fun rebase() {
        val nearest = shift.roundToInt()
        origin = periodAt(nearest)
        shift -= nearest
        bridge = null
    }
}
