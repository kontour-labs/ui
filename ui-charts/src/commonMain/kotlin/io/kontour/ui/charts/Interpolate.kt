package io.kontour.ui.charts

/**
 * [to] as it is [fraction] of the way from [from], for a frame of the chart
 * moving from one set of data to the next.
 *
 * The axes' spans move, so a value that stays put stays put on the screen while
 * the gridlines slide under it; and each series moves from where it was — a bar
 * from its old height, a line from its old line, read at the new places — or,
 * for a series that was not there, rises from its baseline. A chart appearing
 * for the first time is the same thing from [collapsed], which is why there is
 * one animation here and not two.
 *
 * Points and rectangles are matched by their order, and fade in rather than
 * rise when they have nothing to come from: a scatter chart's points rising from
 * the floor would be a picture of nothing.
 */
internal fun interpolate(from: ResolvedChart, to: ResolvedChart, fraction: Float): ResolvedChart {
    if (fraction >= 1f) return to
    val t = fraction.toDouble()
    val x = blendAxis(from.x, to.x, t)
    val y = blendAxis(from.y, to.y, t)
    val layers = to.layers.map { layer ->
        val previous = from.layers.firstOrNull {
            it.markIndex == layer.markIndex && it.series == layer.series && it.mark.kind == layer.mark.kind
        }
        if (previous == null) layer else blendLayer(previous, layer, t, to.horizontal)
    }
    return to.with(x, y, layers)
}

/**
 * [chart] with every bar, area and line at its baseline: where it rises from
 * when it first appears. A line's baseline is the bottom of the plot.
 */
internal fun collapsed(chart: ResolvedChart): ResolvedChart {
    val valueLow = if (chart.horizontal) chart.x.low else chart.y.low
    val layers = chart.layers.map { layer ->
        when (layer.mark.kind) {
            // A band rises from its low edge, as an area does from its base.
            MarkKind.Bar, MarkKind.Area, MarkKind.Line, MarkKind.Band -> {
                val lying = chart.horizontal && layer.mark.kind == MarkKind.Bar
                val floor = DoubleArray(layer.size) { layer.base?.get(it) ?: valueLow }
                if (lying) layer.copy(across = floor) else layer.copy(up = floor)
            }
            MarkKind.Point, MarkKind.Rectangle -> layer
        }
    }
    return chart.with(chart.x, chart.y, layers)
}

private fun blendAxis(from: ResolvedAxis, to: ResolvedAxis, t: Double): ResolvedAxis =
    if (from.kind != to.kind || to.kind == AxisKind.Category) {
        to
    } else {
        to.spanning(lerp(from.low, to.low, t), lerp(from.high, to.high, t))
    }

private fun blendLayer(from: Layer, to: Layer, t: Double, horizontal: Boolean): Layer {
    val lying = horizontal && to.mark.kind == MarkKind.Bar
    return when (to.mark.kind) {
        MarkKind.Line, MarkKind.Area, MarkKind.Band -> {
            // Read the old line at the new places, so a line of 12 points can
            // become one of 30 without anything jumping.
            val up = DoubleArray(to.size) { i -> lerp(sampleAt(from.across, from.up, to.across[i]), to.up[i], t) }
            val base = to.base?.let { newBase ->
                val oldBase = from.base
                DoubleArray(to.size) { i ->
                    lerp(if (oldBase != null) sampleAt(from.across, oldBase, to.across[i]) else newBase[i], newBase[i], t)
                }
            }
            to.copy(up = up, base = base)
        }

        MarkKind.Bar -> {
            // Matched by place: the bar for March becomes the bar for March.
            val oldPlaces = if (lying) from.up else from.across
            val oldTops = if (lying) from.across else from.up
            val newPlaces = if (lying) to.up else to.across
            val newTops = if (lying) to.across else to.up
            val tops = DoubleArray(to.size) { i ->
                val match = oldPlaces.indexOfFirst { it == newPlaces[i] }
                val start = if (match >= 0) oldTops[match] else (to.base?.get(i) ?: 0.0)
                lerp(start, newTops[i], t)
            }
            val base = to.base?.let { newBase ->
                DoubleArray(to.size) { i ->
                    val match = oldPlaces.indexOfFirst { it == newPlaces[i] }
                    val start = if (match >= 0) from.base?.get(match) ?: newBase[i] else newBase[i]
                    lerp(start, newBase[i], t)
                }
            }
            if (lying) to.copy(across = tops, base = base) else to.copy(up = tops, base = base)
        }

        MarkKind.Point, MarkKind.Rectangle -> {
            val across = DoubleArray(to.size) { i -> if (i < from.size) lerp(from.across[i], to.across[i], t) else to.across[i] }
            val up = DoubleArray(to.size) { i -> if (i < from.size) lerp(from.up[i], to.up[i], t) else to.up[i] }
            to.copy(across = across, up = up)
        }
    }
}

/** The value of the line through [places] and [values] at [place], held flat beyond its ends. */
internal fun sampleAt(places: DoubleArray, values: DoubleArray, place: Double): Double {
    if (places.isEmpty()) return Double.NaN
    if (place <= places.first()) return values.first()
    if (place >= places.last()) return values.last()
    var low = 0
    var high = places.lastIndex
    while (high - low > 1) {
        val middle = (low + high) / 2
        if (places[middle] <= place) low = middle else high = middle
    }
    val span = places[high] - places[low]
    if (span <= 0) return values[low]
    val a = values[low]
    val b = values[high]
    if (a.isNaN() || b.isNaN()) return if (place - places[low] < places[high] - place) a else b
    return a + (b - a) * (place - places[low]) / span
}

private fun lerp(a: Double, b: Double, t: Double): Double = when {
    a.isNaN() -> b
    b.isNaN() -> b
    else -> a + (b - a) * t
}
