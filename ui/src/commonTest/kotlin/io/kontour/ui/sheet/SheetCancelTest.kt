package io.kontour.ui.sheet

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A sheet nudged a notch at full height stays there, however the gesture ends:
 * taken away by the system — the notification shade, a focus change — or held
 * still before the finger lifts.
 *
 * Compose sends a pointer the system takes as an up already consumed, at the
 * time of its last move. Read as a release, the sheet was thrown at the speed of
 * the nudge — and the sheet's own tracker, asked for a speed where none was
 * reported, gave it the nudge's too — so a sheet moved a millimetre could close.
 */
@OptIn(ExperimentalTestApi::class)
class SheetCancelTest {

    private fun ComposeUiTest.sheet(): SheetState {
        var state: SheetState? = null
        setContent {
            KontourTheme(reduceMotion = true) {
                OverlayHost(Modifier.size(400.dp, 800.dp)) {
                    val sheet = rememberSheetState(
                        detents = listOf(SheetDetent.Hidden, SheetDetent.Half, SheetDetent.Expanded),
                        initialDetent = SheetDetent.Expanded,
                    )
                    state = sheet
                    ModalBottomSheet(visible = true, onDismissRequest = {}, state = sheet) {
                        Box(Modifier.fillMaxWidth().height(1200.dp).testTag("content"))
                    }
                }
            }
        }
        waitForIdle()
        return state!!
    }

    /** A quick nudge down, from the middle of the sheet, a little past the sheet's slop. */
    private fun ComposeUiTest.nudge(then: androidx.compose.ui.test.TouchInjectionScope.() -> Unit) {
        onNodeWithTag("content").performTouchInput {
            val from = Offset(width / 2f, 200f)
            down(from)
            repeat(4) {
                advanceEventTime(8)
                moveBy(Offset(0f, 15f))
            }
            then()
        }
        waitForIdle()
    }

    @Test
    fun aNudgeTakenAwayByTheSystemIsNotAThrow() = runComposeUiTest {
        val state = sheet()
        nudge { cancel() }
        assertEquals(SheetDetent.Expanded, state.currentDetent, "a nudge the system took away threw the sheet to ${state.currentDetent}")
    }

    /**
     * A long, fast pull the system takes away settles where the sheet is, not
     * where the pull's speed would have thrown it. Past the distance a nudge
     * stays inside, so only knowing it was a cancel keeps it from Half: seen
     * on a phone as a drag Android took, read as a release at 2,600px/s.
     */
    @Test
    fun aLongPullTakenAwayByTheSystemSettlesWhereItIs() = runComposeUiTest {
        val state = sheet()
        onNodeWithTag("content").performTouchInput {
            val from = Offset(width / 2f, 200f)
            down(from)
            // 150dp: past the 48 a throw has to travel, and nearer full height
            // than half, which is 400dp down this 800dp host.
            repeat(5) {
                advanceEventTime(8)
                moveBy(Offset(0f, 30f))
            }
            cancel()
        }
        waitForIdle()
        assertEquals(SheetDetent.Expanded, state.currentDetent, "a long pull the system took away threw the sheet to ${state.currentDetent}")
    }

    @Test
    fun aNudgeHeldStillBeforeLettingGoIsNotAThrow() = runComposeUiTest {
        val state = sheet()
        nudge {
            advanceEventTime(300)
            up()
        }
        assertEquals(SheetDetent.Expanded, state.currentDetent, "a nudge held still, then let go, threw the sheet to ${state.currentDetent}")
    }

    @Test
    fun aRealFlickStillThrowsIt() = runComposeUiTest {
        val state = sheet()
        onNodeWithTag("content").performTouchInput {
            val from = Offset(width / 2f, 200f)
            down(from)
            repeat(8) {
                advanceEventTime(8)
                moveBy(Offset(0f, 30f))
            }
            up()
        }
        waitForIdle()
        assertEquals(SheetDetent.Half, state.currentDetent, "a flick down from full height did not go a detent")
    }
}
