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
 * A gated part is part of the sheet's height, and of the sheet's own content.
 *
 * The first version of `part` composed nothing while it was hidden, and that one
 * decision closed a loop. A hidden part adds nothing to the content's height,
 * `SheetDetent.Expanded` *is* the content's height, so a sheet whose parts were all
 * gated resolved `Expanded` to its *collapsed* height — and could not be dragged
 * any taller than the content it was already showing. Worse where the collapsed
 * content was the peek anchor: `Expanded` and `peek` then resolved to the same
 * offset, `resolveAnchors` dropped the duplicate, `positionOf(Expanded)` answered
 * `NaN`, and the part's own gate — which compared positions — was false forever.
 * Three other readings took that `NaN` with them, including the floor an
 * undismissable sheet is held up by.
 *
 * A part is simply laid out now, in place, at its full height, whatever the sheet
 * is doing: the sheet's own bottom edge is what hides it, and dragging the sheet up
 * uncovers it. So the height is one number again and cannot move when a part comes
 * into view — which these arms are what hold. That a part is *on screen* from the
 * first frame of a drag is measured in pixels, by `SheetPartsTest`.
 *
 * The peek detent is used deliberately. It is the arrangement that produced the
 * collision, and a sheet whose only always-present content is its own peek anchor
 * is not a contrived one — it is the map screen this library was written for.
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
     * A part below the sheet's edge is not a row a screen reader reads out.
     *
     * It is composed, measured and placed there — that is what makes it appear the
     * instant a finger moves — so being off the bottom of the window is all that
     * hides it, and a screen reader does not work in pixels.
     *
     * `clearAndSetSemantics` rather than `hideFromAccessibility`, for the reason
     * `OverlayHost` writes down where it hides a dimmed page: the flag leaves the
     * node in the tree and asks other people's code to honour it, and measured, it
     * left a button findable. An earlier version of this arm found that being
     * *unplaced* was not enough either.
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
                "its peek. It is laid out there, below the sheet's edge, so nothing " +
                "but this keeps a screen reader from reading out content nobody " +
                "can see",
        )
    }

    /**
     * Revealing a part does not rebuild the anchors — not once.
     *
     * The invariant the whole arrangement rests on, and it holds trivially now
     * rather than by arithmetic: a part's height does not depend on where the
     * sheet is, so nothing an anchor is built from changes as the sheet moves. Two
     * earlier versions had to work for this — one deferred the change to the
     * settle, one balanced two heights to the pixel — and a rebuild under a finger
     * re-pins a drag that is already in flight, so it stays asserted.
     */
    @Test
    fun bringingAPartIntoViewDoesNotRebuildTheAnchors() {
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
            "moving the sheet to its tallest detent rebuilt the anchors $rebuilds " +
                "time(s). Nothing the anchors are built from depends on where the " +
                "sheet is",
        )
    }

    /**
     * The part is **reachable** before the sheet arrives, not after.
     *
     * Its pixels are never gated, so what this measures is the one thing `from`
     * still decides: whether the part is in the assistive tree. It keys on where
     * the sheet is *going* rather than where it has got to, so a part comes into
     * the tree as the gesture commits rather than a beat after it stops — measured
     * by stepping the clock by hand and noting how far the sheet still had to
     * travel when the part first became findable.
     */
    @Test
    fun aPartBecomesReachableBeforeTheSheetArrives() {
        var reachableAt = Float.NaN
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
                if (reachableAt.isNaN() &&
                    onAllNodesWithTag(PartTag).fetchSemanticsNodes().isNotEmpty()
                ) {
                    reachableAt = sheet.offset
                }
            }
            landedAt = sheet.offset
        }

        assertTrue(
            !reachableAt.isNaN(),
            "the part never entered the assistive tree at all over $MovingFrames frames",
        )
        assertTrue(
            reachableAt - landedAt > StillToTravel,
            "the part became reachable with the sheet at $reachableAt, which is " +
                "${reachableAt - landedAt}px from where it came to rest at " +
                "$landedAt. It is meant to key on the detent the sheet is heading " +
                "for, not on the one it has reached",
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
