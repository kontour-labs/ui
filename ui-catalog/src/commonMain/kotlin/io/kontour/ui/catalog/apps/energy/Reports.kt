package io.kontour.ui.catalog.apps.energy

import io.kontour.ui.charts.rememberChartSeriesVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.kontour.icons.tabler.Tabler
import io.kontour.icons.tabler.outline.Coin
import io.kontour.icons.tabler.outline.Snowflake
import io.kontour.icons.tabler.outline.Sun
import io.kontour.ui.adaptive.LocalWindowSizeClass
import io.kontour.ui.adaptive.WindowWidthClass
import io.kontour.ui.catalog.apps.transit.rootNavigation
import io.kontour.ui.catalog.pageScroll
import io.kontour.ui.charts.Chart
import io.kontour.ui.charts.ChartAxis
import io.kontour.ui.charts.ChartInterpolation
import io.kontour.ui.charts.ChartSeries
import io.kontour.ui.charts.ChartStacking
import io.kontour.ui.charts.SeriesStyle
import io.kontour.ui.charts.rememberChartSelectionState
import io.kontour.ui.components.display.EmptyState
import io.kontour.ui.components.table.Table
import io.kontour.ui.components.table.TableAlign
import io.kontour.ui.dragdrop.DragType
import io.kontour.ui.dragdrop.rememberDropListState
import io.kontour.ui.foundation.Icon
import io.kontour.ui.nav.DocumentTabs
import io.kontour.ui.nav.TopBar
import io.kontour.ui.theme.Theme
import kotlin.math.abs

/** The kinds of report the app can open, each a different set of charts. */
private enum class ReportKind(val title: String, val icon: ImageVector) {
    Year("Year", Tabler.Outline.Sun),
    Seasons("Seasons", Tabler.Outline.Snowflake),
    Bills("Bills", Tabler.Outline.Coin),
}

private class Report(val id: Int, val title: String, val kind: ReportKind)

private val ReportTab = DragType<Report>("energy-report")

/**
 * Reports, open as documents: a tab each, closed by its ×, another opened by
 * the "+", dragged into another order, and the open one joined to its page.
 * Each report is a set of charts about the last twelve months.
 */
@Composable
internal fun ReportsPage(onMonth: (String) -> Unit) {
    val reports = remember {
        mutableStateListOf(
            Report(1, ReportKind.Year.title, ReportKind.Year),
            Report(2, ReportKind.Seasons.title, ReportKind.Seasons),
            Report(3, ReportKind.Bills.title, ReportKind.Bills),
        )
    }
    var open by remember { mutableStateOf<Report?>(reports.first()) }
    var next by remember { mutableIntStateOf(4) }
    val tabs = rememberDropListState(
        listState = rememberLazyListState(),
        type = ReportTab,
        items = reports,
        key = { it.id },
        label = "Open reports",
        itemLabel = { it.title },
        onDrop = { drop ->
            reports.remove(drop.payload)
            reports.add(drop.index.coerceAtMost(reports.size), drop.payload)
        },
    )
    val pad = energyPagePadding()
    val direction = LocalLayoutDirection.current
    val compact = LocalWindowSizeClass.current.width == WindowWidthClass.Compact

    Column(Modifier.fillMaxSize()) {
        TopBar(navigation = rootNavigation()) { +"Reports" }
        DocumentTabs(
            state = tabs,
            value = open,
            onValueChange = { open = it },
            onClose = { closing ->
                reports.remove(closing)
                if (open == closing) open = reports.lastOrNull()
            },
            onAdd = {
                val kind = ReportKind.entries[next % ReportKind.entries.size]
                val id = next++
                val report = Report(id, "${kind.title} ${id - 2}", kind)
                reports.add(report)
                open = report
            },
            modifier = Modifier
                .padding(start = pad.calculateStartPadding(direction), end = pad.calculateEndPadding(direction))
                .testTag("energy-report-tabs"),
            // Each kind's icon beside its title, where there is room: on a
            // phone three tabs share the width, and the icon took the title's.
            leading = if (compact) null else { report -> Icon(report.kind.icon, contentDescription = null) },
        )
        Column(
            Modifier.fillMaxSize().pageScroll().padding(pad).testTag("energy-report"),
            verticalArrangement = Arrangement.spacedBy(Theme.spacing.lg),
        ) {
            when (open?.kind) {
                ReportKind.Year -> YearReport(onMonth)
                ReportKind.Seasons -> SeasonsReport()
                ReportKind.Bills -> BillsReport()
                null -> EmptyState(Modifier.fillMaxWidth()) {
                    +"No reports open"
                    message { +"Open one with the + above." }
                }
            }
        }
    }
}

