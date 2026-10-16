package io.kontour.ui.charts

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.drawText
import kotlin.math.max
import kotlin.math.min

/** What a chart is drawn with, in pixels, for one density. */
internal class DrawConfig(
    val colours: ChartColours,
    val patterns: Boolean,
    val lineWidth: Float,
    val pointSize: Float,
    val cornerRadius: Float,
    val hairline: Float,
    val tileSize: Int,
    val tileStroke: Float,
    val tiles: PatternTiles,
)

/**
 * Draws one frame of [chart] into [frame]: gridlines, then every mark in the
 * order the content called them, then the rules, the selection and the labels.
 *
 * [alpha] fades the marks in, for an entrance under reduced motion; the frame
 * itself — gridlines, labels — is there from the first frame.
 */
internal fun DrawScope.drawChart(
    frame: ChartFrame,
    chart: ResolvedChart,
    config: DrawConfig,
    alpha: Float,
    selected: Double?,
) {
    val m = Mapper(frame, chart.x, chart.y)
    val plot = frame.plot
    val colours = config.colours

    // Gridlines, at the ticks of each continuous axis that has them.
    if (chart.y.showGridlines && chart.y.kind != AxisKind.Category) {
        chart.y.ticks.forEach { tick ->
            val y = m.py(tick)
            if (y >= plot.top - 0.5f && y <= plot.bottom + 0.5f) {
                drawLine(colours.grid, Offset(plot.left, y), Offset(plot.right, y), config.hairline)
            }
        }
    }
    if (chart.x.showGridlines && chart.x.kind != AxisKind.Category) {
        chart.x.ticks.forEach { tick ->
            val x = m.px(tick)
            if (x >= plot.left - 0.5f && x <= plot.right + 0.5f) {
                drawLine(colours.grid, Offset(x, plot.top), Offset(x, plot.bottom), config.hairline)
            }
        }
    }

    // The baseline: zero on the value axis where zero is on it, and otherwise
    // the edge values are read from.
    if (chart.horizontal) {
        val zero = if (chart.x.low <= 0.0 && chart.x.high >= 0.0) m.px(0.0) else if (frame.rtl) plot.right else plot.left
        drawLine(colours.axis, Offset(zero, plot.top), Offset(zero, plot.bottom), config.hairline)
    } else {
        val zero = if (chart.y.kind == AxisKind.Number && chart.y.low <= 0.0 && chart.y.high >= 0.0) m.py(0.0) else plot.bottom
        drawLine(colours.axis, Offset(plot.left, zero), Offset(plot.right, zero), config.hairline)
    }

    val seriesCount = chart.model.series.size
    val pad = config.pointSize
    clipRect(plot.left - pad, plot.top - pad, plot.right + pad, plot.bottom + pad) {
        chart.layers.forEach { layer ->
            val look = lookOf(layer.slot, colours.series, config.patterns, layer.mark.style, layer.series.style)
            when (layer.mark.kind) {
                MarkKind.Area -> drawArea(layer, look, m, config, alpha)
                MarkKind.Line -> {
                    drawLineMark(layer, look, m, config, alpha)
                    if (layer.mark.showPoints || (config.patterns && seriesCount > 1)) {
                        drawSymbols(layer, look.symbol, look.colour, m, config, alpha)
                    }
                }
                MarkKind.Bar -> drawBars(layer, look, chart, m, config, alpha)
                MarkKind.Point -> drawSymbols(layer, layer.mark.symbol ?: look.symbol, look.colour, m, config, alpha)
                MarkKind.Rectangle -> drawRectangles(layer, look, m, config, alpha)
            }
        }
    }

    // Rules, over the marks they are a reference for.
    chart.rules.forEachIndexed { index, rule ->
        val colour = rule.style?.colour?.takeIf { it != Color.Unspecified } ?: colours.axis
        val effect = (rule.style?.line ?: LinePattern.Dashed).effect(config.hairline * 2)
        val stroke = config.hairline * 1.5f
        val label = frame.ruleLabels.getOrNull(index)
        rule.y?.let { value ->
            val y = m.py(value)
            drawLine(colour, Offset(plot.left, y), Offset(plot.right, y), stroke, pathEffect = effect)
            label?.let {
                val left = if (frame.rtl) plot.left + config.hairline * 2 else plot.right - it.size.width - config.hairline * 2
                drawText(it, colours.label, Offset(left, y - it.size.height - config.hairline * 2))
            }
        }
        rule.x?.let { value ->
            val x = m.px(value)
            drawLine(colour, Offset(x, plot.top), Offset(x, plot.bottom), stroke, pathEffect = effect)
            label?.let {
                val left = if (frame.rtl) x - it.size.width - config.hairline * 3 else x + config.hairline * 3
                drawText(it, colours.label, Offset(left, plot.top))
            }
        }
    }

    // The selection: a line through the selected place, and a ring round each
    // line's value there.
    if (selected != null) {
        val stroke = config.hairline * 1.5f
        if (chart.horizontal) {
            val y = m.py(selected)
            drawLine(colours.selection, Offset(plot.left, y), Offset(plot.right, y), stroke)
        } else {
            val x = m.px(selected)
            drawLine(colours.selection, Offset(x, plot.top), Offset(x, plot.bottom), stroke)
            chart.layers.filter { it.mark.kind == MarkKind.Line || it.mark.kind == MarkKind.Area }.forEach { layer ->
                val value = sampleAt(layer.across, layer.up, selected)
                if (value.isNaN()) return@forEach
                val look = lookOf(layer.slot, colours.series, config.patterns, layer.mark.style, layer.series.style)
                val centre = Offset(x, m.py(value))
                drawCircle(colours.ground, config.pointSize * 0.9f, centre)
                drawCircle(look.colour, config.pointSize * 0.6f, centre)
            }
        }
    }

    drawLabels(frame, m, colours)
}

