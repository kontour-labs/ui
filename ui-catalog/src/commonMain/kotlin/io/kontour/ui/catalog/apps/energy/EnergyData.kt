package io.kontour.ui.catalog.apps.energy

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.asTimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toInstant
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/*
 * A house in Perth with a 6.6 kW roof and a battery, on a spring afternoon.
 * Made up, and made up the same way every time: the gallery's goldens draw it,
 * so nothing here reads a clock or a random number.
 */

/** The day the app is open on. */
internal val EnergyToday = LocalDate(2026, 10, 20)

/** The hour it is now: everything after is a forecast. */
internal const val NowHour = 14

/**
 * Perth's clock, as a fixed offset rather than a zone by name: the web build
 * has no zone database to look one up in, and Perth keeps no summer time.
 */
internal val PerthTime: TimeZone = UtcOffset(hours = 8).asTimeZone()

/** One hour of today, in kilowatts on average across it. */
internal class HourReading(val hour: Int, val solar: Double, val home: Double) {
    /** What the house took from the roof. */
    val fromSolar: Double get() = min(home, solar)

    /** What the battery gave the house in the evening, once the roof had stopped. */
    val fromBattery: Double get() = if (hour in 17..22) min(home - fromSolar, BatteryDraw) else 0.0

    /** The rest, from the grid. */
    val fromGrid: Double get() = (home - fromSolar - fromBattery).coerceAtLeast(0.0)

    /** What the roof made that the house did not use: to the battery first, the rest to the grid. */
    val spare: Double get() = (solar - fromSolar).coerceAtLeast(0.0)

    /** Whether this hour has happened yet. */
    val forecast: Boolean get() = hour > NowHour
}

private const val BatteryDraw = 1.4

/** The roof's output at [hour] on a clear day, scaled by [sky]. */
internal fun solarAt(hour: Double, sky: Double = 1.0): Double =
    if (hour < 6 || hour > 19) 0.0 else max(0.0, 4.8 * sky * sin(PI * (hour - 6) / 13))

/** What the house uses at [hour]: a morning, a quiet middle and an evening. */
internal fun homeAt(hour: Int, day: Int = 0): Double {
    val wobble = ((hour * 37 + day * 53) % 9) / 30.0
    return when (hour) {
        in 0..5 -> 0.42
        in 6..8 -> 1.5 + wobble
        in 9..15 -> 0.7 + wobble
        in 16..21 -> 2.2 + wobble * 2
        else -> 0.8
    } + wobble / 2
}

internal val todayHours: List<HourReading> = List(24) { h ->
    HourReading(h, solarAt(h.toDouble(), sky = if (h == 11) 0.7 else 1.0), homeAt(h))
}

/** The likely range of the roof's output for the hours still to come. */
internal class SolarForecast(val hour: Int, val low: Double, val likely: Double, val high: Double)

internal val forecastHours: List<SolarForecast> = (NowHour..23).map { h ->
    val likely = solarAt(h.toDouble())
    SolarForecast(h, likely * 0.8, likely, likely * 1.12)
}

/** One reading of the meter, ten seconds apart. */
internal class LiveReading(val at: Instant, val solar: Double, val home: Double)

/** Twenty past two this afternoon, where the live readings start. */
internal val EnergyNow: Instant = LocalDateTime(2026, 10, 20, NowHour, 20).toInstant(PerthTime)

/** The [count] readings up to [step] steps after [EnergyNow]. */
internal fun liveReadings(step: Int, count: Int = 60): List<LiveReading> = List(count) { i ->
    // Counted from well before now, so the cycles below never see a negative.
    val k = step - count + 1 + i + 10_000
    // A cloud passes over every couple of minutes, and the kettle goes on.
    val cloud = if ((k / 7) % 3 == 1) 0.55 else 1.0
    val kettle = if (k % 23 in 0..2) 2.1 else 0.0
    val flicker = ((k * 31) % 11) / 40.0
    LiveReading(EnergyNow + ((k - 10_000) * 10).seconds, 3.9 * cloud + flicker, 0.8 + kettle + flicker / 2)
}

/** Where the house's energy went over a month, by the things that used it. */
internal enum class Appliance(val label: String) {
    Cooling("Cooling"),
    Hot("Hot water"),
    Kitchen("Kitchen"),
    Laundry("Laundry"),
    Car("Car"),
    Other("Everything else"),
}

/** A day's use by one appliance, in kilowatt-hours. */
internal class DayUse(val date: LocalDate, val appliance: Appliance, val kwh: Double)

