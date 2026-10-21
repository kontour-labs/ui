package io.kontour.ui.charts

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Which of a chart's series are shown. Given to a [Chart] or a [SectorChart],
 * it makes each entry in the legend a switch: a tap, a click or a screen
 * reader's action hides its series, and another shows it again.
 *
 * ```kotlin
 * val shown = rememberChartSeriesVisibilityState()
 * Chart(xAxis = ChartAxis.date(), yAxis = ChartAxis.number(), seriesVisibility = shown) {
 *     line(week, x = { it.day }, y = { it.riders }, series = { ChartSeries("Route ${it.route}") })
 * }
 * ```
 *
 * A hidden series leaves the chart as new data would: the axes close in on
 * what is left, a stack closes up, and a pie's other sectors grow into its
 * share. It keeps its colour and its pattern, so it comes back as itself, and
 * it stays in the legend, its swatch an empty outline, to be shown again.
 *
 * Asked for as *"the option to hide a series when you tap its box or label in
 * the legend"*.
 */
@Stable
class ChartSeriesVisibilityState internal constructor(initialHidden: Set<ChartSeries>) {
    /** The series hidden, by their keys: everything else is shown. */
    var hidden: Set<ChartSeries> by mutableStateOf(initialHidden)
        private set

    /** Whether [series] is shown. */
    fun isShown(series: ChartSeries): Boolean = series !in hidden

    /** Hides [series]. */
    fun hide(series: ChartSeries) {
        hidden = hidden + series
    }

    /** Shows [series] again. */
    fun show(series: ChartSeries) {
        hidden = hidden - series
    }

    /** Hides [series] if it is shown, and shows it if not. */
    fun toggle(series: ChartSeries) {
        if (isShown(series)) hide(series) else show(series)
    }

    /** Shows every series. */
    fun showAll() {
        hidden = emptySet()
    }
}

/**
 * A [ChartSeriesVisibilityState], remembered, with [initialHidden] hidden to start
 * with.
 */
@Composable
fun rememberChartSeriesVisibilityState(initialHidden: Set<ChartSeries> = emptySet()): ChartSeriesVisibilityState =
    remember { ChartSeriesVisibilityState(initialHidden) }

/** [this] without [hidden]'s runs; every series keeps its slot, and so its colour. */
internal fun ChartModel.without(hidden: Set<ChartSeries>): ChartModel {
    if (hidden.isEmpty()) return this
    return ChartModel(
        marks = marks.map { mark -> mark.with(runs = mark.runs.filter { it.series !in hidden }) },
        rules = rules,
        regions = regions,
        series = series,
        xCategories = xCategories,
        yCategories = yCategories,
    )
}
