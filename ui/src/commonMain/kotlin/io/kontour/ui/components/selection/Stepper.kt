package io.kontour.ui.components.selection

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.action.ButtonSize
import io.kontour.ui.components.action.ButtonVariant
import io.kontour.ui.components.action.IconButton
import io.kontour.ui.foundation.SystemIcons
import io.kontour.ui.foundation.Text
import io.kontour.ui.interaction.Feedback
import io.kontour.ui.interaction.FeedbackIntent
import io.kontour.ui.theme.Theme

/**
 * A bounded number with a button at each end — passengers, bikes, party size.
 *
 * ```kotlin
 * Stepper(
 *     value = adults,
 *     onValueChange = { adults = it },
 *     range = 1..9,
 *     contentDescription = "Adults",
 * )
 * ```
 *
 * **Reach for a [Slider] instead** when the number is approximate and the range
 * is wide. A stepper is for a count someone knows exactly and will change by one
 * or two; nobody taps `+` thirty times.
 *
 * The buttons disable individually at each end of [range] rather than the whole
 * control disabling or the value silently refusing to move. A `+` that looks
 * live and does nothing is the defect this shape exists to avoid — and it is
 * announced, not just drawn, because "unavailable" is the thing a screen-reader
 * user cannot see greyed out.
 *
 * The value between them is **not** a separate node. It is folded into the
 * control's `stateDescription`, so a screen reader says "Adults, 2" rather than
 * offering an unlabelled "2" between two buttons.
 *
 * @param range The inclusive bounds. `value` outside it is clamped for display,
 *   which keeps a bad initial value visible rather than silently corrected.
 * @param step How much each press moves the value.
 * @param contentDescription Names the whole control. Required: `+` and `−` say
 *   nothing about what is being counted.
 * @param format Renders the number. For units — "2 bags", "£4.50".
 */
@Composable
fun Stepper(
    value: Int,
    onValueChange: (Int) -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    range: IntRange = 0..99,
    step: Int = 1,
    size: ButtonSize = ButtonSize.Medium,
    valueWidth: Dp = StepperDefaults.ValueWidth,
    format: (Int) -> String = { it.toString() },
    decrementLabel: String = Theme.strings.decrease,
    incrementLabel: String = Theme.strings.increase,
    interactionSource: MutableInteractionSource? = null,
) {
    val shown = value.coerceIn(range)
    val canDecrement = enabled && shown - step >= range.first
    val canIncrement = enabled && shown + step <= range.last
    val feedback = Feedback

    Row(
        modifier = modifier.semantics {
            this.contentDescription = contentDescription
            stateDescription = format(shown)
            if (!enabled) disabled()
        },
        horizontalArrangement = Arrangement.spacedBy(Theme.spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            icon = SystemIcons.Dash,
            contentDescription = decrementLabel,
            onClick = {
                feedback.perform(FeedbackIntent.Tick)
                onValueChange((shown - step).coerceIn(range))
            },
            enabled = canDecrement,
            variant = ButtonVariant.Tertiary,
            size = size,
            interactionSource = interactionSource,
        )

        Text(
            text = format(shown),
            style = Theme.typography.titleMedium,
            textAlign = TextAlign.Center,
            // Cleared rather than merged: the row above already announces the
            // value as its state, and a bare "2" read out between two buttons
            // is a node with no meaning of its own.
            modifier = Modifier
                .widthIn(min = valueWidth)
                .clearAndSetSemantics {},
        )

        IconButton(
            icon = SystemIcons.Plus,
            contentDescription = incrementLabel,
            onClick = {
                feedback.perform(FeedbackIntent.Tick)
                onValueChange((shown + step).coerceIn(range))
            },
            enabled = canIncrement,
            variant = ButtonVariant.Tertiary,
            size = size,
        )
    }
}

object StepperDefaults {
    /**
     * The floor for the number between the buttons.
     *
     * Wide enough for two digits, so a stepper does not change width as it
     * crosses 9 and shove whatever is beside it sideways. It is a minimum
     * rather than a fixed size, so "12 bags" still fits.
     */
    val ValueWidth: Dp = 40.dp
}
