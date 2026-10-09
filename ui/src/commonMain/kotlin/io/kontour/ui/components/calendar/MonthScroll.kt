package io.kontour.ui.components.calendar

import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.snapping.SnapLayoutInfoProvider
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.snapFlingBehavior
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import io.kontour.ui.theme.Theme
import kotlin.math.abs
import kotlin.math.floor
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/**
 * The scrolling month's list has a row for every week there is, near enough:
 * this many either side of the first week after 1970, some nineteen thousand
 * years, so a scroll never comes to an end of it.
 */
internal const val WeekOrigin: Int = 1_000_000

/** How many rows the scrolling month's list has. */
internal const val WeekRows: Int = WeekOrigin * 2

/** The scrolling month's row for the week [date] is in. */
internal fun CalendarPeriods.rowOf(date: LocalDate): Int = weekIndex(date) + WeekOrigin

/** The first day of the scrolling month's row [row]. */
internal fun CalendarPeriods.weekOfRow(row: Int): LocalDate = startOfWeek(row - WeekOrigin)

/**
 * The row to scroll to the top for [date]'s month, with [rowsInView] whole
 * rows on screen: its first week, unless that leaves [date]'s week below the
 * last whole row — zoomed in to three weeks, the last of a month is never
 * scrolled off for its first.
 */
internal fun CalendarPeriods.monthTarget(date: LocalDate, rowsInView: Int): Int {
    val first = rowOf(LocalDate(date.year, date.month, 1))
    return maxOf(first, rowOf(date) - (rowsInView.coerceAtLeast(1) - 1))
}

/** A row of the scrolling month on screen: its week's first day, and its top and bottom against the list's, in pixels. */
internal class VisibleRow(val weekStart: LocalDate, val top: Int, val bottom: Int)

/**
 * Which month [rows] show the most of — each day's box counted for as much of
 * it as is on a screen [viewport] tall — as a month's [CalendarPeriods.indexOf]:
 * the one the title is about, and the days of the others quieter. [current]
 * stays until another shows [hysteresis] pixels' worth of days more, so a
 * scroll that ends on an even split does not flick between the two.
 */
internal fun CalendarPeriods.dominantMonth(rows: List<VisibleRow>, viewport: Int, current: Int, hysteresis: Float): Int {
    if (rows.isEmpty() || viewport <= 0) return current
    val shares = HashMap<Int, Float>()
    for (row in rows) {
        val shown = (minOf(row.bottom, viewport) - maxOf(row.top, 0)).coerceAtLeast(0)
        if (shown == 0) continue
        for (column in 0 until DaysPerWeek) {
            val month = indexOf(row.weekStart.plus(column, DateTimeUnit.DAY), CalendarView.Month)
            shares[month] = (shares[month] ?: 0f) + shown
        }
    }
    val best = shares.maxByOrNull { it.value } ?: return current
    return if (best.key != current && best.value - (shares[current] ?: 0f) > hysteresis) best.key else current
}

/**
 * The scrolling month's list, and what ties it to the state: scrolled to show
 * a month, the state is about that month; the state moved to another month —
 * by the header, a key, the app, today — the list scrolls there.
 *
 * @param rowsInView How many whole rows fit on screen.
 * @param rowPx How tall a row is, in pixels.
 */
