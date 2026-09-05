package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The three things round 22 measured about a two-thumbed drag.
 *
 * All three were reported as one sentence — "dragging one thumb into the other
 * and continuing, then reversing, does not separate them until the original
 * collision point; sometimes starting a drag makes the other thumb glide
 * somewhere else" — and they turned out to be three separate defects, none of
 * which any existing test could see. `RangeSliderPushTest` asserts the *push*,
 * which was correct throughout; what was wrong was everything about coming
 * back, and everything about a press that had not become a drag yet.
 *
 * Each case here was a measurement before it was a test: the numbers in the
 * messages are what the old code actually did.
 */
class RangeSliderSeparationTest {

    /**
     * The pushed thumb stays where it was pushed to.
     *
     * `emit` holds it with `maxOf(theOtherThumb, dragged + gap)`, which was
     * reading the caller's `value` — one emit behind, because it comes back
     * through a recomposition. So the comparison was against
     * `dragged_previous + gap`, which on the way back is below where the thumb
     * actually is, and the pushed thumb followed the dragging one home with the
     * pair still exactly `gap` apart the whole way.
     */
    @Test
    fun thePushedThumbDoesNotFollowTheOtherBack() {
        var range by mutableStateOf(2f..6f)
        var bounds = Rect.Zero
        var atTheTurn = 0f..0f

        Scene(width = 700, height = 240) {
            Box(Modifier.fillMaxSize().background(Color.White).padding(20.dp)) {
                RangeSlider(
                    value = range,
                    onValueChange = { range = it },
                    valueRange = Range,
                    modifier = Modifier.reportBounds { bounds = it },
                )
            }
        }.use { scene ->
            scene.frames(3)
            assertTrue(bounds.width > 0f, "the range slider never reported a size")
            val from = bounds.alongX(0.2f)
            val to = bounds.alongX(0.7f)

            scene.press(from)
            scene.frame()
            // Carry the start thumb through the end thumb and well past it.
            repeat(20) { step ->
                scene.move(Offset(from.x + (to.x - from.x) * (step + 1) / 20f, from.y))
                scene.frame()
            }
            atTheTurn = range
            // Then come back, without lifting.
            repeat(40) { i ->
                scene.move(Offset(to.x - 2f * (i + 1), from.y))
                scene.frame()
            }
            scene.release(Offset(to.x - 80f, from.y))
            scene.frames(4)
        }

        assertTrue(
            atTheTurn.endInclusive > 6f + Slack,
            "the end thumb was not pushed at all: it sat at ${atTheTurn.endInclusive} " +
                "after the start thumb was dragged to ${atTheTurn.start}",
        )
        assertTrue(
            range.endInclusive >= atTheTurn.endInclusive - Slack,
            "the end thumb was pushed to ${atTheTurn.endInclusive} and then came back to " +
                "${range.endInclusive} as the start thumb retreated to ${range.start}. A " +
                "thumb that has been shoved stays shoved: it only ever moves further, and " +
                "following the other one home is what makes the pair look welded together.",
        )
        assertTrue(
            range.endInclusive - range.start > 1f,
            "the two came back to rest ${range.endInclusive - range.start} apart, so they " +
                "never separated",
        )
    }

    /**
     * With a `minDistance`, the last `gap` of track is unreachable — and the
     * drag accumulator was still climbing into it, so every pixel spent up
     * there had to be spent again on the way back before anything moved.
     */
    @Test
    fun reversingAtTheWallMovesTheThumbImmediately() {
        var range by mutableStateOf(2f..6f)
        var bounds = Rect.Zero
        var atTheWall = 0f
        val afterOneStep = mutableListOf<Float>()

        Scene(width = 700, height = 240) {
            Box(Modifier.fillMaxSize().background(Color.White).padding(20.dp)) {
                RangeSlider(
                    value = range,
                    onValueChange = { range = it },
                    valueRange = Range,
                    minDistance = 2f,
                    modifier = Modifier.reportBounds { bounds = it },
                )
            }
        }.use { scene ->
            scene.frames(3)
            val from = bounds.alongX(0.2f)
            val wall = bounds.alongX(0.98f)

            scene.press(from)
            scene.frame()
            repeat(20) { step ->
                scene.move(Offset(from.x + (wall.x - from.x) * (step + 1) / 20f, from.y))
                scene.frame()
            }
            atTheWall = range.start
            repeat(10) { i ->
                scene.move(Offset(wall.x - 4f * (i + 1), from.y))
                scene.frame()
                afterOneStep += range.start
            }
            scene.release(Offset(wall.x - 40f, from.y))
            scene.frames(4)
        }

        assertTrue(
            atTheWall > 7f,
            "the start thumb only reached $atTheWall against a wall at ${10f - 2f}",
        )
        val moved = afterOneStep.indexOfFirst { it < atTheWall - 0.02f }
        val dead = if (moved < 0) "all 40px" else "${(moved + 1) * 4}px"
        assertTrue(
            moved in 0..2,
            "the start thumb sat at $atTheWall for $dead of reverse travel " +
                "before it moved. With `minDistance = 2f` the top two units of the range " +
                "cannot be reached, and the accumulator was climbing into them anyway — so " +
                "the finger had to buy the same distance twice. Measured at 56px of dead " +
                "travel at `minDistance = 1f` and past 80px at 2f.",
        )
    }

    /**
     * A press picks a thumb; it does not move one. A tap still does.
     *
     * `Slider` brings its thumb to the finger on press, which is right with one
     * thumb and one answer. With two, a press between them dragged whichever was
     * nearer inwards — and a pixel either side of the midpoint picks a different
     * one, so a press that was about to become a drag sent the thumb you were
     * *not* aiming at off to meet your finger.
     */
    @Test
    fun aPressAloneMovesNothingAndATapStillMoves() {
        var pressed = 0f..0f
        var tapped = 0f..0f

        for (tap in listOf(false, true)) {
            var range by mutableStateOf(3f..5f)
            var bounds = Rect.Zero
            Scene(width = 700, height = 240) {
                Box(Modifier.fillMaxSize().background(Color.White).padding(20.dp)) {
                    RangeSlider(
                        value = range,
                        onValueChange = { range = it },
                        valueRange = Range,
                        modifier = Modifier.reportBounds { bounds = it },
                    )
                }
            }.use { scene ->
                scene.frames(3)
                val at = bounds.alongX(0.65f)
                scene.press(at)
                scene.frames(2)
                if (!tap) {
                    pressed = range
                } else {
                    scene.release(at)
                    scene.frames(4)
                    tapped = range
                }
            }
        }

        assertTrue(
            pressed.start == 3f && pressed.endInclusive == 5f,
            "a press that has not moved changed the range to $pressed. Until the finger " +
                "travels there is no way to tell a tap from the start of a drag, and " +
                "answering early is what sent a thumb the user was not aiming at across " +
                "the track — measured at 5.00 becoming 6.69 from a press alone.",
        )
        assertTrue(
            tapped.endInclusive > 5f + Slack,
            "a tap left the range at $tapped — the nearer thumb should still come to the " +
                "finger when the gesture turns out to have been a tap",
        )
    }

    private companion object {
        val Range = 0f..10f
        const val Slack = 0.05f
    }
}
