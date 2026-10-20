package io.kontour.ui.catalog.apps.energy

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.kontour.icons.tabler.Tabler
import io.kontour.icons.tabler.outline.Car
import io.kontour.icons.tabler.outline.Plus
import io.kontour.icons.tabler.outline.WashMachine
import io.kontour.ui.catalog.apps.rememberAppToast
import io.kontour.ui.catalog.apps.transit.rootNavigation
import io.kontour.ui.catalog.pageScroll
import io.kontour.ui.components.action.Button
import io.kontour.ui.components.action.IconButton
import io.kontour.ui.components.calendar.CalendarEvent
import io.kontour.ui.components.calendar.CalendarView
import io.kontour.ui.components.calendar.EventCalendar
import io.kontour.ui.components.calendar.rememberEventCalendarState
import io.kontour.ui.components.list.ListGroup
import io.kontour.ui.components.list.TextFieldListItem
import io.kontour.ui.foundation.FillPattern
import io.kontour.ui.foundation.Text
import io.kontour.ui.foundation.fillPattern
import io.kontour.ui.nav.TopBar
import io.kontour.ui.overlay.tooltip
import io.kontour.ui.sheet.ModalBottomSheet
import io.kontour.ui.sheet.SheetHeader
import io.kontour.ui.theme.StatusColours
import io.kontour.ui.theme.Theme
import io.kontour.ui.theme.Tone
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.plus
import kotlin.math.roundToInt

/**
 * The week ahead: when power is dear and when it is free, as patterned blocks
 * a reader can tell apart without their colours, and the runs the house has
 * planned into the cheap hours. A run is moved or stretched by hand, and a new
 * one is typed into a sheet of `TextFieldListItem` rows.
 */
@Composable
internal fun PlanPage() {
    val toast = rememberAppToast()
    var scheduling by remember { mutableStateOf(false) }
    val runs = remember { mutableStateListOf(*plannedRuns().toTypedArray()) }
    var next by remember { mutableIntStateOf(100) }
    val calendar = rememberEventCalendarState(
        initialDate = EnergyToday,
        initialView = CalendarView.Week,
        initialScrollTime = LocalTime(9, 0),
    )
    val windows = remember { tariffWindows() }
    val pad = energyPagePadding()

    Column(Modifier.fillMaxSize()) {
        TopBar(
            navigation = rootNavigation(),
            actions = {
                IconButton(
                    icon = Tabler.Outline.Plus,
                    contentDescription = "Schedule a run",
                    onClick = { scheduling = true },
                    modifier = Modifier.tooltip("Schedule a run").testTag("energy-schedule"),
                )
            },
        ) { +"Plan" }
        Legend(Modifier.padding(horizontal = Theme.spacing.md))
        EventCalendar(
            state = calendar,
            events = windows + runs,
            now = LocalDateTime(EnergyToday, LocalTime(NowHour, 20)),
            onEventClick = { toast(it.title) },
            onEventMove = { event, start, end -> runs.replaceWith(event, start, end) },
            onEventResize = { event, start, end -> runs.replaceWith(event, start, end) },
            modifier = Modifier.weight(1f).fillMaxWidth().padding(pad).testTag("energy-calendar"),
        )
    }

    ScheduleSheet(
        visible = scheduling,
        onDismiss = { scheduling = false },
        onSchedule = { title, start, hours ->
            val begins = LocalDateTime(EnergyToday.plus(DatePeriod(days = 1)), start)
            val minutes = (hours * 60).roundToInt().coerceIn(15, 12 * 60)
            val ends = LocalTime.fromSecondOfDay((start.toSecondOfDay() + minutes * 60).coerceAtMost(LastSecond))
            runs += CalendarEvent(id = next++, title = title, start = begins, end = LocalDateTime(begins.date, ends))
            scheduling = false
            toast("$title scheduled for tomorrow")
        },
    )
}

/** [event] moved to [start] and [end], where it was in the list. */
private fun MutableList<CalendarEvent>.replaceWith(event: CalendarEvent, start: LocalDateTime, end: LocalDateTime) {
    val at = indexOfFirst { it.id == event.id }
    if (at < 0) return
    this[at] = CalendarEvent(
        id = event.id,
        title = event.title,
        start = start,
        end = end,
        tone = event.tone,
        icon = event.icon,
        pattern = event.pattern,
    )
}

/** The tariff's windows for the week: dear, and free from the roof. Not the reader's to move. */
private fun tariffWindows(): List<CalendarEvent> = (0 until 7).flatMap { d ->
    val day = EnergyToday.plus(DatePeriod(days = d - 1))
    listOf(
        CalendarEvent(
            id = "peak-$d",
            title = "Peak",
            start = LocalDateTime(day, LocalTime(16, 0)),
            end = LocalDateTime(day, LocalTime(21, 0)),
            tone = Tone.Warning,
            supporting = "Power is dearest",
            editable = false,
            pattern = PeakPattern,
        ),
        CalendarEvent(
            id = "sun-$d",
            title = "Free",
            start = LocalDateTime(day, LocalTime(10, 0)),
            end = LocalDateTime(day, LocalTime(14, 0)),
            tone = Tone.Success,
            supporting = "The roof makes more than the house uses",
            editable = false,
            pattern = FreePattern,
        ),
    )
}

