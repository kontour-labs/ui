package io.kontour.ui.components.calendar

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** What an [EventCalendar] shows at once. */
enum class CalendarView {
    /** One day's hours. */
    Day,

    /** Seven days' hours side by side, from the reader's first day of the week. */
    Week,

    /** A month of day boxes, a week to a row, with its events in them. */
    Month,

    /** Twelve small months, the busy days marked. */
    Year,

    /**
     * Three days' hours side by side, from the day the view was shown on and
     * three at a time from there: between a day and a week, for a phone.
     */
    ThreeDays,
}

/**
 * How an [EventCalendar]'s day, three-day and week views lay their days out.
 */
enum class CalendarDaysStyle {
    /**
     * The hours, each event as tall as its time, with the all-day events in a
     * strip above them: three rows at most, and a "+N" for the rest.
     */
    Hours,

    /**
     * The hours below, and every all-day event above them in a pane of its
     * own that scrolls, with no "+N": a divider between them sets how much of
     * the height each has.
     */
    Split,

    /**
     * No hours: each day's events — all-day ones as bars across their days,
     * timed ones as a line with their time — stacked in a list down the days,
     * that scrolls. As many days as the view shows, side by side, so a week
     * view of two weeks is a fortnight's plan at a glance.
     */
    Condensed,
}

/**
 * Each view's periods counted by a plain index, so one pager pages them all:
 * a day's index is its epoch day, a week's the weeks since the first one that
 * starts on [firstDayOfWeek] after 1970-01-01 — [weekCount] of them at a time,
 * counted from the week [anchor] is in — three days' the threes from [anchor],
 * a month's `year × 12 + month`, a year's itself.
 *
 * @param anchor An epoch day, where pages of more than one day or week are
 *   counted from: the day the view was shown on, so its page starts there.
 */
internal class CalendarPeriods(val firstDayOfWeek: DayOfWeek, val weekCount: Int = 1, val anchor: Long = 0) {

    /** The epoch day of the first day after 1970-01-01, itself included, that starts a week. */
    private val weekZero: Long = (firstDayOfWeek.isoDayNumber - Thursday).mod(DaysPerWeek).toLong()

    /** The single week [anchor] is in, which a week view's pages of several are counted from. */
    private val anchorWeek: Int = if (weekCount == 1) 0 else weekIndex(LocalDate.fromEpochDays(anchor))

    /** The index of the [view] period [date] is in. */
    fun indexOf(date: LocalDate, view: CalendarView): Int = when (view) {
        CalendarView.Day -> date.toEpochDays().toInt()
        CalendarView.Week -> (weekIndex(date) - anchorWeek).floorDiv(weekCount)
        CalendarView.Month -> date.year * MonthsPerYear + date.month.ordinal
        CalendarView.Year -> date.year
        CalendarView.ThreeDays -> (date.toEpochDays() - anchor).floorDiv(ThreeDaysLong).toInt()
    }

    /** The first day of the [view] period at [index]. */
    fun startOf(index: Int, view: CalendarView): LocalDate = when (view) {
        CalendarView.Day -> LocalDate.fromEpochDays(index.toLong())
        CalendarView.Week -> startOfWeek(anchorWeek + index * weekCount)
        CalendarView.Month -> LocalDate(index.floorDiv(MonthsPerYear), index.mod(MonthsPerYear) + 1, 1)
        CalendarView.Year -> LocalDate(index, 1, 1)
        CalendarView.ThreeDays -> LocalDate.fromEpochDays(anchor + index.toLong() * ThreeDaysLong)
    }

    /** How many days side by side the hours of [view] show. */
    fun daysIn(view: CalendarView): Int = when (view) {
        CalendarView.Day -> 1
        CalendarView.ThreeDays -> ThreeDaysLong.toInt()
        else -> DaysPerWeek * weekCount
    }

    /** The first day of the week [date] is in. */
    fun weekStart(date: LocalDate): LocalDate = startOfWeek(weekIndex(date))

    /** The index of the single week [date] is in, whatever a week view pages by. */
    fun weekIndex(date: LocalDate): Int = (date.toEpochDays() - weekZero).floorDiv(DaysPerWeek.toLong()).toInt()

    /** The first day of the single week at [index]. */
    fun startOfWeek(index: Int): LocalDate = LocalDate.fromEpochDays(weekZero + index.toLong() * DaysPerWeek)

    /**
     * Every date the [view] period at [index] shows: a month's whole weeks,
     * the days from the months either side included, since they are drawn.
     */
    fun visibleDates(index: Int, view: CalendarView): ClosedRange<LocalDate> {
        val start = startOf(index, view)
        return when (view) {
            CalendarView.Day -> start..start
            CalendarView.Week, CalendarView.ThreeDays -> start..start.plus(daysIn(view) - 1, DateTimeUnit.DAY)
            CalendarView.Month -> {
                val last = start.plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY)
                weekStart(start)..weekStart(last).plus(DaysPerWeek - 1, DateTimeUnit.DAY)
            }
            CalendarView.Year -> start..LocalDate(start.year, 12, 31)
        }
    }

    /** How many weeks the month at [index] spans, four to six. */
    fun weeksIn(index: Int): Int {
        val dates = visibleDates(index, CalendarView.Month)
        return ((dates.endInclusive.toEpochDays() - dates.start.toEpochDays() + 1) / DaysPerWeek).toInt()
    }
}

internal const val DaysPerWeek: Int = 7
private const val MonthsPerYear = 12
private const val Thursday = 4
private const val ThreeDaysLong = 3L

/** The most weeks a week view shows side by side. */
internal const val MaxWeekCount: Int = 6
