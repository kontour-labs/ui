package io.kontour.ui.interaction

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

/**
 * A pointer passing over something plays no haptic; a drag with the button
 * held, and a finger, still do. See [HoverWatch].
 *
 * Reported 2026-10-21 for 0.5.0 from a MacBook Pro's trackpad, which plays the
 * library's haptics under the finger moving the pointer: *"not really a fan of
 * how hovering things on desktop causes the mac trackpad to rumble"*. What did it
 * was a chart, which chooses the place under a resting pointer and ticks for it
 * from its hover as it does from a drag. The strip here does the same — ten
 * places, a [DetentTicker] told the one under any pointer that moves over it —
 * and changes nothing of its own for the rule: it is the theme that knows the
 * event was a hover, so a chart of `:ui-charts` is quiet without a change either.
 */
@OptIn(ExperimentalTestApi::class)
class HoverHapticsTest {

    private val felt = mutableListOf<FeedbackIntent>()

    @Test
    fun aPointerPassingOverThePlacesPlaysNothing() = runComposeUiTest {
        show()
        onNodeWithTag("places").performMouseInput {
            for (step in 0..40) moveTo(Offset(width * step / 40f, centerY))
        }
        waitForIdle()
        assertEquals(emptyList(), felt, "a pointer passing over ten places, with no button down, played $felt")
    }

    @Test
    fun theButtonHeldAndDraggedAcrossThemStillTicksAPlace() = runComposeUiTest {
        show()
        onNodeWithTag("places").performMouseInput {
            moveTo(Offset(1f, centerY))
            press()
            for (step in 1..40) moveTo(Offset(width * step / 40f - 1f, centerY))
            release()
        }
        waitForIdle()
        assertEquals(9, felt.count { it == FeedbackIntent.Tick }, "dragged with the button held across ten places: $felt")
    }

    @Test
    fun aFingerDraggedAcrossThemStillTicksAPlace() = runComposeUiTest {
        show()
        onNodeWithTag("places").performTouchInput {
            down(Offset(1f, centerY))
            for (step in 1..40) moveTo(Offset(width * step / 40f - 1f, centerY))
            up()
        }
        waitForIdle()
        assertEquals(9, felt.count { it == FeedbackIntent.Tick }, "a finger dragged across ten places: $felt")
    }

    /** A pointer's hover across the places, and then a drag: only the drag's crossings are felt. */
    @Test
    fun aDragAfterAHoverTicksForWhereItGoesNotWhereTheHoverWent() = runComposeUiTest {
        show()
        onNodeWithTag("places").performMouseInput {
            for (step in 0..20) moveTo(Offset(width * step / 40f, centerY))
            // Resting on place 5; pressed there, and dragged on to the end.
            press()
            for (step in 21..40) moveTo(Offset(width * step / 40f - 1f, centerY))
            release()
        }
        waitForIdle()
        assertEquals(4, felt.count { it == FeedbackIntent.Tick }, "from place 5 to place 9 with the button held: $felt")
    }

    private fun ComposeUiTest.show() {
        // A clock that moves a second on every report, so the rate floor never
        // drops one and the count is the crossings.
        val clock = TestTimeSource()
        val floor = FeedbackFloor(clock)
        setContent {
            KontourTheme {
                CompositionLocalProvider(
                    LocalFeedback provides FeedbackDispatcher { felt += it; clock += 1.seconds },
                    LocalFeedbackFloor provides floor,
                ) {
                    Places()
                }
            }
        }
        waitForIdle()
    }

    /** Ten places in a row, ticking for the one under a pointer as a chart does: hovering or not. */
    @Composable
    private fun Places() {
        val ticker = rememberDetentTicker()
        Box(
            Modifier.size(400.dp, 40.dp).testTag("places").pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: continue
                        when (event.type) {
                            PointerEventType.Enter, PointerEventType.Move, PointerEventType.Press ->
                                ticker.at((change.position.x / size.width * 10).toInt().coerceIn(0, 9))
                            else -> Unit
                        }
                    }
                }
            },
        )
    }
}
