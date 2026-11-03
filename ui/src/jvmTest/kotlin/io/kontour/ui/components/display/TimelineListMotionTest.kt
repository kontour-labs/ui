package io.kontour.ui.components.display

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A keyed `timelineList`'s stops move into place as the list changes, as a
 * `listSection`'s rows do, rather than jumping: an app wrapped the whole
 * `LazyListScope` to put `animateItem` on rows only the builder could reach.
 */
@OptIn(ExperimentalTestApi::class)
class TimelineListMotionTest {

    @Test
    fun aStopTakenAwayLetsTheOnesBelowSlideUp() = runComposeUiTest {
        val stops = mutableStateListOf("Perth", "McIver", "Claisebrook", "East Perth", "Mount Lawley")
        mainClock.autoAdvance = false
        setContent {
            KontourTheme(darkTheme = false) {
                LazyColumn {
                    timelineList(stops, key = { it }) { stop ->
                        item { label { androidx.compose.foundation.text.BasicText(stop, Modifier.testTag(stop)) } }
                    }
                }
            }
        }
        repeat(10) { mainClock.advanceTimeByFrame() }
        fun top(tag: String) = onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().first().boundsInRoot.top
        val before = top("Claisebrook")
        val above = top("McIver")

        stops.remove("McIver")
        repeat(4) { mainClock.advanceTimeByFrame() }
        val during = top("Claisebrook")
        repeat(60) { mainClock.advanceTimeByFrame() }
        val after = top("Claisebrook")

        assertTrue(after < before - 1f, "the stop below did not move up at all: $before, $after")
        assertTrue(
            during < before - 0.5f && during > after + 0.5f,
            "it jumped rather than moved: $before, then $during a few frames in, then $after (the row above was at $above)",
        )
    }
}
