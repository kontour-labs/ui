package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import io.kontour.ui.adaptive.Scaffold
import io.kontour.ui.components.list.ListItem
import io.kontour.ui.nav.TopBar
import io.kontour.ui.nav.TopBarCollapseState
import io.kontour.ui.nav.TopBarStyle
import io.kontour.ui.nav.rememberTopBarCollapseState
import io.kontour.ui.nav.topBarCollapse
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A mouse wheel collapses a top bar and opens it again, all the way.
 *
 * Reported: *"if you scroll down and then scroll back up, then the bar/image
 * won't expand back to full. this doesn't affect the large bar."* A list
 * declines a wheel's notch outright when it is already at its top, so the notch
 * never became a scroll and the state's connection never heard of it: the bar
 * opened by whatever was left of the notch that brought the list home, and no
 * further. A Large bar's travel is short enough for that remainder to cover;
 * a Hero bar's is about three times as long, and it stopped part-way.
 * [topBarCollapse] hears the notches the list declined.
 */
class TopBarWheelTest {

    private class Page(val scene: Scene, val state: TopBarCollapseState, val list: LazyListState, val bar: () -> Rect)

    private fun page(style: TopBarStyle, test: Page.() -> Unit) {
        lateinit var state: TopBarCollapseState
        lateinit var list: LazyListState
        var bar = Rect.Zero
        Scene(width = 400, height = 1120, density = 2f) {
            state = rememberTopBarCollapseState()
            list = rememberLazyListState()
            Scaffold(
                modifier = Modifier.topBarCollapse(state),
                topBar = {
                    TopBar(
                        modifier = Modifier.reportBounds { bar = it },
                        style = style,
                        onBack = {},
                        image = { Box(Modifier.fillMaxSize().background(Color.Gray)) },
                        collapseState = state,
                        windowInsets = WindowInsets(0),
                    ) { +"Perth Underground"; supporting { +"Platform 2" } }
                },
                windowInsets = WindowInsets(0),
            ) { padding ->
                LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = padding) {
                    items(30) { ListItem { +"Departure $it" } }
                }
            }
        }.use { scene ->
            scene.frames(10)
            Page(scene, state, list, { bar }).test()
        }
    }

    private val over = Offset(200f, 900f)

    private fun Page.wheel(notches: Int, down: Boolean) = repeat(notches) {
        scene.scroll(over, Offset(0f, if (down) 1f else -1f))
        scene.frames(4)
    }

    @Test
    fun aBarScrolledAwayAndBackOpensAllTheWay() {
        for (style in listOf(TopBarStyle.Hero, TopBarStyle.Large)) page(style) {
            val open = bar().height
            wheel(80, down = true)
            scene.frames(60)
            assertEquals(1f, state.progress, "$style: the wheel never collapsed the bar")
            assertEquals(true, list.firstVisibleItemIndex > 0, "$style: the list never scrolled, so this measured nothing")
            wheel(160, down = false)
            scene.frames(60)
            assertEquals(0, list.firstVisibleItemIndex)
            assertEquals(0, list.firstVisibleItemScrollOffset)
            assertEquals(0f, state.progress, "$style: scrolled back to the top, the bar stopped at ${state.progress}")
            assertEquals(open, bar().height, "$style: the bar came back shorter than it started")
        }
    }

    /**
     * A Hero bar takes the whole of a scroll until it has collapsed, so a notch
     * or two collapses it part-way without the list moving at all — and back
     * up, the list at its top declines every notch.
     */
    @Test
    fun aNotchBackOpensABarTheListNeverScrolledFor() {
        page(TopBarStyle.Hero) {
            wheel(2, down = true)
            scene.frames(60)
            val partWay = state.progress
            assertEquals(true, partWay > 0f && partWay < 1f, "two notches left the bar at $partWay, so this measured nothing")
            assertEquals(0, list.firstVisibleItemScrollOffset, "the list scrolled, so this is the other test")
            wheel(1, down = false)
            scene.frames(60)
            assertEquals(true, state.progress < partWay, "a notch back up did not open the bar at all: ${state.progress}")
            wheel(3, down = false)
            scene.frames(60)
            assertEquals(0f, state.progress, "notches back up left the bar at ${state.progress}")
        }
    }

    /**
     * A trackpad's small piece of scroll at a list's top opens the bar as far
     * as it went, not all the way. Reported from a desktop as *"it isn't
     * smooth, it sort of just jumps if you scroll slowly"*: anything the list
     * declined finished the bar, so the first sliver of a slow trackpad scroll
     * threw a part-way bar open.
     */
    @Test
    fun aSliverOfTrackpadScrollAtTheTopOpensTheBarOnlyALittle() {
        page(TopBarStyle.Hero) {
            wheel(2, down = true)
            scene.frames(60)
            val partWay = state.progress
            assertEquals(true, partWay > 0f && partWay < 1f, "two notches left the bar at $partWay, so this measured nothing")
            scene.scroll(over, Offset(0f, -0.05f))
            scene.frames(60)
            val after = state.progress
            assertEquals(true, after < partWay && after > 0f, "a sliver of scroll moved the bar from $partWay to $after")
        }
    }
}
