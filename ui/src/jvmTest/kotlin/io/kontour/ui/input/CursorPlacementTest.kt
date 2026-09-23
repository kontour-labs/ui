package io.kontour.ui.input

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import io.kontour.ui.adaptive.ListDetailPaneScaffold
import io.kontour.ui.adaptive.PaneFocus
import io.kontour.ui.components.action.Button
import io.kontour.ui.components.display.Card
import io.kontour.ui.components.list.ReorderableItem
import io.kontour.ui.components.list.Scrollbar
import io.kontour.ui.components.list.rememberReorderableState
import io.kontour.ui.foundation.SystemIcons
import io.kontour.ui.foundation.Text
import io.kontour.ui.sheet.DragHandle
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * What the pointer turns into over each of the library's drag handles.
 *
 * Every one of these showed a hand, because a hand was the only shape the
 * library could name. A hand says *click me*, and none of them does anything
 * when clicked: a splitter, a scrollbar, a reorder grip and a sheet's grip are
 * all things you *drag*. Each now asks for the shape that says so, and on a
 * platform without that shape it degrades to the nearest one it has.
 *
 * Hovered for real — a mouse moved over the element in a scene that keeps the
 * cursor it is asked for — rather than read off the modifier, because the
 * question is what the reader sees, and that is decided by hit-testing: which
 * node is on top, and whether a parent's icon overrides its children's.
 */
class CursorPlacementTest {

    private fun expected(cursor: Cursor): PointerIcon = pointerIconFor(cursor)

    @Test
    fun aPaneSplitterIsDraggedSideways() {
        var list = Rect.Zero
        CursorScene(width = 1800, height = 800) {
            ListDetailPaneScaffold(
                focus = PaneFocus.List,
                onBack = {},
                list = { Box(Modifier.fillMaxSize().onGloballyPositioned { list = it.boundsInRoot() }) },
                detail = { Box(Modifier.fillMaxSize()) },
                twoPane = true,
                resizable = true,
            )
        }.use { scene ->
            // The handle is the strip between the panes: a few pixels past the
            // list's own right edge.
            val handle = Offset(list.right + 8f, list.center.y)
            scene.hover(handle)
            assertEquals(expected(Cursor.ResizeColumn), scene.cursor, "over the pane splitter")

            // Held and moved, the splitter stays under the pointer — Compose's
            // drag detector uses a mouse's own slop, a fraction of a dp — so the
            // cursor is still the splitter's, and not the pane's beside it.
            val dragged = handle + Offset(160f, 0f)
            scene.press(handle)
            scene.drag(handle, dragged)
            assertEquals(expected(Cursor.ResizeColumn), scene.cursor, "while the splitter is dragged")

            scene.release(dragged)
            scene.hover(dragged + Offset(200f, 0f))
            assertEquals(expected(Cursor.Default), scene.cursor, "over a pane once it is let go")
        }
    }

    @Test
    fun aSheetsGripIsDraggedUpAndDown() {
        var grip = Rect.Zero
        CursorScene(width = 800, height = 400) {
            DragHandle(
                modifier = Modifier.onGloballyPositioned { grip = it.boundsInRoot() },
                state = null,
            )
        }.use { scene ->
            scene.hover(grip.center)
            assertEquals(expected(Cursor.ResizeRow), scene.cursor, "over a sheet's grip")
        }
    }

    @Test
    fun aScrollbarIsGrabbedAndThenHeld() {
        var bar = Rect.Zero
        CursorScene(width = 600, height = 600) {
            val list = rememberLazyListState()
            Box(Modifier.fillMaxSize()) {
                LazyColumn(Modifier.fillMaxSize(), state = list) {
                    items(200) { Text("Row $it", Modifier.padding(8.dp)) }
                }
                Scrollbar(
                    state = list,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .onGloballyPositioned { bar = it.boundsInRoot() },
                )
            }
        }.use { scene ->
            val top = Offset(bar.center.x, bar.top + 20f)
            scene.hover(top)
            assertEquals(expected(Cursor.Grab), scene.cursor, "over a scrollbar at rest")

            scene.press(top)
            scene.drag(top, top + Offset(0f, 200f))
            assertEquals(expected(Cursor.Grabbing), scene.cursor, "while the scrollbar is held")

            scene.release(top + Offset(0f, 200f))
            scene.hover(top + Offset(0f, 201f))
            assertEquals(expected(Cursor.Grab), scene.cursor, "once it is let go")
        }
    }

    @Test
    fun aReorderGripIsGrabbedAndThenHeld() {
        var row = Rect.Zero
        var content = Rect.Zero
        CursorScene(width = 800, height = 600) {
            val list = rememberLazyListState()
            val reorder = rememberReorderableState(list) { _, _ -> }
            LazyColumn(Modifier.fillMaxSize(), state = list) {
                items(3) { index ->
                    ReorderableItem(
                        state = reorder,
                        index = index,
                        modifier = if (index == 0) {
                            Modifier.onGloballyPositioned { row = it.boundsInRoot() }
                        } else {
                            Modifier
                        },
                        itemCount = 3,
                        handleIcon = SystemIcons.More,
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .then(
                                    if (index == 0) {
                                        Modifier.onGloballyPositioned { content = it.boundsInRoot() }
                                    } else {
                                        Modifier
                                    }
                                )
                        )
                    }
                }
            }
        }.use { scene ->
            // The grip is what is left of the row past its content.
            val grip = Offset((content.right + row.right) / 2f, row.center.y)
            scene.hover(grip)
            assertEquals(expected(Cursor.Grab), scene.cursor, "over a reorder grip")

            // Picked up, then moved sideways off the grip and onto the row's
            // content. The row follows the pointer up and down, not across, so
            // a wander sideways leaves the 24dp grip behind. The cursor is the
            // drag's, and it stays grabbing wherever on the held row the pointer
            // is.
            val onContent = Offset(content.center.x, grip.y + 20f)
            scene.press(grip)
            scene.drag(grip, grip + Offset(0f, 20f))
            scene.drag(grip + Offset(0f, 20f), onContent)
            assertEquals(expected(Cursor.Grabbing), scene.cursor, "anywhere on a held row")

            scene.release(onContent)
            scene.hover(onContent + Offset(1f, 0f))
            assertEquals(expected(Cursor.Default), scene.cursor, "on the row's content once it is dropped")
        }
    }

    /**
     * The same promise [DisabledCursorTest] checks on the modifier, kept by a real
     * component inside a real card — which is where it was broken: the arrow is
     * only worth anything if every control passes its `enabled` through.
     */
    @Test
    fun aDisabledButtonInAClickableCardShowsTheArrow() {
        var button = Rect.Zero
        CursorScene(width = 800, height = 400) {
            Card(onClick = {}) {
                Column(Modifier.padding(24.dp)) {
                    Row {
                        Button(
                            onClick = {},
                            modifier = Modifier.onGloballyPositioned { button = it.boundsInRoot() },
                            enabled = false,
                        ) { Text("Save") }
                    }
                }
            }
        }.use { scene ->
            scene.hover(button.center)
            assertEquals(expected(Cursor.Default), scene.cursor, "over a disabled button in a card")
        }
    }
}
