package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.withKeyDown
import androidx.compose.ui.unit.dp
import io.kontour.icons.tabler.Tabler
import io.kontour.icons.tabler.outline.Map
import io.kontour.icons.tabler.outline.Plus
import io.kontour.icons.tabler.outline.Star
import io.kontour.ui.components.action.Button
import io.kontour.ui.components.action.FabMenu
import io.kontour.ui.contract.ComponentSpec
import io.kontour.ui.contract.componentRegistry
import io.kontour.ui.input.InputModality
import io.kontour.ui.input.LocalInputModality
import io.kontour.ui.nav.ModalNavRail
import io.kontour.ui.overlay.AlertDialog
import io.kontour.ui.overlay.Command
import io.kontour.ui.overlay.CommandPalette
import io.kontour.ui.overlay.Dialog
import io.kontour.ui.overlay.DropdownMenu
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.overlay.Popover
import io.kontour.ui.sheet.ModalBottomSheet
import io.kontour.ui.sheet.ModalSideSheet
import io.kontour.ui.theme.KontourTheme
import io.kontour.ui.theme.Theme
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The keyboard reaches every control, shows where it is, and stays in a modal.
 *
 * accessibility.md says a focus ring is drawn "when the keyboard reaches" a
 * control, and overlays.md that the content under a modal "cannot take focus".
 * Neither was swept. `KeyboardFocusRingTest` in `:ui` checks the ring on the
 * handful of controls that had none, and `ModalKeepsFocusTest` the trap under a
 * dialog and a sheet; the November 2026 audit found the gaps between them by
 * hand. So:
 *
 * - **Every specimen under contract is reached by Tab**, from nothing focused,
 *   within [MaxTabs] presses, and when it is, more of the focus ring's colour is
 *   on screen than before. Under contract means operable, and operable from a
 *   keyboard is half of that. A control only a pointer can reach is the
 *   failure; one reached and drawn the same as before is the other.
 * - **Tab never leaves an open modal for the page under it**, a page that
 *   scrolls, in either direction, for every modal overlay the library has. A
 *   scrolling list is a focus target of its own, which is how the page under a
 *   dialog stayed reachable until the audit.
 *
 * Each half has a control that must fail.
 */
@OptIn(ExperimentalTestApi::class)
class KeyboardSweepTest {

    @Test
    fun everyControlIsReachedByTabAndShowsWhereItIs() {
        val under = componentRegistry.filter { it.underContract && it.reachedByTab }
        val failures = under.mapNotNull { spec -> reach { spec.content(Modifier.testTag(Subject), true) {} }?.let { "${spec.name}: $it" } }
        assertTrue(
            failures.isEmpty(),
            "${failures.size} of ${under.size} controls are not reached by Tab or do not show it:\n" +
                failures.joinToString("\n") { "  · $it" },
        )
    }

    @Test
    fun aControlTabCannotReachIsCaught() {
        val missed = reach {
            Box(Modifier.testTag(Subject).size(48.dp).focusProperties { canFocus = false }.clickable {})
        }
        assertTrue(missed != null && "not reached" in missed, "the sweep cannot see a control Tab skips: $missed")
    }

    @Test
    fun aControlWithNoRingIsCaught() {
        val unmarked = reach { Box(Modifier.testTag(Subject).size(48.dp).focusable()) }
        assertTrue(unmarked != null && "ring" in unmarked, "the sweep cannot see a control that draws no ring: $unmarked")
    }

    @Test
    fun tabNeverLeavesAnOpenModalForAScrollingPage() {
        val failures = Modals.mapNotNull { (name, modal) -> escapes(modal)?.let { "$name: $it" } }
        assertTrue(
            failures.isEmpty(),
            "${failures.size} of ${Modals.size} modals let Tab out to the page under them:\n" +
                failures.joinToString("\n") { "  · $it" },
        )
    }

    @Test
    fun aPageThatIsNotTrappedIsCaught() {
        // The control: a panel drawn over the page with no overlay host, so
        // nothing keeps the keyboard in it.
        val escaped = escapes {
            Column(Modifier.background(Theme.colours.surface)) {
                Button(onClick = {}) { +"Keep" }
                Button(onClick = {}) { +"Discard" }
            }
        }
        assertTrue(escaped != null && "page" in escaped, "the sweep cannot see Tab leave a panel: $escaped")
    }

    /**
     * Why [content]'s tagged control is not reached by Tab, or does not show
     * it, or null if it is and does.
     */
    private fun reach(content: @Composable () -> Unit): String? {
        var why: String? = null
        runComposeUiTest {
            var ring = Color.Unspecified
            var focus: FocusManager? = null
            setContent {
                focus = LocalFocusManager.current
                KontourTheme(reduceMotion = true) {
                    ring = Theme.colours.focusRing
                    CompositionLocalProvider(LocalInputModality provides InputModality.Keyboard) {
                        OverlayHost {
                            Box(Modifier.background(Theme.colours.background).padding(12.dp).width(360.dp)) { content() }
                        }
                    }
                }
            }
            waitForIdle()
            var reached = false
            for (press in 1..MaxTabs) {
                onRoot().performKeyInput { pressKey(Key.Tab) }
                waitForIdle()
                if (focusedInSubject()) {
                    reached = true
                    break
                }
            }
            // Against the same screen with nothing focused, rather than
            // against the screen before the first Tab: a menu or a palette
            // puts the focus in itself as it opens, ring and all.
            val at = focusedName()
            val shown = ringPixels(ring)
            runOnIdle { focus?.clearFocus(force = true) }
            waitForIdle()
            why = when {
                !reached -> "not reached in $MaxTabs presses of Tab"
                shown - ringPixels(ring) < MinimumRingPixels -> "reached by Tab, at $at, and drew no focus ring"
                else -> null
            }
        }
        return why
    }

