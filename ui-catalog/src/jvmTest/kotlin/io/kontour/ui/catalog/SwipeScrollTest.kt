package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Archive
import com.composables.icons.tabler.outline.Trash
import io.kontour.ui.components.list.SwipeAction
import io.kontour.ui.components.list.SwipeActions
import io.kontour.ui.components.list.SwipeActionsState
import io.kontour.ui.components.list.SwipeValue
import io.kontour.ui.components.list.rememberSwipeActionsState
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A row's actions from a trackpad and a mouse, with no finger to drag them.
 *
 * Reported as wanting it "more sensitive to side scrolling" on both — touch was
 * fine. Two causes. A scroll's delta is in notches and was read as pixels, so a
 * mouse's click moved the row three pixels of an 88dp action. And a Mac's
 * trackpad arrives as a *pan*, which the row did not listen for at all.
 *
 * Two 88dp actions at this scene's density of 2 reveal at 352px, and settle open
 * once let go of past half of that.
 */
class SwipeScrollTest {

    @Test
    fun aFewNotchesOfSidewaysScrollOpenTheActions() {
        val (state, settled) = row { scene, at, _ ->
            repeat(3) {
                scene.scroll(at, Offset(1f, 0f))
                scene.frame()
            }
        }
        assertTrue(abs(state.offset) > 0f || settled == SwipeValue.End, "three notches did not move the row")
        assertEquals(
            SwipeValue.End, settled,
            "three clicks of a sideways wheel left the row at $settled. A notch is a " +
                "unit, not a pixel; at three pixels a notch this took dozens of clicks.",
        )
    }

    @Test
    fun aMostlyVerticalScrollIsTheListsAndLeavesTheRowAlone() {
        val (state, settled) = row { scene, at, _ ->
            repeat(6) {
                scene.scroll(at, Offset(0.2f, 1f))
                scene.frame()
            }
        }
        assertEquals(0f, state.offset, "a vertical scroll with a little drift moved the row")
        assertEquals(SwipeValue.Resting, settled)
    }

    @Test
    fun aTrackpadPanMovesTheRowWithTheFingersAndSettlesOnItsEnd() {
        var during = 0f
        val (_, settled) = row { scene, at, state ->
            scene.pan(PointerEventType.PanStart, at)
            repeat(10) {
                scene.pan(PointerEventType.PanMove, at, Offset(-24f, 0f))
                scene.frame()
            }
            during = state.offset
            scene.pan(PointerEventType.PanEnd, at)
        }
        assertTrue(
            abs(during - -240f) < 2f,
            "ten pans of 24px left the row at $during, where the fingers had moved it 240",
        )
        assertEquals(SwipeValue.End, settled, "let go past half the actions, a pan left the row at $settled")
    }

    @Test
    fun aMostlyVerticalPanIsTheLists() {
        val (state, settled) = row { scene, at, _ ->
            scene.pan(PointerEventType.PanStart, at)
            repeat(10) {
                scene.pan(PointerEventType.PanMove, at, Offset(-3f, -20f))
                scene.frame()
            }
            scene.pan(PointerEventType.PanEnd, at)
        }
        assertEquals(0f, state.offset, "a vertical pan moved the row")
        assertEquals(SwipeValue.Resting, settled)
    }

    /**
     * A row with two end actions; runs [gesture] on its middle, waits out the
     * settle, and returns the state and where it came to rest.
     */
    private fun row(gesture: (Scene, Offset, SwipeActionsState) -> Unit): Pair<SwipeActionsState, SwipeValue> {
        lateinit var state: SwipeActionsState
        var bounds = Rect.Zero
        Scene(width = 700, height = 200) {
            state = rememberSwipeActionsState()
            Box(Modifier.fillMaxSize().background(Color.White)) {
                SwipeActions(
                    state = state,
                    modifier = Modifier.fillMaxWidth().height(72.dp).reportBounds { bounds = it },
                    end = listOf(
                        SwipeAction("Archive", Tabler.Outline.Archive, {}, Color.Blue),
                        SwipeAction("Delete", Tabler.Outline.Trash, {}, Color.Red),
                    ),
                ) {
                    Box(Modifier.fillMaxWidth().height(72.dp).background(Color.White))
                }
            }
        }.use { scene ->
            scene.frames(3)
            gesture(scene, bounds.center, state)
            // A scroll settles on a wall-clock timer; a pan on its end.
            Thread.sleep(300)
            scene.frames(40)
        }
        return state to state.currentValue
    }
}
