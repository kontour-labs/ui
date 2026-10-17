package io.kontour.ui.foundation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.kontour.ui.theme.Theme

/**
 * How a surface is filled over its colour, as a second cue beside it: a
 * chart's series, an event on a calendar.
 *
 * Colour alone never carries meaning in this library, and where two things of
 * one kind are told apart by colour — two series, a tentative meeting and a
 * confirmed one — a pattern says it again for a reader who cannot see the
 * difference, or has asked not to rely on it. Drawn in a wash over the colour,
 * so it reads on any colour and in either scheme.
 */
enum class FillPattern {
    Solid,
    Diagonal,
    Dots,
    CrossHatch,
    Horizontal,
    ReverseDiagonal,
    Vertical,
}

/**
 * [pattern] drawn over what is under it, in [ink], cut to [shape]: nothing for
 * [FillPattern.Solid]. After the node's own background, so the pattern lies on
 * its ground and under its content.
 *
 * ```kotlin
 * Box(Modifier.background(tone.container, shape).fillPattern(FillPattern.Diagonal, tone.onContainer.copy(alpha = 0.25f), shape))
 * ```
 *
 * The pattern is laid in the window's own space rather than each node's, so the
 * hatching of two surfaces side by side lines up and reads as one.
 *
 * @param ink The pattern's colour: a wash of the content's colour reads over
 *   the ground without fighting the text on it.
 * @param tile How far the pattern repeats.
 */
@Composable
fun Modifier.fillPattern(
    pattern: FillPattern,
    ink: Color,
    shape: Shape = RectangleShape,
    tile: Dp = FillPatternTile,
): Modifier {
    if (pattern == FillPattern.Solid) return this
    val brushes = remember { PatternBrushes() }
    val stroke = Theme.sizing.borderWidth * 1.5f
    return drawWithCache {
        val brush = brushes.brush(pattern, ink, tile.roundToPx(), stroke.toPx())
        val outline = shape.createOutline(size, layoutDirection, this)
        onDrawWithContent {
            if (brush != null) drawOutline(outline, brush)
            drawContent()
        }
    }
}

/** How far a [fillPattern] repeats by default: a chart's hatching's. */
private val FillPatternTile = 7.dp

/**
 * The tiles a [FillPattern] is drawn from, one brush for each pattern, ink and
 * size, made once and kept. For a canvas of an app's own, as a chart's.
 *
 * A shader rather than a clip and a loop of lines: a large area costs one fill
 * however many lines would cross it, and because the shader is laid in the
 * canvas's own space, the hatching of neighbouring shapes lines up, so a stack
 * reads as one surface with lines across it rather than as bricks.
 */
class PatternBrushes {
    private val tiles = HashMap<Triple<FillPattern, Color, Int>, Brush>()

    /** [pattern] in [ink], with a tile [size] pixels square and lines [stroke] wide; `null` for [FillPattern.Solid]. */
    fun brush(pattern: FillPattern, ink: Color, size: Int, stroke: Float): Brush? {
        if (pattern == FillPattern.Solid || size <= 0) return null
        return tiles.getOrPut(Triple(pattern, ink, size)) {
            ShaderBrush(ImageShader(tile(pattern, ink, size, stroke), TileMode.Repeated, TileMode.Repeated))
        }
    }

    private fun tile(pattern: FillPattern, ink: Color, size: Int, stroke: Float): ImageBitmap {
        val image = ImageBitmap(size, size)
        val canvas = Canvas(image)
        val paint = Paint().apply {
            color = ink
            strokeWidth = stroke
            isAntiAlias = true
            style = PaintingStyle.Stroke
            strokeCap = StrokeCap.Square
        }
        val s = size.toFloat()
        // Each diagonal is drawn three times, a tile apart, so that the part
        // of a line that leaves one edge comes back in at the opposite one and
        // the repeat has no seam.
        fun rising() = listOf(-s, 0f, s).forEach { o -> canvas.drawLine(Offset(o, s), Offset(o + s, 0f), paint) }
        fun falling() = listOf(-s, 0f, s).forEach { o -> canvas.drawLine(Offset(o, 0f), Offset(o + s, s), paint) }
        when (pattern) {
            FillPattern.Solid -> Unit
            FillPattern.Diagonal -> rising()
            FillPattern.ReverseDiagonal -> falling()
            FillPattern.CrossHatch -> {
                rising()
                falling()
            }
            FillPattern.Horizontal -> canvas.drawLine(Offset(0f, s / 2), Offset(s, s / 2), paint)
            FillPattern.Vertical -> canvas.drawLine(Offset(s / 2, 0f), Offset(s / 2, s), paint)
            FillPattern.Dots -> {
                paint.style = PaintingStyle.Fill
                canvas.drawCircle(Offset(s / 2, s / 2), stroke * 1.1f, paint)
            }
        }
        return image
    }
}
