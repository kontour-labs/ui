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
 * @param rotation Degrees to rotate the icon, animated. For disclosure chevrons
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
    val motion = Theme.motion
    val feedback = Feedback

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
            .clickable(
                interactionSource = interactions,
                indication = kontourIndication(shape, ButtonDefaults.pressScale(variant)),
                enabled = enabled,
                role = Role.Button,
                onClick = {
                    feedback.perform(FeedbackIntent.Selection)
                    onClick()
                },
            ),
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
 * "Favourite, on" rather than just "Favourite". Pass a distinct [icon] per state
 * — the state must be visible, not only audible.
 *
 * @param stateDescription Overrides the announced state. Defaults to the
 *   platform's own on/off wording; pass something specific where on/off is
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
    val checkedColors = ButtonDefaults.colors(ButtonVariant.Tertiary)
    val uncheckedColors = ButtonDefaults.colors(ButtonVariant.Ghost)
    val accentColors = checkedColors.copy(
        container = Theme.colors.accentContainer,
        content = Theme.colors.onAccentContainer,
    )

    Box(
        modifier.semantics {
            role = Role.Switch
            toggleableState = ToggleableState(checked)
            if (stateDescription != null) this.stateDescription = stateDescription
        }
    ) {
        IconButton(
            icon = icon,
            contentDescription = contentDescription,
            onClick = { onCheckedChange(!checked) },
            enabled = enabled,
            size = size,
            shape = shape,
            colors = if (checked) accentColors else uncheckedColors,
            interactionSource = interactionSource,
        )
    }
}
