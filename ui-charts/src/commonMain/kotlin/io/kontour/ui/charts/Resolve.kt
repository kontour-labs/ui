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
) {
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
            places.contentEquals(other.places) &&
            layers.size == other.layers.size &&
            layers.zip(other.layers).all { (a, b) ->
                a.series == b.series && a.mark.kind == b.mark.kind &&
                    a.across.contentEquals(b.across) && a.up.contentEquals(b.up) &&
                    (a.base?.contentEquals(b.base) ?: (b.base == null))
            }

    /** A copy with these axes' spans and these layers, for a frame between two charts. */
    fun with(x: ResolvedAxis, y: ResolvedAxis, layers: List<Layer>): ResolvedChart =
        ResolvedChart(model, horizontal, x, y, layers, rules, regions, places)
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
) {
    val span: Double get() = (high - low).takeIf { it > 0 } ?: 1.0

    fun spanning(low: Double, high: Double): ResolvedAxis =
        ResolvedAxis(kind, low, high, ticks, tickLabels, title, showGridlines, showLabels, position, write)
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
) {
    val size: Int get() = across.size

    fun copy(across: DoubleArray = this.across, up: DoubleArray = this.up, base: DoubleArray? = this.base): Layer =
        Layer(markIndex, mark, series, slot, across, up, base, acrossEnd, upEnd, values, groupIndex, groupCount)
}

/**
 * Resolves [model] against its axes, writing labels with [formats].
 */
internal fun <X : Any, Y : Any> resolve(
    model: ChartModel,
    xAxis: ChartAxis<X>,
    yAxis: ChartAxis<Y>,
    formats: LocaleFormats,
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
    val normalised = layers.any { it.mark.stacking == ChartStacking.Normalised }
    fun measuresValues(layer: Layer) = !normalised || layer.mark.stacking == ChartStacking.Normalised

    // The data's extent along each axis, with zero wherever a bar or an area is
    // measured from it.
    val xs = Extent()
    val ys = Extent()
    layers.forEach { layer ->
        val values = measuresValues(layer)
        for (i in 0 until layer.size) {
            if (values || !horizontal) xs += layer.across[i]
            if (values || horizontal) ys += layer.up[i]
            if (values || !horizontal) layer.acrossEnd?.let { xs += it[i] }
            if (values || horizontal) layer.upEnd?.let { ys += it[i] }
            if (values) layer.base?.let { base -> if (horizontal) xs += base[i] else ys += base[i] }
        }
    }
    model.rules.forEach { rule ->
        if (!normalised || !horizontal) rule.x?.let { xs += it }
        if (!normalised || horizontal) rule.y?.let { ys += it }
    }
    // A region widens the axes to take it in, as a rule does: a forecast's
    // shading after today runs on past the last day of data.
    model.regions.forEach { region ->
        if (!normalised || !horizontal) {
            region.xStart?.let { xs += it }
            region.xEnd?.let { xs += it }
        }
        if (!normalised || horizontal) {
            region.yStart?.let { ys += it }
            region.yEnd?.let { ys += it }
        }
    }
    val measured = layers.any { it.mark.kind == MarkKind.Bar || it.mark.kind == MarkKind.Area }
    if (measured) {
        if (horizontal) xs += 0.0 else ys += 0.0
    }

    // Bars across a number or date axis stand on their value, so half a bar's
    // width either side of the first and the last is part of the domain.
    val barsAcrossContinuous = !horizontal && xAxis.kind != AxisKind.Category &&
        layers.any { it.mark.kind == MarkKind.Bar }
    val barPad = if (barsAcrossContinuous) smallestGap(layers.filter { it.mark.kind == MarkKind.Bar }) / 2 else 0.0

    val x = resolveAxis(xAxis, xs, model.xCategories, formats, padding = barPad, percent = normalised && horizontal)
    val y = resolveAxis(yAxis, ys, model.yCategories, formats, padding = 0.0, percent = normalised && !horizontal)

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
        rules = model.rules.filter { rule -> rule.x.within(x) && rule.y.within(y) },
        regions = model.regions.mapNotNull { it.clampedTo(x, y) },
        places = places,
    )
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
        Region(xStart, xEnd, yStart, yEnd, label, style)
    }
}

/** How far past an axis's end a reference may sit and still be on it: rounding, not data. */
private const val AxisSlack = 1e-9

/** The layers of one mark, stacked as it says, a run each. */
private fun layersOf(markIndex: Int, mark: Mark, model: ChartModel, horizontal: Boolean): List<Layer> {
    val runs = mark.runs
    val lying = horizontal && mark.kind == MarkKind.Bar
    val stacks = (mark.kind == MarkKind.Bar || mark.kind == MarkKind.Area) &&
        (mark.stacking == ChartStacking.Stacked || mark.stacking == ChartStacking.Normalised)
    val grouped = mark.kind == MarkKind.Bar && !stacks && runs.size > 1
    val normalised = stacks && mark.stacking == ChartStacking.Normalised

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

    if (mark.kind == MarkKind.Band) {
        // Drawn as an area between its edges, its base the low one, so it
        // animates and rises as an area does; read as the range from low to high.
        return runs.map { run ->
            val high = run.yEnds ?: DoubleArray(run.size) { run.ys[it] }
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
                values = run.ys.copyOf(),
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
                val value = values[i]
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

    AxisKind.Number -> {
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
        val ticks = niceTicks(low, high, axis.tickCount)
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
