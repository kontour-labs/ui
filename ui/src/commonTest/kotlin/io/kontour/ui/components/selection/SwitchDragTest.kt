package io.kontour.ui.components.selection

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.down
import androidx.compose.ui.test.moveBy
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.up
import androidx.compose.ui.test.v2.runComposeUiTest
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A thumb dragged across its track changes the switch.
 *
 * The reported defect, and one nothing covered: the haptics suite drags a switch
 * and then asserts `checked || !checked`, which is true of every boolean.
 */
@OptIn(ExperimentalTestApi::class)
class SwitchDragTest {

    /** Drags from one end of the track to the other and reports what happened. */
    private fun drag(from: Boolean, toTheRight: Boolean): List<Boolean> {
        var checked by mutableStateOf(from)
        val reported = mutableListOf<Boolean>()

        runComposeUiTest {
            setContent {
                KontourTheme(reduceMotion = true) {
                    Switch(
                        checked = checked,
                        onCheckedChange = {
                            reported += it
                            checked = it
                        },
                        modifier = Modifier.testTag("switch"),
                    )
                }
            }

            onNodeWithTag("switch").performTouchInput {
                val start = if (toTheRight) width * 0.2f else width * 0.8f
                down(Offset(start, height / 2f))
                // Several small moves, the way a finger arrives.
                val step = (if (toTheRight) 1f else -1f) * width * 0.12f
                repeat(6) { moveBy(Offset(step, 0f)) }
                up()
            }
            waitForIdle()
        }
        return reported
    }

    @Test
    fun draggingRightTurnsItOn() {
        val reported = drag(from = false, toTheRight = true)
        assertTrue(
            reported.isNotEmpty(),
            "dragging the thumb from one end of the track to the other reported " +
                "nothing at all — the switch did not change state.",
        )
        assertEquals(
            true, reported.last(),
            "a drag to the right left the switch at ${reported.last()}: $reported",
        )
    }

    @Test
    fun draggingLeftTurnsItOff() {
        val reported = drag(from = true, toTheRight = false)
        assertTrue(
            reported.isNotEmpty(),
            "dragging the thumb back across the track reported nothing at all.",
        )
        assertEquals(
            false, reported.last(),
            "a drag to the left left the switch at ${reported.last()}: $reported",
        )
    }

    @Test
    fun aDragReportsOnceRatherThanTwice() {
        val reported = drag(from = false, toTheRight = true)
        assertEquals(
            1, reported.size,
            "one gesture reported ${reported.size} changes: $reported. A drag that " +
                "is also arbitrated as a tap toggles twice and ends where it " +
                "started, which is the shape of the report.",
        )
    }

    /**
     * The state changes under the finger, not when it lifts.
     *
     * This is the behaviour the report was about. A switch that reports on
     * release looks, for the whole length of the gesture, like a switch that is
     * not answering the drag — and it is the wrong model besides: a physical
     * toggle goes over at the midpoint and is over from then on.
     */
    @Test
    fun theCrossingCommits() {
        var checked by mutableStateOf(false)
        val reported = mutableListOf<Boolean>()
        var beforeRelease = emptyList<Boolean>()

        runComposeUiTest {
            setContent {
                KontourTheme(reduceMotion = true) {
                    Switch(
                        checked = checked,
                        onCheckedChange = {
                            reported += it
                            checked = it
                        },
                        modifier = Modifier.testTag("switch"),
                    )
                }
            }

            onNodeWithTag("switch").performTouchInput {
                down(Offset(width * 0.2f, height / 2f))
                repeat(6) { moveBy(Offset(width * 0.12f, 0f)) }
            }
            waitForIdle()
            beforeRelease = reported.toList()

            onNodeWithTag("switch").performTouchInput { up() }
            waitForIdle()
        }

        assertEquals(
            listOf(true), beforeRelease,
            "the finger had crossed the midpoint and the switch had reported " +
                "$beforeRelease. It used to report from `onDragStopped`, so " +
                "nothing happened until the finger lifted.",
        )
        assertEquals(
            listOf(true), reported,
            "lifting the finger reported a second time: $reported. The crossing " +
                "is the commit and the release has nothing left to say.",
        )
    }

    /**
     * And inside a row, which is where most switches actually are.
     *
     * A `SelectionRow` hands its switch `onCheckedChange = null` and keeps the
     * tap for itself, so the drag has to reach `LocalRowToggle` instead. That
     * path had no test at all.
     */
    @Test
    fun aDragInsideARowReachesTheRowsToggle() {
        var checked by mutableStateOf(false)
        val reported = mutableListOf<Boolean>()

        runComposeUiTest {
            setContent {
                KontourTheme(reduceMotion = true) {
                    SelectionRow(
                        selected = checked,
                        onSelectedChange = {
                            reported += it
                            checked = it
                        },
                        role = Role.Switch,
                    ) {
                        +"Delays"
                        trailing {
                            Switch(
                                checked = checked,
                                onCheckedChange = null,
                                modifier = Modifier.testTag("switch"),
                            )
                        }
                    }
                }
            }

            // Unmerged: the row merges its children's semantics, so the switch
            // is not a node of its own in the merged tree. It is still a
            // separate layout node and still the thing the finger lands on.
            onNodeWithTag("switch", useUnmergedTree = true).performTouchInput {
                down(Offset(width * 0.2f, height / 2f))
                repeat(6) { moveBy(Offset(width * 0.12f, 0f)) }
                up()
            }
            waitForIdle()
        }

        assertEquals(
            listOf(true), reported,
            "dragging a switch inside a `SelectionRow` reported $reported. The " +
                "row owns the tap, so the drag is the only thing the switch " +
                "itself still answers, and it goes through `LocalRowToggle`.",
        )
    }
}
