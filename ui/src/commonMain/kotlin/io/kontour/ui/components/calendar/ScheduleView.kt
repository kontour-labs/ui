package io.kontour.ui.components.calendar

import io.kontour.ui.motion.ScrollDefaults
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.kontour.ui.foundation.Text
import io.kontour.ui.interaction.pressable
import io.kontour.ui.locale.LocaleFormats
import io.kontour.ui.locale.NameWidth
import io.kontour.ui.theme.Strings
import io.kontour.ui.theme.Theme
import io.kontour.ui.theme.toneColours
import kotlinx.coroutines.flow.collectLatest
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** One row of a schedule: a month's heading, or a day and its events. */
internal sealed interface ScheduleItem {
    /** What the list knows the row by, so it keeps its place as rows come and go above it. */
    val key: Any
}

/** A month's heading, over its days. */
internal class ScheduleMonth(val first: LocalDate) : ScheduleItem {
    // Odd, where a day's is even: the two never meet.
    override val key: Any get() = first.toEpochDays() * 2 + 1
}

/** A day with its [events]: none, for today with nothing on. */
internal class ScheduleDay(val date: LocalDate, val events: List<CalendarEvent>) : ScheduleItem {
    override val key: Any get() = date.toEpochDays() * 2
}

/**
 * The rows of a schedule from [from] to [to]: each day with anything on, its
 * all-day and longer events first and then the rest by when they start, and
 * [today] whether or not; a month's heading before the first of its days. An
 * event over several days is on each of them.
 */
internal fun scheduleItems(events: List<CalendarEvent>, from: LocalDate, to: LocalDate, today: LocalDate?): List<ScheduleItem> {
    val byDay = HashMap<LocalDate, MutableList<CalendarEvent>>()
    for (event in events) {
        val last = if (event.end > event.start) event.end.plusMinutes(-1).date else event.start.date
        var day = maxOf(event.start.date, from)
        val end = minOf(last, to)
        while (day <= end) {
            byDay.getOrPut(day) { ArrayList() } += event
            day = day.plus(1, DateTimeUnit.DAY)
        }
    }
    if (today != null && today in from..to) byDay.getOrPut(today) { ArrayList() }
    val items = ArrayList<ScheduleItem>()
    var month: LocalDate? = null
    for (day in byDay.keys.sorted()) {
        val first = LocalDate(day.year, day.month, 1)
        if (first != month) {
            items += ScheduleMonth(first)
            month = first
        }
        val sorted = byDay.getValue(day).sortedWith(compareBy<CalendarEvent>({ it.isTimed() }, { it.start }, { it.title }))
        items += ScheduleDay(day, sorted)
    }
    return items
}

/** The row to scroll to for [date]: its month's heading at the first of a month, else the first day from it on. */
internal fun scheduleRowFor(items: List<ScheduleItem>, date: LocalDate): Int {
    val first = date.day == 1
    val at = items.indexOfFirst {
        when (it) {
            is ScheduleMonth -> first && it.first == date
            is ScheduleDay -> it.date >= date
        }
    }
    return if (at >= 0) at else (items.size - 1).coerceAtLeast(0)
}

/**
 * The schedule: the days with something on, under their months, scrolled
 * through. The day at the top is the state's, so the title follows the scroll;
 * a step, today or the app's `show` scrolls the list.
 */
