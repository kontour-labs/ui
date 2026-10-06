package io.kontour.ui.components.datetime

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.error
import io.kontour.ui.components.text.TextFieldDefaults
import io.kontour.ui.motion.enterOrFade
import io.kontour.ui.motion.exitOrFade
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import io.kontour.ui.locale.TimePart
import io.kontour.ui.locale.HourCycle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.runtime.CompositionLocalProvider
import io.kontour.ui.locale.LocaleFormats
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import io.kontour.ui.a11y.contrastEdge
import io.kontour.ui.foundation.Surface
import io.kontour.ui.foundation.Text
import io.kontour.ui.input.focusRing
import io.kontour.ui.input.pointerCursor
import io.kontour.ui.interaction.kontourIndication
import io.kontour.ui.theme.Theme
import kotlinx.datetime.LocalTime

/**
 * Picks a time of day.
 *
 * Three wheels — hour, minute, and a day period when the reader's clock has one —
 * rather than a clock dial. For a transit app the value is almost always being
 * *adjusted* ("leave at 8:15 instead of 8:00"), and a wheel gets there in one
 * flick where a dial needs two precise drags.
 *
 * ```
 * TimePicker(value = departAt, onValueChange = viewModel::setDepartAt)
 * ```
 *
 * The clock comes from [Theme.formats] — 24-hour, 12-hour, or Japan's 0–11 — and
 * so do the order of the wheels, what sits between the hour and the minute, and
 * the digits: `오후 2:05`, `14.05`, `٢:٠٥ م`. It follows the platform setting
 * rather than being a parameter every call site has to remember to pass.
 *
 * @param enabled Whether the wheels can be turned. Disabled, the time is shown
 *   in the disabled content colour and answers nothing.
 * @param minuteStep Coarsens the minute wheel. A planner rarely wants
 *   minute-level precision, and 5 turns 60 rows into 12.
 * @param onValueChangeFinished Called when a wheel comes to rest after the
 *   reader turned it. [onValueChange] follows every row a wheel passes; what
 *   should happen once — a save, a request — belongs here. Not called when
 *   [value] is set by the caller.
 */
