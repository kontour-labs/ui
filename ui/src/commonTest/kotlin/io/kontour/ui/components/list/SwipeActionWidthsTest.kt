package io.kontour.ui.components.list

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * How wide each of a swiped row's buttons is, as the row travels.
 *
 * Asked for as: *"right now if there's multiple, then they expand at the same
 * rate until you pass the threshold where the one that will actually respond to
 * the full swipe then becomes full width. i'd like it so instead of expanding at
 * the same rate, the action that takes the full swipe should keep expanding and
 * slowly push the other 1 or 2 action/s to zero"*.
 *
 * The numbers are a row's actions at 88px each with 4px between: two of them
 * revealed at 176 and committing at 224, three at 264 and 312 — each 48 past
 * its reveal, as `SwipeCommitMargin` puts it on a phone.
 */
class SwipeActionWidthsTest {

    private val gap = 4f
    private val full = 84f

    private fun widths(live: Float, count: Int = 2, commit: Float = 48f + 88f * count, takeover: Float = 0f) =
        swipeActionWidths(live = live, count = count, reveal = 88f * count, gap = gap, commit = commit, takeover = takeover)

    private fun near(expected: Float, actual: Float, what: String) =
        assertTrue(abs(expected - actual) < 0.01f, "$what: expected $expected, was $actual")

    /** The buttons and the gaps between them run from half a gap in from the edge to half a gap short of the row. */
    private fun assertFillsTheStrip(live: Float, count: Int, commit: Float = 48f + 88f * count, takeover: Float = 0f) {
        val w = widths(live, count, commit, takeover)
        val fold = swipeFold(live, 88f * count, commit, takeover)
        val between = (count - 1) * gap * (1f - fold)
        near(live - gap, w.sum() + between, "the strip at $live, ${w.toList()}")
    }

    @Test
    fun theyAreDealtOutOneAfterAnotherUpToTheReveal() {
        val w = widths(live = 120f)
        near(full, w[0], "the outermost, a whole action in")
        near(120f - 88f - gap, w[1], "the next, growing beside it")
        widths(live = 176f).forEachIndexed { i, it -> near(full, it, "button $i at the reveal") }
    }

    @Test
    fun pastTheRevealTheFullSwipesActionGrowsAndTheOthersShrink() {
        val halfway = widths(live = 200f)
        near(full / 2, halfway[1], "the inner action halfway to the point of no return")
        assertTrue(halfway[0] > 200f / 2, "the outermost should have more than an even share, was ${halfway[0]}")
        var last = widths(live = 176f)
        for (live in 177..224) {
            val now = widths(live.toFloat())
            assertTrue(now[0] > last[0], "the outermost should grow at $live: ${last[0]} to ${now[0]}")
            assertTrue(now[1] < last[1], "the inner should shrink at $live: ${last[1]} to ${now[1]}")
            last = now
        }
    }

    @Test
    fun atThePointOfNoReturnTheOthersAreGone() {
        val two = widths(live = 224f)
        near(224f - gap, two[0], "two actions' outermost")
        near(0f, two[1], "two actions' inner")
        val three = widths(live = 312f, count = 3)
        near(312f - gap, three[0], "three actions' outermost")
        near(0f, three[1], "three actions' middle")
        near(0f, three[2], "three actions' inner")
        val beyond = widths(live = 300f)
        near(300f - gap, beyond[0], "past it, the outermost")
        near(0f, beyond[1], "past it, the inner")
    }

    @Test
    fun threeActionsInnerTwoShrinkTogether() {
        val w = widths(live = 288f, count = 3)
        near(full / 2, w[1], "the middle halfway")
        near(full / 2, w[2], "the inner halfway")
    }

    @Test
    fun theStripIsAlwaysFilled() {
        for (live in 176..340 step 4) assertFillsTheStrip(live.toFloat(), 2)
        for (live in 264..340 step 4) assertFillsTheStrip(live.toFloat(), 3)
        for (live in 20..340 step 20) assertFillsTheStrip(live.toFloat(), 2, takeover = 1f)
    }

    @Test
    fun aSideWithNoFullSwipeStillSharesEvenly() {
        val w = widths(live = 220f, commit = Float.NaN)
        near(220f / 2 - gap, w[0], "the outermost")
        near(220f / 2 - gap, w[1], "the inner")
    }

    @Test
    fun aCommittedRowOnItsWayHomeKeepsTheOthersFolded() {
        val w = widths(live = 100f, takeover = 1f)
        near(100f - gap, w[0], "the outermost on the way home")
        near(0f, w[1], "the inner on the way home")
    }

    @Test
    fun aPointOfNoReturnAtTheRevealFoldsThemThere() {
        // A narrow row, where the far edge pulls the point of no return back to the reveal.
        near(175f - 88f - gap, widths(live = 175f, commit = 176f)[1], "short of it, still being dealt")
        near(0f, widths(live = 176f, commit = 176f)[1], "at it")
    }

    @Test
    fun oneActionHasTheStripToItself() {
        near(150f - gap, widths(live = 150f, count = 1)[0], "a lone action past its reveal")
    }
}
