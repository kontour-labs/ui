package io.kontour.ui.motion

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * `Transitions.sharedAxis` along X goes forward the way the line reads.
 *
 * Its slides were in raw pixels, which are the same in either direction, so in
 * right to left a step forward arrived from the right — from where the reader
 * had come — and a step back from the left. `PageMotion`'s own shared axis had
 * the sign right; the public preset did not.
 */
@OptIn(ExperimentalTestApi::class)
class SharedAxisDirectionTest {

    /** Where the incoming page's left edge is, a little way into a step forward. */
    private fun ComposeUiTest.incomingLeft(direction: LayoutDirection): Float {
        mainClock.autoAdvance = false
        var step by mutableIntStateOf(0)
        val pages = mutableMapOf<Int, LayoutCoordinates>()
        setContent {
            KontourTheme(reduceMotion = false) {
                CompositionLocalProvider(LocalLayoutDirection provides direction) {
                    val forward = Transitions.sharedAxis(forward = true)
                    Box(Modifier.size(300.dp, 200.dp)) {
                        AnimatedContent(targetState = step, transitionSpec = { forward }, label = "step") { page ->
                            Box(Modifier.fillMaxSize().onPlaced { pages[page] = it })
                        }
                    }
                }
            }
        }
        mainClock.advanceTimeBy(500)
        runOnUiThread { step = 1 }
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeBy(48)
        val incoming = assertNotNull(pages[1], "the incoming page was never placed")
        return runOnUiThread { incoming.positionInRoot().x }
    }

    @Test
    fun forwardArrivesFromTheRightInLeftToRight() = runComposeUiTest {
        val left = incomingLeft(LayoutDirection.Ltr)
        assertTrue(left > 0f, "a step forward in left to right came in from the left: $left")
    }

    @Test
    fun forwardArrivesFromTheLeftInRightToLeft() = runComposeUiTest {
        val left = incomingLeft(LayoutDirection.Rtl)
        assertTrue(left < 0f, "a step forward in right to left came in from the right, where the reader had come from: $left")
    }
}
