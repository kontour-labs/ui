package io.kontour.ui.components.datetime

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/*
 * The arithmetic of an activity calendar: which day each cell is, which column a
 * month's label goes over, and which shade a count gets. Pure, so it is tested
 * without drawing anything and the drawing only has to be right about pixels.
 */

/**
 * The days an `ActivityCalendar` shows: [weeks] columns of seven, a week to a
 * column starting on [firstDayOfWeek], the last column holding [end] — which
 * runs only as far as [end], so it is the partial one — and nothing before
 * [start], if there is one.
 */
internal class ActivityGrid(
    val end: LocalDate,
    val weeks: Int,
    val start: LocalDate?,
    firstDayOfWeek: DayOfWeek,
) {
    private val formats = DateTimeFormats(firstDayOfWeek = firstDayOfWeek)

    /** The first day of the first column, whether or not it is shown. */
    val firstDay: LocalDate =
        end.minus(DatePeriod(days = formats.columnOf(end.dayOfWeek) + (weeks - 1) * 7))

    /** The day at [column] and [row], or null for a cell outside the range. */
    fun dateAt(column: Int, row: Int): LocalDate? {
        if (column !in 0 until weeks || row !in 0..6) return null
        val date = firstDay.plus(DatePeriod(days = column * 7 + row))
        return date.takeIf { contains(it) }
    }

    /** The column and row [date] sits in, or null if it is not shown. */
    fun cellOf(date: LocalDate): Pair<Int, Int>? {
        if (!contains(date)) return null
        val days = firstDay.daysUntil(date)
        return days / 7 to days % 7
    }

    fun contains(date: LocalDate): Boolean =
        date >= firstDay && date <= end && (start == null || date >= start)

    /** The first and last days shown. */
    val firstShown: LocalDate get() = if (start != null && start > firstDay) start else firstDay

    /** The row each weekday sits in. */
    fun rowOf(day: DayOfWeek): Int = formats.columnOf(day)

    /**
     * Where the month labels go: over the first column whose first day is in a
     * new month, as GitHub places them.
     *
     * A label is [columnsFor] columns wide. One that would run into the next is
     * dropped — the first column's, if it is the one in the way, since that is
     * the month the calendar only shows the tail of — and so is one that would
     * run off the end: a month barely begun in the last column goes unlabelled
     * rather than cut off.
     */
    fun monthLabels(columnsFor: (Month) -> Int): List<Pair<Int, Month>> {
        val candidates = (0 until weeks).mapNotNull { column ->
            val top = firstDay.plus(DatePeriod(days = column * 7))
            val before = firstDay.plus(DatePeriod(days = (column - 1) * 7))
            if (column == 0 || top.month != before.month) column to top.month else null
        }
        val kept = ArrayList<Pair<Int, Month>>()
        for (label in candidates) {
            val last = kept.lastOrNull()
            if (last == null || label.first - last.first >= columnsFor(last.second)) {
                kept += label
            } else if (last.first == 0) {
                kept[kept.lastIndex] = label
            }
        }
        return kept.filter { (column, month) -> column + columnsFor(month) <= weeks }
    }
}

/**
 * Where each level starts for counts spread like [counts], in [levels] levels:
 * the non-zero counts split into equal-sized groups, GitHub's rule, so a busy
 * year and a quiet one both use every shade. Level 1 always starts at 1, and each
 * level starts at least one above the one before, so a history of all ones is
 * all level one rather than all level four.
 */
internal fun quantileBounds(counts: Collection<Int>, levels: Int): IntArray {
    val sorted = counts.filter { it > 0 }.sorted()
    val bounds = IntArray(levels.coerceAtLeast(1))
    bounds[0] = 1
    for (k in 1 until bounds.size) {
        val at = if (sorted.isEmpty()) 0 else sorted[minOf(k * sorted.size / bounds.size, sorted.lastIndex)]
        bounds[k] = maxOf(at, bounds[k - 1] + 1)
    }
    return bounds
}

/** The level [count] falls in, where level k starts at `bounds[k - 1]`; nought for nothing. */
internal fun levelOf(count: Int, bounds: IntArray): Int {
    if (count <= 0) return 0
    var level = 0
    for (k in bounds.indices) if (count >= bounds[k]) level = k + 1
    return level
}
