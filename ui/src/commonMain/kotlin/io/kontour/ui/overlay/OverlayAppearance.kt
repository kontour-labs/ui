package io.kontour.ui.overlay

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer

/**
 * The scale-and-fade every overlay panel appears with.
 *
 * @param progress 0 when the panel has just been pushed, 1 once it has arrived.
 * @param fromScale How small it starts. Menus barely scale; a tooltip can afford
 *   more, because it is small and the movement is what draws the eye to it.
 *
 * ### `ModulateAlpha` is the whole point of this being shared
 *
 * A `graphicsLayer` with `alpha < 1` composites offscreen by default, and the
 * offscreen buffer is sized to the **layer's own rectangular bounds**. An
 * overlay's shadow is drawn by a descendant and bleeds roughly 70dp outside
 * those bounds (`Theme.elevation.overlay` is a 20dp offset with a 50dp blur), so
 * the buffer cuts it off at a hard, straight edge. At the same time `scale < 1`
 * shrinks the opaque panel inside those unchanged bounds, exposing the cut.
 *
 * The result is a square of shadow around every appearing menu, popover, tooltip
 * and dialog, visible only while `progress` is between 0 and 1 — which is
 * exactly the window where both conditions hold.
 *
 * [CompositingStrategy.ModulateAlpha] applies the alpha per draw call instead of
 * compositing a buffer, so there are no layer bounds for the shadow to be
 * clipped against. It is correct here precisely because an overlay panel does
 * not overlap itself: modulating each descendant's alpha independently and
 * compositing the whole layer once are the same picture when nothing underneath
 * shows through.
 */
internal fun Modifier.overlayAppearance(
    progress: Float,
    fromScale: Float = 0.9f,
    origin: TransformOrigin = TransformOrigin.Center,
): Modifier = graphicsLayer {
    transformOrigin = origin
    val scale = fromScale + (1f - fromScale) * progress
    scaleX = scale
    scaleY = scale
    alpha = progress
    compositingStrategy = CompositingStrategy.ModulateAlpha
}
