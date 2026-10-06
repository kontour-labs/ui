package io.kontour.ui.components.calendar

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/**
 * One event in one week's row of days: a bar across the days it spans, or a
 * line in the one day it is on.
 *
 * @property from The first of its days in the week, 0 to 6 in reading order.
 * @property to The last, inclusive.
 * @property bar Whether it is drawn as a bar — all day, or more than one day —
 *   rather than as a line with its time.
 * @property continuesBefore Whether it began before the week: its start edge
 *   is square, the way a thing cut off is.
 * @property continuesAfter The same at its end.
 */
internal class WeekItem(
    val event: CalendarEvent,
    val from: Int,
    val to: Int,
    val bar: Boolean,
    val continuesBefore: Boolean,
    val continuesAfter: Boolean,
)

/** Where a [WeekItem] is drawn: which row of the week, 0 at the top. */
internal class WeekPlacement(val item: WeekItem, val row: Int)

/**
 * A week packed into rows.
 *
 * @property placed What is shown, and in which row.
 * @property more For each day, how many of its events are not shown: a "+N"
 *   in its last row.
 * @property shownRows For each day, how many rows its shown events may use; the
 *   "+N", where there is one, is in the row after.
 */
internal class WeekPack(val placed: List<WeekPlacement>, val more: IntArray, val shownRows: IntArray)

/**
 * The events in the week from [weekStart], as [WeekItem]s.
 *
 * With [lines], a month's week: every event, the ones on a single day as
 * lines. Without, the strip above a week's hours: only what is not in the
 * hours — all day, or a whole day long or longer.
 */
internal fun weekItems(weekStart: LocalDate, events: List<CalendarEvent>, lines: Boolean): List<WeekItem> {
    val weekFrom = weekStart.toEpochDays()
    val weekTo = weekFrom + DaysPerWeek - 1
    val items = ArrayList<WeekItem>()
    for (event in events) {
        val firstDay = event.start.date.toEpochDays()
        // The day its last minute is on: an event ending at midnight ends the day before.
        val lastDay = if (event.end > event.start) {
            event.end.plusMinutes(-1).date.toEpochDays()
        } else {
            firstDay
        }
        if (lastDay < weekFrom || firstDay > weekTo) continue
        // Shorter than a day and timed, it is in the hours — or a line, in a month.
        if (!lines && event.isTimed()) continue
        val bar = event.allDay || lastDay > firstDay || !event.isTimed()
        items += WeekItem(
            event = event,
            from = (maxOf(firstDay, weekFrom) - weekFrom).toInt(),
            to = (minOf(lastDay, weekTo) - weekFrom).toInt(),
            bar = bar,
            continuesBefore = firstDay < weekFrom,
            continuesAfter = lastDay > weekTo,
        )
    }
    return items
}

/**
 * [items] in rows, at most [maxRows] of them a day.
 *
 * Bars first — earliest, then longest — then lines by time, each in the
 * lowest row free across all its days, so a bar is one straight bar. A day
 * with more than fits shows one row fewer and a "+N" in the last; a bar that
 * would cross such a day's "+N" is not shown at all rather than broken, and
 * counts in every day it would have crossed. [maxRows] may be 0, where a day
 * is too short for even one: nothing is shown, and every day counts all of its own.
 */
internal fun packWeek(items: List<WeekItem>, maxRows: Int): WeekPack {
    val ordered = items.withIndex().sortedWith(
        compareBy<IndexedValue<WeekItem>>(
            { if (it.value.bar) 0 else 1 },
            { it.value.from },
            { -(it.value.to - it.value.from) },
            { if (it.value.bar) 0L else it.value.event.start.epochMinute },
            { it.index },
        ),
    ).map { it.value }

    // Packed with no limit first: rows as deep as the busiest day needs.
    val rows = ArrayList<BooleanArray>()
    val rowOf = HashMap<WeekItem, Int>()
    for (item in ordered) {
        var row = rows.indexOfFirst { taken -> (item.from..item.to).none { taken[it] } }
        if (row < 0) {
            row = rows.size
            rows += BooleanArray(DaysPerWeek)
        }
        for (day in item.from..item.to) rows[row][day] = true
        rowOf[item] = row
    }

    // Then cut to the limit: a day keeps every row while it fits, and one
    // fewer, for its "+N", once anything on it is hidden. Hiding a bar can put
    // a "+N" on a day that fitted, which can hide another bar, so round again
    // until nothing changes; each round only takes rows away.
    val shownRows = IntArray(DaysPerWeek) { maxRows }
    while (true) {
        val hidden = IntArray(DaysPerWeek)
        for (item in ordered) {
            val row = rowOf.getValue(item)
            if ((item.from..item.to).any { row >= shownRows[it] }) {
                for (day in item.from..item.to) hidden[day]++
            }
        }
        var changed = false
        for (day in 0 until DaysPerWeek) {
            if (hidden[day] > 0 && shownRows[day] == maxRows && maxRows > 0) {
                shownRows[day] = maxRows - 1
                changed = true
            }
        }
        if (!changed) {
            val placed = ordered.mapNotNull { item ->
                val row = rowOf.getValue(item)
                if ((item.from..item.to).all { row < shownRows[it] }) WeekPlacement(item, row) else null
            }
            return WeekPack(placed, hidden, shownRows)
        }
    }
}

/** The date [column] days into the week from [weekStart]. */
internal fun dayInWeek(weekStart: LocalDate, column: Int): LocalDate = weekStart.plus(column, DateTimeUnit.DAY)
