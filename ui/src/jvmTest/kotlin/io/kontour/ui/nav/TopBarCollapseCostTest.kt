package io.kontour.ui.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.kontour.ui.PhaseCounts
import io.kontour.ui.countPhases
import io.kontour.ui.foundation.Text
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A collapsing bar does no more work than its collapse needs.
 *
 * None of these shows in a picture: the bar comes out the same either way. Each
 * was a read of the collapse where nothing drawn depended on it, so the title
 * was laid out or drawn over again on frames that changed nothing about it.
 * Counted by the title itself, given to the bar as a composable label, which is
 * drawn whenever the bar's own drawing of it runs: both of its copies, the large
 * one and the small one the collapse hands over to.
 */
class TopBarCollapseCostTest {

    private class Page {
        val title = PhaseCounts()
        val bar = PhaseCounts()
        var composed = 0
        lateinit var list: LazyListState
        lateinit var collapse: TopBarCollapseState
        private var nanos = 0L

        val scene = ImageComposeScene(width = 720, height = 1400, density = Density(2f)) {
            KontourTheme(darkTheme = false) {
                list = rememberLazyListState()
                collapse = rememberTopBarCollapseState(list)
                // The screen round the list: what reads the bar's open height.
                SideEffect { composed++ }
                Box(Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = list,
                        modifier = Modifier.topBarCollapse(collapse),
                        contentPadding = PaddingValues(top = collapse.openHeight),
                    ) {
                        items(200) { index -> Text("Row $index", modifier = Modifier.fillMaxWidth().height(48.dp)) }
                    }
                    TopBar(
                        modifier = Modifier.countPhases(bar),
                        style = TopBarStyle.Large,
                        collapseState = collapse,
                        onBack = {},
                        windowInsets = WindowInsets(0),
                    ) {
                        label { Text("Perth", modifier = Modifier.countPhases(title)) }
                    }
                }
            }
        }

        fun frame() {
            nanos += 16_000_000L
            scene.render(nanos).close()
        }

        /** Scrolls the list [by] pixels a frame, [frames] times. */
        fun scroll(by: Float, frames: Int) = repeat(frames) {
            list.dispatchRawDelta(by)
            frame()
        }
    }

    /**
     * A bar following its list, collapsed, is not laid out or drawn again as the
     * list goes on scrolling under it.
     *
     * Its progress was read from the list's scroll, which changes on every frame
     * the list moves, so the bar's layout and its title's drawing ran on every
     * one of them for a progress that stayed at 1 — the whole of a long page's
     * scroll once its bar had collapsed. Forty frames of it drew the title forty
     * times.
     */
    @Test
    fun aCollapsedFollowingBarIsNotDrawnAgainAsItsListScrolls() {
        val page = Page()
        try {
            repeat(4) { page.frame() }
            page.scroll(by = 40f, frames = 20)
            assertEquals(1f, page.collapse.progress, "the bar should have collapsed by now")

            page.title.reset()
            page.bar.reset()
            val before = page.list.firstVisibleItemIndex
            page.scroll(by = 10f, frames = Frames)

            assertTrue(page.list.firstVisibleItemIndex > before, "the list never scrolled on")
            assertEquals(0, page.title.draws, "a collapsed bar's title was drawn ${page.title.draws} times in $Frames frames of the list scrolling under it")
            assertEquals(0, page.bar.measures, "a collapsed bar was laid out ${page.bar.measures} times in $Frames frames of the list scrolling under it")
        } finally {
            page.scene.close()
        }
    }

    /**
     * A title of one line is laid out and drawn once for its collapse, not every
     * frame or every few, and the bar's open height stays what it is.
     *
     * Its layer moves and scales it, and nothing drawn inside it changes: the
     * collapse is read in its drawing only to take out a second line, which a
     * title of one line does not have. Read regardless, every frame of every
     * collapse drew the title over again.
     *
     * And the open height, worked back from the height the bar is part-way, came
     * out a pixel taller wherever the collapse's share of it was exactly half a
     * pixel: every fourth frame here. The list's padding moved with it, and the
     * screen that read it composed again, the bar and both its titles with it.
     */
    @Test
    fun aOneLineTitleIsNotLaidOutOrDrawnOnEveryFrameOfItsCollapse() {
        val page = Page()
        try {
            repeat(4) { page.frame() }
            val open = page.collapse.openHeight
            val composed = page.composed
            page.title.reset()
            val heights = mutableSetOf<Dp>()
            var collapsing = 0
            while (page.collapse.progress < 1f && collapsing < 200) {
                page.scroll(by = 4f, frames = 1)
                heights += page.collapse.openHeight
                collapsing++
            }

            assertEquals(1f, page.collapse.progress, "the bar never finished collapsing")
            assertTrue(collapsing >= 20, "the collapse took only $collapsing frames, too few to tell")
            assertEquals(setOf(open), heights, "the bar's open height moved as it collapsed")
            assertEquals(composed, page.composed, "the screen round the bar composed again ${page.composed - composed} times as it collapsed")
            assertTrue(
                page.title.draws <= 4 && page.title.measures <= 4,
                "a one-line title was ${page.title} over a $collapsing-frame collapse",
            )
        } finally {
            page.scene.close()
        }
    }

    private companion object {
        const val Frames = 40
    }
}
