package io.kontour.ui.components.selection

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.down
import androidx.compose.ui.test.moveBy
import androidx.compose.ui.test.up
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import io.kontour.ui.theme.KontourTheme
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A drag lands where a tap on the same spot would, and a stepped slider moves at
 * all.
 *
 * Both were one bug. `Modifier.draggable` calls `onDelta` once per *pointer
 * event*; the slider computed `fraction + delta` where `fraction` came from the
 * hoisted value, which only refreshes once per *composition*. Every delta between
 * two frames was computed from the same stale base and overwritten by the next,
 * so only the last of each frame survived.
 *
 * On a stepped slider that was fatal rather than merely lossy: `snap` quantises
 * before the value reaches the caller, so the sub-step remainder was destroyed on
 * every event, and advancing one detent needed a single event carrying half a
 * step. Real events carry a few pixels.
 *
 * **The tap path is the control.** It is absolute — `offset.x / width` — and was
 * never broken, so asserting that a drag agrees with a tap to the same place
 * needs no geometry of its own and fails for exactly the reason the drag was
 * wrong. A screenshot can see none of it: one frame of a slider that travelled a
 * tenth as far as the finger looks like a slider at a different value.
 */
@OptIn(ExperimentalTestApi::class)
class SliderDragTest {

    @Test
    fun aDragLandsWhereATapWould() {
        var value by mutableFloatStateOf(0f)
        var fromTap = Float.NaN
        var fromDrag = Float.NaN

        runComposeUiTest {
            setContent {
                KontourTheme(reduceMotion = true) {
                    Slider(
                        value = value,
                        onValueChange = { value = it },
                        modifier = Modifier.testTag(Tag).width(Width.dp),
                    )
                }
            }

            // Ground truth, by the path that always worked.
            onNodeWithTag(Tag).performTouchInput { click(Offset(threeQuarters(), centerY)) }
            waitForIdle()
            fromTap = value

            // Back to the middle, then drag the rest of the way by hand.
            onNodeWithTag(Tag).performTouchInput { click(Offset(centerX, centerY)) }
            waitForIdle()

            // Many small moves inside one gesture, and deliberately *not*
            // `swipeRight`: that spaces its moves a frame apart, which is the
            // one condition under which the old code was correct. A real
            // pointer emits faster than the compositor recomposes, and that is
            // what has to be reproduced here.
            onNodeWithTag(Tag).performTouchInput {
                val travel = threeQuarters() - centerX
                down(Offset(centerX, centerY))
                repeat(Moves) { moveBy(Offset(travel / Moves, 0f), delayMillis = 0) }
                up()
            }
            waitForIdle()
            fromDrag = value
        }

        assertTrue(
            abs(fromDrag - fromTap) < Tolerance,
            "a drag to the three-quarter mark landed on $fromDrag where a tap on " +
                "the same spot lands on $fromTap — the deltas between frames are " +
                "being dropped rather than summed",
        )
    }

    @Test
    fun aSteppedSliderCanBeDraggedAtAll() {
        var value by mutableFloatStateOf(0f)
        var afterDrag = Float.NaN

        runComposeUiTest {
            setContent {
                KontourTheme(reduceMotion = true) {
                    Slider(
                        value = value,
                        onValueChange = { value = it },
                        modifier = Modifier.testTag(Tag).width(Width.dp),
                        // Detents at 0, ¼, ½, ¾, 1.
                        steps = 3,
                    )
                }
            }

            onNodeWithTag(Tag).performTouchInput {
                swipeRight(startX = left + inset(), endX = centerX)
            }
            waitForIdle()
            afterDrag = value
        }

        assertTrue(
            afterDrag > 0f,
            "a stepped slider did not move at all. One detent needs a single " +
                "pointer event carrying half a step, because the sub-step " +
                "remainder is thrown away between events",
        )
        assertTrue(
            abs(afterDrag - 0.5f) < Tolerance,
            "a stepped slider dragged to the middle landed on $afterDrag, not the " +
                "0.5 detent",
        )
    }

    private companion object {
        const val Tag = "slider"
        const val Width = 400

        /** Enough that dropping all but the last would be unmistakable. */
        const val Moves = 20

        /**
         * Comfortably inside a quarter-step, so passing cannot be an accident of
         * snapping, and loose enough for the thumb inset the tap and the drag
         * share.
         */
        const val Tolerance = 0.06f
    }
}

/** The track is inset by the thumb's radius at each end; gestures start inside it. */
private fun androidx.compose.ui.test.TouchInjectionScope.inset(): Float = 12.dp.toPx()

private fun androidx.compose.ui.test.TouchInjectionScope.threeQuarters(): Float =
    left + inset() + (right - left - inset() * 2f) * 0.75f
