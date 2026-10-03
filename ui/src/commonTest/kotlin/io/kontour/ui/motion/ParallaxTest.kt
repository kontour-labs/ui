package io.kontour.ui.motion

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kontour.ui.theme.KontourTheme
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * `Modifier.parallax` moves with the page as it scrolls, a little, and is done
 * moving when the page is.
 *
 * Reported as *"the "drift" scroll effect feels like it's trying too hard. it
 * needs to be a bit subtler and smoother"*. A spring chased the offset, and was
 * started again on almost every frame of a scroll, so the content moved rigidly
 * with the page while it scrolled and then glided on its own for half a second
 * after it stopped; and a share of the distance from the window's middle came
 * to some 30dp near its edges.
 *
 * Each element is in a frame; how far the element is drawn from its frame is
 * the drift. At a density of 1, 16dp is 16px.
 */
@OptIn(ExperimentalTestApi::class)
class ParallaxTest {

    private lateinit var scroll: ScrollState

    private fun ComposeUiTest.page() {
        mainClock.autoAdvance = false
        setContent {
            KontourTheme(reduceMotion = false) {
                scroll = rememberScrollState()
                Column(Modifier.fillMaxWidth().height(700.dp).verticalScroll(scroll)) {
                    Box(Modifier.height(40.dp))
                    for (i in 0 until 12) {
                        Box(Modifier.fillMaxWidth().height(160.dp).testTag("frame$i")) {
                            Box(Modifier.parallax().size(40.dp).testTag("drift$i"))
                        }
                    }
                }
            }
        }
        mainClock.advanceTimeBy(1_000)
    }

    private fun ComposeUiTest.drift(i: Int): Float =
        onNodeWithTag("drift$i").fetchSemanticsNode().boundsInRoot.top -
            onNodeWithTag("frame$i").fetchSemanticsNode().boundsInRoot.top

    @Test
    fun itIsDoneMovingWhenThePageIs() = runComposeUiTest {
        page()
        runOnUiThread { scroll.dispatchRawDelta(300f) }
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeByFrame()
        val asTheScrollStops = drift(3)
        mainClock.advanceTimeBy(1_000)
        val aSecondLater = drift(3)
        assertTrue(
            abs(aSecondLater - asTheScrollStops) < 1f,
            "it drifted from $asTheScrollStops to $aSecondLater after the page had stopped",
        )
    }

    @Test
    fun itNeverDriftsFar() = runComposeUiTest {
        page()
        val drifts = (0 until 5).map { drift(it) }
        assertTrue(drifts.all { abs(it) <= 16.5f }, "it drifts as far as ${drifts.maxOf { abs(it) }}px from its place: $drifts")
        assertTrue(drifts.any { it > 1f } && drifts.any { it < -1f }, "it does not drift both ways about the middle: $drifts")
    }
}