private fun DrawScope.fill(path: Path, look: SeriesLook, config: DrawConfig, alpha: Float, opacity: Float = 1f) {
    drawPath(path, look.colour, alpha = alpha * opacity)
    val pattern: Brush? = config.tiles.brush(look.fill, config.colours.patternInk, config.tileSize, config.tileStroke)
    if (pattern != null) drawPath(path, pattern, alpha = alpha)
}

private fun DrawScope.drawLineMark(layer: Layer, look: SeriesLook, m: Mapper, config: DrawConfig, alpha: Float) {
    val points = (0 until layer.size).map { i ->
        val y = layer.up[i]
        if (y.isNaN()) null else Offset(m.px(layer.across[i]), m.py(y))
    }
    val path = linePath(decimate(points, m.frame.plot.width), layer.mark.interpolation)
    drawPath(
        path,
        look.colour,
        alpha = alpha,
        style = Stroke(
            width = config.lineWidth,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
            pathEffect = look.line.effect(config.lineWidth),
        ),
    )
}

private fun DrawScope.drawArea(layer: Layer, look: SeriesLook, m: Mapper, config: DrawConfig, alpha: Float) {
    val base = layer.base ?: DoubleArray(layer.size)
    val stacked = layer.mark.stacking == ChartStacking.Stacked || layer.mark.stacking == ChartStacking.Normalised
    val fillPath = Path()
    val outline = Path()
    // One closed shape per unbroken run of values.
    var start = 0
    while (start < layer.size) {
        while (start < layer.size && layer.up[start].isNaN()) start++
        if (start >= layer.size) break
        var end = start
        while (end + 1 < layer.size && !layer.up[end + 1].isNaN()) end++
        val tops = (start..end).map { Offset(m.px(layer.across[it]), m.py(layer.up[it])) }
        val bottoms = (end downTo start).map { Offset(m.px(layer.across[it]), m.py(base[it])) }
        appendRun(fillPath, tops, layer.mark.interpolation, moveFirst = true)
        appendRun(fillPath, bottoms, layer.mark.interpolation, moveFirst = false)
        fillPath.close()
        appendRun(outline, tops, layer.mark.interpolation, moveFirst = true)
        start = end + 1
    }
    // Unstacked areas overlap, so each lets the others show through; stacked
    // ones tile, and are near solid.
    fill(fillPath, look, config, alpha, opacity = if (stacked) 0.85f else 0.3f)
    drawPath(
        outline,
        look.colour,
        alpha = alpha,
        style = Stroke(width = config.lineWidth, cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = look.line.effect(config.lineWidth)),
    )
}

private fun DrawScope.drawBars(layer: Layer, look: SeriesLook, chart: ResolvedChart, m: Mapper, config: DrawConfig, alpha: Float) {
    val lying = chart.horizontal
    val places = if (lying) layer.up else layer.across
    val tops = if (lying) layer.across else layer.up
    val base = layer.base ?: DoubleArray(layer.size)
    val placeAxis = chart.placeAxis
    val unit = if (lying) m.unitY else m.unitX
    val band = if (placeAxis.kind == AxisKind.Category) unit else unit * smallestStep(places).toFloat()
    val thickness = band * ChartDefaults.BarFraction / layer.groupCount
    val offset = (layer.groupIndex - (layer.groupCount - 1) / 2f) * thickness
    val stacked = layer.mark.stacking == ChartStacking.Stacked || layer.mark.stacking == ChartStacking.Normalised
    val path = Path()
    for (i in 0 until layer.size) {
        if (tops[i].isNaN()) continue
        val positive = tops[i] >= base[i]
        if (lying) {
            val centre = m.py(places[i]) + offset
            val from = m.px(base[i])
            val to = m.px(tops[i])
            val left = min(from, to)
            val right = max(from, to)
            val length = right - left
            val r = if (stacked) 0f else min(config.cornerRadius, min(thickness / 2, length / 2))
            // The far end is rounded; which side that is depends on the sign
            // and on which way the language reads.
            val farIsRight = (to >= from)
            path.addRoundRect(
                RoundRect(
                    rect = Rect(left, centre - thickness / 2, right, centre + thickness / 2),
                    topLeft = CornerRadius(if (farIsRight) 0f else r),
                    topRight = CornerRadius(if (farIsRight) r else 0f),
                    bottomRight = CornerRadius(if (farIsRight) r else 0f),
                    bottomLeft = CornerRadius(if (farIsRight) 0f else r),
                ),
            )
        } else {
            val direction = if (m.frame.rtl) -1f else 1f
            val centre = m.px(places[i]) + offset * direction
            val from = m.py(base[i])
            val to = m.py(tops[i])
            val top = min(from, to)
            val bottom = max(from, to)
            val length = bottom - top
            val r = if (stacked) 0f else min(config.cornerRadius, min(thickness / 2, length / 2))
            path.addRoundRect(
                RoundRect(
                    rect = Rect(centre - thickness / 2, top, centre + thickness / 2, bottom),
                    topLeft = CornerRadius(if (positive) r else 0f),
                    topRight = CornerRadius(if (positive) r else 0f),
                    bottomRight = CornerRadius(if (positive) 0f else r),
                    bottomLeft = CornerRadius(if (positive) 0f else r),
                ),
            )
        }
    }
    fill(path, look, config, alpha)
}

