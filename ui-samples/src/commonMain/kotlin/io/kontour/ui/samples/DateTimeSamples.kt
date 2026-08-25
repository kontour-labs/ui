package io.kontour.ui.samples

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.kontour.ui.components.datetime.CalendarMonth
import io.kontour.ui.components.datetime.DatePicker
import io.kontour.ui.components.datetime.DateRangePicker
import io.kontour.ui.components.datetime.DateTimeFormats
import io.kontour.ui.components.datetime.LocalDateTimeFormats
import io.kontour.ui.components.datetime.RelativeTimeText
import io.kontour.ui.components.datetime.TimeField
import io.kontour.ui.components.datetime.TimePicker
import io.kontour.ui.components.datetime.WheelPicker
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.time.Duration.Companion.minutes

@Composable
fun DatePickerBasics() {
    var travelDate by remember { mutableStateOf<LocalDate?>(null) }

    DatePicker(
        selected = travelDate,
        onSelectedChange = { travelDate = it },
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
        onRangeSelected = { from, to -> start = from; end = to },
        today = LocalDate(2026, 6, 12),
    )
}

@Composable
fun TimePickerBasics() {
    var departAt by remember { mutableStateOf(LocalTime(8, 15)) }

    // Five-minute steps, because a timetable has no use for 08:17.
    TimePicker(value = departAt, onValueChange = { departAt = it }, minuteStep = 5)
}

@Composable
fun TimeFieldBasics() {
    var departAt by remember { mutableStateOf(LocalTime(8, 15)) }

    // A read-only field that opens a picker — `onClick`, not `onValueChange`.
    // Typing a time into a text field is how you get 25:61.
    TimeField(value = departAt, onClick = { start() }, label = "Leave at")
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
        selected = index,
        onSelectedChange = { index = it },
        label = { it },
    )
}

@Composable
fun RelativeTimeTextBasics() {
    // A duration, not an instant: the caller owns the clock, so this is
    // testable without freezing time and does not need a time source of its own.
    RelativeTimeText(until = 4.minutes)
}

@Composable
fun DateTimeFormatsBasics() {
    // Provided once, near the root. Every date and time component below reads
    // it, so 12-hour clocks and a Sunday week start are one decision rather
    // than a parameter on nine call sites.
    CompositionLocalProvider(
        LocalDateTimeFormats provides DateTimeFormats(
            is24Hour = false,
            dayFirst = false,
            firstDayOfWeek = DayOfWeek.SUNDAY,
        ),
    ) {
        Screen()
    }
}
