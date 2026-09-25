package io.kontour.ui.components.selection

import androidx.compose.ui.geometry.Offset
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.sign

/**
 * Reads a drag on a [Knob] as the turn it means — a drag in a line, or the finger
 * going round — and says how much of the range each move is worth.
 *
 * "Can we somehow combine the circular spinning motion of the knob with the
 * left/right and up/down motion?" The two readings are both right: up or right is
 * more, and round clockwise is more. **They agree over the top-left half of the
 * knob** — along the top, right is clockwise; up the left side, up is clockwise —
 * **and disagree over the bottom-right half**, where down the right side is less
 * as a drag and more as a turn. So which one a gesture is cannot be read from its
 * direction. It is read from its **shape**: a finger going round the knob curves,
 * and its heading turns as fast as it sweeps round the middle; a finger going in a
 * line does not curve at all, however much angle it happens to sweep.
 *
 * ### How a gesture is read
 *
 * - **It starts undecided.** Where the two readings agree it follows the drag,
 *   which is the same answer either way. Where they disagree it holds, for the few
 *   dp it takes to tell — and then applies what it held the way it decided, so
 *   nothing a finger did is lost, and neither reading ever goes the wrong way first.
 * - **It decides once it has travelled [decideAfter]**: a turn if the path curved
 *   with its sweep round the middle — the heading between its first half and its
 *   second turned by at least half the angle swept, the way a circle's does — and
 *   a drag otherwise.
 * - **A drag that becomes a circle becomes a turn**: the last stretch of the path
 *   sweeping [PromoteSweep] and curving the way a circle does. A turn stays a turn
 *   until the finger lifts, so a circle that wobbles does not fall back.
 * - **Near the middle an angle is noise**, so inside [reach] of it there is no
 *   turn to read and a gesture there is a drag.
 *
 * Turned, the knob follows the finger's angle — a turn of [sweep] degrees is the
 * whole range — so a finger that grabbed the notch keeps it under the finger. Dragged,
 * [travel] pixels are the whole range, up and right more.
 */
internal class KnobTurnReader {

    private enum class Mode { Undecided, Drag, Turn }

    private var mode = Mode.Undecided
    private var centre = Offset.Zero
    private var reach = 0f
    private var sweep = FullTurn
    private var travel = 1f
    private var decideAfter = 0f
    private var fresh = false
    private var pointer = Offset.Zero
    private var heldDrag = 0f
    private var heldTurn = 0f

    // The path so far — or, once decided, its most recent stretch: where the finger
    // was, how far it had come, how far round the middle it had swept, and whether
    // it was in reach of an angle.
    private val points = ArrayList<Offset>()
    private val travelled = ArrayList<Float>()
    private val swept = ArrayList<Float>()
    private val inReach = ArrayList<Boolean>()

    /** Whether the gesture is being read as a turn. */
    val turning: Boolean get() = mode == Mode.Turn

    /**
     * A new gesture at [at], on a dial whose middle is [centre] and whose track's
     * radius is [radius]. The first [move] after this is the one that brought the
     * finger to [at], as a drag's claim hands it over.
     */
    fun start(at: Offset, centre: Offset, radius: Float, sweep: Float, travel: Float, decideAfter: Float) {
        mode = Mode.Undecided
        this.centre = centre
        reach = radius * ReachShare
        this.sweep = sweep
        this.travel = travel
        // A slow circle on a big knob covers little angle per dp; give it the same
        // arc to show itself in as a small one.
        this.decideAfter = max(decideAfter, radius * DecideArc)
        fresh = true
        pointer = at
        heldDrag = 0f
        heldTurn = 0f
        points.clear()
        travelled.clear()
        swept.clear()
        inReach.clear()
    }

    /** The share of the range the move [delta] turns the knob by, signed. */
    fun move(delta: Offset): Float {
        val from = if (fresh) pointer - delta else pointer
        val to = from + delta
        if (fresh) {
            record(from, 0f, 0f, (from - centre).getDistance() >= reach)
            fresh = false
        }
        pointer = to

        val drag = knobDragTurn(delta, travel)
        val a = from - centre
        val b = to - centre
        val canTurn = a.getDistance() >= reach && b.getDistance() >= reach
        val degrees = if (canTurn) turnBetween(a, b) else 0f
        val turn = degrees / sweep
        record(to, delta.getDistance(), degrees, canTurn)

        return when (mode) {
            Mode.Turn -> turn
            Mode.Drag -> {
                if (goingRound(windowStart(), PromoteSweep, PromoteCurve)) mode = Mode.Turn
                trim()
                drag
            }
            Mode.Undecided -> {
                val now = if (turn == 0f || sign(turn) == sign(drag)) {
                    drag
                } else {
                    heldDrag += drag
                    heldTurn += turn
                    0f
                }
                if (travelled.last() < decideAfter) {
                    now
                } else if (goingRound(0, DecideSweep, DecideCurve)) {
                    mode = Mode.Turn
                    now + heldTurn
                } else {
                    mode = Mode.Drag
                    now + heldDrag
                }
            }
        }
    }

