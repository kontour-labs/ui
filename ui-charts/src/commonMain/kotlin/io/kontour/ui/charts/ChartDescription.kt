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

    val named = chart.model.series.filter { it.name.isNotBlank() }
    if (named.size > 1) parts += strings.seriesCount(formats.quantity(named.size))

    parts += axisSentence(chart.x, chart, strings.horizontalAxis, strings)
    parts += axisSentence(chart.y, chart, strings.verticalAxis, strings)

    val valueAxis = chart.valueAxis
    chart.model.series.forEach { series ->
        val values = chart.layers.filter { it.series == series }.flatMap { layer -> layer.values.filter { !it.isNaN() } }
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
    chart.rules.mapNotNull { it.label }.forEach { parts += it }
    return sentences(parts)
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
    val kinds = model.marks.map { it.kind }.toSet()
    return when {
        kinds == setOf(MarkKind.Line) -> strings.lineChart
        kinds == setOf(MarkKind.Area) -> strings.areaChart
        kinds == setOf(MarkKind.Bar) -> strings.barChart
        kinds == setOf(MarkKind.Point) -> strings.scatterChart
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
        val said = chart.layers.mapNotNull { layer ->
            val places = if (chart.horizontal && layer.mark.kind == MarkKind.Bar) layer.up else layer.across
            val values = group.flatMap { place ->
                places.indices.filter { places[it] == place }.map { layer.values[it] }.filter { !it.isNaN() }
            }
            if (values.isEmpty()) return@mapNotNull null
            val least = values.min()
            val most = values.max()
            val amount = if (least == most) valueAxis.write(least) else strings.valueRange(valueAxis.write(least), valueAxis.write(most))
            if (layer.series.name.isBlank()) amount else strings.seriesValue(layer.series.name, amount)
        }
        PlaceDescription(group, strings.atPosition(label, formats.list(said, ListStyle.And)))
    }
}
