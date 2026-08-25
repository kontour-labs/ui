package io.kontour.ui.samples

import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import io.kontour.ui.components.text.TextField
import io.kontour.ui.components.text.rememberImeChain
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.MapPin
import io.kontour.ui.components.text.Combobox
import io.kontour.ui.components.text.EmailField
import io.kontour.ui.components.text.MultiSelect
import io.kontour.ui.components.text.NumberField
import io.kontour.ui.components.text.PasswordField
import io.kontour.ui.components.text.PhoneField
import io.kontour.ui.components.text.SearchField
import io.kontour.ui.components.text.Select
import io.kontour.ui.components.text.TextArea
import io.kontour.ui.components.text.TextFieldVariant

@Composable
fun TextFieldBasics() {
    val query = rememberTextFieldState()

    TextField(state = query, label = "Where to?", placeholder = "Station, stop or address")
}

@Composable
fun ImeChainForm() {
    val from = rememberTextFieldState()
    val to = rememberTextFieldState()
    val note = rememberTextFieldState()

    val chain = rememberImeChain("from", "to", "note", onSubmit = { plan() })

    TextField(state = from, label = "From", imeChain = chain["from"])
    TextField(state = to, label = "To", imeChain = chain["to"])
    TextField(state = note, label = "Note", imeChain = chain["note"])
}

@Composable
fun TextFieldSlots() {
    val stop = rememberTextFieldState()

    // `label` is a `String?` rather than a slot on purpose: a floating label is
    // chrome, it animates between two positions, and `FieldScaffold` reads it to
    // give the control its accessible name — none of which a composable can do.
    TextField(
        state = stop,
        label = "Where to?",
        placeholder = "Station, stop or address",
        supporting = "We'll remember your last five",
        leadingIcon = Tabler.Outline.MapPin,
        variant = TextFieldVariant.Filled,
    )

    // An error message rather than a red border alone: colour by itself fails
    // WCAG 1.4.1, and the message is how a screen-reader user hears about it.
    TextField(
        state = stop,
        label = "Where to?",
        errorMessage = "We don't know that stop".takeIf { stop.text.isEmpty() },
    )
}

@Composable
fun TextAreaBasics() {
    val note = rememberTextFieldState()

    // Grows between the two bounds and then scrolls, so the form neither starts
    // enormous nor jumps a line every time the sentence wraps.
    TextArea(
        state = note,
        label = "What went wrong?",
        placeholder = "The 950 didn't turn up",
        minLines = 3,
        maxLines = 8,
    )
}

@Composable
fun SearchFieldBasics() {
    val query = rememberTextFieldState()

    // `onQuery` is debounced and `onSearch` is not: the first is for filtering
    // a list as the user types, the second for the action key. Wiring a network
    // call to every keystroke is the bug this split exists to prevent.
    SearchField(
        state = query,
        placeholder = "Search stops",
        onQuery = { openStop(it) },
        onSearch = { openStop(it) },
    )
}

@Composable
fun SelectBasics() {
    val modes = remember { listOf("Any", "Train", "Bus", "Ferry") }
    var mode by remember { mutableStateOf<String?>("Any") }

    Select(
        value = mode,
        options = modes,
        onValueChange = { mode = it },
        label = "Mode",
    )
}

@Composable
fun ComboboxBasics() {
    var stop by remember { mutableStateOf<String?>(null) }
    val names = remember { stops.map { it.name } }

    // A `Select` you can type into, for a list too long to scroll. `matches` is
    // where a fuzzier rule goes — matching on a stop code as well as its name.
    Combobox(
        value = stop,
        options = names,
        onValueChange = { stop = it },
        label = "From",
    )
}

@Composable
fun MultiSelectBasics() {
    val modes = remember { listOf("Train", "Bus", "Ferry", "Tram") }
    var chosen by remember { mutableStateOf(setOf("Train", "Bus")) }

    // The closed field summarises rather than listing everything: three fit,
    // and beyond that it says how many. `summary` overrides that.
    MultiSelect(
        value = chosen,
        options = modes,
        onValueChange = { chosen = it },
        label = "Modes",
    )
}

@Composable
fun SpecialisedFieldsBasics() {
    val password = rememberTextFieldState()
    val adults = rememberTextFieldState()
    val phone = rememberTextFieldState()
    val email = rememberTextFieldState()

    // Each is `TextField` with the keyboard, the autofill hint and the
    // transformation already right — the three things that get forgotten one
    // at a time.
    PasswordField(state = password, label = "Password", isNewPassword = true)
    NumberField(state = adults, label = "Adults", maxLength = 2)
    // Stored clean, displayed masked: the caller reads "0412345678".
    PhoneField(state = phone, label = "Mobile")
    EmailField(state = email, label = "Email")
}
