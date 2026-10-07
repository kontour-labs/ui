package io.kontour.ui.components.calendar

import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Modifier
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.exp
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import io.kontour.ui.interaction.DetentTicker
import io.kontour.ui.interaction.LandingFeedback
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

/** What a drag on the calendar is doing. */
internal enum class EditKind {
    /** Drawing out a new event on empty time. */
    Create,

    /** Carrying an event to another time, or another day. */
    Move,

    /** Dragging an event's start edge. */
    ResizeStart,

    /** Dragging its end edge. */
    ResizeEnd,
}

/**
 * The one edit a calendar can have in hand at a time — a new event drawn out,
 * an event carried, an edge dragged — and where the days are, to tell where a
 * pointer is.
 *
 * Self-contained rather than the library's drag and drop: a drop there says
 * only what landed, not where in a day it landed; it lifts a picture that
 * follows the pointer freely, where an event lands on whole steps of time; and
 * an edge dragged or a range drawn out is not a carry at all. Its pieces are
 * the same ones — the pick-up, the detents, the landing — so it is felt the
 * same.
 *
 * The proposal is state the layout and drawing read, and nothing composes
 * from: a frame of a drag moves the ghost without recomposing the calendar.
 */
@Stable
internal class EventEdit(
    val steps: DetentTicker,
    val landing: LandingFeedback,
    val pickedUp: () -> Unit,
) {
    /** What the edit is, while there is one. */
    var kind: EditKind? by mutableStateOf(null)
        private set

    /** The event being moved or resized; null for one being drawn out. */
    var event: CalendarEvent? by mutableStateOf(null)
        private set

    /** Where it would be put down now. */
    var start: LocalDateTime? by mutableStateOf(null)
        private set
    var end: LocalDateTime? by mutableStateOf(null)
        private set

    /** Whether the edit is of all-day days — a bar moved by whole days — rather than of time. */
    var byDays: Boolean = false
        private set

    /** Whether one is in hand. */
    val live: Boolean get() = kind != null

    /**
     * The event, on touch, whose edges have handles: the one its menu's
     * "Adjust time" was chosen on. Until a press anywhere else.
     */
    var active: Any? by mutableStateOf(null)

    /** The menu a hold or a secondary click opened, while it is open. */
    var menu: EditMenu? by mutableStateOf(null)

    /**
     * The pointer carrying the edit: followed from above the pages by the
     * calendar's own tracker — see `Modifier.carriesEdits` — so the carry
     * goes on into a period paged in, after the page it started on, and the
     * node that took hold in it, have gone.
     */
    var pointer: PointerId? by mutableStateOf(null)
        private set

    /** Whether it was taken up by a finger held still, rather than dragged up by a mouse. */
    var byHold: Boolean = false
        private set

    /** Whether the pointer has gone further than a held finger wanders since the pick-up. */
    var travelled: Boolean = false
        private set

    private var takenAt: Offset = Offset.Zero

    /**
     * How far the pointer is from where the proposal puts what it holds, in
     * pixels, at most half a step either way: what the ghost leans by, pulled,
     * as a range picker's end leans toward the finger between its days.
     */
    var lean: Offset by mutableStateOf(Offset.Zero)
        private set

    /** Which part of the ghost [lean] moves: -1 its top, 1 its bottom, 0 all of it. */
    var leanOn: Int = 0
        private set

    /**
     * The dates the calendar shows, read when asked: a pointer's day is one of
     * these, not one of a page sliding away. A function rather than a copy:
     * one kept from composition went stale at the first page a carry turned,
     * and the carry, finding none of its days, stopped following the finger.
     */
    var shown: () -> ClosedRange<LocalDate>? = { null }

    private val visible: ClosedRange<LocalDate>? get() = shown()

    /** The top, in the root, of the bar taken up by days: which line of its row the ghost keeps to. */
    var grabTop: Float? = null
        private set

    /** The minutes a step is: the calendar's `snap`. */
    var stepMinutes: Int = DefaultStepMinutes

    /** The pointer's last place, in the root, for the scroll near the edges to carry on from. */
    var lastPointer: Offset? by mutableStateOf(null)

    /** Where the pointer was in the event when it was taken hold of, in minutes from its start. */
    private var grab: Long = 0

    /** For a range being drawn out: the step it was started on. */
    private var anchor: Long = 0

    /** For a move by days: the day the pointer took hold of. */
    private var grabDay: LocalDate? = null

    /**
     * Each day's column of hours: where in the root its midnight is and how
     * wide it is. By the column rather than by its date: two pages side by side
     * can both show a day — a month's spare days — and the one going away,
     * keyed by date, took the one coming in with it.
     */
    val columns = HashMap<Any, DayPlace>()

    /** Each day's box in a month, or its place in a week's all-day row, by the box, as [columns]. */
    val days = HashMap<Any, DayPlace>()

    /** [date]'s column on show, or its box: of two, the one nearer the pointer. */
    private fun HashMap<Any, DayPlace>.of(date: LocalDate): LayoutCoordinates? {
        val near = lastPointer
        var best: LayoutCoordinates? = null
        var bestDistance = Float.MAX_VALUE
        for (place in values) {
            if (place.date != date || !place.coordinates.isAttached) continue
            val c = place.coordinates
            val distance = if (near == null) 0f else kotlin.math.abs(c.localToRoot(Offset.Zero).x + c.size.width / 2f - near.x)
            if (distance < bestDistance) {
                bestDistance = distance
                best = c
            }
        }
        return best
    }

    /** How many pixels a minute is in the hours. */
    var pixelsPerMinute: Float = 1f

    /** Whatever takes a mouse drag on the calendar: a pager declines a press on any of these. */
    val draggables = HashMap<Any, LayoutCoordinates>()

    /** Whether a press at [root] lands on something a mouse drags. */
    fun draggableAt(root: Offset): Boolean = draggables.values.any { it.isAttached && it.boundsInRoot().contains(root) }

    /**
     * The events, and their edges, that can be taken hold of: what empty time
     * under them steps aside for, since on a touchscreen both wait out the same
     * long press.
     */
    val events = HashMap<Any, Grab>()

    /** Whether a press at [root] lands on an event that can be taken hold of. */
    fun eventAt(root: Offset): Boolean = events.values.any { it.coordinates.isAttached && it.coordinates.boundsInRoot().contains(root) }

    /**
     * The event nearest [root] within [reach] of it, if any: for a finger held
     * beside a month's line of an event rather than on it — the line is under
     * half a fingertip tall.
     */
    fun eventNear(root: Offset, reach: Float): Grab? {
        var best: Grab? = null
        var bestDistance = Float.MAX_VALUE
        for (grab in events.values) {
            if (grab.edge || !grab.coordinates.isAttached) continue
            val bounds = grab.coordinates.boundsInRoot()
            val dx = maxOf(bounds.left - root.x, 0f, root.x - bounds.right)
            val dy = maxOf(bounds.top - root.y, 0f, root.y - bounds.bottom)
            val distance = dx + dy
            if (distance <= reach && distance < bestDistance) {
                bestDistance = distance
                best = grab
            }
        }
        return best
    }

    /** Whether [root] is on the event whose handles are showing, or on one of them. */
    fun onActive(root: Offset): Boolean {
        val id = active ?: return false
        return events.values.any { it.event.id == id && it.coordinates.isAttached && it.coordinates.boundsInRoot().contains(root) }
    }

    /** The pointer [id] carries the edit from here on, from [root]: held still first, or dragged. */
    fun take(id: PointerId, root: Offset, hold: Boolean) {
        pointer = id
        byHold = hold
        travelled = !hold
        takenAt = root
    }

    /**
     * The carrying pointer moved to [root]. A held finger's wander, within
     * [slop] of where it took hold, moves nothing: what it holds lifts in place,
     * and its menu stays, until the finger sets off.
     */
    fun carry(root: Offset, slop: Float) {
        if (!travelled) {
            if ((root - takenAt).getDistance() < slop) return
            travelled = true
            menu = null
        }
        move(root)
    }

    /** The carrying pointer let go, or went away: the tracker no longer follows it. */
    fun release() {
        pointer = null
    }

    /**
     * The day and wall-clock minute under [root] in the hours: the column it is
     * over, or the nearest one at either side — a pointer past the last day is
     * still on it until a dwell pages on.
     */
    fun timeAt(root: Offset, sameDayAs: LocalDate? = null): Pair<LocalDate, Long>? {
        val column = if (sameDayAs != null) {
            columns.of(sameDayAs)?.let { sameDayAs to it }
        } else {
            nearest(columns, root)
        } ?: return null
        val (date, coordinates) = column
        val top = coordinates.localToRoot(Offset.Zero).y
        val minute = ((root.y - top) / pixelsPerMinute).toLong()
        return date to date.atMidnight.epochMinute + minute
    }

    /** The day under [root] in a month or an all-day row, or the nearest. */
    fun dayAt(root: Offset): LocalDate? = nearest(days, root)?.first

    private fun nearest(of: Map<Any, DayPlace>, root: Offset): Pair<LocalDate, LayoutCoordinates>? {
        var best: Pair<LocalDate, LayoutCoordinates>? = null
        var bestDistance = Float.MAX_VALUE
        val shown = visible
        for ((date, coordinates) in of.values) {
            if (!coordinates.isAttached) continue
            if (shown != null && date !in shown) continue
            val bounds = Rect(coordinates.localToRoot(Offset.Zero), Size(coordinates.size.width.toFloat(), coordinates.size.height.toFloat()))
            if (bounds.width <= 0f) continue
            val dx = when {
                root.x < bounds.left -> bounds.left - root.x
                root.x > bounds.right -> root.x - bounds.right
                else -> 0f
            }
            val dy = when {
                root.y < bounds.top -> bounds.top - root.y
                root.y > bounds.bottom -> root.y - bounds.bottom
                else -> 0f
            }
            // Across first: the day is the column, wherever up or down it is.
            val distance = dx * Across + dy
            if (distance < bestDistance) {
                bestDistance = distance
                best = date to coordinates
            }
        }
        return best
    }

    /** Takes [event] up to move, held at [root] in the hours. */
    fun beginMove(event: CalendarEvent, root: Offset) {
        val (date, minute) = timeAt(root) ?: return
        grab = minute - event.start.epochMinute
        grabX = columns.of(date)?.let { root.x - it.localToRoot(Offset.Zero).x } ?: 0f
        begin(EditKind.Move, event, event.start, event.end, byDays = false)
        lastPointer = root
    }

    /** Where across its day's column the pointer took hold, for the ghost's lean. */
    private var grabX = 0f

    /** Where in its day's box the pointer took hold, for the ghost's lean. */
    private var grabInDay = Offset.Zero

    /** Takes [event] up to move by whole days, held at [root] over [day]. */
    fun beginMoveByDays(event: CalendarEvent, root: Offset, top: Float? = null) {
        val day = dayAt(root) ?: return
        grabDay = day
        overDay = day
        grabInDay = days.of(day)?.let { root - it.localToRoot(Offset.Zero) } ?: Offset.Zero
        grabTop = top
        begin(EditKind.Move, event, event.start, event.end, byDays = true)
        lastPointer = root
    }

    /** The day a carry by days is over now: the one it last settled on. */
    private var overDay: LocalDate? = null

    /**
     * The day under [root] for a carry by days, settled: it changes only once
     * the pointer is well into the next day — past [DayHysteresis] of it from
     * the edge it came in by — so a finger resting on the line between two days
     * does not flick the event back and forth between them.
     */
    private fun settledDayAt(root: Offset): LocalDate? {
        val near = dayAt(root) ?: return overDay
        val current = overDay
        if (current == null || near == current) return near.also { overDay = it }
        // A day paged away, or gone: nothing to hold on to.
        val shown = visible
        val was = days.of(current)?.takeIf { shown == null || current in shown } ?: return near.also { overDay = it }
        val box = days.of(near) ?: return near.also { overDay = it }
        val bounds = box.boundsInRoot()
        val from = was.boundsInRoot()
        // Into the next day only along the way it is next: across for a day
        // either side, down for a week on — a finger moving along a week is
        // not held back by where in its row it happens to be.
        val insetX = if (bounds.left != from.left) bounds.width * DayHysteresis else 0f
        val insetY = if (bounds.top != from.top) bounds.height * DayHysteresis else 0f
        val core = Rect(bounds.left + insetX, bounds.top + insetY, bounds.right - insetX, bounds.bottom - insetY)
        // Off every day — past the first or last, waiting for a dwell — the nearest is the one.
        val onAny = days.values.any { it.coordinates.isAttached && it.coordinates.boundsInRoot().contains(root) }
        return if (!onAny || core.contains(root)) near.also { overDay = it } else current
    }

    /** Takes [event] up from the keyboard: moved by keys rather than by a pointer. */
    fun beginKeyboard(event: CalendarEvent) {
        begin(EditKind.Move, event, event.start, event.end, byDays = false)
    }

    /** Takes hold of one of [event]'s edges. */
    fun beginResize(event: CalendarEvent, atStart: Boolean, root: Offset) {
        begin(if (atStart) EditKind.ResizeStart else EditKind.ResizeEnd, event, event.start, event.end, byDays = false)
        lastPointer = root
    }

    /**
     * Starts drawing out a new event from the step under [root], on [day]: an
     * hour of it, or [length] minutes, until the pointer moves and draws it.
     */
    fun beginCreate(day: LocalDate, root: Offset, length: Int = stepMinutes) {
        val (_, minute) = timeAt(root, sameDayAs = day) ?: return
        val dayStart = day.atMidnight.epochMinute
        anchor = floorTo(minute, stepMinutes).coerceIn(dayStart, dayStart + MinutesPerDay - stepMinutes)
        createDay = day
        val end = minOf(anchor + maxOf(length, stepMinutes), dayStart + MinutesPerDay)
        begin(EditKind.Create, null, localDateTimeOfEpochMinute(anchor), localDateTimeOfEpochMinute(end), byDays = false)
        lastPointer = root
    }

    /** Starts drawing out a new day, or days, from the day under [root]. */
    fun beginCreateDays(root: Offset) {
        val day = dayAt(root) ?: return
        grabDay = day
        overDay = day
        grabTop = null
        begin(EditKind.Create, null, day.atMidnight, day.atMidnight.plusMinutes(MinutesPerDay.toLong()), byDays = true)
        lastPointer = root
    }

    private var createDay: LocalDate? = null

    private fun begin(kind: EditKind, event: CalendarEvent?, start: LocalDateTime, end: LocalDateTime, byDays: Boolean) {
        lean = Offset.Zero
        // Another event taken up, or a new one begun: the handles go.
        if (event?.id != active) active = null
        this.kind = kind
        this.event = event
        this.start = start
        this.end = end
        this.byDays = byDays
        steps.reset()
        steps.at(stepIndex(start))
    }

    /** The pointer is now at [root]: the proposal follows it, a step at a time. */
    fun move(root: Offset) {
        lastPointer = root
        val kind = kind ?: return
        val event = event
        val perMinute = pixelsPerMinute
        val halfStep = stepMinutes * perMinute / 2f
        when (kind) {
            EditKind.Move -> if (event != null) {
                if (byDays) {
                    val from = grabDay ?: return
                    val to = settledDayAt(root) ?: return
                    val days = to.toEpochDays() - from.toEpochDays()
                    leanOn = 0
                    lean = dayLean(to, root)
                    propose(event.start.plusMinutes(days * MinutesPerDay), event.end.plusMinutes(days * MinutesPerDay))
                } else {
                    val (date, minute) = timeAt(root) ?: return
                    val length = event.end.epochMinute - event.start.epochMinute
                    val raw = minute - grab
                    val start = roundTo(raw, stepMinutes)
                    val column = columns.of(date)
                    val across = if (column == null) 0f else {
                        val half = column.size.width / 2f
                        (root.x - column.localToRoot(Offset.Zero).x - grabX).coerceIn(-half, half)
                    }
                    leanOn = 0
                    lean = Offset(across, ((raw - start) * perMinute).coerceIn(-halfStep, halfStep))
                    propose(localDateTimeOfEpochMinute(start), localDateTimeOfEpochMinute(start + length))
                }
            }
            // An edge keeps to its own day: past midnight, or past the top of
            // the hours, it stops there. It took the column the pointer was
            // nearest, and an end dragged low ran the event on over days.
            EditKind.ResizeEnd -> if (event != null) {
                val day = endDay(event.start, event.end)
                val (_, raw) = timeAt(root, sameDayAs = day) ?: return
                val dayStart = day.atMidnight.epochMinute
                val minute = raw.coerceIn(dayStart, dayStart + MinutesPerDay)
                val end = resizedEnd(event.start.epochMinute, event.end.epochMinute, roundTo(minute, stepMinutes), stepMinutes)
                leanOn = 1
                lean = Offset(0f, ((minute - end) * perMinute).coerceIn(-halfStep, halfStep))
                propose(event.start, localDateTimeOfEpochMinute(end))
            }
            EditKind.ResizeStart -> if (event != null) {
                val (_, raw) = timeAt(root, sameDayAs = event.start.date) ?: return
                val dayStart = event.start.date.atMidnight.epochMinute
                val minute = raw.coerceIn(dayStart, dayStart + MinutesPerDay)
                val start = resizedStart(event.start.epochMinute, event.end.epochMinute, roundTo(minute, stepMinutes), stepMinutes)
                leanOn = -1
                lean = Offset(0f, ((minute - start) * perMinute).coerceIn(-halfStep, halfStep))
                propose(localDateTimeOfEpochMinute(start), event.end)
            }
            EditKind.Create -> if (byDays) {
                val from = grabDay ?: return
                val to = settledDayAt(root) ?: return
                val first = minOf(from, to)
                val last = maxOf(from, to)
                propose(first.atMidnight, last.atMidnight.plusMinutes(MinutesPerDay.toLong()))
            } else {
                val day = createDay ?: return
                val (_, minute) = timeAt(root, sameDayAs = day) ?: return
                val dayStart = day.atMidnight.epochMinute
                val head = minute.coerceIn(dayStart, dayStart + MinutesPerDay)
                val (from, to) = if (head >= anchor) {
                    anchor to maxOf(roundTo(head, stepMinutes), anchor + stepMinutes)
                } else {
                    floorTo(head, stepMinutes) to anchor + stepMinutes
                }
                val edge = if (head >= anchor) to else from
                leanOn = if (head >= anchor) 1 else -1
                lean = Offset(0f, ((head - edge) * perMinute).coerceIn(-halfStep, halfStep))
                propose(localDateTimeOfEpochMinute(from), localDateTimeOfEpochMinute(minOf(to, dayStart + MinutesPerDay)))
            }
        }
    }

    /** How far [root] is from where it took hold of its day, in [day]'s box: at most half the box either way. */
    private fun dayLean(day: LocalDate, root: Offset): Offset {
        val box = days.of(day) ?: return Offset.Zero
        val origin = box.localToRoot(Offset.Zero)
        val halfX = box.size.width / 2f
        val halfY = box.size.height / 2f
        return Offset(
            (root.x - origin.x - grabInDay.x).coerceIn(-halfX, halfX),
            (root.y - origin.y - grabInDay.y).coerceIn(-halfY, halfY),
        )
    }

    /** The day an event's last minute is on — for an edge dragged across midnight. */
    private fun endDay(start: LocalDateTime, end: LocalDateTime): LocalDate =
        if (end > start) end.plusMinutes(-1).date else start.date

    /** Moves the proposal by keys: [minutes] either way, or only its end when [resizeEnd]. */
    fun nudge(minutes: Long, resizeEnd: Boolean = false) {
        val start = start ?: return
        val end = end ?: return
        if (resizeEnd) {
            val from = event ?: return
            val next = resizedEnd(from.start.epochMinute, from.end.epochMinute, end.epochMinute + minutes, stepMinutes)
            propose(start, localDateTimeOfEpochMinute(next))
        } else {
            propose(start.plusMinutes(minutes), end.plusMinutes(minutes))
        }
    }

    private fun propose(start: LocalDateTime, end: LocalDateTime) {
        if (start == this.start && end == this.end) return
        this.start = start
        this.end = end
        // Felt once a step, whichever edge moved.
        steps.at(stepIndex(if (kind == EditKind.ResizeEnd) end else start))
    }

    /** A detent a step of the snap — or, carried by days, a day: one tick a day, not a day's worth. */
    private fun stepIndex(at: LocalDateTime): Int =
        if (byDays) (at.epochMinute / MinutesPerDay).toInt() else (at.epochMinute / stepMinutes).toInt()

    /** Puts it down: what changed, for the calendar to report — null where it landed where it was. */
    fun finish(): Finished? {
        val kind = kind
        val start = start
        val end = end
        val event = event
        clear()
        if (kind == null || start == null || end == null) return null
        if (event != null && event.start == start && event.end == end) return null
        landing.landed()
        return Finished(kind, event, start, end)
    }

    /** Puts it back, whatever it was. */
    fun cancel() {
        clear()
    }

    private fun clear() {
        kind = null
        event = null
        start = null
        end = null
        byDays = false
        grabDay = null
        overDay = null
        createDay = null
        lastPointer = null
        pointer = null
        byHold = false
        travelled = false
        grabTop = null
        lean = Offset.Zero
        steps.reset()
    }

    /** An edit put down: what it was, on which event, and where it ended. */
    class Finished(val kind: EditKind, val event: CalendarEvent?, val start: LocalDateTime, val end: LocalDateTime)

    private companion object {
        const val DefaultStepMinutes = 15

        /** Across counts this many times up and down, finding a pointer's day. */
        const val Across = 8f

        /** How far into the next day a carry by days goes before it is over it: a share of the day. */
        const val DayHysteresis = 0.15f
    }
}

