package io.kontour.ui.catalog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.unit.dp
import io.kontour.icons.tabler.Tabler
import io.kontour.icons.tabler.outline.GripVertical
import io.kontour.ui.components.action.Button
import io.kontour.ui.components.list.ListItem
import io.kontour.ui.dragdrop.DragType
import io.kontour.ui.dragdrop.dragItems
import io.kontour.ui.dragdrop.dragSource
import io.kontour.ui.dragdrop.dropList
import io.kontour.ui.dragdrop.dropTarget
import io.kontour.ui.dragdrop.rememberDropListState
import io.kontour.ui.dragdrop.rememberDropTargetState
import io.kontour.ui.foundation.Icon
import io.kontour.ui.foundation.SelectableText
import io.kontour.ui.foundation.Text
import io.kontour.ui.overlay.OverlayHost
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A mouse picks a thing up inside a selection region, starting as a hand does:
 * a pixel or two at a time.
 *
 * Reported for 0.5.6 from the documentation, whose pages are one
 * `SelectableText` with the demos under `DisableSelection`: *"i can't pick items
 * up on desktop web at all"*. A `SelectionContainer` selects between texts with
 * a mouse over the whole of itself, `DisableSelection` or not, and it consumed
 * each small movement after the pick-up had seen it — and the pick-up's slop wait
 * gives up on a movement something else consumed. Every test drove a first step
 * past the slop in one move, which is taken before a selection sees it, so the
 * suite was green while a mouse could not lift a card. Logged from Chrome on a
 * Mac, the first three moves of a real drag were 1, 1 and 4 pixels.
 */
class DragInSelectionTest {

    private val Type = DragType<String>("selection-test")

    /** The documentation's arrangement: a selection region, a list in it, the demo out of the selection. */
    @Composable
    private fun Page(content: @Composable () -> Unit) {
        OverlayHost {
            SelectableText(Modifier.fillMaxSize()) {
                LazyColumn(Modifier.fillMaxSize()) {
                    item { Text("Anything can be carried, and anything can take it.") }
                    item { DisableSelection { Box(Modifier.height(480.dp)) { content() } } }
                    item { Text("A press held on a card picks it up, or a mouse's drag.") }
                }
            }
        }
    }

    private class Board(val todo: SnapshotStateList<String>, val done: SnapshotStateList<String>) {
        val grips = mutableMapOf<String, Rect>()
        val rows = mutableMapOf<String, Rect>()
        var doneList = Rect.Zero
    }

    @Composable
    private fun Lanes(board: Board, handles: Boolean) = Row(Modifier.fillMaxSize()) {
        for ((name, list) in listOf("To do" to board.todo, "Doing" to board.done)) {
            val scroll = rememberLazyListState()
            val lane = rememberDropListState(
                listState = scroll,
                type = Type,
                items = list,
                key = { it },
                label = name,
                itemLabel = { it },
                onDrop = { drop ->
                    (if (drop.payload in board.todo) board.todo else board.done).remove(drop.payload)
                    list.add(drop.index.coerceAtMost(list.size), drop.payload)
                },
            )
            LazyColumn(
                state = scroll,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .dropList(lane)
                    .then(if (name == "Doing") Modifier.reportBounds { board.doneList = it } else Modifier),
            ) {
                dragItems(lane) { _, task ->
                    ListItem(Modifier.reportBounds { board.rows[task] = it }) {
                        +task
                        if (handles) {
                            trailing {
                                Icon(Tabler.Outline.GripVertical, null, Modifier.dragHandle().reportBounds { board.grips[task] = it })
                            }
                        }
                    }
                }
            }
        }
    }

    /** Pressed at [from], a pixel or two at a time to start with, then on to [to] and let go. */
    private fun Scene.handDrag(from: Offset, to: () -> Offset) {
        move(from, PointerType.Mouse)
        frames(2)
        press(from, PointerType.Mouse)
        frame()
        var at = from
        for (step in listOf(Offset(1f, 0f), Offset(1f, 0f), Offset(3f, 2f), Offset(2f, 2f))) {
            at += step
            move(at, PointerType.Mouse)
            frame()
        }
        val end = to()
        repeat(16) { i ->
            val t = (i + 1) / 16f
            move(Offset(at.x + (end.x - at.x) * t, at.y + (end.y - at.y) * t), PointerType.Mouse)
            frame()
        }
        release(end, PointerType.Mouse)
        frames(30)
    }

    private fun boardDrag(handles: Boolean) {
        val board = Board(mutableStateListOf("Fares", "Stops", "Signage"), mutableStateListOf("Maps"))
        Scene(width = 800, height = 600, density = 2f) { Page { Lanes(board, handles) } }.use { scene ->
            scene.frames(10)
            val from = if (handles) board.grips.getValue("Fares").center else board.rows.getValue("Fares").center
            scene.handDrag(from) { board.doneList.center }
            assertEquals(listOf("Stops", "Signage"), board.todo.toList(), "handles = $handles: Fares never left To do")
            assertEquals(listOf("Maps", "Fares"), board.done.toList(), "handles = $handles: Fares did not land in Doing")
        }
    }

    @Test
    fun aGripPicksUpInASelectionRegion() = boardDrag(handles = true)

    @Test
    fun aWholeRowPicksUpInASelectionRegion() = boardDrag(handles = false)

    @Test
    fun aDragSourcePicksUpInASelectionRegion() {
        val binned = mutableListOf<String>()
        var card = Rect.Zero
        var bin = Rect.Zero
        Scene(width = 800, height = 600, density = 2f) {
            Page {
                Column {
                    Text("Fares", Modifier.size(200.dp, 60.dp).dragSource(Type, "Fares", label = "Fares").reportBounds { card = it })
                    val target = rememberDropTargetState(Type, label = "Bin", onDrop = { binned += it })
                    Box(Modifier.padding(top = 120.dp).size(200.dp, 80.dp).dropTarget(target).reportBounds { bin = it })
                }
            }
        }.use { scene ->
            scene.frames(10)
            scene.handDrag(card.center) { bin.center }
            assertEquals(listOf("Fares"), binned, "the card was never carried to the bin")
        }
    }

    /**
     * The control: the press is the source's for what is *outside* it, a
     * selection, and not for what is inside it. A button on a draggable card
     * still clicks.
     */
    @Test
    fun aButtonOnADraggableCardStillClicks() {
        var clicks = 0
        var button = Rect.Zero
        Scene(width = 800, height = 600, density = 2f) {
            Page {
                Box(Modifier.size(300.dp, 120.dp).dragSource(Type, "Fares", label = "Fares")) {
                    Button(onClick = { clicks++ }, modifier = Modifier.reportBounds { button = it }) { +"Pay" }
                }
            }
        }.use { scene ->
            scene.frames(10)
            scene.move(button.center, PointerType.Mouse)
            scene.frames(2)
            scene.press(button.center, PointerType.Mouse)
            scene.frame()
            scene.release(button.center, PointerType.Mouse)
            scene.frames(10)
            assertEquals(1, clicks, "a click on a button inside a drag source went nowhere")
        }
    }
}
