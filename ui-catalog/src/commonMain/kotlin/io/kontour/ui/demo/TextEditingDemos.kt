package io.kontour.ui.demo

import io.kontour.ui.components.text.PhoneFormat
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.kontour.icons.tabler.Tabler
import io.kontour.icons.tabler.outline.Bus
import io.kontour.icons.tabler.outline.Eye
import io.kontour.icons.tabler.outline.MapPin
import io.kontour.icons.tabler.outline.Search
import io.kontour.icons.tabler.outline.Train
import io.kontour.icons.tabler.outline.Walk
import io.kontour.icons.tabler.outline.X
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
import io.kontour.ui.components.text.TextField
import io.kontour.ui.components.text.TextFieldVariant
import io.kontour.ui.components.text.rememberImeChain
import io.kontour.ui.foundation.Text
import io.kontour.ui.theme.Theme

private val fieldVariant =
    Knob.Choice("Variant", TextFieldVariant.entries.toList(), TextFieldVariant.Outlined)
private val fieldError = Knob.Flag("Error")

/**
 * Selectable and focusable, but not editable — which is the difference from
 * `enabled = false` and the reason both exist.
 *
 * A confirmed booking's origin is a value the user should be able to select and
 * copy and cannot change; disabling the field would grey it out and take it out
 * of the focus order, saying "this is unavailable" about something that is
 * simply settled.
 */
private val fieldReadOnly = Knob.Flag("Read-only")
private val fieldEnabled = Knob.Flag("Enabled", initial = true)


