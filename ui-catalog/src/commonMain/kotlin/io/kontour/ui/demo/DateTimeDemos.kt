package io.kontour.ui.demo

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.LaunchedEffect
import kotlinx.datetime.plus
import kotlinx.datetime.DateTimeUnit
import io.kontour.ui.theme.Tone
import io.kontour.ui.components.calendar.rememberEventCalendarState
import io.kontour.ui.components.calendar.EventCalendar
import io.kontour.ui.components.calendar.EventCalendarDefaults
import io.kontour.ui.components.calendar.CalendarView
import io.kontour.ui.components.calendar.CalendarDaysStyle
import io.kontour.ui.components.calendar.CalendarEvent
import io.kontour.ui.theme.ProvideTokens
import io.kontour.ui.locale.HourCycle
import io.kontour.ui.locale.DateOrder
import io.kontour.ui.locale.DateStyle
import io.kontour.ui.locale.ListStyle
import io.kontour.ui.locale.PseudoLocale
import io.kontour.ui.locale.PseudoLocalised
import io.kontour.ui.locale.RelativeUnit
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import io.kontour.icons.tabler.Tabler
import io.kontour.icons.tabler.outline.Plane
import io.kontour.icons.tabler.outline.ChevronDown
import io.kontour.ui.components.datetime.ActivityCalendar
import io.kontour.ui.components.datetime.ActivityLegend
import io.kontour.ui.components.datetime.ActivityMark
import io.kontour.ui.components.datetime.ActivityCalendarDefaults
import io.kontour.ui.components.datetime.ActivityLevels
import io.kontour.ui.components.datetime.CalendarMonth
import io.kontour.ui.components.datetime.DateField
import io.kontour.ui.components.datetime.DatePicker
import io.kontour.ui.components.datetime.FieldPicker
import io.kontour.ui.components.datetime.DayAnnotation
import io.kontour.ui.components.datetime.DateTimeField
import io.kontour.ui.components.datetime.DateRangePicker
import io.kontour.ui.components.datetime.DateTimePicker
import io.kontour.ui.components.datetime.RelativeTimeText
import io.kontour.ui.components.datetime.TimeField
import io.kontour.ui.components.datetime.TimePicker
import io.kontour.ui.components.datetime.WheelPicker
import io.kontour.ui.foundation.Text
import io.kontour.ui.theme.Theme
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.minus
import kotlin.time.Duration.Companion.minutes
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color

/**
 * A fixed "today", not the real one.
 *
 * These demos are drawn by the render sweep as well as read in a browser, and a
 * calendar that moves with the wall clock produces a different image every day.
 */
private val Today = LocalDate(2026, 6, 12)

/** Fares under the days and a holiday in its own colour: `annotationFor` and `containerColourFor`. */
private val fares = Knob.Flag("Fares")

/** A fare for most days from [Today] on, a little cheaper midweek; none before. */
private fun demoFare(day: LocalDate): Double? =
    if (day < Today || day.day % 7 == 0) null else 30.0 + (day.day * 7) % 25 - if (day.dayOfWeek.ordinal in 1..3) 6 else 0

@Composable
private fun demoDayLooks(on: Boolean): Pair<((LocalDate) -> Color?)?, ((LocalDate) -> DayAnnotation?)?> {
    if (!on) return null to null
    val formats = Theme.formats
    val cheap = Theme.colours.success.solid
    val holiday = Theme.colours.warning.container
    return { day: LocalDate -> if (day == LocalDate(2026, 6, 8)) holiday else null } to { day: LocalDate ->
        demoFare(day)?.let { fare ->
            DayAnnotation(
                text = formats.currency(fare, "AUD").substringBefore('.'),
                colour = if (fare < 30.0) cheap else Color.Unspecified,
            )
        }
    }
}

