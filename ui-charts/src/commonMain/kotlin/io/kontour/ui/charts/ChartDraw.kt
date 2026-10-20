package io.kontour.ui.charts

import io.kontour.ui.theme.trailingCornersOnly
import io.kontour.ui.theme.topCornersOnly
import io.kontour.ui.theme.leadingCornersOnly
import io.kontour.ui.theme.bottomCornersOnly
import io.kontour.ui.foundation.PatternBrushes
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Dp
import kotlin.math.max
import kotlin.math.min

/** What a chart is drawn with, in pixels, for one density. */
internal class DrawConfig(
    val colours: ChartColours,
    val patterns: Boolean,
    val lineWidth: Float,
    val pointSize: Float,
    /** The theme's small shape, which a bar's end, a rectangle and a region are cut from. */
    val cornerShape: CornerBasedShape,
    /** The theme's capsule, for a rule's pin. */
    val pinShape: Shape,
    val hairline: Float,
    /** How heavy a pattern's lines are where it does not say. */
    val patternWeight: Dp,
    val tiles: PatternBrushes,
    /** The ground left between a heat map's cells, as between a sector chart's sectors. */
    val cellGap: Float = 0f,
    /** The two inks a heat map's value can be written in: whichever stands out more from its cell. */
    val cellInks: Pair<Color, Color> = Color.Black to Color.White,
) {
    /** The squircles a bar's end, a rectangle and a region are cut from, made once. */
    val corners = Corners(cornerShape)
}

/**
 * The theme's shape, rounded at the far end of a bar each way, or all round.
 * From `Theme.shapes`, so a chart's corners are the squircle every other
 * component has, and a brand that rounds less rounds its charts less. Read
 * left to right whatever the language: a bar's far end is worked out already.
 */
internal class Corners(base: CornerBasedShape) {
    val up: Shape = base.topCornersOnly()
    val down: Shape = base.bottomCornersOnly()
    val right: Shape = base.trailingCornersOnly()
    val left: Shape = base.leadingCornersOnly()
    val all: Shape = base
}

/** How strongly a region is washed in its colour: a ground to read marks over, not a mark. */
private const val RegionOpacity = 0.12f

/** How strongly a band is filled: lighter than an area, which is a value, where a band is a range. */
private const val BandOpacity = 0.22f

