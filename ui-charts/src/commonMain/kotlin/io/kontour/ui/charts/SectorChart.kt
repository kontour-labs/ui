package io.kontour.ui.charts

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.LayoutScopeMarker
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.kontour.ui.theme.Theme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

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
 * whole as one sentence: the kind of chart, then every part and its share.
 *
 * @param innerRadius The hole, as a fraction of the radius: `0` for a pie,
 *   about `0.6` for a donut.
 * @param contentDescription What the whole is, said before its parts: "Spending
 *   in March".
 * @param showLegend Whether the parts are named beside the chart.
 * @param showPatterns Whether sectors are told apart by fill as well as colour.
 * @param animateData Whether the sectors sweep round into place, and move to
 *   new shares when the data changes.
 * @param strings The words the chart says on its own.
 * @param centre What goes in a donut's hole: the total, most often.
 * @param content The sectors.
 */
@Composable
fun SectorChart(
    modifier: Modifier = Modifier,
    innerRadius: Float = 0f,
    contentDescription: String? = null,
    colours: ChartColours = ChartDefaults.colours(),
    legendPosition: ChartLegendPosition = ChartLegendPosition.End,
    showLegend: Boolean = true,
    showPatterns: Boolean = ChartDefaults.showPatterns(),
    animateData: Boolean = true,
    strings: ChartStrings = LocalChartStrings.current,
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

    val summary = remember(parts, strings, formats, contentDescription, innerRadius) {
        val sentences = mutableListOf<String>()
        contentDescription?.takeIf { it.isNotBlank() }?.let { sentences += it }
        sentences += if (innerRadius > 0f) strings.donutChart else strings.pieChart
        if (total <= 0) {
            sentences += strings.noData
        } else {
            parts.forEachIndexed { index, part ->
                sentences += strings.sectorShare(part.series.name, formats.percent(fractions[index].toDouble(), 0))
            }
        }
        sentences
    }
    val sentences = Theme.strings.sentences
    val description = remember(summary, sentences) { sentences(summary) }

    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val tiles = remember { PatternTiles() }
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

    SectorLayout(
        modifier = modifier.semantics { this.contentDescription = description },
        legend = legend,
        legendPosition = legendPosition,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    val radius = min(size.width, size.height) / 2
                    val hole = radius * innerRadius.coerceIn(0f, 0.95f)
                    val circle = Offset(size.width / 2, size.height / 2)
                    val gap = ChartDefaults.SectorGap.toPx()
                    val t = sweep.value
                    var angle = -90f
                    val direction = if (rtl) -1f else 1f
                    parts.forEachIndexed { index, part ->
                        val start = from.firstOrNull { it.first == part.series }?.second ?: 0f
                        val fraction = start + (fractions[index] - start) * t
                        val extent = fraction * 360f * (if (from.isEmpty()) t else 1f)
                        if (extent <= 0f) return@forEachIndexed
                        val look = lookOf(series.indexOf(part.series), colours.series, patterns, part.style, part.series.style)
                        val path = sectorPath(circle, radius, hole, angle, extent * direction)
                        drawPath(path, look.colour, alpha = fade.value)
                        tiles.brush(look.fill, colours.patternInk, PatternTileSize.roundToPx(), PatternTileStroke.toPx())
                            ?.let { drawPath(path, it, alpha = fade.value) }
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
                },
            contentAlignment = Alignment.Center,
        ) {
            if (centre != null && innerRadius > 0f) centre()
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
private val PatternTileStroke = 1.5.dp
