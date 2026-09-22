package io.kontour.ui.sheet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A part collapses for real, and the sheet still knows how tall it could be.
 *
 * The first version of `part` composed nothing while it was hidden, and that one
 * decision closed a loop. A hidden part adds nothing to the content's height,
 * `SheetDetent.Expanded` is the content's height, so a sheet whose parts were all
 * gated resolved `Expanded` to its *collapsed* height — and could not be dragged
 * any taller than the content it was already showing. Worse where the collapsed
 * content was the peek anchor: `Expanded` and `peek` then resolved to the same
 * offset, `resolveAnchors` dropped the duplicate, `positionOf(Expanded)` answered
 * `NaN`, and the part's own gate — which compared positions — was false forever.
 * Three other readings took that `NaN` with them, including the floor an
 * undismissable sheet is held up by.
 *
 * The fix is two heights instead of one: what the column places, and what it
 * would place with every part out. The second is what the anchors see, and it
 * does not move when a part reveals — so the reveal can happen *during* the
 * drag rather than a beat after it, which is the other half of what these arms
 * hold.
 *
 * The peek detent is used deliberately. It is the arrangement that produced the
 * collision, and a sheet whose only always-present content is its own peek
 * anchor is not a contrived one — it is the map screen this library was written
 * for.
 */
@OptIn(ExperimentalTestApi::class)
class SheetPartRevealTest {

    @Test
    fun aGatedPartDoesNotShortenTheSheetsTallestDetent() {
        var atPeek = Float.NaN
        var expanded = Float.NaN
        var partsShowing = -1

        runComposeUiTest {
            lateinit var sheet: SheetState
            var target by mutableStateOf(Peek)

            mainClock.autoAdvance = false
            setContent { Harness(target) { sheet = it } }
            settle()
            atPeek = sheet.offset

            target = SheetDetent.Expanded
            settle()
            expanded = sheet.offset
            partsShowing = onAllNodesWithTag(PartTag).fetchSemanticsNodes().size
        }

        assertTrue(
            atPeek.isFinite() && expanded.isFinite(),
            "the sheet reported a position that is not a number: peek $atPeek, " +
                "expanded $expanded. That is an `animateTo` to a detent the " +
                "anchors dropped as a duplicate",
        )
        assertTrue(
            atPeek - expanded > GrewBy,
            "the sheet went from $atPeek to $expanded — it grew ${atPeek - expanded}px, " +
                "where the part it was asked to reveal is 200dp. `Expanded` was " +
                "measured from the content the sheet was already showing",
        )
        assertEquals(
            1,
            partsShowing,
            "the revealed part is not in the semantics tree at `Expanded`",
        )
    }

    /**
     * A collapsed part is not a row a screen reader reads out.
     *
     * The part is composed while it is hidden — that is what the split heights
     * buy — so "collapsed" has to mean something stronger than "zero pixels
     * tall".
     *
     * **It found that unplacing is not enough.** An unplaced part was still
     * returned by `onAllNodesWithTag`, so the part clears its semantics as well —
     * `clearAndSetSemantics`, not `hideFromAccessibility`, for the reason
     * `OverlayHost` writes down where it hides a dimmed page.
     *
     * Alone among these arms it passes against the old implementation too, and
     * trivially: a hidden part composed nothing at all there, so there was
     * nothing to announce. It guards what the new one gives up to buy a reveal
     * with no composition in it.
     */
    @Test
    fun aCollapsedPartIsNotAnnounced() {
        var showing = -1

        runComposeUiTest {
            mainClock.autoAdvance = false
            setContent { Harness(Peek) { } }
            settle()
            showing = onAllNodesWithTag(PartTag).fetchSemanticsNodes().size
        }

        assertEquals(
            0,
            showing,
            "a part gated on `Expanded` is in the semantics tree with the sheet at " +
                "its peek. It is composed and measured there, so being unplaced is " +
                "the only thing keeping it out — a screen reader would otherwise " +
                "read out content nobody can see",
        )
    }

    /**
     * Revealing a part does not rebuild the anchors — not once.
     *
     * This is the invariant the whole arrangement rests on, and it is exact
     * rather than approximate: both heights are whole pixels, so the part gains
     * in one exactly what it loses in the other and `AnchorInputs` compares
     * equal. If it did not, every reveal would re-pin a drag in flight, which is
     * the defect the settle delay was there to avoid.
     */
    @Test
    fun revealingAPartDoesNotRebuildTheAnchors() {
        var rebuilds = -1
        var moved = Float.NaN

        runComposeUiTest {
            lateinit var sheet: SheetState
            var target by mutableStateOf(Peek)

            mainClock.autoAdvance = false
            setContent { Harness(target) { sheet = it } }
            settle()
            val before = sheet.anchorRebuilds
            val from = sheet.offset

            target = SheetDetent.Expanded
            settle()
            rebuilds = sheet.anchorRebuilds - before
            moved = from - sheet.offset
        }

        assertTrue(moved > GrewBy, "the sheet did not grow: ${moved}px")
        assertEquals(
            0,
            rebuilds,
            "revealing a part rebuilt the anchors $rebuilds time(s). The content's " +
                "placed height changed, which is expected; the height the anchors " +
                "are built from must not have",
        )
    }

