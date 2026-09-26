package io.kontour.ui.components.datetime

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.KontourTheme
import kotlinx.datetime.LocalDate
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The month and year sit in the middle of the calendar, not in the middle of
 * what the buttons either side leave — and on a screen too narrow for that, they
 * move clear of the buttons rather than being cut short.
 *
 * Reported as wanting the title centred at the top of every calendar. The end of
 * the header has the today button as well as the next month's, so spaced between,
 * the title sat toward the start.
 */
@OptIn(ExperimentalTestApi::class)
class CalendarHeaderTest {

    @Test
    fun theTitleIsCentredOnTheCalendar() = runComposeUiTest {
        picker(width = mutableStateOf(400.dp))
        val header = previous().left..next().right
        val title = title()
        val middle = (header.start + header.endInclusive) / 2f
        assertTrue(
            abs(title.center.x - middle) < 1f,
            "the title's centre was at ${title.center.x}, the header's at $middle",
        )
    }

    /**
     * "September 2026" and the chooser's chevron, too wide to centre between one
     * button and two: the title moves toward the single button, clear of the pair,
     * and keeps its whole width.
     */
    @Test
    fun aTitleTooWideToCentreMovesClearOfTheButtons() = runComposeUiTest {
        val width = mutableStateOf(400.dp)
        picker(width)
        val whole = title().width
        width.value = 280.dp
        waitForIdle()
        val title = title()
        val middle = (previous().left + next().right) / 2f
        assertTrue(
            middle + whole / 2f > today().left,
            "at this width the title would clear the buttons centred, so nothing here is tested",
        )
        assertEquals(whole, title.width, "the title was narrowed to fit between the buttons")
        assertTrue(title.left >= previous().right - 0.5f, "the title ran under the previous-month button")
        assertTrue(title.right <= today().left + 0.5f, "the title ran under the today button")
    }

    private fun ComposeUiTest.picker(width: MutableState<Dp>) {
        setContent {
            KontourTheme {
                val across by width
                // The chooser opens in a popover, and a popover needs a host.
                OverlayHost {
                    Box(Modifier.width(across)) {
                        DatePicker(
                            selected = null,
                            onSelectedChange = {},
                            today = LocalDate(2026, 9, 26),
                            previousIcon = Arrow,
                            nextIcon = Arrow,
                            chooserIcon = Arrow,
                        )
                    }
                }
            }
        }
        waitForIdle()
    }

    private fun ComposeUiTest.title(): Rect = onNodeWithText("September 2026").fetchSemanticsNode().boundsInRoot
    private fun ComposeUiTest.previous(): Rect = onNodeWithContentDescription("Previous month").fetchSemanticsNode().boundsInRoot
    private fun ComposeUiTest.next(): Rect = onNodeWithContentDescription("Next month").fetchSemanticsNode().boundsInRoot
    private fun ComposeUiTest.today(): Rect = onNodeWithContentDescription("Return to today").fetchSemanticsNode().boundsInRoot

    private companion object {
        val Arrow: ImageVector = ImageVector.Builder(
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(8f, 4f)
                lineTo(16f, 12f)
                lineTo(8f, 20f)
                close()
            }
        }.build()
    }
}