/** [shape] at [rect], added to [path]; nothing for a rect with no area. */
private fun DrawScope.addShape(path: Path, rect: Rect, shape: Shape) {
    if (rect.width <= 0f || rect.height <= 0f) return
    val outline = shape.createOutline(rect.size, LayoutDirection.Ltr, this)
    path.addPath(Path().apply { addOutline(outline) }, rect.topLeft)
}

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
    // None under a stream, whose middle is no zero, or a heat map, which
    // stands on nothing.
    val grounded = !chart.y.centred && chart.layers.none { it.mark.kind == MarkKind.Cell }
    if (grounded && chart.horizontal) {
        val zero = if (chart.x.low <= 0.0 && chart.x.high >= 0.0) m.px(0.0) else if (frame.rtl) plot.right else plot.left
        drawLine(colours.axis, Offset(zero, plot.top), Offset(zero, plot.bottom), config.hairline)
    } else if (grounded) {
        val zero = if (chart.y.kind == AxisKind.Number && chart.y.low <= 0.0 && chart.y.high >= 0.0) m.py(0.0) else plot.bottom
        drawLine(colours.axis, Offset(plot.left, zero), Offset(plot.right, zero), config.hairline)
    }

    // Regions behind everything drawn from data, each in its selection-coloured
    // wash; their labels are drawn after the marks, where they are clear of them.
    chart.regions.forEach { region ->
        val rect = regionRect(region, m, chart)
        if (rect.width <= 0f || rect.height <= 0f) return@forEach
        val colour = region.style?.colour?.takeIf { it != Color.Unspecified } ?: colours.selection
        val path = Path()
        addShape(path, rect, config.corners.all)
        drawPath(path, colour, alpha = RegionOpacity)
        // At the high tier its edge is drawn too, so it is a shape and not
        // only a tint.
        if (config.patterns) drawPath(path, colour, style = Stroke(config.hairline))
    }

    // Rules' lines under the marks they are a reference for, so a point on a
    // target is the point and not the dash through it; their labels and pins
    // come after the marks, below.
    val inset = frame.gap / 2
    val pins = mutableListOf<Triple<Rect, RulePin, Color>>()
    chart.rules.forEachIndexed { index, rule ->
        val colour = rule.style?.colour?.takeIf { it != Color.Unspecified } ?: colours.axis
        val effect = (rule.style?.line ?: LinePattern.Dashed).effect(config.hairline * 2)
        val stroke = config.hairline * 1.5f
        rule.y?.let { value ->
            val y = m.py(value)
            drawLine(colour, Offset(plot.left, y), Offset(plot.right, y), stroke, pathEffect = effect)
        }
        rule.x?.let { value ->
            val x = m.px(value)
            drawLine(colour, Offset(x, plot.top), Offset(x, plot.bottom), stroke, pathEffect = effect)
            frame.rulePins.getOrNull(index)?.let { pin ->
                val width = pin.layout.size.width + inset * 2
                val bottom = plot.top - inset - pin.row * (frame.pinHeight + inset)
                val left = pinStart(x, width, plot.left, frame.width)
                val rect = Rect(left, bottom - frame.pinHeight, left + width, bottom)
                // Its stem, from the pin to the plot.
                drawLine(colour, Offset(x, rect.bottom), Offset(x, plot.top), stroke, pathEffect = effect)
                pins += Triple(rect, pin, colour)
            }
        }
    }

    val seriesCount = chart.model.series.size
    val pad = config.pointSize
    clipRect(plot.left - pad, plot.top - pad, plot.right + pad, plot.bottom + pad) {
        chart.layers.forEach { layer ->
            val look = lookOf(layer.slot, colours.series, config.patterns, layer.mark.style, layer.series.style)
            // A series on its way out fades as it goes.
            val alpha = alpha * layer.opacity
            if (alpha <= 0f) return@forEach
            when (layer.mark.kind) {
                MarkKind.Area -> drawArea(layer, look, m, config, alpha)
                MarkKind.Band -> drawArea(layer, look, m, config, alpha, band = true)
                MarkKind.Line -> {
                    drawLineMark(layer, look, m, config, alpha)
                    if (layer.mark.showPoints || (config.patterns && seriesCount > 1)) {
                        drawSymbols(layer, look.symbol, look.colour, m, config, alpha)
                    }
                }
                MarkKind.Bar -> drawBars(layer, look, chart, m, config, alpha)
                MarkKind.Point -> drawSymbols(layer, layer.mark.symbol ?: look.symbol, look.colour, m, config, alpha)
                MarkKind.Rectangle -> drawRectangles(layer, look, m, config, alpha)
                MarkKind.Stem -> drawStems(layer, look, m, config, alpha)
                MarkKind.Box -> drawBoxes(layer, look, chart, m, config, alpha)
                MarkKind.Cell -> drawCells(layer, frame, chart, m, config, alpha)
            }
        }
    }

    // The words over the marks, each where it is clear of them: a region's in
    // the first of its corners with no data under it, a rule's at the first
    // end of its line, above it or below, that has none. They sat at one
    // place whatever was there, and a line through "Capacity" crossed it out.
    val needed = frame.regionLabels.any { it != null } || frame.ruleLabels.any { it != null }
    val data = if (needed) obstaclesOf(chart, m, config) else null
    chart.regions.forEachIndexed { index, region ->
        val label = frame.regionLabels.getOrNull(index) ?: return@forEachIndexed
        val rect = regionRect(region, m, chart)
        val w = label.size.width.toFloat()
        val h = label.size.height.toFloat()
        val start = if (frame.rtl) rect.right - inset - w else rect.left + inset
        val end = if (frame.rtl) rect.left + inset else rect.right - inset - w
        val top = rect.top + inset / 2
        val foot = rect.bottom - inset / 2 - h
        val spots = listOf(Offset(start, top), Offset(end, top), Offset(start, foot), Offset(end, foot))
        val at = spots.firstOrNull { data?.hit(Rect(it, Size(w, h))) != true } ?: spots[0]
        drawText(label, colours.label, at)
    }
    chart.rules.forEachIndexed { index, rule ->
        val label = frame.ruleLabels.getOrNull(index) ?: return@forEachIndexed
        val value = rule.y ?: return@forEachIndexed
        val y = m.py(value)
        val w = label.size.width.toFloat()
        val h = label.size.height.toFloat()
        val lift = config.hairline * 2
        val end = if (frame.rtl) plot.left + inset else plot.right - w - inset
        val start = if (frame.rtl) plot.right - w - inset else plot.left + inset
        val spots = listOf(
            Offset(end, y - h - lift), Offset(end, y + lift), Offset(start, y - h - lift), Offset(start, y + lift),
        ).filter { it.y >= plot.top - h / 2 && it.y + h <= plot.bottom + h / 2 }.ifEmpty { listOf(Offset(end, y - h - lift)) }
        val at = spots.firstOrNull { data?.hit(Rect(it, Size(w, h))) != true } ?: spots[0]
        // On the page's ground, so it is read over whatever it could not miss.
        val backing = Path()
        addShape(backing, Rect(at.x - inset / 2, at.y, at.x + w + inset / 2, at.y + h), config.corners.all)
        drawPath(backing, colours.ground, alpha = 0.85f)
        drawText(label, colours.label, at)
    }
    pins.forEach { (rect, pin, colour) ->
        val capsule = Path()
        addShape(capsule, rect, config.pinShape)
        drawPath(capsule, colours.ground)
        // Edged in its rule's colour, so a pin is told by the line it heads.
        drawPath(capsule, colour, style = Stroke(config.hairline))
        drawText(pin.layout, colours.label, Offset(rect.left + inset, rect.top + (rect.height - pin.layout.size.height) / 2))
    }

    // The selection: a line through the selected place, and a ring round each
    // line's value there.
    if (selected != null) {
        val stroke = config.hairline * 1.5f
        if (chart.cells != null && !chart.horizontal) {
            // A heat map's column, outlined round its cells: a line down the
            // middle of them crossed out the values written in them.
            val band = cellBand(chart.placeAxis, chart.places, m.unitX)
            val x = m.px(selected)
            val ring = Path()
            addShape(ring, Rect(x - band / 2, plot.top, x + band / 2, plot.bottom), config.corners.all)
            drawPath(ring, colours.ground, style = Stroke(stroke * 3))
            drawPath(ring, colours.selection, style = Stroke(stroke * 2))
        } else if (chart.horizontal) {
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
    val pattern: Brush? = config.tiles.brush(look.fill, config.colours.patternInk, this, config.patternWeight)
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

private fun DrawScope.drawArea(layer: Layer, look: SeriesLook, m: Mapper, config: DrawConfig, alpha: Float, band: Boolean = false) {
    val base = layer.base ?: DoubleArray(layer.size)
    val stacked = layer.mark.stacking == ChartStacking.Stacked || layer.mark.stacking == ChartStacking.Normalised ||
        layer.mark.stacking == ChartStacking.Centred
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
    // ones tile, and are near solid. A band is a range, not a value, and has
    // no edge of its own: the line drawn through it is the value.
    fill(fillPath, look, config, alpha, opacity = if (band) BandOpacity else if (stacked) 0.85f else 0.3f)
    if (band) return
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
    val stacked = layer.mark.stacking == ChartStacking.Stacked || layer.mark.stacking == ChartStacking.Normalised ||
        layer.mark.stacking == ChartStacking.Centred
    // A stack is one bar, so only its outermost part each way is rounded: the
    // top of what stands above zero and the foot of what hangs below. Every
    // stack had square ends, and stacking is a bar's default, so nearly every
    // bar in a chart was square.
    // A floating bar stands on nothing, so both its ends are rounded.
    val outer = if (stacked && !layer.mark.floating) outermostOf(layer, chart) else null
    val corners = config.corners
    val path = Path()
    for (i in 0 until layer.size) {
        if (tops[i].isNaN()) continue
        val positive = tops[i] >= base[i]
        val rounded = outer == null || outer[i]
        if (lying) {
            val centre = m.py(places[i]) + offset
            val from = m.px(base[i])
            val to = m.px(tops[i])
            val rect = Rect(min(from, to), centre - thickness / 2, max(from, to), centre + thickness / 2)
            // The far end is rounded; which side that is depends on the sign
            // and on which way the language reads, both in `from` and `to`.
            val shape = if (!rounded) null else if (to >= from) corners.right else corners.left
            if (shape == null) path.addRect(rect) else addShape(path, rect, shape)
        } else {
            val direction = if (m.frame.rtl) -1f else 1f
            val centre = m.px(places[i]) + offset * direction
            val from = m.py(base[i])
            val to = m.py(tops[i])
            val rect = Rect(centre - thickness / 2, min(from, to), centre + thickness / 2, max(from, to))
            val shape = when {
                layer.mark.floating -> corners.all
                !rounded -> null
                positive -> corners.up
                else -> corners.down
            }
            if (shape == null) path.addRect(rect) else addShape(path, rect, shape)
        }
    }
    fill(path, look, config, alpha)
}

/**
 * Whether each of [layer]'s values is the outermost of its stack at its place:
 * the highest above zero, or the lowest below it, of every series of its mark.
 */
private fun outermostOf(layer: Layer, chart: ResolvedChart): BooleanArray {
    val lying = chart.horizontal
    val stack = chart.layers.filter { it.markIndex == layer.markIndex }
    val highest = HashMap<Double, Double>()
    val lowest = HashMap<Double, Double>()
    stack.forEach { other ->
        val places = if (lying) other.up else other.across
        val tops = if (lying) other.across else other.up
        val base = other.base ?: DoubleArray(other.size)
        for (i in 0 until other.size) {
            if (tops[i].isNaN() || tops[i] == base[i]) continue
            if (tops[i] > base[i]) highest[places[i]] = max(highest[places[i]] ?: tops[i], tops[i])
            else lowest[places[i]] = min(lowest[places[i]] ?: tops[i], tops[i])
        }
    }
    val places = if (lying) layer.up else layer.across
    val tops = if (lying) layer.across else layer.up
    val base = layer.base ?: DoubleArray(layer.size)
    return BooleanArray(layer.size) { i ->
        if (tops[i] >= base[i]) tops[i] == highest[places[i]] else tops[i] == lowest[places[i]]
    }
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
        addShape(path, Rect(min(x0, x1), min(y0, y1), max(x0, x1), max(y0, y1)), config.corners.all)
    }
    fill(path, look, config, alpha, opacity = 0.85f)
}

/** Each of [layer]'s stems: a line from its foot to its top, as wide as a line and as round at its ends. */
private fun DrawScope.drawStems(layer: Layer, look: SeriesLook, m: Mapper, config: DrawConfig, alpha: Float) {
    val base = layer.base ?: return
    val path = Path()
    for (i in 0 until layer.size) {
        if (layer.up[i].isNaN() || base[i].isNaN()) continue
        addStem(path, m.px(layer.across[i]), m.py(base[i]), m.py(layer.up[i]), config.lineWidth, capHalf = 0f)
    }
    drawPath(path, look.colour, alpha = alpha)
}

/**
 * Each of [layer]'s boxes: its whiskers, capped, out to its low and high; the
 * box from its lower quartile to its upper, filled as a bar is; its median
 * across it in the page's ground, which reads on a hatched box and at the high
 * tier alike; and its outliers as the series' points.
 */
private fun DrawScope.drawBoxes(layer: Layer, look: SeriesLook, chart: ResolvedChart, m: Mapper, config: DrawConfig, alpha: Float) {
    val base = layer.base ?: return
    val box = layer.box ?: return
    val band = cellBand(chart.placeAxis, layer.across, m.unitX)
    val thickness = band * ChartDefaults.BarFraction / layer.groupCount
    val direction = if (m.frame.rtl) -1f else 1f
    val offset = (layer.groupIndex - (layer.groupCount - 1) / 2f) * thickness * direction
    val whiskers = Path()
    val boxes = Path()
    val medians = Path()
    val halo = Path()
    val outliers = Path()
    for (i in 0 until layer.size) {
        if (layer.up[i].isNaN() || base[i].isNaN()) continue
        val centre = m.px(layer.across[i]) + offset
        val top = m.py(layer.up[i])
        val foot = m.py(base[i])
        addStem(whiskers, centre, top, m.py(box.high[i]), config.lineWidth, capHalf = thickness * WhiskerCapShare / 2)
        addStem(whiskers, centre, foot, m.py(box.low[i]), config.lineWidth, capHalf = thickness * WhiskerCapShare / 2)
        addShape(boxes, Rect(centre - thickness / 2, min(top, foot), centre + thickness / 2, max(top, foot)), config.corners.all)
        val median = m.py(layer.values[i])
        medians.addRect(Rect(centre - thickness / 2, median - config.lineWidth / 2, centre + thickness / 2, median + config.lineWidth / 2))
        box.outliers[i].forEach { value ->
            val at = Offset(centre, m.py(value))
            addSymbol(halo, look.symbol, at, config.pointSize + config.hairline * 3)
            addSymbol(outliers, look.symbol, at, config.pointSize)
        }
    }
    drawPath(whiskers, look.colour, alpha = alpha)
    fill(boxes, look, config, alpha, opacity = 0.85f)
    drawPath(medians, config.colours.ground, alpha = alpha)
    drawPath(halo, config.colours.ground, alpha = alpha)
    drawPath(outliers, look.colour, alpha = alpha)
}

/**
 * Each of [layer]'s cells, a full band each way less the ground between them,
 * shaded along the ramp by its value; with patterns on, edged and with its
 * value written in it, where it fits, so it is read by more than its shade.
 */
private fun DrawScope.drawCells(layer: Layer, frame: ChartFrame, chart: ResolvedChart, m: Mapper, config: DrawConfig, alpha: Float) {
    val scale = chart.cells ?: return
    val width = cellBand(chart.x, layer.across, m.unitX) - config.cellGap
    val height = cellBand(chart.y, layer.up, m.unitY) - config.cellGap
    if (width <= 0f || height <= 0f) return
    val labels = frame.cellLabels
    for (i in 0 until layer.size) {
        val value = layer.values[i]
        if (value.isNaN()) continue
        val centre = Offset(m.px(layer.across[i]), m.py(layer.up[i]))
        val rect = Rect(centre.x - width / 2, centre.y - height / 2, centre.x + width / 2, centre.y + height / 2)
        val shade = rampAt(config.colours.ramp, scale.fraction(value))
        val path = Path()
        addShape(path, rect, config.corners.all)
        drawPath(path, shade, alpha = alpha)
        if (config.patterns) {
            drawPath(path, config.colours.axis, alpha = alpha, style = Stroke(config.hairline))
            val label = labels.getOrNull(i) ?: continue
            if (label.size.width > width || label.size.height > height) continue
            val (first, second) = config.cellInks
            val ink = if (contrastOf(first, shade) >= contrastOf(second, shade)) first else second
            drawText(label, ink, Offset(centre.x - label.size.width / 2f, centre.y - label.size.height / 2f), alpha = alpha)
        }
    }
}

/** How far apart two colours' luminances are, as WCAG measures it. */
private fun contrastOf(a: Color, b: Color): Float {
    val la = a.luminance()
    val lb = b.luminance()
    return (max(la, lb) + 0.05f) / (min(la, lb) + 0.05f)
}

/** The band a cell or a box at [places] takes along [axis]: a category's whole, or the smallest step between places. */
private fun cellBand(axis: ResolvedAxis, places: DoubleArray, unit: Float): Float =
    if (axis.kind == AxisKind.Category) unit else unit * smallestStep(places).toFloat()

/** How wide a whisker's cap is, as a share of its box's width. */
private const val WhiskerCapShare = 0.5f

private fun DrawScope.drawLabels(frame: ChartFrame, m: Mapper, colours: ChartColours) {
    val plot = frame.plot
    val half = frame.gap / 2
    frame.xLabels.forEach { label ->
        val w = label.layout.size.width.toFloat()
        val h = label.layout.size.height.toFloat()
        val centre = m.px(label.value)
        if (centre < plot.left - 1f || centre > plot.right + 1f) return@forEach
        val left = (centre - w / 2).coerceIn(0f, max(0f, frame.width - w))
        val top = if (frame.xAxisAtBottom) plot.bottom + half else plot.top - frame.pinBand - half - h
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

/**
 * Where a chart's data is drawn, for a label to keep clear of: each point of a
 * line, an area's or a band's top and every few pixels between, each symbol,
 * and each bar's box.
 */
private class Obstacles(private val points: List<Offset>, private val boxes: List<Rect>) {
    fun hit(rect: Rect): Boolean = points.any { rect.contains(it) } || boxes.any { it.overlaps(rect) }
}

private fun obstaclesOf(chart: ResolvedChart, m: Mapper, config: DrawConfig): Obstacles {
    val points = ArrayList<Offset>()
    val boxes = ArrayList<Rect>()
    val step = config.pointSize
    chart.layers.forEach { layer ->
        when (layer.mark.kind) {
            MarkKind.Bar -> {
                val lying = chart.horizontal
                val places = if (lying) layer.up else layer.across
                val tops = if (lying) layer.across else layer.up
                val base = layer.base ?: DoubleArray(layer.size)
                val unit = if (lying) m.unitY else m.unitX
                val band = if (chart.placeAxis.kind == AxisKind.Category) unit else unit * smallestStep(places).toFloat()
                val half = band * ChartDefaults.BarFraction / 2
                for (i in 0 until layer.size) {
                    if (tops[i].isNaN()) continue
                    boxes += if (lying) {
                        val c = m.py(places[i])
                        Rect(min(m.px(base[i]), m.px(tops[i])), c - half, max(m.px(base[i]), m.px(tops[i])), c + half)
                    } else {
                        val c = m.px(places[i])
                        Rect(c - half, min(m.py(base[i]), m.py(tops[i])), c + half, max(m.py(base[i]), m.py(tops[i])))
                    }
                }
            }
            MarkKind.Rectangle -> {
                val ends = layer.acrossEnd
                val tops = layer.upEnd
                if (ends != null && tops != null) {
                    for (i in 0 until layer.size) {
                        val x0 = m.px(layer.across[i])
                        val x1 = m.px(ends[i])
                        val y0 = m.py(layer.up[i])
                        val y1 = m.py(tops[i])
                        boxes += Rect(min(x0, x1), min(y0, y1), max(x0, x1), max(y0, y1))
                    }
                }
            }
            else -> {
                var last: Offset? = null
                for (i in 0 until layer.size) {
                    val up = layer.up[i]
                    if (up.isNaN() || layer.across[i].isNaN()) {
                        last = null
                        continue
                    }
                    val at = Offset(m.px(layer.across[i]), m.py(up))
                    val from = last
                    if (from != null && layer.mark.kind != MarkKind.Point) {
                        val gaps = ((at - from).getDistance() / step).toInt()
                        for (k in 1 until gaps) points += from + (at - from) * (k / gaps.toFloat())
                    }
                    points += at
                    last = at
                }
            }
        }
    }
    return Obstacles(points, boxes)
}
