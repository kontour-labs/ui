package io.kontour.ui.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.foundation.layout.Row
import com.composables.icons.tabler.outline.CurrentLocation
import com.composables.icons.tabler.outline.Minus
import com.composables.icons.tabler.outline.Stack
import io.kontour.ui.components.action.ButtonGroup
import io.kontour.ui.components.action.FloatingActionButton
import io.kontour.ui.components.action.Toolbar
import io.kontour.ui.components.action.ToolbarDivider
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
                    Button(onClick = tap("Primary"), variant = ButtonVariant.Primary) { +"Primary" }
                    Button(onClick = tap("Secondary"), variant = ButtonVariant.Secondary) { +"Secondary" }
                    Button(onClick = tap("Tertiary"), variant = ButtonVariant.Tertiary) { +"Tertiary" }
                    Button(onClick = tap("Ghost"), variant = ButtonVariant.Ghost) { +"Ghost" }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Theme.spacing.xs)) {
                    Button(onClick = tap("Accent"), variant = ButtonVariant.Accent) { +"Accent" }
                    Button(onClick = tap("Destructive"), variant = ButtonVariant.Destructive) { +"Destructive" }
                    Button(onClick = tap("Destructive ghost"), variant = ButtonVariant.DestructiveGhost) {
                        +"Destructive ghost"
                    }
                }
            }

            Section("Sizes") {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(onClick = tap("XSmall"), size = ButtonSize.XSmall) { +"XSmall" }
                    Button(onClick = tap("Small"), size = ButtonSize.Small) { +"Small" }
                    Button(onClick = tap("Medium"), size = ButtonSize.Medium) { +"Medium" }
                    Button(onClick = tap("Large"), size = ButtonSize.Large) { +"Large" }
                    Button(onClick = tap("XLarge"), size = ButtonSize.XLarge) { +"XLarge" }
                }
            }

            Section("States") {
                // The only specimens in this file that go nowhere when pressed,
                // and all four on purpose: a disabled button and a button
                // mid-request are supposed to swallow the press. Everything else
                // in the catalog is wired to something you can see.
                Row(horizontalArrangement = Arrangement.spacedBy(Theme.spacing.xs)) {
                    Button(onClick = tap("Enabled")) { +"Enabled" }
                    Button(onClick = {}, enabled = false) { +"Disabled" }
                    Button(onClick = {}, loading = true) { +"Loading" }
                    Button(onClick = {}, variant = ButtonVariant.Secondary, enabled = false) {
                        +"Secondary off"
                    }
                }
            }

            Section("Full width") {
                Button(
                    onClick = tap("Plan a trip"),
                    modifier = Modifier.fillMaxWidth(),
                    size = ButtonSize.Large,
                ) { +"Plan a trip" }
            }

            Section("Icon buttons") {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(Tabler.Outline.X, "Close", tap("Close"))
                    IconButton(Tabler.Outline.X, "Close", tap("Close"), variant = ButtonVariant.Tertiary)
                    IconButton(Tabler.Outline.X, "Close", tap("Close"), variant = ButtonVariant.Secondary)
                    IconButton(Tabler.Outline.X, "Close", {}, enabled = false)
                    IconButton(Tabler.Outline.ChevronRight, "Expand", tap("Expand"), rotation = 90f)
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
                    FloatingActionButton(Tabler.Outline.Plus, "Add", tap("Add"), size = FabSize.Small)
                    FloatingActionButton(Tabler.Outline.Plus, "Add", tap("Add"), size = FabSize.Medium)
                    FloatingActionButton(Tabler.Outline.Plus, "Add", tap("Add"), size = FabSize.Large)
                    // Each one collapses and expands itself, which is what the
                    // parameter is for: a real extended FAB collapses as the
                    // page scrolls and grows back at the top. Seeded from what
                    // the pair used to hardcode, so the golden still shows both.
                    val extended = seed(true)
                    val collapsed = seed(false)
                    ExtendedFloatingActionButton(
                        icon = Tabler.Outline.Navigation,
                        contentDescription = "Start trip",
                        expanded = extended.value,
                        onClick = { extended.value = !extended.value },
                    ) { +"Start trip" }
                    ExtendedFloatingActionButton(
                        icon = Tabler.Outline.Navigation,
                        contentDescription = "Start trip",
                        expanded = collapsed.value,
                        onClick = { collapsed.value = !collapsed.value },
                    ) { +"Start trip" }
                }
            }

            Section("Button group") {
                // The builder collects rather than composes, so a `@Composable`
                // helper like `tap` cannot be called inside it. Hoisted, which
                // is what a caller has to do too.
                val zoomOut = tap("Zoom out")
                val recentre = tap("Recentre")
                val zoomIn = tap("Zoom in")
                val day = tap("Day")
                val week = tap("Week")
                val month = tap("Month")
                val only = tap("Only")

                Row(
                    horizontalArrangement = Arrangement.spacedBy(Theme.spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ButtonGroup {
                        item(onClick = zoomOut, contentDescription = "Zoom out", icon = Tabler.Outline.Minus)
                        item(onClick = recentre, contentDescription = "Recentre", icon = Tabler.Outline.CurrentLocation)
                        item(onClick = zoomIn, contentDescription = "Zoom in", icon = Tabler.Outline.Plus)
                    }
                    // Labels rather than icons, and one of them unavailable —
                    // a group has to be able to grey one button without the
                    // caller hiding it and changing the cluster's width.
                    ButtonGroup(variant = ButtonVariant.Secondary) {
                        item(onClick = day) { +"Day" }
                        item(onClick = week) { +"Week" }
                        item(onClick = month, enabled = false) { +"Month" }
                    }
                    // A group of one, which is the case index arithmetic gets
                    // wrong: both ends round, no seams.
                    ButtonGroup {
                        item(onClick = only, contentDescription = "Only", icon = Tabler.Outline.Star)
                    }
                }
            }

            Section("Toolbar") {
                val out = tap("Zoom out")
                val into = tap("Zoom in")

                Toolbar {
                    ButtonGroup {
                        item(onClick = out, contentDescription = "Zoom out", icon = Tabler.Outline.Minus)
                        item(onClick = into, contentDescription = "Zoom in", icon = Tabler.Outline.Plus)
                    }
                    ToolbarDivider()
                    IconButton(Tabler.Outline.Stack, "Map layers", tap("Map layers"))
                    IconButton(Tabler.Outline.CurrentLocation, "My location", tap("My location"))
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
            color = Theme.colors.accent.solid,
        )
        content()
    }
}
