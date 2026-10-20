package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerType
import io.kontour.ui.components.list.ListItem
import io.kontour.ui.components.list.scrollbar
import io.kontour.ui.nav.TopBar
import io.kontour.ui.nav.TopBarCollapseState
import io.kontour.ui.nav.TopBarStyle
import io.kontour.ui.nav.rememberTopBarCollapseState
import io.kontour.ui.nav.topBarCollapse
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A list's scrollbar dragged down collapses a large top bar over it, as a
 * wheel or a swipe down the list does.
 *
 * The thumb moved the list itself, past the scroll chain the bar listens on,
 * so the bar stayed open over a list dragged to its middle. Reported as *"if
 * you drag the scrollbar, the bar doesn't collapse"*.
 */
class ScrollbarCollapsesBarTest {

    @Test
    fun draggingTheThumbCollapsesTheBar() {
        lateinit var collapse: TopBarCollapseState
        lateinit var list: LazyListState
        var bounds = Rect.Zero
        Scene(width = 720, height = 1200) {
            collapse = rememberTopBarCollapseState()
            list = rememberLazyListState()
            Column(Modifier.fillMaxSize().background(Color.White).topBarCollapse(collapse)) {
                TopBar(style = TopBarStyle.Large, collapseState = collapse) { +"Stops" }
                LazyColumn(
                    state = list,
                    modifier = Modifier.fillMaxSize().reportBounds { bounds = it }.scrollbar(list),
                ) {
                    items(80) { index -> ListItem { +"Stop $index" } }
                }
            }
        }.use { scene ->
            scene.frames(6)
            // A mouse, so the bar takes the pointer: on the thumb, at the top
            // of the track down the list's end.
            val thumb = Offset(bounds.right - 6f, bounds.top + 24f)
            scene.move(thumb - Offset(0f, 2f), PointerType.Mouse)
            scene.frames(4)
            scene.drag(from = thumb, to = thumb + Offset(0f, 160f), steps = 16, pointer = PointerType.Mouse)
            scene.frames(30)
        }

        assertTrue(list.firstVisibleItemIndex > 0, "the thumb should have scrolled the list: it is at ${list.firstVisibleItemIndex}")
        assertTrue(collapse.progress > 0.99f, "the bar should have collapsed as the list went down: it is at ${collapse.progress}")
    }
}
