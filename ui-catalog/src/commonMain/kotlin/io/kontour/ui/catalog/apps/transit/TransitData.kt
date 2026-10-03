package io.kontour.ui.catalog.apps.transit

import io.kontour.ui.theme.Tone
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/*
 * Transit's network: a handful of Perth's lines, their stations, the next
 * departures from one of them and a year of somebody's trips.
 *
 * All fixed. The gallery's goldens have to come out the same on every machine
 * and every day, so "today" is a date rather than the clock's, and the year of
 * trips comes from a sum over the day rather than from a random number.
 */

/** The day the app thinks it is. */
internal val TransitToday = LocalDate(2026, 10, 2)

/** How a service runs: a train or a bus. */
internal enum class Mode(val label: String) { Train("Trains"), Bus("Buses") }

/** Whether a departure is running as timetabled. */
internal sealed interface Running {
    val label: String
    val tone: Tone

    data object OnTime : Running {
        override val label = "On time"
        override val tone = Tone.Success
    }

    data class Late(val minutes: Int) : Running {
        override val label get() = "$minutes min late"
        override val tone = Tone.Warning
    }

    data object Cancelled : Running {
        override val label = "Cancelled"
        override val tone = Tone.Danger
    }
}

/** One service leaving the home stop. */
internal data class Departure(
    val id: String,
    val route: String,
    val towards: String,
    val mode: Mode,
    val platform: String,
    val minutes: Int,
    val running: Running,
)

/** A line, and its stations in order. */
internal data class Line(val name: String, val stations: List<String>, val every: Int)

internal val lines: List<Line> = listOf(
    Line(
        "Joondalup line",
        listOf("Perth Underground", "Leederville", "Glendalough", "Stirling", "Warwick", "Greenwood", "Whitfords", "Edgewater", "Joondalup", "Clarkson", "Yanchep"),
        every = 10,
    ),
    Line(
        "Mandurah line",
        listOf("Elizabeth Quay", "Canning Bridge", "Bull Creek", "Murdoch", "Cockburn Central", "Aubin Grove", "Kwinana", "Wellard", "Rockingham", "Warnbro", "Mandurah"),
        every = 10,
    ),
    Line(
        "Fremantle line",
        listOf("Perth", "City West", "West Leederville", "Subiaco", "Daglish", "Shenton Park", "Karrakatta", "Claremont", "Cottesloe", "North Fremantle", "Fremantle"),
        every = 15,
    ),
    Line(
        "Midland line",
        listOf("Perth", "McIver", "Claisebrook", "East Perth", "Mount Lawley", "Maylands", "Meltham", "Bayswater", "Bassendean", "Guildford", "Midland"),
        every = 15,
    ),
    Line(
        "Armadale line",
        listOf("Perth", "McIver", "Claisebrook", "Burswood", "Victoria Park", "Carlisle", "Oats Street", "Queens Park", "Cannington", "Maddington", "Kelmscott", "Armadale"),
        every = 15,
    ),
)

/** A stop, with the lines that call there. */
internal data class StopInfo(val name: String, val lines: List<Line>, val zone: Int, val accessible: Boolean)

/** Every station on any line, once, with the lines through it. */
internal val stops: List<StopInfo> = lines
    .flatMap { line -> line.stations.mapIndexed { i, station -> Triple(station, line, i) } }
    .groupBy { it.first }
    .map { (name, calls) ->
        val furthest = calls.maxOf { it.third }
        StopInfo(
            name = name,
            lines = calls.map { it.second }.distinct(),
            zone = 1 + furthest / 4,
            accessible = name.length % 5 != 0,
        )
    }
    .sortedBy { it.name }

internal fun stopNamed(name: String): StopInfo = stops.first { it.name == name }

internal fun lineNamed(name: String): Line = lines.first { it.name == name }

/** The home stop's next departures, as the app first shows them. */
internal val departures: List<Departure> = listOf(
    Departure("d1", "JDP", "Joondalup", Mode.Train, "1", 2, Running.OnTime),
    Departure("d2", "950", "Elizabeth Quay", Mode.Bus, "Stand C", 4, Running.Late(3)),
    Departure("d3", "MAN", "Mandurah", Mode.Train, "2", 6, Running.OnTime),
    Departure("d4", "Blue CAT", "Northbridge", Mode.Bus, "Stand A", 7, Running.OnTime),
    Departure("d5", "JDP", "Clarkson", Mode.Train, "1", 12, Running.OnTime),
    Departure("d6", "960", "Mirrabooka", Mode.Bus, "Stand D", 14, Running.Cancelled),
    Departure("d7", "MAN", "Cockburn Central", Mode.Train, "2", 16, Running.Late(2)),
    Departure("d8", "935", "Kings Park", Mode.Bus, "Stand B", 19, Running.OnTime),
    Departure("d9", "JDP", "Yanchep", Mode.Train, "1", 22, Running.OnTime),
    Departure("d10", "910", "Fremantle", Mode.Bus, "Stand C", 25, Running.OnTime),
    Departure("d11", "MAN", "Mandurah", Mode.Train, "2", 26, Running.OnTime),
    Departure("d12", "Red CAT", "East Perth", Mode.Bus, "Stand A", 28, Running.OnTime),
)

