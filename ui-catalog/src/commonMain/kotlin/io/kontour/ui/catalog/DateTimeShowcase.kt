package io.kontour.ui.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.ChevronLeft
import com.composables.icons.tabler.outline.ChevronRight
import io.kontour.ui.components.datetime.DatePicker
import io.kontour.ui.components.datetime.DateRangePicker
import io.kontour.ui.components.datetime.DateTimeFormats
import io.kontour.ui.components.datetime.LocalDateTimeFormats
import io.kontour.ui.components.datetime.TimeField
import io.kontour.ui.components.datetime.TimePicker
import io.kontour.ui.foundation.Surface
import io.kontour.ui.foundation.Text
import io.kontour.ui.theme.Theme
import androidx.compose.runtime.CompositionLocalProvider
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

/** The date and time components. Source for the datetime goldens. */
@Composable
fun DateTimeShowcase(modifier: Modifier = Modifier) {
    val today = LocalDate(2026, 6, 12)

    Surface(modifier = modifier, color = Theme.colors.background) {
        Row(
            modifier = Modifier.padding(Theme.spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(Theme.spacing.lg),
        ) {
            Column(
                Modifier.width(360.dp),
                verticalArrangement = Arrangement.spacedBy(Theme.spacing.md),
            ) {
                Section("Date picker") {
                    DatePicker(
                        selected = LocalDate(2026, 6, 18),
                        onSelect = {},
                        today = today,
                        isDateSelectable = { it >= today },
                        previousIcon = Tabler.Outline.ChevronLeft,
                        nextIcon = Tabler.Outline.ChevronRight,
                    )
                }
            }

            Column(
                Modifier.width(360.dp),
                verticalArrangement = Arrangement.spacedBy(Theme.spacing.md),
            ) {
                Section("Range picker") {
                    DateRangePicker(
                        start = LocalDate(2026, 6, 9),
                        end = LocalDate(2026, 6, 21),
                        onSelect = { _, _ -> },
                        today = today,
                        previousIcon = Tabler.Outline.ChevronLeft,
                        nextIcon = Tabler.Outline.ChevronRight,
                    )
                }
            }

            Column(
                Modifier.width(320.dp),
                verticalArrangement = Arrangement.spacedBy(Theme.spacing.md),
            ) {
                Section("Time picker — 24h") {
                    TimePicker(
                        time = LocalTime(8, 15),
                        onTimeChange = {},
                        minuteStep = 5,
                    )
                }

                Section("Time picker — 12h") {
                    CompositionLocalProvider(
                        LocalDateTimeFormats provides DateTimeFormats(is24Hour = false)
                    ) {
                        TimePicker(
                            time = LocalTime(14, 30),
                            onTimeChange = {},
                            minuteStep = 15,
                        )
                    }
                }

                Section("Time field") {
                    TimeField(time = LocalTime(8, 15), onClick = {}, label = "Leave at")
                    Text(
                        text = "Tapping opens a TimePicker in a sheet",
                        style = Theme.typography.bodySmall,
                        color = Theme.colors.contentMuted,
                    )
                }
            }
        }
    }
}
