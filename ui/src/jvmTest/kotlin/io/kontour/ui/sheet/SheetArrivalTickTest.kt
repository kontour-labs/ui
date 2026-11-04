package io.kontour.ui.sheet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kontour.ui.interaction.FeedbackDispatcher
import io.kontour.ui.interaction.FeedbackIntent
import io.kontour.ui.interaction.LocalFeedback
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A sheet dragged by hand ticks as it gets to a detent, not half way to it.
 * It ticked as the detent a release would land on changed, which is half way
 * between two, with nothing under the finger: reported from Anyways, which
 * played its own tick on arrival instead.
 */
@OptIn(ExperimentalTestApi::class)
class SheetArrivalTickTest {

    @Test
    fun aDragFromHalfToFullHeightTicksAsItArrives() = runComposeUiTest {
        val felt = mutableListOf<FeedbackIntent>()
        lateinit var state: SheetState
        mainClock.autoAdvance = false
        setContent {
            KontourTheme(darkTheme = false) {
                CompositionLocalProvider(LocalFeedback provides FeedbackDispatcher { felt += it }) {
                    Box(Modifier.fillMaxSize().background(Color.White)) {
                        OverlayHost(Modifier.fillMaxSize()) {
                            state = rememberSheetState(
                                detents = listOf(SheetDetent.peek(120.dp), SheetDetent.Half, SheetDetent.Expanded),
                                initialDetent = SheetDetent.Half,
                            )
                            BottomSheet(
                                state = state,
                                presentation = SheetPresentation.Edge,
                                dragHandle = { DragHandle(Modifier.testTag("handle")) },
                            ) { Box(Modifier.fillMaxWidth().height(3000.dp)) }
                        }
                    }
                }
            }
        }
        repeat(60) { mainClock.advanceTimeByFrame() }
        val half = state.anchoredState.anchors.positionOf(SheetDetent.Half)
        val full = state.anchoredState.anchors.positionOf(SheetDetent.Expanded)
        val gap = half - full
        assertTrue(gap > 100f, "no room between half and full height: $half, $full")

        // Up, a few pixels a frame, noting where the sheet was at each tick.
        val step = 6f
        val tickedAt = mutableListOf<Float>()
        onNodeWithTag("handle").performTouchInput { down(center) }
        var moved = 0f
        while (moved < gap + 20f) {
            moved += step
            onNodeWithTag("handle").performTouchInput { moveBy(Offset(0f, -step)) }
            mainClock.advanceTimeByFrame()
            val snaps = felt.count { it == FeedbackIntent.Snap }
            while (tickedAt.size < snaps) tickedAt += state.anchoredState.offset
        }

        assertEquals(1, tickedAt.size, "a drag from half to full height ticked ${tickedAt.size} times")
        val short = tickedAt.single() - full
        assertTrue(
            short <= gap * ArrivedShare + step,
            "it ticked ${short}px short of full height, of a ${gap}px drag: before it got there",
        )
    }
}
