package io.kontour.ui.interaction

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.Modifier
import io.kontour.ui.input.LocalInputModality
import io.kontour.ui.theme.Motion
import io.kontour.ui.theme.Theme
import kotlinx.coroutines.launch

/**
 * The design system's press, hover and drag feedback. Replaces Material's ripple.
 *
 * Two things happen at once when a control is pressed: it shrinks slightly, and
 * a tonal wash appears over it. The shrink is what carries the sense of physical
 * response — Uber's interfaces lean on it heavily, and unlike a ripple it reads
 * identically on every platform, costs nothing to draw, and never trails an
 * animation behind a finger that has already lifted.
 *
 * Hover is drawn only when a pointer that can actually hover is in use; on a
 * touchscreen a "hover" state is a stuck highlight left behind by a tap.
 *
 * Under reduced motion the scale is dropped and only the tonal wash remains —
 * the control still acknowledges the press, it just does not move.
 *
 * Obtain one with [kontourIndication] rather than constructing it directly, so
 * it picks up the current theme:
 *
 * ```
 * Box(
 *     Modifier.clickable(
 *         interactionSource = interactionSource,
 *         indication = kontourIndication(shape = Theme.shapes.small),
 *         onClick = onClick,
 *     )
 * )
 * ```
 */
class KontourIndication(
    private val shape: Shape,
    private val hoverColor: Color,
    private val pressColor: Color,
    private val draggedColor: Color,
    private val pressScale: Float,
    private val motion: Motion,
    private val hoverEnabled: Boolean,
) : IndicationNodeFactory {

    override fun create(interactionSource: InteractionSource): DelegatableNode =
        KontourIndicationNode(
            interactionSource = interactionSource,
            shape = shape,
            hoverColor = hoverColor,
            pressColor = pressColor,
            draggedColor = draggedColor,
            pressScale = pressScale,
            motion = motion,
            hoverEnabled = hoverEnabled,
        )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is KontourIndication) return false
        return shape == other.shape &&
            hoverColor == other.hoverColor &&
            pressColor == other.pressColor &&
            draggedColor == other.draggedColor &&
            pressScale == other.pressScale &&
            hoverEnabled == other.hoverEnabled &&
            motion == other.motion
    }

    override fun hashCode(): Int {
        var result = shape.hashCode()
        result = 31 * result + hoverColor.hashCode()
        result = 31 * result + pressColor.hashCode()
        result = 31 * result + draggedColor.hashCode()
        result = 31 * result + pressScale.hashCode()
        result = 31 * result + hoverEnabled.hashCode()
        result = 31 * result + motion.hashCode()
        return result
    }
}

private class KontourIndicationNode(
    private val interactionSource: InteractionSource,
    private val shape: Shape,
    private val hoverColor: Color,
    private val pressColor: Color,
    private val draggedColor: Color,
    private val pressScale: Float,
    private val motion: Motion,
    private val hoverEnabled: Boolean,
) : Modifier.Node(), DrawModifierNode {

    private val overlayAlpha = Animatable(0f)
    private val scale = Animatable(1f)

    /** The wash currently being drawn. Press wins over drag, drag over hover. */
    private var overlayColor: Color = Color.Transparent

    override fun onAttach() {
        coroutineScope.launch {
            var presses = 0
            var hovers = 0
            var drags = 0

            interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> presses++
                    is PressInteraction.Release, is PressInteraction.Cancel -> presses--
                    is HoverInteraction.Enter -> hovers++
                    is HoverInteraction.Exit -> hovers--
                    is DragInteraction.Start -> drags++
                    is DragInteraction.Stop, is DragInteraction.Cancel -> drags--
                }

                val pressed = presses > 0
                val dragged = drags > 0
                val hovered = hovers > 0 && hoverEnabled

                val target = when {
                    pressed -> pressColor
                    dragged -> draggedColor
                    hovered -> hoverColor
                    else -> Color.Transparent
                }

                if (target != Color.Transparent) {
                    overlayColor = target
                }

                launch {
                    overlayAlpha.animateTo(
                        targetValue = if (target == Color.Transparent) 0f else target.alpha,
                        animationSpec = motion.tweenFast(),
                    )
                }
                launch {
                    // Asymmetric on purpose. Going down: snappy and critically
                    // damped, so the control answers the finger immediately.
                    // Coming back: under-damped, so it overshoots a little and
                    // settles — which is what reads as playful rather than
                    // mechanical. Overshooting on the way *down* would just feel
                    // slow.
                    scale.animateTo(
                        targetValue = if (pressed && !motion.reduceMotion) pressScale else 1f,
                        animationSpec = motion.springOrTween(
                            if (pressed) motion.springSnappy else motion.springBouncy
                        ),
                    )
                }
            }
        }
    }

    override fun ContentDrawScope.draw() {
        val currentScale = scale.value
        val alpha = overlayAlpha.value

        // Both inside the same scale, so a pressed control and the wash on top
        // of it are the same shape. Drawn unscaled, the wash painted the
        // control's *original* footprint over a container that had just shrunk
        // out from under it — a faint halo standing where the button used to be,
        // which is the one thing guaranteed to make a shrink read as something
        // else.
        if (currentScale == 1f) {
            drawContent()
            if (alpha > 0f) drawWash(alpha)
        } else {
            scale(currentScale) {
                this@draw.drawContent()
                if (alpha > 0f) this@draw.drawWash(alpha)
            }
        }
    }

    private fun DrawScope.drawWash(alpha: Float) {
        val wash = overlayColor.copy(alpha = alpha)
        if (shape === RectangleShape) {
            drawRect(color = wash)
        } else {
            drawOutline(outline = shape.createOutline(size, layoutDirection, this), color = wash)
        }
    }
}

/** How far a control shrinks while pressed. Subtle on purpose — 3% reads as response, 10% as a bug. */
const val DefaultPressScale: Float = 0.97f

/**
 * A [KontourIndication] wired to the current theme and input modality.
 *
 * @param shape Clips the tonal wash. Pass the same shape the component is
 *   clipped to, or the wash will square off its corners.
 * @param pressScale `1f` disables the shrink — right for anything large, where
 *   scaling the whole surface looks wrong. A list row or a sheet should not
 *   flinch; a button should.
 */
@Composable
fun kontourIndication(
    shape: Shape = RectangleShape,
    pressScale: Float = DefaultPressScale,
): IndicationNodeFactory {
    val colors = Theme.colors
    val motion = Theme.motion
    val hoverEnabled = LocalInputModality.current.supportsHover
    return remember(shape, pressScale, colors, motion, hoverEnabled) {
        KontourIndication(
            shape = shape,
            hoverColor = colors.overlayHover,
            pressColor = colors.overlayPressed,
            draggedColor = colors.overlayDragged,
            pressScale = pressScale,
            motion = motion,
            hoverEnabled = hoverEnabled,
        )
    }
}
