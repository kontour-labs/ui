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
     * Back is at the start of the bubble's row, Skip beside the way on at its
     * end, and Back's word lines up with the title's and message's edge rather
     * than its invisible pad's. Asked for for 0.5.2 as *"the "back" button is
     * on the left side of the tip (and obviously reversed in RTL), rather than
     * right next to the next button with the skip button"*.
     */
    @Test
    fun backIsAtTheStartAndSkipBesideNext() = runComposeUiTest {
        tour("a", "b", "c", showSkip = true)
        button("Next").performClick()
        waitForIdle()
        val margin = with(density) { 16.dp.toPx() }
        val bubble = bubble("b")
        val back = word("Back")
        val skip = word("Skip")
        val next = button("Next").fetchSemanticsNode().boundsInRoot
        assertTrue(abs(back.left - (bubble.left + margin)) <= 1f, "Back's word is ${back.left - bubble.left}px in from the bubble's edge, not ${margin}px")
        assertTrue(back.right < skip.left && skip.right < next.left, "the row is not Back, Skip, Next from left to right: $back, $skip, $next")
        assertTrue(next.right - back.right > bubble.width / 2, "Back is still beside Next")
    }

    @Test
    fun backIsAtTheStartOnTheRightInRightToLeft() = runComposeUiTest {
        tour("a", "b", "c", rtl = true, showSkip = true)
        button("Next").performClick()
        waitForIdle()
        val margin = with(density) { 16.dp.toPx() }
        val bubble = bubble("b")
        val back = word("Back")
        val skip = word("Skip")
        val next = button("Next").fetchSemanticsNode().boundsInRoot
        assertTrue(abs(bubble.right - margin - back.right) <= 1f, "Back's word is ${bubble.right - back.right}px in from the bubble's right edge, not ${margin}px")
        assertTrue(back.left > skip.right && skip.left > next.right, "the row is not Back, Skip, Next from right to left: $back, $skip, $next")
        assertTrue(back.left - next.left > bubble.width / 2, "Back is still beside Next")
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
        // Its edge stands at the bubble's margin, as Next's does at the other end, not on the bubble's own.
        assertTrue(abs((back.left - bubble.left) - (bubble.right - next.right)) <= 1f, "Back's edge is ${back.left - bubble.left}px in, Next's ${bubble.right - next.right}px")
    }

    @Test
    fun aTourOfOneStepSaysGotIt() = runComposeUiTest {
        tour("only")
        onNodeWithText("Got it").assertExists("a lone step has no Got it")
        assertEquals(0, onAllNodes(hasText("Done")).fetchSemanticsNodes().size, "a lone step says Done, which ends a tour it is not part of")
        assertEquals(0, onAllNodes(hasText("Back")).fetchSemanticsNodes().size, "a lone step has a Back to nowhere")
    }
}
