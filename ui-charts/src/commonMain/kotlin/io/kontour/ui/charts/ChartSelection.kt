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
