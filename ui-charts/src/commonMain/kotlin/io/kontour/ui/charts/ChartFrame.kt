package io.kontour.ui.charts

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.max

/** A tick label, measured, and the value it labels. */
internal class AxisLabel(val value: Double, val layout: TextLayoutResult)

/**
 * Where the plot goes once its labels are measured, and which labels there is
 * room for.
 *
 * Measured from the chart it will settle on, not from each frame of getting
 * there, so the plot does not breathe in and out as an animation passes
 * through labels of different widths. Labels that would touch are left out,
 * every other one, then every third: at 200% text a chart keeps fewer labels
 * rather than overlapping ones, and never turns them on their side.
 */
internal class ChartFrame(
    val width: Float,
    val height: Float,
    val plot: Rect,
    val rtl: Boolean,
    val xLabels: List<AxisLabel>,
    val yLabels: List<AxisLabel>,
    val xTitle: TextLayoutResult?,
    val yTitle: TextLayoutResult?,
    /** Each rule's label, in the order of the chart's rules; `null` for a rule with none. */
    val ruleLabels: List<TextLayoutResult?>,
    val gap: Float,
    val xAxisAtBottom: Boolean,
    val yAxisAtStart: Boolean,
)

/** Maps the chart's units onto [frame]'s plot, for one frame's axes. */
internal class Mapper(val frame: ChartFrame, val x: ResolvedAxis, val y: ResolvedAxis) {
    private val plot = frame.plot

    fun px(value: Double): Float {
        val fraction = ((value - x.low) / x.span).toFloat()
        return if (frame.rtl) plot.right - fraction * plot.width else plot.left + fraction * plot.width
    }

    /** A category axis up runs top to bottom, in the order it reads; any other, bottom to top. */
    fun py(value: Double): Float {
        val fraction = ((value - y.low) / y.span).toFloat()
        return if (y.kind == AxisKind.Category) plot.top + fraction * plot.height else plot.bottom - fraction * plot.height
    }

    /** Pixels per unit across. */
    val unitX: Float get() = plot.width / x.span.toFloat()

    /** Pixels per unit up. */
    val unitY: Float get() = plot.height / y.span.toFloat()

    /** The value across at [offset]'s position. */
    fun xAt(offset: Offset): Double {
        val fraction = if (frame.rtl) (plot.right - offset.x) / plot.width else (offset.x - plot.left) / plot.width
        return x.low + fraction * x.span
    }

    /** The value up at [offset]'s position. */
    fun yAt(offset: Offset): Double {
        val fraction = if (y.kind == AxisKind.Category) (offset.y - plot.top) / plot.height else (plot.bottom - offset.y) / plot.height
        return y.low + fraction * y.span
    }
}

/**
 * Measures [chart]'s labels and places its plot in [width] by [height].
 *
 * @param gap The space between a label and the plot, and between two labels.
 */
