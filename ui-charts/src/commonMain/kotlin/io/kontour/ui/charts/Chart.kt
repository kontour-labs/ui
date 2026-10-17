package io.kontour.ui.charts

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.kontour.ui.foundation.ContentScope
import io.kontour.ui.input.focusRing
import io.kontour.ui.interaction.rememberDetentTicker
import io.kontour.ui.overlay.Tooltip
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
 * @param selection Where the selected place is kept. A chart given one is
 *   interactive: a tap or a drag selects the nearest place, a pointer selects
 *   what it is over, and with focus the arrow keys move between places. A
 *   chart of lying bars is read bar by bar, and is not selected by place.
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
    selection: ChartSelectionState<X>? = null,
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
    val model by remember(xAxis, yAxis, content) {
        derivedStateOf { ChartRecorder(xAxis, yAxis).apply(content).build() }
    }
    val chart = remember(model, formats) { resolve(model, xAxis, yAxis, formats) }
    val patterns = showPatterns || model.series.size > colours.series.size

    val animate = animateData && !motion.reduceMotion
    val transition = remember { ChartTransition(chart, animate) }
    LaunchedEffect(chart, animate) {
        transition.settleOn(chart, animate, motion.springOrTween(motion.springGentle))
    }
    // Under reduced motion nothing moves: a chart appearing fades in, and a
    // change of data is the new chart at once.
    val fade = remember { Animatable(if (animateData && motion.reduceMotion) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (fade.value < 1f) fade.animateTo(1f, motion.tweenFast())
    }

    val sentences = Theme.strings.sentences
    val summary = remember(chart, strings, formats, sentences, contentDescription) {
        chartSummary(chart, strings, formats, sentences, contentDescription)
    }
    val places = remember(chart, strings, formats) { placeDescriptions(chart, strings, formats) }

    val legend: (@Composable () -> Unit)? =
        if (showLegend && model.series.count { it.name.isNotBlank() } > 1) {
            {
                ChartLegend(
                    model = model,
                    colours = colours,
                    patterns = patterns,
                    stacked = legendPosition == ChartLegendPosition.Start || legendPosition == ChartLegendPosition.End,
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

    /** This frame's chart. Read while drawing, so a frame redraws without recomposing. */
    fun frame(): ResolvedChart = interpolate(from, to, progress.value)

    suspend fun settleOn(next: ResolvedChart, animate: Boolean, spec: AnimationSpec<Float>) {
        if (next !== to) {
            from = if (animate) frame() else next
            to = next
            progress.snapTo(if (animate) 0f else 1f)
        }
        if (animate) progress.animateTo(1f, spec) else progress.snapTo(1f)
    }
}

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
    val tiles = remember { PatternTiles() }
    // Every stroke from the theme's sizing, so that lines thicken with the
    // rest of the library at the high contrast tier.
    val sizing = Theme.sizing
    val shapes = Theme.shapes
    val config = remember(colours, patterns, density, sizing, shapes) {
        with(density) {
            DrawConfig(
                colours = colours,
                patterns = patterns,
                lineWidth = sizing.borderWidthStrong.toPx(),
                pointSize = ChartDefaults.PointSize.toPx(),
                cornerShape = shapes.extraSmall,
                pinShape = shapes.capsule,
                hairline = sizing.dividerThickness.toPx(),
                tileSize = PatternTile.roundToPx(),
                tileStroke = (sizing.borderWidth * 1.5f).toPx(),
                tiles = tiles,
            )
        }
    }

    val interactive = selection != null && enabled && chart.places.isNotEmpty()
    val selectedX = selection?.x
    val selectedPlace = selectedX?.let { nearestPlace(chart.places, encode(it)) }
    val ticker = rememberDetentTicker()
    val currentSelection by rememberUpdatedState(selection)
    val select: (Double) -> Unit = remember(chart) {
        { place ->
            decode(place)?.let { x ->
                ticker.at(chart.places.indexOfFirst { it == place })
                currentSelection?.select(x)
            }
        }
    }
    val selectAt: (Offset) -> Unit = { offset ->
        frame?.let { current ->
            val mapper = Mapper(current, chart.x, chart.y)
            nearestPlace(chart.places, mapper.xAt(offset))?.let(select)
        }
    }
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val rtl = layoutDirection == LayoutDirection.Rtl
    val selectedDescription = selectedPlace?.let { place -> places.firstOrNull { group -> group.places.any { it == place } }?.text }

    val input = if (interactive) {
        Modifier
            .pointerInput(chart) { detectTapGestures { selectAt(it) } }
            .pointerInput(chart) {
                detectHorizontalDragGestures(onDragStart = { selectAt(it) }) { change, _ ->
                    change.consume()
                    selectAt(change.position)
                }
            }
            .pointerInput(chart) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: continue
                        if (event.type == PointerEventType.Move && change.type == PointerType.Mouse) selectAt(change.position)
                    }
                }
            }
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                val index = selectedPlace?.let { place -> chart.places.indexOfFirst { it == place } } ?: -1
                val last = chart.places.lastIndex
                val next = when (event.key) {
                    Key.DirectionRight -> if (rtl) index - 1 else index + 1
                    Key.DirectionLeft -> if (rtl) index + 1 else if (index < 0) last else index - 1
                    Key.MoveHome -> 0
                    Key.MoveEnd -> last
                    Key.Escape -> {
                        if (index < 0) return@onKeyEvent false
                        selection.clear()
                        return@onKeyEvent true
                    }
                    else -> return@onKeyEvent false
                }
                select(chart.places[next.coerceIn(0, last)])
                true
            }
            .focusRing(source, shape = Theme.shapes.small)
            .focusable(enabled = true, interactionSource = source)
            .semantics { if (selectedDescription != null) stateDescription = selectedDescription }
    } else {
        Modifier
    }

    val showProbe = interactive && showSelectionTooltip && selectedPlace != null
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
            if (showProbe) {
                val shown = selectionAt(chart, selectedPlace, decode, strings)
                if (shown != null) {
                    Box {
                        Tooltip(visible = true) {
                            if (tooltip != null) {
                                tooltip(shown)
                            } else {
                                val lines = listOf(shown.label) + shown.values.map { value ->
                                    if (value.series.name.isBlank()) value.label else strings.seriesValue(value.series.name, value.label)
                                }
                                +lines.joinToString("\n")
                            }
                        }
                    }
                }
            }
        },
        modifier = Modifier
            .drawBehind {
                frame?.let { drawChart(it, transition.frame(), config, fade(), selectedPlace) }
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
        val laidOut = layOutFrame(
            chart = chart,
            width = width.toFloat(),
            height = height.toFloat(),
            measurer = measurer,
            labelStyle = labelStyle,
            titleStyle = titleStyle,
            gap = gap.toPx(),
            density = this,
            layoutDirection = layoutDirection,
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
private fun <X : Any> selectionAt(chart: ResolvedChart, place: Double, decode: (Double) -> X?, strings: ChartStrings): ChartSelection<X>? {
    val x = decode(place) ?: return null
    val write = chart.valueAxis.write
    val values = chart.layers.mapNotNull { layer ->
        val index = layer.across.indexOfFirst { it == place }
        if (index < 0) return@mapNotNull null
        val value = layer.values[index]
        if (value.isNaN()) return@mapNotNull null
        // A band's value is its foot, and what it shows is its range.
        val top = if (layer.mark.kind == MarkKind.Band) layer.upEnd?.get(index) else null
        val label = if (top != null && top != value) strings.valueRange(write(value), write(top)) else write(value)
        ChartSelectedValue(layer.series, value, label)
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

/**
 * The tile a fill pattern repeats: small enough that a narrow bar still shows
 * two repeats of it, so it reads as a texture rather than as a stray line.
 */
private val PatternTile = 7.dp

/** A chart's width when nothing gives it one: a phone's, so it is not a sliver in a row. */
private val DefaultWidth = 320.dp