/**
 * The year: where the roof's energy went as a stream about its middle, what
 * was bought against what was sold as dumbbells, and every figure in a table
 * whose month column narrows as it scrolls across.
 */
@Composable
private fun YearReport(onMonth: (String) -> Unit) {
    val patterns = energyPatterns()
    val names = remember { ChartAxis.category(monthNames) }
    val energy = remember { ChartAxis.number(title = "kWh") }
    val selection = rememberChartSelectionState<String>()
    // Read out here: a chart's content records marks and is not composed.
    val joining = SeriesStyle(colour = Theme.colours.outlineStrong)
    val formats = Theme.formats

    EnergyHeading("Where the roof's energy went", supporting = "Each stream as thick as what it took")
    Chart(
        xAxis = names,
        yAxis = energy,
        modifier = Modifier.fillMaxWidth().height(ChartHeight).testTag("energy-stream"),
        selection = selection,
        seriesVisibility = rememberChartSeriesVisibility(),
        contentDescription = "Where each month's generation went",
        showPatterns = patterns,
    ) {
        area(
            flows,
            x = { it.month },
            y = { it.kwh },
            series = { ChartSeries(it.into) },
            stacking = ChartStacking.Centred,
            interpolation = ChartInterpolation.Monotone,
        )
    }

    EnergyHeading("Bought and sold", supporting = "From the grid against to it, joined")
    Chart(
        xAxis = names,
        yAxis = energy,
        modifier = Modifier.fillMaxWidth().height(ChartHeight).testTag("energy-dumbbells"),
        selection = selection,
        contentDescription = "Energy bought and sold each month",
        showPatterns = patterns,
    ) {
        stem(months, x = { it.name }, yStart = { it.exported }, yEnd = { it.imported }, style = joining)
        point(months, x = { it.name }, y = { it.imported }, series = { Bought })
        point(months, x = { it.name }, y = { it.exported }, series = { Sold })
    }

    EnergyHeading("Month by month", supporting = "Choose a month for its days")
    Table(
        items = months,
        modifier = Modifier.fillMaxWidth().testTag("energy-months-table"),
        key = { it.name },
        stickyColumns = 1,
        narrowStickyColumns = true,
        striped = true,
        onRowClick = { onMonth(it.name) },
    ) {
        column("Month", width = MonthColumn) { +it.name }
        column("Made", width = FigureColumn, numeric = true) { +kilowattHours(it.generated) }
        column("Used", width = FigureColumn, numeric = true) { +kilowattHours(it.used) }
        column("Bought", width = FigureColumn, numeric = true) { +kilowattHours(it.imported) }
        column("Sold", width = FigureColumn, numeric = true) { +kilowattHours(it.exported) }
        column("Own power", width = FigureColumn, numeric = true) { +formats.percent(it.selfPowered) }
        column("Bill", width = FigureColumn, numeric = true) { +("$" + formats.decimal(it.bill, 2)) }
    }
}

/** A month's energy to or from the grid, bought drawn to the left as a negative. */
private class Trade(val month: String, val way: ChartSeries, val kwh: Double)

/**
 * Summer and winter: bought and sold as a pyramid, a month a row, and the
 * spread of each month's days from its quietest to its busiest as range bars.
 */
