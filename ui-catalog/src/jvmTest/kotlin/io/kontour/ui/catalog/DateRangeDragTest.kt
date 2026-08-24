package io.kontour.ui.catalog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import io.kontour.ui.components.datetime.DateRangePicker
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A range is dragged out in one gesture, in either direction.
 *
 * Both halves were built in an earlier round and neither had ever been driven by
 * a pointer — `CalendarMonth` grew `onDragSelect` and `DateRangePicker` grew the
 * ordering that goes with it, and the only thing standing behind either was that
 * the code reads correctly. This is the gesture actually being performed.
 *
 * Backwards matters more than it sounds. Dragging from the 20th to the 16th
 * means the 16th to the 20th — a range whose `start` is the later date is not a
 * range, and the natural implementation reports the two dates in the order the
 * finger visited them.
 */
class DateRangeDragTest {

    @Test
    fun draggingForwardsSelectsTheRange() {
        val picked = pickByDragging(from = 10, to = 14)
        assertEquals(LocalDate(2026, 8, 10) to LocalDate(2026, 8, 14), picked)
    }

    @Test
    fun draggingBackwardsSelectsTheSameRange() {
        val picked = pickByDragging(from = 20, to = 16)
        assertEquals(LocalDate(2026, 8, 16) to LocalDate(2026, 8, 20), picked)
    }

    /**
     * Drags from one day of August 2026 to another and returns what came back.
     *
     * The cells are found the way `CalendarMonth`'s own hit test finds them:
     * seven equal columns, every row one cell tall and square. August 2026 lays
     * out as five leading blanks and six rows, so day *d* is at cell `d + 4`.
     */
    private fun pickByDragging(from: Int, to: Int): Pair<LocalDate?, LocalDate?> {
        var start by mutableStateOf<LocalDate?>(null)
        var end by mutableStateOf<LocalDate?>(null)
        var bounds = Rect.Zero

        Scene(width = 700, height = 800) {
            Box(Modifier.fillMaxSize()) {
                DateRangePicker(
                    start = start,
                    end = end,
                    onRangeSelected = { s, e -> start = s; end = e },
                    // Which month is shown follows `start ?: today`, and there
                    // is no start yet. Without this the picker opens on its own
                    // fallback month and the arithmetic below is about the
                    // wrong grid.
                    today = LocalDate(2026, 8, 1),
                    modifier = Modifier.reportBounds { bounds = it },
                )
            }
        }.use { scene ->
            scene.frames(6)
            assertTrue(bounds.width > 0f, "the picker never reported a size")
            scene.drag(from = cell(bounds, from), to = cell(bounds, to), steps = 24)
            scene.frames(4)
        }

        return start to end
    }

    private fun cell(bounds: Rect, day: Int): Offset {
        val cellSize = bounds.width / 7f
        // The month grid is the bottom six rows; everything above it is the
        // picker's own header and the weekday initials.
        val gridTop = bounds.bottom - Rows * cellSize
        val index = day - 1 + LeadingBlanks
        val row = index / 7
        val column = index % 7
        return Offset(
            bounds.left + (column + 0.5f) * cellSize,
            gridTop + (row + 0.5f) * cellSize,
        )
    }

    private companion object {
        /** August 2026 starts on a Saturday, so five cells lead. */
        const val LeadingBlanks = 5
        const val Rows = 6
    }
}
