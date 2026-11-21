package io.kontour.ui.components.datetime

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.error
import io.kontour.ui.components.text.TextFieldDefaults
import io.kontour.ui.motion.enterOrFade
import io.kontour.ui.motion.exitOrFade
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import io.kontour.ui.locale.TimePart
import io.kontour.ui.locale.HourCycle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.runtime.CompositionLocalProvider
import io.kontour.ui.locale.LocaleFormats
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import io.kontour.ui.a11y.contrastEdge
import io.kontour.ui.foundation.Surface
import io.kontour.ui.foundation.Text
import io.kontour.ui.input.focusRing
import io.kontour.ui.input.pointerCursor
import io.kontour.ui.interaction.kontourIndication
import io.kontour.ui.theme.Theme
import kotlinx.datetime.LocalTime
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.TimeZone
import kotlin.time.Clock
import io.kontour.ui.components.action.ButtonVariant
import io.kontour.ui.components.action.ButtonSize
import io.kontour.ui.components.action.Button
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import kotlin.math.abs

/**
 * Picks a time of day.
 *
 * Three wheels — hour, minute, and a day period when the reader's clock has one —
 * rather than a clock dial. For a transit app the value is almost always being
 * *adjusted* ("leave at 8:15 instead of 8:00"), and a wheel gets there in one
 * flick where a dial needs two precise drags.
 *
 * ```
 * TimePicker(value = departAt, onValueChange = viewModel::setDepartAt)
 * ```
 *
 * The clock comes from [Theme.formats] — 24-hour, 12-hour, or Japan's 0–11 — and
 * so do the order of the wheels, what sits between the hour and the minute, and
 * the digits: `오후 2:05`, `14.05`, `٢:٠٥ م`. It follows the platform setting
 * rather than being a parameter every call site has to remember to pass.
 *
 * @param enabled Whether the wheels can be turned. Disabled, the time is shown
 *   in the disabled content colour and answers nothing.
 * @param minuteStep Coarsens the minute wheel. A planner rarely wants
 *   minute-level precision, and 5 turns 60 rows into 12.
 * @param infinite Whether the hour and minute wheels go round, as a clock's
 *   numbers do: past :59 comes :00, and past 23 comes 0, with no end to reach.
 *   On by default. On a 12-hour clock the day period turns with the hour, so
 *   11 PM turned on is 12 AM, not noon, as an iOS picker's does. Only the
 *   wheel wraps: the minutes going past :00 leave the hour as it is, and a
 *   [DateTimePicker]'s day as it is. Off, each wheel stops at its ends, as it
 *   did before 0.5.1.
 * @param earliest The earliest time the wheels can be turned to, or null for
 *   none. The hour wheel starts at its hour, and on that hour the minute wheel
 *   starts at its minute, opening up again once the hour moves past it; there
 *   are no rows before it to turn to, and the drum gives at it as it does at the
 *   end of a wheel that does not go round. With [minuteStep], the first row is
 *   the step at or after it. A [value] before it is moved forward to it. The
 *   keys and a screen reader have the same rows and no others: Home is the
 *   earliest, an arrow at it stays there, and the range a screen reader adjusts
 *   starts at it.
 * @param latest The latest, as [earliest] is the earliest: the wheels stop at
 *   the step at or before it. Bounded, a wheel goes round only while it has all
 *   its rows, so the hours stop at the bounds' hours; on a 12-hour clock they
 *   read in the clock's order, 12 to 11, and the day period offers only the
 *   halves of the day the bounds reach.
 * @param now The time now, for the **Now** button under the wheels: pressed,
 *   the wheels scroll to it — rounded to the nearest [minuteStep] — rather than
 *   jumping, so the reader sees where the time went. The system clock's by
 *   default; null for no button, on a picker whose time has nothing to do with
 *   the present.
 * @param onValueChangeFinished Called when a wheel comes to rest after the
 *   reader turned it, and once when the wheels have reached Now.
 *   [onValueChange] follows every row a wheel passes; what should happen once —
 *   a save, a request — belongs here. Not called when [value] is set by the
 *   caller.
 */
