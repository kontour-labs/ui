package io.kontour.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composition
import androidx.compose.runtime.ExperimentalComposeRuntimeApi
import androidx.compose.runtime.currentComposer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.tooling.setObserver
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import io.kontour.ui.components.selection.FilterChip
import io.kontour.ui.components.selection.SegmentedControl
import io.kontour.ui.foundation.Text
import io.kontour.ui.nav.Tab
import io.kontour.ui.nav.TabBar
import io.kontour.ui.theme.KontourTheme
import io.kontour.ui.theme.Theme
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A label fading to its new colour is drawn again for each frame of the fade,
 * and composed once.
 *
 * A tab's label, a filter chip's and a segment's fade over 150ms when the
 * selection moves, and each read its colour in composition to hand it on: to
 * `LocalContentColour`, which recomposes everything under it when it changes,
 * or to `Text`. So every frame of a fade composed the label again, its row and
 * its text, for a colour the draw pass can change on its own.
 *
 * Counted rather than timed, as `RatingFillRecompositionTest` is. Each count is
 * of a whole change of selection, from the frame it is made to the frame the
 * last thing it moved has settled, so a cost moved somewhere else rather than
 * removed still counts.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalComposeRuntimeApi::class)
class LabelColourRecompositionTest {

    @Test
    fun aTabsLabelIsNotComposedForEachFrameOfItsFade() {
        var selected by mutableIntStateOf(0)
        val composed = countChange(change = { selected = 1 }) {
            TabBar {
                Tab(selected = selected == 0, onClick = { selected = 0 }, key = 0) { +"Departures" }
                Tab(selected = selected == 1, onClick = { selected = 1 }, key = 1) {
                    +Theme.icons.chevronBack
                    +"Arrivals"
                }
                Tab(selected = selected == 2, onClick = { selected = 2 }, key = 2) { Text("Alerts") }
            }
        }
        assertWithin(TabBudget, composed, "a tab bar's selection moving")
    }

    @Test
    fun aFilterChipsLabelIsNotComposedForEachFrameOfItsFade() {
        var selected by mutableStateOf(false)
        val composed = countChange(change = { selected = true }) {
            FilterChip(selected = selected, onSelectedChange = { selected = it }) {
                +Theme.icons.chevronBack
                +"Trains"
            }
        }
        assertWithin(ChipBudget, composed, "a filter chip turning on")
    }

    @Test
    fun aSegmentsLabelIsNotComposedForEachFrameOfItsFade() {
        var selected by mutableIntStateOf(0)
        val composed = countChange(change = { selected = 2 }) {
            SegmentedControl(listOf("Day", "Week", "Month"), selected, { selected = it })
        }
        assertWithin(SegmentedBudget, composed, "a segmented control's selection moving")
    }

    /** The scopes composed from [change] until everything it moved has settled. */
    private fun countChange(change: () -> Unit, content: @Composable () -> Unit): Int {
        var entered = 0
        runComposeUiTest {
            lateinit var composition: Composition
            mainClock.autoAdvance = false
            setContent {
                KontourTheme(reduceMotion = false) {
                    composition = currentComposer.composition
                    content()
                }
            }
            repeat(10) { mainClock.advanceTimeByFrame() }

            val scopes = ScopeCount()
            val watching = runOnUiThread { composition.setObserver(scopes) }
            repeat(2) { mainClock.advanceTimeByFrame() }
            scopes.entered = 0

            change()
            repeat(Frames) { mainClock.advanceTimeByFrame() }
            runOnUiThread { watching?.dispose() }
            assertTrue(watching != null, "the composition could not be watched, so this measured nothing")
            entered = scopes.entered
        }
        return entered
    }

    private fun assertWithin(budget: Int, composed: Int, what: String) {
        println("LabelColourRecompositionTest: $what composed $composed scopes")
        assertTrue(composed > 0, "$what composed nothing, so the change was never seen")
        assertTrue(
            composed <= budget,
            "$what composed $composed scopes over $Frames frames: a label's colour is being read in " +
                "composition again, so each frame of its fade composes it",
        )
    }

    private companion object {
        /** Long enough for the marker's spring to settle, as well as the fades. */
        const val Frames = 60

        /**
         * What one change honestly costs, with room: 31 for the tab bar, 58 for
         * the chip and 48 for the segmented control, measured. The fade read in
         * composition cost 134, 89 and 357. The chip's ground and edge still
         * fade in composition, which is most of what is left of its count.
         */
        const val TabBudget = 45
        const val ChipBudget = 70
        const val SegmentedBudget = 70
    }
}
