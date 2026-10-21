package io.kontour.ui.charts

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import io.kontour.ui.locale.PseudoLocale
import io.kontour.ui.locale.Quantity
import io.kontour.ui.locale.pseudoLocalise
import io.kontour.ui.theme.StringLookup
import io.kontour.ui.theme.phrase

/**
 * The words a chart says on its own: what a screen reader hears of it, and the
 * few labels it draws itself.
 *
 * The charts' counterpart to `Strings`, and kept apart from it for the reason
 * the module is: an app without a chart carries none of these. English by
 * default. An app in another language builds its own with [from], over the same
 * `StringLookup` it gives `Strings.from`, and provides it with
 * [LocalChartStrings], built again only when its language changes:
 *
 * ```kotlin
 * val chartStrings = remember(locale) { ChartStrings.from(lookupFor(locale)) }
 * CompositionLocalProvider(LocalChartStrings provides chartStrings) {
 *     App()
 * }
 * ```
 *
 * Remembered, because its phrases are functions, and two built apart are
 * never equal: built in the composition, every chart below took them for new
 * words at each recomposition, and wrote its whole description again.
 *
 * `python3 docs/export-strings.py` writes these with the library's own, as
 * `kontour_` resources in one `strings.xml`, for a translator to start from.
 *
 * A phrase with a number in it takes a `Quantity`, which carries the number as
 * the reader writes it and its plural form in their language, so a translation
 * can choose "1 series" or "2 series" the way its own grammar does. Every
 * number and date put into a phrase has already been written by
 * `Theme.formats`.
 *
 * @property lineChart What a chart of lines is, said first of it.
 * @property areaChart What a chart of areas is.
 * @property barChart What a chart of bars is.
 * @property scatterChart What a chart of points alone is.
 * @property chart What a chart of more than one kind of mark is.
 * @property pieChart What a [SectorChart] with no hole is.
 * @property donutChart What a [SectorChart] with a hole is.
 * @property trend What a [Sparkline] is, said before what it shows.
 * @property seriesCount How many series a chart has: "3 series".
 * @property horizontalAxis The axis across, where it has no title.
 * @property verticalAxis The axis up, where it has no title.
 * @property axisRange What an axis spans: "Riders, 0 to 1,200".
 * @property seriesSummary One series in a sentence: its name, the least and the
 *   most of it, and which way it goes from start to end.
 * @property valuesSummary The same, for the series of a chart that has only
 *   one, which has no name.
 * @property trendRising The way a series goes when it ends higher than it starts.
 * @property trendFalling When it ends lower.
 * @property trendSteady When it ends where it started.
 * @property seriesValue One series' value at one place: "Bus, 42".
 * @property atPosition Everything at one place along the chart: "March: Bus, 42
 *   and Train, 17".
 * @property valueRange A span of the axis, where one place stands for several:
 *   "1 March to 7 March".
 * @property sectorShare One sector's part of the whole: "Rent, 45%".
 * @property noData What a chart with nothing to show says it shows.
 * @property total What the whole of a stack is called in its tooltip: a
 *   stacked bar's or area's, at the place chosen.
 * @property selectedRange What a range marked across a chart is said as:
 *   "Selected 3 Mar to 9 Mar".
 * @property selectedBox What a box marked on a chart is said as, across and
 *   then up: "Selected 14 to 22, 2 to 6".
 * @property dateColumn The heading of a [ChartDataTable]'s first column, over
 *   dates, where the axis has no title.
 * @property categoryColumn The same, over categories.
 * @property valueColumn The heading of a column of values that has no name of
 *   its own: a chart of one unnamed series, or numbers along the place axis.
 * @property boxPlot What a chart of boxes is.
 * @property heatMap What a chart of cells is.
 * @property boxSummary One box in a phrase: "median 18, quartiles 15 to 21,
 *   range 10 to 30".
 * @property boxOutliers A box's outliers, after its summary: "outliers 3 and 41".
 * @property lowColumn A [ChartDataTable]'s column of the low ends of boxes'
 *   whiskers; the rest of a box's columns likewise.
 * @property seriesColumn A column of one of a series' numbers, where a series has
 *   several: "Route 950, Median".
 */
