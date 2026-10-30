package io.kontour.ui.samples

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import io.kontour.icons.tabler.Tabler
import io.kontour.icons.tabler.outline.Bookmark
import io.kontour.icons.tabler.outline.Copy
import io.kontour.icons.tabler.outline.Dots
import io.kontour.icons.tabler.outline.InfoCircle
import io.kontour.icons.tabler.outline.Route
import io.kontour.icons.tabler.outline.Share
import io.kontour.icons.tabler.outline.Trash
import io.kontour.ui.components.action.Button
import io.kontour.ui.components.action.ButtonVariant
import io.kontour.ui.components.action.IconButton
import io.kontour.ui.components.text.TextField
import io.kontour.ui.foundation.Text
import io.kontour.ui.overlay.AlertDialog
import io.kontour.ui.overlay.LocalOverlayQueue
import io.kontour.ui.overlay.coachmark
import io.kontour.ui.overlay.CoachmarkStep
import io.kontour.ui.overlay.CoachmarkTransition
import io.kontour.ui.overlay.coachmarkStep
import io.kontour.ui.overlay.rememberCoachmarkTour
import io.kontour.ui.overlay.rememberOverlayQueue
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
import io.kontour.ui.theme.Tone
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

    val name = rememberTextFieldState("Perth Underground")
    Dialog(
        visible = open,
        onDismissRequest = { open = false },
        title = { +"Rename favourite" },
        // Under the content, and kept on screen while it scrolls. Placed by role:
        // the confirm on the trailing edge, the cancel beside it.
        actions = {
            cancel()
            confirm("Save", enabled = name.text.isNotBlank()) {
                rename(name.text.toString())
                open = false
            }
        },
    ) {
        Text(
            "Give it a name you will recognise on the home screen.",
            style = Theme.typography.bodySmall,
            colour = Theme.colours.contentMuted,
        )
        TextField(name, label = "Name")
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
        message {
            +"Perth Underground will be taken off your home screen. You can add it back any time."
        }
    }
}

@Composable
fun TypeToConfirmBasics() {
    var open by remember { mutableStateOf(false) }
    val typed = rememberTextFieldState()

    // A question with more than an answer is a `Dialog` with `actions`: the
    // same row an alert draws, under whatever the question needs.
    Dialog(
        visible = open,
        onDismissRequest = { open = false },
        title = { +"Delete this account?" },
        actions = {
            cancel()
            confirm("Delete account", destructive = true, enabled = typed.text.trim() == account.email) {
                deleteAccount()
                open = false
            }
        },
    ) {
        Text(
            "Every journey and favourite goes with it. Type ${account.email} to confirm.",
            colour = Theme.colours.contentMuted,
        )
        TextField(typed, label = "Email", keyboardType = KeyboardType.Email)
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
        Popover(visible = open, onDismissRequest = { open = false }, title = { +"Route 950" }) {
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
            item("Share", leadingIcon = Tabler.Outline.Share, shortcut = "⌘S", onClick = { open = false })
            item("Copy stop ID", leadingIcon = Tabler.Outline.Copy, onClick = { open = false })
            item("Set a reminder", enabled = false, onClick = {})
            divider()
            section("Sort departures by")
            item("Departure time", selected = order == 0, onClick = { order = 0 })
            item("Journey length", selected = order == 1, onClick = { order = 1 })
            divider()
            item(
                "Remove favourite",
                leadingIcon = Tabler.Outline.Trash,
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
        // A press anywhere else asks it to go.
    Tooltip(visible = open, onDismissRequest = { open = false }) { +"Bring the map back to you" }
    }
}

@Composable
fun CoachmarkBasics() {
    // One tip, shown when the app decides — through a queue, so six features do
    // not all introduce themselves on one launch, and never over a dialog.
    CompositionLocalProvider(LocalOverlayQueue provides rememberOverlayQueue(sessionCount = launches)) {
        IconButton(
            icon = Tabler.Outline.Bookmark,
            contentDescription = "Save this trip",
            onClick = { save() },
            modifier = Modifier.coachmark(
                id = "save-trip",
                title = "Save this trip",
                message = "Trips you save show up on the home screen.",
                minSessions = 3,
            ),
        )
    }

    // A tour the user asked for: the order here, and each step's words at the
    // control they explain.
    val tour = rememberCoachmarkTour("plan", "saved")
    Row {
        IconButton(
            icon = Tabler.Outline.Route,
            contentDescription = "Plan a trip",
            onClick = { plan() },
            modifier = Modifier.coachmarkStep(tour, id = "plan", title = "Plan a trip", message = "From here to anywhere on the network."),
        )
        IconButton(
            icon = Tabler.Outline.Bookmark,
            contentDescription = "Saved trips",
            onClick = { save() },
            modifier = Modifier.coachmarkStep(tour, id = "saved", title = "Saved trips", message = "The ones you keep show up here."),
        )
    }
    Button(onClick = tour::start, variant = ButtonVariant.Secondary) { +"Show me around" }
}

@Composable
fun CoachmarkTourAcrossTheApp() {
    var tab by remember { mutableStateOf("plan") }

    // Above the screens, so it outlives each of them, and told each step as
    // it gets there, to take the reader to the screen that step is on.
    val tour = rememberCoachmarkTour("welcome", "plan", "saved", transition = CoachmarkTransition.Fade) { step ->
        if (step != "welcome") tab = step
    }

    // A step that points at nothing: a card in the middle of the dimmed screen.
    CoachmarkStep(tour, id = "welcome", title = "Welcome aboard", message = "A minute's tour of the app.")

    when (tab) {
        "plan" -> IconButton(
            icon = Tabler.Outline.Route,
            contentDescription = "Plan a trip",
            onClick = { plan() },
            modifier = Modifier.coachmarkStep(tour, id = "plan", title = "Plan a trip", message = "From here to anywhere on the network."),
        )
        else -> IconButton(
            icon = Tabler.Outline.Bookmark,
            contentDescription = "Saved trips",
            onClick = { save() },
            modifier = Modifier.coachmarkStep(
                tour,
                id = "saved",
                title = "Saved trips",
                message = "The ones you keep show up here.",
                tone = Tone.Success,
            ),
        )
    }
    Button(onClick = tour::start, variant = ButtonVariant.Secondary) { +"Show me around" }
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
                    tone = Tone.Danger,
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
            Command("plan", "Plan a trip", onAction = { plan() }, shortcut = "P"),
            Command("saved", "Saved trips", onAction = { nearby() }, keywords = listOf("favourites")),
            Command("offline", "Download for offline", onAction = { save() }, enabled = false),
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
