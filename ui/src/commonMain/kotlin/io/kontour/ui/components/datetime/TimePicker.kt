package io.kontour.ui.components.datetime

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.selection.SegmentedControl
import io.kontour.ui.foundation.Surface
import io.kontour.ui.foundation.Text
import io.kontour.ui.interaction.FeedbackIntent
import io.kontour.ui.interaction.LocalFeedback
import io.kontour.ui.interaction.kontourIndication
import io.kontour.ui.theme.Theme
import kotlinx.datetime.LocalTime

/**
 * Picks a time of day.
 *
 * Three wheels — hour, minute, and am/pm when the user is on a 12-hour clock —
 * rather than a clock dial. For a transit app the value is almost always being
 * *adjusted* ("leave at 8:15 instead of 8:00"), and a wheel gets there in one
 * flick where a dial needs two precise drags.
 *
 * ```
 * TimePicker(time = departAt, onTimeChange = viewModel::setDepartAt)
 * ```
 *
 * The 12/24-hour split comes from [LocalDateTimeFormats], so it follows the
 * platform setting rather than being a parameter every call site has to
 * remember to pass.
 *
 * @param minuteStep Coarsens the minute wheel. A planner rarely wants
 *   minute-level precision, and 5 turns 60 rows into 12.
 */
@Composable
fun TimePicker(
    time: LocalTime,
    onTimeChange: (LocalTime) -> Unit,
    modifier: Modifier = Modifier,
    minuteStep: Int = 1,
    formats: DateTimeFormats = LocalDateTimeFormats.current,
) {
    require(minuteStep in 1..30) { "minuteStep must divide an hour sensibly" }

    val minutes = remember(minuteStep) { (0 until 60 step minuteStep).toList() }
    val is24 = formats.is24Hour
    val hours = remember(is24) { if (is24) (0..23).toList() else (1..12).toList() }

    val displayHour = when {
        is24 -> time.hour
        time.hour % 12 == 0 -> 12
        else -> time.hour % 12
    }
    val hourIndex = hours.indexOf(displayHour).coerceAtLeast(0)
    val minuteIndex = minutes.indexOfFirst { it >= time.minute }.coerceAtLeast(0)
    val isPm = time.hour >= 12

    fun emit(hourValue: Int = displayHour, minute: Int = time.minute, pm: Boolean = isPm) {
        val hour24 = when {
            is24 -> hourValue
            hourValue == 12 -> if (pm) 12 else 0
            else -> if (pm) hourValue + 12 else hourValue
        }
        onTimeChange(LocalTime(hour24.coerceIn(0, 23), minute.coerceIn(0, 59)))
    }

    Column(modifier) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(72.dp).semantics { contentDescription = "Hour" }) {
                WheelPicker(
                    items = hours,
                    selectedIndex = hourIndex,
                    onSelect = { emit(hourValue = hours[it]) },
                    label = { if (is24) it.toString().padStart(2, '0') else it.toString() },
                )
            }

            Text(":", style = Theme.typography.headlineSmall, color = Theme.colors.contentMuted)

            Box(Modifier.width(72.dp).semantics { contentDescription = "Minute" }) {
                WheelPicker(
                    items = minutes,
                    selectedIndex = minuteIndex,
                    onSelect = { emit(minute = minutes[it]) },
                    label = { it.toString().padStart(2, '0') },
                )
            }
        }

        if (!is24) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = Theme.spacing.sm),
                horizontalArrangement = Arrangement.Center,
            ) {
                Box(Modifier.width(180.dp)) {
                    SegmentedControl(
                        options = listOf("AM", "PM"),
                        selectedIndex = if (isPm) 1 else 0,
                        onSelect = { emit(pm = it == 1) },
                    )
                }
            }
        }
    }
}

/**
 * The chosen time, shown as a tappable field.
 *
 * The thing you put on a form; tapping it is expected to open a [TimePicker] in
 * a sheet. Kept separate from the picker so the picker never has to know how it
 * is being presented.
 */
@Composable
fun TimeField(
    time: LocalTime,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: String? = null,
    formats: DateTimeFormats = LocalDateTimeFormats.current,
    interactionSource: MutableInteractionSource? = null,
) {
    val interactions = interactionSource ?: remember { MutableInteractionSource() }
    val feedback = LocalFeedback.current
    val shape = Theme.shapes.small

    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (label != null) {
            Text(
                text = label,
                style = Theme.typography.labelMedium,
                color = if (enabled) Theme.colors.contentMuted else Theme.colors.contentDisabled,
            )
        }
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = interactions,
                    // The same press wash every other tappable surface in the
                    // system uses. It read as inert without it — the one field
                    // in the library that did not respond to a press.
                    indication = kontourIndication(shape, pressScale = 1f),
                    enabled = enabled,
                    role = Role.Button,
                    onClick = {
                        feedback.perform(FeedbackIntent.Selection)
                        onClick()
                    },
                ),
            shape = shape,
            color = Theme.colors.surfaceSunken,
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = formats.time(time),
                style = Theme.typography.bodyLarge,
                color = if (enabled) Theme.colors.content else Theme.colors.contentDisabled,
                modifier = Modifier.padding(
                    horizontal = Theme.spacing.sm,
                    vertical = Theme.spacing.sm,
                ),
            )
        }
    }
}
