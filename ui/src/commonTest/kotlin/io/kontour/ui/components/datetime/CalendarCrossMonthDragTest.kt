package io.kontour.ui.components.datetime

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kontour.ui.theme.KontourTheme
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A range dragged across a month border: the drag survives the month changing
 * under it, by the header's arrows or by dwelling on the arrows the drag shows at
 * the month's edges, and carries on in the new month.
 *
 * Reported: *"when you change the month while dragging using the arrow buttons, it
 * doesn't stop the drag"*, and *"as you drag it towards the start or end of a
 * month, a small arrow … appears. when you drag over that arrow for enough time …
 * it switches to the corresponding month, and the drag continues."* Monday first
 * throughout, so the months' blanks are the ISO week's.
 */
@OptIn(ExperimentalTestApi::class)
class CalendarCrossMonthDragTest {

    /** A second finger on the header's next-month arrow pages, and the first finger's drag goes on in September. */
    @Test
    fun theHeaderArrowPagesWithoutEndingTheDrag() = runComposeUiTest {
        val picked = Picked()
        val navigation = picker(LocalDate(2026, 8, 1), picked)
        val from = centreOf(20)
        val to = centreOf(26)
        val next = onNodeWithContentDescription("Next month").fetchSemanticsNode().boundsInRoot.center
        onRoot().performTouchInput {
            down(0, from)
            steps(from, to) { moveTo(0, it) }
            down(1, next)
            up(1)
        }
        mainClock.advanceTimeBy(600)
        assertEquals(LocalDate(2026, 9, 1), navigation.visibleMonth, "the header arrow did not page")
        val third = centreOf(3)
        onRoot().performTouchInput {
            moveTo(0, third)
            up(0)
        }
        assertEquals(LocalDate(2026, 8, 20) to LocalDate(2026, 9, 3), picked.range)
    }

    /** Held on the arrow after the 31st, August pages to September and the drag carries on there. */
    @Test
    fun dwellingOnTheNextArrowPagesAndTheDragContinues() = runComposeUiTest {
        val picked = Picked()
        val navigation = picker(LocalDate(2026, 8, 1), picked)
        val from = centreOf(20)
        val last = centreOf(31)
        // The blank after the 31st, a Monday in the last row: the next cell along.
        val arrow = last + Offset(cellWidth(), 0f)
        onRoot().performTouchInput {
            down(0, from)
            steps(from, last) { moveTo(0, it) }
            moveTo(0, arrow)
        }
        mainClock.advanceTimeBy(DwellMillis + 300L)
        assertEquals(LocalDate(2026, 9, 1), navigation.visibleMonth, "dwelling on the arrow did not page")
        val third = centreOf(3)
        onRoot().performTouchInput {
            moveTo(0, third)
            up(0)
        }
        assertEquals(LocalDate(2026, 8, 20) to LocalDate(2026, 9, 3), picked.range)
    }

    /** Off the arrow before the ring fills, and nothing pages. */
    @Test
    fun leavingTheArrowEarlyCancelsTheDwell() = runComposeUiTest {
        val picked = Picked()
        val navigation = picker(LocalDate(2026, 8, 1), picked)
        val from = centreOf(20)
        val last = centreOf(31)
        val arrow = last + Offset(cellWidth(), 0f)
        // By hand: left to itself the test clock runs until nothing is animating,
        // which is a dwell run to the end.
        mainClock.autoAdvance = false
        onRoot().performTouchInput {
            down(0, from)
            steps(from, last) { moveTo(0, it) }
            moveTo(0, arrow)
        }
        mainClock.advanceTimeBy(DwellMillis / 2L)
        onRoot().performTouchInput { moveTo(0, last) }
        mainClock.advanceTimeBy(DwellMillis * 2L)
        assertEquals(LocalDate(2026, 8, 1), navigation.visibleMonth, "a dwell cut short still paged")
        // And the control: the same clock, a dwell held to the end, pages.
        onRoot().performTouchInput { moveTo(0, arrow) }
        mainClock.advanceTimeBy(DwellMillis + 300L)
        onRoot().performTouchInput { up(0) }
        mainClock.advanceTimeBy(300L)
        assertEquals(LocalDate(2026, 9, 1), navigation.visibleMonth, "a full dwell on the same clock did not page")
    }

