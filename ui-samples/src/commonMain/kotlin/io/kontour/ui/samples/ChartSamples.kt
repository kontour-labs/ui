package io.kontour.ui.samples

import androidx.compose.foundation.layout.Column
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
import io.kontour.ui.charts.ChartRangeMode
import io.kontour.ui.charts.ChartSeries
import io.kontour.ui.charts.ChartStacking
import io.kontour.ui.charts.LinePattern
import io.kontour.ui.charts.SectorChart
import io.kontour.ui.charts.SeriesStyle
import io.kontour.ui.charts.Sparkline
import io.kontour.ui.charts.rememberChartRangeSelectionState
import io.kontour.ui.charts.rememberChartSelectionState
import io.kontour.ui.charts.rememberChartSeriesVisibilityState
import io.kontour.ui.foundation.Text
import io.kontour.ui.theme.Theme
import kotlinx.datetime.LocalDate
import kotlin.math.abs

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

@Composable
fun ChartHidingSeries() {
    // The legend's entries become switches: a tap hides a route, and the
    // chart closes in on the one that is left.
    val shown = rememberChartSeriesVisibilityState()
    Chart(
        xAxis = ChartAxis.date(),
        yAxis = ChartAxis.number(title = "Riders"),
        modifier = Modifier.fillMaxWidth().height(220.dp),
        seriesVisibility = shown,
        contentDescription = "Riders this week, by route",
    ) {
        line(week, x = { it.day }, y = { it.riders }, series = { ChartSeries("Route ${it.route}") })
    }
}

private class Takings(val day: LocalDate, val riders: Int, val fares: Double)

private val takings = week.filter { it.route == "950" }.map { Takings(it.day, it.riders, it.riders * 2.9) }

