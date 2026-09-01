package io.kontour.ui.foundation

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp

/**
 * Draws a slash across the content, [progress] of the way along it.
 *
 * The "no" stroke: an eye that is showing something it should not, a bell that
 * is silenced, a layer that is hidden. Every icon set ships a second glyph with
 * the slash already in it, and swapping to that glyph is a cut — the slash is
 * simply there on the next frame. Drawn here instead, it is a stroke that
 * *arrives*, from one end to the other, which is what the gesture that turned it
 * on looked like.
 *
 * ### The halo
 *
 * A line laid straight over a glyph reads as an artefact — two dark shapes
 * overlapping with no depth between them. Every hand-drawn slashed icon cuts a
 * groove out of the glyph where the line passes, and that is what [halo] is: the
 * same stroke, wider, in the colour of whatever is behind the icon, drawn first.
 * The slash then sits in its own gap and reads as being on top.
 *
 * @param progress A lambda, so the value is read in the draw phase. A slash that
 *   animates through a parameter recomposes its whole button sixty times a
 *   second to move a line.
 * @param halo The colour behind the glyph — the button's container, not the
 *   page. A groove cut in the wrong colour is a second line beside the first.
 */
internal fun Modifier.strikethrough(
    progress: () -> Float,
    color: Color,
    halo: Color,
    width: Dp,
): Modifier = drawWithContent {
    drawContent()

    val fraction = progress().coerceIn(0f, 1f)
    if (fraction <= 0f) return@drawWithContent

    // Held off the corners: a slash that starts exactly in the corner of the
    // box reads as a frame around the icon rather than a mark on it.
    val inset = size.minDimension * SlashInset
    val start = Offset(inset, inset)
    val end = Offset(size.width - inset, size.height - inset)
    val to = Offset(
        start.x + (end.x - start.x) * fraction,
        start.y + (end.y - start.y) * fraction,
    )

    val strokeWidth = width.toPx()
    drawLine(
        color = halo,
        start = start,
        end = to,
        strokeWidth = strokeWidth * HaloWidth,
        cap = StrokeCap.Round,
    )
    drawLine(
        color = color,
        start = start,
        end = to,
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round,
    )
}

/** How far in from the corners the slash begins and ends. */
private const val SlashInset = 0.06f

/** The groove is twice the line, which leaves half a line of gap on each side. */
private const val HaloWidth = 2f
