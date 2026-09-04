package io.kontour.ui.overlay

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.pow

/**
 * The scale-and-fade every overlay appears with.
 *
 * @param progress 0 when the overlay has just been pushed, 1 once it has arrived.
 *   A lambda, read inside the layer block rather than captured from composition,
 *   so an overlay animating does not recompose everything that reads it. See
 *   [LocalOverlayProgress].
 * @param fromScale What scale it starts at. Below 1 it grows into place, which
 *   is right for something anchored to a control it came out of; above 1 it
 *   settles down onto the screen, which is right for a dialog that arrives from
 *   in front of it. Menus barely scale; a tooltip can afford
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
    progress: () -> Float,
    fromScale: Float = 0.9f,
    origin: TransformOrigin = TransformOrigin.Center,
): Modifier = graphicsLayer {
    transformOrigin = origin
    val arrived = progress().coerceIn(0f, 1f)
    // Scale on the plain progress, alpha ahead of it — see [PanelFade].
    val scale = fromScale + (1f - fromScale) * arrived
    scaleX = scale
    scaleY = scale
    alpha = arrived.pow(PanelFade)
}

/**
 * How far ahead of the scrim the panel fades in.
 *
 * ### The problem, stated as arithmetic
 *
 * A scrim and the panel in front of it are driven by one number and are both
 * linear in it, so they *are* in step — and they still do not look it. What the
 * eye reads as "the dialog has arrived" is not its alpha but how far it stands
 * out from the page around it, and on a page the dialog needs the scrim to be
 * visible against at all — a near-white panel on a near-white page, which is the
 * commonest screen there is — that contrast is the **product** of two ramps: the
 * panel's own alpha, and the darkening underneath it. A product of two linear
 * ramps goes as the square. So at the middle of the animation the scrim is
 * better than half dimmed and the dialog is barely a third of the way to being
 * distinct, and the backdrop looks like it arrived first. It did.
 *
 * ### The number
 *
 * `DialogBackdropRateTest` opens a dialog over four backgrounds — a white page, a
 * mid-tone one, a saturated one, and a dark page under the dark theme — recovers
 * the panel's alpha from the rendered pixels, and reports how far the two curves
 * separate at their worst frame for a family of candidate curves. Straight alpha
 * separates them by 0.24 at worst; this exponent is where the worst case across
 * all four bottoms out, at 0.16.
 *
 * It does not reach zero, and it cannot: a white panel on a white page has no
 * contrast of its own, so the scrim is the entire reason it is visible and no
 * alpha curve can put it in front. What is left is the part of the effect that
 * belongs to the design rather than to the animation.
 *
 * Only the alpha is eased. The scale stays on the plain progress, so the panel
 * still *settles* at the rate everything else does — it arrives sooner without
 * arriving faster, which is the difference between this and simply shortening
 * the animation.
 */
private const val PanelFade = 0.6f