@Composable
fun TimePicker(
    value: LocalTime,
    onValueChange: (LocalTime) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    minuteStep: Int = 1,
    infinite: Boolean = true,
    earliest: LocalTime? = null,
    latest: LocalTime? = null,
    now: (() -> LocalTime)? = TimePickerDefaults::currentTime,
    formats: LocaleFormats = Theme.formats,
    onValueChangeFinished: (() -> Unit)? = null,
) = TimeWheels(
    value = value,
    onValueChange = onValueChange,
    modifier = modifier,
    enabled = enabled,
    minuteStep = minuteStep,
    infinite = infinite,
    now = now,
    formats = formats,
    onValueChangeFinished = onValueChangeFinished,
    onNow = null,
    earliest = earliest,
    latest = latest,
)

/** What a [TimePicker] takes by default. */
object TimePickerDefaults {
    /** The time now on the system's clock, in its zone: what a picker's Now turns to. */
    fun currentTime(): LocalTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).time
}

/**
 * [time] to the nearest [step] of the minute wheel, half up, and whether that
 * went past midnight into the next day: 23:58 by fives is 00:00 tomorrow.
 */
internal fun nearestStep(time: LocalTime, step: Int): Pair<LocalTime, Boolean> {
    var minute = (time.minute * 2 + step) / (2 * step) * step
    var hour = time.hour
    if (minute >= 60) {
        minute = 0
        hour += 1
    }
    return if (hour >= 24) LocalTime(0, minute) to true else LocalTime(hour, minute) to false
}

/**
 * A time picker's bounds, on the minute wheel's steps: [first] the step at or
 * after the earliest time (a time with seconds in it is after the minute it
 * is in), [last] the step at or before the latest. Both are midnight's ends
 * of the day where they are not given.
 *
 * An earliest past the day's last step leaves only that step, as no step
 * after it is today's; a latest before the earliest leaves only the earliest.
 * Neither throws: a bound worked out from the time now — *leave no sooner than
 * now*, *arrive no sooner than now and the quickest way there* — crosses
 * midnight or the other bound as the clock runs, and a picker that crashed
 * then would be the worse answer.
 */
internal class TimeBounds(earliest: LocalTime?, latest: LocalTime?, step: Int) {
    private val lastStep = LocalTime(23, (59 / step) * step)
    val bounded = earliest != null || latest != null
    val first: LocalTime = earliest?.let { stepUp(it, step) } ?: LocalTime(0, 0)
    val last: LocalTime = latest?.let { stepDown(it, step) }?.let { if (it < first) first else it } ?: lastStep

    /** [time], moved in to the nearest bound when it is outside them. */
    fun clamp(time: LocalTime): LocalTime = when {
        time < first -> first
        time > last -> last
        else -> time
    }

    /** Whether any minute of [hour], on the 24-hour clock, is inside the bounds. */
    fun allowsHour(hour: Int): Boolean = hour in first.hour..last.hour

    /** Whether [minute] of [hour] is inside the bounds. */
    fun allows(hour: Int, minute: Int): Boolean = LocalTime(hour, minute).let { it >= first && it <= last }

    private fun stepUp(time: LocalTime, step: Int): LocalTime {
        // A second into 9:07 is after 9:07: the earliest minute is 9:08.
        val past = time.second > 0 || time.nanosecond > 0
        val minutes = time.hour * 60 + time.minute + if (past) 1 else 0
        val stepped = (minutes + step - 1) / step * step
        val hour = stepped / 60
        val minute = stepped % 60
        return if (hour > 23 || (hour == 23 && minute > lastStep.minute)) lastStep else LocalTime(hour, minute)
    }

    private fun stepDown(time: LocalTime, step: Int): LocalTime = LocalTime(time.hour, time.minute / step * step)
}

/**
 * Whether turning an hour wheel of [hours] the short way from row [from] to row
 * [to] passes the day period's change an odd number of times: from 11 to 12 on
 * a 12-hour clock, or 11 to 0 on Japan's, either way.
 *
 * Counted along the path rather than read from the two ends, because a flick
 * can carry the wheel several rows in a frame, and the wheel reports only
 * where it is.
 */