@Composable
internal fun ScheduleView(scope: CalendarScope) {
    val state = scope.state
    val periods = state.periods
    // Six months back and a year on from the month in hand, worked out again
    // as the scroll reaches another month: rows keyed by their day keep their
    // place as months come and go above them.
    val month = periods.indexOf(state.date, CalendarView.Schedule)
    val first = periods.startOf(month, CalendarView.Schedule)
    val from = first.minus(ScheduleMonthsBack, DateTimeUnit.MONTH)
    val to = first.plus(ScheduleMonthsOn, DateTimeUnit.MONTH)
    val items = remember(scope.events, from, scope.today) { scheduleItems(scope.events, from, to, scope.today) }
    val list = remember(periods) { LazyListState(firstVisibleItemIndex = scheduleRowFor(items, state.date)) }
    val rows by rememberUpdatedState(items)
    val motion by rememberUpdatedState(Theme.motion)
    val going = remember { BooleanArray(1) }

    // The list → the state: the day at the top is the one the title is about.
    LaunchedEffect(list) {
        snapshotFlow { list.firstVisibleItemIndex }.collect { index ->
            if (going[0] || state.view != CalendarView.Schedule) return@collect
            val day = when (val item = rows.getOrNull(index)) {
                is ScheduleDay -> item.date
                is ScheduleMonth -> item.first
                null -> return@collect
            }
            if (day != state.date) state.showFromScheduleScroll(day)
        }
    }

    // The state → the list: to the day, or the first after it with anything on.
    LaunchedEffect(list) {
        snapshotFlow { state.date }.collectLatest { date ->
            if (state.view != CalendarView.Schedule || date == state.scrolledDate) return@collectLatest
            val target = scheduleRowFor(rows, date)
            going[0] = true
            try {
                // At once under reduced motion, as the month's scroll does.
                val reduceMotion = motion.reduceMotion
                if (reduceMotion || kotlin.math.abs(target - list.firstVisibleItemIndex) > FarScheduleRows) {
                    list.scrollToItem(target)
                } else {
                    list.animateScrollToItem(target)
                }
            } finally {
                going[0] = false
            }
            state.scrolledDate = date
        }
    }

    LazyColumn(
        state = list,
        userScrollEnabled = scope.enabled,
        flingBehavior = ScrollDefaults.flingBehavior(),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(items, key = { it.key }) { item ->
            when (item) {
                is ScheduleMonth -> ScheduleMonthHeading(scope, item.first)
                is ScheduleDay -> ScheduleDayRow(scope, item)
            }
        }
    }
}

@Composable
private fun ScheduleMonthHeading(scope: CalendarScope, first: LocalDate) {
    Text(
        scope.formats.monthAndYear(first),
        style = Theme.typography.titleSmall,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Theme.spacing.md, bottom = Theme.spacing.xs, start = Theme.spacing.xs)
            .semantics { heading() },
    )
}

@Composable
private fun ScheduleDayRow(scope: CalendarScope, day: ScheduleDay) {
    val formats = scope.formats
    val isToday = day.date == scope.today
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = Theme.spacing.xxs),
        horizontalArrangement = Arrangement.spacedBy(Theme.spacing.sm),
    ) {
        // The day: its weekday over its number, ringed for today. Pressed, it
        // opens the day, as a month's number does.
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .width(ScheduleGutter)
                .pressable(
                    onClick = { scope.openDay(day.date) },
                    enabled = scope.enabled,
                    shape = Theme.shapes.control,
                    onClickLabel = formats.date(day.date, io.kontour.ui.locale.DateStyle.Full),
                )
                .padding(vertical = Theme.spacing.xxs),
        ) {
            Text(
                formats.weekdayName(day.date.dayOfWeek, NameWidth.Abbreviated),
                style = Theme.typography.labelSmall,
                colour = quietInk(),
                modifier = Modifier.clearAndSetSemantics { },
            )
            Box(Modifier.size(ScheduleDayCircle).todayRing(isToday), contentAlignment = Alignment.Center) {
                Text(
                    formats.digits(day.date.day.toLong()),
                    style = Theme.typography.titleMedium,
                    modifier = Modifier.clearAndSetSemantics { },
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f).padding(top = Theme.spacing.xxs),
            verticalArrangement = Arrangement.spacedBy(Theme.spacing.xxs),
        ) {
            if (day.events.isEmpty()) {
                Text(
                    scope.strings.nothingPlanned,
                    style = Theme.typography.bodyMedium,
                    colour = quietInk(),
                    modifier = Modifier.padding(vertical = Theme.spacing.xs, horizontal = Theme.spacing.xs),
                )
            }
            for (event in day.events) ScheduleEventRow(scope, event, day.date)
        }
    }
}

/**
 * An event in the schedule, on [day]: its tone's dot, or its icon, its title,
 * and when — "All day" or its times, see [scheduleTime] — and where, on the
 * line under. Pressed, it is the calendar's `onEventClick`; held, or clicked
 * with a mouse's other button, its menu; Delete, deleted where it may be.
 */
