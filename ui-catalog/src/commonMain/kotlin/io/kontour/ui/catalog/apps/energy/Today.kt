package io.kontour.ui.catalog.apps.energy

import io.kontour.ui.charts.rememberChartSeriesVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.kontour.icons.tabler.Tabler
import io.kontour.icons.tabler.outline.Battery2
import io.kontour.icons.tabler.outline.HomeBolt
import io.kontour.icons.tabler.outline.Plug
import io.kontour.icons.tabler.outline.SolarPanel
import io.kontour.ui.catalog.apps.transit.rootNavigation
import io.kontour.ui.catalog.pageScroll
import io.kontour.ui.charts.Chart
import io.kontour.ui.charts.ChartAxis
import io.kontour.ui.charts.ChartInterpolation
import io.kontour.ui.charts.ChartSelection
import io.kontour.ui.charts.ChartSeries
import io.kontour.ui.charts.ChartStacking
import io.kontour.ui.charts.LinePattern
import io.kontour.ui.charts.SectorChart
import io.kontour.ui.charts.SeriesStyle
import io.kontour.ui.charts.Sparkline
import io.kontour.ui.charts.rememberChartSelectionState
import io.kontour.ui.components.action.IconButton
import io.kontour.ui.components.display.Card
import io.kontour.ui.components.display.CardDefaults
import io.kontour.ui.components.display.CardVariant
import io.kontour.ui.components.display.Gauge
import io.kontour.ui.components.display.KeyValueList
import io.kontour.ui.components.display.Meter
import io.kontour.ui.components.display.Stat
import io.kontour.ui.components.selection.Switch
import io.kontour.ui.foundation.Text
import io.kontour.ui.input.onPressOutside
import io.kontour.ui.nav.TopBar
import io.kontour.ui.nav.TopBarStyle
import io.kontour.ui.nav.rememberTopBarCollapseState
import io.kontour.ui.nav.topBarCollapse
import io.kontour.ui.overlay.tooltip
import io.kontour.ui.sheet.ModalBottomSheet
import io.kontour.ui.sheet.SheetHeader
import io.kontour.ui.theme.Theme
import kotlinx.coroutines.delay
import kotlin.time.Instant

/**
 * Today: what the roof has made and the house has used so far, the meter as it
 * reads now, the day by the hour with the rest of it forecast, and where the
 * house's power came from.
 */
@Composable
internal fun TodayPage() {
    val collapse = rememberTopBarCollapseState()
    var battery by remember { mutableStateOf(false) }
    var picked by remember { mutableStateOf<String?>(null) }
    val patterns = energyPatterns()

    Column(Modifier.fillMaxSize().topBarCollapse(collapse)) {
        TopBar(
            style = TopBarStyle.Large,
            navigation = rootNavigation(),
            collapseState = collapse,
            actions = {
                IconButton(
                    icon = Tabler.Outline.Battery2,
                    contentDescription = "Battery",
                    onClick = { battery = true },
                    // A tooltip's icon beside its words: the modifier takes
                    // the same slots as the component.
                    modifier = Modifier.tooltip {
                        +Tabler.Outline.Battery2
                        +"82% and charging"
                    },
                )
            },
        ) { +"Today" }
        Column(
            Modifier.fillMaxSize().pageScroll().padding(energyPagePadding()).testTag("energy-today"),
            verticalArrangement = Arrangement.spacedBy(Theme.spacing.lg),
        ) {
            Tiles(picked, onPick = { picked = it })
            LivePower(patterns)
            DayByHour(patterns)
            WhereItCameFrom(patterns)
        }
    }

    BatterySheet(visible = battery, onDismiss = { battery = false })
}

/**
 * Three figures for the day so far, each with its trend at the size of a word.
 * A tile pressed opens its breakdown under the row, and a press anywhere
 * else closes it again — [onPressOutside], which hears the press without
 * taking it, so the press still does whatever it was for.
 *
 * The breakdown is under the row rather than in its tile: a row that shares
 * its width out by weight asks each tile how narrow it can go, and a
 * `KeyValueList` cannot be asked that.
 */
