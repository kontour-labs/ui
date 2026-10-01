package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.datetime.WheelPickerDefaults
import io.kontour.ui.components.text.SelectState
import io.kontour.ui.components.text.WheelSelect
import io.kontour.ui.components.text.rememberSelectState
import io.kontour.ui.interaction.FeedbackDispatcher
import io.kontour.ui.interaction.FeedbackIntent
import io.kontour.ui.interaction.LocalFeedback
import io.kontour.ui.overlay.OverlayHost
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A field that opens into a wheel: on a tap, to stay; on a drag, for as long as
 * the drum is turning.
 *
 * Asked for as *"a dropdown-like component where clicking it makes it expand
 * into a wheel picker, but dragging up and down on it while it's not expanded
 * makes it start wheel picking"*.
 */
class WheelSelectTest {

    private companion object {
        const val Density = 2f
        val Row = WheelPickerDefaults.ItemHeight.value * Density
    }

    private var field = Rect.Zero

    private class Rig(val scene: Scene, val value: () -> Int, val state: () -> SelectState, val felt: List<FeedbackIntent>)

    private fun rig(dragToPick: Boolean = true, start: Int = 5): Rig {
        var value by mutableStateOf(start)
        var held: SelectState? = null
        val felt = mutableListOf<FeedbackIntent>()
        val scene = Scene(width = 800, height = 1000, density = Density, reduceMotion = true) {
            CompositionLocalProvider(LocalFeedback provides FeedbackDispatcher { felt += it }) {
                Box(Modifier.fillMaxSize().background(Color.White)) {
                    OverlayHost(Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                            val state = rememberSelectState().also { held = it }
                            WheelSelect(
                                value = value,
                                options = (0..20).toList(),
                                onValueChange = { value = it },
                                dragToPick = dragToPick,
                                state = state,
                                modifier = Modifier.width(240.dp).reportBounds { field = it },
                            )
                        }
                    }
                }
            }
        }
        scene.frames(6)
        return Rig(scene, { value }, { held!! }, felt)
    }

    private fun Scene.settle(frames: Int = 60) = frames(frames)

    @Test
    fun aTapOpensTheWheelOverTheFieldAndItStaysOpen() {
        val rig = rig()
        rig.scene.use { scene ->
            scene.tap(field.center)
            scene.settle()
            assertTrue(rig.state().expanded, "a tap did not open the wheel")

            // The row below the middle — the middle is where the field's value
            // was — is the next value.
            scene.tap(Offset(field.center.x, field.center.y + Row))
            scene.settle()
            assertEquals(6, rig.value(), "a tap on the row under the middle did not turn to it")
            assertTrue(rig.state().expanded, "a wheel opened by a tap closed by itself")

            // And the value in the middle closes it.
            scene.tap(field.center)
            scene.settle()
            assertFalse(rig.state().expanded, "a tap on the middle row did not close the wheel")
            assertEquals(6, rig.value())
        }
    }

    @Test
    fun aDragOnTheClosedFieldTurnsTheWheelAndItFoldsAwayAgain() {
        val rig = rig()
        rig.scene.use { scene ->
            // Up by two and a bit rows: the drum turns forward. On the frame
            // clock alone, with no wall-clock pacing: the field's press wash
            // holds for a real-time floor when a press lets go, and a pause long
            // enough to reach it resumes that hold on another thread in this
            // harness, in the middle of the drag.
            val from = field.center
            val to = Offset(from.x, from.y - Row * 2.2f)
            scene.drag(from = from, to = to, steps = 24, release = false)
            assertTrue(rig.state().expanded, "the wheel did not open under the drag")
            scene.release(to)
            scene.settle(20)
            assertEquals(7, rig.value(), "dragged up two rows from 5, the value is ${rig.value()}")
            // The drum's own detent, not an imitation of it. Counted as at least
            // one: at frame-clock speed the shared rate floor keeps the rest.
            assertTrue(rig.felt.any { it == FeedbackIntent.Tick }, "no tick as the rows passed: ${rig.felt}")

            // Folds back into the field by itself once it has rested a moment.
            scene.settle(60)
            assertFalse(rig.state().expanded, "a wheel opened by a drag stayed open after it came to rest")
            assertEquals(7, rig.value())
        }
    }

    /**
     * A wheel opened by a drag takes another flick, and folds after the last.
     *
     * Asked for as *"if you start it by dragging it and then tap it again to keep
     * the wheel scrolling, it auto-closes? Because longer lists might require
     * multiple flicks"*. The fold was timed from the first drag's rest, so a
     * finger back on the wheel within the pause had it folded away underneath
     * it; and a flick that caught the drum still turning took its scroll from
     * the first drag, whose rest was then never reported, and the wheel never
     * folded at all.
     */
    @Test
    fun aWheelOpenedByADragTakesAnotherFlickAndFoldsAfterTheLast() {
        val rig = rig()
        rig.scene.use { scene ->
            val from = field.center
            val first = Offset(from.x, from.y - Row * 2.2f)
            scene.drag(from = from, to = first, steps = 24)
            scene.settle(5)
            assertTrue(rig.state().expanded, "the wheel did not open under the first drag")

            // Back on the wheel — its middle row is over the field — and up by
            // three more rows, then held there for longer than the pause.
            val second = Offset(from.x, from.y - Row * 3.2f)
            scene.drag(from = from, to = second, steps = 24, release = false)
            scene.settle(45)
            assertTrue(rig.state().expanded, "the wheel folded away under a finger on it")
            scene.release(second)
            scene.settle(20)
            assertEquals(10, rig.value(), "two drags of two and three rows from 5 left the value at ${rig.value()}")

            scene.settle(60)
            assertFalse(rig.state().expanded, "the wheel stayed open after the last flick came to rest")
            assertEquals(10, rig.value())
        }
    }

    /** The control: with the drag off, the same gesture turns nothing. */
    @Test
    fun withDragToPickOffTheSameDragDoesNothing() {
        val rig = rig(dragToPick = false)
        rig.scene.use { scene ->
            val from = field.center
            val to = Offset(from.x, from.y - Row * 2.2f)
            scene.drag(from = from, to = to, steps = 24, paceMillis = 0)
            scene.settle()
            assertEquals(5, rig.value())
            assertFalse(rig.state().expanded)
        }
    }
}
