package io.kontour.ui.charts

/**
 * What a chart's content recorded, in the chart's own units: a number is
 * itself, a date its epoch day, and a category its index along its axis.
 *
 * Built once per change of the content by [ChartRecorder], and never by a
 * frame. Everything a frame does — scaling, animating, hit-testing — starts
 * from this.
 */
internal class ChartModel(
    val marks: List<Mark>,
    val rules: List<Rule>,
    val regions: List<Region>,
    /** Every series, in the order it first appears: a series' slot is its index here. */
    val series: List<ChartSeries>,
    /** The values of a category axis across, by index; empty for any other kind. */
    val xCategories: List<Any>,
    /** The values of a category axis up, by index; empty for any other kind. */
    val yCategories: List<Any>,
) {
    val isEmpty: Boolean get() = marks.all { mark -> mark.runs.all { it.size == 0 } }

    /** The slot of [series]: where its colour and patterns come from. */
    fun slotOf(series: ChartSeries): Int = this.series.indexOf(series).coerceAtLeast(0)
}

internal enum class MarkKind { Line, Area, Bar, Point, Rectangle, Band }

/**
 * One mark: one call in the content. Its items are split into one run per
 * series.
 */
internal class Mark(
    val kind: MarkKind,
    val runs: List<Run>,
    val stacking: ChartStacking,
    val interpolation: ChartInterpolation,
    val showPoints: Boolean,
    val symbol: PointSymbol?,
    val style: SeriesStyle?,
)

/**
 * One series' values within one mark, in the chart's own units.
 *
 * [ys] holds `NaN` where an item's value was `null`. A line's and an area's
 * runs are sorted by [xs]; the rest keep the items' order.
 */
internal class Run(
    val series: ChartSeries,
    val xs: DoubleArray,
    val ys: DoubleArray,
    /** A rectangle's other corner across; `null` for every other mark. */
    val xEnds: DoubleArray?,
    /** A rectangle's other corner up, or a band's top; `null` for every other mark. */
    val yEnds: DoubleArray?,
) {
    val size: Int get() = xs.size
}

/** A [ChartScope.rule], in the chart's own units. */
internal class Rule(
    val x: Double?,
    val y: Double?,
    val label: String?,
    val style: SeriesStyle?,
)

/**
 * A [ChartScope.region], in the chart's own units: `null` on an axis it spans
 * the whole of.
 */
internal class Region(
    val xStart: Double?,
    val xEnd: Double?,
    val yStart: Double?,
    val yEnd: Double?,
    val label: String?,
    val style: SeriesStyle?,
)

/**
 * Records a chart's content into a [ChartModel].
 *
 * A category is given its index here, in the order of the axis's own list if
 * it has one and in the order of first appearance if not, so that two marks on
 * one category axis agree on where "March" is.
 */
