package io.kontour.ui.components.calendar

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.datetime.LocalDateTime

/**
 * How far an edge can be dragged: within its own day. Reported as an end
 * dragged to the bottom of the hours that *just goes across multiple days*.
 */
class ResizeLimitTest {

    private fun at(day: Int, hour: Int, minute: Int = 0) = LocalDateTime(2026, 10, day, hour, minute).epochMinute

    @Test
    fun anEndStopsAtItsDaysMidnight() {
        assertEquals(at(15, 0), resizedEnd(at(14, 22), at(14, 23), at(15, 2), 15))
    }

    @Test
    fun anEndIsAStepAfterItsStartAtTheLeast() {
        assertEquals(at(14, 22, 15), resizedEnd(at(14, 22), at(14, 23), at(14, 20), 15))
    }

    @Test
    fun anEventPastMidnightHasItsEndOnTheSecondDay() {
        // 22:00 to 02:00: its end is Thursday's, from Thursday's midnight to the next.
        assertEquals(at(15, 0, 15), resizedEnd(at(14, 22), at(15, 2), at(14, 23), 15))
        assertEquals(at(16, 0), resizedEnd(at(14, 22), at(15, 2), at(16, 5), 15))
        assertEquals(at(15, 5), resizedEnd(at(14, 22), at(15, 2), at(15, 5), 15))
    }

    @Test
    fun anEndAtMidnightIsTheDaysLastMinute() {
        // 23:00 to 24:00 ends on its own day, not the next.
        assertEquals(at(15, 0), resizedEnd(at(14, 23), at(15, 0), at(15, 3), 15))
    }

    @Test
    fun aStartStopsAtItsDaysMidnight() {
        assertEquals(at(14, 0), resizedStart(at(14, 1), at(14, 2), at(13, 22), 15))
    }

    @Test
    fun aStartIsAStepBeforeItsEndAtTheLatest() {
        assertEquals(at(14, 1, 45), resizedStart(at(14, 1), at(14, 2), at(14, 5), 15))
        // And on its own day, however late the event ends.
        assertEquals(at(14, 23, 45), resizedStart(at(14, 22), at(15, 2), at(15, 1), 15))
    }

    @Test
    fun aCarriedEventKeepsToTheDayItIsOver() {
        val day = at(14, 0)
        // An hour long: from midnight to an hour before the next.
        assertEquals(at(14, 23), movedStart(at(14, 23, 45), 60, day, oneDay = true, step = 15))
        assertEquals(at(14, 0), movedStart(at(13, 23), 60, day, oneDay = true, step = 15))
        assertEquals(at(14, 9), movedStart(at(14, 9), 60, day, oneDay = true, step = 15))
    }

    @Test
    fun anEventAlreadyPastMidnightKeepsOnlyItsStartOnTheDay() {
        val day = at(14, 0)
        // 22:00 to 02:00: it can still start as late as the last step.
        assertEquals(at(14, 23, 45), movedStart(at(15, 1), 240, day, oneDay = false, step = 15))
    }
}
