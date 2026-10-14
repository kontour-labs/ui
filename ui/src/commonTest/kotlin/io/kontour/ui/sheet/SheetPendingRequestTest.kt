package io.kontour.ui.sheet

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kontour.ui.theme.KontourTheme
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A request made before the sheet has been laid out arrives as it was asked for.
 *
 * Before the first layout there are no anchors, so a move asked for then waits
 * and is delivered once there are. Two things went wrong on the way:
 *
 * - **A detent the anchors dropped.** Two detents that resolve to one offset keep
 *   one anchor between them, and asked for by the other's name the sheet had
 *   nowhere to go. `expand()` on a sheet whose only content is its peek anchor
 *   asks for `Expanded`, which the peek got to first: the sheet stayed at hidden
 *   while it said it was open — for a modal, a scrim over nothing.
 * - **A snap, delivered as a slide.** `snapTo` is for restoring a sheet where it
 *   was left, and the commonest place to call it is a `LaunchedEffect` that runs
 *   before the first layout; the wait delivered every request by animating.
 */
@OptIn(ExperimentalTestApi::class)
class SheetPendingRequestTest {

    @Test
    fun anExpandAskedForBeforeLayoutOpensASheetWhoseExpandedIsItsPeek() {
        runComposeUiTest {
            val state = SheetState(
                detents = listOf(SheetDetent.Hidden, SheetDetent.peek(140.dp), SheetDetent.Expanded),
                initialDetent = SheetDetent.Hidden,
                confirmDetentChange = { true },
            )
            // Before there is anything to lay out: the request has to wait.
            ask { state.expand() }

            setContent {
                KontourTheme(reduceMotion = true) {
                    Box(Modifier.requiredSize(360.dp, 800.dp)) {
                        BottomSheet(state) {
                            // The header is all there is, so `Expanded` — the
                            // content's height — is where the peek already is.
                            SheetHeader(Modifier.sheetPeekAnchor(), onClose = null) { +"Perth Underground" }
                        }
                    }
                }
            }
            waitForIdle()

            assertTrue(
                state.visibleHeight > 0f,
                "the sheet was asked to expand before it was laid out and shows ${state.visibleHeight}px; " +
                    "it is at ${state.currentDetent} with its offset at ${state.offset}",
            )
            assertFalse(state.isMoving, "the sheet never came to rest")
        }
    }

    @Test
    fun aSnapAskedForBeforeLayoutArrivesWithoutTravelling() {
        runComposeUiTest {
            val state = SheetState(
                detents = listOf(SheetDetent.Hidden, SheetDetent.Half, SheetDetent.Expanded),
                initialDetent = SheetDetent.Hidden,
                confirmDetentChange = { true },
            )
            ask { state.snapTo(SheetDetent.Half) }

            mainClock.autoAdvance = false
            setContent {
                // Full motion: the slide is what must not happen.
                KontourTheme(reduceMotion = false) {
                    Box(Modifier.requiredSize(360.dp, 800.dp)) {
                        BottomSheet(state) {
                            Box(Modifier.requiredSize(360.dp, 700.dp))
                        }
                    }
                }
            }
            repeat(2) { mainClock.advanceTimeByFrame() }

            assertFalse(state.isMoving, "a snap asked for before layout was delivered as an animation")
            assertEquals(SheetDetent.Half, state.currentDetent, "the snap did not arrive at Half")
            assertEquals(
                state.offsetOf(SheetDetent.Half),
                state.offset,
                0.5f,
                "two frames in, a snap to Half should already be at Half's offset",
            )
        }
    }

    /**
     * Runs [request] where a screen's `LaunchedEffect` would, before the sheet
     * has been laid out. With no anchors it does not suspend: it leaves the
     * request waiting and returns.
     */
    private fun ask(request: suspend () -> Unit) {
        request.startCoroutine(Continuation(EmptyCoroutineContext) { it.getOrThrow() })
    }
}
