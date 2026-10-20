package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerType
import io.kontour.ui.components.list.ListItem
import io.kontour.ui.dragdrop.DragType
import io.kontour.ui.dragdrop.DropEvent
import io.kontour.ui.dragdrop.DropListState
import io.kontour.ui.dragdrop.DropStatus
import io.kontour.ui.dragdrop.dragItems
import io.kontour.ui.dragdrop.dropList
import io.kontour.ui.dragdrop.rememberDropListState
import io.kontour.ui.interaction.FeedbackDispatcher
import io.kontour.ui.interaction.FeedbackIntent
import io.kontour.ui.interaction.LocalFeedback
import io.kontour.ui.overlay.OverlayHost
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * A card carried from one list to another: the kanban board the reorderable
 * list could not make.
 *
 * Reported: *"The reorderable list can't drag between lists, which a kanban board
 * needs."* `ReorderableItem` moves a row within its own list and nowhere else.
 * These drive real touches and a real mouse through two `dropList`s side by side
 * and read back what the caller was told, once, at the drop.
 */
class DragBetweenListsTest {

    @Test
    fun aHeldCardCarriedToAnotherListLandsAtThePlaceItWasLetGo() {
        val board = Board(todo = listOf("Timetable", "Fares", "Signage", "Lifts"), done = listOf("Maps", "Wi-Fi", "Bins"))
        board.run { scene ->
            val grab = board.bounds.getValue("Fares").center
            // Between Maps and Wi-Fi: Wi-Fi's top edge, which is past Maps' middle
            // and before Wi-Fi's.
            val drop = Offset(board.doneList.center.x, board.bounds.getValue("Wi-Fi").top + 4f)
            scene.hold(grab)
            scene.carry(grab, drop)

            assertEquals(1, board.done.state!!.dropIndex, "the gap should be under the card, before Wi-Fi, as it is carried")
            val wifiBefore = board.restingTop.getValue("Wi-Fi")
            assertTrue(
                board.bounds.getValue("Wi-Fi").top - wifiBefore > board.bounds.getValue("Maps").height * 0.8f,
                "Wi-Fi should have moved down to make room: it is at ${board.bounds.getValue("Wi-Fi").top}, was at $wifiBefore",
            )
            assertTrue(board.drops.isEmpty(), "nothing should have been dropped while it is still carried")

            scene.release(drop)
            scene.frames(40)
        }
        assertEquals(listOf("Timetable", "Signage", "Lifts"), board.todo.items.toList())
        assertEquals(listOf("Maps", "Fares", "Wi-Fi", "Bins"), board.done.items.toList())
        val dropped = board.drops.single()
        assertEquals(1, dropped.index)
        assertEquals(1, dropped.fromIndex)
        assertTrue(dropped.fromList === board.todo.state, "the drop should say which list it came from")
    }

    @Test
    fun aMousePicksUpWithoutAHold() {
        val board = Board(todo = listOf("Timetable", "Fares", "Signage"), done = listOf("Maps", "Wi-Fi"))
        board.run { scene ->
            val grab = board.bounds.getValue("Timetable").center
            // Well below the last card, wherever the gap has pushed it.
            val drop = Offset(board.doneList.center.x, board.doneList.bottom - 200f)
            // A mouse is a mouse once it has moved: that is what tells the theme.
            scene.move(grab - Offset(4f, 4f), PointerType.Mouse)
            scene.frames(2)
            scene.press(grab, PointerType.Mouse)
            scene.frame()
            scene.carry(grab, drop, PointerType.Mouse)
            scene.release(drop, PointerType.Mouse)
            scene.frames(40)
        }
        assertEquals(listOf("Maps", "Wi-Fi", "Timetable"), board.done.items.toList(), "let go below the last card, it should be last")
        assertEquals(2, board.drops.single().index)
    }

