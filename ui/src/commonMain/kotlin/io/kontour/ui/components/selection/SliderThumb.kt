package io.kontour.ui.components.selection

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope

/**
 * One slider thumb, stretched by how far it is from where it is trying to be.
 *
 * Shared by [Slider] and [RangeSlider] because they are the same control with a
 * second thumb on it, and `RangeSlider`'s own header already says what happens
 * otherwise: *"Two sliders in one library that answer the same gesture
 * differently is worse than either of them being wrong."* That was true of the
 * gesture and had been copied by hand for the drawing.
 *
 * ### One signal, three situations
 *
 * [reachPx] is the gap between the thumb's **target** and where it is actually
 * drawn — signed, positive meaning it is being pulled to the right — and every
 * case the two sliders have falls out of it without a special case:
 *
 * - **A dragged thumb on a stepped slider** is held at its detent while the
 *   finger goes past, so the target leads the drawing by the strain and the
 *   thumb elongates toward the finger. The longer it is held between notches the
 *   further it stretches, and it snaps round again the moment it lands.
 * - **A tapped thumb** is travelling: its spring lags its new value, so it
 *   stretches along the direction of travel and rounds off as it arrives. This is
 *   what the docs have claimed the slider does since it was written, and what it
 *   did not do — it grew 25% uniformly and stayed a circle.
 * - **A thumb being pushed by the other one** lags for the same reason a tapped
 *   one does, so being shoved reads as being shoved. That is the case this was
 *   asked for.
 *
 * - **A dragged thumb on a continuous slider** is pinned to the finger, so the
 *   gap is zero and it stays round. Which is right: there is nothing straining.
 *
 * At `reachPx == 0` this is a circle, so there is no discontinuity between a
 * thumb that is moving and one that has stopped.
 *
 * @param scale The uniform grow-while-touched, applied to the radius. The stretch
 *   is on top of it and along one axis only.
 * @param ringPx The page-coloured ring that keeps the thumb legible where it
 *   overlaps the filled track. A constant width rather than a scaled one: a
 *   border that thickens as the thumb grows reads as the thumb changing weight.
 */
internal fun DrawScope.sliderThumb(
    centreX: Float,
    centreY: Float,
    radiusPx: Float,
    scale: Float,
    reachPx: Float,
    ringColor: Color,
    fillColor: Color,
    ringPx: Float,
) {
    val r = radiusPx * scale
    val limit = r * SliderDefaults.MaxStretch
    val reach = reachPx.coerceIn(-limit, limit)

    val left = centreX - r + minOf(reach, 0f)
    val right = centreX + r + maxOf(reach, 0f)

    drawRoundRect(
        color = ringColor,
        topLeft = Offset(left, centreY - r),
        size = Size(right - left, r * 2f),
        cornerRadius = CornerRadius(r),
    )
    drawRoundRect(
        color = fillColor,
        topLeft = Offset(left + ringPx, centreY - r + ringPx),
        size = Size(
            (right - left - ringPx * 2f).coerceAtLeast(0f),
            (r * 2f - ringPx * 2f).coerceAtLeast(0f),
        ),
        cornerRadius = CornerRadius((r - ringPx).coerceAtLeast(0f)),
    )
}
