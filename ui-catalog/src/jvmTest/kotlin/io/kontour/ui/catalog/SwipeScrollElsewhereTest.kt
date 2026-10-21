package io.kontour.ui.catalog

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.dp
import io.kontour.icons.tabler.Tabler
import io.kontour.icons.tabler.outline.Archive
import io.kontour.icons.tabler.outline.Trash
import io.kontour.ui.components.list.SwipeAction
import io.kontour.ui.components.list.SwipeActions
import io.kontour.ui.components.list.SwipeActionsState
import io.kontour.ui.components.list.SwipeValue
import io.kontour.ui.components.list.rememberSwipeActionsState
import io.kontour.ui.overlay.OverlayHost
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A row left showing its actions closes when something else starts to scroll,
 * as it does at a press: asked for as *"can we apply that too if you start
 * side-scrolling somewhere else?"*. A finger's scroll starts with a press and
 * always closed it; a wheel's and a trackpad's have no press, and the row
 * stayed open while a carousel or a table beside it was scrolled across.
 *
 * Each scene is a swipe row, open on its end actions, above a strip that
 * scrolls sideways, in a list that scrolls down, inside an `OverlayHost`.
 */
class SwipeScrollElsewhereTest {

    @Test
    fun aTouchDragAcrossAStripElsewhereClosesIt() = scene { scene, s ->
        scene.drag(s.strip.alongX(0.8f), s.strip.alongX(0.2f), steps = 12)
        scene.frames(30)
        assertTrue(s.across.value > 0, "the drag did not scroll the strip, so this measured nothing")
        assertEquals(SwipeValue.Resting, s.row.currentValue, "the row stayed open while a strip beside it was dragged across")
    }

    @Test
    fun aSidewaysWheelOverAStripElsewhereClosesIt() = scene { scene, s ->
        repeat(3) {
            scene.scroll(s.strip.center, Offset(1f, 0f))
            scene.frame()
        }
        scene.frames(30)
        assertTrue(s.across.value > 0, "the wheel did not scroll the strip, so this measured nothing")
        assertEquals(SwipeValue.Resting, s.row.currentValue, "the row stayed open while a strip beside it was scrolled across by a wheel")
    }

    @Test
    fun aTrackpadPanOverAStripElsewhereClosesIt() = scene { scene, s ->
        scene.pan(PointerEventType.PanStart, s.strip.center)
        repeat(6) {
            scene.pan(PointerEventType.PanMove, s.strip.center, Offset(-24f, 0f))
            scene.frame()
        }
        scene.pan(PointerEventType.PanEnd, s.strip.center)
        scene.frames(30)
        assertEquals(SwipeValue.Resting, s.row.currentValue, "the row stayed open while a trackpad pushed a strip beside it across")
    }

    /**
     * The list scrolled closes it too, from a wheel as from a finger: the page
     * has always said so, and only a finger's scroll, which starts with a
     * press, did it.
     */
    @Test
    fun aVerticalWheelOverTheListElsewhereClosesIt() = scene { scene, s ->
        repeat(3) {
            scene.scroll(s.below.center, Offset(0f, 1f))
            scene.frame()
        }
        scene.frames(30)
        assertTrue(s.down.value > 0, "the wheel did not scroll the list, so this measured nothing")
        assertEquals(SwipeValue.Resting, s.row.currentValue, "the row stayed open while the list was scrolled by a wheel")
    }

    /** A scroll on the open row is the row's: one more notch out, and it settles open again. */
    @Test
    fun aWheelOnTheOpenRowItselfStillMovesIt() = scene { scene, s ->
        scene.scroll(s.swipe.center, Offset(1f, 0f))
        scene.frame()
        val moved = s.row.offset
        Thread.sleep(300)
        scene.frames(40)
        assertTrue(moved < s.open - 1f, "a notch on the open row did not carry it further out than ${s.open}: $moved")
        assertEquals(SwipeValue.End, s.row.currentValue, "a scroll on the open row itself closed it")
    }

    @Test
    fun aTrackpadPanOnTheOpenRowItselfStillMovesIt() = scene { scene, s ->
        scene.pan(PointerEventType.PanStart, s.swipe.center)
        repeat(4) {
            scene.pan(PointerEventType.PanMove, s.swipe.center, Offset(-6f, 0f))
            scene.frame()
        }
        val moved = s.row.offset
        scene.pan(PointerEventType.PanEnd, s.swipe.center)
        scene.frames(40)
        assertTrue(moved < s.open - 1f, "a pan on the open row did not carry it further out than ${s.open}: $moved")
        assertEquals(SwipeValue.End, s.row.currentValue, "a pan on the open row itself closed it")
    }

