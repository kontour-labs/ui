package io.kontour.ui.charts

import io.kontour.ui.theme.trailingCornersOnly
import io.kontour.ui.theme.topCornersOnly
import io.kontour.ui.theme.leadingCornersOnly
import io.kontour.ui.theme.bottomCornersOnly
import io.kontour.ui.foundation.PatternBrushes
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.translate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import kotlin.math.abs
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

    /** Paths drawn into and emptied again for each bar that is cut from its stack: see [addCut]. */
    val cutting = CutPaths()
}

/**
 * Three paths kept for cutting a part of a stack from the stack's outline, so
 * that a frame of a hundred bars does not make three hundred: emptied and
 * filled again for each part. Drawn into on the main thread only, a chart's own.
 */
internal class CutPaths {
    val whole = Path()
    val part = Path()
    val cut = Path()
}

/**
 * The theme's shape, rounded at the far end of a bar each way, or all round.
 * From `Theme.shapes`, so a chart's corners are the squircle every other
 * component has, and a brand that rounds less rounds its charts less. Read
 * left to right whatever the language: a bar's far end is worked out already.
 */
internal class Corners(private val base: CornerBasedShape) {
    val up: Shape = base.topCornersOnly()
    val down: Shape = base.bottomCornersOnly()
    val right: Shape = base.trailingCornersOnly()
    val left: Shape = base.leadingCornersOnly()
    val all: Shape = base

    /** How far a corner reaches into a bar [size] across, in pixels. */
    fun reach(size: Size, density: Density): Float = base.topStart.toPx(size, density)

    // A bar's far end [open] of the way to rounded: square under another
    // bar, rounded clear of one, and in between while it comes out from
    // under one. See `openEndsOf`.
    fun up(open: Float): Shape? = eased(open, up) { it.topCornersOnly() }
    fun down(open: Float): Shape? = eased(open, down) { it.bottomCornersOnly() }
    fun right(open: Float): Shape? = eased(open, right) { it.trailingCornersOnly() }
    fun left(open: Float): Shape? = eased(open, left) { it.leadingCornersOnly() }

    private inline fun eased(open: Float, whole: Shape, cut: (CornerBasedShape) -> Shape): Shape? = when {
        open <= NoCorner -> null
        open >= 1f - NoCorner -> whole
        else -> cut(base.copy(Scaled(base.topStart, open), Scaled(base.topEnd, open), Scaled(base.bottomEnd, open), Scaled(base.bottomStart, open)))
    }
}

/** [size], [by] of the way: a corner part rounded. */
private class Scaled(private val size: CornerSize, private val by: Float) : CornerSize {
    override fun toPx(shapeSize: Size, density: Density): Float = size.toPx(shapeSize, density) * by
}

/** Below this much of the way to rounded, an end is drawn square: a corner nobody could see. */
private const val NoCorner = 0.01f

/** How strongly a region is washed in its colour: a ground to read marks over, not a mark. */
private const val RegionOpacity = 0.12f

/** How strongly a band is filled: lighter than an area, which is a value, where a band is a range. */
private const val BandOpacity = 0.22f

/** How strongly a marked range is washed: a little more than a region, which it may lie over. */
private const val MarkedOpacity = 0.16f

/**
 * A range the reader has marked, in the chart's own units: [from] and [to]
 * across, and for a box [upFrom] and [upTo] up. [whole] for one drawn out to
 * the whole of the places at its ends, as one set by its values is among bars
 * and categories; a drag's is drawn where the drag was.
 */
internal class MarkedRange(val from: Double, val to: Double, val upFrom: Double?, val upTo: Double?, val whole: Boolean = false)

/** How strongly a selected place among bars is washed: enough to find, not enough to tint the bars it is behind. */
private const val SelectionWashOpacity = 0.1f

/**
 * Whether a selected place is shown as a wash the width of its place, behind
 * the bars or boxes standing there, rather than as a line through them. A heat
 * map's column is outlined instead, and lying bars take no selection.
 */
internal val ResolvedChart.selectsBands: Boolean
    get() = !horizontal && cells == null && layers.any { it.mark.kind == MarkKind.Bar || it.mark.kind == MarkKind.Box }

/**
 * [shape] at [rect], added to [path]; nothing for a rect with no area. Added
 * straight from its outline, moved to [rect]: by way of a path of its own, a
 * frame made one for every bar it drew.
 */
