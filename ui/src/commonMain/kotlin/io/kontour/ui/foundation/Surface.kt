package io.kontour.ui.foundation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import io.kontour.ui.a11y.contentColorFor
import io.kontour.ui.theme.Shadow
import io.kontour.ui.theme.Theme

/**
 * A ground: background, shape, border, shadow, and the content colour that
 * cascades from it.
 *
 * The substrate nearly every component in the system is built on. A card is a
 * `Surface`; so is a sheet, a menu, a dialog, a chip. What makes it worth having
 * rather than reaching for `Modifier.background()` is the last part — it sets
 * [LocalContentColor], so text and icons inside it are legible without any call
 * site knowing what colour it painted.
 *
 * ```
 * Surface(
 *     shape = Theme.shapes.medium,
 *     color = Theme.colors.surface,
 *     shadow = Theme.elevation.low,
 * ) {
 *     Column(Modifier.padding(Theme.spacing.md)) {
 *         Text("Perth Station")     // inherits a legible colour automatically
 *     }
 * }
 * ```
 *
 * @param contentColor Defaults to whichever of the scheme's light or dark
 *   content colours reads better on [color] — so a surface painted an arbitrary
 *   colour (a route colour out of a transit feed, say) still gets legible
 *   content without the caller working it out.
 * @param shadow A [Shadow] from `Theme.elevation`, not a raw dp. Pick the level
 *   that matches where the element sits in the overlay stack.
 */
@Composable
fun Surface(
    modifier: Modifier = Modifier,
    shape: Shape = RectangleShape,
    color: Color = Theme.colors.surface,
    contentColor: Color = defaultContentColorFor(color),
    border: BorderStroke? = null,
    shadow: Shadow = Shadow.None,
    propagateMinConstraints: Boolean = false,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalContentColor provides contentColor) {
        Box(
            modifier = modifier
                .elevation(shadow, shape)
                .clip(shape)
                .background(color = color, shape = shape)
                .then(if (border != null) Modifier.border(border, shape) else Modifier),
            propagateMinConstraints = propagateMinConstraints,
            content = { content() },
        )
    }
}

/**
 * Draws [shadow] beneath this element, in [shape].
 *
 * Each [io.kontour.ui.theme.ShadowSpec] in the shadow becomes its own draw
 * layer, so a two-layer shadow — a tight contact shadow plus a wide ambient one
 * — composites the way it does in CSS. Ordered furthest-first so the tight,
 * darker layer lands on top.
 */
fun Modifier.elevation(shadow: Shadow, shape: Shape): Modifier {
    if (shadow.layers.isEmpty()) return this
    return shadow.layers.fold(this) { acc, spec ->
        acc.dropShadow(shape) {
            // DropShadowScope works in pixels and is itself a Density, so the
            // dp-based tokens convert here rather than at authoring time.
            radius = spec.blurRadius.toPx()
            spread = spec.spread.toPx()
            offset = Offset(spec.offsetX.toPx(), spec.offsetY.toPx())
            color = spec.color
            alpha = spec.alpha
        }
    }
}

/**
 * The content colour to use on [background].
 *
 * Recognises the scheme's own grounds and returns their designed partner; for
 * anything else, picks whichever of the scheme's content colours has better
 * contrast.
 */
@Composable
private fun defaultContentColorFor(background: Color): Color {
    val colors = Theme.colors
    return when (background) {
        colors.background, colors.surface, colors.surfaceSunken, colors.surfaceRaised -> colors.content
        colors.surfaceInverse -> colors.onSurfaceInverse
        colors.primary -> colors.onPrimary
        colors.accent -> colors.onAccent
        colors.accentContainer -> colors.onAccentContainer
        colors.success.solid -> colors.success.onSolid
        colors.success.container -> colors.success.onContainer
        colors.warning.solid -> colors.warning.onSolid
        colors.warning.container -> colors.warning.onContainer
        colors.danger.solid -> colors.danger.onSolid
        colors.danger.container -> colors.danger.onContainer
        colors.info.solid -> colors.info.onSolid
        colors.info.container -> colors.info.onContainer
        else -> contentColorFor(
            background = background,
            light = if (colors.isDark) colors.content else colors.onPrimary,
            dark = if (colors.isDark) colors.onPrimary else colors.content,
        )
    }
}
