package io.kontour.ui.catalog.apps.energy

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
import io.kontour.ui.charts.ChartStacking
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
    val energy = remember { ChartAxis.number(title = "kWh") }
    val selection = rememberChartSelectionState<LocalDate>()
    val average = remember(uses, span) { uses.sumOf { it.kwh } / span.days }
    // One content for the chart and its table, so the two never disagree.
    val content: ChartScope<LocalDate, Number>.() -> Unit = remember(uses, stacking, average) {
        {
            bar(uses, x = { it.date }, y = { it.kwh }, series = { ChartSeries(it.appliance.label) }, stacking = stacking)
            if (stacking == ChartStacking.Stacked) rule(y = average, label = "Average")
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(Theme.spacing.sm)) {
        EnergyHeading("By appliance", supporting = "What used it, day by day")
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
    Column(verticalArrangement = Arrangement.spacedBy(Theme.spacing.xs)) {
        EnergyHeading("Heat and use", supporting = "A month a point, by its season")
        Chart(
            xAxis = heat,
            yAxis = used,
            modifier = Modifier.fillMaxWidth().height(ChartHeight).testTag("energy-scatter"),
            selection = selection,
            contentDescription = "Each month's hottest day against what the house used",
            showPatterns = patterns,
        ) {
            point(months, x = { it.maxTemp }, y = { it.used }, series = { ChartSeries(seasonOf(it.name)) })
        }
    }
}
