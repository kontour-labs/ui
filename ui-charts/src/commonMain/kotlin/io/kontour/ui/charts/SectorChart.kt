package io.kontour.ui.charts

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.LayoutScopeMarker
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.kontour.ui.foundation.ContentScope
import io.kontour.ui.input.focusRing
import io.kontour.ui.interaction.rememberDetentTicker
import io.kontour.ui.overlay.Tooltip
import io.kontour.ui.theme.Theme
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The parts of a whole, as sectors of a circle: a pie, or with an
 * [innerRadius], a donut with room in the middle for the total.
 *
 * ```kotlin
 * SectorChart(innerRadius = 0.6f, modifier = Modifier.size(200.dp)) {
 *     sectors(spending, value = { it.amount }, series = { ChartSeries(it.category) })
 * }
 * ```
 *
 * A sector chart is for a few parts of one whole — up to five or six reads at a
 * glance. For more, or to compare parts across wholes, a [Chart] of bars is
 * the better chart; for the same reason, a sector too thin to see is still
 * drawn, but small ones are best gathered into one "Other" before they get
 * here.
 *
 * Sectors start at the top and go clockwise, or anticlockwise in a
 * right-to-left language, in the order the content gives them, each in its
 * series' colour and, with [showPatterns], its fill. A screen reader hears the
 * whole first, the kind of chart and every part and its share, and then each
 * sector on its own.
 *
 * ### Selection
 *
 * Give it a [selection] and a sector is chosen as a [Chart]'s place is: by a
 * tap on it, a pointer over it, the arrow keys round the circle in the order it
 * is drawn, or a screen reader on its node. The chosen sector stands out from
 * the centre and is outlined, so it is told apart by its shape as well as its
 * ink, and the tooltip says its value and share.
 *
 * @param enabled Off, the chart cannot be selected from, and is still read.
 * @param selection Which sector is chosen, by its series. Null, the default, is
 *   a picture with a description.
 * @param innerRadius The hole, as a fraction of the radius: `0` for a pie,
 *   about `0.6` for a donut.
 * @param contentDescription What the whole is, said before its parts: "Spending
 *   in March".
 * @param showLegend Whether the parts are named beside the chart.
 * @param showPatterns Whether sectors are told apart by fill as well as colour.
 * @param showSelectionTooltip Whether the chosen sector's value is shown in a
 *   tooltip beside it.
 * @param animateData Whether the sectors sweep round into place, and move to
 *   new shares when the data changes.
 * @param strings The words the chart says on its own.
 * @param tooltip What the tooltip shows, in place of the sector's name, value
 *   and share.
 * @param centre What goes in a donut's hole: the total, most often.
 * @param content The sectors.
 */
