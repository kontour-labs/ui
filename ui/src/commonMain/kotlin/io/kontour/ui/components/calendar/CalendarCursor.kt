package io.kontour.ui.components.calendar

import androidx.compose.foundation.focusable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.kontour.ui.locale.DateStyle
import io.kontour.ui.locale.ListStyle
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.plus

/*
 * The keyboard's way round the calendar: one stop for the whole grid, and a
 * cursor in it the arrows move — a day in a month, a slot of time in a day or
 * a week. The cursor's day is the calendar's own date, so moving off the end
 * of the period pages, as the header's arrows do, and the cursor is where the
 * header says the calendar is. Tab from the grid goes on to the events of the
 * cursor's day, and only those: a month is otherwise forty-odd stops of dates
 * and every event in it before the next thing on the page.
 *
 * The one stop sits outside the pages, so paging does not take its focus away
 * with the page it was on.
 */

/**
 * The grid as one keyboard stop: described as its cursor, its keys handled
 * while it — not an event inside it — has focus.
 *
 * @param collection Rows and columns, for a grid that does not already say so.
 * @param onFocused Called as the grid takes focus: where a cursor without a
 *   place of its own yet takes one.
 */
@Composable
internal fun Modifier.calendarCursor(
    scope: CalendarScope,
    description: String,
    collection: CollectionInfo? = null,
    onFocused: () -> Unit = {},
    onKey: (Key) -> Boolean,
): Modifier {
    // The grid's own focus, not an event's in it: an event's arrows it does not
    // use come up through here, and are not the cursor's.
    var focused by remember { mutableStateOf(false) }
    return this
        .semantics {
            contentDescription = description
            if (collection != null) collectionInfo = collection
        }
        .onFocusChanged {
            focused = it.isFocused
            if (it.isFocused) onFocused()
        }
        .onKeyEvent { event ->
            if (!focused || !scope.enabled || event.type != KeyEventType.KeyDown || event.hasCommandModifier()) return@onKeyEvent false
            onKey(event.key)
        }
        .focusable(enabled = scope.enabled, interactionSource = scope.cursorInteractions)
}

/** A key held with Ctrl, Alt or Meta is a shortcut of something else's. */
private fun KeyEvent.hasCommandModifier(): Boolean = isCtrlPressed || isAltPressed || isMetaPressed

/** Whether [key] activates: Enter, or Space. */
internal fun activates(key: Key): Boolean = key == Key.Enter || key == Key.NumPadEnter || key == Key.Spacebar

/**
 * The month's keys: a day either side, a week up or down, the week's first
 * and last day, a month back or on — and the day itself, opened.
 */
internal fun CalendarScope.monthKey(key: Key, rtl: Boolean): Boolean {
    val date = state.date
    val next = when (key) {
        // The day before is on the left, unless the reading runs the other way.
        Key.DirectionLeft -> date.plus(if (rtl) 1 else -1, DateTimeUnit.DAY)
        Key.DirectionRight -> date.plus(if (rtl) -1 else 1, DateTimeUnit.DAY)
        Key.DirectionUp -> date.plus(-DaysPerWeek, DateTimeUnit.DAY)
        Key.DirectionDown -> date.plus(DaysPerWeek, DateTimeUnit.DAY)
        Key.MoveHome -> state.periods.weekStart(date)
        Key.MoveEnd -> state.periods.weekStart(date).plus(DaysPerWeek - 1, DateTimeUnit.DAY)
        Key.PageUp -> {
            state.step(-1)
            null
        }
        Key.PageDown -> {
            state.step(1)
            null
        }
        else -> {
            if (!activates(key)) return false
            val click = onDateClick
            if (click != null) click(date) else state.show(date, CalendarView.Day)
            return true
        }
    }
    if (next != null) state.show(next)
    announcer.say(dayDescription(state.date))
    return true
}

/**
 * The hours' keys: a step of the snap up or down, an hour by the page, the
 * day's first and last slot, a day either side — and the slot itself, a time
 * pressed, or a new event an hour long where there is no time to press.
 */
internal fun CalendarScope.timeKey(key: Key, rtl: Boolean, defaultMinute: () -> Int): Boolean {
    val step = edit.stepMinutes
    val last = MinutesPerDay - step
    val minute = cursorMinute(defaultMinute)
    val date = state.date
    val page = maxOf(step, MinutesPerHour)
    when (key) {
        Key.DirectionUp -> state.cursorMinute = (minute - step).coerceIn(0, last)
        Key.DirectionDown -> state.cursorMinute = (minute + step).coerceIn(0, last)
        Key.PageUp -> state.cursorMinute = (minute - page).coerceIn(0, last)
        Key.PageDown -> state.cursorMinute = (minute + page).coerceIn(0, last)
        Key.MoveHome -> state.cursorMinute = 0
        Key.MoveEnd -> state.cursorMinute = last
        Key.DirectionLeft -> state.show(date.plus(if (rtl) 1 else -1, DateTimeUnit.DAY))
        Key.DirectionRight -> state.show(date.plus(if (rtl) -1 else 1, DateTimeUnit.DAY))
        else -> {
            if (!activates(key)) return false
            val start = date.atMidnight.plusMinutes(minute.toLong())
            val click = onTimeClick
            val create = onCreateRequest
            when {
                click != null -> click(start)
                create != null -> {
                    val end = date.atMidnight.plusMinutes(minOf(minute + NewEventMinutes, MinutesPerDay).toLong())
                    create(start, end)
                    announce(strings.newEvent, start, end)
                }
                else -> return false
            }
            return true
        }
    }
    announcer.say(slotDescription(state.date, cursorMinute(defaultMinute)))
    return true
}

/** The cursor's minute of the day: its own, on the snap, or — before it has one — [default]'s. */
internal fun CalendarScope.cursorMinute(default: () -> Int): Int {
    val step = edit.stepMinutes
    val minute = state.cursorMinute ?: default()
    return floorTo(minute.toLong(), step).toInt().coerceIn(0, MinutesPerDay - step)
}

/** A day, as the cursor reads it out: its date and how many events it has. */
internal fun CalendarScope.dayDescription(date: LocalDate): String {
    val count = eventsBetween(date, date).size
    return formats.list(
        listOfNotNull(formats.date(date, DateStyle.Full), if (count > 0) strings.eventCount(formats.quantity(count)) else null),
        ListStyle.Unit,
    )
}

/** A slot of time, as the cursor reads it out: its day and its time. */
internal fun CalendarScope.slotDescription(date: LocalDate, minute: Int?): String {
    val day = formats.date(date, DateStyle.Full)
    if (minute == null) return day
    return formats.list(listOf(day, formats.time(LocalTime(minute / MinutesPerHour, minute % MinutesPerHour))), ListStyle.Unit)
}

/** The cursor's ring, a stroke inside [rect] so a neighbour does not paint over it. */
internal fun DrawScope.drawCursorRing(rect: Rect, colour: Color, width: Float) {
    if (rect.width <= width || rect.height <= width) return
    val half = width / 2f
    drawRect(
        colour,
        topLeft = Offset(rect.left + half, rect.top + half),
        size = Size(rect.width - width, rect.height - width),
        style = Stroke(width),
    )
}

private const val MinutesPerHour = 60

/** How long a new event made from the keyboard is: an hour, or to the end of the day. */
internal const val NewEventMinutes = 60
