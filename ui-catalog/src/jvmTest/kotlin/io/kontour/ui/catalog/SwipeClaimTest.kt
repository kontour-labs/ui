package io.kontour.ui.catalog

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Trash
import io.kontour.ui.components.list.SwipeAction
import io.kontour.ui.components.list.SwipeActions
import io.kontour.ui.components.list.SwipeActionsState
import io.kontour.ui.components.list.rememberSwipeActionsState
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A swipe row inside a list gets the sideways drags and gives the list the rest.
 *
 * Reported: on iOS the swipe was "way too hard to do". The row waited for a full
 * touch slop sideways while the list waited for one downwards, and whichever
 * crossed first took the gesture — so a thumb's arc, which always has some drop in
 * it, handed the drag to the list more often than not. The row now decides early,
 * from the direction of the first few pixels.
 */
class SwipeClaimTest {

    @Test
    fun aDragThirtyDegreesOffSidewaysOpensTheRowAndLeavesTheList() {
        val (offset, scrolled, _) = dragAt(degrees = 30f)
        assertTrue(offset < -40f, "a drag 30° off sideways moved the row only $offset px")
        assertEquals(0, scrolled, "the list scrolled ${scrolled}px under a drag the row took")
    }

    @Test
    fun aDragSixtyDegreesOffSidewaysScrollsTheList() {
        val (offset, scrolled, _) = dragAt(degrees = 60f)
        assertTrue(abs(offset) < 1f, "a mostly vertical drag moved the row $offset px")
        assertTrue(scrolled > 20, "a mostly vertical drag scrolled the list only ${scrolled}px")
    }

    @Test
    fun aTapStillReachesTheRow() {
        val (_, _, clicks) = dragAt(degrees = 0f, distance = 0f)
        assertEquals(1, clicks, "a tap on a swipe row did not reach the row's own click")
    }

    /**
     * Drags leftwards and down from the middle of the swipe row, [degrees] below
     * horizontal, over [distance] px, and reports the row's offset, the list's scroll
     * and how many clicks the row's content took.
     */
    private fun dragAt(degrees: Float, distance: Float = 160f): Triple<Float, Int, Int> {
        var row = Rect.Zero
        var state: SwipeActionsState? = null
        var scroll: ScrollState? = null
        var clicks = 0
        Scene(width = 700, height = 900) {
            val swipe = rememberSwipeActionsState()
            state = swipe
            val list = rememberScrollState()
            scroll = list
            Column(Modifier.fillMaxSize().background(Color.White).verticalScroll(list)) {
                Box(Modifier.fillMaxWidth().height(120.dp))
                SwipeActions(
                    end = listOf(SwipeAction("Remove", Tabler.Outline.Trash, {}, Color.Red)),
                    state = swipe,
                    modifier = Modifier.reportBounds { row = it },
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .background(Color.LightGray)
                            .clickable { clicks++ }
                    )
                }
                repeat(30) { Box(Modifier.fillMaxWidth().height(64.dp)) }
            }
        }.use { scene ->
            scene.frames(4)
            val from = Offset(row.center.x + 100f, row.center.y)
            if (distance == 0f) {
                scene.press(from)
                scene.release(from)
            } else {
                val radians = Math.toRadians(degrees.toDouble())
                val to = Offset(
                    from.x - (distance * cos(radians)).toFloat(),
                    from.y - (distance * sin(radians)).toFloat(),
                )
                scene.drag(from, to, steps = 20, release = false)
                scene.frames(2)
            }
            scene.frames(4)
        }
        return Triple(requireNotNull(state).offset.let { if (it.isNaN()) 0f else it }, requireNotNull(scroll).value, clicks)
    }
}
