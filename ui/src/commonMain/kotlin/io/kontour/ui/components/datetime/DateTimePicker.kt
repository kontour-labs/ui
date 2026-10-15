package io.kontour.ui.components.datetime

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import io.kontour.ui.components.selection.SegmentedControl
import io.kontour.ui.foundation.SystemIcons
import io.kontour.ui.locale.DateStyle
import io.kontour.ui.locale.LocaleFormats
import io.kontour.ui.theme.Theme
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/** What a [DateTimePicker] takes by default. */
object DateTimePickerDefaults {
    /** The date and time now on the system's clock, in its zone: what a picker's Now turns to. */
    fun currentDateTime(): LocalDateTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
}

/**
 * Picks a date and a time of day: a [DatePicker] and a [TimePicker], side by
 * side where there is room for both and one at a time where there is not.
 *
 * ```
 * DateTimePicker(
 *     value = leaveAt,
 *     onValueChange = viewModel::setLeaveAt,
 *     today = today,
 *     isDateSelectable = { it >= today },
 *     minuteStep = 5,
 * )
 * ```
 *
 * Asked for as *"a component for a date and time picker? it should basically
 * just be the two individual components we currently have for that, combined"*,
 * and that is what it is: the month grid with its header, paging, chooser and
 * marks, and the hour and minute wheels on the reader's own clock, each as it
 * is on its own but for the wheels' width, which is their numbers' and not a
 * fixed 72dp. A day chosen keeps the time, and a wheel turned keeps the day.
 *
 * **Side by side, or one at a time — never one over the other.** Wherever the
 * calendar, at 40dp a day, a gap and the wheels fit across, they sit next to
 * each other, centred together, the calendar taking what is left up to its
 * widest. On a 24-hour clock at the default type size that is 390dp or so,
 * which a large phone is; the wheels are measured, so a 12-hour clock's day
 * period, 453dp, and larger type each take more. Narrower, a segmented control above
 * them says the chosen date and the chosen time, and shows the calendar or the
 * wheels as one or the other is picked. Only the width decides, so nothing
 * changes arrangement as a page scrolls or a sheet is dragged. The wheels were
 * under the calendar where a phone had the height, and that was *"way too much
 * vertical room"*. One at a time, the space is the calendar's, whichever is
 * showing, so swapping does not move what is below.
 *
 * `isDateSelectable` turns days away and not times: a time earlier today than
 * now is the caller's to refuse, since only the caller knows whether "now" is
 * the floor.
 *
 * @param value The date and time shown: the day selected in the grid, and the
 *   time on the wheels.
 * @param onValueChange Every change: a day tapped, or each row a wheel passes,
 *   as the time picker's own does.
 * @param minuteStep Coarsens the minute wheel; see [TimePicker].
 * @param now The date and time now, for the **Now** button under the wheels:
 *   pressed, the wheels scroll to the time — rounded to [minuteStep] — and the
 *   day becomes today, the calendar paging to it. The system clock's by
 *   default; null for no button.
 * @param navigation Which month is on show; it opens on [value]'s.
 * @param onValueChangeFinished Called once a change has settled: when a day is
 *   tapped, and when a wheel comes to rest after the reader turned it. A save,
 *   a request or a toast belongs here rather than in [onValueChange], which a
 *   flick of the minutes calls a dozen times. Not called when [value] is set by
 *   the caller.
 * @see DatePicker for [today], [isDateSelectable], [decorationFor],
 *   [showDecorationLine], the icons and [firstDayOfWeek], which
 *   mean what they mean there.
 */
