package io.kontour.ui.components.selection

import androidx.compose.ui.geometry.Offset
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sign
import kotlin.math.sin

/**
 * Reads a drag on a [Knob] as the turn it means — a drag in a line, or the finger
 * going round — and says how much of the range each move is worth.
 *
 * Modelled on Apple's GarageBand knob, the one most people have used: circles turn
 * it, and a straight drag **locks into a slider along its axis** — vertical or
 * horizontal — whose direction depends on where on the knob it pulls. Here that is
 * decided at **the notch**, where the knob is: "if it started on the very end … and
 * I try to drag it up, I'm expecting … to pull it back around to 0".
 *
 * ### A drag pulls the notch, and keeps pulling
 *
 * The first stretch of a straight drag decides its direction: whichever way it
 * moves the notch along the arc — clockwise more, anticlockwise less. At the end of
 * the scale, low on the right, up pulls the notch back towards the top, so it is
 * less. Where the drag runs straight across the arc at the notch — up or down with
 * the notch at the top — it pulls neither way, and the ordinary rule decides: up or
 * right is more. So does a pull into the end stop the value is already at, so that
 * right at zero is still more, and nothing a finger does at an end is refused.
 *
 * Then it is a slider along the axis it started on, in the direction it chose:
 * carried on, it keeps turning the same way — past the top and round — "pull the
 * knob up, but then keep pulling it in the same direction"; brought back, it turns
 * back.
 *
 * ### Going round
 *
 * A finger going round the knob curves, its heading turning as fast as it sweeps
 * round the middle; a finger going in a line does not curve at all. So the shape of
 * the first stretch also says whether it is a turn, and a turn follows the finger's
 * angle — a turn of [sweep] degrees is the whole range. A drag that carries on into
 * a circle becomes a turn, and a turn stays one until the finger lifts.
 *
 * Until it has decided, the knob follows the two readings where they agree and
 * holds where they disagree, and at the decision it makes up whatever the decided
 * reading says it should have done. Neither reading goes the wrong way first, and
 * nothing a finger did is lost. Near the middle, an angle is noise, and a gesture
 * there is a drag.
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
    private var begin = Offset.Zero

    /** The value when the gesture began, as a share of the range. */
    private var fraction = 0f

    /** Where the notch was, in degrees clockwise from three o'clock. */
    private var notch = 0f

    /** What has been handed out so far while undecided, and what a turn would have. */
    private var given = 0f
    private var turned = 0f

    /** A decided drag: the axis it runs along, and which way along it is more. */
    private var axis = Offset.Zero
    private var sense = 1f

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
     * A new gesture at [at], on a dial whose middle is [centre], whose track's radius
     * is [radius] and whose scale starts at [start] degrees and runs [sweep], with
     * the value at [fraction] of it. The first [move] after this is the one that
     * brought the finger to [at], as a drag's claim hands it over.
     */
    fun start(
        at: Offset,
        centre: Offset,
        radius: Float,
        start: Float,
        sweep: Float,
        fraction: Float,
        travel: Float,
        decideAfter: Float,
    ) {
        mode = Mode.Undecided
        this.centre = centre
        reach = radius * ReachShare
        this.sweep = sweep
        this.fraction = fraction.coerceIn(0f, 1f)
        notch = start + sweep * this.fraction
        this.travel = travel
        // A slow circle on a big knob covers little angle per dp; give it the same
        // arc to show itself in as a small one.
        this.decideAfter = max(decideAfter, radius * DecideArc)
        fresh = true
        pointer = at
        given = 0f
        turned = 0f
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
            begin = from
            record(from, 0f, 0f, (from - centre).getDistance() >= reach)
            fresh = false
        }
        pointer = to

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
                along(delta)
            }
            Mode.Undecided -> {
                turned += turn
                val dragged = draggedSoFar()
                val out = if (dragged == 0f || turned == 0f || sign(dragged) == sign(turned)) {
                    dragged - given
                } else {
                    0f
                }
                given += out
                if (travelled.last() < decideAfter) {
                    out
                } else if (goingRound(0, DecideSweep, DecideCurve)) {
                    mode = Mode.Turn
                    out + settle(turned)
                } else {
                    decideDrag()
                    out + settle(draggedSoFar())
                }
            }
        }
    }

    /**
     * How fast, as a share of the range a second, a release at [velocity] turns the
     * knob, read the way the gesture was: round the middle for a turn, along its axis
     * for a drag. [minimumSpeed] is how fast the finger has to be moving, along
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
        if (mode == Mode.Undecided) decideDrag()
        val speed = velocity.x * axis.x + velocity.y * axis.y
        return if (abs(speed) < minimumSpeed) null else sense * speed / travel
    }

    /** What is owed at a decision: the decided reading's total less what was given. */
    private fun settle(total: Float): Float {
        val owed = total - given
        given = total
        return owed
    }

    /** A decided drag's worth of [delta]: along its axis, the way it chose. */
    private fun along(delta: Offset): Float = sense * (delta.x * axis.x + delta.y * axis.y) / travel

    /** What the straight drag so far would have turned the knob, read as it would decide now. */
    private fun draggedSoFar(): Float {
        val net = pointer - begin
        if (net.getDistance() < 1f) return 0f
        val (onAxis, pull) = dragOf(net)
        return pull * (net.x * onAxis.x + net.y * onAxis.y) / travel
    }

    private fun decideDrag() {
        mode = Mode.Drag
        val net = pointer - begin
        val (onAxis, pull) = dragOf(if (net.getDistance() < 1f) Offset(0f, -1f) else net)
        axis = onAxis
        sense = pull
    }

    /**
     * The axis a drag of [net] runs along — up or right, whichever it mostly is — and
     * which way along it is more: the way it pulls the notch round the arc, or, where
     * it pulls neither way or would pull into the end the value is already at, up or
     * right.
     */
    private fun dragOf(net: Offset): Pair<Offset, Float> {
        val onAxis = if (abs(net.y) >= abs(net.x)) Up else Right
        val forward = sign(net.x * onAxis.x + net.y * onAxis.y).takeIf { it != 0f } ?: 1f
        val radians = notch * PI.toFloat() / 180f
        // Clockwise round the dial at the notch, on screen, where y runs down.
        val tangent = Offset(-sin(radians), cos(radians))
        val length = net.getDistance()
        val pulling = (net.x * tangent.x + net.y * tangent.y) / length
        val round = when {
            abs(pulling) < PullShare -> forward
            pulling > 0f && fraction >= 1f - AtEnd -> forward
            pulling < 0f && fraction <= AtEnd -> forward
            else -> sign(pulling)
        }
        // Which way along the axis is more: the way the drag went, if that is the way
        // it turns the knob, and the other way if not.
        return onAxis to round * forward
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

        /**
         * How much of a drag has to run along the arc at the notch for it to pull
         * the notch — within 60° of the arc. Less, and it runs across the arc.
         */
        const val PullShare: Float = 0.5f

        /** Within this of an end, the value is at it. */
        const val AtEnd: Float = 0.001f

        /** Past this many points out of any window, the oldest are dropped. */
        const val TrimAfter: Int = 64

        const val DegreesPerRadian: Float = (180 / PI).toFloat()

        /** A drag's two axes, pointing the way that is more by the ordinary rule. */
        val Up = Offset(0f, -1f)
        val Right = Offset(1f, 0f)
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
