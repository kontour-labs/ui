package io.kontour.ui.components.calendar

import androidx.compose.runtime.getValue
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import io.kontour.ui.motion.ScrollDefaults
import io.kontour.ui.theme.Theme
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/**
 * [CalendarDaysStyle.Condensed]: no hours, and each day's events stacked down
 * the days — all-day ones as bars across their days, timed ones as their
 * line — packed into the lowest row free across every day each covers, as a
 * month's week is but with no row cap, in a list that scrolls: asked for as
 * *"option to hide the hourly grid and only show all-day events (condensed
 * list per day)"*. Paged sideways with the headings above it.
 *
 * A day's empty space opens it, held makes a day, or days; an event is
 * carried by whole days, its time kept, and the list scrolls under a carry
 * held near its top or foot.
 */
@Composable
internal fun CondensedDays(scope: CalendarScope, days: Int, modifier: Modifier) {
    val state = scope.state
    val frame = Theme.shapes.container
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Box(
        modifier
            .fillMaxWidth()
            .clip(frame)
            .border(Theme.sizing.borderWidth, gridLine(), frame)
            .calendarCursor(scope, description = scope.dayDescription(state.date)) { key -> scope.monthKey(key, rtl) },
    ) {
        PeriodStrip(scope.pager, Modifier.fillMaxSize()) { period -> CondensedPage(scope, period, days) }
    }
}

@Composable
private fun CondensedPage(scope: CalendarScope, period: Int, days: Int) {
    val state = scope.state
    val from = state.periods.startOf(period, state.view)
    val to = from.plus(days - 1, DateTimeUnit.DAY)
    val pack = remember(scope.events, from, days) {
        packWeek(weekItems(from, scope.eventsBetween(from, to), lines = true, days = days), maxRows = Int.MAX_VALUE, days = days)
    }
    // Each row's events, so the list composes only the rows on screen.
    val rows = remember(pack) { pack.placed.groupBy { it.row } }
    val count = (pack.placed.maxOfOrNull { it.row } ?: -1) + 1
    // Two lines an event, a list's worth of room: its title, then its time or
    // where it is, as a planner's bar is.
    val item = with(LocalDensity.current) { Theme.typography.labelSmall.lineHeight.toDp() } * CondensedLines + Theme.spacing.xxs * 2
    val gap = Theme.spacing.xxs / 2
    val list = rememberLazyListState()
    val placed = remember { PlacedAt() }
    val line = Theme.sizing.borderWidth
    val lineColour = gridLine()
    val ringColour = Theme.colours.focusRing
    val ringWidth = Theme.sizing.focusRingWidth
    val cursorShown = scope.cursorShown
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { placed.coordinates = it }
            .holdsDays(scope, placed, from)
            // A day's empty space, pressed: the day, as a month's box opens it.
            .pointerInput(from, days, scope.enabled, rtl) {
                if (!scope.enabled) return@pointerInput
                detectTapGestures { at ->
                    val column = (at.x / (size.width.toFloat() / days)).toInt().coerceIn(0, days - 1)
                    val reading = if (rtl) days - 1 - column else column
                    scope.openDay(from.plus(reading, DateTimeUnit.DAY))
                }
            }
            .drawWithContent {
                // A line at each day's start edge, the length of the list: the
                // first day's too, as the hours' columns draw it, so a page
                // swiped in beside this one has its line between the two —
                // the last day of one week and the first of the next.
                val width = line.toPx()
                val cell = size.width / days
                val edge = if (layoutDirection == LayoutDirection.Rtl) size.width - width / 2 else width / 2
                drawLine(lineColour, Offset(edge, 0f), Offset(edge, size.height), width)
                for (column in 1 until days) {
                    val x = cell * column
                    drawLine(lineColour, Offset(x, 0f), Offset(x, size.height), width)
                }
                drawContent()
                // The keyboard's day, round its whole column.
                val date = state.date
                if (!cursorShown || date < from || date > to) return@drawWithContent
                val column = (date.toEpochDays() - from.toEpochDays()).toInt()
                val left = if (layoutDirection == LayoutDirection.Rtl) size.width - cell * (column + 1) else cell * column
                drawCursorRing(Rect(left, 0f, left + cell, size.height), ringColour, ringWidth.toPx())
            },
    ) {
        val cell = maxWidth / days
        // Each day's place, the list's whole height, for an event carried by whole days.
        Row(Modifier.fillMaxSize()) {
            for (column in 0 until days) {
                DayTarget(scope, from.plus(column, DateTimeUnit.DAY), Modifier.weight(1f).fillMaxHeight())
            }
        }
        CompositionLocalProvider(LocalItemLines provides CondensedLines) {
            CondensedRows(scope, list, count, rows, item, gap, cell, from)
        }
        DaysGhostLayer(scope, from, weeks = 1, days = days, slot = gap / 2, item = item, gap = gap)
        EditScroll(scope, placed, list, byDays = true)
    }
}

/** How many lines each event in a condensed list has. */
private const val CondensedLines = 2

@Composable
private fun CondensedRows(
    scope: CalendarScope,
    list: LazyListState,
    count: Int,
    rows: Map<Int, List<WeekPlacement>>,
    item: Dp,
    gap: Dp,
    cell: Dp,
    from: LocalDate,
) {
    LazyColumn(
        state = list,
        flingBehavior = ScrollDefaults.flingBehavior(),
        userScrollEnabled = scope.enabled,
        contentPadding = PaddingValues(top = Theme.spacing.xxs, bottom = item),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(count) { row ->
            Box(Modifier.fillMaxWidth().height(item)) {
                for (placement in rows[row].orEmpty()) {
                    val shown = placement.item
                    EventChip(
                        event = shown.event,
                        presentation = if (shown.bar) EventPresentation.Bar else EventPresentation.Line,
                        scope = scope,
                        clippedStart = shown.continuesBefore,
                        clippedEnd = shown.continuesAfter,
                        focusableOn = from.plus(shown.from, DateTimeUnit.DAY)..from.plus(shown.to, DateTimeUnit.DAY),
                        modifier = Modifier
                            .padding(start = cell * shown.from + gap, top = gap / 2)
                            .width(cell * (shown.to - shown.from + 1) - gap * 2)
                            .height(item - gap),
                    )
                }
            }
        }
    }
}

