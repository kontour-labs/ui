package io.kontour.ui.charts

import io.kontour.ui.locale.DateStyle
import io.kontour.ui.locale.LocaleFormats
import io.kontour.ui.locale.NameWidth
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.time.Instant

/**
 * A chart ready to lay out: its axes' domains, ticks and labels, and every mark
 * stacked, still in the chart's own units so that it can be interpolated from
 * one set of data to the next.
 *
 * @property horizontal Whether bars lie along the axis across — a category axis
 *   up and a number across — rather than standing.
 */
internal class ResolvedChart(
    val model: ChartModel,
    val horizontal: Boolean,
    val x: ResolvedAxis,
    val y: ResolvedAxis,
    val layers: List<Layer>,
    val rules: List<Rule>,
    /** The regions on the axes, cut to them. */
    val regions: List<Region>,
    /** Every place a reader can select or move to, along the bucket axis, in order. */
    val places: DoubleArray,
    /** The values a heat map's cells run between, for their shades; `null` for a chart with none. */
    val cells: CellScale? = null,
    /**
     * The axis up the end edge, for what [ChartScope.onEndAxis] recorded;
     * `null` with none, or nothing shown on it.
     */
    val yEnd: ResolvedAxis? = null,
) {
    /** The axis a value up is read on: the end axis for what is [on the end][Mark.onEnd], the start axis otherwise. */
    fun upAxis(onEnd: Boolean): ResolvedAxis = if (onEnd) yEnd ?: y else y

    /** The axis places run along: across, or up for lying bars. */
    val placeAxis: ResolvedAxis get() = if (horizontal) y else x

    /** The axis values are read off. */
    val valueAxis: ResolvedAxis get() = if (horizontal) x else y

    /**
     * Whether [other] draws the same: the same axes and the same values at the
     * same places. A chart recorded again for a recomposition that changed
     * nothing is a new object and the same chart, and is not animated to.
     */
    fun sameAs(other: ResolvedChart): Boolean =
        horizontal == other.horizontal &&
            x.low == other.x.low && x.high == other.x.high && y.low == other.y.low && y.high == other.y.high &&
            yEnd?.low == other.yEnd?.low && yEnd?.high == other.yEnd?.high &&
            places.contentEquals(other.places) &&
            layers.size == other.layers.size &&
            layers.zip(other.layers).all { (a, b) ->
                a.series == b.series && a.mark.kind == b.mark.kind &&
                    a.across.contentEquals(b.across) && a.up.contentEquals(b.up) &&
                    (a.base?.contentEquals(b.base) ?: (b.base == null)) &&
                    // A heat map whose shades change and whose cells stay put,
                    // or a box whose whiskers move, is a new chart too.
                    a.values.contentEquals(b.values) &&
                    (a.box?.let { box -> b.box != null && box.low.contentEquals(b.box.low) && box.high.contentEquals(b.box.high) } ?: (b.box == null))
            }

    /** A copy with these axes' spans and these layers, for a frame between two charts. */
    fun with(
        x: ResolvedAxis,
        y: ResolvedAxis,
        layers: List<Layer>,
        yEnd: ResolvedAxis? = this.yEnd,
        rules: List<Rule> = this.rules,
        regions: List<Region> = this.regions,
    ): ResolvedChart = ResolvedChart(model, horizontal, x, y, layers, rules, regions, places, cells, yEnd)
}

/**
 * One axis, resolved.
 *
 * A category axis runs from -0.5 to the last index plus 0.5, so that each
 * category is the middle of a band one unit wide.
 */
internal class ResolvedAxis(
    val kind: AxisKind,
    val low: Double,
    val high: Double,
    val ticks: DoubleArray,
    val tickLabels: List<String>,
    val title: String?,
    val showGridlines: Boolean,
    val showLabels: Boolean,
    val position: ChartAxisPosition,
    /** Any value on this axis as a reader reads it: in the tooltip, and to a screen reader. */
    val write: (Double) -> String,
    /** Whether a stream is stacked about its middle, where a position up it measures nothing: no ticks, no baseline. */
    val centred: Boolean = false,
) {
    val span: Double get() = (high - low).takeIf { it > 0 } ?: 1.0

    fun spanning(low: Double, high: Double): ResolvedAxis =
        ResolvedAxis(kind, low, high, ticks, tickLabels, title, showGridlines, showLabels, position, write, centred)
}

