package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import io.kontour.ui.components.list.ListItem
import io.kontour.ui.components.list.ReorderableItem
import io.kontour.ui.components.list.rememberReorderableState
import io.kontour.ui.interaction.FeedbackDispatcher
import io.kontour.ui.interaction.FeedbackIntent
import io.kontour.ui.interaction.LocalFeedback
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A finger that wanders during the hold still picks the row up.
 *
 * `detectDragGesturesAfterLongPress` cancels the moment the pointer leaves
 * `viewConfiguration.touchSlop`, which is the threshold for "this is a scroll" —
 * and it is the wrong question to ask of a finger that has not gone anywhere
 * yet. Reported from a phone browser as "cannot drag downwards, the page scrolls
 * instead", and reproduced here with the hold intact and ten dp of wander in the
 * middle of it: the row was never picked up, and whatever the finger did next
 * went to the thing that scrolls behind it.
 *
 * Three cases, and the last two are what stop the fix from being "reorder
 * everything": an interrupted hold must still be a scroll, and a scroll that
 * leaves the row must still be a scroll.
 */
class ReorderHoldTest {

    @Test
    fun aFingerThatWandersDuringTheHoldStillPicksTheRowUp() {
        val moved = reorder(holdMillis = 900, wander = Wander, travel = 160f)
        assertEquals(
            "West Leederville", moved.order[2],
            "the row was held for 900ms with ${Wander.toInt()}px of wander in the " +
                "middle and never moved: the list is ${moved.order}. Ten dp is a " +
                "fingertip settling, not a scroll.",
        )
    }

    @Test
    fun aHoldTooShortToBeOneIsStillAScroll() {
        val quick = reorder(holdMillis = 100, wander = 0f, travel = -160f)
        assertEquals(
            "Subiaco", quick.order[2],
            "a 100ms press reordered the list: ${quick.order}. That is a scroll, " +
                "and a list that reorders on one cannot be scrolled at all.",
        )
        assertTrue(
            quick.firstVisibleIndex > 0 || quick.firstVisibleOffset > 0,
            "the list did not scroll either — the gesture went nowhere, so this " +
                "proves nothing about who took it",
        )
    }

    @Test
    fun aSlowScrollDuringTheHoldIsStillAScroll() {
        // Past the budget, but only just, and over the whole of the hold: this
        // is the gesture the budget has to keep rejecting.
        val slow = reorder(holdMillis = 900, wander = TooFar, travel = -160f)
        assertEquals(
            "Subiaco", slow.order[2],
            "a finger that travelled ${TooFar.toInt()}px before the hold expired " +
                "picked the row up anyway: ${slow.order}. The budget is meant to " +
                "be a fingertip, not a gesture.",
        )
    }

    /**
     * Every wander either reorders **or** scrolls, and only one of them buzzes.
     *
     * ### This was written to catch a defect that turned out not to exist
     *
     * `ReorderHoldSlop` is 24dp and a `LazyColumn` claims at
     * `viewConfiguration.touchSlop`, which is smaller — so on paper there is a
     * band where the scroller has already taken the gesture and the hold, which
     * never checks `isConsumed`, runs to completion anyway: the row lifts, fires
     * a `LongPress`, and is cancelled by the next event. A thump announcing a row
     * the user does not get.
     *
     * Swept at 20, 30, 36, 40, 44, 48 and 56 px of wander, that band **is not
     * there**:
     *
     * ```
     * 20-48px  reorders, felt [LongPress, Selection, Selection, Tick]
     * 56px     scrolls,  felt nothing
     * ```
     *
     * The handover is clean and it happens at the hold's own budget, not at the
     * scroller's slop. Whatever is wrong with dragging in a browser, it is not
     * this — so nothing was changed for it, and this test records the invariant
     * instead of a fix.
     *
     * ### What it is now a ratchet on
     *
     * The pairing, not the boundary. Wherever the boundary sits, a gesture that
     * ends in a scroll must not have announced a pick-up first. Lower
     * `ReorderHoldSlop` under the scroller's slop and this opens the band it was
     * written to find.
     */
    @Test
    fun aHoldEitherPicksTheRowUpOrScrolls_neverBoth() {
        val offenders = Wanders.mapNotNull { wander ->
            val run = reorder(holdMillis = 900, wander = wander, travel = -160f)
            val lifted = FeedbackIntent.LongPress in run.felt
            val reordered = run.order[2] != "Subiaco"
            when {
                reordered && !lifted -> "${wander.toInt()}px reordered in silence"
                lifted && !reordered ->
                    "${wander.toInt()}px announced a pick-up and then scrolled instead " +
                        "(felt ${run.felt}, list at ${run.firstVisibleIndex})"
                else -> null
            }
        }

        assertTrue(
            offenders.isEmpty(),
            "a hold has to end in exactly one of two things, and say so: " +
                offenders.joinToString("; "),
        )
    }