private fun DrawScope.addShape(path: Path, rect: Rect, shape: Shape) {
    if (rect.width <= 0f || rect.height <= 0f) return
    when (val outline = shape.createOutline(rect.size, LayoutDirection.Ltr, this)) {
        is Outline.Rectangle -> path.addRect(outline.rect.translate(rect.topLeft))
        is Outline.Rounded -> path.addRoundRect(outline.roundRect.translate(rect.topLeft))
        is Outline.Generic -> path.addPath(outline.path, rect.topLeft)
    }
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
    /** The chart a move is heading to, which decides where the words go: see below. */
    settled: ResolvedChart = chart,
    /** A range marked on it, if one is. */
    marked: MarkedRange? = null,
) {
    // And up the end axis, for what is on it: see [Mapper.up].
    val m = Mapper(frame, chart.x, chart.y, chart.yEnd)
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
        val rect = regionRect(region, m.up(region.onEnd), chart)
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
            val y = m.up(rule.onEnd).py(value)
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

    // A range marked by the reader, behind the marks as a region is, and
    // edged in the selection's colour at every tier: a region is the chart's
    // own, washed only, and this is the reader's, which they will want to see
    // the bounds of as they drag them. One set by its values takes the whole
    // of the places at its ends among bars and categories; a drag's ends are
    // where the drag's were.
    if (marked != null) {
        val half = if (marked.whole) cellBand(chart.placeAxis, chart.step, m.unitX) / 2 else 0f
        val a = m.px(marked.from)
        val b = m.px(marked.to)
        val upFrom = marked.upFrom
        val upTo = marked.upTo
        val top = if (upFrom != null && upTo != null) min(m.py(upFrom), m.py(upTo)) else plot.top
        val bottom = if (upFrom != null && upTo != null) max(m.py(upFrom), m.py(upTo)) else plot.bottom
        val rect = Rect(
            (min(a, b) - half).coerceAtLeast(plot.left),
            top.coerceAtLeast(plot.top),
            (max(a, b) + half).coerceAtMost(plot.right),
            bottom.coerceAtMost(plot.bottom),
        )
        val shape = Path()
        addShape(shape, rect, config.corners.all)
        drawPath(shape, colours.selection, alpha = MarkedOpacity)
        drawPath(shape, colours.selection, style = Stroke(config.hairline * 1.5f))
    }

    // A selected place among bars is washed behind them, the width of its
    // place: a line through the middle of a bar, or of a group of them, cut
    // the bars it was about in two and was all but lost in a wide one.
    if (selected != null && chart.selectsBands) {
        val band = cellBand(chart.placeAxis, chart.step, m.unitX)
        val x = m.px(selected)
        val wash = Path()
        addShape(wash, Rect(x - band / 2, plot.top, x + band / 2, plot.bottom), config.corners.all)
        // In the labels' ink, which stands off the ground by as much in a
        // dark theme as in a light one: the axis's grey all but vanished on
        // a dark ground.
        drawPath(wash, colours.label, alpha = SelectionWashOpacity)
        // At the high tier its edge too, in the selection's colour, so the
        // place is found by more than a tint.
        if (config.patterns) drawPath(wash, colours.selection, style = Stroke(config.hairline * 1.5f))
    }

    val seriesCount = chart.model.series.size
    val pad = config.pointSize
    // Every bar layer's width and open ends, worked out once this frame for
    // all of them: see [BarFrame].
    val bars = BarFrame(chart, m, config, this)
    clipRect(plot.left - pad, plot.top - pad, plot.right + pad, plot.bottom + pad) {
        chart.layers.forEach { layer ->
            val look = lookOf(layer.slot, colours.series, config.patterns, layer.mark.style, layer.series.style)
            // A series on its way out fades as it goes.
            val alpha = alpha * layer.opacity
            if (alpha <= 0f) return@forEach
            val mapped = m.up(layer.mark.onEnd)
            when (layer.mark.kind) {
                MarkKind.Area -> drawArea(layer, look, mapped, config, alpha)
                MarkKind.Band -> drawArea(layer, look, mapped, config, alpha, band = true)
                MarkKind.Line -> {
                    drawLineMark(layer, look, mapped, config, alpha)
                    if (layer.mark.showPoints || (config.patterns && seriesCount > 1)) {
                        drawSymbols(layer, look.symbol, look.colour, mapped, config, alpha)
                    }
                }
                MarkKind.Bar -> drawBars(layer, look, chart, mapped, config, alpha, bars)
                MarkKind.Point -> drawSymbols(layer, layer.mark.symbol ?: look.symbol, look.colour, mapped, config, alpha)
                MarkKind.Rectangle -> drawRectangles(layer, look, mapped, config, alpha)
                MarkKind.Stem -> drawStems(layer, look, mapped, config, alpha)
                MarkKind.Box -> drawBoxes(layer, look, chart, mapped, config, alpha)
                MarkKind.Cell -> drawCells(layer, frame, chart, mapped, config, alpha)
            }
        }
    }

    // The words over the marks, each where it is clear of them: a region's in
    // the first of its corners with no data under it, a rule's at the first
    // end of its line, above it or below, that has none. They sat at one
    // place whatever was there, and a line through "Capacity" crossed it out.
    //
    // **Which** place is worked out from the chart a move is heading to, not
    // from each frame of the move: on each frame the data under the places
    // changed, and the label hopped between them until the data settled.
    // Reported as the energy app's "Average" flickering when its span changed,
    // before it moved to the left. Now it goes to where it will stay as the
    // move begins, and only rides its line up or down on the way.
    val needed = frame.regionLabels.any { it != null } || frame.ruleLabels.any { it != null }
    val settledMapper = if (settled === chart) m else Mapper(frame, settled.x, settled.y, settled.yEnd)
    val data = if (needed) obstaclesOf(settled, settledMapper, config) else null
    fun regionSpots(rect: Rect, w: Float, h: Float): List<Offset> {
        val start = if (frame.rtl) rect.right - inset - w else rect.left + inset
        val end = if (frame.rtl) rect.left + inset else rect.right - inset - w
        val top = rect.top + inset / 2
        val foot = rect.bottom - inset / 2 - h
        return listOf(Offset(start, top), Offset(end, top), Offset(start, foot), Offset(end, foot))
    }
    chart.regions.forEachIndexed { index, region ->
        val label = frame.regionLabels.getOrNull(index) ?: return@forEachIndexed
        val w = label.size.width.toFloat()
        val h = label.size.height.toFloat()
        val target = settled.regions.getOrNull(index) ?: region
        val goal = regionSpots(regionRect(target, settledMapper.up(target.onEnd), settled), w, h)
        val choice = goal.indexOfFirst { data?.hit(Rect(it, Size(w, h))) != true }.coerceAtLeast(0)
        val at = regionSpots(regionRect(region, m.up(region.onEnd), chart), w, h)[choice]
        drawText(label, colours.label, at)
    }
    chart.rules.forEachIndexed { index, rule ->
        val label = frame.ruleLabels.getOrNull(index) ?: return@forEachIndexed
        val value = rule.y ?: return@forEachIndexed
        val y = m.up(rule.onEnd).py(value)
        val w = label.size.width.toFloat()
        val h = label.size.height.toFloat()
        val lift = config.hairline * 2
        val end = if (frame.rtl) plot.left + inset else plot.right - w - inset
        val start = if (frame.rtl) plot.right - w - inset else plot.left + inset
        fun spotsAt(y: Float) = listOf(
            Offset(end, y - h - lift), Offset(end, y + lift), Offset(start, y - h - lift), Offset(start, y + lift),
        )
        fun inPlot(spot: Offset) = spot.y >= plot.top - h / 2 && spot.y + h <= plot.bottom + h / 2
        val target = settled.rules.getOrNull(index) ?: rule
        val goal = spotsAt(target.y?.let { settledMapper.up(target.onEnd).py(it) } ?: y)
        val choice = goal.indices.firstOrNull { inPlot(goal[it]) && data?.hit(Rect(goal[it], Size(w, h))) != true }
            ?: goal.indices.firstOrNull { inPlot(goal[it]) }
            ?: 0
        val at = spotsAt(y)[choice]
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

    // The selection: a line through the selected place, unless it is washed
    // behind bars above, and a ring round each line's value there.
    if (selected != null) {
        val stroke = config.hairline * 1.5f
        if (chart.cells != null && !chart.horizontal) {
            // A heat map's column, outlined round its cells: a line down the
            // middle of them crossed out the values written in them.
            val band = cellBand(chart.placeAxis, chart.step, m.unitX)
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
            if (!chart.selectsBands) drawLine(colours.selection, Offset(x, plot.top), Offset(x, plot.bottom), stroke)
            chart.layers.filter { it.mark.kind == MarkKind.Line || it.mark.kind == MarkKind.Area }.forEach { layer ->
                // Only where the line is: a forecast from two o'clock had a
                // ring at seven, at its first value, held flat back to there
                // by the reading below, on nothing at all. And not on an area
                // with nothing in it at the place, whose ring sat on the edge
                // of the one under it: *"a phantom yellow dot not on a line"*.
                if (layer.size == 0 || selected < layer.across.min() || selected > layer.across.max()) return@forEach
                if (layer.mark.kind == MarkKind.Area && layer.mark.stacking != ChartStacking.None && layer.mark.stacking != ChartStacking.Grouped) {
                    val at = layer.across.indexOfFirst { it == selected }
                    if (at >= 0 && (layer.values[at] == 0.0 || layer.values[at].isNaN())) return@forEach
                }
                val value = sampleAt(layer.across, layer.up, selected)
                if (value.isNaN()) return@forEach
                val look = lookOf(layer.slot, colours.series, config.patterns, layer.mark.style, layer.series.style)
                val centre = Offset(x, m.up(layer.mark.onEnd).py(value))
                drawCircle(colours.ground, config.pointSize * 0.9f, centre)
                drawCircle(look.colour, config.pointSize * 0.6f, centre)
            }
        }
    }

    drawLabels(frame, m, colours)
}

private fun DrawScope.fill(path: Path, look: SeriesLook, config: DrawConfig, alpha: Float, opacity: Float = 1f) {
    drawPath(path, look.colour, alpha = alpha * opacity)
    val pattern: Brush? = config.tiles.brush(look.pattern, config.colours.patternInk, this, config.patternWeight)
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

/**
 * What every bar layer of one frame is drawn with, worked out once for the
 * frame: the band a place gives its bars, each layer's thickness and how far
 * its corners reach, and how open each layer's ends are.
 *
 * How open a part's end is was found for each layer, and again for every
 * other part of its stack as each part was drawn, building its lookups anew
 * each time: a stack of n parts did the work n times over, every frame.
 */
private class BarFrame(private val chart: ResolvedChart, private val m: Mapper, config: DrawConfig, private val density: Density) {
    private val corners = config.corners
    private val lying = chart.horizontal

    /** The band one place gives its bars: a category's, or the chart's least step along a continuous axis. */
    val band: Float = cellBand(chart.placeAxis, chart.step, if (lying) m.unitY else m.unitX)

    fun thickness(layer: Layer): Float = band * ChartDefaults.BarFraction * layer.groupWidth

    fun reach(layer: Layer): Float = corners.reach(Size(thickness(layer), thickness(layer)), density)

    /** Where a value along [layer]'s value axis is drawn, in pixels. */
    fun at(layer: Layer): (Double) -> Float {
        val mapped = m.up(layer.mark.onEnd)
        return if (lying) mapped::px else mapped::py
    }

    private val opens = HashMap<Layer, FloatArray>()
    private val indices = HashMap<Layer, Map<Double, Int>>()

    /** How open each of [layer]'s far ends is: see [openEndsOf]. */
    fun openEnds(layer: Layer): FloatArray = opens.getOrPut(layer) { openEndsOf(layer, chart, reach(layer), at(layer), ::placeIndex) }

    /** Each of [layer]'s places' index, its first where one repeats. */
    fun placeIndex(layer: Layer): Map<Double, Int> = indices.getOrPut(layer) { placeIndexOf(layer, lying) }
}

/** Each of [layer]'s places' index, its first where one repeats: up for lying bars, across for any other. */
private fun placeIndexOf(layer: Layer, lying: Boolean): Map<Double, Int> {
    val places = if (lying) layer.up else layer.across
    return HashMap<Double, Int>(places.size * 2).apply { places.forEachIndexed { j, place -> getOrPut(place) { j } } }
}

private fun DrawScope.drawBars(layer: Layer, look: SeriesLook, chart: ResolvedChart, m: Mapper, config: DrawConfig, alpha: Float, bars: BarFrame) {
    val lying = chart.horizontal
    val places = if (lying) layer.up else layer.across
    val tops = if (lying) layer.across else layer.up
    val base = layer.base ?: DoubleArray(layer.size)
    val band = bars.band
    val thickness = bars.thickness(layer)
    val offset = band * ChartDefaults.BarFraction * layer.groupOffset
    // A stack is one bar, so only its outermost part each way is rounded: the
    // top of what stands above zero and the foot of what hangs below. Every
    // stack had square ends, and stacking is a bar's default, so nearly every
    // bar in a chart was square.
    // A floating bar stands on nothing, so both its ends are rounded.
    val corners = config.corners
    val reach = bars.reach(layer)
    val at = bars.at(layer)
    val open = if (!layer.mark.floating) bars.openEnds(layer) else null
    val stack = if (!layer.mark.floating) StackEnds(layer, chart, reach, at, bars::openEnds, bars::placeIndex) else null
    val path = Path()
    for (i in 0 until layer.size) {
        if (tops[i].isNaN()) continue
        val positive = tops[i] >= base[i]
        val opened = open?.get(i) ?: 1f
        // A part under another, near enough to the stack's end to be inside
        // its corner, is cut from the stack's rounded outline: see [StackEnds].
        val end = stack?.endOver(i, opened)
        if (lying) {
            val centre = m.py(places[i]) + offset
            val from = m.px(base[i])
            val to = m.px(tops[i])
            val rect = Rect(min(from, to), centre - thickness / 2, max(from, to), centre + thickness / 2)
            // The far end is rounded; which side that is depends on the sign
            // and on which way the language reads, both in `from` and `to`.
            if (end != null) {
                val far = m.px(end.value)
                val whole = Rect(min(from, far), rect.top, max(from, far), rect.bottom)
                addCut(path, rect, whole, if (far >= from) corners.right(end.open) else corners.left(end.open), config.cutting)
            } else {
                val shape = if (to >= from) corners.right(opened) else corners.left(opened)
                if (shape == null) path.addRect(rect) else addShape(path, rect, shape)
            }
        } else {
            val direction = if (m.frame.rtl) -1f else 1f
            val centre = m.px(places[i]) + offset * direction
            val from = m.py(base[i])
            val to = m.py(tops[i])
            val rect = Rect(centre - thickness / 2, min(from, to), centre + thickness / 2, max(from, to))
            if (end != null) {
                val far = m.py(end.value)
                val whole = Rect(rect.left, min(from, far), rect.right, max(from, far))
                addCut(path, rect, whole, if (positive) corners.up(end.open) else corners.down(end.open), config.cutting)
            } else {
                val shape = when {
                    layer.mark.floating -> corners.all
                    positive -> corners.up(opened)
                    else -> corners.down(opened)
                }
                if (shape == null) path.addRect(rect) else addShape(path, rect, shape)
            }
        }
    }
    fill(path, look, config, alpha)
}

/**
 * [part] cut from [whole] drawn in [shape], added to [path]: a part of a stack
 * with the stack's corners where it reaches them. Cut in [scratch]'s paths,
 * emptied first, rather than three new ones a part.
 */
private fun DrawScope.addCut(path: Path, part: Rect, whole: Rect, shape: Shape?, scratch: CutPaths) {
    if (part.width <= 0f || part.height <= 0f) return
    if (shape == null) {
        path.addRect(part)
        return
    }
    scratch.whole.rewind()
    addShape(scratch.whole, whole, shape)
    scratch.part.rewind()
    scratch.part.addRect(part)
    scratch.cut.rewind()
    scratch.cut.op(scratch.part, scratch.whole, PathOperation.Intersect)
    path.addPath(scratch.cut)
}

/**
 * Where the stack a part of [layer] is in ends, beyond the part itself:
 * followed from part to part standing on it, end to foot and over most of its
 * width, to the last, whose end is the stack's and is rounded as clear as
 * that part is.
 *
 * Only the outermost part of a stack had its corners, so a thin one, a share
 * of two per cent on top of a stack of shares, was a cap rounded tighter than
 * itself on a square column: *"corner rounding and spacing is a bit off"*. A
 * part within a corner's reach of the stack's end is now cut from the whole
 * stack's rounded outline, so the curve runs on down through it.
 */
private class StackEnds(
    private val layer: Layer,
    chart: ResolvedChart,
    private val reach: Float,
    private val at: (Double) -> Float,
    /** How open a layer's ends are, and where its places are: the frame's, worked out once for it. */
    private val openEnds: (Layer) -> FloatArray,
    placeIndex: (Layer) -> Map<Double, Int>,
) {
    private val lying = chart.horizontal
    private val mates = chart.layers.filter { other ->
        other !== layer && other.markIndex == layer.markIndex && other.mark.kind == MarkKind.Bar &&
            other.base != null && other.opacity > 0f && !other.mark.floating
    }
    private val indices = mates.map(placeIndex)

    /** A width's overlap of [mate] and [over], as a share of [over]'s width. */
    private fun overlap(over: Layer, mate: Layer): Float {
        val start = max(over.groupOffset - over.groupWidth / 2, mate.groupOffset - mate.groupWidth / 2)
        val end = min(over.groupOffset + over.groupWidth / 2, mate.groupOffset + mate.groupWidth / 2)
        return ((end - start) / over.groupWidth).coerceIn(0f, 1f)
    }

    class End(val value: Double, val open: Float)

    /** The stack's end over the [i]th part, whose own end is [opened] clear; `null` where it is the end, or the end is past a corner's reach. */
    fun endOver(i: Int, opened: Float): End? {
        if (opened >= 0.5f || mates.isEmpty()) return null
        val places = if (lying) layer.up else layer.across
        val tops = if (lying) layer.across else layer.up
        val foot = layer.base?.get(i) ?: 0.0
        val rising = tops[i] > foot
        var over = layer
        var end = tops[i]
        var open = opened
        repeat(mates.size) {
            if (open >= 0.5f) return@repeat
            var next = -1
            var nextIndex = -1
            for (k in mates.indices) {
                val j = indices[k][places[i]] ?: continue
                val mate = mates[k]
                val mateTop = (if (lying) mate.across else mate.up)[j]
                val mateFoot = mate.base!![j]
                if (mateTop.isNaN() || mateTop == mateFoot || (mateTop > mateFoot) != rising) continue
                if (abs(at(mateFoot) - at(end)) > 1f || overlap(over, mate) < 0.5f) continue
                next = k
                nextIndex = j
                break
            }
            if (next < 0) return@repeat
            over = mates[next]
            end = (if (lying) over.across else over.up)[nextIndex]
            open = openEnds(over)[nextIndex]
        }
        if (over === layer || abs(at(end) - at(tops[i])) >= reach) return null
        return End(end, open)
    }
}

/**
 * How clear each of [layer]'s far ends is, from 0, under another bar of its
 * mark standing on it, to 1, with nothing on it: the outermost part of a stack,
 * above zero or below it, and every bar of a group, are 1. The end is rounded
 * that much of the way, so its corners ease in and out as bars move.
 *
 * Found from where the bars are in this frame, rather than from how the mark
 * stacks: moving from a stack to bars side by side, every part was rounded in
 * the first frame, where they were all still stacked, and notched where each
 * stood on the next; and back again, a part was squared the moment the move
 * began, while it still stood alone.
 *
 * And by how much rather than whether, since bars side by side moving into a
 * stack only touched in its last frame, and every part kept a rounded top the
 * whole way and squared off once it had stopped: *"all the sections look like
 * the top stack, and they only snap into position once they stop"*. How much
 * of an end is covered is how much of its width the bar on it overlaps, how
 * near that bar's foot is, and how tall that bar is, each against how far a
 * corner reaches, [reach]: a bar on its way out of a stack lets the end under
 * it round as it shrinks, rather than the moment it is gone. [at] is where a
 * value along the value axis is drawn, in pixels, and [placeIndex] each
 * layer's index at each of its places.
 */
internal fun openEndsOf(
    layer: Layer,
    chart: ResolvedChart,
    reach: Float,
    at: (Double) -> Float,
    placeIndex: (Layer) -> Map<Double, Int> = { placeIndexOf(it, chart.horizontal) },
): FloatArray {
    val lying = chart.horizontal
    val corner = max(reach, 1f)
    // Only the bars over some of this one's width: side by side, neither is over the other.
    val others = chart.layers.filter { other ->
        other.markIndex == layer.markIndex && other !== layer && other.opacity > 0f && other.base != null &&
            abs(layer.groupOffset - other.groupOffset) < (layer.groupWidth + other.groupWidth) / 2
    }
    // Each one's index at each place, found once rather than for every bar.
    val indices = others.map(placeIndex)
    // How much of this one's width each overlaps.
    val overlaps = others.map { other ->
        val start = max(layer.groupOffset - layer.groupWidth / 2, other.groupOffset - other.groupWidth / 2)
        val end = min(layer.groupOffset + layer.groupWidth / 2, other.groupOffset + other.groupWidth / 2)
        ((end - start) / layer.groupWidth).coerceIn(0f, 1f)
    }
    val places = if (lying) layer.up else layer.across
    val tops = if (lying) layer.across else layer.up
    return FloatArray(layer.size) { i ->
        val top = tops[i]
        val foot = layer.base?.get(i) ?: 0.0
        val rising = top > foot
        val end = at(top)
        var covered = 0f
        for (k in others.indices) {
            val other = others[k]
            val j = indices[k][places[i]] ?: continue
            val otherTops = if (lying) other.across else other.up
            val otherTop = otherTops[j]
            val otherFoot = other.base!![j]
            if (otherTop.isNaN() || otherTop == otherFoot) continue
            // Going on the same way, and beyond this one rather than under it.
            if ((otherTop > otherFoot) != rising) continue
            if (rising != (otherTop + otherFoot > top + foot)) continue
            val near = 1f - (abs(at(otherFoot) - end) / corner).coerceAtMost(1f)
            val tall = (abs(at(otherTop) - at(otherFoot)) / corner).coerceAtMost(1f)
            covered = max(covered, overlaps[k] * near * tall)
        }
        1f - covered
    }
}

/** The smallest step between neighbouring [places]: a heat map's cells' up a continuous axis. */
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
    val band = cellBand(chart.placeAxis, chart.step, m.unitX)
    val thickness = band * ChartDefaults.BarFraction * layer.groupWidth
    val direction = if (m.frame.rtl) -1f else 1f
    val offset = band * ChartDefaults.BarFraction * layer.groupOffset * direction
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
    val width = cellBand(chart.x, chart.step, m.unitX) - config.cellGap
    val height = cellBand(chart.y, smallestStep(layer.up), m.unitY) - config.cellGap
    if (width <= 0f || height <= 0f) return
    val labels = frame.cellLabels
    // One path, emptied for each cell, which is drawn in a shade of its own.
    val path = Path()
    for (i in 0 until layer.size) {
        val value = layer.values[i]
        if (value.isNaN()) continue
        val centre = Offset(m.px(layer.across[i]), m.py(layer.up[i]))
        val rect = Rect(centre.x - width / 2, centre.y - height / 2, centre.x + width / 2, centre.y + height / 2)
        val shade = rampAt(config.colours.ramp, scale.fraction(value))
        path.rewind()
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

/** The band a place takes along [axis], [unit] pixels to one of its units: a category's whole, or [step] along a continuous one. */
internal fun cellBand(axis: ResolvedAxis, step: Double, unit: Float): Float =
    if (axis.kind == AxisKind.Category) unit else unit * step.toFloat()

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
    // The end axis's, down the other edge.
    val endMapper = m.up(onEnd = true)
    frame.yEndLabels.forEach { label ->
        val w = label.layout.size.width.toFloat()
        val h = label.layout.size.height.toFloat()
        val centre = endMapper.py(label.value)
        if (centre < plot.top - 1f || centre > plot.bottom + 1f) return@forEach
        val top = (centre - h / 2).coerceIn(0f, max(0f, frame.height - h))
        val onLeft = frame.yAxisAtStart == frame.rtl
        val left = if (onLeft) plot.left - half - w else plot.right + half
        drawText(label.layout, colours.label, Offset(left, top))
    }
    frame.yTitle?.let { title ->
        val left = if (frame.yAxisAtStart != frame.rtl) 0f else frame.width - title.size.width
        drawText(title, colours.label, Offset(left, 0f))
    }
    frame.yEndTitle?.let { title ->
        val left = if (frame.yAxisAtStart == frame.rtl) 0f else frame.width - title.size.width
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

private fun obstaclesOf(chart: ResolvedChart, chartMapper: Mapper, config: DrawConfig): Obstacles {
    val points = ArrayList<Offset>()
    val boxes = ArrayList<Rect>()
    val step = config.pointSize
    chart.layers.forEach { layer ->
        val m = chartMapper.up(layer.mark.onEnd)
        when (layer.mark.kind) {
            MarkKind.Bar -> {
                val lying = chart.horizontal
                val places = if (lying) layer.up else layer.across
                val tops = if (lying) layer.across else layer.up
                val base = layer.base ?: DoubleArray(layer.size)
                val band = cellBand(chart.placeAxis, chart.step, if (lying) m.unitY else m.unitX)
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
