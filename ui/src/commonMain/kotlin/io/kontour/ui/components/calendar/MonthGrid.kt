package io.kontour.ui.components.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.kontour.ui.a11y.highContrast
import io.kontour.ui.foundation.Text
import io.kontour.ui.interaction.pressable
import io.kontour.ui.locale.DateStyle
import io.kontour.ui.locale.ListStyle
import io.kontour.ui.locale.NameWidth
import io.kontour.ui.overlay.LocalOverlayHostOrNull
import io.kontour.ui.overlay.Popover
import io.kontour.ui.theme.Theme
import kotlin.math.floor
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/**
 * A month of day boxes, a week to a row — every week the month touches, with
 * the days of the months either side in them, quieter — and each box holding
 * as many of its events as fit, and "+N" for the rest.
 */
@Composable
internal fun MonthView(scope: CalendarScope) {
    val pager = scope.pager
    Column(Modifier.fillMaxWidth()) {
        WeekdayRow(scope)
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f, fill = false)) {
            // Bounded, the weeks share the height; in a scrolling page, each
            // week is as tall as a date and three events.
            val bounded = constraints.hasBoundedHeight
            val minRow = monthRowMinHeight()
            val swipe = with(pager) { Modifier.swipesPeriods(scope.enabled) }
            PeriodStrip(pager, Modifier.fillMaxWidth().then(if (bounded) Modifier.fillMaxHeight() else Modifier).then(swipe)) { month ->
                MonthPage(scope, month, if (bounded) null else minRow)
            }
        }
    }
}