@Composable
private fun SeasonsReport() {
    val patterns = energyPatterns()
    val formats = Theme.formats
    val either = remember(formats) { ChartAxis.number(title = "kWh", label = { formats.decimal(abs(it), 0) }) }
    val names = remember { ChartAxis.category(monthNames) }
    val perDay = remember { ChartAxis.number(title = "kWh a day") }
    val trades = remember {
        months.flatMap { listOf(Trade(it.name, Bought, -it.imported), Trade(it.name, Sold, it.exported)) }
    }

    EnergyHeading("Bought and sold", supporting = "Bought to the left, sold to the right")
    Chart(
        xAxis = either,
        yAxis = names,
        modifier = Modifier.fillMaxWidth().height(TallChartHeight).testTag("energy-pyramid"),
        contentDescription = "Energy bought and sold each month",
        showPatterns = patterns,
    ) {
        bar(trades, x = { it.kwh }, y = { it.month }, series = { it.way })
    }

    EnergyHeading("Quiet days and busy ones", supporting = "Each month from its quietest day to its busiest")
    Chart(
        xAxis = names,
        yAxis = perDay,
        modifier = Modifier.fillMaxWidth().height(ChartHeight).testTag("energy-ranges"),
        contentDescription = "Each month's range of daily use",
        showPatterns = patterns,
    ) {
        bar(months, x = { it.name }, yStart = { it.lowestDay }, yEnd = { it.highestDay })
        region(xStart = "Jun", xEnd = "Aug", label = "Winter")
    }
}

/** A stretch of the day at one price. */
private class Window(val name: String, val from: Int, val to: Int, val price: Double)

/**
 * Bills: each month's, with its average and winter shaded, and the day's
 * prices — the tariff's windows as rectangles under a stepped line, at the
 * rates the settings page holds, and the feed-in rate as a rule.
 */
@Composable
private fun BillsReport() {
    val patterns = energyPatterns()
    val prefs = LocalEnergyPrefs.current
    val formats = Theme.formats
    val names = remember { ChartAxis.category(monthNames) }
    val dollars = remember { ChartAxis.number(title = "$") }
    val hours = remember { ChartAxis.number(domain = 0.0..24.0, label = { hourLabel(it) }) }
    val rate = remember { ChartAxis.number(title = "$ a kWh", includeZero = true) }
    val selection = rememberChartSelectionState<String>()

    val peak = formats.parseDecimal(prefs.peak.text.toString()) ?: 0.55
    val offPeak = formats.parseDecimal(prefs.offPeak.text.toString()) ?: 0.24
    val feedIn = formats.parseDecimal(prefs.feedIn.text.toString()) ?: 0.07
    val shoulder = (peak + offPeak) / 2
    val windows = listOf(
        Window("Off-peak", 0, 7, offPeak),
        Window("Shoulder", 7, 16, shoulder),
        Window("Peak", 16, 21, peak),
        Window("Off-peak", 21, 24, offPeak),
    )
    val prices = (0..24).map { hour -> hour to (windows.firstOrNull { hour >= it.from && hour < it.to }?.price ?: offPeak) }
    val average = months.sumOf { it.bill } / months.size

    EnergyHeading("Each month's bill", supporting = "Net of what the roof sold")
    Chart(
        xAxis = names,
        yAxis = dollars,
        modifier = Modifier.fillMaxWidth().height(ChartHeight).testTag("energy-bills"),
        selection = selection,
        contentDescription = "Each month's bill",
        showPatterns = patterns,
    ) {
        region(xStart = "Jun", xEnd = "Aug", label = "Winter")
        bar(months, x = { it.name }, y = { it.bill })
        rule(y = average, label = "Average")
    }

    EnergyHeading("A day's prices", supporting = "From the tariff in Settings")
    Chart(
        xAxis = hours,
        yAxis = rate,
        modifier = Modifier.fillMaxWidth().height(ChartHeight).testTag("energy-prices"),
        contentDescription = "The price of a kilowatt-hour through the day",
        showPatterns = patterns,
    ) {
        rectangle(
            windows,
            xStart = { it.from },
            xEnd = { it.to },
            yStart = { 0.0 },
            yEnd = { it.price },
            series = { ChartSeries(it.name) },
        )
        line(prices, x = { it.first }, y = { it.second }, series = { Price }, interpolation = ChartInterpolation.StepEnd)
        rule(y = feedIn, label = "Feed-in")
    }
}

private val Bought = ChartSeries("Bought")
private val Sold = ChartSeries("Sold")
private val Price = ChartSeries("Price")
private val MonthColumn = 80.dp
private val FigureColumn = 96.dp
