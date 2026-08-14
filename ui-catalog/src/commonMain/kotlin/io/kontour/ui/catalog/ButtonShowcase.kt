package io.kontour.ui.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.composables.icons.fontawesome.FontAwesome
import com.composables.icons.fontawesome.solid.ChevronRight
import com.composables.icons.fontawesome.solid.LocationArrow
import com.composables.icons.fontawesome.solid.Plus
import com.composables.icons.fontawesome.solid.Star
import com.composables.icons.fontawesome.solid.Times
import io.kontour.ui.components.action.Button
import io.kontour.ui.components.action.ButtonSize
import io.kontour.ui.components.action.ButtonVariant
import io.kontour.ui.components.action.ExtendedFloatingActionButton
import io.kontour.ui.components.action.FabSize
import io.kontour.ui.components.action.FloatingActionButton
import io.kontour.ui.components.action.IconButton
import io.kontour.ui.components.action.IconToggleButton
import io.kontour.ui.foundation.Surface
import io.kontour.ui.foundation.Text
import io.kontour.ui.theme.Theme

/**
 * Every action component, in every state.
 *
 * The source for the action screenshot goldens, and the page to open when a
 * variant's colours or a size's metrics change.
 */
@Composable
fun ButtonShowcase(modifier: Modifier = Modifier) {
    Surface(modifier = modifier, color = Theme.colors.background) {
        Column(
            modifier = Modifier.padding(Theme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Theme.spacing.lg),
        ) {
            Section("Variants") {
                Row(horizontalArrangement = Arrangement.spacedBy(Theme.spacing.xs)) {
                    Button("Primary", {}, variant = ButtonVariant.Primary)
                    Button("Secondary", {}, variant = ButtonVariant.Secondary)
                    Button("Tertiary", {}, variant = ButtonVariant.Tertiary)
                    Button("Ghost", {}, variant = ButtonVariant.Ghost)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Theme.spacing.xs)) {
                    Button("Destructive", {}, variant = ButtonVariant.Destructive)
                    Button("Destructive ghost", {}, variant = ButtonVariant.DestructiveGhost)
                }
            }

            Section("Sizes") {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button("XSmall", {}, size = ButtonSize.XSmall)
                    Button("Small", {}, size = ButtonSize.Small)
                    Button("Medium", {}, size = ButtonSize.Medium)
                    Button("Large", {}, size = ButtonSize.Large)
                    Button("XLarge", {}, size = ButtonSize.XLarge)
                }
            }

            Section("States") {
                Row(horizontalArrangement = Arrangement.spacedBy(Theme.spacing.xs)) {
                    Button("Enabled", {})
                    Button("Disabled", {}, enabled = false)
                    Button("Loading", {}, loading = true)
                    Button("Secondary off", {}, variant = ButtonVariant.Secondary, enabled = false)
                }
            }

            Section("Full width") {
                Button("Plan a trip", {}, size = ButtonSize.Large, fillMaxWidth = true)
            }

            Section("Icon buttons") {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(FontAwesome.Solid.Times, "Close", {})
                    IconButton(FontAwesome.Solid.Times, "Close", {}, variant = ButtonVariant.Tertiary)
                    IconButton(FontAwesome.Solid.Times, "Close", {}, variant = ButtonVariant.Secondary)
                    IconButton(FontAwesome.Solid.Times, "Close", {}, enabled = false)
                    IconButton(FontAwesome.Solid.ChevronRight, "Expand", {}, rotation = 90f)
                    IconToggleButton(FontAwesome.Solid.Star, "Favourite", checked = true, {})
                    IconToggleButton(FontAwesome.Solid.Star, "Favourite", checked = false, {})
                }
            }

            Section("Floating actions") {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Theme.spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FloatingActionButton(FontAwesome.Solid.Plus, "Add", {}, size = FabSize.Small)
                    FloatingActionButton(FontAwesome.Solid.Plus, "Add", {}, size = FabSize.Medium)
                    FloatingActionButton(FontAwesome.Solid.Plus, "Add", {}, size = FabSize.Large)
                    ExtendedFloatingActionButton(
                        icon = FontAwesome.Solid.LocationArrow,
                        text = "Start trip",
                        contentDescription = "Start trip",
                        onClick = {},
                    )
                    ExtendedFloatingActionButton(
                        icon = FontAwesome.Solid.LocationArrow,
                        text = "Start trip",
                        contentDescription = "Start trip",
                        expanded = false,
                        onClick = {},
                    )
                }
            }
        }
    }
}

@Composable
internal fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Theme.spacing.xs)) {
        Text(
            text = title.uppercase(),
            style = Theme.typography.monoLabel,
            color = Theme.colors.accent,
        )
        content()
    }
}
