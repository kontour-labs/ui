package io.kontour.ui.components.action

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.kontour.ui.a11y.minimumTouchTarget
import io.kontour.ui.foundation.Icon
import io.kontour.ui.foundation.ProvideTextStyle
import io.kontour.ui.foundation.Surface
import io.kontour.ui.foundation.Text
import io.kontour.ui.input.focusRing
import io.kontour.ui.interaction.Feedback
import io.kontour.ui.interaction.FeedbackIntent
import io.kontour.ui.interaction.kontourIndication
import io.kontour.ui.theme.Theme

/** How large a [FloatingActionButton] is. */
enum class FabSize(internal val container: Dp, internal val icon: Dp) {
    Small(40.dp, 20.dp),
    Medium(56.dp, 24.dp),
    Large(72.dp, 32.dp),
}

/**
 * A floating action button — the one persistent action on a screen.
 *
 * ```
 * FloatingActionButton(
 *     icon = Icons.Plus,
 *     contentDescription = "Add favourite",
 *     onClick = ::addFavourite,
 * )
 * ```
 *
 * Floats above content, so it takes [io.kontour.ui.theme.Elevation.medium] and
 * a pill shape. One per screen: a second FAB is two competing "the" actions.
 *
 * If the action needs a label to be understood, use [ExtendedFloatingActionButton]
 * rather than hoping the icon carries it.
 */
@Composable
fun FloatingActionButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: FabSize = FabSize.Medium,
    shape: Shape = Theme.shapes.pill,
    containerColor: Color = Theme.colors.primary,
    contentColor: Color = Theme.colors.onPrimary,
    interactionSource: MutableInteractionSource? = null,
) {
    val interactions = interactionSource ?: remember { MutableInteractionSource() }
    val feedback = Feedback

    Surface(
        modifier = modifier
            .minimumTouchTarget()
            .focusRing(interactions, shape)
            .defaultMinSize(minWidth = size.container, minHeight = size.container)
            .height(size.container)
            .clickable(
                interactionSource = interactions,
                indication = kontourIndication(shape),
                enabled = enabled,
                role = Role.Button,
                onClick = {
                    feedback.perform(FeedbackIntent.Confirm)
                    onClick()
                },
            ),
        shape = shape,
        color = containerColor,
        contentColor = contentColor,
        shadow = Theme.elevation.medium,
        // `defaultMinSize` makes the surface larger than the icon inside it, so
        // without this the icon lands in the FAB's top-left corner rather than
        // its middle.
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, size = size.icon)
    }
}

/**
 * A FAB that carries a label as well as an icon, and can collapse to just the
 * icon as the user scrolls.
 *
 * ```
 * ExtendedFloatingActionButton(
 *     icon = Icons.Navigation,
 *     text = "Start trip",
 *     contentDescription = "Start trip",
 *     expanded = !listState.isScrollingDown,
 *     onClick = ::startTrip,
 * )
 * ```
 *
 * The collapse animates the *width* rather than cross-fading between two
 * components, so the icon stays put and the label slides out from behind it.
 * Cross-fading makes the icon appear to jump sideways.
 *
 * @param contentDescription Announced by a screen reader. Kept separate from
 *   [text] because the label may be terse where the announcement should not be
 *   — "Start" on screen, "Start trip to Perth Station" for a screen reader.
 */
@Composable
fun ExtendedFloatingActionButton(
    icon: ImageVector,
    text: String,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    expanded: Boolean = true,
    enabled: Boolean = true,
    shape: Shape = Theme.shapes.pill,
    containerColor: Color = Theme.colors.primary,
    contentColor: Color = Theme.colors.onPrimary,
    interactionSource: MutableInteractionSource? = null,
) {
    val interactions = interactionSource ?: remember { MutableInteractionSource() }
    val motion = Theme.motion
    val feedback = Feedback

    val horizontalPadding by animateDpAsState(
        targetValue = if (expanded) 20.dp else 16.dp,
        animationSpec = motion.springOrTween(motion.springDefault),
        label = "fabPadding",
    )

    Surface(
        modifier = modifier
            .minimumTouchTarget()
            .focusRing(interactions, shape)
            .height(FabSize.Medium.container)
            .clickable(
                interactionSource = interactions,
                indication = kontourIndication(shape),
                enabled = enabled,
                role = Role.Button,
                onClick = {
                    feedback.perform(FeedbackIntent.Confirm)
                    onClick()
                },
            ),
        shape = shape,
        color = containerColor,
        contentColor = contentColor,
        shadow = Theme.elevation.medium,
    ) {
        Row(
            modifier = Modifier
                .height(FabSize.Medium.container)
                .padding(horizontal = horizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = if (expanded) null else contentDescription,
                size = FabSize.Medium.icon,
            )
            AnimatedVisibility(
                visible = expanded,
                enter = expandHorizontally(motion.springOrTween(motion.springDefault)) +
                    fadeIn(motion.tweenFast()),
                exit = shrinkHorizontally(motion.springOrTween(motion.springDefault)) +
                    fadeOut(motion.tweenFast()),
            ) {
                ProvideTextStyle(Theme.typography.labelLarge) {
                    Text(text, maxLines = 1)
                }
            }
        }
    }
}