/** The smallest step between neighbouring [places], for the width of bars across a continuous axis. */
private fun smallestStep(places: DoubleArray): Double {
    val sorted = places.filter { !it.isNaN() }.distinct().sorted()
    var step = Double.MAX_VALUE
    for (i in 1 until sorted.size) step = min(step, sorted[i] - sorted[i - 1])
    return if (step == Double.MAX_VALUE || step <= 0) 1.0 else step
}

private fun DrawScope.drawSymbols(layer: Layer, symbol: PointSymbol, colour: Color, m: Mapper, config: DrawConfig, alpha: Float) {
    val halo = Path()
    val marks = Path()
    for (i in 0 until layer.size) {
        val y = layer.up[i]
        if (y.isNaN() || layer.across[i].isNaN()) continue
        val centre = Offset(m.px(layer.across[i]), m.py(y))
        // A ring of the page's ground round each, so neighbouring points of
        // two series stay two points where they overlap.
        addSymbol(halo, symbol, centre, config.pointSize + config.hairline * 3)
        addSymbol(marks, symbol, centre, config.pointSize)
    }
    drawPath(halo, config.colours.ground, alpha = alpha)
    drawPath(marks, colour, alpha = alpha)
}

private fun DrawScope.drawRectangles(layer: Layer, look: SeriesLook, m: Mapper, config: DrawConfig, alpha: Float) {
    val ends = layer.acrossEnd ?: return
    val tops = layer.upEnd ?: return
    val path = Path()
    for (i in 0 until layer.size) {
        val x0 = m.px(layer.across[i])
        val x1 = m.px(ends[i])
        val y0 = m.py(layer.up[i])
        val y1 = m.py(tops[i])
        val rect = Rect(min(x0, x1), min(y0, y1), max(x0, x1), max(y0, y1))
        val r = min(config.cornerRadius, min(rect.width, rect.height) / 2)
        path.addRoundRect(RoundRect(rect, CornerRadius(r)))
    }
    fill(path, look, config, alpha, opacity = 0.85f)
}

private fun DrawScope.drawLabels(frame: ChartFrame, m: Mapper, colours: ChartColours) {
    val plot = frame.plot
    val half = frame.gap / 2
    frame.xLabels.forEach { label ->
        val w = label.layout.size.width.toFloat()
        val h = label.layout.size.height.toFloat()
        val centre = m.px(label.value)
        if (centre < plot.left - 1f || centre > plot.right + 1f) return@forEach
        val left = (centre - w / 2).coerceIn(0f, max(0f, frame.width - w))
        val top = if (frame.xAxisAtBottom) plot.bottom + half else plot.top - half - h
        drawText(label.layout, colours.label, Offset(left, top))
    }
    frame.yLabels.forEach { label ->
        val w = label.layout.size.width.toFloat()
        val h = label.layout.size.height.toFloat()
        val centre = m.py(label.value)
        if (centre < plot.top - 1f || centre > plot.bottom + 1f) return@forEach
        val top = (centre - h / 2).coerceIn(0f, max(0f, frame.height - h))
        val onLeft = frame.yAxisAtStart != frame.rtl
        val left = if (onLeft) plot.left - half - w else plot.right + half
        drawText(label.layout, colours.label, Offset(left, top))
    }
    frame.yTitle?.let { title ->
        val left = if (frame.yAxisAtStart != frame.rtl) 0f else frame.width - title.size.width
        drawText(title, colours.label, Offset(left, 0f))
    }
    frame.xTitle?.let { title ->
        val left = (plot.center.x - title.size.width / 2).coerceIn(0f, max(0f, frame.width - title.size.width))
        drawText(title, colours.label, Offset(left, frame.height - title.size.height))
    }
}
