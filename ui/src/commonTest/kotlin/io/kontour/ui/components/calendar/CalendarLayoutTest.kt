package io.kontour.ui.components.calendar

import io.kontour.ui.theme.Tone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

/**
 * The event calendar's arithmetic, on its own: where timed events go in a day,
 * how a week's bars pack, how each view counts its periods, and what an event
 * accepts.
 */
class CalendarLayoutTest {

    private val day = LocalDate(2026, 10, 14)
    private var next = 0

    private fun at(hour: Int, minute: Int = 0, date: LocalDate = day) = LocalDateTime(date.year, date.month, date.day, hour, minute)

    private fun timed(from: LocalDateTime, to: LocalDateTime) = CalendarEvent(id = next++, title = "Event $next", start = from, end = to)

    // --- Hour labels ---

    @Test
    fun aTwelveHourClocksHoursAreLabelledWithoutTheirMinutes() {
        val twelve = io.kontour.ui.locale.LocaleFormats.english(
            io.kontour.ui.locale.DateTimeConventions(hourCycle = io.kontour.ui.locale.HourCycle.H12),
        )
        // The fixture writes its minutes: what is taken out was there.
        assertEquals("9:00 am", twelve.time(kotlinx.datetime.LocalTime(9, 0)))
        assertEquals("9 am", twelve.hourLabel(9))
        assertEquals("12 pm", twelve.hourLabel(12))
    }

    @Test
    fun aTwentyFourHourClocksHoursAreLabelledAsTheClockWritesThem() {
        val twentyFour = io.kontour.ui.locale.LocaleFormats.english(
            io.kontour.ui.locale.DateTimeConventions(hourCycle = io.kontour.ui.locale.HourCycle.H23),
        )
        assertEquals(twentyFour.time(kotlinx.datetime.LocalTime(14, 0)), twentyFour.hourLabel(14))
    }

    // --- Timed lanes ---

    // --- Laid over each other, as Google's calendar does ---

    @Test
    fun anEventStartingBelowAnothersTitleIsLaidOverItInItsLane() {
        // 9–11, then 9:30–10:30: half an hour on, below the first's title.
        val placed = layoutTimedDay(day, listOf(timed(at(9), at(11)), timed(at(9, 30), at(10, 30))), minMinutes = 15, cascadeMinutes = 24)
        assertEquals(listOf(0, 0), placed.map { it.lane }, "the second went beside the first")
        assertEquals(listOf(1, 1), placed.map { it.lanes }, "the pair took two lanes")
        assertEquals(listOf(0, 1), placed.map { it.depth })
    }

    @Test
    fun anEventStartingBesideAnothersTitleStillGoesBesideIt() {
        // A quarter of an hour on: its top would be over the first's title.
        val placed = layoutTimedDay(day, listOf(timed(at(9), at(11)), timed(at(9, 15), at(10, 30))), minMinutes = 15, cascadeMinutes = 24)
        assertEquals(listOf(0, 1), placed.map { it.lane })
        assertEquals(listOf(0, 0), placed.map { it.depth })
    }

    @Test
    fun eachLaterOneIsLaidOverTheOneBefore() {
        val placed = layoutTimedDay(
            day,
            listOf(timed(at(9), at(12)), timed(at(9, 30), at(11, 30)), timed(at(10), at(11))),
            minMinutes = 15,
            cascadeMinutes = 24,
        )
        assertEquals(listOf(0, 1, 2), placed.map { it.depth })
        assertEquals(setOf(1), placed.map { it.lanes }.toSet())
    }

    @Test
    fun aLaneFreeForItIsTakenBeforeLayingItOverAnother() {
        // 9–12 and 9–9:30 side by side; at 10 the second lane is free again.
        val placed = layoutTimedDay(
            day,
            listOf(timed(at(9), at(12)), timed(at(9), at(9, 30)), timed(at(10), at(11))),
            minMinutes = 15,
            cascadeMinutes = 24,
        )
        assertEquals(listOf(0, 1, 1), placed.map { it.lane })
        assertEquals(listOf(0, 0, 0), placed.map { it.depth })
    }

    @Test
    fun withoutCascadingTheLanesAreAsTheyWere() {
        val events = listOf(
            timed(at(9), at(12)), timed(at(9, 30), at(11, 30)), timed(at(10), at(11)),
            timed(at(13), at(14)), timed(at(13, 10), at(13, 50)), timed(at(15), at(15)),
        )
        val placed = layoutTimedDay(day, events, minMinutes = 15)
        assertEquals(listOf(0, 1, 2, 0, 1, 0), placed.map { it.lane })
        assertEquals(setOf(0), placed.map { it.depth }.toSet())
    }

