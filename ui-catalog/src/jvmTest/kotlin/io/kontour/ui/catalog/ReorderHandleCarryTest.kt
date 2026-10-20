package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import io.kontour.ui.components.list.ListItem
import io.kontour.ui.components.list.ReorderableItem
import io.kontour.ui.components.list.rememberReorderableState
import io.kontour.icons.tabler.Tabler
import io.kontour.icons.tabler.outline.GripVertical
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A row carried by its grip stays carried for as long as the finger is down,
 * however slowly it goes.
 *
 * The row under the grip waits out its own half-second hold on every touch,
 * the grip's included. The row moves with the finger, so the finger never
 * left the hold's budget in the row's own terms: half a second into a slow
 * carry the hold came true, took the moves before the grip could, and the grip
 * read that as something else taking its pointer and put the row down.
 *
 * Reported as *"with drag handles in the reorderable item, it just drops it
 * sometimes after like 1 or 2 positions. holding and dragging works perfectly
 * though"*.
 */
class ReorderHandleCarryTest {

    @Test
    fun aSlowCarryByTheGripGoesTheWholeWay() = carry(paceMillis = PaceMillis)

    /** The same carry at a finger's speed, which never reached the hold. */
    @Test
    fun aQuickCarryByTheGripGoesTheWholeWay() = carry(paceMillis = 0L)

    private fun carry(paceMillis: Long) {
        val rows = mutableStateListOf(
            "Perth", "Daglish", "Subiaco", "West Leederville", "Leederville", "Glendalough", "Stirling",
        )
        var bounds = Rect.Zero

        Scene(width = 500, height = 600) {
            val listState = rememberLazyListState()
            val reorder = rememberReorderableState(listState) { from, to ->
                rows.add(to, rows.removeAt(from))
            }
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize().background(Color.White)) {
                itemsIndexed(rows, key = { _, name -> name }) { i, name ->
                    ReorderableItem(
                        state = reorder,
                        index = i,
                        itemCount = rows.size,
                        modifier = if (name == "Daglish") Modifier.reportBounds { bounds = it } else Modifier,
                        handleIcon = Tabler.Outline.GripVertical,
                    ) {
                        ListItem { +name }
                    }
                }
            }
        }.use { scene ->
            scene.frames(4)
            // The grip is at the row's end, inside its last touch target.
            val grab = Offset(bounds.right - 24f, bounds.center.y)
            // Three rows and a bit: the last crossing is at the next row's middle.
            val travel = bounds.height * 3.4f
            scene.press(grab)
            scene.frame()
            repeat(Steps) { i ->
                scene.move(grab + Offset(0f, travel * (i + 1) / Steps))
                scene.frame()
                // Slower than the hold, in real time: the long press is a
                // `delay`, which this harness runs on the wall clock.
                Thread.sleep(paceMillis)
            }
            scene.release(grab + Offset(0f, travel))
            scene.frames(10)
        }

        assertEquals(
            "Daglish", rows[4],
            "carried three rows down by its grip over ${Steps * paceMillis}ms, it should have gone the whole " +
                "way and not been put down on the way: the list is $rows",
        )
    }

    private companion object {
        const val Steps = 24
        const val PaceMillis = 60L
    }
}
