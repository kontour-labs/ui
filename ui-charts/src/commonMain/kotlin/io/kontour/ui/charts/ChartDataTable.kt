package io.kontour.ui.charts

import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import io.kontour.ui.components.table.Table
import io.kontour.ui.locale.ListStyle
import io.kontour.ui.locale.LocaleFormats
import io.kontour.ui.theme.Theme

/**
 * A [Chart]'s numbers as a table: the same axes and the same content, a row
 * for each place along the chart and a column for each series.
 *
 * ```kotlin
 * val content: ChartScope<String, Number>.() -> Unit = {
 *     bar(months, x = { it.month }, y = { it.riders }, series = { ChartSeries(it.route) })
 * }
 * Chart(xAxis = ChartAxis.category(), yAxis = ChartAxis.number(), content = content)
 * ChartDataTable(xAxis = ChartAxis.category(), yAxis = ChartAxis.number(), content = content)
 * ```
 *
 * A chart shows the shape of its data and a table shows the data, and some
 * readers need the second: someone comparing two values exactly, someone
 * whose screen reader is better at a table than at a picture, someone printing
 * the page. Offering one beside the chart, behind a "Show as table", gives
 * them it without a second copy of the data that can drift from the first.
 *
 * Every value is written as the chart writes it, in the reader's own numbers
 * and dates, and a normalised stack's are its shares. A rectangle's cell is
 * its range, and rules, which are not data, are left out. The first column is
 * named by the place axis's title, or by what it holds.
 *
 * @param striped Every other row on a quiet ground, for reading across.
 * @param strings The words the table says on its own.
 * @param content The chart's marks, as a [Chart] takes them.
 */
@Composable
fun <X : Any, Y : Any> ChartDataTable(
    xAxis: ChartAxis<X>,
    yAxis: ChartAxis<Y>,
    modifier: Modifier = Modifier,
    striped: Boolean = false,
    strings: ChartStrings = ChartDefaults.strings(),
    content: ChartScope<X, Y>.() -> Unit,
) {
    val formats = Theme.formats
    val model by remember(xAxis, yAxis, content) {
        derivedStateOf { ChartRecorder(xAxis, yAxis).apply(content).build() }
    }
    val table = remember(model, formats, strings) {
        chartTable(resolve(model, xAxis, yAxis, formats), strings, formats)
    }
    Table(
        items = table.rows,
        modifier = modifier,
        key = { it.place },
        striped = striped,
    ) {
        column(table.placeTitle, numeric = table.placeNumeric, key = PlaceColumn) { row -> +row.label }
        table.columns.forEachIndexed { index, title ->
            column(title, numeric = true, key = index) { row -> +row.cells[index] }
        }
    }
}

/** The key of a [ChartDataTable]'s first column, apart from any series' index. */
private object PlaceColumn

/** A chart as rows of written values. */
internal class ChartTable(
    val placeTitle: String,
    val placeNumeric: Boolean,
    val columns: List<String>,
    val rows: List<ChartTableRow>,
)

/** One place's row: its label, and each series' value there, `""` for none. */
internal class ChartTableRow(val place: Double, val label: String, val cells: List<String>)

/**
 * [chart] as a table. Its places are the rows, each series a column, in the
 * order the series first appear, and one series drawn by two marks is one
 * column, its values from whichever mark has one at the place.
 */
internal fun chartTable(chart: ResolvedChart, strings: ChartStrings, formats: LocaleFormats): ChartTable {
    val placeAxis = chart.placeAxis
    val valueAxis = chart.valueAxis
    val placeTitle = placeAxis.title?.takeIf { it.isNotBlank() } ?: when (placeAxis.kind) {
        AxisKind.Date, AxisKind.Instant -> strings.dateColumn
        AxisKind.Category -> strings.categoryColumn
        AxisKind.Number -> strings.valueColumn
    }
    chart.cells?.let { scale -> return heatMapTable(chart, scale, placeTitle) }

    // A column a value a series has at each place, or a box's five numbers and
    // its outliers, each a column of its own. A stem that only joins what other
    // marks say has none.
    class Column(val title: String, val cell: (Double) -> String)
    val series = chart.layers.filterNot { it.isSilent(chart) }.map { it.series }.distinct()
    val columns = series.flatMap { one ->
        val layers = chart.layers.filter { it.series == one && !it.isSilent(chart) }
        val named = one.name.takeIf { it.isNotBlank() }
        val boxes = layers.firstOrNull { it.box != null }
        if (boxes != null) {
            val box = boxes.box!!
            fun at(place: Double, read: (Int) -> String): String {
                val i = boxes.across.indexOfFirst { it == place }
                return if (i < 0 || boxes.values[i].isNaN()) "" else read(i)
            }
            val base = boxes.base ?: boxes.values
            listOf(
                strings.lowColumn to { place: Double -> at(place) { valueAxis.write(box.low[it]) } },
                strings.lowerQuartileColumn to { place: Double -> at(place) { valueAxis.write(base[it]) } },
                strings.medianColumn to { place: Double -> at(place) { valueAxis.write(boxes.values[it]) } },
                strings.upperQuartileColumn to { place: Double -> at(place) { valueAxis.write(boxes.up[it]) } },
                strings.highColumn to { place: Double -> at(place) { valueAxis.write(box.high[it]) } },
                strings.outliersColumn to { place: Double -> at(place) { formats.list(box.outliers[it].map(valueAxis.write), ListStyle.Unit) } },
            ).map { (title, cell) -> Column(if (named != null) strings.seriesColumn(named, title) else title, cell) }
        } else {
            listOf(
                Column(named ?: valueAxis.title ?: strings.valueColumn) { place ->
                    val written = layers.flatMap { layer ->
                        val places = if (chart.horizontal && layer.mark.kind == MarkKind.Bar) layer.up else layer.across
                        places.indices.filter { places[it] == place && !layer.values[it].isNaN() }.map { i ->
                            val value = layer.values[i]
                            val end = layer.endAt(i) ?: (if (chart.horizontal) layer.acrossEnd else layer.upEnd)?.getOrNull(i)
                            if (end != null && !end.isNaN() && end != value) {
                                strings.valueRange(valueAxis.write(value), valueAxis.write(end))
                            } else {
                                valueAxis.write(value)
                            }
                        }
                    }
                    // A scatter can put several points of one series at one place.
                    when (written.size) {
                        0 -> ""
                        1 -> written[0]
                        else -> formats.list(written, ListStyle.Unit)
                    }
                },
            )
        }
    }
    val rows = chart.places.map { place -> ChartTableRow(place, placeAxis.write(place), columns.map { it.cell(place) }) }
    return ChartTable(placeTitle, placeAxis.kind == AxisKind.Number, columns.map { it.title }, rows)
}

/**
 * A heat map as a table: a row for each of its columns and a column for each
 * of its rows, as its places run across and its rows are its series would be.
 */
private fun heatMapTable(chart: ResolvedChart, scale: CellScale, placeTitle: String): ChartTable {
    val layer = chart.layers.first { it.mark.kind == MarkKind.Cell }
    val rowsUp = layer.up.filter { !it.isNaN() }.distinct().sorted()
    val rows = chart.places.map { place ->
        ChartTableRow(
            place,
            chart.placeAxis.write(place),
            rowsUp.map { up ->
                val i = layer.across.indices.firstOrNull { layer.across[it] == place && layer.up[it] == up }
                if (i == null || layer.values[i].isNaN()) "" else scale.write(layer.values[i])
            },
        )
    }
    return ChartTable(placeTitle, chart.placeAxis.kind == AxisKind.Number, rowsUp.map { chart.y.write(it) }, rows)
}
