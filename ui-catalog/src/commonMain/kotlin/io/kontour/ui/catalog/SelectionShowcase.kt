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
import io.kontour.ui.components.selection.FilterChip
import io.kontour.ui.components.selection.InputChip
import io.kontour.ui.components.selection.RadioButton
import io.kontour.ui.components.selection.SegmentedControl
import io.kontour.ui.components.selection.SelectionRow
import io.kontour.ui.components.selection.RangeSlider
import io.kontour.ui.components.selection.Slider
import io.kontour.ui.components.selection.Stepper
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
                        selected = delays.value,
                        onSelectedChange = { delays.value = it },
                        role = Role.Checkbox,
                    ) {
                        +"Notify me about delays"
                        supporting { +"Only for favourited routes" }
                        trailing { Checkbox(checked = delays.value, onCheckedChange = null) }
                    }
                    SelectionRow(
                        selected = live.value,
                        onSelectedChange = { live.value = it },
                        role = Role.Switch,
                    ) {
                        +"Show live vehicles"
                        trailing { Switch(checked = live.value, onCheckedChange = null) }
                    }
                    SelectionRow(
                        selected = leaveNow.value,
                        onSelectedChange = { leaveNow.value = it },
                        role = Role.RadioButton,
                    ) {
                        +"Leave now"
                        leading { RadioButton(selected = leaveNow.value, onClick = null) }
                    }
                    SelectionRow(
                        selected = false,
                        onSelectedChange = {},
                        enabled = false,
                        role = Role.Checkbox,
                    ) {
                        +"Unavailable in this network"
                        trailing { Checkbox(checked = false, onCheckedChange = null, enabled = false) }
                    }
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
                    Chip(onClick = tap("Share")) { +"Share" }

                    // An input chip that actually removes itself, and a way back:
                    // a gallery where the only interactive thing a control does is
                    // disappear for good is one you can try exactly once.
                    val place = seed(true)
                    if (place.value) {
                        InputChip(
                            onRemove = { place.value = false },
                            removeIcon = Tabler.Outline.X,
                            removeLabel = "Remove Perth Station",
                        ) {
                            +"Perth Station"
                        }
                    } else {
                        Chip(onClick = { place.value = true }) { +"Undo" }
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

            Section("RangeSlider") {
                Column(
                    Modifier.width(460.dp),
                    verticalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
                ) {
                    val window = seed(0.25f..0.7f)
                    // Both thumbs on the same value, which is what "no filter
                    // set" looks like and the one state a range slider can get
                    // stuck in. It is here so it is on screen, not only in a test.
                    val closed = seed(4f..4f)

                    RangeSlider(
                        value = window.value,
                        onValueChange = { window.value = it },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    RangeSlider(
                        value = closed.value,
                        onValueChange = { closed.value = it },
                        valueRange = 0f..8f,
                        steps = 7,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    RangeSlider(
                        value = 0.3f..0.8f,
                        onValueChange = {},
                        enabled = false,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Section("Stepper") {
                Row(horizontalArrangement = Arrangement.spacedBy(Theme.spacing.lg)) {
                    val adults = seed(2)
                    val bags = seed(0)

                    Stepper(
                        value = adults.value,
                        onValueChange = { adults.value = it },
                        contentDescription = "Adults",
                        range = 1..9,
                    )
                    // Resting at its lower bound, so the disabled end of the
                    // control is visible in the golden rather than only
                    // reachable by pressing.
                    Stepper(
                        value = bags.value,
                        onValueChange = { bags.value = it },
                        contentDescription = "Bags",
                        range = 0..4,
                        format = { if (it == 1) "1 bag" else "$it bags" },
                    )
                    Stepper(
                        value = 3,
                        onValueChange = {},
                        contentDescription = "Bikes",
                        enabled = false,
                    )
                }
            }
        }
    }
}