/**
 * After a refresh: everything a few minutes on, the first two gone, and the
 * late bus later still — so pulling to refresh visibly changes something.
 */
internal fun refreshed(from: List<Departure>): List<Departure> = from
    .map { it.copy(minutes = it.minutes - 3, running = if (it.running is Running.Late) Running.Late(it.running.minutes + 1) else it.running) }
    .filter { it.minutes >= 0 }

/** One leg of a trip. */
internal data class Leg(val how: String, val from: String, val to: String, val at: String, val minutes: Int, val walk: Boolean)

/** A trip somebody took. */
internal data class Trip(
    val id: String,
    val date: LocalDate,
    val title: String,
    val fare: String,
    val legs: List<Leg>,
    val with: List<String>,
)

internal val trips: List<Trip> = listOf(
    Trip(
        "t1", TransitToday, "Home to Elizabeth Quay", "$3.20",
        listOf(
            Leg("Walk", "Home", "Glendalough", "07:58", 6, walk = true),
            Leg("Joondalup line", "Glendalough", "Perth Underground", "08:05", 9, walk = false),
            Leg("950", "Perth Underground", "Elizabeth Quay", "08:21", 7, walk = false),
        ),
        with = listOf("Mia Chen", "Sam Okafor"),
    ),
    Trip(
        "t2", TransitToday.minus(DatePeriod(days = 1)), "Elizabeth Quay to Fremantle", "$4.90",
        listOf(
            Leg("Walk", "Elizabeth Quay", "Perth", "17:40", 9, walk = true),
            Leg("Fremantle line", "Perth", "Fremantle", "17:52", 28, walk = false),
        ),
        with = listOf("Mia Chen"),
    ),
    Trip(
        "t3", TransitToday.minus(DatePeriod(days = 3)), "Leederville to Kings Park", "$3.20",
        listOf(
            Leg("Joondalup line", "Leederville", "Perth Underground", "10:12", 3, walk = false),
            Leg("935", "Perth Underground", "Kings Park", "10:24", 12, walk = false),
            Leg("Walk", "Kings Park", "Fraser Avenue", "10:36", 4, walk = true),
        ),
        with = listOf("Mia Chen", "Sam Okafor", "Priya Nair", "Tom Hughes"),
    ),
)

internal fun tripWithId(id: String): Trip = trips.first { it.id == id }

/**
 * A year of trips, a count a day: none on most Sundays, two on a working day,
 * more on a few, from a sum over the date rather than from a random number.
 */
internal val tripsByDay: Map<LocalDate, Int> = buildMap {
    var day = TransitToday.minus(DatePeriod(days = 364))
    while (day <= TransitToday) {
        val n = day.toEpochDays().toInt()
        val weekday = ((n % 7) + 7 + 3) % 7 // Monday is 0
        val count = when {
            weekday == 6 && n % 3 != 0 -> 0
            weekday == 5 -> n % 3
            else -> 2 + (if (n % 11 == 0) 2 else 0) + (if (n % 17 == 0) 1 else 0)
        }
        if (count > 0) put(day, count)
        day = day.plus(DatePeriod(days = 1))
    }
}

/** The weekly fare cap and what this week has spent towards it. */
internal const val WeeklyCap = 50f
internal const val SpentThisWeek = 31.4f

/** The ticket's balance, and the daily cap: three paid trips, the rest free. */
internal const val Balance = 23.6f
internal const val DailyCapTrips = 3
internal const val TripsToday = 2

/** Weekday, Saturday and Sunday timetables for a line, from its frequency. */
internal fun timetable(line: Line, day: Int): List<TimetableRow> {
    val every = when (day) {
        0 -> line.every
        1 -> line.every + 5
        else -> line.every * 2
    }
    val first = 5 * 60 + 20 + day * 30
    return List(12) { i ->
        val at = first + i * every
        val back = at + line.stations.size * 3
        TimetableRow(
            service = "${line.name.first()}${100 + i}",
            departs = clock(at),
            arrives = clock(back),
            platform = if (i % 2 == 0) "1" else "2",
        )
    }
}

/** One row of a timetable. */
internal data class TimetableRow(val service: String, val departs: String, val arrives: String, val platform: String)

private fun clock(minutes: Int): String {
    val h = (minutes / 60) % 24
    val m = minutes % 60
    return "${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}"
}
