package io.kontour.ui.components.selection

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.remember
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
 * ### It has a floor, and it is wider than most controls'
 *
 * Two touch targets and a value cell between them, so a `Stepper` wants about
 * [StepperDefaults.MinWidth] and cannot usefully be given less. Below that a
 * `Row` hands the first button everything and the second one nothing, so the
 * increment button stops being drawn at all — a control that has silently lost
 * half of what it does, which is worse than one that is visibly too big for its
 * space. Every alternative is worse again: shrinking the buttons puts them under
 * the platform's touch minimum, and dropping the value leaves two unlabelled
 * arrows. Give it the room, or use a
 * [Select][io.kontour.ui.components.text.Select] of the plausible counts.
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

    // The value cell is as wide as the *widest value this stepper can show*,
    // not as wide as the value it is showing.
    //
    // A fixed minimum is not enough once `format` puts words in: "1 bag" is
    // narrower than "2 bags", so incrementing pushed the `+` button sideways and
    // the whole control twitched. Measuring the candidates is the only way to
    // reserve the right amount, because only `format` knows how wide a value
    // gets.
    //
    // Sampled rather than exhaustive: a range of 1..9 is nine strings, but
    // nothing stops a caller passing 0..100000. The ends and each digit
    // boundary between them cover the two things that actually change the
    // width — the number of digits, and singular versus plural at the bottom of
    // the range.
    val valueStyle = Theme.typography.titleMedium
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val widestValue = remember(range, step, valueStyle, density, measurer, format) {
        val candidates = buildSet {
            add(range.first)
            add(range.last)
            add((range.first + step).coerceIn(range))
            var boundary = 9
            while (boundary < range.last) {
                if (boundary >= range.first) add(boundary)
                add((boundary + 1).coerceIn(range))
                boundary = boundary * 10 + 9
            }
        }
        with(density) {
            candidates.maxOf { measurer.measure(format(it), valueStyle).size.width }.toDp()
        }
    }

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
            style = valueStyle,
            textAlign = TextAlign.Center,
            // Cleared rather than merged: the row above already announces the
            // value as its state, and a bare "2" read out between two buttons
            // is a node with no meaning of its own.
            modifier = Modifier
                .widthIn(min = maxOf(valueWidth, widestValue))
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

    /**
     * The narrowest a stepper can be drawn correctly.
     *
     * Two 48dp touch targets, [ValueWidth] between them and the gaps either
     * side. Not enforced — a component that refuses its own constraints is its
     * own kind of problem — but stated, because below it the layout stops being
     * wrong in a way anyone can see and starts being wrong by leaving a button
     * out. `ComponentSpec.minWidth` carries the same number for the width sweep.
     */
    val MinWidth: Dp = 144.dp
}
