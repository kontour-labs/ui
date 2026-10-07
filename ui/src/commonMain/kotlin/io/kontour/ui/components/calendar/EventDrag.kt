package io.kontour.ui.components.calendar

import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Modifier
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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

    /** The event, on touch, whose edges have handles: one held and let go without moving. */
    var active: Any? by mutableStateOf(null)

    /** The minutes a step is: the calendar's `snap`. */
    var stepMinutes: Int = DefaultStepMinutes

    /** The pointer's last place, in the root, for the scroll near the edges to carry on from. */
    var lastPointer: Offset? = null

    /** Where the pointer was in the event when it was taken hold of, in minutes from its start. */
    private var grab: Long = 0

    /** For a range being drawn out: the step it was started on. */
    private var anchor: Long = 0

    /** For a move by days: the day the pointer took hold of. */
    private var grabDay: LocalDate? = null

    /** Each day's column of hours, by date: where in the root its midnight is and how wide it is. */
    val columns = HashMap<LocalDate, LayoutCoordinates>()

    /** Each day's box in a month, or its place in a week's all-day row, by date. */
    val days = HashMap<LocalDate, LayoutCoordinates>()

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
    val events = HashMap<Any, LayoutCoordinates>()

    /** Whether a press at [root] lands on an event that can be taken hold of. */
    fun eventAt(root: Offset): Boolean = events.values.any { it.isAttached && it.boundsInRoot().contains(root) }

    /**
     * The day and wall-clock minute under [root] in the hours: the column it is
     * over, or the nearest one at either side — a pointer past the last day is
     * still on it until a dwell pages on.
     */
    fun timeAt(root: Offset, sameDayAs: LocalDate? = null): Pair<LocalDate, Long>? {
        val column = if (sameDayAs != null) {
            columns[sameDayAs]?.takeIf { it.isAttached }?.let { sameDayAs to it }
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

    private fun nearest(of: Map<LocalDate, LayoutCoordinates>, root: Offset): Pair<LocalDate, LayoutCoordinates>? {
        var best: Pair<LocalDate, LayoutCoordinates>? = null
        var bestDistance = Float.MAX_VALUE
        for ((date, coordinates) in of) {
            if (!coordinates.isAttached) continue
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
        val (_, minute) = timeAt(root) ?: return
        grab = minute - event.start.epochMinute
        begin(EditKind.Move, event, event.start, event.end, byDays = false)
        lastPointer = root
    }

    /** Takes [event] up to move by whole days, held at [root] over [day]. */
    fun beginMoveByDays(event: CalendarEvent, root: Offset) {
        grabDay = dayAt(root) ?: return
        overDay = grabDay
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
        val box = days[near]?.takeIf { it.isAttached } ?: return near.also { overDay = it }
        val bounds = box.boundsInRoot()
        val insetX = bounds.width * DayHysteresis
        val insetY = bounds.height * DayHysteresis
        val core = Rect(bounds.left + insetX, bounds.top + insetY, bounds.right - insetX, bounds.bottom - insetY)
        // Off every day — past the first or last, waiting for a dwell — the nearest is the one.
        val onAny = days.values.any { it.isAttached && it.boundsInRoot().contains(root) }
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

    /** Starts drawing out a new event from the step under [root], on [day]. */
    fun beginCreate(day: LocalDate, root: Offset) {
        val (_, minute) = timeAt(root, sameDayAs = day) ?: return
        val dayStart = day.atMidnight.epochMinute
        anchor = floorTo(minute, stepMinutes).coerceIn(dayStart, dayStart + MinutesPerDay - stepMinutes)
        createDay = day
        begin(EditKind.Create, null, localDateTimeOfEpochMinute(anchor), localDateTimeOfEpochMinute(anchor + stepMinutes), byDays = false)
        lastPointer = root
    }

    private var createDay: LocalDate? = null

    private fun begin(kind: EditKind, event: CalendarEvent?, start: LocalDateTime, end: LocalDateTime, byDays: Boolean) {
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
        when (kind) {
            EditKind.Move -> if (event != null) {
                if (byDays) {
                    val from = grabDay ?: return
                    val to = settledDayAt(root) ?: return
                    val days = to.toEpochDays() - from.toEpochDays()
                    propose(event.start.plusMinutes(days * MinutesPerDay), event.end.plusMinutes(days * MinutesPerDay))
                } else {
                    val (_, minute) = timeAt(root) ?: return
                    val length = event.end.epochMinute - event.start.epochMinute
                    val start = roundTo(minute - grab, stepMinutes)
                    propose(localDateTimeOfEpochMinute(start), localDateTimeOfEpochMinute(start + length))
                }
            }
            EditKind.ResizeEnd -> if (event != null) {
                val (_, minute) = timeAt(root, sameDayAs = this.end?.let { endDay(event.start, it) }) ?: timeAt(root) ?: return
                val end = maxOf(roundTo(minute, stepMinutes), event.start.epochMinute + stepMinutes)
                propose(event.start, localDateTimeOfEpochMinute(end))
            }
            EditKind.ResizeStart -> if (event != null) {
                val (_, minute) = timeAt(root, sameDayAs = event.start.date) ?: timeAt(root) ?: return
                val start = minOf(roundTo(minute, stepMinutes), event.end.epochMinute - stepMinutes)
                propose(localDateTimeOfEpochMinute(start), event.end)
            }
            EditKind.Create -> {
                val day = createDay ?: return
                val (_, minute) = timeAt(root, sameDayAs = day) ?: return
                val dayStart = day.atMidnight.epochMinute
                val head = minute.coerceIn(dayStart, dayStart + MinutesPerDay)
                val (from, to) = if (head >= anchor) {
                    anchor to maxOf(roundTo(head, stepMinutes), anchor + stepMinutes)
                } else {
                    floorTo(head, stepMinutes) to anchor + stepMinutes
                }
                propose(localDateTimeOfEpochMinute(from), localDateTimeOfEpochMinute(minOf(to, dayStart + MinutesPerDay)))
            }
        }
    }

    /** The day an event's last minute is on — for an edge dragged across midnight. */
    private fun endDay(start: LocalDateTime, end: LocalDateTime): LocalDate =
        if (end > start) end.plusMinutes(-1).date else start.date

    /** Moves the proposal by keys: [minutes] either way, or only its end when [resizeEnd]. */
    fun nudge(minutes: Long, resizeEnd: Boolean = false) {
        val start = start ?: return
        val end = end ?: return
        if (resizeEnd) {
            val next = maxOf(end.epochMinute + minutes, start.epochMinute + stepMinutes)
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

