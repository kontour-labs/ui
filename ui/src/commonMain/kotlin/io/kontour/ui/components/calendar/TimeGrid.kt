package io.kontour.ui.components.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.kontour.ui.a11y.highContrast
import io.kontour.ui.foundation.Text
import io.kontour.ui.interaction.pressable
import io.kontour.ui.locale.DateStyle
import io.kontour.ui.locale.NameWidth
import io.kontour.ui.motion.ScrollDefaults
import io.kontour.ui.theme.Theme
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.first
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.plus

/**
 * A day's hours, or a week's side by side: the days' headings, what takes the
 * whole day above the hours, and the hours themselves, one scroll for all the
 * days so a time is level across the week.
 */
@Composable
internal fun TimeView(scope: CalendarScope) {
    val state = scope.state
    val pager = scope.pager
    val days = if (state.view == CalendarView.Day) 1 else DaysPerWeek
    val gutter = gutterWidth(scope)
    val swipe = with(pager) { Modifier.swipesPeriods(scope.enabled) }
    val hourPx = with(LocalDensity.current) { scope.hourHeight.toPx() }

    Column(Modifier.fillMaxWidth().then(swipe)) {
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.width(gutter))
            PeriodStrip(pager, Modifier.weight(1f)) { period -> DayHeadings(scope, period, days) }
        }
        AllDayStrip(scope, gutter, days)
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f, fill = false)) {
            // In a page that scrolls, the hours have no height to fill: a working
            // day's worth of them, scrolling within.
            val viewport = if (constraints.hasBoundedHeight) Modifier.fillMaxHeight() else Modifier.height(scope.hourHeight * UnboundedHours)
            Row(
                viewport
                    .fillMaxWidth()
                    .verticalScroll(state.timeScroll, flingBehavior = ScrollDefaults.flingBehavior()),
            ) {
                HourGutter(scope, gutter)
                PeriodStrip(pager, Modifier.weight(1f).height(scope.hourHeight * HoursPerDay)) { period ->
                    DayColumns(scope, period, days)
                }
            }
        }
    }

    // Opened at the start of a working day, once, or put back where it was.
    LaunchedEffect(state, hourPx) {
        snapshotFlow { state.timeScroll.maxValue }.first { it > 0 }
        val restored = state.restoredScroll
        if (restored != null) {
            state.timeScroll.scrollTo(restored)
            state.restoredScroll = null
        } else if (!state.scrolledToStart) {
            state.timeScroll.scrollTo((state.initialScrollTime.minuteOfDay * hourPx / MinutesPerHour).roundToInt())
        }
        state.scrolledToStart = true
    }
}

private const val HoursPerDay = 24
private const val MinutesPerHour = 60
private const val UnboundedHours = 8

/** As wide as the widest hour label and a margin either side. */
@Composable
private fun gutterWidth(scope: CalendarScope): Dp {
    val measurer = rememberTextMeasurer()
    val style = Theme.typography.labelSmall
    val density = LocalDensity.current
    val margin = Theme.spacing.xs
    return remember(scope.formats, style, density, measurer) {
        val widest = (0 until HoursPerDay).maxOf { hour ->
            measurer.measure(scope.formats.time(LocalTime(hour, 0)), style).size.width
        }
        with(density) { widest.toDp() } + margin * 2
    }
}

/** Each day's weekday and date over its column, the date opening that day. */
@Composable
private fun DayHeadings(scope: CalendarScope, period: Int, days: Int) {
    val start = scope.state.periods.startOf(period, scope.state.view)
    val colours = Theme.colours
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val narrow = maxWidth / days < WeekdayHeadingWidth
        Row(Modifier.fillMaxWidth()) {
            for (column in 0 until days) {
                val date = start.plus(column, DateTimeUnit.DAY)
                val isToday = date == scope.today
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = Theme.spacing.xxs)
                        .semantics(mergeDescendants = true) { heading() },
                ) {
                    Text(
                        scope.formats.weekdayName(date.dayOfWeek, if (narrow) NameWidth.Narrow else NameWidth.Abbreviated),
                        style = Theme.typography.labelSmall,
                        colour = quietInk(),
                        maxLines = 1,
                        modifier = Modifier.clearAndSetSemantics { },
                    )
                    Text(
                        scope.formats.integer(date.day),
                        style = Theme.typography.titleSmall,
                        colour = if (isToday) colours.accent.onSolid else colours.content,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier
                            .then(if (isToday) Modifier.background(colours.accent.solid, Theme.shapes.capsule) else Modifier)
                            .pressable(
                                onClick = { scope.state.show(date, CalendarView.Day) },
                                shape = Theme.shapes.capsule,
                                enabled = scope.enabled,
                                minimumTouchTarget = false,
                            )
                            .semantics { contentDescription = scope.formats.date(date, DateStyle.Full) }
                            .padding(horizontal = Theme.spacing.xs),
                    )
                }
            }
        }
    }
}