/** A day's column of hours or its box, and the date it is. */
internal data class DayPlace(val date: LocalDate, val coordinates: LayoutCoordinates)

/**
 * Something a pointer can take hold of: an event, or one of its edges.
 *
 * @property byDays Whether it is carried by whole days.
 * @property edge Whether it is an edge's handle rather than the event.
 */
internal class Grab(var coordinates: LayoutCoordinates, var event: CalendarEvent, val byDays: Boolean, val edge: Boolean = false)

/**
 * A menu opened on the calendar: by a finger held on an event, or on empty
 * time, or by a secondary click.
 *
 * @property at Where it hangs from, in the root.
 * @property event The event it is for; null for one on empty time, whose
 *   "New event" makes what the edit in hand proposed as it opened.
 * @property inHours Whether the event is in the day's hours, where its times
 *   can be adjusted by its edges.
 */
internal class EditMenu(val at: Offset, val event: CalendarEvent?, val inHours: Boolean) {
    /**
     * For a new event, where it would be made: taken as the menu opens, since
     * an item chosen closes the menu — and so lets go of the edit — before it
     * runs.
     */
    var start: LocalDateTime? = null
    var end: LocalDateTime? = null
}

/** Where the last press on a node went down, in the node: read when it is picked up. Not state. */
internal class PressAt {
    var position: Offset? = null
}