@Composable
private fun ScheduleEventRow(scope: CalendarScope, event: CalendarEvent, day: LocalDate) {
    val colours = toneColours(event.tone)
    val formats = scope.formats
    val strings = scope.strings
    val shape = Theme.shapes.control
    val placed = remember { PlacedAt() }
    val described = eventDescription(event, formats, strings)
    val openMenu = {
        placed.coordinates?.takeIf { it.isAttached }?.let { at ->
            val centre = at.localToRoot(androidx.compose.ui.geometry.Offset(at.size.width / 2f, at.size.height / 2f))
            scope.openMenu(centre, event, edges = false)
        }
        Unit
    }
    val click = scope.onEventClick
    val menus = scope.hasMenu(event, edges = false)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { placed.coordinates = it }
            .background(colours.container, shape)
            .border(BorderStroke(Theme.sizing.borderWidth, colours.border), shape)
            .pressable(
                onClick = { click?.invoke(event) },
                enabled = scope.enabled && (click != null || menus),
                shape = shape,
                onLongClick = if (menus) openMenu else null,
                minimumTouchTarget = false,
            )
            .then(if (menus) Modifier.secondaryClick { openMenu() } else Modifier)
            .onKeyEvent { key ->
                if (key.type != KeyEventType.KeyDown || (key.key != Key.Delete && key.key != Key.Backspace)) return@onKeyEvent false
                if (!scope.deletable(event)) return@onKeyEvent false
                scope.onEventDelete?.invoke(event)
                true
            }
            .semantics { contentDescription = described }
            .padding(horizontal = Theme.spacing.sm, vertical = Theme.spacing.xs),
    ) {
        val style = Theme.typography.bodyMedium
        val icon = event.icon
        if (icon != null) {
            ItemIcon(icon, style, tint = colours.solid)
        } else {
            Box(Modifier.size(Theme.spacing.sm).background(colours.solid, Theme.shapes.capsule))
        }
        Column(Modifier.weight(1f).clearAndSetSemantics { }) {
            Text(event.title, style = style, colour = colours.onContainer, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val time = scheduleTime(event, day, formats, strings)
            val line = formats.list(listOfNotNull(time.text, event.location), io.kontour.ui.locale.ListStyle.Unit)
            Text(time.placedIn(line), style = Theme.typography.labelSmall, colour = colours.onContainer, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * When [event] is on [day], for the line under its title in a schedule.
 *
 * An all-day event is "All day" on each day it is on, and one inside a day
 * its times. One that runs into a later day — a conference from nine on
 * Monday to five on Wednesday, a flight from half ten at night — reads as
 * flights and shifts write it: its own times on the day it starts, the end
 * raised by the days on, "09:00 – 17:00⁺²"; "All day" on the days between;
 * and "Until 17:00" on the last, unless it ends at the midnight that closes
 * it and so has all of that day too. Its first day read "09:00 – 00:00",
 * the part of it on the day, which was asked to be *"the fairly standard
 * superscript '+1' notation"* instead.
 */
internal fun scheduleTime(event: CalendarEvent, day: LocalDate, formats: LocaleFormats, strings: Strings): AnnotatedString {
    if (event.allDay) return AnnotatedString(strings.allDay)
    val lastDay = if (event.end > event.start) event.end.plusMinutes(-1).date else event.start.date
    return when {
        day <= event.start.date -> eventTimes(event.start, event.end, formats, strings)
        day >= lastDay && event.end != day.plus(1, DateTimeUnit.DAY).atMidnight -> AnnotatedString(strings.untilTime(formats.time(event.end.time)))
        else -> AnnotatedString(strings.allDay)
    }
}

/** How many months back from the one in hand a schedule lists. */
private const val ScheduleMonthsBack = 6

/** How many months on from the one in hand a schedule lists. */
private const val ScheduleMonthsOn = 12

/** Further than this many rows, a scroll to a day is a cut rather than a slide. */
private const val FarScheduleRows = 30

/** The day's column at the start of each row. */
private val ScheduleGutter = 48.dp

/** Today's ring round a day's number. */
private val ScheduleDayCircle = 32.dp