    @Test
    fun oneLaidOverAnotherIsInFromItAndNeverMoreThanHalfway() {
        val parent = TimedPlacement(timed(at(9), at(11)), 540, 660, lane = 0, lanes = 1, clippedStart = false, clippedEnd = false)
        val child = TimedPlacement(timed(at(9, 30), at(10)), 570, 600, lane = 0, lanes = 1, clippedStart = false, clippedEnd = false, depth = 1)
        val deep = TimedPlacement(timed(at(9, 30), at(10)), 570, 600, lane = 0, lanes = 1, clippedStart = false, clippedEnd = false, depth = 4)
        assertEquals(0f to 100f, timedAcross(parent, 100f, gap = 0f, indent = 16f))
        assertEquals(16f to 84f, timedAcross(child, 100f, gap = 0f, indent = 16f))
        // Four deep at 16 would be 64 in; it stops at half.
        assertEquals(50f to 50f, timedAcross(deep, 100f, gap = 0f, indent = 16f))
    }

    @Test
    fun eventsThatDoNotOverlapTakeTheWholeWidth() {
        val placed = layoutTimedDay(day, listOf(timed(at(9), at(10)), timed(at(10), at(11))), minMinutes = 15)
        assertEquals(listOf(1, 1), placed.map { it.lanes }, "back-to-back events share no time, so need no lanes")
    }

    @Test
    fun twoOverlappingEventsShareHalves() {
        val placed = layoutTimedDay(day, listOf(timed(at(9), at(11)), timed(at(10), at(12))), minMinutes = 15)
        // The fixture really does overlap: 10 is before 11.
        assertTrue(placed[1].start < placed[0].end)
        assertEquals(listOf(0, 1), placed.map { it.lane })
        assertEquals(listOf(2, 2), placed.map { it.lanes })
    }

    @Test
    fun aChainWhereOnlyNeighboursOverlapUsesTwoLanes() {
        // 9–11, 10–12, 11:30–13: the third starts after the first ends, so it
        // reuses its lane, and the whole run needs only two.
        val placed = layoutTimedDay(day, listOf(timed(at(9), at(11)), timed(at(10), at(12)), timed(at(11, 30), at(13))), minMinutes = 15)
        assertEquals(listOf(0, 1, 0), placed.map { it.lane })
        assertEquals(setOf(2), placed.map { it.lanes }.toSet())
    }

    @Test
    fun anEventRunsOnAcrossLanesThatAreFreeForAllOfIt() {
        // 9–11 beside 9–9:30 then 10–11: three lanes are not needed, and the
        // 10 o'clock, starting after the half-hour ends, takes the second lane.
        // Then 9–12 with two short ones in turn beside it, and a late one after
        // the first short one: the late one may run across into the free lane.
        val long = timed(at(9), at(12))
        val first = timed(at(9), at(9, 30))
        val second = timed(at(9), at(10))
        val late = timed(at(10, 30), at(11))
        val placed = layoutTimedDay(day, listOf(long, first, second, late), minMinutes = 15).associateBy { it.event }
        // The fixture needs three lanes at nine.
        assertEquals(3, placed.getValue(long).lanes)
        assertEquals(listOf(0, 1, 2), listOf(long, second, first).map { placed.getValue(it).lane })
        // The late one takes the first free lane after nine-thirty and runs on
        // over the lane the hour-long one had finished with.
        assertEquals(1, placed.getValue(late).lane)
        assertEquals(2, placed.getValue(late).span)
        // Those that something meets in every lane after theirs stay one wide.
        assertEquals(listOf(1, 1, 1), listOf(long, second, first).map { placed.getValue(it).span })
    }

    @Test
    fun aSpanStopsAtALaneSomethingMeetsItIn() {
        // 9–11, 9–10 and 9–9:30 fill three lanes; 10–10:30 takes the second
        // as the hour-long one leaves it, and 10:15–11 the third. The half-hour
        // at ten would run into the third lane but for the one at quarter past.
        val ten = timed(at(10), at(10, 30))
        val quarterPast = timed(at(10, 15), at(11))
        val placed = layoutTimedDay(
            day,
            listOf(timed(at(9), at(11)), timed(at(9), at(10)), timed(at(9), at(9, 30)), ten, quarterPast),
            minMinutes = 15,
        ).associateBy { it.event }
        assertEquals(1 to 2, placed.getValue(ten).lane to placed.getValue(quarterPast).lane)
        assertEquals(1, placed.getValue(ten).span, "the one at quarter past meets it in the third lane")
        // Without the one at quarter past, it runs on: the canary for the fixture.
        val alone = layoutTimedDay(day, listOf(timed(at(9), at(11)), timed(at(9), at(10)), timed(at(9), at(9, 30)), ten), minMinutes = 15)
        assertEquals(2, alone.single { it.event == ten }.span)
    }

