package io.kontour.ui.overlay

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.kontour.ui.foundation.Text
import io.kontour.ui.sheet.ModalBottomSheet
import io.kontour.ui.sheet.ModalSideSheet
import io.kontour.ui.theme.KontourTheme
import io.kontour.ui.theme.Theme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * An overlay that is already showing still listens to its parameters.
 *
 * Every overlay in this library publishes an `OverlayEntry` from inside a
 * `LaunchedEffect`, and that effect is keyed on the handful of inputs that
 * decide *whether* it shows — `visible`, the anchor, the scrim. Everything else
 * the entry's content closes over is captured at the moment it was published, so
 * without care it is frozen for as long as the overlay is up: re-theme a dialog
 * that is open, or resize it, and nothing happens until it is dismissed and
 * shown again.
 *
 * `content` and `onDismissRequest` were hoisted through `rememberUpdatedState`
 * from the start. The appearance parameters were not, and nothing noticed —
 * they are the ones nobody changes mid-flight in a catalog, and a screenshot of
 * a settled overlay cannot see it either.
 *
 * A width is the cheapest thing to assert on: it is in the modifier, and the
 * modifier is the parameter every one of these components takes.
 */
@OptIn(ExperimentalTestApi::class)
class OverlayLiveParametersTest {

    @Test
    fun aDialogFollowsItsModifierWhileItIsOpen() {
        assertFollowsModifier { width, content ->
            Dialog(visible = true, onDismissRequest = {}, modifier = Modifier.width(width)) {
                content()
            }
        }
    }

    @Test
    fun aModalSheetFollowsItsModifierWhileItIsOpen() {
        assertFollowsModifier { width, content ->
            io.kontour.ui.sheet.ModalBottomSheet(
                visible = true,
                onDismissRequest = {},
                modifier = Modifier.width(width),
            ) {
                content()
            }
        }
    }

    /**
     * Shows the overlay at one width, changes the width, and checks the node
     * moved. Measured rather than counted: the node exists either way — the
     * question is whether it is listening.
     */
    private fun assertFollowsModifier(
        overlay: @Composable (Dp, @Composable () -> Unit) -> Unit,
    ) {
        runComposeUiTest {
            var width by mutableStateOf(200.dp)
            setContent {
                KontourTheme(reduceMotion = true) {
                    OverlayHost(Modifier.fillMaxSize()) {
                        overlay(width) { Text("Rename favourite") }
                    }
                }
            }
            waitForIdle()
            val before = onNodeWithText("Rename favourite").fetchSemanticsNode().positionInRoot.x

            width = 360.dp
            waitForIdle()
            val after = onNodeWithText("Rename favourite").fetchSemanticsNode().positionInRoot.x

            assertEquals(
                true,
                before != after,
                "the overlay ignored its modifier while it was showing — the entry " +
                    "captured it when it was published instead of reading it live",
            )
        }
    }

    // ---------------------------------------------------------------------
    // `dismissible`, read as the reader tries rather than when it opened
    // ---------------------------------------------------------------------
    //
    // The commonest live parameter there is: `dismissible = !form.dirty`. The
    // entry's flags were fixed when it was published, so a reader who had typed
    // into the form could still tap the scrim or go back, and the typing went
    // with the overlay. The other way round, an overlay that became dismissible
    // while it was up still refused. The drag of a sheet already read it live;
    // the scrim and the back gesture did not.

    @Test
    fun aDialogMadeUndismissableWhileOpenRefusesTheScrimAndBack() =
        assertRefusesOnceUndismissable { dismissible, onDismissRequest ->
            Dialog(visible = true, onDismissRequest = onDismissRequest, dismissible = dismissible, dismissLabel = Away) {
                Text("Rename favourite")
            }
        }

    @Test
    fun aDialogMadeDismissibleWhileOpenTakesTheScrimAndBack() =
        assertAcceptsOnceDismissible { dismissible, onDismissRequest ->
            Dialog(visible = true, onDismissRequest = onDismissRequest, dismissible = dismissible, dismissLabel = Away) {
                Text("Rename favourite")
            }
        }

    @Test
    fun aModalSheetMadeUndismissableWhileOpenRefusesTheScrimAndBack() =
        assertRefusesOnceUndismissable { dismissible, onDismissRequest ->
            ModalBottomSheet(visible = true, onDismissRequest = onDismissRequest, dismissible = dismissible, dismissLabel = Away) {
                Text("Rename favourite")
            }
        }

    @Test
    fun aModalSheetMadeDismissibleWhileOpenTakesTheScrimAndBack() =
        assertAcceptsOnceDismissible { dismissible, onDismissRequest ->
            ModalBottomSheet(visible = true, onDismissRequest = onDismissRequest, dismissible = dismissible, dismissLabel = Away) {
                Text("Rename favourite")
            }
        }

    @Test
    fun aModalSideSheetMadeUndismissableWhileOpenRefusesTheScrimAndBack() =
        assertRefusesOnceUndismissable { dismissible, onDismissRequest ->
            ModalSideSheet(visible = true, onDismissRequest = onDismissRequest, dismissible = dismissible, dismissLabel = Away) {
                Text("Rename favourite")
            }
        }

    @Test
    fun aModalSideSheetMadeDismissibleWhileOpenTakesTheScrimAndBack() =
        assertAcceptsOnceDismissible { dismissible, onDismissRequest ->
            ModalSideSheet(visible = true, onDismissRequest = onDismissRequest, dismissible = dismissible, dismissLabel = Away) {
                Text("Rename favourite")
            }
        }