/**
 * One series of one mark, stacked and ready to draw.
 *
 * [across] and [up] are where each point is, in the units of the axis across
 * and the axis up. For a bar or an area, [base] is where it starts along the
 * value axis, and [values] is each item's own value, whatever it was stacked
 * on, which is what a reader is told.
 */
internal class Layer(
    val markIndex: Int,
    val mark: Mark,
    val series: ChartSeries,
    val slot: Int,
    val across: DoubleArray,
    val up: DoubleArray,
    val base: DoubleArray?,
    val acrossEnd: DoubleArray?,
    val upEnd: DoubleArray?,
    val values: DoubleArray,
    /** Which of [groupCount] bars side by side at one place this is. */
    val groupIndex: Int,
    val groupCount: Int,
    /** A box plot's whiskers, median and outliers; its box is [base] to [up]. */
    val box: BoxRun? = null,
    /** How much of it is drawn: less than whole only while a hidden series leaves. See [interpolate]. */
    val opacity: Float = 1f,
) {
    val size: Int get() = across.size

    /** Whether each value is a range, from [values] to [upEnd]: a band, a floating bar, a stem, a box. */
    val spans: Boolean get() = mark.kind == MarkKind.Band || mark.kind == MarkKind.Stem || mark.floating

    /** Where the value at [i] runs to, for a [range][spans]; `null` for a value. */
    fun endAt(i: Int): Double? = if (spans) upEnd?.getOrNull(i)?.takeUnless { it.isNaN() } else null

    fun copy(
        across: DoubleArray = this.across,
        up: DoubleArray = this.up,
        base: DoubleArray? = this.base,
        values: DoubleArray = this.values,
        box: BoxRun? = this.box,
        opacity: Float = this.opacity,
    ): Layer = Layer(markIndex, mark, series, slot, across, up, base, acrossEnd, upEnd, values, groupIndex, groupCount, box, opacity)
}

/**
 * Resolves [model] against its axes, writing labels with [formats].
 */
