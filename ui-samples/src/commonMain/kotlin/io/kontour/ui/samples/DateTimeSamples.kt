package io.kontour.ui.samples

import androidx.compose.foundation.layout.fillMaxSize
import io.kontour.ui.theme.Tone
import io.kontour.ui.components.calendar.rememberEventCalendarState
import io.kontour.ui.components.calendar.EventCalendar
import io.kontour.ui.components.calendar.CalendarEvent
import io.kontour.ui.theme.ProvideTokens
import io.kontour.ui.locale.HourCycle
import io.kontour.ui.locale.DateOrder
import io.kontour.icons.tabler.Tabler
import io.kontour.icons.tabler.outline.Plane
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.kontour.ui.components.datetime.DatePickerDefaults
import io.kontour.ui.sheet.ModalBottomSheet
import io.kontour.ui.sheet.SheetHeader
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.kontour.ui.components.datetime.ActivityCalendar
import io.kontour.ui.components.datetime.ActivityMark
import io.kontour.ui.components.datetime.CalendarMonth
import io.kontour.ui.components.datetime.DateField
import io.kontour.ui.components.datetime.DatePicker
import io.kontour.ui.components.datetime.DayAnnotation
import androidx.compose.ui.graphics.Color
import io.kontour.ui.components.datetime.DateRangePicker
import io.kontour.ui.components.datetime.DateTimePicker
import io.kontour.ui.components.datetime.RelativeTimeText
import io.kontour.ui.components.datetime.TimeField
import io.kontour.ui.components.datetime.TimePicker
import io.kontour.ui.components.datetime.WheelPicker
import io.kontour.ui.theme.Theme
import kotlinx.datetime.DayOfWeek
import io.kontour.ui.overlay.Dialog
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.time.Duration.Companion.minutes

@Composable
fun DatePickerBasics() {
    var travelDate by remember { mutableStateOf<LocalDate?>(null) }

    DatePicker(
        value = travelDate,
        onValueChange = { travelDate = it },
        today = LocalDate(2026, 6, 12),
        // Timetables do not go back, so neither does the picker.
        isDateSelectable = { it >= LocalDate(2026, 6, 12) },
    )
}

@Composable
fun DateRangePickerBasics() {
    var start by remember { mutableStateOf<LocalDate?>(null) }
    var end by remember { mutableStateOf<LocalDate?>(null) }

    // `end` arrives null on the first tap and filled on the second, so the
    // caller can show a half-picked range rather than waiting for both.
    DateRangePicker(
        start = start,
        end = end,
        onRangeChange = { from, to -> start = from; end = to },
        today = LocalDate(2026, 6, 12),
    )
}

@Composable
fun DatePickerAnnotated() {
    var travelDate by remember { mutableStateOf<LocalDate?>(null) }
    val fares = remember { mapOf(LocalDate(2026, 6, 15) to 42.0, LocalDate(2026, 6, 16) to 38.0) }
    val holidays = remember { setOf(LocalDate(2026, 6, 8)) }
    val formats = Theme.formats
    val cheap = Theme.colours.success.solid
    val holiday = Theme.colours.warning.container

    DatePicker(
        value = travelDate,
        onValueChange = { travelDate = it },
        today = LocalDate(2026, 6, 12),
        // A public holiday in a colour of its own; the chosen day is drawn
        // over it.
        containerColourFor = { if (it in holidays) holiday else null },
        // The fare under each day that has one, the cheapest in green, and
        // said in words after the date.
        annotationFor = { day ->
            fares[day]?.let { fare ->
                DayAnnotation(
                    text = formats.currency(fare, "AUD"),
                    colour = if (fare < 40.0) cheap else Color.Unspecified,
                )
            }
        },
    )
}

@Composable
fun DateTimePickerBasics() {
    var leaveAt by remember { mutableStateOf(LocalDateTime(LocalDate(2026, 6, 12), LocalTime(8, 15))) }

    // One value, a day and a time: a day tapped keeps the time, and a wheel
    // turned keeps the day.
    DateTimePicker(
        value = leaveAt,
        onValueChange = { leaveAt = it },
        today = LocalDate(2026, 6, 12),
        isDateSelectable = { it >= LocalDate(2026, 6, 12) },
        minuteStep = 5,
    )
}

@Composable
fun TimePickerBasics() {
    var departAt by remember { mutableStateOf(LocalTime(8, 15)) }

    // Five-minute steps, because a timetable has no use for 08:17.
    TimePicker(value = departAt, onValueChange = { departAt = it }, minuteStep = 5)
}

