package io.kontour.ui.input

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A swipe that starts on the home indicator — out of the app, on iOS — does not
 * scroll the list under it, a swipe that starts above it does, and a tap in the
 * strip still taps.
 */
@OptIn(ExperimentalTestApi::class)
class HomeIndicatorGuardTest {

    private class Probe {
        lateinit var list: LazyListState
        var taps = 0
    }

    private fun ComposeUiTest.screen(): Probe {
        val probe = Probe()
        setContent {
            Box(Modifier.size(400.dp, 600.dp).guardBottomStrip { 34f }) {
                probe.list = rememberLazyListState()
                LazyColumn(Modifier.fillMaxSize(), state = probe.list) {
                    items(100) { Box(Modifier.fillMaxWidth().height(60.dp)) }
                }
                Box(Modifier.align(Alignment.BottomStart).size(100.dp, 20.dp).clickable { probe.taps++ })
            }
        }
        waitForIdle()
        return probe
    }

    private fun ComposeUiTest.swipeUpFrom(y: Float) {
        onRoot().performTouchInput {
            down(Offset(300f, y))
            repeat(12) {
                advanceEventTime(16)
                moveBy(Offset(0f, -25f))
            }
            cancel()
        }
        waitForIdle()
    }

    @Test
    fun aSwipeFromTheHomeIndicatorDoesNotScroll() = runComposeUiTest {
        val probe = screen()
        swipeUpFrom(590f)
        assertEquals(0, probe.list.firstVisibleItemIndex * 1000 + probe.list.firstVisibleItemScrollOffset, "a swipe up from the home indicator scrolled the list")
    }

    @Test
    fun aSwipeFromAboveItStillScrolls() = runComposeUiTest {
        val probe = screen()
        swipeUpFrom(450f)
        assertTrue(probe.list.firstVisibleItemIndex * 1000 + probe.list.firstVisibleItemScrollOffset > 0, "a swipe up from the middle of the screen did not scroll the list")
    }

    @Test
    fun aTapInTheStripStillTaps() = runComposeUiTest {
        val probe = screen()
        onRoot().performTouchInput {
            down(Offset(50f, 590f))
            advanceEventTime(30)
            moveBy(Offset(1f, 1f))
            advanceEventTime(30)
            up()
        }
        waitForIdle()
        assertEquals(1, probe.taps, "a tap on the bar in the home indicator's strip did not tap")
    }
}