    /**
     * August 2026 starts on a Saturday. Pushed past the 1st into the blank before
     * it, and then on along the row to where Monday would be, the dwell carries on
     * and pages — going further past the edge day is still going past it.
     */
    @Test
    fun pushingFarPastTheFirstKeepsTheDwellGoing() = runComposeUiTest {
        val picked = Picked()
        val navigation = picker(LocalDate(2026, 8, 1), picked)
        val from = centreOf(12)
        val first = centreOf(1)
        val width = cellWidth()
        mainClock.autoAdvance = false
        onRoot().performTouchInput {
            down(0, from)
            steps(from, first) { moveTo(0, it) }
            moveTo(0, first - Offset(width, 0f))
        }
        mainClock.advanceTimeBy(DwellMillis / 2L)
        // On to Monday's column, five cells before the Saturday the month starts on.
        onRoot().performTouchInput { moveTo(0, first - Offset(width * 5f, 0f)) }
        mainClock.advanceTimeBy(DwellMillis / 2L + 200L)
        onRoot().performTouchInput { up(0) }
        mainClock.advanceTimeBy(300L)
        assertEquals(LocalDate(2026, 7, 1), navigation.visibleMonth, "pushing further past the 1st cut the dwell short")
    }

    /**
     * June 2026 starts on a Monday, so there is no blank before the 1st: the arrow
     * hangs past the grid's start edge, and pushing the handle past the edge in that
     * row reaches it.
     */
    @Test
    fun aMonthStartingOnTheFirstWeekdayHangsItsArrowPastTheEdge() = runComposeUiTest {
        val picked = Picked()
        val navigation = picker(LocalDate(2026, 6, 1), picked)
        val from = centreOf(5)
        val first = centreOf(1)
        val past = first - Offset(cellWidth() * 0.8f, 0f)
        onRoot().performTouchInput {
            down(0, from)
            steps(from, first) { moveTo(0, it) }
            moveTo(0, past)
        }
        mainClock.advanceTimeBy(DwellMillis + 300L)
        onRoot().performTouchInput { up(0) }
        assertEquals(LocalDate(2026, 5, 1), navigation.visibleMonth, "past the edge of a month starting on a Monday did not page")
    }

    /**
     * February and March 2026 both start on a Sunday, so the previous-month arrow is
     * in the same place in both. Having paged to February, a handle still on it does
     * not page again; off and back on, it does.
     */
    @Test
    fun aSecondIdenticalEdgeNeedsLeavingFirst() = runComposeUiTest {
        val picked = Picked()
        val navigation = picker(LocalDate(2026, 3, 1), picked)
        val from = centreOf(10)
        val first = centreOf(1)
        val arrow = first - Offset(cellWidth(), 0f)
        onRoot().performTouchInput {
            down(0, from)
            steps(from, first) { moveTo(0, it) }
            moveTo(0, arrow)
        }
        mainClock.advanceTimeBy(DwellMillis + 300L)
        assertEquals(LocalDate(2026, 2, 1), navigation.visibleMonth, "the first dwell did not page")
        mainClock.advanceTimeBy(DwellMillis * 3L)
        assertEquals(LocalDate(2026, 2, 1), navigation.visibleMonth, "a handle left on the same spot paged again")
        onRoot().performTouchInput {
            moveTo(0, first)
            moveTo(0, arrow)
        }
        mainClock.advanceTimeBy(DwellMillis + 300L)
        onRoot().performTouchInput { up(0) }
        assertEquals(LocalDate(2026, 1, 1), navigation.visibleMonth, "off the arrow and back on, it did not page")
    }

    private class Picked {
        var start: LocalDate? = null
        var end: LocalDate? = null
        val range get() = start to end
    }

    private fun ComposeUiTest.picker(month: LocalDate, picked: Picked): CalendarNavigationState {
        lateinit var navigation: CalendarNavigationState
        setContent {
            KontourTheme {
                navigation = rememberCalendarNavigationState(month)
                var start by remember { mutableStateOf<LocalDate?>(null) }
                var end by remember { mutableStateOf<LocalDate?>(null) }
                Box(Modifier.width(420.dp).padding(24.dp)) {
                    DateRangePicker(
                        start = start,
                        end = end,
                        onRangeSelected = { s, e ->
                            start = s
                            end = e
                            picked.start = s
                            picked.end = e
                        },
                        previousIcon = Arrow,
                        nextIcon = Arrow,
                        navigation = navigation,
                        firstDayOfWeek = DayOfWeek.MONDAY,
                    )
                }
            }
        }
        waitForIdle()
        return navigation
    }

    private fun ComposeUiTest.centreOf(day: Int): Offset =
        onAllNodesWithText("$day").onFirst().fetchSemanticsNode().boundsInRoot.center

    /** The 3rd and 4th: a column apart, and in the same week in every month here. */
    private fun ComposeUiTest.cellWidth(): Float = centreOf(4).x - centreOf(3).x

    /** From [from] to [to] in small moves, past the touch slop and across every day between. */
    private fun steps(from: Offset, to: Offset, move: (Offset) -> Unit) {
        val count = 12
        for (step in 1..count) move(from + (to - from) * (step / count.toFloat()))
    }

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