    /**
     * Where Tab went that it should not have, with [modal] open over a page
     * that scrolls, or null if it stayed in the modal and reached something in
     * it.
     */
    private fun escapes(modal: @Composable () -> Unit): String? {
        var why: String? = null
        runComposeUiTest {
            setContent {
                KontourTheme(reduceMotion = true) {
                    CompositionLocalProvider(LocalInputModality provides InputModality.Keyboard) {
                        OverlayHost(Modifier.fillMaxSize()) {
                            LazyColumn(Modifier.fillMaxSize()) {
                                items(PageControls) { i ->
                                    Button(onClick = {}, modifier = Modifier.testTag("$Page $i")) { +"$Page $i" }
                                }
                            }
                            modal()
                        }
                    }
                }
            }
            waitForIdle()
            val reached = mutableListOf<String>()
            repeat(Presses) {
                onRoot().performKeyInput { pressKey(Key.Tab) }
                waitForIdle()
                reached += focusedName()
            }
            repeat(Presses) {
                onRoot().performKeyInput { withKeyDown(Key.ShiftLeft) { pressKey(Key.Tab) } }
                waitForIdle()
                reached += focusedName()
            }
            why = when {
                reached.any { it.startsWith(Page) } -> "Tab reached the page under it: $reached"
                reached.none { it != Nothing } -> "Tab reached nothing in it: $reached"
                else -> null
            }
        }
        return why
    }

    private fun ComposeUiTest.focusedInSubject(): Boolean =
        onAllNodes(isFocused() and (hasTestTag(Subject) or hasAnyAncestor(hasTestTag(Subject))), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .isNotEmpty()

    /** What the focused node is called: its tag, its text or its description. */
    private fun ComposeUiTest.focusedName(): String =
        onAllNodes(isFocused(), useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()?.name() ?: Nothing

    private fun SemanticsNode.name(): String =
        config.getOrNull(SemanticsProperties.TestTag)
            ?: config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }
            ?: config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString()
            ?: children.firstNotNullOfOrNull { it.config.getOrNull(SemanticsProperties.Text)?.joinToString { t -> t.text } }
            ?: "node $id"

    private fun ComposeUiTest.ringPixels(ring: Color): Int {
        val pixels = onRoot().captureToImage().toPixelMap()
        var count = 0
        for (y in 0 until pixels.height) {
            for (x in 0 until pixels.width) {
                val pixel = pixels[x, y]
                if (abs(pixel.red - ring.red) < Tolerance &&
                    abs(pixel.green - ring.green) < Tolerance &&
                    abs(pixel.blue - ring.blue) < Tolerance
                ) {
                    count++
                }
            }
        }
        return count
    }

    private companion object {
        const val Subject = "subject"
        const val Page = "Page"
        const val Nothing = "nothing"

        /** Enough to pass a page's worth of controls before the one asked for. */
        const val MaxTabs = 24

        /** Round a modal's controls and then some, each way. */
        const val Presses = 8

        /** A page of buttons in a list that scrolls, under the modal. */
        const val PageControls = 3

        /** `KeyboardFocusRingTest`'s: a ring round the smallest control is more. */
        const val MinimumRingPixels = 40
        const val Tolerance = 0.04f

        /** Every modal overlay the library has, open, each with a control or two in it. */
        val Modals: List<Pair<String, @Composable () -> Unit>> = listOf(
            "Dialog" to {
                Dialog(visible = true, onDismissRequest = {}) {
                    Column {
                        Button(onClick = {}) { +"Keep" }
                        Button(onClick = {}) { +"Discard" }
                    }
                }
            },
            "AlertDialog" to {
                AlertDialog(visible = true, onDismissRequest = {}, confirmLabel = "Remove", onConfirm = {}) {
                    +"Remove this favourite?"
                }
            },
            "CommandPalette" to {
                CommandPalette(
                    visible = true,
                    onDismissRequest = {},
                    commands = listOf(
                        Command("plan", "Plan a trip", onAction = {}),
                        Command("stops", "Nearby stops", onAction = {}),
                    ),
                )
            },
            "ModalBottomSheet" to {
                ModalBottomSheet(visible = true, onDismissRequest = {}) {
                    Column {
                        Button(onClick = {}) { +"Keep" }
                        Button(onClick = {}) { +"Discard" }
                    }
                }
            },
            "ModalSideSheet" to {
                ModalSideSheet(visible = true, onDismissRequest = {}) {
                    Button(onClick = {}) { +"Keep" }
                    Button(onClick = {}) { +"Discard" }
                }
            },
            "ModalNavRail" to {
                ModalNavRail(visible = true, onDismissRequest = {}) {
                    item(selected = true, onClick = {}, key = "plan", icon = Tabler.Outline.Star) { +"Plan" }
                    item(selected = false, onClick = {}, key = "map", icon = Tabler.Outline.Map) { +"Map" }
                }
            },
            "DropdownMenu" to {
                Box {
                    DropdownMenu(visible = true, onDismissRequest = {}) {
                        item("Share") {}
                        item("Copy stop ID") {}
                    }
                }
            },
            "Popover" to {
                Box {
                    Popover(visible = true, onDismissRequest = {}) {
                        Column {
                            Button(onClick = {}) { +"Keep" }
                            Button(onClick = {}) { +"Discard" }
                        }
                    }
                }
            },
            "FabMenu" to {
                FabMenu(expanded = true, onExpandedChange = {}, icon = Tabler.Outline.Plus, contentDescription = "Add") {
                    item(Tabler.Outline.Star, "Save stop") {}
                    item(Tabler.Outline.Map, "Nearby") {}
                }
            },
        )
    }
}
