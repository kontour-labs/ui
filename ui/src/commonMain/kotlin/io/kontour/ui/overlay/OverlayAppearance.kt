package io.kontour.ui.overlay

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer

/**
 * The scale-and-fade every overlay appears with.
 *
 * @param progress 0 when the overlay has just been pushed, 1 once it has arrived.
 * @param fromScale How small it starts. Menus barely scale; a tooltip can afford
 *   more, because it is small and the movement is what draws the eye to it.
 * @param origin What it grows out of. Anchored overlays point this at their
 *   anchor, so a menu unfolds from the control it belongs to.
 *
 * ### Apply this well outside the panel, not to the panel
 *
 * `alpha < 1` composites offscreen, and the offscreen buffer is sized to the
 * **layer's own rectangular bounds**. An overlay's shadow bleeds roughly 70dp
 * outside the panel that casts it (`Theme.elevation.overlay` is a 20dp offset
 * with a 50dp blur), so a layer wrapped tightly around the panel cuts the shadow
 * off at a hard, straight edge — while `scale < 1` shrinks the opaque panel
 * inside those unchanged bounds and exposes the cut.
 *
 * That is the square of shadow that showed up around every appearing menu,
 * popover and dialog: visible only while `progress` is between 0 and 1, because
 * that is the only window where both conditions hold.
 *
 * So the fix is to give the layer room. Every caller puts this on a node that
 * fills the overlay host rather than on the panel itself, which leaves the
 * panel's shadow comfortably inside the buffer. It costs a host-sized
 * compositing layer for the length of the animation — the price of a fade over
 * a shadow, and only while something is actually appearing.
 *
 * ### Not `CompositingStrategy.ModulateAlpha`
 *
 * Which is the obvious way out of the buffer entirely, and does not work here.
 * On this toolkit's Skia backend it is not an alternative route to the same
 * picture — `alpha` is simply not applied. Set it and an overlay scales into
 * place at full opacity, never fading at all; and a layer carrying it suppresses
 * `Modifier.dropShadow` in any node beneath it, so the shadow does not draw
 * either. Both found by rendering a frame mid-animation, which is what
 * `OverlayMotionScreenshotTest` now exists to keep doing.
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
}
