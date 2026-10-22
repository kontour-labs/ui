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
 * The events in the [days] from [weekStart] — a week, or the one day of a day view — as [WeekItem]s.
 *
 * With [lines], a month's week: every event, the ones on a single day as
 * lines. Without, the strip above a week's hours: only what is not in the
 * hours — all day, or a whole day long or longer.
 */
internal fun weekItems(weekStart: LocalDate, events: List<CalendarEvent>, lines: Boolean, days: Int = DaysPerWeek): List<WeekItem> {
    val weekFrom = weekStart.toEpochDays()
    val weekTo = weekFrom + days - 1
    val items = ArrayList<WeekItem>()
    for (event in events) {
        val firstDay = event.start.date.toEpochDays()
        // The day its last minute is on: an event ending at midnight ends the day before.
        val lastDay = lastDayOf(event.start, event.end).toEpochDays()
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
 * A row for each bar in [events], the same on every page and in every week
 * the bar is in, so a bar running on into the next day, three days or week
 * is on the same line there and its two pieces meet: *"can we just make the
 * label stay in place as the calendar moves, so it only shows once?"* Packed
 * a page at a time, a bar running on was put by the days of each page alone:
 * on one page under a bar that had started before it, on the next at the top.
 *
 * Every bar of the calendar packed as one: earliest first, then longest, each
 * in the lowest row free on its first day. Only the bars still going on that
 * day decide its row, so a day's own bars are no deeper than the bars on it
 * make them, a bar of one day fills a row a longer one has left, and the rows
 * start again from the top after any day with no bar on it. What a page
 * gives up is a row left empty above a bar that ran on into it under one
 * that has ended. Packed once for as long as the events are the same.
 *
 * With [lines], what a month's week or a condensed list draws as a bar,
 * a timed event past midnight among them; without, what the strip above the
 * hours has: see [weekItems].
 */
internal fun barRows(events: List<CalendarEvent>, lines: Boolean): Map<CalendarEvent, Int> {
    class Span(val event: CalendarEvent, val first: Long, val last: Long, val index: Int)
    val spans = ArrayList<Span>()
    for ((index, event) in events.withIndex()) {
        val first = event.start.date.toEpochDays()
        val last = lastDayOf(event.start, event.end).toEpochDays()
        // As [weekItems] has it: the strip has only what is not in the hours.
        val bar = !event.isTimed() || (lines && last > first)
        if (bar) spans += Span(event, first, last, index)
    }
    spans.sortWith(compareBy<Span>({ it.first }, { -(it.last - it.first) }, { it.index }))
    // The last day taken in each row so far.
    val taken = ArrayList<Long>()
    val rows = HashMap<CalendarEvent, Int>(spans.size * 2)
    for (span in spans) {
        var row = taken.indexOfFirst { it < span.first }
        if (row < 0) {
            row = taken.size
            taken += span.last
        } else {
            taken[row] = span.last
        }
        rows[span.event] = row
    }
    return rows
}

/**
 * [items] in rows, at most [maxRows] of them a day.
 *
 * Bars first — earliest, then longest — then lines by time, each in the
 * lowest row free across all its days, so a bar is one straight bar. With
 * [rows], a bar is in the row given it there, the same on every page — see
 * [barRows] — where that row is within [maxRows]; what has none, or one past
 * the limit, or finds it taken, is in the lowest free. A day
 * with more than fits shows one row fewer and a "+N" in the last; a bar that
 * would cross such a day's "+N" is not shown at all rather than broken, and
 * counts in every day it would have crossed. [maxRows] may be 0, where a day
 * is too short for even one: nothing is shown, and every day counts all of its own.
 */
internal fun packWeek(items: List<WeekItem>, maxRows: Int, days: Int = DaysPerWeek, rows: Map<CalendarEvent, Int>? = null): WeekPack {
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
    val taken = ArrayList<BooleanArray>()
    val rowOf = HashMap<WeekItem, Int>()
    fun WeekItem.freeIn(row: Int) = row >= taken.size || (from..to).none { taken[row][it] }
    fun WeekItem.putIn(row: Int) {
        while (taken.size <= row) taken += BooleanArray(days)
        for (day in from..to) taken[row][day] = true
        rowOf[this] = row
    }
    // The bars given a row first, each in its own where that is shown: one
    // whose row is past the limit goes where there is room, seen rather than
    // counted in a "+N" under empty rows. Two the same event, copies, cannot
    // share one, and the second goes where there is room too.
    if (rows != null) {
        for (item in ordered) {
            val row = if (item.bar) rows[item.event] else null
            if (row != null && row < maxRows && item.freeIn(row)) item.putIn(row)
        }
    }
    for (item in ordered) {
        if (item in rowOf) continue
        item.putIn((0..taken.size).first { item.freeIn(it) })
    }

    // Then cut to the limit: a day keeps every row while it fits, and one
    // fewer, for its "+N", once anything on it is hidden. Hiding a bar can put
    // a "+N" on a day that fitted, which can hide another bar, so round again
    // until nothing changes; each round only takes rows away.
    val shownRows = IntArray(days) { maxRows }
    while (true) {
        val hidden = IntArray(days)
        for (item in ordered) {
            val row = rowOf.getValue(item)
            if ((item.from..item.to).any { row >= shownRows[it] }) {
                for (day in item.from..item.to) hidden[day]++
            }
        }
        var changed = false
        for (day in 0 until days) {
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
