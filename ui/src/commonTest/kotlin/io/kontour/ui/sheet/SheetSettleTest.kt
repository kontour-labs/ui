package io.kontour.ui.sheet

import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Settling a sheet from a fling that started in its content.
 *
 * A sheet holding anything scrollable — a list, a wheel picker — hands its
 * flings to [SheetState.nestedScrollConnection], which finishes the sheet's own
 * travel. That path **crashed**, and crashed on the most ordinary thing anyone
 * would do with a sheet:
 *
 * ```
 * java.lang.IllegalArgumentException: AnchoredDraggableState was configured
 * through a constructor without providing positional and velocity threshold.
 * This overload of settle has been deprecated.
 * ```
 *
 * `settle(velocity)` is deprecated and throws unless the state was built with
 * thresholds, and `SheetState` builds it without — thresholds live on the fling
 * behaviour now. Nothing caught it because every sheet test either drove the
 * state directly or rendered a still frame; none of them flung anything.
 *
 * These call the connection's fling callbacks straight, which is the whole
 * reproduction. Reverting to `settle(available.y)` fails both.
 */
class SheetSettleTest {

    private val detents = listOf(SheetDetent.Hidden, SheetDetent.Expanded)

    /** A state with anchors already attached, as a laid-out sheet would have. */
    private fun anchoredState(): SheetState {
        val state = SheetState(
            detents = detents,
            initialDetent = SheetDetent.Expanded,
            confirmDetentChange = { true },
        )
        state.containerHeight = 1000f
        state.sheetHeight = 600f
        state.anchoredState.updateAnchors(
            DraggableAnchors {
                SheetDetent.Hidden at 1000f
                SheetDetent.Expanded at 400f
            },
            SheetDetent.Expanded,
        )
        return state
    }

    @Test
    fun aFlingThatEndsInTheContentSettlesTheSheetInsteadOfThrowing() = runTest {
        val state = anchoredState()
        val connection = state.nestedScrollConnection(tween(0))

        connection.onPostFling(consumed = Velocity.Zero, available = Velocity(0f, 800f))

        assertEquals(
            SheetDetent.Expanded,
            state.currentDetent,
            "the sheet should have settled somewhere rather than thrown",
        )
    }
}
