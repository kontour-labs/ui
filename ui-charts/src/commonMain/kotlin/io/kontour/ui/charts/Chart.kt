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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import io.kontour.ui.a11y.highContrast
import io.kontour.ui.foundation.ContentScope
import io.kontour.ui.interaction.FeedbackIntent
import io.kontour.ui.interaction.rememberDetentTicker
import io.kontour.ui.theme.Theme
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
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
 * @param enabled Whether a reader can select, mark a range and hide a series
 *   from the legend. A chart is drawn the same either way, and what is chosen
 *   on it is still said, with each place marked unavailable.
 * @param endYAxis A second axis up, down the end edge of the plot, for what
 *   the content puts [on it][ChartScope.onEndAxis]: a series in other units,
 *   with its own scale and labels. Its ticks are the start axis's in number,
 *   so its labels sit on the start axis's gridlines. `null`, the default, is a
 *   chart with one axis up. Not for a chart whose axis up is a category axis.
 * @param selection Where the selected place is kept. A chart given one is
 *   interactive: a tap or a drag selects the nearest place, a pointer selects
 *   what it is over or what it clicks, as [hoverSelection] says, and with
 *   focus the arrow keys move between places. A
 *   chart of lying bars is read bar by bar, and is not selected by place.
 *   `null`, the default, is a picture: nothing on it answers a touch, a
 *   pointer or a key, it takes no focus, and a screen reader still reads it.
 * @param rangeSelection Where a range marked on the chart is kept: a stretch
 *   across, or a box, by its [mode][ChartRangeSelectionState.mode]. A pointer
 *   marks one by dragging, a finger by holding and then dragging, the hold
 *   felt as it takes and the range drawn as a line until it opens, and Shift
 *   and the arrows stretch one from the selected place, or the first; a tap or
 *   Escape lets go. Without a [selection], a plain drag across is the page's,
 *   and the arrows, Home and End are left to what is round the chart. `null`,
 *   the default, marks none. Not for a chart of lying bars.
 * @param hoverSelection Whether a pointer resting over a place selects it, or
 *   only a click does, as a tap, the keys and a screen reader do whatever this
 *   says; and on a chart that marks ranges, whether it goes on selecting while
 *   its button is down marking one. By default it selects, and waits while a
 *   range is dragged out, so the selected place's line and tooltip do not lie
 *   over the range being marked. See [ChartHoverSelection].
 * @param seriesVisibility Which series are shown. A chart given one makes
 *   each entry in its legend a switch that hides its series and shows it
 *   again. See [ChartSeriesVisibilityState].
 * @param contentDescription What the chart shows, in a sentence, said before
 *   the summary the chart writes itself: "Riders this week, by route".
 * @param legendPosition Where the legend goes, when there is one.
 * @param showLegend Whether a chart of two or more named series shows a legend.
 * @param showPatterns Whether series are told apart by dash, symbol and
 *   hatching as well as by colour.
 * @param showSelectionTooltip Whether the selected place shows its values in a
 *   tooltip: a card beside it, with a stack's total under its parts.
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
    rangeSelection: ChartRangeSelectionState<X, Y>? = null,
    hoverSelection: ChartHoverSelection = ChartHoverSelection.ExceptWhileDragging,
    seriesVisibility: ChartSeriesVisibilityState? = null,
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
                    enabled = enabled,
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
            rangeSelection = rangeSelection?.takeUnless { chart.horizontal },
            hoverSelection = hoverSelection,
            enabled = enabled,
            showSelectionTooltip = showSelectionTooltip,
            strings = strings,
            interactionSource = interactionSource,
            tooltip = tooltip,
            decode = { place -> decodePlace(place, xAxis, model) },
            encode = { x -> encodePlace(x, xAxis, model) },
            // Up, only for a box, and so only where the axis up is a measure.
            decodeUp = if (yAxis.kind == AxisKind.Category) null else { value -> yAxis.decode(value) },
            encodeUp = if (yAxis.kind == AxisKind.Category) null else { y -> yAxis.encode(y) },
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

