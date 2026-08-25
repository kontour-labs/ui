package io.kontour.ui.demo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Bus
import com.composables.icons.tabler.outline.Eye
import com.composables.icons.tabler.outline.EyeOff
import com.composables.icons.tabler.outline.MapPin
import com.composables.icons.tabler.outline.Search
import com.composables.icons.tabler.outline.Train
import com.composables.icons.tabler.outline.Walk
import com.composables.icons.tabler.outline.X
import io.kontour.ui.components.text.Combobox
import io.kontour.ui.components.text.EmailField
import io.kontour.ui.components.text.MultiSelect
import io.kontour.ui.components.text.NumberField
import io.kontour.ui.components.text.PasswordField
import io.kontour.ui.components.text.PhoneField
import io.kontour.ui.components.text.SearchField
import io.kontour.ui.components.text.Select
import io.kontour.ui.components.text.TextArea
import io.kontour.ui.components.text.TextField
import io.kontour.ui.components.text.TextFieldVariant
import io.kontour.ui.theme.Theme

private val fieldVariant =
    Knob.Choice("Variant", TextFieldVariant.entries.toList(), TextFieldVariant.Outlined)
private val fieldError = Knob.Flag("Error")
private val fieldEnabled = Knob.Flag("Enabled", initial = true)

internal val TextFieldDemo = ComponentDemo(
    slug = "text-field",
    knobs = listOf(fieldVariant, fieldError, fieldEnabled),
) {
    val state = rememberTextFieldState("Perth Station")
    TextField(
        state = state,
        label = "Destination",
        placeholder = "Station, stop or address",
        leadingIcon = Tabler.Outline.MapPin,
        variant = this[fieldVariant],
        errorMessage = "That stop is not on this network".takeIf { this[fieldError] },
        enabled = this[fieldEnabled],
        modifier = Modifier.fillMaxWidth(),
    )
}

internal val SearchFieldDemo = ComponentDemo(slug = "search-field") {
    // The clear button only appears once there is something to clear, which is
    // the behaviour worth showing — so this starts with text in it.
    val state = rememberTextFieldState("Elizabeth Quay")
    SearchField(
        state = state,
        searchIcon = Tabler.Outline.Search,
        clearIcon = Tabler.Outline.X,
        placeholder = "Search stops and routes",
        modifier = Modifier.fillMaxWidth(),
    )
}

internal val TextAreaDemo = ComponentDemo(slug = "text-area") {
    val state = rememberTextFieldState(
        "The 960 was 12 minutes late and the app still showed it as on time.",
    )
    TextArea(
        state = state,
        label = "What went wrong?",
        minLines = 3,
        maxLines = 6,
        modifier = Modifier.fillMaxWidth(),
    )
}

internal val SpecialisedFieldsDemo = ComponentDemo(slug = "specialised-fields") {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Theme.spacing.md),
    ) {
        PasswordField(
            state = rememberTextFieldState("hunter2"),
            label = "Password",
            revealIcon = Tabler.Outline.Eye,
            hideIcon = Tabler.Outline.EyeOff,
            modifier = Modifier.fillMaxWidth(),
        )
        EmailField(
            state = rememberTextFieldState("aaron@kontour.io"),
            label = "Email",
            modifier = Modifier.fillMaxWidth(),
        )
        PhoneField(
            state = rememberTextFieldState("0412345678"),
            label = "Mobile",
            supporting = "Masked for display; stored as digits",
            modifier = Modifier.fillMaxWidth(),
        )
        NumberField(
            state = rememberTextFieldState("42"),
            label = "Walk speed",
            supporting = "Non-numeric keystrokes never arrive",
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private enum class Departure(val label: String) {
    DepartAt("Depart at"),
    ArriveBy("Arrive by"),
    LastPossible("Last possible"),
}

private enum class Mode(val label: String) {
    Bus("Bus"), Train("Train"), Ferry("Ferry"), Walk("Walking"), Bike("Cycling")
}

/**
 * Room for the menu to open into.
 *
 * An open `Select` renders into the nearest `OverlayHost`, and on the site that
 * is the one at the root of the page — outside this card and taller than it. The
 * card would otherwise grow and shrink as the menu opens and closes, which moves
 * the prose underneath while somebody is reading it.
 */
private val MenuRoom = 300.dp

internal val SelectDemo = ComponentDemo(slug = "select") {
    var departure by remember { mutableStateOf(Departure.DepartAt) }
    Box(Modifier.fillMaxWidth().height(MenuRoom)) {
        Select(
            value = departure,
            options = Departure.entries,
            onValueChange = { departure = it },
            label = "When",
            optionLabel = { it.label },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

internal val MultiSelectDemo = ComponentDemo(slug = "multi-select") {
    var modes by remember { mutableStateOf(setOf(Mode.Bus, Mode.Train)) }
    Box(Modifier.fillMaxWidth().height(MenuRoom)) {
        MultiSelect(
            value = modes,
            options = Mode.entries,
            onValueChange = { modes = it },
            label = "Include",
            optionLabel = { it.label },
            optionIcon = {
                when (it) {
                    Mode.Bus -> Tabler.Outline.Bus
                    Mode.Train -> Tabler.Outline.Train
                    Mode.Walk -> Tabler.Outline.Walk
                    else -> null
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private val operators = listOf(
    "Transperth", "Transwa", "Public Transport Authority", "Swan Transit",
    "Path Transit", "Transdev", "Airport Connect",
)

internal val ComboboxDemo = ComponentDemo(slug = "combobox") {
    var operator by remember { mutableStateOf<String?>("Transperth") }
    Box(Modifier.fillMaxWidth().height(MenuRoom)) {
        Combobox(
            value = operator,
            options = operators,
            onValueChange = { operator = it },
            label = "Operator",
            supporting = "Type to narrow a long list",
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

internal val textEditingDemos = listOf(
    TextFieldDemo,
    SearchFieldDemo,
    TextAreaDemo,
    SpecialisedFieldsDemo,
    SelectDemo,
    MultiSelectDemo,
    ComboboxDemo,
)