internal val DatePickerDemo = ComponentDemo(slug = "date-picker", knobs = listOf(fares)) {
    val (colourFor, annotationFor) = demoDayLooks(this[fares])
    var depart by remember { mutableStateOf(LocalDate(2026, 6, 18)) }
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Theme.spacing.sm),
        // The picker stops at a phone's width; wider, it sits in the middle of
        // the card rather than against its start, and its caption under it.
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DatePicker(
            value = depart,
            onValueChange = { depart = it },
            today = Today,
            // Past dates are unselectable, which is what a departure date is —
            // and it is the parameter people reach for first.
            isDateSelectable = { it >= Today },
            // The header opens two wheels. Without a glyph it is a title that
            // happens to be a button, which on a touch screen has no hover to
            // discover it with.
            chooserIcon = Tabler.Outline.ChevronDown,
            containerColourFor = colourFor,
            annotationFor = annotationFor,
        )
        Text("$depart", style = Theme.typography.labelSmall, colour = Theme.colours.contentMuted)
    }
}

internal val DateRangePickerDemo = ComponentDemo(slug = "date-range-picker", knobs = listOf(fares)) {
    val (colourFor, annotationFor) = demoDayLooks(this[fares])
    var range by remember {
        mutableStateOf<Pair<LocalDate, LocalDate?>>(
            LocalDate(2026, 6, 9) to LocalDate(2026, 6, 21),
        )
    }
    DateRangePicker(
        start = range.first,
        end = range.second,
        onRangeChange = { start, end -> range = start to end },
        today = Today,
        // In the middle of a card wider than the picker's 308dp.
        modifier = Modifier.fillMaxWidth().wrapContentWidth(),
        chooserIcon = Tabler.Outline.ChevronDown,
        containerColourFor = colourFor,
        annotationFor = annotationFor,
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
        modifier = Modifier.fillMaxWidth().wrapContentWidth(),
    )
}

private val twelveHour = Knob.Flag("12-hour")

internal val TimePickerDemo = ComponentDemo(
    slug = "time-picker",
    knobs = listOf(twelveHour),
) {
    var at by remember { mutableStateOf(LocalTime(8, 15)) }
    // The 12/24-hour choice is a *convention* rather than a parameter, provided
    // the way an app would provide it — once, for everything below.
    val formats = Theme.formats
    ProvideTokens(
        formats = formats.withConventions(
            formats.conventions.copy(hourCycle = if (this[twelveHour]) HourCycle.H12 else HourCycle.H23),
        ),
    ) {
        TimePicker(value = at, onValueChange = { at = it }, minuteStep = 5)
    }
}

internal val DateTimePickerDemo = ComponentDemo(slug = "date-time-picker", knobs = listOf(twelveHour)) {
    var leave by remember { mutableStateOf(LocalDateTime(LocalDate(2026, 6, 18), LocalTime(8, 15))) }
    // The clock is a convention, provided as an app would; see the time picker's.
    val formats = Theme.formats
    ProvideTokens(
        formats = formats.withConventions(
            formats.conventions.copy(hourCycle = if (this[twelveHour]) HourCycle.H12 else HourCycle.H23),
        ),
    ) {
        Column(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Theme.spacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            DateTimePicker(
                value = leave,
                onValueChange = { leave = it },
                today = Today,
                isDateSelectable = { it >= Today },
                chooserIcon = Tabler.Outline.ChevronDown,
                minuteStep = 5,
            )
            Text("$leave", style = Theme.typography.labelSmall, colour = Theme.colours.contentMuted)
        }
    }
}

internal val TimeFieldDemo = ComponentDemo(slug = "time-field") {
    var leaveAt by remember { mutableStateOf(LocalTime(8, 15)) }
    var picking by remember { mutableStateOf(false) }
    TimeField(
        value = leaveAt,
        onClick = { picking = true },
        label = "Leave at",
        modifier = Modifier.fillMaxWidth(),
    )
    // A sheet on a phone, a dialog on anything wider: the same as the date
    // field's. The wheels set the time as they turn, so there is no Cancel.
    FieldPicker(visible = picking, onDismissRequest = { picking = false }, title = "Leave at") {
        TimePicker(value = leaveAt, onValueChange = { leaveAt = it }, minuteStep = 5)
    }
}