@Composable
private fun Tiles(picked: String?, onPick: (String?) -> Unit) {
    val sofar = todayHours.filter { !it.forecast }
    val spare = sofar.sumOf { it.spare }
    Column(
        Modifier.onPressOutside(enabled = picked != null) { onPick(null) },
        verticalArrangement = Arrangement.spacedBy(Theme.spacing.sm),
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Theme.spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Theme.spacing.sm),
        ) {
            Tile(
                key = "made",
                title = "Made",
                figure = kilowattHours(sofar.sumOf { it.solar }),
                subtitle = "Since sunrise",
                explanation = "What the roof has made today",
                icon = Tabler.Outline.SolarPanel,
                values = sofar.map { it.solar },
                picked = picked,
                onPick = onPick,
            )
            Tile(
                key = "used",
                title = "Used",
                figure = kilowattHours(sofar.sumOf { it.home }),
                subtitle = "${kilowattHours(sofar.sumOf { it.fromSolar })} of it from the roof",
                explanation = "What the house has used today",
                icon = Tabler.Outline.HomeBolt,
                values = sofar.map { it.home },
                picked = picked,
                onPick = onPick,
            )
            Tile(
                key = "sold",
                title = "Exported",
                figure = kilowattHours(spare),
                subtitle = "Earning about \$${(spare * 0.07 * 100).toInt() / 100.0}",
                explanation = "What the roof made that the house did not need",
                icon = Tabler.Outline.Plug,
                values = sofar.map { it.spare },
                picked = picked,
                onPick = onPick,
            )
        }
        if (picked != null) {
            Card(variant = CardVariant.Outlined, modifier = Modifier.fillMaxWidth().testTag("tile-breakdown")) {
                KeyValueList(compact = true) {
                    when (picked) {
                        "made" -> {
                            item("Best hour", "12:00, ${kilowatts(sofar.maxOf { it.solar })}")
                            item("Cloud", "About 11:00")
                        }
                        "used" -> {
                            item("Busiest hour", "07:00")
                            item("Quietest", "03:00")
                        }
                        else -> {
                            item("To the battery", kilowattHours(BatteryShare * spare))
                            item("To the grid", kilowattHours((1 - BatteryShare) * spare))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FlowRowScope.Tile(
    key: String,
    title: String,
    figure: String,
    subtitle: String,
    explanation: String,
    icon: ImageVector,
    values: List<Double>,
    picked: String?,
    onPick: (String?) -> Unit,
) {
    Card(
        modifier = Modifier
            .widthIn(min = TileMinWidth)
            .weight(1f)
            .tooltip {
                +icon
                +explanation
            }
            .testTag("tile-$key"),
        variant = CardVariant.Outlined,
        onClick = { onPick(if (picked == key) null else key) },
        border = if (picked == key) BorderStroke(2.dp, Theme.colours.accent.solid) else CardDefaults.border(CardVariant.Outlined),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Theme.spacing.xs)) {
            Stat {
                value(figure)
                +title
                supporting(subtitle)
            }
            Sparkline(
                values = values,
                modifier = Modifier.fillMaxWidth().height(SparklineHeight),
                contentDescription = "$title through the day",
                showArea = true,
            )
        }
    }
}

/**
 * The meter as it reads now, ten seconds a reading, on a clock's axis: the
 * roof and the house over the last ten minutes. Live is a switch, off to start
 * with, so the gallery's pictures of it hold still.
 */
@Composable
private fun LivePower(patterns: Boolean) {
    val prefs = LocalEnergyPrefs.current
    var step by remember { mutableIntStateOf(0) }
    LaunchedEffect(prefs.live) {
        while (prefs.live) {
            delay(LiveStepMillis)
            step++
        }
    }
    val readings = remember(step) { liveReadings(step) }
    val selection = rememberChartSelectionState<Instant>()
    // Spanning the readings exactly. Left to itself the axis rounds out to
    // whole minutes on its ticks, and held there as the readings moved on:
    // the plot's start emptied for five minutes and then jumped, as if the
    // chart were eating itself away.
    val clock = remember(readings) {
        ChartAxis.instant(timeZone = PerthTime, domain = readings.first().at..readings.last().at)
    }
    val power = remember { ChartAxis.number(title = "kW", includeZero = true) }

    Column(verticalArrangement = Arrangement.spacedBy(Theme.spacing.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EnergyHeading("Now", Modifier.weight(1f), supporting = "The last ten minutes, a reading every ten seconds")
            Text("Live", style = Theme.typography.labelMedium, modifier = Modifier.padding(end = Theme.spacing.xs))
            Switch(checked = prefs.live, onCheckedChange = { prefs.live = it })
        }
        Chart(
            xAxis = clock,
            yAxis = power,
            modifier = Modifier.fillMaxWidth().height(ChartHeight).testTag("energy-live"),
            selection = selection,
            contentDescription = "Power over the last ten minutes",
            showPatterns = patterns,
        ) {
            area(readings, x = { it.at }, y = { it.solar }, series = { Roof }, interpolation = ChartInterpolation.Monotone)
            line(readings, x = { it.at }, y = { it.home }, series = { House }, interpolation = ChartInterpolation.StepCentre)
        }
    }
}

/** A share of an hour's power, by where it came from, for the stacked areas. */
private class Source(val hour: Int, val name: String, val kw: Double)

/**
 * The day by the hour: the house's power stacked by where it came from, the
 * roof's output over it, and from now on the roof's forecast — its likely
 * range as a band and its likeliest as a dashed line. The evening peak is
 * shaded, and now is a pin above the plot.
 */
@Composable
private fun DayByHour(patterns: Boolean) {
    val sources = remember {
        todayHours.filter { !it.forecast }.flatMap {
            listOf(
                Source(it.hour, "From the roof", it.fromSolar),
                Source(it.hour, "From the battery", it.fromBattery),
                Source(it.hour, "From the grid", it.fromGrid),
            )
        }
    }
    val hours = remember { ChartAxis.number(domain = 0.0..23.0, label = { hourLabel(it) }) }
    val power = remember { ChartAxis.number(title = "kW", includeZero = true) }
    val selection = rememberChartSelectionState<Number>()
    val shownSources = rememberChartSeriesVisibility()
    // Read out here: a chart's content records marks and is not composed.
    val dashed = SeriesStyle(line = LinePattern.Dashed)

    Column(verticalArrangement = Arrangement.spacedBy(Theme.spacing.xs)) {
        EnergyHeading("By the hour", supporting = "The house's power by where it came from, and the roof's")
        Chart(
            xAxis = hours,
            yAxis = power,
            modifier = Modifier.fillMaxWidth().height(TallChartHeight).testTag("energy-hours"),
            selection = selection,
            seriesVisibility = shownSources,
            contentDescription = "Today's power by the hour, with the afternoon forecast",
            showPatterns = patterns,
            tooltip = { shown -> HourTooltip(shown) },
        ) {
            region(xStart = PeakStart, xEnd = PeakEnd, label = "Peak")
            area(
                sources,
                x = { it.hour },
                y = { it.kw },
                series = { ChartSeries(it.name) },
                stacking = ChartStacking.Stacked,
                interpolation = ChartInterpolation.Monotone,
            )
            line(todayHours.filter { !it.forecast }, x = { it.hour }, y = { it.solar }, series = { Roof }, interpolation = ChartInterpolation.Monotone)
            band(forecastHours, x = { it.hour }, yStart = { it.low }, yEnd = { it.high }, series = { Roof }, interpolation = ChartInterpolation.Monotone)
            line(forecastHours, x = { it.hour }, y = { it.likely }, series = { Roof }, interpolation = ChartInterpolation.Monotone, style = dashed)
            rule(x = NowHour, label = "Now")
        }
    }
}

/**
 * An hour's tooltip, the app's own in place of the chart's row for each
 * source: what the house drew then, and how much of it was its own, as a
 * meter. A forecast hour says what the roof is likely to make. A chart's
 * `tooltip` is any content at all, in the same card as the default.
 */
@Composable
private fun HourTooltip(shown: ChartSelection<Number>) {
    val formats = Theme.formats
    val typography = Theme.typography
    val drawn = shown.values.filter { it.series.name in SourceNames }
    Column(Modifier.width(IntrinsicSize.Max), verticalArrangement = Arrangement.spacedBy(Theme.spacing.xxs)) {
        Text(shown.label, colour = Theme.colours.contentMuted, style = typography.labelSmall)
        if (drawn.isEmpty()) {
            val likely = shown.values.lastOrNull { it.series == Roof }?.value ?: 0.0
            Text("${formats.decimal(likely, 1)} kW likely", style = typography.titleSmall)
            Text("From the roof, forecast", style = typography.labelMedium)
            return@Column
        }
        val use = drawn.sumOf { it.value }
        val own = drawn.filter { it.series.name != FromTheGrid }.sumOf { it.value }
        val share = if (use > 0) own / use else 0.0
        Text("${formats.decimal(use, 1)} kW in use", style = typography.titleSmall)
        Meter(value = share.toFloat(), modifier = Modifier.fillMaxWidth())
        Text("${formats.percent(share, 0)} its own", style = typography.labelMedium)
        val hour = (shown.x as? Number)?.toInt()
        if (hour != null && hour >= PeakStart && hour < PeakEnd) {
            Text("At the peak price", colour = Theme.colours.contentMuted, style = typography.labelSmall)
        }
    }
}

/** The sources an hour's power is stacked from, the last of them the grid's. */
private val SourceNames = setOf("From the roof", "From the battery", "From the grid")

private const val FromTheGrid = "From the grid"

/**
 * Where the house's power came from today, as a donut: a sector chosen by tap,
 * pointer or keys says its share in the hole.
 */
@Composable
private fun WhereItCameFrom(patterns: Boolean) {
    val sofar = todayHours.filter { !it.forecast }
    val shares = remember {
        listOf(
            "From the roof" to sofar.sumOf { it.fromSolar },
            "From the battery" to sofar.sumOf { it.fromBattery },
            "From the grid" to sofar.sumOf { it.fromGrid },
        )
    }
    val selection = rememberChartSelectionState<ChartSeries>()
    val total = shares.sumOf { it.second }
    val chosen = selection.x?.let { series -> shares.firstOrNull { it.first == series.name } }

    Column(verticalArrangement = Arrangement.spacedBy(Theme.spacing.xs)) {
        EnergyHeading("Where it came from", supporting = "Choose a part to see its share")
        SectorChart(
            modifier = Modifier.fillMaxWidth().height(ChartHeight).testTag("energy-sources"),
            selection = selection,
            seriesVisibility = rememberChartSeriesVisibility(),
            innerRadius = DonutHole,
            contentDescription = "Today's power by where it came from",
            showPatterns = patterns,
            centre = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (chosen != null) Theme.formats.percent(chosen.second / total) else kilowattHours(total),
                        style = Theme.typography.titleMedium,
                    )
                    Text(chosen?.first ?: "Used", style = Theme.typography.bodySmall, colour = Theme.colours.contentMuted)
                }
            },
        ) {
            sectors(shares, value = { it.second }, series = { ChartSeries(it.first) })
        }
    }
}

