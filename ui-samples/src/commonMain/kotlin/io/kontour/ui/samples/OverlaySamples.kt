package io.kontour.ui.samples

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Bookmark
import com.composables.icons.tabler.outline.Copy
import com.composables.icons.tabler.outline.Dots
import com.composables.icons.tabler.outline.InfoCircle
import com.composables.icons.tabler.outline.Share
import com.composables.icons.tabler.outline.Trash
import io.kontour.ui.components.action.Button
import io.kontour.ui.components.action.ButtonVariant
import io.kontour.ui.components.action.IconButton
import io.kontour.ui.foundation.Text
import io.kontour.ui.overlay.AlertDialog
import io.kontour.ui.overlay.Command
import io.kontour.ui.overlay.CommandPalette
import io.kontour.ui.overlay.ContextMenuArea
import io.kontour.ui.overlay.Dialog
import io.kontour.ui.overlay.DropdownMenu
import io.kontour.ui.overlay.LoadingOverlay
import io.kontour.ui.overlay.MenuItem
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.overlay.Popover
import io.kontour.ui.overlay.ToastHost
import io.kontour.ui.overlay.ToastTone
import io.kontour.ui.overlay.rememberToastHostState
import io.kontour.ui.overlay.tooltip
import io.kontour.ui.nav.Tab
import io.kontour.ui.nav.TabBar
import io.kontour.ui.overlay.Tooltip
import io.kontour.ui.theme.Theme
import kotlinx.coroutines.launch

@Composable
fun DialogBasics() {
    var open by remember { mutableStateOf(false) }

    Button(onClick = { open = true }) { +"Rename favourite" }

    Dialog(visible = open, onDismissRequest = { open = false }) {
        Text("Rename favourite", style = Theme.typography.titleMedium)
        Text(
            "Give it a name you will recognise on the home screen.",
            style = Theme.typography.bodySmall,
            colour = Theme.colours.contentMuted,
        )
        Button(onClick = { open = false }, modifier = Modifier.fillMaxWidth()) { +"Save" }
    }
}

@Composable
fun AlertDialogBasics() {
    var open by remember { mutableStateOf(false) }

    AlertDialog(
        visible = open,
        onDismissRequest = { open = false },
        confirmLabel = "Remove",
        onConfirm = { remove("Perth Underground"); open = false },
        destructive = true,
    ) {
        +"Remove this favourite?"
        supporting {
            +"Perth Underground will be taken off your home screen. You can add it back any time."
        }
    }
}

@Composable
fun PopoverBasics() {
    var open by remember { mutableStateOf(false) }

    // The anchor is whatever the popover is declared beside, so both live in
    // one `Box` — the popover positions itself against its sibling.
    Box {
        IconButton(
            icon = Tabler.Outline.InfoCircle,
            contentDescription = "About this route",
            onClick = { open = !open },
        )
        Popover(visible = open, onDismissRequest = { open = false }) {
            Text("Route 950", style = Theme.typography.titleSmall)
            Text(
                "Runs every 15 minutes until 11pm, then every 30 minutes overnight.",
                style = Theme.typography.bodySmall,
                colour = Theme.colours.contentMuted,
            )
        }
    }
}

@Composable
fun DropdownMenuBasics() {
    var open by remember { mutableStateOf(false) }
    var order by remember { mutableStateOf(0) }

    Box {
        IconButton(
            icon = Tabler.Outline.Dots,
            contentDescription = "More",
            onClick = { open = !open },
        )
        DropdownMenu(visible = open, onDismissRequest = { open = false }) {
            section("This stop")
            item("Share", icon = Tabler.Outline.Share, shortcut = "⌘S", onClick = { open = false })
            item("Copy stop ID", icon = Tabler.Outline.Copy, onClick = { open = false })
            item("Set a reminder", enabled = false, onClick = {})
            divider()
            section("Sort departures by")
            item("Departure time", selected = order == 0, onClick = { order = 0 })
            item("Journey length", selected = order == 1, onClick = { order = 1 })
            divider()
            item(
                "Remove favourite",
                icon = Tabler.Outline.Trash,
                destructive = true,
                onClick = { open = false },
            )
        }
    }
}

@Composable
fun ContextMenuAreaBasics() {
    ContextMenuArea(
        menu = {
            MenuItem(onClick = { report() }) { +"Report a problem" }
            MenuItem(onClick = { suggest() }) { +"Suggest a correction" }
        },
    ) {
        Text("Perth Underground")
    }
}

@Composable
fun TooltipBasics() {
    // The modifier is what a caller nearly always wants: it tracks hover and
    // focus itself and honours the input modality, so it never appears from a
    // touch that was really a tap.
    IconButton(
        icon = Tabler.Outline.Bookmark,
        contentDescription = "Save this trip",
        onClick = { save() },
        modifier = Modifier.tooltip("Save this trip"),
    )

    // The component, for a tooltip whose visibility you own — a coach mark on
    // first run, or one shown from a keyboard shortcut.
    var open by remember { mutableStateOf(false) }
    Box {
        Button(onClick = { open = !open }, variant = ButtonVariant.Secondary) { +"Recentre" }
        Tooltip(visible = open) { +"Bring the map back to you" }
    }
}

@Composable
fun ToastBasics() {
    val toasts = rememberToastHostState()
    val scope = rememberCoroutineScope()

    // One host, high in the tree, beside the content it floats over.
    Box(Modifier.fillMaxSize()) {
        Screen()
        ToastHost(toasts)
    }

    Button(onClick = { scope.launch { toasts.show("Saved for offline") } }) { +"Save" }

    Button(
        onClick = {
            scope.launch {
                toasts.show(
                    "Couldn't reach the timetable service",
                    tone = ToastTone.Danger,
                    actionLabel = "Retry",
                    onAction = { refresh() },
                )
            }
        },
    ) { +"Refresh" }
}

@Composable
fun LoadingOverlayBasics() {
    var planning by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        Screen()
        LoadingOverlay(visible = planning, label = "Planning your trip")
    }
}

@Composable
fun CommandPaletteBasics() {
    var open by remember { mutableStateOf(false) }
    val commands = remember {
        listOf(
            Command("plan", "Plan a trip", onRun = { plan() }, shortcut = "P"),
            Command("saved", "Saved trips", onRun = { nearby() }, keywords = listOf("favourites")),
            Command("offline", "Download for offline", onRun = { save() }, enabled = false),
        )
    }

    CommandPalette(visible = open, onDismissRequest = { open = false }, commands = commands)
}

@Composable
fun OverlayHostBasics() {
    // One host at the root of the window. Every dialog, menu, popover and
    // tooltip below it draws into this, above everything and clipped by nothing.
    OverlayHost(Modifier.fillMaxSize()) {
        Screen()
    }
}

@Composable
fun SelectionIndicatorBasics() {
    var selected by remember { mutableStateOf(0) }
    val tabs = listOf("Departures", "Route map", "Alerts")

    // The pill is the bar's, not the tab's: `key` is what it animates between,
    // so it travels rather than appearing on one and vanishing from another.
    TabBar(modifier = Modifier.fillMaxWidth()) {
        tabs.forEachIndexed { index, label ->
            Tab(selected = selected == index, onClick = { selected = index }, key = index) {
                +label
            }
        }
    }
}
