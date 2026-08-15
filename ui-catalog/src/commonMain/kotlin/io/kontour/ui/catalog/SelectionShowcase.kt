package io.kontour.ui.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Bus
import com.composables.icons.tabler.outline.Check
import com.composables.icons.tabler.outline.X
import io.kontour.ui.components.selection.Checkbox
import io.kontour.ui.components.selection.Chip
import io.kontour.ui.components.selection.ChipGroup
import io.kontour.ui.components.selection.ControlPosition
import io.kontour.ui.components.selection.FilterChip
import io.kontour.ui.components.selection.InputChip
import io.kontour.ui.components.selection.RadioButton
import io.kontour.ui.components.selection.SegmentedControl
import io.kontour.ui.components.selection.SelectionRow
import io.kontour.ui.components.selection.Slider
import io.kontour.ui.components.selection.Switch
import io.kontour.ui.components.selection.TriStateCheckbox
import io.kontour.ui.foundation.Surface
import io.kontour.ui.theme.Theme

/** Every selection control, in every state. Source for the selection goldens. */
@Composable
fun SelectionShowcase(modifier: Modifier = Modifier) {
    Surface(modifier = modifier, color = Theme.colors.background) {
        Column(
            modifier = Modifier.padding(Theme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Theme.spacing.lg),
        ) {
            Section("Checkbox, radio, switch") {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Theme.spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Seeded from what each of these used to hardcode, so every
                    // golden is unchanged and every control now responds. The
                    // disabled ones stay disabled — that *is* the state on show.
                    val on = seed(true)
                    val off = seed(false)
                    val tri = seed(ToggleableState.Indeterminate)

                    Checkbox(checked = on.value, onCheckedChange = { on.value = it })
                    Checkbox(checked = off.value, onCheckedChange = { off.value = it })
                    TriStateCheckbox(tri.value, onClick = { tri.value = tri.value.next() })
                    Checkbox(checked = true, onCheckedChange = {}, enabled = false)
                    Checkbox(checked = false, onCheckedChange = {}, enabled = false)

                    val picked = seed(true)
                    RadioButton(selected = picked.value, onClick = { picked.value = true })
                    RadioButton(selected = !picked.value, onClick = { picked.value = false })
                    RadioButton(selected = true, onClick = {}, enabled = false)

                    val switchOn = seed(true)
                    val switchOff = seed(false)
                    Switch(checked = switchOn.value, onCheckedChange = { switchOn.value = it })
                    Switch(checked = switchOff.value, onCheckedChange = { switchOff.value = it })
                    Switch(checked = true, onCheckedChange = {}, enabled = false)
                }
            }

            Section("Selection rows") {
                Column(Modifier.width(460.dp)) {
                    val delays = seed(true)
                    val live = seed(false)
                    val leaveNow = seed(true)

                    SelectionRow(
                        label = "Notify me about delays",
                        supporting = "Only for favourited routes",
                        selected = delays.value,
                        onSelectedChange = { delays.value = it },
                        role = Role.Checkbox,
                        control = { Checkbox(checked = delays.value, onCheckedChange = null) },
                    )
                    SelectionRow(
                        label = "Show live vehicles",
                        selected = live.value,
                        onSelectedChange = { live.value = it },
                        role = Role.Switch,
                        control = { Switch(checked = live.value, onCheckedChange = null) },
                    )
                    SelectionRow(
                        label = "Leave now",
                        selected = leaveNow.value,
                        onSelectedChange = { leaveNow.value = it },
                        role = Role.RadioButton,
                        controlPosition = ControlPosition.Leading,
                        control = { RadioButton(selected = leaveNow.value, onClick = null) },
                    )
                    SelectionRow(
                        label = "Unavailable in this network",
                        selected = false,
                        onSelectedChange = {},
                        enabled = false,
                        role = Role.Checkbox,
                        control = { Checkbox(checked = false, onCheckedChange = null, enabled = false) },
                    )
                }
            }

            Section("Chips") {
                ChipGroup {
                    val buses = seed(true)
                    val trains = seed(false)
                    val ferries = seed(false)

                    FilterChip(
                        selected = buses.value,
                        onClick = { buses.value = !buses.value },
                        selectedIcon = Tabler.Outline.Check,
                    ) {
                        +"Buses"
                    }
                    FilterChip(
                        selected = trains.value,
                        onClick = { trains.value = !trains.value },
                        selectedIcon = Tabler.Outline.Check,
                    ) {
                        +Tabler.Outline.Bus
                        +"Trains"
                    }
                    FilterChip(
                        selected = ferries.value,
                        onClick = { ferries.value = !ferries.value },
                    ) {
                        +"Ferries"
                    }
                    FilterChip(selected = false, onClick = {}, enabled = false) { +"Disabled" }
                    Chip(onClick = {}) { +"Share" }
                    InputChip(
                        onRemove = {},
                        removeIcon = Tabler.Outline.X,
                        removeLabel = "Remove Perth Station",
                    ) {
                        +"Perth Station"
                    }
                }
            }

            Section("Segmented control") {
                Column(
                    Modifier.width(420.dp),
                    verticalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
                ) {
                    val when_ = seed(0)
                    val span = seed(1)

                    SegmentedControl(
                        listOf("Depart", "Arrive"),
                        selectedIndex = when_.value,
                        onSelect = { when_.value = it },
                    )
                    SegmentedControl(
                        listOf("Day", "Week", "Month"),
                        selectedIndex = span.value,
                        onSelect = { span.value = it },
                    )
                }
            }

            Section("Slider") {
                Column(
                    Modifier.width(460.dp),
                    verticalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
                ) {
                    val amount = seed(0.35f)
                    val stepped = seed(3f)

                    Slider(
                        value = amount.value,
                        onValueChange = { amount.value = it },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Slider(
                        value = stepped.value,
                        onValueChange = { stepped.value = it },
                        valueRange = 1f..5f,
                        steps = 3,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Slider(
                        value = 0.6f,
                        onValueChange = {},
                        enabled = false,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
