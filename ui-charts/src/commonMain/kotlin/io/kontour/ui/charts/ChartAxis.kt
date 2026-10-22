package io.kontour.ui.charts

import androidx.compose.runtime.Immutable
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.math.roundToLong
import kotlin.time.Instant

/**
 * One axis of a [Chart]: what its values are, and how they are labelled.
 *
 * ```kotlin
 * Chart(
 *     xAxis = ChartAxis.date(),
 *     yAxis = ChartAxis.number(title = "Riders"),
 * ) { … }
 * ```
 *
 * Four kinds, by what is plotted along it:
 *
 * | | Values | Ticks and labels |
 * |---|---|---|
 * | [number] | any `Number` | round steps — 1, 2, 2.5 or 5 of a power of ten — in the reader's digits |
 * | [date] | `LocalDate` | days, weeks, months or years, whichever fits, in the reader's calendar words |
 * | [instant] | `Instant` | seconds, minutes or hours on the clock of a time zone, then as [date]'s |
 * | [category] | anything | one band each, in order |
 *
 * Numbers are written in full, "12,000" rather than "12K", on every platform:
 * not every platform can write the short form in every language, and an axis
 * that reads one way on one phone and another way on the next is worse.
 *
 * A bar runs along whichever axis is the number: with a [category] axis across
 * and a [number] axis up, bars stand; the other way round, they lie on their
 * side, which is the better chart for long category names.
 *
 * Labels are the reader's own unless [number]'s or [date]'s `label` says
 * otherwise: `Theme.formats` writes every number and date, so a chart in German
 * has German digits and German month names with nothing said.
 */