@Composable
fun DateFieldBasics() {
    var date by remember { mutableStateOf<LocalDate?>(null) }
    var picking by remember { mutableStateOf(false) }

    // One tap target that opens the picker, as `TimeField` is for a time —
    // not a text field with a calendar button beside it.
    DateField(value = date, onClick = { picking = true }, label = "Post date")

    Dialog(
        visible = picking,
        onDismissRequest = { picking = false },
        title = { +"Post date" },
        actions = { cancel() },
    ) {
        DatePicker(
            value = date,
            onValueChange = { date = it; picking = false },
            // Marks today, and gives the header its way back to it.
            today = remember { DatePickerDefaults.currentDate() },
            // The picker stops at a phone's width; this centres it in a wider dialog.
            modifier = Modifier.fillMaxWidth().wrapContentWidth(),
        )
    }
}

@Composable
fun TimeFieldBasics() {
    var departAt by remember { mutableStateOf(LocalTime(8, 15)) }

    var picking by remember { mutableStateOf(false) }

    // A read-only field that opens a picker — `onClick`, not `onValueChange`.
    // Typing a time into a text field is how you get 25:61.
    TimeField(value = departAt, onClick = { picking = true }, label = "Leave at")

    // The wheels set the time as they turn, so the sheet needs no Done: its
    // close button, a drag down or a tap outside puts it away.
    ModalBottomSheet(visible = picking, onDismissRequest = { picking = false }) { padding ->
        SheetHeader { +"Leave at" }
        TimePicker(
            value = departAt,
            onValueChange = { departAt = it },
            minuteStep = 5,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(padding),
        )
    }
}

@Composable
fun ActivityCalendarBasics() {
    var picked by remember { mutableStateOf<LocalDate?>(null) }

    // A year to today, a shade a day. Point at a day, long-press it or reach it
    // with the arrow keys to see its count; tap it to pick it.
    ActivityCalendar(
        activity = tripsByDay,
        end = LocalDate(2026, 6, 5),
        today = LocalDate(2026, 6, 5),
        selected = picked,
        onDayClick = { picked = it },
    )
}

@Composable
fun ActivityCalendarMarks() {
    val holiday = Theme.colours.warning.solid
    // A mark draws on a day as well as its shade — a folded corner, an icon, a
    // count spelled out — and says in words what it means, for the tooltip and
    // the screen reader, which cannot see a corner. A day can have several.
    ActivityCalendar(
        activity = tripsByDay,
        end = LocalDate(2026, 6, 5),
        marksFor = { date, count ->
            listOfNotNull(
                ActivityMark(corner = holiday, description = "Public holiday").takeIf { date in publicHolidays },
                flights[date]?.let { ActivityMark(icon = Tabler.Outline.Plane, description = it) },
                ActivityMark(text = "$count").takeIf { count >= 10 },
            )
        },
    )
}

@Composable
fun CalendarMonthBasics() {
    var selected by remember { mutableStateOf(LocalDate(2026, 6, 18)) }

    // The grid on its own, with no header and no paging — for a screen that
    // shows three months at once, or supplies its own navigation.
    CalendarMonth(
        month = LocalDate(2026, 6, 1),
        isSelected = { it == selected },
        onSelectedChange = { selected = it },
        today = LocalDate(2026, 6, 12),
    )
}

@Composable
fun WheelPickerBasics() {
    val platforms = remember { listOf("Platform 1", "Platform 2", "Platform 3") }
    var index by remember { mutableStateOf(1) }

    WheelPicker(
        items = platforms,
        selectedIndex = index,
        onSelectedIndexChange = { index = it },
        itemLabel = { it },
    )
}

@Composable
fun RelativeTimeTextBasics() {
    // A duration, not an instant: the caller owns the clock, so this is
    // testable without freezing time and does not need a time source of its own.
    RelativeTimeText(until = 4.minutes)
}

@Composable
fun LocaleFormatsBasics() {
    // Changed once, near the root. Every date and time component below reads
    // `Theme.formats`, so a 12-hour clock and a Sunday week start are one
    // decision rather than a parameter on nine call sites.
    val formats = Theme.formats
    ProvideTokens(
        formats = formats.withConventions(
            formats.conventions.copy(
                hourCycle = HourCycle.H12,
                dateOrder = DateOrder.MonthDayYear,
                firstDayOfWeek = DayOfWeek.SUNDAY,
            ),
        ),
    ) {
        Screen()
    }
}

@Composable
fun EventCalendarBasics() {
    // The app keeps the events; the calendar shows them and says what was pressed.
    val calendar = rememberEventCalendarState()
    val events = listOf(
        CalendarEvent(id = 1, title = "Stand-up", start = LocalDateTime(2026, 10, 14, 9, 0), end = LocalDateTime(2026, 10, 14, 9, 15)),
        CalendarEvent(id = 2, title = "Perth trip", startDate = LocalDate(2026, 10, 13), endDate = LocalDate(2026, 10, 16), tone = Tone.Warning),
    )
    EventCalendar(
        state = calendar,
        events = events,
        onEventClick = { open(it.title) },
        modifier = Modifier.fillMaxSize(),
    )
}

