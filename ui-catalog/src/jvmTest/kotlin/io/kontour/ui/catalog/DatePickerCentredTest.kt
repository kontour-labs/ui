package io.kontour.ui.catalog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kontour.ui.demo.CalendarMonthDemo
import io.kontour.ui.demo.ComponentDemo
import io.kontour.ui.demo.DateRangePickerDemo
import io.kontour.ui.demo.DatePickerDemo
import io.kontour.ui.demo.DemoCard
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.KontourTheme
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The gallery's calendars sit in the middle of their cards.
 *
 * Reported once the pickers stopped at a phone's width: *"it needs to be
 * centred (at least in your demos)"*. A picker no wider than 308dp in a card
 * wider than that sat against the card's start, with the spare width all on
 * the other side.
 *
 * June 2026 starts on a Monday, so the 1st and the 7th are the grid's first
 * and last columns, and the middle between them is the grid's.
 */
@OptIn(ExperimentalTestApi::class)
class DatePickerCentredTest {

    private fun ComposeUiTest.offCentre(demo: ComponentDemo): Float {
        setContent {
            KontourTheme(darkTheme = false) {
                // The month chooser is a popover, and a popover needs a host.
                OverlayHost(Modifier.fillMaxSize()) {
                    Box(Modifier.width(420.dp)) {
                        DemoCard(demo, Modifier.testTag("card"))
                    }
                }
            }
        }
        waitForIdle()
        fun day(n: String) = onAllNodesWithText(n, useUnmergedTree = true).fetchSemanticsNodes().first().boundsInRoot.center.x
        val grid = (day("1") + day("7")) / 2f
        val card = onNodeWithTag("card").fetchSemanticsNode().boundsInRoot.center.x
        return grid - card
    }

    @Test
    fun theDatePickerIsInTheMiddleOfItsCard() = runComposeUiTest { centred(offCentre(DatePickerDemo), "DatePicker") }

    @Test
    fun theRangePickerIsInTheMiddleOfItsCard() = runComposeUiTest { centred(offCentre(DateRangePickerDemo), "DateRangePicker") }

    @Test
    fun theMonthIsInTheMiddleOfItsCard() = runComposeUiTest { centred(offCentre(CalendarMonthDemo), "CalendarMonth") }

    private fun centred(off: Float, what: String) =
        assertTrue(abs(off) <= 1f, "the $what's grid is ${off}dp off the middle of its card")
}
