package io.kontour.ui.demo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Bell
import com.composables.icons.tabler.outline.ChevronRight
import com.composables.icons.tabler.outline.CurrentLocation
import com.composables.icons.tabler.outline.Minus
import com.composables.icons.tabler.outline.Navigation
import com.composables.icons.tabler.outline.Plus
import com.composables.icons.tabler.outline.Stack
import com.composables.icons.tabler.outline.Star
import io.kontour.ui.components.action.Button
import io.kontour.ui.components.action.ButtonGroup
import io.kontour.ui.components.action.ButtonSize
import io.kontour.ui.components.action.ButtonVariant
import io.kontour.ui.components.action.ExtendedFloatingActionButton
import io.kontour.ui.components.action.FabMenu
import io.kontour.ui.components.action.FabMenuLayout
import io.kontour.ui.components.action.FabSize
import io.kontour.ui.components.action.FloatingActionButton
import io.kontour.ui.components.action.IconButton
import io.kontour.ui.components.action.IconToggleButton
import io.kontour.ui.components.action.SplitButton
import io.kontour.ui.components.action.Toolbar
import io.kontour.ui.components.action.ToolbarDivider
import io.kontour.ui.components.display.Spinner
import io.kontour.ui.foundation.Text
import io.kontour.ui.theme.Theme

// --- Button ---------------------------------------------------------------

private val buttonVariant = Knob.Choice("Variant", ButtonVariant.entries.toList())
private val buttonSize = Knob.Choice("Size", ButtonSize.entries.toList(), ButtonSize.Medium)
private val buttonLoading = Knob.Flag("Loading")
private val buttonEnabled = Knob.Flag("Enabled", initial = true)

internal val ButtonDemo = ComponentDemo(
    slug = "button",
    knobs = listOf(buttonVariant, buttonSize, buttonLoading, buttonEnabled),
) {
    Button(
        onClick = { echo("Pressed ${this[buttonVariant]}") },
        variant = this[buttonVariant],
        size = this[buttonSize],
        loading = this[buttonLoading],
        enabled = this[buttonEnabled],
    ) {
        +"Plan a trip"
    }
}

// --- IconButton -----------------------------------------------------------

private val iconButtonVariant =
    Knob.Choice("Variant", ButtonVariant.entries.toList(), ButtonVariant.Ghost)
private val iconButtonSize = Knob.Choice("Size", ButtonSize.entries.toList(), ButtonSize.Medium)
private val iconButtonRotated = Knob.Flag("Rotated")
private val iconButtonEnabled = Knob.Flag("Enabled", initial = true)

internal val IconButtonDemo = ComponentDemo(
    slug = "icon-button",
    knobs = listOf(iconButtonVariant, iconButtonSize, iconButtonRotated, iconButtonEnabled),
) {
    IconButton(
        icon = Tabler.Outline.ChevronRight,
        contentDescription = "Expand",
        onClick = { echo("Expanded") },
        variant = this[iconButtonVariant],
        size = this[iconButtonSize],
        // The rotation animates, which is the parameter's whole point — a
        // chevron that turns says which way things are about to move.
        rotation = if (this[iconButtonRotated]) 90f else 0f,
        enabled = this[iconButtonEnabled],
    )
}

// --- IconToggleButton -----------------------------------------------------

/**
 * Draws a slash across the glyph when unchecked, rather than beside it.
 *
 * For a mute or a visibility toggle, where the *off* state is the one that
 * needs a shape of its own — a bell and a crossed-out bell read at a glance
 * where two bells in different colours do not.
 */
private val iconToggleStrike = Knob.Flag("Strikethrough")
private val iconToggleEnabled = Knob.Flag("Enabled", initial = true)

internal val IconToggleButtonDemo = ComponentDemo(
    slug = "icon-toggle-button",
    knobs = listOf(iconToggleStrike, iconToggleEnabled),
) {
    var favourited by remember { mutableStateOf(false) }
    var audible by remember { mutableStateOf(true) }
    val strike = this[iconToggleStrike]
    val enabled = this[iconToggleEnabled]
    Row(horizontalArrangement = Arrangement.spacedBy(Theme.spacing.sm)) {
        IconToggleButton(
            icon = Tabler.Outline.Star,
            contentDescription = "Favourite",
            checked = favourited,
            onCheckedChange = { favourited = it },
            enabled = enabled,
        )
        IconToggleButton(
            icon = Tabler.Outline.Bell,
            contentDescription = "Delay alerts",
            checked = audible,
            onCheckedChange = { audible = it },
            strikethrough = strike,
            enabled = enabled,
        )
    }
}

// --- FloatingActionButton -------------------------------------------------

private val fabSize = Knob.Choice("Size", FabSize.entries.toList(), FabSize.Medium)
private val fabEnabled = Knob.Flag("Enabled", initial = true)

internal val FloatingActionButtonDemo = ComponentDemo(
    slug = "fab",
    knobs = listOf(fabSize, fabEnabled),
) {
    FloatingActionButton(
        icon = Tabler.Outline.Plus,
        contentDescription = "Add",
        onClick = { echo("Add") },
        size = this[fabSize],
        enabled = this[fabEnabled],
    )
}

// --- ExtendedFloatingActionButton -----------------------------------------

private val extendedFabSize = Knob.Choice("Size", FabSize.entries.toList(), FabSize.Medium)

