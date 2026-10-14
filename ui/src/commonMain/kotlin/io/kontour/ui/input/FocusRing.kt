package io.kontour.ui.input

import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import io.kontour.ui.theme.Theme
import io.kontour.ui.theme.inset
import io.kontour.ui.theme.outset

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
 * @param colour Defaults to [io.kontour.ui.theme.ColourScheme.focusRing], which
 *   is guaranteed to clear 3:1 against every ground in the scheme.
 */
@Composable
fun Modifier.focusRing(
    interactionSource: InteractionSource,
    shape: Shape = RectangleShape,
    colour: Color = Theme.colours.focusRing,
    enabled: Boolean = true,
): Modifier {
    // Collected only where a ring can be drawn. A row with nothing to press
    // passes `enabled = false` and an interaction source nothing ever emits
    // to, and every one of them in a scrolling list still started a collector
    // for it: a coroutine, a state and a subscriber apiece, for a ring that
    // could never show.
    val focused = if (enabled) interactionSource.collectIsFocusedAsState().value else false

    // The modality is read only once the control has focus, and the order of
    // the `&&` is the whole of that. Read first, as it was, every control that
    // draws a ring — most of them on a screen — recomposed whenever the hand
    // went from the mouse to the keyboard, to arrive at "no ring" again. A
    // focused control still reads it, so its ring comes and goes with the
    // modality; one that gains focus reads it on the recomposition focus brings.
    val visible = focused && LocalInputModality.current.showsFocusRing
    if (!visible) return this
    val sizing = Theme.sizing

    return drawWithCache {
        val stroke = sizing.focusRingWidth.toPx()
        val gap = sizing.focusRingOffset.toPx()

        // Grow by the gap plus half the stroke, so the stroke's outer edge — not
        // its centre — sits `gap` away from the component.
        val grow = gap + stroke / 2f
        val ringSize = Size(size.width + grow * 2f, size.height + grow * 2f)

        // Grow the *corners* by the same amount, not just the box.
        //
        // Two rounded rectangles are concentric when the outer radius is the
        // inner radius plus the distance between them. Drawing the ring at the
        // component's own radius on a box 6dp larger left every dp-based shape
        // in the library under-rounded by exactly that much: a `Button` at 8dp
        // needed 11, a `ListItem` at 12 needed 15, a `Checkbox` at 4 needed 7.
        // The gap read wider at the corners than along the edges, which is the
        // tell that two shapes are not concentric.
        //
        // `outset` is the library's name for that rule, and this is its second
        // caller: it resolves the corner against the *component's* box rather
        // than the ring's — see its own KDoc for why that distinction is not
        // pedantry — and grows through `copy`, so a ring around a squircle is
        // still a squircle. Rebuilding it as a RoundedCornerShape silently
        // returned every smoothed corner in the library to a circular arc at
        // the one moment it is under a spotlight.
        val ringShape = (shape as? CornerBasedShape)?.outset(grow.toDp()) ?: shape
        val outline = ringShape.createOutline(ringSize, layoutDirection, this)

        onDrawWithContent {
            drawContent()
            translate(left = -grow, top = -grow) {
                drawOutline(
                    outline = outline,
                    color = colour,
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
    val focused = interactionSource.collectIsFocusedAsState().value
    return focused && LocalInputModality.current.showsFocusRing
}

/**
 * A focus ring drawn just *inside* the component rather than around it.
 *
 * For the components whose neighbours touch them — a table row, a day in a
 * calendar, a row in a menu, an action under a swiped row. Around them, the next
 * one along would paint over the ring's far edge, or the panel they sit in would
 * clip it. Inside, it is always whole.
 *
 * Shown on the same terms as [focusRing]: only when focus came from the
 * keyboard. Apply it *after* the component's `clip`, so it is drawn on top of the
 * pressed and selected grounds rather than under them.
 *
 * @param shape The component's own shape; the ring follows it, a stroke-width in.
 * @param colour The ring's colour. The scheme's focus ring clears 3:1 on every
 *   ground in the scheme; a component on a ground of its own — a red delete
 *   action — passes its content colour instead.
 */
@Composable
internal fun Modifier.insetFocusRing(
    interactionSource: InteractionSource,
    shape: Shape = RectangleShape,
    enabled: Boolean = true,
    colour: Color = Theme.colours.focusRing,
): Modifier {
    val visible = rememberFocusRingVisible(interactionSource) && enabled
    if (!visible) return this
    val width = Theme.sizing.focusRingWidth
    return drawWithCache {
        val stroke = width.toPx()
        val half = stroke / 2f
        // Concentric with the component: its corner, less the distance in.
        val ringShape = (shape as? CornerBasedShape)?.inset(half.toDp()) ?: shape
        val outline = ringShape.createOutline(
            Size((size.width - stroke).coerceAtLeast(0f), (size.height - stroke).coerceAtLeast(0f)),
            layoutDirection,
            this,
        )
        onDrawWithContent {
            drawContent()
            translate(left = half, top = half) {
                drawOutline(outline = outline, color = colour, style = Stroke(width = stroke))
            }
        }
    }
}
