package io.kontour.ui.components.action

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.indication
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import io.kontour.ui.a11y.minimumTouchTarget
import io.kontour.ui.components.display.Spinner
import io.kontour.ui.foundation.LocalContentColour
import io.kontour.ui.foundation.ProvideTextStyle
import io.kontour.ui.foundation.RowContentScope
import io.kontour.ui.foundation.contentScope
import io.kontour.ui.foundation.Text
import io.kontour.ui.input.focusRing
import io.kontour.ui.input.pointerCursor
import io.kontour.ui.interaction.Feedback
import io.kontour.ui.interaction.FeedbackIntent
import io.kontour.ui.interaction.kontourIndication
import androidx.compose.ui.graphics.graphicsLayer
import io.kontour.ui.theme.Theme

/**
 * A button.
 *
 * ```
 * Button(onClick = ::planTrip) { +"Plan a trip" }
 *
 * Button(
 *     onClick = ::deleteFavourite,
 *     variant = ButtonVariant.Destructive,
 *     size = ButtonSize.Small,
 * ) {
 *     +Tabler.Outline.Trash
 *     +"Delete"
 * }
 * ```
 *
 * Choose [variant] by how important the action is, not by how you want it to
 * look — see [ButtonVariant]. Choose [size] by how dense the surroundings are.
 *
 * [loading] swaps the label for a spinner **without changing the button's
 * width**, so a row of buttons does not reflow the moment one is pressed.
 *
 * @param onClick Fired on click. Not fired when [enabled] is false or [loading]
 *   is true.
 * @param loading Shows a spinner in place of the content and blocks input.
 * @param loadingLabel What a screen reader announces while [loading]. Defaults
 *   to "Loading"; pass something specific where you can ("Planning your trip").
 */
@Composable
fun Button(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    variant: ButtonVariant = ButtonVariant.Primary,
    size: ButtonSize = ButtonSize.Medium,
    loading: Boolean = false,
    loadingLabel: String = Theme.strings.loading,
    shape: Shape = Theme.shapes.control,
    colours: ButtonColours = ButtonDefaults.colours(variant),
    metrics: ButtonMetrics = ButtonDefaults.metrics(size),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowContentScope.() -> Unit
) {
    val interactions = interactionSource ?: remember { MutableInteractionSource() }
    val interactive = enabled && !loading
    val motion = Theme.motion
    val feedback = Feedback

    val container by animateColorAsState(
        targetValue = colours.container(enabled),
        animationSpec = motion.tweenFast(),
        label = "buttonContainer",
    )
    val contentColour by animateColorAsState(
        targetValue = colours.content(enabled),
        animationSpec = motion.tweenFast(),
        label = "buttonContent",
    )
    val borderColour = colours.border(enabled)

    Row(
        modifier = modifier
            .semantics(mergeDescendants = true) {
                role = Role.Button
                if (loading) stateDescription = loadingLabel
            }
            .minimumTouchTarget()
            .focusRing(interactions, shape)
            // Ahead of the container, not inside `clickable` below.
            //
            // `KontourIndication` scales `drawContent()`, and a draw modifier
            // only draws what comes *after* it in the chain — so an indication
            // handed to `clickable`, which sits past `.background()`, was
            // scaling the label and nothing else. The button's own silhouette
            // never moved: measured at 212px pressed and 212px at rest. Every
            // filled control in the library has been "shrinking on press" by
            // shrinking its text inside a container that stayed put, which is
            // most of why 3% never read as anything.
            .indication(
                interactions,
                kontourIndication(shape, ButtonDefaults.pressScale(variant, size)),
            )
            .height(metrics.height)
            .clip(shape)
            .background(container, shape)
            .then(
                if (borderColour != null) {
                    Modifier.border(BorderStroke(Theme.sizing.borderWidthStrong, borderColour), shape)
                } else {
                    Modifier
                }
            )
            .pointerCursor(enabled = interactive)
            .clickable(
                interactionSource = interactions,
                indication = null,
                enabled = interactive,
                role = Role.Button,
                onClick = {
                    feedback.perform(
                        if (variant == ButtonVariant.Destructive) {
                            FeedbackIntent.Reject
                        } else {
                            FeedbackIntent.Selection
                        }
                    )
                    onClick()
                },
            )
            .padding(horizontal = metrics.horizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(metrics.gap, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalContentColour provides contentColour) {
            ProvideTextStyle(metrics.textStyle) {
                ButtonContent(
                    loading = loading,
                    metrics = metrics,
                    content = content,
                )
            }
        }
    }
}

/**
 * Swaps between the label and the spinner.
 *
 * The arrangement of the label is this component's own; the exchange is
 * [LoadingSwap], shared with every other control that can be `loading`.
 */
@Composable
private fun RowScope.ButtonContent(
    loading: Boolean,
    metrics: ButtonMetrics,
    content: @Composable RowContentScope.() -> Unit
) {
    LoadingSwap(loading = loading, spinnerSize = metrics.iconSize) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(metrics.gap, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            contentScope(iconSize = metrics.iconSize, content = content)
        }
    }
}

