package io.kontour.ui.charts

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Which place along a [Chart] is selected: the value across that a reader has
 * tapped, dragged to, pointed at or moved to with the arrow keys.
 *
 * ```kotlin
 * val selection = rememberChartSelectionState<LocalDate>()
 * Chart(xAxis = ChartAxis.date(), yAxis = ChartAxis.number(), selection = selection) { … }
 * Text(selection.x?.let { formats.date(it) } ?: "")
 * ```
 *
 * A chart given one is interactive: a tap or a drag selects the nearest place,
 * a pointer selects what it is over, and the arrows move from one place to the
 * next. A chart without one is a picture with a description.
 *
 * Selection is by place, not by point: everything at the selected value across
 * is selected together, every series' value there, which is what a reader
 * asking "what was it in March?" wants and what the tooltip shows.
 *
 * A [SectorChart]'s is by part, a `ChartSelectionState<ChartSeries>`: the
 * series of the sector that is chosen.
 */
@Stable
class ChartSelectionState<X : Any> internal constructor(initialX: X?) {

    /** The selected value across, or `null` for nothing selected. */
    var x: X? by mutableStateOf(initialX)
        private set

    /**
     * Selects [x]. A value between two of the chart's places selects the
     * nearer, as a tap there would.
     */
    fun select(x: X) {
        this.x = x
    }

    /** Selects nothing. */
    fun clear() {
        x = null
    }
}

/**
 * Whether a pointer resting over a [Chart] or a [SectorChart] selects what it
 * is over, and whether it goes on doing so while a range is dragged out on it.
 * A click selects whatever this says, as a tap, the keys and a screen reader
 * do.
 *
 * Three ways and not a switch. It was `selectOnHover`, a Boolean, off by
 * default on a chart that marks ranges, and the gallery's knob for that
 * default read "Selects, unless marking", which was taken for what
 * [ExceptWhileDragging] does now: *"that one feels like it should always do
 * the hover select, unless the user is dragging. then with "selects", it should
 * do the hover select even while we are dragging"*. Changed 2026-10-21 for
 * 0.5.0, before the charts were first released.
 */
enum class ChartHoverSelection {
    /**
     * A resting pointer selects what it is over, and goes on selecting while
     * its button is down marking a range: the place, its line and its tooltip
     * follow the pointer as the range opens out behind them. For a page that
     * reads the values under the pointer as a range is marked.
     */
    Always,

    /**
     * A resting pointer selects what it is over, except while its button is
     * down on a chart that marks ranges: from the press, which is a click on
     * its way or the start of a range, to the release. A range starting lets
     * go of the selected place, so nothing is drawn over it as it opens, and
     * the pointer selects again from its first move once the button is up. The
     * default, and the same as [Always] on a chart that marks no ranges, where
     * there is no drag to wait for.
     */
    ExceptWhileDragging,

    /**
     * A pointer selects by clicking, as a finger does by tapping: moving over
     * the chart selects nothing and lets go of nothing, and the selected place
     * stays where the click put it.
     */
    Never,
}

/**
 * A [ChartSelectionState], kept across recomposition, starting at [initialX].
 */
@Composable
fun <X : Any> rememberChartSelectionState(initialX: X? = null): ChartSelectionState<X> =
    remember { ChartSelectionState(initialX) }

/**
 * What is at the selected place, for a [Chart]'s `tooltip`.
 *
 * @property x The selected value across.
 * @property label [x] as the axis writes it.
 * @property values Every series' value there, in the order the series first
 *   appear.
 */
@Immutable
class ChartSelection<X : Any>(
    val x: X,
    val label: String,
    val values: List<ChartSelectedValue>,
)

/**
 * One series' value at the selected place.
 *
 * @property series The series.
 * @property value Its value, in the units of the axis it is measured along.
 * @property label [value] as that axis writes it.
 */
@Immutable
class ChartSelectedValue(
    val series: ChartSeries,
    val value: Double,
    val label: String,
)