    @Test
    fun withinOneListItMovesToItsNewPlace() {
        val board = Board(todo = listOf("Timetable", "Fares", "Signage", "Lifts"), done = listOf("Maps"))
        board.run { scene ->
            val grab = board.bounds.getValue("Timetable").center
            // Past Signage's middle and before Lifts': into the third place.
            val drop = Offset(grab.x, board.bounds.getValue("Lifts").top + 4f)
            scene.hold(grab)
            scene.carry(grab, drop)
            scene.release(drop)
            scene.frames(40)
        }
        assertEquals(listOf("Fares", "Signage", "Timetable", "Lifts"), board.todo.items.toList())
        val dropped = board.drops.single()
        assertEquals(2, dropped.index, "counted as if it had already left: Fares, Signage, here")
        assertEquals(0, dropped.fromIndex)
        assertTrue(dropped.fromList === board.todo.state)
    }

    @Test
    fun anEmptyListTakesACard() {
        val board = Board(todo = listOf("Timetable", "Fares"), done = emptyList())
        board.run { scene ->
            val grab = board.bounds.getValue("Fares").center
            val drop = board.doneList.center
            scene.hold(grab)
            scene.carry(grab, drop)
            assertEquals(0, board.done.state!!.dropIndex)
            scene.release(drop)
            scene.frames(40)
        }
        assertEquals(listOf("Fares"), board.done.items.toList())
        assertEquals(0, board.drops.single().index)
    }

    @Test
    fun putBackWhereItWasNothingIsDropped() {
        val board = Board(todo = listOf("Timetable", "Fares", "Signage"), done = listOf("Maps"))
        board.run { scene ->
            val grab = board.bounds.getValue("Fares").center
            scene.hold(grab)
            scene.carry(grab, grab + Offset(0f, 6f))
            scene.release(grab + Offset(0f, 6f))
            scene.frames(40)
        }
        assertTrue(board.drops.isEmpty(), "a card let go in its own place has not moved: ${board.drops.map { it.payload }}")
        assertEquals(listOf("Timetable", "Fares", "Signage"), board.todo.items.toList())
    }

    @Test
    fun letGoOverNothingItGoesHome() {
        val board = Board(todo = listOf("Timetable", "Fares"), done = listOf("Maps"), withGutter = true)
        board.run { scene ->
            val grab = board.bounds.getValue("Fares").center
            val nowhere = board.gutterBounds.center
            scene.hold(grab)
            scene.carry(grab, nowhere)
            scene.release(nowhere)
            scene.frames(40)
        }
        assertTrue(board.drops.isEmpty())
        assertEquals(listOf("Timetable", "Fares"), board.todo.items.toList())
    }

    /**
     * A snap for each new place, and nothing for holding still over one. The
     * gap opening under the card moves what is under the finger; measured as if
     * it were not there, the place does not change, and nor does the snap.
     */
    @Test
    fun holdingStillOverAPlaceSnapsOnce() {
        val felt = mutableListOf<FeedbackIntent>()
        val board = Board(todo = listOf("Timetable", "Fares", "Signage"), done = listOf("Maps", "Wi-Fi", "Bins"), felt = felt)
        board.run { scene ->
            val grab = board.bounds.getValue("Fares").center
            val drop = Offset(board.doneList.center.x, board.bounds.getValue("Wi-Fi").top + 4f)
            scene.hold(grab)
            scene.carry(grab, drop)
            val arrived = felt.count { it == FeedbackIntent.Snap }
            repeat(30) { scene.frame() }
            assertEquals(arrived, felt.count { it == FeedbackIntent.Snap }, "held still over its place, it snapped again: $felt")
            scene.release(drop)
            scene.frames(40)
        }
        assertTrue(FeedbackIntent.LongPress in felt, "a held pick-up should be felt: $felt")
        assertTrue(FeedbackIntent.GestureEnd in felt, "and the landing: $felt")
    }