internal fun passesPeriodChange(hours: List<Int>, from: Int, to: Int): Boolean {
    val size = hours.size
    if (from !in hours.indices || to !in hours.indices || from == to) return false
    val forward = (to - from).mod(size)
    val turn = if (forward * 2 > size) forward - size else forward
    val step = if (turn > 0) 1 else -1
    var row = from
    var changes = 0
    repeat(abs(turn)) {
        val next = (row + step).mod(size)
        val a = hours[row] % 12
        val b = hours[next] % 12
        if ((a == 11 && b == 0) || (a == 0 && b == 11)) changes++
        row = next
    }
    return changes % 2 == 1
}

/**
 * [TimePicker], and [onNow] for a [DateTimePicker], told when Now is pressed
 * whether the time it turns to is tomorrow's, so the date can go with it. It
 * answers whether the date went: where tomorrow could not be taken, Now is the
 * last step of today rather than midnight at its start.
 *
 * [narrow] wheels are as wide as their widest number or word and the room
 * either side of it the day period's has, rather than 72dp and 64: the date
 * and time picker's, so they fit beside a calendar on a phone.
 */
@Composable
internal fun TimeWheels(
    value: LocalTime,
    onValueChange: (LocalTime) -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    minuteStep: Int,
    infinite: Boolean,
    now: (() -> LocalTime)?,
    formats: LocaleFormats,
    onValueChangeFinished: (() -> Unit)?,
    onNow: ((tomorrow: Boolean) -> Boolean)?,
    narrow: Boolean = false,
    earliest: LocalTime? = null,
    latest: LocalTime? = null,
) {
    require(minuteStep in 1..30) { "minuteStep must divide an hour sensibly" }
    val bounds = remember(earliest, latest, minuteStep) { TimeBounds(earliest, latest, minuteStep) }

    val minutes = remember(minuteStep) { (0 until 60 step minuteStep).toList() }
    val cycle = formats.conventions.hourCycle
    val layout = formats.timeLayout
    // The numbers the clock shows: 0–23, 1–12, 0–11 or 1–24.
    val hours = remember(cycle) {
        when (cycle) {
            HourCycle.H23 -> (0..23).toList()
            HourCycle.H12 -> (1..12).toList()
            HourCycle.H11 -> (0..11).toList()
            HourCycle.H24 -> (1..24).toList()
        }
    }

    fun displayHourOf(hour: Int) = when (cycle) {
        HourCycle.H23 -> hour
        HourCycle.H12 -> (hour % 12).let { if (it == 0) 12 else it }
        HourCycle.H11 -> hour % 12
        HourCycle.H24 -> if (hour == 0) 24 else hour
    }
    // Shown inside the bounds, whatever was asked: a value before the earliest
    // shows the earliest, and is moved there below.
    val shown = bounds.clamp(value)
    val isPm = shown.hour >= 12

    // **Bounded, each wheel has only the rows the bounds allow**, and the drum's
    // own end is where it stops. Asked for by the planner that has to keep
    // *leave at* from before now: the app let the wheel stop on 9:10 and set
    // 9:15, and the drum jumped there. The hours in the clock's own order, on
    // the 24-hour clock, the half of the day shown, or the whole of it: what the
    // bounds keep out of a 12-hour wheel's 1 to 12 is 12 and 1 and 2 at one
    // end, and its rows would not be one run.
    val boundedHours: List<Int> = remember(bounds, cycle, isPm) {
        val day = if (cycle.usesDayPeriod) (if (isPm) 12..23 else 0..11) else 0..23
        day.filter { bounds.allowsHour(it) }
    }
    // On the bounds' hours, the minutes inside them; on every other hour, all.
    val shownMinutes = remember(bounds, minutes, shown.hour) {
        if (!bounds.bounded) minutes else minutes.filter { bounds.allows(shown.hour, it) }
    }
    val hourRows: List<Int> = if (bounds.bounded) boundedHours.map(::displayHourOf) else hours
    val hourIndex = if (bounds.bounded) {
        boundedHours.indexOf(shown.hour).coerceAtLeast(0)
    } else {
        hours.indexOf(displayHourOf(shown.hour)).coerceAtLeast(0)
    }
    // A minute between two steps shows the step after it — except past the
    // last step, where the one after is the next hour's :00, which this wheel
    // cannot show without the hour. It showed this hour's :00 instead, and the
    // drum, reporting the row it opened on, turned 9:58 into 9:00. The last
    // step is the nearest this hour has.
    val minuteIndex = shownMinutes.indexOfFirst { it >= shown.minute }.let { if (it < 0) shownMinutes.lastIndex else it }
    // A wheel goes round only while it has all its rows: one that stops at a
    // bound has an end, and is a wheel with ends.
    val hoursGoRound = infinite && !bounds.bounded
    val minutesGoRound = infinite && shownMinutes.size == minutes.size

    // The value as last reported, not as last composed: two wheels turning in
    // the same frame — as they do to Now — each change one part of it, and a
    // part read from the composed value undid the other wheel's.
    val working = remember { Working(value) }
    working.compose(value)

    // Every change inside the bounds: an hour turned onto the earliest's, from a
    // minute before the earliest's, is the earliest; a day period turned to a
    // half of the day the bounds do not reach all of, the nearest time in it.
    fun report(time: LocalTime) {
        val next = bounds.clamp(time)
        if (next == working.value) return
        working.value = next
        onValueChange(next)
    }

    fun emit(
        hourValue: Int = displayHourOf(working.value.hour),
        minute: Int = working.value.minute,
        pm: Boolean = working.value.hour >= 12,
    ) {
        val hour24 = when (cycle) {
            HourCycle.H23 -> hourValue
            HourCycle.H24 -> hourValue % 24
            HourCycle.H11 -> hourValue + if (pm) 12 else 0
            HourCycle.H12 -> (hourValue % 12) + if (pm) 12 else 0
        }
        report(LocalTime(hour24.coerceIn(0, 23), minute.coerceIn(0, 59)))
    }

    // A value the caller set outside the bounds, or bounds that moved past the
    // value — the earliest that is *now*, a minute on — moved inside them, once,
    // as a change: the wheels already show it there.
    LaunchedEffect(value, bounds) {
        if (bounds.bounded && bounds.clamp(value) != value) report(value)
    }

    // The turns Now asks of the hour, minute and day-period wheels.
    var turns by remember { mutableStateOf<List<WheelTurn?>>(listOf(null, null, null)) }

    val strings = Theme.strings
    // The locale's own words, and the same ones the time is written with: the
    // wheel said `AM` beside a field that said `2:05 pm`.
    val periods = remember(formats) { listOf(formats.dayPeriod(LocalTime(0, 0)), formats.dayPeriod(LocalTime(12, 0))) }
    // In the reader's digits, padded as their clock pads: `09`, `9`, `٠٩`.
    val zero = formats.symbols.zeroDigit
    val hourLabel: (Int) -> String = remember(formats, layout) {
        { hour -> formats.integer(hour).let { if (layout.hourPadded) it.padStart(2, zero) else it } }
    }
    val minuteLabel: (Int) -> String = remember(formats) { { minute -> formats.integer(minute).padStart(2, zero) } }
    val muted = if (enabled) Theme.colours.contentMuted else Theme.colours.contentDisabled

    // Measured, at the reader's type size, where the wheels are narrow: the
    // widest of the numbers either wheel can show. One width for both, so the
    // separator sits in the middle of the pair.
    val measurer = rememberTextMeasurer()
    val rowStyle = Theme.typography.titleLarge
    val density = LocalDensity.current
    val clockWheelWidth = if (!narrow) {
        ClockWheelWidth
    } else {
        remember(hours, minutes, hourLabel, minuteLabel, rowStyle, density) {
            val widest = (hours.map(hourLabel) + minutes.map(minuteLabel)).maxOf { measurer.measure(it, rowStyle).size.width }
            with(density) { widest.toDp() + PeriodWheelPadding }
        }
    }

    // The hour and minute read left to right in every language — a clock's
    // numbers are a number, not words — so they are laid out as one
    // left-to-right group, and only the day period takes the line's direction.
    val clock = @Composable {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                WheelDrum(
                    items = hourRows,
                    selectedIndex = hourIndex,
                    onSelectedIndexChange = { index ->
                        if (bounds.bounded) {
                            // Bounded, a row is an hour of the half of the day
                            // shown, and the wheel does not go round to turn it.
                            boundedHours.getOrNull(index)?.let { report(LocalTime(it, working.value.minute)) }
                            return@WheelDrum
                        }
                        val pm = working.value.hour >= 12
                        // Round the end of the clock, the day period turns
                        // with it: 11 PM on is 12 AM. Not while Now is turning
                        // the wheels, whose day period turns on its own.
                        val turning = turns[0]?.done?.isCompleted == false
                        val flips = hoursGoRound && cycle.usesDayPeriod && !turning &&
                            passesPeriodChange(hours, hours.indexOf(displayHourOf(working.value.hour)), index)
                        emit(hourValue = hours[index], pm = pm != flips)
                    },
                    itemLabel = hourLabel,
                    modifier = Modifier.width(clockWheelWidth),
                    enabled = enabled,
                    contentDescription = strings.hour,
                    infinite = hoursGoRound,
                    onSettled = onValueChangeFinished,
                    turn = turns[0],
                    itemKey = if (bounds.bounded) ({ it }) else null,
                )

                Text(layout.separator, style = Theme.typography.headlineSmall, colour = muted)

                WheelDrum(
                    items = shownMinutes,
                    selectedIndex = minuteIndex,
                    onSelectedIndexChange = { index -> shownMinutes.getOrNull(index)?.let { emit(minute = it) } },
                    itemLabel = minuteLabel,
                    modifier = Modifier.width(clockWheelWidth),
                    enabled = enabled,
                    contentDescription = strings.minute,
                    infinite = minutesGoRound,
                    onSettled = onValueChangeFinished,
                    turn = turns[1],
                    // So the minutes that open up past the bound's hour come in
                    // above the one in the band, rather than moving it.
                    itemKey = if (bounds.bounded) ({ it }) else null,
                )
            }
        }
    }

    // Wide enough for the longer of the two words, and never narrower than it
    // was for `AM`: `முற்பகல்` is not going to fit in 64dp. Narrow, the word
    // and its room are all of it.
    val periodWidth = remember(periods, rowStyle, density, narrow) {
        val widest = with(density) { periods.maxOf { measurer.measure(it, rowStyle).size.width }.toDp() + PeriodWheelPadding }
        if (narrow) widest else maxOf(PeriodWheelWidth, widest)
    }

    // A third wheel, not a segmented control below.
    //
    // AM/PM is part of the time, and the picker reads as one instrument
    // when every column of it works the same way. Underneath as a
    // segmented control it was a second control the thumb had to travel
    // to, in a different idiom, deciding something the two wheels above
    // it were already deciding half of.
    //
    // Two items on a five-row wheel is mostly empty space, so this one
    // shows three rows. Before the time or after it, as the locale writes
    // it: `2:05 pm`, but `오후 2:05`.
    // The halves of the day the bounds reach: a picker that cannot be before
    // 2 PM has no morning to offer.
    val shownPeriods: List<Boolean> = remember(bounds) {
        if (!bounds.bounded) listOf(false, true) else listOfNotNull(false.takeIf { bounds.first.hour < 12 }, true.takeIf { bounds.last.hour >= 12 })
    }
    val period = @Composable { first: Boolean ->
        WheelDrum(
            items = shownPeriods.map { pm -> periods[if (pm) 1 else 0] },
            selectedIndex = shownPeriods.indexOf(isPm).coerceAtLeast(0),
            onSelectedIndexChange = { index -> shownPeriods.getOrNull(index)?.let { emit(pm = it) } },
            itemLabel = { it },
            modifier = Modifier
                .padding(start = if (first) 0.dp else Theme.spacing.sm, end = if (first) Theme.spacing.sm else 0.dp)
                .width(periodWidth),
            enabled = enabled,
            contentDescription = strings.dayPeriod,
            onSettled = onValueChangeFinished,
            visibleItems = 3,
            turn = turns[2],
        )
    }
    val periodFirst = layout.parts.indexOf(TimePart.DayPeriod).let { it >= 0 && it < layout.parts.indexOf(TimePart.Hour) }

    Column(modifier) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (cycle.usesDayPeriod && periodFirst) period(true)
            clock()
            if (cycle.usesDayPeriod && !periodFirst) period(false)
        }
        if (now != null) {
            val scope = rememberCoroutineScope()
            val change by rememberUpdatedState(onValueChange)
            val finished by rememberUpdatedState(onValueChangeFinished)
            // The last press's wait for its wheels: a second press takes over from it.
            val waiting = remember { arrayOfNulls<Job>(1) }
            // Under the wheels, a quiet button: the wheels are the control,
            // this is a shortcut to a place on them.
            Button(
                onClick = {
                    val (rounded, tomorrow) = nearestStep(now(), minuteStep)
                    // 23:58 by fives rounds to midnight, which is tomorrow's.
                    // Where the date could not go with it, the wheels stop at
                    // the last step of today: 00:00 on today's date is a day
                    // before now, the furthest from it the wheels can show.
                    val tookTomorrow = onNow?.invoke(tomorrow) ?: true
                    val unbounded = if (tomorrow && !tookTomorrow) LocalTime(23, minutes.last()) else rounded
                    // No further than the bounds: Now on a picker whose earliest
                    // is a quarter of an hour away turns to that.
                    val target = bounds.clamp(unbounded)
                    if (bounds.bounded) {
                        // Set rather than turned. A bounded wheel's rows depend
                        // on the hour and the half of the day it is turning to,
                        // so a row counted now is a different row by the time
                        // the turn gets there.
                        waiting[0]?.cancel()
                        if (working.value != target) {
                            working.value = target
                            change(target)
                        }
                        finished?.invoke()
                        return@Button
                    }
                    val asked = listOf(
                        WheelTurn(hours.indexOf(displayHourOf(target.hour)).coerceAtLeast(0)),
                        WheelTurn(minutes.indexOf(target.minute).coerceAtLeast(0)),
                        WheelTurn(if (target.hour >= 12) 1 else 0).takeIf { cycle.usesDayPeriod },
                    )
                    turns = asked
                    waiting[0]?.cancel()
                    waiting[0] = scope.launch {
                        // Every wheel there, or a finger took one on the way: then the
                        // finger's is the value, and Now says nothing over it.
                        val arrived = asked.all { turn -> turn?.done?.await() ?: true }
                        if (!arrived) return@launch
                        // Where the wheels came to rest, exactly, and said once.
                        if (working.value != target) {
                            working.value = target
                            change(target)
                        }
                        finished?.invoke()
                    }
                },
                modifier = Modifier.align(Alignment.CenterHorizontally),
                enabled = enabled,
                variant = ButtonVariant.Ghost,
                size = ButtonSize.Small,
            ) { +strings.now }
        }
    }
}

