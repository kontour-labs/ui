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

    /**
     * Let go, it ticks as the release sends it, not as it lands. It waited out
     * the settle: *"it waits for the sheet to fully settle before playing the
     * haptic. it should play on the snap"*, raised 2026-11-10 for 0.5.5. A
     * flick up from half, short of full height, ticks while the sheet is still
     * on its way there.
     */
    @Test
    fun aFlickTicksOnTheSnapNotOnTheLanding() = runComposeUiTest {
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
        val full = state.anchoredState.anchors.positionOf(SheetDetent.Expanded)

        // A quick flick up, short of arriving: a few fast moves and let go.
        onNodeWithTag("handle").performTouchInput {
            down(center)
            repeat(5) {
                advanceEventTime(8)
                moveBy(Offset(0f, -24f))
            }
            advanceEventTime(8)
            up()
        }

        var framesToTick = -1
        var stillMovingAtTick = false
        var frames = 0
        while (frames < 120) {
            mainClock.advanceTimeByFrame()
            frames++
            if (framesToTick < 0 && felt.any { it == FeedbackIntent.Snap }) {
                framesToTick = frames
                stillMovingAtTick = state.isMoving && state.anchoredState.offset - full > 1f
            }
            if (!state.isMoving && framesToTick >= 0) break
        }
        assertEquals(SheetDetent.Expanded, state.currentDetent, "the flick did not take it to full height")
        assertTrue(framesToTick in 1..3, "it ticked $framesToTick frames after it was let go, not on the snap")
        assertTrue(stillMovingAtTick, "it ticked only once it had landed: ${frames} frames to settle, ticked at $framesToTick")
        assertEquals(1, felt.count { it == FeedbackIntent.Snap }, "a flick to full height ticked more than once")
    }
}
