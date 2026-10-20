package io.kontour.ui.charts

import io.kontour.ui.locale.LocaleFormats
import io.kontour.ui.foundation.FillPatternDefaults
import io.kontour.ui.foundation.PatternBrushes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
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
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import io.kontour.ui.foundation.ContentScope
import io.kontour.ui.interaction.rememberDetentTicker
import io.kontour.ui.theme.Theme
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * A chart of marks along two axes: lines, areas, bars, points, rectangles and
 * rules, as many and as mixed as the chart needs.
 *
 * ```kotlin
 * Chart(
 *     xAxis = ChartAxis.date(),
 *     yAxis = ChartAxis.number(title = "Riders"),
 *     modifier = Modifier.fillMaxWidth().height(220.dp),
 * ) {
 *     line(trips, x = { it.day }, y = { it.riders }, series = { ChartSeries(it.route) })
 *     rule(y = 1000.0, label = "Capacity")
 * }
 * ```
 *
 * The content says what to draw; the chart does the rest the way the library
 * does it everywhere. Series take their colours from the theme, in the order
 * they first appear, and keep them as the data changes. Ticks fall on round
 * numbers and on calendar boundaries, and every number and date is written by
 * `Theme.formats`. A change of data moves from the old chart to the new one,
 * and under reduced motion it does not move at all.
 *
 * ### Colour is never the only difference
 *
 * With [showPatterns] on — by default, at the high contrast tier and wherever
 * the reader has asked for colour not to be relied on — each series also has
 * its own line dash, point symbol and fill hatching, and the legend shows them.
 * A chart with more series than the palette has colours turns them on whatever
 * this says, so the seventh series is not the first one again.
 *
 * ### What a screen reader hears
 *
 * First a summary: the kind of chart, how many series, what each axis spans,
 * and each series' least, most and direction — "Line chart. 2 series.
 * Riders, 0 to 1,200. Route 950, 210 to 980, rising." Then the places along
 * it, one at a time, each with every series' value there; past sixty places,
 * a range at a time. With a [selection], a place can be chosen from there as
 * it can by touch.
 *
 * @param xAxis The axis across: what the content's `x` values are.
 * @param yAxis The axis up: what its `y` values are.
 * @param enabled Whether a reader can select. A chart is drawn the same either way.
 * @param endYAxis A second axis up, down the end edge of the plot, for what
 *   the content puts [on it][ChartScope.onEndAxis]: a series in other units,
 *   with its own scale and labels. Its ticks are the start axis's in number,
 *   so its labels sit on the start axis's gridlines. `null`, the default, is a
 *   chart with one axis up. Not for a chart whose axis up is a category axis.
 * @param selection Where the selected place is kept. A chart given one is
 *   interactive: a tap or a drag selects the nearest place, a pointer selects
 *   what it is over, and with focus the arrow keys move between places. A
 *   chart of lying bars is read bar by bar, and is not selected by place.
 * @param seriesVisibility Which series are shown. A chart given one makes
 *   each entry in its legend a switch that hides its series and shows it
 *   again. See [ChartSeriesVisibility].
 * @param contentDescription What the chart shows, in a sentence, said before
 *   the summary the chart writes itself: "Riders this week, by route".
 * @param legendPosition Where the legend goes, when there is one.
 * @param showLegend Whether a chart of two or more named series shows a legend.
 * @param showPatterns Whether series are told apart by dash, symbol and
 *   hatching as well as by colour.
 * @param showSelectionTooltip Whether the selected place shows its values in a
 *   tooltip.
 * @param animateData Whether a change of data moves from the old chart to the
 *   new, and the chart rises into place when it first appears.
 * @param strings The words the chart says on its own. See [ChartStrings].
 * @param tooltip What the selection's tooltip shows, in place of the selected
 *   place and every series' value there.
 * @param content The marks.
 */
