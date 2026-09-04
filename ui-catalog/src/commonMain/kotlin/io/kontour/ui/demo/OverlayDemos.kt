package io.kontour.ui.demo

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Bookmark
import com.composables.icons.tabler.outline.Copy
import com.composables.icons.tabler.outline.Dots
import com.composables.icons.tabler.outline.InfoCircle
import com.composables.icons.tabler.outline.Share
import com.composables.icons.tabler.outline.Trash
import io.kontour.ui.components.action.Button
import io.kontour.ui.components.action.ButtonSize
import io.kontour.ui.components.action.ButtonVariant
import io.kontour.ui.components.action.IconButton
import io.kontour.ui.foundation.Surface
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
import io.kontour.ui.nav.Tab
import io.kontour.ui.nav.TabBar
import io.kontour.ui.overlay.Tooltip
import io.kontour.ui.overlay.rememberToastHostState
import io.kontour.ui.overlay.tooltip
import io.kontour.ui.theme.Theme
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

/**
 * A framed stage for one overlay, with its own host.
 *
 * Every overlay renders into the *nearest* `OverlayHost`, and on the site the
 * nearest one is the root of the page — so a dialog opened from a demo would
 * cover the whole site rather than the card it belongs to, and a scrim would dim
 * the navigation. A host per stage keeps each demo inside its own borders, which
 * is also what makes the scrim visible: there is content behind it to dim.
 */
@Composable
private fun Stage(height: Dp = 260.dp, content: @Composable BoxScope.() -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .border(
                width = Theme.sizing.borderWidth,
                color = Theme.colours.outline,
                shape = Theme.shapes.medium,
            )
            .clip(Theme.shapes.medium),
        colour = Theme.colours.surface,
    ) {
        OverlayHost(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier.padding(Theme.spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Theme.spacing.xxs),
                ) {
                    Text("Perth Underground", style = Theme.typography.titleSmall)
                    Text(
                        "Platform 2 · Joondalup line",
                        style = Theme.typography.bodySmall,
                        colour = Theme.colours.contentMuted,
                    )
                }
                content()
            }
        }
    }
}

/**
 * Whether a press on the scrim closes it.
 *
 * Off is the modal that has to be answered rather than escaped. Every demo
 * carrying this knob keeps a button inside that closes it, because a reader who
 * turns it off and cannot get out has found a trap, not a demonstration.
 */
private val overlayDismissible = Knob.Flag("Dismissible", initial = true)

internal val DialogDemo = ComponentDemo(slug = "dialog", knobs = listOf(overlayDismissible)) {
    var open by remember { mutableStateOf(false) }
    val dismissible = this[overlayDismissible]
    Stage {
        Button(
            onClick = { open = true },
            variant = ButtonVariant.Secondary,
            modifier = Modifier.align(Alignment.Center),
        ) { +"Open a dialog" }

        Dialog(
            visible = open,
            onDismissRequest = { open = false },
            dismissible = dismissible,
        ) {
            Text("Rename favourite", style = Theme.typography.titleMedium)
            Text(
                "Give it a name you will recognise on the home screen.",
                style = Theme.typography.bodySmall,
                colour = Theme.colours.contentMuted,
            )
            Button(onClick = { open = false }, modifier = Modifier.fillMaxWidth()) { +"Save" }
        }
    }
}

private val alertDestructive = Knob.Flag("Destructive", initial = true)
private val alertNeutral = Knob.Flag("Third answer", initial = true)

internal val AlertDialogDemo = ComponentDemo(
    slug = "alert-dialog",
    knobs = listOf(alertDestructive, alertNeutral, overlayDismissible),
) {
    var open by remember { mutableStateOf(false) }
    val destructive = this[alertDestructive]
    val neutral = this[alertNeutral]
    val dismissible = this[overlayDismissible]
    Stage {
        Button(
            onClick = { open = true },
            variant = ButtonVariant.Secondary,
            modifier = Modifier.align(Alignment.Center),
        ) { +"Remove favourite" }

        AlertDialog(
            visible = open,
            confirmLabel = "Remove",
            onConfirm = { open = false; echo("Removed") },
            onDismissRequest = { open = false },
            dismissible = dismissible,
            neutralLabel = if (neutral) "Hide instead" else null,
            onNeutral = if (neutral) ({ open = false; echo("Hidden") }) else null,
            destructive = destructive,
        ) {
            +"Remove this favourite?"
            supporting {
                +("Perth Underground will be taken off your home screen. " +
                    "You can add it back any time.")
            }
        }
    }
}