/**
 * Whether the field starts with no date, so its placeholder shows until a day
 * is picked.
 */
private val dateFieldEmpty = Knob.Flag("Starts empty", initial = false)

/** How the date is written, from "14/03/2026" to the weekday spelled out. */
private val dateFieldStyle = Knob.Choice("Style", DateStyle.entries.toList(), DateStyle.Medium)

internal val DateFieldDemo = ComponentDemo(slug = "date-field", knobs = listOf(dateFieldEmpty, dateFieldStyle)) {
    val empty = this[dateFieldEmpty]
    val style = this[dateFieldStyle]
    var date by remember(empty) { mutableStateOf<LocalDate?>(if (empty) null else LocalDate(2026, 3, 14)) }
    var publishAt by remember { mutableStateOf(LocalDateTime(LocalDate(2026, 3, 14), LocalTime(9, 30))) }
    var pickingDate by remember { mutableStateOf(false) }
    var pickingPublish by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Theme.spacing.sm)) {
        DateField(
            value = date,
            onClick = { pickingDate = true },
            label = "Post date",
            style = style,
            modifier = Modifier.fillMaxWidth(),
        )
        DateTimeField(
            value = publishAt,
            onClick = { pickingPublish = true },
            label = "Publish at",
            modifier = Modifier.fillMaxWidth(),
        )
    }
    // Both open the same way: a sheet on a phone, a dialog on anything wider.
    FieldPicker(visible = pickingDate, onDismissRequest = { pickingDate = false }, title = "Post date") {
        DatePicker(
            value = date,
            // A date is picked at one tap, so the picker goes with it.
            onValueChange = { date = it; pickingDate = false },
            today = Today,
            chooserIcon = Tabler.Outline.ChevronDown,
        )
    }
    FieldPicker(visible = pickingPublish, onDismissRequest = { pickingPublish = false }, title = "Publish at") {
        DateTimePicker(
            value = publishAt,
            onValueChange = { publishAt = it },
            today = Today,
            chooserIcon = Tabler.Outline.ChevronDown,
            minuteStep = 5,
        )
    }
}

/**
 * Whether the list wraps past its ends.
 *
 * Hours do wrap — 23 is next to 00 — and a picker that stops dead at midnight
 * makes the user scroll the whole way back. Off is right for anything with a
 * real first and last, which is why it is not the default.
 */
private val wheelInfinite = Knob.Flag("Wrap around", initial = true)

internal val WheelPickerDemo = ComponentDemo(
    slug = "wheel-picker",
    knobs = listOf(wheelInfinite),
) {
    var index by remember { mutableStateOf(2) }
    val values = remember { (0..23).map { it.toString().padStart(2, '0') } }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Theme.spacing.sm)) {
        WheelPicker(
            items = values,
            selectedIndex = index,
            onSelectedIndexChange = { index = it },
            infinite = this@ComponentDemo[wheelInfinite],
            itemLabel = { it },
        )
        Text(
            "Hour ${values[index]}",
            style = Theme.typography.labelSmall,
            colour = Theme.colours.contentMuted,
        )
    }
}

/**
 * Whether the number rolls over as the countdown ticks.
 *
 * The one place a departure board's animation is worth the frames: the digit
 * that changes is the whole content, and a roll says *this just changed* where
 * a silent swap looks like a re-render.
 */
private val relativeAnimate = Knob.Flag("Animate", initial = true)

internal val RelativeTimeTextDemo = ComponentDemo(
    slug = "relative-time-text",
    knobs = listOf(relativeAnimate),
) {
    // A duration until, not an instant: the component ticks it down itself, so
    // what a caller hands over is "how long from when this composed".
    val animate = this[relativeAnimate]
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Theme.spacing.xs)) {
        RelativeTimeText(until = 4.minutes, animateValue = animate)
        RelativeTimeText(until = 14.minutes, animateValue = animate)
    }
}