/** Below this a day's heading has room for its weekday's letter, not its abbreviation. */
private val WeekdayHeadingWidth = 48.dp

/** What takes the whole day, above the hours: as bars across their days. */
@Composable
private fun AllDayStrip(scope: CalendarScope, gutter: Dp, days: Int) {
    val state = scope.state
    val start = state.periods.startOf(state.period, state.view)
    val end = start.plus(days - 1, DateTimeUnit.DAY)
    val any = remember(scope.events, start, days) { weekItems(start, scope.eventsBetween(start, end), lines = false, days = days).isNotEmpty() }
    if (!any) return
    val item = with(LocalDensity.current) { Theme.typography.labelSmall.lineHeight.toDp() } + Theme.spacing.xxs * 2
    Row(Modifier.fillMaxWidth()) {
        Box(Modifier.width(gutter).padding(top = Theme.spacing.xxs), contentAlignment = Alignment.TopEnd) {
            Text(
                Theme.strings.allDay,
                style = Theme.typography.labelSmall,
                colour = quietInk(),
                maxLines = 1,
                modifier = Modifier.padding(horizontal = Theme.spacing.xs).clearAndSetSemantics { },
            )
        }
        PeriodStrip(scope.pager, Modifier.weight(1f)) { period ->
            val from = state.periods.startOf(period, state.view)
            val to = from.plus(days - 1, DateTimeUnit.DAY)
            val pack = remember(scope.events, from, days) {
                packWeek(weekItems(from, scope.eventsBetween(from, to), lines = false, days = days), maxRows = AllDayRows, days = days)
            }
            val rows = (pack.placed.maxOfOrNull { it.row } ?: -1) + 1 + if (pack.more.any { it > 0 }) 1 else 0
            BoxWithConstraints(Modifier.fillMaxWidth().height(item * rows)) {
                val cell = maxWidth / days
                val gap = Theme.spacing.xxs / 2
                for (placement in pack.placed) {
                    val placed = placement.item
                    EventChip(
                        event = placed.event,
                        presentation = EventPresentation.Bar,
                        scope = scope,
                        modifier = Modifier
                            .padding(start = cell * placed.from + gap, top = item * placement.row + gap / 2)
                            .width(cell * (placed.to - placed.from + 1) - gap * 2)
                            .height(item - gap),
                    )
                }
                for (column in 0 until days) {
                    val more = pack.more[column]
                    if (more == 0) continue
                    val date = from.plus(column, DateTimeUnit.DAY)
                    val described = Theme.strings.moreEvents(scope.formats.quantity(more))
                    Text(
                        Theme.strings.moreCount(scope.formats.quantity(more)),
                        style = Theme.typography.labelSmall,
                        colour = quietInk(),
                        maxLines = 1,
                        modifier = Modifier
                            .padding(start = cell * column + gap, top = item * pack.shownRows[column])
                            .pressable(
                                onClick = { scope.state.show(date, CalendarView.Day) },
                                shape = Theme.shapes.capsule,
                                enabled = scope.enabled,
                                minimumTouchTarget = false,
                            )
                            .semantics { contentDescription = described }
                            .padding(horizontal = Theme.spacing.xs),
                    )
                }
            }
        }
    }
}

/** At most this many rows of all-day bars before a day says "+N". */
private const val AllDayRows = 3

/** The hours down the side, each label level with its line. */
@Composable
private fun HourGutter(scope: CalendarScope, width: Dp) {
    val hour = scope.hourHeight
    val marginDp = Theme.spacing.xs
    Layout(
        content = {
            for (h in 1 until HoursPerDay) {
                Text(
                    scope.formats.time(LocalTime(h, 0)),
                    style = Theme.typography.labelSmall,
                    colour = quietInk(),
                    maxLines = 1,
                    modifier = Modifier.clearAndSetSemantics { },
                )
            }
        },
        modifier = Modifier.width(width).height(hour * HoursPerDay),
    ) { measurables, constraints ->
        val hourPx = hour.toPx()
        val margin = marginDp.roundToPx()
        val labels = measurables.map { it.measure(Constraints(maxWidth = (constraints.maxWidth - margin * 2).coerceAtLeast(0))) }
        layout(constraints.maxWidth, constraints.maxHeight) {
            labels.forEachIndexed { i, label ->
                val y = ((i + 1) * hourPx - label.height / 2f).roundToInt()
                label.placeRelative(constraints.maxWidth - margin - label.width, y)
            }
        }
    }
}

