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
import kotlin.math.min

/** A tick label, measured, and the value it labels. */
internal class AxisLabel(val value: Double, val layout: TextLayoutResult)

/**
 * A rule up the plot's label, in a pin above the plot: [row] 0 sits on the
 * plot, and a pin that would touch one already in a row goes up a row.
 */
internal class RulePin(val layout: TextLayoutResult, val row: Int)

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
    /** Each rule's pin, for a rule up the plot with a label; `null` for any other. */
    val rulePins: List<RulePin?>,
    /** Each region's label, fitted to its width; `null` for one with none, or no room. */
    val regionLabels: List<TextLayoutResult?>,
    /** How tall a pin is, and the band of pins above the plot. */
    val pinHeight: Float,
    val pinBand: Float,
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
    // The title over the value axis, a full gap clear of the top tick's label,
    // which is centred on the plot's top edge and so stands half above it. It
    // was half a gap from the plot instead, and the label reached up to it.
    val titleTop = yTitle?.let { it.size.height + gap } ?: 0f
    val labelHalf = labelHeight / 2

    val left = if (rtl) width - plotRightLtr else plotLeftLtr
    val right = if (rtl) width - plotLeftLtr else plotRightLtr

    // Pins for the rules up the plot, laid out across before the plot's top is
    // known, which only the mapping up needs.
    val pinPad = gap / 2
    val across = Mapper(blankFrame(width, height, Rect(left, 0f, right, 1f), rtl, gap, xAtBottom, yAtStart), x, y)
    var pinHeight = 0f
    val pinsByRule = chart.rules.map { rule ->
        val value = rule.x
        val label = rule.label
        if (value == null || rule.y != null || label.isNullOrBlank()) return@map null
        measure(label, labelStyle, (plotWidth / 3).toInt()) to value
    }
    val spans = pinsByRule.map { pin ->
        pin?.let { (layout, value) ->
            pinHeight = max(pinHeight, layout.size.height + pinPad)
            val pinWidth = layout.size.width + pinPad * 2
            val start = pinStart(across.px(value), pinWidth, left, width)
            start to start + pinWidth
        }
    }
    val rows = stackPins(spans, pinPad)
    val rowCount = rows.maxOrNull()?.plus(1) ?: 0
    val pinBand = if (rowCount == 0) 0f else rowCount * (pinHeight + pinPad) + pinPad

    val top = if (xAtBottom) {
        titleTop + max(labelHalf, pinBand)
    } else {
        titleTop + labelHalf + xGutter + xTitleBand + pinBand
    }
    val bottom = height - if (xAtBottom) xGutter + xTitleBand else labelHalf
    val plot = Rect(left, top, right, max(top + 1f, bottom))

    val ruleLabels = chart.rules.map { rule ->
        if (rule.y == null) null else rule.label?.takeIf { it.isNotBlank() }?.let { measure(it, labelStyle, (plot.width / 2).toInt()) }
    }
    val rulePins = pinsByRule.mapIndexed { index, pin -> pin?.let { RulePin(it.first, rows[index]) } }
    val frame = blankFrame(width, height, plot, rtl, gap, xAtBottom, yAtStart)
    val mapper = Mapper(frame, x, y)
    val regionLabels = chart.regions.map { region ->
        val label = region.label?.takeIf { it.isNotBlank() } ?: return@map null
        val span = regionRect(region, mapper, chart)
        val room = (span.width - pinPad * 2).toInt()
        if (room < labelHeight) null else measure(label, labelStyle, room)
    }
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
        rulePins = rulePins,
        regionLabels = regionLabels,
        pinHeight = pinHeight,
        pinBand = pinBand,
        gap = gap,
        xAxisAtBottom = xAtBottom,
        yAxisAtStart = yAtStart,
    )
}

/** A frame with only its plot, for mapping before the labels are in. */
private fun blankFrame(width: Float, height: Float, plot: Rect, rtl: Boolean, gap: Float, xAtBottom: Boolean, yAtStart: Boolean) =
    ChartFrame(
        width, height, plot, rtl, emptyList(), emptyList(), null, null, emptyList(), emptyList(), emptyList(),
        0f, 0f, gap, xAtBottom, yAtStart,
    )

/**
 * The row each pin goes in, from where it starts to where it ends across, `null`
 * for a rule with no pin, which is given row -1. Taken from the start of the
 * plot, each pin goes in the lowest row it would not touch the last pin of,
 * with [pad] between, and in a new row above them where it would touch them all.
 */
internal fun stackPins(spans: List<Pair<Float, Float>?>, pad: Float): IntArray {
    val rows = IntArray(spans.size) { -1 }
    val rowEnds = mutableListOf<Float>()
    spans.indices.filter { spans[it] != null }.sortedBy { spans[it]!!.first }.forEach { index ->
        val (start, end) = spans[index]!!
        var row = rowEnds.indexOfFirst { start >= it + pad }
        if (row < 0) {
            rowEnds += end
            row = rowEnds.lastIndex
        }
        rowEnds[row] = end
        rows[index] = row
    }
    return rows
}

/** Where a pin [pinWidth] wide, centred on [centre], starts: inside the plot's start and the chart's far edge. */
internal fun pinStart(centre: Float, pinWidth: Float, plotLeft: Float, width: Float): Float =
    (centre - pinWidth / 2).coerceIn(min(plotLeft, width - pinWidth), max(0f, width - pinWidth))

/**
 * Where [region] is on the plot: its ends where it has them and the plot's
 * edges where it does not, a category's whole band on a category axis.
 */
internal fun regionRect(region: Region, m: Mapper, chart: ResolvedChart): Rect {
    val plot = m.frame.plot
    val xHalf = if (chart.x.kind == AxisKind.Category) 0.5 else 0.0
    val yHalf = if (chart.y.kind == AxisKind.Category) 0.5 else 0.0
    val (left, right) = if (region.xStart == null && region.xEnd == null) {
        plot.left to plot.right
    } else {
        val a = region.xStart?.let { m.px(it - xHalf) } ?: if (m.frame.rtl) plot.right else plot.left
        val b = region.xEnd?.let { m.px(it + xHalf) } ?: if (m.frame.rtl) plot.left else plot.right
        min(a, b) to max(a, b)
    }
    val (top, bottom) = if (region.yStart == null && region.yEnd == null) {
        plot.top to plot.bottom
    } else {
        // A category axis up runs top to bottom, so its start is at the top.
        val startEdge = if (chart.y.kind == AxisKind.Category) plot.top else plot.bottom
        val endEdge = if (chart.y.kind == AxisKind.Category) plot.bottom else plot.top
        val a = region.yStart?.let { m.py(if (chart.y.kind == AxisKind.Category) it - yHalf else it) } ?: startEdge
        val b = region.yEnd?.let { m.py(if (chart.y.kind == AxisKind.Category) it + yHalf else it) } ?: endEdge
        min(a, b) to max(a, b)
    }
    return Rect(
        left.coerceIn(plot.left, plot.right),
        top.coerceIn(plot.top, plot.bottom),
        right.coerceIn(plot.left, plot.right),
        bottom.coerceIn(plot.top, plot.bottom),
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
