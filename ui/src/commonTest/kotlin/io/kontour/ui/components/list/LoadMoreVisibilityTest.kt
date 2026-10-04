package io.kontour.ui.components.list

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kontour.ui.theme.KontourTheme
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `LoadMore` asks for the next page when it is on screen, not when it is
 * composed.
 *
 * Reported from an app: *"`LoadMore` loads as soon as it's composed, not when
 * it comes into view … inside a plain `verticalScroll` column it's composed
 * straight away, so it chains through every page until the list ends."* Its
 * docs said "when it comes into view", and it loaded whenever it was idle.
 */
@OptIn(ExperimentalTestApi::class)
class LoadMoreVisibilityTest {

    @Test
    fun aFootBelowTheFoldWaits() = runComposeUiTest {
        var loads = 0
        lateinit var scroll: ScrollState
        setContent {
            KontourTheme(reduceMotion = true) {
                scroll = rememberScrollState()
                Column(Modifier.size(320.dp, 480.dp).verticalScroll(scroll)) {
                    repeat(30) { Box(Modifier.fillMaxWidth().height(64.dp)) }
                    LoadMore(status = LoadMoreStatus.Idle, onLoadMore = { loads++ })
                }
            }
        }
        waitForIdle()
        assertEquals(0, loads, "the foot is thirty rows below a 480dp column: nothing should have loaded")
        runOnIdle { runBlocking { scroll.scrollTo(scroll.maxValue) } }
        waitForIdle()
        assertEquals(1, loads, "scrolled to the foot, it should load once")
    }

    @Test
    fun inALazyColumnItLoadsWhenReached() = runComposeUiTest {
        var loads = 0
        val list = LazyListState()
        setContent {
            KontourTheme(reduceMotion = true) {
                LazyColumn(Modifier.size(320.dp, 480.dp), state = list) {
                    items(30) { Box(Modifier.fillMaxWidth().height(64.dp)) }
                    item { LoadMore(status = LoadMoreStatus.Idle, onLoadMore = { loads++ }) }
                }
            }
        }
        waitForIdle()
        assertEquals(0, loads)
        runOnIdle { runBlocking { list.scrollToItem(30) } }
        waitForIdle()
        assertEquals(1, loads)
    }

    /** A page too short to push the foot off screen: still in view, it asks again. */
    @Test
    fun stillInViewAfterAPageItAsksAgain() = runComposeUiTest {
        var loads = 0
        var status by mutableStateOf(LoadMoreStatus.Idle)
        setContent {
            KontourTheme(reduceMotion = true) {
                Column(Modifier.size(320.dp, 480.dp).verticalScroll(rememberScrollState())) {
                    repeat(2) { Box(Modifier.fillMaxWidth().height(64.dp)) }
                    LoadMore(status = status, onLoadMore = { loads++; status = LoadMoreStatus.Loading })
                }
            }
        }
        waitForIdle()
        assertEquals(1, loads)
        status = LoadMoreStatus.Idle
        waitForIdle()
        assertEquals(2, loads, "a foot still on screen after a short page should ask for the next one")
    }

    @Test
    fun disabledItWaitsEvenInView() = runComposeUiTest {
        var loads = 0
        setContent {
            KontourTheme(reduceMotion = true) {
                Column(Modifier.size(320.dp, 480.dp)) {
                    LoadMore(status = LoadMoreStatus.Idle, onLoadMore = { loads++ }, enabled = false)
                }
            }
        }
        waitForIdle()
        assertEquals(0, loads)
    }
}
