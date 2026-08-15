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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
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
import io.kontour.ui.sheet.ModalBottomSheet
import io.kontour.ui.sheet.SheetHeader
import io.kontour.ui.sheet.SideSheet
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

    /**
     * And the same overlays on their way back out.
     *
     * A separate golden because leaving is not appearing in reverse: the entry
     * runs a stiff spring, the exit a tween on the `exit` easing, so they pass
     * through the same scales at very different opacities. Anything that only
     * shows at high opacity and small scale is invisible in the entry frame and
     * plain here — which is where the arrows were reported coming away from their
     * bubbles after the entry frame said they were attached.
     *
     * Dismissal is driven from inside the content rather than by the harness, so
     * this needs no new hook: each overlay is shown, given time to settle, and
     * then hidden, with the frame count landing partway through the exit.
     */
    @Test
    fun rendersOverlaysWhileTheyAreLeaving() {
        val file = Screenshot.render(
            name = "overlays-leaving",
            width = 4340,
            height = 900,
            // Settled by frame 12, dismissed there, and this lands three frames
            // into the 150ms exit.
            frames = 15,
        ) {
            KontourTheme(darkTheme = false, reduceMotion = false) {
                AppearingOverlays(dismissAfterFrames = 12)
            }
        }
        assertTrue(file.length() > 0, "overlays-leaving rendered an empty file")
    }

    /**
     * Sheets on their way out, with their scrims.
     *
     * The scrim and the thing it dims are one event, and they used to be drawn by
     * two animations that disagreed about how long that event takes. A modal
     * sheet was the worst of it: the sheet slides on `springGentle` while the
     * scrim ran a 220ms tween, so the dimming was gone with the sheet still
     * halfway down the screen.
     *
     * A still frame catches that: a sheet only part-way out from the bottom, over
     * a scrim that is still dark, is the two agreeing. A pale scrim behind a sheet
     * that has barely moved is the bug.
     */
    @Test
    fun rendersSheetsWhileTheyAreLeaving() {
        val file = Screenshot.render(
            name = "sheets-leaving",
            width = 2000,
            height = 1240,
            frames = 26,
        ) {
            KontourTheme(darkTheme = false, reduceMotion = false) {
                LeavingSheets(dismissAfterFrames = 20)
            }
        }
        assertTrue(file.length() > 0, "sheets-leaving rendered an empty file")
    }
}

/** A modal sheet and a side sheet, both dismissed part-way through the render. */
@Composable
private fun LeavingSheets(dismissAfterFrames: Int) {
    val showing = remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        repeat(dismissAfterFrames) { withFrameNanos { } }
        showing.value = false
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Theme.colors.background) {
        Row(
            modifier = Modifier.padding(Theme.spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(Theme.spacing.lg),
        ) {
            MotionPanel("Modal sheet") {
                ModalBottomSheet(
                    visible = showing.value,
                    onDismissRequest = { showing.value = false },
                ) {
                    SheetHeader(title = "Rename favourite", supporting = "Perth Underground")
                }
            }
            MotionPanel("Side sheet") {
                SideSheet(
                    visible = showing.value,
                    onDismissRequest = { showing.value = false },
                    width = 220.dp,
                ) {
                    SheetHeader(title = "Filters")
                }
            }
        }
    }
}

/**
 * One panel with no arrow, three with one, and one that is not anchored at all.
 *
 * @param dismissAfterFrames How many rendered frames to wait before hiding them
 *   all, or `null` to leave them open.
 *
 *   Frames rather than a `delay`, and that is not a detail. `ImageComposeScene`
 *   drives animations from the frame clock the harness advances by hand, but
 *   `delay` resolves against wall time — so a `delay(400)` fires whenever the
 *   render loop happens to take 400ms, which on a warm JVM is somewhere in the
 *   first few frames and on a cold one is never. The first version of this
 *   golden did that and came out empty: everything had already finished leaving.
 */
@Composable
private fun AppearingOverlays(dismissAfterFrames: Int? = null) {
    val showing = remember { mutableStateOf(true) }
    if (dismissAfterFrames != null) {
        LaunchedEffect(Unit) {
            repeat(dismissAfterFrames) { withFrameNanos { } }
            showing.value = false
        }
    }

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
                        expanded = showing.value,
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
                    Tooltip(visible = showing.value, text = "Save this trip")
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
                                enabled = showing.value,
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
                    Popover(expanded = showing.value, onDismissRequest = {}) {
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
                    visible = showing.value,
                    confirmLabel = "Remove",
                    onConfirm = {},
                    onDismissRequest = {},
                    destructive = true,
                ) {
                    +"Remove this favourite?"
                    supporting { +"Perth Underground will be taken off your home screen." }
                }
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
