package io.kontour.ui.charts

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * One series of a chart: what a legend and a screen reader call it, and what
 * tells its marks apart from the others.
 *
 * ```kotlin
 * Chart(xAxis = ChartAxis.date(), yAxis = ChartAxis.number()) {
 *     line(readings, x = { it.day }, y = { it.level }, series = { ChartSeries(it.station) })
 * }
 * ```
 *
 * Two series are the same series when their [key]s are equal, which is what
 * keeps a series on its colour as the data changes: the third station to appear
 * keeps the third colour while it is there, wherever in the list it comes. The
 * key is the [name] unless it is given, which is enough unless two series could
 * share a name.
 *
 * Its look comes from its slot — the order series first appear in — so a chart
 * of three needs none of it said: three colours from the theme, and with
 * patterns on, three line dashes, three point symbols and three fills. [style]
 * overrides whichever of those it sets.
 *
 * @property name What it is called, in the reader's language: on the legend, in
 *   the tooltip, and in what a screen reader says.
 * @property key What makes it the same series from one set of data to the next.
 * @property style What to draw it with, over what its slot gives it.
 */
@Immutable
class ChartSeries(
    val name: String,
    val key: Any = name,
    val style: SeriesStyle? = null,
) {
    override fun equals(other: Any?): Boolean = other is ChartSeries && other.key == key

    override fun hashCode(): Int = key.hashCode()

    override fun toString(): String = "ChartSeries($name)"

    companion object {
        /**
         * The series of a chart that has only one, and so nothing to name: a
         * legend leaves it out, and a screen reader describes its values without
         * a series' name in front of each.
         */
        val Default: ChartSeries = ChartSeries(name = "", key = UnnamedSeries)
    }
}

/** The key of [ChartSeries.Default]: equal to nothing a caller could pass. */
private object UnnamedSeries

/**
 * How one series is drawn, over what its slot gives it. Every field is
 * optional, and one left out is the slot's.
 *
 * ```kotlin
 * ChartSeries("Target", style = SeriesStyle(line = LinePattern.Dashed))
 * ```
 *
 * @property colour The series' colour, in place of the palette's. Choose one
 *   with at least 3:1 against the chart's ground: the palette's are held to it,
 *   and a series that cannot be seen cannot be read.
 * @property line How a line is stroked.
 * @property symbol The mark at each point, where points are drawn.
 * @property fill How a bar, an area, a rectangle or a sector is filled.
 */
@Immutable
data class SeriesStyle(
    val colour: Color = Color.Unspecified,
    val line: LinePattern? = null,
    val symbol: PointSymbol? = null,
    val fill: FillPattern? = null,
)

/**
 * How a line is stroked, as a second cue beside its colour.
 *
 * In a chart with patterns on, series take these in this order, so the first is
 * always solid.
 */
enum class LinePattern {
    Solid,
    Dashed,
    Dotted,
    DashDot,
    LongDash,
}

/**
 * The mark at each point of a series, as a second cue beside its colour.
 *
 * In a chart with patterns on, series take these in this order. Without them,
 * every series' points are circles.
 */
enum class PointSymbol {
    Circle,
    Square,
    Triangle,
    Diamond,
    InvertedTriangle,
    Cross,
    Plus,
}

/**
 * How a bar, an area, a rectangle or a sector is filled, as a second cue beside
 * its colour.
 *
 * The pattern is drawn over the series' colour in a wash of the page's ground,
 * so it reads on any colour and in either scheme. In a chart with patterns on,
 * series take these in this order, so the first is always solid.
 */
enum class FillPattern {
    Solid,
    Diagonal,
    Dots,
    CrossHatch,
    Horizontal,
    ReverseDiagonal,
    Vertical,
}