/** The weekday names over the month's columns: abbreviated, or a letter where they do not fit. */
@Composable
private fun WeekdayRow(scope: CalendarScope) {
    val periods = scope.state.periods
    val weekStart = periods.weekStart(scope.state.date)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val narrow = maxWidth / DaysPerWeek < WeekdayAbbreviatedWidth
        Row(Modifier.fillMaxWidth().padding(vertical = Theme.spacing.xxs).clearAndSetSemantics { }) {
            for (column in 0 until DaysPerWeek) {
                val day = dayInWeek(weekStart, column).dayOfWeek
                Text(
                    scope.formats.weekdayName(day, if (narrow) NameWidth.Narrow else NameWidth.Abbreviated),
                    style = Theme.typography.labelSmall,
                    colour = quietInk(),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** Below this a column has room for a weekday's letter, not its abbreviation. */
private val WeekdayAbbreviatedWidth = 40.dp

/** A week row's height in a calendar given no height of its own: its date and three events. */
@Composable
private fun monthRowMinHeight(): Dp = dateLabelHeight() + itemHeight() * MinRowsInPage

private const val MinRowsInPage = 3

@Composable
private fun itemHeight(): Dp = with(LocalDensity.current) { Theme.typography.labelSmall.lineHeight.toDp() } + Theme.spacing.xxs * 2

@Composable
private fun dateLabelHeight(): Dp = with(LocalDensity.current) { Theme.typography.labelMedium.lineHeight.toDp() } + Theme.spacing.xs * 2

@Composable
private fun MonthPage(scope: CalendarScope, month: Int, rowHeight: Dp?) {
    val periods = scope.state.periods
    val dates = periods.visibleDates(month, CalendarView.Month)
    val weeks = periods.weeksIn(month)
    val firstOfMonth = periods.startOf(month, CalendarView.Month)
    val events = remember(scope.events, dates) { scope.eventsBetween(dates.start, dates.endInclusive) }
    Column(
        Modifier
            .fillMaxWidth()
            .then(if (rowHeight == null) Modifier.fillMaxHeight() else Modifier)
            .semantics { collectionInfo = CollectionInfo(rowCount = weeks, columnCount = DaysPerWeek) },
    ) {
        for (week in 0 until weeks) {
            val weekStart = dates.start.plus(week * DaysPerWeek, DateTimeUnit.DAY)
            val rowModifier = if (rowHeight == null) Modifier.weight(1f) else Modifier.height(rowHeight)
            MonthWeek(scope, weekStart, firstOfMonth, events, rowModifier.fillMaxWidth())
        }
    }
}

@Composable
private fun MonthWeek(
    scope: CalendarScope,
    weekStart: LocalDate,
    firstOfMonth: LocalDate,
    events: List<CalendarEvent>,
    modifier: Modifier,
) {
    val item = itemHeight()
    val dateLabel = dateLabelHeight()
    val gap = Theme.spacing.xxs / 2
    val weekEvents = remember(events, weekStart) {
        events.filter { it.overlaps(weekStart, weekStart.plus(DaysPerWeek - 1, DateTimeUnit.DAY)) }
    }
    BoxWithConstraints(modifier) {
        val cell = maxWidth / DaysPerWeek
        val room = (maxHeight - dateLabel).coerceAtLeast(0.dp)
        val maxRows = if (item > 0.dp) floor(room / item).toInt().coerceAtLeast(0) else 0
        val pack = remember(weekEvents, weekStart, maxRows) { packWeek(weekItems(weekStart, weekEvents, lines = true), maxRows) }
        Row(Modifier.fillMaxSize()) {
            for (column in 0 until DaysPerWeek) {
                val date = dayInWeek(weekStart, column)
                val count = weekEvents.count { it.coversDay(date) }
                DayBox(
                    scope = scope,
                    date = date,
                    inMonth = date.month == firstOfMonth.month && date.year == firstOfMonth.year,
                    count = count,
                    more = pack.more[column],
                    moreRow = pack.shownRows[column],
                    itemHeight = item,
                    dateLabel = dateLabel,
                    events = weekEvents,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
        }
        for (placement in pack.placed) {
            val placed = placement.item
            val span = placed.to - placed.from + 1
            EventChip(
                event = placed.event,
                presentation = if (placed.bar) EventPresentation.Bar else EventPresentation.Line,
                scope = scope,
                modifier = Modifier
                    .padding(start = cell * placed.from + gap, top = dateLabel + item * placement.row + gap / 2)
                    .width(cell * span - gap * 2)
                    .height(item - gap),
            )
        }
    }
}

/** Whether any of this event is on [date]. */
internal fun CalendarEvent.coversDay(date: LocalDate): Boolean = overlaps(date, date)

/**
 * Whether any of this event is between [from] and [to], both days included —
 * half-open, as every event is, except that one with no length is on the day
 * it is at.
 */
internal fun CalendarEvent.overlaps(from: LocalDate, to: LocalDate): Boolean {
    val rangeStart = from.atMidnight.epochMinute
    val rangeEnd = to.atMidnight.epochMinute + MinutesPerDay
    val a = start.epochMinute
    val b = end.epochMinute
    return if (a == b) a in rangeStart until rangeEnd else a < rangeEnd && b > rangeStart
}

@Composable
private fun DayBox(
    scope: CalendarScope,
    date: LocalDate,
    inMonth: Boolean,
    count: Int,
    more: Int,
    moreRow: Int,
    itemHeight: Dp,
    dateLabel: Dp,
    events: List<CalendarEvent>,
    modifier: Modifier,
) {
    val formats = scope.formats
    val strings = Theme.strings
    val colours = Theme.colours
    val line = Theme.sizing.borderWidth
    val lineColour = if (highContrast()) colours.outlineStrong else colours.outline
    val isToday = date == scope.today
    val dayClick = scope.onDateClick
    val described = formats.list(
        listOfNotNull(formats.date(date, DateStyle.Full), if (count > 0) strings.eventCount(formats.quantity(count)) else null),
        ListStyle.Unit,
    )
    var listing by remember { mutableStateOf(false) }
    val canList = LocalOverlayHostOrNull.current != null
    Box(
        modifier
            .then(if (inMonth) Modifier else Modifier.background(colours.surfaceSunken))
            .drawBehind {
                // The end and the foot of each box: the grid's lines, drawn once each.
                val width = line.toPx()
                val x = if (layoutDirection == LayoutDirection.Rtl) 0f else size.width - width
                drawLine(lineColour, Offset(x + width / 2, 0f), Offset(x + width / 2, size.height), width)
                drawLine(lineColour, Offset(0f, size.height - width / 2), Offset(size.width, size.height - width / 2), width)
            }
            .then(
                if (dayClick != null) {
                    Modifier.pressable(
                        onClick = { dayClick(date) },
                        shape = RectangleShape,
                        enabled = scope.enabled,
                        minimumTouchTarget = false,
                    )
                } else {
                    Modifier
                },
            )
            .semantics { contentDescription = described },
    ) {
        // The date: its day, opened by pressing its number.
        Box(Modifier.padding(Theme.spacing.xxs).height(dateLabel - Theme.spacing.xxs * 2), contentAlignment = Alignment.CenterStart) {
            Text(
                formats.integer(date.day),
                style = Theme.typography.labelMedium,
                colour = when {
                    isToday -> colours.accent.onSolid
                    inMonth -> colours.content
                    else -> quietInk()
                },
                maxLines = 1,
                // A date is never broken over two lines: "28" as "2" was the
                // narrow box's first try.
                softWrap = false,
                modifier = Modifier
                    .then(if (isToday) Modifier.background(colours.accent.solid, Theme.shapes.capsule) else Modifier)
                    .pressable(
                        onClick = { scope.state.show(date, CalendarView.Day) },
                        shape = Theme.shapes.capsule,
                        enabled = scope.enabled,
                        minimumTouchTarget = false,
                    )
                    .padding(horizontal = Theme.spacing.xxs),
            )
        }
        if (more > 0) {
            Box(Modifier.padding(top = dateLabel + itemHeight * moreRow)) {
                Text(
                    strings.moreCount(formats.quantity(more)),
                    style = Theme.typography.labelSmall,
                    colour = quietInk(),
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier
                        .pressable(
                            // The day's list where there is a host to show it in;
                            // the day itself where there is not.
                            onClick = { if (canList) listing = true else scope.state.show(date, CalendarView.Day) },
                            shape = Theme.shapes.capsule,
                            enabled = scope.enabled,
                            minimumTouchTarget = false,
                        )
                        .semantics { contentDescription = strings.moreEvents(formats.quantity(more)) }
                        .padding(horizontal = Theme.spacing.xs),
                )
                // Every event of the day, from its "+N".
                if (canList) Popover(visible = listing, onDismissRequest = { listing = false }, title = { +formats.date(date, DateStyle.Long) }) {
                    Column(verticalArrangement = Arrangement.spacedBy(Theme.spacing.xxs)) {
                        for (event in events.filter { it.coversDay(date) }.sortedBy { it.start }) {
                            EventChip(
                                event = event,
                                presentation = if (event.allDay) EventPresentation.Bar else EventPresentation.Line,
                                scope = scope,
                                modifier = Modifier.fillMaxWidth().height(itemHeight),
                            )
                        }
                    }
                }
            }
        }
    }
}