    /**
     * The part is revealed at the *start* of the sheet's travel, not the end.
     *
     * The point of the split heights, stated as a distance. `part` keys on where
     * the sheet is *going*, so the reveal begins on the frame the target changes
     * — and since revealing cannot move an anchor any more, there is nothing left
     * that needed it to wait. Measured by stepping the clock by hand and noting
     * how far the sheet still had to travel when the part first appeared.
     */
    @Test
    fun aPartIsRevealedBeforeTheSheetArrives() {
        var revealedAt = Float.NaN
        var landedAt = Float.NaN

        runComposeUiTest {
            lateinit var sheet: SheetState
            var target by mutableStateOf(Peek)

            mainClock.autoAdvance = false
            setContent { Harness(target) { sheet = it } }
            settle()

            target = SheetDetent.Expanded
            repeat(MovingFrames) {
                mainClock.advanceTimeByFrame()
                if (revealedAt.isNaN() &&
                    onAllNodesWithTag(PartTag).fetchSemanticsNodes().isNotEmpty()
                ) {
                    revealedAt = sheet.offset
                }
            }
            landedAt = sheet.offset
        }

        assertTrue(
            !revealedAt.isNaN(),
            "the part never appeared at all over $MovingFrames frames",
        )
        assertTrue(
            revealedAt - landedAt > StillToTravel,
            "the part appeared with the sheet at $revealedAt, which is " +
                "${revealedAt - landedAt}px from where it came to rest at " +
                "$landedAt. It is meant to reveal as the sheet passes the detent, " +
                "not once the sheet has stopped",
        )
    }

    /**
     * Steps the clock until the sheet has stopped, rather than waiting for idle.
     *
     * `waitForIdle` does not return with a sheet on screen — it advances the
     * virtual clock for as long as anything is animating, and a live sheet always
     * has something armed. `SheetFramePressureTest` is the only other test here
     * that drives one, and it waits for idle while its sheet is still *hidden* for
     * the same reason. Frames by hand are what both of them use, and they are also
     * the honest unit: what these arms ask about is which frame something happens
     * on.
     */
    private fun ComposeUiTest.settle(frames: Int = SettleFrames) {
        repeat(frames) { mainClock.advanceTimeByFrame() }
    }

    @Composable
    private fun Harness(target: SheetDetent, onState: (SheetState) -> Unit) {
        KontourTheme {
            Box(Modifier.fillMaxSize().background(Color.White)) {
                OverlayHost(Modifier.fillMaxSize()) {
                    val state = rememberSheetState(
                        detents = listOf(Peek, SheetDetent.Expanded),
                        initialDetent = Peek,
                    )
                    onState(state)
                    LaunchedEffect(target) { state.animateTo(target) }
                    BottomSheet(state = state) {
                        // The sheet's only always-present content, and its peek
                        // anchor — which is what made `Expanded` and `peek`
                        // collide.
                        part {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(60.dp)
                                    .sheetPeekAnchor()
                                    .background(Color(0xFF3355AA))
                            )
                        }
                        part(from = SheetDetent.Expanded) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                                    .testTag(PartTag)
                                    .background(Color(0xFFEE2211))
                            )
                        }
                    }
                }
            }
        }
    }

    private companion object {
        val Peek = SheetDetent.peek(fallback = 80.dp)
        const val PartTag = "departures"

        /** Most of the 200dp part, in pixels at the test's density of 1. */
        const val GrewBy = 150f

        /** Long enough for any spec the theme could give the sheet. */
        const val MovingFrames = 120

        /**
         * How much of the sheet's travel must still be ahead of it when the part
         * appears, in pixels.
         *
         * The whole journey is 200dp. Most of it, rather than all: the gate flips
         * with `targetValue`, which is the first frame of the move, and the part
         * needs a frame or two of its own reveal before there is a placed node to
         * find.
         */
        const val StillToTravel = 120f

        /** Long enough for a sheet to arrive and its parts to finish revealing. */
        const val SettleFrames = 90
    }
}
