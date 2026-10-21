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
     * down to where it was let go, on every chart, and is read as the
     * categories whose bands it reaches into, or on a date axis the day each
     * end falls in. One set with [select][ChartRangeSelectionState.select], or
     * stretched by the keys, takes whole places along categories and among
     * bars.
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
 * plain drag across is how it moves the selected place; and with focus,
 * Shift and the arrow keys stretch one from the selected place, a place at a
 * time. A tap lets go of it, as Escape does. It is a range of the axes, not of
 * the data: the chart does not say which points are in it, and a page that
 * wants them reads [xStart] and [xEnd] against its own.
 *
 * A drag marks from where it went down to where it is, not from place to
 * place: snapped to whole places among bars and categories, a range jumped a
 * band at a time under the pointer and never started or stopped where the
 * reader did. *"it should just start selecting from where i start and stop
 * where i stop"*. What it reports is still in the axis's values: along
 * categories, the first and last whose bands it reaches into.
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
     * [xStart] to [xEnd] and [yStart] to [yEnd].
     */
    internal fun mark(acrossFrom: Double, acrossTo: Double, upFrom: Double?, upTo: Double?, xStart: X, xEnd: X, yStart: Y?, yEnd: Y?) {
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
