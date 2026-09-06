package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.selection.RangeSlider
import io.kontour.ui.components.selection.Slider
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Moving the second thumb must not undo the first.
 *
 * Reported as "move one end, then the other, and the first one returns to where
 * it started", and it is a stale capture rather than anything about ranges.
 *
 * `horizontalDragOwning` keys its `pointerInput` on `enabled` and the
 * interaction source, neither of which changes, so the block never restarts —
 * and the `onStart`/`onDelta`/`onEnd` lambdas it captured on the *first*
 * composition are the ones it keeps calling forever. Those lambdas close over
 * `value`. `RangeSlider.emit` falls back to `value` for the first emission of
 * every gesture, because `emitted` is nulled when one starts. So the second
 * gesture rebuilds the pair from the range the slider had when it was first
 * composed, and whatever the first gesture did is thrown away.
 *
 * Which is why the plain [Slider] is here too. It has the same modifier and the
 * same staleness, and its symptom is quieter — a second drag starts from the
 * old value rather than the current one — so it would have gone on being
 * invisible while the range slider got fixed on its own.
 */
class RangeSliderSecondDragTest {

    @Test
    fun movingTheEndThumbLeavesTheStartWhereItWasPut() {
        var value by mutableStateOf(0.2f..0.8f)
        var bounds = Rect.Zero

        Scene(width = 600, height = 200) {
            Box(Modifier.fillMaxSize().background(Color.White).padding(20.dp)) {
                RangeSlider(
                    value = value,
                    onValueChange = { value = it },
                    modifier = Modifier.fillMaxWidth().reportBounds { bounds = it },
                )
            }
        }.use { scene ->
            scene.frames(3)
            val y = bounds.center.y
            fun at(fraction: Float) = Offset(bounds.left + bounds.width * fraction, y)

            // First gesture: take the start thumb from 0.2 out to about 0.4.
            scene.drag(at(0.2f), at(0.4f), steps = 16)
            scene.frames(3)
            val afterFirst = value.start
            assertTrue(
                afterFirst > 0.3f,
                "the first drag did not move the start thumb at all — it is at " +
                    "$afterFirst, so this test measured nothing",
            )

            // Second gesture: the *other* thumb, from 0.8 in to about 0.65.
            scene.drag(at(0.8f), at(0.65f), steps = 16)
            scene.frames(3)
        }

        assertTrue(
            value.start > 0.3f,
            "dragging the end thumb sent the start thumb back to ${value.start}. " +
                "It was left at about 0.4 by the drag before this one, and nothing " +
                "in this gesture went near it — the second gesture is rebuilding " +
                "the pair from a `value` captured on the first composition.",
        )
        assertTrue(
            value.endInclusive < 0.75f,
            "the second drag did not move the end thumb: ${value.endInclusive}",
        )
    }

    @Test
    fun aSecondDragOnAPlainSliderStartsFromWhereTheFirstLeftIt() {
        var value by mutableStateOf(0.5f)
        var bounds = Rect.Zero

        Scene(width = 600, height = 200) {
            Box(Modifier.fillMaxSize().background(Color.White).padding(20.dp)) {
                Slider(
                    value = value,
                    onValueChange = { value = it },
                    modifier = Modifier.fillMaxWidth().reportBounds { bounds = it },
                )
            }
        }.use { scene ->
            scene.frames(3)
            val y = bounds.center.y
            fun at(fraction: Float) = Offset(bounds.left + bounds.width * fraction, y)

            // Out to the right, let go, then a short drag *from where it now is*.
            scene.drag(at(0.5f), at(0.9f), steps = 16)
            scene.frames(3)
            val afterFirst = value
            assertTrue(afterFirst > 0.8f, "the first drag left it at $afterFirst")

            // Press on the thumb and nudge left by a tenth of the track.
            scene.drag(at(0.9f), at(0.8f), steps = 8)
            scene.frames(3)
        }

        assertTrue(
            value > 0.7f,
            "a second drag left the slider at $value. It was at about 0.9 and the " +
                "finger moved a tenth of the track to the left, so about 0.8 is " +
                "the answer; anything near 0.4 is the gesture accumulating from a " +
                "`value` captured on the first composition.",
        )
    }
}