@Immutable
data class ChartStrings(
    val lineChart: String = "Line chart",
    val areaChart: String = "Area chart",
    val barChart: String = "Bar chart",
    val scatterChart: String = "Scatter chart",
    val chart: String = "Chart",
    val pieChart: String = "Pie chart",
    val donutChart: String = "Donut chart",
    val trend: String = "Trend",
    val seriesCount: (count: Quantity) -> String =
        { count -> count.select(one = "${count.formatted} series", other = "${count.formatted} series") },
    val horizontalAxis: String = "Horizontal axis",
    val verticalAxis: String = "Vertical axis",
    val axisRange: (axis: String, from: String, to: String) -> String =
        { axis, from, to -> "$axis, $from to $to" },
    val seriesSummary: (name: String, least: String, most: String, direction: String) -> String =
        { name, least, most, direction -> "$name, $least to $most, $direction" },
    val valuesSummary: (least: String, most: String, direction: String) -> String =
        { least, most, direction -> "$least to $most, $direction" },
    val trendRising: String = "rising",
    val trendFalling: String = "falling",
    val trendSteady: String = "steady",
    val seriesValue: (series: String, amount: String) -> String = { series, amount -> "$series, $amount" },
    val atPosition: (position: String, values: String) -> String = { position, values -> "$position: $values" },
    val valueRange: (from: String, to: String) -> String = { from, to -> "$from to $to" },
    val sectorShare: (name: String, percent: String) -> String = { name, percent -> "$name, $percent" },
    val noData: String = "No data",
    val dateColumn: String = "Date",
    val categoryColumn: String = "Category",
    val valueColumn: String = "Value",
    val boxPlot: String = "Box plot",
    val heatMap: String = "Heat map",
    val boxSummary: (low: String, lowerQuartile: String, median: String, upperQuartile: String, high: String) -> String =
        { low, lowerQuartile, median, upperQuartile, high ->
            "median $median, quartiles $lowerQuartile to $upperQuartile, range $low to $high"
        },
    val boxOutliers: (values: String) -> String = { values -> "outliers $values" },
    val lowColumn: String = "Low",
    val lowerQuartileColumn: String = "Lower quartile",
    val medianColumn: String = "Median",
    val upperQuartileColumn: String = "Upper quartile",
    val highColumn: String = "High",
    val outliersColumn: String = "Outliers",
    val seriesColumn: (series: String, column: String) -> String = { series, column -> "$series, $column" },
    val total: String = "Total",
    val selectedRange: (from: String, to: String) -> String = { from, to -> "Selected $from to $to" },
    val selectedBox: (fromAcross: String, toAcross: String, fromUp: String, toUp: String) -> String =
        { fromAcross, toAcross, fromUp, toUp -> "Selected $fromAcross to $toAcross, $fromUp to $toUp" },
) {
    companion object {
        /**
         * These strings from an app's own resources, by field name, the way
         * `Strings.from` reads the library's: whatever [lookup] has no
         * translation for keeps [fallback]'s English.
         */
        fun from(lookup: StringLookup, fallback: ChartStrings = ChartStrings()): ChartStrings = ChartStrings(
            lineChart = lookup.text("lineChart") ?: fallback.lineChart,
            areaChart = lookup.text("areaChart") ?: fallback.areaChart,
            barChart = lookup.text("barChart") ?: fallback.barChart,
            scatterChart = lookup.text("scatterChart") ?: fallback.scatterChart,
            chart = lookup.text("chart") ?: fallback.chart,
            pieChart = lookup.text("pieChart") ?: fallback.pieChart,
            donutChart = lookup.text("donutChart") ?: fallback.donutChart,
            trend = lookup.text("trend") ?: fallback.trend,
            seriesCount = { count ->
                lookup.phrase("seriesCount", count, count.formatted) ?: fallback.seriesCount(count)
            },
            horizontalAxis = lookup.text("horizontalAxis") ?: fallback.horizontalAxis,
            verticalAxis = lookup.text("verticalAxis") ?: fallback.verticalAxis,
            axisRange = { axis, from, to ->
                lookup.phrase("axisRange", null, axis, from, to) ?: fallback.axisRange(axis, from, to)
            },
            seriesSummary = { name, least, most, direction ->
                lookup.phrase("seriesSummary", null, name, least, most, direction)
                    ?: fallback.seriesSummary(name, least, most, direction)
            },
            valuesSummary = { least, most, direction ->
                lookup.phrase("valuesSummary", null, least, most, direction)
                    ?: fallback.valuesSummary(least, most, direction)
            },
            trendRising = lookup.text("trendRising") ?: fallback.trendRising,
            trendFalling = lookup.text("trendFalling") ?: fallback.trendFalling,
            trendSteady = lookup.text("trendSteady") ?: fallback.trendSteady,
            seriesValue = { series, amount ->
                lookup.phrase("seriesValue", null, series, amount) ?: fallback.seriesValue(series, amount)
            },
            atPosition = { position, values ->
                lookup.phrase("atPosition", null, position, values) ?: fallback.atPosition(position, values)
            },
            valueRange = { from, to ->
                lookup.phrase("valueRange", null, from, to) ?: fallback.valueRange(from, to)
            },
            sectorShare = { name, percent ->
                lookup.phrase("sectorShare", null, name, percent) ?: fallback.sectorShare(name, percent)
            },
            noData = lookup.text("noData") ?: fallback.noData,
            dateColumn = lookup.text("dateColumn") ?: fallback.dateColumn,
            categoryColumn = lookup.text("categoryColumn") ?: fallback.categoryColumn,
            valueColumn = lookup.text("valueColumn") ?: fallback.valueColumn,
            boxPlot = lookup.text("boxPlot") ?: fallback.boxPlot,
            heatMap = lookup.text("heatMap") ?: fallback.heatMap,
            boxSummary = { low, lowerQuartile, median, upperQuartile, high ->
                lookup.phrase("boxSummary", null, low, lowerQuartile, median, upperQuartile, high)
                    ?: fallback.boxSummary(low, lowerQuartile, median, upperQuartile, high)
            },
            boxOutliers = { values -> lookup.phrase("boxOutliers", null, values) ?: fallback.boxOutliers(values) },
            lowColumn = lookup.text("lowColumn") ?: fallback.lowColumn,
            lowerQuartileColumn = lookup.text("lowerQuartileColumn") ?: fallback.lowerQuartileColumn,
            medianColumn = lookup.text("medianColumn") ?: fallback.medianColumn,
            upperQuartileColumn = lookup.text("upperQuartileColumn") ?: fallback.upperQuartileColumn,
            highColumn = lookup.text("highColumn") ?: fallback.highColumn,
            outliersColumn = lookup.text("outliersColumn") ?: fallback.outliersColumn,
            seriesColumn = { series, column ->
                lookup.phrase("seriesColumn", null, series, column) ?: fallback.seriesColumn(series, column)
            },
            total = lookup.text("total") ?: fallback.total,
            selectedRange = { from, to ->
                lookup.phrase("selectedRange", null, from, to) ?: fallback.selectedRange(from, to)
            },
            selectedBox = { fromAcross, toAcross, fromUp, toUp ->
                lookup.phrase("selectedBox", null, fromAcross, toAcross, fromUp, toUp)
                    ?: fallback.selectedBox(fromAcross, toAcross, fromUp, toUp)
            },
        )
    }
}

