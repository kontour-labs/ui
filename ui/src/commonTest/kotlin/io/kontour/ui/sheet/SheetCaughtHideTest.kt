package io.kontour.ui.sheet

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.KontourTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A finger on a sheet that code is sending away catches it, and the code that
 * sent it is told by [SheetState.animateTo] throwing.
 *
 * Both halves are what an app has to plan for. Anyways' detail sheet keeps
 * the pages it was showing until it is out of sight, and hid itself with
 * `animateTo(Hidden)` inside a `collect`: a second tap of a close button, landing
 * on the sheet as it went down, caught it, the throw ended the collect, and the
 * sheet settled at its lowest detent over pages already closed, with nothing
 * left to send it away. Reported from iPhones, November 2026, as a sheet that
 * back, close and the edge swipe could not close. The fix was in the app: a
 * watcher that goes on after its move is taken from it.
 */
@OptIn(ExperimentalTestApi::class)
class SheetCaughtHideTest {

    @Test
    fun aPressOnAHidingSheetCatchesItAndTheHideThrows() {
        runComposeUiTest {
            lateinit var sheet: SheetState
            lateinit var scope: CoroutineScope
            setContent {
                // Full motion: under reduced motion a hide fades where it is.
                KontourTheme(reduceMotion = false) {
                    OverlayHost(Modifier.fillMaxSize()) {
                        scope = rememberCoroutineScope()
                        sheet = rememberSheetState(
                            detents = listOf(SheetDetent.Hidden, SheetDetent.Half, SheetDetent.Expanded),
                            initialDetent = SheetDetent.Half,
                        )
                        BottomSheet(state = sheet, presentation = SheetPresentation.Edge) {
                            Box(Modifier.fillMaxSize().testTag("content"))
                        }
                    }
                }
            }
            waitForIdle()
            assertEquals(SheetDetent.Half, sheet.currentDetent)

            mainClock.autoAdvance = false
            var thrown = false
            var arrived = false
            scope.launch {
                try {
                    sheet.animateTo(SheetDetent.Hidden)
                    arrived = true
                } catch (_: CancellationException) {
                    thrown = true
                }
            }
            // A few frames in: on its way, with most of it still up.
            repeat(3) { mainClock.advanceTimeByFrame() }
            assertTrue(sheet.isMoving, "the hide never set off")

            // A tap, as a second press of a close button is.
            onNodeWithTag("content").performTouchInput { down(center) }
            mainClock.advanceTimeByFrame()
            onNodeWithTag("content").performTouchInput { up() }
            mainClock.autoAdvance = true
            waitForIdle()

            assertTrue(thrown && !arrived, "a hide caught by a finger did not throw to the code that asked for it")
            assertEquals(
                SheetDetent.Half,
                sheet.currentDetent,
                "a hide caught near where it started did not settle back at the detent it left",
            )
        }
    }

    @Test
    fun aWatcherThatOutlivesTheCatchStillHidesTheSheet() {
        runComposeUiTest {
            lateinit var sheet: SheetState
            lateinit var scope: CoroutineScope
            setContent {
                KontourTheme(reduceMotion = false) {
                    OverlayHost(Modifier.fillMaxSize()) {
                        scope = rememberCoroutineScope()
                        sheet = rememberSheetState(
                            detents = listOf(SheetDetent.Hidden, SheetDetent.Half, SheetDetent.Expanded),
                            initialDetent = SheetDetent.Half,
                        )
                        BottomSheet(state = sheet, presentation = SheetPresentation.Edge) {
                            Box(Modifier.fillMaxSize().testTag("content"))
                        }
                    }
                }
            }
            waitForIdle()

            mainClock.autoAdvance = false
            // What the app's watcher does: whenever the sheet is at rest and up
            // with nobody's finger on it, send it away, and go on if that move
            // is taken from it.
            scope.launch {
                androidx.compose.runtime.snapshotFlow {
                    sheet.currentDetent != SheetDetent.Hidden && !sheet.isMoving && !sheet.draggedByHand
                }.collect { up ->
                    if (!up) return@collect
                    try {
                        sheet.animateTo(SheetDetent.Hidden)
                    } catch (interrupted: CancellationException) {
                        currentCoroutineContext().ensureActive()
                    }
                }
            }
            repeat(3) { mainClock.advanceTimeByFrame() }
            assertTrue(sheet.isMoving, "the hide never set off")

            onNodeWithTag("content").performTouchInput { down(center) }
            mainClock.advanceTimeByFrame()
            onNodeWithTag("content").performTouchInput { up() }
            mainClock.autoAdvance = true
            waitForIdle()

            assertEquals(SheetDetent.Hidden, sheet.currentDetent, "the sheet stayed up after its hide was caught")
        }
    }
}