/** The last [days] days, ending today. */
internal fun dayUses(days: Int): List<DayUse> = (days - 1 downTo 0).flatMap { back ->
    val date = EnergyToday.minus(DatePeriod(days = back))
    val d = date.day
    Appliance.entries.mapIndexed { i, appliance ->
        val base = listOf(3.2, 2.6, 1.9, 1.1, 4.0, 1.6)[i]
        val swing = ((d * 13 + i * 29) % 10) / 10.0
        // The car charges every third day, and the washing goes on at weekends.
        val kwh = when (appliance) {
            Appliance.Car -> if (d % 3 == 0) base * 2.4 else 0.0
            Appliance.Laundry -> if (date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY) base * 2.2 else base * 0.3
            else -> base + swing
        }
        DayUse(date, appliance, kwh)
    }
}

/** A weekday's use at an hour, for the heat map: when the house is busiest. */
internal class HourOfWeek(val day: String, val hour: String, val kwh: Double)

internal val weekDays = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
internal val dayParts = listOf("6am", "9am", "Noon", "3pm", "6pm", "9pm")

internal val hoursOfWeek: List<HourOfWeek> = weekDays.flatMapIndexed { d, day ->
    dayParts.mapIndexed { p, part ->
        val hour = 6 + p * 3
        val weekend = d >= 5
        val kwh = homeAt(hour, d) * if (weekend && p in 1..3) 1.6 else 1.0
        HourOfWeek(day, part, (kwh * 10).roundToInt() / 10.0)
    }
}

/** A month of the last year, all of it in kilowatt-hours but the bill. */
internal class EnergyMonth(
    val name: String,
    val generated: Double,
    val used: Double,
    val imported: Double,
    val exported: Double,
    val bill: Double,
    val lowestDay: Double,
    val highestDay: Double,
    val dailyUse: List<Double>,
    val maxTemp: Double,
) {
    val selfPowered: Double get() = 1 - imported / used
}

internal val monthNames = listOf("Nov", "Dec", "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct")

internal val months: List<EnergyMonth> = monthNames.mapIndexed { i, name ->
    // Summer at the turn of the year, winter in the middle: the roof makes
    // most when the house needs cooling, least when it needs heating, and in
    // summer more of what it makes is used at home.
    val season = cos(2 * PI * (i - 2) / 12.0)
    val wobble = ((i * 37) % 7 - 3) / 40.0
    val generated = (620 + 300 * season) * (1 + wobble)
    val used = (600 + 120 * season * season + if (season < 0) -160 * season else 0.0) * (1 - wobble / 2)
    val selfUse = min(used * (0.5 + 0.18 * season.coerceAtLeast(0.0)), generated * 0.7)
    val imported = used - selfUse
    val exported = generated - selfUse
    val daily = List(30) { d -> used / 30 * (0.75 + ((d * 17 + i * 7) % 13) / 24.0) + if (d % 11 == 0) 9.0 else 0.0 }
    EnergyMonth(
        name = name,
        generated = generated.round1(),
        used = used.round1(),
        imported = imported.round1(),
        exported = exported.round1(),
        bill = (imported * 0.31 - exported * 0.07 + 30 * 1.12).round1(),
        lowestDay = daily.min().round1(),
        highestDay = daily.max().round1(),
        dailyUse = daily.map { it.round1() },
        maxTemp = (27 + 9 * season + wobble * 20).round1(),
    )
}

/** Where each month's generation went, for the stream of it. */
internal class MonthFlow(val month: String, val into: String, val kwh: Double)

internal val flows: List<MonthFlow> = months.flatMap { m ->
    val battery = min(m.exported * 0.4, 180.0)
    listOf(
        MonthFlow(m.name, "The house", (m.generated - m.exported).round1()),
        MonthFlow(m.name, "The battery", battery.round1()),
        MonthFlow(m.name, "The grid", (m.exported - battery).round1()),
    )
}

private fun Double.round1(): Double = (this * 10).roundToInt() / 10.0

/** A kilowatt figure as the app writes it: one decimal place and its unit. */
internal fun kilowatts(value: Double): String = "${(value * 10).roundToInt() / 10.0} kW"

/** A kilowatt-hour figure: whole numbers past a hundred, one place below. */
internal fun kilowattHours(value: Double): String =
    if (value >= 100) "${value.roundToInt()} kWh" else "${(value * 10).roundToInt() / 10.0} kWh"
