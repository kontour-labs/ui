package io.kontour.ui.foundation

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import io.kontour.ui.theme.Theme

/**
 * A tick that writes itself on and rubs itself out.
 *
 * The same mark a [io.kontour.ui.components.selection.Checkbox] draws, without
 * the box around it — for the places a selection is shown by a bare tick: a
 * selected menu item, a chosen option in a `Select`. Those used to swap an
 * `Icon` in and out on the frame the value changed, which is fine where the menu
 * closes on the way out and conspicuous where it does not. A `MultiSelect` stays
 * open, so every tick it draws is one the user is looking directly at.
 *
 * The box is always laid out; only the drawing is conditional. A mark that
 * occupies space when checked and none when not is a row of labels that shifts
 * as the user works down it.
 *
 * @param spread See [drawCheckMark]. Larger than the checkbox's, because there
 *   is no border here to leave room for.
 */
@Composable
internal fun AnimatedCheckMark(
    checked: Boolean,
    colour: Color,
    size: Dp,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = Theme.sizing.borderWidthStrong,
    spread: Float = 1.6f,
) {
    val motion = Theme.motion
    val progress by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = motion.tweenDefault(),
        label = "checkMark",
    )

    Canvas(modifier.size(size)) {
        drawCheckMark(
            flatness = 0f,
            progress = progress,
            colour = colour,
            strokeWidth = strokeWidth.toPx(),
            spread = spread,
        )
    }
}

/**
 * Draws the mark: a tick, the indeterminate bar, or any point between them.
 *
 * ### One shape, not two
 *
 * The tick and the bar are the same three points — a start, an elbow and an end
 * — at different heights. [flatness] lifts the elbow and levels the two ends
 * onto one line, so a box going from ticked to indeterminate *flattens*.
 *
 * It used to be two branches of an `if`, and the state flipped between them in
 * one frame while a single progress value animated: the tick was rubbed out and
 * the bar snapped in, which is exactly what the report described. Nothing was
 * animating the thing that was actually changing.
 *
 * ### Where the reveal starts
 *
 * A tick is *written*, from its start to its end, because that is how a tick is
 * drawn by hand. A bar grows from its middle outwards, because it has no start —
 * it is a symmetrical mark and revealing it left-to-right reads as a tick that
 * lost its way.
 *
 * So the reveal's pivot travels with [flatness] too: the visible span runs from
 * `pivot × (1 − progress)` to `pivot + (1 − pivot) × progress`, which is
 * `0 → progress` for a tick and symmetric about the centre for a bar, with no
 * special case for either.
 *
 * @param spread How much of the drawing box the mark occupies, relative to the
 *   proportions a [io.kontour.ui.components.selection.Checkbox] wants. `1` is a mark sitting inside a box with a
 *   border and some air around it; a tick drawn on its own — a selected menu
 *   item — has neither and wants more.
 */
internal fun DrawScope.drawCheckMark(
    flatness: Float,
    progress: Float,
    colour: Color,
    strokeWidth: Float,
    spread: Float = 1f,
) {
    if (progress <= 0f) return

    val w = size.width
    val h = size.height
    val f = flatness.coerceIn(0f, 1f)

    fun blend(tick: Offset, bar: Offset): Offset {
        val x = tick.x + (bar.x - tick.x) * f
        val y = tick.y + (bar.y - tick.y) * f
        // Around the centre, so a mark drawn on its own rather than inside a
        // box can fill the space it was given. See [spread].
        return Offset(
            w / 2f + (x - w / 2f) * spread,
            h / 2f + (y - h / 2f) * spread,
        )
    }

    // The bar's three points are collinear, so the elbow simply has nowhere to
    // be but the middle — which is what makes the blend a flattening rather than
    // a slide.
    val start = blend(Offset(w * 0.24f, h * 0.52f), Offset(w * 0.25f, h * 0.5f))
    val elbow = blend(Offset(w * 0.43f, h * 0.70f), Offset(w * 0.50f, h * 0.5f))
    val end = blend(Offset(w * 0.76f, h * 0.32f), Offset(w * 0.75f, h * 0.5f))

    val firstLength = (elbow - start).getDistance()
    val secondLength = (end - elbow).getDistance()
    val total = firstLength + secondLength
    if (total <= 0f) return

    val pivot = 0.5f * f
    val lo = (pivot * (1f - progress)) * total
    val hi = (pivot + (1f - pivot) * progress) * total

    /** Where along the two segments a distance from the start falls. */
    fun pointAt(distance: Float): Offset = if (distance <= firstLength) {
        val t = if (firstLength == 0f) 0f else distance / firstLength
        Offset(start.x + (elbow.x - start.x) * t, start.y + (elbow.y - start.y) * t)
    } else {
        val t = if (secondLength == 0f) 0f else (distance - firstLength) / secondLength
        Offset(elbow.x + (end.x - elbow.x) * t, elbow.y + (end.y - elbow.y) * t)
    }

    val path = Path().apply {
        val from = pointAt(lo)
        moveTo(from.x, from.y)
        // The elbow is a real corner and has to be a vertex, or a mark revealed
        // across it draws as a straight line between two points on either side.
        if (lo < firstLength && hi > firstLength) lineTo(elbow.x, elbow.y)
        val to = pointAt(hi)
        lineTo(to.x, to.y)
    }

    drawPath(
        path = path,
        color = colour,
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}
