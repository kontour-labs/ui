package io.kontour.ui.foundation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import io.kontour.ui.a11y.contentColourFor
import io.kontour.ui.theme.Shadow
import io.kontour.ui.theme.Theme

/**
 * A ground: background, shape, border, shadow, and the content colour that
 * cascades from it.
 *
 * The substrate nearly every component in the system is built on. A card is a
 * `Surface`; so is a sheet, a menu, a dialog, a chip. What makes it worth having
 * rather than reaching for `Modifier.background()` is the last part — it sets
 * [LocalContentColour], so text and icons inside it are legible without any call
 * site knowing what colour it painted.
 *
 * ```
 * Surface(
 *     shape = Theme.shapes.medium,
 *     colour = Theme.colours.surface,
 *     shadow = Theme.elevation.low,
 * ) {
 *     Column(Modifier.padding(Theme.spacing.md)) {
 *         Text("Perth Station")     // inherits a legible colour automatically
 *     }
 * }
 * ```
 *
 * @param contentColour Defaults to whichever of the scheme's light or dark
 *   content colours reads better on [colour] — so a surface painted an arbitrary
 *   colour (a route colour out of a transit feed, say) still gets legible
 *   content without the caller working it out.
 * @param contentAlignment Where the content sits when the surface is larger
 *   than it — because a `defaultMinSize` or a fixed height made it so. The
 *   default `TopStart` matches `Box`, but anything with a minimum size almost
 *   certainly wants `Center`; leaving it at the default is what puts an icon in
 *   the corner of a FAB.
 * @param shadow A [Shadow] from `Theme.elevation`, not a raw dp. Pick the level
 *   that matches where the element sits in the overlay stack.
 */
@Composable
fun Surface(
    modifier: Modifier = Modifier,
    shape: Shape = RectangleShape,
    colour: Color = Theme.colours.surface,
    contentColour: Color = defaultContentColourFor(colour),
    border: BorderStroke? = null,
    shadow: Shadow = Shadow.None,
    contentAlignment: Alignment = Alignment.TopStart,
    propagateMinConstraints: Boolean = false,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalContentColour provides contentColour) {
        Box(
            modifier = modifier
                .elevation(shadow, shape)
                .clip(shape)
                .background(color = colour, shape = shape)
                .then(if (border != null) Modifier.border(border, shape) else Modifier),
            contentAlignment = contentAlignment,
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
            color = spec.colour
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
 *
 * A **transparent** background is not a ground at all — whatever is behind the
 * surface is still the thing the content sits on — so it inherits rather than
 * deciding. Measuring it would treat it as black, since `contrastRatio` reads
 * the colour channels and a transparent colour's are `(0, 0, 0)`, and every
 * `Surface(colour = Color.Transparent)` would get near-white content on a light
 * page. That is the case a bar with no ground of its own is in, and it is why
 * `TabBar` could not go through `Surface` until now.
 */
@Composable
private fun defaultContentColourFor(background: Color): Color {
    val colours = Theme.colours
    if (background.alpha == 0f) return LocalContentColour.current
    return when (background) {
        colours.background, colours.surface, colours.surfaceSunken, colours.surfaceRaised -> colours.content
        colours.surfaceInverse -> colours.onSurfaceInverse
        colours.primary -> colours.onPrimary
        colours.accent.solid -> colours.accent.onSolid
        colours.accent.container -> colours.accent.onContainer
        colours.success.solid -> colours.success.onSolid
        colours.success.container -> colours.success.onContainer
        colours.warning.solid -> colours.warning.onSolid
        colours.warning.container -> colours.warning.onContainer
        colours.danger.solid -> colours.danger.onSolid
        colours.danger.container -> colours.danger.onContainer
        colours.info.solid -> colours.info.onSolid
        colours.info.container -> colours.info.onContainer
        else -> contentColourFor(
            background = background,
            light = if (colours.isDark) colours.content else colours.onPrimary,
            dark = if (colours.isDark) colours.onPrimary else colours.content,
        )
    }
}
