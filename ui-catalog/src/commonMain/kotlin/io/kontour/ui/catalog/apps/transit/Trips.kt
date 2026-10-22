package io.kontour.ui.catalog.apps.transit

import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.dp
import io.kontour.ui.charts.Chart
import io.kontour.ui.charts.ChartAxis
import io.kontour.ui.charts.rememberChartSelectionState
import androidx.compose.foundation.layout.PaddingValues
import io.kontour.ui.catalog.pageScroll
import io.kontour.ui.theme.Tone
import io.kontour.ui.overlay.coachmarkStep
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.kontour.icons.tabler.Tabler
import io.kontour.icons.tabler.outline.ChevronRight
import io.kontour.icons.tabler.outline.InfoCircle
import io.kontour.icons.tabler.outline.Route
import io.kontour.ui.catalog.apps.rememberAppToast
import io.kontour.ui.components.action.Button
import io.kontour.ui.components.action.ButtonVariant
import io.kontour.ui.components.action.IconButton
import io.kontour.ui.components.datetime.ActivityCalendar
import io.kontour.ui.components.display.AvatarGroup
import io.kontour.ui.components.display.Card
import io.kontour.ui.components.display.ConnectorStyle
import io.kontour.ui.components.display.GaugeIndicator
import io.kontour.ui.components.display.KeyValueList
import io.kontour.ui.components.display.Meter
import io.kontour.ui.components.display.Timeline
import io.kontour.ui.components.display.TimelineItem
import io.kontour.ui.components.list.ListItem
import io.kontour.ui.components.list.ListItemDefaults
import io.kontour.ui.components.selection.RadioGroup
import io.kontour.ui.components.selection.Rating
import io.kontour.ui.components.text.TextArea
import io.kontour.ui.foundation.GroupPosition
import io.kontour.ui.foundation.Text
import io.kontour.ui.motion.pageBack
import io.kontour.ui.nav.TopBar
import io.kontour.ui.nav.TopBarStyle
import io.kontour.ui.nav.rememberTopBarCollapseState
import io.kontour.ui.nav.topBarCollapse
import io.kontour.ui.overlay.Popover
import io.kontour.ui.sheet.ModalBottomSheet
import io.kontour.ui.sheet.SheetHeader
import io.kontour.ui.theme.Theme
import kotlinx.datetime.LocalDate
import kotlin.math.roundToInt

/**
 * A year of trips, a shade a day, and how this week stands against the fare
 * cap — then the recent trips themselves.
 *
 * Choosing a day says how many trips it held, and a popover beside it lists
 * them; the meter under it is the week's spend across its scale, the cap at
 * its end, so "how close am I" is answered by where the thumb is.
 */
@Composable
internal fun TripsPage(onTrip: (String) -> Unit) {
    val formats = Theme.formats
    var picked by remember { mutableStateOf<LocalDate?>(TransitToday) }
    var detail by remember { mutableStateOf(false) }
    val collapse = rememberTopBarCollapseState()

    Column(Modifier.fillMaxSize().topBarCollapse(collapse)) {
        TopBar(style = TopBarStyle.Large, navigation = rootNavigation(), collapseState = collapse) {
            +"Trips"
            supporting { +"${tripsByDay.values.sum()} in the last year" }
        }
        Column(
            Modifier.fillMaxSize().pageScroll().padding(transitPagePadding()).testTag("trips"),
            verticalArrangement = Arrangement.spacedBy(Theme.spacing.md),
        ) {
            ActivityCalendar(
                activity = tripsByDay,
                end = TransitToday,
                today = TransitToday,
                selected = picked,
                onDayClick = { picked = it },
                modifier = Modifier.fillMaxWidth().then(
                    LocalTransitTour.current?.let { tour ->
                        Modifier.coachmarkStep(
                            tour,
                            id = "activity",
                            title = "A year of trips",
                            message = "A shade a day: the darker the day, the more you travelled.",
                            tone = Tone.Success,
                        )
                    } ?: Modifier,
                ),
            )
            picked?.let { day ->
                val count = tripsByDay[day] ?: 0
                val those = trips.filter { it.date == day }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${formats.date(day)}: ${if (count == 0) "no trips" else if (count == 1) "1 trip" else "$count trips"}",
                        style = Theme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    if (those.isNotEmpty()) {
                        Box {
                            IconButton(
                                icon = Tabler.Outline.InfoCircle,
                                contentDescription = "That day's trips",
                                onClick = { detail = !detail },
                            )
                            Popover(visible = detail, onDismissRequest = { detail = false }, title = { +formats.date(day) }) {
                                // Compact: a popover is a small container of its own.
                                KeyValueList(compact = true) {
                                    for (trip in those) item(trip.title, trip.fare)
                                }
                            }
                        }
                    }
                }
            }
            TransitHeading("This week")
            Meter(
                value = SpentThisWeek,
                valueRange = 0f..WeeklyCap,
                modifier = Modifier.fillMaxWidth(),
                indicator = GaugeIndicator.Thumb,
                majorTicks = 6,
                tickLabel = { "$${it.roundToInt()}" },
                contentDescription = "Spent this week",
                stateDescription = { "${it.roundToInt()} of ${WeeklyCap.roundToInt()} dollars" },
            ) {
                +dollars(SpentThisWeek)
                label("of the $${WeeklyCap.roundToInt()} weekly cap")
            }
            TransitHeading("By month")
            TripsByMonth()
            TransitHeading("Recent")
            Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.Spacing)) {
                trips.forEachIndexed { index, trip ->
                    ListItem(
                        onClick = { onTrip(trip.id) },
                        position = GroupPosition.of(index, trips.size),
                        modifier = Modifier.testTag("trip-${trip.id}"),
                    ) {
                        leading { +Tabler.Outline.Route }
                        +trip.title
                        supporting { +"${formats.date(trip.date)} · ${trip.fare}" }
                        trailing { +Tabler.Outline.ChevronRight }
                    }
                }
            }
        }
    }
}