internal class ChartRecorder<X : Any, Y : Any>(
    private val xAxis: ChartAxis<X>,
    private val yAxis: ChartAxis<Y>,
) : ChartScope<X, Y> {

    private val marks = mutableListOf<Mark>()
    private val rules = mutableListOf<Rule>()
    private val regions = mutableListOf<Region>()
    private val series = LinkedHashSet<ChartSeries>()
    private val xCategories = CategoryIndex(xAxis.kind == AxisKind.Category, xAxis.categories)
    private val yCategories = CategoryIndex(yAxis.kind == AxisKind.Category, yAxis.categories)

    fun build(): ChartModel = ChartModel(
        marks = marks.toList(),
        rules = rules.toList(),
        regions = regions.toList(),
        series = series.toList(),
        xCategories = xCategories.values(),
        yCategories = yCategories.values(),
    )

    private fun xOf(value: X): Double =
        if (xAxis.kind == AxisKind.Category) xCategories.indexOf(value) else xAxis.encode(value)

    private fun yOf(value: Y?): Double = when {
        value == null -> Double.NaN
        yAxis.kind == AxisKind.Category -> yCategories.indexOf(value)
        else -> yAxis.encode(value)
    }

    private fun <T> runsOf(
        items: List<T>,
        x: (T) -> X,
        y: (T) -> Y?,
        seriesOf: (T) -> ChartSeries,
        sorted: Boolean,
    ): List<Run> {
        val bySeries = LinkedHashMap<ChartSeries, MutableList<Pair<Double, Double>>>()
        items.forEach { item ->
            val across = xOf(x(item))
            if (across.isNaN()) return@forEach
            val up = yOf(y(item))
            val owner = seriesOf(item)
            series += owner
            bySeries.getOrPut(owner) { mutableListOf() } += across to up
        }
        return bySeries.map { (owner, points) ->
            val ordered = if (sorted) points.sortedBy { it.first } else points
            Run(
                series = owner,
                xs = DoubleArray(ordered.size) { ordered[it].first },
                ys = DoubleArray(ordered.size) { ordered[it].second },
                xEnds = null,
                yEnds = null,
            )
        }
    }

    override fun <T> line(
        items: List<T>,
        x: (T) -> X,
        y: (T) -> Y?,
        series: (T) -> ChartSeries,
        interpolation: ChartInterpolation,
        showPoints: Boolean,
        style: SeriesStyle?,
    ) {
        marks += Mark(MarkKind.Line, runsOf(items, x, y, series, sorted = true), ChartStacking.None, interpolation, showPoints, null, style)
    }

    override fun <T> area(
        items: List<T>,
        x: (T) -> X,
        y: (T) -> Y?,
        series: (T) -> ChartSeries,
        stacking: ChartStacking,
        interpolation: ChartInterpolation,
        style: SeriesStyle?,
    ) {
        marks += Mark(MarkKind.Area, runsOf(items, x, y, series, sorted = true), stacking, interpolation, false, null, style)
    }

    override fun <T> bar(
        items: List<T>,
        x: (T) -> X,
        y: (T) -> Y?,
        series: (T) -> ChartSeries,
        stacking: ChartStacking,
        style: SeriesStyle?,
    ) {
        marks += Mark(MarkKind.Bar, runsOf(items, x, y, series, sorted = false), stacking, ChartInterpolation.Linear, false, null, style)
    }

    override fun <T> point(
        items: List<T>,
        x: (T) -> X,
        y: (T) -> Y?,
        series: (T) -> ChartSeries,
        symbol: PointSymbol?,
        style: SeriesStyle?,
    ) {
        marks += Mark(MarkKind.Point, runsOf(items, x, y, series, sorted = false), ChartStacking.None, ChartInterpolation.Linear, true, symbol, style)
    }

    override fun <T> rectangle(
        items: List<T>,
        xStart: (T) -> X,
        xEnd: (T) -> X,
        yStart: (T) -> Y,
        yEnd: (T) -> Y,
        series: (T) -> ChartSeries,
        style: SeriesStyle?,
    ) {
        val bySeries = LinkedHashMap<ChartSeries, MutableList<DoubleArray>>()
        items.forEach { item ->
            val corners = doubleArrayOf(xOf(xStart(item)), xOf(xEnd(item)), yOf(yStart(item)), yOf(yEnd(item)))
            if (corners.any { it.isNaN() }) return@forEach
            val owner = series(item)
            this.series += owner
            bySeries.getOrPut(owner) { mutableListOf() } += corners
        }
        val runs = bySeries.map { (owner, cells) ->
            Run(
                series = owner,
                xs = DoubleArray(cells.size) { cells[it][0] },
                ys = DoubleArray(cells.size) { cells[it][2] },
                xEnds = DoubleArray(cells.size) { cells[it][1] },
                yEnds = DoubleArray(cells.size) { cells[it][3] },
            )
        }
        marks += Mark(MarkKind.Rectangle, runs, ChartStacking.None, ChartInterpolation.Linear, false, null, style)
    }

    override fun <T> band(
        items: List<T>,
        x: (T) -> X,
        yStart: (T) -> Y?,
        yEnd: (T) -> Y?,
        series: (T) -> ChartSeries,
        interpolation: ChartInterpolation,
        style: SeriesStyle?,
    ) {
        val bySeries = LinkedHashMap<ChartSeries, MutableList<DoubleArray>>()
        items.forEach { item ->
            val across = xOf(x(item))
            if (across.isNaN()) return@forEach
            val low = yOf(yStart(item))
            val high = yOf(yEnd(item))
            val owner = series(item)
            this.series += owner
            // Either end missing is a gap, as a line's missing value is; ends
            // given the wrong way round are the same band.
            val gap = low.isNaN() || high.isNaN()
            bySeries.getOrPut(owner) { mutableListOf() } += doubleArrayOf(
                across,
                if (gap) Double.NaN else minOf(low, high),
                if (gap) Double.NaN else maxOf(low, high),
            )
        }
        val runs = bySeries.map { (owner, points) ->
            val ordered = points.sortedBy { it[0] }
            Run(
                series = owner,
                xs = DoubleArray(ordered.size) { ordered[it][0] },
                ys = DoubleArray(ordered.size) { ordered[it][1] },
                xEnds = null,
                yEnds = DoubleArray(ordered.size) { ordered[it][2] },
            )
        }
        marks += Mark(MarkKind.Band, runs, ChartStacking.None, interpolation, false, null, style)
    }

    override fun region(xStart: X?, xEnd: X?, yStart: Y?, yEnd: Y?, label: String?, style: SeriesStyle?) {
        fun across(value: X?) = value?.let { xOf(it) }?.takeUnless { it.isNaN() }
        fun up(value: Y?) = value?.let { yOf(it) }?.takeUnless { it.isNaN() }
        regions += Region(across(xStart), across(xEnd), up(yStart), up(yEnd), label, style)
    }

    override fun rule(x: X?, y: Y?, label: String?, style: SeriesStyle?) {
        rules += Rule(
            x = x?.let { xOf(it) }?.takeUnless { it.isNaN() },
            y = y?.let { yOf(it) }?.takeUnless { it.isNaN() },
            label = label,
            style = style,
        )
    }
}

/**
 * Where each category goes along its axis: its index in the axis's own list, or
 * the order of first appearance where there is none.
 */
internal class CategoryIndex(private val active: Boolean, fixed: List<Any>?) {
    private val fixedOrder = fixed != null
    private val indices = LinkedHashMap<Any, Int>().apply {
        fixed?.forEachIndexed { index, value -> put(value, index) }
    }

    /** [value]'s index, `NaN` for a value the axis's own list leaves out. */
    fun indexOf(value: Any): Double {
        check(active) { "a category index for an axis that is not a category axis" }
        indices[value]?.let { return it.toDouble() }
        if (fixedOrder) return Double.NaN
        val next = indices.size
        indices[value] = next
        return next.toDouble()
    }

    fun values(): List<Any> = if (active) indices.keys.toList() else emptyList()
}