@Composable
fun <X : Any, Y : Any> Chart(
    xAxis: ChartAxis<X>,
    yAxis: ChartAxis<Y>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    endYAxis: ChartAxis<Y>? = null,
    selection: ChartSelectionState<X>? = null,
    seriesVisibility: ChartSeriesVisibility? = null,
    contentDescription: String? = null,
    colours: ChartColours = ChartDefaults.colours(),
    legendPosition: ChartLegendPosition = ChartLegendPosition.Bottom,
    showLegend: Boolean = true,
    showPatterns: Boolean = ChartDefaults.showPatterns(),
    showSelectionTooltip: Boolean = true,
    animateData: Boolean = true,
    strings: ChartStrings = ChartDefaults.strings(),
    interactionSource: MutableInteractionSource? = null,
    tooltip: (@Composable ContentScope.(ChartSelection<X>) -> Unit)? = null,
    content: ChartScope<X, Y>.() -> Unit,
) {
    val formats = Theme.formats
    val motion = Theme.motion

    // Recorded once per change of the content, and again when a state it read
    // changes — never once a frame.
    val model by remember(xAxis, yAxis, endYAxis, content) {
        derivedStateOf { ChartRecorder(xAxis, yAxis, endYAxis).apply(content).build() }
    }
    // Without what the legend has hidden: the chart is worked out from what
    // is shown, so the axes and stacks close in on it, while the legend and
    // every series' colour still come from the whole.
    val hidden = seriesVisibility?.hidden.orEmpty()
    val shown = remember(model, hidden) { model.without(hidden) }
    val chart = remember(shown, formats, endYAxis) { resolve(shown, xAxis, yAxis, formats, endYAxis) }
    val patterns = showPatterns || model.series.size > colours.series.size

    val animate = animateData && !motion.reduceMotion
    val transition = remember { ChartTransition(chart, animate) }
    LaunchedEffect(chart, animate) {
        transition.settleOn(chart, animate, motion.springOrTween(motion.springGentle))
    }
    // Under reduced motion nothing moves: a chart appearing fades in, and a
    // change of data is the new chart at once.
    val fade = rememberChartFade(animateData, empty = chart.empty)

    val sentences = Theme.strings.sentences
    val summary = remember(chart, strings, formats, sentences, contentDescription) {
        chartSummary(chart, strings, formats, sentences, contentDescription)
    }
    val places = remember(chart, strings, formats) { placeDescriptions(chart, strings, formats) }

    // A heat map's legend is its scale, which it needs however many series.
    val legend: (@Composable () -> Unit)? =
        if (showLegend && (model.series.count { it.name.isNotBlank() } > 1 || chart.cells != null)) {
            {
                ChartLegend(
                    model = model,
                    colours = colours,
                    patterns = patterns,
                    stacked = legendPosition == ChartLegendPosition.Start || legendPosition == ChartLegendPosition.End,
                    scale = chart.cells,
                    visibility = seriesVisibility,
                )
            }
        } else {
            null
        }

    ChartLayout(
        modifier = modifier.semantics {
            this.contentDescription = summary
            isTraversalGroup = true
            collectionInfo = CollectionInfo(rowCount = places.size, columnCount = 1)
        },
        legend = legend,
        legendPosition = legendPosition,
    ) {
        ChartPlot(
            chart = chart,
            transition = transition,
            fade = { fade.value },
            places = places,
            colours = colours,
            patterns = patterns,
            selection = selection?.takeUnless { chart.horizontal },
            enabled = enabled,
            showSelectionTooltip = showSelectionTooltip,
            strings = strings,
            interactionSource = interactionSource,
            tooltip = tooltip,
            decode = { place -> decodePlace(place, xAxis, model) },
            encode = { x -> encodePlace(x, xAxis, model) },
        )
    }
}

/**
 * The frames between one chart and the next. The chart it is moving to is kept
 * as well as the one it came from, so that new data mid-animation starts from
 * the frame on screen rather than jumping back to where the last one began.
 */
@Stable
internal class ChartTransition(initial: ResolvedChart, animate: Boolean) {
    var from: ResolvedChart by mutableStateOf(if (animate) collapsed(initial) else initial)
        private set
    var to: ResolvedChart by mutableStateOf(initial)
        private set
    val progress = Animatable(if (animate) 0f else 1f)

