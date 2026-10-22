package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.unit.dp
import io.kontour.ui.charts.Chart
import io.kontour.ui.charts.ChartAxis
import io.kontour.ui.charts.ChartRangeMode
import io.kontour.ui.charts.ChartRangeSelectionState
import io.kontour.ui.charts.rememberChartRangeSelectionState
import io.kontour.ui.charts.rememberChartSelectionState
import io.kontour.ui.interaction.FeedbackDispatcher
import io.kontour.ui.interaction.FeedbackIntent
import io.kontour.ui.interaction.LocalFeedback
import io.kontour.ui.overlay.OverlayHost
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A finger held on a chart that marks ranges feels the hold take, once, as
 * the library's long presses do, and a mouse's drag, which has no hold, feels
 * nothing for it.
 *
 * Asked for 2026-10-21 for 0.5.0 from a phone: *"you have to hold down a bit on
 * the chart before it lets you select. that is fine, but a bit of feedback
 * would be nice to know the selection has started. for example, maybe a bit of
 * haptics"*.
 *
 * Six months of riders as a line, in a 360 by 240 chart at this scene's
 * density of 2, the selected place ticking as it moves as well, so what is
 * counted here is the long press alone.
 */
class ChartRangeHoldHapticsTest {

    @Test
    fun aFingerHeldToMarkARangeFeelsTheHoldOnce() = chart { scene, bounds, range, felt ->
        val start = bounds.alongX(0.3f)
        scene.press(start)
        scene.frame()
        // Held well past the wait with nothing sent, so the wait's timer,
        // which a scene runs on a thread of its own, fires with no move
        // arriving beside it: see `ChartHoverHapticsTest`.
        Thread.sleep(LongPressHeldMillis)
        scene.frame()
        assertEquals(listOf(FeedbackIntent.LongPress), felt, "the hold taking, before the finger moved")
        for (step in 1..8) {
            scene.move(bounds.alongX(0.3f + step * 0.05f))
            scene.frame()
        }
        scene.release(bounds.alongX(0.7f))
        scene.frame()
        assertTrue(!range.isEmpty, "the held drag should mark a range")
        assertEquals(1, felt.count { it == FeedbackIntent.LongPress }, "felt once a hold, not again as it moved or lifted: $felt")
    }

    @Test
    fun aMouseDraggingARangeFeelsNoHold() = chart { scene, bounds, range, felt ->
        scene.move(bounds.alongX(0.3f), PointerType.Mouse)
        scene.frame()
        scene.press(bounds.alongX(0.3f), PointerType.Mouse)
        scene.frame()
        for (step in 1..8) {
            scene.move(bounds.alongX(0.3f + step * 0.05f), PointerType.Mouse)
            scene.frame()
        }
        scene.release(bounds.alongX(0.7f), PointerType.Mouse)
        scene.frame()
        assertTrue(!range.isEmpty, "the drag should mark a range")
        assertEquals(0, felt.count { it == FeedbackIntent.LongPress }, "a mouse's drag has no hold to feel: $felt")
    }

    /** Nor one held still first: it is not marking until it moves, and then not by a hold. */
    @Test
    fun aMouseHeldStillThenDraggedFeelsNoHold() = chart { scene, bounds, range, felt ->
        scene.move(bounds.alongX(0.3f), PointerType.Mouse)
        scene.frame()
        scene.press(bounds.alongX(0.3f), PointerType.Mouse)
        scene.frame()
        Thread.sleep(LongPressHeldMillis)
        scene.frame()
        for (step in 1..8) {
            scene.move(bounds.alongX(0.3f + step * 0.05f), PointerType.Mouse)
            scene.frame()
        }
        scene.release(bounds.alongX(0.7f), PointerType.Mouse)
        scene.frame()
        assertTrue(!range.isEmpty, "the drag should mark a range")
        assertEquals(0, felt.count { it == FeedbackIntent.LongPress }, "a mouse held still has no hold to feel: $felt")
    }

    private class Spend(val month: String, val amount: Int)

    private fun chart(test: (Scene, Rect, ChartRangeSelectionState<String, Number>, List<FeedbackIntent>) -> Unit) {
        val felt = mutableListOf<FeedbackIntent>()
        lateinit var range: ChartRangeSelectionState<String, Number>
        val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun").mapIndexed { i, month -> Spend(month, 100 + i * 20) }
        var bounds = Rect.Zero
        Scene(width = 800, height = 560, reduceMotion = true) {
            range = rememberChartRangeSelectionState(ChartRangeMode.Across)
            val selection = rememberChartSelectionState<String>()
            CompositionLocalProvider(LocalFeedback provides FeedbackDispatcher { felt += it }) {
                OverlayHost(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().background(Color.White)) {
                        Chart(
                            xAxis = ChartAxis.category<String>(),
                            yAxis = ChartAxis.number(),
                            modifier = Modifier.size(360.dp, 240.dp).reportBounds { bounds = it },
                            selection = selection,
                            rangeSelection = range,
                            showLegend = false,
                            animateData = false,
                        ) {
                            line(months, x = { it.month }, y = { it.amount })
                        }
                    }
                }
            }
        }.use { scene ->
            scene.frames(3)
            test(scene, bounds, range, felt)
        }
    }

    private companion object {
        /** Past a long press's wait, 500ms on the desktop, with room for a busy machine. */
        const val LongPressHeldMillis = 1500L
    }
}
