package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import io.kontour.ui.components.list.ListItem
import io.kontour.ui.dragdrop.DragType
import io.kontour.ui.dragdrop.DropStatus
import io.kontour.ui.dragdrop.DropTargetState
import io.kontour.ui.dragdrop.dragSource
import io.kontour.ui.dragdrop.dropTarget
import io.kontour.ui.dragdrop.rememberDropTargetState
import io.kontour.ui.overlay.OverlayHost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A thing carried out of a lazy list is still carried when its row has
 * scrolled away.
 *
 * The row is what holds the pointer: Compose sends a held pointer's events to
 * the nodes it went down on, and a lazy list disposes a row once it is off the
 * screen. Disposed, the gesture would end where the finger still is. The source
 * is pinned for as long as its thing is carried, so it is not.
 */
class DragSurvivesSourceTest {

    @Test
    fun aCarriedThingOutlivesItsRowScrollingAway() {
        val dropped = mutableListOf<String>()
        var bin = Rect.Zero
        var first = Rect.Zero
        lateinit var list: LazyListState
        lateinit var scope: CoroutineScope
        lateinit var target: DropTargetState<String>

        Scene(width = 600, height = 800) {
            OverlayHost {
                Row(Modifier.fillMaxSize().background(Color.White)) {
                    list = rememberLazyListState()
                    scope = rememberCoroutineScope()
                    LazyColumn(state = list, modifier = Modifier.weight(1f).fillMaxHeight()) {
                        items((0 until 40).map { "Stop $it" }, key = { it }) { stop ->
                            ListItem(
                                modifier = Modifier
                                    .dragSource(Stop, stop, label = stop)
                                    .then(if (stop == "Stop 0") Modifier.reportBounds { first = it } else Modifier),
                            ) { +stop }
                        }
                    }
                    target = rememberDropTargetState(Stop, label = "Bin", onDrop = { dropped += it })
                    Box(Modifier.weight(1f).fillMaxHeight().dropTarget(target).reportBounds { bin = it })
                }
            }
        }.use { scene ->
            scene.frames(6)
            val grab = first.center
            scene.press(grab)
            scene.renderUntil(timeoutMillis = 700) { false }
            scene.move(grab + Offset(0f, 12f))
            scene.frame()

            // The list goes thirty rows down under the finger.
            scope.launch { list.scrollToItem(30) }
            scene.frames(6)
            assertTrue(list.firstVisibleItemIndex >= 30, "the list should have scrolled away from the first row")

            val to = bin.center
            repeat(12) { i ->
                scene.move(grab + (to - grab) * ((i + 1f) / 12f))
                scene.frame()
            }
            assertEquals(DropStatus.Over, target.status, "the bin should know the stop is over it")
            scene.release(to)
            scene.frames(40)
        }
        assertEquals(listOf("Stop 0"), dropped, "the drag should have survived its row going off the screen")
    }

    private companion object {
        val Stop = DragType<String>("stop")
    }
}
