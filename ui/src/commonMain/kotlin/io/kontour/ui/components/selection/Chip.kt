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
import io.kontour.ui.motion.AnimatedSlot
import io.kontour.ui.motion.SlotGap
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.togetherWith
import io.kontour.ui.theme.Theme
import io.kontour.ui.theme.invisible

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
/**
 * The gap between a chip's tick and its label.
 *
 * Named because [AnimatedSlot] needs it as a value rather than as an
 * arrangement, and because three chip variants were each restating `6.dp`.
 */
private val ChipIconGap = 6.dp

@Composable
fun Chip(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    /**
     * What makes this chip's content *this* content.
     *
     * A chip whose label is replaced — "Perth Station" becoming "Undo" once the
     * filter is cleared — cuts between the two, because the label arrives
     * through a slot and a slot has no identity to compare. Give the key
     * whatever the label is derived from and the change cross-fades and resizes
     * instead.
     *
     * Null by default: without a key there is nothing to diff, and animating on
     * every recomposition would flicker a chip whose label never changed.
     */
    contentKey: Any? = null,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowContentScope.() -> Unit
) {
    val interactions = interactionSource ?: remember { MutableInteractionSource() }
    val colors = Theme.colors
    val shape = Theme.shapes.control
    val feedback = Feedback

    ChipSurface(
        modifier = modifier
            // Target first, then the visuals — the order every other component
            // in the library uses, and the one `Modifier.minimumTouchTarget`'s
            // own KDoc documents: reserve the space, draw the pill inside it.
            .minimumTouchTarget()
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
        content = { KeyedChipContent(contentKey, content) },
    )
}

/**
 * A chip's label, cross-faded when its [key] changes.
 *
 * Without a key this is the slot and nothing else, so a chip that never changes
 * label pays for none of this — no `AnimatedContent`, no extra layout node, and
 * no chance of a flicker on an unrelated recomposition.
 *
 * With one, the label fades and the chip resizes to it, which is what a filter
 * chip turning into an "Undo" chip should do: it is the same chip changing its
 * mind, not one chip leaving and another arriving.
 */
@Composable
private fun RowContentScope.KeyedChipContent(
    key: Any?,
    content: @Composable RowContentScope.() -> Unit,
) {
    if (key == null) {
        content()
        return
    }
    val motion = Theme.motion
    AnimatedContent(
        targetState = key,
        transitionSpec = {
            (fadeIn(motion.tweenFast()) togetherWith fadeOut(motion.tweenFast()))
                .using(SizeTransform(clip = false))
        },
        label = "chipContent",
    ) { _ ->
        content()
    }
}

/**
 * A chip that toggles a facet on and off.
 *
 * When selected it fills with the accent container and takes the accent for its
 * label, dropping the outline it wears unselected.
 *
 * **The tick is opt-in.** Pass [selectedIcon] and it *expands in* rather than
 * appearing, pushing the label across — the small shove is what makes a filter
 * bar feel responsive when you rattle through several of them. It is a parameter
 * rather than a default because the library ships no glyphs at all: the icon set
 * is the application's choice, so there is no tick here to reach for.
 *
 * Without one, selection is carried by colour alone. That is legible, and it is
 * the only channel — so a filter bar where the distinction matters should pass
 * an icon, for the same reason a chart should not encode its meaning in hue.
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
    val shape = Theme.shapes.control
    val feedback = Feedback

    val container by animateColorAsState(
        targetValue = when {
            !enabled -> colors.accent.container.invisible()
            selected -> colors.accent.container
            else -> colors.accent.container.invisible()
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
            selected -> colors.outlineStrong.invisible()
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
        // No `spacedBy`: the tick carries its own gap, so the row does not lose
        // it in one frame when the tick leaves composition. See `AnimatedSlot`.
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            if (selectedIcon != null) {
                AnimatedSlot(
                    visible = selected,
                    gap = ChipIconGap,
                    side = SlotGap.Trailing,
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
            // The static content keeps an arrangement of its own.
            //
            // `contentScope` emits its icon and its label straight into the
            // surrounding row — it has no layout of its own — so the chip's
            // `spacedBy` was the only thing separating a leading icon from the
            // words next to it. Dropping that arrangement for the tick's sake
            // jammed "🚌Trains" together, which is a different bug in the same
            // row and one the golden caught only because someone looked.
            //
            // Two arrangements, then: the tick's gap travels with the tick, and
            // everything that is always there is spaced by a row that is always
            // there.
            Row(
                horizontalArrangement = Arrangement.spacedBy(ChipIconGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
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
    val shape = Theme.shapes.control
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
                        .clip(Theme.shapes.control)
                        .clickable(
                            enabled = enabled,
                            role = Role.Button,
                            onClickLabel = removeLabel,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = kontourIndication(Theme.shapes.control),
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
        // No `minimumTouchTarget` here, and that is the fix rather than an
        // omission.
        //
        // `modifier` arrives from the caller already carrying `clip`,
        // `background` and `clickable`, so anything appended below them is
        // *inside* the visuals — and `minimumTouchTarget` expands the node it is
        // on. The pill was therefore drawn at the reserved target: 48dp on
        // Android, 44 on iOS, beside 34dp filter and input chips built in the
        // correct order. On the JVM `max(34, 24)` is 34, so every golden showed
        // three identical chips and the suite stayed green.
        //
        // The target belongs at the top of the chain, which is where the caller
        // puts it now — see [Chip].
        modifier = modifier
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
