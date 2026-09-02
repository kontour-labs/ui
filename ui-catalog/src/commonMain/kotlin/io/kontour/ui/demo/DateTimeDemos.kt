package io.kontour.ui.demo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.ChevronLeft
import com.composables.icons.tabler.outline.ChevronRight
import io.kontour.ui.components.datetime.CalendarMonth
import io.kontour.ui.components.datetime.DatePicker
import io.kontour.ui.components.datetime.DateRangePicker
import io.kontour.ui.components.datetime.DateTimeFormats
import io.kontour.ui.components.datetime.LocalDateTimeFormats
import io.kontour.ui.components.datetime.RelativeTimeText
import io.kontour.ui.components.datetime.TimeField
import io.kontour.ui.components.datetime.TimePicker
import io.kontour.ui.components.datetime.WheelPicker
import io.kontour.ui.foundation.Text
import io.kontour.ui.theme.Theme
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.time.Duration.Companion.minutes

/**
 * A fixed "today", not the real one.
 *
 * These demos are drawn by the render sweep as well as read in a browser, and a
 * calendar that moves with the wall clock produces a different image every day.
 */
private val Today = LocalDate(2026, 6, 12)

internal val DatePickerDemo = ComponentDemo(slug = "date-picker") {
    var depart by remember { mutableStateOf(LocalDate(2026, 6, 18)) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Theme.spacing.sm)) {
        DatePicker(
            selected = depart,
            onSelectedChange = { depart = it },
            today = Today,
            // Past dates are unselectable, which is what a departure date is —
            // and it is the parameter people reach for first.
            isDateSelectable = { it >= Today },
            previousIcon = Tabler.Outline.ChevronLeft,
            nextIcon = Tabler.Outline.ChevronRight,
        )
        Text("$depart", style = Theme.typography.labelSmall, colour = Theme.colours.contentMuted)
    }
}

internal val DateRangePickerDemo = ComponentDemo(slug = "date-range-picker") {
    var range by remember {
        mutableStateOf<Pair<LocalDate, LocalDate?>>(
            LocalDate(2026, 6, 9) to LocalDate(2026, 6, 21),
        )
    }
    DateRangePicker(
        start = range.first,
        end = range.second,
        onRangeSelected = { start, end -> range = start to end },
        today = Today,
        previousIcon = Tabler.Outline.ChevronLeft,
        nextIcon = Tabler.Outline.ChevronRight,
    )
}

internal val CalendarMonthDemo = ComponentDemo(slug = "calendar-month") {
    var picked by remember { mutableStateOf(LocalDate(2026, 6, 18)) }
    CalendarMonth(
        month = Today,
        // A predicate rather than a value, because the same calendar draws a
        // single date and a range and only the caller knows which.
        isSelected = { it == picked },
        onSelectedChange = { picked = it },
    )
}

private val twelveHour = Knob.Flag("12-hour")

internal val TimePickerDemo = ComponentDemo(
    slug = "time-picker",
    knobs = listOf(twelveHour),
) {
    var at by remember { mutableStateOf(LocalTime(8, 15)) }
    // The 12/24-hour choice is a *format* rather than a parameter, provided the
    // way an app would provide it — one local, once, at the root.
    CompositionLocalProvider(
        LocalDateTimeFormats provides DateTimeFormats(is24Hour = !this[twelveHour]),
    ) {
        TimePicker(value = at, onValueChange = { at = it }, minuteStep = 5)
    }
}

internal val TimeFieldDemo = ComponentDemo(slug = "time-field") {
    var leaveAt by remember { mutableStateOf(LocalTime(8, 15)) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Theme.spacing.sm)) {
        TimeField(
            value = leaveAt,
            onClick = { echo("Would open a TimePicker in a sheet") },
            label = "Leave at",
            modifier = Modifier.fillMaxWidth(),
        )
        TimePicker(value = leaveAt, onValueChange = { leaveAt = it }, minuteStep = 5)
    }
}

internal val WheelPickerDemo = ComponentDemo(slug = "wheel-picker") {
    var index by remember { mutableStateOf(2) }
    val values = remember { (0..23).map { it.toString().padStart(2, '0') } }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Theme.spacing.sm)) {
        WheelPicker(
            items = values,
            selected = index,
            onSelectedChange = { index = it },
            label = { it },
        )
        Text(
            "Hour ${values[index]}",
            style = Theme.typography.labelSmall,
            colour = Theme.colours.contentMuted,
        )
    }
}

internal val RelativeTimeTextDemo = ComponentDemo(slug = "relative-time-text") {
    // A duration until, not an instant: the component ticks it down itself, so
    // what a caller hands over is "how long from when this composed".
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Theme.spacing.xs)) {
        RelativeTimeText(until = 4.minutes)
        RelativeTimeText(until = 14.minutes)
    }
}

internal val dateTimeDemos = listOf(
    DatePickerDemo,
    DateRangePickerDemo,
    CalendarMonthDemo,
    TimePickerDemo,
    TimeFieldDemo,
    WheelPickerDemo,
    RelativeTimeTextDemo,
)