    /**
     * How fast, as a share of the range a second, a release at [velocity] turns the
     * knob, read the way the gesture was: round the middle for a turn, along the
     * axes for a drag. [minimumSpeed] is how fast the finger has to be moving, along
     * whichever it was, for it to count; below that, null.
     */
    fun release(velocity: Offset, minimumSpeed: Float): Float? {
        if (mode == Mode.Turn) {
            val r = pointer - centre
            val distance = r.getDistance()
            if (distance < reach) return null
            val across = r.x * velocity.y - r.y * velocity.x
            if (abs(across) / distance < minimumSpeed) return null
            return across / (distance * distance) * DegreesPerRadian / sweep
        }
        val along = knobDragTurn(velocity, travel)
        return if (abs(along) * travel < minimumSpeed) null else along
    }

    private fun record(at: Offset, step: Float, degrees: Float, canTurn: Boolean) {
        points += at
        travelled += (travelled.lastOrNull() ?: 0f) + step
        swept += (swept.lastOrNull() ?: 0f) + degrees
        inReach += canTurn
    }

    /**
     * Whether the path from point [from] to now went round the middle: it swept at
     * least [minSweep] degrees, never came inside [reach], and its heading turned the
     * same way by at least [minCurve] of half that — which is what a circle's does,
     * and a line's never does.
     */
    private fun goingRound(from: Int, minSweep: Float, minCurve: Float): Boolean {
        val last = points.lastIndex
        if (last - from < 2) return false
        for (index in from..last) if (!inReach[index]) return false
        val sweptHere = swept[last] - swept[from]
        if (abs(sweptHere) < minSweep) return false
        val halfway = (travelled[from] + travelled[last]) / 2f
        var middle = from + 1
        while (middle < last - 1 && travelled[middle] < halfway) middle++
        val first = points[middle] - points[from]
        val second = points[last] - points[middle]
        if (first.getDistance() < 1f || second.getDistance() < 1f) return false
        val headingTurn = turnBetween(first, second)
        return headingTurn * sweptHere > 0f && headingTurn / (sweptHere / 2f) >= minCurve
    }

    /** The first point of the stretch a drag is watched over for becoming a circle. */
    private fun windowStart(): Int {
        val radius = (pointer - centre).getDistance()
        val window = max(radius * PromoteArc, reach)
        val end = travelled.last()
        var start = points.lastIndex
        while (start > 0 && end - travelled[start - 1] <= window) start--
        return start
    }

    /** Drops what is too far back to be in any window again. */
    private fun trim() {
        val keep = windowStart()
        if (keep < TrimAfter) return
        repeat(keep) {
            points.removeAt(0)
            travelled.removeAt(0)
            swept.removeAt(0)
            inReach.removeAt(0)
        }
    }

    private companion object {
        /** Inside this share of the track's radius, an angle round the middle is noise. */
        const val ReachShare: Float = 0.3f

        /** The arc, in radians of the start's radius, a gesture has to show itself in: 14°. */
        const val DecideArc: Float = 0.25f

        /** A turn has swept at least this many degrees when it is decided. */
        const val DecideSweep: Float = 12f

        /** And its heading turned by at least this share of half its sweep. */
        const val DecideCurve: Float = 0.5f

        /** The stretch a drag is watched over, in radians of its radius: 60°. */
        const val PromoteArc: Float = (PI / 3).toFloat()

        /** A drag becomes a turn once that stretch sweeps this far, curving. */
        const val PromoteSweep: Float = 45f
        const val PromoteCurve: Float = 0.6f

        /** Past this many points out of any window, the oldest are dropped. */
        const val TrimAfter: Int = 64

        const val DegreesPerRadian: Float = (180 / PI).toFloat()
    }
}

/**
 * The turn from [from] to [to] about the origin, in degrees, the short way round —
 * so a finger crossing the gap at the bottom, where the angle jumps from 180 to
 * −180, reads as the few degrees it moved. Positive is clockwise on screen.
 */
internal fun turnBetween(from: Offset, to: Offset): Float {
    val a = atan2(from.y, from.x) * 180f / PI.toFloat()
    val b = atan2(to.y, to.x) * 180f / PI.toFloat()
    var d = b - a
    while (d > HalfTurn) d -= FullTurn
    while (d < -HalfTurn) d += FullTurn
    return d
}

private const val HalfTurn: Float = 180f
private const val FullTurn: Float = 360f
