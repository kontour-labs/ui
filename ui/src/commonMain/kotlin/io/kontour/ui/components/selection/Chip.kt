package io.kontour.ui.components.selection

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.kontour.ui.a11y.minimumTouchTarget
import io.kontour.ui.foundation.Icon
import io.kontour.ui.foundation.LocalContentColor
import io.kontour.ui.foundation.ProvideTextStyle
import io.kontour.ui.foundation.RowContentScope
import io.kontour.ui.foundation.contentScope
import io.kontour.ui.foundation.Text
import io.kontour.ui.input.focusRing
import io.kontour.ui.interaction.Feedback
import io.kontour.ui.interaction.FeedbackIntent
import io.kontour.ui.interaction.kontourIndication
import io.kontour.ui.theme.Theme

private val ChipHeight = 34.dp

/**
 * A compact, pill-shaped control.
 *
 * ```
 * // Filter — toggles a facet on and off
 * FilterChip(selected = showBuses, onClick = ::toggleBuses) { +"Buses" }
 *
 * // Assist — performs an action
 * Chip(onClick = ::shareTrip) {
 *     +Tabler.Outline.Share
 *     +"Share"
 * }
 *
 * // Input — represents a value the user entered, and can be removed
 * InputChip(onRemove = ::clearOrigin, removeLabel = "Remove Perth Station") {
 *     +"Perth Station"
 * }
 * ```
 *
 * Chips are for things that come in *sets*. A single chip on a screen is
 * usually a small button wearing the wrong clothes.
 */
@Composable
fun Chip(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowContentScope.() -> Unit
) {
    val interactions = interactionSource ?: remember { MutableInteractionSource() }
    val colors = Theme.colors
    val shape = Theme.shapes.pill
    val feedback = Feedback

    ChipSurface(
        modifier = modifier
            .focusRing(interactions, shape)
            .clip(shape)
            .background(if (enabled) colors.surfaceSunken else Color.Transparent, shape)
            .clickable(
                interactionSource = interactions,
                indication = kontourIndication(shape),
                enabled = enabled,
                role = Role.Button,
                onClick = {
                    feedback.perform(FeedbackIntent.Selection)
                    onClick()
                },
            ),
        contentColor = if (enabled) colors.content else colors.contentDisabled,
        content = content
    )
}

/**
 * A chip that toggles a facet on and off.
 *
 * When selected it fills with the accent container and grows a tick in front of
 * the label. The tick *expands in* rather than appearing, pushing the label
 * across — the small shove is what makes a filter bar feel responsive when you
 * rattle through several of them.
 */
@Composable
fun FilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selectedIcon: ImageVector? = null,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowContentScope.() -> Unit
) {
    val interactions = interactionSource ?: remember { MutableInteractionSource() }
    val colors = Theme.colors
    val motion = Theme.motion
    val shape = Theme.shapes.pill
    val feedback = Feedback

    val container by animateColorAsState(
        targetValue = when {
            !enabled -> Color.Transparent
            selected -> colors.accent.container
            else -> Color.Transparent
        },
        animationSpec = motion.tweenFast(),
        label = "chipContainer",
    )
    val contentColor by animateColorAsState(
        targetValue = when {
            !enabled -> colors.contentDisabled
            selected -> colors.accent.onContainer
            else -> colors.content
        },
        animationSpec = motion.tweenFast(),
        label = "chipContent",
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            !enabled -> colors.outline
            selected -> Color.Transparent
            else -> colors.outlineStrong
        },
        animationSpec = motion.tweenFast(),
        label = "chipBorder",
    )

    Row(
        modifier = modifier
            .minimumTouchTarget()
            .focusRing(interactions, shape)
            .height(ChipHeight)
            .clip(shape)
            .background(container, shape)
            .border(BorderStroke(Theme.sizing.borderWidth, borderColor), shape)
            .selectable(
                selected = selected,
                onClick = {
                    feedback.perform(FeedbackIntent.Selection)
                    onClick()
                },
                enabled = enabled,
                role = Role.Checkbox,
                interactionSource = interactions,
                indication = kontourIndication(shape),
            )
            .padding(horizontal = Theme.spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            if (selectedIcon != null) {
                AnimatedVisibility(
                    visible = selected,
                    enter = expandHorizontally(motion.springOrTween(motion.springSnappy)) +
                        fadeIn(motion.tweenFast()) +
                        scaleIn(motion.springOrTween(motion.springBouncy), initialScale = 0.4f),
                    exit = shrinkHorizontally(motion.tweenFast()) +
                        fadeOut(motion.tweenFast()) +
                        scaleOut(motion.tweenFast(), targetScale = 0.4f),
                ) {
                    Icon(selectedIcon, contentDescription = null, size = Theme.sizing.iconSmall)
                }
            }
            ProvideTextStyle(Theme.typography.labelMedium) {
                contentScope(
                    iconSize = Theme.sizing.iconSmall,
                    maxLines = 1,
                    content = content,
                )
            }
        }
    }
}

