package io.kontour.ui.sheet

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.foundation.OverscrollEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.unit.Velocity

/**
 * Lets a sheet be pulled above its tallest detent, and springs it back.
 *
 * `anchoredDraggable` clamps the offset to its anchor range, so a sheet at its
 * top detent does not move at all under a finger still travelling upward — the
 * gesture simply stops answering. Measured rather than assumed: dragged 292px
 * past the top, the offset stayed at `352.0` for every frame of the drag and the
 * sheet's crown row never moved a pixel. That is the "too rigid".
 *
 * ### Why an overscroll effect rather than a second gesture
 *
 * The delta this needs is the part `anchoredDraggable` *could not use*, and an
 * `OverscrollEffect` is the one place the framework hands that over. A second
 * `pointerInput` above the drag would have to decide, per event, which of the
 * two owns the finger — and two detectors arguing over one gesture is how the
 * slider lost its drag in round 13.
 *
 * ### The three things it does
 *
 * **Pays back before it scrolls.** A finger coming back closes the gap it opened
 * before the sheet itself starts moving. Without that ordering the sheet slides
 * away while the stretch is still open, and one gesture produces two motions.
 *
 * **Holds the floor.** A sheet the user is not allowed to put away has a bottom
 * as well as a top — see [SheetState.dragFloor] — and past it the sheet stretches
 * downward on the same arithmetic rather than sliding all the way off the
 * bottom, settling hidden and being put back a second later.
 *
 * **Stretches with diminishing returns.** See [SheetState.stretch]: a linear
 * stretch with a hard stop is the same rigid boundary moved somewhere else.
 *
 * **Springs back on release**, in [applyToFling], after the fling itself has
 * settled — so a flick that carries the sheet to another detent is not fighting
 * a stretch unwinding underneath it.
 *
 * The stretch is purely visual: [SheetState.overshoot] is subtracted at layout
 * and no anchor, detent or derived value knows about it. A sheet cannot settle
 * in the overshoot, which is the whole difference between this and adding a
 * detent above the top one.
 */
internal class SheetOverscroll(
    private val state: SheetState,
    private val spec: AnimationSpec<Float>,
) : OverscrollEffect {

    override val isInProgress: Boolean
        get() = state.overshoot != 0f

    // Nothing to draw or measure: the stretch reaches the screen through the
    // sheet's own `offset`, which is already reading `overshoot` in the layout
    // phase. The interface requires a node, so this is an empty one.
    override val node: DelegatableNode = object : Modifier.Node() {}

    override fun applyToScroll(
        delta: Offset,
        source: NestedScrollSource,
        performScroll: (Offset) -> Offset,
    ): Offset {
        // Only a finger stretches a sheet. A programmatic scroll — `animateTo`,
        // an accessibility action — should land exactly where it was told to.
        if (source != NestedScrollSource.UserInput) return performScroll(delta)

        val paidBack = state.payBackOvershoot(delta.y)
        val offered = delta.y - paidBack

        // As far down as the sheet is allowed to go, and no further. Ordinarily
        // that is all of it — `dragFloor` is `NaN` for a sheet the user may put
        // away, because hidden is then a real place for a drag to end.
        val toSheet = state.roomBeforeFloor(offered)
        val consumed = performScroll(Offset(delta.x, toSheet)).y
        val leftOver = offered - consumed

        val stretched = when {
            // Upward, and the sheet had nowhere left to go.
            leftOver < 0f && state.canOvershoot -> -state.stretch(-leftOver)
            // Downward, and the floor is holding it.
            leftOver > 0f -> state.stretchDown(leftOver)
            else -> 0f
        }

        return Offset(delta.x, paidBack + consumed + stretched)
    }

    override suspend fun applyToFling(
        velocity: Velocity,
        performFling: suspend (Velocity) -> Velocity,
    ) {
        performFling(velocity)
        state.releaseOvershoot(spec)
    }
}

/**
 * Remembers the [SheetOverscroll] for [state].
 *
 * [spec] is the sheet's own settle spec, passed rather than chosen here for the
 * reason `nestedScrollConnection` takes one: two settling policies on one sheet
 * is a sheet that arrives differently depending on where the gesture started.
 */
@Composable
internal fun rememberSheetOverscroll(
    state: SheetState,
    spec: AnimationSpec<Float>,
): OverscrollEffect = remember(state, spec) { SheetOverscroll(state, spec) }
