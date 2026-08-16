package io.kontour.ui.components.text

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.ui.semantics.clearAndSetSemantics
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
 * @param content Fills the box between the leading slots and the trailing slot.
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
    supporting: String? = null,
    errorMessage: String? = null,
    leadingIcon: ImageVector? = null,
    leading: (@Composable () -> Unit)? = null,
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
    // Animated for the same reason the border is: a ground that changes colour
    // between frames reads as a repaint, and one that fades reads as a response.
    val containerColor by animateColorAsState(
        targetValue = colors.container(enabled, focused),
        animationSpec = motion.tweenFast(),
        label = "fieldContainer",
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
                // Visible, but not a node of its own. Every control that goes
                // through this scaffold takes the label as its accessible *name*
                // — see `TextField` and `SelectFrame` — so leaving this
                // announceable would make a screen reader read "Origin" and then
                // "Origin, Perth, edit box", one field sounding like two things.
                // `ComponentContractTest.everyLabelledControlAnnouncesItsLabel`
                // is what stops this from silently losing the label instead.
                modifier = Modifier.clearAndSetSemantics {},
                style = Theme.typography.labelMedium,
                color = labelColor,
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = metrics.minHeight)
                .clip(shape)
                .background(containerColor, shape)
                .border(borderWidth, borderColor, shape)
                .then(frameModifier)
                // Each side is padded for what is actually on it. A glyph does
                // not fill its own box, so an icon padded like text reads as
                // further in than the text beside it — see
                // `TextFieldMetrics.iconPadding` for the measurement.
                .padding(
                    start = if (leading != null || leadingIcon != null) {
                        metrics.iconPadding
                    } else {
                        metrics.horizontalPadding
                    },
                    end = if (trailing != null) {
                        metrics.iconPadding
                    } else {
                        metrics.horizontalPadding
                    },
                    top = metrics.verticalPadding,
                    bottom = metrics.verticalPadding,
                ),
            horizontalArrangement = Arrangement.spacedBy(metrics.gap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            leading?.invoke()

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
            visible = errorMessage != null || supporting != null,
            enter = fadeIn(motion.tweenFast()) + expandVertically(motion.tweenFast()),
            exit = fadeOut(motion.tweenFast()) + shrinkVertically(motion.tweenFast()),
        ) {
            // Crossfaded, not swapped. The slot already animates open and
            // shut; what it did not do was change *between* two messages, so a
            // field that was showing a hint and then failed validation replaced
            // one sentence with another between frames — the one moment in the
            // form where the user most needs to notice something changed.
            AnimatedContent(
                targetState = errorMessage ?: supporting.orEmpty(),
                transitionSpec = {
                    fadeIn(motion.tweenFast()) togetherWith fadeOut(motion.tweenFast())
                },
                label = "fieldMessage",
            ) { message ->
                Text(
                    text = message,
                    style = Theme.typography.bodySmall,
                    color = if (isError) colors.error else colors.helper,
                )
            }
        }
    }
}
