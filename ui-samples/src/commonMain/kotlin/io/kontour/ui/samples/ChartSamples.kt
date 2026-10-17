package io.kontour.ui.samples

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.kontour.ui.charts.Chart
import io.kontour.ui.charts.ChartAxis
import io.kontour.ui.charts.ChartDataTable
import io.kontour.ui.charts.ChartInterpolation
import io.kontour.ui.charts.ChartSeries
import io.kontour.ui.charts.ChartStacking
import io.kontour.ui.charts.LinePattern
import io.kontour.ui.charts.SectorChart
import io.kontour.ui.charts.SeriesStyle
import io.kontour.ui.charts.Sparkline
import io.kontour.ui.charts.rememberChartSelectionState
import io.kontour.ui.foundation.Text
import io.kontour.ui.theme.Theme
import kotlinx.datetime.LocalDate

private class Trip(val day: LocalDate, val route: String, val riders: Int)

private val week = listOf(
    Trip(LocalDate(2026, 10, 12), "950", 640), Trip(LocalDate(2026, 10, 12), "951", 410),
    Trip(LocalDate(2026, 10, 13), "950", 720), Trip(LocalDate(2026, 10, 13), "951", 380),
    Trip(LocalDate(2026, 10, 14), "950", 690), Trip(LocalDate(2026, 10, 14), "951", 455),
    Trip(LocalDate(2026, 10, 15), "950", 810), Trip(LocalDate(2026, 10, 15), "951", 470),
    Trip(LocalDate(2026, 10, 16), "950", 905), Trip(LocalDate(2026, 10, 16), "951", 520),
)

@Composable
fun ChartBasics() {
    // A line per route, across the days of a week, with a reference line.
    val selection = rememberChartSelectionState<LocalDate>()
    Chart(
        xAxis = ChartAxis.date(),
        yAxis = ChartAxis.number(title = "Riders"),
        modifier = Modifier.fillMaxWidth().height(220.dp),
        selection = selection,
        contentDescription = "Riders this week, by route",
    ) {
        line(
            week,
            x = { it.day },
            y = { it.riders },
            series = { ChartSeries("Route ${it.route}") },
            interpolation = ChartInterpolation.Monotone,
            showPoints = true,
        )
        rule(y = 800, label = "Capacity")
    }
}

private class Forecast(val day: LocalDate, val low: Int, val likely: Int, val high: Int)

private val ahead = listOf(
    Forecast(LocalDate(2026, 10, 16), 905, 905, 905),
    Forecast(LocalDate(2026, 10, 17), 860, 930, 990),
    Forecast(LocalDate(2026, 10, 18), 840, 955, 1060),
)

@Composable
fun ChartAnnotations() {
    // The week so far, a forecast's likely range after today, and what else
    // happened: a span shaded and named, events pinned above the plot.
    Chart(
        xAxis = ChartAxis.date(),
        yAxis = ChartAxis.number(title = "Riders"),
        modifier = Modifier.fillMaxWidth().height(240.dp),
        contentDescription = "Route 950 this week, and the next two days",
    ) {
        val route = ChartSeries("Route 950")
        region(xStart = LocalDate(2026, 10, 16), label = "Forecast")
        region(xStart = LocalDate(2026, 10, 13), xEnd = LocalDate(2026, 10, 14), label = "School holidays")
        band(ahead, x = { it.day }, yStart = { it.low }, yEnd = { it.high }, series = { route })
        line(week.filter { it.route == "950" }, x = { it.day }, y = { it.riders }, series = { route })
        line(ahead, x = { it.day }, y = { it.likely }, series = { route }, style = SeriesStyle(line = LinePattern.Dashed))
        rule(x = LocalDate(2026, 10, 15), label = "Timetable change")
        rule(y = 800, label = "Capacity")
    }
}

@Composable
fun ChartDataTableBasics() {
    // The same week as a table: a row a day, a column a route.
    ChartDataTable(
        xAxis = ChartAxis.date(),
        yAxis = ChartAxis.number(title = "Riders"),
        modifier = Modifier.fillMaxWidth(),
    ) {
        line(week, x = { it.day }, y = { it.riders }, series = { ChartSeries("Route ${it.route}") })
    }
}

private class Spend(val month: String, val category: String, val amount: Double)

private val quarter = listOf(
    Spend("Jul", "Rent", 1450.0), Spend("Jul", "Food", 520.0), Spend("Jul", "Travel", 180.0),
    Spend("Aug", "Rent", 1450.0), Spend("Aug", "Food", 610.0), Spend("Aug", "Travel", 340.0),
    Spend("Sep", "Rent", 1450.0), Spend("Sep", "Food", 480.0), Spend("Sep", "Travel", 95.0),
)

@Composable
fun ChartStackedBars() {
    // Bars of categories, stacked into each month's whole.
    Chart(
        xAxis = ChartAxis.category<String>(),
        yAxis = ChartAxis.number(),
        modifier = Modifier.fillMaxWidth().height(220.dp),
        contentDescription = "Spending by month",
    ) {
        bar(
            quarter,
            x = { it.month },
            y = { it.amount },
            series = { ChartSeries(it.category) },
            stacking = ChartStacking.Stacked,
        )
    }
}

@Composable
fun SectorChartBasics() {
    // September's spending as a donut, with the total in its hole.
    SectorChart(
        modifier = Modifier.fillMaxWidth().height(200.dp),
        innerRadius = 0.6f,
        contentDescription = "Spending in September",
        centre = { Text(Theme.formats.currency(2025.0, "AUD")) },
    ) {
        sectors(quarter.filter { it.month == "Sep" }, value = { it.amount }, series = { ChartSeries(it.category) })
    }
}

@Composable
fun SparklineBasics() {
    // A week's trend, at the size of a word, beside the number that matters.
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("905 riders")
        Sparkline(
            values = week.filter { it.route == "950" }.map { it.riders },
            modifier = Modifier.size(width = 64.dp, height = 20.dp),
            contentDescription = "Route 950 this week",
        )
    }
}
