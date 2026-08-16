package io.kontour.ui.catalog

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.ArrowsSort
import com.composables.icons.tabler.outline.Bookmark
import com.composables.icons.tabler.outline.Clock
import com.composables.icons.tabler.outline.Copy
import com.composables.icons.tabler.outline.Dots
import com.composables.icons.tabler.outline.InfoCircle
import com.composables.icons.tabler.outline.Share
import com.composables.icons.tabler.outline.Trash
import io.kontour.ui.components.action.Button
import io.kontour.ui.components.action.ButtonVariant
import io.kontour.ui.components.action.IconButton
import io.kontour.ui.foundation.Surface
import io.kontour.ui.foundation.Text
import io.kontour.ui.overlay.AlertDialog
import androidx.compose.runtime.remember
import io.kontour.ui.overlay.Command
import io.kontour.ui.overlay.CommandPalette
import io.kontour.ui.overlay.DropdownMenu
import io.kontour.ui.overlay.LoadingOverlay
import io.kontour.ui.overlay.LocalOverlayQueue
import io.kontour.ui.overlay.MenuDivider
import io.kontour.ui.overlay.MenuItem
import io.kontour.ui.overlay.MenuSectionHeader
import io.kontour.ui.overlay.OverlayAlignment
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.overlay.OverlaySide
import io.kontour.ui.overlay.Popover
import io.kontour.ui.overlay.SubMenu
import io.kontour.ui.overlay.ToastHost
import io.kontour.ui.overlay.ToastTone
import io.kontour.ui.overlay.Tooltip
import io.kontour.ui.overlay.coachMark
import io.kontour.ui.overlay.rememberOverlayQueue
import io.kontour.ui.overlay.rememberToastHostState
import io.kontour.ui.theme.Theme

/**
 * Every overlay, each open in its own host.
 *
 * One host per panel rather than one for the whole page, because overlays are
 * positioned against the host that owns them — and because a single host would
 * stack six scrims and dim everything to black. Each panel is what one overlay
 * looks like on one screen.
 */
