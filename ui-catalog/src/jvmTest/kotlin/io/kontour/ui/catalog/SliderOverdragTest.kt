package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.selection.RangeSlider
import io.kontour.ui.components.selection.Slider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A finger dragged off the end of a slider has to come back level with the
 * thumb before the thumb moves again — asked for as *"if the slider isn't the
 * full width of the screen, then if i drag past the edge of the slider and then
 * drag back, the slider doesn't wait for my finger/mouse to come back in line
 * with it, it just starts moving. that affects the range slider too"*.
 *
 * The rubber band past the end used to be the only record of how far the
 * finger had gone, and a band stops growing at its limit: past it, a finger
 * turning round set the value moving with the finger still well off the end.
 */
class SliderOverdragTest {

    @Test
    fun aSliderWaitsForTheFingerToComeBack() = overdrag(PointerType.Touch, range = false)

    @Test
    fun aSliderWaitsForTheMouseToComeBack() = overdrag(PointerType.Mouse, range = false)

    @Test
    fun aRangeSlidersEndThumbWaitsForTheFingerToComeBack() = overdrag(PointerType.Touch, range = true)

    /**
     * With a `minDistance`, a thumb pushed to an end stops that much short of it
     * — *"that slider fix didn't work for range sliders that have a minimum
     * distance between each other"*. Measured from the track's end, the wait
     * ended that much early; it is measured from the thumb.
     */
    @Test
    fun aRangeSlidersThumbHeldShortOfTheEndWaitsForTheFingerToComeBack() {
        var range by mutableStateOf(0.2f..0.6f)
        var bounds = Rect.Zero
        Scene(width = 800, height = 200) {
            Box(Modifier.fillMaxSize().background(Color.White)) {
                RangeSlider(
                    value = range,
                    onValueChange = { range = it },
                    minDistance = 0.2f,
                    modifier = Modifier.align(Alignment.Center).width(140.dp).reportBounds { bounds = it },
                )
            }
        }.use { scene ->
            scene.frames(3)
            val grab = bounds.alongX(0.2f)
            val farPast = Offset(bounds.right + bounds.width, grab.y)
            scene.press(grab)
            scene.frame()
            stepTo(scene, grab, farPast, PointerType.Touch)
            // The fixture: the start thumb has pushed the end one to the end and
            // stopped the minimum distance short of it.
            assertEquals(0.8f, range.start, 0.01f, "the start thumb is not held short of the end")
            val stillPast = Offset(bounds.right + bounds.width * 0.3f, grab.y)
            stepTo(scene, farPast, stillPast, PointerType.Touch)
            assertEquals(0.8f, range.start, 0.01f, "coming back but still past the end, it moved")
            // Over the track, but not yet back to the thumb.
            val between = bounds.alongX(0.9f)
            stepTo(scene, stillPast, between, PointerType.Touch)
            assertEquals(0.8f, range.start, 0.01f, "the finger at 90% is not back to the thumb at 80%, and it moved")
            val backIn = bounds.alongX(0.7f)
            stepTo(scene, between, backIn, PointerType.Touch)
            // 70% of the slider's bounds, which are wider than its track by the
            // thumb: about two thirds of the track.
            assertTrue(range.start in 0.6f..0.75f, "back to 70%, the start thumb is at ${range.start}")
            scene.release(backIn)
            scene.frames(2)
        }
    }

    private fun overdrag(pointer: PointerType, range: Boolean) {
        var value by mutableStateOf(0.5f)
        var upper by mutableStateOf(0.6f)
        var bounds = Rect.Zero
        Scene(width = 800, height = 200) {
            Box(Modifier.fillMaxSize().background(Color.White)) {
                // A slider in the middle of the screen, a third of its width:
                // there is room either side to drag past its ends.
                val modifier = Modifier.align(Alignment.Center).width(140.dp).reportBounds { bounds = it }
                if (range) {
                    RangeSlider(value = 0.2f..upper, onValueChange = { upper = it.endInclusive }, modifier = modifier)
                } else {
                    Slider(value = value, onValueChange = { value = it }, modifier = modifier)
                }
            }
        }.use { scene ->
            scene.frames(3)
            val grab = bounds.alongX(if (range) 0.6f else 0.5f)
            val farPast = Offset(bounds.right + bounds.width, grab.y)
            val stillPast = Offset(bounds.right + bounds.width * 0.3f, grab.y)
            val backIn = bounds.alongX(0.7f)
            val read = { if (range) upper else value }

            scene.press(grab, pointer)
            scene.frame()
            stepTo(scene, grab, farPast, pointer)
            assertEquals(1f, read(), "dragged far past the end, it is not at its end")
            // The fixture: the finger has gone well past the end, a whole track's width.
            assertTrue(farPast.x - bounds.right > bounds.width * 0.9f)

            stepTo(scene, farPast, stillPast, pointer)
            assertEquals(1f, read(), "coming back but still past the end, it moved off its end")

            stepTo(scene, stillPast, backIn, pointer)
            val back = read()
            assertTrue(back < 0.8f && back > 0.6f, "back over the track at 70%, it is at $back")
            scene.release(backIn, pointer)
            scene.frames(2)
        }
    }

    private fun stepTo(scene: Scene, from: Offset, to: Offset, pointer: PointerType) {
        repeat(12) { i ->
            val t = (i + 1) / 12f
            scene.move(Offset(from.x + (to.x - from.x) * t, from.y), pointer)
            scene.frame()
        }
    }
}