/**
 * A language that does not exist, over everything the formats write.
 *
 * Off is the reader's own; the other three are the tests a screen runs before a
 * translator sees it — longer words, right to left with Arabic-Indic digits, and
 * marks stacked above and below every letter.
 */
private val pseudoLocale = Knob.Choice("Pseudo-locale", listOf(null) + PseudoLocale.entries, name = { it?.name ?: "Off" })

private val hourCycle = Knob.Choice("Hour cycle", HourCycle.entries.toList(), HourCycle.H23)

private val dateOrder = Knob.Choice("Date order", DateOrder.entries.toList())

internal val LocaleFormatsDemo = ComponentDemo(
    slug = "locale-formats",
    knobs = listOf(pseudoLocale, hourCycle, dateOrder),
) {
    val mode = this[pseudoLocale]
    val formats = Theme.formats
    ProvideTokens(
        formats = formats.withConventions(
            formats.conventions.copy(hourCycle = this[hourCycle], dateOrder = this[dateOrder]),
        ),
    ) {
        if (mode == null) FormatsSpecimen() else PseudoLocalised(mode) { FormatsSpecimen() }
    }
}

/** One of everything the formats write, and a phrase from the strings around a count. */
@Composable
private fun FormatsSpecimen() {
    val formats = Theme.formats
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Theme.spacing.xs)) {
        Text(formats.date(Today, DateStyle.Full))
        Text(formats.dateTime(Today, LocalTime(14, 5)))
        Text(formats.date(Today, DateStyle.Short))
        Text(formats.integer(12_345))
        Text(formats.relativeTime(-5, RelativeUnit.Minute))
        Text(formats.list(listOf("Perth", "Fremantle", "Joondalup"), ListStyle.And))
        Text(
            Theme.strings.pageOfCount(formats.quantity(2), formats.quantity(12)),
            style = Theme.typography.labelSmall,
            colour = Theme.colours.contentMuted,
        )
    }
}

/**
 * A made-up year of trips, the same on every run: a hash of each day, quieter
 * at weekends, with about a third of days empty.
 */
private fun demoActivity(end: LocalDate): Map<LocalDate, Int> = (0 until 371).associate { back ->
    val day = end.minus(DatePeriod(days = back))
    val hash = ((day.toEpochDays() * 2_654_435_761L) ushr 9) % 13
    val weekend = day.dayOfWeek == DayOfWeek.SATURDAY || day.dayOfWeek == DayOfWeek.SUNDAY
    day to (if (hash < 4) 0 else (hash - 3).toInt()).let { if (weekend) it / 3 else it }
}

private val activityLook = Knob.Choice("Look", listOf("GitHub", "Minimal"))

private val activityLevels = Knob.Choice("Levels", listOf("Quantiles", "Thresholds"))

private val activityMonths = Knob.Flag("Month labels", initial = true)

private val activityWeekdays = Knob.Flag("Weekday labels", initial = true)

/** What the shades mean, under the grid: a square of each, one blended bar, or nothing. */
private val activityLegend = Knob.Choice("Legend", ActivityLegend.entries, ActivityLegend.Steps)

/**
 * Days decorated as well as shaded: holidays folded over, trips with an icon, busy
 * days counted — and with "All", every one that applies, so a holiday spent
 * travelling carries both.
 */
private val activityMarks = Knob.Choice("Marks", listOf("None", "Holidays", "Trips", "Busy days", "All"))

/** Western Australia's public holidays in the demo's year. */
private val holidays = listOf(
    LocalDate(2026, 1, 1), LocalDate(2026, 1, 26), LocalDate(2026, 3, 2), LocalDate(2026, 4, 3),
    LocalDate(2026, 4, 6), LocalDate(2026, 4, 27), LocalDate(2026, 6, 1),
    LocalDate(2025, 9, 29), LocalDate(2025, 12, 25), LocalDate(2025, 12, 26),
)

