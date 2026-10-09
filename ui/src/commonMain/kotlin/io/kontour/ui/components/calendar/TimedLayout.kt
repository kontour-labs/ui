package io.kontour.ui.components.calendar

import kotlinx.datetime.LocalDate

/**
 * Where one timed event sits in one day's column of hours.
 *
 * @property start Minutes after the day's midnight its part of the day starts, 0 at the earliest.
 * @property end Minutes after midnight its part ends, [MinutesPerDay] at the latest.
 * @property lane Which of [lanes] side-by-side places it takes in its cluster
 *   of overlapping events, 0 at the start edge.
 * @property span How many lanes it is wide, from [lane] on: more than one where
 *   the lanes beside it are free for all of its length.
 * @property clippedStart Whether it began on an earlier day: its top edge is
 *   not its start, so it has no corner there and no handle to move it by.
 * @property clippedEnd The same at the bottom, for one that runs past midnight.
 * @property depth How many events it is laid over in its lane: 0 for one with
 *   the lane to itself, 1 for one that starts below the title of the event
 *   it is on, and so on — drawn a little in from it, over it.
 */
internal class TimedPlacement(
    val event: CalendarEvent,
    val start: Int,
    val end: Int,
    val lane: Int,
    val lanes: Int,
    val clippedStart: Boolean,
    val clippedEnd: Boolean,
    val span: Int = 1,
    val depth: Int = 0,
)

/**
 * Whether this event goes in the hours of a day — timed and shorter than a whole
 * day — rather than across the top, with the all-day ones.
 */
internal fun CalendarEvent.isTimed(): Boolean =
    !allDay && end.epochMinute - start.epochMinute < MinutesPerDay

/**
 * The timed [events] that are on [day], placed in its column.
 *
 * Each is cut to the day — a flight from ten at night to six in the morning is
 * two pieces, one on each day — and is at least [minMinutes] long **for the
 * purpose of overlapping**, since that is the least it is drawn at: a reminder
 * with no length, drawn a step tall, pushes the event after it aside rather
 * than printing over it.
 *
 * Overlapping events share the column: they are gathered into clusters, each
 * cluster runs until nothing in it is still going, and each event takes the
 * first lane free when it starts. Every event in a cluster is as narrow as the
 * cluster's widest moment needs, so a long event beside two short ones in turn
 * takes half, not a third.
 *
 * With [cascadeMinutes], an event that starts that long or more after every
 * event still going in a lane — below their titles — is laid over them in it,
 * a [TimedPlacement.depth] deeper, rather than given a lane of its own, as a
 * day in Google's calendar is: asked for as *"if the text doesn't overlap,
 * then the event partially overlaps"*. In a week's narrow column a lane each
 * left an afternoon of three meetings a letter wide apiece. A lane free for it
 * is still taken first, and one that starts beside another, before its title
 * is done, still goes beside it. Null: a lane each, side by side.
 */
internal fun layoutTimedDay(day: LocalDate, events: List<CalendarEvent>, minMinutes: Int, cascadeMinutes: Int? = null): List<TimedPlacement> {
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
        Segment(event, index, start, end, minOf(maxOf(end, start + minMinutes), MinutesPerDay))
    }.sortedWith(compareBy<Segment>({ it.start }, { -(it.end - it.start) }, { it.index }))

    val placed = ArrayList<TimedPlacement>(segments.size)

    class InLane(val segment: Segment, val lane: Int, val depth: Int)

    var cluster = ArrayList<InLane>()
    // What each lane of the cluster holds, ended or not.
    val lanes = ArrayList<ArrayList<InLane>>()
    var clusterEnd = Int.MIN_VALUE

    fun close() {
        val count = lanes.size
        for (item in cluster) {
            val segment = item.segment
            val event = segment.event
            // On across the next lanes for as long as nothing in them meets it.
            var span = 1
            while (item.lane + span < count) {
                val blocked = lanes[item.lane + span].any { other ->
                    other.segment.start < segment.visualEnd && segment.start < other.segment.visualEnd
                }
                if (blocked) break
                span++
            }
            placed += TimedPlacement(
                event = event,
                start = segment.start,
                end = segment.end,
                lane = item.lane,
                lanes = count,
                clippedStart = event.start.epochMinute < dayStart,
                clippedEnd = event.end.epochMinute > dayEnd,
                span = span,
                depth = item.depth,
            )
        }
        cluster = ArrayList()
        lanes.clear()
    }

    for (segment in segments) {
        if (cluster.isNotEmpty() && segment.start >= clusterEnd) close()
        fun going(lane: Int) = lanes[lane].filter { it.segment.visualEnd > segment.start }
        var lane = lanes.indices.firstOrNull { going(it).isEmpty() } ?: -1
        var depth = 0
        if (lane < 0 && cascadeMinutes != null) {
            // Over the lane whose latest event it starts below the title of —
            // and below the title of everything else still going there.
            var latest = Int.MIN_VALUE
            for (candidate in lanes.indices) {
                val there = going(candidate)
                if (there.any { segment.start - it.segment.start < cascadeMinutes }) continue
                val start = there.maxOf { it.segment.start }
                if (start > latest) {
                    latest = start
                    lane = candidate
                    depth = there.maxOf { it.depth } + 1
                }
            }
        }
        if (lane < 0) {
            lane = lanes.size
            lanes += ArrayList<InLane>()
        }
        val item = InLane(segment, lane, depth)
        lanes[lane] += item
        cluster += item
        clusterEnd = if (cluster.size == 1) segment.visualEnd else maxOf(clusterEnd, segment.visualEnd)
    }
    if (cluster.isNotEmpty()) close()
    return placed
}

/**
 * Where [p] goes across a day [width] wide, as its start and its width: its
 * lanes' share of the day, less [gap], and in from its start by [indent] for
 * each event it is laid over — never more than half its lanes' width in all,
 * so a deep pile still leaves the top one half the room.
 */
internal fun timedAcross(p: TimedPlacement, width: Float, gap: Float, indent: Float): Pair<Float, Float> {
    val lane = width / p.lanes
    val share = lane * p.span
    val inset = if (p.depth == 0) 0f else p.depth * minOf(indent, share * MaxCascadeShare / p.depth)
    return (p.lane * lane + inset + gap / 2) to (share - inset - gap)
}

/** The most of its lanes' width an event laid over others is in from their start. */
private const val MaxCascadeShare = 0.5f
