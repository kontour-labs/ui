package io.kontour.ui.components.selection

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.triStateToggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.kontour.ui.a11y.minimumTouchTarget
import io.kontour.ui.input.focusRing
import io.kontour.ui.interaction.Feedback
import io.kontour.ui.interaction.FeedbackIntent
import io.kontour.ui.theme.Theme

/** The box's drawn size. The touch target around it is much larger. */
private val CheckboxSize = 20.dp

/**
 * A checkbox.
 *
 * ```
 * Checkbox(checked = notifyOnDelay, onCheckedChange = viewModel::setNotifyOnDelay)
 * ```
 *
 * For a checkbox with a label, use [SelectionRow] — it makes the whole row
 * tappable, which is what users expect and what gives the control a target
 * worth aiming at.
 *
 * The tick is drawn on a `Canvas` and *strokes itself on* rather than fading in,
 * with the box springing up to meet it. Two frames of personality on a control
 * people tap dozens of times a session.
 *
 * @param onCheckedChange Pass `null` to make the checkbox non-interactive while
 *   still showing state — for a row whose click is handled by a parent.
 */
@Composable
fun Checkbox(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
) {
    TriStateCheckbox(
        state = ToggleableState(checked),
        onClick = onCheckedChange?.let { { it(!checked) } },
        modifier = modifier,
        enabled = enabled,
        interactionSource = interactionSource,
    )
}

/**
 * A checkbox with three states: checked, unchecked, and indeterminate.
 *
 * [ToggleableState.Indeterminate] is for a parent whose children are partly
 * selected — "select all" over a mixed list. It draws a dash rather than a tick.
 * Clicking an indeterminate checkbox should select everything, not clear it;
 * that is the caller's decision, and the common wrong answer.
 */
@Composable
fun TriStateCheckbox(
    state: ToggleableState,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
) {
    val interactions = interactionSource ?: remember { MutableInteractionSource() }
    val colors = Theme.colors
    val motion = Theme.motion
    val shape = Theme.shapes.extraSmall
    val feedback = Feedback

    val selected = state != ToggleableState.Off

    val stroke = selectionStroke(enabled)

    val container by animateColorAsState(
        targetValue = when {
            !enabled && selected -> colors.contentDisabled
            !enabled -> Color.Transparent
            selected -> colors.primary
            else -> Color.Transparent
        },
        animationSpec = motion.tweenFast(),
        label = "checkboxContainer",
    )
    val border by animateColorAsState(
        targetValue = when {
            !enabled -> colors.contentDisabled
            selected -> colors.primary
            else -> colors.outlineStrong
        },
        animationSpec = motion.tweenFast(),
        label = "checkboxBorder",
    )
    // The mark strokes on: 0 to 1 is the fraction of the tick path drawn.
    val markProgress by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = motion.springOrTween(motion.springSnappy),
        label = "checkboxMark",
    )
    // The box itself springs up to meet the tick — the bounce lands on the way
    // *in*, which is the one place an overshoot on arrival is right.
    val boxScale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.92f,
        animationSpec = motion.springOrTween(motion.springBouncy),
        label = "checkboxScale",
    )

    Canvas(
        modifier = modifier
            .minimumTouchTarget()
            .focusRing(interactions, shape)
            .then(
                if (onClick != null) {
                    Modifier.triStateToggleable(
                        state = state,
                        onClick = {
                            feedback.perform(FeedbackIntent.Selection)
                            onClick()
                        },
                        enabled = enabled,
                        role = Role.Checkbox,
                        interactionSource = interactions,
                        // The box is 20dp; a ripple or wash across a 48dp target
                        // would paint well outside the control.
                        indication = null,
                    )
                } else {
                    // Not interactive, but still *state*, which is what the
                    // `@param` above promises. A box handed a null callback is
                    // showing what a row decided, and a row that is `clickable`
                    // rather than `toggleable` — every `SettingRow` — publishes
                    // no checked state of its own, so without this a screen
                    // reader reads the row as a button with a name and no tick.
                    // `SelectionRow` publishes it too; the same value merged
                    // twice is harmless.
                    Modifier.semantics {
                        role = Role.Checkbox
                        toggleableState = state
                    }
                }
            )
            .size(CheckboxSize)
    ) {
        scale(boxScale) {
            val outline = shape.createOutline(size, layoutDirection, this)
            val strokeWidth = stroke.toPx()

            drawOutline(outline = outline, color = container)
            drawOutline(
                outline = outline,
                color = border,
                style = Stroke(width = strokeWidth),
            )

            if (markProgress > 0f) {
                drawMark(
                    state = state,
                    progress = markProgress,
                    color = if (enabled) colors.onPrimary else colors.surface,
                    strokeWidth = strokeWidth,
                )
            }
        }
    }
}

/**
 * Draws the tick or dash, revealed [progress] of the way along its path.
 *
 * The tick is two segments: a short down-stroke and a long up-stroke. Revealing
 * them in order is what makes it read as being *drawn* rather than appearing.
 */
private fun DrawScope.drawMark(
    state: ToggleableState,
    progress: Float,
    color: Color,
    strokeWidth: Float,
) {
    val w = size.width
    val h = size.height

    if (state == ToggleableState.Indeterminate) {
        val inset = w * 0.25f
        val y = h / 2f
        val halfLength = (w / 2f - inset) * progress
        drawLine(
            color = color,
            start = Offset(w / 2f - halfLength, y),
            end = Offset(w / 2f + halfLength, y),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
        return
    }

    val start = Offset(w * 0.24f, h * 0.52f)
    val elbow = Offset(w * 0.43f, h * 0.70f)
    val end = Offset(w * 0.76f, h * 0.32f)

    // Segment lengths, so the reveal moves at a constant speed across the elbow
    // instead of racing through the short leg.
    val firstLength = (elbow - start).getDistance()
    val secondLength = (end - elbow).getDistance()
    val total = firstLength + secondLength
    val drawn = total * progress

    val path = Path().apply {
        moveTo(start.x, start.y)
        if (drawn <= firstLength) {
            val t = if (firstLength == 0f) 0f else drawn / firstLength
            lineTo(start.x + (elbow.x - start.x) * t, start.y + (elbow.y - start.y) * t)
        } else {
            lineTo(elbow.x, elbow.y)
            val t = if (secondLength == 0f) 0f else (drawn - firstLength) / secondLength
            lineTo(elbow.x + (end.x - elbow.x) * t, elbow.y + (end.y - elbow.y) * t)
        }
    }

    drawPath(
        path = path,
        color = color,
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}

/** The drawn size of a [Checkbox], for callers laying out around one. */
val CheckboxVisualSize: Dp = CheckboxSize