    @Test
    fun aShortEventStillTakesItsLeastHeightAndPushesTheNextAside() {
        // A five-minute reminder at nine and a meeting at five past: by the
        // clock they do not overlap, but the reminder is drawn a quarter-hour
        // tall, so drawn without lanes they would print over each other.
        val placed = layoutTimedDay(day, listOf(timed(at(9), at(9, 5)), timed(at(9, 5), at(10))), minMinutes = 15)
        assertEquals(listOf(2, 2), placed.map { it.lanes }, "the reminder's drawn height was not counted as overlap")
        // And without that least height, they would not share: the canary for the fixture.
        val bare = layoutTimedDay(day, listOf(timed(at(9), at(9, 5)), timed(at(9, 5), at(10))), minMinutes = 0)
        assertEquals(listOf(1, 1), bare.map { it.lanes })
    }

    @Test
    fun anEventOverMidnightIsAPieceOnEachDay() {
        val flight = timed(at(22), at(6, date = LocalDate(2026, 10, 15)))
        val tonight = layoutTimedDay(day, listOf(flight), minMinutes = 15).single()
        val tomorrow = layoutTimedDay(LocalDate(2026, 10, 15), listOf(flight), minMinutes = 15).single()
        assertEquals(22 * 60 to MinutesPerDay, tonight.start to tonight.end)
        assertTrue(!tonight.clippedStart && tonight.clippedEnd)
        assertEquals(0 to 6 * 60, tomorrow.start to tomorrow.end)
        assertTrue(tomorrow.clippedStart && !tomorrow.clippedEnd)
    }

    @Test
    fun anEventWithNoLengthIsOnItsDay() {
        val placed = layoutTimedDay(day, listOf(timed(at(9), at(9))), minMinutes = 15).single()
        assertEquals(9 * 60, placed.start)
    }

    @Test
    fun allDayAndDayLongEventsAreNotInTheHours() {
        val holiday = CalendarEvent(id = "h", title = "Holiday", startDate = day)
        val dayLong = timed(at(0), at(0, date = LocalDate(2026, 10, 15)))
        assertTrue(layoutTimedDay(day, listOf(holiday, dayLong), minMinutes = 15).isEmpty())
    }

    // --- Week packing ---

    private val week = LocalDate(2026, 10, 12) // a Monday

    @Test
    fun barsTakeTheLowestRowFreeAcrossAllTheirDays() {
        val long = CalendarEvent(id = "a", title = "Conference", startDate = week, endDate = LocalDate(2026, 10, 14))
        val later = CalendarEvent(id = "b", title = "Trip", startDate = LocalDate(2026, 10, 14), endDate = LocalDate(2026, 10, 16))
        val pack = packWeek(weekItems(week, listOf(long, later), lines = true), maxRows = 4)
        assertEquals(listOf(0, 1), pack.placed.map { it.row }, "the trip shares the 14th with the conference, so goes under it")
        assertEquals(listOf(0 to 2, 2 to 4), pack.placed.map { it.item.from to it.item.to })
    }

    @Test
    fun aDayThatOverflowsShowsOneRowFewerAndCountsTheRest() {
        val four = (0 until 4).map { timed(at(9 + it), at(10 + it, date = day)) }
        val pack = packWeek(weekItems(week, four, lines = true), maxRows = 3)
        val column = 2 // the 14th
        assertEquals(2, pack.shownRows[column])
        assertEquals(2, pack.more[column], "four events in three rows: two shown, and \"+2\"")
        assertEquals(2, pack.placed.size)
    }

    @Test
    fun aBarThatWouldCrossAMoreIsHiddenWholeAndCountedInEveryDay() {
        // Three two-day bars over the 13th and 14th fill those days' rows; a
        // bar from the 14th to the 16th comes after them, in the fourth row,
        // which the 14th's "+N" has taken. It is hidden whole, not broken.
        val pairs = (0 until 3).map {
            CalendarEvent(id = "pair$it", title = "Pair $it", startDate = LocalDate(2026, 10, 13), endDate = day)
        }
        val bar = CalendarEvent(id = "bar", title = "Visit", startDate = day, endDate = LocalDate(2026, 10, 16))
        val pack = packWeek(weekItems(week, pairs + bar, lines = true), maxRows = 2)
        assertTrue(pack.placed.none { it.item.event.id == "bar" }, "a bar was drawn across a day's \"+N\"")
        assertEquals(1, pack.more[3], "the 15th lost the bar and should say so")
        assertEquals(1, pack.more[4], "and so should the 16th")
        assertEquals(1, pack.shownRows[3], "the 15th needs a row for its \"+N\" once it has lost something")
    }

