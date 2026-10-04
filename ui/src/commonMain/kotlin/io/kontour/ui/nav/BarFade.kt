package io.kontour.ui.nav

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * A bar's ground thinning out over the page: [colour] at [solidAt], nothing at
 * [clearAt], along y. Either way up — a top bar's fades downwards from its
 * foot, a bottom bar's upwards from its middle.
 *
 * Eased rather than straight, as a hero's fade is, so there is no line where it
 * starts or ends.
 */
internal fun easedFade(colour: Color, solidAt: Float, clearAt: Float): Brush = Brush.verticalGradient(
    0f to colour,
    0.25f to colour.copy(alpha = colour.alpha * 0.84f),
    0.5f to colour.copy(alpha = colour.alpha * 0.5f),
    0.75f to colour.copy(alpha = colour.alpha * 0.16f),
    1f to colour.copy(alpha = 0f),
    startY = solidAt,
    endY = clearAt,
)
