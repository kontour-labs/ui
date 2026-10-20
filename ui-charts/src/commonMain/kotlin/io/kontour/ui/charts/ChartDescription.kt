package io.kontour.ui.charts

import io.kontour.ui.locale.ListStyle
import io.kontour.ui.locale.LocaleFormats

/**
 * What a screen reader hears of a chart before anything in it: what kind of
 * chart it is, how many series, what each axis spans, and each series in a
 * sentence — its least and its most and which way it goes — then any rule's
 * label. The shape of Apple's Audio Graphs summary, read rather than played.
 *
 * A chart is a picture of numbers, and a description of the picture ("a line
 * going up") is no use without the numbers. This says the numbers a sighted
 * reader takes from a glance; the places beneath it, [placeDescriptions], say
 * the rest one at a time.
 */
internal fun chartSummary(
    chart: ResolvedChart,
    strings: ChartStrings,
    formats: LocaleFormats,
    sentences: (List<String>) -> String,
    contentDescription: String?,
): String {
    val parts = mutableListOf<String>()
    contentDescription?.takeIf { it.isNotBlank() }?.let { parts += it }
    parts += kindOf(chart.model, strings)
    if (chart.model.isEmpty) {
        parts += strings.noData
        return sentences(parts)
    }

    // Only what is drawn: a series the legend has hidden is not counted.
    val named = chart.model.series.filter { series ->
        series.name.isNotBlank() && chart.model.marks.any { mark -> mark.runs.any { it.series == series } }
    }
    if (named.size > 1) parts += strings.seriesCount(formats.quantity(named.size))

    // A stream's value axis spans nothing a reader can use: its series say
    // their own numbers.
    if (!chart.x.centred) parts += axisSentence(chart.x, chart, strings.horizontalAxis, strings)
    if (!chart.y.centred) parts += axisSentence(chart.y, chart, strings.verticalAxis, strings)

    val valueAxis = chart.valueAxis
    // A heat map's values are its cells' shades, written by its scale.
    chart.cells?.let { scale -> parts += strings.axisRange(strings.valueColumn, scale.write(scale.low), scale.write(scale.high)) }
    chart.model.series.forEach { series ->
        val layers = chart.layers.filter { it.series == series && !it.isSilent(chart) }
        if (layers.any { it.mark.kind == MarkKind.Cell }) return@forEach
        // A range's top counts as well as its foot, and a box's whiskers and
        // outliers as well as its median: the most a band reaches is its high
        // edge, and the least a box does is the end of its lower whisker.
        val values = layers.flatMap { layer ->
            layer.values.filter { !it.isNaN() } +
                layer.values.indices.mapNotNull { layer.endAt(it) } +
                (layer.box?.let { box -> box.low.toList() + box.high.toList() + box.outliers.flatMap { it.toList() } }.orEmpty())
                    .filter { !it.isNaN() }
        }
        if (values.isEmpty()) return@forEach
        val direction = when (trendOf(values.toDoubleArray())) {
            1 -> strings.trendRising
            -1 -> strings.trendFalling
            else -> strings.trendSteady
        }
        val least = valueAxis.write(values.min())
        val most = valueAxis.write(values.max())
        parts += if (series.name.isBlank()) {
            strings.valuesSummary(least, most, direction)
        } else {
            strings.seriesSummary(series.name, least, most, direction)
        }
    }
    // A rule and a region by their labels and where they are: "Capacity, 700",
    // "Launch fortnight, 1 Feb to 14 Feb". One with no label is decoration.
    chart.rules.forEach { rule ->
        val label = rule.label?.takeIf { it.isNotBlank() } ?: return@forEach
        val at = rule.y?.let { chart.y.write(it) } ?: rule.x?.let { chart.x.write(it) }
        parts += if (at == null) label else strings.seriesValue(label, at)
    }
    chart.regions.forEach { region ->
        val label = region.label?.takeIf { it.isNotBlank() } ?: return@forEach
        val span = when {
            region.xStart != null || region.xEnd != null -> spanOf(chart.x, region.xStart, region.xEnd, strings)
            region.yStart != null || region.yEnd != null -> spanOf(chart.y, region.yStart, region.yEnd, strings)
            else -> null
        }
        parts += if (span == null) label else strings.seriesValue(label, span)
    }
    return sentences(parts)
}

/** From [start] to [end] on [axis], written, an end not given being the axis's own. */
private fun spanOf(axis: ResolvedAxis, start: Double?, end: Double?, strings: ChartStrings): String {
    val first = if (axis.kind == AxisKind.Category) 0.0 else axis.low
    val last = if (axis.kind == AxisKind.Category) axis.ticks.lastOrNull() ?: 0.0 else axis.high
    val from = axis.write(start ?: first)
    val to = axis.write(end ?: last)
    return if (from == to) from else strings.valueRange(from, to)
}

private fun axisSentence(axis: ResolvedAxis, chart: ResolvedChart, unnamed: String, strings: ChartStrings): String {
    val name = axis.title ?: unnamed
    val isPlaceAxis = axis === chart.placeAxis
    val (from, to) = when {
        axis.kind == AxisKind.Category -> axis.write(0.0) to axis.write(axis.ticks.lastOrNull() ?: 0.0)
        isPlaceAxis && chart.places.isNotEmpty() -> axis.write(chart.places.first()) to axis.write(chart.places.last())
        else -> axis.write(axis.low) to axis.write(axis.high)
    }
    return strings.axisRange(name, from, to)
}