    @Test
    fun aCommandPaletteMadeUndismissableWhileOpenRefusesTheScrimAndBack() =
        assertRefusesOnceUndismissable(label = { Theme.strings.dismiss }) { dismissible, onDismissRequest ->
            CommandPalette(visible = true, onDismissRequest = onDismissRequest, commands = Commands, dismissible = dismissible)
        }

    @Test
    fun aCommandPaletteMadeDismissibleWhileOpenTakesTheScrimAndBack() =
        assertAcceptsOnceDismissible(label = { Theme.strings.dismiss }) { dismissible, onDismissRequest ->
            CommandPalette(visible = true, onDismissRequest = onDismissRequest, commands = Commands, dismissible = dismissible)
        }

    /**
     * And a dialog reports to the `onDismissRequest` it has now.
     *
     * Its scrim and the back gesture were handed the callback as it was when the
     * dialog opened, while its own buttons already went through the latest one:
     * a caller whose callback closes over something that has since changed was
     * told on the old one, and only for those two ways out.
     */
    @Test
    fun aDialogReportsADismissalToTheCallbackItHasNow() {
        for (route in Route.entries) {
            var told = ""
            val first: () -> Unit = { told = "first" }
            val second: () -> Unit = { told = "second" }
            runComposeUiTest {
                var callback by mutableStateOf(first)
                lateinit var host: OverlayHostState
                setContent {
                    host = rememberOverlayHostState()
                    KontourTheme(reduceMotion = true) {
                        OverlayHost(Modifier.fillMaxSize(), state = host) {
                            Dialog(visible = true, onDismissRequest = callback, dismissLabel = Away) {
                                Text("Rename favourite")
                            }
                        }
                    }
                }
                waitForIdle()
                callback = second
                waitForIdle()
                take(route, host, Away)
                waitForIdle()
            }
            assertEquals("second", told, "a dismissal by $route was reported to the callback the dialog opened with")
        }
    }

    /** The two ways out a reader has that the overlay does not draw itself. */
    private enum class Route { Scrim, Back }

    /** Takes [route] out of the topmost overlay, if it offers one. */
    private fun ComposeUiTest.take(route: Route, host: OverlayHostState, label: String) {
        when (route) {
            Route.Scrim ->
                if (scrims(label).fetchSemanticsNodes().isNotEmpty()) {
                    scrims(label).onFirst().performSemanticsAction(SemanticsActions.OnClick)
                }
            Route.Back -> host.dismissTop()
        }
    }

    /** Opened dismissible, then made not: neither the scrim nor back may close it. */
    private fun assertRefusesOnceUndismissable(
        label: @Composable () -> String = { Away },
        overlay: @Composable (dismissible: Boolean, onDismissRequest: () -> Unit) -> Unit,
    ) {
        for (route in Route.entries) {
            val asked = dismissalsAfterFlip(opened = true, route = route, label = label, overlay = overlay)
            assertEquals(
                0,
                asked,
                "made undismissable while it was open, the overlay still asked to be dismissed by $route",
            )
        }
    }

    /** Opened undismissable, then made dismissible: the scrim and back both close it. */
    private fun assertAcceptsOnceDismissible(
        label: @Composable () -> String = { Away },
        overlay: @Composable (dismissible: Boolean, onDismissRequest: () -> Unit) -> Unit,
    ) {
        for (route in Route.entries) {
            val asked = dismissalsAfterFlip(opened = false, route = route, label = label, overlay = overlay)
            assertEquals(
                1,
                asked,
                "made dismissible while it was open, the overlay still refused to be dismissed by $route",
            )
        }
    }

    /**
     * Shows the overlay with `dismissible` as [opened], turns it over, takes
     * [route] out, and counts how many times the overlay asked to be dismissed.
     * One composition per route: a dismissal the host carries out takes the
     * overlay away, so the second route would find nothing to try.
     */
    private fun dismissalsAfterFlip(
        opened: Boolean,
        route: Route,
        label: @Composable () -> String,
        overlay: @Composable (dismissible: Boolean, onDismissRequest: () -> Unit) -> Unit,
    ): Int {
        var asked = 0
        runComposeUiTest {
            var dismissible by mutableStateOf(opened)
            var scrimLabel = ""
            lateinit var host: OverlayHostState
            setContent {
                host = rememberOverlayHostState()
                KontourTheme(reduceMotion = true) {
                    scrimLabel = label()
                    OverlayHost(Modifier.fillMaxSize(), state = host) {
                        overlay(dismissible) { asked++ }
                    }
                }
            }
            waitForIdle()
            if (opened) {
                assertTrue(
                    scrims(scrimLabel).fetchSemanticsNodes().isNotEmpty(),
                    "opened dismissible, the overlay's scrim offered no dismissal, so this measured nothing",
                )
            }

            dismissible = !opened
            waitForIdle()
            take(route, host, scrimLabel)
            waitForIdle()
        }
        return asked
    }

    /** The scrim, by the label it says its dismissal with — and only while it offers one. */
    private fun ComposeUiTest.scrims(label: String) =
        onAllNodes(hasContentDescription(label) and hasClickAction())

    private companion object {
        /** What the scrim's dismissal is called here, so it is the only node by that name. */
        const val Away = "Put it away"

        val Commands = listOf(Command(id = "save", label = "Save trip", onAction = {}))
    }
}
