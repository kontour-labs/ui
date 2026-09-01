package io.kontour.ui.motion

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import io.kontour.ui.theme.Theme

/** Which way a [Transitions.sharedAxis] transition travels. */
enum class MotionAxis { X, Y, Z }

/**
 * Transitions between screens and states, as presets.
 *
 * Three of them, and the choice between them says what the *relationship*
 * between the two states is:
 *
 * | | For | Says |
 * |---|---|---|
 * | [fadeThrough] | Unrelated content in the same place | "a different thing" |
 * | [Transitions.sharedAxis] | A step forward or back in a sequence | "further along" |
 * | [containerTransform] | One thing becoming a bigger view of itself | "the same thing" |
 *
 * Picking the wrong one is not a cosmetic mistake. A shared-axis slide between
 * two unrelated tabs implies an order that is not there; a fade between a list
 * row and its detail throws away the one cue that connects them.
 *
 * Every preset reads `Theme.motion`, so all of them collapse to a plain
 * cross-fade under reduced motion — movement is the thing the preference exists
 * to remove, and a fade is not movement.
 *
 * Named `Transitions` rather than `Motion` because [io.kontour.ui.theme.Motion]
 * is the token set these read from, and a file that wanted both could not import
 * them without an alias.
 */
object Transitions {

    /**
     * For content that replaces content, with no relationship between them.
     *
     * The outgoing fades and shrinks slightly, the incoming fades and grows into
     * place. The scale is small on purpose: it reads as a change of subject
     * rather than as a zoom.
     *
     * @param fast Runs the whole thing on the fast token instead of the default.
     *   For a change the user makes repeatedly and expects to be *there* — a tab
     *   switch, most of all. 220ms is right for a change of subject the user
     *   chose to make once; on the fourth tap of the same tab it is a wait.
     */
    @Composable
    @ReadOnlyComposable
    fun fadeThrough(fast: Boolean = false): ContentTransform {
        val motion = Theme.motion
        if (motion.reduceMotion) {
            return fadeIn(motion.tweenFast()) togetherWith fadeOut(motion.tweenFast())
        }
        val incoming = if (fast) motion.tweenFast<Float>() else motion.tweenDefault<Float>()
        return (fadeIn(incoming) + scaleIn(incoming, initialScale = 0.94f))
            .togetherWith(
                fadeOut(motion.tweenFast()) +
                    scaleOut(motion.tweenFast(), targetScale = 0.94f)
            )
    }

    /**
     * For a step along a sequence — forward through a flow, back out of it.
     *
     * @param forward Which direction the user is going. Getting this from the
     *   navigation state rather than hard-coding it is what makes back feel like
     *   back.
     * @param axis [MotionAxis.X] for a horizontal flow, [MotionAxis.Y] for a
     *   vertical one, [MotionAxis.Z] for going *into* something — a list row
     *   opening a detail on top of itself rather than beside it.
     */
    @Composable
    @ReadOnlyComposable
    fun sharedAxis(forward: Boolean, axis: MotionAxis = MotionAxis.X): ContentTransform {
        val motion = Theme.motion
        if (motion.reduceMotion) {
            return fadeIn(motion.tweenFast()) togetherWith fadeOut(motion.tweenFast())
        }

        // A third of the width, not the whole width. Content that slides in from
        // fully off-screen has to travel the entire viewport in the same time,
        // which reads as thrown rather than moved.
        val enter: EnterTransition
        val exit: ExitTransition
        when (axis) {
            MotionAxis.X -> {
                enter = slideInHorizontally(motion.tweenDefault()) { w ->
                    if (forward) w / 3 else -w / 3
                } + fadeIn(motion.tweenFast())
                exit = slideOutHorizontally(motion.tweenDefault()) { w ->
                    if (forward) -w / 3 else w / 3
                } + fadeOut(motion.tweenFast())
            }

            MotionAxis.Y -> {
                enter = slideInVertically(motion.tweenDefault()) { h ->
                    if (forward) h / 3 else -h / 3
                } + fadeIn(motion.tweenFast())
                exit = slideOutVertically(motion.tweenDefault()) { h ->
                    if (forward) -h / 3 else h / 3
                } + fadeOut(motion.tweenFast())
            }

            MotionAxis.Z -> {
                enter = scaleIn(
                    motion.tweenDefault(),
                    initialScale = if (forward) 0.9f else 1.1f,
                ) + fadeIn(motion.tweenFast())
                exit = scaleOut(
                    motion.tweenFast(),
                    targetScale = if (forward) 1.1f else 0.9f,
                ) + fadeOut(motion.tweenFast())
            }
        }
        return enter togetherWith exit
    }

