package io.kontour.ui.charts

import kotlin.math.max

/**
 * [to] as it is [fraction] of the way from [start], for a frame of the chart
 * moving from one set of data to the next.
 *
 * The axes' spans move, so a value that stays put stays put on the screen while
 * the gridlines slide under it; and each series moves from where it was — a bar
 * from its old height, a line from its old line, read at the new places — or,
 * for a series that was not there, rises from its baseline. A chart appearing
 * for the first time is the same thing from [collapsed], which is why there is
 * one animation here and not two.
 *
 * Points and rectangles are matched by their order, and a series of them with
 * nothing to come from, as the chart first appears or new to it, fades in
 * rather than rising: a scatter chart's points rising from the floor would be
 * a picture of nothing. They were there from the first frame, which is what
 * this said they did not do. A point more in a series that was there already
 * is where it is from the first frame: a series fades as one.
 *
 * A series drawn another way, bars turned to a line or a line to an area,
 * fades from the one to the other as each moves towards the other's values;
 * see `reshaped` and `heldOn`.
 */
internal fun interpolate(start: ResolvedChart, to: ResolvedChart, fraction: Float): ResolvedChart {
    if (fraction >= 1f) return to
    val t = fraction.toDouble()
    // A stack turned to shares, or shares back to a stack, is in other units:
    // see `inUnitsOf`.
    val from = inUnitsOf(start, to)
    val x = blendAxis(from.x, to.x, t)
    val y = blendAxis(from.y, to.y, t)
    // The end axis eases as the start does. Arriving, it is there at once,
    // with its series rising up it; leaving, its series go down the scale
    // they were on rather than jumping to the start axis's.
    val toEnd = to.yEnd
    val fromEnd = from.yEnd
    val yEnd = when {
        toEnd != null && fromEnd != null -> blendAxis(fromEnd, toEnd, t)
        else -> toEnd ?: fromEnd
    }
    // Places along a continuous axis slide as it moves, so one that has gone
    // can be seen going; a category's place is only its order.
    val sliding = to.placeAxis.kind != AxisKind.Category && from.placeAxis.kind == to.placeAxis.kind
    fun Layer.sameAs(other: Layer) = markIndex == other.markIndex && series == other.series && mark.kind == other.mark.kind
    // The same series drawn another way: bars turned to a line, a line to an
    // area. See `reshaped`.
    fun Layer.redrawnAs(other: Layer) = markIndex == other.markIndex && series == other.series &&
        mark.kind != other.mark.kind && mark.kind in Redrawable && other.mark.kind in Redrawable
    // This frame's value axis for [layer]: across for lying bars, up the end
    // axis for what is on it.
    fun valueAxisNow(layer: Layer) = when {
        to.horizontal -> x
        layer.mark.onEnd -> yEnd ?: y
        else -> y
    }
    fun valueAxisOf(chart: ResolvedChart, layer: Layer) = if (chart.horizontal) chart.x else chart.upAxis(layer.mark.onEnd)
    val layers = to.layers.map { layer ->
        val previous = from.layers.firstOrNull { it.sameAs(layer) }
        val before = if (previous == null) from.layers.firstOrNull { it.redrawnAs(layer) } else null
        if (before != null) {
            // Drawn the new way from where the old way had it, fading in over
            // the old as that fades out. A stack from a line fades in where
            // it will stand, while the line goes up to its tops.
            val moved = if (before.base == null && layer.stacked) {
                heldOn(layer, valueAxisOf(to, layer), valueAxisNow(layer), to.horizontal)
            } else {
                blendLayer(reshaped(before, layer, to), layer, t, to.horizontal, sliding)
            }
            return@map moved.copy(opacity = before.opacity * fraction)
        }
        // A series that was not there rises from its baseline, as the whole
        // chart does when it first appears. It used to arrive at once.
        val origin = previous ?: collapsedLayer(layer, to)
        val blended = blendLayer(origin, layer, t, to.horizontal, sliding)
        // Shown again before it had finished leaving, back from as faint as
        // it was; and points and rectangles, which have no baseline, in
        // from nothing.
        if (origin.opacity < 1f) {
            blended.copy(opacity = origin.opacity + (1f - origin.opacity) * fraction)
        } else {
            blended
        }
    }
    // And one that has gone — hidden from the legend, or no longer in the
    // data — goes the same way back: down to its baseline on the new axes,
    // fading as it goes, under what stays. It used to vanish in the first
    // frame, while everything else moved. Reported as "when you turn a series
    // off, it doesn't animate out".
    //
    // One drawn another way now goes to where the new way has it as it fades,
    // so that bars turned to a line are not bars sinking under a line rising.
    val leaving = from.layers.filter { old -> to.layers.none { it.sameAs(old) } }.map { old ->
        val after = to.layers.firstOrNull { it.redrawnAs(old) }
        val moved = when {
            // A stack turned to a line stays where it stood as it fades, and
            // the line comes down from its tops.
            after != null && after.base == null && old.stacked ->
                heldOn(old, valueAxisOf(from, old), valueAxisNow(old), to.horizontal)
            after != null -> blendLayer(old, reshaped(after, old, to), t, to.horizontal, sliding)
            else -> blendLayer(old, collapsedLayer(old, to), t, to.horizontal, sliding)
        }
        moved.copy(opacity = old.opacity * (1f - fraction))
    }
    // The rules and the regions move with the axes they are on. They were the
    // new chart's from the first frame, on axes still easing from the old one:
    // a new average above an axis that had not grown to it yet was drawn off
    // the plot, and its label vanished for the first frames of the move.
    val rules = to.rules.mapIndexed { i, rule ->
        val old = from.rules.getOrNull(i)?.takeIf { it.label == rule.label && it.onEnd == rule.onEnd } ?: return@mapIndexed rule
        Rule(blendEnd(old.x, rule.x, t), blendEnd(old.y, rule.y, t), rule.label, rule.style, rule.onEnd)
    }
    val regions = to.regions.mapIndexed { i, region ->
        val old = from.regions.getOrNull(i)?.takeIf { it.label == region.label && it.onEnd == region.onEnd } ?: return@mapIndexed region
        Region(
            blendEnd(old.xStart, region.xStart, t), blendEnd(old.xEnd, region.xEnd, t),
            blendEnd(old.yStart, region.yStart, t), blendEnd(old.yEnd, region.yEnd, t),
            region.label, region.style, region.onEnd,
        )
    }
    // Bars widen or narrow with the places they stand at, as a series that
    // set the closest of them comes and goes.
    val step = if (sliding) lerp(from.step, to.step, t) else to.step
    return to.with(x, y, leaving + layers, yEnd, rules, regions, step)
}

