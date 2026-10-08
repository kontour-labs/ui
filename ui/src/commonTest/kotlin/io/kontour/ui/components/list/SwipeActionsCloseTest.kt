package io.kontour.ui.components.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kontour.ui.foundation.SystemIcons
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A row left showing its actions closes as soon as something else is used, as
 * a SwiftUI list's row does: asked for as *"in swipe actions, if you start
 * interacting with another component, it closes"*. It stayed open, its
 * actions out, while the reader went on to another row or another control.
 *
 * The press still does what it does: the other row's click, the button's
 * action. The open row only gets out of the way.
 */
@OptIn(ExperimentalTestApi::class)
class SwipeActionsCloseTest {

    private val delete = SwipeAction(label = "Delete", icon = SystemIcons.Close, onAction = {}, background = Color.Red)

    @Composable
    private fun Row(tag: String, state: SwipeActionsState, onClick: () -> Unit) {
        SwipeActions(modifier = Modifier.fillMaxWidth().height(72.dp), end = listOf(delete), state = state) {
            Box(Modifier.fillMaxWidth().height(72.dp).testTag(tag).clickable(onClick = onClick))
        }
    }

    @Test
    fun anOpenRowClosesWhenAnotherRowIsPressed() = runComposeUiTest {
        lateinit var first: SwipeActionsState
        val clicks = mutableListOf<String>()
        setContent {
            KontourTheme(reduceMotion = true) {
                OverlayHost {
                    first = rememberSwipeActionsState(SwipeValue.End)
                    Column {
                        Row("first", first) { clicks += "first" }
                        Row("second", rememberSwipeActionsState()) { clicks += "second" }
                    }
                }
            }
        }
        waitForIdle()
        assertEquals(SwipeValue.End, first.currentValue, "the first row did not start open")
        onNodeWithTag("second").performClick()
        waitForIdle()
        assertEquals(SwipeValue.Resting, first.currentValue, "the first row stayed open while the second was used")
        assertEquals(listOf("second"), clicks, "the second row's press should still be its own")
    }

    @Test
    fun anOpenRowClosesWhenAnotherControlIsPressed() = runComposeUiTest {
        lateinit var row: SwipeActionsState
        var pressed = 0
        setContent {
            KontourTheme(reduceMotion = true) {
                OverlayHost {
                    row = rememberSwipeActionsState(SwipeValue.End)
                    Column {
                        Row("row", row) {}
                        Box(Modifier.size(80.dp).testTag("button").clickable { pressed++ })
                    }
                }
            }
        }
        waitForIdle()
        onNodeWithTag("button").performClick()
        waitForIdle()
        assertEquals(SwipeValue.Resting, row.currentValue, "the row stayed open while a button beside it was used")
        assertEquals(1, pressed, "the button's press should still be its own")
    }

    /**
     * As it was reported, by finger: the first row swiped open, then a second
     * swiped the other way — the first stayed open until a third was swiped.
     * Its opening slide was still going when the second row was pressed.
     */
    @Test
    fun aRowStillSlidingOpenClosesWhenAnotherIsSwiped() = runComposeUiTest {
        val favourite = SwipeAction(label = "Favourite", icon = SystemIcons.Close, onAction = {}, background = Color.Green)
        val states = mutableListOf<SwipeActionsState>()
        setContent {
            KontourTheme(reduceMotion = false) {
                OverlayHost {
                    Column {
                        repeat(3) { i ->
                            val state = rememberSwipeActionsState()
                            if (states.size <= i) states += state
                            SwipeActions(modifier = Modifier.fillMaxWidth().height(72.dp), start = listOf(favourite), end = listOf(delete), state = state) {
                                Box(Modifier.fillMaxWidth().height(72.dp).testTag("row$i"))
                            }
                        }
                    }
                }
            }
        }
        waitForIdle()
        // The clock held, so the first row is still on its way open when the
        // second is taken: a few frames after letting go, not settled.
        mainClock.autoAdvance = false
        onNodeWithTag("row0").performTouchInput { swipe(centerLeft + Offset(20f, 0f), center + Offset(10f, 0f), durationMillis = 200) }
        repeat(3) { mainClock.advanceTimeByFrame() }
        assertEquals(SwipeValue.Resting, states[0].currentValue, "the first row had already settled, so this measured nothing")
        onNodeWithTag("row1").performTouchInput { swipe(centerRight - Offset(20f, 0f), center - Offset(10f, 0f), durationMillis = 200) }
        mainClock.autoAdvance = true
        waitForIdle()
        assertEquals(SwipeValue.End, states[1].currentValue, "the second row did not open")
        assertEquals(SwipeValue.Resting, states[0].currentValue, "the first row stayed open while the second was swiped")
    }

    /** The same, with the first row settled before the second is touched. */
    @Test
    fun aSettledRowClosesWhenAnotherIsSwiped() = runComposeUiTest {
        val favourite = SwipeAction(label = "Favourite", icon = SystemIcons.Close, onAction = {}, background = Color.Green)
        val states = mutableListOf<SwipeActionsState>()
        setContent {
            KontourTheme(reduceMotion = false) {
                OverlayHost {
                    Column {
                        repeat(2) { i ->
                            val state = rememberSwipeActionsState()
                            if (states.size <= i) states += state
                            SwipeActions(modifier = Modifier.fillMaxWidth().height(72.dp), start = listOf(favourite), end = listOf(delete), state = state) {
                                Box(Modifier.fillMaxWidth().height(72.dp).testTag("row$i"))
                            }
                        }
                    }
                }
            }
        }
        waitForIdle()
        onNodeWithTag("row0").performTouchInput { swipe(centerLeft + Offset(20f, 0f), center + Offset(10f, 0f), durationMillis = 200) }
        waitForIdle()
        assertEquals(SwipeValue.Start, states[0].currentValue, "the first row did not open")
        onNodeWithTag("row1").performTouchInput { swipe(centerRight - Offset(20f, 0f), center - Offset(10f, 0f), durationMillis = 200) }
        waitForIdle()
        assertEquals(SwipeValue.End, states[1].currentValue, "the second row did not open")
        assertEquals(SwipeValue.Resting, states[0].currentValue, "the first row stayed open while the second was swiped")
    }

    /** Between rows the rows see to it themselves, with no host round them. */
    @Test
    fun withoutAHostAnotherRowStillClosesIt() = runComposeUiTest {
        lateinit var first: SwipeActionsState
        setContent {
            KontourTheme(reduceMotion = true) {
                first = rememberSwipeActionsState(SwipeValue.End)
                Column {
                    Row("first", first) {}
                    Row("second", rememberSwipeActionsState()) {}
                }
            }
        }
        waitForIdle()
        onNodeWithTag("second").performClick()
        waitForIdle()
        assertEquals(SwipeValue.Resting, first.currentValue)
    }
}