@Composable
fun DateTimePicker(
    value: LocalDateTime,
    onValueChange: (LocalDateTime) -> Unit,
    modifier: Modifier = Modifier,
    /** Whether a day can be chosen and the wheels turned; disabled, both show the value and answer nothing. */
    enabled: Boolean = true,
    today: LocalDate? = null,
    isDateSelectable: (LocalDate) -> Boolean = { true },
    decorationFor: ((LocalDate) -> DayDecoration?)? = null,
    showDecorationLine: Boolean = decorationFor != null,
    previousIcon: ImageVector? = SystemIcons.ChevronBack,
    nextIcon: ImageVector? = SystemIcons.ChevronForward,
    todayIcon: ImageVector? = DatePickerDefaults.TodayIcon,
    chooserIcon: ImageVector? = null,
    minuteStep: Int = 1,
    now: (() -> LocalDateTime)? = DateTimePickerDefaults::currentDateTime,
    navigation: CalendarNavigationState = rememberCalendarNavigationState(value.date),
    formats: LocaleFormats = Theme.formats,
    firstDayOfWeek: DayOfWeek = formats.conventions.firstDayOfWeek,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    // Each half changes its own part of whatever the value is by then, not of
    // the value it was composed with: a day tapped while the minutes are still
    // settling keeps the minute they settle on.
    val working = remember { Working(value) }
    working.compose(value)
    val report by rememberUpdatedState(onValueChange)
    val change: (LocalDateTime) -> Unit = { next ->
        if (next != working.value) {
            working.value = next
            report(next)
        }
    }
    val finished by rememberUpdatedState(onValueChangeFinished)
    val date = @Composable { dateModifier: Modifier ->
        DatePicker(
            value = value.date,
            onValueChange = { date ->
                change(LocalDateTime(date, working.value.time))
                // A tap is the whole of a day's change: settled as it lands.
                finished?.invoke()
            },
            enabled = enabled,
            today = today,
            isDateSelectable = isDateSelectable,
            decorationFor = decorationFor,
            showDecorationLine = showDecorationLine,
            previousIcon = previousIcon,
            nextIcon = nextIcon,
            todayIcon = todayIcon,
            chooserIcon = chooserIcon,
            navigation = navigation,
            formats = formats,
            firstDayOfWeek = firstDayOfWeek,
            modifier = dateModifier,
        )
    }
    // Now read once a press, for the wheels' time and the calendar's day alike.
    val pressedAt = remember { arrayOfNulls<LocalDate>(1) }
    val time = @Composable { timeModifier: Modifier ->
        TimeWheels(
            value = value.time,
            onValueChange = { time -> change(LocalDateTime(working.value.date, time)) },
            modifier = timeModifier,
            enabled = enabled,
            minuteStep = minuteStep,
            now = now?.let { read -> { read().also { pressedAt[0] = it.date }.time } },
            formats = formats,
            onValueChangeFinished = onValueChangeFinished,
            narrow = true,
            // The day goes with the time: today, or tomorrow when the time
            // rounded past midnight, and the calendar pages there.
            onNow = { tomorrow ->
                val today = pressedAt[0]
                val next = today?.plus(1, DateTimeUnit.DAY)
                // Tomorrow only where the calendar would let it be chosen.
                // Refused, Now is the end of today instead — the wheels turn to
                // its last step — rather than midnight on a day that stays put,
                // which was 24 hours before the time now.
                val tookTomorrow = tomorrow && next != null && (next == working.value.date || isDateSelectable(next))
                val day = if (tookTomorrow) next else today
                // Only to a day the calendar would let be chosen: one it would
                // refuse, today included, keeps the day there was.
                if (day != null && day != working.value.date && isDateSelectable(day)) {
                    change(LocalDateTime(day, working.value.time))
                    navigation.jumpTo(day)
                }
                tookTomorrow
            },
        )
    }

    // Which half shows when they take turns; kept through a change of width,
    // as both halves are, so a reader who turns a phone round finds them as
    // they were.
    var showing by rememberSaveable { mutableIntStateOf(0) }
    val segments = @Composable {
        // What is chosen, in the words the pickers below choose it in: the
        // segment is also the summary of its half.
        SegmentedControl(
            options = listOf(formats.date(value.date, DateStyle.Medium), formats.time(value.time)),
            selectedIndex = showing,
            onSelectedIndexChange = { showing = it },
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
        )
    }
    // The wheels as they are drawn, but with nothing that changes as they turn,
    // so this copy is measured once and not composed again with every row the
    // real ones pass.
    val probe = @Composable {
        TimeWheels(
            value = LocalTime(0, 0),
            onValueChange = Unheard,
            modifier = Silent,
            enabled = enabled,
            minuteStep = minuteStep,
            now = if (now != null) Midnight else null,
            formats = formats,
            onValueChangeFinished = null,
            onNow = null,
            narrow = true,
        )
    }
    // The gap between the two is small because it is not all the space between
    // them: the calendar's last column has room of its own round its numbers,
    // so a reader sees some 20dp from a Sunday to the hours. Every dp of it is
    // one a phone needs to show the two together. Under the segmented
    // control, the gap a card puts between its groups.
    val beside = Theme.spacing.xs
    val under = Theme.spacing.sm
    SubcomposeLayout(modifier) { constraints ->
        val gap = beside.roundToPx()
        // **The wheels' own width, measured**, from a copy that is never placed:
        // a 12-hour clock's day period, a long word for it and larger type all
        // widen them, and a width written down here was wrong for all three.
        val wheels = subcompose(Part.Probe, probe).maxOfOrNull { it.maxIntrinsicWidth(Constraints.Infinity) } ?: 0
        // Side by side where a calendar of days no narrower than a week is
        // tall fits beside them: on the width this is given and nothing else,
        // so the arrangement cannot change under a reader's finger.
        val sideBySide = !constraints.hasBoundedWidth ||
            constraints.maxWidth >= (NarrowestDay * 7).roundToPx() + gap + wheels
        val widest = CalendarMonthDefaults.MaxWidth.roundToPx()

        if (sideBySide) {
            // The wheels as wide as they are, and the calendar what is left up
            // to its widest: given the rest of the row, the wheels sat in the
            // middle of it, some 70dp off the calendar on a wide window —
            // *"there's too much room between the date picker and time
            // picker"*.
            val calendarWidth = if (constraints.hasBoundedWidth) minOf(widest, constraints.maxWidth - gap - wheels) else widest
            val date = subcompose(Part.Date) { date(Modifier) }
                .map { it.measure(Constraints(maxWidth = calendarWidth, maxHeight = constraints.maxHeight)) }
            val time = subcompose(Part.Time) { time(Modifier) }
                .map { it.measure(Constraints(minWidth = wheels, maxWidth = wheels, maxHeight = constraints.maxHeight)) }
            val dateWidth = date.maxOfOrNull { it.width } ?: 0
            val pair = dateWidth + gap + wheels
            val tallest = (date + time).maxOfOrNull { it.height } ?: 0
            val width = constraints.constrainWidth(pair)
            layout(width, constraints.constrainHeight(tallest)) {
                // Centred together across the width, and on each other's
                // middles. Relative, so right to left the calendar is at the
                // right and the wheels come after it, to its left.
                val start = (width - pair) / 2
                date.forEach { it.placeRelative(start, (tallest - it.height) / 2) }
                time.forEach { it.placeRelative(start + dateWidth + gap, (tallest - it.height) / 2) }
            }
        } else {
            val column = if (constraints.hasBoundedWidth) minOf(widest, constraints.maxWidth) else widest
            val loose = Constraints(maxWidth = column, maxHeight = constraints.maxHeight)
            val control = subcompose(Part.Segments, segments)
                .map { it.measure(Constraints(minWidth = column, maxWidth = column, maxHeight = constraints.maxHeight)) }
            // **Both halves measured and composed, one placed**: the space is
            // the taller one's, so changing which shows moves nothing below,
            // and the other keeps its month and its wheels as they were. Not
            // placed, it is neither drawn nor touched, and it says nothing to a
            // screen reader either.
            val date = subcompose(Part.Date) { date(if (showing == 0) Modifier else Silent) }.map { it.measure(loose) }
            val time = subcompose(Part.Time) { time(if (showing == 1) Modifier else Silent) }.map { it.measure(loose) }
            val top = (control.maxOfOrNull { it.height } ?: 0) + under.roundToPx()
            val room = (date + time).maxOfOrNull { it.height } ?: 0
            val width = constraints.constrainWidth(column)
            layout(width, constraints.constrainHeight(top + room)) {
                val start = (width - column) / 2
                control.forEach { it.placeRelative(start, 0) }
                (if (showing == 0) date else time).forEach {
                    it.placeRelative(start + (column - it.width) / 2, top + (room - it.height) / 2)
                }
            }
        }
    }
}