internal val PopoverDemo = ComponentDemo(slug = "popover") {
    var open by remember { mutableStateOf(false) }
    Stage {
        Box(Modifier.align(Alignment.Center)) {
            IconButton(
                icon = Tabler.Outline.InfoCircle,
                contentDescription = "About this route",
                onClick = { open = !open },
            )
            Popover(visible = open, onDismissRequest = { open = false }) {
                Text("Route 950", style = Theme.typography.titleSmall)
                Text(
                    "Runs every 15 minutes until 11pm, then every 30 minutes " +
                        "overnight.",
                    style = Theme.typography.bodySmall,
                    colour = Theme.colours.contentMuted,
                )
            }
        }
    }
}

/**
 * Whether the menu takes the width of what opened it.
 *
 * Right when the anchor *is* the menu's subject — a select, a filter pill —
 * because a panel narrower or wider than the control it belongs to reads as a
 * separate thing floating nearby. Wrong for an overflow button, where the menu
 * has nothing to do with the size of the dots that opened it.
 */
private val menuMatchAnchor = Knob.Flag("Match the anchor")

internal val DropdownMenuDemo = ComponentDemo(
    slug = "dropdown-menu",
    knobs = listOf(menuMatchAnchor),
) {
    var open by remember { mutableStateOf(false) }
    var order by remember { mutableStateOf(0) }
    // The builder collects rather than composes, so the callbacks are hoisted.
    val share: () -> Unit = { echo("Share"); open = false }
    val copy: () -> Unit = { echo("Copy stop ID"); open = false }
    val remove: () -> Unit = { echo("Remove favourite"); open = false }

    Stage {
        Box(Modifier.align(Alignment.TopEnd).padding(Theme.spacing.sm)) {
            IconButton(
                icon = Tabler.Outline.Dots,
                contentDescription = "More",
                onClick = { open = !open },
            )
            DropdownMenu(
                visible = open,
                onDismissRequest = { open = false },
                matchAnchorWidth = this@ComponentDemo[menuMatchAnchor],
            ) {
                section("This stop")
                item("Share", icon = Tabler.Outline.Share, shortcut = "⌘S", onClick = share)
                item("Copy stop ID", icon = Tabler.Outline.Copy, onClick = copy)
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
                    onClick = remove,
                )
            }
        }
    }
}

internal val ContextMenuAreaDemo = ComponentDemo(slug = "context-menu-area") {
    val report: () -> Unit = { echo("Report a problem") }
    val suggest: () -> Unit = { echo("Suggest a correction") }
    Stage {
        ContextMenuArea(
            modifier = Modifier.align(Alignment.Center),
            menu = {
                MenuItem(onClick = report) { +"Report a problem" }
                MenuItem(onClick = suggest) { +"Suggest a correction" }
            },
        ) {
            Surface(
                modifier = Modifier.padding(Theme.spacing.md),
                colour = Theme.colours.surfaceSunken,
                shape = Theme.shapes.medium,
            ) {
                Text(
                    "Right-click or long-press me",
                    modifier = Modifier.padding(Theme.spacing.md),
                    style = Theme.typography.bodyMedium,
                )
            }
        }
    }
}

internal val TooltipDemo = ComponentDemo(slug = "tooltip") {
    var open by remember { mutableStateOf(false) }
    Stage {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Theme.spacing.md),
        ) {
            Box {
                IconButton(
                    icon = Tabler.Outline.Bookmark,
                    contentDescription = "Save this trip",
                    onClick = { open = !open },
                )
                Tooltip(visible = open) { +"Save this trip" }
            }
            // The modifier is what a caller nearly always wants: it tracks hover
            // and focus itself and honours the input modality, so it never
            // appears from a touch that was really a tap.
            Button(
                onClick = { echo("Recentre") },
                variant = ButtonVariant.Secondary,
                size = ButtonSize.Small,
                modifier = Modifier.tooltip("Bring the map back to you"),
            ) { +"Hover me" }
        }
    }
}

internal val ToastDemo = ComponentDemo(slug = "toast") {
    val toasts = rememberToastHostState()
    val scope = rememberCoroutineScope()
    Stage {
        ToastHost(toasts, showClose = true)
        Column(
            modifier = Modifier.align(Alignment.Center),
            verticalArrangement = Arrangement.spacedBy(Theme.spacing.sm),
        ) {
            Button(
                onClick = { scope.launch { toasts.show("Saved for offline") } },
                variant = ButtonVariant.Secondary,
                size = ButtonSize.Small,
            ) { +"Raise one" }
            Button(
                onClick = {
                    scope.launch {
                        toasts.show(
                            "Couldn't reach the timetable service",
                            tone = ToastTone.Danger,
                            actionLabel = "Retry",
                            onAction = { },
                        )
                    }
                },
                variant = ButtonVariant.Secondary,
                size = ButtonSize.Small,
            ) { +"With an action" }
            Text(
                "Three at once is the cap — press faster than they expire.",
                style = Theme.typography.labelSmall,
                colour = Theme.colours.contentMuted,
            )
        }
    }
}