/** A rule's or a region's end on its way from [from] to [to]: where it is going, if it was not somewhere before. */
private fun blendEnd(from: Double?, to: Double?, t: Double): Double? =
    if (from == null || to == null) to else lerp(from, to, t)

/** Whether [this] shows no marks at all: a chart whose data has not come yet. */
internal val ResolvedChart.empty: Boolean get() = layers.all { it.size == 0 }

/**
 * [chart] with every bar, area and line at its baseline: where it rises from
 * when it first appears. A line's baseline is the bottom of the plot.
 *
 * A stack rises as one, every part of it from the stack's foot. Each part
 * rose from its own foot, which was already where it would end, so a stack
 * of three opened as three bars, each standing in the air over a gap the one
 * under it had not grown to fill yet: *"it looks a bit weird with each of the
 * sections animating open individually"*.
 */
internal fun collapsed(chart: ResolvedChart): ResolvedChart =
    chart.with(chart.x, chart.y, chart.layers.map { collapsedLayer(it, chart, wholeStack = true) })

/**
 * Whether [this] is a part of a stack, standing on the parts under it: a
 * stacked or normalised bar or area. A stream is stacked too, and swells out
 * of its middle instead; see [collapsedLayer].
 */
private val Layer.stacked: Boolean
    get() = (mark.kind == MarkKind.Bar || mark.kind == MarkKind.Area) && !mark.floating &&
        (mark.stacking == ChartStacking.Stacked || mark.stacking == ChartStacking.Normalised ||
            (mark.kind == MarkKind.Bar && mark.stacking == ChartStacking.Centred))

