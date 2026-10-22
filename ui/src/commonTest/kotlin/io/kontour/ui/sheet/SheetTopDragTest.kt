package io.kontour.ui.sheet

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A full-height sheet pulled down from anywhere on it follows the finger until
 * the finger lets go — the top of it, just under the status bar, included.
 *
 * Reported from Android: *"when you try to drag full-screen bottom sheets down
 * from just above the safe area, it lets go and just snaps back up the top"*.
 * The sheet followed for a moment, no notification shade came down, and then
 * it sprang back to full height with the finger still on the glass.
 *
 * **It was the phone.** Logged on it (2026-10-19), every drag that let go
 * ended in a cancel from Android, an up already consumed at the time of the
 * last move, and every one began inside the system gestures' top inset, 190px
 * there against a 161px status bar; a drag begun below it followed all the
 * way. Instagram's sheets let go the same way on the same phone. The system
 * takes a downward swipe that starts in that band once it has gone about
 * twenty dp, and no app keeps a pointer the system has taken.
 *
 * So what this holds is the half that is the sheet's: given the pointer, it
 * follows to the end, from the top as from anywhere. Each case drives the
 * gesture a frame at a time, so the sheet lays itself out between moves as it
 * does on a phone, and says frame by frame where the sheet was and what it
 * thought the finger was doing.
 */
@OptIn(ExperimentalTestApi::class)
class SheetTopDragTest {

    private lateinit var state: SheetState

    private fun ComposeUiTest.sheet() {
        setContent {
            KontourTheme(reduceMotion = false) {
                OverlayHost(Modifier.size(400.dp, 800.dp)) {
                    val sheet = rememberSheetState(
                        detents = listOf(SheetDetent.Hidden, SheetDetent.Half, SheetDetent.Full),
                        initialDetent = SheetDetent.Full,
                    )
                    state = sheet
                    ModalBottomSheet(
                        visible = true,
                        onDismissRequest = {},
                        state = sheet,
                        // An Android phone's status bar.
                        windowInsets = WindowInsets(top = StatusBar),
                    ) { _ ->
                        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                            Box(Modifier.fillMaxWidth().height(2000.dp))
                        }
                    }
                }
            }
        }
        waitForIdle()
    }

    /**
     * Down at [from] down the window, then [Steps] moves of [Step] each, a frame
     * apiece: fails, with every frame, if the sheet stopped following before the
     * finger lifted.
     */
    private fun ComposeUiTest.pullFrom(from: Dp) {
        sheet()
        val top = state.offset
        val start = with(density) { from.toPx() }
        val step = with(density) { Step.toPx() }
        val seen = mutableListOf<String>()
        mainClock.autoAdvance = false
        onRoot().performTouchInput { down(Offset(width / 2f, start)) }
        mainClock.advanceTimeByFrame()
        var last = state.offset
        var fellBack = false
        repeat(Steps) { i ->
            onRoot().performTouchInput { moveBy(Offset(0f, step)) }
            mainClock.advanceTimeByFrame()
            val now = state.offset
            if (now < last - 0.5f) fellBack = true
            seen += "move ${i + 1}: finger ${step * (i + 1)} down, sheet ${now - top} down, " +
                "pressed ${state.finger.pressed}, cancelled ${state.finger.cancelled}, moving ${state.isMoving}"
            last = now
        }
        val travelled = state.offset - top
        onRoot().performTouchInput { up() }
        mainClock.autoAdvance = true
        waitForIdle()
        val story = seen.joinToString("\n")
        assertTrue(
            !fellBack && travelled > step * Steps / 2f && state.finger.cancelled.not(),
            "pulled from ${from.value}dp down the window, the sheet stopped following the finger " +
                "($travelled of ${step * Steps} px):\n$story",
        )
    }

    @Test
    fun pulledFromUnderTheStatusBarItFollows() = runComposeUiTest { pullFrom(StatusBar / 2) }

    @Test
    fun pulledByItsHandleJustBelowTheBarItFollows() = runComposeUiTest { pullFrom(StatusBar + 12.dp) }

    @Test
    fun pulledFromHalfWayDownItFollows() = runComposeUiTest { pullFrom(300.dp) }

    private companion object {
        val StatusBar = 24.dp
        val Step = 12.dp
        const val Steps = 20
    }
}
