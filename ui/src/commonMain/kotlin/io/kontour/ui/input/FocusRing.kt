package io.kontour.ui.input

import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import io.kontour.ui.theme.Theme

/**
 * Draws the keyboard focus indicator around this component.
 *
 * The ring sits *outside* the component's bounds, separated by a small gap, so
 * it never eats into the content or shifts layout when it appears. It is drawn
 * only when focus arrived from the keyboard — see
 * [io.kontour.ui.input.InputModality]. A ring that appears on every tap makes
 * a touch interface look broken; a ring that never appears makes the app
 * unusable without a mouse. Tracking modality is what lets both be true.
 *
 * ```
 * val interactionSource = remember { MutableInteractionSource() }
 * Box(
 *     Modifier
 *         .focusRing(interactionSource, Theme.shapes.small)
 *         .clip(Theme.shapes.small)
 *         .clickable(interactionSource = interactionSource, indication = …) { … }
 * )
 * ```
 *
 * Apply it *before* any `clip` in the chain, or the clip will cut the ring off.
 *
 * @param shape Should match the component's own shape; the ring is drawn as
 *   that shape, grown by the offset.
 * @param color Defaults to [io.kontour.ui.theme.ColorScheme.focusRing], which
 *   is guaranteed to clear 3:1 against every ground in the scheme.
 */
@Composable
fun Modifier.focusRing(
    interactionSource: InteractionSource,
    shape: Shape = RectangleShape,
    color: Color = Theme.colors.focusRing,
    enabled: Boolean = true,
): Modifier {
    val focused by interactionSource.collectIsFocusedAsState()
    val modality = LocalInputModality.current
    val sizing = Theme.sizing

    val visible = enabled && focused && modality.showsFocusRing
    if (!visible) return this

    return drawWithCache {
        val stroke = sizing.focusRingWidth.toPx()
        val gap = sizing.focusRingOffset.toPx()

        // Grow by the gap plus half the stroke, so the stroke's outer edge — not
        // its centre — sits `gap` away from the component.
        val grow = gap + stroke / 2f
        val ringSize = Size(size.width + grow * 2f, size.height + grow * 2f)
        val outline = shape.createOutline(ringSize, layoutDirection, this)

        onDrawWithContent {
            drawContent()
            translate(left = -grow, top = -grow) {
                drawOutline(
                    outline = outline,
                    color = color,
                    style = Stroke(width = stroke),
                )
            }
        }
    }
}

/**
 * Whether a focus ring would currently be shown for [interactionSource].
 *
 * For components that need to react to focus beyond drawing the ring — a text
 * field that also thickens its border, say.
 */
@Composable
fun rememberFocusRingVisible(interactionSource: InteractionSource): Boolean {
    val focused by interactionSource.collectIsFocusedAsState()
    return focused && LocalInputModality.current.showsFocusRing
}