internal fun <X : Any, Y : Any> resolve(
    model: ChartModel,
    xAxis: ChartAxis<X>,
    yAxis: ChartAxis<Y>,
    formats: LocaleFormats,
    endYAxis: ChartAxis<Y>? = null,
): ResolvedChart {
    val horizontal = yAxis.kind == AxisKind.Category && xAxis.kind != AxisKind.Category

    val layers = mutableListOf<Layer>()
    model.marks.forEachIndexed { markIndex, mark ->
        layers += layersOf(markIndex, mark, model, horizontal)
    }

    // On a normalised chart the value axis is shares of each place's whole, so
    // only the normalised stacks measure it. A rule, a region, a band or a line
    // beside them is read as a share too, and stretched the axis to its own
    // units instead: a "Capacity" rule at 700 riders made the axis run to
    // 70,000% and pressed the bars flat against its foot.
    //
    // Only on the start axis: what is on the end axis is in its own units,
    // and is measured on its own scale whatever the start axis is in.
    val normalised = layers.any { !it.mark.onEnd && it.mark.stacking == ChartStacking.Normalised }
    fun measuresValues(layer: Layer) = layer.mark.onEnd || !normalised || layer.mark.stacking == ChartStacking.Normalised

    // The data's extent along each axis, with zero wherever a bar or an area is
    // measured from it; what is on the end axis, up its own.
    val xs = Extent()
    val ys = Extent()
    val endYs = Extent()
    layers.forEach { layer ->
        val values = measuresValues(layer)
        val up = if (layer.mark.onEnd) endYs else ys
        for (i in 0 until layer.size) {
            if (values || !horizontal) xs += layer.across[i]
            if (values || horizontal) up += layer.up[i]
            if (values || !horizontal) layer.acrossEnd?.let { xs += it[i] }
            if (values || horizontal) layer.upEnd?.let { up += it[i] }
            if (values) layer.base?.let { base -> if (horizontal) xs += base[i] else up += base[i] }
            // A box reaches past itself, to its whiskers and its outliers.
            if (values) layer.box?.let { box ->
                val reach = if (horizontal) xs else up
                reach += box.low[i]
                reach += box.high[i]
                box.outliers[i].forEach { reach += it }
            }
        }
    }
    model.rules.forEach { rule ->
        val shares = normalised && !rule.onEnd
        if (!shares || !horizontal) rule.x?.let { xs += it }
        if (!shares || horizontal) rule.y?.let { if (rule.onEnd) endYs += it else ys += it }
    }
    // A region widens the axes to take it in, as a rule does: a forecast's
    // shading after today runs on past the last day of data.
    model.regions.forEach { region ->
        val shares = normalised && !region.onEnd
        val up = if (region.onEnd) endYs else ys
        if (!shares || !horizontal) {
            region.xStart?.let { xs += it }
            region.xEnd?.let { xs += it }
        }
        if (!shares || horizontal) {
            region.yStart?.let { up += it }
            region.yEnd?.let { up += it }
        }
    }
    // A floating bar is measured from its start, not from zero.
    fun Layer.fromZero() = (mark.kind == MarkKind.Bar && !mark.floating) || mark.kind == MarkKind.Area
    val measured = layers.any { !it.mark.onEnd && it.fromZero() }
    val centred = layers.any { !it.mark.onEnd && it.mark.kind == MarkKind.Area && it.mark.stacking == ChartStacking.Centred }
    if (measured) {
        if (horizontal) xs += 0.0 else ys += 0.0
    }
    if (layers.any { it.mark.onEnd && it.fromZero() }) endYs += 0.0

    // Bars across a number or date axis stand on their value, so half a bar's
    // width either side of the first and the last is part of the domain.
    val standing = setOf(MarkKind.Bar, MarkKind.Box, MarkKind.Cell)
    val barsAcrossContinuous = !horizontal && xAxis.kind != AxisKind.Category &&
        layers.any { it.mark.kind in standing }
    val barPad = if (barsAcrossContinuous) smallestGap(layers.filter { it.mark.kind in standing }) / 2 else 0.0

    val x = resolveAxis(xAxis, xs, model.xCategories, formats, padding = barPad, percent = normalised && horizontal)
    val y = resolveAxis(yAxis, ys, model.yCategories, formats, padding = 0.0, percent = normalised && !horizontal, centred = centred && !horizontal)

    // The end axis, only while something is on it: a series hidden from the
    // legend takes its axis with it, and the plot widens into its gutter. Its
    // ticks are the start axis's in number, where both count and the start's
    // run from its low to its high, so its labels sit on the start's
    // gridlines rather than beside nothing.
    val onEnd = model.marks.any { it.onEnd && it.runs.isNotEmpty() } || model.rules.any { it.onEnd } || model.regions.any { it.onEnd }
    val yEnd = if (endYAxis != null && onEnd && !horizontal) {
        val intervals = y.ticks.size - 1
        val aligned = y.kind == AxisKind.Number && endYAxis.kind == AxisKind.Number && endYAxis.domain == null &&
            !y.centred && intervals >= 1 &&
            abs(y.ticks.first() - y.low) <= y.span * 1e-9 && abs(y.ticks.last() - y.high) <= y.span * 1e-9
        resolveAxis(endYAxis, endYs, emptyList(), formats, padding = 0.0, percent = false, intervals = if (aligned) intervals else null)
    } else {
        null
    }

    val places = when {
        horizontal -> DoubleArray(model.yCategories.size) { it.toDouble() }
        xAxis.kind == AxisKind.Category -> DoubleArray(model.xCategories.size) { it.toDouble() }
        else -> layers.flatMap { layer -> layer.across.filter { !it.isNaN() } }.distinct().sorted().toDoubleArray()
    }

    return ResolvedChart(
        model = model,
        horizontal = horizontal,
        x = x,
        y = y,
        layers = layers,
        rules = model.rules.filter { rule -> rule.x.within(x) && rule.y.within(if (rule.onEnd) yEnd ?: y else y) },
        regions = model.regions.mapNotNull { it.clampedTo(x, if (it.onEnd) yEnd ?: y else y) },
        places = places,
        cells = cellScaleOf(layers, formats),
        yEnd = yEnd,
    )
}

