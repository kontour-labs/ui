package io.kontour.ui.demo

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
import io.kontour.ui.charts.ChartStacking
import io.kontour.ui.charts.SectorChart
import io.kontour.ui.charts.Sparkline
import io.kontour.ui.charts.rememberChartSelectionState
import io.kontour.ui.components.action.Button
import io.kontour.ui.components.action.ButtonVariant
import io.kontour.ui.foundation.Text
import io.kontour.ui.theme.Theme

/** What the chart demo draws, one kind of mark at a time and a mix. */
internal enum class ChartKindDemo { Lines, Areas, Bars, LyingBars, Points, Mixed }

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
    val selection = rememberChartSelectionState<String>()
    val spacing = Theme.spacing

    Column(verticalArrangement = Arrangement.spacedBy(spacing.lg)) {
        val chartModifier = Modifier.fillMaxWidth().height(260.dp)
        if (kind == ChartKindDemo.LyingBars) {
            Chart(
                xAxis = ChartAxis.number(title = "Riders in August"),
                yAxis = ChartAxis.category<String>(),
                modifier = chartModifier,
                contentDescription = "Riders in August, by route",
                legendPosition = legendPosition,
                showLegend = showLegend,
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
                contentDescription = "Riders by month",
                legendPosition = legendPosition,
                showLegend = showLegend,
                showPatterns = patterns,
                showSelectionTooltip = tooltip,
                animateData = animated,
            ) {
                val series = { item: Riders -> ChartSeries(item.route) }
                when (kind) {
                    ChartKindDemo.Lines ->
                        line(data, x = { it.month }, y = { it.riders }, series = series, interpolation = interpolation, showPoints = points)
                    ChartKindDemo.Areas ->
                        area(data, x = { it.month }, y = { it.riders }, series = series, stacking = stacking, interpolation = interpolation)
                    ChartKindDemo.Bars ->
                        bar(data, x = { it.month }, y = { it.riders }, series = series, stacking = stacking)
                    ChartKindDemo.Points ->
                        point(data, x = { it.month }, y = { it.riders }, series = series)
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
                    ChartKindDemo.LyingBars -> Unit
                }
                rule(y = 700, label = "Capacity")
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(spacing.md), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { seed = (seed + 1) % 4 }, variant = ButtonVariant.Secondary) { +"New data" }
            Text(
                selection.x?.let { "Selected: $it" } ?: "Tap, drag or use the arrow keys to select a month",
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

/** The Charts family: one page, which shows the three of them. */
internal val chartDemos = listOf(ChartDemo)
