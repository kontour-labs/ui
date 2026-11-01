package io.kontour.ui.interaction

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ObserverModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.observeReads
import androidx.compose.ui.Modifier
import io.kontour.ui.a11y.highContrast
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
 * touchscreen a "hover" state is a stuck highlight left behind by a tap. The
 * node asks [io.kontour.ui.input.LocalInputModality] itself, so `hoverEnabled`
 * says only whether this control draws a hover at all, and [kontourIndication]
 * passes `true`.
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
            behind = false,
            pressedInk = Color.Unspecified,
            inkShape = RectangleShape,
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

/**
 * @param behind Whether the wash is a ground drawn under the content, for
 *   [GroundIndication], rather than a wash over it.
 * @param pressedInk What the content is recoloured to while pressed, or
 *   unspecified to leave it as it is drawn.
 * @param inkShape Where [pressedInk] is laid: only there, and only over what the
 *   content drew.
 */
private class KontourIndicationNode(
    private val interactionSource: InteractionSource,
    private val shape: Shape,
    private val hoverColour: Color,
    private val pressColour: Color,
    private val draggedColour: Color,
    private val pressScale: Float,
    private val motion: Motion,
    private val hoverEnabled: Boolean,
    private val behind: Boolean,
    private val pressedInk: Color,
    private val inkShape: Shape,
) : Modifier.Node(), DrawModifierNode, CompositionLocalConsumerModifierNode, ObserverModifierNode {

    // Replaced rather than snapped in [reset], which runs outside a coroutine.
    // The draw reads whichever is current, so the swap is what it draws next.
    //
    // **Null until the first interaction, and null again after a reset.**
    // Raised 2026-10-29 for 0.5.1. Most controls on a screen are never
    // pressed, and every one of them made both of these on construction and
    // both again in `onAttach`: four `Animatable`s, each with its states, its
    // vectors and its mutex, for a control at rest. A hundred plain
    // `pressable` boxes composing into a frame cost 11,873 bytes each with
    // them and 8,769 without, and a theme change, which replaces every node on
    // screen, 846KB against 543. Null draws what a fresh pair at 0 and 1 drew.
    private var overlayAlpha: Animatable<Float, AnimationVector1D>? = null
    private var scale: Animatable<Float, AnimationVector1D>? = null

    /**
     * How far the content has been recoloured to [pressedInk]: 1 pressed, 0 not.
     *
     * Its own animation rather than the wash's, because a ground answers a hover
     * as well as a press and the ink only a press. Made with the other two, and
     * only by a node that has an ink to lay.
     */
    private var ink: Animatable<Float, AnimationVector1D>? = null

    /** The wash currently being drawn. Press wins over drag, drag over hover. */
    private var overlayColour: Color = Color.Transparent

    /**
     * When the current press went down, on the wall clock.
     *
     * Null whenever nothing is pressed. Used for [IndicationDefaults.PressFloor] and nothing else:
     * the wall clock is right for *how long a finger was down*, which is a fact
     * about the person rather than about how many frames the renderer managed. It
     * is wrong for *whether the shrink has arrived*, which is a fact about the
     * frames — see the hold in [retarget], which learned that the hard way.
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

    /**
     * The presses, hovers and drags under way, each held by the interaction that
     * began it, and an end taken off only by the one it names.
     *
     * **Not counts.** These were three integers, a press adding one and a release
     * taking one away, and a count is only right for a node that has seen every
     * interaction since the control was made. This one has not, always: the node
     * is replaced whenever the factory stops being equal — a theme or a contrast
     * tier changing under a finger — and the new one hears the release of a press
     * it never saw. Counted, that was minus one, every later press only brought it
     * back to nought, and the control never drew pressed again. An end whose start
     * is not here is now simply not this node's business.
     */
    private val presses = mutableListOf<PressInteraction.Press>()
    private val hovers = mutableListOf<HoverInteraction.Enter>()
    private val drags = mutableListOf<DragInteraction.Start>()

    /**
     * Whether the input in use can hover, as of the last time it was read.
     *
     * **Read here rather than in composition.** It was read by `kontourIndication`
     * and folded into the factory, so moving the hand from the mouse to the screen
     * recomposed every control on screen and replaced every indication node in
     * them — and the node replaced under the first touch is the one that heard a
     * release with no press. Read through [observeReads], a change reaches this
     * node and nothing else, and the node it reaches is the same one.
     */
    private var pointerHovers = false

    override fun onAttach() {
        // From nothing, whatever this node last drew. A node taken back out of a
        // lazy list's pool kept its animatables where they were when it was put
        // away — pressed, if it was scrolled off under a finger — and drew that.
        reset()
        readModality()
        coroutineScope.launch {
            interactionSource.interactions.collect { interaction ->
                val changed = when (interaction) {
                    is PressInteraction.Press -> presses.add(interaction)
                    is PressInteraction.Release -> presses.remove(interaction.press)
                    is PressInteraction.Cancel -> presses.remove(interaction.press)
                    is HoverInteraction.Enter -> hovers.add(interaction)
                    is HoverInteraction.Exit -> hovers.remove(interaction.enter)
                    is DragInteraction.Start -> drags.add(interaction)
                    is DragInteraction.Stop -> drags.remove(interaction.start)
                    is DragInteraction.Cancel -> drags.remove(interaction.start)
                    // Focus and the rest are drawn by something else.
                    else -> false
                }
                if (changed) retarget()
            }
        }
    }

    override fun onReset() {
        reset()
        if (isAttached) invalidateDraw()
    }

    override fun onObservedReadsChanged() {
        val before = pointerHovers
        readModality()
        // Only a hover under way looks any different for it.
        if (pointerHovers != before && hovers.isNotEmpty()) retarget()
    }

    private fun readModality() {
        observeReads { pointerHovers = currentValueOf(LocalInputModality).supportsHover }
    }

    private fun reset() {
        settle?.cancel()
        settle = null
        presses.clear()
        hovers.clear()
        drags.clear()
        pressedAt = null
        overlayColour = Color.Transparent
        overlayAlpha = null
        scale = null
        ink = null
    }

    /** Moves the drawing to what [presses], [drags] and [hovers] now say. */
    private fun retarget() {
        val pressed = presses.isNotEmpty()
        val dragged = drags.isNotEmpty()
        val hovered = hovers.isNotEmpty() && hoverEnabled && pointerHovers

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
         *
         * **The shrink arriving is measured by the shrink.** The floor was
         * once the whole of it, on the wall clock — and the animation it
         * protects runs on the frame clock. On a phone that dropped frames
         * just as the finger lifted, the 120ms ran out while nothing was
         * being drawn, and the release turned the shrink round before it
         * had begun: the flicker, back on exactly the devices most likely to
         * show it. Measured with a stall past the floor, a tap did not move
         * the control at all. So a release now waits for the press's own
         * animation to finish, however many frames that takes, and only
         * then for whatever is left of [IndicationDefaults.PressFloor]. The floor can lengthen
         * the hold; it can no longer cut the shrink short.
         *
         * Still pressed, or something else taking the control over — a drag
         * that grew out of the press, a pointer still hovering — is not a
         * release, and holds nothing: there is no flicker to prevent, and
         * holding would mean a drag's own wash arriving late.
         */
        val pressMark = pressedAt
        val releasing = !pressed && target == Color.Transparent && pressMark != null
        pressedAt = if (pressed) pressedAt ?: TimeSource.Monotonic.markNow() else null

        // **Cancelled after the hold, not before it.** The job being
        // replaced is usually the press's own animation, and cancelling
        // it up front stops the shrink at wherever it had got to — which
        // is the flicker again, arrived at from the other direction. A
        // release *joins* it instead, so by the time `cancel` runs it has
        // finished and this is a no-op; a *press* arriving mid-hold joins
        // nothing and so still takes over at once.
        val previous = settle
        // Made here, by the first thing that animates them. The draw has been
        // reading neither, so their frames would not reach it: asked to draw
        // again, it reads these, and from then on it hears every one.
        val made = overlayAlpha == null
        val alpha = overlayAlpha ?: Animatable(0f).also { overlayAlpha = it }
        val shrink = scale ?: Animatable(1f).also { scale = it }
        val inked = if (pressedInk.isSpecified) ink ?: Animatable(0f).also { ink = it } else null
        if (made) {
            indicationAnimationsMade++
            invalidateDraw()
        }
        settle = coroutineScope.launch {
            if (releasing) {
                previous?.join()
                val rest = IndicationDefaults.PressFloor - pressMark.elapsedNow()
                if (rest > Duration.ZERO) delay(rest)
            }
            previous?.cancel()

            if (target != Color.Transparent) {
                overlayColour = target
            }

            launch {
                alpha.animateTo(
                    targetValue = if (target == Color.Transparent) 0f else target.alpha,
                    animationSpec = motion.tweenFast(),
                )
            }
            if (inked != null) {
                launch {
                    // Held with the ground, by the same hold above, so a quick
                    // tap's label is recoloured for as long as its ground is
                    // there to be read against.
                    inked.animateTo(
                        targetValue = if (pressed) 1f else 0f,
                        animationSpec = motion.tweenFast(),
                    )
                }
            }
            launch {
                // Asymmetric on purpose. Going down: snappy and critically
                // damped, so the control answers the finger immediately.
                // Coming back: under-damped, so it overshoots a little and
                // settles — which is what reads as playful rather than
                // mechanical. Overshooting on the way *down* would just feel
                // slow.
                shrink.animateTo(
                    targetValue = if (pressed && !motion.reduceMotion) pressScale else 1f,
                    animationSpec = motion.springOrTween(
                        if (pressed) motion.springSnappy else motion.springBouncy
                    ),
                )
            }
        }
    }

    override fun ContentDrawScope.draw() {
        val currentScale = scale?.value ?: 1f
        val alpha = overlayAlpha?.value ?: 0f

        // Both inside the same scale, so a pressed control and the wash on top
        // of it are the same shape. Drawn unscaled, the wash painted the
        // control's *original* footprint over a container that had just shrunk
        // out from under it — a faint halo standing where the button used to be,
        // which is the one thing guaranteed to make a shrink read as something
        // else.
        if (currentScale == 1f) {
            drawLayers(alpha)
        } else {
            scale(currentScale) { this@draw.drawLayers(alpha) }
        }
    }

    private fun ContentDrawScope.drawLayers(alpha: Float) {
        if (!behind) {
            drawContent()
            if (alpha > 0f) drawWash(alpha)
            return
        }
        if (alpha > 0f) drawWash(alpha)
        val inked = ink?.value ?: 0f
        if (inked <= 0f) {
            drawContent()
            return
        }
        // The pressed ink, laid over the content in a layer of its own so that
        // `SrcAtop` recolours what the content drew and nothing else: a glyph's
        // own edges keep their coverage, the ground under it is outside the
        // layer, and the space between the letters stays empty. Only while
        // pressed, so a control at rest draws no layer at all.
        //
        // A colour change through composition was what this replaced, and it
        // could not be held: the label changed when the press state did, a
        // frame at most for a tap, which is no change anyone sees.
        drawIntoCanvas { it.saveLayer(Rect(Offset.Zero, size), Paint()) }
        drawContent()
        val colour = pressedInk.copy(alpha = pressedInk.alpha * inked)
        if (inkShape === RectangleShape) {
            drawRect(color = colour, blendMode = BlendMode.SrcAtop)
        } else {
            drawOutline(
                outline = inkShape.createOutline(size, layoutDirection, this),
                color = colour,
                blendMode = BlendMode.SrcAtop,
            )
        }
        drawIntoCanvas { it.restore() }
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

/**
 * How many times an indication node has made the animations a press needs.
 *
 * An increment and a field, as `SheetState.anchorRebuilds` is, so the test suite
 * can ask whether a control nobody touched made any: they are made by the first
 * interaction a node answers, and not before.
 */
internal var indicationAnimationsMade: Int = 0

/**
 * The press and hover of something with no container of its own — a word, a
 * glyph at the size of the text — drawn **behind** it as a ground, where
 * [KontourIndication] lays a wash over a container.
 *
 * A wash over a word is a grey film on the letters and the space between them,
 * which reads as the word going dim rather than as the word being pressed, and
 * the scale is the one thing a line of type cannot take: it reads as the text
 * jumping. So the press is the accent's own ground behind the label and the
 * label in the ink made for that ground, both drawn: nothing is measured again,
 * the bounds do not move, and the words either side stay where they were.
 *
 * Everything else is [KontourIndication]'s, by being its node: the floor that
 * holds a tap long enough to be seen, hover only for a pointer that can, the
 * fade on the theme's `tweenFast`, and the shrink when [pressScale] asks for
 * one — which only a glyph does.
 *
 * Asked for for 0.5.2 as *"with text buttons, can we add some sort of visual
 * feedback that they've been tapped, similar to regular buttons?"*. There was
 * a tint, but it followed the press state through composition, and a tap is a
 * press and a release inside one frame — inside a scrolling column, where
 * `clickable` holds the press back to see whether it is a scroll, always. The
 * tint was there for a mouse and never for a thumb.
 *
 * @param shape The ground's shape. A text button's own, or the line boxes of an
 *   inline link, given as a shape that draws them.
 * @param inkShape Where [pressedInk] is laid over the content.
 */
internal data class GroundIndication(
    val shape: Shape,
    val hoverGround: Color,
    val pressGround: Color,
    val pressedInk: Color,
    val pressScale: Float,
    val motion: Motion,
    val inkShape: Shape = RectangleShape,
) : IndicationNodeFactory {

    override fun create(interactionSource: InteractionSource): DelegatableNode =
        KontourIndicationNode(
            interactionSource = interactionSource,
            shape = shape,
            hoverColour = hoverGround,
            pressColour = pressGround,
            // A text button is never dragged, and a link's drag is a selection,
            // which has its own highlight.
            draggedColour = Color.Transparent,
            pressScale = pressScale,
            motion = motion,
            hoverEnabled = true,
            behind = true,
            pressedInk = pressedInk,
            inkShape = inkShape,
        )
}

/**
 * The ground a [TextButton][io.kontour.ui.components.action.TextButton] and a
 * link in a [Text][io.kontour.ui.foundation.Text] draw behind themselves, from
 * the theme.
 *
 * The accent's container behind the word for a hover and a press alike, and the
 * label in the container's own ink while pressed — the pair the scheme holds
 * legible on each other, so nothing here is a colour the scheme lacks.
 *
 * **At the enhanced tier a press inverts**: the accent's solid behind the word
 * and the label in its `onSolid`. The container there is a tint a few points off
 * the page, about 1.25:1 on the light tier's white, and the two inks it is
 * pressed between are both a deep violet; a ghost [Button][io.kontour.ui.components.action.Button]'s
 * wash is made stronger at that tier for the same reason, and a wash over a
 * tint muddies the words on it. The solid and its `onSolid` are a pair the tier
 * holds to its own contrast, and a solid ground is unmissable. A hover stays the
 * tint: it says where a pointer is, not that anything happened.
 */
@Composable
internal fun textIndication(
    shape: Shape,
    pressScale: Float = 1f,
    inkShape: Shape = RectangleShape,
): GroundIndication {
    val colours = Theme.colours
    val motion = Theme.motion
    val enhanced = highContrast()
    return remember(shape, inkShape, pressScale, colours, motion, enhanced) {
        GroundIndication(
            shape = shape,
            hoverGround = colours.accent.container,
            pressGround = if (enhanced) colours.accent.solid else colours.accent.container,
            pressedInk = if (enhanced) colours.accent.onSolid else colours.accent.onContainer,
            pressScale = pressScale,
            motion = motion,
            inkShape = inkShape,
        )
    }
}

/** What a press looks like, when a control does not say otherwise. */
object IndicationDefaults {
    /** How far a control shrinks while pressed. Subtle on purpose — 3% reads as response, 10% as a bug. */
    const val PressScale: Float = 0.97f

    /**
     * The shortest a press is allowed to *look*, however briefly it was one.
     *
     * A tap is over before its own animation has started: the shrink is a snappy
     * spring and the wash a `tweenFast`, and a thumb is on the glass for well under
     * either. Released at once, both turn round part-way and the control flickers
     * rather than answering — reported from a phone, against a press that looks right
     * under a mouse for the simple reason that a click lasts longer.
     *
     * A hundred and twenty milliseconds from the press, and only ever *after* the
     * press's own animation has arrived — the animation is what decides that the
     * shrink got there, on the frames it was drawn on, and this is the minimum on top
     * of it. Short enough that a fast double tap is still two taps rather than one long
     * one. It is a **floor** and not a delay — a press already past it, whose shrink
     * has finished, is answered the instant the finger lifts, which is every mouse
     * click and every deliberate hold. See [KontourIndication] and the note inside its
     * node.
     *
     * A starting point, and the kind of number only a thumb can settle.
     */
    val PressFloor: Duration = 120.milliseconds
}

/**
 * A [KontourIndication] wired to the current theme. It follows the input
 * modality from inside the node, so a change of input recomposes nothing.
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
    pressScale: Float = IndicationDefaults.PressScale,
): IndicationNodeFactory {
    val colours = Theme.colours
    val motion = Theme.motion
    // No input modality here: the node reads it. Read here, a hand moving from
    // the mouse to the screen recomposed every control and replaced every node.
    return remember(shape, pressScale, colours, motion) {
        KontourIndication(
            shape = shape,
            hoverColour = colours.overlayHover,
            pressColour = colours.overlayPressed,
            draggedColour = colours.overlayDragged,
            pressScale = pressScale,
            motion = motion,
            hoverEnabled = true,
        )
    }
}
