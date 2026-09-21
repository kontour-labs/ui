package io.kontour.ui.catalog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.datetime.WheelPicker
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The drum reports the row that is in the band, not the one below it.
 *
 * Reported from a phone: *"in the 'AM/PM' picker in your time field, switching it
 * to 'AM' just selects 'PM', even if 'AM' is actually in the centre"*.
 *
 * It did. `centredIndex` was `firstVisibleItemIndex` plus one whenever the scroll
 * offset was anything but exactly zero — a correction applied past any fraction of
 * a row rather than past half of one. A two-item drum is the worst case and the one
 * that was reported: `n` rows give `(n - 1)` rows of travel, so two items give one,
 * and "the offset is not zero" was true across almost the whole range. The drum said
 * `PM` everywhere except pixel-exact zero, `TimePicker` committed it, and then
 * `selected` and `centredIndex` agreed so nothing corrected it.
 *
 * ### The arithmetic these two tests are aimed at
 *
 * At the scene's density of 2, a 40dp row is 80px. Three visible rows is a 240px
 * box, padded by one row (80px) at each end, so the drum's centre line is at 120px
 * and row `i` sits with its centre at `80 - scroll + i * 80 + 40`. `PM` centred is
 * a scroll of 80; `AM` is nearest the band from a scroll of 39 downwards. So a drag
 * that leaves the scroll at about 25px is unambiguously showing `AM` — and is
 * exactly where the old expression said `PM`.
 */
class WheelCentreTest {

    /**
     * Held, mid-drag: what the drum says while the finger is still on it.
     *
     * Dragged **off** `AM` rather than toward it, which is what makes the case
     * independent of touch slop. Slop eats the first eighteen-odd pixels of a
     * gesture and there is no way to read the drum's scroll offset from out here,
     * so a drag aimed at a particular offset is a drag aimed at an offset plus an
     * unknown. Aiming at the *region* instead: any travel that leaves the drum
     * between one pixel and half a row still shows `AM` in the band, so a 38px
     * drag lands in it for any slop from nothing to 37px.
     *
     * Two phases, because the first assertion alone cannot tell "it reported AM"
     * from "it never moved". The second carries the drum well past the boundary
     * and insists it reports `PM` there.
     */
    @Test
    fun aTwoItemDrumReportsTheRowUnderTheBandWhileItIsBeingHeld() {
        var selected by mutableIntStateOf(0)
        var bounds = Rect.Zero

        Scene(width = 200, height = 300) {
            Box(Modifier.fillMaxSize()) {
                WheelPicker(
                    items = Meridiems,
                    selected = selected,
                    onSelectedChange = { selected = it },
                    label = { it },
                    visibleItems = 3,
                    itemHeight = 40.dp,
                    modifier = Modifier.reportBounds { bounds = it },
                )
            }
        }.use { scene ->
            scene.frames(4)
            assertTrue(bounds.height > 0f, "the wheel never reported a size")
            assertEquals(0, selected, "the drum did not start on AM")

            val from = bounds.alongY(0.5f)
            scene.press(from)
            // Up, which turns the drum forward — less than half a row of travel
            // whatever slop takes out of it.
            scene.move(Offset(from.x, from.y - 38f))
            scene.frames(2)

            assertEquals(
                0,
                selected,
                "the drum has moved less than half a row off AM, so AM is still " +
                    "the row in the highlight band — and it reported PM. This is " +
                    "the phone report: switching it to AM just selects PM.",
            )

            scene.move(Offset(from.x, from.y - 200f))
            scene.frames(2)
            assertEquals(
                1,
                selected,
                "carried well past the boundary the drum still said AM, which " +
                    "means the drag never reached it and the assertion above " +
                    "proved nothing",
            )

            scene.release(Offset(from.x, from.y - 200f))
            scene.frames(8)
        }
    }

    /**
     * And released: where the drum settles, which is the sharper form of the report.
     *
     * A mouse drag goes through the outer `draggable` rather than the list, and that
     * path settles with `animateScrollToItem(centredIndex)`. So the wrong value did
     * not merely get reported, it got *travelled to* — let go two thirds of the way
     * to AM and the wheel animated back to PM.
     */
    @Test
    fun aDragLetGoTwoThirdsOfTheWayToAmSettlesOnAm() {
        var selected by mutableIntStateOf(1)
        var bounds = Rect.Zero

        Scene(width = 200, height = 300) {
            Box(Modifier.fillMaxSize()) {
                WheelPicker(
                    items = Meridiems,
                    selected = selected,
                    onSelectedChange = { selected = it },
                    label = { it },
                    visibleItems = 3,
                    itemHeight = 40.dp,
                    modifier = Modifier.reportBounds { bounds = it },
                )
            }
        }.use { scene ->
            scene.frames(4)
            val from = bounds.alongY(0.5f)
            scene.drag(
                from = from,
                to = Offset(from.x, from.y + 55f),
                steps = 12,
                pointer = PointerType.Mouse,
            )
            scene.frames(30)
        }

        assertEquals(
            0,
            selected,
            "let go two thirds of the way toward AM, the drum settled on PM — it " +
                "animated back to the value the old rounding had already reported",
        )
    }

    /**
     * A longer drum keeps landing where it was sent, which is what must not regress.
     *
     * The two-item cases above are the defect; this is the ordinary case, and it is
     * here because rounding to nearest is a change to every wheel and not just the
     * short one. Twenty-four rows, three rows of travel, and the value follows.
     */
    @Test
    fun aTwentyFourRowDrumStillTravelsWhereItIsDragged() {
        var selected by mutableIntStateOf(12)
        var bounds = Rect.Zero

        Scene(width = 200, height = 400) {
            Box(Modifier.fillMaxSize()) {
                WheelPicker(
                    items = (0..23).toList(),
                    selected = selected,
                    onSelectedChange = { selected = it },
                    label = { it.toString().padStart(2, '0') },
                    itemHeight = 40.dp,
                    modifier = Modifier.reportBounds { bounds = it },
                )
            }
        }.use { scene ->
            scene.frames(4)
            val from = bounds.alongY(0.5f)
            // Three rows down: 240px at 80px a row.
            scene.drag(from = from, to = Offset(from.x, from.y + 240f), steps = 24)
            scene.frames(12)
        }

        assertEquals(
            9,
            selected,
            "three rows of drag from 12 should land on 9",
        )
    }

    private companion object {
        /** The `TimePicker` column the report came from, verbatim. */
        val Meridiems = listOf("AM", "PM")
    }
}
