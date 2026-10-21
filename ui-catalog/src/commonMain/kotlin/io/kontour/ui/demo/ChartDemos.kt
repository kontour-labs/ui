package io.kontour.ui.demo

import io.kontour.ui.charts.rememberChartSeriesVisibilityState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.kontour.ui.charts.Chart
import io.kontour.ui.charts.ChartAxis
import io.kontour.ui.charts.ChartInterpolation
import io.kontour.ui.charts.ChartLegendPosition
import io.kontour.ui.charts.ChartSeries
import io.kontour.ui.charts.ChartRangeMode
import io.kontour.ui.charts.ChartRangeSelectionState
import io.kontour.ui.charts.ChartStacking
import io.kontour.ui.charts.SectorChart
import io.kontour.ui.charts.Sparkline
import io.kontour.ui.charts.rememberChartRangeSelectionState
import io.kontour.ui.charts.rememberChartSelectionState
import io.kontour.ui.components.action.Button
import io.kontour.ui.components.action.ButtonSize
import io.kontour.ui.components.action.ButtonVariant
import io.kontour.ui.foundation.Text
import io.kontour.ui.theme.Theme
import kotlin.math.abs

/** What the chart demo draws, one kind of mark at a time and a mix. */
internal enum class ChartKindDemo { Lines, Areas, Bars, LyingBars, Points, Mixed, Ranges, Dumbbells, Boxes, HeatMap, Pyramid }

private val chartKind = Knob.Choice("Marks", ChartKindDemo.entries)
private val chartStacking = Knob.Choice("Stacking", ChartStacking.entries, initial = ChartStacking.Stacked)
private val chartInterpolation = Knob.Choice("Line", ChartInterpolation.entries, initial = ChartInterpolation.Monotone)
private val chartLegendPosition = Knob.Choice("Legend", ChartLegendPosition.entries, initial = ChartLegendPosition.Bottom)
private val chartPatterns = Knob.Flag("Patterns")
private val chartLegend = Knob.Flag("Show legend", initial = true)
private val chartPoints = Knob.Flag("Points", initial = true)
private val chartTooltip = Knob.Flag("Tooltip", initial = true)
private val chartAnimated = Knob.Flag("Animated", initial = true)
private val chartDonut = Knob.Flag("Donut", initial = true)
private val chartReferences = Knob.Flag("References", initial = true)
private val chartHides = Knob.Flag("Legend hides series", initial = true)
private val chartMarks = Knob.Flag("Mark a range")

// A box wants a measure both ways, so it is the points' alone, on their two
// number axes; every other chart here is across months, where a box is a
// range across, and the knob offered one that did nothing different.
private val chartRangeMode = Knob.Choice(
    "Range",
    ChartRangeMode.entries.toList(),
    initial = ChartRangeMode.Across,
    name = { if (it == ChartRangeMode.Box) "Box, on points" else "Across" },
)

/** A month's riders on one route. */
private class Riders(val month: String, val route: String, val riders: Int)

private val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug")
private val routes = listOf("Route 950", "Route 951", "Night bus")

/**
 * The same eight months of three routes for a given [seed], so that "New
 * data" moves the chart to new values and back again rather than to noise.
 */
private fun ridersFor(seed: Int): List<Riders> = months.flatMapIndexed { m, month ->
    routes.mapIndexed { r, route ->
        val base = listOf(620, 380, 120)[r]
        val swing = ((m * 37 + r * 53 + seed * 71) % 160) - 60
        Riders(month, route, base + swing + m * 18)
    }
}

/**
 * A month's trip times on one route, in minutes, for a box of them: a spread
 * about its middle, wider for the night bus, with now and then a trip held up.
 */
private fun tripsFor(month: Int, route: Int, seed: Int): List<Int> = List(24) { k ->
    val middle = listOf(28, 34, 22)[route] + (month * 3 + seed * 5) % 7
    val spread = listOf(6, 8, 12)[route]
    val wobble = ((k * 41 + month * 13 + route * 29 + seed * 17) % (spread * 2 + 1)) - spread
    middle + wobble + if ((k + month + seed) % 11 == 0) spread * 3 else 0
}

/**
 * A month on one route as a point: how many rode it against how long its
 * typical trip took, which a box can be drawn round, both being measures.
 */
private class Busy(val month: String, val route: String, val riders: Int, val minutes: Int)

private fun busyFor(data: List<Riders>, seed: Int): List<Busy> = data.map { item ->
    val trips = tripsFor(months.indexOf(item.month), routes.indexOf(item.route), seed).sorted()
    Busy(item.month, item.route, item.riders, trips[trips.size / 2])
}

