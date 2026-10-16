package io.kontour.ui.sheet

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kontour.ui.foundation.Text
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.KontourTheme
import io.kontour.ui.theme.Theme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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

    /**
     * The handle offers what it would do, and only that.
     *
     * Expand and collapse were on it whatever the sheet was doing, so the default
     * modal sheet — hidden or expanded, and open — offered both and did nothing
     * for either, and offered nothing at all for putting it away: a reader who
     * cannot drag had a handle of two dead actions.
     */
    @Test
    fun aModalSheetAtItsOneSizeOffersDismissAndNotExpandOrCollapse() = runComposeUiTest {
        var dismissed by mutableStateOf(false)
        val actions = handle(
            detents = listOf(SheetDetent.Hidden, SheetDetent.Expanded),
            onDismissRequest = { dismissed = true },
        ).config
        assertFalse(SemanticsActions.Expand in actions, "the handle offers to expand a sheet that has nowhere taller to go")
        assertFalse(SemanticsActions.Collapse in actions, "the handle offers to collapse a sheet that has nowhere shorter to go")
        assertTrue(SemanticsActions.Dismiss in actions, "the handle offers no way to put the sheet away")

        handleNode().performSemanticsAction(SemanticsActions.Dismiss)
        waitForIdle()
        assertTrue(dismissed, "the handle's dismiss did not reach the caller's onDismissRequest")
    }

    /**
     * At the top of three, it collapses and dismisses, and does not offer to
     * expand.
     *
     * Over content taller than half the window: a sheet's half is no shorter
     * than all of content that fits in it, and over a line of text `Half` and
     * `Expanded` are one height, with nothing to collapse to — which the handle
     * rightly does not offer.
     */
    @Test
    fun aModalSheetAtItsTallestOffersCollapseAndDismiss() = runComposeUiTest {
        val actions = handle(
            detents = listOf(SheetDetent.Hidden, SheetDetent.Half, SheetDetent.Expanded),
            content = { Box(Modifier.fillMaxWidth().height(1200.dp)) },
        ).config
        assertFalse(SemanticsActions.Expand in actions, "the handle offers to expand a sheet already at its tallest")
        assertTrue(SemanticsActions.Collapse in actions, "the handle does not offer to collapse a sheet with a shorter size")
        assertTrue(SemanticsActions.Dismiss in actions, "the handle offers no way to put the sheet away")
    }

    /** A sheet the user may not put away offers no dismissal from its handle either. */
    @Test
    fun anUndismissableSheetsHandleOffersNoDismiss() = runComposeUiTest {
        val actions = handle(
            detents = listOf(SheetDetent.Hidden, SheetDetent.Half, SheetDetent.Expanded),
            dismissible = false,
        ).config
        assertFalse(SemanticsActions.Dismiss in actions, "an undismissable sheet's handle offers to dismiss it")
    }

    private var handleLabels: List<String> = emptyList()

    /** A modal sheet shown and settled, and its handle's semantics node. */
    private fun ComposeUiTest.handle(
        detents: List<SheetDetent>,
        dismissible: Boolean = true,
        onDismissRequest: () -> Unit = {},
        content: @Composable () -> Unit = { Text("A choice") },
    ): SemanticsNode {
        var visible by mutableStateOf(true)
        setContent {
            KontourTheme(reduceMotion = true) {
                handleLabels = listOf(Theme.strings.expandSheet, Theme.strings.collapseSheet)
                OverlayHost(Modifier.fillMaxSize()) {
                    val state = rememberSheetState(detents = detents, initialDetent = SheetDetent.Hidden)
                    ModalBottomSheet(
                        visible = visible,
                        onDismissRequest = {
                            visible = false
                            onDismissRequest()
                        },
                        state = state,
                        dismissible = dismissible,
                    ) {
                        content()
                    }
                }
            }
        }
        waitForIdle()
        return handleNode().fetchSemanticsNode()
    }

    private fun ComposeUiTest.handleNode() =
        onNode(hasContentDescription(handleLabels[0]) or hasContentDescription(handleLabels[1]), useUnmergedTree = true)
}