    private class Result(
        val order: List<String>,
        val firstVisibleIndex: Int,
        val firstVisibleOffset: Int,
        val felt: List<FeedbackIntent>,
    )

    @Composable
    private fun Recording(into: MutableList<FeedbackIntent>, content: @Composable () -> Unit) {
        CompositionLocalProvider(
            LocalFeedback provides FeedbackDispatcher { into += it },
            content = content,
        )
    }

    /**
     * Presses the third row, wanders [wander] pixels, holds for [holdMillis] of
     * real time, then drags [travel] and lets go.
     *
     * The hold is on the wall clock because Compose's long-press timeout is a
     * `delay`, and a `delay` in this harness is real time whatever the frame
     * clock is doing.
     */
    private fun reorder(holdMillis: Long, wander: Float, travel: Float): Result {
        val rows = mutableStateListOf(
            "Perth", "Daglish", "Subiaco", "West Leederville", "Leederville", "Glendalough",
        )
        var bounds = Rect.Zero
        var index = 0
        var offset = 0
        val felt = mutableListOf<FeedbackIntent>()

        Scene(width = 500, height = 300) {
            Recording(felt) {
            val listState = rememberLazyListState()
            val reorder = rememberReorderableState(listState) { from, to ->
                rows.add(to, rows.removeAt(from))
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().background(Color.White),
            ) {
                itemsIndexed(rows) { i, name ->
                    ReorderableItem(state = reorder, index = i, itemCount = rows.size) {
                        ListItem(
                            modifier = if (i == 2) Modifier.reportBounds { bounds = it } else Modifier
                        ) { +name }
                    }
                }
            }
            index = listState.firstVisibleItemIndex
            offset = listState.firstVisibleItemScrollOffset
            }
        }.use { scene ->
            scene.frames(4)
            val grab = bounds.center
            scene.press(grab)
            if (wander != 0f) {
                scene.move(grab + Offset(0f, wander))
                scene.frame()
            }
            scene.renderUntil(timeoutMillis = holdMillis) { false }
            repeat(Steps) { i ->
                scene.move(grab + Offset(0f, wander + travel * (i + 1) / Steps))
                scene.frame()
            }
            scene.release(grab + Offset(0f, wander + travel))
            scene.frames(10)
        }

        return Result(rows.toList(), index, offset, felt.toList())
    }

    private companion object {
        const val Steps = 12

        /** Ten dp at this scene's density: a fingertip settling. */
        const val Wander = 20f

        /** Past `ReorderHoldSlop`'s 24dp: a gesture, not a settle. */
        const val TooFar = 56f

        /**
         * Either side of `ReorderHoldSlop`, in steps small enough to land inside
         * any band that opens up between the two thresholds.
         *
         * 48px is the budget exactly and 56 is past it, so the pair brackets the
         * handover; the four below them are the region a shrinking budget would
         * expose first.
         */
        val Wanders = listOf(20f, 30f, 36f, 40f, 44f, 48f, 56f)
    }
}
