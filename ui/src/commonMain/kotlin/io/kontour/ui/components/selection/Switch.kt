package io.kontour.ui.components.selection

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import io.kontour.ui.a11y.contentColorFor
import io.kontour.ui.a11y.minimumTouchTarget
import io.kontour.ui.input.focusRing
import io.kontour.ui.interaction.Feedback
import io.kontour.ui.interaction.FeedbackIntent
import io.kontour.ui.interaction.LocalRowInteractionSource
import io.kontour.ui.theme.Theme

private val TrackWidth = 48.dp
private val TrackHeight = 28.dp
private val ThumbSize = 22.dp
private val ThumbPadding = 3.dp

/**
 * A switch.
 *
 * ```
 * Switch(checked = liveVehicles, onCheckedChange = viewModel::setLiveVehicles)
 * ```
 *
 * Use a switch for a setting that takes effect *immediately*, and a
 * [Checkbox] for one that is part of a form and takes effect on submit. The
 * distinction matters: a user who flips a switch expects the thing to have
 * happened, and a user who ticks a box expects to press Save.
 *
 * The thumb stretches as it travels — wider mid-flight, round at rest — the way
 * a physical toggle would if it had any give in it. It costs one animated value
 * and it is what stops the control feeling like a rectangle sliding in a slot.
 * Under reduced motion the thumb simply moves.
 *
 * The track colour uses [io.kontour.ui.theme.ColorScheme.primary] when on and
 * a bordered, unfilled track when off, rather than a light grey fill. The
 * marketing site's own notes call this out: a grey track sits too close in tone
 * to the surfaces it is toggled on top of to read as a distinct control.
 */
@Composable
fun Switch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
) {
    val interactions = interactionSource ?: remember { MutableInteractionSource() }
    val colors = Theme.colors
    val motion = Theme.motion
    val shape = Theme.shapes.pill
    val feedback = Feedback

    // A switch inside a `SelectionRow` has no callback of its own — the row owns
    // the tap — so its own source never sees a press and the thumb never
    // stretched when the row was tapped. Borrow the row's, but only when this
    // switch is genuinely a passenger: an explicit source or a callback of its
    // own both mean it is the target.
    val row = LocalRowInteractionSource.current
    val pressSource: InteractionSource =
        if (onCheckedChange == null && interactionSource == null && row != null) row else interactions
    val pressed by pressSource.collectIsPressedAsState()

    val stroke = selectionStroke(enabled)

    val trackColor by animateColorAsState(
        targetValue = when {
            !enabled && checked -> colors.contentDisabled
            !enabled -> Color.Transparent
            checked -> colors.primary
            else -> Color.Transparent
        },
        animationSpec = motion.tweenFast(),
        label = "switchTrack",
    )
    val trackBorder by animateColorAsState(
        targetValue = when {
            !enabled -> colors.contentDisabled
            checked -> colors.primary
            else -> colors.outlineStrong
        },
        animationSpec = motion.tweenFast(),
        label = "switchTrackBorder",
    )
    val thumbColor by animateColorAsState(
        targetValue = when {
            !enabled && checked -> contentColorFor(colors.contentDisabled)
            !enabled -> colors.contentDisabled
            checked -> colors.onPrimary
            else -> colors.outlineStrong
        },
        animationSpec = motion.tweenFast(),
        label = "switchThumb",
    )

    val thumbOffset by animateDpAsState(
        targetValue = if (checked) TrackWidth - ThumbSize - ThumbPadding else ThumbPadding,
        animationSpec = motion.springOrTween(motion.springSnappy),
        label = "switchThumbOffset",
    )
    // Squash-and-stretch: the thumb elongates while pressed or in transit.
    val thumbStretch by animateFloatAsState(
        targetValue = if (pressed && !motion.reduceMotion) 1.25f else 1f,
        animationSpec = motion.springOrTween(motion.springBouncy),
        label = "switchThumbStretch",
    )

    Canvas(
        modifier = modifier
            .minimumTouchTarget()
            .focusRing(interactions, shape)
            .then(
                if (onCheckedChange != null) {
                    Modifier.toggleable(
                        value = checked,
                        onValueChange = {
                            feedback.perform(FeedbackIntent.Selection)
                            onCheckedChange(it)
                        },
                        enabled = enabled,
                        role = Role.Switch,
                        interactionSource = interactions,
                        indication = null,
                    )
                } else {
                    // Not interactive, but still *state*. A switch handed a null
                    // callback is showing what a row decided, and if it says
                    // nothing a screen reader reads the row as a button with a
                    // name and no on or off. The row publishes this too when it
                    // is `toggleable`; the same value merged twice is harmless,
                    // and a `SettingRow` — which is `clickable`, not
                    // `toggleable` — publishes nothing at all without it.
                    Modifier.semantics {
                        role = Role.Switch
                        toggleableState = ToggleableState(checked)
                    }
                }
            )
            .size(width = TrackWidth, height = TrackHeight)
    ) {
        val strokeWidth = stroke.toPx()
        val trackRadius = size.height / 2f

        drawRoundRect(
            color = trackColor,
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackRadius),
        )
        drawRoundRect(
            color = trackBorder,
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackRadius),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth),
        )

        val thumbPx = ThumbSize.toPx()
        val stretchedWidth = thumbPx * thumbStretch
        // Stretch away from the direction of travel, so the leading edge holds
        // its position and the trailing edge lags — which is what reads as give.
        val left = if (checked) {
            thumbOffset.toPx() - (stretchedWidth - thumbPx)
        } else {
            thumbOffset.toPx()
        }
        val top = (size.height - thumbPx) / 2f

        drawRoundRect(
            color = thumbColor,
            topLeft = Offset(left.coerceIn(0f, size.width - stretchedWidth), top),
            size = Size(stretchedWidth, thumbPx),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(thumbPx / 2f),
        )
    }
}
