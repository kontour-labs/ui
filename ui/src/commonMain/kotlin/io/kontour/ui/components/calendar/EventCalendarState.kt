package io.kontour.ui.components.calendar

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import io.kontour.ui.theme.Theme
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

/**
 * What an [EventCalendar] is showing: which view, and the day it is about.
 * Made by [rememberEventCalendarState].
 *
 * ```kotlin
 * val calendar = rememberEventCalendarState(initialView = CalendarView.Week)
 * LaunchedEffect(calendar.visibleDates) { events = repository.between(calendar.visibleDates) }
 * EventCalendar(state = calendar, events = events)
 * ```
 *
 * The calendar moves it itself — its header's arrows and switcher, a swipe, a
 * tap on a day — and an app can too, with [show], [step] and [showToday].
 */
@Stable
class EventCalendarState internal constructor(
    initialDate: LocalDate,
    initialView: CalendarView,
    internal val initialScrollTime: LocalTime,
    firstDayOfWeek: DayOfWeek,
) {
    /** The day the calendar is about: the day itself, or a day in the week, month or year shown. */
    var date: LocalDate by mutableStateOf(initialDate)
        private set

    /** Which view it shows. */
    var view: CalendarView by mutableStateOf(initialView)
        private set

    /**
     * The reader's first day of the week, which weeks and month rows start on.
     * Kept current by [rememberEventCalendarState].
     */
    internal var periods: CalendarPeriods by mutableStateOf(CalendarPeriods(firstDayOfWeek))

    /** Today, as the calendar was last told — null when it was told nothing. */
    internal var today: LocalDate? by mutableStateOf(null)

    /** The hours' scroll, shared by the day and week views so a switch between them keeps the time in view. */
    internal val timeScroll = ScrollState(0)

    /**
     * The keyboard cursor's minute of the day in the day and week views; its
     * day is [date]. Null until the hours first take focus.
     */
    internal var cursorMinute: Int? by mutableStateOf(null)

    /** Whether the hours have been scrolled to [initialScrollTime] yet. */
    internal var scrolledToStart = false

    /** The period of the current view that [date] is in. */
    internal val period: Int get() = periods.indexOf(date, view)

    /**
     * Every date the current view shows — for loading the events it needs.
     * A month's includes the days of the months either side its weeks show,
     * and whatever weeks either side of it are scrolled into view.
     */
    val visibleDates: ClosedRange<LocalDate>
        get() {
            val dates = periods.visibleDates(period, view)
            val shown = shownWeeks?.takeIf { view == CalendarView.Month } ?: return dates
            return minOf(dates.start, shown.start)..maxOf(dates.endInclusive, shown.endInclusive)
        }

    /** The days of the weeks a scrolling month has on screen, whole weeks; null where it has not said. */
    internal var shownWeeks: ClosedRange<LocalDate>? by mutableStateOf(null)

    /**
     * The month a scrolling month was last scrolled to, as [period]: so the
     * scroll that set it is not answered by scrolling there again.
     */
    internal var scrolledTo: Int? = null

    /**
     * The month at [month], scrolled to: the same day of it where it has one,
     * its last day where it does not, as [step] keeps it.
     */
    internal fun showFromScroll(month: Int) {
        val first = periods.startOf(month, CalendarView.Month)
        date = LocalDate(first.year, first.month, minOf(date.day, lengthOfMonth(first)))
        scrolledTo = month
        scrolledDate = date
    }

    /** The day [showFromScroll] last moved to, which the scroll need not bring into view. */
    internal var scrolledDate: LocalDate? = null

    /** Shows [view], about the same day. */
    fun show(view: CalendarView) {
        this.view = view
    }

    /** Shows [date], in [view] — the current one unless given. */
    fun show(date: LocalDate, view: CalendarView = this.view) {
        this.date = date
        this.view = view
    }

    /**
     * Steps [periods] days, weeks, months or years on — back, if negative —
     * whichever the view shows. A month step keeps the day of the month where
     * the month has it, and the last day where it does not.
     */
    fun step(periods: Int) {
        val target = this.periods.startOf(period + periods, view)
        date = when (view) {
            CalendarView.Day, CalendarView.Week -> {
                // The same day of the week, the same distance into the period.
                val into = date.toEpochDays() - this.periods.startOf(period, view).toEpochDays()
                LocalDate.fromEpochDays(target.toEpochDays() + into)
            }
            CalendarView.Month -> {
                val last = lengthOfMonth(target)
                LocalDate(target.year, target.month, minOf(date.day, last))
            }
            CalendarView.Year -> {
                val last = lengthOfMonth(LocalDate(target.year, date.month, 1))
                LocalDate(target.year, date.month, minOf(date.day, last))
            }
        }
    }

    /**
     * Shows today, in the same view, and points at it — today's date pulses
     * twice, as the date picker's does. Nothing while the calendar has not been
     * told today.
     */
    fun showToday() {
        val today = today ?: return
        val paged = periods.indexOf(today, view) != period
        date = today
        todayRequest = TodayRequest((todayRequest?.serial ?: 0) + 1, paged)
    }

    /** The latest [showToday], for the calendar to flash today for. */
    internal var todayRequest: TodayRequest? by mutableStateOf(null)
        private set

    internal companion object {
        /** The date as an epoch day, the view, and the hours' scroll. */
        val Saver: Saver<EventCalendarState, Any> = listSaver(
            save = { listOf(it.date.toEpochDays(), it.view.ordinal, it.timeScroll.value, it.initialScrollTime.toSecondOfDay(), it.periods.firstDayOfWeek.ordinal) },
            restore = { saved ->
                EventCalendarState(
                    initialDate = LocalDate.fromEpochDays(saved[0] as Long),
                    initialView = CalendarView.entries[saved[1] as Int],
                    initialScrollTime = LocalTime.fromSecondOfDay(saved[3] as Int),
                    firstDayOfWeek = DayOfWeek.entries[saved[4] as Int],
                ).also { state ->
                    state.restoredScroll = saved[2] as Int
                    state.scrolledToStart = true
                }
            },
        )
    }

    /** A scroll to put back once the hours are laid out, after recreation. */
    internal var restoredScroll: Int? = null
}