    /**
     * For one element becoming a larger view of itself.
     *
     * A card opening into the screen it represents. The incoming content grows
     * from nearly the outgoing's size while the outgoing fades, so the two read
     * as one object rather than two.
     *
     * A true container transform morphs the bounds of a shared element between
     * two layouts; Compose has `SharedTransitionLayout` for that, and this is
     * the cheap version for when the two are not literally the same node. Reach
     * for `SharedTransitionLayout` when they are.
     */
    @Composable
    @ReadOnlyComposable
    fun containerTransform(): ContentTransform {
        val motion = Theme.motion
        return if (motion.reduceMotion) {
            fadeIn(motion.tweenFast()) togetherWith fadeOut(motion.tweenFast())
        } else {
            (scaleIn(motion.tweenDefault(), initialScale = 0.88f) + fadeIn(motion.tweenDefault()))
                .togetherWith(fadeOut(motion.tweenFast()))
        }
    }
}

/**
 * Which side of an [AnimatedSlot] its gap sits on.
 *
 * The side away from the content it is spacing away from: a slot at the *start*
 * of a row carries its gap [Trailing], and one at the end carries it [Leading].
 */
enum class SlotGap { Leading, Trailing }

/**
 * An appearing or disappearing row item that carries its own gap.
 *
 * ### The bug this exists to prevent
 *
 * `AnimatedVisibility` inside a `Row` with `Arrangement.spacedBy(gap)` animates
 * the child's width down to nothing and then, when the animation finishes,
 * removes the child from composition. The arrangement applies the **full** gap
 * for as long as that child exists and none the instant it does not — so the row
 * loses `gap` of width in a single frame, *after* everything else has stopped
 * moving. It reads as a snap at the very end of an otherwise smooth collapse,
 * and it was reported independently against the extended FAB, the chip and a
 * text field's error row before anyone noticed it was one bug.
 *
 * The fix is not to animate the gap alongside the content: that is a second
 * animation with its own curve, and keeping two curves in step is a promise the
 * next person to tune one of them will break. The gap goes **inside** the thing
 * being animated, so it is part of the width already being interpolated and
 * there is only ever one animation to get right.
 *
 * Drop the `spacedBy` from the parent when using this — a gap on both sides is
 * the same snap with an extra `gap` of width in front of it.
 *
 * @param orientation The parent's axis. A column has exactly the same bug on the
 *   vertical one: a validation message leaving takes its line height *and* the
 *   gap above it, and the gap goes in the last frame.
 */
@Composable
internal fun AnimatedSlot(
    visible: Boolean,
    gap: Dp,
    enter: EnterTransition,
    exit: ExitTransition,
    modifier: Modifier = Modifier,
    side: SlotGap = SlotGap.Leading,
    orientation: Orientation = Orientation.Horizontal,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(visible = visible, modifier = modifier, enter = enter, exit = exit) {
        if (orientation == Orientation.Horizontal) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (side == SlotGap.Leading) Spacer(Modifier.width(gap))
                content()
                if (side == SlotGap.Trailing) Spacer(Modifier.width(gap))
            }
        } else {
            Column {
                if (side == SlotGap.Leading) Spacer(Modifier.height(gap))
                content()
                if (side == SlotGap.Trailing) Spacer(Modifier.height(gap))
            }
        }
    }
}