    /**
     * Set with [from] and [to], in the same frame, and cleared once [progress]
     * has been put back: between the two, a frame is drawn from the start.
     *
     * `snapTo` suspends, and the new [to] used to be set before it: a frame
     * drawn in between read the new chart at the old progress, the end of the
     * last move, and so showed where the data was going, then jumped back to
     * where it came from and moved there.
     */
    private var restarting by mutableStateOf(false)

    /** This frame's chart. Read while drawing, so a frame redraws without recomposing. */
    fun frame(): ResolvedChart = interpolate(from, to, if (restarting) 0f else progress.value)

    /** Where the plot's edges were drawn when this move began; `null` for a move that jumps. */
    private var startInsets: PlotInsets? = null

    /** The edges drawn last. Written while drawing and read as a move begins, so not state. */
    var drawnInsets: PlotInsets? = null

    /**
     * This frame's plot edges, eased towards [target] by the move's progress.
     * From where they were drawn rather than from where the last move meant to
     * leave them, so a move taken up halfway through goes on from where the
     * plot is.
     */
    fun insets(target: PlotInsets): PlotInsets = blendInsets(startInsets, target, if (restarting) 0f else progress.value)

    suspend fun settleOn(next: ResolvedChart, animate: Boolean, spec: AnimationSpec<Float>) {
        if (next !== to) {
            if (animate && next.sameAs(to)) {
                // The same numbers again, from content recorded anew for a
                // recomposition that changed nothing: taken without a move of
                // its own, and whatever move was under way carries on to it.
                to = next
            } else {
                from = if (animate) moveFrom(to, next, ::frame) else next
                startInsets = if (animate) drawnInsets else null
                to = next
                restarting = animate
                progress.snapTo(if (animate) 0f else 1f)
                restarting = false
            }
        }
        if (animate) progress.animateTo(1f, spec) else progress.snapTo(1f)
    }
}

/**
 * Where a move from [current] to [next] starts: the frame on screen, [frame] —
 * or, for data arriving in a chart that had none, the new chart laid flat, on
 * its own axes, so it rises into place as a chart that had data from the start
 * does. From the empty frame, the marks arrived at once, and through the empty
 * chart's axes, which span nothing much, they fell into place from far above
 * the plot as the axes opened out.
 */
internal fun moveFrom(current: ResolvedChart, next: ResolvedChart, frame: () -> ResolvedChart): ResolvedChart =
    if (current.empty && !next.empty) collapsed(next) else frame()

@Suppress("UNCHECKED_CAST")
private fun <X : Any> decodePlace(place: Double, axis: ChartAxis<X>, model: ChartModel): X? = when (axis.kind) {
    AxisKind.Category -> model.xCategories.getOrNull(place.roundToInt()) as X?
    else -> axis.decode(place)
}

private fun <X : Any> encodePlace(x: X, axis: ChartAxis<X>, model: ChartModel): Double = when (axis.kind) {
    AxisKind.Category -> model.xCategories.indexOf(x).takeIf { it >= 0 }?.toDouble() ?: Double.NaN
    else -> axis.encode(x)
}

/** The place in [places] nearest [value], or `null` for none. */
internal fun nearestPlace(places: DoubleArray, value: Double): Double? {
    if (places.isEmpty() || value.isNaN()) return null
    var best = places[0]
    for (place in places) if (abs(place - value) < abs(best - value)) best = place
    return best
}

/**
 * The plot and its legend: the legend at its natural size, and the plot in
 * what is left. With no height from outside, the plot takes
 * [ChartDefaults.Height] and the legend goes on top of that.
 */
