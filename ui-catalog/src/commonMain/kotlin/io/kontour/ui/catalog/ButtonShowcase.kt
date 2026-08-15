package io.kontour.ui.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.state.ToggleableState
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.ChevronRight
import com.composables.icons.tabler.outline.Navigation
import com.composables.icons.tabler.outline.Plus
import com.composables.icons.tabler.outline.Star
import com.composables.icons.tabler.outline.X
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
                    IconButton(Tabler.Outline.X, "Close", {})
                    IconButton(Tabler.Outline.X, "Close", {}, variant = ButtonVariant.Tertiary)
                    IconButton(Tabler.Outline.X, "Close", {}, variant = ButtonVariant.Secondary)
                    IconButton(Tabler.Outline.X, "Close", {}, enabled = false)
                    IconButton(Tabler.Outline.ChevronRight, "Expand", {}, rotation = 90f)
                    // The one control here that was genuinely broken as a demo:
                    // a toggle wired to `{}` never toggles, unlike a button,
                    // which at least still presses.
                    val favourited = seed(true)
                    val watching = seed(false)
                    IconToggleButton(
                        Tabler.Outline.Star,
                        "Favourite",
                        checked = favourited.value,
                        onCheckedChange = { favourited.value = it },
                    )
                    IconToggleButton(
                        Tabler.Outline.Star,
                        "Favourite",
                        checked = watching.value,
                        onCheckedChange = { watching.value = it },
                    )
                }
            }

            Section("Floating actions") {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Theme.spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FloatingActionButton(Tabler.Outline.Plus, "Add", {}, size = FabSize.Small)
                    FloatingActionButton(Tabler.Outline.Plus, "Add", {}, size = FabSize.Medium)
                    FloatingActionButton(Tabler.Outline.Plus, "Add", {}, size = FabSize.Large)
                    ExtendedFloatingActionButton(
                        icon = Tabler.Outline.Navigation,
                        label = "Start trip",
                        contentDescription = "Start trip",
                        onClick = {},
                    )
                    ExtendedFloatingActionButton(
                        icon = Tabler.Outline.Navigation,
                        label = "Start trip",
                        contentDescription = "Start trip",
                        expanded = false,
                        onClick = {},
                    )
                }
            }
        }
    }
}

/**
 * State for a showcase control, seeded from what it used to hardcode.
 *
 * The showcases are golden sources *and* a place to try the components, and
 * those pulled against each other: a hardcoded `checked = true` captures a state
 * a golden could not otherwise reach, and a dead `onCheckedChange = {}` makes
 * the control inert. Seeding from the old literal keeps every golden
 * byte-for-byte and makes the control live, which is not a trade at all.
 *
 * A `MutableState` rather than a wrapper composable on purpose: a showcase
 * should show the component's real API, not a catalog-shaped stand-in for it.
 */
@Composable
internal fun <T> seed(value: T): MutableState<T> = remember { mutableStateOf(value) }

/** Cycles a tri-state checkbox the way a real one would. */
internal fun ToggleableState.next(): ToggleableState = when (this) {
    ToggleableState.Off -> ToggleableState.On
    ToggleableState.On -> ToggleableState.Indeterminate
    ToggleableState.Indeterminate -> ToggleableState.Off
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
