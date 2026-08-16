package io.kontour.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

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
                spec(1, 3, 0.06f * boost),
                spec(8, 24, 0.10f * boost),
            )
        ),
        high = Shadow(
            listOf(
                spec(2, 4, 0.08f * boost),
                spec(12, 32, 0.14f * boost),
            )
        ),
        overlay = Shadow(
            listOf(
                spec(4, 8, 0.10f * boost),
                spec(20, 50, 0.22f * boost),
            )
        ),
    )
}
