package io.kontour.ui.components.calendar

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
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
    initialWeekCount: Int = 1,
) {
    init {
        require(initialWeekCount in 1..MaxWeekCount) { "A week view shows 1 to $MaxWeekCount weeks, not $initialWeekCount." }
    }

    /** The day the calendar is about: the day itself, or a day in the week, month or year shown. */
    var date: LocalDate by mutableStateOf(initialDate)
        private set

    /** Which view it shows. */
    var view: CalendarView by mutableStateOf(initialView)
        private set

    /**
     * How many weeks the [CalendarView.Week] view shows side by side, 1 to 6:
     * a fortnight's two, as a planner's, and the steps and swipes go by as
     * many. Set with [showWeeks].
     */
    var weekCount: Int by mutableStateOf(initialWeekCount)
        private set

    /**
     * The reader's first day of the week, which weeks and month rows start
     * on, kept current by [rememberEventCalendarState]; and where pages of
     * several days or weeks start, kept by [show].
     */
    internal var periods: CalendarPeriods by mutableStateOf(CalendarPeriods(firstDayOfWeek, initialWeekCount, initialDate.toEpochDays()))

    /** The periods again, with any of what they are counted by changed. */
    internal fun recount(firstDayOfWeek: DayOfWeek = periods.firstDayOfWeek, weekCount: Int = this.weekCount, anchor: Long = periods.anchor) {
        val now = periods
        if (now.firstDayOfWeek != firstDayOfWeek || now.weekCount != weekCount || now.anchor != anchor) {
            periods = CalendarPeriods(firstDayOfWeek, weekCount, anchor)
        }
    }

    /** Today, as the calendar was last told — null when it was told nothing. */
    internal var today: LocalDate? by mutableStateOf(null)

    /**
     * How many times an event's box has composed, in any view.
     *
     * An increment and a field, as `SheetState.surfaceCompositions` is, so the
     * test suite can ask whether something that is nothing to an event — the
     * time now moving on a minute — composes every event again. Counting the
     * app's `eventContent` cannot tell: a box composed again calls it with
     * what it had, and it skips, while the box round it has done the work.
     */
    internal var eventCompositions: Int = 0

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

    /**
     * How many weeks the month shows at once: [MonthRowsInView] for the whole
     * of one, fewer zoomed in, each taller. A whole number but while a pinch
     * is under way.
     */
    internal var monthRows: Float by mutableFloatStateOf(MonthRowsInView.toFloat())

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

    /**
     * The year [year], scrolled to: the same month, and the same day of it
     * where it has one, as [step] keeps it.
     */
    internal fun showYearFromScroll(year: Int) {
        val first = LocalDate(year, date.month, 1)
        date = LocalDate(year, date.month, minOf(date.day, lengthOfMonth(first)))
        scrolledDate = date
    }

    /** A schedule scrolled to [day]: the state is about it, and the list need not go there. */
    internal fun showFromScheduleScroll(day: LocalDate) {
        date = day
        scrolledDate = day
    }

    /** The day [showFromScroll] last moved to, which the scroll need not bring into view. */
    internal var scrolledDate: LocalDate? = null

    /**
     * Shows [view], about the same day. Three days, or several weeks, start
     * their page at the day, or its week.
     */
    fun show(view: CalendarView) {
        show(date, view)
    }

    /**
     * Shows [date], in [view] — the current one unless given.
     *
     * A view the calendar does not offer — see `EventCalendar`'s `views` — is
     * shown as the nearest one it does: a day as three days or the week, a
     * month as the week. With none near enough, [date] is shown in the view
     * already on show.
     */
    fun show(date: LocalDate, view: CalendarView = this.view) {
        val view = offered(view) ?: this.view
        // Re-counted only on a change of view: a move within it pages, as a key's does.
        val anew = view != this.view
        this.date = date
        this.view = view
        if (anew && (view == CalendarView.ThreeDays || (view == CalendarView.Week && weekCount > 1))) recount(anchor = date.toEpochDays())
    }

    /**
     * Shows [count] weeks side by side in the week view — 1 to 6 — from the
     * week [date] is in. In another view, the week view will show as many
     * when it is shown.
     */
    fun showWeeks(count: Int) {
        require(count in 1..MaxWeekCount) { "A week view shows 1 to $MaxWeekCount weeks, not $count." }
        weekCount = count
        recount(weekCount = count, anchor = date.toEpochDays())
    }

    /**
     * Steps [periods] days, weeks, months or years on — back, if negative —
     * whichever the view shows. A month step keeps the day of the month where
     * the month has it, and the last day where it does not.
     */
    fun step(periods: Int) {
        val target = this.periods.startOf(period + periods, view)
        date = when (view) {
            CalendarView.Day, CalendarView.Week, CalendarView.ThreeDays -> {
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
            // To the month's start: its first day with anything on comes to the top.
            CalendarView.Schedule -> target
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

    /** The views the calendar offers, in its switcher's order: `EventCalendar`'s `views`. */
    internal var allowedViews: List<CalendarView> by mutableStateOf(CalendarView.entries.toList())

    /** [view] if the calendar offers it, or else the nearest one it does; null for none. */
    internal fun offered(view: CalendarView): CalendarView? =
        if (view in allowedViews) view else nearViews(view).firstOrNull { it in allowedViews }

    /** The latest [showToday], for the calendar to flash today for. */
    internal var todayRequest: TodayRequest? by mutableStateOf(null)
        private set

    internal companion object {
        /** The date as an epoch day, the view, the hours' scroll, how far the month is zoomed, and how many weeks from where. */
        val Saver: Saver<EventCalendarState, Any> = listSaver(
            save = {
                listOf(
                    it.date.toEpochDays(),
                    it.view.ordinal,
                    it.timeScroll.value,
                    it.initialScrollTime.toSecondOfDay(),
                    it.periods.firstDayOfWeek.ordinal,
                    it.monthRows,
                    it.weekCount,
                    it.periods.anchor,
                )
            },
            restore = { saved ->
                EventCalendarState(
                    initialDate = LocalDate.fromEpochDays(saved[0] as Long),
                    initialView = CalendarView.entries[saved[1] as Int],
                    initialScrollTime = LocalTime.fromSecondOfDay(saved[3] as Int),
                    firstDayOfWeek = DayOfWeek.entries[saved[4] as Int],
                    initialWeekCount = saved.getOrNull(6) as? Int ?: 1,
                ).also { state ->
                    state.restoredScroll = saved[2] as Int
                    state.scrolledToStart = true
                    // Each added on the end, so what an earlier version saved still restores.
                    (saved.getOrNull(5) as? Float)?.let { state.monthRows = it }
                    (saved.getOrNull(7) as? Long)?.let { state.recount(anchor = it) }
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
 * @param initialWeekCount How many weeks the week view shows side by side, 1
 *   to 6, until [EventCalendarState.showWeeks] says otherwise.
 */
@Composable
fun rememberEventCalendarState(
    initialDate: LocalDate = EventCalendarDefaults.currentDate(),
    initialView: CalendarView = CalendarView.Month,
    initialScrollTime: LocalTime = EventCalendarDefaults.ScrollTime,
    firstDayOfWeek: DayOfWeek = Theme.formats.conventions.firstDayOfWeek,
    initialWeekCount: Int = 1,
): EventCalendarState {
    val state = rememberSaveable(saver = EventCalendarState.Saver) {
        EventCalendarState(initialDate, initialView, initialScrollTime, firstDayOfWeek, initialWeekCount)
    }
    SideEffect {
        state.recount(firstDayOfWeek = firstDayOfWeek)
    }
    return state
}

private fun lengthOfMonth(first: LocalDate): Int {
    val next = if (first.month.ordinal == 11) LocalDate(first.year + 1, 1, 1) else LocalDate(first.year, first.month.ordinal + 2, 1)
    return (next.toEpochDays() - LocalDate(first.year, first.month, 1).toEpochDays()).toInt()
}

/** One [EventCalendarState.showToday]: which, and whether it had to page to get there. */
internal class TodayRequest(val serial: Int, val paged: Boolean)

/**
 * What to show [view] as when it is not offered, nearest first: a day as a few
 * days, then a week; a month as a week; a year as a month.
 */
private fun nearViews(view: CalendarView): List<CalendarView> = when (view) {
    CalendarView.Day -> listOf(CalendarView.ThreeDays, CalendarView.Week, CalendarView.Schedule, CalendarView.Month)
    CalendarView.ThreeDays -> listOf(CalendarView.Day, CalendarView.Week, CalendarView.Schedule)
    CalendarView.Week -> listOf(CalendarView.ThreeDays, CalendarView.Day, CalendarView.Month, CalendarView.Schedule)
    CalendarView.Month -> listOf(CalendarView.Week, CalendarView.Schedule, CalendarView.Year)
    CalendarView.Year -> listOf(CalendarView.Month, CalendarView.Schedule)
    CalendarView.Schedule -> listOf(CalendarView.Month, CalendarView.Week)
}
