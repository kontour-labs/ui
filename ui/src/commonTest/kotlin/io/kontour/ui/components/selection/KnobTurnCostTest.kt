package io.kontour.ui.components.selection

import androidx.compose.runtime.Composition
import androidx.compose.runtime.ExperimentalComposeRuntimeApi
import androidx.compose.runtime.currentComposer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.tooling.setObserver
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import io.kontour.ui.components.ScopeCount
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * What a frame of turning a knob composes.
 *
 * A knob's value is its caller's, so a turn composes the knob once a frame
 * whatever it does: the value is a parameter, and its readout and its
 * semantics say it. What it should not do is compose a second time. The face
 * was laid out in a `BoxWithConstraints` for the one number it read off it —
 * the side the knob has room for — and that is a subcomposition whose content
 * took the value, so each frame of a turn composed the knob and then, in the
 * middle of measuring it, composed its face again: 180 scopes over a
 * thirty-frame turn, against 60 now. `Slider` and `RangeSlider` gave theirs up
 * for the same reason.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalComposeRuntimeApi::class)
class KnobTurnCostTest {

    @Test
    fun aFrameOfATurnComposesTheKnobOnce() = runComposeUiTest {
        var level by mutableFloatStateOf(0.2f)
        lateinit var composition: Composition
        mainClock.autoAdvance = false
        setContent {
            KontourTheme(reduceMotion = true) {
                composition = currentComposer.composition
                Knob(value = level, onValueChange = { level = it }, contentDescription = "Volume")
            }
        }
        repeat(4) { mainClock.advanceTimeByFrame() }
        val knob = onNodeWithContentDescription("Volume")

        val scopes = ScopeCount()
        val watching = runOnUiThread { composition.setObserver(scopes) }
        knob.performTouchInput { down(center) }
        // Past the knob's decision between a turn and a drag, and the frames
        // it takes to see what each scope reads.
        repeat(4) {
            knob.performTouchInput { moveBy(Offset(0f, -Step)) }
            mainClock.advanceTimeByFrame()
        }
        val from = level
        scopes.entered = 0

        repeat(Frames) {
            knob.performTouchInput { moveBy(Offset(0f, -Step)) }
            mainClock.advanceTimeByFrame()
        }
        val turned = scopes.entered
        knob.performTouchInput { up() }
        mainClock.advanceTimeByFrame()
        runOnUiThread { watching?.dispose() }

        assertTrue(watching != null, "the composition could not be watched, so this measured nothing")
        assertTrue(level > from, "the knob did not turn: it went from $from to $level")
        assertTrue(
            turned <= Frames * 3,
            "$Frames frames of turning composed $turned scopes, more than three a frame: the knob's face is " +
                "being composed again on every frame, apart from the knob, for the room it is laid out in",
        )
    }

    private companion object {
        const val Frames = 30

        /** Pixels a frame, up the knob: a steady turn that stays short of the top of the range. */
        const val Step = 4f
    }
}