    /**
     * Over a column that is full, nothing in it moves: it refuses the card, so
     * it makes no room for it, and let go there the card goes home.
     *
     * Reported as *"if a drop zone is full, then i don't want its items to
     * move around and make room if another item is dragged over the top"*.
     */
    @Test
    fun aFullColumnMakesNoRoom() {
        val board = Board(todo = listOf("Timetable", "Fares"), done = listOf("Maps", "Wi-Fi", "Bins"), limit = 3)
        board.run { scene ->
            val grab = board.bounds.getValue("Fares").center
            val over = Offset(board.doneList.center.x, board.bounds.getValue("Wi-Fi").top + 4f)
            scene.hold(grab)
            scene.carry(grab, over)
            repeat(20) { scene.frame() }

            assertEquals(DropStatus.Refused, board.done.state!!.status)
            assertEquals(null, board.done.state!!.dropIndex, "a full column should have no place for the card")
            for (card in listOf("Maps", "Wi-Fi", "Bins")) {
                val moved = board.bounds.getValue(card).top - board.restingTop.getValue(card)
                assertTrue(abs(moved) < 1f, "$card moved ${moved}px to make room in a column that will not take the card")
            }
            scene.release(over)
            scene.frames(40)
        }
        assertTrue(board.drops.isEmpty(), "a full column took a card: ${board.drops.map { it.payload }}")
        assertEquals(listOf("Timetable", "Fares"), board.todo.items.toList())
    }

    // -----------------------------------------------------------------------

    private class Column(initial: List<String>, val label: String) {
        val items: SnapshotStateList<String> = mutableStateListOf<String>().also { it.addAll(initial) }
        var state: DropListState<String>? = null
    }

    private inner class Board(
        todo: List<String>,
        done: List<String>,
        val felt: MutableList<FeedbackIntent>? = null,
        val withGutter: Boolean = false,
        /** How many cards a column holds, beyond which it takes no more. */
        val limit: Int = Int.MAX_VALUE,
    ) {
        val todo = Column(todo, "To do")
        val done = Column(done, "Done")
        val drops = mutableListOf<DropEvent<String>>()
        val bounds = mutableMapOf<String, Rect>()
        val restingTop = mutableMapOf<String, Float>()
        var doneList = Rect.Zero
        var gutterBounds = Rect.Zero

        fun move(event: DropEvent<String>, into: Column) {
            val from = listOf(todo, done).first { it.state === event.fromList }
            from.items.removeAt(event.fromIndex!!)
            into.items.add(event.index, event.payload)
        }

        fun run(drive: (Scene) -> Unit) {
            Scene(width = 640, height = 900) {
                val content: @Composable () -> Unit = {
                    OverlayHost {
                        Row(Modifier.fillMaxSize().background(Color.White)) {
                            Lane(todo, Modifier.weight(1f))
                            if (withGutter) {
                                androidx.compose.foundation.layout.Box(
                                    Modifier.weight(0.5f).fillMaxHeight().reportBounds { gutterBounds = it },
                                )
                            }
                            Lane(done, Modifier.weight(1f).reportBounds { doneList = it })
                        }
                    }
                }
                if (felt != null) {
                    CompositionLocalProvider(LocalFeedback provides FeedbackDispatcher { felt += it }, content = content)
                } else {
                    content()
                }
            }.use { scene ->
                scene.frames(6)
                restingTop.putAll(bounds.mapValues { it.value.top })
                drive(scene)
            }
        }

        @Composable
        private fun Lane(column: Column, modifier: Modifier) {
            val scroll = rememberLazyListState()
            val state = rememberDropListState(
                listState = scroll,
                type = Card,
                items = column.items,
                key = { it },
                label = column.label,
                itemLabel = { it },
                onDrop = { event ->
                    drops += event
                    move(event, column)
                },
                // Its own cards move within it, full or not.
                accepts = { it in column.items || column.items.size < limit },
            )
            column.state = state
            LazyColumn(state = scroll, modifier = modifier.fillMaxHeight().dropList(state)) {
                dragItems(state) { _, item ->
                    ListItem(modifier = Modifier.reportBounds { bounds[item] = it }) { +item }
                }
            }
        }
    }

    /** A finger held still long enough to pick up. */
    private fun Scene.hold(at: Offset) {
        press(at)
        renderUntil(timeoutMillis = 700) { false }
    }

    /** From [from] to [to] in steps, a frame each. */
    private fun Scene.carry(from: Offset, to: Offset, pointer: PointerType = PointerType.Touch) {
        repeat(Steps) { i ->
            move(from + (to - from) * ((i + 1f) / Steps), pointer)
            frame()
        }
        frames(4)
    }

    private companion object {
        val Card = DragType<String>("card")
        const val Steps = 16
    }
}
