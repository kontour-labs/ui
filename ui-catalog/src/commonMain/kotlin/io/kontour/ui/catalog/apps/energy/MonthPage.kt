package io.kontour.ui.catalog.apps.energy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.kontour.ui.catalog.pageScroll
import io.kontour.ui.charts.Chart
import io.kontour.ui.charts.ChartAxis
import io.kontour.ui.charts.ChartInterpolation
import io.kontour.ui.charts.ChartSeries
import io.kontour.ui.charts.rememberChartSelectionState
import io.kontour.ui.components.display.KeyValueList
import io.kontour.ui.nav.TopBar
import io.kontour.ui.theme.Theme

/**
 * One month: its figures as a list of keys and values, and its days as a line
 * with a point for each, against the month's average.
 */
@Composable
internal fun MonthPage(month: EnergyMonth, onBack: () -> Unit) {
    val formats = Theme.formats
    val patterns = energyPatterns()
    val days = remember { ChartAxis.number(title = "Day", domain = 1.0..month.dailyUse.size.toDouble()) }
    val energy = remember { ChartAxis.number(title = "kWh", includeZero = true) }
    val selection = rememberChartSelectionState<Number>()
    val average = month.dailyUse.average()

    Column(Modifier.fillMaxSize()) {
        TopBar(onBack = onBack) { +month.name }
        Column(
            Modifier.fillMaxSize().pageScroll().padding(energyPagePadding()).testTag("energy-month"),
            verticalArrangement = Arrangement.spacedBy(Theme.spacing.lg),
        ) {
            KeyValueList(showDividers = true) {
                item("Made", kilowattHours(month.generated))
                item("Used", kilowattHours(month.used))
                item("Bought", kilowattHours(month.imported))
                item("Sold", kilowattHours(month.exported))
                item("Own power", formats.percent(month.selfPowered))
                item("Bill", "$" + formats.decimal(month.bill, 2))
            }
            EnergyHeading("Day by day", supporting = "Each day's use, against the month's average")
            Chart(
                xAxis = days,
                yAxis = energy,
                modifier = Modifier.fillMaxWidth().height(ChartHeight).testTag("energy-month-days"),
                selection = selection,
                contentDescription = "${month.name}, day by day",
                showPatterns = patterns,
            ) {
                line(
                    month.dailyUse.withIndex().toList(),
                    x = { it.index + 1 },
                    y = { it.value },
                    series = { Used },
                    interpolation = ChartInterpolation.Monotone,
                    showPoints = true,
                )
                rule(y = average, label = "Average")
            }
        }
    }
}

private val Used = ChartSeries("Used")
