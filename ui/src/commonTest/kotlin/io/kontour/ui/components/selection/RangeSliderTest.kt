package io.kontour.ui.components.selection

import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.down
import androidx.compose.ui.test.moveBy
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.up
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
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
     * The start thumb stops at the end thumb rather than passing it.
     *
     * Without the clamp in `emit` the range inverts, and `0.8f..0.2f` is a range
     * whose `endInclusive < start` — every consumer of it then draws a
     * negative-width band, or throws. Reverting either `coerceAtMost` /
     * `coerceAtLeast` fails this.
     */
    @Test
    fun aThumbNeverPassesTheOther() = runComposeUiTest {
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
        assertEquals(
            0.4f,
            range.endInclusive,
            0.001f,
            "the end thumb should have stayed where it was",
        )
    }
}
