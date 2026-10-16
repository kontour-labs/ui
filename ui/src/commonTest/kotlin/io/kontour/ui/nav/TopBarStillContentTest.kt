package io.kontour.ui.nav

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A bar taking the scroll itself tells content that cannot scroll from content
 * that is only at its end.
 *
 * A push past the content's end, with the bar not yet collapsed, was taken to
 * mean the content could not scroll whenever the content had taken none of the
 * drag: the bar stopped collapsing ahead of it, folded through the band, and
 * opened again on release. A list that scrolls but was at its end — left there
 * by a release that opened the bar over a list scrolled some way, or put there
 * by the app — is the same to that test, and on iOS its bar would not collapse
 * until the finger had first dragged the other way.
 *
 * Content that cannot scroll is at its start *and* its end. So it is still only
 * when it has also been seen at its start since it last moved — a scroll
 * towards the start that it left for the bar.
 *
 * Driven through the connection, as the scrollable under it would drive it, so
 * what the content took and left is set exactly.
 */
class TopBarStillContentTest {

    /** A bar that gives up half of each collapsing scroll's pixels, with a push to take. */
    private fun bar(): TopBarCollapseState = TopBarCollapseState(0f).apply {
        measured(shrink = 50f, travel = 100f)
        takesPushes = true
    }

    private val connection get() = state.nestedScrollConnection
    private lateinit var state: TopBarCollapseState

    /** A drag of [delta] towards the start, all of which the content took. */
    private fun backTaken(delta: Float) {
        state.pressedChanged(true)
        val pre = connection.onPreScroll(Offset(0f, delta), NestedScrollSource.UserInput)
        connection.onPostScroll(Offset(0f, delta - pre.y), Offset.Zero, NestedScrollSource.UserInput)
        state.pressedChanged(false)
    }

    /** A drag of [delta] towards the start, none of which the content took. */
    private fun backRefused(delta: Float) {
        state.pressedChanged(true)
        val pre = connection.onPreScroll(Offset(0f, delta), NestedScrollSource.UserInput)
        connection.onPostScroll(Offset.Zero, Offset(0f, delta - pre.y), NestedScrollSource.UserInput)
        state.pressedChanged(false)
    }

    /**
     * A drag towards the end, in steps of 10px, none of which the content can
     * take: it is at its end. Returns what the bar took ahead of the content
     * at the last step.
     */
    private fun pushAtTheEnd(steps: Int): Float {
        state.pressedChanged(true)
        var ahead = 0f
        repeat(steps) {
            val pre = connection.onPreScroll(Offset(0f, -10f), NestedScrollSource.UserInput)
            ahead = pre.y
            connection.onPostScroll(Offset.Zero, Offset(0f, -10f - pre.y), NestedScrollSource.UserInput)
        }
        return ahead
    }

    @Test
    fun aListAtItsEndUnderAnOpenBarCollapsesIt() {
        state = bar()
        // It scrolled back some way: it moves, and is not at its start.
        backTaken(20f)
        assertEquals(0f, state.progress, "the bar should still be open")

        val ahead = pushAtTheEnd(steps = 3)
        assertTrue(ahead < 0f, "at the list's end the bar stopped collapsing ahead of the drag, as it does over a page that cannot scroll")
        assertTrue(state.progress > 0.25f, "three pushes collapsed the bar only to ${state.progress}")
    }

    @Test
    fun aPageThatCannotScrollIsStillWhatItWas() {
        state = bar()
        // It refused a drag towards its start: it is at its start.
        backRefused(20f)

        val ahead = pushAtTheEnd(steps = 3)
        assertEquals(0f, ahead, "over a page that cannot scroll, the bar went on collapsing ahead of the content")
    }

    @Test
    fun contentNeverSeenToMoveIsTakenForStill() {
        // The first drag on a fresh screen: as before, the bar folds through the
        // band and opens again, over a page that may not scroll at all.
        state = bar()
        val ahead = pushAtTheEnd(steps = 3)
        assertEquals(0f, ahead)
    }
}