    @Test
    fun noRowsShowsNothingAndCountsEverything() {
        val pack = packWeek(weekItems(week, listOf(timed(at(9), at(10))), lines = true), maxRows = 0)
        assertTrue(pack.placed.isEmpty())
        assertEquals(1, pack.more[2])
    }

    @Test
    fun aBarCutByTheWeekSaysWhichEndsGoOn() {
        val long = CalendarEvent(id = "a", title = "Leave", startDate = LocalDate(2026, 10, 10), endDate = LocalDate(2026, 10, 20))
        val item = weekItems(week, listOf(long), lines = true).single()
        assertTrue(item.continuesBefore && item.continuesAfter)
        assertEquals(0 to 6, item.from to item.to)
    }

    @Test
    fun theStripAboveTheHoursHasOnlyWhatIsNotInThem() {
        val meeting = timed(at(9), at(10))
        val holiday = CalendarEvent(id = "h", title = "Holiday", startDate = day)
        val items = weekItems(week, listOf(meeting, holiday), lines = false)
        assertEquals(listOf<Any>("h"), items.map { it.event.id })
        assertTrue(items.single().bar)
    }

    // --- Periods ---

    @Test
    fun aWeekStartsOnTheReadersFirstDayAcrossAYearsEnd() {
        val monday = CalendarPeriods(DayOfWeek.MONDAY)
        val sunday = CalendarPeriods(DayOfWeek.SUNDAY)
        val newYear = LocalDate(2027, 1, 1) // a Friday
        assertEquals(LocalDate(2026, 12, 28), monday.weekStart(newYear))
        assertEquals(LocalDate(2026, 12, 27), sunday.weekStart(newYear))
        val index = monday.indexOf(newYear, CalendarView.Week)
        assertEquals(LocalDate(2027, 1, 4), monday.startOf(index + 1, CalendarView.Week))
    }

    @Test
    fun aMonthShowsItsWholeWeeksWithTheDaysEitherSide() {
        val periods = CalendarPeriods(DayOfWeek.MONDAY)
        val october = periods.indexOf(LocalDate(2026, 10, 14), CalendarView.Month)
        val dates = periods.visibleDates(october, CalendarView.Month)
        assertEquals(LocalDate(2026, 9, 28), dates.start)
        assertEquals(LocalDate(2026, 11, 1), dates.endInclusive)
        assertEquals(5, periods.weeksIn(october))
    }

    @Test
    fun periodsBefore1970CountTheSameWay() {
        val periods = CalendarPeriods(DayOfWeek.MONDAY)
        val old = LocalDate(1969, 12, 31) // a Wednesday
        for (view in CalendarView.entries) {
            val index = periods.indexOf(old, view)
            val range = periods.visibleDates(index, view)
            assertTrue(old in range, "$view: $old is not in its own period, $range")
        }
        assertEquals(LocalDate(1969, 12, 29), periods.weekStart(old))
    }

    // --- The event itself, and the minutes ---

    @Test
    fun anAllDayEventByItsDaysRunsToTheMidnightAfterTheLast() {
        val event = CalendarEvent(id = 1, title = "Trip", startDate = day, endDate = LocalDate(2026, 10, 16), tone = Tone.Success)
        assertTrue(event.allDay)
        assertEquals(at(0, date = LocalDate(2026, 10, 17)), event.end)
    }

    @Test
    fun anEventEndingBeforeItStartsIsRefusedByName() {
        val error = assertFailsWith<IllegalArgumentException> { CalendarEvent(id = 1, title = "Backwards", start = at(10), end = at(9)) }
        assertTrue("CalendarEvent" in error.message.orEmpty() && "`end`" in error.message.orEmpty(), error.message)
    }

    @Test
    fun minutesGoBackwardsOverMidnightAndBefore1970() {
        assertEquals(at(23, 45, LocalDate(2026, 10, 13)), at(0).plusMinutes(-15))
        val early = LocalDateTime(1969, 12, 31, 23, 59)
        assertEquals(early, localDateTimeOfEpochMinute(early.epochMinute))
        assertEquals(-15L, floorTo(-1, 15))
        assertEquals(15L, roundTo(8, 15))
    }
}
