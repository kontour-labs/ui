package io.kontour.ui.samples

import io.kontour.ui.components.text.PhoneFormat
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import io.kontour.ui.components.text.TextField
import io.kontour.ui.components.text.rememberImeChain
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import io.kontour.icons.tabler.Tabler
import io.kontour.icons.tabler.outline.Eye
import io.kontour.icons.tabler.outline.MapPin
import androidx.compose.foundation.layout.Column
import io.kontour.ui.components.display.Avatar
import io.kontour.ui.components.display.AvatarSize
import io.kontour.ui.components.text.Combobox
import io.kontour.ui.components.text.EmailField
import io.kontour.ui.components.text.MultiSelect
import io.kontour.ui.components.text.NumberField
import io.kontour.ui.components.text.PasswordField
import io.kontour.ui.components.text.PhoneField
import io.kontour.ui.components.text.SearchField
import io.kontour.ui.components.text.SecretField
import io.kontour.ui.components.text.Select
import io.kontour.ui.components.text.WheelSelect
import io.kontour.ui.components.text.TextArea
import io.kontour.ui.components.text.TextFieldVariant
import io.kontour.ui.foundation.Text
import io.kontour.ui.theme.Theme

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

    // `label` is a `String?` rather than a slot on purpose: a field's label is
    // chrome, drawn above the frame in the field's own style, and `FieldScaffold` reads it to
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
        lineLimits = TextFieldLineLimits.MultiLine(minHeightInLines = 3, maxHeightInLines = 8),
    )
}

@Composable
fun CodeTextArea() {
    val config = rememberTextFieldState("route = 960\nretry = 0")
    val stop = rememberTextFieldState()

    // The mono face, so columns line up and a 0 is not an O, and a keyboard
    // that neither corrects nor capitalises: either would change what was meant.
    TextArea(state = config, label = "Feed override", code = true)
    TextField(state = stop, label = "Stop code", placeholder = "per-10352", code = true)
}

@Composable
fun SecretFieldBasics() {
    val apiKey = rememberTextFieldState()

    // Masked and revealed as a password is, and not one to the platform: no
    // autofill hint, so nothing offers to save it as the reader's login.
    SecretField(state = apiKey, label = "API key", revealIcon = Tabler.Outline.Eye)
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
fun SelectWithOther() {
    val reasons = remember { listOf("Running late", "Cancelled", "Too full") }
    var reason by remember { mutableStateOf<String?>(null) }
    var otherReason by remember { mutableStateOf<String?>(null) }

    // "Something else" ends the menu; picked, a box opens in the field to
    // type in. The answer is `otherReason ?: reason`.
    Select(
        value = reason,
        options = reasons,
        onValueChange = { reason = it },
        label = "Reason",
        otherText = otherReason,
        onOtherTextChange = { otherReason = it },
        otherLabel = "Something else",
        otherPlaceholder = "What happened?",
    )
}

@Composable
fun SelectOptionContent() {
    var rider by remember { mutableStateOf<Rider?>(household.first()) }

    // A face, a name and a home stop in the menu; the face and the name in the
    // field, so it stands as tall as the fields beside it. The name is still
    // what a screen reader says for either, whatever the slot draws.
    Select(
        value = rider,
        options = household,
        onValueChange = { rider = it },
        label = "Booked for",
        optionLabel = { it.name },
        valueContent = { person ->
            Avatar(name = person.name, size = AvatarSize.XSmall)
            +person.name
        },
    ) { person ->
        Avatar(name = person.name, size = AvatarSize.Small)
        Column {
            Text(person.name)
            Text(person.home, style = Theme.typography.bodySmall, colour = Theme.colours.contentMuted)
        }
    }
}

@Composable
fun WheelSelectBasics() {
    var passengers by remember { mutableStateOf(2) }

    // A tap opens the wheel over the field; a drag up or down on the closed
    // field turns it straight away, and it folds back once it comes to rest.
    WheelSelect(
        value = passengers,
        options = (1..9).toList(),
        onValueChange = { passengers = it },
        label = "Passengers",
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
    PasswordField(state = password, label = "Password", newPassword = true)
    NumberField(state = adults, label = "Adults", maxLength = 2)
    // Stored clean, displayed masked: the caller reads "0412345678". The
    // grouping is the app's to name — the field guesses no country.
    PhoneField(state = phone, label = "Mobile", format = PhoneFormat.AustralianMobile)
    EmailField(state = email, label = "Email")
}
