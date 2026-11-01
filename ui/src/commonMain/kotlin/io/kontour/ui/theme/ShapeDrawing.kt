package io.kontour.ui.theme

import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Rect
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.unit.Density
import kotlin.math.min

/*
 * The theme's corners, for what the library draws rather than clips.
 *
 * A progress bar's ends, a slider's track, a page dot and a timeline's stop
 * are drawn on a canvas, and each used to name its own radius — half its
 * height, half its width — which no theme could reach: a square theme had
 * square buttons beside round-ended bars and round dots. Raised for 0.5.2.
 *
 * Each of these resolves a token to the radius it gives the box being drawn,
 * and draws exactly what was drawn before when that radius is the one that
 * was named: a default capsule on a bar is half the bar's height, a default
 * pill on a dot is the dot's radius, so the default theme does not move by a
 * pixel. Only a theme that asks for a squarer corner gets a different call.
 */

/**
 * The radius this shape gives the top-start corner of a box of [size], no
 * more than half its shorter side: what `drawRoundRect` wants to draw it.
 *
 * One corner, because everything drawn this way is the same at all four.
 */
internal fun CornerBasedShape.cornerPx(size: Size, density: Density): Float =
    min(topStart.toPx(size, density), size.minDimension / 2f)

/** [cornerPx] as a [CornerRadius]. */
internal fun CornerBasedShape.cornerRadius(size: Size, density: Density): CornerRadius =
    CornerRadius(cornerPx(size, density))

/**
 * A dot of [radius] at [center], in [shape] — a circle in the default theme,
 * as it always was, and a rounded or square box in a theme whose [shape]
 * stops short of round.
 */
internal fun DrawScope.drawDot(
    color: Color,
    radius: Float,
    center: Offset,
    shape: CornerBasedShape,
    alpha: Float = 1f,
    style: DrawStyle = Fill,
) {
    if (radius <= 0f) return
    val box = Size(radius * 2f, radius * 2f)
    val corner = shape.cornerPx(box, this)
    if (corner >= radius) {
        drawCircle(color, radius = radius, center = center, alpha = alpha, style = style)
    } else {
        drawRoundRect(
            color = color,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = box,
            cornerRadius = CornerRadius(corner),
            alpha = alpha,
            style = style,
        )
    }
}

/**
 * A corner that is [base], but never rounder than [ceiling].
 *
 * What a grouped row's inner corners are: 4dp where two rows meet, unless the
 * rows themselves are squarer than that — in which case the inside of the
 * group was rounder than its outside, which a square theme showed up at once.
 * Deferred, as [CapsuleCornerSize] is, because either side may need the box.
 */
@Immutable
internal data class MinCornerSize(val base: CornerSize, val ceiling: CornerSize) : CornerSize {
    override fun toPx(shapeSize: Size, density: Density): Float =
        min(base.toPx(shapeSize, density), ceiling.toPx(shapeSize, density))
}

/**
 * Adds a [drawDot] of [radius] at [center] to this path: an oval in the
 * default theme, as it always was, and a rounded or square box where the
 * theme's [shape] is squarer.
 */
internal fun Path.addDot(center: Offset, radius: Float, shape: CornerBasedShape, density: Density) {
    val box = Rect(center, radius)
    val corner = shape.cornerPx(box.size, density)
    if (corner >= radius) addOval(box) else addRoundRect(RoundRect(box, CornerRadius(corner)))
}
