package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import io.kontour.ui.components.selection.RangeSlider
import io.kontour.ui.components.selection.Slider
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * What the two sliders do when a finger arrives, rather than what they draw
 * once it has gone.
 *
 * Three gestures, none of which a golden can photograph:
 *
 * - **The thumb comes to the finger.** Pressing at 80% of a slider sitting at
 *   20% and dragging used to move the value *from 20%*, so the first part of
 *   every drag was spent catching up to a press that had already landed.
 * - **A tap travels.** The value is the value, and nothing between one frame and
 *   the next said the thumb had gone anywhere. Now it springs.
 * - **A range slider has detents too.** It shared the plain slider's drag
 *   accumulator and none of its feel, so a stepped range jumped between stops
 *   while a stepped slider eased onto them.
 */
class SliderGestureTest {

    @Test
    fun pressingTheTrackBringsTheThumbToTheFinger() {
        var value by mutableStateOf(0.1f)
        var bounds = Rect.Zero

        Scene(width = 800, height = 200) {
            Box(Modifier.fillMaxSize().background(Color.White)) {
                Slider(
                    value = value,
                    onValueChange = { value = it },
                    modifier = Modifier.fillMaxWidth().reportBounds { bounds = it },
                )
            }
        }.use { scene ->
            scene.frames(3)
            // Past the touch slop, or this is a tap and the tap has always
            // worked — but only just past it. What is being measured is where
            // the *press* put the value, and a long drag would arrive at the
            // right answer from either starting point.
            scene.drag(
                from = bounds.alongX(0.8f),
                to = bounds.alongX(0.85f),
                steps = 10,
            )
            scene.frames(2)
        }

        assertTrue(
            abs(value - 0.85f) < 0.08f,
            "pressing at 80% of the track and dragging five percent further " +
                "left the value at $value rather than near 0.85 — the drag " +
                "started from where the thumb was (0.1) rather than from where " +
                "the finger landed",
        )
    }

    @Test
    fun aTapTravelsRatherThanTeleporting() {
        var value by mutableStateOf(0.05f)
        var bounds = Rect.Zero
        val positions = mutableListOf<Float>()

        Scene(width = 800, height = 200) {
            Box(Modifier.fillMaxSize().background(Color.White)) {
                Slider(
                    value = value,
                    onValueChange = { value = it },
                    modifier = Modifier.fillMaxWidth().reportBounds { bounds = it },
                )
            }
        }.use { scene ->
            scene.frames(3)
            scene.press(bounds.alongX(0.9f))
            scene.frame()
            scene.release(bounds.alongX(0.9f))
            repeat(8) {
                val image = scene.frame()
                val row = image.crownRow() + 4
                positions += image.runsIn(row).last().centre()
            }
        }

        val travelled = positions.last() - positions.first()
        assertTrue(
            travelled > 40f,
            "the thumb only moved ${travelled}px over eight frames after a tap " +
                "at 90%, so it was already there — a tap teleports it",
        )
        // And it did not arrive all at once either: the frames in between are
        // the animation, and without one they would all read the same.
        val distinct = positions.distinct().size
        assertTrue(
            distinct >= 4,
            "the thumb took only $distinct distinct positions across the tap, " +
                "which is a jump with a rounding error rather than a spring:\n" +
                positions.joinToString(),
        )
    }

    @Test
    fun aSteppedRangeSlidersThumbNeverGoesBackwards() {
        // The defect `SliderDetentTest` pins for the plain slider, on the
        // control that shares its arithmetic and had none of its easing.
        var value by mutableStateOf(0.05f..0.15f)
        var bounds = Rect.Zero
        val ends = mutableListOf<Float>()

        Scene(width = 900, height = 200) {
            Box(Modifier.fillMaxSize().background(Color(0xFFFF00FF))) {
                RangeSlider(
                    value = value,
                    onValueChange = { value = it },
                    steps = 4,
                    contentDescription = "Window",
                    modifier = Modifier.fillMaxWidth().reportBounds { bounds = it },
                )
            }
        }.use { scene ->
            scene.frames(3)
            scene.drag(
                from = bounds.alongX(0.15f),
                to = bounds.alongX(0.95f),
                steps = 60,
            ) { _, image ->
                val row = image.crownRow() + 4
                // The rightmost run is the end thumb; the start thumb is not
                // being dragged and stays where it was.
                ends += image.runsIn(row).last().centre()
            }
        }

        assertTrue(ends.size > 20, "only ${ends.size} frames were captured")
        assertTrue(
            ends.last() - ends.first() > 200f,
            "the end thumb moved ${ends.last() - ends.first()}px, so this is not " +
                "measuring a range slider that responded",
        )

        // The detents are eased, not jumped. Without the easing the thumb can
        // only ever be on one of the six stops, so the whole drag reads as six
        // distinct positions; with it, the thumb strains between them.
        val distinct = ends.distinct().size
        assertTrue(
            distinct > 12,
            "the end thumb took only $distinct distinct positions across a " +
                "60-frame drag over five detents, so it is jumping between stops " +
                "rather than easing onto them:\n${ends.distinct().joinToString()}",
        )

        var worst = 0f
        var at = 0
        for (i in 1 until ends.size) {
            val back = ends[i - 1] - ends[i]
            if (back > worst) {
                worst = back
                at = i
            }
        }
        assertTrue(
            worst <= 4f,
            "the end thumb went backwards ${worst}px at frame $at while the " +
                "finger went forwards.\n" +
                (maxOf(0, at - 3)..minOf(ends.size - 1, at + 3)).joinToString("\n") {
                    "  frame $it: ${ends[it]}px" + if (it == at) "  <-- here" else ""
                },
        )
    }
}