internal val TextFieldDemo = ComponentDemo(
    slug = "text-field",
    knobs = listOf(fieldVariant, fieldError, fieldReadOnly, fieldEnabled),
) {
    val state = rememberTextFieldState("Perth Station")
    TextField(
        state = state,
        label = "Destination",
        placeholder = "Station, stop or address",
        leadingIcon = Tabler.Outline.MapPin,
        variant = this[fieldVariant],
        errorMessage = "That stop is not on this network".takeIf { this[fieldError] },
        readOnly = this[fieldReadOnly],
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

/**
 * The mono face, and a keyboard that leaves what is typed alone. It swaps the
 * prose for a snippet, since the difference is the columns lining up and the
 * 0 that is not an O.
 */
private val areaCode = Knob.Flag("Code")

internal val TextAreaDemo = ComponentDemo(slug = "text-area", knobs = listOf(areaCode)) {
    val code = this[areaCode]
    val state = remember(code) {
        TextFieldState(
            if (code) {
                "route  = 960\nfrom   = \"Curtin Central\"\nto     = \"Perth Busport\"\nretry  = 0"
            } else {
                "The 960 was 12 minutes late and the app still showed it as on time."
            },
        )
    }
    TextArea(
        state = state,
        label = if (code) "Feed override" else "What went wrong?",
        // Clear the text to see it: at the top, where the first line goes.
        placeholder = if (code) "key = value" else "Which service, where, and when",
        lineLimits = TextFieldLineLimits.MultiLine(minHeightInLines = 3, maxHeightInLines = 6),
        code = code,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** A decimal point, for a walking speed rather than a passenger count. */
private val numberDecimal = Knob.Flag("Decimals")

/** A leading minus, for a temperature or a balance rather than a distance. */
private val numberNegative = Knob.Flag("Negatives")
// Tells the platform's password manager to offer a *generated* password
// rather than a saved one, and turns off autocorrect and suggestions. Nothing
// about it is visible, which is exactly why it wants to be pressable: the
// difference is what the keyboard and the manager do.
private val passwordIsNew = Knob.Flag("Sign-up field")

/**
 * The character just typed shows for a moment before it becomes a dot, the way
 * a phone's own password fields do. Off masks every character as it arrives.
 */
private val passwordRevealLast = Knob.Flag("Show last typed", initial = true)

internal val SpecialisedFieldsDemo = ComponentDemo(
    slug = "specialised-fields",
    knobs = listOf(numberDecimal, numberNegative, passwordIsNew, passwordRevealLast),
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Theme.spacing.md),
    ) {
        PasswordField(
            state = rememberTextFieldState("hunter2"),
            label = if (this@ComponentDemo[passwordIsNew]) "Choose a password" else "Password",
            newPassword = this@ComponentDemo[passwordIsNew],
            revealLastTyped = this@ComponentDemo[passwordRevealLast],
            revealIcon = Tabler.Outline.Eye,
            modifier = Modifier.fillMaxWidth(),
        )
        // Masked as a password is, and not one: no autofill, no "save
        // password", an ASCII keyboard and the mono face.
        SecretField(
            state = rememberTextFieldState("kt_live_51Hx7Qp"),
            label = "API key",
            revealLastTyped = this@ComponentDemo[passwordRevealLast],
            revealIcon = Tabler.Outline.Eye,
            modifier = Modifier.fillMaxWidth(),
        )
        EmailField(
            state = rememberTextFieldState("aaron@kontour.io"),
            label = "Email",
            modifier = Modifier.fillMaxWidth(),
        )
        // An Australian app, so an Australian mobile's grouping. The field
        // guesses no country by default: a mask for the wrong one is worse
        // than none.
        PhoneField(
            state = rememberTextFieldState("0412345678"),
            label = "Mobile",
            supporting = "Masked for display; stored as digits",
            modifier = Modifier.fillMaxWidth(),
            format = PhoneFormat.AustralianMobile,
        )
        // The two knobs are the field: what it *will not* accept is the whole
        // of what a number field is, and both refusals are silent — a rejected
        // keystroke never reaches the state, so there is nothing to see except
        // by trying to type one.
        NumberField(
            state = rememberTextFieldState("42"),
            label = "Walk speed",
            supporting = if (this@ComponentDemo[numberDecimal]) {
                "A decimal point is allowed; other keystrokes never arrive"
            } else {
                "Non-numeric keystrokes never arrive"
            },
            allowDecimal = this@ComponentDemo[numberDecimal],
            allowNegative = this@ComponentDemo[numberNegative],
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

internal val SelectDemo = ComponentDemo(slug = "select", knobs = listOf(fieldVariant)) {
    var departure by remember { mutableStateOf(Departure.DepartAt) }
    Box(Modifier.fillMaxWidth().height(MenuRoom)) {
        Select(
            value = departure,
            options = Departure.entries,
            onValueChange = { departure = it },
            label = "When",
            optionLabel = { it.label },
            variant = this@ComponentDemo[fieldVariant],
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** A wheel that wraps round, as hours and months do. */
private val wheelInfinite = Knob.Flag("Wraps round")

/** Whether a drag on the closed field turns the wheel, or only a tap opens it. */
private val wheelDrag = Knob.Flag("Drag to pick", initial = true)

internal val WheelSelectDemo = ComponentDemo(
    slug = "wheel-select",
    knobs = listOf(wheelDrag, wheelInfinite, fieldVariant),
) {
    var minutes by remember { mutableStateOf(15) }
    // Centred, so the wheel has room to open either side of the field; see
    // `MenuRoom` for why the card keeps that room whether it is open or not.
    Box(Modifier.fillMaxWidth().height(MenuRoom), contentAlignment = Alignment.Center) {
        WheelSelect(
            value = minutes,
            options = (0..55 step 5).toList(),
            onValueChange = { minutes = it },
            label = "Remind me before",
            optionLabel = { "$it min" },
            dragToPick = this@ComponentDemo[wheelDrag],
            infinite = this@ComponentDemo[wheelInfinite],
            variant = this@ComponentDemo[fieldVariant],
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

internal val MultiSelectDemo = ComponentDemo(slug = "multi-select", knobs = listOf(fieldVariant)) {
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
            variant = this@ComponentDemo[fieldVariant],
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private val operators = listOf(
    "Transperth", "Transwa", "Public Transport Authority", "Swan Transit",
    "Path Transit", "Transdev", "Airport Connect",
)

internal val ComboboxDemo = ComponentDemo(slug = "combobox", knobs = listOf(fieldVariant)) {
    var operator by remember { mutableStateOf<String?>("Transperth") }
    Box(Modifier.fillMaxWidth().height(MenuRoom)) {
        Combobox(
            value = operator,
            options = operators,
            onValueChange = { operator = it },
            label = "Operator",
            supporting = "Type to narrow a long list",
            variant = this@ComponentDemo[fieldVariant],
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

internal val ImeChainDemo = ComponentDemo(slug = "ime-chain") {
    val from = rememberTextFieldState("Perth Underground")
    val to = rememberTextFieldState()
    val chain = rememberImeChain("from", "to")
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Theme.spacing.md)) {
        TextField(
            state = from,
            label = "From",
            imeChain = chain["from"],
            modifier = Modifier.fillMaxWidth(),
        )
        TextField(
            state = to,
            label = "To",
            placeholder = "Where to?",
            imeChain = chain["to"],
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "The first field's action key says Next; the last one says Done.",
            style = Theme.typography.labelSmall,
            colour = Theme.colours.contentMuted,
        )
    }
}

internal val textEditingDemos = listOf(
    TextFieldDemo,
    SearchFieldDemo,
    TextAreaDemo,
    SpecialisedFieldsDemo,
    SelectDemo,
    WheelSelectDemo,
    MultiSelectDemo,
    ComboboxDemo,
    ImeChainDemo,
)