@Composable
private fun ChartLayout(
    modifier: Modifier,
    legend: (@Composable () -> Unit)?,
    legendPosition: ChartLegendPosition,
    plot: @Composable () -> Unit,
) {
    val gap = Theme.spacing.sm
    Layout(
        content = {
            plot()
            legend?.invoke()
        },
        modifier = modifier,
    ) { measurables, constraints ->
        val gapPx = gap.roundToPx()
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else max(constraints.minWidth, DefaultWidth.roundToPx())
        val plotMeasurable = measurables[0]
        val legendMeasurable = measurables.getOrNull(1)
        when (legendPosition) {
            ChartLegendPosition.Top, ChartLegendPosition.Bottom -> {
                val legendPlaceable = legendMeasurable?.measure(Constraints(maxWidth = width))
                val legendBand = legendPlaceable?.let { it.height + gapPx } ?: 0
                val plotHeight = if (constraints.hasBoundedHeight) {
                    (constraints.maxHeight - legendBand).coerceAtLeast(0)
                } else {
                    max(ChartDefaults.Height.roundToPx(), constraints.minHeight - legendBand)
                }
                val height = plotHeight + legendBand
                val plotPlaceable = plotMeasurable.measure(Constraints.fixed(width, plotHeight))
                layout(width, height) {
                    if (legendPosition == ChartLegendPosition.Top) {
                        legendPlaceable?.placeRelative(0, 0)
                        plotPlaceable.placeRelative(0, legendBand)
                    } else {
                        plotPlaceable.placeRelative(0, 0)
                        legendPlaceable?.placeRelative(0, plotHeight + gapPx)
                    }
                }
            }
            ChartLegendPosition.Start, ChartLegendPosition.End -> {
                val height = if (constraints.hasBoundedHeight) constraints.maxHeight else max(constraints.minHeight, ChartDefaults.Height.roundToPx())
                val legendPlaceable = legendMeasurable?.measure(Constraints(maxWidth = width / 3, maxHeight = height))
                val legendBand = legendPlaceable?.let { it.width + gapPx } ?: 0
                val plotPlaceable = plotMeasurable.measure(Constraints.fixed((width - legendBand).coerceAtLeast(0), height))
                layout(width, height) {
                    val legendTop = legendPlaceable?.let { (height - it.height) / 2 } ?: 0
                    if (legendPosition == ChartLegendPosition.Start) {
                        legendPlaceable?.placeRelative(0, legendTop)
                        plotPlaceable.placeRelative(legendBand, 0)
                    } else {
                        plotPlaceable.placeRelative(0, 0)
                        legendPlaceable?.placeRelative(plotPlaceable.width + gapPx, legendTop)
                    }
                }
            }
        }
    }
}

/**
 * The plot: drawn in one pass over [transition]'s frame, with one invisible
 * node per place for a screen reader to move through and, while a place is
 * selected, a probe there for its tooltip to point at.
 */