    /** And a finger on it still closes it by dragging it home. */
    @Test
    fun aTouchDragOnTheOpenRowItselfStillClosesItByHand() = scene { scene, s ->
        val home = s.swipe.alongX(0.3f) + Offset(-s.open, 0f)
        scene.drag(s.swipe.alongX(0.3f), home, steps = 10, release = false)
        val held = s.row.offset
        scene.release(home)
        scene.frames(40)
        assertTrue(held > s.open / 2f, "the finger did not carry the open row home with it from ${s.open}: $held")
        assertEquals(SwipeValue.Resting, s.row.currentValue)
    }

    /** With no host round the app, a scroll on another swipe row still closes it, as a press there does. */
    @Test
    fun withoutAHostASidewaysWheelOnAnotherRowStillClosesIt() {
        lateinit var first: SwipeActionsState
        lateinit var second: SwipeActionsState
        var bounds = Rect.Zero
        Scene(width = 700, height = 400, reduceMotion = true) {
            first = rememberSwipeActionsState(SwipeValue.End)
            second = rememberSwipeActionsState()
            Column(Modifier.fillMaxSize().background(Color.White)) {
                SwipeRow(first)
                SwipeRow(second, Modifier.reportBounds { bounds = it })
            }
        }.use { scene ->
            scene.frames(3)
            repeat(4) {
                scene.scroll(bounds.center, Offset(1f, 0f))
                scene.frame()
            }
            Thread.sleep(300)
            scene.frames(40)
            assertEquals(SwipeValue.End, second.currentValue, "the second row did not open, so this measured nothing")
            assertEquals(SwipeValue.Resting, first.currentValue, "the first row stayed open while the second was scrolled open")
        }
    }

    private class Parts {
        lateinit var row: SwipeActionsState
        lateinit var across: ScrollState
        lateinit var down: ScrollState
        /** Where the open row rests, in pixels: negative, for its end actions. */
        var open = 0f
        var swipe = Rect.Zero
        var strip = Rect.Zero
        var below = Rect.Zero
    }

    /**
     * The scene: the swipe row open on its two end actions, a strip nearly
     * three times the scene's width that scrolls sideways, and a tall box
     * below, in a column that scrolls down.
     */
    private fun scene(test: (Scene, Parts) -> Unit) {
        val parts = Parts()
        Scene(width = 700, height = 500, reduceMotion = true) {
            OverlayHost(Modifier.fillMaxSize()) {
                parts.row = rememberSwipeActionsState(SwipeValue.End)
                parts.across = rememberScrollState()
                parts.down = rememberScrollState()
                Column(Modifier.fillMaxSize().background(Color.White).verticalScroll(parts.down)) {
                    SwipeRow(parts.row, Modifier.reportBounds { parts.swipe = it })
                    Row(Modifier.fillMaxWidth().height(80.dp).reportBounds { parts.strip = it }.horizontalScroll(parts.across)) {
                        repeat(12) { i ->
                            Box(Modifier.width(80.dp).height(80.dp).background(if (i % 2 == 0) Color.LightGray else Color.Gray))
                        }
                    }
                    Box(Modifier.fillMaxWidth().height(1500.dp).reportBounds { parts.below = Rect(it.left, it.top, it.right, it.top + 200f) })
                }
            }
        }.use { scene ->
            scene.frames(3)
            assertEquals(SwipeValue.End, parts.row.currentValue, "the row did not start open")
            parts.open = parts.row.offset
            test(scene, parts)
        }
    }
}

@androidx.compose.runtime.Composable
private fun SwipeRow(state: SwipeActionsState, modifier: Modifier = Modifier) {
    SwipeActions(
        state = state,
        modifier = modifier.fillMaxWidth().height(72.dp),
        end = listOf(
            SwipeAction("Archive", Tabler.Outline.Archive, {}, Color.Blue),
            SwipeAction("Delete", Tabler.Outline.Trash, {}, Color.Red),
        ),
    ) {
        Box(Modifier.fillMaxWidth().height(72.dp).background(Color.White))
    }
}
