package io.kontour.ui.components.calendar

import io.kontour.ui.theme.drawDot
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.datetime.CalendarMonthDefaults
import io.kontour.ui.components.datetime.LocalTodayFlash
import io.kontour.ui.components.datetime.TodayFlashAlpha
import io.kontour.ui.foundation.Text
import io.kontour.ui.interaction.pressable
import io.kontour.ui.locale.LocaleFormats
import io.kontour.ui.locale.NameWidth
import io.kontour.ui.motion.ScrollDefaults
import io.kontour.ui.theme.Theme
import io.kontour.ui.theme.Tone
import io.kontour.ui.theme.toneColours
import kotlin.math.abs
import kotlinx.coroutines.flow.collectLatest
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/**
 * Small months, as many to a row as fit, each busy day marked in the tone of
 * its first event, scrolling up and down through the years as the month does
 * through its weeks: asked for as *"please make it infinite scroll (up and
 * down, similar to the new month scrolling)"*. The year most on screen is the
 * one the title and the state are about. A month's name opens the month; a
 * day, the day.
 *
 * Each month is drawn, not composed: its days are one node, their numbers
 * measured once for every month and drawn from there, and one press finds the
 * day under it. A month was the date picker's — forty-odd controls, each with
 * its own animations — and came in a frame at a time, one month after
 * another: *"some months tend to load in one at a time"*.
 */
@Composable
internal fun YearView(scope: CalendarScope) {
    val tones = Tone.entries.associateWith { toneColours(it).solid }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = (maxWidth / CalendarMonthDefaults.MaxWidth).toInt().coerceIn(1, MaxColumns)
        val digits = rememberDigits(scope.formats, scope.state.periods.firstDayOfWeek)
        // Each year's busy days, found once for as long as the events are the same.
        val busyByYear = remember(scope.events) { HashMap<Int, Map<LocalDate, Tone>>() }
        val busy = { year: Int -> busyByYear.getOrPut(year) { busyDays(scope, year) } }
        if (!constraints.hasBoundedHeight) {
            // Given no height, nothing scrolls: the year, in a column, and
            // today in it as soon as it is gone back to.
            val year = scope.state.date.year
            LaunchedEffect(scope.state) {
                var seen = scope.state.todayRequest?.serial
                snapshotFlow { scope.state.todayRequest }.collect { request ->
                    if (request == null || request.serial == seen) return@collect
                    seen = request.serial
                    scope.todayFlash?.flash()
                }
            }
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Theme.spacing.md)) {
                for (months in Month.entries.chunked(columns)) YearRow(scope, year, months, columns, busy(year), tones, digits)
            }
            return@BoxWithConstraints
        }
        val perYear = 1 + MonthsPerYear / columns
        val list = rememberYearList(scope, columns, perYear)
        LazyColumn(
            state = list,
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Theme.spacing.md),
            flingBehavior = ScrollDefaults.flingBehavior(),
            userScrollEnabled = scope.enabled,
        ) {
            items(YearItems * perYear, key = { index -> yearKey(index, perYear, columns) }) { index ->
                val year = FirstYear + index / perYear
                val within = index % perYear
                if (within == 0) {
                    Text(
                        scope.formats.digits(year.toLong()),
                        style = Theme.typography.titleLarge,
                        modifier = Modifier
                            .padding(horizontal = Theme.spacing.xs)
                            .padding(top = Theme.spacing.sm)
                            .semantics { heading() },
                    )
                } else {
                    val from = (within - 1) * columns
                    YearRow(scope, year, Month.entries.subList(from, from + columns), columns, busy(year), tones, digits)
                }
            }
        }
    }
}

private const val MaxColumns = 4
private const val MonthsPerYear = 12

/** The first year the year's list has. */
internal const val FirstYear = 1

/** How many years the list has, from [FirstYear]: to the end of 9999. */
private const val YearItems = 9999

/** A row's key, which a change in how many months a row holds does not reuse. */
private fun yearKey(index: Int, perYear: Int, columns: Int): String {
    val year = FirstYear + index / perYear
    val within = index % perYear
    return if (within == 0) "y$year" else "m$year-${(within - 1) * columns}-$columns"
}

