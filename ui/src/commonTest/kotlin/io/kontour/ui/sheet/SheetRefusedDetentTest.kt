package io.kontour.ui.sheet

import androidx.compose.ui.unit.Density
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A detent the sheet refuses has no anchor, and the one it is sitting on always does.
 *
 * `confirmDetentChange` used to be `AnchoredDraggableState`'s `confirmValueChange`
 * — a veto consulted on each attempted move, so a refused detent still had a
 * position and a drag towards it followed the finger and sprang back. That
 * overload is deprecated, and upstream's replacement is to give the refused
 * position no anchor at all.
 *
 * The clause worth testing is the exception to that. Filtering *everything* the
 * predicate refuses is wrong the moment the sheet is standing on one of them:
 * `initialDetent` defaults to `detents.first()`, `DefaultSheetDetents` begins
 * with [SheetDetent.Hidden], and refusing hidden is the one thing this parameter
 * is for — so the obvious implementation leaves the commonest configuration with
 * no anchor for its own starting position, and an offset of `NaN`.
 */
class SheetRefusedDetentTest {

    private val detents = listOf(SheetDetent.Hidden, SheetDetent.Half, SheetDetent.Expanded)

    /** A sheet with anchors resolved, as a laid-out one would have. */
    private fun sheet(
        initialDetent: SheetDetent,
        confirmDetentChange: (SheetDetent) -> Boolean,
    ): SheetState = SheetState(detents, initialDetent, confirmDetentChange).apply {
        containerHeight = 1000f
        sheetHeight = 600f
        updateAnchors(Density(1f))
    }

    private fun SheetState.hasAnchorFor(detent: SheetDetent): Boolean =
        !anchoredState.anchors.positionOf(detent).isNaN()

    @Test
    fun aRefusedDetentGetsNoAnchor() {
        val state = sheet(SheetDetent.Expanded) { it != SheetDetent.Hidden }

        assertFalse(
            SheetDetent.Hidden in state.allowedDetents,
            "hidden was refused and the sheet is not sitting on it, so it should " +
                "not be a position the sheet has",
        )
        assertFalse(
            state.hasAnchorFor(SheetDetent.Hidden),
            "a refused detent still had an anchor, so a drag towards it has " +
                "somewhere to go — which is the vetoing behaviour this replaced",
        )
        assertTrue(state.hasAnchorFor(SheetDetent.Expanded), "the sheet lost its own anchor")
    }

    @Test
    fun theDetentTheSheetIsSittingOnSurvivesBeingRefused() {
        val state = sheet(SheetDetent.Hidden) { it != SheetDetent.Hidden }

        assertTrue(
            SheetDetent.Hidden in state.allowedDetents,
            "the sheet starts hidden and refuses hidden — filtering it out leaves " +
                "the sheet with no anchor for where it actually is",
        )
        assertTrue(state.hasAnchorFor(SheetDetent.Hidden), "the starting position has no offset")
    }

    @Test
    fun aSheetThatRefusesHiddenStillOpensFromIt() = runTest {
        val state = sheet(SheetDetent.Hidden) { it != SheetDetent.Hidden }

        state.snapTo(SheetDetent.Expanded)

        assertEquals(SheetDetent.Expanded, state.currentDetent, "the sheet could not open")
        assertFalse(
            SheetDetent.Hidden in state.allowedDetents,
            "once the sheet has settled somewhere else, hidden should stop being " +
                "a place it can go back to — that is the whole point of refusing it",
        )
    }

    @Test
    fun movingToARefusedDetentDoesNothing() = runTest {
        val state = sheet(SheetDetent.Expanded) { it != SheetDetent.Hidden }

        state.snapTo(SheetDetent.Hidden)

        assertEquals(
            SheetDetent.Expanded,
            state.currentDetent,
            "a programmatic move reached a detent with no anchor",
        )
    }

    @Test
    fun refusingNothingLeavesEveryDetentInPlace() {
        val state = sheet(SheetDetent.Half) { true }

        assertEquals(detents, state.allowedDetents, "the default predicate changed the sheet")
        detents.forEach { assertTrue(state.hasAnchorFor(it), "$it lost its anchor") }
    }
}
