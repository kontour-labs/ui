package io.kontour.ui.catalog

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import io.kontour.ui.nav.Tab
import io.kontour.ui.nav.TabBar
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A tab bar's marker, dragged between tabs whenever the row has nothing to
 * scroll: always for a fixed bar, and for a scrollable one whose tabs fit.
 *
 * A scrollable bar is a horizontal scroller, and a sideways drag along it is
 * the one gesture it cannot share. But a scrollable bar of three short tabs on
 * a wide window has nowhere to scroll, and a drag across it did nothing at all.
 * The question is asked of the row when the finger goes down: tabs that fit
 * drag the marker, and tabs that overflow scroll, as they always did.
 */
class TabBarMarkerDragTest {

    private companion object {
        const val Density = 2f
        val Short = listOf("Go", "Up", "On")
        val Long = listOf(
            "Departures", "Arrivals", "Platforms", "Disruptions", "Lifts", "Fares",
            "Timetables", "Accessibility", "Parking", "Facilities", "Tickets", "Help",
        )
    }

    private val tabs = HashMap<Int, Rect>()

    private class Bar(
        val scene: Scene,
        val clicks: MutableList<Int>,
        val scroll: () -> ScrollState,
        val labels: (List<String>) -> Unit,
    )

    private fun bar(
        labels: List<String>,
        scrollable: Boolean = true,
        direction: LayoutDirection = LayoutDirection.Ltr,
    ): Bar {
        val clicks = mutableListOf<Int>()
        var here by mutableStateOf(0)
        var shown by mutableStateOf(labels)
        var scroll: ScrollState? = null
        val scene = Scene(width = 800, height = 200, density = Density, reduceMotion = true) {
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                Box(Modifier.fillMaxSize().background(Color.White)) {
                    val state = rememberScrollState()
                    scroll = state
                    TabBar(scrollable = scrollable, scrollState = state) {
                        shown.forEachIndexed { i, label ->
                            Tab(
                                selected = here == i,
                                onClick = { clicks += i; here = i },
                                key = i,
                                modifier = Modifier.reportBounds { tabs[i] = it },
                            ) { +label }
                        }
                    }
                }
            }
        }
        scene.frames(8)
        return Bar(scene, clicks, { requireNotNull(scroll) }, { shown = it })
    }

    private fun centre(i: Int) = requireNotNull(tabs[i]) { "tab $i was never placed" }.center

    @Test
    fun aScrollableBarWhoseTabsFitDragsItsMarker() {
        val bar = bar(Short)
        bar.scene.use { scene ->
            assertEquals(0, bar.scroll().maxValue, "three short tabs should fit in 400dp")
            scene.drag(from = centre(0), to = centre(2), steps = 16)
            scene.frames(10)
            assertEquals(
                listOf(2),
                bar.clicks,
                "dragged from the first tab to the third of a row with nothing to scroll, " +
                    "and the lift should have chosen the third, once",
            )
        }
    }

    @Test
    fun aFixedBarDragsItsMarker() {
        val bar = bar(Short, scrollable = false)
        bar.scene.use { scene ->
            scene.drag(from = centre(0), to = centre(2), steps = 16)
            scene.frames(10)
            assertEquals(listOf(2), bar.clicks, "a fixed bar never scrolls, and the drag chose nothing")
        }
    }

    @Test
    fun rightToLeftTheDragFollowsTheFinger() {
        val bar = bar(Short, direction = LayoutDirection.Rtl)
        bar.scene.use { scene ->
            assertTrue(centre(0).x > centre(2).x, "right to left, the first tab should be on the right")
            scene.drag(from = centre(0), to = centre(1), steps = 16)
            scene.frames(10)
            assertEquals(listOf(1), bar.clicks, "right to left, the drag should choose the tab under the finger")
        }
    }

    @Test
    fun aBarWhoseTabsOverflowStillScrolls() {
        val bar = bar(Long)
        bar.scene.use { scene ->
            assertTrue(bar.scroll().maxValue > 0, "twelve long tabs should overflow 400dp")
            val from = centre(2)
            scene.drag(from = from, to = Offset(from.x - 300f, from.y), steps = 16)
            scene.frames(30)
            assertTrue(bar.scroll().value > 100, "a drag along an overflowing row scrolled it ${bar.scroll().value}px")
            assertEquals(emptyList(), bar.clicks, "a scroll of an overflowing row chose a tab")
        }
    }

    /** Tabs added until they overflow: the same drag scrolls rather than choosing. */
    @Test
    fun tabsAddedPastTheEdgeTurnTheDragIntoAScroll() {
        val bar = bar(Short)
        bar.scene.use { scene ->
            scene.drag(from = centre(0), to = centre(1), steps = 12)
            scene.frames(10)
            assertEquals(listOf(1), bar.clicks, "three short tabs fit, so the drag should have moved the marker")

            bar.labels(Long)
            scene.frames(10)
            assertTrue(bar.scroll().maxValue > 0, "twelve long tabs should overflow 400dp")
            val from = centre(2)
            scene.drag(from = from, to = Offset(from.x - 300f, from.y), steps = 16)
            scene.frames(30)
            assertTrue(bar.scroll().value > 100, "grown past the edge, the row did not scroll")
            assertEquals(listOf(1), bar.clicks, "grown past the edge, the drag chose a tab")
        }
    }
}
