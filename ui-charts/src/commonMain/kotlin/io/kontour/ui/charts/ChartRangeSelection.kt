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
     * these months. Along a category axis, or among bars, it takes whole
     * places.
     */
    Across,

    /**
     * A box, across and up: the points in a corner of a scatter plot. Only
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
 * plain drag across is how it moves the selected place; and with focus,
 * Shift and the arrow keys stretch one from the selected place, a place at a
 * time. A tap lets go of it, as Escape does. It is a range of the axes, not of
 * the data: the chart does not say which points are in it, and a page that
 * wants them reads [xStart] and [xEnd] against its own.
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

    /** Whether nothing is marked. */
    val isEmpty: Boolean get() = xStart == null

    /** Marks from [start] to [end] across, given in order. */
    fun select(start: X, end: X) {
        xStart = start
        xEnd = end
        yStart = null
        yEnd = null
    }

    /** Marks a box from [xStart] to [xEnd] across and [yStart] to [yEnd] up, each given in order. */
    fun select(xStart: X, xEnd: X, yStart: Y, yEnd: Y) {
        this.xStart = xStart
        this.xEnd = xEnd
        this.yStart = yStart
        this.yEnd = yEnd
    }

    /** Marks nothing. */
    fun clear() {
        xStart = null
        xEnd = null
        yStart = null
        yEnd = null
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