/** People by age group and sex, for a population pyramid: men drawn to the left as negatives. */
private class Cohort(val ages: String, val sex: String, val people: Int)

private val ageGroups = listOf("80+", "70–79", "60–69", "50–59", "40–49", "30–39", "20–29", "10–19", "0–9")

private fun cohortsFor(seed: Int): List<Cohort> = ageGroups.flatMapIndexed { i, ages ->
    val size = 300 + i * 160 + (i * 37 + seed * 53) % 90
    listOf(Cohort(ages, "Men", -(size - (i * 23 + seed * 7) % 70)), Cohort(ages, "Women", size))
}

internal val ChartDemo = ComponentDemo(
    slug = "chart",
    knobs = listOf(
        chartKind,
        chartStacking,
        chartInterpolation,
        chartLegendPosition,
        chartPatterns,
        chartLegend,
        chartPoints,
        chartTooltip,
        chartAnimated,
        chartDonut,
        chartReferences,
        chartHides,
        chartMarks,
        chartRangeMode,
    ),
) {
    var seed by remember { mutableIntStateOf(0) }
    val data = remember(seed) { ridersFor(seed) }
    val kind = this[chartKind]
    val stacking = this[chartStacking]
    val interpolation = this[chartInterpolation]
    val legendPosition = this[chartLegendPosition]
    val patterns = this[chartPatterns]
    val showLegend = this[chartLegend]
    val points = this[chartPoints]
    val tooltip = this[chartTooltip]
    val animated = this[chartAnimated]
    val donut = this[chartDonut]
    val references = this[chartReferences]
    // One for the charts across months and one for the circle: a route hidden
    // from the lines stays in the circle until it is hidden there too.
    val shownRoutes = rememberChartSeriesVisibilityState()
    val shownSectors = rememberChartSeriesVisibilityState()
    val hides = this[chartHides]
    val visibility = shownRoutes.takeIf { hides }
    val selection = rememberChartSelectionState<String>()
    // Across months for every chart but the points, which are on two
    // measures and so can take a box.
    val range = rememberChartRangeSelectionState<String, Number>(ChartRangeMode.Across)
    val pointSelection = rememberChartSelectionState<Number>()
    val pointRange = rememberChartRangeSelectionState<Number, Number>(this[chartRangeMode])
    val marks = this[chartMarks]
    val spacing = Theme.spacing
    val formats = Theme.formats
    val busy = remember(data, seed) { busyFor(data, seed) }

    Column(verticalArrangement = Arrangement.spacedBy(spacing.lg)) {
        val chartModifier = Modifier.fillMaxWidth().height(260.dp)
        if (kind == ChartKindDemo.HeatMap) {
            // Each route's riders a month, its shade the number: a heat map's
            // rows down, its columns across, both categories.
            Chart(
                xAxis = ChartAxis.category(months),
                yAxis = ChartAxis.category(routes),
                modifier = chartModifier,
                contentDescription = "Riders by route and month",
                showLegend = showLegend,
                seriesVisibility = visibility,
                legendPosition = legendPosition,
                showPatterns = patterns,
                showSelectionTooltip = tooltip,
                animateData = animated,
                selection = selection,
            ) {
                cell(data, x = { it.month }, y = { it.route }, value = { it.riders })
            }
        } else if (kind == ChartKindDemo.Pyramid) {
            // Men to the left as negatives, women to the right, each written
            // as the number it is.
            val formats = Theme.formats
            val people = remember(formats) { ChartAxis.number(title = "People", label = { formats.decimal(abs(it), 0) }) }
            val cohorts = remember(seed) { cohortsFor(seed) }
            Chart(
                xAxis = people,
                yAxis = ChartAxis.category(ageGroups),
                modifier = chartModifier,
                contentDescription = "Population by age and sex",
                legendPosition = legendPosition,
                showLegend = showLegend,
                seriesVisibility = visibility,
                showPatterns = patterns,
                animateData = animated,
            ) {
                bar(cohorts, x = { it.people }, y = { it.ages }, series = { ChartSeries(it.sex) }, stacking = stacking)
            }
        } else if (kind == ChartKindDemo.Boxes) {
            // A month's trip times on two routes, side by side: the box the
            // middle half of them, the whiskers the rest, and a trip held up
            // far past them a dot of its own.
            Chart(
                xAxis = ChartAxis.category(months),
                yAxis = ChartAxis.number(title = "Trip, minutes"),
                modifier = chartModifier,
                selection = selection,
                contentDescription = "Trip times by month",
                legendPosition = legendPosition,
                showLegend = showLegend,
                seriesVisibility = visibility,
                showPatterns = patterns,
                showSelectionTooltip = tooltip,
                animateData = animated,
            ) {
                box(
                    data.filter { it.route != routes[2] },
                    x = { it.month },
                    samples = { tripsFor(months.indexOf(it.month), routes.indexOf(it.route), seed) },
                    series = { ChartSeries(it.route) },
                )
            }
        } else if (kind == ChartKindDemo.Points) {
            // Each route's months as points, riders against trip time: on
            // two measures, so a range here can be a box.
            Chart(
                xAxis = ChartAxis.number(title = "Riders"),
                yAxis = ChartAxis.number(title = "Typical trip, minutes"),
                modifier = chartModifier,
                selection = pointSelection,
                rangeSelection = pointRange.takeIf { marks },
                contentDescription = "Riders against trip time, a month a point",
                legendPosition = legendPosition,
                showLegend = showLegend,
                seriesVisibility = visibility,
                showPatterns = patterns,
                showSelectionTooltip = tooltip,
                animateData = animated,
            ) {
                point(busy, x = { it.riders }, y = { it.minutes }, series = { ChartSeries(it.route) })
            }
        } else if (kind == ChartKindDemo.LyingBars) {
            Chart(
                xAxis = ChartAxis.number(title = "Riders in August"),
                yAxis = ChartAxis.category<String>(),
                modifier = chartModifier,
                contentDescription = "Riders in August, by route",
                legendPosition = legendPosition,
                showLegend = showLegend,
                seriesVisibility = visibility,
                showPatterns = patterns,
                animateData = animated,
            ) {
                bar(data.filter { it.month == "Aug" }, x = { it.riders }, y = { it.route }, stacking = stacking)
            }
        } else {
            Chart(
                xAxis = ChartAxis.category(months),
                yAxis = ChartAxis.number(title = "Riders"),
                modifier = chartModifier,
                selection = selection,
                rangeSelection = range.takeIf { marks },
                contentDescription = "Riders by month",
                legendPosition = legendPosition,
                showLegend = showLegend,
                seriesVisibility = visibility,
                showPatterns = patterns,
                showSelectionTooltip = tooltip,
                animateData = animated,
            ) {
                val series = { item: Riders -> ChartSeries(item.route) }
                // Under the lines, as a range should be: the first route's
                // likely spread either side of what it carried.
                if (references && kind == ChartKindDemo.Lines) {
                    band(
                        data.filter { it.route == routes[0] },
                        x = { it.month },
                        yStart = { it.riders - 60 },
                        yEnd = { it.riders + 60 },
                        series = series,
                        interpolation = interpolation,
                    )
                }
                when (kind) {
                    ChartKindDemo.Lines ->
                        line(data, x = { it.month }, y = { it.riders }, series = series, interpolation = interpolation, showPoints = points)
                    ChartKindDemo.Areas ->
                        area(data, x = { it.month }, y = { it.riders }, series = series, stacking = stacking, interpolation = interpolation)
                    ChartKindDemo.Bars ->
                        bar(data, x = { it.month }, y = { it.riders }, series = series, stacking = stacking)
                    ChartKindDemo.Mixed -> {
                        bar(data.filter { it.route == routes[0] }, x = { it.month }, y = { it.riders }, series = series)
                        line(
                            data.filter { it.route != routes[0] },
                            x = { it.month },
                            y = { it.riders },
                            series = series,
                            interpolation = interpolation,
                            showPoints = points,
                        )
                    }
                    ChartKindDemo.Ranges ->
                        // Each route's quietest and busiest day of the month.
                        bar(
                            data.filter { it.route != routes[2] },
                            x = { it.month },
                            yStart = { it.riders - 90 },
                            yEnd = { it.riders + 70 },
                            series = series,
                        )
                    ChartKindDemo.Dumbbells -> {
                        // The first route's quietest and busiest days, joined.
                        val first = data.filter { it.route == routes[0] }
                        stem(first, x = { it.month }, yStart = { it.riders - 140 }, yEnd = { it.riders + 90 })
                        point(first, x = { it.month }, y = { it.riders - 140 }, series = { ChartSeries("Quietest") })
                        point(first, x = { it.month }, y = { it.riders + 90 }, series = { ChartSeries("Busiest") })
                    }
                    ChartKindDemo.LyingBars, ChartKindDemo.Points, ChartKindDemo.Boxes, ChartKindDemo.HeatMap, ChartKindDemo.Pyramid -> Unit
                }
                // A normalised chart reads in shares, and so do its rules: the
                // capacity in riders would be far off its end. A centred one
                // has no level to rule a line at, only thicknesses.
                val stacks = kind == ChartKindDemo.Bars || kind == ChartKindDemo.Areas
                when {
                    stacks && stacking == ChartStacking.Normalised -> rule(y = 0.5, label = "Half")
                    stacks && stacking == ChartStacking.Centred && kind == ChartKindDemo.Areas -> Unit
                    else -> rule(y = 700, label = "Capacity")
                }
                if (references) {
                    // A school holiday shaded, and three events pinned, the
                    // last two close enough that one pin stands on the other.
                    region(xStart = "Apr", xEnd = "May", label = "Holidays")
                    rule(x = "Feb", label = "Launch")
                    rule(x = "Jun", label = "v1.2")
                    rule(x = "Jul", label = "Outage")
                }
            }
        }

        // What the range takes in, worked out from the data the way an app
        // would: the months it covers and what they came to, or the points
        // inside it and what they average. It said only "Marked: Feb to Apr".
        val shown = { route: String -> !hides || shownRoutes.isShown(ChartSeries(route)) }
        val onPoints = kind == ChartKindDemo.Points
        val marked: String? = when {
            !marks -> null
            onPoints -> pointRange.xStart?.let { _ ->
                val inside = busy.filter { shown(it.route) && it.inside(pointRange) }
                if (inside.isEmpty()) {
                    "No months inside"
                } else {
                    val riders = inside.sumOf { it.riders } / inside.size.toDouble()
                    val minutes = inside.sumOf { it.minutes } / inside.size.toDouble()
                    "${inside.size} months inside, averaging ${formats.decimal(riders, 0)} riders and " +
                        "${formats.decimal(minutes, 0)} minutes a trip"
                }
            }
            else -> range.xStart?.let { from ->
                val to = range.xEnd ?: from
                val covered = months.subList(months.indexOf(from), months.indexOf(to) + 1)
                val riders = data.filter { it.month in covered && shown(it.route) }.sumOf { it.riders }
                val span = if (covered.size == 1) from else "$from to $to"
                "$span: ${covered.size} ${if (covered.size == 1) "month" else "months"}, ${formats.decimal(riders.toDouble(), 0)} riders"
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.md), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { seed = (seed + 1) % 4 }, variant = ButtonVariant.Secondary) { +"New data" }
            if (marked != null) {
                // A tap on the chart or Escape lets go too; this is the way
                // that says so.
                Button(
                    onClick = { if (onPoints) pointRange.clear() else range.clear() },
                    variant = ButtonVariant.Tertiary,
                    size = ButtonSize.Small,
                ) { +"Clear" }
            }
            Text(
                marked ?: (if (onPoints) pointSelection.x?.let { "Selected: ${formats.decimal(it.toDouble(), 0)} riders" } else selection.x?.let { "Selected: $it" })
                    ?: when {
                        marks && onPoints && pointRange.mode == ChartRangeMode.Box ->
                            "Drag, or hold and drag, a box round some months"
                        marks -> "Drag, or hold and drag, across the chart; Shift and the arrows stretch from the selected place"
                        onPoints -> "Tap, drag or use the arrow keys to select a point"
                        else -> "Tap, drag or use the arrow keys to select a month"
                    },
                style = Theme.typography.bodySmall,
                colour = Theme.colours.contentMuted,
            )
        }

        // Side by side where there is room, and the sparklines under the
        // circle on a phone, where beside it they left it 40dp across.
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            SectorChart(
                modifier = Modifier.widthIn(min = 260.dp).weight(1f).height(180.dp),
                innerRadius = if (donut) 0.6f else 0f,
                seriesVisibility = shownSectors.takeIf { hides },
                contentDescription = "Riders in August, by route",
                showPatterns = patterns,
                showLegend = showLegend,
                animateData = animated,
            ) {
                sectors(data.filter { it.month == "Aug" }, value = { it.riders }, series = { ChartSeries(it.route) })
            }
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                routes.forEach { route ->
                    Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                        Text(route, style = Theme.typography.labelMedium)
                        Sparkline(
                            values = data.filter { it.route == route }.map { it.riders },
                            modifier = Modifier.size(width = 72.dp, height = 22.dp),
                            contentDescription = "$route this year",
                            animateData = animated,
                        )
                    }
                }
            }
        }
    }
}

/** Whether this month is inside [range]: across it, and up it where it is a box. */
private fun Busy.inside(range: ChartRangeSelectionState<Number, Number>): Boolean {
    val across = (range.xStart?.toDouble() ?: return false)..(range.xEnd?.toDouble() ?: return false)
    val up = range.yStart?.toDouble()?.let { low -> low..(range.yEnd?.toDouble() ?: low) }
    return riders.toDouble() in across && (up == null || minutes.toDouble() in up)
}

/** The Charts family: one page, which shows the three of them. */
internal val chartDemos = listOf(ChartDemo)