/** Where [layer]'s stack stands in [chart]: at zero where its value axis reaches it, and at that axis's low end where it does not. */
private fun stackFoot(layer: Layer, chart: ResolvedChart): Double {
    val axis = if (chart.horizontal) chart.x else chart.upAxis(layer.mark.onEnd)
    return 0.0.coerceIn(axis.low, max(axis.low, axis.high))
}

/**
 * [layer] of [chart] at its baseline: see [collapsed]. A part of a stack
 * comes down to the stack's foot with the [wholeStack], and to its own foot
 * otherwise: a series turned on in the middle of a stack opens in its place,
 * as the parts above it move up to make room.
 */
private fun collapsedLayer(layer: Layer, chart: ResolvedChart, wholeStack: Boolean = false): Layer {
    val valueLow = if (chart.horizontal) chart.x.low else chart.upAxis(layer.mark.onEnd).low
    if (wholeStack && layer.stacked) {
        val foot = stackFoot(layer, chart)
        val floor = DoubleArray(layer.size) { foot }
        val lying = chart.horizontal && layer.mark.kind == MarkKind.Bar
        return if (lying) layer.copy(across = floor, base = floor.copyOf()) else layer.copy(up = floor, base = floor.copyOf())
    }
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
        // Points and rectangles have no baseline to rise from: they fade in.
        MarkKind.Point, MarkKind.Rectangle -> layer.copy(opacity = 0f)
    }
}

private fun blendAxis(from: ResolvedAxis, to: ResolvedAxis, t: Double): ResolvedAxis =
    if (from.kind != to.kind || to.kind == AxisKind.Category) {
        to
    } else {
        to.spanning(lerp(from.low, to.low, t), lerp(from.high, to.high, t))
    }

private fun blendLayer(from: Layer, to: Layer, t: Double, horizontal: Boolean, sliding: Boolean = false): Layer {
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
            // Into a stack from side by side, each part rises to its place in
            // the stack first, still beside the others, and then they close up
            // into one bar; out of one, they part first and then come down.
            // Moved both ways at once, every part hung in the air over the one
            // it would stand on, or sank through it, and the stack came
            // together only as it stopped: *"all the sections look like the
            // top stack, and they only snap into position once they stop"*.
            // In turn, the parts are at different heights all the time they
            // move across, and meet edge to edge.
            val regroups = from.stacked != to.stacked && (from.groupWidth != to.groupWidth || from.groupOffset != to.groupOffset)
            val up = if (!regroups) t else if (to.stacked) firstHalf(t) else secondHalf(t)
            val across = if (!regroups) t else if (to.stacked) secondHalf(t) else firstHalf(t)
            blendByPlace(from, to, up, lying, keepGone = sliding)
                .copy(groupWidth = lerp(from.groupWidth, to.groupWidth, across), groupOffset = lerp(from.groupOffset, to.groupOffset, across))
        }

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
                groupWidth = lerp(from.groupWidth, to.groupWidth, t),
                groupOffset = lerp(from.groupOffset, to.groupOffset, t),
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
            fun moved(old: DoubleArray?, new: DoubleArray): DoubleArray =
                DoubleArray(to.size) { i -> if (old != null && i < old.size) lerp(old[i], new[i], t) else new[i] }
            // A rectangle by both its corners: only its first moved, and one
            // moving right stretched out to where it was going and then
            // closed up behind.
            to.copy(
                across = moved(from.across, to.across),
                up = moved(from.up, to.up),
                acrossEnd = to.acrossEnd?.let { moved(from.acrossEnd, it) },
                upEnd = to.upEnd?.let { moved(from.upEnd, it) },
            )
        }
    }
}