/**
 * The value at [place] across: a number or a time as it is, a category by the
 * band it falls in. A range's end at the axis's edge, half a band past the
 * first or the last category, is that category's.
 */
@Suppress("UNCHECKED_CAST")
private fun <X : Any> decodePlace(place: Double, axis: ChartAxis<X>, model: ChartModel): X? = when {
    place.isNaN() -> null
    axis.kind == AxisKind.Category ->
        model.xCategories.takeIf { it.isNotEmpty() }?.let { it[place.roundToInt().coerceIn(0, it.lastIndex)] } as X?
    else -> axis.decode(place)
}

/**
 * The categories, by index from 0 to [last], that a range from [from] to [to]
 * along them takes in, or `null` for none. Where [bands], the chart has marks
 * as wide as their bands, a bar, a box, a cell or a rectangle, and a range
 * takes each one whose band it reaches into: a drag from late in February's
 * band takes February's bar. Otherwise it takes each whose place is inside
 * it, as a line is drawn whole only inside it and a point is in it only where
 * it is: *"it'd be cool if the highlight stopped exactly at the boundaries of
 * the selection. That also means not selecting points and stuff that aren't
 * explicitly inside the selection"*.
 *
 * [slack] is how far past an edge, in the axis's units, is still on it, as it
 * is where the range is drawn: see [MarkFocus].
 */
