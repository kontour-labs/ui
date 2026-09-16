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
import io.kontour.ui.components.selection.ColourPickerMode
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
        valueField: Boolean = false,
        mode: ColourPickerMode = ColourPickerMode.Spectrum,
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
                    mode = mode,
                    swatches = emptyList(),
                    valueField = valueField,
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

    /**
     * The far end of the hue track is the far end, not the near one.
     *
     * The track's fraction used to be `hue % 360`, and `Track` reports a clamped
     * `0..1` — so the right edge emitted 360, the wrap mapped it to 0 and the
     * cursor teleported the width of the control while the colour under it did
     * not change at all. Red is both ends of the wheel, which is why it looked
     * like nothing and felt like a fault.
     */
    @Test
    fun theHueTrackDoesNotJumpBackAtItsFarEnd() {
        val start = Color(0xFF1E88E5)
        // Below the area: 180dp of area at 1.6 aspect, then the track.
        val trackY = (16 + 200 + 10) * 2f
        val seen = picker(start) {
            drag(
                from = Offset(x = (16 + 160) * 2f, y = trackY),
                to = Offset(x = (16 + 318) * 2f, y = trackY),
                steps = 12,
            )
        }

        assertTrue(seen.isNotEmpty(), "the drag along the hue track reported nothing")
        val hue = seen.last().toHsv().hue
        // Either end of the wheel is red, so the colour cannot tell the two
        // apart — the fraction can. Anything that wrapped lands near zero.
        assertTrue(
            hue > 180f,
            "dragging to the right-hand end of the hue track left the hue at " +
                "$hue. The far end wrapped back to the near one, which is the " +
                "cursor jumping the width of the track under a finger that has " +
                "not moved.",
        )
    }

    /**
     * Palette mode is a palette, and it still has a hue.
     *
     * It used to be neither. The mode guarded the area, the hue track *and* the
     * opacity track together, with nothing in the `else`, so `Palette` with no
     * swatches rendered a lone hex box and there was no way to choose a colour
     * at all. The grid replaces the area; everything under it stays.
     */
    @Test
    fun thePaletteGridPicksAColourAndKeepsItsHueTrack() {
        val start = Color(0xFF1E88E5)
        val picked = picker(start, mode = ColourPickerMode.Palette) {
            tap(areaCentre)
        }
        assertTrue(
            picked.isNotEmpty(),
            "tapping the middle of a palette reported no colour — the grid is " +
                "not there, or it is not answering",
        )

        val trackY = (16 + 200 + 10) * 2f
        val hues = picker(start, mode = ColourPickerMode.Palette) {
            drag(
                from = Offset(x = (16 + 160) * 2f, y = trackY),
                to = Offset(x = (16 + 60) * 2f, y = trackY),
                steps = 8,
            )
        }
        assertTrue(
            hues.isNotEmpty() && abs(hues.last().toHsv().hue - start.toHsv().hue) > 5f,
            "the hue track under a palette did not move the hue — a palette of " +
                "one hue is a column of greys, which is why both modes keep it",
        )
    }
}
