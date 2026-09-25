package io.kontour.ui.components.datetime

import androidx.compose.ui.geometry.Offset
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The drag's reading of the grid, where a phone lays it out: columns narrower than
 * the 48dp touch target a day reserves, so rows taller than the columns are wide.
 *
 * Reported as the last day starting the month-change timer where the first needed
 * pushing past. Read as squares, the rows drifted down the month a few pixels each,
 * until a finger on the bottom of the last day read as below the grid — past the
 * month's last day. A desktop test cannot see it happen, because the desktop's
 * minimum target is 24dp and its rows stay square; so the geometry is asked
 * directly.
 */
class CalendarGridGeometryTest {

    /** August 2026, Monday first: five blanks, six rows, the 31st alone on the last. */
    private val august = GridGeometry.of(
        month = LocalDate(2026, 8, 1),
        formats = platformDateTimeFormats().startingOn(DayOfWeek.MONDAY),
        origin = Offset.Zero,
        cell = Column,
        rowHeight = Row,
        rtl = false,
    )

    @Test
    fun theBottomOfTheLastDayIsTheLastDay() {
        // The 31st's cell, three pixels above its bottom edge, in its later half.
        val bottom = Offset(Column * 0.8f, Row * 6 - 3f)
        val cells = august.cellsAt(bottom)
        assertEquals(LocalDate(2026, 8, 31), august.dateAt(august.nearestIndex(cells)))
        assertNull(august.edgeAt(cells), "the bottom of the last day read as past it: $cells")
    }

    @Test
    fun belowTheLastRowIsPastTheLastDay() {
        val below = Offset(Column * 0.8f, Row * 6 + 6f)
        assertEquals(DwellEdge.Next, august.edgeAt(august.cellsAt(below)))
    }

    @Test
    fun aMiddleRowIsReadAsItsOwn() {
        // The middle of the 19th: fourth row, third column.
        val nineteenth = Offset(Column * 2.5f, Row * 3.5f)
        assertEquals(LocalDate(2026, 8, 19), august.dateAt(august.nearestIndex(august.cellsAt(nineteenth))))
    }

    private companion object {
        /** A 36dp column and a 48dp row, at density one. */
        const val Column = 36f
        const val Row = 48f
    }
}
