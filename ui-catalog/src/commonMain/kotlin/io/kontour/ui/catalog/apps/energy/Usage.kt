package io.kontour.ui.catalog.apps.energy

import io.kontour.ui.charts.rememberChartSeriesVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.kontour.ui.catalog.apps.transit.rootNavigation
import io.kontour.ui.catalog.pageScroll
import io.kontour.ui.charts.Chart
import io.kontour.ui.charts.ChartAxis
import io.kontour.ui.charts.ChartDataTable
import io.kontour.ui.charts.ChartScope
import io.kontour.ui.charts.ChartSeries
import io.kontour.ui.charts.ChartRangeMode
import io.kontour.ui.charts.ChartRangeSelectionState
import io.kontour.ui.charts.ChartStacking
import io.kontour.ui.charts.rememberChartRangeSelectionState
import io.kontour.ui.charts.rememberChartSelectionState
import io.kontour.ui.components.selection.SegmentedControl
import io.kontour.ui.components.selection.Switch
import io.kontour.ui.foundation.Text
import io.kontour.ui.nav.TopBar
import io.kontour.ui.nav.TopBarStyle
import io.kontour.ui.nav.rememberTopBarCollapseState
import io.kontour.ui.nav.topBarCollapse
import io.kontour.ui.theme.Theme
import kotlinx.datetime.LocalDate

/** How far back the usage page looks. */
private enum class Span(val label: String, val days: Int) {
    Week("Week", 7),
    Fortnight("Fortnight", 14),
    Month("Month", 30),
}

/** The stackings a day's bars can be drawn with, and what each is called here. */
private val stackings = listOf(ChartStacking.Stacked, ChartStacking.Grouped, ChartStacking.Normalised)

private fun stackingLabel(stacking: ChartStacking): String = when (stacking) {
    ChartStacking.Grouped -> "Side by side"
    ChartStacking.Normalised -> "Shares"
    else -> "Stacked"
}

/**
 * Usage: where the house's energy went, day by day and appliance by appliance,
 * as bars that stack, group or share out, or as the table of the same numbers;
 * then when in the week it goes, as a heat map; how a month's days spread, as
 * boxes; and how the heat drives it, as points.
 */
@Composable
internal fun UsagePage() {
    val collapse = rememberTopBarCollapseState()
    val patterns = energyPatterns()
    Column(Modifier.fillMaxSize().topBarCollapse(collapse)) {
        TopBar(style = TopBarStyle.Large, navigation = rootNavigation(), collapseState = collapse) { +"Usage" }
        Column(
            Modifier.fillMaxSize().pageScroll().padding(energyPagePadding()).testTag("energy-usage"),
            verticalArrangement = Arrangement.spacedBy(Theme.spacing.lg),
        ) {
            ByAppliance(patterns)
            BusyHours(patterns)
            DaysSpread(patterns)
            HeatAndUse(patterns)
        }
    }
}

