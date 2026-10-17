package io.kontour.ui.charts

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt


/** A line's dashes, in multiples of its width, as a path effect; `null` for a solid one. */
internal fun LinePattern.effect(width: Float): PathEffect? = when (this) {
    LinePattern.Solid -> null
    LinePattern.Dashed -> PathEffect.dashPathEffect(floatArrayOf(4f * width, 3f * width))
    LinePattern.Dotted -> PathEffect.dashPathEffect(floatArrayOf(0.01f, 2.5f * width))
    LinePattern.DashDot -> PathEffect.dashPathEffect(floatArrayOf(5f * width, 2.5f * width, 0.01f, 2.5f * width))
    LinePattern.LongDash -> PathEffect.dashPathEffect(floatArrayOf(9f * width, 3.5f * width))
}

/**
 * [symbol] centred on [centre], [size] across, added to [path].
 *
 * Each is sized to the same visual weight rather than the same box: a triangle
 * in a circle's box looks smaller than the circle, so it is drawn larger.
 */
internal fun addSymbol(path: Path, symbol: PointSymbol, centre: Offset, size: Float) {
    val r = size / 2
    val x = centre.x
    val y = centre.y
    when (symbol) {
        PointSymbol.Circle -> path.addOval(Rect(centre, r))
        PointSymbol.Square -> {
            // Its corners eased, as every square in the library is; still a
            // square beside the circle, at a glance and to the eye that needs
            // the two told apart.
            val h = r * 0.9f
            path.addRoundRect(RoundRect(Rect(x - h, y - h, x + h, y + h), CornerRadius(h * 0.35f)))
        }
        PointSymbol.Diamond -> {
            val h = r * 1.25f
            path.moveTo(x, y - h)
            path.lineTo(x + h, y)
            path.lineTo(x, y + h)
            path.lineTo(x - h, y)
            path.close()
        }
        PointSymbol.Triangle, PointSymbol.InvertedTriangle -> {
            val h = r * 1.3f
            val direction = if (symbol == PointSymbol.Triangle) 1f else -1f
            path.moveTo(x, y - h * direction)
            path.lineTo(x + h * 0.95f, y + h * 0.65f * direction)
            path.lineTo(x - h * 0.95f, y + h * 0.65f * direction)
            path.close()
        }
        PointSymbol.Cross, PointSymbol.Plus -> {
            // A cross of two bars, a third of the symbol thick, so it reads as
            // a shape when filled rather than as two hairlines.
            val arm = r * 1.15f
            val half = r * 0.36f
            val points = listOf(
                Offset(-half, -arm), Offset(half, -arm), Offset(half, -half), Offset(arm, -half),
                Offset(arm, half), Offset(half, half), Offset(half, arm), Offset(-half, arm),
                Offset(-half, half), Offset(-arm, half), Offset(-arm, -half), Offset(-half, -half),
            )
            val turned = if (symbol == PointSymbol.Cross) points.map { rotate45(it) } else points
            turned.forEachIndexed { index, point ->
                if (index == 0) path.moveTo(x + point.x, y + point.y) else path.lineTo(x + point.x, y + point.y)
            }
            path.close()
        }
    }
}

private fun rotate45(point: Offset): Offset {
    val c = sqrt(0.5f)
    return Offset((point.x - point.y) * c, (point.x + point.y) * c)
}

/**
 * The line through [points] — on the screen, in order across — with a break
 * wherever a point is missing, joined as [interpolation] says.
 *
 * A missing point is a gap rather than a line drawn straight across it,
 * because a line across a gap is a value nobody measured.
 */
internal fun linePath(points: List<Offset?>, interpolation: ChartInterpolation, into: Path = Path()): Path {
    var run = mutableListOf<Offset>()
    fun flush() {
        if (run.isNotEmpty()) appendRun(into, run, interpolation, moveFirst = true)
        run = mutableListOf()
    }
    points.forEach { point -> if (point == null) flush() else run += point }
    flush()
    return into
}

/**
 * Adds one unbroken run to [path], starting with a move to its first point when
 * [moveFirst], or a line to it when it continues a path already begun (the top
 * of an area, after its base).
 */
