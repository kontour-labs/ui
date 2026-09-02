package io.kontour.ui.components.selection

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.ui.unit.lerp
import io.kontour.ui.a11y.contentColourFor
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
 * ColourSwatchPicker(
 *     value = prefs.accent,
 *     options = AccentColour.entries,
 *     onValueChange = viewModel::updateAccent,
 *     swatchColour = { it.seed },
 *     swatchLabel = { it.displayName },
 * )
 * ```
 *
 * A grid of swatches rather than a dropdown of colour names, because the choice
 * being made is visual: "which of these do I like" is answered by looking, and a
 * list that shows one colour at a time makes the user open it six times.
 *
 * Selection reads the way it does on a [RadioButton], because it is the same
 * choice: the swatch's own outline takes the selected colour and thickens, and
 * the tick springs in. Pressing previews a third of the way, as every other
 * selection control in the library does.
 *
 * **The tick is drawn in whatever colour is legible on the swatch**, resolved
 * through `contentColourFor()`. A fixed white tick vanishes on pale yellow and a
 * fixed black one vanishes on navy, and a picker whose selection is invisible on
 * two of its own options is a picker with a bug in it.
 *
 * Every swatch carries [swatchLabel] as its content description and reports
 * `Role.RadioButton`. A colour with no name is unusable to anyone who cannot see
 * it — and to anyone who can, in a screenshot they are describing over the
 * phone.
 *
 * @param swatchColour The colour to draw. Return `null` for an option that is not
 *   a colour — a "match the system" entry — which renders as an outlined swatch
 *   with [automaticIcon] instead.
 */
@Composable
fun <T> ColourSwatchPicker(
    value: T?,
    options: List<T>,
    onValueChange: (T) -> Unit,
    swatchColour: (T) -> Color?,
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
                colour = swatchColour(option),
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
    colour: Color?,
    label: String,
    selected: Boolean,
    onSelectedChange: () -> Unit,
    enabled: Boolean,
    size: Dp,
    automaticIcon: ImageVector?,
) {
    val scheme = Theme.colours
    val motion = Theme.motion
    val feedback = LocalFeedback.current
    val interactions = remember { MutableInteractionSource() }
    val shape = Theme.shapes.pill

    val fill = colour ?: scheme.surfaceSunken
    val tick = if (colour != null) contentColourFor(colour) else scheme.content

    // The swatch used to grow a second ring out of its own centre, at full size
    // by the time it stopped — an animation nothing else in the library does,
    // on the one control where the thing being judged is the colour that ring
    // was expanding across. It is now the radio button's mechanism: the outline
    // takes the selected colour and thickens, and the mark springs in.
    val pressed by interactions.collectIsPressedAsState()
    val press = if (pressed && enabled) SelectionPressPreview else 0f
    val mark by animateFloatAsState(
        targetValue = if (selected) 1f - press else press,
        animationSpec = motion.springOrTween(motion.springBouncy),
        label = "swatchMark",
    )

    val ringColour by animateColorAsState(
        targetValue = when {
            !enabled -> scheme.contentDisabled
            selected -> scheme.content
            // A pale swatch on a pale ground needs an edge, or the user is
            // picking from a row of invisible circles.
            else -> scheme.outline
        },
        animationSpec = motion.tweenFast(),
        label = "swatchRing",
    )
    val ringWidth = lerp(
        Theme.sizing.borderWidth,
        Theme.sizing.borderWidthStrong,
        mark.coerceAtLeast(0f),
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
            .border(width = ringWidth, color = ringColour, shape = shape)
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
        // The two marks trade places on one number, so the automatic swatch does
        // not cut from its icon to a tick with a blank frame in between.
        if (colour == null && automaticIcon != null && mark < 1f) {
            Icon(
                imageVector = automaticIcon,
                contentDescription = null,
                modifier = Modifier.scale((1f - mark).coerceAtLeast(0f)),
                tint = if (enabled) scheme.contentMuted else scheme.contentDisabled,
                size = Theme.sizing.iconMedium,
            )
        }
        if (mark > 0f) {
            Icon(
                imageVector = SystemIcons.Check,
                contentDescription = null,
                modifier = Modifier.scale(mark),
                tint = tick,
                size = Theme.sizing.iconMedium,
            )
        }
    }
}