@Composable
fun TimePicker(
    value: LocalTime,
    onValueChange: (LocalTime) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    minuteStep: Int = 1,
    formats: LocaleFormats = Theme.formats,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    require(minuteStep in 1..30) { "minuteStep must divide an hour sensibly" }

    val minutes = remember(minuteStep) { (0 until 60 step minuteStep).toList() }
    val cycle = formats.conventions.hourCycle
    val layout = formats.timeLayout
    // The numbers the clock shows: 0–23, 1–12, 0–11 or 1–24.
    val hours = remember(cycle) {
        when (cycle) {
            HourCycle.H23 -> (0..23).toList()
            HourCycle.H12 -> (1..12).toList()
            HourCycle.H11 -> (0..11).toList()
            HourCycle.H24 -> (1..24).toList()
        }
    }

    val displayHour = when (cycle) {
        HourCycle.H23 -> value.hour
        HourCycle.H12 -> (value.hour % 12).let { if (it == 0) 12 else it }
        HourCycle.H11 -> value.hour % 12
        HourCycle.H24 -> if (value.hour == 0) 24 else value.hour
    }
    val hourIndex = hours.indexOf(displayHour).coerceAtLeast(0)
    val minuteIndex = minutes.indexOfFirst { it >= value.minute }.coerceAtLeast(0)
    val isPm = value.hour >= 12

    fun emit(hourValue: Int = displayHour, minute: Int = value.minute, pm: Boolean = isPm) {
        val hour24 = when (cycle) {
            HourCycle.H23 -> hourValue
            HourCycle.H24 -> hourValue % 24
            HourCycle.H11 -> hourValue + if (pm) 12 else 0
            HourCycle.H12 -> (hourValue % 12) + if (pm) 12 else 0
        }
        onValueChange(LocalTime(hour24.coerceIn(0, 23), minute.coerceIn(0, 59)))
    }

    val strings = Theme.strings
    // The locale's own words, and the same ones the time is written with: the
    // wheel said `AM` beside a field that said `2:05 pm`.
    val periods = remember(formats) { listOf(formats.dayPeriod(LocalTime(0, 0)), formats.dayPeriod(LocalTime(12, 0))) }
    // In the reader's digits, padded as their clock pads: `09`, `9`, `٠٩`.
    val zero = formats.symbols.zeroDigit
    val hourLabel: (Int) -> String = remember(formats, layout) {
        { hour -> formats.integer(hour).let { if (layout.hourPadded) it.padStart(2, zero) else it } }
    }
    val minuteLabel: (Int) -> String = remember(formats) { { minute -> formats.integer(minute).padStart(2, zero) } }
    val muted = if (enabled) Theme.colours.contentMuted else Theme.colours.contentDisabled

    // The hour and minute read left to right in every language — a clock's
    // numbers are a number, not words — so they are laid out as one
    // left-to-right group, and only the day period takes the line's direction.
    val clock = @Composable {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                WheelPicker(
                    items = hours,
                    selectedIndex = hourIndex,
                    onSelectedIndexChange = { emit(hourValue = hours[it]) },
                    itemLabel = hourLabel,
                    modifier = Modifier.width(72.dp),
                    enabled = enabled,
                    contentDescription = strings.hour,
                    onSelectedIndexChangeFinished = onValueChangeFinished,
                )

                Text(layout.separator, style = Theme.typography.headlineSmall, colour = muted)

                WheelPicker(
                    items = minutes,
                    selectedIndex = minuteIndex,
                    onSelectedIndexChange = { emit(minute = minutes[it]) },
                    itemLabel = minuteLabel,
                    modifier = Modifier.width(72.dp),
                    enabled = enabled,
                    contentDescription = strings.minute,
                    onSelectedIndexChangeFinished = onValueChangeFinished,
                )
            }
        }
    }

    // Wide enough for the longer of the two words, and never narrower than it
    // was for `AM`: `முற்பகல்` is not going to fit in 64dp.
    val measurer = rememberTextMeasurer()
    val periodStyle = Theme.typography.titleLarge
    val density = LocalDensity.current
    val periodWidth = remember(periods, periodStyle, density) {
        val widest = periods.maxOf { measurer.measure(it, periodStyle).size.width }
        with(density) { maxOf(PeriodWheelWidth, widest.toDp() + PeriodWheelPadding) }
    }

    // A third wheel, not a segmented control below.
    //
    // AM/PM is part of the time, and the picker reads as one instrument
    // when every column of it works the same way. Underneath as a
    // segmented control it was a second control the thumb had to travel
    // to, in a different idiom, deciding something the two wheels above
    // it were already deciding half of.
    //
    // Two items on a five-row wheel is mostly empty space, so this one
    // shows three rows. Before the time or after it, as the locale writes
    // it: `2:05 pm`, but `오후 2:05`.
    val period = @Composable { first: Boolean ->
        WheelPicker(
            items = periods,
            selectedIndex = if (isPm) 1 else 0,
            onSelectedIndexChange = { emit(pm = it == 1) },
            itemLabel = { it },
            modifier = Modifier
                .padding(start = if (first) 0.dp else Theme.spacing.sm, end = if (first) Theme.spacing.sm else 0.dp)
                .width(periodWidth),
            enabled = enabled,
            contentDescription = strings.dayPeriod,
            onSelectedIndexChangeFinished = onValueChangeFinished,
            visibleItems = 3,
        )
    }
    val periodFirst = layout.parts.indexOf(TimePart.DayPeriod).let { it >= 0 && it < layout.parts.indexOf(TimePart.Hour) }

    Column(modifier) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (cycle.usesDayPeriod && periodFirst) period(true)
            clock()
            if (cycle.usesDayPeriod && !periodFirst) period(false)
        }
    }
}

/**
 * The chosen time, shown as a tappable field.
 *
 * The thing you put on a form; tapping it is expected to open a [TimePicker] in
 * a sheet. Kept separate from the picker so the picker never has to know how it
 * is being presented. [DateField] and [DateTimeField] are the same field for a
 * [DatePicker] and a [DateTimePicker].
 *
 * @param supporting A line under the field, as a [DateField]'s.
 * @param errorMessage In place of [supporting], in the error colour, as a
 *   [DateField]'s.
 */
@Composable
fun TimeField(
    value: LocalTime,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: String? = null,
    supporting: String? = null,
    errorMessage: String? = null,
    formats: LocaleFormats = Theme.formats,
    interactionSource: MutableInteractionSource? = null,
) {
    PickerField(
        text = formats.time(value),
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        label = label,
        interactionSource = interactionSource,
        supporting = supporting,
        errorMessage = errorMessage,
    )
}