@Composable
fun SectorChart(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selection: ChartSelectionState<ChartSeries>? = null,
    innerRadius: Float = 0f,
    contentDescription: String? = null,
    colours: ChartColours = ChartDefaults.colours(),
    legendPosition: ChartLegendPosition = ChartLegendPosition.End,
    showLegend: Boolean = true,
    showPatterns: Boolean = ChartDefaults.showPatterns(),
    showSelectionTooltip: Boolean = true,
    animateData: Boolean = true,
    strings: ChartStrings = ChartDefaults.strings(),
    interactionSource: MutableInteractionSource? = null,
    tooltip: (@Composable ContentScope.(ChartSelection<ChartSeries>) -> Unit)? = null,
    centre: (@Composable () -> Unit)? = null,
    content: SectorChartScope.() -> Unit,
) {
    val formats = Theme.formats
    val motion = Theme.motion
    val parts by remember(content) { derivedStateOf { SectorRecorder().apply(content).parts } }
    val total = parts.sumOf { it.value.coerceAtLeast(0.0) }
    val series = parts.map { it.series }.distinct()
    val patterns = showPatterns || series.size > colours.series.size

    // One fraction per part, animated from the last shares to these; the
    // first time, from nothing, so the sectors sweep round.
    val animate = animateData && !motion.reduceMotion
    val sweep = remember { Animatable(if (animate) 0f else 1f) }
    val fractions = parts.map { if (total > 0) (it.value.coerceAtLeast(0.0) / total).toFloat() else 0f }
    val previous = remember { mutableListOf<Pair<ChartSeries, Float>>() }
    val from = remember(parts) { previous.toList() }
    LaunchedEffect(parts, animate) {
        if (animate) {
            sweep.snapTo(0f)
            sweep.animateTo(1f, motion.springOrTween(motion.springGentle))
        } else {
            sweep.snapTo(1f)
        }
        previous.clear()
        previous += parts.map { it.series }.zip(fractions)
    }
    val fade = remember { Animatable(if (animateData && motion.reduceMotion) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (fade.value < 1f) fade.animateTo(1f, motion.tweenFast())
    }

    val shares = remember(parts, strings, formats) {
        parts.mapIndexed { index, part ->
            strings.sectorShare(part.series.name, formats.percent(fractions[index].toDouble(), 0))
        }
    }
    val summary = remember(shares, strings, contentDescription, innerRadius, total) {
        val sentences = mutableListOf<String>()
        contentDescription?.takeIf { it.isNotBlank() }?.let { sentences += it }
        sentences += if (innerRadius > 0f) strings.donutChart else strings.pieChart
        if (total <= 0) sentences += strings.noData else sentences += shares
        sentences
    }
    val sentences = Theme.strings.sentences
    val description = remember(summary, sentences) { sentences(summary) }

    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val direction = if (rtl) -1f else 1f
    val tiles = remember { PatternTiles() }
    val sizing = Theme.sizing
    val legendModel = remember(parts) {
        ChartModel(
            marks = listOf(
                Mark(
                    kind = MarkKind.Rectangle,
                    runs = series.map { Run(it, DoubleArray(0), DoubleArray(0), null, null) },
                    stacking = ChartStacking.None,
                    interpolation = ChartInterpolation.Linear,
                    showPoints = false,
                    symbol = null,
                    style = null,
                ),
            ),
            rules = emptyList(),
            series = series,
            xCategories = emptyList(),
            yCategories = emptyList(),
        )
    }
    val legend: (@Composable () -> Unit)? = if (showLegend && series.count { it.name.isNotBlank() } > 0) {
        {
            ChartLegend(
                model = legendModel,
                colours = colours,
                patterns = patterns,
                stacked = legendPosition == ChartLegendPosition.Start || legendPosition == ChartLegendPosition.End,
            )
        }
    } else {
        null
    }

    // Selection: the chosen part, by its series, and how far each part stands
    // out from the centre — read as it draws, so a sector moving out
    // recomposes nothing.
    val interactive = selection != null && enabled && total > 0
    val chosen = selection?.x?.let { x -> parts.indexOfFirst { it.series == x } }?.takeIf { it >= 0 }
    val pulls = parts.mapIndexed { index, part ->
        key(part.series) {
            animateFloatAsState(
                targetValue = if (index == chosen) 1f else 0f,
                animationSpec = if (motion.reduceMotion) snap() else motion.springOrTween(motion.springGentle),
                label = "sectorPull",
            )
        }
    }
    val pullOut = Theme.spacing.xxs
    val ticker = rememberDetentTicker()
    val currentSelection by rememberUpdatedState(selection)
    val choose: (Int) -> Unit = { index ->
        parts.getOrNull(index)?.let { part ->
            ticker.at(index)
            currentSelection?.select(part.series)
        }
    }
    var side by remember { mutableIntStateOf(0) }
    val geometry = remember(fractions, innerRadius, direction) { SectorGeometry(fractions, innerRadius, direction) }
    val chooseAt: (Offset) -> Unit = { offset -> geometry.partAt(offset, side.toFloat())?.let(choose) }
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val input = if (interactive) {
        Modifier
            .pointerInput(geometry) { detectTapGestures { chooseAt(it) } }
            .pointerInput(geometry) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: continue
                        if (event.type == PointerEventType.Move && change.type == PointerType.Mouse) chooseAt(change.position)
                    }
                }
            }
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                val index = chosen ?: -1
                val last = parts.lastIndex
                // Round the circle the way it is drawn: clockwise is on, and
                // a right-to-left circle is drawn the other way round.
                val next = when (event.key) {
                    Key.DirectionRight -> if (rtl) index - 1 else index + 1
                    Key.DirectionLeft -> if (rtl) index + 1 else if (index < 0) last else index - 1
                    Key.DirectionDown -> index + 1
                    Key.DirectionUp -> if (index < 0) last else index - 1
                    Key.MoveHome -> 0
                    Key.MoveEnd -> last
                    Key.Escape -> {
                        if (index < 0) return@onKeyEvent false
                        selection.clear()
                        return@onKeyEvent true
                    }
                    else -> return@onKeyEvent false
                }
                choose(if (next < 0) last else next % (last + 1))
                true
            }
            .focusRing(source, shape = Theme.shapes.small)
            .focusable(enabled = true, interactionSource = source)
            .semantics { if (chosen != null) stateDescription = shares[chosen] }
    } else {
        Modifier
    }

    SectorLayout(
        modifier = modifier.semantics {
            this.contentDescription = description
            isTraversalGroup = true
            collectionInfo = CollectionInfo(rowCount = parts.size, columnCount = 1)
        },
        legend = legend,
        legendPosition = legendPosition,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .onSizeChanged { side = min(it.width, it.height) }
                .drawBehind {
                    val radius = min(size.width, size.height) / 2 - pullOut.toPx()
                    val hole = radius * innerRadius.coerceIn(0f, 0.95f)
                    val circle = Offset(size.width / 2, size.height / 2)
                    val gap = ChartDefaults.SectorGap.toPx()
                    val t = sweep.value
                    var angle = -90f
                    parts.forEachIndexed { index, part ->
                        val start = from.firstOrNull { it.first == part.series }?.second ?: 0f
                        val fraction = start + (fractions[index] - start) * t
                        val extent = fraction * 360f * (if (from.isEmpty()) t else 1f)
                        if (extent <= 0f) return@forEachIndexed
                        val look = lookOf(series.indexOf(part.series), colours.series, patterns, part.style, part.series.style)
                        // A chosen sector stands out along its middle, away from the centre.
                        val middle = radians(angle + extent * direction / 2)
                        val out = pullOut.toPx() * pulls[index].value
                        val origin = Offset(circle.x + out * cos(middle), circle.y + out * sin(middle))
                        val path = sectorPath(origin, radius, hole, angle, extent * direction)
                        drawPath(path, look.colour, alpha = fade.value)
                        tiles.brush(look.fill, colours.patternInk, PatternTileSize.roundToPx(), (sizing.borderWidth * 1.5f).toPx())
                            ?.let { drawPath(path, it, alpha = fade.value) }
                        if (pulls[index].value > 0f) {
                            drawPath(
                                path,
                                colours.selection,
                                alpha = pulls[index].value,
                                style = Stroke(width = sizing.borderWidthStrong.toPx()),
                            )
                        }
                        // The gap between sectors, in the page's own ground, so
                        // that two sectors of near colours are still two.
                        val edge = radians(angle)
                        drawLine(
                            colours.ground,
                            Offset(circle.x + hole * cos(edge), circle.y + hole * sin(edge)),
                            Offset(circle.x + radius * cos(edge), circle.y + radius * sin(edge)),
                            gap,
                        )
                        angle += extent * direction
                    }
                }
                .then(input),
            contentAlignment = Alignment.Center,
        ) {
            if (centre != null && innerRadius > 0f) centre()
            // One node a sector, over its middle, so a screen reader moves
            // round the parts and its focus is drawn on the one it reads.
            val showProbe = interactive && showSelectionTooltip && chosen != null
            Layout(
                content = {
                    parts.forEachIndexed { index, _ ->
                        Box(
                            Modifier.semantics {
                                this.contentDescription = shares[index]
                                collectionItemInfo = CollectionItemInfo(rowIndex = index, rowSpan = 1, columnIndex = 0, columnSpan = 1)
                                if (interactive) {
                                    selected = index == chosen
                                    onClick {
                                        choose(index)
                                        true
                                    }
                                }
                            },
                        )
                    }
                    if (showProbe && chosen != null) {
                        val part = parts[chosen]
                        val shown = ChartSelection(
                            x = part.series,
                            label = part.series.name,
                            values = listOf(
                                ChartSelectedValue(
                                    part.series,
                                    part.value,
                                    formats.percent(fractions[chosen].toDouble(), 0),
                                ),
                            ),
                        )
                        Box {
                            Tooltip(visible = true) {
                                if (tooltip != null) tooltip(shown) else +shares[chosen]
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxSize(),
            ) { measurables, constraints ->
                val width = constraints.maxWidth
                val height = constraints.maxHeight
                val boxes = geometry.boxes(width.toFloat(), height.toFloat())
                val nodes = parts.indices.map { index ->
                    val box = boxes[index]
                    measurables[index].measure(
                        Constraints.fixed(max(1, box.width.roundToInt()), max(1, box.height.roundToInt())),
                    ) to box
                }
                val probe = measurables.getOrNull(parts.size)?.measure(Constraints.fixed(1, 1))
                layout(width, height) {
                    nodes.forEach { (node, box) -> node.place(box.left.roundToInt(), box.top.roundToInt()) }
                    if (probe != null && chosen != null) {
                        val box = boxes[chosen]
                        probe.place(box.center.x.roundToInt(), box.top.roundToInt())
                    }
                }
            }
        }
    }
}

/**
 * Where each part of a [SectorChart] is, for finding the one under a pointer
 * and placing each one's node: from the top, clockwise or, by [direction],
 * anticlockwise. The target shares, not the animated ones, since a tap is
 * aimed at where a sector is going.
 */
private class SectorGeometry(
    private val fractions: List<Float>,
    private val innerRadius: Float,
    private val direction: Float,
) {
    /** The part under [offset] in a square of [side], or `null` for the hole or outside. */
    fun partAt(offset: Offset, side: Float): Int? {
        if (side <= 0f) return null
        val radius = side / 2
        val dx = offset.x - radius
        val dy = offset.y - radius
        val distance = sqrt(dx * dx + dy * dy)
        if (distance > radius || distance < radius * innerRadius.coerceIn(0f, 0.95f)) return null
        val degrees = atan2(dy, dx) * 180f / PI.toFloat()
        // From the top, the way the circle is drawn.
        val along = (((degrees + 90f) * direction) % 360f + 360f) % 360f / 360f
        var end = 0f
        fractions.forEachIndexed { index, fraction ->
            end += fraction
            if (along < end) return index
        }
        return null
    }

    /** A box round the middle of each part's band, in a canvas of [width] by [height]. */
    fun boxes(width: Float, height: Float): List<Rect> {
        val radius = min(width, height) / 2
        val hole = radius * innerRadius.coerceIn(0f, 0.95f)
        val band = max(radius - hole, 1f)
        val reach = (hole + radius) / 2
        var start = 0f
        return fractions.map { fraction ->
            val middle = radians(-90f + (start + fraction / 2) * 360f * direction)
            start += fraction
            Rect(Offset(width / 2 + reach * cos(middle), height / 2 + reach * sin(middle)), band / 2)
        }
    }
}

/**
 * The parts of a [SectorChart].
 */
@LayoutScopeMarker
@Stable
interface SectorChartScope {
    /**
     * A sector for each of [items], its share of the whole being its [value]
     * over the sum of every value in the chart. A value of zero or less draws
     * nothing, and is said as 0%.
     */
    fun <T> sectors(
        items: List<T>,
        value: (T) -> Number,
        series: (T) -> ChartSeries,
        style: SeriesStyle? = null,
    )
}

private class SectorPart(val series: ChartSeries, val value: Double, val style: SeriesStyle?)

private class SectorRecorder : SectorChartScope {
    val parts = mutableListOf<SectorPart>()

    override fun <T> sectors(items: List<T>, value: (T) -> Number, series: (T) -> ChartSeries, style: SeriesStyle?) {
        items.forEach { item -> parts += SectorPart(series(item), value(item).toDouble(), style) }
    }
}

/** A sector of a ring from [hole] to [radius] round [centre], from [start] degrees for [sweep] degrees. */
private fun sectorPath(centre: Offset, radius: Float, hole: Float, start: Float, sweep: Float): Path {
    val path = Path()
    val outer = Rect(centre, radius)
    if (hole <= 0f) {
        path.moveTo(centre.x, centre.y)
        path.arcTo(outer, start, sweep, forceMoveTo = false)
        path.close()
    } else {
        val inner = Rect(centre, hole)
        path.arcTo(outer, start, sweep, forceMoveTo = true)
        path.arcTo(inner, start + sweep, -sweep, forceMoveTo = false)
        path.close()
    }
    return path
}

private fun radians(degrees: Float): Float = (degrees * PI / 180.0).toFloat()

/** The circle and its legend, side by side or one above the other. */
@Composable
private fun SectorLayout(
    modifier: Modifier,
    legend: (@Composable () -> Unit)?,
    legendPosition: ChartLegendPosition,
    chart: @Composable () -> Unit,
) {
    val gap = Theme.spacing.md
    Layout(
        content = {
            chart()
            legend?.invoke()
        },
        modifier = modifier,
    ) { measurables, constraints ->
        val gapPx = gap.roundToPx()
        val fallback = DefaultSize.roundToPx()
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else fallback
        val legendMeasurable = measurables.getOrNull(1)
        val across = legendPosition == ChartLegendPosition.Start || legendPosition == ChartLegendPosition.End
        if (across) {
            val legendPlaceable = legendMeasurable?.measure(Constraints(maxWidth = width / 2))
            val legendBand = legendPlaceable?.let { it.width + gapPx } ?: 0
            val side = min(width - legendBand, if (constraints.hasBoundedHeight) constraints.maxHeight else fallback).coerceAtLeast(0)
            val height = maxOf(side, legendPlaceable?.height ?: 0, constraints.minHeight)
            val circle = measurables[0].measure(Constraints.fixed(side, side))
            layout(width, height) {
                val circleTop = (height - side) / 2
                val legendTop = legendPlaceable?.let { (height - it.height) / 2 } ?: 0
                if (legendPosition == ChartLegendPosition.Start) {
                    legendPlaceable?.placeRelative(0, legendTop)
                    circle.placeRelative(legendBand, circleTop)
                } else {
                    circle.placeRelative(0, circleTop)
                    legendPlaceable?.placeRelative(side + gapPx, legendTop)
                }
            }
        } else {
            val legendPlaceable = legendMeasurable?.measure(Constraints(maxWidth = width))
            val legendBand = legendPlaceable?.let { it.height + gapPx } ?: 0
            val room = if (constraints.hasBoundedHeight) constraints.maxHeight - legendBand else fallback
            val side = min(width, room).coerceAtLeast(0)
            val height = side + legendBand
            val circle = measurables[0].measure(Constraints.fixed(side, side))
            layout(width, height) {
                val circleLeft = (width - side) / 2
                if (legendPosition == ChartLegendPosition.Top) {
                    legendPlaceable?.placeRelative(0, 0)
                    circle.placeRelative(circleLeft, legendBand)
                } else {
                    circle.placeRelative(circleLeft, 0)
                    legendPlaceable?.placeRelative(0, side + gapPx)
                }
            }
        }
    }
}

/** A sector chart's size when nothing gives it one. */
private val DefaultSize = 200.dp

/** The tile a sector's fill pattern repeats, as a [Chart]'s. */
private val PatternTileSize = 7.dp