internal val LoadingOverlayDemo = ComponentDemo(slug = "loading-overlay") {
    var busy by remember { mutableStateOf(false) }
    Stage {
        Button(
            onClick = { busy = !busy },
            variant = ButtonVariant.Secondary,
            modifier = Modifier.align(Alignment.Center),
        ) { +(if (busy) "Stop" else "Block the screen") }
        LoadingOverlay(visible = busy, label = "Planning your trip")
    }
}

internal val CommandPaletteDemo = ComponentDemo(
    slug = "command-palette",
    knobs = listOf(overlayDismissible),
) {
    var open by remember { mutableStateOf(false) }
    val dismissible = this[overlayDismissible]
    // Running a command closes the palette and says which one ran.
    //
    // `onRun = {}` before, which made the palette look alive while doing
    // nothing — the exact defect `echo` exists for. It also mattered once the
    // knob below could switch the tap-outside off: a palette whose commands do
    // nothing and whose scrim is inert is one there is no way out of.
    val commands = remember {
        listOf(
            Command("plan", "Plan a trip", onRun = { open = false; echo("Plan a trip") }, shortcut = "P"),
            Command("saved", "Saved trips", onRun = { open = false; echo("Saved trips") }, keywords = listOf("favourites")),
            Command("settings", "Settings", onRun = { open = false; echo("Settings") }, keywords = listOf("prefs")),
            Command("offline", "Download for offline", onRun = {}, enabled = false),
        )
    }
    Stage(height = 320.dp) {
        Button(
            onClick = { open = true },
            variant = ButtonVariant.Secondary,
            modifier = Modifier.align(Alignment.Center),
        ) { +"Open the palette" }
        // Sized down from its defaults, which assume a window rather than a card.
        CommandPalette(
            visible = open,
            onDismissRequest = { open = false },
            commands = commands,
            dismissible = dismissible,
            width = 320.dp,
            topInset = 24.dp,
            maxHeight = 220.dp,
        )
    }
}

private val hostScrim = Knob.Choice("Scrim", listOf("Dimmed", "Transparent"))

internal val OverlayHostDemo = ComponentDemo(slug = "overlay-host", knobs = listOf(hostScrim)) {
    var open by remember { mutableStateOf(false) }
    val dimmed = this[hostScrim] == "Dimmed"
    // Two stages side by side would be the honest picture, but the point is
    // simpler than that: this card has its own host, so the dialog stays inside
    // the border. Without one it would find the site's host and cover the page.
    Stage {
        Button(
            onClick = { open = true },
            variant = ButtonVariant.Secondary,
            modifier = Modifier.align(Alignment.Center),
        ) { +"Open something" }

        if (dimmed) {
            Dialog(visible = open, onDismissRequest = { open = false }) {
                Text("Contained", style = Theme.typography.titleSmall)
                Text(
                    "The scrim stops at this card's edge, because the nearest " +
                        "host is the one inside it.",
                    style = Theme.typography.bodySmall,
                    colour = Theme.colours.contentMuted,
                )
            }
        } else {
            Popover(visible = open, onDismissRequest = { open = false }) {
                Text(
                    "Transparent: blocked, but not dimmed — what a menu wants.",
                    style = Theme.typography.bodySmall,
                )
            }
        }
    }
}

internal val SelectionIndicatorDemo = ComponentDemo(slug = "selection-indicator") {
    var selected by remember { mutableStateOf(0) }
    val tabs = listOf("Departures", "Route map", "Alerts")
    Column(verticalArrangement = Arrangement.spacedBy(Theme.spacing.sm)) {
        TabBar(modifier = Modifier.fillMaxWidth()) {
            tabs.forEachIndexed { index, label ->
                Tab(selected = selected == index, onClick = { selected = index }, key = index) {
                    +label
                }
            }
        }
        Text(
            "The pill travels between tabs rather than appearing on one — one " +
                "indicator owned by the bar, not three owned by the tabs.",
            style = Theme.typography.bodySmall,
            colour = Theme.colours.contentMuted,
        )
    }
}

internal val overlayDemos = listOf(
    DialogDemo,
    AlertDialogDemo,
    PopoverDemo,
    DropdownMenuDemo,
    ContextMenuAreaDemo,
    TooltipDemo,
    ToastDemo,
    LoadingOverlayDemo,
    CommandPaletteDemo,
    OverlayHostDemo,
    SelectionIndicatorDemo,
)
