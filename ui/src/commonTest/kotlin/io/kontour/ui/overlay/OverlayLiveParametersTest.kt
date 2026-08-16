package io.kontour.ui.overlay

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.kontour.ui.foundation.Text
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertEquals

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
}