private fun plannedRuns(): List<CalendarEvent> = listOf(
    CalendarEvent(
        id = 1,
        title = "Car",
        supporting = "Charging from the roof",
        start = LocalDateTime(EnergyToday, LocalTime(10, 30)),
        end = LocalDateTime(EnergyToday, LocalTime(13, 0)),
        icon = Tabler.Outline.Car,
    ),
    CalendarEvent(
        id = 2,
        title = "Washing",
        start = LocalDateTime(EnergyToday.plus(DatePeriod(days = 4)), LocalTime(11, 0)),
        end = LocalDateTime(EnergyToday.plus(DatePeriod(days = 4)), LocalTime(12, 30)),
        icon = Tabler.Outline.WashMachine,
    ),
    CalendarEvent(
        id = 3,
        title = "Outage",
        start = LocalDateTime(EnergyToday.plus(DatePeriod(days = 2)), LocalTime(8, 0)),
        end = LocalDateTime(EnergyToday.plus(DatePeriod(days = 2)), LocalTime(10, 0)),
        tone = Tone.Danger,
        supporting = "Grid maintenance; the battery covers it",
        editable = false,
        pattern = OutagePattern,
    ),
)

/**
 * What each pattern means, drawn with `Modifier.fillPattern` over a swatch of
 * the same tone, so the legend matches the blocks without their colours.
 */
@Composable
private fun Legend(modifier: Modifier = Modifier) {
    val colours = Theme.colours
    FlowRow(
        modifier = modifier.fillMaxWidth().padding(vertical = Theme.spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Theme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
    ) {
        Swatch("Peak", PeakPattern, colours.warning)
        Swatch("Free from the roof", FreePattern, colours.success)
        Swatch("Outage", OutagePattern, colours.danger)
        Swatch("Planned", FillPattern.Solid, colours.accent)
    }
}

@Composable
private fun Swatch(label: String, pattern: FillPattern, tone: StatusColours) {
    val shape = Theme.shapes.extraSmall
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Theme.spacing.xs)) {
        Box(
            Modifier
                .size(SwatchSize)
                .clip(shape)
                .background(if (pattern == FillPattern.Solid) tone.solid else tone.container)
                .fillPattern(pattern, ink = tone.solid, shape = shape),
        )
        Text(label, style = Theme.typography.labelMedium)
    }
}

/**
 * A run for tomorrow, typed into rows of a group: what, when and for how
 * long. The first row takes the focus as the sheet opens, so the keyboard
 * arrives with the sheet — and the sheet stays where it is, its rows
 * scrolling behind the keyboard rather than the window being pushed up.
 */
@Composable
private fun ScheduleSheet(visible: Boolean, onDismiss: () -> Unit, onSchedule: (String, LocalTime, Double) -> Unit) {
    val formats = Theme.formats
    val name = rememberTextFieldState()
    val start = rememberTextFieldState("11:00")
    val hours = rememberTextFieldState("1.5")
    val first = remember { FocusRequester() }
    val time = parseTime(start.text.toString())
    val length = formats.parseDecimal(hours.text.toString())

    ModalBottomSheet(visible = visible, onDismissRequest = onDismiss) { padding ->
        Column(
            Modifier.pageScroll(barPadding = PaddingValues()).padding(padding).testTag("energy-schedule-sheet"),
            verticalArrangement = Arrangement.spacedBy(Theme.spacing.md),
        ) {
            SheetHeader {
                +"Schedule a run"
                supporting { +"Tomorrow, in the cheapest hours you choose" }
            }
            ListGroup(Modifier.padding(horizontal = Theme.spacing.md)) {
                item { position ->
                    TextFieldListItem(
                        state = name,
                        label = "What",
                        placeholder = "Dishwasher",
                        position = position,
                        modifier = Modifier.focusRequester(first),
                    )
                }
                field(
                    start,
                    label = "Starts",
                    supporting = "On the 24-hour clock",
                    errorMessage = if (time == null) "Write it as 11:30" else null,
                    keyboardType = KeyboardType.Number,
                )
                field(
                    hours,
                    label = "Hours",
                    errorMessage = if (length == null || length <= 0) "How long it runs, like 1.5" else null,
                    keyboardType = KeyboardType.Decimal,
                )
            }
            Button(
                onClick = {
                    if (time != null && length != null && length > 0) {
                        onSchedule(name.text.ifBlank { "Dishwasher" }.toString(), time, length)
                    }
                },
                enabled = time != null && length != null && length > 0,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Theme.spacing.md, vertical = Theme.spacing.sm),
            ) { +"Schedule" }
        }
        LaunchedEffect(Unit) { first.requestFocus() }
    }
}

/** "11:30" as a time, or null when it is not one. */
private fun parseTime(text: String): LocalTime? {
    val parts = text.trim().split(":")
    if (parts.size != 2) return null
    val hour = parts[0].toIntOrNull() ?: return null
    val minute = parts[1].toIntOrNull() ?: return null
    return if (hour in 0..23 && minute in 0..59) LocalTime(hour, minute) else null
}

private val PeakPattern = FillPattern.Diagonal
private val FreePattern = FillPattern.Dots
private val OutagePattern = FillPattern.CrossHatch
private val SwatchSize = 18.dp
private const val LastSecond = 23 * 3_600 + 59 * 60