/** The list's item for [date]'s month, in rows of [columns] with [perYear] items a year. */
internal fun yearItemOf(date: LocalDate, columns: Int, perYear: Int): Int =
    (date.year - FirstYear).coerceAtLeast(0) * perYear + 1 + date.month.ordinal / columns

/**
 * The year list, and what ties it to the state, as the month's weeks are:
 * scrolled to show a year, the state is about it; the state moved to another
 * year — the header, today, the app — the list goes to its month.
 *
 * Today gone back to is brought in even where the state was about it
 * already, scrolled away from within its own year, and flashed once it is
 * there, as every other view flashes it: *"in the event calendar's year view,
 * clicking the 'today' button doesn't flash today like every other calendar
 * view does"*. Flashed at the press, as the other views are, it would play on
 * a month still off screen or still sliding in.
 */
@Composable
private fun rememberYearList(scope: CalendarScope, columns: Int, perYear: Int): LazyListState {
    val state = scope.state
    val list = remember(columns) { LazyListState(firstVisibleItemIndex = yearItemOf(state.date, columns, perYear)) }
    val motion by rememberUpdatedState(Theme.motion)
    val going = remember { BooleanArray(1) }
    LaunchedEffect(list, perYear) {
        snapshotFlow {
            // Each year's pixels on screen.
            val info = list.layoutInfo
            val shares = HashMap<Int, Int>()
            for (item in info.visibleItemsInfo) {
                val shown = (minOf(item.offset + item.size, info.viewportEndOffset) - maxOf(item.offset, info.viewportStartOffset)).coerceAtLeast(0)
                val year = FirstYear + item.index / perYear
                shares[year] = (shares[year] ?: 0) + shown
            }
            shares
        }.collect { shares ->
            if (going[0] || state.view != CalendarView.Year) return@collect
            val current = state.date.year
            val best = shares.maxByOrNull { it.value } ?: return@collect
            val enough = (best.value - (shares[current] ?: 0)) > list.layoutInfo.viewportSize.height / 4
            if (best.key != current && enough) state.showYearFromScroll(best.key)
        }
    }
    LaunchedEffect(list, perYear) {
        var seen = state.todayRequest?.serial
        // The date and today's requests together: a press of today sets both,
        // and one scroll answers it.
        snapshotFlow { state.date to state.todayRequest }.collectLatest { (date, request) ->
            if (state.view != CalendarView.Year) return@collectLatest
            val pointed = request != null && request.serial != seen
            if (!pointed && date == state.scrolledDate) return@collectLatest
            val target = yearItemOf(date, columns, perYear)
            // Already on screen, whole, it stays where it is.
            val item = list.layoutInfo.visibleItemsInfo.firstOrNull { it.index == target }
            val whole = item != null && item.offset >= 0 && item.offset + item.size <= list.layoutInfo.viewportEndOffset
            if (!whole) {
                going[0] = true
                try {
                    // At once under reduced motion, as the month's arrows go.
                    if (motion.reduceMotion || abs(target - list.firstVisibleItemIndex) > perYear * 2) list.scrollToItem(target) else list.animateScrollToItem(target)
                } finally {
                    going[0] = false
                }
            }
            if (pointed) {
                seen = request.serial
                // Now that it is there: a flash is a fade rather than a
                // movement, so it plays under reduced motion too, as in the
                // other views.
                scope.todayFlash?.flash()
            }
        }
    }
    return list
}

/** [year]'s days with an event, each in the tone of its first. */
private fun busyDays(scope: CalendarScope, year: Int): Map<LocalDate, Tone> {
    val first = LocalDate(year, 1, 1)
    val last = LocalDate(year, 12, 31)
    val marks = HashMap<LocalDate, Tone>()
    for (event in scope.eventsBetween(first, last).sortedBy { it.start }) {
        var day = maxOf(event.start.date, first)
        val end = lastDayOf(event.start, event.end)
        while (day <= end && day <= last) {
            marks.getOrPut(day) { event.tone }
            day = day.plus(1, DateTimeUnit.DAY)
        }
    }
    return marks
}

