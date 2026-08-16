package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.kontour.ui.nav.tabSwipe
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Swiping the pane changes the tab.
 *
 * The one navigation control where the gesture and the indicator live in
 * different places: nobody swipes the bar, they swipe the thing the bar is
 * describing. So this is a modifier on the content rather than anything the bar
 * owns.
 *
 * The last case is the one the shape was chosen for. A tab pane routinely holds
 * something that scrolls sideways itself, and a swipe handler that ate those
 * drags would make a carousel inside a tab unusable. Being an *ancestor* of the
 * content is what prevents it: a child gets the main pointer pass first.
 */
class TabSwipeTest {

    @Test
    fun draggingLeftGoesToTheNextTab() {
        var tab by mutableIntStateOf(0)
        var bounds = Rect.Zero

        Scene(width = 800, height = 400) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.LightGray)
                    .reportBounds { bounds = it }
                    .tabSwipe(selected = tab, count = 3, onSelectedChange = { tab = it })
            )
        }.use { scene ->
            scene.frames(3)
            assertTrue(bounds.width > 0f, "the pane never reported a size")
            // Thirty percent of the pane: past the quarter-width threshold, and
            // not far enough for two.
            scene.drag(from = bounds.alongX(0.7f), to = bounds.alongX(0.4f))
            scene.frames(2)
        }

        assertEquals(1, tab, "a leftward swipe left the tab at $tab")
    }

    @Test
    fun draggingRightGoesBack() {
        var tab by mutableIntStateOf(2)
        var bounds = Rect.Zero

        Scene(width = 800, height = 400) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.LightGray)
                    .reportBounds { bounds = it }
                    .tabSwipe(selected = tab, count = 3, onSelectedChange = { tab = it })
            )
        }.use { scene ->
            scene.frames(3)
            scene.drag(from = bounds.alongX(0.3f), to = bounds.alongX(0.6f))
            scene.frames(2)
        }

        assertEquals(1, tab, "a rightward swipe left the tab at $tab")
    }

    @Test
    fun aLongDragStepsThroughSeveral() {
        var tab by mutableIntStateOf(0)
        var bounds = Rect.Zero
        val seen = mutableListOf<Int>()

        Scene(width = 800, height = 400) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.LightGray)
                    .reportBounds { bounds = it }
                    .tabSwipe(
                        selected = tab,
                        count = 4,
                        onSelectedChange = { tab = it; seen += it },
                    )
            )
        }.use { scene ->
            scene.frames(3)
            scene.drag(from = bounds.alongX(0.95f), to = bounds.alongX(0.05f), steps = 40)
            scene.frames(2)
        }

        assertEquals(listOf(1, 2, 3), seen, "a full-width drag did not step through the tabs")
    }

    @Test
    fun itStopsAtTheEnds() {
        var tab by mutableIntStateOf(1)
        var bounds = Rect.Zero

        Scene(width = 800, height = 400) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.LightGray)
                    .reportBounds { bounds = it }
                    .tabSwipe(selected = tab, count = 2, onSelectedChange = { tab = it })
            )
        }.use { scene ->
            scene.frames(3)
            scene.drag(from = bounds.alongX(0.95f), to = bounds.alongX(0.05f), steps = 40)
            scene.frames(2)
        }

        assertEquals(1, tab, "dragging past the last tab went somewhere: $tab")
    }

    @Test
    fun somethingScrollableInsideKeepsItsOwnDrags() {
        var tab by mutableIntStateOf(1)
        var bounds = Rect.Zero

        Scene(width = 800, height = 400) {
            Box(
                Modifier
                    .fillMaxSize()
                    .reportBounds { bounds = it }
                    .tabSwipe(selected = tab, count = 3, onSelectedChange = { tab = it })
            ) {
                Row(Modifier.fillMaxSize().horizontalScroll(rememberScrollState())) {
                    repeat(8) { index ->
                        Box(
                            Modifier
                                .size(300.dp)
                                .background(if (index % 2 == 0) Color.Gray else Color.DarkGray)
                        )
                    }
                }
            }
        }.use { scene ->
            scene.frames(3)
            scene.drag(from = bounds.alongX(0.9f), to = bounds.alongX(0.1f), steps = 30)
            scene.frames(2)
        }

        assertEquals(
            1,
            tab,
            "a drag over a horizontally scrolling row changed the tab to $tab — " +
                "the swipe is eating drags that belong to the content",
        )
    }
}
