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

private val triStateEnabled = Knob.Flag("Enabled", initial = true)

internal val TriStateCheckboxDemo = ComponentDemo(
    slug = "tri-state-checkbox",
    knobs = listOf(triStateEnabled),
) {
    var state by remember { mutableStateOf(ToggleableState.Indeterminate) }
    val enabled = this[triStateEnabled]
    Row(
        horizontalArrangement = Arrangement.spacedBy(Theme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TriStateCheckbox(
            state = state,
            enabled = enabled,
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

/**
 * Whether the chip that changes its label morphs or cuts.
 *
 * The knob passes `contentKey` or `null`, which is the whole of the parameter:
 * with a key the label cross-fades and the chip resizes to it, without one the
 * two labels swap between frames. Worth being able to see both, because the cut
 * is what every chip in the library did until this was wired up.
 */
private val chipMorph = Knob.Flag("Morph", initial = true)

internal val ChipDemo = ComponentDemo(slug = "chip", knobs = listOf(chipMorph)) {
    var buses by remember { mutableStateOf(true) }
    var trains by remember { mutableStateOf(false) }
    var cleared by remember { mutableStateOf(false) }
    var place by remember { mutableStateOf(true) }
    val morph = this[chipMorph]

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
        // One chip changing its mind, not one leaving and another arriving —
        // which is why both states are the same `Chip` rather than an
        // `InputChip` swapped for a plain one. `AnimatedContent` cannot span two
        // different composables, so written that way the morph could not happen
        // however the key was set, which is why it read as missing.
        Chip(
            onClick = { cleared = !cleared },
            contentKey = if (morph) cleared else null,
        ) {
            if (cleared) {
                +"Undo"
            } else {
                +Tabler.Outline.Bus
                +"Perth Station"
            }
        }
        if (place) {
            InputChip(
                onRemove = { place = false },
                removeIcon = Tabler.Outline.X,
                removeLabel = "Remove Elizabeth Quay",
            ) { +"Elizabeth Quay" }
        } else {
            Chip(onClick = { place = true }) { +"Restore" }
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

/**
 * Dots along the bar at each step.
 *
 * Only means anything on a stepped slider, which is why it sits beside
 * [sliderSteps] rather than alone: `showTicks` on a continuous range has no
 * steps to mark and the component draws nothing.
 */
private val sliderTicks = Knob.Flag("Ticks", initial = true)
private val sliderEnabled = Knob.Flag("Enabled", initial = true)

internal val SliderDemo = ComponentDemo(
    slug = "slider",
    knobs = listOf(sliderSteps, sliderTicks, sliderEnabled),
) {
    var amount by remember { mutableStateOf(0.35f) }
    var stepped by remember { mutableStateOf(3f) }
    val enabled = this[sliderEnabled]
    val ticks = this[sliderTicks]
    if (this[sliderSteps]) {
        Column(Modifier.fillMaxWidth()) {
            Slider(
                value = stepped,
                onValueChange = { stepped = it },
                valueRange = 1f..5f,
                steps = 3,
                showTicks = ticks,
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

private val rangeSliderSteps = Knob.Flag("Stepped")
private val rangeSliderTicks = Knob.Flag("Ticks", initial = true)
private val rangeSliderEnabled = Knob.Flag("Enabled", initial = true)

internal val RangeSliderDemo = ComponentDemo(
    slug = "range-slider",
    knobs = listOf(rangeSliderSteps, rangeSliderTicks, rangeSliderEnabled),
) {
    var window by remember { mutableStateOf(0.25f..0.7f) }
    var hours by remember { mutableStateOf(8f..17f) }
    val enabled = this[rangeSliderEnabled]
    val ticks = this[rangeSliderTicks]
    if (this[rangeSliderSteps]) {
        // A departure window in whole hours, which is the shape `minDistance`
        // and the tick marks were both built for.
        RangeSlider(
            value = hours,
            onValueChange = { hours = it },
            valueRange = 6f..20f,
            steps = 13,
            showTicks = ticks,
            minDistance = 1f,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        )
    } else {
        RangeSlider(
            value = window,
            onValueChange = { window = it },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Whether the number rolls or is replaced.
 *
 * On by default here, which is the opposite of the component's own default — a
 * demo exists to show what something can do, and `AnimatedCounter` has been
 * wired into `Stepper` since it was written with nothing anywhere turning it
 * on. Off is still one press away, and that is the comparison worth having.
 */
private val stepperAnimate = Knob.Flag("Animate", initial = true)
private val stepperEnabled = Knob.Flag("Enabled", initial = true)

internal val StepperDemo = ComponentDemo(
    slug = "stepper",
    knobs = listOf(stepperAnimate, stepperEnabled),
) {
    var adults by remember { mutableStateOf(2) }
    var bags by remember { mutableStateOf(0) }
    val enabled = this[stepperEnabled]
    val animate = this[stepperAnimate]
    Row(horizontalArrangement = Arrangement.spacedBy(Theme.spacing.lg)) {
        Stepper(
            value = adults,
            onValueChange = { adults = it },
            contentDescription = "Adults",
            range = 1..9,
            animateValue = animate,
            enabled = enabled,
        )
        Stepper(
            value = bags,
            onValueChange = { bags = it },
            contentDescription = "Bags",
            range = 0..4,
            format = { if (it == 1) "1 bag" else "$it bags" },
            animateValue = animate,
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