/** One row of a year's months, each as wide as the rest. */
@Composable
private fun YearRow(
    scope: CalendarScope,
    year: Int,
    row: List<Month>,
    columns: Int,
    busy: Map<LocalDate, Tone>,
    tones: Map<Tone, Color>,
    digits: Digits,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Theme.spacing.sm)) {
        for (month in row) {
            val firstOfMonth = LocalDate(year, month, 1)
            val busyDays = remember(busy, firstOfMonth) { busy.keys.count { it.year == year && it.month == month } }
            val described = scope.formats.monthAndYear(firstOfMonth)
            val count = if (busyDays > 0) scope.strings.eventCount(scope.formats.quantity(busyDays)) else null
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                // The month, to a screen reader and the keyboard: its name and
                // year, how busy it is, and a press that opens it. Its days are
                // reached in the month.
                Text(
                    scope.formats.monthName(month, NameWidth.Wide),
                    style = Theme.typography.titleSmall,
                    maxLines = 1,
                    modifier = Modifier
                        .pressable(
                            onClick = { scope.state.show(firstOfMonth, CalendarView.Month) },
                            shape = Theme.shapes.capsule,
                            enabled = scope.enabled,
                            minimumTouchTarget = false,
                        )
                        .semantics {
                            heading()
                            contentDescription = described
                            if (count != null) stateDescription = count
                        }
                        .padding(horizontal = Theme.spacing.xs, vertical = Theme.spacing.xxs),
                )
                MiniMonth(scope, firstOfMonth, busy, tones, digits)
            }
        }
        // A short last row keeps its months the width of the rest.
        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
    }
}

/**
 * A month's days, drawn: the weekday initials, then a row a week of day
 * numbers from [digits], today ringed as the date picker rings it and a busy
 * day dotted under its number. A press on a day opens it.
 */
@Composable
private fun MiniMonth(scope: CalendarScope, month: LocalDate, busy: Map<LocalDate, Tone>, tones: Map<Tone, Color>, digits: Digits) {
    val today = scope.today
    val colours = Theme.colours
    val ink = if (scope.enabled) colours.content else colours.contentMuted
    val muted = colours.contentMuted
    val ring = colours.outlineStrong
    val ringWidth = Theme.sizing.borderWidth
    // Today pointed at: the date picker's pulse inside its ring, read as the
    // days are drawn, so a pulse redraws this month and composes nothing.
    val flash = LocalTodayFlash.current
    val flashColour = colours.accent.solid
    val density = LocalDensity.current
    val markerGap = with(density) { MarkerGap.toPx() }
    val markerRadius = with(density) { MarkerSize.toPx() } / 2f
    // Today's ring and the busy days' dots, in the theme's pill, as a month
    // grid's day cells are. 0.5.2.
    val dotShape = Theme.shapes.pill
    // At least as tall as a number and its busy dot, where 40dp cut both at
    // large type.
    val numberHeight = digits.numbers.maxOfOrNull { it.size.height }?.toFloat() ?: 0f
    val maxRow = maxOf(with(density) { MaxRowHeight.toPx() }, numberHeight + markerGap + markerRadius * 2)
    val lead = digits.columnOf(month.dayOfWeek)
    val length = month.plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY).day
    val rows = (lead + length + DaysPerWeek - 1) / DaysPerWeek
    val headerPx = digits.initialsHeight
    val openDay = rememberUpdatedState { day: LocalDate -> scope.state.show(day, CalendarView.Day) }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Spacer(
        Modifier
            .widthIn(max = CalendarMonthDefaults.MaxWidth)
            .fillMaxWidth()
            .layout { measurable, constraints ->
                val width = constraints.maxWidth
                val row = minOf(width / DaysPerWeek.toFloat(), maxRow).toInt()
                val height = (headerPx + row * rows).toInt()
                // The days' whole box, for the press and the drawing that follow to have.
                val placeable = measurable.measure(Constraints.fixed(width, height))
                layout(width, height) { placeable.place(0, 0) }
            }
            .clearAndSetSemantics { }
            .pointerInput(month, scope.enabled, lead, rows, rtl) {
                if (!scope.enabled) return@pointerInput
                detectTapGestures { at ->
                    val cell = size.width / DaysPerWeek.toFloat()
                    val row = minOf(cell, maxRow)
                    val y = at.y - headerPx
                    if (y < 0f) return@detectTapGestures
                    val column = (at.x / cell).toInt().coerceIn(0, DaysPerWeek - 1)
                    val reading = if (rtl) DaysPerWeek - 1 - column else column
                    val day = (y / row).toInt() * DaysPerWeek + reading - lead + 1
                    if (day in 1..length) openDay.value(LocalDate(month.year, month.month, day))
                }
            }
            .drawWithCache {
                val cell = size.width / DaysPerWeek
                val row = minOf(cell, maxRow)
                val rtl = layoutDirection == LayoutDirection.Rtl
                fun x(column: Int) = (if (rtl) DaysPerWeek - 1 - column else column) * cell + cell / 2f
                onDrawBehind {
                    for (column in 0 until DaysPerWeek) {
                        val initial = digits.initials[column]
                        drawText(initial, muted, topLeft = Offset(x(column) - initial.size.width / 2f, (headerPx - initial.size.height) / 2f))
                    }
                    for (day in 1..length) {
                        val index = lead + day - 1
                        val column = index % DaysPerWeek
                        val centre = Offset(x(column), headerPx + (index / DaysPerWeek) * row + row / 2f)
                        val date = LocalDate(month.year, month.month, day)
                        val isToday = date == today
                        val text = if (isToday) digits.todayNumbers[day - 1] else digits.numbers[day - 1]
                        if (isToday) {
                            val amount = flash?.invoke() ?: 0f
                            if (amount > 0f) drawDot(flashColour, radius = row * TodayRingShare, center = centre, shape = dotShape, alpha = amount * TodayFlashAlpha)
                            drawDot(ring, radius = row * TodayRingShare, center = centre, shape = dotShape, style = Stroke(ringWidth.toPx()))
                        }
                        drawText(text, ink, topLeft = Offset(centre.x - text.size.width / 2f, centre.y - text.size.height / 2f))
                        val tone = busy[date] ?: continue
                        drawDot(
                            tones.getValue(tone),
                            radius = markerRadius,
                            center = Offset(centre.x, centre.y + text.size.height / 2f + markerGap + markerRadius),
                            shape = dotShape,
                        )
                    }
                }
            },
    )
}

