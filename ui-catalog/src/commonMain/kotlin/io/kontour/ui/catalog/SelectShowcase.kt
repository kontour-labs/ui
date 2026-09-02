package io.kontour.ui.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Bus
import com.composables.icons.tabler.outline.Sparkles
import com.composables.icons.tabler.outline.Train
import com.composables.icons.tabler.outline.Walk
import io.kontour.ui.components.selection.ColourSwatchPicker
import io.kontour.ui.components.text.Combobox
import io.kontour.ui.components.text.MultiSelect
import io.kontour.ui.components.text.Select
import io.kontour.ui.components.text.TextField
import io.kontour.ui.components.text.rememberImeChain
import io.kontour.ui.components.text.rememberSelectState
import io.kontour.ui.foundation.Surface
import io.kontour.ui.foundation.Text
import io.kontour.ui.theme.Theme
import androidx.compose.foundation.text.input.rememberTextFieldState

private enum class Departure(val label: String) {
    DepartAt("Depart at"),
    ArriveBy("Arrive by"),
    LastPossible("Last possible"),
}

private enum class Mode(val label: String) {
    Bus("Bus"), Train("Train"), Ferry("Ferry"), Walk("Walking"), Bike("Cycling")
}

private enum class Accent(val label: String, val seed: Color?) {
    System("Match system", null),
    Anyways("Anyways", Color(0xFF6D28D9)),
    Ocean("Ocean", Color(0xFF0E7490)),
    Forest("Forest", Color(0xFF15803D)),
    Sunset("Sunset", Color(0xFFC2410C)),
    Rose("Rose", Color(0xFFBE123C)),
    Slate("Slate", Color(0xFF475569)),
}

private val operators = listOf(
    "Transperth", "Transwa", "Public Transport Authority", "Swan Transit",
    "Path Transit", "Transdev", "Airport Connect",
)

/** Selects, comboboxes and the swatch picker. Source for the form goldens. */
@Composable
fun SelectShowcase(modifier: Modifier = Modifier) {
    Surface(modifier = modifier, colour = Theme.colours.background) {
        Panels {
            FormPanel("Select, closed") {
                var departure by remember { mutableStateOf(Departure.DepartAt) }
                var mode by remember { mutableStateOf<Mode?>(null) }

                Select(
                    value = departure,
                    options = Departure.entries,
                    onValueChange = { departure = it },
                    label = "When",
                    optionLabel = { it.label },
                )
                Select(
                    value = mode,
                    options = Mode.entries,
                    onValueChange = { mode = it },
                    label = "Preferred mode",
                    placeholder = "No preference",
                    optionLabel = { it.label },
                    supporting = "Used to break ties between equal routes",
                )
                Select(
                    value = Departure.ArriveBy,
                    options = Departure.entries,
                    onValueChange = {},
                    label = "Disabled",
                    optionLabel = { it.label },
                    enabled = false,
                )
                // Live, and still shows its error: the message is the exhibit,
                // and a field you cannot answer cannot show the error clearing.
                val whenToLeave = seed<Departure?>(null)
                Select(
                    value = whenToLeave.value,
                    options = Departure.entries,
                    onValueChange = { whenToLeave.value = it },
                    label = "With an error",
                    optionLabel = { it.label },
                    errorMessage = "Pick when you want to travel"
                        .takeIf { whenToLeave.value == null },
                )
            }

            FormPanel("Select, open") {
                var departure by remember { mutableStateOf(Departure.ArriveBy) }
                Select(
                    value = departure,
                    options = Departure.entries,
                    onValueChange = { departure = it },
                    label = "When",
                    optionLabel = { it.label },
                    // The menu matches the field's width — the thing worth
                    // seeing here, and the reason Select anchors to the frame
                    // rather than to the slot the menu is declared in.
                    state = rememberSelectState(initiallyExpanded = true),
                )
            }

            FormPanel("Multi-select and combobox") {
                var modes by remember { mutableStateOf(setOf(Mode.Bus, Mode.Train)) }
                var operator by remember { mutableStateOf<String?>("Transperth") }

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
                )
                Combobox(
                    value = operator,
                    options = operators,
                    onValueChange = { operator = it },
                    label = "Operator",
                    supporting = "Type to narrow a long list",
                )
            }

            FormPanel("Swatches and a chained form") {
                var accent by remember { mutableStateOf(Accent.Anyways) }
                val from = rememberTextFieldState("Perth Underground")
                val to = rememberTextFieldState()
                val chain = rememberImeChain("from", "to")

                ColourSwatchPicker(
                    value = accent,
                    options = Accent.entries,
                    onValueChange = { accent = it },
                    swatchColour = { it.seed },
                    swatchLabel = { it.label },
                    automaticIcon = Tabler.Outline.Sparkles,
                )
                TextField(
                    state = from,
                    label = "From",
                    imeChain = chain["from"],
                )
                TextField(
                    state = to,
                    label = "To",
                    placeholder = "Where to?",
                    imeChain = chain["to"],
                )
            }
        }
    }
}

/**
 * Tall enough for a panel's fields plus an open menu below the last of them.
 *
 * A fixed number rather than a fill: see the note in [FormPanel].
 */
private val FormPanelHeight = 420.dp

@Composable
private fun FormPanel(title: String, content: @Composable () -> Unit) {
    Panel(width = 400.dp, spacing = Theme.spacing.md) {
        Text(
            text = title.uppercase(),
            style = Theme.typography.monoLabel,
            colour = Theme.colours.accent.solid,
        )
        // No host of its own. An open select renders into the *root* one from
        // `Catalog`, which is outside the page's scroll and fills the window.
        //
        // Each panel used to install one, to give the overlay bounds to position
        // against — the page is inside a `verticalScroll`, so a host placed here
        // has an unbounded height and nothing to measure, which is what the Forms
        // page originally crashed on. Pinning a height fixed the crash and bought
        // a worse bug: `LocalOverlayHost` is a static local, so the nearest host
        // wins, and the dismiss scrim `fillMaxSize()`d into a 400x420 box. A tap
        // anywhere outside that rectangle never reached it, so a select would
        // only close if you clicked in its own column.
        //
        // The height stays — it keeps the panels aligned, and it is what gives an
        // open menu room to render without the row below it moving.
        Box(Modifier.fillMaxWidth().height(FormPanelHeight)) {
            Column(verticalArrangement = Arrangement.spacedBy(Theme.spacing.md)) {
                content()
            }
        }
    }
}