/**
 * The battery, in a sheet that dims the page and leaves it where it is:
 * `recedes = false`, for a page with something moving on it — here the live
 * meter, on a phone a map or a video — which a receding page would carry with
 * it on every frame of the sheet.
 */
@Composable
private fun BatterySheet(visible: Boolean, onDismiss: () -> Unit) {
    val formats = Theme.formats
    ModalBottomSheet(visible = visible, onDismissRequest = onDismiss, recedes = false) { padding ->
        Column(
            Modifier.fillMaxWidth().padding(padding).testTag("energy-battery"),
            verticalArrangement = Arrangement.spacedBy(Theme.spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SheetHeader {
                +"Battery"
                supporting { +"13.5 kWh, behind the garage" }
            }
            Gauge(
                value = BatteryCharge,
                contentDescription = "Battery charge",
                stateDescription = { formats.percent(it.toDouble()) },
            ) {
                +"82%"
                label("Charged")
            }
            KeyValueList(compact = true, modifier = Modifier.padding(bottom = Theme.spacing.md)) {
                item("Charging at", kilowatts(1.6))
                item("Full by", "16:10")
                item("Kept back", "20% for a blackout")
            }
        }
    }
}

/** An hour on the axis, as the clock writes it. */
internal fun hourLabel(hour: Double): String = "${hour.toInt().toString().padStart(2, '0')}:00"

internal val Roof = ChartSeries("The roof")
private val House = ChartSeries("The house")

private const val PeakStart = 16
private const val BatteryShare = 0.45
private const val PeakEnd = 21
private const val BatteryCharge = 0.82f
private const val DonutHole = 0.62f
private const val LiveStepMillis = 1_000L
private val TileMinWidth = 160.dp
private val SparklineHeight = 28.dp
internal val ChartHeight = 220.dp
internal val TallChartHeight = 260.dp
