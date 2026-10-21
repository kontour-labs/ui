package io.kontour.ui.charts

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * What a reader marks out on a [Chart] with a [ChartRangeSelectionState]: a
 * stretch of the axis across, or a box.
 */
enum class ChartRangeMode {
    /**
     * A stretch of the axis across, the whole height of the plot: these weeks,
     * these months. Marked by a drag, it runs from exactly where the drag went
     * down to where it was let go, on every chart. Along categories it is read
     * as the first and last it takes in: on a chart with bars, boxes, cells or
     * rectangles, those whose bands it reaches into, and on one of lines,
     * areas and points, those whose places are inside it, which can be none.
     * On a date axis it is read as the day each end falls in. One set with
     * [select][ChartRangeSelectionState.select], or stretched by the keys,
     * takes whole places along categories and among bars.
     */
    Across,

    /**
     * A box, across and up: the points in a corner of a scatter plot, from
     * exactly where a drag went down to where it was let go, both ways. Only
     * where both axes are numbers or times; where either is a category it is
     * [Across].
     */
    Box,
}

/**
 * The range a reader has marked out on a [Chart], [across][mode] it or in a
 * box: from [xStart] to [xEnd] across, and in a box from [yStart] to [yEnd]
 * up, each pair in order; `null` for nothing marked.
 *
 * ```kotlin
 * val range = rememberChartRangeSelectionState<LocalDate, Number>()
 * Chart(xAxis = ChartAxis.date(), yAxis = ChartAxis.number(), rangeSelection = range) { … }
 * Text(range.xStart?.let { "From ${formats.date(it)}" } ?: "Drag across the chart")
 * ```
 *
 * A pointer marks one by dragging; a finger by holding, then dragging, since a
 * plain drag across is how it moves the selected place; and with focus, Shift
 * and the arrow keys stretch one from the selected place, a place at a time. A
 * tap lets go of it, on the chart or anywhere else, as Escape does. It is a
 * range of the axes, not of the data: the chart does not say which points are
 * in it, and a page that wants them reads [xStart] and [xEnd] against its own.
 *
 * A drag marks from where it went down to where it is, not from place to
 * place: snapped to whole places among bars and categories, a range jumped a
 * band at a time under the pointer and never started or stopped where the
 * reader did. *"it should just start selecting from where i start and stop
 * where i stop"*. What it reports is still in the axis's values.
 *
 * What is outside it fades, and what is whole is what it reports. A line, an
 * area or a band is whole exactly between its edges, on every axis. A point,
 * a stem or a dumbbell's end is in it where it is, in a box up as well as
 * across. A bar, a box plot, a cell or a rectangle is large, and in it where
 * the range reaches into its band at all: a drag from late in February's band
 * takes February's bar. So along categories [xStart] and [xEnd] are, on a
 * chart with any of those, the first and last whose bands the range reaches
 * into, the bars' rule deciding a chart of bars and a line; and on a chart of
 * lines, areas and points, the first and last whose places are inside it. A
 * drag there that takes in no place reports nothing, and is drawn while it
 * goes on, so the reader sees what they are marking. Along categories those
 * were the bands' for every mark, and a line was whole out past the range to
 * the edges of the bands it reached into: *"it'd be cool if the highlight
 * stopped exactly at the boundaries of the selection. That also means not
 * selecting points and stuff that aren't explicitly inside the selection. The
 * only way this differs I think should be large objects like bars"*.
 */
@Stable
class ChartRangeSelectionState<X : Any, Y : Any> internal constructor(
    /** Whether a range is a stretch across or a box. */
    val mode: ChartRangeMode,
) {
    /** Where the range starts across, or `null` for none. */
    var xStart: X? by mutableStateOf(null)
        private set

    /** Where the range ends across, or `null` for none. */
    var xEnd: X? by mutableStateOf(null)
        private set

    /** Where a box starts up, or `null` for none or a range [across][ChartRangeMode.Across]. */
    var yStart: Y? by mutableStateOf(null)
        private set

    /** Where a box ends up, or `null` for none or a range [across][ChartRangeMode.Across]. */
    var yEnd: Y? by mutableStateOf(null)
        private set

    /**
     * The range as a drag marked it, in the chart's own units: across from
     * [acrossFrom] to [acrossTo] and, for a box, up from [upFrom] to [upTo].
     * Drawn as it is, so its edges are where the drag's were. `null` for a
     * range set with [select] or by the keys, which is drawn from its values.
     */
    internal var acrossFrom: Double? by mutableStateOf(null)
        private set
    internal var acrossTo: Double? by mutableStateOf(null)
        private set
    internal var upFrom: Double? by mutableStateOf(null)
        private set
    internal var upTo: Double? by mutableStateOf(null)
        private set

    /** Whether nothing is marked. */
    val isEmpty: Boolean get() = xStart == null

    /** Marks from [start] to [end] across, given in order. */
    fun select(start: X, end: X) {
        set(start, end, null, null)
    }

    /** Marks a box from [xStart] to [xEnd] across and [yStart] to [yEnd] up, each given in order. */
    fun select(xStart: X, xEnd: X, yStart: Y, yEnd: Y) {
        set(xStart, xEnd, yStart, yEnd)
    }

    /** Marks nothing. */
    fun clear() {
        set(null, null, null, null)
    }

    /**
     * Marks what a drag marked: from [acrossFrom] to [acrossTo] across and, for
     * a box, [upFrom] to [upTo] up, in the chart's units and in order, read as
     * [xStart] to [xEnd] and [yStart] to [yEnd]: `null` across for one along
     * categories that takes none in, which is drawn and reports nothing.
     */
    internal fun mark(acrossFrom: Double, acrossTo: Double, upFrom: Double?, upTo: Double?, xStart: X?, xEnd: X?, yStart: Y?, yEnd: Y?) {
        this.xStart = xStart
        this.xEnd = xEnd
        this.yStart = yStart
        this.yEnd = yEnd
        this.acrossFrom = acrossFrom
        this.acrossTo = acrossTo
        this.upFrom = upFrom
        this.upTo = upTo
    }

    private fun set(xStart: X?, xEnd: X?, yStart: Y?, yEnd: Y?) {
        this.xStart = xStart
        this.xEnd = xEnd
        this.yStart = yStart
        this.yEnd = yEnd
        acrossFrom = null
        acrossTo = null
        upFrom = null
        upTo = null
    }
}

/**
 * A [ChartRangeSelectionState], kept across recomposition, marking ranges in
 * [mode]: [across][ChartRangeMode.Across] by default.
 */
@Composable
fun <X : Any, Y : Any> rememberChartRangeSelectionState(
    mode: ChartRangeMode = ChartRangeMode.Across,
): ChartRangeSelectionState<X, Y> = remember(mode) { ChartRangeSelectionState(mode) }
