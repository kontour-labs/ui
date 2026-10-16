package io.kontour.ui.charts

import androidx.compose.foundation.layout.LayoutScopeMarker
import androidx.compose.runtime.Stable

/**
 * The marks a [Chart] is made of, in the order they are drawn: a later mark
 * over an earlier one.
 *
 * ```kotlin
 * Chart(xAxis = ChartAxis.category(), yAxis = ChartAxis.number()) {
 *     bar(months, x = { it.name }, y = { it.rainfall })
 *     line(months, x = { it.name }, y = { it.average }, series = { average })
 *     rule(y = 50.0, label = "Drought line")
 * }
 * ```
 *
 * Every mark takes the items it draws and says how to read a value across (`x`)
 * and a value up (`y`) from each, in the axes' own types, and which series each
 * item is in. An item's `y` can be `null` for a value that is missing, which a
 * line leaves as a gap rather than joining across.
 *
 * Not composable: what is called here is recorded and drawn once, together, so
 * a chart of ten thousand points is one drawing rather than ten thousand nodes.
 * A state read inside it is followed, and records the chart again when it
 * changes.
 */
@LayoutScopeMarker
@Stable
interface ChartScope<X : Any, Y : Any> {

    /**
     * A line through [items], in the order of their `x`, one line per series.
     *
     * @param interpolation How the line goes from one point to the next.
     * @param showPoints Whether each point gets its series' symbol as well as
     *   the line through it. A chart of several series with patterns on draws
     *   them whatever this says, so that a series is told apart by its shape
     *   where its colour is not enough.
     * @param style How every series of this line is drawn, over its slot.
     */
    fun <T> line(
        items: List<T>,
        x: (T) -> X,
        y: (T) -> Y?,
        series: (T) -> ChartSeries = { ChartSeries.Default },
        interpolation: ChartInterpolation = ChartInterpolation.Linear,
        showPoints: Boolean = false,
        style: SeriesStyle? = null,
    )

    /**
     * The area under a line through [items], down to zero, or to the series
     * below it when [stacking] stacks them.
     */
    fun <T> area(
        items: List<T>,
        x: (T) -> X,
        y: (T) -> Y?,
        series: (T) -> ChartSeries = { ChartSeries.Default },
        stacking: ChartStacking = ChartStacking.None,
        interpolation: ChartInterpolation = ChartInterpolation.Linear,
        style: SeriesStyle? = null,
    )

    /**
     * A bar for each of [items], from zero to its value along whichever axis is
     * the number: standing, with a category axis across, or lying down, with a
     * category axis up.
     *
     * @param stacking How the bars of several series at one place share it:
     *   stacked into one bar, by default, or side by side.
     */
    fun <T> bar(
        items: List<T>,
        x: (T) -> X,
        y: (T) -> Y?,
        series: (T) -> ChartSeries = { ChartSeries.Default },
        stacking: ChartStacking = ChartStacking.Stacked,
        style: SeriesStyle? = null,
    )

    /**
     * A symbol at each of [items]: a scatter chart, or the points on a line.
     *
     * @param symbol The symbol, for every series of this mark. Left out, each
     *   series has its slot's — a circle, or with patterns on, one of
     *   [PointSymbol] in turn.
     */
    fun <T> point(
        items: List<T>,
        x: (T) -> X,
        y: (T) -> Y?,
        series: (T) -> ChartSeries = { ChartSeries.Default },
        symbol: PointSymbol? = null,
        style: SeriesStyle? = null,
    )

    /**
     * A rectangle for each of [items], spanning [xStart] to [xEnd] across and
     * [yStart] to [yEnd] up: a range, a band of time, a cell of a heat map.
     */
    fun <T> rectangle(
        items: List<T>,
        xStart: (T) -> X,
        xEnd: (T) -> X,
        yStart: (T) -> Y,
        yEnd: (T) -> Y,
        series: (T) -> ChartSeries = { ChartSeries.Default },
        style: SeriesStyle? = null,
    )

    /**
     * A line right across the plot at [y], or right up it at [x] — a target, an
     * average, today — with an optional [label] at its end.
     *
     * A rule is a reference rather than data: it is drawn and labelled, and a
     * screen reader hears its label after the chart's summary, but it is not a
     * series and has no place in the legend.
     */
    fun rule(
        x: X? = null,
        y: Y? = null,
        label: String? = null,
        style: SeriesStyle? = null,
    )
}

/** How the series of an [area][ChartScope.area] or a [bar][ChartScope.bar] mark share one place. */
enum class ChartStacking {
    /** Each from zero, over each other: areas overlap, and bars stand side by side. */
    None,

    /** One on top of the next, so the whole is the sum. Negative values stack down from zero. */
    Stacked,

    /** Side by side, each from zero. For bars; areas overlap, as with [None]. */
    Grouped,

    /** Stacked, and each place scaled to the same whole, so the axis reads as shares of it. */
    Normalised,
}

/** How a line or an area goes from one point to the next. */
enum class ChartInterpolation {
    /** Straight. */
    Linear,

    /**
     * Curved, through every point, and never past one: a value between two is
     * between them, which a plain curve does not promise.
     */
    Monotone,

    /** Flat from each point until the next, and up or down at it. */
    StepStart,

    /** Flat from halfway before each point to halfway after it. */
    StepCentre,

    /** Flat to each point from the one before, and up or down at the point before. */
    StepEnd,
}

/** Where a chart's legend goes. */
enum class ChartLegendPosition {
    Top,
    Bottom,
    Start,
    End,
}
