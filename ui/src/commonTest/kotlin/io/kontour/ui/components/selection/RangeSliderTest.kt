package io.kontour.ui.components.selection

import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.down
import androidx.compose.ui.test.moveBy
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.up
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The two things a second thumb adds, and both of them can fail silently.
 *
 * The drag accumulator is inherited from `Slider` and covered by
 * `SliderDragTest`; what is new here is deciding *which* thumb a gesture moves,
 * and keeping the range from inverting once it has been decided.
 */
@OptIn(ExperimentalTestApi::class)
class RangeSliderTest {

    /**
     * A range that starts closed can be opened in either direction.
     *
     * `0.5f..0.5f` is what "no filter set yet" looks like, and both thumbs are
     * then at the same pixel. Picking by distance alone gives an arbitrary
     * winner — and whichever one it is, the clamp then pins it: grab the start
     * thumb and drag right and it stops at the end thumb, which has not moved.
     * The range can never open, and it looks like a dead control rather than a
     * wrong one.
     *
     * Reverting the direction tie-break in `onDragStarted` fails this.
     */
    @Test
    fun coincidentThumbsOpenInTheDirectionDragged() = runComposeUiTest {
        var range = 0.5f..0.5f
        setContent {
            KontourTheme {
                RangeSlider(
                    value = range,
                    onValueChange = { range = it },
                    modifier = Modifier.width(200.dp).testTag("range"),
                )
            }
        }

        onNodeWithTag("range").performTouchInput {
            down(Offset(centerX, centerY))
            repeat(10) { moveBy(Offset(width * 0.03f, 0f)) }
            up()
        }
        waitForIdle()

        assertTrue(
            range.endInclusive > 0.5f,
            "dragging right from a closed range should move the end thumb, " +
                "but the range is $range",
        )
        assertEquals(0.5f, range.start, 0.001f, "the start thumb should not have moved")
    }

    @Test
    fun coincidentThumbsOpenLeftWhenDraggedLeft() = runComposeUiTest {
        var range = 0.5f..0.5f
        setContent {
            KontourTheme {
                RangeSlider(
                    value = range,
                    onValueChange = { range = it },
                    modifier = Modifier.width(200.dp).testTag("range"),
                )
            }
        }

        onNodeWithTag("range").performTouchInput {
            down(Offset(centerX, centerY))
            repeat(10) { moveBy(Offset(-width * 0.03f, 0f)) }
            up()
        }
        waitForIdle()

        assertTrue(
            range.start < 0.5f,
            "dragging left from a closed range should move the start thumb, " +
                "but the range is $range",
        )
        assertEquals(0.5f, range.endInclusive, 0.001f, "the end thumb should not have moved")
    }

    /**
     * The start thumb pushes the end thumb rather than passing it, or stopping.
     *
     * This used to assert that the end thumb *did not move* — the rule was a
     * clamp, and the docs stated it as a virtue. Blocking turned out to be the
     * wrong half of the right idea: what must never happen is the range
     * **inverting**, because `0.8f..0.2f` has a negative width and every
     * consumer of it either draws a negative-width band or throws. Stopping the
     * drag dead is one way to prevent that and pushing the neighbour along is
     * another, and only one of them keeps the thumb under the finger.
     *
     * So the invariant is unchanged and still the load-bearing assertion here;
     * what changed is the thing that enforces it. Reverting the arithmetic in
     * `emit` fails this on the second assertion rather than the first.
     */
    @Test
    fun aThumbPushesTheOtherRatherThanPassingIt() = runComposeUiTest {
        var range = 0.2f..0.4f
        setContent {
            KontourTheme {
                RangeSlider(
                    value = range,
                    onValueChange = { range = it },
                    modifier = Modifier.width(200.dp).testTag("range"),
                )
            }
        }

        // Grab the start thumb — nearest the left — and haul it well past the end.
        onNodeWithTag("range").performTouchInput {
            down(Offset(width * 0.2f, centerY))
            repeat(15) { moveBy(Offset(width * 0.05f, 0f)) }
            up()
        }
        waitForIdle()

        assertTrue(
            range.start <= range.endInclusive,
            "the range inverted: $range",
        )
        assertTrue(
            range.endInclusive > 0.4f,
            "the start thumb was hauled well past the end thumb and the end " +
                "thumb stayed at ${range.endInclusive} — it should have been " +
                "shoved along in front of it",
        )
        assertTrue(
            range.endInclusive <= 1f,
            "the end thumb was pushed past the end of its own track, to " +
                "${range.endInclusive}",
        )
    }

