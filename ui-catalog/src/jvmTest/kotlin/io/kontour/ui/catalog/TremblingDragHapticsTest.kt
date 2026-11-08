package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.selection.Rating
import io.kontour.ui.components.selection.Slider
import io.kontour.ui.interaction.FeedbackDispatcher
import io.kontour.ui.interaction.FeedbackIntent
import io.kontour.ui.interaction.LocalFeedback
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A finger is never still. Dragged slowly across a control, it wobbles a pixel
 * or two back and forth as it goes, and a control that cuts the finger's
 * position into steps with no give at the boundaries feels each wobble across
 * one as another step: a haptic for every tremble, where the hand crossed once.
 *
 * Found in November 2026 in the segmented control (see
 * `SegmentedControlFeedbackTest`); this holds the other controls that turn a
 * finger's position into steps to the same rule. Each drag here creeps across
 * the control three pixels at a time with a two-and-a-half pixel tremble on top,
 * at a hand's pace, and the haptics it felt are counted against the steps the
 * value actually moved through.
 */
class TremblingDragHapticsTest {

    private fun Scene.trembleAcross(from: Offset, to: Offset) {
        val length = abs(to.x - from.x)
        val steps = (length / 3f).toInt()
        press(from)
        for (i in 1..steps) {
            val x = from.x + (to.x - from.x) * i / steps + if (i % 2 == 0) 2.5f else -2.5f
            move(Offset(x, from.y))
            frame()
            Thread.sleep(16)
        }
        release(to)
        frames(4)
    }

    @Composable
    private fun recorded(felt: MutableList<FeedbackIntent>, content: @Composable () -> Unit) =
        CompositionLocalProvider(LocalFeedback provides FeedbackDispatcher { felt += it }, content = content)

    @Test
    fun aSteppedSliderTicksOncePerStepUnderATremblingFinger() {
        val felt = mutableListOf<FeedbackIntent>()
        val values = mutableListOf<Float>()
        var value by mutableFloatStateOf(0f)
        var bounds = Rect.Zero
        Scene(width = 600, height = 200) {
            recorded(felt) {
                Box(Modifier.fillMaxSize().background(Color.White).padding(20.dp)) {
                    Slider(
                        value = value,
                        onValueChange = { value = it; values += it },
                        valueRange = 0f..4f,
                        steps = 3,
                        modifier = Modifier.fillMaxWidth().reportBounds { bounds = it },
                    )
                }
            }
        }.use { scene ->
            scene.frames(3)
            scene.trembleAcross(bounds.alongX(0.02f), bounds.alongX(0.98f))
        }
        val moves = values.zipWithNext().count { (a, b) -> a != b }
        val ticks = felt.count { it == FeedbackIntent.Tick }
        assertTrue(ticks <= 4, "four steps crossed by a trembling finger felt $ticks ticks, the value moved $moves times: $values")
    }

    @Test
    fun aRatingTicksOncePerStarUnderATremblingFinger() {
        val felt = mutableListOf<FeedbackIntent>()
        val values = mutableListOf<Float>()
        var score by mutableFloatStateOf(0f)
        var bounds = Rect.Zero
        Scene(width = 600, height = 200) {
            recorded(felt) {
                Box(Modifier.fillMaxSize().background(Color.White)) {
                    Rating(
                        value = score,
                        contentDescription = "Your rating",
                        onValueChange = { score = it; values += it },
                        modifier = Modifier.reportBounds { bounds = it },
                    )
                }
            }
        }.use { scene ->
            scene.frames(3)
            scene.trembleAcross(bounds.alongX(0.05f), bounds.alongX(0.95f))
        }
        val ticks = felt.count { it == FeedbackIntent.Tick }
        assertTrue(ticks <= 5, "five stars crossed by a trembling finger felt $ticks ticks: ${felt}, values $values")
    }
}
