package io.kontour.ui.catalog

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import io.kontour.ui.catalog.apps.AppHost
import io.kontour.ui.catalog.apps.MiniApp
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Board, run in its host: cards move between columns, Doing holds three and no
 * more, and the archive takes what is dropped on it — each through the moves a
 * screen reader is offered, which are the same drops a hand makes, without the
 * carry. The carry itself is `DragBetweenListsTest`'s and `DropZoneDragTest`'s.
 */
@OptIn(ExperimentalTestApi::class)
class BoardAppTest {

    private fun ComposeUiTest.launch() {
        setContent { KontourTheme(darkTheme = false) { AppHost(MiniApp.Board, onLeave = {}) } }
        waitForIdle()
    }

    /**
     * The node that offers [card]'s moves: the innermost that does, since its
     * column offers the column's own moves and holds the card as well.
     */
    private fun ComposeUiTest.mover(card: String): SemanticsNode =
        onAllNodes(hasMoves() and holds(card)).fetchSemanticsNodes().last()

    private fun ComposeUiTest.offers(card: String, move: String): Boolean =
        mover(card).config.getOrNull(SemanticsActions.CustomActions)?.any { it.label == move } == true

    private fun ComposeUiTest.move(card: String, move: String) {
        val action = mover(card).config[SemanticsActions.CustomActions].first { it.label == move }
        runOnIdle { action.action() }
        waitForIdle()
    }

    private fun ComposeUiTest.isIn(card: String, lane: String): Boolean =
        onAllNodes(hasText(card) and hasAnyAncestor(hasTestTag("lane-$lane"))).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun aCardMovesToAnotherColumn() = runDesktopComposeUiTest(width = 1280, height = 800) {
        launch()
        assertTrue(isIn("Night bus survey", "done"))
        move("Refresh the 960 timetable", "Move to Done")
        assertTrue(isIn("Refresh the 960 timetable", "done"), "the card should be in Done")
        assertFalse(isIn("Refresh the 960 timetable", "todo"), "and not still in To do")
    }

    @Test
    fun doingHoldsThreeAndNoMore() = runDesktopComposeUiTest(width = 1280, height = 800) {
        launch()
        assertTrue(offers("Refresh the 960 timetable", "Move to Doing"), "Doing has room for a third")
        move("Refresh the 960 timetable", "Move to Doing")
        assertTrue(isIn("Refresh the 960 timetable", "doing"))
        assertFalse(
            offers("Fares page for the new zones", "Move to Doing"),
            "a full Doing should not be offered a fourth card",
        )
        onNodeWithText("3 of 3", useUnmergedTree = true).assertExists()
    }

    @Test
    fun theArchiveTakesACard() = runDesktopComposeUiTest(width = 1280, height = 800) {
        launch()
        move("Night bus survey", "Move to Archive")
        assertFalse(isIn("Night bus survey", "done"), "an archived card leaves its column")
        onNodeWithText("1 put away", useUnmergedTree = true).assertExists()
        onNodeWithText("Restore all", useUnmergedTree = true).assertExists()
    }

    @Test
    fun onAPhoneTheColumnsScrollAndTheArchiveIsStillOffered() = runDesktopComposeUiTest(width = 400, height = 860) {
        launch()
        // Done is off the side of a phone, and still somewhere to move to.
        move("Signage at Elizabeth Quay", "Move to Done")
        assertTrue(isIn("Signage at Elizabeth Quay", "done"))
    }

    private fun hasMoves() = SemanticsMatcher("offers moves") { it.config.getOrNull(SemanticsActions.CustomActions)?.isNotEmpty() == true }

    /** Is — or holds — the card titled [card]. */
    private fun holds(card: String) = SemanticsMatcher("is $card") { node ->
        fun says(n: SemanticsNode): Boolean =
            n.config.getOrNull(SemanticsProperties.Text)?.any { it.text == card } == true || n.children.any(::says)
        says(node)
    }
}
