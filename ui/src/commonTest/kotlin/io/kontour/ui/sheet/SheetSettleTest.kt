package io.kontour.ui.sheet

import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
 *
 * ### The first version of this file asserted the next defect
 *
 * It drove an 800px/s downward fling at an expanded sheet and asserted the sheet
 * was **still expanded** — under the heading "the sheet should have settled
 * somewhere rather than thrown", which is what it was really testing. The
 * sheet staying put was incidental, and it was wrong: it is the reported
 * behaviour that a sheet cannot be flicked shut however hard you throw it.
 *
 * The throwing claim survives, because the reason for it does — `settle(velocity)`
 * still throws on a state built without thresholds, and this is still the only
 * place that would notice. What it asserts instead is that the sheet settles on
 * *a* detent; where it settles is the next test's business.
 */
class SheetSettleTest {

    private val detents = listOf(SheetDetent.Hidden, SheetDetent.Expanded)

    /**
     * The flick floor in pixels, at the 1x density these offsets are written in.
     *
     * [SheetFlickVelocity] is a `Dp` because it is a real distance a real finger
     * covers; this fixture has no composition and works in raw pixels, so it
     * converts once here.
     */
    private val floor = with(Density(1f)) { SheetFlickVelocity.toPx() }

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
        val connection = state.nestedScrollConnection(tween(0), floor)

        connection.onPostFling(consumed = Velocity.Zero, available = Velocity(0f, 800f))

        assertTrue(
            state.currentDetent in detents,
            "the sheet should have settled on one of its detents rather than " +
                "thrown — `settle(velocity)` is deprecated and throws on a state " +
                "built without thresholds, which this one is",
        )
    }

    /**
     * And a hard flick downward closes it.
     *
     * The report, in as many words: a sheet with only two detents — full height
     * and closed — cannot be closed by flicking however hard, and has to be
     * dragged more than half way down instead.
     *
     * Both halves of the arithmetic said so. `settle` animates to the state's
     * current `targetValue`, which is chosen by **position** against a 0.5
     * positional threshold and never looks at velocity; and the velocity never
     * reached it anyway, because a downward fling passes straight through
     * `onPreFling` and lands in `onPostFling`, which settled positionally. Every
     * flick on a settings sheet begins on its content, so every flick was
     * settled as though the finger had stopped dead.
     *
     * 4000px/s is a brisk throw on a phone and projects a long way past the
     * anchor it started on; the sheet is expanded at 400 with hidden at 1000.
     */
    @Test
    fun aHardFlickDownwardClosesTheSheet() {
        val state = anchoredState()

        assertEquals(
            SheetDetent.Hidden,
            state.detentAimedAt(4000f, floor),
            "a 4000px/s downward flick is aimed at " +
                "${state.detentAimedAt(4000f, floor)}. " +
                "A flick has to be allowed to mean something a drag does not — " +
                "the alternative is what was reported, which is that the only way " +
                "to shut a sheet is to haul it past the half-way mark.",
        )
    }

    /**
     * A gentle one is not a flick at all.
     *
     * The control, and it is the assertion that keeps the projection honest. A
     * response that closes the sheet on any downward velocity at all is not
     * "where the flick was aimed" — it is a threshold with extra steps, and it
     * would take the sheet away from a reader who was scrolling its content and
     * stopped.
     *
     * **Null rather than `Expanded`, and that is the change.** This used to
     * assert the nudge was *aimed at* `Expanded`, which was true only because the
     * sheet happened to be sitting there — the projection was consulted and came
     * back with the anchor it started on. It was consulted for every gesture
     * however slight, which is what made the sheet too easy to close once a drag
     * had already carried it part of the way. Now a gesture under the floor gets
     * no opinion at all and the caller settles by position, which is the sheet's
     * behaviour from before any of this existed.
     */
    @Test
    fun aGentleDownwardFlickIsNotAFlick() {
        val state = anchoredState()

        assertNull(
            state.detentAimedAt(200f, floor),
            "a 200px/s nudge was read as a flick aimed at " +
                "${state.detentAimedAt(200f, floor)}. That is a finger coming to " +
                "rest, not a throw; it has to hand the decision back to the " +
                "positional settle rather than pick a detent.",
        )
    }

}
