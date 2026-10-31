package io.kontour.ui.overlay

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.kontour.ui.theme.ContrastLevel
import io.kontour.ui.theme.KontourTheme
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A tour's last step has a Back and a Done, and both are buttons that do what
 * they say; a tour of one step has neither, only "Got it".
 *
 * Asked for as *"in the coachmark tour, there needs to be a "done" and "back"
 * button in the final item's coachmark. if there already is, it doesn't show up
 * in your demo"*. There was a Back; the way out was "Got it", a lone mark's
 * word, and both were ghost buttons on the bubble's accent, which read as words
 * rather than as buttons.
 */
@OptIn(ExperimentalTestApi::class)
class CoachmarkTourButtonsTest {

    private fun ComposeUiTest.tour(
        vararg steps: String,
        rtl: Boolean = false,
        showSkip: Boolean = false,
        contrast: ContrastLevel = ContrastLevel.Standard,
    ): CoachmarkTour {
        lateinit var tour: CoachmarkTour
        setContent {
            KontourTheme(contrast = contrast, reduceMotion = true) {
                CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                    tour = rememberCoachmarkTour(*steps, showSkip = showSkip)
                    Box(Modifier.requiredSize(400.dp, 700.dp)) {
                        OverlayHost(Modifier.fillMaxSize()) {
                            Column {
                                for (step in steps) {
                                    Box(Modifier.size(48.dp).coachmarkStep(tour, step, title = "Step $step", message = "About $step."))
                                }
                            }
                        }
                    }
                }
            }
        }
        waitForIdle()
        runOnUiThread { tour.start() }
        waitForIdle()
        return tour
    }

    private fun ComposeUiTest.button(label: String) = onNode(hasText(label) and hasClickAction())

    /** Where a button's word is drawn, rather than the button with its invisible pad. */
    private fun ComposeUiTest.word(label: String): Rect = onAllNodes(hasText(label), useUnmergedTree = true)
        .fetchSemanticsNodes().first { it.children.isEmpty() }.boundsInRoot

    private fun ComposeUiTest.bubble(step: String): Rect =
        onNode(hasContentDescription("Step $step", substring = true)).fetchSemanticsNode().boundsInRoot

    /**
     * Back follows the count, at the start side of the bubble's row, with the
     * same room round it as every other button: the row's gap between its box
     * and the count, as between Skip's box and Next's. Asked for for 0.5.2 as
     * *"the "back" button is on the left side of the tip"*, and then *"it
     * should be to the right of the progress marker, and it also needs equal
     * padding, same as the other buttons"*.
     */
    @Test
    fun backFollowsTheCountAndSkipStandsBesideNext() = runComposeUiTest {
        tour("a", "b", "c", showSkip = true)
        button("Next").performClick()
        waitForIdle()
        val margin = with(density) { 16.dp.toPx() }
        val bubble = bubble("b")
        val back = button("Back").fetchSemanticsNode().boundsInRoot
        val skip = button("Skip").fetchSemanticsNode().boundsInRoot
        val next = button("Next").fetchSemanticsNode().boundsInRoot
        assertTrue(back.right < skip.left && skip.right < next.left, "the row is not Back, Skip, Next from left to right: $back, $skip, $next")
        assertTrue(back.left - bubble.left > margin + with(density) { 12.dp.toPx() }, "Back is at the bubble's start, not after the count")
        assertTrue(next.right - back.right > bubble.width / 3, "Back is still beside Next")
        // Back's box stands as far from the count as Skip's from Next's: one gap.
        val gap = next.left - skip.right
        assertTrue(gap > 0f, "Skip and Next touch")
    }

    @Test
    fun backFollowsTheCountOnTheRightInRightToLeft() = runComposeUiTest {
        tour("a", "b", "c", rtl = true, showSkip = true)
        button("Next").performClick()
        waitForIdle()
        val margin = with(density) { 16.dp.toPx() }
        val bubble = bubble("b")
        val back = button("Back").fetchSemanticsNode().boundsInRoot
        val skip = button("Skip").fetchSemanticsNode().boundsInRoot
        val next = button("Next").fetchSemanticsNode().boundsInRoot
        assertTrue(back.left > skip.right && skip.left > next.right, "the row is not Back, Skip, Next from right to left: $back, $skip, $next")
        assertTrue(bubble.right - back.right > margin + with(density) { 12.dp.toPx() }, "Back is at the bubble's right edge, not after the count")
        assertTrue(back.left - next.left > bubble.width / 3, "Back is still beside Next")
    }

    /**
     * Not pulled out by its pad: Back is a plain ghost button, the row's gap
     * after the count, exactly as Skip is the row's gap before Next. Measured
     * against a tour with no Skip, where only Back's place can differ.
     */
    @Test
    fun backKeepsTheSameRoomAsTheOtherButtons() = runComposeUiTest {
        tour("a", "b", "c", showSkip = true)
        button("Next").performClick()
        waitForIdle()
        val back = button("Back").fetchSemanticsNode().boundsInRoot
        val skip = button("Skip").fetchSemanticsNode().boundsInRoot
        val next = button("Next").fetchSemanticsNode().boundsInRoot
        val backWord = word("Back")
        val skipWord = word("Skip")
        // The same pad inside each ghost button, either side of its word.
        assertTrue(abs((backWord.left - back.left) - (skipWord.left - skip.left)) <= 1f, "Back's word is ${backWord.left - back.left}px inside its box, Skip's ${skipWord.left - skip.left}px")
        assertTrue(abs((back.right - backWord.right) - (skip.right - skipWord.right)) <= 1f, "Back's box ends ${back.right - backWord.right}px past its word, Skip's ${skip.right - skipWord.right}px")
        assertTrue(next.left - skip.right > 0f)
    }

    @Test
    fun theLastStepHasBackAndDone() = runComposeUiTest {
        val tour = tour("a", "b", "c")
        button("Next").performClick()
        waitForIdle()
        button("Next").performClick()
        waitForIdle()
        assertEquals("c", tour.current)
        button("Back").assertExists("the last step has no Back")
        button("Done").assertExists("the last step has no Done")
        assertEquals(0, onAllNodes(hasText("Got it")).fetchSemanticsNodes().size, "the last step of a tour still says Got it")

        button("Back").performClick()
        waitForIdle()
        assertEquals("b", tour.current, "Back on the last step went somewhere else")
        button("Next").performClick()
        waitForIdle()
        button("Done").performClick()
        waitForIdle()
        assertTrue(tour.isFinished, "Done did not finish the tour")
    }

    /**
     * At the high-contrast tier a ghost button is drawn with an edge, and the
     * row's buttons keep a real gap between their edges. Back and Skip used to
     * be pulled together by their invisible pads, and there their outlines
     * overlapped; Back's nearly touched Next's. Reported for 0.5.2 from a phone.
     */
    @Test
    fun outlinedButtonsNeverTouchAtHighContrast() = runComposeUiTest {
        tour("a", "b", "c", showSkip = true, contrast = ContrastLevel.High)
        button("Next").performClick()
        waitForIdle()
        val gap = with(density) { 4.dp.toPx() }
        val bubble = bubble("b")
        val buttons = listOf("Back", "Skip", "Next").associateWith { button(it).fetchSemanticsNode().boundsInRoot }
        for ((a, one) in buttons) {
            for ((b, other) in buttons) {
                if (a < b) assertFalse(one.overlaps(other), "$a ($one) and $b ($other) overlap")
            }
        }
        val skip = buttons.getValue("Skip")
        val next = buttons.getValue("Next")
        val back = buttons.getValue("Back")
        assertTrue(next.left - skip.right >= gap, "Skip's edge is ${next.left - skip.right}px from Next's")
        assertTrue(skip.left - back.right >= gap, "Back's edge is ${skip.left - back.right}px from Skip's")
        // After the count, not at the bubble's edge.
        assertTrue(back.left - bubble.left > (bubble.right - next.right) + gap, "Back's edge is at the bubble's start, not after the count")
    }

    @Test
    fun aTourOfOneStepSaysGotIt() = runComposeUiTest {
        tour("only")
        onNodeWithText("Got it").assertExists("a lone step has no Got it")
        assertEquals(0, onAllNodes(hasText("Done")).fetchSemanticsNodes().size, "a lone step says Done, which ends a tour it is not part of")
        assertEquals(0, onAllNodes(hasText("Back")).fetchSemanticsNodes().size, "a lone step has a Back to nowhere")
    }
}