/**
 * What a heat map's shades run between: the least and the most of its cells,
 * and how one is written, to as many places as the span between them needs.
 */
internal class CellScale(val low: Double, val high: Double, val write: (Double) -> String) {
    /** How far along the ramp [value] is, from 0 at [low] to 1 at [high]. */
    fun fraction(value: Double): Float =
        if (high <= low) 1f else ((value - low) / (high - low)).toFloat().coerceIn(0f, 1f)
}

private fun cellScaleOf(layers: List<Layer>, formats: LocaleFormats): CellScale? {
    val amounts = layers.filter { it.mark.kind == MarkKind.Cell }.flatMap { layer -> layer.values.filter { !it.isNaN() } }
    if (amounts.isEmpty()) return null
    val low = amounts.min()
    val high = amounts.max()
    val digits = niceTicks(low, high, ChartAxisDefaults.TickCount).fractionDigits
    return CellScale(low, high) { value -> formats.decimal(value, precisionOf(value, digits)) }
}

/**
 * Whether a reference at this position is on [axis]: anywhere along it, or not
 * on it at all. One past the end is not drawn or said, as with a share above
 * 100% on a normalised chart, or a value past a domain the caller fixed.
 */
private fun Double?.within(axis: ResolvedAxis): Boolean =
    this == null || axis.kind == AxisKind.Category ||
        (this >= axis.low - AxisSlack * (axis.high - axis.low) && this <= axis.high + AxisSlack * (axis.high - axis.low))

/** This region cut to the axes, or `null` if none of it is on them. */
private fun Region.clampedTo(x: ResolvedAxis, y: ResolvedAxis): Region? {
    fun clamp(start: Double?, end: Double?, axis: ResolvedAxis): Pair<Double?, Double?>? {
        if (axis.kind == AxisKind.Category) return start to end
        val from = start ?: axis.low
        val to = end ?: axis.high
        if (max(from, to) < axis.low || min(from, to) > axis.high) return null
        return start?.coerceIn(axis.low, axis.high) to end?.coerceIn(axis.low, axis.high)
    }
    val (xStart, xEnd) = clamp(this.xStart, this.xEnd, x) ?: return null
    val (yStart, yEnd) = clamp(this.yStart, this.yEnd, y) ?: return null
    return if (xStart == this.xStart && xEnd == this.xEnd && yStart == this.yStart && yEnd == this.yEnd) {
        this
    } else {
        Region(xStart, xEnd, yStart, yEnd, label, style, onEnd)
    }
}

/** How far past an axis's end a reference may sit and still be on it: rounding, not data. */
private const val AxisSlack = 1e-9