/**
 * The chosen time, shown as a tappable field.
 *
 * ```
 * TimeField(value = leaveAt, onClick = { picking = true }, label = "Leave at")
 *
 * FieldPicker(visible = picking, onDismissRequest = { picking = false }, title = "Leave at") {
 *     TimePicker(value = leaveAt, onValueChange = { leaveAt = it })
 * }
 * ```
 *
 * The thing you put on a form; tapping it is expected to open a [TimePicker] in
 * a [FieldPicker]. Kept separate from the picker so the picker never has to know how it
 * is being presented. [DateField] and [DateTimeField] are the same field for a
 * [DatePicker] and a [DateTimePicker].
 *
 * @param supporting A line under the field, as a [DateField]'s.
 * @param errorMessage In place of [supporting], in the error colour, as a
 *   [DateField]'s.
 */
@Composable
fun TimeField(
    value: LocalTime,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: String? = null,
    supporting: String? = null,
    errorMessage: String? = null,
    formats: LocaleFormats = Theme.formats,
    interactionSource: MutableInteractionSource? = null,
) {
    PickerField(
        text = formats.time(value),
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        label = label,
        interactionSource = interactionSource,
        supporting = supporting,
        errorMessage = errorMessage,
    )
}

/**
 * A chosen value shown as a field that opens its picker: [TimeField],
 * [DateField] and [DateTimeField] are this, with their own words.
 *
 * @param text What is chosen, as the field shows it; `null` for nothing yet,
 *   when [placeholder] is shown instead.
 * @param supporting A line under the field, as a text field's: what the value is
 *   for, or what it has to be.
 * @param errorMessage In place of [supporting], in the error colour, with the
 *   field's edge in it too; and said with the field, as a text field's is.
 */
