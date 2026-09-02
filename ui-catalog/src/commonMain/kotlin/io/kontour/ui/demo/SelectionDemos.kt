package io.kontour.ui.demo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Bus
import com.composables.icons.tabler.outline.Check
import com.composables.icons.tabler.outline.Sparkles
import com.composables.icons.tabler.outline.X
import io.kontour.ui.components.selection.Checkbox
import io.kontour.ui.components.selection.Chip
import io.kontour.ui.components.selection.ChipGroup
import io.kontour.ui.components.selection.ColourSwatchPicker
import io.kontour.ui.components.selection.FilterChip
import io.kontour.ui.components.selection.InputChip
import io.kontour.ui.components.selection.RadioButton
import io.kontour.ui.components.selection.RadioGroup
import io.kontour.ui.components.selection.RangeSlider
import io.kontour.ui.components.selection.Rating
import io.kontour.ui.components.selection.SegmentedControl
import io.kontour.ui.components.selection.SelectionRow
import io.kontour.ui.components.selection.Slider
import io.kontour.ui.components.selection.Stepper
import io.kontour.ui.components.selection.Switch
import io.kontour.ui.components.selection.TriStateCheckbox
import io.kontour.ui.foundation.Text
import io.kontour.ui.theme.Theme

private val checkboxEnabled = Knob.Flag("Enabled", initial = true)

internal val CheckboxDemo = ComponentDemo(
    slug = "checkbox",
    knobs = listOf(checkboxEnabled),
) {
    // In a SelectionRow, because a bare checkbox has no name of its own and the
    // page it sits on says so — `namedByContext` in the registry is the same
    // fact, asserted. Showing it alone would demonstrate the one arrangement
    // the documentation tells you not to use.
    var delays by remember { mutableStateOf(true) }
    val enabled = this[checkboxEnabled]
    SelectionRow(
        selected = delays,
        onSelectedChange = { delays = it },
        role = Role.Checkbox,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
    ) {
        +"Notify me about delays"
        supporting { +"Only for favourited routes" }
        trailing { Checkbox(checked = delays, onCheckedChange = null, enabled = enabled) }
    }
}

internal val TriStateCheckboxDemo = ComponentDemo(slug = "tri-state-checkbox") {
    var state by remember { mutableStateOf(ToggleableState.Indeterminate) }
    Row(
        horizontalArrangement = Arrangement.spacedBy(Theme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TriStateCheckbox(
            state = state,
            onClick = {
                state = when (state) {
                    ToggleableState.Off -> ToggleableState.On
                    ToggleableState.On -> ToggleableState.Indeterminate
                    ToggleableState.Indeterminate -> ToggleableState.Off
                }
            },
        )
        Text(state.name, style = Theme.typography.bodyMedium, colour = Theme.colours.contentMuted)
    }
}

internal val RadioButtonDemo = ComponentDemo(slug = "radio-button") {
    var leaveNow by remember { mutableStateOf(true) }
    Column(Modifier.fillMaxWidth()) {
        listOf("Leave now" to true, "Leave later" to false).forEach { (label, value) ->
            SelectionRow(
                selected = leaveNow == value,
                onSelectedChange = { leaveNow = value },
                role = Role.RadioButton,
                modifier = Modifier.fillMaxWidth(),
            ) {
                +label
                leading { RadioButton(selected = leaveNow == value, onClick = null) }
            }
        }
    }
}

private val radioGroupEnabled = Knob.Flag("Enabled", initial = true)

internal val RadioGroupDemo = ComponentDemo(
    slug = "radio-group",
    knobs = listOf(radioGroupEnabled),
) {
    var mode by remember { mutableStateOf("Bus") }
    RadioGroup(
        options = listOf("Bus", "Train", "Ferry"),
        selected = mode,
        onSelectedChange = { mode = it },
        enabled = this[radioGroupEnabled],
        label = { it },
    )
}

private val switchEnabled = Knob.Flag("Enabled", initial = true)

internal val SwitchDemo = ComponentDemo(
    slug = "switch",
    knobs = listOf(switchEnabled),
) {
    var live by remember { mutableStateOf(false) }
    val enabled = this[switchEnabled]
    SelectionRow(
        selected = live,
        onSelectedChange = { live = it },
        role = Role.Switch,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
    ) {
        +"Show live vehicles"
        trailing { Switch(checked = live, onCheckedChange = null, enabled = enabled) }
    }
}

internal val SelectionRowDemo = ComponentDemo(slug = "selection-row") {
    var delays by remember { mutableStateOf(true) }
    var live by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        SelectionRow(
            selected = delays,
            onSelectedChange = { delays = it },
            role = Role.Checkbox,
            modifier = Modifier.fillMaxWidth(),
        ) {
            +"Notify me about delays"
            supporting { +"Only for favourited routes" }
            trailing { Checkbox(checked = delays, onCheckedChange = null) }
        }
        SelectionRow(
            selected = live,
            onSelectedChange = { live = it },
            role = Role.Switch,
            modifier = Modifier.fillMaxWidth(),
        ) {
            +"Show live vehicles"
            trailing { Switch(checked = live, onCheckedChange = null) }
        }
    }
}