/** The layers of one mark, stacked as it says, a run each. */
private fun layersOf(markIndex: Int, mark: Mark, model: ChartModel, horizontal: Boolean): List<Layer> {
    val runs = mark.runs
    val lying = horizontal && mark.kind == MarkKind.Bar
    val stacks = (mark.kind == MarkKind.Bar || mark.kind == MarkKind.Area) &&
        (mark.stacking == ChartStacking.Stacked || mark.stacking == ChartStacking.Normalised || mark.stacking == ChartStacking.Centred)
    val grouped = (mark.kind == MarkKind.Bar || mark.kind == MarkKind.Box) && !stacks && runs.size > 1
    val normalised = stacks && mark.stacking == ChartStacking.Normalised
    // A stream: each place's stack starts half its whole below the middle.
    // Bars take `Centred` as `Stacked`, as the enum says.
    val centred = mark.kind == MarkKind.Area && mark.stacking == ChartStacking.Centred

    // Place → running totals above and below zero, and the whole for a normalised stack.
    val above = HashMap<Double, Double>()
    val below = HashMap<Double, Double>()
    val whole = HashMap<Double, Double>()
    if (mark.stacking == ChartStacking.Normalised) {
        runs.forEach { run ->
            for (i in 0 until run.size) {
                val place = if (lying) run.ys[i] else run.xs[i]
                val value = if (lying) run.xs[i] else run.ys[i]
                if (!value.isNaN()) whole[place] = (whole[place] ?: 0.0) + abs(value)
            }
        }
        // One series is the whole of every place it stands at, so each bar came
        // out full and all of them alike. Its whole is the series' instead, and
        // each place reads as its share of all of them, as a sector's does.
        if (runs.size == 1) {
            val total = whole.values.sum()
            whole.keys.toList().forEach { whole[it] = total }
        }
    }

    if (centred) {
        // A negative value has no thickness in a stream: it is drawn as
        // nothing, and still said as itself.
        runs.forEach { run ->
            for (i in 0 until run.size) {
                val value = run.ys[i]
                if (!value.isNaN()) whole[run.xs[i]] = (whole[run.xs[i]] ?: 0.0) + max(value, 0.0)
            }
        }
        whole.forEach { (place, total) -> above[place] = -total / 2 }
    }

    if (mark.kind == MarkKind.Band || mark.kind == MarkKind.Stem || mark.floating || mark.kind == MarkKind.Box) {
        // A range: a band drawn as an area between its edges, a stem as a line
        // between its ends, a floating bar as a bar between them, and a box from
        // its lower quartile to its upper. Each has its foot as its base, so it
        // animates and rises as the others do. A band, a stem and a floating bar
        // are read as the range from foot to top; a box by its median.
        return runs.mapIndexed { runIndex, run ->
            val high = run.yEnds ?: DoubleArray(run.size) { run.ys[it] }
            val sideBySide = grouped
            Layer(
                markIndex = markIndex,
                mark = mark,
                series = run.series,
                slot = model.slotOf(run.series),
                across = run.xs.copyOf(),
                up = high.copyOf(),
                base = run.ys.copyOf(),
                acrossEnd = null,
                upEnd = high,
                values = run.box?.median?.copyOf() ?: run.ys.copyOf(),
                groupIndex = if (sideBySide) runIndex else 0,
                groupCount = if (sideBySide) runs.size else 1,
                box = run.box,
            )
        }
    }

    if (mark.kind == MarkKind.Cell) {
        // A cell is where it is, across and up, and its value is its shade.
        return runs.map { run ->
            Layer(
                markIndex = markIndex,
                mark = mark,
                series = run.series,
                slot = model.slotOf(run.series),
                across = run.xs.copyOf(),
                up = run.ys.copyOf(),
                base = null,
                acrossEnd = null,
                upEnd = null,
                values = run.amounts?.copyOf() ?: DoubleArray(run.size) { Double.NaN },
                groupIndex = 0,
                groupCount = 1,
            )
        }
    }
    return runs.mapIndexed { runIndex, run ->
        val n = run.size
        val raw = DoubleArray(n) { if (lying) run.xs[it] else run.ys[it] }
        // What is written and said: a normalised stack's axis reads in shares
        // of each place's whole, so its values are those shares too. They were
        // the raw values, written by the percent writer the axis uses, and a
        // value of 42 was said as "4,200%".
        val values = if (normalised) {
            DoubleArray(n) { i ->
                val place = if (lying) run.ys[i] else run.xs[i]
                val total = whole[place] ?: 0.0
                if (raw[i].isNaN()) Double.NaN else if (total > 0) raw[i] / total else 0.0
            }
        } else {
            raw
        }
        val measuredFrom = mark.kind == MarkKind.Bar || mark.kind == MarkKind.Area
        val base = if (measuredFrom) DoubleArray(n) else null
        val tops = values.copyOf()
        if (stacks) {
            for (i in 0 until n) {
                val place = if (lying) run.ys[i] else run.xs[i]
                // Already a share, for a normalised stack: see `values`.
                val value = if (centred) max(values[i], 0.0) else values[i]
                if (value.isNaN()) continue
                val totals = if (value >= 0) above else below
                val start = totals[place] ?: 0.0
                // A whole of shares that adds to a hair over one, as 0.1 + 0.2
                // + 0.7 does, is one: past it, the axis took another step and
                // topped out at 120% over bars that stop at 100.
                val top = if (normalised) snappedToWhole(start + value) else start + value
                base!![i] = start
                tops[i] = top
                totals[place] = top
            }
        }
        Layer(
            markIndex = markIndex,
            mark = mark,
            series = run.series,
            slot = model.slotOf(run.series),
            across = if (lying) tops else run.xs.copyOf(),
            up = if (lying) run.ys.copyOf() else tops,
            base = base,
            acrossEnd = run.xEnds,
            upEnd = run.yEnds,
            values = values,
            groupIndex = if (grouped) runIndex else 0,
            groupCount = if (grouped) runs.size else 1,
        )
    }
}

