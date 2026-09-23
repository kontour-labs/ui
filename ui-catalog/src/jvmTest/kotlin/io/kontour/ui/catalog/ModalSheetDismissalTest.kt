package io.kontour.ui.catalog

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import io.kontour.ui.foundation.Text
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.sheet.ModalBottomSheet
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `onDismissRequest` means the *user* asked to close the sheet.
 *
 * It used to mean "the sheet reached the bottom", which is the same thing only
 * when the user is the one who put it there. A caller that closed the sheet itself
 * — `visible = false` — was then told the user had dismissed it, one frame later.
 * For a caller whose dismissal is `open = false` that is a second write of the
 * same value and nobody noticed. For one whose dismissal *does* something — pops
 * a back stack, logs an event, navigates — it did it twice. Found by the
 * Navigation 3 supporting-pane strategy, where a system back popped the sheet's
 * entry and the sheet's own exit then popped the page underneath it as well.
 */
@OptIn(ExperimentalTestApi::class)
class ModalSheetDismissalTest {

    @Test
    fun closingTheSheetYourselfIsNotADismissal() = runDesktopComposeUiTest(width = 400, height = 800) {
        var open by mutableStateOf(true)
        var dismissals = 0
        setContent {
            KontourTheme {
                OverlayHost {
                    ModalBottomSheet(visible = open, onDismissRequest = { dismissals++; open = false }) {
                        Text("a sheet")
                    }
                }
            }
        }
        waitForIdle()
        onNodeWithText("a sheet").assertExists()

        open = false
        waitForIdle()

        onNodeWithText("a sheet").assertDoesNotExist()
        assertEquals(0, dismissals, "the caller closed the sheet and was told the user had dismissed it")
    }

    @Test
    fun theUserDismissingItIsStillReportedOnce() = runDesktopComposeUiTest(width = 400, height = 800) {
        // The control: narrowing what counts as a dismissal must not lose the
        // real ones.
        var open by mutableStateOf(true)
        var dismissals = 0
        setContent {
            KontourTheme {
                OverlayHost {
                    ModalBottomSheet(visible = open, onDismissRequest = { dismissals++; open = false }) {
                        Text("a sheet")
                    }
                }
            }
        }
        waitForIdle()

        // The scrim, as a screen reader presses it.
        onNode(hasContentDescription("Close") and SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick))
            .performSemanticsAction(SemanticsActions.OnClick)
        waitForIdle()

        onNodeWithText("a sheet").assertDoesNotExist()
        assertEquals(1, dismissals, "a dismissal by the user should be reported exactly once")
    }
}
