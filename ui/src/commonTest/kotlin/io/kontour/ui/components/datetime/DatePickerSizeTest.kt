package io.kontour.ui.components.datetime

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kontour.ui.foundation.SystemIcons
import io.kontour.ui.theme.KontourTheme
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A date picker is about a phone's calendar wherever it is, and no taller than
 * its weeks need.
 *
 * Reported as *"i've always found the date picker to be really big and spaced
 * out, compared to a lot of our other components"*. Its cells were square and a
 * seventh of whatever width it had, up to 511dp, with the digits growing with
 * them: on a desktop, half a window of numbers at half as large again, and on a
 * phone six weeks of cells as tall as they were wide.
 *
 * At a density of 1, with June 2026, which starts on a Monday.
 */
@OptIn(ExperimentalTestApi::class)
class DatePickerSizeTest {

    private fun ComposeUiTest.picker(width: Int): Rect {
        setContent {
            KontourTheme {
                Box(Modifier.width(width.dp)) {
                    DatePicker(
                        value = LocalDate(2026, 6, 18),
                        onValueChange = {},
                        today = LocalDate(2026, 6, 12),
                        previousIcon = SystemIcons.ChevronLeft,
                        nextIcon = SystemIcons.ChevronRight,
                        modifier = Modifier.testTag("picker"),
                    )
                }
            }
        }
        waitForIdle()
        return onNodeWithTag("picker").fetchSemanticsNode().boundsInRoot
    }

    private fun ComposeUiTest.day(n: Int): Rect =
        onAllNodesWithText("$n", useUnmergedTree = true).fetchSemanticsNodes().first().boundsInRoot

    @Test
    fun onAWideWindowItIsAPhonesWidth() = runComposeUiTest {
        val picker = picker(width = 1000)
        assertTrue(picker.width <= 7 * 44f + 1f, "on a wide window the picker spread to ${picker.width}dp")
    }

    @Test
    fun theDigitsStayOnTheTypeScale() = runComposeUiTest {
        picker(width = 1000)
        val wide = day(2).height
        // 15sp body text, a line of it about 20dp tall; grown, it was 31.
        assertTrue(wide <= 22f, "on a wide window a day's digit is ${wide}dp tall, grown past the type scale")
    }

    @Test
    fun onAPhoneAWeekIsShorterThanADayIsWide() = runComposeUiTest {
        picker(width = 340)
        val pitch = day(8).center.y - day(1).center.y
        val across = day(2).center.x - day(1).center.x
        assertTrue(pitch <= 40.5f, "a week is ${pitch}dp tall")
        assertTrue(pitch < across, "a week is ${pitch}dp tall and a day ${across}dp wide")
    }

    @Test
    fun onAPhoneItIsNoTallerThanItNeeds() = runComposeUiTest {
        val picker = picker(width = 340)
        // Six weeks at most 40dp, the initials over them and the header: it was
        // 379dp.
        assertTrue(picker.height <= 320f, "the picker is ${picker.height}dp tall at a phone's width")
    }
}