@Composable
private fun <X : Any> ChartPlot(
    chart: ResolvedChart,
    transition: ChartTransition,
    fade: () -> Float,
    places: List<PlaceDescription>,
    colours: ChartColours,
    patterns: Boolean,
    selection: ChartSelectionState<X>?,
    enabled: Boolean,
    showSelectionTooltip: Boolean,
    strings: ChartStrings,
    interactionSource: MutableInteractionSource?,
    tooltip: (@Composable ContentScope.(ChartSelection<X>) -> Unit)?,
    decode: (Double) -> X?,
    encode: (X) -> Double,
) {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val measurer = rememberTextMeasurer()
    val typography = Theme.typography
    val labelStyle = typography.labelSmall
    val titleStyle = typography.labelMedium
    val gap = Theme.spacing.xs
    var frame by remember { mutableStateOf<ChartFrame?>(null) }
    val tiles = remember { PatternBrushes() }
    // Every stroke from the theme's sizing, so that lines thicken with the
    // rest of the library at the high contrast tier.
    val sizing = Theme.sizing
    val shapes = Theme.shapes
    val patternWeight = FillPatternDefaults.Weight
    // A heat map's value is written in its cell in whichever of the page's
    // ink and the page itself stands out more from the cell's shade.
    val inks = Theme.colours.content to Theme.colours.background
    val config = remember(colours, patterns, density, sizing, shapes, patternWeight, inks) {
        with(density) {
            DrawConfig(
                colours = colours,
                patterns = patterns,
                lineWidth = sizing.borderWidthStrong.toPx(),
                pointSize = ChartDefaults.PointSize.toPx(),
                cornerShape = shapes.extraSmall,
                pinShape = shapes.capsule,
                hairline = sizing.dividerThickness.toPx(),
                patternWeight = patternWeight,
                tiles = tiles,
                cellGap = ChartDefaults.SectorGap.toPx(),
                cellInks = inks,
            )
        }
    }

    val interactive = selection != null && enabled && chart.places.isNotEmpty()
    val selectedX = selection?.x
    val selectedPlace = selectedX?.let { nearestPlace(chart.places, encode(it)) }
    val ticker = rememberDetentTicker()
    val currentSelection by rememberUpdatedState(selection)
    val currentChart by rememberUpdatedState(chart)
    val currentDecode by rememberUpdatedState(decode)
    val select: (Double) -> Unit = { place ->
        currentDecode(place)?.let { x ->
            ticker.at(currentChart.places.indexOfFirst { it == place })
            currentSelection?.select(x)
        }
    }
    val tip = rememberChartTip()
    val selectedIndex = selectedPlace?.let { place -> chart.places.indexOfFirst { it == place } }?.takeIf { it >= 0 }
    val selectedDescription = selectedPlace?.let { place -> places.firstOrNull { group -> group.places.any { it == place } }?.text }

    val input = Modifier.chartSelectionInput(
        enabled = interactive,
        tip = tip,
        chosen = selectedPlace,
        index = selectedIndex,
        count = chart.places.size,
        steps = ChartSteps.Along,
        dragsAcross = true,
        said = selectedDescription,
        interactionSource = interactionSource,
        pickAt = { offset ->
            frame?.let { current ->
                val now = currentChart
                nearestPlace(now.places, Mapper(current, now.x, now.y).xAt(offset))?.let(select)
            }
        },
        pick = { index -> select(currentChart.places[index]) },
        clear = { currentSelection?.clear() },
    )

    val showProbe = interactive && showSelectionTooltip && tip.shownFor(selectedPlace)
    Layout(
        content = {
            places.forEachIndexed { index, place ->
                Box(
                    Modifier.semantics {
                        contentDescription = place.text
                        collectionItemInfo = CollectionItemInfo(rowIndex = index, rowSpan = 1, columnIndex = 0, columnSpan = 1)
                        if (interactive) {
                            selected = place.places.any { it == selectedPlace }
                            onClick {
                                select(place.places.first())
                                true
                            }
                        }
                    },
                )
            }
            if (showProbe && selectedPlace != null) {
                val shown = selectionAt(chart, selectedPlace, decode, strings, Theme.formats)
                if (shown != null) {
                    ChartTooltipProbe(shown, tooltip, onDismissRequest = { tip.putAway(selectedPlace) }) {
                        ChartTooltipBody(
                            title = shown.label,
                            rows = shown.values.map { value ->
                                val scale = chart.cells
                                // A heat map's row is keyed by its own shade.
                                val swatch: @Composable () -> Unit = if (scale != null) {
                                    { ShadeSwatch(rampAt(colours.ramp, scale.fraction(value.value))) }
                                } else {
                                    { SeriesSwatch(chart.model, value.series, colours, patterns) }
                                }
                                TooltipRow(swatch, value.series.name, value.label)
                            },
                        )
                    }
                }
            }
        },
        modifier = Modifier
            .drawBehind {
                frame?.let { target ->
                    val insets = transition.insets(target.insets)
                    transition.drawnInsets = insets
                    drawChart(target.withInsets(insets), transition.frame(), config, fade(), selectedPlace)
                }
            }
            .then(input),
    ) { measurables, constraints ->
        // Its size is the one it is given. Asked for its intrinsic size, inside
        // a `FlowRow` or under `IntrinsicSize`, it is measured unbounded, and it
        // was as wide as `Int.MAX_VALUE`, which no layout may be. A drawing has
        // no size of its own, so it answers with its least, and leaves the
        // frame it draws alone: a frame laid out at nothing, kept, is a blank.
        if (!constraints.hasBoundedWidth || !constraints.hasBoundedHeight) {
            return@Layout layout(constraints.minWidth, constraints.minHeight) {}
        }
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        // Laid out for where the chart is going once its move has begun, and
        // until then for where it is: measured from the new chart before its
        // move started, a frame drew the old data in the new plot.
        val laidOut = layOutFrame(
            chart = transition.to,
            width = width.toFloat(),
            height = height.toFloat(),
            measurer = measurer,
            labelStyle = labelStyle,
            titleStyle = titleStyle,
            gap = gap.toPx(),
            density = this,
            layoutDirection = layoutDirection,
            writeCells = patterns,
        )
        frame = laidOut
        val mapper = Mapper(laidOut, chart.x, chart.y)
        val plot = laidOut.plot
        // Each place's node spans its share of the plot, so a screen reader's
        // focus rectangle is drawn round the place it is reading.
        val nodes = places.mapIndexed { index, place ->
            val (from, to) = placeExtent(chart.places, place.places)
            val a = if (chart.horizontal) mapper.py(from) else mapper.px(from)
            val b = if (chart.horizontal) mapper.py(to) else mapper.px(to)
            val start = minOf(a, b).roundToInt()
            val size = max(1, abs(b - a).roundToInt())
            val placeable = if (chart.horizontal) {
                measurables[index].measure(Constraints.fixed(plot.width.roundToInt().coerceAtLeast(1), size))
            } else {
                measurables[index].measure(Constraints.fixed(size, plot.height.roundToInt().coerceAtLeast(1)))
            }
            placeable to start
        }
        val probe = if (measurables.size > places.size) measurables[places.size].measure(Constraints.fixed(1, 1)) else null
        layout(width, height) {
            nodes.forEach { (node, start) ->
                if (chart.horizontal) node.place(plot.left.roundToInt(), start) else node.place(start, plot.top.roundToInt())
            }
            if (probe != null && selectedPlace != null) {
                probe.place(mapper.px(selectedPlace).roundToInt(), plot.top.roundToInt())
            }
        }
    }
}

