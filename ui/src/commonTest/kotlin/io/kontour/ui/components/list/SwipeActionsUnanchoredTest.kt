package io.kontour.ui.components.list

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.ScrollWheel
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kontour.ui.foundation.SystemIcons
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A row dragged before it has its anchors moves nothing until it has them,
 * and then follows the finger, rather than crashing.
 *
 * The anchors are set from an effect once the row has its width, which is the
 * frame after it first appears, and until then the row has no offset at all.
 * A finger already moving when a row scrolls into view lands in that frame, and
 * `dispatchRawDelta` read the missing offset and threw *"The offset was read
 * before being initialized"*. Reported from an Android app in November 2026,
 * fixed in 0.5.4.
 */
@OptIn(ExperimentalTestApi::class)
class SwipeActionsUnanchoredTest {

    private val delete = SwipeAction(label = "Delete", icon = SystemIcons.Default.close, onAction = {}, background = Color.Red)

    @Test
    fun aDragBeforeTheAnchorsAreSetDoesNotCrash() = runComposeUiTest {
        lateinit var state: SwipeActionsState
        mainClock.autoAdvance = false
        setContent {
            KontourTheme(reduceMotion = true) {
                OverlayHost {
                    state = rememberSwipeActionsState()
                    SwipeActions(modifier = Modifier.fillMaxWidth().height(72.dp), end = listOf(delete), state = state) {
                        Box(Modifier.fillMaxWidth().height(72.dp).testTag("row"))
                    }
                }
            }
        }
        onNodeWithTag("row").performTouchInput {
            swipe(Offset(width * 0.8f, centerY), Offset(width * 0.2f, centerY), durationMillis = 120)
        }
        mainClock.autoAdvance = true
        waitForIdle()
        // The frame without anchors moves nothing, and the row follows the
        // rest of the swipe from the next, which carries it open.
        assertEquals(SwipeValue.End, state.currentValue, "the row should follow the rest of the drag once it has anchors")
    }

    @Test
    fun aSidewaysScrollBeforeTheAnchorsAreSetDoesNotCrash() = runComposeUiTest {
        lateinit var state: SwipeActionsState
        mainClock.autoAdvance = false
        setContent {
            KontourTheme(reduceMotion = true) {
                OverlayHost {
                    state = rememberSwipeActionsState()
                    SwipeActions(modifier = Modifier.fillMaxWidth().height(72.dp), end = listOf(delete), state = state) {
                        Box(Modifier.fillMaxWidth().height(72.dp).testTag("row"))
                    }
                }
            }
        }
        onNodeWithTag("row").performMouseInput {
            moveTo(center)
            repeat(4) { scroll(3f, ScrollWheel.Horizontal) }
        }
        mainClock.autoAdvance = true
        waitForIdle()
        assertEquals(SwipeValue.Resting, state.currentValue, "a scroll the row could not follow should leave it at rest")
    }
}