@Composable
fun OverlayShowcase(modifier: Modifier = Modifier) {
    Surface(modifier = modifier, color = Theme.colors.background) {
        Column(
            modifier = Modifier.padding(Theme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Theme.spacing.lg),
        ) {
            // Every overlay here used to be pinned open with a dead
            // `onDismissRequest`, so a golden could capture it. Seeded from those
            // same literals: the goldens still capture them open, and they can
            // now actually be dismissed and reopened.
            val menu = seed(true)
            val sort = seed(true)
            val popover = seed(true)
            val alert = seed(true)
            val tooltip = seed(true)
            val loading = seed(true)
            val edgeMenu = seed(true)

            Row(horizontalArrangement = Arrangement.spacedBy(Theme.spacing.lg)) {
                Panel("Dropdown menu") {
                    Box(Modifier.align(Alignment.TopEnd).padding(end = 16.dp, top = 12.dp)) {
                        IconButton(
                            icon = Tabler.Outline.Dots,
                            contentDescription = "More",
                            onClick = { menu.value = !menu.value },
                        )
                        DropdownMenu(
                            visible = menu.value,
                            onDismissRequest = { menu.value = false },
                            alignment = OverlayAlignment.Center,
                        ) {
                            // Written with the DSL, and the golden for this panel
                            // did not move — which is the claim the shorthands
                            // make: the same components, fewer places to slip.
                            section("This stop")
                            item(
                                "Share",
                                icon = Tabler.Outline.Share,
                                shortcut = "⌘S",
                                onClick = tap("Share"),
                            )
                            item(
                                "Copy stop ID",
                                icon = Tabler.Outline.Copy,
                                onClick = tap("Copy stop ID"),
                            )
                            item(
                                "Set a reminder",
                                icon = Tabler.Outline.Clock,
                                enabled = false,
                                onClick = {},
                            )
                            divider()
                            item(
                                "Remove favourite",
                                icon = Tabler.Outline.Trash,
                                destructive = true,
                                onClick = tap("Remove favourite"),
                            )
                        }
                    }
                }

                Panel("Menu with a nested row") {
                    Box(Modifier.align(Alignment.TopStart).padding(start = 16.dp, top = 72.dp)) {
                        Button(
                            onClick = { sort.value = !sort.value },
                            variant = ButtonVariant.Secondary,
                        ) {
                            +"Sort"
                        }
                        DropdownMenu(visible = sort.value, onDismissRequest = { sort.value = false }) {
                            val order = seed(0)
                            item(
                                "Departure time",
                                selected = order.value == 0,
                                onClick = { order.value = 0 },
                            )
                            item(
                                "Journey length",
                                selected = order.value == 1,
                                onClick = { order.value = 1 },
                            )
                            divider()
                            submenu("Group by", icon = Tabler.Outline.ArrowsSort) {
                                item("Route", onClick = tap("Group by route"))
                                item("Operator", onClick = tap("Group by operator"))
                            }
                        }
                    }
                }

                Panel("Popover") {
                    Box(Modifier.align(Alignment.TopEnd).padding(end = 16.dp, top = 12.dp)) {
                        IconButton(
                            icon = Tabler.Outline.InfoCircle,
                            contentDescription = "About this route",
                            onClick = { popover.value = !popover.value },
                        )
                        Popover(visible = popover.value, onDismissRequest = { popover.value = false }) {
                            Text("Route 950", style = Theme.typography.titleSmall)
                            Text(
                                "Runs every 15 minutes until 11pm, then every 30 " +
                                    "minutes overnight.",
                                style = Theme.typography.bodySmall,
                                color = Theme.colors.contentMuted,
                            )
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(Theme.spacing.lg)) {
                Panel("Alert dialog") {
                    AlertDialog(
                        visible = alert.value,
                        confirmLabel = "Remove",
                        onConfirm = { alert.value = false },
                        onDismissRequest = { alert.value = false },
                        // Three actions, so the confirm takes its own line.
                        neutralLabel = "Hide instead",
                        onNeutral = { alert.value = false },
                        destructive = true,
                    ) {
                        +"Remove this favourite?"
                        supporting {
                            +(
                                "Perth Underground will be taken off your home screen. " +
                                    "You can add it back any time."
                                )
                        }
                    }
                }

                Panel("Tooltip") {
                    Box(Modifier.align(Alignment.Center)) {
                        IconButton(
                            icon = Tabler.Outline.Bookmark,
                            contentDescription = "Save this trip",
                            onClick = { tooltip.value = !tooltip.value },
                        )
                        Tooltip(visible = tooltip.value) { +"Save this trip" }
                    }
                }

                Panel("Coach mark") {
                    // The queue is what a coach mark registers into; without one
                    // in scope the modifier is inert by design.
                    val queue = rememberOverlayQueue()
                    CompositionLocalProvider(LocalOverlayQueue provides queue) {
                        Box(Modifier.align(Alignment.TopCenter).padding(top = 16.dp)) {
                            IconButton(
                                icon = Tabler.Outline.Bookmark,
                                contentDescription = "Save this trip",
                                onClick = tap("Save this trip"),
                                modifier = Modifier.coachMark(
                                    id = "save-trip",
                                    title = "Save this trip",
                                    text = "Trips you save show up on the home " +
                                        "screen, ready for next time.",
                                    priority = 10,
                                ),
                            )
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(Theme.spacing.lg)) {
                Panel("Toast") {
                    val toasts = rememberToastHostState()
                    // `showClose`, because these are pinned: a toast that never
                    // expires and cannot be closed is a sticker.
                    ToastHost(toasts, showClose = true)
                    // Hoisted out of the effect: `tap` reads a composition local,
                    // and a coroutine is not a composition.
                    val retry = tap("Retry")
                    LaunchedEffect(Unit) {
                        // Two, so the render shows the stack rather than one
                        // toast that happens to be alone. The older one peeks
                        // out behind and above the newer.
                        toasts.show("Saved for offline", durationMillis = 0)
                        toasts.show(
                            "Couldn't reach the timetable service",
                            tone = ToastTone.Danger,
                            actionLabel = "Retry",
                            onAction = retry,
                            // Pinned, so the golden is a golden: a toast that
                            // dismisses itself is a race against how long the
                            // render takes. Zero says "stays" rather than naming
                            // a number big enough to look like one — which is
                            // also what stops a test advancing its clock through
                            // ten minutes of frames.
                            durationMillis = 0,
                        )
                    }
                }

                Panel("Loading overlay") {
                    LoadingOverlay(visible = loading.value, label = "Planning your trip")
                }

                Panel("Command palette", height = 440.dp) {
                    // Seeded open so the golden shows the palette, and a
                    // *toggle* rather than a setter — pressing "open" while it
                    // is already open does nothing, which is exactly what
                    // `EverythingRespondsTest` exists to catch. It caught this.
                    val open = seed(true)
                    // Aligned, like every other trigger in here. Left where it
                    // fell, it landed on top of the panel's own stand-in text
                    // and the two drew over each other — which is most of what
                    // made this panel look broken.
                    Button(
                        onClick = { open.value = !open.value },
                        variant = ButtonVariant.Secondary,
                        modifier = Modifier.align(Alignment.BottomStart).padding(16.dp),
                    ) { +"Open palette" }
                    CommandPalette(
                        visible = open.value,
                        onDismissRequest = { open.value = false },
                        commands = remember {
                            listOf(
                                Command("plan", "Plan a trip", onRun = {}, shortcut = "P"),
                                Command("saved", "Saved trips", onRun = {}, keywords = listOf("favourites")),
                                Command("settings", "Settings", onRun = {}, keywords = listOf("prefs")),
                                Command("offline", "Download for offline", onRun = {}, enabled = false),
                            )
                        },
                        // All three of these, not just the width. The defaults
                        // are sized for a real window: 96dp of top inset and
                        // 360dp of results do not fit a 300dp panel, so the
                        // palette ran off the bottom and the gallery showed a
                        // component that looked like it could not lay itself
                        // out. It lays out fine; it was being given a window a
                        // fifth the size of the one it was drawn for.
                        width = 320.dp,
                        topInset = 32.dp,
                        maxHeight = 240.dp,
                    )
                }

                Panel("Menu near an edge") {
                    // The flip case: anchored at the bottom, asked to open
                    // downward, and there is nowhere to go.
                    Box(Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
                        Button(
                            onClick = { edgeMenu.value = !edgeMenu.value },
                            variant = ButtonVariant.Ghost,
                        ) { +"Options" }
                        DropdownMenu(
                            visible = edgeMenu.value,
                            onDismissRequest = { edgeMenu.value = false },
                            side = OverlaySide.Bottom,
                        ) {
                            MenuItem(onClick = tap("Report a problem")) {
                                +"Report a problem"
                            }
                            MenuItem(onClick = tap("Suggest a correction")) {
                                +"Suggest a correction"
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * A fixed-size screen stand-in with its own overlay host.
 *
 * @param height Taller for the command palette, which is the one component in
 *   here that wants a window rather than a card. Squeezed into 300dp it ran off
 *   the bottom, and a gallery that clips the thing it is showing is worse than
 *   no gallery.
 */
@Composable
private fun Panel(
    title: String,
    height: Dp = 300.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    Panel(width = 360.dp, spacing = Theme.spacing.xs) {
        Text(
            text = title.uppercase(),
            style = Theme.typography.monoLabel,
            color = Theme.colors.accent.solid,
        )
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .border(
                    width = Theme.sizing.borderWidth,
                    color = Theme.colors.outline,
                    shape = Theme.shapes.medium,
                )
                .clip(Theme.shapes.medium),
            color = Theme.colors.surface,
        ) {
            OverlayHost(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize()) {
                    // Something behind the overlay, so the scrim has a job to do.
                    Column(
                        modifier = Modifier.padding(Theme.spacing.md),
                        verticalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
                    ) {
                        Text("Perth Underground", style = Theme.typography.titleSmall)
                        Text(
                            "Platform 2 · Joondalup line",
                            style = Theme.typography.bodySmall,
                            color = Theme.colors.contentMuted,
                        )
                    }
                    content()
                }
            }
        }
    }
}
