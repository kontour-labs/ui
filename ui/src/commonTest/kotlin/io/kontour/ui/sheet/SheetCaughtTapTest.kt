package io.kontour.ui.sheet

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.action.Button
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A button in a sheet answers a tap while the sheet is moving.
 *
 * A finger laid on a moving sheet catches it — holds it where it is — and that
 * is right: it is what lets a sheet thrown the wrong way be taken back. But the
 * catch took the whole gesture, every change consumed before anything inside the
 * sheet saw it, the lift included, and a control that sees its press lifted as
 * consumed takes it as a cancel. A sheet is moving while it opens, while it
 * lands and while it is thrown, so a button tapped in any of those did nothing.
 *
 * The catch is the press's; a press that does not move is the button's too.
 */
@OptIn(ExperimentalTestApi::class)
class SheetCaughtTapTest {

    @Test
    fun aButtonTappedWhileTheSheetOpensIsPressed() {
        runComposeUiTest {
            var visible by mutableStateOf(false)
            var pressed = 0
            lateinit var sheet: SheetState
            setContent {
                // Full motion: under reduced motion the sheet does not travel.
                KontourTheme(reduceMotion = false) {
                    OverlayHost(Modifier.fillMaxSize()) {
                        sheet = rememberSheetState(
                            detents = listOf(SheetDetent.Hidden, SheetDetent.Expanded),
                            initialDetent = SheetDetent.Hidden,
                        )
                        ModalBottomSheet(visible = visible, onDismissRequest = { visible = false }, state = sheet) {
                            Column {
                                Button(onClick = { pressed++ }) { +"Choose" }
                                Box(Modifier.fillMaxWidth().height(300.dp))
                            }
                        }
                    }
                }
            }
            waitForIdle()

            mainClock.autoAdvance = false
            visible = true
            // Past half way, so the press — which holds the sheet, and lets it
            // settle at the nearest detent once lifted — leaves it open.
            var frames = 0
            while (!(sheet.isMoving && sheet.visibleFraction > 0.6f) && frames < OpenFrames) {
                mainClock.advanceTimeByFrame()
                frames++
            }
            assertTrue(
                sheet.isMoving && sheet.visibleFraction > 0.6f,
                "the sheet was never part of the way open and moving, so nothing was tapped mid-open",
            )

            onNodeWithText("Choose").performTouchInput { down(center) }
            mainClock.advanceTimeByFrame()
            onNodeWithText("Choose").performTouchInput { up() }
            repeat(4) { mainClock.advanceTimeByFrame() }

            assertEquals(
                1,
                pressed,
                "a button tapped as its sheet opened did nothing: the sheet's catch took the lift as well as the press",
            )
        }
    }

    private companion object {
        const val OpenFrames = 40
    }
}
