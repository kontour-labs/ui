package io.kontour.ui.dragdrop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.list.ListItem
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A card moved between lists without a pointer: by keys, and by the moves a
 * screen reader offers.
 *
 * Carried by a finger alone, a board is a board only for people who can make
 * the gesture. Space on a handle picks a card up, the arrows move it — along its
 * list, and across to the next — Enter puts it down and Escape puts it back. A
 * screen reader gets the same as named moves on the card, "Move to Done", and
 * hears each step said.
 */
@OptIn(ExperimentalTestApi::class)
class DragKeyboardTest {

    @Test
    fun spacePicksUpTheArrowsCarryAndEnterPutsDown() = runComposeUiTest {
        val board = board()
        grip("Fares").requestFocus()
        keys(Key.Spacebar)
        assertTrue(board.said().startsWith("Picked up Fares"), "a pick-up should be said: '${board.said()}'")
        // Across to Done, at the same place, then one further down.
        keys(Key.DirectionRight)
        keys(Key.DirectionDown)
        assertEquals(2, board.done.state!!.dropIndex, "two arrows: across to Done at place 1, then down a place")
        assertTrue("Done" in board.said(), "the place should be said: '${board.said()}'")
        keys(Key.Enter)
        waitForIdle()
        assertEquals(listOf("Timetable", "Signage"), board.todo.items.toList())
        assertEquals(listOf("Maps", "Wi-Fi", "Fares"), board.done.items.toList())
        assertEquals(1, board.drops.size, "one drop for one move")
        assertTrue(board.said().contains("dropped in Done"), "the drop should be said: '${board.said()}'")
    }

    @Test
    fun escapePutsItBack() = runComposeUiTest {
        val board = board()
        grip("Fares").requestFocus()
        keys(Key.Spacebar)
        keys(Key.DirectionRight)
        keys(Key.Escape)
        waitForIdle()
        assertTrue(board.drops.isEmpty())
        assertEquals(listOf("Timetable", "Fares", "Signage"), board.todo.items.toList())
        assertTrue(board.said().contains("put back"), "a cancel should be said: '${board.said()}'")
    }

    @Test
    fun aScreenReaderMovesACardStraightToAnotherList() = runComposeUiTest {
        val board = board()
        val move = onNode(hasMove("Move to Done") and hasMoveFor("Fares")).fetchSemanticsNode()
            .config[SemanticsActions.CustomActions].first { it.label == "Move to Done" }
        runOnIdle { move.action() }
        waitForIdle()
        assertEquals(listOf("Timetable", "Signage"), board.todo.items.toList())
        assertEquals(listOf("Maps", "Wi-Fi", "Fares"), board.done.items.toList(), "a move to a list goes to its end")
        assertEquals(2, board.drops.single().index)
    }

    @Test
    fun aScreenReaderMovesACardUpItsOwnList() = runComposeUiTest {
        val board = board()
        val node = onNode(hasMove("Move up") and hasMoveFor("Signage")).fetchSemanticsNode()
        val up = node.config[SemanticsActions.CustomActions].first { it.label == "Move up" }
        runOnIdle { up.action() }
        waitForIdle()
        assertEquals(listOf("Timetable", "Signage", "Fares"), board.todo.items.toList())
        val dropped = board.drops.single()
        assertEquals(1, dropped.index)
        assertEquals(2, dropped.fromIndex)
    }

    // -----------------------------------------------------------------------

    private class Column(initial: List<String>, val label: String) {
        val items: SnapshotStateList<String> = mutableStateListOf<String>().also { it.addAll(initial) }
        var state: DropListState<String>? = null
    }

    private class Board {
        val todo = Column(listOf("Timetable", "Fares", "Signage"), "To do")
        val done = Column(listOf("Maps", "Wi-Fi"), "Done")
        val drops = mutableListOf<DropEvent<String>>()
        var host: DragHost? = null

        fun said(): String = host?.said.orEmpty()

        fun move(event: DropEvent<String>, into: Column) {
            val from = listOf(todo, done).first { it.state === event.fromList }
            from.items.removeAt(event.fromIndex!!)
            into.items.add(event.index, event.payload)
        }
    }

    private fun ComposeUiTest.board(): Board {
        val board = Board()
        setContent {
            KontourTheme {
                OverlayHost {
                    board.host = LocalDragHost.current
                    Row(Modifier.fillMaxSize()) {
                        Lane(board, board.todo, Modifier.weight(1f))
                        Lane(board, board.done, Modifier.weight(1f))
                    }
                }
            }
        }
        waitForIdle()
        return board
    }

    @Composable
    private fun Lane(board: Board, column: Column, modifier: Modifier) {
        val scroll = rememberLazyListState()
        val state = rememberDropListState(
            listState = scroll,
            type = Card,
            items = column.items,
            key = { it },
            label = column.label,
            itemLabel = { it },
            onDrop = { event ->
                board.drops += event
                board.move(event, column)
            },
        )
        column.state = state
        LazyColumn(state = scroll, modifier = modifier.fillMaxHeight().dropList(state)) {
            dragItems(state) { _, item ->
                ListItem {
                    +item
                    trailing { Box(Modifier.size(24.dp).testTag("grip-$item").dragHandle()) }
                }
            }
        }
    }

    // The grip merges into its row, as a control inside a row does.
    private fun ComposeUiTest.grip(item: String): SemanticsNodeInteraction = onNodeWithTag("grip-$item", useUnmergedTree = true)

    private fun ComposeUiTest.keys(key: Key) {
        onRoot().performKeyInput { pressKey(key) }
        waitForIdle()
    }

    private fun hasMove(label: String) = SemanticsMatcher("offers \"$label\"") { node ->
        node.config.getOrNull(SemanticsActions.CustomActions)?.any { it.label == label } == true
    }

    /** Has a move, and is — or holds — the item named [item]. */
    private fun hasMoveFor(item: String) = SemanticsMatcher("is $item") { node ->
        fun says(n: androidx.compose.ui.semantics.SemanticsNode): Boolean =
            n.config.getOrNull(SemanticsProperties.Text)?.any { it.text == item } == true || n.children.any(::says)
        says(node)
    }

    private companion object {
        val Card = DragType<String>("card")
    }
}