@Immutable
class ChartAxis<V : Any> internal constructor(
    internal val kind: AxisKind,
    internal val title: String?,
    internal val position: ChartAxisPosition,
    internal val showGridlines: Boolean,
    internal val showLabels: Boolean,
    internal val tickCount: Int,
    internal val domain: ClosedFloatingPointRange<Double>?,
    internal val includeZero: Boolean,
    internal val categories: List<V>?,
    internal val numberLabel: ((Double) -> String)?,
    internal val dateLabel: ((LocalDate) -> String)?,
    internal val categoryLabel: ((V) -> String)?,
    internal val timeZone: TimeZone? = null,
    internal val instantLabel: ((Instant) -> String)? = null,
) {
    /**
     * A value of this axis in the chart's own units: a number, an epoch day, an
     * epoch millisecond, or nothing for a category.
     */
    internal fun encode(value: V): Double = when (kind) {
        AxisKind.Number -> (value as Number).toDouble()
        AxisKind.Date -> (value as LocalDate).toEpochDays().toDouble()
        AxisKind.Instant -> (value as Instant).toEpochMilliseconds().toDouble()
        AxisKind.Category -> error("a category is placed by its index, not encoded")
    }

    /** The value at [position] in the chart's own units, for a number or a date axis. */
    @Suppress("UNCHECKED_CAST")
    internal fun decode(position: Double): V = when (kind) {
        AxisKind.Number -> position as V
        AxisKind.Date -> LocalDate.fromEpochDays(position.roundToLong()) as V
        AxisKind.Instant -> Instant.fromEpochMilliseconds(position.roundToLong()) as V
        AxisKind.Category -> error("a category is decoded from its index")
    }

    companion object {
        /**
         * An axis of numbers, with round steps between its ticks.
         *
         * @param domain The range the axis spans. Left out, it is the data's,
         *   widened to the nearest round steps — and to zero, for bars and areas,
         *   whose length is the value.
         * @param title What the axis measures: "Riders", "Temperature (°C)".
         *   Drawn along it, and said first in what a screen reader hears of it.
         * @param label How a tick, and any value on this axis, is written. Left
         *   out, `Theme.formats` writes it to the precision of the tick step.
         * @param tickCount About how many ticks to draw. The steps are round, so
         *   the count is a target rather than a promise.
         * @param includeZero Whether zero is always on the axis, as it is for a
         *   bar or an area whatever this says.
         */
        fun number(
            domain: ClosedFloatingPointRange<Double>? = null,
            title: String? = null,
            label: ((Double) -> String)? = null,
            tickCount: Int = ChartAxisDefaults.TickCount,
            position: ChartAxisPosition = ChartAxisPosition.Auto,
            includeZero: Boolean = false,
            showGridlines: Boolean = true,
            showLabels: Boolean = true,
        ): ChartAxis<Number> = ChartAxis(
            kind = AxisKind.Number,
            title = title,
            position = position,
            showGridlines = showGridlines,
            showLabels = showLabels,
            tickCount = tickCount,
            domain = domain,
            includeZero = includeZero,
            categories = null,
            numberLabel = label,
            dateLabel = null,
            categoryLabel = null,
        )

        /**
         * An axis of dates, ticked by whichever calendar unit fits: days, weeks
         * (from the reader's first day of the week), months, or years.
         *
         * @param domain The range the axis spans; left out, the data's.
         * @param title What the dates are of, if it needs saying.
         * @param label How a tick, and any date on this axis, is written. Left
         *   out, `Theme.formats` writes it in the unit the ticks are in: a short
         *   date, a month's name, or a year.
         * @param tickCount About how many ticks to draw.
         */
        fun date(
            domain: ClosedRange<LocalDate>? = null,
            title: String? = null,
            label: ((LocalDate) -> String)? = null,
            tickCount: Int = ChartAxisDefaults.TickCount,
            position: ChartAxisPosition = ChartAxisPosition.Auto,
            showGridlines: Boolean = true,
            showLabels: Boolean = true,
        ): ChartAxis<LocalDate> = ChartAxis(
            kind = AxisKind.Date,
            title = title,
            position = position,
            showGridlines = showGridlines,
            showLabels = showLabels,
            tickCount = tickCount,
            domain = domain?.let { it.start.toEpochDays().toDouble()..it.endInclusive.toEpochDays().toDouble() },
            includeZero = false,
            categories = null,
            numberLabel = null,
            dateLabel = label,
            categoryLabel = null,
        )

        /**
         * An axis of moments, ticked on the clock of [timeZone]: every few
         * seconds, minutes or hours, from its midnight, for a span of a day or
         * less, and by the calendar, as [date] is, for a longer one.
         *
         * The ticks are where the zone's clocks show them, so a chart of a day
         * that a clock change falls in has a tick at three that is two hours
         * after the one at midnight, as the riders who were there would say.
         *
         * @param timeZone Whose clock the ticks are read off: the device's,
         *   left out, or a place's, for a timetable of somewhere else.
         * @param domain The span the axis covers; left out, the data's.
         * @param title What the moments are of, if it needs saying.
         * @param label How a tick, and any moment on this axis, is written.
         *   Left out, `Theme.formats` writes the time, with the date where it
         *   changes, or the date alone for ticks a day or more apart.
         * @param tickCount About how many ticks to draw.
         */
        fun instant(
            timeZone: TimeZone = TimeZone.currentSystemDefault(),
            domain: ClosedRange<Instant>? = null,
            title: String? = null,
            label: ((Instant) -> String)? = null,
            tickCount: Int = ChartAxisDefaults.TickCount,
            position: ChartAxisPosition = ChartAxisPosition.Auto,
            showGridlines: Boolean = true,
            showLabels: Boolean = true,
        ): ChartAxis<Instant> = ChartAxis(
            kind = AxisKind.Instant,
            title = title,
            position = position,
            showGridlines = showGridlines,
            showLabels = showLabels,
            tickCount = tickCount,
            domain = domain?.let { it.start.toEpochMilliseconds().toDouble()..it.endInclusive.toEpochMilliseconds().toDouble() },
            includeZero = false,
            categories = null,
            numberLabel = null,
            dateLabel = null,
            categoryLabel = null,
            timeZone = timeZone,
            instantLabel = label,
        )

        /**
         * An axis of categories, a band each.
         *
         * @param categories The bands, in order. Left out, they are the values
         *   the marks use, in the order they first appear; given, they also fix
         *   which appear at all, and a value not among them is left off.
         * @param title What the categories are, if it needs saying.
         * @param label How a category is written. Left out, a `String` is its
         *   own label; anything else should say how it is named.
         */
        fun <C : Any> category(
            categories: List<C>? = null,
            title: String? = null,
            label: ((C) -> String)? = null,
            position: ChartAxisPosition = ChartAxisPosition.Auto,
            showGridlines: Boolean = false,
            showLabels: Boolean = true,
        ): ChartAxis<C> = ChartAxis(
            kind = AxisKind.Category,
            title = title,
            position = position,
            showGridlines = showGridlines,
            showLabels = showLabels,
            tickCount = 0,
            domain = null,
            includeZero = false,
            categories = categories,
            numberLabel = null,
            dateLabel = null,
            categoryLabel = label,
        )
    }
}

/** Which edge of the plot an axis is drawn along. */
enum class ChartAxisPosition {
    /** The bottom for the axis across, and the start edge for the axis up — the left, or the right in a right-to-left language. */
    Auto,

    /** The top for the axis across, the start edge for the axis up. */
    Start,

    /** The bottom for the axis across, the end edge for the axis up. */
    End,
}

/** The values [ChartAxis] starts from. */
object ChartAxisDefaults {
    /**
     * About how many ticks an axis of numbers or dates aims for: five, so that
     * a chart the height of a card has room between its gridlines for a label
     * at 200% text and a reader can still read a value off it.
     */
    const val TickCount: Int = 5
}

internal enum class AxisKind { Number, Date, Instant, Category }