internal fun takenIn(from: Double, to: Double, last: Int, bands: Boolean, slack: Double): IntRange? {
    val first = if (bands) floor(from + slack - 0.5).toInt() + 1 else ceil(from - slack).toInt()
    val end = if (bands) ceil(to - slack + 0.5).toInt() - 1 else floor(to + slack).toInt()
    return (first.coerceAtLeast(0)..end.coerceAtMost(last)).takeUnless { it.isEmpty() }
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
private fun <X : Any, Y : Any> ChartPlot(
    chart: ResolvedChart,
    transition: ChartTransition,
    fade: () -> Float,
    places: List<PlaceDescription>,
    colours: ChartColours,
    patterns: Boolean,
    selection: ChartSelectionState<X>?,
    rangeSelection: ChartRangeSelectionState<X, Y>?,
    hoverSelection: ChartHoverSelection,
    enabled: Boolean,
    showSelectionTooltip: Boolean,
    strings: ChartStrings,
    interactionSource: MutableInteractionSource?,
    tooltip: (@Composable ContentScope.(ChartSelection<X>) -> Unit)?,
    decode: (Double) -> X?,
    encode: (X) -> Double,
    decodeUp: ((Double) -> Y)?,
    encodeUp: ((Y) -> Double)?,
) {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val motion = Theme.motion
    val measurer = rememberTextMeasurer()
    val frames = remember { FrameCache() }
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
    val highContrast = highContrast()
    val config = remember(colours, patterns, density, sizing, shapes, patternWeight, inks, highContrast) {
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
                highContrast = highContrast,
            )
        }
    }

    val interactive = (selection != null || rangeSelection != null) && enabled && chart.places.isNotEmpty()
    val selectedX = selection?.x
    val selectedPlace = selectedX?.let { nearestPlace(chart.places, encode(it)) }
    val ticker = rememberDetentTicker()
    val holds = rememberDetentTicker(intent = FeedbackIntent.LongPress)
    val currentSelection by rememberUpdatedState(selection)
    val currentChart by rememberUpdatedState(chart)
    val currentDecode by rememberUpdatedState(decode)
    val currentEncode by rememberUpdatedState(encode)
    // Only a chart that chooses places ticks as one is chosen: one that only
    // marks ranges chose nothing, and ticked for it.
    val select: (Double) -> Unit = { place ->
        val chosen = currentSelection
        val x = currentDecode(place)
        if (chosen != null && x != null) {
            ticker.at(currentChart.places.indexOfFirst { it == place })
            chosen.select(x)
        }
    }
    val tip = rememberChartTip()
    val selectedIndex = selectedPlace?.let { place -> chart.places.indexOfFirst { it == place } }?.takeIf { it >= 0 }
    val selectedDescription = selectedPlace?.let { place -> places.firstOrNull { group -> group.places.any { it == place } }?.text }

    // The range marked, in the chart's units, and what it is said as: as a
    // drag marked it, where it did; otherwise from its values, so that one
    // marked by the app or the keys is drawn whole places among bars and
    // categories.
    val marked = rangeSelection?.let { range ->
        val acrossFrom = range.acrossFrom
        val acrossTo = range.acrossTo
        if (acrossFrom != null && acrossTo != null) {
            return@let MarkedRange(acrossFrom, acrossTo, range.upFrom, range.upTo)
        }
        val from = range.xStart
        val to = range.xEnd
        // A range kept across a change of data can name a category the data no
        // longer has, which encodes as NaN: nothing of it is drawn or said.
        val start = from?.let(encode)?.takeIf { it.isFinite() }
        val end = to?.let(encode)?.takeIf { it.isFinite() }
        if (start == null || end == null) {
            null
        } else {
            val upFrom = range.yStart?.let { y -> encodeUp?.invoke(y) }?.takeIf { it.isFinite() }
            val upTo = range.yEnd?.let { y -> encodeUp?.invoke(y) }?.takeIf { it.isFinite() }
            MarkedRange(start, end, upFrom, upTo, whole = chart.x.kind == AxisKind.Category || chart.selectsBands)
        }
    }
    // Said as what it reports, and not at all while it reports nothing: along
    // categories a drag's ends need not be in the first and last it takes,
    // and one on a line from past February's point is from March.
    val markedDescription = marked?.let { range ->
        val xStart = rangeSelection.xStart ?: return@let null
        val xEnd = rangeSelection.xEnd ?: xStart
        val across = chart.placeAxis.write
        val categories = chart.x.kind == AxisKind.Category
        val from = if (categories) encode(xStart) else range.from
        val to = if (categories) encode(xEnd) else range.to
        val upFrom = range.upFrom
        val upTo = range.upTo
        if (upFrom != null && upTo != null) {
            strings.selectedBox(across(from), across(to), chart.y.write(upFrom), chart.y.write(upTo))
        } else {
            strings.selectedRange(across(from), across(to))
        }
    }
    // What is outside a marked range fades as one is marked, and comes back as
    // it is let go, from the range it was: see [MarkFocus]. At once under
    // reduced motion, where a chart's whole look changing is not a movement.
    val focusing = remember { Animatable(0f) }
    val lastMarked = remember { LastMarked() }
    // Not while it is a line, as a hold has just started it: the whole chart
    // would fade but for a hairline. It fades as the range opens out.
    val opened = marked?.takeIf { it.opened }
    SideEffect { if (opened != null) lastMarked.range = opened }
    val focused = opened != null
    LaunchedEffect(focused, motion.reduceMotion) {
        val target = if (focused) 1f else 0f
        if (motion.reduceMotion) focusing.snapTo(target) else focusing.animateTo(target, motion.tweenFast())
    }

    val sentences = Theme.strings.sentences
    val said = listOfNotNull(selectedDescription, markedDescription).takeIf { it.isNotEmpty() }?.let(sentences)
    val currentRange by rememberUpdatedState(rangeSelection)
    val currentDecodeUp by rememberUpdatedState(decodeUp)
    val currentSelectedIndex by rememberUpdatedState(selectedIndex)
    val currentMarked by rememberUpdatedState(marked)
    val brush = if (rangeSelection == null) {
        null
    } else {
        remember {
            // Where a drag for a range went down, and which end the keys stretch.
            var markFrom = Offset.Zero
            var keyAnchor = -1
            var keyEnd = -1
            // From exactly where the drag went down to where it is, on every
            // chart, and read as the axis's values at its ends: along
            // categories, the ones it takes in, by [takenIn]. It was
            // snapped to whole places among bars and categories, and a box
            // there lost its height: *"i don't like how the select is locked
            // to like a grid, it should just start selecting from where i
            // start and stop where i stop"*.
            fun markBetween(a: Offset, b: Offset) {
                val current = frame ?: return
                val range = currentRange ?: return
                val now = currentChart
                val mapper = Mapper(current, now.x, now.y)
                val across = listOf(mapper.xAt(a), mapper.xAt(b)).map { it.coerceIn(now.x.low, now.x.high) }.sorted()
                val decodeY = currentDecodeUp
                // A box wants a measure both ways; across categories it is a
                // range across, as `ChartRangeMode.Box` says.
                val up = if (range.mode == ChartRangeMode.Box && decodeY != null && now.x.kind != AxisKind.Category) {
                    listOf(mapper.yAt(a), mapper.yAt(b)).map { it.coerceIn(now.y.low, now.y.high) }.sorted()
                } else {
                    null
                }
                // No wider than a line, or a box no taller, as one is at the
                // moment a finger's hold starts it: drawn, so the reader sees
                // where it starts, and reporting nothing until it opens. It
                // was not drawn at all until the finger moved: *"there's no
                // visual indicator for the selection region when it has 0
                // width. just a line which then turns into the box as you
                // start dragging would be nice"*.
                if (across[0] == across[1] || (up != null && up[0] == up[1])) {
                    range.mark(across[0], across[1], up?.get(0), up?.get(1), null, null, null, null)
                    return
                }
                if (now.x.kind == AxisKind.Category) {
                    // One that takes in no category is drawn as the drag
                    // goes, so the reader sees what they are marking, and
                    // reports nothing.
                    val taken = takenIn(across[0], across[1], floor(now.x.high).toInt(), now.takesBands, (EdgeSlack / mapper.unitX).toDouble())
                    val start = taken?.let { currentDecode(it.first.toDouble()) }
                    val end = taken?.let { currentDecode(it.last.toDouble()) }
                    range.mark(across[0], across[1], null, null, start, end, null, null)
                    return
                }
                val start = currentDecode(across[0]) ?: return
                val end = currentDecode(across[1]) ?: return
                if (up != null && decodeY != null) {
                    range.mark(across[0], across[1], up[0], up[1], start, end, decodeY(up[0]), decodeY(up[1]))
                } else {
                    range.mark(across[0], across[1], null, null, start, end, null, null)
                }
            }
            ChartBrush(
                // Marked from the start, as a line where it went down, before
                // it has moved: see [markBetween].
                start = { at ->
                    markFrom = at
                    tip.resting = false
                    currentSelection?.clear()
                    markBetween(at, at)
                },
                // A long press's report, as `:ui`'s own long presses make,
                // played as a threshold crossed once a hold: from not held to
                // held. The library's long-press helper is `:ui`'s alone, and
                // a ticker is the way to the same intent from here without a
                // call site of the chart's own.
                held = {
                    holds.reset()
                    holds.at(0)
                    holds.at(1)
                },
                drag = { at -> markBetween(markFrom, at) },
                // Let go having taken in nothing, nothing is marked, and
                // nothing is left drawn to say otherwise.
                end = { currentRange?.takeIf { it.isEmpty }?.clear() },
                clear = { currentRange?.clear() },
                stretch = { by ->
                    val now = currentChart
                    val range = currentRange
                    if (range != null && now.places.isNotEmpty()) {
                        val shown = currentMarked
                        if (shown == null) {
                            // From the selected place, or the first.
                            keyAnchor = currentSelectedIndex ?: 0
                            keyEnd = keyAnchor
                        } else {
                            // From the range as it is, kept the way round the
                            // keys last had it if they marked it: from the
                            // places it reports, which along categories a
                            // drag's ends need not be nearest.
                            val from = range.xStart?.let(currentEncode) ?: shown.from
                            val to = range.xEnd?.let(currentEncode) ?: shown.to
                            val first = now.places.indexOfFirst { it == nearestPlace(now.places, from) }.coerceAtLeast(0)
                            val last = now.places.indexOfFirst { it == nearestPlace(now.places, to) }.coerceAtLeast(0)
                            if (minOf(keyAnchor, keyEnd) != first || maxOf(keyAnchor, keyEnd) != last) {
                                keyAnchor = first
                                keyEnd = last
                            }
                        }
                        keyEnd = (keyEnd + by).coerceIn(0, now.places.lastIndex)
                        val low = now.places[minOf(keyAnchor, keyEnd)]
                        val high = now.places[maxOf(keyAnchor, keyEnd)]
                        val start = currentDecode(low)
                        val end = currentDecode(high)
                        if (start != null && end != null) range.select(start, end)
                    }
                },
                marked = { currentMarked != null },
            )
        }
    }

    val input = Modifier.chartSelectionInput(
        enabled = interactive,
        tip = tip,
        chosen = selectedPlace,
        index = selectedIndex,
        count = chart.places.size,
        steps = ChartSteps.Along,
        // A plain drag across chooses a place, so only on a chart that
        // chooses one: on one that only marks ranges, which a finger does
        // after a hold, it is the page's, and scrolls what the chart is in.
        dragsAcross = selection != null,
        hoverSelection = hoverSelection,
        said = said,
        interactionSource = interactionSource,
        pickAt = { offset ->
            frame?.let { current ->
                val now = currentChart
                nearestPlace(now.places, Mapper(current, now.x, now.y).xAt(offset))?.let(select)
            }
            true
        },
        pick = if (selection != null) { index -> select(currentChart.places[index]) } else null,
        clear = { currentSelection?.clear() },
        marked = marked != null,
        brush = brush,
    )

    val showProbe = interactive && showSelectionTooltip && tip.shownFor(selectedPlace)
    Layout(
        content = {
            places.forEachIndexed { index, place ->
                Box(
                    Modifier.semantics {
                        contentDescription = place.text
                        collectionItemInfo = CollectionItemInfo(rowIndex = index, rowSpan = 1, columnIndex = 0, columnSpan = 1)
                        // What is chosen is said whether or not it can be
                        // chosen again: drawn chosen on a disabled chart, it
                        // was said as nothing.
                        if (selection != null) selected = place.places.any { it == selectedPlace }
                        if (interactive && selection != null) {
                            onClick {
                                select(place.places.first())
                                true
                            }
                        }
                        if (!enabled && (selection != null || rangeSelection != null)) disabled()
                    },
                )
            }
            if (showProbe && selectedPlace != null) {
                val shown = selectionAt(chart, selectedPlace, decode, strings, Theme.formats)
                if (shown != null) {
                    ChartTooltipProbe(shown, tooltip, tip, chosen = selectedPlace) {
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
                            total = totalAt(chart, selectedPlace)?.let { TooltipRow({}, strings.total, it) },
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
                    drawChart(
                        target.withInsets(insets),
                        transition.frame(),
                        config,
                        fade(),
                        selectedPlace,
                        settled = transition.to,
                        marked = marked,
                        focused = opened ?: lastMarked.range,
                        focusing = focusing.value,
                    )
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
        val laidOut = frames.layOut(
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
        // The tooltip stands beside what is selected, so its probe is as wide
        // as the place: a bar's whole band, and on a line the stretch nearer
        // this point than any other. A pointer anywhere in it chooses this
        // place, so it can never be on the card beside it: the probe was a
        // point's ring wide on a line, and a pointer moving on from the point
        // ran onto the card before it was nearer the next one.
        val reach = cellBand(chart.placeAxis, chart.step, mapper.unitX).roundToInt().coerceAtLeast(1)
        val probe = if (measurables.size > places.size) measurables[places.size].measure(Constraints.fixed(reach, 1)) else null
        layout(width, height) {
            nodes.forEach { (node, start) ->
                if (chart.horizontal) node.place(plot.left.roundToInt(), start) else node.place(start, plot.top.roundToInt())
            }
            if (probe != null && selectedPlace != null) {
                probe.place((mapper.px(selectedPlace) - reach / 2f).roundToInt(), plot.top.roundToInt())
            }
        }
    }
}

/**
 * The range last marked on a chart, kept while what was outside it comes back:
 * let go, the range is gone at once and its marks' fade is not. Not state:
 * written after a composition that marked one, and read while drawing.
 */
private class LastMarked {
    var range: MarkedRange? = null
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
    // Each value with the axis it is written on, where it is a plain value
    // that can be written in step with the others on that axis: see below.
    class Read(val value: ChartSelectedValue, val axis: ResolvedAxis?)
    val read = chart.layers.flatMap { layer ->
        when (layer.mark.kind) {
            // A heat map's column: each of its cells, named by its row.
            MarkKind.Cell -> {
                val scale = chart.cells
                layer.across.indices.filter { layer.across[it] == place && !layer.values[it].isNaN() }.map { i ->
                    val value = layer.values[i]
                    Read(ChartSelectedValue(ChartSeries(chart.y.write(layer.up[i])), value, scale?.write?.invoke(value) ?: write(value)), null)
                }
            }
            else -> {
                val index = layer.across.indexOfFirst { it == place }
                if (index < 0 || layer.values[index].isNaN() || stemSilentAt(chart, layer, index)) return@flatMap emptyList()
                // In its own axis's units: a series on the end axis is not
                // in the start axis's.
                val axis = if (chart.horizontal) chart.valueAxis else chart.upAxis(layer.mark.onEnd)
                val value = layer.values[index]
                // A range's value is its foot, and what it shows is its range;
                // a box's is its median, and what it shows is its five numbers.
                val top = layer.endAt(index)
                when {
                    layer.box != null -> listOf(Read(ChartSelectedValue(layer.series, value, boxSaid(layer, index, axis.write, strings, formats)), null))
                    top != null && top != value ->
                        listOf(Read(ChartSelectedValue(layer.series, value, strings.valueRange(axis.write(value), axis.write(top))), null))
                    else -> listOf(Read(ChartSelectedValue(layer.series, value, axis.write(value)), axis))
                }
            }
        }
    }
    // A place's values on one axis are written to as many digits as the one
    // that needs most, so that the tooltip's column lines up digit under
    // digit: each written on its own, "2" stood over "2.42", and in shares
    // "0%" under "2.7%".
    val inStep = HashMap<Read, String>()
    read.filter { it.axis != null }.groupBy { it.axis!! }.forEach { (axis, group) ->
        group.zip(axis.writeInStep(group.map { it.value.value })).forEach { (one, label) -> inStep[one] = label }
    }
    val values = read.map { one -> inStep[one]?.let { ChartSelectedValue(one.value.series, one.value.value, it) } ?: one.value }
    return ChartSelection(x, chart.placeAxis.write(place), values)
}

/**
 * What a stack at [place] comes to, written on the value axis, for the foot of
 * its tooltip: `null` where its values make no one figure. Only for one stack
 * of bars or areas with two parts or more there, up the start axis: a stack of
 * shares comes to the whole every time, and two stacks, or a line beside one,
 * leave it unclear what the figure would be the total of.
 */
private fun totalAt(chart: ResolvedChart, place: Double): String? {
    val stacked = chart.layers.filter { layer ->
        val kind = layer.mark.kind
        (kind == MarkKind.Bar || kind == MarkKind.Area) && !layer.mark.onEnd && !layer.mark.floating &&
            (layer.mark.stacking == ChartStacking.Stacked || layer.mark.stacking == ChartStacking.Centred)
    }
    if (stacked.isEmpty() || stacked.any { it.markIndex != stacked[0].markIndex } || stacked.size != chart.layers.size) return null
    val values = stacked.mapNotNull { layer ->
        val index = layer.across.indexOfFirst { it == place }
        layer.values.getOrNull(index)?.takeUnless { it.isNaN() }
    }
    if (values.size < 2) return null
    // In step with the values over it, as they are with each other.
    return chart.valueAxis.writeInStep(values + values.sum()).last()
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
