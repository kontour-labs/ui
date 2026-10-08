package io.kontour.ui.components.calendar

import androidx.compose.runtime.Immutable
import io.kontour.ui.theme.Tone
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.plus

/**
 * One thing on an [EventCalendar]: a meeting, a trip, a holiday.
 *
 * ```kotlin
 * CalendarEvent(id = 1, title = "Stand-up", start = LocalDateTime(2026, 10, 14, 9, 0), end = LocalDateTime(2026, 10, 14, 9, 15))
 * CalendarEvent(id = 2, title = "Perth trip", startDate = LocalDate(2026, 10, 14), endDate = LocalDate(2026, 10, 16), tone = Tone.Success)
 * ```
 *
 * ### Wall-clock time
 *
 * [start] and [end] are times on the reader's clock, not instants: the calendar
 * draws 24 rows a day whatever the clocks did that night, and a meeting moved
 * to ten o'clock is at ten o'clock. An app that keeps its events as instants
 * converts once, `instant.toLocalDateTime(TimeZone.currentSystemDefault())`,
 * as it hands them over.
 *
 * ### Half-open
 *
 * Every event runs from [start] up to, but not including, [end]: one that ends
 * at ten and one that starts at ten do not overlap, and an all-day event on the
 * 14th runs from the 14th's midnight to the 15th's. The date constructor takes
 * the last day itself, as people say it, and does that sum.
 *
 * @property id What the event is known by across changes: a moved event comes
 *   back with the same id. Any value with a meaningful `equals`.
 * @property title What it is called: what its box shows first, and what a
 *   screen reader says first.
 * @property start When it begins, on the reader's clock: a wall-clock time,
 *   with no zone, as a calendar shows one.
 * @property end Not before [start]. An event with no length — a reminder at
 *   nine — is drawn a quarter-hour tall, the least an event is drawn.
 * @property allDay Whether it is a day or days rather than a time: drawn as a
 *   bar across its days, above the hours in the day and week views.
 * @property tone Its colour: the accent by default, any [Tone] that says what
 *   kind of thing it is.
 * @property location Where, read out and shown under the title where there is room.
 * @property supporting A line more about it, shown where there is room.
 * @property editable Whether a drag or a keyboard can move it or change its
 *   length, where the calendar offers those at all. False for an event the app
 *   only shows — a public holiday from a subscribed calendar.
 */
@Immutable
class CalendarEvent(
    val id: Any,
    val title: String,
    val start: LocalDateTime,
    val end: LocalDateTime,
    val allDay: Boolean = false,
    val tone: Tone = Tone.Accent,
    val location: String? = null,
    val supporting: String? = null,
    val editable: Boolean = true,
) {
    /**
     * An all-day event from [startDate] to [endDate], both days included — as
     * people say it, "the 14th to the 16th".
     *
     * @param id What the event is known by across changes.
     * @param title What it is called.
     * @param startDate Its first day.
     * @param endDate Its last day, itself included: the same day for a day's event.
     * @param tone Its colour.
     * @param location Where, read out and shown under the title where there is room.
     * @param supporting A line more about it, shown where there is room.
     * @param editable Whether a drag or a keyboard can move it or change its length.
     */
    constructor(
        id: Any,
        title: String,
        startDate: LocalDate,
        endDate: LocalDate = startDate,
        tone: Tone = Tone.Accent,
        location: String? = null,
        supporting: String? = null,
        editable: Boolean = true,
    ) : this(
        id = id,
        title = title,
        start = startDate.atMidnight,
        end = endDate.plus(1, DateTimeUnit.DAY).atMidnight,
        allDay = true,
        tone = tone,
        location = location,
        supporting = supporting,
        editable = editable,
    )

    init {
        require(end >= start) {
            "CalendarEvent: `end` ($end) is before `start` ($start) for \"$title\". An event runs from " +
                "its start up to its end; give the end the later of the two."
        }
    }

    /** This event with some of its fields changed — a moved one, say. */
    fun copy(
        id: Any = this.id,
        title: String = this.title,
        start: LocalDateTime = this.start,
        end: LocalDateTime = this.end,
        allDay: Boolean = this.allDay,
        tone: Tone = this.tone,
        location: String? = this.location,
        supporting: String? = this.supporting,
        editable: Boolean = this.editable,
    ): CalendarEvent = CalendarEvent(id, title, start, end, allDay, tone, location, supporting, editable)

    override fun equals(other: Any?): Boolean =
        other is CalendarEvent &&
            id == other.id && title == other.title && start == other.start && end == other.end &&
            allDay == other.allDay && tone == other.tone && location == other.location &&
            supporting == other.supporting && editable == other.editable

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + title.hashCode()
        result = 31 * result + start.hashCode()
        result = 31 * result + end.hashCode()
        result = 31 * result + allDay.hashCode()
        result = 31 * result + tone.hashCode()
        result = 31 * result + (location?.hashCode() ?: 0)
        result = 31 * result + (supporting?.hashCode() ?: 0)
        result = 31 * result + editable.hashCode()
        return result
    }

    override fun toString(): String = "CalendarEvent(id=$id, title=$title, start=$start, end=$end, allDay=$allDay)"
}

/** Which of its shapes an event is drawn in — for an [EventCalendar]'s own `eventContent`. */
enum class EventPresentation {
    /** A timed event in a day's column of hours: as tall as it is long. */
    Block,

    /** An all-day or several-day event: a bar across its days. */
    Bar,

    /** A timed event in a month's day: one line, its time and its title. */
    Line,
}
