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
import kotlin.math.abs
import kotlin.test.assertTrue

/**
 * The heading stays with what it heads: on the type scale, at any width.
 *
 * The day numbers used to grow with their cells on a wide calendar, and the
 * weekday initials with them, after *"weekday indicators do not scale with text
 * size in any date component"*. The cells now stop at a phone's size — *"i've
 * always found the date picker to be really big and spaced out"* — so neither
 * grows, and the initials must not be left growing on their own over digits
 * that do not.
 *
 * Measured at two widths rather than against a number: the claim is that the
 * width makes no difference.
 */
class CalendarWeekdayScaleTest {

    @Test
    fun theWeekdayInitialsAreTheSameAtAnyWidth() {
        val narrow = weekdayInk(sceneWidth = 320)
        val wide = weekdayInk(sceneWidth = 1200)

        assertTrue(narrow > 0 && wide > 0, "no weekday initials were drawn to measure")
        assertTrue(
            abs(wide - narrow) <= 1,
            "the weekday initials are ${narrow}px tall in a narrow calendar and ${wide}px in a wide one",
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
