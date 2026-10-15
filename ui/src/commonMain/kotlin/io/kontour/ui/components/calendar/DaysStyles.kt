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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.Snapshot
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
        val packing = remember(scope.events, days) { CondensedPacking(scope.events) }
        val lists = remember(scope.pager) { HashMap<Int, LazyListState>() }
        PeriodStrip(scope.pager, Modifier.fillMaxSize()) { period -> CondensedPage(scope, packing, lists, period, days) }
    }
}

/**
 * What a condensed view's pages share of their rows, for as long as the
 * events are the same: each bar's row, the same on every page — see
 * [barRows] — so a bar running on into the next page is on the same line
 * there, and each page's rows, packed once.
 */
private class CondensedPacking(events: List<CalendarEvent>) {
    val rows = barRows(events, lines = true)
    val packs = HashMap<LocalDate, WeekPack>()
}

/**
 * A condensed view's page. [lists] are the lists of the pages in view, by
 * period: a page swiped in opens its list where the page it comes in beside
 * has scrolled to, so the two pages' rows are level. Each opened its list at
 * the top, and a bar running on into the next page was on another line there.
 */
@Composable
private fun CondensedPage(scope: CalendarScope, packing: CondensedPacking, lists: HashMap<Int, LazyListState>, period: Int, days: Int) {
    val state = scope.state
    val from = state.periods.startOf(period, state.view)
    val to = from.plus(days - 1, DateTimeUnit.DAY)
    // Packed once for as long as the events are the same, and emptied now and
    // then, so a long way swiped does not keep every page it passed.
    val packOf = { start: LocalDate ->
        if (start !in packing.packs && packing.packs.size >= KeptCondensedPacks) packing.packs.clear()
        packing.packs.getOrPut(start) {
            val items = weekItems(start, scope.eventsBetween(start, start.plus(days - 1, DateTimeUnit.DAY)), lines = true, days = days)
            packWeek(items, maxRows = Int.MAX_VALUE, days = days, rows = packing.rows)
        }
    }
    val pack = packOf(from)
    // The pages either side, where they are the days either side: for which
    // bars run on into theirs on the same line, and keep one title.
    val before = state.periods.startOf(period - 1, state.view).takeIf { it.plus(days, DateTimeUnit.DAY) == from }?.let(packOf)
    val after = state.periods.startOf(period + 1, state.view).takeIf { it == to.plus(1, DateTimeUnit.DAY) }?.let(packOf)
    val origin = scope.pager.periods.origin
    val still = Theme.motion.reduceMotion
    // Each row's events, so the list composes only the rows on screen.
    val rows = remember(pack) { pack.placed.groupBy { it.row } }
    // As many rows as the pages either side too, so a list scrolled to its
    // foot opens the page beside it at the same place rather than at that
    // page's own, shorter, foot.
    val count = listOf(period - 1, period, period + 1).maxOf { beside ->
        (packOf(state.periods.startOf(beside, state.view)).placed.maxOfOrNull { it.row } ?: -1) + 1
    }
    // Two lines an event, a list's worth of room: its title, then its time or
    // where it is, as a planner's bar is.
    val item = with(LocalDensity.current) { Theme.typography.labelSmall.lineHeight.toDp() } * CondensedLines + Theme.spacing.xxs * 2
    val gap = Theme.spacing.xxs / 2
    val list = rememberSaveable(saver = LazyListState.Saver) {
        // Read once, as the page comes in: not followed as the other scrolls.
        Snapshot.withoutReadObservation {
            val beside = lists[scope.pager.periods.origin] ?: lists.values.firstOrNull()
            LazyListState(beside?.firstVisibleItemIndex ?: 0, beside?.firstVisibleItemScrollOffset ?: 0)
        }
    }
    DisposableEffect(lists, period, list) {
        lists[period] = list
        onDispose { if (lists[period] === list) lists.remove(period) }
    }
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
        val pagePx = constraints.maxWidth
        CompositionLocalProvider(LocalItemLines provides CondensedLines) {
            CondensedRows(scope, list, count, rows, item, gap, cell, from) { placement ->
                if (still || !placement.item.bar) null else runOnTitle(scope.pager, period, origin, placement, before, after, days, cell, gap, pagePx)
            }
        }
        DaysGhostLayer(scope, from, weeks = 1, days = days, slot = gap / 2, item = item, gap = gap)
        EditScroll(scope, placed, list, byDays = true)
    }
}

/** How many pages' packed rows a condensed view keeps: the pages in view and those either side, with room to spare. */
private const val KeptCondensedPacks = 12

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
    /** A bar's one title with the page beside it: see [runOnTitle]. */
    titleOf: @Composable (WeekPlacement) -> RunOnTitle?,
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
                    val title = titleOf(placement)
                    EventChip(
                        event = shown.event,
                        presentation = if (shown.bar) EventPresentation.Bar else EventPresentation.Line,
                        scope = scope,
                        clippedStart = shown.continuesBefore,
                        clippedEnd = shown.continuesAfter,
                        focusableOn = from.plus(shown.from, DateTimeUnit.DAY)..from.plus(shown.to, DateTimeUnit.DAY),
                        contentWidth = title?.width,
                        contentAt = title?.at,
                        modifier = Modifier
                            .padding(top = gap / 2)
                            .barAcross(cell, shown.from, shown.to, shown.continuesBefore, shown.continuesAfter, gap)
                            .height(item - gap),
                    )
                }
            }
        }
    }
}

