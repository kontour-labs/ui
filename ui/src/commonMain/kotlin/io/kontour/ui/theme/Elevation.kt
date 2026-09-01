package io.kontour.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import kotlin.math.max

/**
 * One shadow layer: colour, geometry, softness.
 *
 * Expressed the way CSS expresses it rather than as an Android elevation in dp,
 * because a single dp number cannot describe the two-layer shadows the web
 * properties use, and because Android's elevation model does not exist on iOS,
 * web or desktop.
 */
@Immutable
data class ShadowSpec(
    val color: Color,
    val alpha: Float,
    val offsetY: Dp,
    val blurRadius: Dp,
    val spread: Dp = 0.dp,
    val offsetX: Dp = 0.dp,
)

/**
 * A complete shadow, which may stack more than one [ShadowSpec].
 *
 * Two layers is the useful case, and it is what the settings FAB on the
 * marketing site already does: a tight, darker *contact* shadow gives the shape
 * a crisp edge against a busy background, and a wide, soft *ambient* layer
 * carries the sense of height. One layer alone gives you either a hard edge or
 * a vague smudge, never both.
 */
@Immutable
data class Shadow(val layers: List<ShadowSpec>) {
    companion object {
        val None = Shadow(emptyList())
    }
}

/**
 * The elevation scale — how far off the page something sits.
 *
 * Elevation is a *rank*, not a decoration: pick the level that matches how the
 * element behaves in the overlay stack, and the shadow follows. A menu is
 * [high] because it floats above cards; it does not become [high] because a
 * bigger shadow looked nicer.
 */
@Immutable
data class Elevation(
    /** Flush with the page. Most content. */
    val flat: Shadow = Shadow.None,
    /** Cards and list groups at rest. */
    val low: Shadow,
    /** Nav bars, raised cards, anything hovering just above content. */
    val medium: Shadow,
    /** Menus, popovers, tooltips. */
    val high: Shadow,
    /** Dialogs and sheets — the top of the stack. */
    val overlay: Shadow,
)

/**
 * The default elevation scale.
 *
 * Shadows are cut harder in dark mode: on a near-black ground a soft black
 * shadow is invisible, so the alphas roughly double and the blur tightens.
 * Passing [dark] rather than shipping one scale is what stops dark mode from
 * looking flat.
 *
 * The top two tiers are lighter than they were. [Elevation.overlay] used to be a 20dp
 * offset with a 50dp blur at 22%, which bled about 70dp past a dialog's edge and
 * was doing the whole job of separating it from the page. That job is now shared
 * — what is behind a modal is blurred as well as dimmed, see
 * [io.kontour.ui.overlay.BackdropStyle] — and two mechanisms both pushed to
 * their limit read as one heavy-handed one. Shrinking it also stops every dialog
 * and sheet re-rasterising a 150-pixel-radius shadow on a 3× phone.
 */
fun kontourElevation(dark: Boolean): Elevation {
    val ink = Color.Black
    fun spec(offsetY: Int, blur: Int, alpha: Float) =
        ShadowSpec(color = ink, alpha = alpha, offsetY = offsetY.dp, blurRadius = blur.dp)

    val boost = if (dark) 2.4f else 1f

    return Elevation(
        low = Shadow(
            listOf(
                spec(1, 2, 0.04f * boost),
                spec(2, 6, 0.06f * boost),
            )
        ),
        medium = Shadow(
            listOf(
                spec(1, 3, 0.05f * boost),
                spec(6, 18, 0.08f * boost),
            )
        ),
        high = Shadow(
            listOf(
                spec(1, 3, 0.06f * boost),
                spec(8, 20, 0.10f * boost),
            )
        ),
        overlay = Shadow(
            listOf(
                spec(2, 6, 0.08f * boost),
                spec(10, 28, 0.14f * boost),
            )
        ),
    )
}

/**
 * Every field of a shadow layer, interpolated.
 *
 * Geometry as well as colour: the two scales differ only in alpha today, but
 * nothing stops a consumer's dark shadows from being tighter as well as
 * stronger, and a fade that moved the alpha while the blur jumped would be worse
 * than not fading at all.
 */
internal fun lerp(start: ShadowSpec, stop: ShadowSpec, fraction: Float): ShadowSpec =
    ShadowSpec(
        color = lerp(start.color, stop.color, fraction),
        alpha = start.alpha + (stop.alpha - start.alpha) * fraction,
        offsetY = lerp(start.offsetY, stop.offsetY, fraction),
        blurRadius = lerp(start.blurRadius, stop.blurRadius, fraction),
        spread = lerp(start.spread, stop.spread, fraction),
        offsetX = lerp(start.offsetX, stop.offsetX, fraction),
    )

/**
 * Two shadows interpolated, whatever their layer counts.
 *
 * **A layer that does not exist is that layer at alpha zero.** That single rule
 * is what makes this total rather than a crash waiting for the first consumer
 * whose dark scale has a contact shadow their light scale does not: the lists are
 * paired as far as the shorter one goes, and each unmatched layer is interpolated
 * against a transparent copy of *itself*. So it fades in place, with its own blur
 * and its own offset, instead of sliding in from a zero-sized shadow at the
 * origin — which is what pairing it against a default [ShadowSpec] would have
 * done.
 *
 * [Shadow.None] is an empty list, so a `flat` tier meeting a consumer's
 * single-layer `flat` falls out of the same rule with nothing special-cased.
 */
internal fun lerp(start: Shadow, stop: Shadow, fraction: Float): Shadow {
    if (start.layers.isEmpty() && stop.layers.isEmpty()) return Shadow.None
    return Shadow(
        List(max(start.layers.size, stop.layers.size)) { i ->
            val from = start.layers.getOrNull(i)
            val to = stop.layers.getOrNull(i)
            when {
                from != null && to != null -> lerp(from, to, fraction)
                from != null -> lerp(from, from.copy(alpha = 0f), fraction)
                else -> lerp(to!!.copy(alpha = 0f), to, fraction)
            }
        }
    )
}

/**
 * The whole scale, interpolated.
 *
 * Driven by the same fraction as the colour scheme — see `animatedTheme` — so a
 * shadow and the surface it is cast on are never at different points of the same
 * transition.
 */
internal fun lerp(start: Elevation, stop: Elevation, fraction: Float): Elevation =
    Elevation(
        flat = lerp(start.flat, stop.flat, fraction),
        low = lerp(start.low, stop.low, fraction),
        medium = lerp(start.medium, stop.medium, fraction),
        high = lerp(start.high, stop.high, fraction),
        overlay = lerp(start.overlay, stop.overlay, fraction),
    )
