package io.kontour.ui.charts

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.kontour.ui.theme.Theme

/**
 * A trend at the size of a word: a line through [values], with no axes, for a
 * table cell, a stat or the end of a sentence.
 *
 * ```kotlin
 * Row(verticalAlignment = Alignment.CenterVertically) {
 *     Text("Riders")
 *     Sparkline(lastWeek, modifier = Modifier.size(width = 64.dp, height = 20.dp))
 * }
 * ```
 *
 * It shows the shape and nothing else, so it is for a trend a reader takes in
 * at a glance — up, down, steady, spiky — beside the number that matters. To
 * read values off it, it should be a [Chart].
 *
 * A screen reader hears it as a sentence: the [contentDescription], then the
 * least and the most of it and which way it went.
 *
 * @param values The values, in order. `null` is a gap.
 * @param contentDescription What it is a trend of: "Riders this week".
 * @param colour The line's colour: the first of the series palette, unless it
 *   should say something — `Theme.colours.danger.solid` for a fall that matters.
 * @param showArea Whether the area under the line is washed in its colour.
 * @param animateData Whether the line draws itself in when it first appears,
 *   and moves to its new shape when [values] change.
 * @param strings The words it says on its own.
 */
@Composable
fun Sparkline(
    values: List<Number?>,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    colour: Color = ChartDefaults.seriesColours().first(),
    showArea: Boolean = false,
    animateData: Boolean = true,
    strings: ChartStrings = ChartDefaults.strings(),
) {
    val formats = Theme.formats
    val motion = Theme.motion
    val numbers = remember(values) { DoubleArray(values.size) { values[it]?.toDouble() ?: Double.NaN } }

    val description = run {
        val present = numbers.filter { !it.isNaN() }
        val sentences = Theme.strings.sentences
        remember(numbers, strings, formats, contentDescription, sentences) {
            val parts = mutableListOf<String>()
            contentDescription?.takeIf { it.isNotBlank() }?.let { parts += it }
            parts += strings.trend
            if (present.isEmpty()) {
                parts += strings.noData
            } else {
                val digits = niceTicks(present.min(), present.max(), ChartAxisDefaults.TickCount).fractionDigits
                val direction = when (trendOf(numbers)) {
                    1 -> strings.trendRising
                    -1 -> strings.trendFalling
                    else -> strings.trendSteady
                }
                parts += strings.valuesSummary(formats.decimal(present.min(), digits), formats.decimal(present.max(), digits), direction)
            }
            sentences(parts)
        }
    }

    val reveals = animateData && !motion.reduceMotion
    val reveal = remember { Animatable(if (reveals) 0f else 1f) }
    // Drawn in as it first appears, and again as its first values arrive where
    // it had none: they used to be there at once.
    val empty = numbers.all { it.isNaN() }
    LaunchedEffect(empty) {
        if (empty) {
            if (reveals) reveal.snapTo(0f)
        } else if (reveal.value < 1f) {
            reveal.animateTo(1f, motion.tweenSlow())
        }
    }

    // **New values move the line to its new shape**, as a chart's do, rather
    // than replacing it: asked for as *"if the data in the sparkline changes,
    // it animates"*. From the line as it is drawn, so values that change
    // faster than the move carry on from wherever it had got to; each new
    // point from where that line was at the same place along it, so a live
    // trend that gains a value and drops its oldest slides into shape rather
    // than every point climbing to its neighbour's height.
    val drawn = remember { DrawnLine() }
    val from = remember { mutableStateOf<List<Offset?>?>(null) }
    val morph = remember { Animatable(1f) }
    LaunchedEffect(numbers) {
        val before = drawn.points
        if (!reveals || reveal.value < 1f || before == null || before.all { it == null }) return@LaunchedEffect
        from.value = before
        morph.snapTo(0f)
        morph.animateTo(1f, motion.springOrTween(motion.springGentle))
        from.value = null
    }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val lineWidth = Theme.sizing.borderWidthStrong

    Spacer(
        modifier
            .defaultMinSize(minWidth = DefaultWidth, minHeight = DefaultHeight)
            .semantics { this.contentDescription = description }
            .drawWithCache {
                val stroke = lineWidth.toPx()
                val present = numbers.filter { !it.isNaN() }
                val low = present.minOrNull() ?: 0.0
                val high = present.maxOrNull() ?: 1.0
                val span = (high - low).takeIf { it > 0 } ?: 1.0
                val inset = stroke
                val last = (numbers.size - 1).coerceAtLeast(1)
                val points = numbers.indices.map { i ->
                    val value = numbers[i]
                    if (value.isNaN()) {
                        null
                    } else {
                        val fraction = i.toFloat() / last
                        val x = inset + fraction * (size.width - 2 * inset)
                        val y = size.height - inset - ((value - low) / span).toFloat() * (size.height - 2 * inset)
                        Offset(if (rtl) size.width - x else x, y)
                    }
                }
                fun areaUnder(points: List<Offset?>, path: Path = Path()): Path {
                    var run = mutableListOf<Offset>()
                    fun finish() {
                        if (run.size > 1) {
                            appendRun(path, run, ChartInterpolation.Monotone, moveFirst = true)
                            path.lineTo(run.last().x, size.height)
                            path.lineTo(run.first().x, size.height)
                            path.close()
                        }
                        run = mutableListOf()
                    }
                    points.forEach { point -> if (point == null) finish() else run += point }
                    finish()
                    return path
                }
                val line = linePath(points, ChartInterpolation.Monotone)
                val area = if (showArea) areaUnder(points) else null
                val lineStroke = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
                // Where each new point sets off from: the old line's height at
                // the same place along it.
                val start = from.value?.let { old -> points.mapIndexed { i, point -> point?.let { it.copy(y = old.heightAt(i.toFloat() / last) ?: it.y) } } }
                onDrawBehind {
                    val t = morph.value
                    val moving = start != null && t < 1f
                    val now = if (start != null && moving) {
                        points.mapIndexed { i, point ->
                            val was = start[i]
                            if (point == null || was == null) point else Offset(point.x, was.y + (point.y - was.y) * t)
                        }
                    } else {
                        points
                    }
                    drawn.points = now
                    // Drawn in from the start edge, as the line is read.
                    val shown = size.width * reveal.value
                    val left = if (rtl) size.width - shown else 0f
                    clipRect(left = left, right = left + shown) {
                        // While it moves, into the two paths it keeps for
                        // that, emptied, rather than two new ones a frame.
                        (if (moving && showArea) areaUnder(now, drawn.area.apply { rewind() }) else area)
                            ?.let { drawPath(it, colour, alpha = AreaAlpha) }
                        drawPath(
                            if (moving) linePath(now, ChartInterpolation.Monotone, into = drawn.line.apply { rewind() }) else line,
                            colour,
                            style = lineStroke,
                        )
                    }
                }
            },
    )
}

/**
 * The line as it was last drawn, for the next change of values to move from,
 * and the paths it is drawn into while it moves. Not state: only draw writes it.
 *
 * Raised 2026-10-29 for 0.5.1: each frame of a move made a new path for the
 * line and another for its area, native objects each, in every sparkline
 * moving at once, a list of them taking a reading together.
 */
private class DrawnLine {
    var points: List<Offset?>? = null
    val line = Path()
    val area = Path()
}

/**
 * This line's height [fraction] of the way along it, from its points spread
 * evenly from start to end, or null over a gap.
 */
private fun List<Offset?>.heightAt(fraction: Float): Float? {
    if (isEmpty()) return null
    val at = fraction.coerceIn(0f, 1f) * (size - 1)
    val below = this[at.toInt()]
    val above = this[minOf(at.toInt() + 1, size - 1)]
    val share = at - at.toInt()
    return when {
        below != null && above != null -> below.y + (above.y - below.y) * share
        else -> (below ?: above)?.y
    }
}

/** A sparkline's size when nothing gives it one: about a word's. */
private val DefaultWidth = 64.dp
private val DefaultHeight = 20.dp

/** How strongly the area under a sparkline is washed. */
private const val AreaAlpha = 0.18f