    /**
     * A stepped `5f..5f` range takes a tap and a key without throwing.
     *
     * The detent tick divided by the width of the range, and `roundToInt`
     * throws on the `NaN` — from a tap, from an arrow key and from
     * `setProgress`, on a range `require` lets through.
     */
    @Test
    fun aZeroWidthSteppedRangeTakesATapAndAKey() = runComposeUiTest {
        var range = 5f..5f
        setContent {
            KontourTheme(reduceMotion = true) {
                RangeSlider(
                    value = range,
                    onValueChange = { range = it },
                    modifier = Modifier.width(200.dp).testTag("range"),
                    valueRange = 5f..5f,
                    steps = 3,
                    startContentDescription = "From",
                    endContentDescription = "To",
                )
            }
        }

        onNodeWithTag("range").performTouchInput { click(Offset(centerX, centerY)) }
        waitForIdle()
        assertEquals(5f..5f, range, "a tap moved a zero-width range")

        onNodeWithContentDescription("From").requestFocus()
        onNodeWithContentDescription("From").performKeyInput { pressKey(Key.DirectionRight) }
        waitForIdle()
        assertEquals(5f..5f, range, "an arrow key moved a zero-width range")
    }

    /**
     * A key builds on the caller's range, not on the last one a key emitted.
     *
     * The caller here takes nothing: it holds `0..10` throughout, as one that
     * had reset or clamped in between would. The first key moves the end thumb
     * to 9.9 and that is reported; the second moves the *start* thumb, and the
     * end it reports has to be the caller's 10 — it was 9.9, the end thumb
     * jumping back to where a press the caller refused had left it.
     */
    @Test
    fun aKeyBuildsOnTheCallersRangeNotTheLastOneEmitted() = runComposeUiTest {
        val reported = mutableListOf<ClosedFloatingPointRange<Float>>()
        setContent {
            KontourTheme(reduceMotion = true) {
                RangeSlider(
                    value = 0f..10f,
                    onValueChange = { reported += it },
                    modifier = Modifier.width(200.dp).testTag("range"),
                    valueRange = 0f..10f,
                    startContentDescription = "From",
                    endContentDescription = "To",
                )
            }
        }

        onNodeWithContentDescription("To").requestFocus()
        onNodeWithContentDescription("To").performKeyInput { pressKey(Key.DirectionLeft) }
        waitForIdle()
        assertEquals(9.9f, reported.last().endInclusive, 0.001f, "Left on the end thumb, reported ${reported.last()}")

        onNodeWithContentDescription("From").requestFocus()
        onNodeWithContentDescription("From").performKeyInput { pressKey(Key.DirectionRight) }
        waitForIdle()
        val last = reported.last()
        assertEquals(0.1f, last.start, 0.001f, "Right on the start thumb, reported $last")
        assertEquals(
            10f, last.endInclusive, 0.001f,
            "the end thumb came back as ${last.endInclusive}: the key built on the range the " +
                "previous key emitted, not on the 0..10 the caller holds",
        )
    }

    /**
     * A minimum that is a whole number of steps is that many steps, not one more.
     *
     * `0.3f / 0.01f` is 30.000002, and rounding that up onto the grid made the
     * minimum 31 steps, so the start thumb's ceiling on a `0f..1f` range was
     * 0.69 rather than 0.7.
     */
    @Test
    fun aWholeStepMinimumIsNotRoundedUpAStep() = runComposeUiTest {
        setContent {
            KontourTheme(reduceMotion = true) {
                RangeSlider(
                    value = 0.2f..0.8f,
                    onValueChange = {},
                    modifier = Modifier.width(200.dp),
                    minDistance = 0.3f,
                    steps = 99,
                    startContentDescription = "From",
                    endContentDescription = "To",
                )
            }
        }

        val info = onNodeWithContentDescription("From").fetchSemanticsNode()
            .config[SemanticsProperties.ProgressBarRangeInfo]
        assertEquals(0.7f, info.range.endInclusive, 0.001f, "the start thumb's ceiling with a 0.3 minimum")
    }

    /** A tap at the far end of the track is the end of the range, exactly — see `SliderDragTest`. */
    @Test
    fun aTapAtTheEndReportsTheEndExactly() = runComposeUiTest {
        var range = 0.3f..1.0f
        setContent {
            KontourTheme(reduceMotion = true) {
                RangeSlider(
                    value = range,
                    onValueChange = { range = it },
                    modifier = Modifier.width(200.dp).testTag("range"),
                    valueRange = 0.3f..1.9f,
                )
            }
        }

        onNodeWithTag("range").performTouchInput { click(Offset(right - 1f, centerY)) }
        waitForIdle()
        assertEquals(1.9f, range.endInclusive, "a tap at the end of 0.3..1.9 reported $range")
    }
}