@Composable
internal fun rememberMonthList(scope: CalendarScope, rowsInView: Int, rowPx: Float): LazyListState {
    val state = scope.state
    val periods = state.periods
    // Kept as the periods are counted afresh for a week view: only the first
    // day of the week moves a month's rows.
    val list = remember(periods.firstDayOfWeek) { LazyListState(firstVisibleItemIndex = periods.monthTarget(state.date, rowsInView)) }
    val motion by rememberUpdatedState(Theme.motion)
    val rows by rememberUpdatedState(rowsInView)
    val row by rememberUpdatedState(rowPx)
    val edit = scope.edit

    // While the state moves the list, what the list shows does not move the
    // state: it would be about each month it passed on the way.
    val going = remember { BooleanArray(1) }

    // The list → the state.
    LaunchedEffect(list, periods) {
        snapshotFlow {
            val info = list.layoutInfo
            val visible = info.visibleItemsInfo
            val viewport = info.viewportEndOffset - info.viewportStartOffset
            val shown = visible.map { VisibleRow(periods.weekOfRow(it.index), it.offset, it.offset + it.size) }
            Triple(shown, viewport, visible.firstOrNull()?.index to visible.lastOrNull()?.index)
        }.collect { (shown, viewport, ends) ->
            val (first, last) = ends
            if (first != null && last != null) {
                val from = periods.weekOfRow(first)
                val weeks = from..periods.weekOfRow(last).plus(DaysPerWeek - 1, DateTimeUnit.DAY)
                if (state.shownWeeks != weeks) state.shownWeeks = weeks
            }
            if (going[0] || state.view != CalendarView.Month) return@collect
            val current = periods.indexOf(state.date, CalendarView.Month)
            val month = periods.dominantMonth(shown, viewport, current, hysteresis = row * DaysPerWeek / 2f)
            if (month != current) state.showFromScroll(month)
        }
    }

    // The state → the list: to another month, its first week at the top; a
    // keyboard's move within the month, only as far as brings its week in.
    LaunchedEffect(list, periods) {
        snapshotFlow { state.date }.collectLatest { date ->
            if (state.view != CalendarView.Month) return@collectLatest
            val period = periods.indexOf(date, CalendarView.Month)
            val target = if (period != state.scrolledTo) {
                state.scrolledTo = period
                periods.monthTarget(date, rows)
            } else {
                // Moved there by the scroll itself, it is where the scroll left it.
                if (!scope.cursorShown || date == state.scrolledDate) return@collectLatest
                val row = periods.rowOf(date)
                val item = list.layoutInfo.visibleItemsInfo.firstOrNull { it.index == row }
                val viewport = list.layoutInfo.viewportEndOffset
                if (item != null && item.offset >= 0 && item.offset + item.size <= viewport) return@collectLatest
                val above = item?.let { it.offset < 0 } ?: (row < list.firstVisibleItemIndex)
                if (above) row else (row - rows + 1).coerceAtLeast(0)
            }
            going[0] = true
            try {
                if (motion.reduceMotion || abs(target - list.firstVisibleItemIndex) > FarRows) {
                    list.scrollToItem(target)
                } else {
                    list.animateScrollToItem(target)
                }
            } finally {
                going[0] = false
            }
        }
    }

    // Left between weeks by a wheel or a carry's scroll, it settles on one.
    LaunchedEffect(list) {
        snapshotFlow { list.isScrollInProgress || edit.live }.collectLatest { moving ->
            if (moving) return@collectLatest
            delay(WheelSettleMillis)
            val first = list.layoutInfo.visibleItemsInfo.firstOrNull() ?: return@collectLatest
            if (first.offset == 0) return@collectLatest
            val back = -first.offset
            val toNext = first.size + first.offset
            val row = if (back <= toNext) first.index else first.index + 1
            if (motion.reduceMotion) list.scrollToItem(row) else list.animateScrollToItem(row)
        }
    }
    return list
}

/** A month's fling: as far as it was flung, ending with a week at the top. */
@Composable
internal fun rememberWeekSnap(list: LazyListState): FlingBehavior {
    val motion = Theme.motion
    val spring = motion.springOrTween<Float>(motion.springSnappy)
    return remember(list, spring) {
        snapFlingBehavior(SnapLayoutInfoProvider(list, SnapPosition.Start), exponentialDecay(), spring)
    }
}

/** How many rows a scroll to another month is animated over, at most: further, it is a cut. */
private const val FarRows = 26

/** How long after a wheel's last turn the month settles on a week. */
private const val WheelSettleMillis = 150L

/** The whole rows of [rowPx] that fit in [viewportPx]. */
internal fun wholeRows(viewportPx: Float, rowPx: Float): Int = if (rowPx <= 0f) 1 else floor(viewportPx / rowPx + 0.001f).toInt().coerceAtLeast(1)
