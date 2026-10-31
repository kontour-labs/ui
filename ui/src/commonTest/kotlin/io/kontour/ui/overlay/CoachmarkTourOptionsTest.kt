package io.kontour.ui.overlay

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kontour.ui.theme.KontourTheme
import io.kontour.ui.theme.Theme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A tour that a stray tap does not end, and one that can be skipped — asked for
 * as *"have an option for it to be non-dismissable (this should be the default
 * for a tour)"* and *"an option to add a "skip" button to the coachmarks (which
 * takes you to the last one)"*.
 */
@OptIn(ExperimentalTestApi::class)
class CoachmarkTourOptionsTest {

    /** A small ghost button's pad and the bubble row's gap, read from the theme. */
    private var gap = 0.dp

    private fun ComposeUiTest.tour(dismissible: Boolean = false, showSkip: Boolean = false): CoachmarkTour {
        val steps = arrayOf("a", "b", "c")
        lateinit var tour: CoachmarkTour
        setContent {
            KontourTheme(reduceMotion = true) {
                gap = Theme.componentDefaults.buttonPaddingSmall + Theme.spacing.xs
                tour = rememberCoachmarkTour(*steps, dismissible = dismissible, showSkip = showSkip)
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
        waitForIdle()
        runOnUiThread { tour.start() }
        waitForIdle()
        return tour
    }

    /** A tap on the dim, far from the step and its bubble. */
    private fun ComposeUiTest.tapTheDim() {
        onRoot().performTouchInput {
            down(Offset(width - 10f, height - 10f))
            up()
        }
        waitForIdle()
    }

    private fun ComposeUiTest.button(label: String) = onNode(hasText(label) and hasClickAction())

    @Test
    fun byDefaultATapOnTheDimOrEscapeDoesNotEndIt() = runComposeUiTest {
        val tour = tour()
        tapTheDim()
        onRoot().performKeyInput { pressKey(Key.Escape) }
        waitForIdle()
        assertTrue(tour.isRunning, "a stray tap or Escape ended a tour that is not dismissible")
        assertEquals("a", tour.current)
        // The bubble's own way on still works.
        button("Next").performClick()
        waitForIdle()
        assertEquals("b", tour.current)
    }

    @Test
    fun aDismissibleTourEndsAtATapOnTheDim() = runComposeUiTest {
        val tour = tour(dismissible = true)
        tapTheDim()
        assertTrue(tour.isFinished, "a dismissible tour went on after a tap on the dim")
    }

    @Test
    fun skipGoesToTheLastStepAndIsNotOnIt() = runComposeUiTest {
        val tour = tour(showSkip = true)
        button("Skip").performClick()
        waitForIdle()
        assertEquals("c", tour.current, "Skip did not go to the last step")
        assertEquals(0, onAllNodes(hasText("Skip")).fetchSemanticsNodes().size, "the last step still offers Skip")
        button("Done").assertExists()
    }

    /**
     * Skip's word stands as far from Next as any ghost button's does from what
     * follows it: its own pad and the row's gap. When Skip and Back stood side
     * by side, each kept its invisible pad and there was twice of it between
     * their words; Back has gone to the start of the row since 0.5.2, and Skip
     * stands alone beside Next.
     */
    @Test
    fun skipStandsItsPadAndAGapFromNext() = runComposeUiTest {
        tour(showSkip = true)
        button("Next").performClick()
        waitForIdle()
        val skip = onAllNodes(hasText("Skip"), useUnmergedTree = true).fetchSemanticsNodes()
            .first { it.children.isEmpty() }.boundsInRoot
        val next = button("Next").fetchSemanticsNode().boundsInRoot
        val expected = with(density) { gap.toPx() }
        assertTrue(
            kotlin.math.abs(next.left - skip.right - expected) <= 2f,
            "Skip's word is ${next.left - skip.right}px from Next, not its pad and the row's gap, ${expected}px",
        )
    }

    @Test
    fun noSkipUnlessAskedFor() = runComposeUiTest {
        tour()
        assertEquals(0, onAllNodes(hasText("Skip")).fetchSemanticsNodes().size, "a tour offered Skip without being asked to")
    }
}