/** [share], or exactly one whole either way when it is within rounding of it. */
private fun snappedToWhole(share: Double): Double = when {
    abs(share - 1.0) < WholeSlack -> 1.0
    abs(share + 1.0) < WholeSlack -> -1.0
    else -> share
}

/** How near a stack of shares must come to one to be one: rounding, not data. */
private const val WholeSlack = 1e-9

/** The smallest step between neighbouring places of [layers], or 1 if there is none. */
private fun smallestGap(layers: List<Layer>): Double {
    val places = layers.flatMap { layer -> layer.across.filter { !it.isNaN() } }.distinct().sorted()
    var gap = Double.MAX_VALUE
    for (i in 1 until places.size) gap = min(gap, places[i] - places[i - 1])
    return if (gap == Double.MAX_VALUE || gap <= 0) 1.0 else gap
}

private fun <V : Any> resolveAxis(
    axis: ChartAxis<V>,
    extent: Extent,
    categories: List<Any>,
    formats: LocaleFormats,
    padding: Double,
    percent: Boolean,
    centred: Boolean = false,
    /** For an end axis: exactly this many steps between ticks, to sit on the start axis's. See [alignedTicks]. */
    intervals: Int? = null,
): ResolvedAxis = when (axis.kind) {
    AxisKind.Category -> {
        @Suppress("UNCHECKED_CAST")
        val write: (Double) -> String = { position ->
            val index = position.roundToInt()
            val value = categories.getOrNull(index)
            when {
                value == null -> ""
                axis.categoryLabel != null -> axis.categoryLabel.invoke(value as V)
                value is String -> value
                else -> value.toString()
            }
        }
        val ticks = DoubleArray(categories.size) { it.toDouble() }
        ResolvedAxis(
            kind = AxisKind.Category,
            low = -0.5,
            high = max(categories.size, 1) - 0.5,
            ticks = ticks,
            tickLabels = ticks.map(write),
            title = axis.title,
            showGridlines = axis.showGridlines,
            showLabels = axis.showLabels,
            position = axis.position,
            write = write,
        )
    }

    AxisKind.Number -> if (centred && axis.domain == null) centredAxis(axis, extent, formats) else {
        var low = axis.domain?.start ?: extent.low
        var high = axis.domain?.endInclusive ?: extent.high
        if (axis.domain == null && axis.includeZero) {
            low = min(low, 0.0)
            high = max(high, 0.0)
        }
        if (percent && axis.domain == null) {
            low = min(low, 0.0)
            high = max(high, 1.0)
        }
        low -= padding
        high += padding
        val ticks = if (intervals != null) alignedTicks(low, high, intervals) else niceTicks(low, high, axis.tickCount)
        val domainLow = if (axis.domain != null || padding > 0) low else ticks.low
        val domainHigh = if (axis.domain != null || padding > 0) high else ticks.high
        val shown = ticks.values.filter { it >= domainLow - 1e-9 && it <= domainHigh + 1e-9 }.toDoubleArray()
        val label = axis.numberLabel
        val tickLabel: (Double) -> String = when {
            label != null -> label
            percent -> { value -> formats.percent(value, max(0, ticks.fractionDigits - 2)) }
            else -> { value -> formats.decimal(value, ticks.fractionDigits) }
        }
        val write: (Double) -> String = when {
            label != null -> label
            // As many digits as the share needs, and one at most: 80% is "80%"
            // whatever the ticks are stepped in, and a third is "33.3%".
            percent -> { value -> formats.percent(value, min(precisionOf(value * 100, 0), 1)) }
            else -> { value -> formats.decimal(value, precisionOf(value, ticks.fractionDigits)) }
        }
        ResolvedAxis(
            kind = AxisKind.Number,
            low = domainLow,
            high = domainHigh,
            ticks = shown,
            tickLabels = shown.map(tickLabel),
            title = axis.title,
            showGridlines = axis.showGridlines,
            showLabels = axis.showLabels,
            position = axis.position,
            write = write,
        )
    }

    AxisKind.Date -> {
        val low = (axis.domain?.start ?: extent.low) - padding
        val high = (axis.domain?.endInclusive ?: extent.high) + padding
        val ticks = dateTicks(low, high, axis.tickCount, formats.conventions.firstDayOfWeek)
        val domainLow = if (axis.domain != null) low else min(low, ticks.low)
        val domainHigh = if (axis.domain != null) high else max(high, ticks.high)
        val shown = ticks.values.filter { it >= domainLow - 1e-9 && it <= domainHigh + 1e-9 }.toDoubleArray()
        val label = axis.dateLabel
        val first = shown.firstOrNull()
        val tickLabel: (Double) -> String = { position ->
            val date = LocalDate.fromEpochDays(position.roundToLong())
            label?.invoke(date) ?: dateTickLabel(date, ticks.unit ?: DateUnit.Day, position == first, formats)
        }
        val write: (Double) -> String = { position ->
            val date = LocalDate.fromEpochDays(position.roundToLong())
            label?.invoke(date) ?: formats.date(date, DateStyle.Medium)
        }
        ResolvedAxis(
            kind = AxisKind.Date,
            low = domainLow,
            high = domainHigh,
            ticks = shown,
            tickLabels = shown.map(tickLabel),
            title = axis.title,
            showGridlines = axis.showGridlines,
            showLabels = axis.showLabels,
            position = axis.position,
            write = write,
        )
    }

    AxisKind.Instant -> {
        val zone = axis.timeZone ?: TimeZone.currentSystemDefault()
        val low = (axis.domain?.start ?: extent.low) - padding
        val high = (axis.domain?.endInclusive ?: extent.high) + padding
        val ticks = instantTicks(low, high, axis.tickCount, zone, formats.conventions.firstDayOfWeek)
        val domainLow = if (axis.domain != null) low else min(low, ticks.low)
        val domainHigh = if (axis.domain != null) high else max(high, ticks.high)
        val shown = ticks.values.filter { it >= domainLow - 1e-9 && it <= domainHigh + 1e-9 }.toDoubleArray()
        val label = axis.instantLabel
        fun local(position: Double): LocalDateTime =
            Instant.fromEpochMilliseconds(position.roundToLong()).toLocalDateTime(zone)
        val tickLabels = shown.mapIndexed { index, position ->
            val at = local(position)
            when {
                label != null -> label(Instant.fromEpochMilliseconds(position.roundToLong()))
                ticks.clock != null -> clockTickLabel(at, shown.getOrNull(index - 1)?.let(::local), formats)
                else -> dateTickLabel(at.date, ticks.unit ?: DateUnit.Day, index == 0, formats)
            }
        }
        val write: (Double) -> String = { position ->
            label?.invoke(Instant.fromEpochMilliseconds(position.roundToLong()))
                ?: local(position).let { formats.dateTime(it.date, it.time) }
        }
        ResolvedAxis(
            kind = AxisKind.Instant,
            low = domainLow,
            high = domainHigh,
            ticks = shown,
            tickLabels = tickLabels,
            title = axis.title,
            showGridlines = axis.showGridlines,
            showLabels = axis.showLabels,
            position = axis.position,
            write = write,
        )
    }
}

