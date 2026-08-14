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
import com.composables.icons.fontawesome.FontAwesome
import com.composables.icons.fontawesome.solid.Bus
import com.composables.icons.fontawesome.solid.Check
import com.composables.icons.fontawesome.solid.Times
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
                    Checkbox(checked = true, onCheckedChange = {})
                    Checkbox(checked = false, onCheckedChange = {})
                    TriStateCheckbox(ToggleableState.Indeterminate, onClick = {})
                    Checkbox(checked = true, onCheckedChange = {}, enabled = false)
                    Checkbox(checked = false, onCheckedChange = {}, enabled = false)

                    RadioButton(selected = true, onClick = {})
                    RadioButton(selected = false, onClick = {})
                    RadioButton(selected = true, onClick = {}, enabled = false)

                    Switch(checked = true, onCheckedChange = {})
                    Switch(checked = false, onCheckedChange = {})
                    Switch(checked = true, onCheckedChange = {}, enabled = false)
                }
            }

            Section("Selection rows") {
                Column(Modifier.width(460.dp)) {
                    SelectionRow(
                        label = "Notify me about delays",
                        supporting = "Only for favourited routes",
                        selected = true,
                        onClick = {},
                        role = Role.Checkbox,
                        control = { Checkbox(checked = true, onCheckedChange = null) },
                    )
                    SelectionRow(
                        label = "Show live vehicles",
                        selected = false,
                        onClick = {},
                        role = Role.Switch,
                        control = { Switch(checked = false, onCheckedChange = null) },
                    )
                    SelectionRow(
                        label = "Leave now",
                        selected = true,
                        onClick = {},
                        role = Role.RadioButton,
                        controlPosition = ControlPosition.Leading,
                        control = { RadioButton(selected = true, onClick = null) },
                    )
                    SelectionRow(
                        label = "Unavailable in this network",
                        selected = false,
                        onClick = {},
                        enabled = false,
                        role = Role.Checkbox,
                        control = { Checkbox(checked = false, onCheckedChange = null, enabled = false) },
                    )
                }
            }

            Section("Chips") {
                ChipGroup {
                    FilterChip(
                        "Buses",
                        selected = true,
                        onClick = {},
                        selectedIcon = FontAwesome.Solid.Check,
                    )
                    FilterChip(
                        "Trains",
                        selected = false,
                        onClick = {},
                        leadingIcon = FontAwesome.Solid.Bus,
                        selectedIcon = FontAwesome.Solid.Check,
                    )
                    FilterChip("Ferries", selected = false, onClick = {})
                    FilterChip("Disabled", selected = false, onClick = {}, enabled = false)
                    Chip("Share", onClick = {})
                    InputChip(
                        "Perth Station",
                        onRemove = {},
                        removeIcon = FontAwesome.Solid.Times,
                    )
                }
            }

            Section("Segmented control") {
                Column(
                    Modifier.width(420.dp),
                    verticalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
                ) {
                    SegmentedControl(listOf("Depart", "Arrive"), selectedIndex = 0, onSelect = {})
                    SegmentedControl(
                        listOf("Day", "Week", "Month"),
                        selectedIndex = 1,
                        onSelect = {},
                    )
                }
            }

            Section("Slider") {
                Column(
                    Modifier.width(460.dp),
                    verticalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
                ) {
                    Slider(value = 0.35f, onValueChange = {}, modifier = Modifier.fillMaxWidth())
                    Slider(
                        value = 3f,
                        onValueChange = {},
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
