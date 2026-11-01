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
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Where a tour's Skip goes, which the app chooses: on to the last step, as it
 * always did and still does by default; out of the tour there and then; or on
 * to a step of its choosing. Asked for for 0.5.2 as *"i'd like the user to be
 * able to control where it skips to, because right now it only skips to the
 * last one. sometimes i only want to skip to a certain option, sometimes i want
 * to skip the whole tour entirely, including the last coachmark, etc. make sure
 * the current behaviour remains the default."*
 */
@OptIn(ExperimentalTestApi::class)
class CoachmarkTourSkipTest {

    /** Every step `navigate` was told, in order. */
    private val visited = mutableListOf<String>()

    private fun ComposeUiTest.tour(skipTo: CoachmarkSkip? = null): CoachmarkTour {
        val steps = arrayOf("a", "b", "c", "d")
        lateinit var tour: CoachmarkTour
        setContent {
            KontourTheme(reduceMotion = true) {
                tour = if (skipTo == null) {
                    rememberCoachmarkTour(*steps, showSkip = true) { visited += it }
                } else {
                    rememberCoachmarkTour(*steps, showSkip = true, skipTo = skipTo) { visited += it }
                }
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

    private fun ComposeUiTest.offersSkip(): Boolean = onAllNodes(hasText("Skip")).fetchSemanticsNodes().isNotEmpty()

    private fun ComposeUiTest.press(label: String) {
        button(label).performClick()
        waitForIdle()
    }

    /** Which steps, from the first to the last, offer Skip. */
    private fun ComposeUiTest.stepsOfferingSkip(tour: CoachmarkTour): List<String> {
        runOnUiThread { tour.start() }
        waitForIdle()
        val offering = mutableListOf<String>()
        while (true) {
            val step = tour.current ?: break
            if (offersSkip()) offering += step
            if (!tour.hasNext) break
            press("Next")
        }
        return offering
    }

    @Test
    fun byDefaultSkipGoesToTheLastStepFromEveryStepButIt() = runComposeUiTest {
        val tour = tour()
        press("Next")
        press("Skip")
        assertEquals("d", tour.current, "Skip did not go to the last step")
        assertTrue(tour.isRunning, "Skip ended a tour asked for nothing but Skip")
        assertEquals("d", visited.last(), "navigate was not told the step Skip went to")
        assertEquals(listOf("a", "b", "c"), stepsOfferingSkip(tour))
    }

    @Test
    fun endEndsTheTourFromAMiddleStep() = runComposeUiTest {
        val tour = tour(CoachmarkSkip.End)
        press("Next")
        assertEquals("b", tour.current)
        press("Skip")
        assertTrue(tour.isFinished, "Skip with End did not end the tour")
        assertFalse(tour.isRunning)
        assertFalse("d" in visited, "a tour skipped to its end went to its last step on the way")
        // The last step's Done already ends it, so it has no Skip of its own.
        assertEquals(listOf("a", "b", "c"), stepsOfferingSkip(tour))
    }

    @Test
    fun skippingToAStepGoesThereAndIsOfferedOnlyBeforeIt() = runComposeUiTest {
        val tour = tour(CoachmarkSkip.to("c"))
        press("Skip")
        assertEquals("c", tour.current, "Skip did not go to the step it was given")
        assertEquals("c", visited.last())
        assertEquals(listOf("a", "b"), stepsOfferingSkip(tour), "Skip was offered at or past the step it goes to")

        // On the step itself, skip() does nothing.
        runOnUiThread { tour.start() }
        waitForIdle()
        press("Next")
        press("Next")
        assertEquals("c", tour.current)
        runOnUiThread { tour.skip() }
        waitForIdle()
        assertEquals("c", tour.current, "skip() moved a tour already at the step it skips to")
    }

    /** Two steps on, or out of the tour where there are not two more. */
    @Test
    fun aRuleOfTheAppsOwn() = runComposeUiTest {
        val tour = tour(CoachmarkSkip { from, steps -> steps.getOrNull(steps.indexOf(from) + 2) })
        press("Skip")
        assertEquals("c", tour.current, "the rule said two steps on")
        assertEquals(listOf("a", "b", "c"), stepsOfferingSkip(tour))
        // From the last step back to the second, and two on from there.
        press("Back")
        press("Back")
        press("Skip")
        assertEquals("d", tour.current)
        press("Back")
        press("Skip")
        assertTrue(tour.isFinished, "the rule said to end the tour from the second-last step")
    }

    @Test
    fun aRuleThatGoesBackOffersNoSkip() = runComposeUiTest {
        val tour = tour(CoachmarkSkip { _, _ -> "a" })
        press("Next")
        assertFalse(offersSkip(), "Skip was offered where it would go backwards")
        runOnUiThread { tour.skip() }
        waitForIdle()
        assertEquals("b", tour.current, "skip() went backwards")
    }

    @Test
    fun skipToGoesForwardsOnly() = runComposeUiTest {
        val tour = tour()
        runOnUiThread { tour.skipTo("c") }
        waitForIdle()
        assertEquals("c", tour.current)
        assertEquals("c", visited.last(), "navigate was not told the step skipTo went to")
        runOnUiThread { tour.skipTo("a") }
        waitForIdle()
        assertEquals("c", tour.current, "skipTo went backwards")
        runOnUiThread { tour.skipTo("nowhere") }
        waitForIdle()
        assertEquals("c", tour.current, "skipTo went to a step the tour does not have")
    }
}
