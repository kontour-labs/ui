package io.kontour.ui.components.text

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.kontour.ui.foundation.Icon
import io.kontour.ui.foundation.Text
import io.kontour.ui.theme.Theme

/**
 * The frame every form control shares: label above, bordered box, helper or
 * error below.
 *
 * Extracted so a [Select] is not a careful re-creation of a [TextField] that
 * drifts from it on the next change. Anything that sits in a form and takes a
 * value goes through here, which is what makes a column of mixed controls line
 * up without per-call-site padding.
 *
 * @param frameModifier Applied to the bordered box, not the whole control —
 *   where a `clickable` belongs for a control that opens something, so the tap
 *   target is the field and not the label and helper text as well.
 * @param content Fills the box between the leading icon and the trailing slot.
 *   Give it `Modifier.weight(1f)` unless the control is meant to hug its value.
 */
@Composable
internal fun FieldScaffold(
    modifier: Modifier,
    enabled: Boolean,
    focused: Boolean,
    colors: TextFieldColors,
    metrics: TextFieldMetrics,
    shape: Shape,
    label: String? = null,
    supportingText: String? = null,
    errorMessage: String? = null,
    leadingIcon: ImageVector? = null,
    trailing: (@Composable () -> Unit)? = null,
    frameModifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val motion = Theme.motion
    val isError = errorMessage != null

    val borderColor by animateColorAsState(
        targetValue = colors.border(enabled, focused, isError),
        animationSpec = motion.tweenFast(),
        label = "fieldBorder",
    )
    val borderWidth by animateDpAsState(
        targetValue = if (focused || isError) {
            Theme.sizing.borderWidthStrong
        } else {
            Theme.sizing.borderWidth
        },
        animationSpec = motion.tweenFast(),
        label = "fieldBorderWidth",
    )
    val labelColor by animateColorAsState(
        targetValue = when {
            !enabled -> colors.contentDisabled
            isError -> colors.error
            focused -> colors.labelFocused
            else -> colors.label
        },
        animationSpec = motion.tweenFast(),
        label = "fieldLabel",
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (label != null) {
            Text(
                text = label,
                style = Theme.typography.labelMedium,
                color = labelColor,
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = metrics.minHeight)
                .clip(shape)
                .background(colors.container(enabled, focused), shape)
                .border(borderWidth, borderColor, shape)
                .then(frameModifier)
                .padding(
                    horizontal = metrics.horizontalPadding,
                    vertical = metrics.verticalPadding,
                ),
            horizontalArrangement = Arrangement.spacedBy(metrics.gap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = if (enabled) colors.label else colors.contentDisabled,
                    size = Theme.sizing.iconMedium,
                )
            }

            content()

            trailing?.invoke()
        }

        // Helper and error occupy the same slot and animate in place, so the
        // form does not jump by a line height every time validation flips.
        AnimatedVisibility(
            visible = errorMessage != null || supportingText != null,
            enter = fadeIn(motion.tweenFast()) + expandVertically(motion.tweenFast()),
            exit = fadeOut(motion.tweenFast()) + shrinkVertically(motion.tweenFast()),
        ) {
            Text(
                text = errorMessage ?: supportingText.orEmpty(),
                style = Theme.typography.bodySmall,
                color = if (isError) colors.error else colors.helper,
            )
        }
    }
}
