package io.kontour.ui.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import io.kontour.ui.sheet.ModalBottomSheet
import io.kontour.ui.sheet.SheetHeader
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
                    // Seeded from what each of these used to hardcode, so every
                    // golden is unchanged and every picker can now be used. A
                    // date picker nobody can pick a date in is the one component
                    // where a dead callback is indistinguishable from a broken
                    // one — which is how the range band's height bug survived a
                    // whole round.
                    val depart = seed(LocalDate(2026, 6, 18))
                    DatePicker(
                        selected = depart.value,
                        onSelect = { depart.value = it },
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
                    // `end` is nullable: a range being drawn has a start and no
                    // end yet, which is the state the in-flight band shows.
                    val range = seed<Pair<LocalDate, LocalDate?>>(
                        LocalDate(2026, 6, 9) to LocalDate(2026, 6, 21)
                    )
                    DateRangePicker(
                        start = range.value.first,
                        end = range.value.second,
                        onSelect = { start, end -> range.value = start to end },
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
                    val at = seed(LocalTime(8, 15))
                    TimePicker(
                        time = at.value,
                        onTimeChange = { at.value = it },
                        minuteStep = 5,
                    )
                }

                Section("Time picker — 12h") {
                    CompositionLocalProvider(
                        LocalDateTimeFormats provides DateTimeFormats(is24Hour = false)
                    ) {
                        val afternoon = seed(LocalTime(14, 30))
                        TimePicker(
                            time = afternoon.value,
                            onTimeChange = { afternoon.value = it },
                            minuteStep = 15,
                        )
                    }
                }

                Section("Time field") {
                    // The caption said this opened a picker in a sheet, and it
                    // did not — the field was a literal with a dead callback.
                    // It does now, which is also the only place in the catalog
                    // that shows the field and the picker working as the pair
                    // they are meant to be.
                    val leaveAt = seed(LocalTime(8, 15))
                    val picking = seed(false)

                    TimeField(
                        time = leaveAt.value,
                        onClick = { picking.value = true },
                        label = "Leave at",
                    )
                    Text(
                        text = "Tapping opens a TimePicker in a sheet",
                        style = Theme.typography.bodySmall,
                        color = Theme.colors.contentMuted,
                    )

                    ModalBottomSheet(
                        visible = picking.value,
                        onDismissRequest = { picking.value = false },
                    ) {
                        SheetHeader { +"Leave at" }
                        Column(
                            modifier = Modifier.padding(
                                start = Theme.spacing.md,
                                end = Theme.spacing.md,
                                bottom = Theme.spacing.lg,
                            ),
                        ) {
                            TimePicker(
                                time = leaveAt.value,
                                onTimeChange = { leaveAt.value = it },
                                minuteStep = 5,
                            )
                        }
                    }
                }
            }
        }
    }
}
