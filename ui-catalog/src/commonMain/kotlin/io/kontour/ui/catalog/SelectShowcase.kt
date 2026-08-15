package io.kontour.ui.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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
import io.kontour.ui.components.selection.ColorSwatchPicker
import io.kontour.ui.components.text.Combobox
import io.kontour.ui.components.text.MultiSelect
import io.kontour.ui.components.text.Select
import io.kontour.ui.components.text.TextField
import io.kontour.ui.components.text.rememberImeChain
import io.kontour.ui.components.text.rememberSelectState
import io.kontour.ui.foundation.Surface
import io.kontour.ui.foundation.Text
import io.kontour.ui.overlay.OverlayHost
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
    Surface(modifier = modifier, color = Theme.colors.background) {
        Row(
            modifier = Modifier.padding(Theme.spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(Theme.spacing.lg),
        ) {
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
                Select(
                    value = null,
                    options = Departure.entries,
                    onValueChange = {},
                    label = "With an error",
                    optionLabel = { it.label },
                    errorMessage = "Pick when you want to travel",
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
                    values = modes,
                    options = Mode.entries,
                    onValuesChange = { modes = it },
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

                ColorSwatchPicker(
                    value = accent,
                    options = Accent.entries,
                    onValueChange = { accent = it },
                    swatchColor = { it.seed },
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

@Composable
private fun FormPanel(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.width(400.dp),
        verticalArrangement = Arrangement.spacedBy(Theme.spacing.md),
    ) {
        Text(
            text = title.uppercase(),
            style = Theme.typography.monoLabel,
            color = Theme.colors.accent,
        )
        // Each panel gets a host: an open select renders into the nearest one,
        // and one shared host would put every menu in the same coordinate space.
        Box(Modifier.fillMaxSize()) {
            OverlayHost {
                Column(verticalArrangement = Arrangement.spacedBy(Theme.spacing.md)) {
                    content()
                }
            }
        }
    }
}