/** What is at [place], for the tooltip. */
private fun <X : Any> selectionAt(
    chart: ResolvedChart,
    place: Double,
    decode: (Double) -> X?,
    strings: ChartStrings,
    formats: LocaleFormats,
): ChartSelection<X>? {
    val x = decode(place) ?: return null
    val write = chart.valueAxis.write
    val values = chart.layers.flatMap { layer ->
        when (layer.mark.kind) {
            // A heat map's column: each of its cells, named by its row.
            MarkKind.Cell -> {
                val scale = chart.cells
                layer.across.indices.filter { layer.across[it] == place && !layer.values[it].isNaN() }.map { i ->
                    val value = layer.values[i]
                    ChartSelectedValue(ChartSeries(chart.y.write(layer.up[i])), value, scale?.write?.invoke(value) ?: write(value))
                }
            }
            else -> {
                val index = layer.across.indexOfFirst { it == place }
                if (index < 0 || layer.values[index].isNaN() || stemSilentAt(chart, layer, index)) return@flatMap emptyList()
                // In its own axis's units: a series on the end axis is not
                // in the start axis's.
                val write = if (chart.horizontal) write else chart.upAxis(layer.mark.onEnd).write
                val value = layer.values[index]
                // A range's value is its foot, and what it shows is its range;
                // a box's is its median, and what it shows is its five numbers.
                val top = layer.endAt(index)
                val label = when {
                    layer.box != null -> boxSaid(layer, index, write, strings, formats)
                    top != null && top != value -> strings.valueRange(write(value), write(top))
                    else -> write(value)
                }
                listOf(ChartSelectedValue(layer.series, value, label))
            }
        }
    }
    return ChartSelection(x, chart.placeAxis.write(place), values)
}

/** The span along the place axis that [group] answers for: halfway to each neighbour. */
private fun placeExtent(all: DoubleArray, group: DoubleArray): Pair<Double, Double> {
    val first = all.indexOfFirst { it == group.first() }
    val last = all.indexOfFirst { it == group.last() }
    fun half(i: Int, j: Int): Double = if (i in all.indices && j in all.indices) abs(all[j] - all[i]) / 2 else Double.NaN
    val before = half(first - 1, first).takeUnless { it.isNaN() } ?: half(first, first + 1).takeUnless { it.isNaN() } ?: 0.5
    val after = half(last, last + 1).takeUnless { it.isNaN() } ?: half(last - 1, last).takeUnless { it.isNaN() } ?: 0.5
    return group.first() - before to group.last() + after
}

/** A chart's width when nothing gives it one: a phone's, so it is not a sliver in a row. */
private val DefaultWidth = 320.dp
