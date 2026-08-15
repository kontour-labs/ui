package io.kontour.ui.components.action

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import io.kontour.ui.a11y.minimumTouchTarget
import io.kontour.ui.foundation.Icon
import io.kontour.ui.foundation.LocalContentColor
import io.kontour.ui.input.focusRing
import io.kontour.ui.interaction.Feedback
import io.kontour.ui.interaction.FeedbackIntent
import io.kontour.ui.interaction.kontourIndication
import io.kontour.ui.theme.Theme

/**
 * A button whose whole label is an icon.
 *
 * ```
 * IconButton(
 *     icon = Icons.Close,
 *     contentDescription = "Close",
 *     onClick = ::dismiss,
 * )
 * ```
 *
 * The visual bounds stay small — the icon plus a little padding — while the
 * touch target expands to the platform minimum around it. That is why a toolbar
 * of 20dp icons is still usable with a thumb.
 *
 * @param contentDescription **Required and non-null.** There is no visible text
 *   to fall back on, so an icon button without a description is a control a
 *   screen-reader user simply cannot identify. If the icon is genuinely
 *   decorative, it is not a button.
 * @param rotation Degrees to rotate the icon, animated.
 *
 *   Deliberately *not* `Modifier.rotate()` at the call site, for two reasons.
 *   The caller's `modifier` lands on the outer box, which carries the touch
 *   target, the focus ring, the ripple and the container — a rotation there
 *   turns the hit rectangle and the focus ring with the glyph, which is
 *   invisible on a pill and wrong everywhere else. And this is a *target*, not a
 *   value: it is animated through `springBouncy`, so it overshoots a few degrees
 *   and settles, which a static modifier cannot express.
 *
 *   For disclosure chevrons
 *   and menu/close morphs — pass `if (expanded) 90f else 0f` rather than
 *   swapping between two icons, which reads as a flicker.
 */
@Composable
fun IconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    variant: ButtonVariant = ButtonVariant.Ghost,
    size: ButtonSize = ButtonSize.Medium,
    shape: Shape = Theme.shapes.pill,
    rotation: Float = 0f,
    colors: ButtonColors = ButtonDefaults.colors(variant),
    metrics: ButtonMetrics = ButtonDefaults.metrics(size),
    interactionSource: MutableInteractionSource? = null,
) {
    val interactions = interactionSource ?: remember { MutableInteractionSource() }
    val feedback = Feedback

    IconButtonSurface(
        icon = icon,
        contentDescription = contentDescription,
        modifier = modifier,
        enabled = enabled,
        shape = shape,
        rotation = rotation,
        colors = colors,
        metrics = metrics,
        interactions = interactions,
        behaviour = Modifier.clickable(
            interactionSource = interactions,
            indication = kontourIndication(shape, ButtonDefaults.pressScale(variant)),
            enabled = enabled,
            role = Role.Button,
            onClick = {
                feedback.perform(FeedbackIntent.Selection)
                onClick()
            },
        ),
    )
}

/**
 * An [IconButton] with an on/off state.
 *
 * ```
 * IconToggleButton(
 *     icon = if (favourite) Icons.StarFilled else Icons.StarOutline,
 *     contentDescription = "Favourite",
 *     checked = favourite,
 *     onCheckedChange = viewModel::setFavourite,
 * )
 * ```
 *
 * Announces itself as a toggle with its current state, so a screen reader says
 * "Favourite, ticked" rather than just "Favourite". Pass a distinct [icon] per
 * state — the state must be visible, not only audible.
 *
 * `Role.Checkbox`, not `Role.Switch`, and the same as [FilterChip][
 * io.kontour.ui.components.selection.FilterChip]: `Switch` is reserved for the
 * sliding control, where the announcement "on/off" matches something the user
 * can see. A star that announces itself as a switch describes a widget that is
 * not on screen.
 *
 * @param stateDescription Overrides the announced state. Defaults to the
 *   platform's own ticked/unticked wording; pass something specific where that is
 *   ambiguous ("Showing live vehicles" / "Hiding live vehicles").
 */
