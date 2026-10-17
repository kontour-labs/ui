package io.kontour.ui.nav

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.kontour.ui.theme.SquircleShape
import io.kontour.ui.theme.cornerReaches

/**
 * A selected document tab's outline: its [body], with a foot at each bottom
 * corner that curves out into whatever it stands on, so the tab and the page
 * below it read as one piece, as a browser's tab and its page do.
 *
 * Drawn at the body's width plus [foot] on either side: the body stands in the
 * middle, and each foot fills the corner between its side and the bottom edge,
 * hollowed by a squircle's corner — the library's own curve, turned inside out
 * — that leaves the side straight up and meets the bottom flat. Its reach is
 * [foot] exactly, so the curve lets go of the side and the bottom where the
 * foot does, with no step at either end.
 *
 * Symmetric, so the same in either direction.
 */
internal class FlaredTabShape(
    private val body: CornerBasedShape,
    private val foot: Dp,
) : Shape {

    // The last outline made, and what it was made for: a selected tab asks for
    // its ground and, at the high contrast tier, its edge, every frame its strip
    // animates. As `ChatBubbleShape` does. Not part of the shape's equality.
    private var lastSize = Size.Unspecified
    private var lastDirection: LayoutDirection? = null
    private var lastDensity = 0f
    private var last: Outline? = null

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val cached = last
        if (cached != null && size == lastSize && layoutDirection == lastDirection && density.density == lastDensity) {
            return cached
        }
        return outline(size, layoutDirection, density).also {
            last = it
            lastSize = size
            lastDirection = layoutDirection
            lastDensity = density.density
        }
    }

    private fun outline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val w = size.width
        val h = size.height
        // A tab with no room is nothing. A strip squeezed to no height — the
        // second of two in a column, when the first took all its room — drew
        // its selected tab's ground at less than none, which is not a shape
        // corners can be fitted to: asking threw.
        if (w <= 0f || h <= 0f) return Outline.Rectangle(Rect.Zero)
        // No wider than a third of the whole, and no taller than half of it, so
        // a tab shrunk to nothing as it closes is a body still.
        val r = with(density) { foot.toPx() }.coerceAtMost(minOf(w / 3f, h / 2f)).coerceAtLeast(0f)
        val bodySize = Size((w - 2 * r).coerceAtLeast(0f), h)
        val path = Path()
        path.addOutline(body.createOutline(bodySize, layoutDirection, density))
        path.translate(Offset(r, 0f))
        if (r <= 0f || bodySize.width <= 0f) return Outline.Generic(path)

        val cutter = footCutter(r, density)
        // Left: the square between the body's side and the bottom, less a
        // rounded corner standing in the square's bottom right.
        val left = Path.combine(
            PathOperation.Difference,
            Path().apply { addRect(Rect(0f, h - r, r, h)) },
            Path().apply {
                addOutline(cutter.end.createOutline(Size(2 * r, 2 * r), LayoutDirection.Ltr, density))
                translate(Offset(-r, h - 2 * r))
            },
        )
        val right = Path.combine(
            PathOperation.Difference,
            Path().apply { addRect(Rect(w - r, h - r, w, h)) },
            Path().apply {
                addOutline(cutter.start.createOutline(Size(2 * r, 2 * r), LayoutDirection.Ltr, density))
                translate(Offset(w - r, h - 2 * r))
            },
        )
        return Outline.Generic(Path.combine(PathOperation.Union, Path.combine(PathOperation.Union, path, left), right))
    }

    /** The squircles each foot is hollowed by: one rounded at its bottom end, one at its bottom start. */
    private class Cutters(val end: SquircleShape, val start: SquircleShape)

    /**
     * The corner to hollow a foot with, sized so its curve reaches [r] along
     * both edges. A squircle's curve reaches past its radius by its smoothing,
     * so the radius comes from what [cornerReaches] measures for a first guess,
     * scaled once to land on [r].
     */
    private fun footCutter(r: Float, density: Density): Cutters {
        val square = Size(2 * r, 2 * r)
        fun cutter(radius: Float) = with(density) {
            SquircleShape(topStart = 0.dp, topEnd = 0.dp, bottomEnd = radius.toDp(), bottomStart = 0.dp)
        }
        val guess = r / (1f + SquircleShape.DefaultSmoothing)
        val reached = cutter(guess).cornerReaches(square, density, LayoutDirection.Ltr).bottomRight.x
        val radius = if (reached > 0f) guess * r / reached else guess
        val end = cutter(radius)
        val start = with(density) {
            SquircleShape(topStart = 0.dp, topEnd = 0.dp, bottomEnd = 0.dp, bottomStart = radius.toDp())
        }
        return Cutters(end, start)
    }

    override fun equals(other: Any?): Boolean = other is FlaredTabShape && other.body == body && other.foot == foot

    override fun hashCode(): Int = 31 * body.hashCode() + foot.hashCode()
}
