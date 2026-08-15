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

/**
 * A button.
 *
 * ```
 * Button(onClick = ::planTrip) {
 *     Text("Plan a trip")
 * }
 *
 * Button(
 *     onClick = ::deleteFavourite,
 *     variant = ButtonVariant.Destructive,
 *     size = ButtonSize.Small,
 * ) {
 *     Icon(Icons.Trash, contentDescription = null)
 *     Text("Delete")
 * }
 * ```
 *
 * Choose [variant] by how important the action is, not by how you want it to
 * look — see [ButtonVariant]. Choose [size] by how dense the surroundings are.
 *
 * ### Loading
 *
 * Setting [loading] swaps the label for a spinner **without changing the
 * button's width**, so a row of buttons does not reflow the moment one is
 * pressed. The button is also non-interactive while loading, and announces
 * itself as busy — so a screen-reader user is not left tapping a control that
 * has already accepted their input.
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
    loadingLabel: String = "Loading",
    shape: Shape = Theme.shapes.small,
    colors: ButtonColors = ButtonDefaults.colors(variant),
    metrics: ButtonMetrics = ButtonDefaults.metrics(size),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowContentScope.() -> Unit
) {
    val interactions = interactionSource ?: remember { MutableInteractionSource() }
    val interactive = enabled && !loading
    val motion = Theme.motion
    val feedback = Feedback

    val container by animateColorAsState(
        targetValue = colors.container(enabled),
        animationSpec = motion.tweenFast(),
        label = "buttonContainer",
    )
    val contentColor by animateColorAsState(
        targetValue = colors.content(enabled),
        animationSpec = motion.tweenFast(),
        label = "buttonContent",
    )
    val borderColor = colors.border(enabled)

    Row(
        modifier = modifier
            .semantics(mergeDescendants = true) {
                role = Role.Button
                if (loading) stateDescription = loadingLabel
            }
            .minimumTouchTarget()
            .focusRing(interactions, shape)
            .height(metrics.height)
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
        CompositionLocalProvider(LocalContentColor provides contentColor) {
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
 * The label stays composed but invisible underneath while loading, which is what
 * holds the button's width steady. Cross-fading alone would let the button
 * collapse to spinner width and shove its neighbours sideways.
 */
@Composable
private fun RowScope.ButtonContent(
    loading: Boolean,
    metrics: ButtonMetrics,
    content: @Composable RowContentScope.() -> Unit
) {
    val motion = Theme.motion

    Box(contentAlignment = Alignment.Center) {
        val labelAlpha by animateFloatAsState(
            targetValue = if (loading) 0f else 1f,
            animationSpec = motion.tweenFast(),
            label = "buttonLabelAlpha",
        )

        Row(
            modifier = Modifier
                .alpha(labelAlpha)
                .then(if (loading) Modifier.clearAndSetSemantics { } else Modifier),
            horizontalArrangement = Arrangement.spacedBy(metrics.gap, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            contentScope(iconSize = metrics.iconSize, content = content)
        }

        AnimatedContent(
            targetState = loading,
            transitionSpec = {
                // The spinner pops in rather than fading — a slightly overscaled
                // entrance is the difference between "something is happening"
                // and "something appeared".
                (fadeIn(motion.tweenFast()) + scaleIn(motion.tweenFast(), initialScale = 0.6f))
                    .togetherWith(fadeOut(motion.tweenFast()) + scaleOut(motion.tweenFast(), targetScale = 0.6f))
            },
            label = "buttonSpinner",
        ) { isLoading ->
            if (isLoading) {
                Spinner(
                    modifier = Modifier.size(metrics.iconSize),
                    color = LocalContentColor.current,
                )
            } else {
                Box(Modifier.size(0.dp))
            }
        }
    }
}