@Composable
fun ChartEndAxis() {
    // Two series in different units, each against its own axis: the riders
    // up the start edge, and what they paid down the end.
    val formats = Theme.formats
    Chart(
        xAxis = ChartAxis.date(),
        yAxis = ChartAxis.number(title = "Riders"),
        endYAxis = ChartAxis.number(title = "Takings", label = { formats.currency(it, "AUD") }),
        modifier = Modifier.fillMaxWidth().height(220.dp),
        contentDescription = "Route 950's riders and takings this week",
    ) {
        bar(takings, x = { it.day }, y = { it.riders }, series = { ChartSeries("Riders") })
        onEndAxis {
            line(takings, x = { it.day }, y = { it.fares }, series = { ChartSeries("Takings") }, showPoints = true)
        }
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
fun ChartCustomTooltip() {
    // The selected month's tooltip says what was left of a budget, in place
    // of each category's figure.
    val selection = rememberChartSelectionState<String>()
    val formats = Theme.formats
    Chart(
        xAxis = ChartAxis.category<String>(),
        yAxis = ChartAxis.number(),
        modifier = Modifier.fillMaxWidth().height(220.dp),
        selection = selection,
        contentDescription = "Spending by month",
        tooltip = { shown ->
            val spent = shown.values.sumOf { it.value }
            Column {
                Text(shown.label, colour = Theme.colours.contentMuted, style = Theme.typography.labelSmall)
                Text(formats.currency(spent, "AUD"), style = Theme.typography.titleSmall)
                Text("${formats.currency(2500.0 - spent, "AUD")} under budget")
            }
        },
    ) {
        bar(quarter, x = { it.month }, y = { it.amount }, series = { ChartSeries(it.category) })
    }
}

@Composable
fun ChartSelectOnClick() {
    // A pointer selects a day by clicking it, as a finger does by tapping:
    // moving across the chart leaves the selected day where it is.
    val selection = rememberChartSelectionState<LocalDate>()
    Chart(
        xAxis = ChartAxis.date(),
        yAxis = ChartAxis.number(title = "Riders"),
        modifier = Modifier.fillMaxWidth().height(220.dp),
        selection = selection,
        selectsOnHover = false,
        contentDescription = "Riders this week, by route",
    ) {
        line(week, x = { it.day }, y = { it.riders }, series = { ChartSeries("Route ${it.route}") })
    }
}

@Composable
fun ChartRangeSelection() {
    // Drag across the week to mark some days, and say what they came to.
    val range = rememberChartRangeSelectionState<LocalDate, Number>()
    val formats = Theme.formats
    Chart(
        xAxis = ChartAxis.date(),
        yAxis = ChartAxis.number(title = "Riders"),
        modifier = Modifier.fillMaxWidth().height(220.dp),
        rangeSelection = range,
        contentDescription = "Riders this week, by route",
    ) {
        bar(week, x = { it.day }, y = { it.riders }, series = { ChartSeries("Route ${it.route}") })
    }
    val from = range.xStart
    val to = range.xEnd
    if (from != null && to != null) {
        val riders = week.filter { it.day in from..to }.sumOf { it.riders }
        Text("${formats.decimal(riders.toDouble(), 0)} riders, ${formats.date(from)} to ${formats.date(to)}")
    }
}

@Composable
fun ChartBoxSelection() {
    // A box round some of a scatter's points: the hot days that used the most.
    val box = rememberChartRangeSelectionState<Number, Number>(ChartRangeMode.Box)
    Chart(
        xAxis = ChartAxis.number(title = "Hottest, °C"),
        yAxis = ChartAxis.number(title = "kWh"),
        modifier = Modifier.fillMaxWidth().height(220.dp),
        rangeSelection = box,
        contentDescription = "Each day's heat against its use",
    ) {
        point(heat, x = { it.first }, y = { it.second })
    }
}

private val heat = listOf(18.0 to 9.5, 22.0 to 11.0, 26.0 to 14.5, 31.0 to 19.0, 35.0 to 23.5, 24.0 to 12.0)

private class Weather(val month: String, val low: Int, val high: Int)

private val autumn = listOf(Weather("Mar", 17, 25), Weather("Apr", 14, 23), Weather("May", 11, 20), Weather("Jun", 9, 17))

@Composable
fun ChartRangeBars() {
    // Each month from its coldest to its warmest, a bar from one to the other.
    Chart(
        xAxis = ChartAxis.category<String>(),
        yAxis = ChartAxis.number(title = "°C"),
        modifier = Modifier.fillMaxWidth().height(200.dp),
        contentDescription = "Temperatures in autumn",
    ) {
        bar(autumn, x = { it.month }, yStart = { it.low }, yEnd = { it.high })
    }
}

private class Fare(val route: String, val offPeak: Double, val peak: Double)

private val fares = listOf(Fare("950", 3.10, 4.40), Fare("951", 2.80, 3.90), Fare("T1", 4.20, 6.10))

@Composable
fun ChartDumbbells() {
    // Each route's two fares, joined by a stem: a dumbbell.
    val offPeak = ChartSeries("Off-peak")
    val peak = ChartSeries("Peak")
    // Read out here: a chart's content records marks, and is not composed.
    val joining = SeriesStyle(colour = Theme.colours.outlineStrong)
    Chart(
        xAxis = ChartAxis.category<String>(),
        yAxis = ChartAxis.number(title = "Fare"),
        modifier = Modifier.fillMaxWidth().height(200.dp),
        contentDescription = "Fares by route",
    ) {
        stem(fares, x = { it.route }, yStart = { it.offPeak }, yEnd = { it.peak }, style = joining)
        point(fares, x = { it.route }, y = { it.offPeak }, series = { offPeak })
        point(fares, x = { it.route }, y = { it.peak }, series = { peak })
    }
}

private class Journeys(val route: String, val minutes: List<Int>)

private val journeys = listOf(
    Journeys("950", listOf(24, 26, 27, 27, 28, 29, 31, 33, 52)),
    Journeys("951", listOf(30, 31, 33, 34, 36, 36, 38, 41, 43)),
    Journeys("T1", listOf(18, 18, 19, 19, 20, 20, 21, 22, 23)),
)

@Composable
fun ChartBoxPlot() {
    // How long a trip takes on each route: the box is the middle half of the
    // trips, and the slow one on the 950 is a dot past its whisker.
    Chart(
        xAxis = ChartAxis.category<String>(),
        yAxis = ChartAxis.number(title = "Minutes"),
        modifier = Modifier.fillMaxWidth().height(220.dp),
        contentDescription = "Trip times by route",
    ) {
        box(journeys, x = { it.route }, samples = { it.minutes })
    }
}

@Composable
fun ChartStreamgraph() {
    // The week's riders by route, stacked about the middle: how thick each
    // route is says how many it carried.
    Chart(
        xAxis = ChartAxis.date(),
        yAxis = ChartAxis.number(title = "Riders"),
        modifier = Modifier.fillMaxWidth().height(200.dp),
        contentDescription = "Riders this week, by route",
    ) {
        area(
            week,
            x = { it.day },
            y = { it.riders },
            series = { ChartSeries("Route ${it.route}") },
            stacking = ChartStacking.Centred,
            interpolation = ChartInterpolation.Monotone,
        )
    }
}

private class Busy(val day: String, val hour: String, val riders: Int)

private val mornings = listOf("Mon", "Tue", "Wed").flatMapIndexed { d, day ->
    listOf("6am" to 120, "7am" to 340, "8am" to 410).map { (hour, riders) -> Busy(day, hour, riders + d * 25) }
}

@Composable
fun ChartHeatMap() {
    // Riders by day and hour, each cell shaded by how many, with the scale
    // under the plot.
    Chart(
        xAxis = ChartAxis.category<String>(),
        yAxis = ChartAxis.category<String>(),
        modifier = Modifier.fillMaxWidth().height(200.dp),
        contentDescription = "Morning riders by day and hour",
    ) {
        cell(mornings, x = { it.day }, y = { it.hour }, value = { it.riders })
    }
}

private class Cohort(val ages: String, val sex: String, val people: Int)

private val cohorts = listOf(
    Cohort("60+", "Men", -410), Cohort("60+", "Women", 470),
    Cohort("30–59", "Men", -820), Cohort("30–59", "Women", 800),
    Cohort("0–29", "Men", -900), Cohort("0–29", "Women", 870),
)

@Composable
fun ChartPopulationPyramid() {
    // Men to the left as negatives and women to the right, both written as
    // the number of people they are.
    val formats = Theme.formats
    Chart(
        xAxis = ChartAxis.number(title = "People", label = { formats.decimal(abs(it), 0) }),
        yAxis = ChartAxis.category<String>(),
        modifier = Modifier.fillMaxWidth().height(200.dp),
        contentDescription = "Population by age and sex",
    ) {
        bar(cohorts, x = { it.people }, y = { it.ages }, series = { ChartSeries(it.sex) })
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
