package io.kontour.ui.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Bookmark
import io.kontour.ui.components.action.IconButton
import io.kontour.ui.foundation.Surface
import io.kontour.ui.foundation.Text
import io.kontour.ui.overlay.AlertDialog
import io.kontour.ui.overlay.DropdownMenu
import io.kontour.ui.overlay.LocalOverlayQueue
import io.kontour.ui.overlay.OverlayAlignment
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.overlay.OverlaySide
import io.kontour.ui.overlay.Popover
import io.kontour.ui.overlay.Tooltip
import io.kontour.ui.overlay.coachMark
import io.kontour.ui.overlay.rememberOverlayQueue
import io.kontour.ui.theme.KontourTheme
import io.kontour.ui.theme.Theme
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Overlays caught halfway through appearing.
 *
 * Every other golden pins `reduceMotion = true`, which collapses the host's
 * progress animation to a snap — so the whole suite only ever draws an overlay
 * that has already arrived. Two of the defects reported against this library
 * lived entirely in the frames those goldens skip: a square of shadow around a
 * panel while it scaled up, and a tooltip's arrow coming away from its bubble on
 * the way in. Neither could fail a test, because no test rendered them.
 *
 * This one does. `reduceMotion = false`, and a frame count chosen to land inside
 * the entry spring: far enough in that the panels are drawn and their shadows
 * resolved, not so far that they have settled.
 *
 * ### What to look for when it fails
 *
 * The two things it exists to catch are visible by eye in `-actual.png`:
 *
 * - **A hard-edged rectangle of shadow** around a panel means the appearance
 *   transform is compositing into an offscreen buffer that clips the shadow's
 *   bleed at the layer's own bounds.
 * - **A detached arrow** — a triangle sitting apart from the bubble it points
 *   from — means the panel and the arrow are being transformed by different
 *   nodes.
 *
 * Anything else is most likely the spring's shape shifting under a Compose
 * upgrade, in which case re-record and look at the new frame.
 */
class OverlayMotionScreenshotTest {

    @Test
    fun rendersOverlaysWhileTheyAreStillAppearing() {
        val file = Screenshot.render(
            name = "overlays-appearing",
            width = 4340,
            height = 900,
            // A few frames to lay out, anchor and push each overlay into its
            // host, then a few more into the spring. `springSnappy` is stiff
            // enough that much beyond this has settled.
            frames = 7,
        ) {
            KontourTheme(darkTheme = false, reduceMotion = false) {
                AppearingOverlays()
            }
        }
        assertTrue(file.length() > 0, "overlays-appearing rendered an empty file")
    }
}

/** One panel with no arrow, three with one, and one that is not anchored at all. */
@Composable
private fun AppearingOverlays() {
    Surface(modifier = Modifier.fillMaxSize(), color = Theme.colors.background) {
        Row(
            modifier = Modifier.padding(Theme.spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(Theme.spacing.lg),
        ) {
            MotionPanel("Menu") {
                Box(Modifier.align(Alignment.TopCenter).padding(top = 24.dp)) {
                    IconButton(
                        icon = Tabler.Outline.Bookmark,
                        contentDescription = "Save",
                        onClick = {},
                    )
                    DropdownMenu(
                        expanded = true,
                        onDismissRequest = {},
                        alignment = OverlayAlignment.Center,
                    ) {
                        item("Share") {}
                        item("Copy stop ID") {}
                        item("Remove favourite", destructive = true) {}
                    }
                }
            }

            MotionPanel("Tooltip") {
                Box(Modifier.align(Alignment.Center)) {
                    IconButton(
                        icon = Tabler.Outline.Bookmark,
                        contentDescription = "Save this trip",
                        onClick = {},
                    )
                    Tooltip(visible = true, text = "Save this trip")
                }
            }

            MotionPanel("Coach mark") {
                val queue = rememberOverlayQueue()
                CompositionLocalProvider(LocalOverlayQueue provides queue) {
                    Box(Modifier.align(Alignment.TopCenter).padding(top = 24.dp)) {
                        IconButton(
                            icon = Tabler.Outline.Bookmark,
                            contentDescription = "Save this trip",
                            onClick = {},
                            modifier = Modifier.coachMark(
                                id = "save-trip",
                                title = "Save this trip",
                                text = "Saved trips show up on the home screen.",
                                priority = 10,
                            ),
                        )
                    }
                }
            }

            MotionPanel("Popover") {
                Box(Modifier.align(Alignment.TopCenter).padding(top = 24.dp)) {
                    IconButton(
                        icon = Tabler.Outline.Bookmark,
                        contentDescription = "Details",
                        onClick = {},
                    )
                    Popover(expanded = true, onDismissRequest = {}) {
                        Text(
                            "Runs every 15 minutes until 11pm.",
                            style = Theme.typography.bodySmall,
                        )
                    }
                }
            }

            // Not anchored, and it appears through the same modifier — so it has
            // the same two ways to go wrong, and had them: it was scaling into
            // place at full opacity with its shadow cut into a square.
            MotionPanel("Dialog") {
                AlertDialog(
                    visible = true,
                    title = "Remove this favourite?",
                    message = "Perth Underground will be taken off your home screen.",
                    confirmLabel = "Remove",
                    onConfirm = {},
                    onDismissRequest = {},
                    destructive = true,
                )
            }
        }
    }
}

/**
 * One overlay's worth of room, with its own host.
 *
 * Deliberately generous, and with the host's own surface left unclipped: an
 * overlay close to the edge of its host would have its shadow cut off by the
 * host's bounds, which is the very artefact this golden is looking for
 * elsewhere.
 */
@Composable
private fun MotionPanel(label: String, content: @Composable BoxScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Theme.spacing.xs)) {
        Text(
            text = label.uppercase(),
            style = Theme.typography.monoLabel,
            color = Theme.colors.accent,
        )
        Surface(
            modifier = Modifier.width(400.dp).height(380.dp),
            shape = Theme.shapes.large,
            color = Theme.colors.surface,
        ) {
            OverlayHost(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize(), content = content)
            }
        }
    }
}
