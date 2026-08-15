package io.kontour.ui.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Eye
import com.composables.icons.tabler.outline.MapPin
import com.composables.icons.tabler.outline.Search
import com.composables.icons.tabler.outline.X
import io.kontour.ui.components.text.EmailField
import io.kontour.ui.components.text.NumberField
import io.kontour.ui.components.text.PasswordField
import io.kontour.ui.components.text.PhoneField
import io.kontour.ui.components.text.SearchField
import io.kontour.ui.components.text.TextArea
import io.kontour.ui.components.text.TextField
import io.kontour.ui.components.text.TextFieldVariant
import io.kontour.ui.foundation.Surface
import io.kontour.ui.theme.Theme

/** Every text field, in every state. Source for the text-editing goldens. */
@Composable
fun TextShowcase(modifier: Modifier = Modifier) {
    Surface(modifier = modifier, color = Theme.colors.background) {
        Row(
            modifier = Modifier.padding(Theme.spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(Theme.spacing.lg),
        ) {
            Column(
                Modifier.width(420.dp),
                verticalArrangement = Arrangement.spacedBy(Theme.spacing.md),
            ) {
                Section("States") {
                    TextField(
                        state = rememberTextFieldState("Perth Station"),
                        label = "Destination",
                        placeholder = "Station, stop or address",
                        leadingIcon = Tabler.Outline.MapPin,
                    )
                    TextField(
                        state = rememberTextFieldState(),
                        label = "Empty with placeholder",
                        placeholder = "Nothing typed yet",
                    )
                    TextField(
                        state = rememberTextFieldState("j.smith@"),
                        label = "Email",
                        errorMessage = "That does not look like an email address",
                    )
                    TextField(
                        state = rememberTextFieldState("Read only"),
                        label = "Disabled",
                        enabled = false,
                        supporting = "Not available on this network",
                    )
                }

                Section("Filled variant") {
                    TextField(
                        state = rememberTextFieldState("Filled field"),
                        label = "Filled",
                        variant = TextFieldVariant.Filled,
                        supporting = "For dense forms and fields inside a card",
                    )
                }
            }

            Column(
                Modifier.width(420.dp),
                verticalArrangement = Arrangement.spacedBy(Theme.spacing.md),
            ) {
                Section("Search") {
                    SearchField(
                        state = rememberTextFieldState("Elizabeth Quay"),
                        searchIcon = Tabler.Outline.Search,
                        clearIcon = Tabler.Outline.X,
                        placeholder = "Search stops and routes",
                    )
                    SearchField(
                        state = rememberTextFieldState(),
                        searchIcon = Tabler.Outline.Search,
                        clearIcon = Tabler.Outline.X,
                        placeholder = "Search stops and routes",
                    )
                }

                Section("Specialised") {
                    PasswordField(
                        state = rememberTextFieldState("hunter2"),
                        label = "Password",
                        revealIcon = Tabler.Outline.Eye,
                        hideIcon = Tabler.Outline.Eye,
                    )
                    EmailField(
                        state = rememberTextFieldState("aaron@kontour.io"),
                        label = "Email",
                    )
                    PhoneField(
                        state = rememberTextFieldState("0412345678"),
                        label = "Mobile",
                        supporting = "Masked for display; stored as digits",
                    )
                    NumberField(
                        state = rememberTextFieldState("42"),
                        label = "Walk speed",
                        supporting = "Non-numeric keystrokes never arrive",
                    )
                }

                Section("Multi-line") {
                    TextArea(
                        state = rememberTextFieldState(
                            "The 960 was 12 minutes late and the app still showed it as on time."
                        ),
                        label = "What went wrong?",
                        minLines = 3,
                        maxLines = 6,
                    )
                }
            }
        }
    }
}
