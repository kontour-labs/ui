package io.kontour.ui.components.datetime

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.LayoutDirection
import io.kontour.ui.theme.KontourTheme
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A picker given no icons still has its way to the next month.
 *
 * The paging arrows were the caller's to pass, and a picker given none had no
 * buttons: it paged by a swipe nobody could see, and with a mouse not at all.
 * Reported, of a `DatePicker` in a dialog, as *"there's no left/right
 * buttons"*. They default to the library's chevrons now, as the other
 * structural glyphs do, and point back and on in the reading direction.
 */
@OptIn(ExperimentalTestApi::class)
class DatePickerArrowsTest {

    @Test
    fun aDatePickerGivenNoIconsHasItsArrows() = runComposeUiTest {
        var month = LocalDate(2026, 9, 1)
        setContent {
            KontourTheme {
                val navigation = rememberCalendarNavigationState(LocalDate(2026, 9, 26))
                month = navigation.visibleMonth
                DatePicker(value = null, onValueChange = {}, navigation = navigation)
            }
        }
        waitForIdle()
        onNodeWithContentDescription("Next month").performClick()
        waitForIdle()
        assertEquals(LocalDate(2026, 10, 1), month, "the next-month button did not page")
        onNodeWithContentDescription("Previous month").performClick()
        waitForIdle()
        assertEquals(LocalDate(2026, 9, 1), month, "the previous-month button did not page")
    }

    @Test
    fun theRangeAndDateTimePickersHaveTheirsToo() = runComposeUiTest {
        setContent {
            KontourTheme {
                DateRangePicker(start = null, end = null, onRangeChange = { _, _ -> })
                DateTimePicker(
                    value = LocalDateTime(LocalDate(2026, 9, 26), LocalTime(9, 30)),
                    onValueChange = {},
                )
            }
        }
        waitForIdle()
        assertEquals(2, onAllNodesWithContentDescription("Previous month").fetchSemanticsNodes().size)
        assertEquals(2, onAllNodesWithContentDescription("Next month").fetchSemanticsNodes().size)
    }

    @Test
    fun nullStillLeavesAnArrowOut() = runComposeUiTest {
        setContent {
            KontourTheme {
                DatePicker(value = null, onValueChange = {}, previousIcon = null)
            }
        }
        waitForIdle()
        assertEquals(0, onAllNodesWithContentDescription("Previous month").fetchSemanticsNodes().size)
        assertEquals(1, onAllNodesWithContentDescription("Next month").fetchSemanticsNodes().size)
    }

    /**
     * The way back points back: left in a left-to-right layout, right in a
     * right-to-left one, where it sits at the right-hand end. A chevron's open
     * side is where its ink runs tallest, so the glyph points away from it.
     */
    @Test
    fun theArrowsPointTheWayTheyGo() = runComposeUiTest {
        var direction by mutableStateOf(LayoutDirection.Ltr)
        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                KontourTheme { DatePicker(value = null, onValueChange = {}) }
            }
        }
        waitForIdle()
        assertEquals(Pointing.Left, pointing("Previous month"), "back, left to right")
        assertEquals(Pointing.Right, pointing("Next month"), "on, left to right")
        direction = LayoutDirection.Rtl
        waitForIdle()
        assertEquals(Pointing.Right, pointing("Previous month"), "back, right to left")
        assertEquals(Pointing.Left, pointing("Next month"), "on, right to left")
    }

    private enum class Pointing { Left, Right }

    private fun ComposeUiTest.pointing(description: String): Pointing {
        val image = onNodeWithContentDescription(description).captureToImage().toPixelMap()
        val ground = image[0, 0]
        fun ink(x: Int, y: Int): Boolean {
            val c = image[x, y]
            return abs(c.red - ground.red) + abs(c.green - ground.green) + abs(c.blue - ground.blue) +
                abs(c.alpha - ground.alpha) > 0.3f
        }
        val columns = (0 until image.width).filter { x -> (0 until image.height).any { y -> ink(x, y) } }
        assertTrue(columns.isNotEmpty(), "$description drew nothing")
        fun spread(x: Int): Int {
            val rows = (0 until image.height).filter { y -> ink(x, y) }
            return rows.last() - rows.first()
        }
        // The two outermost columns of ink, a pixel in from each so that
        // antialiasing does not decide it.
        val left = spread(columns.first() + 1)
        val right = spread(columns.last() - 1)
        assertTrue(left != right, "$description is as tall at both ends ($left)")
        return if (right > left) Pointing.Left else Pointing.Right
    }
}