/**
 * Notes where each press goes down — seen first, before anything else takes
 * it — so a pick-up that waited for the pointer's slop measures from where the
 * pointer took hold rather than from where it had got to: an event that trails
 * a mouse by the slop is one that slides out from under it.
 */
internal fun Modifier.notesPress(at: PressAt): Modifier = pointerInput(at) {
    awaitEachGesture {
        at.position = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial).position
    }
}


/**
 * A ghost's hop from one step to the next: where it was drawn to where the
 * proposal now puts it, short and with no overshoot, so it neither trails the
 * pointer within a step nor runs back past where it was.
 *
 * Decided in layout, in the pass that first sees the new step, from the place
 * the ghost was last drawn: a hop decided in an effect a frame later drew the
 * new place first and then jumped back to start from the old. Driven by
 * [frame], which [run] ticks only while a hop is under way.
 */
@Stable
internal class GhostHop {
    /** The frame time the hop is read at: state, so a hop lays the ghost out each frame. */
    var frame by mutableLongStateOf(0L)
        private set

    /** Whether a hop is under way. */
    var hopping by mutableStateOf(false)
        private set

    private var key: Any? = null
    private var from = Rect.Zero
    private var start = NotStarted
    private var last: Rect? = null

    /**
     * Where to draw a ghost whose proposal is [key], at [target]: there, or on
     * its way there from the last place it was drawn. [rebase], given that
     * place and the target, can say where to come in from instead, as a delta
     * from the target — a week's wrap, from beside rather than across the row.
     */
    fun drawnAt(key: Any, target: Rect, still: Boolean, rebase: (last: Rect, target: Rect) -> Rect? = { _, _ -> null }): Rect {
        if (key != this.key) {
            val prior = last
            if (this.key != null && prior != null && !still) {
                from = rebase(prior, target) ?: Rect(prior.left - target.left, prior.top - target.top, prior.right - target.right, prior.bottom - target.bottom)
                start = NotStarted
                if (!hopping) hopping = true
            } else {
                from = Rect.Zero
            }
            this.key = key
        }
        val f = if (!hopping || still) 0f else if (start == NotStarted) 1f else settle((frame - start) / NanosPerSecond)
        val drawn = Rect(target.left + from.left * f, target.top + from.top * f, target.right + from.right * f, target.bottom + from.bottom * f)
        last = drawn
        return drawn
    }

    /** Ticks [frame] for as long as a hop is under way, and nothing between hops. */
    suspend fun run() {
        snapshotFlow { hopping }.collect { on ->
            if (!on) return@collect
            while (true) {
                val now = withFrameNanos { it }
                if (start == NotStarted) start = now
                frame = now
                if (now - start >= HopNanos) {
                    hopping = false
                    break
                }
            }
        }
    }

    private companion object {
        const val NotStarted = Long.MIN_VALUE
        const val NanosPerSecond = 1_000_000_000f

        /** About a tenth of a second to land, a critically damped spring's. */
        const val Omega = 60f
        const val HopNanos = 150_000_000L

        /** What is left of a hop [seconds] in: critically damped, from all of it to none, never past. */
        fun settle(seconds: Float): Float = ((1f + Omega * seconds) * exp(-Omega * seconds)).coerceIn(0f, 1f)
    }
}
