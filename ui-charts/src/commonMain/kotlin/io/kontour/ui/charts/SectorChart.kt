package io.kontour.ui.charts

import io.kontour.ui.foundation.FillPatternDefaults
import io.kontour.ui.foundation.PatternBrushes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.LayoutScopeMarker
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.key
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
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.kontour.ui.foundation.ContentScope
import io.kontour.ui.interaction.rememberDetentTicker
import io.kontour.ui.overlay.OverlayAlignment
import io.kontour.ui.overlay.OverlaySide
import io.kontour.ui.theme.Theme
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
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
 * @param seriesVisibility Which sectors are shown. A chart given one makes each
 *   entry in its legend a switch: a hidden sector leaves the circle, and the
 *   others grow into its share. See [ChartSeriesVisibility].
 * @param innerRadius The hole, as a fraction of the radius: `0` for a pie,
 *   about `0.6` for a donut.
 * @param contentDescription What the whole is, said before its parts: "Spending
 *   in March".
 * @param showLegend Whether the parts are named beside the chart.
 * @param showPatterns Whether sectors are told apart by fill as well as colour.
 * @param showSelectionTooltip Whether the chosen sector's value is shown in a
 *   tooltip: above the pointer or the finger that chose it, or over the
 *   sector for one chosen by a key.
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
    seriesVisibility: ChartSeriesVisibility? = null,
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
    val recorded by remember(content) { derivedStateOf { SectorRecorder().apply(content).parts } }
    // What is drawn is what the legend has not hidden; every series keeps its
    // slot, and so its colour, from the whole.
    val hidden = seriesVisibility?.hidden.orEmpty()
    val parts = remember(recorded, hidden) { recorded.filter { it.series !in hidden } }
    val total = parts.sumOf { it.value.coerceAtLeast(0.0) }
    val series = recorded.map { it.series }.distinct()
    val patterns = showPatterns || series.size > colours.series.size

    // One fraction per part, animated from the last shares to these; the
    // first time, from nothing, so the sectors sweep round.
    val animate = animateData && !motion.reduceMotion
    val sweep = remember { Animatable(if (animate) 0f else 1f) }
    val fractions = parts.map { if (total > 0) (it.value.coerceAtLeast(0.0) / total).toFloat() else 0f }
    val previous = remember { mutableListOf<Pair<ChartSeries, Float>>() }
    // Keyed on what the parts are rather than on the list: recorded again for
    // a recomposition that changed nothing, they are a new list and the same
    // chart, and swept round again for it — a selection made in the first
    // sweep started it over.
    val shape = parts.map { it.series to it.value }
    val from = remember(shape) { previous.toList() }
    // Drawn from the start until the sweep has been put back to it. New parts
    // and their `from` arrive in the composition, the sweep's reset only once
    // the effect runs, and a frame between them drew the new shares at the
    // old sweep's end: where they were going, then back, then the move.
    val restarting = remember(shape) { mutableStateOf(animate) }
    // Every sector recorded, hidden or not, with its share — nothing for a
    // hidden one — in the order they were recorded: what is drawn. A hidden
    // sector shrinks away where it is while the others close in on its place,
    // and one shown again grows back into it. Drawing only what was shown, a
    // sector hidden left its place empty, and the circle was whole again only
    // once the others had grown; and they grew from where their shares had
    // been, so the ones after it swung round to meet it. Reported as "it just
    // disappears after the animation finishes" and "animates in a bit of a
    // strange way when you turn it off".
    val drawn = remember(recorded, hidden, total) {
        recorded.map { part ->
            val share = if (part.series in hidden || total <= 0) 0f else (part.value.coerceAtLeast(0.0) / total).toFloat()
            part to share
        }
    }
    LaunchedEffect(shape, animate) {
        if (animate) {
            sweep.snapTo(0f)
            restarting.value = false
            sweep.animateTo(1f, motion.springOrTween(motion.springGentle))
        } else {
            sweep.snapTo(1f)
        }
        previous.clear()
        previous += drawn.map { (part, share) -> part.series to share }
    }
    val fade = rememberChartFade(animateData, empty = parts.isEmpty())

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
    val tiles = remember { PatternBrushes() }
    val sizing = Theme.sizing
    val patternWeight = FillPatternDefaults.Weight
    val legendModel = remember(recorded) {
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
            regions = emptyList(),
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
                visibility = seriesVisibility,
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
    // Each sector's corners, where its edges meet its arcs: the theme's small
    // shape's, as a bar's end is.
    //
    // A fraction of it: the whole of the theme's corner on a sector's four was
    // a slice of soap, and a sector is a slice. Enough to take the edge off.
    val corner = Theme.shapes.extraSmall.topStart
    val ticker = rememberDetentTicker()
    val currentSelection by rememberUpdatedState(selection)
    // Where a pointer or a finger chose the part, so that its tooltip is
    // there and not over the part's middle: *"can we make the tooltip show up
    // where your mouse/finger is"*. `null` for a part chosen by a key or a
    // screen reader, whose tooltip stays over the part it is about. Read only
    // where the probe is placed, so a pointer moving round a sector places it
    // again and recomposes nothing.
    var pointer by remember { mutableStateOf<Offset?>(null) }
    val choose: (Int) -> Unit = { index ->
        parts.getOrNull(index)?.let { part ->
            pointer = null
            ticker.at(index)
            currentSelection?.select(part.series)
        }
    }
    var box by remember { mutableStateOf(IntSize.Zero) }
    val geometry = remember(fractions, innerRadius, direction) { SectorGeometry(fractions, innerRadius, direction) }
    val chooseAt: (Offset) -> Boolean = { offset ->
        val index = geometry.partAt(offset, box.width.toFloat(), box.height.toFloat())
        if (index != null) {
            choose(index)
            pointer = offset
        }
        index != null
    }
    val tip = rememberChartTip()
    // Round the circle with a finger after a hold, so that a drag that starts
    // upright is still the page's scroll; round it with the keys the way it is
    // drawn, clockwise on, and the other way round right to left.
    val input = Modifier.chartSelectionInput(
        enabled = interactive,
        tip = tip,
        chosen = chosen,
        index = chosen,
        count = parts.size,
        steps = ChartSteps.Round,
        dragsAcross = false,
        said = chosen?.let { shares[it] },
        interactionSource = interactionSource,
        pickAt = chooseAt,
        pick = choose,
        clear = { currentSelection?.clear() },
    )

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
                .onSizeChanged { box = it }
                .drawBehind {
                    val radius = min(size.width, size.height) / 2 - pullOut.toPx()
                    val hole = radius * innerRadius.coerceIn(0f, 0.95f)
                    val circle = Offset(size.width / 2, size.height / 2)
                    val gap = ChartDefaults.SectorGap.toPx()
                    val t = if (restarting.value) 0f else sweep.value
                    var angle = -90f
                    drawn.forEach { (part, share) ->
                        val start = from.firstOrNull { it.first == part.series }?.second ?: 0f
                        val fraction = start + (share - start) * t
                        val extent = fraction * 360f * (if (from.isEmpty()) t else 1f)
                        if (extent <= 0f) return@forEach
                        // Hidden, or on its way out: not chosen, and not a node.
                        val index = parts.indexOf(part)
                        val pull = if (index >= 0) pulls[index].value else 0f
                        val look = lookOf(series.indexOf(part.series), colours.series, patterns, part.style, part.series.style)
                        // A chosen sector stands out along its middle, away from the centre.
                        val middle = radians(angle + extent * direction / 2)
                        val out = pullOut.toPx() * pull
                        val origin = Offset(circle.x + out * cos(middle), circle.y + out * sin(middle))
                        val path = sectorPath(origin, radius, hole, angle, extent * direction, corner.toPx(size, this) * SectorCornerShare)
                        drawPath(path, look.colour, alpha = fade.value)
                        tiles.brush(look.fill, colours.patternInk, this, patternWeight)
                            ?.let { drawPath(path, it, alpha = fade.value) }
                        if (pull > 0f) {
                            drawPath(
                                path,
                                colours.selection,
                                alpha = pull,
                                style = Stroke(width = sizing.borderWidthStrong.toPx()),
                            )
                        }
                        // The gap between sectors, in the page's own ground, so
                        // that two sectors of near colours are still two. None
                        // for a sector that is the whole circle: it has no
                        // neighbour to be told from, and a line across it
                        // reads as a part of nothing.
                        val edge = radians(angle)
                        if (extent < 359.9f) drawLine(
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
            val showProbe = interactive && showSelectionTooltip && tip.shownFor(chosen)
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
                        ChartTooltipProbe(
                            shown,
                            tooltip,
                            tip,
                            chosen = chosen,
                            side = OverlaySide.Top,
                            alignment = OverlayAlignment.Centre,
                        ) {
                            val look = lookOf(series.indexOf(part.series), colours.series, patterns, part.style, part.series.style)
                            ChartTooltipBody(title = null, rows = listOf(TooltipRow({ FillSwatch(look, colours) }, part.series.name, shown.values[0].label)))
                        }
                    }
                },
                modifier = Modifier.fillMaxSize(),
            ) { measurables, constraints ->
                // Its size is the one it is given. Asked for its intrinsic
                // size it is measured unbounded, inside a `FlowRow` or under
                // `IntrinsicSize`, and was as wide as `Int.MAX_VALUE`, which no
                // layout may be: a drawing has no size of its own to answer.
                val width = if (constraints.hasBoundedWidth) constraints.maxWidth else constraints.minWidth
                val height = if (constraints.hasBoundedHeight) constraints.maxHeight else constraints.minHeight
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
                        val at = pointer
                        if (at != null) {
                            probe.place(at.x.roundToInt(), at.y.roundToInt())
                        } else {
                            val box = boxes[chosen]
                            probe.place(box.center.x.roundToInt(), box.top.roundToInt())
                        }
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
internal class SectorGeometry(
    private val fractions: List<Float>,
    private val innerRadius: Float,
    private val direction: Float,
) {
    /**
     * The part under [offset] in a box of [width] by [height], or `null` for
     * the hole or outside. The circle is in the middle of the box, as it is
     * drawn: this took the middle of a square in its top corner, so in a wide
     * box a tap chose the sector of a circle that was not there.
     */
    fun partAt(offset: Offset, width: Float, height: Float): Int? {
        if (width <= 0f || height <= 0f) return null
        val radius = min(width, height) / 2
        val dx = offset.x - width / 2
        val dy = offset.y - height / 2
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

/**
 * A sector of a ring round [centre], from [start] through [sweep] degrees, between [hole] and [radius],
 * each corner where an edge meets an arc rounded by [corner] — less where the
 * sector is too thin or the ring too narrow to take it, and square at the
 * centre of a pie, where its edges meet each other.
 */
internal fun sectorPath(centre: Offset, radius: Float, hole: Float, start: Float, sweep: Float, corner: Float = 0f): Path {
    val path = Path()
    val outer = Rect(centre, radius)
    val half = (abs(sweep) / 2).coerceAtMost(90f) * PI.toFloat() / 180f
    val reach = sin(half)
    var r = min(corner, (radius - hole) / 2)
    r = min(r, radius * reach / (1 + reach))
    if (hole > 0f && reach < 1f) r = min(r, hole * reach / (1 - reach))
    if (r < 0.5f || abs(sweep) >= 359.9f) {
        if (hole <= 0f) {
            path.moveTo(centre.x, centre.y)
            path.wholeArcTo(outer, start, sweep, forceMoveTo = false)
            path.close()
        } else {
            path.wholeArcTo(outer, start, sweep, forceMoveTo = true)
            path.wholeArcTo(Rect(centre, hole), start + sweep, -sweep, forceMoveTo = false)
            path.close()
        }
        return path
    }
    val way = if (sweep < 0) -1f else 1f
    val end = start + sweep
    fun at(degrees: Float, distance: Float) =
        Offset(centre.x + distance * cos(radians(degrees)), centre.y + distance * sin(radians(degrees)))
    fun degrees(value: Float) = value * 180f / PI.toFloat()
    // The fillet between an edge and the outer arc: its circle sits r inside
    // the arc, and meets the edge where it is r from it.
    val outerTurn = degrees(asin(r / (radius - r)))
    val outerAlong = sqrt((radius - r) * (radius - r) - r * r)
    path.moveTo(at(start, outerAlong).x, at(start, outerAlong).y)
    fillet(path, at(start + way * outerTurn, radius - r), r, at(start, outerAlong), at(start + way * outerTurn, radius))
    path.arcTo(outer, start + way * outerTurn, sweep - 2 * way * outerTurn, forceMoveTo = false)
    fillet(path, at(end - way * outerTurn, radius - r), r, at(end - way * outerTurn, radius), at(end, outerAlong))
    if (hole > 0f) {
        val innerTurn = degrees(asin(r / (hole + r)))
        val innerAlong = sqrt((hole + r) * (hole + r) - r * r)
        path.lineTo(at(end, innerAlong).x, at(end, innerAlong).y)
        fillet(path, at(end - way * innerTurn, hole + r), r, at(end, innerAlong), at(end - way * innerTurn, hole))
        path.arcTo(Rect(centre, hole), end - way * innerTurn, -(sweep - 2 * way * innerTurn), forceMoveTo = false)
        fillet(path, at(start + way * innerTurn, hole + r), r, at(start + way * innerTurn, hole), at(start, innerAlong))
    } else {
        path.lineTo(centre.x, centre.y)
    }
    path.close()
    return path
}

/**
 * `arcTo`, in halves past half a turn, so a whole turn is drawn as one.
 *
 * Skia, like Android's own `Path.arcTo`, takes a sweep of a whole turn as no
 * turn at all: it starts and ends in the same place, and is drawn as a point.
 * So a sector that was the whole of its circle, the last series shown in a
 * pie at 100%, vanished. Reported as *"when one item is 100%, it rolls over
 * to 0%"*.
 */
private fun Path.wholeArcTo(oval: Rect, start: Float, sweep: Float, forceMoveTo: Boolean) {
    if (abs(sweep) <= 180f) {
        arcTo(oval, start, sweep, forceMoveTo)
        return
    }
    arcTo(oval, start, sweep / 2, forceMoveTo)
    arcTo(oval, start + sweep / 2, sweep / 2, forceMoveTo = false)
}

/** The short way round the circle of [r] about [centre], from [from] to [to]. */
private fun fillet(path: Path, centre: Offset, r: Float, from: Offset, to: Offset) {
    val a = atan2(from.y - centre.y, from.x - centre.x) * 180f / PI.toFloat()
    val b = atan2(to.y - centre.y, to.x - centre.x) * 180f / PI.toFloat()
    var turn = b - a
    while (turn > 180f) turn -= 360f
    while (turn <= -180f) turn += 360f
    path.arcTo(Rect(centre, r), a, turn, forceMoveTo = false)
}

/** How much of the theme's small corner a sector's corners take. */
private const val SectorCornerShare = 0.4f

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
        val beside = legendPosition == ChartLegendPosition.Start || legendPosition == ChartLegendPosition.End
        // Beside the circle only while the legend keeps its names whole in half
        // the width. In a narrow chart they broke word by word, "Route / 950",
        // and the circle shrank to what was left; there it goes under the
        // circle instead, or over it for a legend at the start.
        val across = beside && (legendMeasurable?.maxIntrinsicWidth(Constraints.Infinity) ?: 0) <= width / 2
        val above = legendPosition == ChartLegendPosition.Top || legendPosition == ChartLegendPosition.Start
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
                // A legend that was meant to stand beside is a column, and is
                // centred under the circle it moved below.
                val legendLeft = if (beside) legendPlaceable?.let { (width - it.width) / 2 } ?: 0 else 0
                if (above) {
                    legendPlaceable?.placeRelative(legendLeft, 0)
                    circle.placeRelative(circleLeft, legendBand)
                } else {
                    circle.placeRelative(circleLeft, 0)
                    legendPlaceable?.placeRelative(legendLeft, side + gapPx)
                }
            }
        }
    }
}

/** A sector chart's size when nothing gives it one. */
private val DefaultSize = 200.dp