@Composable
internal fun PickerField(
    text: String?,
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    label: String?,
    interactionSource: MutableInteractionSource?,
    placeholder: String? = null,
    supporting: String? = null,
    errorMessage: String? = null,
) {
    val interactions = interactionSource ?: remember { MutableInteractionSource() }
    val shape = Theme.shapes.field
    val shown = text ?: placeholder.orEmpty()
    // A text field's own colours for the line under it, so a date in a form
    // says "required" in the same grey and the same red as the name above it.
    val fieldColours = TextFieldDefaults.colours()

    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (label != null) {
            Text(
                text = label,
                style = Theme.typography.labelMedium,
                colour = if (enabled) Theme.colours.contentMuted else Theme.colours.contentDisabled,
                // Cleared, because the name goes on the control below instead.
                //
                // Compose has no `labelledBy`: a label drawn above a field is an
                // unrelated node however close it is on screen. Left as it was,
                // a screen reader read "Leave at" and then, separately, "08:15,
                // button" — a control whose name is its own value. `FieldScaffold`
                // has done it this way for every text field since the contract
                // suite arrived; this field was written later and did not.
                modifier = Modifier.clearAndSetSemantics {},
            )
        }
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .focusRing(interactions, shape, enabled = enabled)
                .semantics {
                    if (label != null) contentDescription = label
                    stateDescription = shown
                    if (errorMessage != null) error(errorMessage)
                }
                .pointerCursor(enabled = enabled)
                .clickable(
                    interactionSource = interactions,
                    // The same press wash every other tappable surface in the
                    // system uses. It read as inert without it — the one field
                    // in the library that did not respond to a press.
                    indication = kontourIndication(shape, pressScale = 1f),
                    enabled = enabled,
                    role = Role.Button,
                    onClick = onClick,
                ),
            shape = shape,
            containerColour = Theme.colours.surfaceSunken,
            // A well on the page is 1.13:1 at the enhanced tier; the field has
            // to be seen to be pressed. In error, the error colour all round.
            border = if (errorMessage != null) {
                BorderStroke(Theme.sizing.borderWidth, fieldColours.error)
            } else {
                contrastEdge()
            },
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = shown,
                style = Theme.typography.bodyLarge,
                colour = when {
                    !enabled -> Theme.colours.contentDisabled
                    // Nothing chosen yet reads as a placeholder does in a text
                    // field, so an empty date is not mistaken for one.
                    text == null -> Theme.colours.contentSubtle
                    else -> Theme.colours.content
                },
                modifier = Modifier.padding(
                    horizontal = Theme.spacing.sm,
                    vertical = Theme.spacing.sm,
                ),
            )
        }
        // The same slot a text field has: one line, the error in place of the
        // supporting text, opening and closing in place.
        val message = errorMessage ?: supporting
        val motion = Theme.motion
        AnimatedVisibility(
            visible = message != null,
            enter = motion.enterOrFade(fadeIn(motion.tweenFast()) + expandVertically(motion.tweenFast())),
            exit = motion.exitOrFade(fadeOut(motion.tweenFast()) + shrinkVertically(motion.tweenFast())),
        ) {
            var last by remember { mutableStateOf(message.orEmpty()) }
            if (message != null) last = message
            Text(
                text = message ?: last,
                style = Theme.typography.bodySmall,
                colour = when {
                    !enabled -> Theme.colours.contentDisabled
                    errorMessage != null -> fieldColours.error
                    else -> fieldColours.supporting
                },
                // Said with the field: its error as the field's, its supporting
                // line as a line of its own after it.
                modifier = if (errorMessage != null) Modifier.clearAndSetSemantics {} else Modifier,
            )
        }
    }
}

/** The hour and minute wheels' width, but where they are narrow. */
private val ClockWheelWidth = 72.dp

/** The day-period wheel's width when its words are short. */
private val PeriodWheelWidth = 64.dp

/** Room either side of the longest day-period word. */
private val PeriodWheelPadding = 24.dp

/**
 * A value a picker's parts each change a part of, read as it was last changed
 * rather than as it was last composed: two parts changing in one frame keep
 * both changes. Taken from the caller afresh whenever the caller's changes.
 */
internal class Working<T>(initial: T) {
    private var composed = initial
    var value = initial

    fun compose(caller: T) {
        if (caller != composed) {
            composed = caller
            value = caller
        }
    }
}