/**
 * A tick of an instant axis within a day: its time on the zone's clock, with its
 * date at the first tick and wherever the date changes from the tick before,
 * so a reader of a night's chart knows which side of midnight they are on.
 */
private fun clockTickLabel(at: LocalDateTime, before: LocalDateTime?, formats: LocaleFormats): String =
    if (before == null || before.date != at.date) formats.dateTime(at.date, at.time) else formats.time(at.time)

/**
 * A tick of a date axis, in the unit its ticks are in: a short date for days and
 * weeks, a month's short name for months — with its year at January and at the
 * first tick, so a reader knows which year they are in — and a year for years.
 */
private fun dateTickLabel(date: LocalDate, unit: DateUnit, first: Boolean, formats: LocaleFormats): String = when (unit) {
    DateUnit.Day, DateUnit.Week -> formats.date(date, DateStyle.Short)
    DateUnit.Month ->
        if (first || date.month == Month.JANUARY) formats.monthAndYear(date)
        else formats.monthName(date.month, NameWidth.Abbreviated)
    DateUnit.Year -> formats.digits(date.year.toLong())
}

/**
 * A stream's value axis: as far below its middle as above, so the middle is
 * the middle of the plot, and no ticks, since where a stream sits up it
 * measures nothing; only how thick each series is does. Its values are still
 * written, as plain numbers, for the tooltip and the table.
 */
