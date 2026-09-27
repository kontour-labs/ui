package io.kontour.ui.locale

import kotlin.test.Test
import kotlin.test.assertEquals

/** What each platform hands over is read into the same patterns. */
class PatternReadingTest {

    @Test
    fun theClockAndTheOrderOfADate() {
        assertEquals(HourCycle.H12, hourCycleIn("h:mm a"))
        assertEquals(HourCycle.H23, hourCycleIn("HH:mm"))
        assertEquals(HourCycle.H11, hourCycleIn("aK:mm"))
        assertEquals(HourCycle.H24, hourCycleIn("kk:mm"))
        assertEquals(HourCycle.H23, hourCycleIn("'h' HH:mm"), "a quoted h is a letter in a word")
        assertEquals(DateOrder.DayMonthYear, dateOrderIn("d/M/yy"))
        assertEquals(DateOrder.MonthDayYear, dateOrderIn("M/d/yy"))
        assertEquals(DateOrder.YearMonthDay, dateOrderIn("y/MM/dd"))
        assertEquals(DateOrder.YearMonthDay, dateOrderIn("y. M. d."))
        assertEquals(DateOrder.DayMonthYear, dateOrderIn("dd.MM.y"))
    }

    @Test
    fun aClockTheOtherWayRound() {
        assertEquals("HH:mm", twentyFourHourFrom("h:mm a"))
        assertEquals("HH:mm", twentyFourHourFrom("a h:mm"))
        assertEquals("HH:mm", twentyFourHourFrom("aK:mm"))
        assertEquals("HH.mm", twentyFourHourFrom("H.mm"))
        assertEquals("h:mm a", twelveHourFrom("HH:mm"))
        assertEquals("a h:mm", twelveHourFrom("a H:mm"))
    }

    @Test
    fun aMonthWithItsYearFromALongDate() {
        assertEquals("LLLL y", monthYearFrom("d MMMM y"))
        assertEquals("LLLL y", monthYearFrom("d. MMMM y"))
        assertEquals("LLLL y", monthYearFrom("MMMM d, y"))
        assertEquals("LLLL y", monthYearFrom("EEEE, d MMMM y"))
        assertEquals("y年L月", monthYearFrom("y年M月d日"))
        assertEquals("y년 LLLL", monthYearFrom("y년 MMMM d일"))
        assertEquals("LLLL' de 'y", monthYearFrom("d 'de' MMMM 'de' y"))
    }

    @Test
    fun theGlueBetweenADateAndATime() {
        assertEquals("{1}, {0}", dateTimeGlue("d MMM y, h:mm a", "d MMM y", "h:mm a"))
        assertEquals("{1} {0}", dateTimeGlue("y/MM/dd H:mm", "y/MM/dd", "H:mm"))
        assertEquals("{1} 'um' {0}".replace("'", ""), dateTimeGlue("d MMM y 'um' HH:mm", "d MMM y", "HH:mm"))
    }

    @Test
    fun listPatternsFromAFormatter() {
        val pattern = listPatternFrom { items ->
            when (items.size) {
                2 -> "${items[0]} und ${items[1]}"
                else -> items.dropLast(1).joinToString(", ") + " und " + items.last()
            }
        }
        assertEquals("a, b, c und d", pattern!!.join(listOf("a", "b", "c", "d")))
        assertEquals("a und b", pattern.join(listOf("a", "b")))
    }

    @Test
    fun aPatternFromTheBrowsersParts() {
        val months = CalendarNames(
            wide = List(12) { if (it == ExampleMonth) "June" else "M$it" },
            abbreviated = List(12) { if (it == ExampleMonth) "Jun" else "m$it" },
            narrow = List(12) { "J" },
        )
        val weekdays = CalendarNames(
            wide = List(7) { if (it == ExampleWeekday) "Tuesday" else "D$it" },
            abbreviated = List(7) { if (it == ExampleWeekday) "Tue" else "d$it" },
            narrow = List(7) { "T" },
        )
        assertEquals(
            "EEEE, d MMMM y",
            patternFromParts(
                listOf("weekday" to "Tuesday", "literal" to ", ", "day" to "9", "literal" to " ", "month" to "June", "literal" to " ", "year" to "2026"),
                months, weekdays,
            ),
        )
        assertEquals(
            "dd/MM/yy",
            patternFromParts(listOf("day" to "09", "literal" to "/", "month" to "06", "literal" to "/", "year" to "26"), months, weekdays),
        )
        assertEquals(
            "y'年'M'月'd'日'".replace("'", ""),
            patternFromParts(listOf("year" to "2026", "literal" to "年", "month" to "6", "literal" to "月", "day" to "9", "literal" to "日"), months, weekdays),
        )
    }

    @Test
    fun quotedLiteralsSurviveTheWalk() {
        val parts = mutableListOf<String>()
        forEachField("d 'de' MMMM 'o''clock'", literal = { parts += "[$it]" }) { field, count -> parts += field.toString().repeat(count) }
        assertEquals(listOf("d", "[ ]", "[de]", "[ ]", "MMMM", "[ ]", "[o'clock]"), parts)
    }
}