/** Days away, with a plane on them. */
private val trips = mapOf(
    // A holiday as well: under "All" it carries a folded corner and a plane.
    LocalDate(2026, 1, 26) to "Ferry to Rottnest",
    LocalDate(2026, 2, 13) to "Flight to Sydney",
    LocalDate(2026, 2, 17) to "Flight home",
    LocalDate(2026, 5, 8) to "Flight to Broome",
)

internal val ActivityCalendarDemo = ComponentDemo(
    slug = "activity-calendar",
    knobs = listOf(activityLook, activityLevels, activityMonths, activityWeekdays, activityLegend, activityMarks),
) {
    val end = LocalDate(2026, 6, 5)
    val activity = remember { demoActivity(end) }
    var picked by remember { mutableStateOf<LocalDate?>(null) }
    val formats = Theme.formats
    val marks = this[activityMarks]
    val holidayColour = Theme.colours.warning.solid
    val tripColour = Theme.colours.info.solid
    ActivityCalendar(
        activity = activity,
        end = end,
        modifier = Modifier.fillMaxWidth(),
        today = end,
        selected = picked,
        onDayClick = {
            picked = it
            echo("${activity[it] ?: 0} on ${formats.date(it, DateStyle.Medium)}")
        },
        levels = if (this[activityLevels] == "Thresholds") {
            ActivityLevels.Thresholds(listOf(1, 3, 6, 9))
        } else {
            ActivityLevels.Quantiles
        },
        colours = ActivityCalendarDefaults.colours(
            full = if (this[activityLook] == "GitHub") Theme.colours.success.solid else Theme.colours.info.solid,
        ),
        showMonthLabels = this[activityMonths],
        showWeekdayLabels = this[activityWeekdays],
        legend = this[activityLegend],
        marksFor = if (marks == "None") {
            null
        } else {
            { date, count ->
                val all = marks == "All"
                listOfNotNull(
                    ActivityMark(corner = holidayColour, description = "Public holiday")
                        .takeIf { (all || marks == "Holidays") && date in holidays },
                    trips[date]?.takeIf { all || marks == "Trips" }
                        ?.let { ActivityMark(icon = Tabler.Outline.Plane, fill = tripColour, description = it) },
                    ActivityMark(text = "$count", description = "A busy day")
                        .takeIf { (all || marks == "Busy days") && count >= 8 },
                )
            }
        },
    )
}

/** The view the event calendar opens in; its own switcher changes it too. */
private val eventCalendarView = Knob.Choice("View", EventCalendarDefaults.Views, initial = CalendarView.Month)

/** How many weeks the week view shows side by side. */
private val eventCalendarWeeks = Knob.Choice("Weeks", listOf(1, 2, 3), initial = 1)

/** How the day, three-day and week views lay their days out. */
private val eventCalendarDays = Knob.Choice("Days", CalendarDaysStyle.entries.toList(), initial = CalendarDaysStyle.Hours)

/** A fortnight of a working life, round the demo's today. */
internal fun demoCalendarEvents(today: LocalDate): List<CalendarEvent> {
    fun at(days: Int, hour: Int, minute: Int = 0) = LocalDateTime(today.plus(days, DateTimeUnit.DAY), LocalTime(hour, minute))
    return listOf(
        CalendarEvent(1, "Stand-up", at(0, 9), at(0, 9, 15)),
        CalendarEvent(2, "Design review", at(0, 9), at(0, 10, 30), tone = Tone.Info, location = "Room 4"),
        CalendarEvent(3, "Planning", at(0, 10), at(0, 11)),
        CalendarEvent(4, "Lunch with Sam", at(0, 12), at(0, 13), tone = Tone.Success),
        CalendarEvent(5, "Gym", at(0, 18), at(0, 19), tone = Tone.Neutral),
        CalendarEvent(6, "Perth trip", startDate = today.minus(1, DateTimeUnit.DAY), endDate = today.plus(2, DateTimeUnit.DAY), tone = Tone.Warning),
        CalendarEvent(7, "Dentist", at(1, 15, 30), at(1, 16), tone = Tone.Danger),
        CalendarEvent(8, "Stand-up", at(1, 9), at(1, 9, 15)),
        CalendarEvent(9, "Release", startDate = today.plus(6, DateTimeUnit.DAY), tone = Tone.Accent),
        CalendarEvent(10, "Flight to Sydney", at(7, 22), at(8, 6), tone = Tone.Info),
        CalendarEvent(11, "Retro", at(-2, 14), at(-2, 15)),
        CalendarEvent(12, "Public holiday", startDate = today.plus(12, DateTimeUnit.DAY), tone = Tone.Warning, editable = false),
    )
}

