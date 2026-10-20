package io.kontour.ui.catalog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.list.rememberScrollbarListState
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A [rememberScrollbarListState] list that leaves while its rows are being
 * measured ahead leaves nothing behind to touch it.
 *
 * Reported from an app on 0.4.5 as a crash, "LayoutNode should be attached to
 * an owner", from leaving a page — closing it, switching tabs, dismissing a
 * sheet — while its list was still loading or scrolling. The rows measured so
 * far are held composed until the last has reported, and letting go of them
 * once the list had gone is what Compose refuses.
 */
class ScrollbarListLeavesTest {

    /** A page closed while its rows are being measured: the state goes with the list. */
    @Test
    fun aPageThatClosesMidMeasureLeavesNothingBehind() {
        var shown by mutableStateOf(true)
        val rows = Rows()
        Scene(width = 400, height = 800) {
            KontourTheme {
                if (shown) {
                    val state = rememberScrollbarListState()
                    LazyColumn(Modifier.fillMaxSize(), state = state) { rows.of(this, List(100) { "row $it" }) }
                }
            }
        }.use { scene ->
            rows.untilMeasuring(scene)
            shown = false
            scene.frames(20)
        }
    }

    /**
     * A state kept above its list — across tabs, or while a spinner stands in
     * for the list — outlives the list it measured for, and lets go of nothing
     * of that list once it is gone, even when the list that comes back is of
     * other rows.
     */
    @Test
    fun aStateKeptAboveItsListOutlivesIt() {
        var shown by mutableStateOf(true)
        var keys by mutableStateOf(List(100) { "first $it" })
        val rows = Rows()
        Scene(width = 400, height = 800) {
            KontourTheme {
                val state = rememberScrollbarListState()
                if (shown) {
                    LazyColumn(Modifier.fillMaxSize(), state = state) { rows.of(this, keys) }
                }
            }
        }.use { scene ->
            rows.untilMeasuring(scene)
            shown = false
            scene.frames(20)
            keys = List(100) { "second $it" }
            shown = true
            scene.frames(60)
        }
    }

    /**
     * A tab of a pager swiped away while its list is being measured: the page
     * is not disposed but put by for reuse, its list detached and kept.
     */
    @Test
    fun aPagerTabSwipedAwayMidMeasureLeavesNothingBehind() {
        val rows = Rows()
        var pager: PagerState? = null
        Scene(width = 400, height = 800) {
            KontourTheme {
                val state = rememberPagerState { 4 }
                pager = state
                HorizontalPager(state, Modifier.fillMaxSize(), beyondViewportPageCount = 0) { page ->
                    val list = rememberScrollbarListState()
                    LazyColumn(Modifier.fillMaxSize(), state = list) { rows.of(this, List(100) { "page $page row $it" }) }
                }
            }
        }.use { scene ->
            rows.untilMeasuring(scene)
            onComposeUiThread { kotlinx.coroutines.runBlocking { requireNotNull(pager).scrollToPage(2) } }
            scene.frames(20)
            onComposeUiThread { kotlinx.coroutines.runBlocking { requireNotNull(pager).scrollToPage(0) } }
            scene.frames(60)
        }
    }

    /** How many rows are composed: those on screen, and those held measured ahead. */
    private class Rows {
        var live = 0

        fun of(list: LazyListScope, keys: List<String>) {
            list.items(keys, key = { it }) {
                DisposableEffect(Unit) {
                    live++
                    onDispose { live-- }
                }
                Box(Modifier.fillMaxWidth().height(40.dp))
            }
        }

        /** Frames until rows beyond those on screen are held, measured ahead. */
        fun untilMeasuring(scene: Scene) {
            scene.frames(5)
            val onScreen = live
            var frames = 0
            while (live < onScreen + HeldAhead && frames < 600) {
                scene.frame()
                frames++
            }
            assertTrue(live >= onScreen + HeldAhead, "no rows were measured ahead in $frames frames ($live composed), so this measures nothing")
        }
    }

    private companion object {
        /** Rows held measured ahead before the list is taken away: a few, not all. */
        const val HeldAhead = 4
    }
}