/** The tallest a week's row is, however wide its days, unless its numbers need more. */
private val MaxRowHeight = 40.dp

/** The busy dot's width, and its gap below the number. */
private val MarkerSize = 4.dp
private val MarkerGap = 1.dp

/** Today's ring against a row's height, as the date picker draws it. */
private const val TodayRingShare = 0.46f

/**
 * Every month's numbers and weekday initials, laid out once for the whole
 * year view: a month draws from these, and measures nothing of its own.
 */
private class Digits(
    val numbers: List<TextLayoutResult>,
    val todayNumbers: List<TextLayoutResult>,
    val initials: List<TextLayoutResult>,
    val initialsHeight: Float,
    private val firstDayOfWeek: DayOfWeek,
) {
    /** Which column of a week [day] is in, from the first day of the week. */
    fun columnOf(day: DayOfWeek): Int = (day.ordinal - firstDayOfWeek.ordinal).mod(DaysPerWeek)
}

@Composable
private fun rememberDigits(formats: LocaleFormats, firstDayOfWeek: DayOfWeek): Digits {
    val measurer: TextMeasurer = rememberTextMeasurer(cacheSize = 0)
    val type = Theme.typography
    val number = type.bodyMedium
    val today = type.labelMedium
    val initial = type.labelSmall.copy(fontWeight = FontWeight.Normal)
    val padding = with(LocalDensity.current) { Theme.spacing.xxs.toPx() }
    return remember(measurer, formats, firstDayOfWeek, number, today, initial, padding) {
        fun measure(text: String, style: TextStyle) = measurer.measure(text, style, maxLines = 1, softWrap = false)
        val initials = (0 until DaysPerWeek).map { DayOfWeek.entries[(firstDayOfWeek.ordinal + it) % DaysPerWeek] }
            .map { measure(formats.weekdayName(it, NameWidth.Narrow), initial) }
        Digits(
            numbers = (1..31).map { measure(formats.integer(it), number) },
            todayNumbers = (1..31).map { measure(formats.integer(it), today) },
            initials = initials,
            initialsHeight = (initials.maxOfOrNull { it.size.height } ?: 0) + padding * 2,
            firstDayOfWeek = firstDayOfWeek,
        )
    }
}