/** The parts of a [DateTimePicker], each composed once and kept as the width changes. */
private enum class Part { Probe, Date, Time, Segments }

/**
 * How narrow a day may be for a [DateTimePicker]'s calendar to sit beside its
 * wheels: as wide as a week is tall, 40dp, so a day is still a square target
 * the height every week already has, and its number has room round it at any
 * type size. A calendar at its widest has 44.
 */
private val NarrowestDay: Dp = MaxRowHeight

/** What is composed only to be measured, or kept but not shown: nothing a screen reader should find. */
private val Silent = Modifier.clearAndSetSemantics {}

/** The measured wheels' change, which goes nowhere. */
private val Unheard: (LocalTime) -> Unit = {}

/** The measured wheels' Now: a time, so they have the button, and never read. */
private val Midnight: () -> LocalTime = { LocalTime(0, 0) }

/**
 * The chosen date and time, shown as a tappable field.
 *
 * ```
 * var departs by remember { mutableStateOf(LocalDateTime(2026, 3, 14, 9, 5)) }
 * var picking by remember { mutableStateOf(false) }
 *
 * DateTimeField(value = departs, onClick = { picking = true }, label = "Departs")
 *
 * FieldPicker(visible = picking, onDismissRequest = { picking = false }, title = "Departs") {
 *     DateTimePicker(value = departs, onValueChange = { departs = it })
 * }
 * ```
 *
 * The thing you put on a form, as [TimeField] and [DateField] are: tapping it is
 * expected to open a [DateTimePicker] in a [FieldPicker]. Written as the
 * locale writes a date and a time together.
 *
 * @param value The date and time chosen. Always one, as a [DateTimePicker]
 *   always has one: there is no empty state to show a placeholder for.
 * @param supporting A line under the field, as a [DateField]'s.
 * @param errorMessage In place of [supporting], in the error colour, as a
 *   [DateField]'s.
 * @param formats Writes the value; the theme's, by default.
 */
@Composable
fun DateTimeField(
    value: LocalDateTime,
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
        text = formats.dateTime(value.date, value.time),
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        label = label,
        interactionSource = interactionSource,
        supporting = supporting,
        errorMessage = errorMessage,
    )
}