internal val ChipDemo = ComponentDemo(slug = "chip") {
    var buses by remember { mutableStateOf(true) }
    var trains by remember { mutableStateOf(false) }
    var place by remember { mutableStateOf(true) }

    ChipGroup {
        FilterChip(
            selected = buses,
            onClick = { buses = !buses },
            selectedIcon = Tabler.Outline.Check,
        ) { +"Buses" }
        FilterChip(
            selected = trains,
            onClick = { trains = !trains },
            selectedIcon = Tabler.Outline.Check,
        ) {
            +Tabler.Outline.Bus
            +"Trains"
        }
        Chip(onClick = { echo("Share") }) { +"Share" }
        if (place) {
            InputChip(
                onRemove = { place = false },
                removeIcon = Tabler.Outline.X,
                removeLabel = "Remove Perth Station",
            ) { +"Perth Station" }
        } else {
            Chip(onClick = { place = true }) { +"Undo" }
        }
    }
}

internal val SegmentedControlDemo = ComponentDemo(slug = "segmented-control") {
    var span by remember { mutableStateOf(1) }
    SegmentedControl(
        options = listOf("Day", "Week", "Month"),
        selected = span,
        onSelectedChange = { span = it },
        modifier = Modifier.fillMaxWidth(),
    )
}

private val sliderSteps = Knob.Flag("Stepped")
private val sliderEnabled = Knob.Flag("Enabled", initial = true)

internal val SliderDemo = ComponentDemo(
    slug = "slider",
    knobs = listOf(sliderSteps, sliderEnabled),
) {
    var amount by remember { mutableStateOf(0.35f) }
    var stepped by remember { mutableStateOf(3f) }
    val enabled = this[sliderEnabled]
    if (this[sliderSteps]) {
        Column(Modifier.fillMaxWidth()) {
            Slider(
                value = stepped,
                onValueChange = { stepped = it },
                valueRange = 1f..5f,
                steps = 3,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "${stepped.toInt()} of 5",
                style = Theme.typography.labelSmall,
                colour = Theme.colours.contentMuted,
            )
        }
    } else {
        Slider(
            value = amount,
            onValueChange = { amount = it },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private val rangeSliderEnabled = Knob.Flag("Enabled", initial = true)

internal val RangeSliderDemo = ComponentDemo(
    slug = "range-slider",
    knobs = listOf(rangeSliderEnabled),
) {
    var window by remember { mutableStateOf(0.25f..0.7f) }
    RangeSlider(
        value = window,
        onValueChange = { window = it },
        enabled = this[rangeSliderEnabled],
        modifier = Modifier.fillMaxWidth(),
    )
}

private val stepperEnabled = Knob.Flag("Enabled", initial = true)

internal val StepperDemo = ComponentDemo(
    slug = "stepper",
    knobs = listOf(stepperEnabled),
) {
    var adults by remember { mutableStateOf(2) }
    var bags by remember { mutableStateOf(0) }
    val enabled = this[stepperEnabled]
    Row(horizontalArrangement = Arrangement.spacedBy(Theme.spacing.lg)) {
        Stepper(
            value = adults,
            onValueChange = { adults = it },
            contentDescription = "Adults",
            range = 1..9,
            enabled = enabled,
        )
        Stepper(
            value = bags,
            onValueChange = { bags = it },
            contentDescription = "Bags",
            range = 0..4,
            format = { if (it == 1) "1 bag" else "$it bags" },
            enabled = enabled,
        )
    }
}

private val ratingHalf = Knob.Flag("Half marks")
private val ratingEnabled = Knob.Flag("Enabled", initial = true)

internal val RatingDemo = ComponentDemo(
    slug = "rating",
    knobs = listOf(ratingHalf, ratingEnabled),
) {
    var score by remember { mutableStateOf(3f) }
    Rating(
        value = score,
        contentDescription = "Your rating",
        onValueChange = { score = it },
        allowHalf = this[ratingHalf],
        enabled = this[ratingEnabled],
    )
}

/** The picker takes a `Color?` per option, and null is the "match system" case. */
private enum class Accent(val label: String, val seed: Color?) {
    System("Match system", null),
    Anyways("Anyways", Color(0xFF6D28D9)),
    Ocean("Ocean", Color(0xFF0E7490)),
    Forest("Forest", Color(0xFF15803D)),
    Sunset("Sunset", Color(0xFFC2410C)),
    Rose("Rose", Color(0xFFBE123C)),
}

internal val ColourSwatchPickerDemo = ComponentDemo(slug = "colour-swatch-picker") {
    var accent by remember { mutableStateOf(Accent.Anyways) }
    ColourSwatchPicker(
        value = accent,
        options = Accent.entries,
        onValueChange = { accent = it },
        swatchColour = { it.seed },
        swatchLabel = { it.label },
        automaticIcon = Tabler.Outline.Sparkles,
    )
}

internal val selectionDemos = listOf(
    CheckboxDemo,
    TriStateCheckboxDemo,
    RadioButtonDemo,
    RadioGroupDemo,
    SwitchDemo,
    SelectionRowDemo,
    ChipDemo,
    SegmentedControlDemo,
    SliderDemo,
    RangeSliderDemo,
    StepperDemo,
    RatingDemo,
    ColourSwatchPickerDemo,
)