/**
 * A chosen value shown as a field that opens its picker: [TimeField],
 * [DateField] and [DateTimeField] are this, with their own words.
 *
 * @param text What is chosen, as the field shows it; `null` for nothing yet,
 *   when [placeholder] is shown instead.
 * @param supporting A line under the field, as a text field's: what the value is
 *   for, or what it has to be.
 * @param errorMessage In place of [supporting], in the error colour, with the
 *   field's edge in it too; and said with the field, as a text field's is.
 */
@Composable
internal fun PickerField(
    text: String?,
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    label: String?,
    interactionSource: MutableInteractionSource?,
    placeholder: String? = null,
    supporting: String? = null,
    errorMessage: String? = null,
) {
    val interactions = interactionSource ?: remember { MutableInteractionSource() }
    val shape = Theme.shapes.field
    val shown = text ?: placeholder.orEmpty()
    // A text field's own colours for the line under it, so a date in a form
    // says "required" in the same grey and the same red as the name above it.
    val fieldColours = TextFieldDefaults.colours()

    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (label != null) {
            Text(
                text = label,
                style = Theme.typography.labelMedium,
                colour = if (enabled) Theme.colours.contentMuted else Theme.colours.contentDisabled,
                // Cleared, because the name goes on the control below instead.
                //
                // Compose has no `labelledBy`: a label drawn above a field is an
                // unrelated node however close it is on screen. Left as it was,
                // a screen reader read "Leave at" and then, separately, "08:15,
                // button" — a control whose name is its own value. `FieldScaffold`
                // has done it this way for every text field since the contract
                // suite arrived; this field was written later and did not.
                modifier = Modifier.clearAndSetSemantics {},
            )
        }
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .focusRing(interactions, shape, enabled = enabled)
                .semantics {
                    if (label != null) contentDescription = label
                    stateDescription = shown
                    if (errorMessage != null) error(errorMessage)
                }
                .pointerCursor(enabled = enabled)
                .clickable(
                    interactionSource = interactions,
                    // The same press wash every other tappable surface in the
                    // system uses. It read as inert without it — the one field
                    // in the library that did not respond to a press.
                    indication = kontourIndication(shape, pressScale = 1f),
                    enabled = enabled,
                    role = Role.Button,
                    onClick = onClick,
                ),
            shape = shape,
            containerColour = Theme.colours.surfaceSunken,
            // A well on the page is 1.13:1 at the enhanced tier; the field has
            // to be seen to be pressed. In error, the error colour all round.
            border = if (errorMessage != null) {
                BorderStroke(Theme.sizing.borderWidth, fieldColours.error)
            } else {
                contrastEdge()
            },
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = shown,
                style = Theme.typography.bodyLarge,
                colour = when {
                    !enabled -> Theme.colours.contentDisabled
                    // Nothing chosen yet reads as a placeholder does in a text
                    // field, so an empty date is not mistaken for one.
                    text == null -> Theme.colours.contentSubtle
                    else -> Theme.colours.content
                },
                modifier = Modifier.padding(
                    horizontal = Theme.spacing.sm,
                    vertical = Theme.spacing.sm,
                ),
            )
        }
        // The same slot a text field has: one line, the error in place of the
        // supporting text, opening and closing in place.
        val message = errorMessage ?: supporting
        val motion = Theme.motion
        AnimatedVisibility(
            visible = message != null,
            enter = motion.enterOrFade(fadeIn(motion.tweenFast()) + expandVertically(motion.tweenFast())),
            exit = motion.exitOrFade(fadeOut(motion.tweenFast()) + shrinkVertically(motion.tweenFast())),
        ) {
            var last by remember { mutableStateOf(message.orEmpty()) }
            if (message != null) last = message
            Text(
                text = message ?: last,
                style = Theme.typography.bodySmall,
                colour = when {
                    !enabled -> Theme.colours.contentDisabled
                    errorMessage != null -> fieldColours.error
                    else -> fieldColours.supporting
                },
                // Said with the field: its error as the field's, its supporting
                // line as a line of its own after it.
                modifier = if (errorMessage != null) Modifier.clearAndSetSemantics {} else Modifier,
            )
        }
    }
}

/** The day-period wheel's width when its words are short. */
private val PeriodWheelWidth = 64.dp

/** Room either side of the longest day-period word. */
private val PeriodWheelPadding = 24.dp
