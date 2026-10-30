package io.kontour.ui.foundation

import androidx.compose.runtime.Composition
import androidx.compose.runtime.ExperimentalComposeRuntimeApi
import androidx.compose.runtime.currentComposer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.tooling.setObserver
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import io.kontour.ui.components.ScopeCount
import io.kontour.ui.components.selection.SegmentedControl
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A segmented control's thumb travels without composing the control on the way.
 *
 * The thumb leans from where the marker is drawn, so the marker published that
 * rect for it — and to publish it, read its travelling rect in composition. So
 * every frame of every travel composed the group again, and with it every
 * segment: 736 scopes for two changes of selection, about eight a frame.
 *
 * A disabled control, so that the labels stay the one colour and the travel is
 * all there is to count; `LabelColourRecompositionTest` counts their fade.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalComposeRuntimeApi::class)
class IndicatorTravelRecompositionTest {

    @Test
    fun aTravellingThumbComposesNothingOnTheWay() = runComposeUiTest {
        var selected by mutableIntStateOf(0)
        lateinit var composition: Composition
        mainClock.autoAdvance = false
        setContent {
            KontourTheme(reduceMotion = false) {
                composition = currentComposer.composition
                SegmentedControl(listOf("Day", "Week", "Month", "Year"), selected, { selected = it }, enabled = false)
            }
        }
        repeat(10) { mainClock.advanceTimeByFrame() }

        val scopes = ScopeCount()
        val watching = runOnUiThread { composition.setObserver(scopes) }
        repeat(2) { mainClock.advanceTimeByFrame() }
        scopes.entered = 0

        for (to in listOf(3, 0)) {
            selected = to
            repeat(Frames) { mainClock.advanceTimeByFrame() }
        }
        runOnUiThread { watching?.dispose() }

        println("IndicatorTravelRecompositionTest: two travels composed ${scopes.entered} scopes")
        assertTrue(watching != null, "the composition could not be watched, so this measured nothing")
        assertTrue(scopes.entered > 0, "the new selection composed nothing, so the control never saw it")
        assertTrue(
            scopes.entered <= Budget,
            "two travels of the thumb composed ${scopes.entered} scopes over ${Frames * 2} frames: the marker's " +
                "travelling rect is being read in composition again, so every frame of it composes the control",
        )
    }

    private companion object {
        /** Long enough for the marker's spring to settle. */
        const val Frames = 40

        /**
         * Each change composing the control once, 104 for the two, with room.
         * A rect read in composition is 648.
         */
        const val Budget = 140
    }
}