/**
 * A chip representing a value the user supplied, with a remove affordance.
 *
 * The remove button is a *separate* target with its own description, so a screen
 * reader offers "Perth Station" and "Remove Perth Station" as distinct actions
 * rather than one ambiguous one.
 */
@Composable
fun InputChip(
    onRemove: () -> Unit,
    /**
     * What the remove button announces — "Remove Perth Station", not "Remove".
     *
     * Required, and it used to default to `"Remove $label"`. With the label in a
     * slot there is no string to interpolate, and a bare "Remove" in a row of
     * five chips tells a screen-reader user nothing about which one goes.
     */
    removeLabel: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    removeIcon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowContentScope.() -> Unit
) {
    val interactions = interactionSource ?: remember { MutableInteractionSource() }
    val colors = Theme.colors
    val shape = Theme.shapes.pill
    val feedback = Feedback

    Row(
        modifier = modifier
            .minimumTouchTarget()
            .focusRing(interactions, shape)
            .height(ChipHeight)
            .clip(shape)
            .background(colors.surfaceSunken, shape)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interactions,
                        indication = kontourIndication(shape),
                        enabled = enabled,
                        role = Role.Button,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                }
            )
            .padding(start = Theme.spacing.sm, end = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(
            LocalContentColor provides if (enabled) colors.content else colors.contentDisabled
        ) {
            ProvideTextStyle(Theme.typography.labelMedium) {
                contentScope(
                    iconSize = Theme.sizing.iconSmall,
                    maxLines = 1,
                    content = content,
                )
            }
            if (removeIcon != null) {
                Row(
                    Modifier
                        .clip(Theme.shapes.pill)
                        .clickable(
                            enabled = enabled,
                            role = Role.Button,
                            onClickLabel = removeLabel,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = kontourIndication(Theme.shapes.pill),
                            onClick = {
                                feedback.perform(FeedbackIntent.Selection)
                                onRemove()
                            },
                        )
                        .padding(4.dp)
                ) {
                    Icon(removeIcon, contentDescription = removeLabel, size = Theme.sizing.iconSmall)
                }
            }
        }
    }
}

/**
 * Lays chips out in rows, wrapping onto the next line as they run out of width.
 *
 * A horizontally-scrolling chip row hides options off the edge of the screen;
 * wrapping shows the user everything they can filter by. Prefer this unless the
 * set is genuinely unbounded.
 */
@Composable
fun ChipGroup(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(Theme.spacing.xs),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(Theme.spacing.xs),
    content: @Composable () -> Unit,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = horizontalArrangement,
        verticalArrangement = verticalArrangement,
    ) {
        content()
    }
}

@Composable
private fun ChipSurface(
    modifier: Modifier,
    contentColor: Color,
    content: @Composable RowContentScope.() -> Unit
) {
    Row(
        modifier = modifier
            .minimumTouchTarget()
            .height(ChipHeight)
            .padding(horizontal = Theme.spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            ProvideTextStyle(Theme.typography.labelMedium) {
                contentScope(
                    iconSize = Theme.sizing.iconSmall,
                    maxLines = 1,
                    content = content,
                )
            }
        }
    }
}
