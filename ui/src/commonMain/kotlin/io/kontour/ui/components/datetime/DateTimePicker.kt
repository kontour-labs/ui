package io.kontour.ui.components.datetime

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.selection.SegmentedControl
import io.kontour.ui.locale.DateStyle
import androidx.compose.foundation.interaction.MutableInteractionSource
import io.kontour.ui.foundation.SystemIcons
import io.kontour.ui.locale.LocaleFormats
import io.kontour.ui.theme.Theme
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.plus
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.TimeZone
import kotlin.time.Clock
import androidx.compose.runtime.remember

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
 * marks, and the hour and minute wheels on the reader's own clock, each exactly
 * as it is on its own. A day chosen keeps the time, and a wheel turned keeps
 * the day.
 *
 * **Side by side, or one at a time.** Given 560dp or more, the
 * calendar and the wheels sit next to each other, a gap apart and centred
 * together in whatever width is left over. Narrower — a phone — a
 * segmented control above them says the chosen date and the chosen time, and
 * shows the calendar or the wheels as one or the other is picked. They were
 * stacked at first, about 560dp tall, which a phone could not show whole —
 * *"the two things need to be either side-by-side, or have a segmented control
 * that allows the user to swap between them"*. One at a time, the space is the
 * calendar's, whichever is showing, so swapping does not move what is below.
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
 * @see DatePicker for [today], [isDateSelectable], [markerFor],
 *   [containerColourFor], [annotationFor], the icons and [firstDayOfWeek], which
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
    markerFor: ((LocalDate) -> Color?)? = null,
    containerColourFor: ((LocalDate) -> Color?)? = null,
    annotationFor: ((LocalDate) -> DayAnnotation?)? = null,
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
            markerFor = markerFor,
            containerColourFor = containerColourFor,
            annotationFor = annotationFor,
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
            // The day goes with the time: today, or tomorrow when the time
            // rounded past midnight, and the calendar pages there.
            onNow = { tomorrow ->
                val day = pressedAt[0]?.let { if (tomorrow) it.plus(1, DateTimeUnit.DAY) else it }
                // Only to a day the calendar would let be chosen: one it would
                // refuse, today included, keeps the day there was.
                if (day != null && day != working.value.date && isDateSelectable(day)) {
                    change(LocalDateTime(day, working.value.time))
                    navigation.jumpTo(day)
                }
            },
        )
    }

    BoxWithConstraints(modifier, contentAlignment = Alignment.TopCenter) {
        if (maxWidth >= SideBySideWidth) {
            // The two together, a gap apart and centred as one. The wheels
            // are as wide as they are, and the calendar takes what is left up
            // to its widest: given the rest of the row, the wheels sat in the
            // middle of it, some 70dp off the calendar on a wide window —
            // *"there's too much room between the date picker and time
            // picker"*.
            Row(
                horizontalArrangement = Arrangement.spacedBy(Theme.spacing.lg, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                date(Modifier.weight(1f, fill = false).widthIn(max = CalendarMonthDefaults.MaxWidth))
                time(Modifier.width(IntrinsicSize.Max))
            }
        } else {
            var showing by rememberSaveable { mutableIntStateOf(0) }
            Column(
                modifier = Modifier.widthIn(max = CalendarMonthDefaults.MaxWidth),
                verticalArrangement = Arrangement.spacedBy(Theme.spacing.md),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // What is chosen, in the words the pickers below choose it
                // in: the segment is also the summary of its half.
                SegmentedControl(
                    options = listOf(formats.date(value.date, DateStyle.Medium), formats.time(value.time)),
                    selectedIndex = showing,
                    onSelectedIndexChange = { showing = it },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = enabled,
                )
                // The half not showing is measured for its height and kept
                // composed, its month and its wheels as they were, but it is
                // not placed and says nothing to a screen reader.
                OneOf(showing) {
                    date(if (showing == 0) Modifier else Modifier.clearAndSetSemantics {})
                    time(if (showing == 1) Modifier else Modifier.clearAndSetSemantics {})
                }
            }
        }
    }
}

/**
 * How wide a [DateTimePicker] has to be to put its calendar and its wheels side
 * by side: the calendar's widest, a gap, and room for the wheels of a 12-hour
 * clock.
 */
private val SideBySideWidth: Dp = 560.dp

/**
 * One of [content]'s children, [index], placed; every one of them measured, and
 * the space the tallest of them, so changing which one shows moves nothing
 * below. The others are composed and kept, but not placed, so they are neither
 * drawn nor touched; the caller clears what they would say.
 */
@Composable
private fun OneOf(index: Int, content: @Composable () -> Unit) {
    Layout(content) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val placeables = measurables.map { it.measure(loose) }
        val width = constraints.constrainWidth(placeables.maxOfOrNull { it.width } ?: 0)
        val height = constraints.constrainHeight(placeables.maxOfOrNull { it.height } ?: 0)
        layout(width, height) {
            placeables.getOrNull(index)?.let { it.placeRelative((width - it.width) / 2, (height - it.height) / 2) }
        }
    }
}

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