private fun <V : Any> centredAxis(axis: ChartAxis<V>, extent: Extent, formats: LocaleFormats): ResolvedAxis {
    val reach = max(abs(extent.low.takeUnless { it.isNaN() } ?: 0.0), abs(extent.high.takeUnless { it.isNaN() } ?: 0.0))
        .takeIf { it > 0 } ?: 1.0
    val digits = niceTicks(0.0, reach, axis.tickCount).fractionDigits
    val label = axis.numberLabel
    return ResolvedAxis(
        kind = AxisKind.Number,
        low = -reach,
        high = reach,
        ticks = DoubleArray(0),
        tickLabels = emptyList(),
        title = axis.title,
        showGridlines = false,
        showLabels = false,
        position = axis.position,
        write = label ?: { value -> formats.decimal(value, precisionOf(value, digits)) },
        centred = true,
    )
}

/**
 * Digits enough after the point for [value], at least the ticks' [floor] and at
 * most two more: a reading of 41.25 on an axis ticked in whole numbers is said
 * as 41.25, and one of 41 as 41.
 */
private fun precisionOf(value: Double, floor: Int): Int {
    var digits = floor
    while (digits < floor + 2) {
        val scaled = value * pow10(digits)
        if (abs(scaled - scaled.roundToLong()) < 1e-6) break
        digits++
    }
    return digits
}

private fun pow10(digits: Int): Double {
    var result = 1.0
    repeat(digits) { result *= 10.0 }
    return result
}

/** The least and the most of a set of values, ignoring gaps. */
internal class Extent {
    var low = Double.NaN
        private set
    var high = Double.NaN
        private set

    operator fun plusAssign(value: Double) {
        if (value.isNaN() || value.isInfinite()) return
        low = if (low.isNaN()) value else min(low, value)
        high = if (high.isNaN()) value else max(high, value)
    }
}

/** Places merged into at most [limit] groups of neighbours, for what a screen reader moves through. */
internal fun groupPlaces(places: DoubleArray, limit: Int): List<DoubleArray> {
    if (places.size <= limit) return places.map { doubleArrayOf(it) }
    val size = ceil(places.size / limit.toDouble()).toInt()
    return places.toList().chunked(size).map { it.toDoubleArray() }
}

/** How a series went from its first value to its last: up, down, or neither, by more than a tenth of its range. */
internal fun trendOf(values: DoubleArray): Int {
    val present = values.filter { !it.isNaN() }
    if (present.size < 2) return 0
    val range = (present.max() - present.min()).takeIf { it > 0 } ?: return 0
    val change = present.last() - present.first()
    return when {
        change > range * 0.1 -> 1
        change < -range * 0.1 -> -1
        else -> 0
    }
}