internal fun appendRun(path: Path, run: List<Offset>, interpolation: ChartInterpolation, moveFirst: Boolean) {
    if (run.isEmpty()) return
    val first = run.first()
    if (moveFirst) path.moveTo(first.x, first.y) else path.lineTo(first.x, first.y)
    if (run.size == 1) return
    when (interpolation) {
        ChartInterpolation.Linear -> for (i in 1 until run.size) path.lineTo(run[i].x, run[i].y)
        ChartInterpolation.StepStart -> for (i in 1 until run.size) {
            path.lineTo(run[i].x, run[i - 1].y)
            path.lineTo(run[i].x, run[i].y)
        }
        ChartInterpolation.StepEnd -> for (i in 1 until run.size) {
            path.lineTo(run[i - 1].x, run[i].y)
            path.lineTo(run[i].x, run[i].y)
        }
        ChartInterpolation.StepCentre -> for (i in 1 until run.size) {
            val middle = (run[i - 1].x + run[i].x) / 2
            path.lineTo(middle, run[i - 1].y)
            path.lineTo(middle, run[i].y)
            path.lineTo(run[i].x, run[i].y)
        }
        ChartInterpolation.Monotone -> monotone(path, run)
    }
}

/**
 * A curve through every point that never overshoots one: Fritsch and Carlson's
 * monotone cubic, from "Monotone Piecewise Cubic Interpolation" (1980), as
 * Swift Charts and d3 both draw it.
 *
 * A plain spline through a dip and a rise swings below the dip, and a reader
 * then sees a value lower than anything measured. This limits each point's
 * slope so the curve between two points stays between them.
 */
private fun monotone(path: Path, run: List<Offset>) {
    val n = run.size
    val slopes = FloatArray(n - 1) { i ->
        val dx = run[i + 1].x - run[i].x
        if (dx == 0f) 0f else (run[i + 1].y - run[i].y) / dx
    }
    val tangents = FloatArray(n)
    tangents[0] = slopes[0]
    tangents[n - 1] = slopes[n - 2]
    for (i in 1 until n - 1) {
        tangents[i] = if (slopes[i - 1] * slopes[i] <= 0f) 0f else (slopes[i - 1] + slopes[i]) / 2
    }
    for (i in 0 until n - 1) {
        if (slopes[i] == 0f) {
            tangents[i] = 0f
            tangents[i + 1] = 0f
            continue
        }
        val a = tangents[i] / slopes[i]
        val b = tangents[i + 1] / slopes[i]
        val h = a * a + b * b
        if (h > 9f) {
            val scale = 3f / sqrt(h)
            tangents[i] = scale * a * slopes[i]
            tangents[i + 1] = scale * b * slopes[i]
        }
    }
    for (i in 0 until n - 1) {
        val p0 = run[i]
        val p1 = run[i + 1]
        val third = (p1.x - p0.x) / 3
        path.cubicTo(
            p0.x + third, p0.y + tangents[i] * third,
            p1.x - third, p1.y - tangents[i + 1] * third,
            p1.x, p1.y,
        )
    }
}

/**
 * [points] cut down to what [width] pixels can show: in each column of pixels,
 * the first, the lowest, the highest and the last. M4, from Jugel et al.,
 * "M4: A Visualization-Oriented Time Series Data Aggregation" (2014).
 *
 * A line of ten thousand points on a phone is fifty points to a pixel, drawn on
 * top of each other. These four per column draw exactly the same picture, the
 * peaks included, which is what an average or every-nth-point would lose.
 */
internal fun decimate(points: List<Offset?>, width: Float): List<Offset?> {
    if (points.size <= max(4f * width, 64f)) return points
    val result = ArrayList<Offset?>(min(points.size, (width * 4).toInt() + 8))
    var column = Float.NaN
    var first: Offset? = null
    var low: Offset? = null
    var high: Offset? = null
    var last: Offset? = null
    fun emit() {
        val chosen = listOfNotNull(first, low, high, last).distinct().sortedBy { it.x }
        result.addAll(chosen)
        first = null
        low = null
        high = null
        last = null
    }
    points.forEach { point ->
        if (point == null) {
            emit()
            result += null
            column = Float.NaN
            return@forEach
        }
        val here = floor(point.x)
        if (column.isNaN() || abs(here - column) >= 1f) {
            emit()
            column = here
        }
        if (first == null) first = point
        if (low == null || point.y > low!!.y) low = point
        if (high == null || point.y < high!!.y) high = point
        last = point
    }
    emit()
    return result
}