@Composable
private fun ByAppliance(patterns: Boolean) {
    var span by remember { mutableStateOf(Span.Fortnight) }
    var stacking by remember { mutableStateOf(ChartStacking.Stacked) }
    var asTable by remember { mutableStateOf(false) }
    val uses = remember(span) { dayUses(span.days) }
    val days = remember { ChartAxis.date() }
    // In shares the axis up reads in per cent of each day, not in kWh.
    val energy = remember(stacking) { ChartAxis.number(title = if (stacking == ChartStacking.Normalised) "Share of the day" else "kWh") }
    val selection = rememberChartSelectionState<LocalDate>()
    // A tap on an appliance in the legend takes it off the bars, and another
    // puts it back: the car's days, without the car.
    val appliances = rememberChartSeriesVisibility()
    val average = remember(uses, span) { uses.sumOf { it.kwh } / span.days }
    // One content for the chart and its table, so the two never disagree.
    val content: ChartScope<LocalDate, Number>.() -> Unit = remember(uses, stacking, average) {
        {
            bar(uses, x = { it.date }, y = { it.kwh }, series = { ChartSeries(it.appliance.label) }, stacking = stacking)
            if (stacking == ChartStacking.Stacked) rule(y = average, label = "Average")
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(Theme.spacing.sm)) {
        EnergyHeading("By appliance", supporting = "What used it, day by day. Tap one below to leave it out")
        SegmentedControl(
            value = span,
            options = Span.entries,
            onValueChange = { span = it },
            optionLabel = { it.label },
            modifier = Modifier.fillMaxWidth(),
        )
        SegmentedControl(
            value = stacking,
            options = stackings,
            onValueChange = { stacking = it },
            optionLabel = ::stackingLabel,
            modifier = Modifier.fillMaxWidth(),
        )
        if (asTable) {
            ChartDataTable(xAxis = days, yAxis = energy, modifier = Modifier.fillMaxWidth().testTag("energy-usage-table"), striped = true, content = content)
        } else {
            Chart(
                xAxis = days,
                yAxis = energy,
                modifier = Modifier.fillMaxWidth().height(TallChartHeight).testTag("energy-usage-chart"),
                selection = selection,
                seriesVisibility = appliances,
                contentDescription = "Energy used each day, by appliance",
                showPatterns = patterns,
                content = content,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Show as a table", style = Theme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Switch(checked = asTable, onCheckedChange = { asTable = it })
        }
    }
}

@Composable
private fun BusyHours(patterns: Boolean) {
    val days = remember { ChartAxis.category(weekDays) }
    val parts = remember { ChartAxis.category(dayParts) }
    Column(verticalArrangement = Arrangement.spacedBy(Theme.spacing.xs)) {
        EnergyHeading("When in the week", supporting = "Each cell the house's use in the three hours from it")
        Chart(
            xAxis = days,
            yAxis = parts,
            modifier = Modifier.fillMaxWidth().height(TallChartHeight).testTag("energy-heat-map"),
            contentDescription = "Use by day of the week and time of day",
            showPatterns = patterns,
        ) {
            cell(hoursOfWeek, x = { it.day }, y = { it.hour }, value = { it.kwh })
        }
    }
}

@Composable
private fun DaysSpread(patterns: Boolean) {
    val names = remember { ChartAxis.category(monthNames) }
    val perDay = remember { ChartAxis.number(title = "kWh a day") }
    val selection = rememberChartSelectionState<String>()
    Column(verticalArrangement = Arrangement.spacedBy(Theme.spacing.xs)) {
        EnergyHeading("A month's days", supporting = "The box is the middle half of them; the dots, days the car charged")
        Chart(
            xAxis = names,
            yAxis = perDay,
            modifier = Modifier.fillMaxWidth().height(ChartHeight).testTag("energy-boxes"),
            selection = selection,
            contentDescription = "How each month's days spread",
            showPatterns = patterns,
        ) {
            box(months, x = { it.name }, samples = { it.dailyUse })
        }
    }
}

/** The season a month is in, south of the equator. */
private fun seasonOf(month: String): String = when (month) {
    "Dec", "Jan", "Feb" -> "Summer"
    "Mar", "Apr", "May" -> "Autumn"
    "Jun", "Jul", "Aug" -> "Winter"
    else -> "Spring"
}

@Composable
private fun HeatAndUse(patterns: Boolean) {
    val heat = remember { ChartAxis.number(title = "Hottest day, °C") }
    val used = remember { ChartAxis.number(title = "kWh used") }
    val selection = rememberChartSelectionState<Number>()
    // A box drawn round some of the months says what they come to: the heat
    // of a run of hot months against what they cost, say.
    val box = rememberChartRangeSelectionState<Number, Number>(ChartRangeMode.Box)
    val formats = Theme.formats
    Column(verticalArrangement = Arrangement.spacedBy(Theme.spacing.xs)) {
        EnergyHeading("Heat and use", supporting = "A month a point, by its season. Drag round some to add them up")
        Chart(
            xAxis = heat,
            yAxis = used,
            modifier = Modifier.fillMaxWidth().height(ChartHeight).testTag("energy-scatter"),
            selection = selection,
            rangeSelection = box,
            contentDescription = "Each month's hottest day against what the house used",
            showPatterns = patterns,
        ) {
            point(months, x = { it.maxTemp }, y = { it.used }, series = { ChartSeries(seasonOf(it.name)) })
        }
        val inBox = boxed(box)
        Text(
            if (inBox == null) {
                "Drag, or hold and drag, round some months"
            } else if (inBox.isEmpty()) {
                "No months in the box"
            } else {
                val average = inBox.sumOf { it.used } / inBox.size
                "${inBox.size} months, ${formats.decimal(average, 0)} kWh on average"
            },
            style = Theme.typography.bodySmall,
            colour = Theme.colours.contentMuted,
        )
    }
}

/** The months inside [box], or `null` with nothing marked. */
private fun boxed(box: ChartRangeSelectionState<Number, Number>): List<EnergyMonth>? {
    val xStart = box.xStart?.toDouble() ?: return null
    val xEnd = box.xEnd?.toDouble() ?: return null
    val yStart = box.yStart?.toDouble() ?: Double.NEGATIVE_INFINITY
    val yEnd = box.yEnd?.toDouble() ?: Double.POSITIVE_INFINITY
    return months.filter { it.maxTemp.toDouble() in xStart..xEnd && it.used.toDouble() in yStart..yEnd }
}
