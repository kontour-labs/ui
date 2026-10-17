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
     * A bar for each of [items] from [yStart] to [yEnd] rather than from zero:
     * a day's least and most, a shift's start and end, a fare's range.
     *
     * ```kotlin
     * bar(days, x = { it.day }, yStart = { it.low }, yEnd = { it.high })
     * ```
     *
     * Rounded at both ends, since neither stands on anything. Several series
     * stand side by side at a place, as grouped bars do. A reader hears each as
     * its range, "Monday: 12 to 25", and a tooltip and a [ChartDataTable] write
     * it so. Either end `null` leaves the place empty; ends given the wrong way
     * round are the same bar.
     */
    fun <T> bar(
        items: List<T>,
        x: (T) -> X,
        yStart: (T) -> Y?,
        yEnd: (T) -> Y?,
        series: (T) -> ChartSeries = { ChartSeries.Default },
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
     * A band from [yStart] to [yEnd] at each of [items], one band per series:
     * a forecast's likely range, a day's least and most, a reading and its
     * margin of error.
     *
     * ```kotlin
     * band(forecast, x = { it.day }, yStart = { it.low }, yEnd = { it.high }, series = { likely })
     * line(forecast, x = { it.day }, y = { it.expected }, series = { likely })
     * ```
     *
     * Drawn as a light fill between the two edges, so a line drawn after it,
     * in the same series, reads as the value and the band as how far it might
     * go. A reader hears the range at each place, a tooltip shows it, and a
     * [ChartDataTable] writes it as one cell. Either end `null` is a gap.
     */
    fun <T> band(
        items: List<T>,
        x: (T) -> X,
        yStart: (T) -> Y?,
        yEnd: (T) -> Y?,
        series: (T) -> ChartSeries = { ChartSeries.Default },
        interpolation: ChartInterpolation = ChartInterpolation.Linear,
        style: SeriesStyle? = null,
    )

    /**
     * A thin stem from [yStart] to [yEnd] at each of [items]: with a [point]
     * at each end, a dumbbell, a day's low and high; from zero with one at its
     * top, a lollipop; over a bar, its margin of error.
     *
     * ```kotlin
     * stem(days, x = { it.day }, yStart = { it.low }, yEnd = { it.high }, style = joining)
     * point(days, x = { it.day }, y = { it.low }, series = { low })
     * point(days, x = { it.day }, y = { it.high }, series = { high })
     * ```
     *
     * `joining` is a [SeriesStyle] read before the chart, such as
     * `SeriesStyle(colour = Theme.colours.outlineStrong)`: the content records
     * marks and is not composed, so it cannot read the theme itself.
     *
     * A stem is said as its range where nothing else at its place says its
     * ends: a dumbbell's two points already say the low and the high, and the
     * stem between them is said again by nobody.
     */
    fun <T> stem(
        items: List<T>,
        x: (T) -> X,
        yStart: (T) -> Y?,
        yEnd: (T) -> Y?,
        series: (T) -> ChartSeries = { ChartSeries.Default },
        style: SeriesStyle? = null,
    )

    /**
     * A box plot at each of [items], from five numbers worked out already: a
     * box from [lowerQuartile] to [upperQuartile] with a line at the [median],
     * whiskers out to [low] and [high], and any [outliers] beyond them as
     * points. Several series stand side by side at a place, as grouped bars do.
     *
     * ```kotlin
     * box(trips, x = { it.route }, low = { it.p0 }, lowerQuartile = { it.p25 }, median = { it.p50 },
     *     upperQuartile = { it.p75 }, high = { it.p100 })
     * ```
     *
     * A reader hears each as "Route 950: median 18, quartiles 15 to 21, range
     * 10 to 30", and a [ChartDataTable] gives each number its own column.
     */
    fun <T> box(
        items: List<T>,
        x: (T) -> X,
        low: (T) -> Y,
        lowerQuartile: (T) -> Y,
        median: (T) -> Y,
        upperQuartile: (T) -> Y,
        high: (T) -> Y,
        outliers: (T) -> List<Y> = { emptyList() },
        series: (T) -> ChartSeries = { ChartSeries.Default },
        style: SeriesStyle? = null,
    )

    /**
     * A box plot of each of [items]' [samples], its numbers worked out here:
     * quartiles as a spreadsheet's `QUARTILE` has them, whiskers out to the
     * furthest sample within one and a half box lengths of the box, and every
     * sample past that an outlier. For whiskers to the least and the most, work
     * the five numbers out and give them to the other `box`.
     *
     * ```kotlin
     * box(routes, x = { it.name }, samples = { it.tripMinutes })
     * ```
     */
    fun <T> box(
        items: List<T>,
        x: (T) -> X,
        samples: (T) -> List<Y>,
        series: (T) -> ChartSeries = { ChartSeries.Default },
        style: SeriesStyle? = null,
    )

    /**
     * A heat map's cell at each of [items]: its [x] and [y] place, coloured by
     * its [value] along the chart's [ramp][ChartColours.ramp], the least value
     * at its pale end and the most at its deep one, with a scale under the
     * plot that says which is which.
     *
     * ```kotlin
     * cell(riders, x = { it.day }, y = { it.hour }, value = { it.count })
     * ```
     *
     * With patterns on, each cell's value is written in it as well, so it is
     * read by more than its shade. A reader hears a column at a time, "Monday:
     * 6am, 12 and 7am, 30", and a [ChartDataTable] has a row for each column of
     * the map and a column for each of its rows. A `null` value leaves the
     * cell empty.
     */
    fun <T> cell(
        items: List<T>,
        x: (T) -> X,
        y: (T) -> Y,
        value: (T) -> Number?,
    )

    /**
     * A line right across the plot at [y], or right up it at [x] — a target, an
     * average, today, an event — with an optional [label].
     *
     * A rule up the plot has its label in a pin above it, and pins that would
     * touch stack one above the other, so a run of events close together keeps
     * every label. A rule across has its label at its end, on the page's
     * ground, so it stays legible over the marks it crosses.
     *
     * A rule is a reference rather than data: it is drawn and labelled, and a
     * screen reader hears its label and where it is after the chart's summary,
     * but it is not a series and has no place in the legend.
     *
     * On a [normalised][ChartStacking.Normalised] chart the value axis is
     * shares, and so is a rule along it: `rule(y = 0.5)` is the halfway line.
     * One past the end of an axis is not drawn.
     */
    fun rule(
        x: X? = null,
        y: Y? = null,
        label: String? = null,
        style: SeriesStyle? = null,
    )

    /**
     * A shaded span of the plot: from [xStart] to [xEnd] across and right up
     * it, or [yStart] to [yEnd] up and right across it, or a box given all
     * four — a fortnight a launch ran, an outage, the forecast after today, a
     * comfortable range. An end left `null` runs to the edge of the plot.
     *
     * ```kotlin
     * region(xStart = launchDay, xEnd = fortnightLater, label = "Launch fortnight")
     * ```
     *
     * Drawn behind every mark, in the selection's colour unless [style] gives
     * one, with its [label] inside its top corner. On a category axis it covers
     * the whole of each category it names. Like a rule it is a reference, not
     * data: a reader hears its label and its span after the chart's summary.
     * Along a [normalised][ChartStacking.Normalised] chart's value axis its
     * ends are shares, as a [rule]'s are, and it is cut to the axis.
     */
    fun region(
        xStart: X? = null,
        xEnd: X? = null,
        yStart: Y? = null,
        yEnd: Y? = null,
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

    /**
     * Stacked, and each place scaled to the same whole, so the axis reads as
     * shares of it: what part of each month each route carried.
     *
     * With one series there is nothing to share a place with, so its whole is
     * the series' own, and each place reads as its share of all of them. The
     * value axis runs from 0 to 100%, down to -100% with negatives, and
     * everything else along it — a rule, a region, a line or band beside the
     * stacks — is read as a share too, rather than stretching it to its own
     * units.
     */
    Normalised,

    /**
     * Stacked about a centre line rather than on zero, half of each place's
     * whole either side of it: a streamgraph, where what matters is how thick
     * each series is and how that changes, not where it sits. The value axis
     * has no ticks, since a height up it measures nothing, and a reader hears
     * each series' own values. For areas; bars stack as with [Stacked].
     */
    Centred,
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
