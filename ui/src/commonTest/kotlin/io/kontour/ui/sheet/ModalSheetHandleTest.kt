package io.kontour.ui.sheet

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.v2.runComposeUiTest
import io.kontour.ui.foundation.Text
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.KontourTheme
import io.kontour.ui.theme.Theme
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A modal sheet that cannot be dismissed, and has one size to be, has no drag
 * handle: there is nowhere a drag could take it.
 */
@OptIn(ExperimentalTestApi::class)
class ModalSheetHandleTest {

    private fun ComposeUiTest.handles(dismissible: Boolean, detents: List<SheetDetent>): Int {
        var expand = ""
        var collapse = ""
        setContent {
            KontourTheme(reduceMotion = true) {
                expand = Theme.strings.expandSheet
                collapse = Theme.strings.collapseSheet
                OverlayHost(Modifier.fillMaxSize()) {
                    val state = rememberSheetState(detents = detents, initialDetent = SheetDetent.Hidden)
                    ModalBottomSheet(visible = true, onDismissRequest = {}, state = state, dismissible = dismissible) {
                        Text("A required choice")
                    }
                }
            }
        }
        waitForIdle()
        return onAllNodes(hasContentDescription(expand) or hasContentDescription(collapse), useUnmergedTree = true).fetchSemanticsNodes().size
    }

    @Test
    fun anUndismissableSheetWithOneSizeHasNoHandle() = runComposeUiTest {
        assertEquals(0, handles(dismissible = false, detents = listOf(SheetDetent.Hidden, SheetDetent.Expanded)))
    }

    @Test
    fun anUndismissableSheetWithSizesToDragBetweenKeepsItsHandle() = runComposeUiTest {
        assertEquals(1, handles(dismissible = false, detents = listOf(SheetDetent.Hidden, SheetDetent.Half, SheetDetent.Expanded)))
    }

    @Test
    fun aDismissibleSheetKeepsItsHandle() = runComposeUiTest {
        assertEquals(1, handles(dismissible = true, detents = listOf(SheetDetent.Hidden, SheetDetent.Expanded)))
    }
}