internal val EventCalendarDemo = ComponentDemo(slug = "event-calendar", knobs = listOf(eventCalendarView, eventCalendarWeeks, eventCalendarDays)) {
    val today = LocalDate(2026, 10, 14)
    val view = this[eventCalendarView]
    val weeks = this[eventCalendarWeeks]
    val calendar = rememberEventCalendarState(initialDate = today, initialView = view)
    LaunchedEffect(view) { calendar.show(view) }
    LaunchedEffect(weeks) { calendar.showWeeks(weeks) }
    // The demo keeps its events, as an app does, and takes the calendar's edits.
    var events by remember { mutableStateOf(demoCalendarEvents(today)) }
    var made by remember { mutableIntStateOf(100) }
    val formats = Theme.formats
    fun replace(event: CalendarEvent, start: LocalDateTime, end: LocalDateTime) {
        events = events.map { if (it.id == event.id) it.copy(start = start, end = end) else it }
    }
    EventCalendar(
        state = calendar,
        events = events,
        now = LocalDateTime(today, LocalTime(11, 20)),
        onEventClick = { echo(it.title) },
        // A day pressed in the month, told and opened, as the calendar opens it by itself.
        onDateClick = {
            echo(formats.date(it, DateStyle.Medium))
            calendar.show(it, CalendarView.Day)
        },
        onTimeClick = { echo("${formats.date(it.date, DateStyle.Medium)} ${formats.time(it.time)}") },
        onCreateRequest = { start, end ->
            // From the month: whole days, midnight to midnight, kept as all-day.
            val wholeDays = start.time == LocalTime(0, 0) && end.time == LocalTime(0, 0) && end.date > start.date
            events = events + if (wholeDays) {
                CalendarEvent(made++, "New event", startDate = start.date, endDate = end.date.minus(1, DateTimeUnit.DAY))
            } else {
                CalendarEvent(made++, "New event", start, end)
            }
            echo(if (wholeDays) "New event on ${formats.date(start.date, DateStyle.Medium)}" else "New event at ${formats.time(start.time)}")
        },
        onEventMove = { event, start, end ->
            replace(event, start, end)
            echo("${event.title} moved")
        },
        onEventResize = { event, start, end ->
            replace(event, start, end)
            echo("${event.title} now ends ${formats.time(end.time)}")
        },
        onEventDelete = { event ->
            events = events.filterNot { it.id == event.id }
            echo("${event.title} deleted")
        },
        daysStyle = this[eventCalendarDays],
        modifier = Modifier.fillMaxWidth().height(EventCalendarDemoHeight),
    )
}

/** Tall enough for a month's six weeks with an event or two each, and a working day's hours. */
private val EventCalendarDemoHeight = 640.dp

internal val dateTimeDemos = listOf(
    ActivityCalendarDemo,
    EventCalendarDemo,
    DatePickerDemo,
    DateRangePickerDemo,
    DateTimePickerDemo,
    CalendarMonthDemo,
    TimePickerDemo,
    TimeFieldDemo,
    DateFieldDemo,
    WheelPickerDemo,
    RelativeTimeTextDemo,
    LocaleFormatsDemo,
)
