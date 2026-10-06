package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import io.kontour.ui.components.list.ListItem
import io.kontour.ui.dragdrop.DragType
import io.kontour.ui.dragdrop.DropEvent
import io.kontour.ui.dragdrop.DropListState
import io.kontour.ui.dragdrop.dragItems
import io.kontour.ui.dragdrop.dropList
import io.kontour.ui.dragdrop.rememberDropListState
import io.kontour.ui.overlay.OverlayHost
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A card held near the end of a list scrolls the list, so it can be carried to
 * a place that was not on the screen when it was picked up.
 */
class DragAutoScrollTest {

    @Test
    fun heldAtTheBottomOfAListTheListScrollsAndTheCardLandsBeyondIt() {
        val lanes = Lanes(short = 2, long = 30)
        lanes.run { scene ->
            val grab = lanes.bounds.getValue("A0").center
            // Inside the long list's bottom edge, well clear of its last row.
            val edge = Offset(lanes.long.box.center.x, lanes.long.box.bottom - 20f)
            scene.press(grab)
            scene.renderUntil(timeoutMillis = 700) { false }
            scene.slide(grab, edge)
            val before = lanes.long.scroll!!.firstVisibleItemIndex
            scene.frames(90)
            val after = lanes.long.scroll!!.firstVisibleItemIndex
            assertTrue(after > before + 3, "held at its edge for 90 frames, the list went from row $before to row $after")
            scene.release(edge)
            scene.frames(40)
        }
        val landed = lanes.drops.single()
        assertTrue(landed.index > 10, "the card should land well past the rows that were on screen, at ${landed.index}")
        assertEquals("A0", lanes.long.items[landed.index])
    }

    /**
     * A card picked up beside an edge is not asking to be carried past it. The
     * list waits until the card has been somewhere else first.
     */
    @Test
    fun aCardPickedUpAtAnEdgeDoesNotScrollItsListUntilItHasLeftIt() {
        val lanes = Lanes(short = 2, long = 30)
        lanes.run { scene ->
            val bottom = lanes.long.box.bottom
            // The last row whose middle is inside the bottom edge.
            val row = lanes.bounds.filterKeys { it.startsWith("B") }.values
                .filter { it.center.y < bottom && it.center.y > bottom - 110f }
                .maxBy { it.center.y }
            val grab = row.center
            scene.press(grab)
            scene.renderUntil(timeoutMillis = 700) { false }
            scene.move(grab + Offset(0f, 2f))
            scene.frames(60)
            assertEquals(0, lanes.long.scroll!!.firstVisibleItemIndex, "picked up at the edge and held there, the list should stay put")

            // Up out of the edge, and back into it: now it is asked.
            val away = grab - Offset(0f, 300f)
            scene.slide(grab, away)
            scene.slide(away, Offset(grab.x, bottom - 20f))
            scene.frames(60)
            assertTrue(lanes.long.scroll!!.firstVisibleItemIndex > 0, "back in the edge after leaving it, the list should scroll")
            scene.release(Offset(grab.x, bottom - 20f))
            scene.frames(40)
        }
    }

    // -----------------------------------------------------------------------

    private class Lane(val prefix: String, count: Int) {
        val items: SnapshotStateList<String> = mutableStateListOf<String>().also { list ->
            repeat(count) { list += "$prefix$it" }
        }
        var state: DropListState<String>? = null
        var scroll: LazyListState? = null
        var box = Rect.Zero
    }

    private inner class Lanes(short: Int, long: Int) {
        val short = Lane("A", short)
        val long = Lane("B", long)
        val drops = mutableListOf<DropEvent<String>>()
        val bounds = mutableMapOf<String, Rect>()

        fun run(drive: (Scene) -> Unit) {
            Scene(width = 640, height = 900) {
                OverlayHost {
                    Row(Modifier.fillMaxSize().background(Color.White)) {
                        Column(short, Modifier.weight(1f))
                        Column(long, Modifier.weight(1f))
                    }
                }
            }.use { scene ->
                scene.frames(6)
                drive(scene)
            }
        }

        @Composable
        private fun Column(lane: Lane, modifier: Modifier) {
            val scroll = rememberLazyListState()
            lane.scroll = scroll
            val state = rememberDropListState(
                listState = scroll,
                type = Card,
                items = lane.items,
                key = { it },
                label = lane.prefix,
                itemLabel = { it },
                onDrop = { event ->
                    drops += event
                    val from = listOf(short, long).first { it.state === event.fromList }
                    from.items.removeAt(event.fromIndex!!)
                    lane.items.add(event.index, event.payload)
                },
            )
            lane.state = state
            LazyColumn(
                state = scroll,
                modifier = modifier.fillMaxHeight().dropList(state).reportBounds { lane.box = it },
            ) {
                dragItems(state) { _, item ->
                    ListItem(modifier = Modifier.reportBounds { bounds[item] = it }) { +item }
                }
            }
        }
    }

    private fun Scene.slide(from: Offset, to: Offset) {
        repeat(12) { i ->
            move(from + (to - from) * ((i + 1f) / 12f))
            frame()
        }
    }

    private companion object {
        val Card = DragType<String>("card")
    }
}
