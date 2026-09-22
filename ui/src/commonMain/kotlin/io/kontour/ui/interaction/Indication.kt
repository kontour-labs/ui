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
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    private val hoverColour: Color,
    private val pressColour: Color,
    private val draggedColour: Color,
    private val pressScale: Float,
    private val motion: Motion,
    private val hoverEnabled: Boolean,
) : IndicationNodeFactory {

    override fun create(interactionSource: InteractionSource): DelegatableNode =
        KontourIndicationNode(
            interactionSource = interactionSource,
            shape = shape,
            hoverColour = hoverColour,
            pressColour = pressColour,
            draggedColour = draggedColour,
            pressScale = pressScale,
            motion = motion,
            hoverEnabled = hoverEnabled,
        )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is KontourIndication) return false
        return shape == other.shape &&
            hoverColour == other.hoverColour &&
            pressColour == other.pressColour &&
            draggedColour == other.draggedColour &&
            pressScale == other.pressScale &&
            hoverEnabled == other.hoverEnabled &&
            motion == other.motion
    }

    override fun hashCode(): Int {
        var result = shape.hashCode()
        result = 31 * result + hoverColour.hashCode()
        result = 31 * result + pressColour.hashCode()
        result = 31 * result + draggedColour.hashCode()
        result = 31 * result + pressScale.hashCode()
        result = 31 * result + hoverEnabled.hashCode()
        result = 31 * result + motion.hashCode()
        return result
    }
}

private class KontourIndicationNode(
    private val interactionSource: InteractionSource,
    private val shape: Shape,
    private val hoverColour: Color,
    private val pressColour: Color,
    private val draggedColour: Color,
    private val pressScale: Float,
    private val motion: Motion,
    private val hoverEnabled: Boolean,
) : Modifier.Node(), DrawModifierNode {

    private val overlayAlpha = Animatable(0f)
    private val scale = Animatable(1f)

    /** The wash currently being drawn. Press wins over drag, drag over hover. */
    private var overlayColour: Color = Color.Transparent

    /**
     * When the current press went down, on the wall clock.
     *
     * Null whenever nothing is pressed. The wall clock rather than the frame
     * clock for the reason `Toast`'s own clock gives: what is being measured is
     * how long a *finger* was down, which is a fact about the person and not
     * about how many frames the renderer managed in the meantime.
     */
    private var pressedAt: TimeSource.Monotonic.ValueTimeMark? = null

    /**
     * The job that answers the newest interaction, held so the next one can
     * cancel it.
     *
     * A press arriving while a release is being held back has to take over
     * immediately, and cancelling mid-animation is safe: an `Animatable` retargets
     * from wherever it has got to.
     */
    private var settle: Job? = null

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
                    pressed -> pressColour
                    dragged -> draggedColour
                    hovered -> hoverColour
                    else -> Color.Transparent
                }

                /**
                 * How long to keep the pressed look before answering a release.
                 *
                 * **A tap on a touchscreen is shorter than the animation it
                 * starts.** Reported from a phone: on the desktop a click shrinks
                 * the control and darkens it and looks right, and on a phone "it
                 * sometimes just looks like it's flashing" — because a thumb is
                 * down for something like 60ms and the release retargeted both
                 * animatables the moment it arrived, so the shrink turned round a
                 * third of the way down and the wash never reached its own alpha.
                 * What a reader saw was a flicker, which is the interface
                 * acknowledging the tap in a way that cannot be read as
                 * acknowledgement.
                 *
                 * So the press is held for a floor. A slow press — anything at or
                 * past the floor — is unaffected, which is every mouse click and
                 * every deliberate hold; only a tap too quick to see gets the rest
                 * of its own animation. The same shape as
                 * `ToastDefaults.PromotedFloor`, which tops a promoted toast's
                 * remaining time up to a floor rather than restarting it, and
                 * named to match.
                 *
                 * Not a rate limiter, which is what `DetentTicker`'s
                 * `MinimumTickInterval` is and is the wrong precedent: nothing here
                 * is being dropped, it is being finished.
                 */
                val hold = when {
                    // Still pressed, or something else has taken the control over —
                    // a drag that grew out of the press, a pointer still hovering.
                    // There is no flicker to prevent in either case, and holding
                    // would mean a drag's own wash arriving a tenth of a second
                    // after the drag did.
                    pressed || target != Color.Transparent -> Duration.ZERO
                    else -> pressedAt?.let { PressFloor - it.elapsedNow() }
                        ?.coerceAtLeast(Duration.ZERO)
                        ?: Duration.ZERO
                }
                pressedAt = if (pressed) pressedAt ?: TimeSource.Monotonic.markNow() else null

                // **Cancelled after the hold, not before it.** The job being
                // replaced is usually the press's own animation, and cancelling
                // it up front stops the shrink at wherever it had got to — which
                // is the flicker again, arrived at from the other direction.
                // Measured: a tap did not shrink the control at all. After the
                // delay the press has finished and this is a no-op; a *press*
                // arriving mid-hold has no delay and so still takes over at once.
                val previous = settle
                settle = launch {
                    if (hold > Duration.ZERO) delay(hold)
                    previous?.cancel()

                    if (target != Color.Transparent) {
                        overlayColour = target
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
        val wash = overlayColour.copy(alpha = alpha)
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
 * The shortest a press is allowed to *look*, however briefly it was one.
 *
 * A tap is over before its own animation has started: the shrink is a snappy
 * spring and the wash a `tweenFast`, and a thumb is on the glass for well under
 * either. Released at once, both turn round part-way and the control flickers
 * rather than answering — reported from a phone, against a press that looks right
 * under a mouse for the simple reason that a click lasts longer.
 *
 * A hundred and twenty milliseconds: long enough for the shrink to arrive and the
 * wash to reach its alpha, short enough that a fast double tap is still two taps
 * rather than one long one. It is a **floor** and not a delay — a press already
 * past it is answered the instant the finger lifts, which is every mouse click and
 * every deliberate hold. See [KontourIndication] and the note inside its node.
 *
 * A starting point, and the kind of number only a thumb can settle.
 */
val PressFloor: Duration = 120.milliseconds

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
    val colours = Theme.colours
    val motion = Theme.motion
    val hoverEnabled = LocalInputModality.current.supportsHover
    return remember(shape, pressScale, colours, motion, hoverEnabled) {
        KontourIndication(
            shape = shape,
            hoverColour = colours.overlayHover,
            pressColour = colours.overlayPressed,
            draggedColour = colours.overlayDragged,
            pressScale = pressScale,
            motion = motion,
            hoverEnabled = hoverEnabled,
        )
    }
}