/**
 * The [ChartStrings] every chart below reads, English unless an app provides its
 * own.
 */
val LocalChartStrings = staticCompositionLocalOf { ChartStrings() }

/** Every word in these strings in [mode], for the gallery's pseudo-locales, as `Strings.pseudoLocalised` is. */
fun ChartStrings.pseudoLocalised(mode: PseudoLocale): ChartStrings = mapped { pseudoLocalise(it, mode) }

/** These strings with every word passed through [transform], the phrases' after they are filled in. */
internal fun ChartStrings.mapped(transform: (String) -> String): ChartStrings = ChartStrings(
    lineChart = transform(lineChart),
    areaChart = transform(areaChart),
    barChart = transform(barChart),
    scatterChart = transform(scatterChart),
    chart = transform(chart),
    pieChart = transform(pieChart),
    donutChart = transform(donutChart),
    trend = transform(trend),
    seriesCount = { a0 -> transform(seriesCount(a0)) },
    horizontalAxis = transform(horizontalAxis),
    verticalAxis = transform(verticalAxis),
    axisRange = { a0, a1, a2 -> transform(axisRange(a0, a1, a2)) },
    seriesSummary = { a0, a1, a2, a3 -> transform(seriesSummary(a0, a1, a2, a3)) },
    valuesSummary = { a0, a1, a2 -> transform(valuesSummary(a0, a1, a2)) },
    trendRising = transform(trendRising),
    trendFalling = transform(trendFalling),
    trendSteady = transform(trendSteady),
    seriesValue = { a0, a1 -> transform(seriesValue(a0, a1)) },
    atPosition = { a0, a1 -> transform(atPosition(a0, a1)) },
    valueRange = { a0, a1 -> transform(valueRange(a0, a1)) },
    sectorShare = { a0, a1 -> transform(sectorShare(a0, a1)) },
    noData = transform(noData),
    dateColumn = transform(dateColumn),
    categoryColumn = transform(categoryColumn),
    valueColumn = transform(valueColumn),
    boxPlot = transform(boxPlot),
    heatMap = transform(heatMap),
    boxSummary = { a0, a1, a2, a3, a4 -> transform(boxSummary(a0, a1, a2, a3, a4)) },
    boxOutliers = { a0 -> transform(boxOutliers(a0)) },
    lowColumn = transform(lowColumn),
    lowerQuartileColumn = transform(lowerQuartileColumn),
    medianColumn = transform(medianColumn),
    upperQuartileColumn = transform(upperQuartileColumn),
    highColumn = transform(highColumn),
    outliersColumn = transform(outliersColumn),
    seriesColumn = { a0, a1 -> transform(seriesColumn(a0, a1)) },
    total = transform(total),
    selectedRange = { a0, a1 -> transform(selectedRange(a0, a1)) },
    selectedBox = { a0, a1, a2, a3 -> transform(selectedBox(a0, a1, a2, a3)) },
)
