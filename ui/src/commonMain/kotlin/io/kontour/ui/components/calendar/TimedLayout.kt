package io.kontour.ui.components.calendar

import kotlinx.datetime.LocalDate

/**
 * Where one timed event sits in one day's column of hours.
 *
 * @property start Minutes after the day's midnight its part of the day starts, 0 at the earliest.
 * @property end Minutes after midnight its part ends, [MinutesPerDay] at the latest.
 * @property lane Which of [lanes] side-by-side places it takes in its cluster
 *   of overlapping events, 0 at the start edge.
 * @property clippedStart Whether it began on an earlier day: its top edge is
 *   not its start, so it has no corner there and no handle to move it by.
 * @property clippedEnd The same at the bottom, for one that runs past midnight.
 */
internal class TimedPlacement(
    val event: CalendarEvent,
    val start: Int,
    val end: Int,
    val lane: Int,
    val lanes: Int,
    val clippedStart: Boolean,
    val clippedEnd: Boolean,
)

/**
 * Whether [event] goes in the hours of a day — timed and shorter than a whole
 * day — rather than across the top, with the all-day ones.
 */
internal fun CalendarEvent.isTimed(): Boolean =
    !allDay && end.epochMinute - start.epochMinute < MinutesPerDay

/**
 * The timed [events] that are on [day], placed in its column.
 *
 * Each is cut to the day — a flight from ten at night to six in the morning is
 * two pieces, one on each day — and is at least [minMinutes] long **for the
 * purpose of overlapping**, since that is the least it is drawn at: a five-minute
 * reminder drawn as tall as a quarter-hour pushes the event after it aside
 * rather than printing over it.
 *
 * Overlapping events share the column: they are gathered into clusters, each
 * cluster runs until nothing in it is still going, and each event takes the
 * first lane free when it starts. Every event in a cluster is as narrow as the
 * cluster's widest moment needs, so a long event beside two short ones in turn
 * takes half, not a third.
 */
internal fun layoutTimedDay(day: LocalDate, events: List<CalendarEvent>, minMinutes: Int): List<TimedPlacement> {
    val dayStart = day.atMidnight.epochMinute
    val dayEnd = dayStart + MinutesPerDay

    class Segment(val event: CalendarEvent, val index: Int, val start: Int, val end: Int, val visualEnd: Int)

    val segments = events.mapIndexedNotNull { index, event ->
        if (!event.isTimed()) return@mapIndexedNotNull null
        val from = event.start.epochMinute
        val to = event.end.epochMinute
        // Half-open, except that an event with no length is on the day it is at.
        val onDay = if (from == to) from in dayStart until dayEnd else from < dayEnd && to > dayStart
        if (!onDay) return@mapIndexedNotNull null
        val start = (maxOf(from, dayStart) - dayStart).toInt()
        val end = (minOf(to, dayEnd) - dayStart).toInt()
        Segment(event, index, start, end, maxOf(end, start + minMinutes))
    }.sortedWith(compareBy<Segment>({ it.start }, { -(it.end - it.start) }, { it.index }))

    val placed = ArrayList<TimedPlacement>(segments.size)
    var cluster = ArrayList<Pair<Segment, Int>>()
    val laneEnds = ArrayList<Int>()
    var clusterEnd = Int.MIN_VALUE

    fun close() {
        val lanes = laneEnds.size
        for ((segment, lane) in cluster) {
            val event = segment.event
            placed += TimedPlacement(
                event = event,
                start = segment.start,
                end = segment.end,
                lane = lane,
                lanes = lanes,
                clippedStart = event.start.epochMinute < dayStart,
                clippedEnd = event.end.epochMinute > dayEnd,
            )
        }
        cluster = ArrayList()
        laneEnds.clear()
    }

    for (segment in segments) {
        if (cluster.isNotEmpty() && segment.start >= clusterEnd) close()
        var lane = laneEnds.indexOfFirst { it <= segment.start }
        if (lane < 0) {
            lane = laneEnds.size
            laneEnds += segment.visualEnd
        } else {
            laneEnds[lane] = segment.visualEnd
        }
        cluster += segment to lane
        clusterEnd = if (cluster.size == 1) segment.visualEnd else maxOf(clusterEnd, segment.visualEnd)
    }
    if (cluster.isNotEmpty()) close()
    return placed
}