/**
 * The year's trips, a bar a month, from the same days the calendar shades:
 * the calendar for a day, the bars for the shape of the year.
 */
@Composable
private fun TripsByMonth() {
    val byMonth = remember {
        tripsByDay.entries
            .groupBy { LocalDate(it.key.year, it.key.month, 1) }
            .map { (first, days) -> first to days.sumOf { it.value } }
            .sortedBy { it.first }
    }
    val months = remember { ChartAxis.date() }
    val count = remember { ChartAxis.number(title = "Trips") }
    Chart(
        xAxis = months,
        yAxis = count,
        modifier = Modifier.fillMaxWidth().height(MonthsHeight).testTag("trips-by-month"),
        selection = rememberChartSelectionState<LocalDate>(),
        contentDescription = "Trips each month",
        showLegend = false,
    ) {
        bar(byMonth, x = { it.first }, y = { it.second })
    }
}

private val MonthsHeight = 180.dp

/**
 * One trip, leg by leg, with who came along and how it went — and a way to
 * report a problem with it, in a sheet.
 *
 * The legs are a vertical timeline, a walk's connector dashed; the company is
 * an avatar group, which counts whoever it has no room for; the rating is a
 * control, and rating the trip says thank you.
 */
@Composable
internal fun TripPage(trip: Trip) {
    val toast = rememberAppToast()
    val formats = Theme.formats
    var stars by remember { mutableFloatStateOf(0f) }
    var reporting by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        TopBar(onBack = pageBack()) {
            +trip.title
            supporting { +formats.date(trip.date) }
        }
        Column(
            Modifier.fillMaxSize().pageScroll().padding(transitPagePadding()),
            verticalArrangement = Arrangement.spacedBy(Theme.spacing.md),
        ) {
            Card(Modifier.fillMaxWidth()) {
                Timeline {
                    trip.legs.forEachIndexed { i, leg ->
                        val last = i == trip.legs.lastIndex
                        TimelineItem(
                            connector = when {
                                last -> ConnectorStyle.None
                                leg.walk -> ConnectorStyle.Dashed
                                else -> ConnectorStyle.Solid
                            },
                            filled = !leg.walk,
                        ) {
                            +"${leg.how}: ${leg.from} to ${leg.to}"
                            supporting { +"${leg.at} · ${leg.minutes} min" }
                        }
                    }
                }
            }
            TransitHeading("Travelling with")
            Row(
                horizontalArrangement = Arrangement.spacedBy(Theme.spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AvatarGroup(names = trip.with, max = 3)
                Text(formats.list(trip.with), style = Theme.typography.bodySmall, colour = Theme.colours.contentMuted)
            }
            TransitHeading("How was it?")
            Rating(
                value = stars,
                onValueChange = { stars = it },
                // Once the finger lifts, not for every star a drag crossed.
                onValueChangeFinished = { toast("Thanks — that helps us plan the timetable") },
                contentDescription = "Rate this trip",
            )
            Button(onClick = { reporting = true }, variant = ButtonVariant.Secondary, modifier = Modifier.fillMaxWidth()) {
                +"Report a problem"
            }
        }
    }

    ReportSheet(
        visible = reporting,
        onDismiss = { reporting = false },
        onSend = { reason ->
            reporting = false
            toast("Reported: $reason. We'll reply within two days.")
        },
    )
}

/** What went wrong, as a list to pick from and room to say more. */
@Composable
private fun ReportSheet(visible: Boolean, onDismiss: () -> Unit, onSend: (String) -> Unit) {
    var reason by remember { mutableStateOf(Reasons.first()) }
    val more = rememberTextFieldState()
    ModalBottomSheet(visible = visible, onDismissRequest = onDismiss, paneTitle = "Report a problem") { padding ->
        Column(
            Modifier.pageScroll(barPadding = PaddingValues()).padding(padding),
            verticalArrangement = Arrangement.spacedBy(Theme.spacing.sm),
        ) {
            SheetHeader { +"Report a problem" }
            Column(
                Modifier.padding(horizontal = Theme.spacing.md),
                verticalArrangement = Arrangement.spacedBy(Theme.spacing.sm),
            ) {
                RadioGroup(options = Reasons, value = reason, onValueChange = { reason = it }) { +it }
                TextArea(state = more, label = "Anything else?", placeholder = "Which stop, what time, what happened")
                Button(
                    onClick = {
                        more.clearText()
                        onSend(reason.lowercase())
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { +"Send" }
            }
        }
    }
}

private val Reasons = listOf("Late or cancelled", "Overcrowded", "Charged the wrong fare", "Something else")
