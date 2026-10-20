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
    fun Layer.sameAs(other: Layer) = markIndex == other.markIndex && series == other.series && mark.kind == other.mark.kind
    val layers = to.layers.map { layer ->
        val previous = from.layers.firstOrNull { it.sameAs(layer) }
        // A series that was not there rises from its baseline, as the whole
        // chart does when it first appears. It used to arrive at once.
        val blended = blendLayer(previous ?: collapsedLayer(layer, to), layer, t, to.horizontal)
        // Shown again before it had finished leaving: back from as faint as it was.
        if (previous != null && previous.opacity < 1f) {
            blended.copy(opacity = previous.opacity + (1f - previous.opacity) * fraction)
        } else {
            blended
        }
    }
    // And one that has gone — hidden from the legend, or no longer in the
    // data — goes the same way back: down to its baseline on the new axes,
    // fading as it goes, under what stays. It used to vanish in the first
    // frame, while everything else moved. Reported as "when you turn a series
    // off, it doesn't animate out".
    val leaving = from.layers.filter { old -> to.layers.none { it.sameAs(old) } }.map { old ->
        blendLayer(old, collapsedLayer(old, to), t, to.horizontal).copy(opacity = old.opacity * (1f - fraction))
    }
    return to.with(x, y, leaving + layers)
}

/** Whether [this] shows no marks at all: a chart whose data has not come yet. */
internal val ResolvedChart.empty: Boolean get() = layers.all { it.size == 0 }

/**
 * [chart] with every bar, area and line at its baseline: where it rises from
 * when it first appears. A line's baseline is the bottom of the plot.
 */
internal fun collapsed(chart: ResolvedChart): ResolvedChart =
    chart.with(chart.x, chart.y, chart.layers.map { collapsedLayer(it, chart) })

/** [layer] of [chart] at its baseline: see [collapsed]. */
private fun collapsedLayer(layer: Layer, chart: ResolvedChart): Layer {
    val valueLow = if (chart.horizontal) chart.x.low else chart.y.low
    // A stream swells out of its middle, top and foot together.
    if (layer.mark.kind == MarkKind.Area && layer.mark.stacking == ChartStacking.Centred) {
        val middle = DoubleArray(layer.size)
        return layer.copy(up = middle, base = middle.copyOf())
    }
    return when (layer.mark.kind) {
        // A band rises from its low edge, as an area does from its base,
        // and a stem and a floating bar from their feet.
        MarkKind.Bar, MarkKind.Area, MarkKind.Line, MarkKind.Band, MarkKind.Stem -> {
            val lying = chart.horizontal && layer.mark.kind == MarkKind.Bar
            val floor = DoubleArray(layer.size) { layer.base?.get(it) ?: valueLow }
            if (lying) layer.copy(across = floor) else layer.copy(up = floor)
        }
        // A box opens out from its median line.
        MarkKind.Box -> {
            val median = layer.values
            val box = layer.box?.let { BoxRun(median.copyOf(), median.copyOf(), median.copyOf(), it.outliers) }
            layer.copy(up = median.copyOf(), base = median.copyOf(), box = box)
        }
        // A heat map warms in from its palest.
        MarkKind.Cell -> {
            val palest = layer.values.filter { !it.isNaN() }.minOrNull() ?: 0.0
            layer.copy(values = DoubleArray(layer.size) { if (layer.values[it].isNaN()) Double.NaN else palest })
        }
        MarkKind.Point, MarkKind.Rectangle -> layer
    }
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

        MarkKind.Bar -> blendByPlace(from, to, t, lying)

        MarkKind.Stem -> blendByPlace(from, to, t, lying = false)

        MarkKind.Box -> {
            // Matched by place, all five numbers at once; the outliers are the
            // new ones, which have nowhere to move from.
            val moved = blendByPlace(from, to, t, lying = false)
            val oldBox = from.box
            val newBox = to.box ?: return moved
            fun blend(old: DoubleArray?, new: DoubleArray): DoubleArray = DoubleArray(to.size) { i ->
                val match = from.across.indexOfFirst { it == to.across[i] }
                lerp(if (match >= 0 && old != null) old[match] else to.values[i], new[i], t)
            }
            val median = blend(from.values, to.values)
            moved.copy(
                values = median,
                box = BoxRun(blend(oldBox?.low, newBox.low), median, blend(oldBox?.high, newBox.high), newBox.outliers),
            )
        }

        MarkKind.Cell -> {
            // Matched by where the cell is: its shade moves, it does not.
            val values = DoubleArray(to.size) { i ->
                var match = -1
                for (j in 0 until from.size) {
                    if (from.across[j] == to.across[i] && from.up[j] == to.up[i]) {
                        match = j
                        break
                    }
                }
                lerp(if (match >= 0) from.values[match] else to.values[i], to.values[i], t)
            }
            to.copy(values = values)
        }

        MarkKind.Point, MarkKind.Rectangle -> {
            val across = DoubleArray(to.size) { i -> if (i < from.size) lerp(from.across[i], to.across[i], t) else to.across[i] }
            val up = DoubleArray(to.size) { i -> if (i < from.size) lerp(from.up[i], to.up[i], t) else to.up[i] }
            to.copy(across = across, up = up)
        }
    }
}

/**
 * [to] as [t] of the way from [from], each value matched by its place: the bar
 * for March becomes the bar for March, top and foot. One with nothing to come
 * from grows from its foot.
 */
private fun blendByPlace(from: Layer, to: Layer, t: Double, lying: Boolean): Layer {
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
    return if (lying) to.copy(across = tops, base = base) else to.copy(up = tops, base = base)
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
