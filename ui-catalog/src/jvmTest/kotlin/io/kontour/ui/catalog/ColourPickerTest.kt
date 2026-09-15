package io.kontour.ui.catalog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.selection.ColourPicker
import io.kontour.ui.foundation.toHsv
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * What a colour picker has to remember, and what a drag in it has to do.
 *
 * Neither is visible in a golden: a picker at rest draws the same square whether
 * or not the square answers a finger, and the hue it is holding while it draws a
 * black is not on screen at all.
 */
class ColourPickerTest {

    /**
     * The middle of the area: 320dp wide, 16dp in, and the first thing in the
     * picker because this harness turns the mode switch and the field off.
     *
     * Written out rather than measured because the scene cannot ask a private
     * composable where it is — and a number that is wrong shows up immediately
     * as a test reporting no change at all rather than as a silent pass.
     */
    private val areaCentre = Offset(x = (16 + 160) * 2f, y = (16 + 90) * 2f)

    private fun picker(
        start: Color,
        block: Scene.() -> Unit,
    ): List<Color> {
        val seen = mutableListOf<Color>()
        var colour by mutableStateOf(start)
        Scene(width = 704, height = 900, reduceMotion = true) {
            Box(Modifier.fillMaxSize()) {
                ColourPicker(
                    colour = colour,
                    onColourChange = { colour = it; seen += it },
                    modifier = Modifier.padding(16.dp).width(320.dp),
                    swatches = emptyList(),
                    valueField = false,
                )
            }
        }.use { scene ->
            scene.frames(4)
            scene.block()
            scene.frames(4)
        }
        return seen
    }

    /** Far enough below the area's bottom edge that the value clamps at zero. */
    private val Reach = 260f
    private val Steps = 10

    @Test
    fun aDragAcrossTheAreaChangesTheColour() {
        val seen = picker(Color(0xFF1E88E5)) {
            drag(from = areaCentre, to = areaCentre + Offset(120f, 0f), steps = 10)
        }
        assertTrue(
            seen.isNotEmpty(),
            "dragging across the saturation-and-value area reported nothing. The " +
                "area is the whole of the spectrum mode, so this is the picker " +
                "not being operable at all rather than a detail of the gesture.",
        )
        assertTrue(
            seen.last().toHsv().saturation > seen.first().toHsv().saturation,
            "dragging to the right did not raise the saturation: it went from " +
                "${seen.first().toHsv().saturation} to ${seen.last().toHsv().saturation}. " +
                "Saturation is the horizontal axis.",
        )
    }

    /**
     * A press is a colour, without any travel.
     *
     * The area claims its drag on the **press** for this reason. With
     * `DragClaim.Movement` a tap on a colour would do nothing at all, because
     * nothing in the area is tappable underneath.
     */
    @Test
    fun aPressWithoutADragIsAlreadyAColour() {
        val seen = picker(Color(0xFF1E88E5)) {
            press(areaCentre + Offset(60f, 40f))
            frame()
            release(areaCentre + Offset(60f, 40f))
            frame()
        }
        assertTrue(
            seen.isNotEmpty(),
            "a press in the colour area with no travel reported nothing. Every " +
                "colour area on every platform picks the colour you put your " +
                "finger on.",
        )
    }

    /**
     * Drag to the bottom of the area and back, and the hue is where it was.
     *
     * The claim the picker's own KDoc makes, and the reason it keeps an `Hsv`
     * rather than reading one back out of the `Color`. At the bottom of the area
     * the value is zero and the colour is black, and **black has no hue in it** —
     * so a picker that re-derived the hue every frame would come back red.
     */
    @Test
    fun theHueSurvivesATripThroughBlack() {
        val start = Color(0xFF1E88E5)
        val hue = start.toHsv().hue
        val seen = picker(start) {
            // One gesture, down past the black edge and back. Two `drag`s would
            // not do: the second would press below the area, on the hue track.
            press(areaCentre)
            frame()
            repeat(Steps) { move(areaCentre + Offset(0f, Reach * (it + 1) / Steps)); frame() }
            repeat(Steps) {
                move(areaCentre + Offset(0f, Reach - Reach * (it + 1) / Steps))
                frame()
            }
            release(areaCentre)
            frame()
        }

        val black = seen.minByOrNull { it.toHsv().value }
        assertTrue(
            black != null && black.toHsv().value == 0f,
            "the drag never reached the bottom of the area, so this test did not " +
                "test anything — the darkest colour it saw was " +
                "${black?.toHsv()?.value} rather than a flat zero.",
        )
        val back = seen.last().toHsv().hue
        assertTrue(
            abs(back - hue) < 1f,
            "the hue came back as $back after a trip through black, having " +
                "started at $hue. A picker that reads its hue back out of the " +
                "colour loses it the moment the colour is black, and the whole " +
                "of the area then belongs to red.",
        )
    }
}
