package io.kontour.ui.charts

import kotlin.math.floor

/** A box plot's five numbers and what lies past its whiskers, in the chart's own units. */
internal class BoxNumbers(
    val low: Double,
    val lowerQuartile: Double,
    val median: Double,
    val upperQuartile: Double,
    val high: Double,
    val outliers: DoubleArray,
)

/**
 * The box plot of [samples]: its quartiles, its whiskers and its outliers, or
 * `null` with nothing to plot. `NaN`s are left out.
 *
 * Quartiles by linear interpolation between the sorted samples — Hyndman and
 * Fan's seventh method — which is what R, NumPy, pandas, d3 and a spreadsheet's
 * `QUARTILE` give, so a chart agrees with the analyst's sheet beside it, and
 * which is defined for one sample as for a thousand. Whiskers as Tukey drew
 * them: out to the furthest sample within one and a half box lengths of the
 * box, and every sample past that an outlier. For whiskers to the least and the
 * most, give the five numbers yourself.
 */
internal fun boxOf(samples: DoubleArray): BoxNumbers? {
    val sorted = samples.filter { !it.isNaN() }.sorted()
    if (sorted.isEmpty()) return null
    val lowerQuartile = quantile(sorted, 0.25)
    val median = quantile(sorted, 0.5)
    val upperQuartile = quantile(sorted, 0.75)
    val reach = WhiskerReach * (upperQuartile - lowerQuartile)
    val lowFence = lowerQuartile - reach
    val highFence = upperQuartile + reach
    val inside = sorted.filter { it in lowFence..highFence }
    return BoxNumbers(
        low = inside.firstOrNull() ?: lowerQuartile,
        lowerQuartile = lowerQuartile,
        median = median,
        upperQuartile = upperQuartile,
        high = inside.lastOrNull() ?: upperQuartile,
        outliers = sorted.filter { it < lowFence || it > highFence }.toDoubleArray(),
    )
}

/** The [p] quantile of [sorted], by the seventh method: `(n - 1) * p` along it, between neighbours. */
internal fun quantile(sorted: List<Double>, p: Double): Double {
    if (sorted.size == 1) return sorted[0]
    val at = (sorted.size - 1) * p
    val below = floor(at).toInt()
    val above = minOf(below + 1, sorted.lastIndex)
    return sorted[below] + (sorted[above] - sorted[below]) * (at - below)
}

/** How far past the box a whisker reaches, in box lengths: Tukey's. */
private const val WhiskerReach = 1.5