/**
 * Remembers an [EventCalendarState], kept across recreation.
 *
 * @param initialDate The day it opens on: today by default.
 * @param initialView The view it opens in.
 * @param initialScrollTime Where the day and week views' hours open, scrolled
 *   so this time is at the top: the start of a working day by default, so the
 *   first thing seen is not the small hours.
 * @param firstDayOfWeek The day weeks and month rows start on: the reader's, by
 *   their locale, unless an app has a reason of its own.
 */
@Composable
fun rememberEventCalendarState(
    initialDate: LocalDate = EventCalendarDefaults.currentDate(),
    initialView: CalendarView = CalendarView.Month,
    initialScrollTime: LocalTime = EventCalendarDefaults.ScrollTime,
    firstDayOfWeek: DayOfWeek = Theme.formats.conventions.firstDayOfWeek,
): EventCalendarState {
    val state = rememberSaveable(saver = EventCalendarState.Saver) {
        EventCalendarState(initialDate, initialView, initialScrollTime, firstDayOfWeek)
    }
    SideEffect {
        if (state.periods.firstDayOfWeek != firstDayOfWeek) state.periods = CalendarPeriods(firstDayOfWeek)
    }
    return state
}

private fun lengthOfMonth(first: LocalDate): Int {
    val next = if (first.month.ordinal == 11) LocalDate(first.year + 1, 1, 1) else LocalDate(first.year, first.month.ordinal + 2, 1)
    return (next.toEpochDays() - LocalDate(first.year, first.month, 1).toEpochDays()).toInt()
}

/** One [EventCalendarState.showToday]: which, and whether it had to page to get there. */
internal class TodayRequest(val serial: Int, val paged: Boolean)