/** The days' columns of the period, side by side. */
@Composable
private fun DayColumns(scope: CalendarScope, period: Int, days: Int) {
    val start = scope.state.periods.startOf(period, scope.state.view)
    Row(Modifier.fillMaxWidth().fillMaxHeight()) {
        for (column in 0 until days) {
            DayColumn(scope, start.plus(column, DateTimeUnit.DAY), Modifier.weight(1f).fillMaxHeight())
        }
    }
}

/**
 * One day's hours: a line each hour, its events laid side by side where they
 * overlap, and the line for now on today.
 */
@Composable
private fun DayColumn(scope: CalendarScope, date: LocalDate, modifier: Modifier) {
    val density = LocalDensity.current
    val colours = Theme.colours
    val line = Theme.sizing.borderWidth
    val nowLine = Theme.sizing.borderWidthStrong
    val lineColour = if (highContrast()) colours.outlineStrong else colours.outline
    val nowColour = colours.accent.solid
    val hourPx = with(density) { scope.hourHeight.toPx() }
    val minChipPx = with(density) { (Theme.typography.labelMedium.lineHeight.toDp() + Theme.spacing.xxs * 2).toPx() }
    val minMinutes = if (hourPx > 0f) ceil(minChipPx / (hourPx / MinutesPerHour)).toInt() else 0
    val placements = remember(scope.events, date, minMinutes) { layoutTimedDay(date, scope.eventsBetween(date, date), minMinutes) }
    val now = scope.now?.takeIf { it.date == date }?.time
    val click by rememberUpdatedState(scope.onTimeClick)
    val step = TapStepMinutes
    val gapPx = with(density) { (Theme.spacing.xxs / 2).toPx() }

    Layout(
        content = {
            for (placement in placements) {
                key(placement.event.id, placement.start) {
                    EventChip(event = placement.event, presentation = EventPresentation.Block, scope = scope)
                }
            }
        },
        modifier = modifier
            .drawBehind {
                val width = line.toPx()
                // The day's start edge, and an hour's line across it each hour.
                val x = if (layoutDirection == LayoutDirection.Rtl) size.width - width / 2 else width / 2
                drawLine(lineColour, Offset(x, 0f), Offset(x, size.height), width)
                for (h in 1 until HoursPerDay) {
                    val y = h * hourPx
                    drawLine(lineColour, Offset(0f, y), Offset(size.width, y), width)
                }
                if (now != null) {
                    val y = now.minuteOfDay * hourPx / MinutesPerHour
                    val stroke = nowLine.toPx()
                    drawLine(nowColour, Offset(0f, y), Offset(size.width, y), stroke)
                    val dot = if (layoutDirection == LayoutDirection.Rtl) size.width else 0f
                    drawCircle(nowColour, radius = stroke * NowDotScale, center = Offset(dot, y))
                }
            }
            .then(
                if (scope.onTimeClick != null && scope.enabled) {
                    Modifier.pointerInput(date, hourPx) {
                        detectTapGestures { at ->
                            val minute = floorTo((at.y / hourPx * MinutesPerHour).toLong(), step).coerceIn(0L, (MinutesPerDay - step).toLong())
                            click?.invoke(date.atMidnight.plusMinutes(minute))
                        }
                    }
                } else {
                    Modifier
                },
            ),
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val perMinute = hourPx / MinutesPerHour
        val placeables = measurables.mapIndexed { i, measurable ->
            val p = placements[i]
            val lane = width.toFloat() / p.lanes
            val w = (lane - gapPx).roundToInt().coerceAtLeast(0)
            val h = (maxOf(minChipPx, (p.end - p.start) * perMinute) - gapPx).roundToInt().coerceAtLeast(0)
            measurable.measure(Constraints.fixed(w, h))
        }
        layout(width, height) {
            placeables.forEachIndexed { i, placeable ->
                val p = placements[i]
                val lane = width.toFloat() / p.lanes
                placeable.placeRelative((p.lane * lane + gapPx / 2).roundToInt(), (p.start * perMinute + gapPx / 2).roundToInt())
            }
        }
    }
}

/** An empty time pressed is the quarter-hour it is in. */
private const val TapStepMinutes = 15

/** The dot at the start of the line for now, against the line's own thickness. */
private const val NowDotScale = 2.5f