/** What a chart of these marks is called: by the one kind of mark it has, or a chart, for a mix. */
internal fun kindOf(model: ChartModel, strings: ChartStrings): String {
    // A band is an area between two edges, and named as one.
    val kinds = model.marks.map { if (it.kind == MarkKind.Band) MarkKind.Area else it.kind }.toSet()
    return when {
        kinds == setOf(MarkKind.Line) -> strings.lineChart
        kinds == setOf(MarkKind.Area) -> strings.areaChart
        kinds == setOf(MarkKind.Bar) -> strings.barChart
        kinds == setOf(MarkKind.Point) -> strings.scatterChart
        kinds == setOf(MarkKind.Box) -> strings.boxPlot
        kinds == setOf(MarkKind.Cell) -> strings.heatMap
        else -> strings.chart
    }
}

/** One place, or a range of places, along a chart, and what a screen reader hears of it. */
internal class PlaceDescription(val places: DoubleArray, val text: String)

/**
 * A sentence for each place along the chart — "March: Bus, 42 and Train, 17" —
 * or past [ChartDefaults.MaxAccessiblePlaces] of them, for each run of places,
 * with the least and the most of each series in it.
 */
internal fun placeDescriptions(chart: ResolvedChart, strings: ChartStrings, formats: LocaleFormats): List<PlaceDescription> {
    val placeAxis = chart.placeAxis
    val valueAxis = chart.valueAxis
    return groupPlaces(chart.places, ChartDefaults.MaxAccessiblePlaces).map { group ->
        val label = if (group.size == 1) {
            placeAxis.write(group[0])
        } else {
            strings.valueRange(placeAxis.write(group.first()), placeAxis.write(group.last()))
        }
        val said = chart.layers.flatMap { layer ->
            val places = if (chart.horizontal && layer.mark.kind == MarkKind.Bar) layer.up else layer.across
            val indices = group.flatMap { place ->
                places.indices.filter { places[it] == place && !layer.values[it].isNaN() && !stemSilentAt(chart, layer, it) }
            }
            if (indices.isEmpty()) return@flatMap emptyList()
            when {
                // A heat map's column, cell by cell, each by its row: "6am, 12".
                layer.mark.kind == MarkKind.Cell -> {
                    val scale = chart.cells
                    indices.map { i -> strings.seriesValue(chart.y.write(layer.up[i]), scale?.write?.invoke(layer.values[i]) ?: valueAxis.write(layer.values[i])) }
                }
                // One box by its numbers, or several, grouped, from the least
                // whisker's end to the most.
                layer.box != null && indices.size == 1 -> {
                    val amount = boxSaid(layer, indices[0], valueAxis.write, strings, formats)
                    listOf(if (layer.series.name.isBlank()) amount else strings.seriesValue(layer.series.name, amount))
                }
                else -> {
                    val box = layer.box
                    val least = if (box != null) indices.minOf { box.low[it] } else indices.minOf { layer.values[it] }
                    // A range's place is said from its foot to its top.
                    val most = when {
                        box != null -> indices.maxOf { box.high[it] }
                        layer.spans -> indices.maxOf { layer.endAt(it) ?: layer.values[it] }
                        else -> indices.maxOf { layer.values[it] }
                    }
                    val amount = if (least == most) valueAxis.write(least) else strings.valueRange(valueAxis.write(least), valueAxis.write(most))
                    listOf(if (layer.series.name.isBlank()) amount else strings.seriesValue(layer.series.name, amount))
                }
            }
        }
        PlaceDescription(group, strings.atPosition(label, formats.list(said, ListStyle.And)))
    }
}

/**
 * A box at [i] in a phrase: its median, its quartiles, its whiskers' ends,
 * and its outliers if it has any.
 */
internal fun boxSaid(layer: Layer, i: Int, write: (Double) -> String, strings: ChartStrings, formats: LocaleFormats): String {
    val box = layer.box ?: return write(layer.values[i])
    val base = layer.base ?: layer.values
    val summary = strings.boxSummary(write(box.low[i]), write(base[i]), write(layer.values[i]), write(layer.up[i]), write(box.high[i]))
    val outliers = box.outliers[i]
    if (outliers.isEmpty()) return summary
    return formats.list(listOf(summary, strings.boxOutliers(formats.list(outliers.map(write), ListStyle.And))), ListStyle.Unit)
}

/**
 * Whether [layer]'s value at [i] is a stem that says nothing new: both its ends
 * are values other marks at its place already say, or zero. A dumbbell's two
 * points say its low and its high, and a lollipop's point its top, so the
 * stem between them is said by nobody; an error bar over a bar is said.
 */
internal fun stemSilentAt(chart: ResolvedChart, layer: Layer, i: Int): Boolean {
    if (layer.mark.kind != MarkKind.Stem) return false
    val place = layer.across[i]
    val said = HashSet<Double>().apply { add(0.0) }
    chart.layers.forEach { other ->
        if (other === layer) return@forEach
        for (j in 0 until other.size) if (other.across[j] == place) said += other.values[j]
    }
    return layer.values[i] in said && (layer.endAt(i) ?: layer.values[i]) in said
}

/** Whether every value of this layer is a [silent stem][stemSilentAt], said by nothing of its own. */
internal fun Layer.isSilent(chart: ResolvedChart): Boolean =
    mark.kind == MarkKind.Stem && (0 until size).all { stemSilentAt(chart, this, it) }