@Composable
fun IconToggleButton(
    icon: ImageVector,
    contentDescription: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: ButtonSize = ButtonSize.Medium,
    shape: Shape = Theme.shapes.pill,
    stateDescription: String? = null,
    interactionSource: MutableInteractionSource? = null,
) {
    val interactions = interactionSource ?: remember { MutableInteractionSource() }
    val feedback = Feedback
    val checkedColors = ButtonDefaults.colors(ButtonVariant.Tertiary)
    val uncheckedColors = ButtonDefaults.colors(ButtonVariant.Ghost)
    val accentColors = checkedColors.copy(
        container = Theme.colors.accent.container,
        content = Theme.colors.accent.onContainer,
    )

    IconButtonSurface(
        icon = icon,
        contentDescription = contentDescription,
        // The state description goes on the same node as the toggle, not on a
        // wrapper around it. A wrapper produces two nodes — a labelled one with
        // no action and an actionable one with no label — and assistive tech
        // reads the pair as a control inside a control.
        modifier = modifier.then(
            if (stateDescription != null) {
                Modifier.semantics { this.stateDescription = stateDescription }
            } else {
                Modifier
            }
        ),
        enabled = enabled,
        shape = shape,
        rotation = 0f,
        colors = if (checked) accentColors else uncheckedColors,
        metrics = ButtonDefaults.metrics(size),
        interactions = interactions,
        behaviour = Modifier.toggleable(
            value = checked,
            interactionSource = interactions,
            indication = kontourIndication(shape, ButtonDefaults.pressScale(ButtonVariant.Ghost)),
            enabled = enabled,
            role = Role.Checkbox,
            onValueChange = {
                feedback.perform(FeedbackIntent.Selection)
                onCheckedChange(it)
            },
        ),
    )
}

/**
 * The shared body of [IconButton] and [IconToggleButton]: a circular container
 * with one centred, optionally-rotated glyph.
 *
 * [behaviour] is the caller's `clickable` or `toggleable` — passed in rather than
 * built here so that the role, the state and the action all land on **one**
 * semantics node. That is the whole reason this exists: the obvious alternative,
 * wrapping an `IconButton` in a `Box` that adds toggle semantics, produces a node
 * tree that reads as a switch containing a button.
 */
@Composable
private fun IconButtonSurface(
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier,
    enabled: Boolean,
    shape: Shape,
    rotation: Float,
    colors: ButtonColors,
    metrics: ButtonMetrics,
    interactions: MutableInteractionSource,
    behaviour: Modifier,
) {
    val motion = Theme.motion

    val container by animateColorAsState(
        targetValue = colors.container(enabled),
        animationSpec = motion.tweenFast(),
        label = "iconButtonContainer",
    )
    val contentColor by animateColorAsState(
        targetValue = colors.content(enabled),
        animationSpec = motion.tweenFast(),
        label = "iconButtonContent",
    )
    val animatedRotation by animateFloatAsState(
        targetValue = rotation,
        // Bouncy: a chevron that overshoots a few degrees and settles reads as
        // a physical flip rather than a value being assigned.
        animationSpec = motion.springOrTween(motion.springBouncy),
        label = "iconButtonRotation",
    )
    val borderColor = colors.border(enabled)
    val boxSize = metrics.iconSize + metrics.iconOnlyPadding * 2

    Box(
        modifier = modifier
            .minimumTouchTarget()
            .focusRing(interactions, shape)
            .size(boxSize)
            .clip(shape)
            .background(container, shape)
            .then(
                if (borderColor != null) {
                    Modifier.border(BorderStroke(Theme.sizing.borderWidthStrong, borderColor), shape)
                } else {
                    Modifier
                }
            )
            .then(behaviour),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.rotate(animatedRotation),
                size = metrics.iconSize,
            )
        }
    }
}
