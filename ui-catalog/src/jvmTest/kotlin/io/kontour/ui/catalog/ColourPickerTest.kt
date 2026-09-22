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
     * A hue set on the track survives a drag in the area afterwards.
     *
     * Reported, reproduced, and unexplained for a round: move the hue track, then
     * drag the spectrum, and the hue reverts to the one the picker was *born*
     * with. Never to an intermediate value, and always on the first frame of the
     * area gesture.
     *
     * ### Why nothing here caught it
     *
     * Every other case in this file exercises exactly one control per scene.
     * `theHueSurvivesATripThroughBlack` is the closest, and it starts from the
     * birth colour — so the area's hue and the picker's hue are the same number
     * and a stale one is indistinguishable from a fresh one. It takes two
     * controls in one scene to tell them apart, and until now no test used two.
     *
     * ### What it is
     *
     * `SaturationValueArea` passed its drag's `onStart` as `::report`, a callable
     * reference to a local function. A lambda literal has its transitive captures
     * recorded as memoization keys, so it is rebuilt when the hue changes; a
     * reference to a local function has no captures at the reference site, so it
     * is built once at first composition and handed back for the life of the
     * picker — holding the `hsv` parameter it was born with. `onStart` fires once
     * per gesture, on the press, which is the single bad emission; `onDelta` is a
     * lambda and faithfully continues from whatever that write left behind.
     *
     * `OwnedDrag`'s KDoc records the same bug in `RangeSlider`, in the same
     * words, with "range" for "hue".
     */
    @Test
    fun aHueSetOnTheTrackSurvivesADragInTheArea() {
        val start = Color(0xFF1E88E5)
        val born = start.toHsv().hue
        val trackY = (16 + 200 + 10) * 2f

        // The track on its own first, to learn where it leaves the hue. The
        // second scene repeats this drag exactly, so the number carries over.
        val trackOnly = picker(start) {
            // Leftward, well clear of either end.
            drag(
                from = Offset(x = (16 + 160) * 2f, y = trackY),
                to = Offset(x = (16 + 60) * 2f, y = trackY),
                steps = 8,
            )
        }
        val hueTrackLeft = trackOnly.last().toHsv().hue
        assertTrue(
            abs(hueTrackLeft - born) > 20f,
            "the track drag only moved the hue from $born to $hueTrackLeft, which " +
                "is not far enough for a revert to be visible. This test is " +
                "measuring the wrong thing.",
        )

        // And now the same drag with an area gesture after it, in one scene.
        val both = picker(start) {
            drag(
                from = Offset(x = (16 + 160) * 2f, y = trackY),
                to = Offset(x = (16 + 60) * 2f, y = trackY),
                steps = 8,
            )
            frames(2)
            drag(from = areaCentre, to = areaCentre + Offset(40f, 0f), steps = 6)
        }

        // And the same thing as a tap, which is the gesture with no `onDelta`
        // after it to paper over a bad first emission.
        val tapped = picker(start) {
            drag(
                from = Offset(x = (16 + 160) * 2f, y = trackY),
                to = Offset(x = (16 + 60) * 2f, y = trackY),
                steps = 8,
            )
            frames(2)
            tap(areaCentre)
        }
        val afterTap = tapped.last().toHsv().hue
        assertTrue(
            abs(afterTap - hueTrackLeft) < 2f,
            "tapping the area after moving the hue track left the hue at " +
                "$afterTap, where the track had put it at $hueTrackLeft. A tap is " +
                "one emission with nothing after it, so it is the gesture that " +
                "shows a stale first write rather than hiding it.",
        )

        val landed = both.last().toHsv().hue
        assertTrue(
            abs(landed - born) > 20f,
            "after moving the hue track and then dragging the area, the hue is " +
                "$landed — the colour this picker was born holding was $born. The " +
                "area handed back a hue it captured at first composition, so one " +
                "gesture undid the one before it.",
        )
        assertTrue(
            abs(landed - hueTrackLeft) < 2f,
            "the area drag moved the hue from $hueTrackLeft to $landed. Dragging " +
                "the saturation and value axes must not touch the third one at " +
                "all — that is the whole reason the picker keeps an `Hsv` rather " +
                "than round-tripping a `Color`.",
        )
    }

    /**
     * And the palette, which carries the identical gesture wiring.
     *
     * Worth its own case rather than trusting the spectrum's: the two areas are
     * separate composables with separately written drag handlers, and the second
     * one was copied from the first — including what was wrong with it.
     */
    @Test
    fun aHueSetOnTheTrackSurvivesATapInThePalette() {
        val start = Color(0xFF1E88E5)
        val born = start.toHsv().hue
        val trackY = (16 + 200 + 10) * 2f

        val seen = picker(start, mode = ColourPickerMode.Palette) {
            drag(
                from = Offset(x = (16 + 160) * 2f, y = trackY),
                to = Offset(x = (16 + 60) * 2f, y = trackY),
                steps = 8,
            )
            frames(2)
            tap(areaCentre)
        }

        val landed = seen.last().toHsv().hue
        assertTrue(
            abs(landed - born) > 20f,
            "after moving the hue track and then tapping the palette, the hue is " +
                "$landed against a birth hue of $born",
        )
    }

    /**
     * The palette's marker sits on the swatch that was tapped, on every row.
     *
     * Reported as the grid's touch targets not lining up with its swatches —
     * "sometimes tapping one taps the one above it". The hit test was innocent:
     * the colour the grid reported was always the colour under the finger. What
     * was one cell out was the **ring**, and a ring on the wrong swatch is
     * indistinguishable from a tap on the wrong swatch.
     *
     * ### An exact inverse, computed inexactly
     *
     * A cell writes `value = 1 − row / rows` and this read it back as
     * `((1 − value) × rows).toInt()`. That is the exact inverse in real
     * arithmetic and not in binary: `1f − 0.8f` is `0.19999999`, five times that
     * is `0.99999994`, and truncating it gives row **zero**. Rows one and two
     * fell short of their own integers; rows zero, three and four landed on
     * them. Two rows in five is the "sometimes".
     *
     * The column axis had the same shape and never showed it, because eighths
     * are exact in binary *and* because it rounded rather than truncated. This
     * sweeps every row, so the two that were wrong fail here and the three that
     * were right cannot start being wrong.
     *
     * ### Why the marker and not the callback
     *
     * `aHueSetOnTheTrackSurvivesATapInThePalette` and
     * `thePaletteGridPicksAColourAndKeepsItsHueTrack` both watch what the grid
     * emits, and both passed throughout. Nothing but pixels can see this.
     */
    @Test
    fun thePaletteMarkerLandsOnTheSwatchThatWasTapped() {
        var colour by mutableStateOf(Color(0xFF1E88E5))
        val wrong = mutableListOf<String>()

        Scene(width = 704, height = 900, reduceMotion = true) {
            Box(Modifier.fillMaxSize()) {
                ColourPicker(
                    colour = colour,
                    onColourChange = { colour = it },
                    modifier = Modifier.padding(16.dp).width(320.dp),
                    mode = ColourPickerMode.Palette,
                    swatches = emptyList(),
                    valueField = false,
                )
            }
        }.use { scene ->
            scene.frames(4)
            // A different column per row, so the across axis is swept as well
            // rather than trusted — it is the one that was already right.
            for (row in 0 until PaletteRows) {
                val column = row + 1
                scene.tap(
                    Offset(
                        x = GridLeft + (column + 0.5f) * Cell,
                        y = GridTop + (row + 0.5f) * Cell,
                    )
                )
                val marker = scene.frames(4).markerCentre()
                if (marker == null) {
                    wrong += "row $row: no marker drawn inside the grid at all"
                    continue
                }
                val onRow = ((marker.y - GridTop) / Cell).toInt()
                val onColumn = ((marker.x - GridLeft) / Cell).toInt()
                if (onRow != row || onColumn != column) {
                    wrong += "tapped ($row, $column), marked ($onRow, $onColumn)"
                }
            }
        }

        assertTrue(
            wrong.isEmpty(),
            "the palette marked a different swatch from the one that was " +
                "tapped — ${wrong.joinToString("; ")}. The marker is the only " +
                "thing on screen that says which swatch is chosen, so a marker " +
                "one cell out reads as a grid whose targets are a row off.",
        )
    }

    /**
     * Where the white ring is, or null if it is not in the grid.
     *
     * The ring is opaque white and no cell in the grid can be: the least
     * saturated column is an eighth of the way in, so every cell has a channel
     * at or below 224. Inset by a corner radius so the clipped corners — which
     * show the page behind, and the page is white — cannot be mistaken for it.
     */
    private fun java.awt.image.BufferedImage.markerCentre(): Offset? {
        var x = 0L
        var y = 0L
        var found = 0
        for (row in (GridTop + Inset).toInt() until (GridTop + Rows * Cell - Inset).toInt()) {
            for (column in
                (GridLeft + Inset).toInt() until (GridLeft + Columns * Cell - Inset).toInt()
            ) {
                if (getRGB(column, row) == White) {
                    x += column
                    y += row
                    found++
                }
            }
        }
        return if (found < LeastRing) null else Offset(x.toFloat() / found, y.toFloat() / found)
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

    private companion object {
        /**
         * The grid, in the pixels this scene puts it in.
         *
         * 320dp of picker inset 16dp at density two, and the grid is the first
         * thing in the column — so it starts at 32px in and 32px down, is 640px
         * across, and its 1.6 aspect makes forty 80px squares.
         */
        const val GridLeft = 32f
        const val GridTop = 32f
        const val Cell = 80f
        const val Columns = 8
        const val Rows = 5
        const val PaletteRows = 5

        /** A corner radius, to keep the clipped corners out of the scan. */
        const val Inset = 20f

        val White: Int = 0xFFFFFFFF.toInt()

        /**
         * Less than a ring and far more than a stray pixel.
         *
         * A 7dp ring with a 2dp stroke at density two is about 350 opaque
         * pixels before antialiasing takes the edges off either side.
         */
        const val LeastRing = 60
    }
}
