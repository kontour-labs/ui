package io.kontour.ui.samples

import io.kontour.ui.foundation.Text
import io.kontour.ui.components.selection.Knob
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.ArrowsRightLeft
import com.composables.icons.tabler.outline.Bolt
import com.composables.icons.tabler.outline.Check
import com.composables.icons.tabler.outline.Walk
import io.kontour.ui.components.selection.Checkbox
import io.kontour.ui.components.selection.ChipGroup
import io.kontour.ui.components.selection.ColourPicker
import io.kontour.ui.components.selection.ColourPickerMode
import io.kontour.ui.components.selection.ColourSwatchPicker
import io.kontour.ui.components.selection.FilterChip
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
import kotlin.math.roundToInt

@Composable
fun CheckboxBasics() {
    var notify by remember { mutableStateOf(false) }

    Checkbox(checked = notify, onCheckedChange = { notify = it })
}

@Composable
fun TriStateCheckboxBasics() {
    var routes by remember { mutableStateOf(listOf(true, false, false)) }

    val state = when {
        routes.all { it } -> ToggleableState.On
        routes.none { it } -> ToggleableState.Off
        else -> ToggleableState.Indeterminate
    }

    TriStateCheckbox(
        state = state,
        onClick = { routes = List(routes.size) { state != ToggleableState.On } },
    )
}

@Composable
fun RadioGroupBasics() {
    var mode by remember { mutableStateOf(Mode.Fastest) }

    RadioGroup(
        options = Mode.entries,
        value = mode,
        onValueChange = { mode = it },
    ) { option ->
        +option.displayName
        leading { +option.icon }
        supporting { +option.explanation }
    }
}

@Composable
fun SwitchBasics() {
    var liveAlerts by remember { mutableStateOf(true) }

    Switch(checked = liveAlerts, onCheckedChange = { liveAlerts = it })
}

@Composable
fun SelectionRowBasics() {
    var notifyOnDelay by remember { mutableStateOf(false) }

    SelectionRow(
        selected = notifyOnDelay,
        onSelectedChange = { notifyOnDelay = it },
        role = Role.Checkbox,
    ) {
        +"Notify me about delays"
        supporting { +"Only for favourited routes" }
        // The row owns the interaction; the control is here to show state.
        trailing { Checkbox(notifyOnDelay, onCheckedChange = null) }
    }
}

@Composable
fun FilterChipGroup() {
    var active by remember { mutableStateOf(setOf(Mode.Fastest)) }

    ChipGroup {
        Mode.entries.forEach { mode ->
            FilterChip(
                selected = mode in active,
                onSelectedChange = { on ->
                    active = if (on) active + mode else active - mode
                },
                selectedIcon = Tabler.Outline.Check,
            ) {
                +mode.displayName
            }
        }
    }
}

@Composable
fun SegmentedControlBasics() {
    var selected by remember { mutableStateOf(0) }

    SegmentedControl(
        options = listOf("Bus", "Train", "Ferry"),
        selectedIndex = selected,
        onSelectedIndexChange = { selected = it },
    )
}

@Composable
fun ColourSwatchPickerBasics() {
    var accent by remember { mutableStateOf(RouteColor.Red) }

    ColourSwatchPicker(
        value = accent,
        options = RouteColor.entries,
        onValueChange = { accent = it },
        swatchColour = { it.colour },
        swatchLabel = { it.displayName },
    )
}

@Composable
fun KnobBasics() {
    var volume by remember { mutableStateOf(0.4f) }

    Knob(
        value = volume,
        onValueChange = { volume = it },
        steps = 9,
        contentDescription = "Volume",
        stateDescription = { "${(it * 100).roundToInt()}%" },
    ) {
        Text("${(volume * 100).roundToInt()}")
    }
}

@Composable
fun SliderBasics() {
    var walkSpeed by remember { mutableStateOf(4f) }

    Slider(
        value = walkSpeed,
        onValueChange = { walkSpeed = it },
        valueRange = 2f..7f,
        steps = 4,
        showTicks = true,
        // Without this the announcement is a bare percentage, which is not the
        // number the user is choosing.
        stateDescription = { "${it.roundToInt()} km/h" },
    )
}

@Composable
fun RangeSliderBasics() {
    var window by remember { mutableStateOf(7f..19f) }

    RangeSlider(
        value = window,
        onValueChange = { window = it },
        valueRange = 0f..24f,
        steps = 23,
        startContentDescription = "Earliest departure",
        endContentDescription = "Latest departure",
        stateDescription = { "${it.start.roundToInt()}:00 to ${it.endInclusive.roundToInt()}:00" },
    )
}

@Composable
fun StepperBasics() {
    var adults by remember { mutableStateOf(1) }

    Stepper(
        value = adults,
        onValueChange = { adults = it },
        contentDescription = "Adults",
        valueRange = 1..9,
    )
}

@Composable
fun RatingBasics() {
    var rating by remember { mutableStateOf(0f) }

    Rating(value = rating, onValueChange = { rating = it }, contentDescription = "Your rating")

    // A null callback means read-only, which means not a control at all: no role,
    // no touch target, one node saying "Average rating, 4.3 out of 5".
    Rating(value = 4.3f, onValueChange = null, contentDescription = "Average rating")
}

// --- The caller's own types -------------------------------------------------

internal enum class Mode(
    val displayName: String,
    val explanation: String,
    val icon: ImageVector,
) {
    Fastest("Fastest", "Shortest total travel time", Tabler.Outline.Bolt),
    FewestChanges("Fewest changes", "Fewer transfers, possibly slower", Tabler.Outline.ArrowsRightLeft),
    LeastWalking("Least walking", "Shortest distance on foot", Tabler.Outline.Walk),
}

internal enum class RouteColor(val displayName: String, val colour: Color) {
    Red("Red", Color(0xFFDC2626)),
    Blue("Blue", Color(0xFF2563EB)),
    Green("Green", Color(0xFF16A34A)),
}

@Composable
fun RadioButtonBasics() {
    var mode by remember { mutableStateOf("Train") }

    // The row carries the click and the role; the button is passed `null` so it
    // is not a second target announcing the same thing. A bare `RadioButton` is
    // for a table cell or a custom row — everywhere else, use `RadioGroup`.
    SelectionRow(
        selected = mode == "Train",
        onSelectedChange = { mode = "Train" },
        role = Role.RadioButton,
    ) {
        +"Train"
        leading { RadioButton(selected = mode == "Train", onClick = null) }
    }
}

@Composable
fun ColourPickerBasics() {
    var accent by remember { mutableStateOf(Color(0xFF1E88E5)) }

    ColourPicker(colour = accent, onColourChange = { accent = it })
}

@Composable
fun ColourPickerParts() {
    // A label colour: the eight we offer, and nothing to invent a ninth with.
    ColourPicker(
        colour = label,
        onColourChange = { label = it },
        mode = ColourPickerMode.Palette,
        showValueField = false,
    )
}