internal fun layOutFrame(
    chart: ResolvedChart,
    width: Float,
    height: Float,
    measurer: TextMeasurer,
    labelStyle: TextStyle,
    titleStyle: TextStyle,
    gap: Float,
    density: Density,
    layoutDirection: LayoutDirection,
): ChartFrame {
    val rtl = layoutDirection == LayoutDirection.Rtl
    val x = chart.x
    val y = chart.y
    val xAtBottom = x.position != ChartAxisPosition.Start
    val yAtStart = y.position != ChartAxisPosition.End

    fun measure(text: String, style: TextStyle, maxWidth: Int = Constraints.Infinity): TextLayoutResult =
        measurer.measure(
            text = text,
            style = style,
            overflow = TextOverflow.Ellipsis,
            softWrap = false,
            maxLines = 1,
            constraints = Constraints(maxWidth = maxWidth.coerceAtLeast(1)),
            layoutDirection = layoutDirection,
            density = density,
        )

    // Up the start edge: as wide as its widest label, but never more than two
    // fifths of the chart, for the long names of lying bars.
    val yCap = (width * 0.4f).toInt()
    val yMeasured = if (y.showLabels) {
        y.ticks.indices.map { i -> AxisLabel(y.ticks[i], measure(y.tickLabels[i], labelStyle, yCap)) }
    } else {
        emptyList()
    }
    val yGutter = yMeasured.maxOfOrNull { it.layout.size.width }?.let { it + gap } ?: 0f
    val yTitle = y.title?.let { measure(it, titleStyle, width.toInt()) }
    val xTitle = x.title?.let { measure(it, titleStyle, width.toInt()) }

    val labelHeight = yMeasured.maxOfOrNull { it.layout.size.height }?.toFloat() ?: 0f
    val endPad = gap
    val plotLeftLtr = if (yAtStart) yGutter else endPad
    val plotRightLtr = width - if (yAtStart) endPad else yGutter
    val plotWidth = max(1f, plotRightLtr - plotLeftLtr)

    // Across: a category's label gets its own band and no more.
    val band = if (x.kind == AxisKind.Category) (plotWidth / x.span.toFloat()).toInt() else Constraints.Infinity
    val xMeasured = if (x.showLabels) {
        x.ticks.indices.map { i -> AxisLabel(x.ticks[i], measure(x.tickLabels[i], labelStyle, band)) }
    } else {
        emptyList()
    }
    val xLabelHeight = xMeasured.maxOfOrNull { it.layout.size.height }?.toFloat() ?: 0f
    val xGutter = if (xMeasured.isEmpty()) 0f else xLabelHeight + gap
    val xTitleBand = xTitle?.let { it.size.height + gap / 2 } ?: 0f
    val yTitleBand = yTitle?.let { it.size.height + gap / 2 } ?: 0f
    // Half a label above the top tick, so a label centred on it is not cut.
    val topPad = max(labelHeight / 2, yTitleBand)

    val top = if (xAtBottom) topPad else topPad + xGutter + xTitleBand
    val bottom = height - if (xAtBottom) xGutter + xTitleBand else labelHeight / 2
    val left = if (rtl) width - plotRightLtr else plotLeftLtr
    val right = if (rtl) width - plotLeftLtr else plotRightLtr
    val plot = Rect(left, top, right, max(top + 1f, bottom))

    val ruleLabels = chart.rules.map { rule -> rule.label?.let { measure(it, labelStyle, (plot.width / 2).toInt()) } }
    val frame = ChartFrame(width, height, plot, rtl, xMeasured, yMeasured, xTitle, yTitle, ruleLabels, gap, xAtBottom, yAtStart)
    val mapper = Mapper(frame, x, y)
    return ChartFrame(
        width = width,
        height = height,
        plot = plot,
        rtl = rtl,
        xLabels = thinned(xMeasured, gap) { label ->
            val centre = mapper.px(label.value)
            centre - label.layout.size.width / 2f to centre + label.layout.size.width / 2f
        },
        yLabels = thinned(yMeasured, gap / 2) { label ->
            val centre = mapper.py(label.value)
            centre - label.layout.size.height / 2f to centre + label.layout.size.height / 2f
        },
        xTitle = xTitle,
        yTitle = yTitle,
        ruleLabels = ruleLabels,
        gap = gap,
        xAxisAtBottom = xAtBottom,
        yAxisAtStart = yAtStart,
    )
}

/**
 * The labels that fit: every one if none touch, else every second, every third
 * and so on, so that what is left is evenly spaced rather than whichever
 * happened to fit first.
 */
private fun thinned(labels: List<AxisLabel>, gap: Float, extent: (AxisLabel) -> Pair<Float, Float>): List<AxisLabel> {
    if (labels.size < 2) return labels
    for (every in 1..labels.size) {
        val kept = labels.filterIndexed { index, _ -> index % every == 0 }
        val spans = kept.map(extent).sortedBy { it.first }
        val fits = spans.zipWithNext().all { (a, b) -> b.first - a.second >= gap }
        if (fits) return kept
    }
    return labels.take(1)
}