/**
 * [to] as [t] of the way from [from], each value matched by its place: the bar
 * for March becomes the bar for March, top and foot. One with nothing to come
 * from grows from its foot, and a part of a stack at a new place from the
 * stack's foot, so that a new place's stack rises as one: see [collapsed].
 * With [keepGone], along an axis that slides, one that is not there any more
 * goes down to its foot.
 */
private fun blendByPlace(from: Layer, to: Layer, t: Double, lying: Boolean, keepGone: Boolean = false): Layer {
    val oldPlaces = if (lying) from.up else from.across
    val oldTops = if (lying) from.across else from.up
    val newPlaces = if (lying) to.up else to.across
    val newTops = if (lying) to.across else to.up
    // The foot a new place's stack rises from: zero, which every stack is
    // built up and down from.
    val stackFoot = if (to.stacked) 0.0 else null
    val tops = DoubleArray(to.size) { i ->
        val match = oldPlaces.indexOfFirst { it == newPlaces[i] }
        val start = if (match >= 0) oldTops[match] else (stackFoot ?: to.base?.get(i) ?: 0.0)
        lerp(start, newTops[i], t)
    }
    val base = to.base?.let { newBase ->
        DoubleArray(to.size) { i ->
            val match = oldPlaces.indexOfFirst { it == newPlaces[i] }
            val start = if (match >= 0) from.base?.get(match) ?: newBase[i] else (stackFoot ?: newBase[i])
            lerp(start, newBase[i], t)
        }
    }
    // And a place that has gone sinks to its foot where it was, rather than
    // vanishing in the first frame: a month's bars going to a fortnight's
    // slide off the plot's edge as the axis closes in, going down as they go.
    // Only for bars that are a value each; a range has its ends kept beside it.
    val gone = if (!keepGone || to.spans || to.acrossEnd != null || to.upEnd != null) {
        emptyList()
    } else {
        oldPlaces.indices.filter { j -> !oldTops[j].isNaN() && newPlaces.none { it == oldPlaces[j] } }
    }
    if (gone.isNotEmpty()) {
        val foot = { j: Int -> stackFoot ?: from.base?.get(j) ?: 0.0 }
        val places = newPlaces + DoubleArray(gone.size) { oldPlaces[gone[it]] }
        val allTops = tops + DoubleArray(gone.size) { lerp(oldTops[gone[it]], foot(gone[it]), t) }
        val allBase = (base ?: DoubleArray(to.size)) + DoubleArray(gone.size) {
            val j = gone[it]
            lerp(from.base?.get(j) ?: 0.0, foot(j), t)
        }
        val values = to.values + DoubleArray(gone.size) { from.values[gone[it]] }
        return if (lying) {
            to.copy(across = allTops, up = places, base = allBase, values = values)
        } else {
            to.copy(across = places, up = allTops, base = allBase, values = values)
        }
    }
    return if (lying) to.copy(across = tops, base = base) else to.copy(up = tops, base = base)
}

/** The kinds of mark one series can be drawn as in turn and move between: a value at each place. */
private val Redrawable = setOf(MarkKind.Bar, MarkKind.Line, MarkKind.Area)

/**
 * [source]'s values in [shape]'s form: at [shape]'s places, read off
 * [source]'s, as a mark of [shape]'s kind, where a move between the two
 * starts or ends. A bar or an area from a line, which has no foot, keeps the
 * height it is going to have and stands where the line was; one going to a
 * line keeps its height and goes to where the line is.
 *
 * Not for a part of a stack and a line, which [heldOn] keeps in place
 * instead: a part moved to a line's value slid down through the parts under
 * it.
 */
private fun reshaped(source: Layer, shape: Layer, chart: ResolvedChart): Layer {
    val lying = chart.horizontal
    val sourcePlaces = if (lying) source.up else source.across
    val sourceValues = if (lying) source.across else source.up
    val places = if (lying) shape.up else shape.across
    val shapeValues = if (lying) shape.across else shape.up
    val values = DoubleArray(shape.size) { i -> sampleAt(sourcePlaces, sourceValues, places[i]) }
    val base = shape.base?.let { shapeBase ->
        val sourceBase = source.base
        DoubleArray(shape.size) { i ->
            if (sourceBase != null) sampleAt(sourcePlaces, sourceBase, places[i]) else values[i] - (shapeValues[i] - shapeBase[i])
        }
    }
    return if (lying) shape.copy(across = values, base = base) else shape.copy(up = values, base = base)
}