internal val ExtendedFabDemo = ComponentDemo(
    slug = "extended-fab",
    knobs = listOf(extendedFabSize),
) {
    // Collapsing is the component, so the button collapses itself rather than
    // taking a knob for it: the animation between the two is the thing to look
    // at, and a dropdown that jumps between states does not show it.
    var extended by remember { mutableStateOf(true) }
    ExtendedFloatingActionButton(
        icon = Tabler.Outline.Navigation,
        contentDescription = "Start trip",
        expanded = extended,
        size = this[extendedFabSize],
        onClick = { extended = !extended },
    ) {
        +"Start trip"
    }
}

// --- ButtonGroup ----------------------------------------------------------

private val groupVariant =
    Knob.Choice("Variant", ButtonVariant.entries.toList(), ButtonVariant.Tertiary)
private val groupSize = Knob.Choice("Size", ButtonSize.entries.toList(), ButtonSize.Medium)

internal val ButtonGroupDemo = ComponentDemo(
    slug = "button-group",
    knobs = listOf(groupVariant, groupSize),
) {
    // The builder collects rather than composes, so `echo` cannot be called
    // from inside it — hoisted, which is what a caller has to do too.
    val out: () -> Unit = { echo("Zoom out") }
    val recentre: () -> Unit = { echo("Recentre") }
    val into: () -> Unit = { echo("Zoom in") }

    ButtonGroup(variant = this[groupVariant], size = this[groupSize]) {
        item(onClick = out, contentDescription = "Zoom out", icon = Tabler.Outline.Minus)
        item(
            onClick = recentre,
            contentDescription = "Recentre",
            icon = Tabler.Outline.CurrentLocation,
        )
        item(onClick = into, contentDescription = "Zoom in", icon = Tabler.Outline.Plus)
    }
}

// --- SplitButton ----------------------------------------------------------

private val splitVariant =
    Knob.Choice("Variant", ButtonVariant.entries.toList(), ButtonVariant.Primary)
private val splitSize = Knob.Choice("Size", ButtonSize.entries.toList(), ButtonSize.Medium)

internal val SplitButtonDemo = ComponentDemo(
    slug = "split-button",
    knobs = listOf(splitVariant, splitSize),
) {
    var open by remember { mutableStateOf(false) }
    val save: () -> Unit = { echo("Save and close"); open = false }
    val copy: () -> Unit = { echo("Save a copy"); open = false }

    SplitButton(
        onClick = { echo("Save") },
        expanded = open,
        onExpandedChange = { open = it },
        menuContentDescription = "Other save options",
        variant = this[splitVariant],
        size = this[splitSize],
        menu = {
            item("Save and close", onClick = save)
            item("Save a copy", onClick = copy)
        },
    ) {
        +"Save"
    }
}

// --- FabMenu --------------------------------------------------------------

private val fabMenuLayout = Knob.Choice("Layout", FabMenuLayout.entries.toList())

// Follows the layout by default — a vertical stack has room beside it for a
// label and a fan does not — so this is the knob that shows the default is a
// default rather than a rule.
private val fabMenuLabels = Knob.Flag("Labels", initial = true)

internal val FabMenuDemo = ComponentDemo(
    slug = "fab-menu",
    knobs = listOf(fabMenuLayout, fabMenuLabels),
) {
    var open by remember { mutableStateOf(false) }
    val layout = this[fabMenuLayout]
    val labels = this[fabMenuLabels]
    val save: () -> Unit = { echo("Save stop"); open = false }
    val nearby: () -> Unit = { echo("Nearby"); open = false }
    val directions: () -> Unit = { echo("Directions"); open = false }

    // Bottom-right in a box with room above it, because the component picks
    // which way to open from where it finds itself in the window. A specimen
    // sitting in the middle of a card opens downward into nothing and shows an
    // arrangement no real screen would produce.
    Box(Modifier.fillMaxWidth().height(260.dp)) {
        FabMenu(
            expanded = open,
            onExpandedChange = { open = it },
            icon = Tabler.Outline.Plus,
            contentDescription = "Add",
            layout = layout,
            showLabels = labels && layout == FabMenuLayout.Vertical,
            modifier = Modifier.align(Alignment.BottomEnd),
        ) {
            item(Tabler.Outline.Star, "Save stop", onClick = save)
            item(Tabler.Outline.CurrentLocation, "Nearby", onClick = nearby)
            item(Tabler.Outline.Navigation, "Directions", onClick = directions)
        }
    }
}

// --- Toolbar --------------------------------------------------------------

internal val ToolbarDemo = ComponentDemo(slug = "toolbar") {
    val out: () -> Unit = { echo("Zoom out") }
    val into: () -> Unit = { echo("Zoom in") }

    Toolbar {
        ButtonGroup {
            item(onClick = out, contentDescription = "Zoom out", icon = Tabler.Outline.Minus)
            item(onClick = into, contentDescription = "Zoom in", icon = Tabler.Outline.Plus)
        }
        ToolbarDivider()
        IconButton(Tabler.Outline.Stack, "Map layers", { echo("Map layers") })
        IconButton(Tabler.Outline.CurrentLocation, "My location", { echo("My location") })
    }
}

// --- Spinner --------------------------------------------------------------

internal val SpinnerDemo = ComponentDemo(slug = "spinner") {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Theme.spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Spinner()
            Text("default", style = Theme.typography.labelSmall, colour = Theme.colours.contentMuted)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Spinner(size = 32.dp)
            Text("32dp", style = Theme.typography.labelSmall, colour = Theme.colours.contentMuted)
        }
    }
}

internal val actionDemos = listOf(
    ButtonDemo,
    IconButtonDemo,
    IconToggleButtonDemo,
    FloatingActionButtonDemo,
    ExtendedFabDemo,
    ButtonGroupDemo,
    SplitButtonDemo,
    FabMenuDemo,
    ToolbarDemo,
    SpinnerDemo,
)
