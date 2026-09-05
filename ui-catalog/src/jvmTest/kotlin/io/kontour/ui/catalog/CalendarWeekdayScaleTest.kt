package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import io.kontour.ui.components.datetime.CalendarMonth
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The heading grows with what it heads.
 *
 * A day cell is a seventh of the grid, and a calendar given more width grows its
 * digits in proportion so a wide picker is not small numbers adrift in large
 * circles. The row of weekday initials above them was left on the type scale, so
 * every width past a phone's drew large numbers under letters that had stopped
 * looking like a heading for them — reported as "weekday indicators do not scale
 * with text size in any date component".
 *
 * Both are now the same growth from the same cell size, which is why this
 * measures the initials at two widths rather than against a number: the claim is
 * that the header answers the grid, and nothing about the value it lands on.
 */
class CalendarWeekdayScaleTest {

    @Test
    fun theWeekdayInitialsGrowWithTheCells() {
        // A cell below `ReferenceCell` (44dp), so nothing grows.
        val narrow = weekdayInk(sceneWidth = 500)
        // Wide enough for the growth to reach its cap.
        val wide = weekdayInk(sceneWidth = 1200)

        assertTrue(narrow > 0 && wide > 0, "no weekday initials were drawn to measure")
        assertTrue(
            wide > narrow * 13 / 10,
            "the weekday initials are ${narrow}px tall in a narrow calendar and " +
                "${wide}px in one wide enough for the day numbers to reach their " +
                "largest — a header that stays put while what it heads grows stops " +
                "reading as one",
        )
    }

    /** The height, in pixels, of the topmost band of ink the calendar draws. */
    private fun weekdayInk(sceneWidth: Int): Int {
        var bounds = Rect.Zero
        var height = 0

        Scene(width = sceneWidth, height = 900) {
            Box(Modifier.fillMaxSize().background(Color.White)) {
                CalendarMonth(
                    month = LocalDate(2026, 8, 1),
                    isSelected = { false },
                    onSelectedChange = {},
                    modifier = Modifier.reportBounds { bounds = it },
                )
            }
        }.use { scene ->
            val image = scene.frames(6)
            assertTrue(bounds.width > 0f, "the calendar never reported a size")

            val left = bounds.left.toInt().coerceAtLeast(0)
            val right = bounds.right.toInt().coerceAtMost(image.width)
            fun rowHasInk(y: Int): Boolean {
                for (x in left until right) {
                    val rgb = image.getRGB(x, y)
                    if ((rgb shr 16 and 0xFF) < 160) return true
                }
                return false
            }

            // The first band from the top is the row of initials; the gap after
            // it is the padding above the first week.
            var y = bounds.top.toInt().coerceAtLeast(0)
            val bottom = bounds.bottom.toInt().coerceAtMost(image.height)
            while (y < bottom && !rowHasInk(y)) y++
            val top = y
            while (y < bottom && rowHasInk(y)) y++
            height = y - top
        }

        return height
    }
}