/**
 * [layer], drawn on [axis], where it is on the plot now that the value axis
 * is [now]: still, while the axis moves under it. A stack fading out as the
 * axis shrinks to a line's range otherwise grew out of the top of the plot
 * as it went, and one fading in grew down into it.
 */
private fun heldOn(layer: Layer, axis: ResolvedAxis, now: ResolvedAxis, horizontal: Boolean): Layer {
    fun held(values: DoubleArray) = DoubleArray(values.size) { now.low + (values[it] - axis.low) / axis.span * now.span }
    val base = layer.base?.let(::held)
    return if (horizontal) layer.copy(across = held(layer.across), base = base) else layer.copy(up = held(layer.up), base = base)
}

/**
 * [from] in [to]'s units up its start axis, where the two are in different
 * ones: a stack and the same stack as shares of each place's whole. Each
 * value keeps its place on the plot, and the axis is the new one from the
 * first frame, so the bars move to their shares and the labels change once.
 * Blended across, the axis's span went from thousands to one, and every new
 * label sat in a heap at the foot of the plot until the last frames.
 */
private fun inUnitsOf(from: ResolvedChart, to: ResolvedChart): ResolvedChart {
    if (from.inShares == to.inShares || from.empty || to.empty) return from
    val old = from.valueAxis
    val new = to.valueAxis
    fun moved(value: Double): Double = new.low + (value - old.low) / old.span * new.span
    fun moved(values: DoubleArray): DoubleArray = DoubleArray(values.size) { moved(values[it]) }
    val layers = from.layers.map { layer ->
        if (layer.mark.onEnd) return@map layer
        val base = layer.base?.let(::moved)
        if (from.horizontal) layer.copy(across = moved(layer.across), base = base) else layer.copy(up = moved(layer.up), base = base)
    }
    // The rules and regions up it too, so that a target line keeps its
    // place as the bars do rather than sweeping in from thousands.
    fun movedOr(value: Double?) = value?.let(::moved)
    val rules = from.rules.map { rule ->
        when {
            rule.onEnd -> rule
            from.horizontal -> Rule(movedOr(rule.x), rule.y, rule.label, rule.style, rule.onEnd)
            else -> Rule(rule.x, movedOr(rule.y), rule.label, rule.style, rule.onEnd)
        }
    }
    val regions = from.regions.map { region ->
        when {
            region.onEnd -> region
            from.horizontal -> Region(movedOr(region.xStart), movedOr(region.xEnd), region.yStart, region.yEnd, region.label, region.style, region.onEnd)
            else -> Region(region.xStart, region.xEnd, movedOr(region.yStart), movedOr(region.yEnd), region.label, region.style, region.onEnd)
        }
    }
    val x = if (from.horizontal) new else from.x
    val y = if (from.horizontal) from.y else new
    return from.with(x, y, layers, from.yEnd, rules, regions)
}

/** Whether [this]'s start axis reads in shares of each place's whole: a normalised stack's. */
internal val ResolvedChart.inShares: Boolean
    get() = layers.any { !it.mark.onEnd && it.mark.stacking == ChartStacking.Normalised && (it.mark.kind == MarkKind.Bar || it.mark.kind == MarkKind.Area) }

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

/** How far through the first half of a move [t] is, as a move of its own. */
private fun firstHalf(t: Double): Double = (t * 2).coerceIn(0.0, 1.0)

/** How far through the second half of a move [t] is, as a move of its own. */
private fun secondHalf(t: Double): Double = (t * 2 - 1).coerceIn(0.0, 1.0)

private fun lerp(a: Float, b: Float, t: Double): Float = a + (b - a) * t.toFloat()

private fun lerp(a: Double, b: Double, t: Double): Double = when {
    a.isNaN() -> b
    b.isNaN() -> b
    else -> a + (b - a) * t
}
