package io.kontour.ui.overlay

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertEquals
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

    private fun ComposeUiTest.tour(vararg steps: String): CoachmarkTour {
        lateinit var tour: CoachmarkTour
        setContent {
            KontourTheme(reduceMotion = true) {
                tour = rememberCoachmarkTour(*steps)
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

    private fun ComposeUiTest.button(label: String) = onNode(hasText(label) and hasClickAction())

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

    @Test
    fun aTourOfOneStepSaysGotIt() = runComposeUiTest {
        tour("only")
        onNodeWithText("Got it").assertExists("a lone step has no Got it")
        assertEquals(0, onAllNodes(hasText("Done")).fetchSemanticsNodes().size, "a lone step says Done, which ends a tour it is not part of")
        assertEquals(0, onAllNodes(hasText("Back")).fetchSemanticsNodes().size, "a lone step has a Back to nowhere")
    }
}
