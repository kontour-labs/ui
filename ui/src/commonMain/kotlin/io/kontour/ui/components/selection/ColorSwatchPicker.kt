package io.kontour.ui.components.selection

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.kontour.ui.a11y.contentColorFor
import io.kontour.ui.a11y.minimumTouchTarget
import io.kontour.ui.foundation.Icon
import io.kontour.ui.foundation.SystemIcons
import io.kontour.ui.input.focusRing
import io.kontour.ui.interaction.FeedbackIntent
import io.kontour.ui.interaction.LocalFeedback
import io.kontour.ui.interaction.kontourIndication
import io.kontour.ui.theme.Theme

/**
 * Picks a colour from a fixed set.
 *
 * ```kotlin
 * ColorSwatchPicker(
 *     value = prefs.accent,
 *     options = AccentColour.entries,
 *     onValueChange = viewModel::updateAccent,
 *     swatchColor = { it.seed },
 *     swatchLabel = { it.displayName },
 * )
 * ```
 *
 * A grid of swatches rather than a dropdown of colour names, because the choice
 * being made is visual: "which of these do I like" is answered by looking, and a
 * list that shows one colour at a time makes the user open it six times.
 *
 * **The tick is drawn in whatever colour is legible on the swatch**, resolved
 * through `contentColorFor()`. A fixed white tick vanishes on pale yellow and a
 * fixed black one vanishes on navy, and a picker whose selection is invisible on
 * two of its own options is a picker with a bug in it.
 *
 * Every swatch carries [swatchLabel] as its content description and reports
 * `Role.RadioButton`. A colour with no name is unusable to anyone who cannot see
 * it — and to anyone who can, in a screenshot they are describing over the
 * phone.
 *
 * @param swatchColor The colour to draw. Return `null` for an option that is not
 *   a colour — a "match the system" entry — which renders as an outlined swatch
 *   with [automaticIcon] instead.
 */
@Composable
fun <T> ColorSwatchPicker(
    value: T?,
    options: List<T>,
    onValueChange: (T) -> Unit,
    swatchColor: (T) -> Color?,
    swatchLabel: (T) -> String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    swatchSize: Dp = 40.dp,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(Theme.spacing.xs),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(Theme.spacing.xs),
    automaticIcon: ImageVector? = null,
) {
    FlowRow(
        modifier = modifier.selectableGroup(),
        horizontalArrangement = horizontalArrangement,
        verticalArrangement = verticalArrangement,
    ) {
        for (option in options) {
            Swatch(
                color = swatchColor(option),
                label = swatchLabel(option),
                selected = option == value,
                onSelectedChange = { onValueChange(option) },
                enabled = enabled,
                size = swatchSize,
                automaticIcon = automaticIcon,
            )
        }
    }
}

@Composable
private fun Swatch(
    color: Color?,
    label: String,
    selected: Boolean,
    onSelectedChange: () -> Unit,
    enabled: Boolean,
    size: Dp,
    automaticIcon: ImageVector?,
) {
    val scheme = Theme.colors
    val motion = Theme.motion
    val feedback = LocalFeedback.current
    val interactions = remember { MutableInteractionSource() }
    val shape = Theme.shapes.pill

    val fill = color ?: scheme.surfaceSunken
    val tick = if (color != null) contentColorFor(color) else scheme.content

    // The ring grows out of the swatch rather than fading in, so picking one
    // feels like it clicked into place. It sits outside the fill, so it never
    // eats into the colour being judged.
    val ring by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = motion.springOrTween(motion.springBouncy),
        label = "swatchRing",
    )
    val ringColor by animateColorAsState(
        targetValue = if (selected) scheme.content else Color.Transparent,
        animationSpec = motion.tweenFast(),
        label = "swatchRingColor",
    )

    Box(
        modifier = Modifier
            .semantics {
                contentDescription = label
            }
            .minimumTouchTarget()
            .focusRing(interactions, shape)
            .size(size)
            .clip(shape)
            .background(if (enabled) fill else fill.copy(alpha = 0.5f), shape)
            .border(
                width = Theme.sizing.borderWidth,
                // A pale swatch on a pale ground needs an edge, or the user is
                // picking from a row of invisible circles.
                color = if (selected) Color.Transparent else scheme.outline,
                shape = shape,
            )
            .selectable(
                selected = selected,
                interactionSource = interactions,
                indication = kontourIndication(shape),
                enabled = enabled,
                role = Role.RadioButton,
                onClick = {
                    feedback.perform(FeedbackIntent.Selection)
                    onSelectedChange()
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (ring > 0f) {
            Box(
                Modifier
                    .scale(ring)
                    .size(size)
                    .border(Theme.sizing.borderWidthStrong, ringColor, shape)
            )
        }

        when {
            selected -> Icon(
                imageVector = SystemIcons.Check,
                contentDescription = null,
                modifier = Modifier.scale(ring),
                tint = tick,
                size = Theme.sizing.iconMedium,
            )

            color == null && automaticIcon != null -> Icon(
                imageVector = automaticIcon,
                contentDescription = null,
                tint = if (enabled) scheme.contentMuted else scheme.contentDisabled,
                size = Theme.sizing.iconMedium,
            )
        }
    }
}
