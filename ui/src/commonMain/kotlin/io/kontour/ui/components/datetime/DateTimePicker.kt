package io.kontour.ui.components.datetime

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import io.kontour.ui.locale.LocaleFormats
import io.kontour.ui.theme.Theme
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

/**
 * Picks a date and a time of day: a [DatePicker] with a [TimePicker] under it.
 *
 * ```
 * DateTimePicker(
 *     value = leaveAt,
 *     onValueChange = viewModel::setLeaveAt,
 *     today = today,
 *     isDateSelectable = { it >= today },
 *     previousIcon = Tabler.Outline.ChevronLeft,
 *     nextIcon = Tabler.Outline.ChevronRight,
 *     minuteStep = 5,
 * )
 * ```
 *
 * Asked for as *"a component for a date and time picker? it should basically
 * just be the two individual components we currently have for that, combined"*,
 * and that is what it is: the month grid with its header, paging, chooser and
 * marks, and under it the hour and minute wheels on the reader's own clock,
 * each exactly as it is on its own. A day chosen keeps the time, and a wheel
 * turned keeps the day.
 *
 * **It is as tall as both**, about 560dp at the default type size, so it needs
 * somewhere that scrolls even more than a date picker does: a sheet, a dialog,
 * or a page. Laid side by side the two would fit a wide window better and a
 * phone not at all, so they are stacked, centred, the calendar no wider than
 * a phone's.
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
 * @param navigation Which month is on show; it opens on [value]'s.
 * @param onValueChangeFinished Called once a change has settled: when a day is
 *   tapped, and when a wheel comes to rest after the reader turned it. A save,
 *   a request or a toast belongs here rather than in [onValueChange], which a
 *   flick of the minutes calls a dozen times. Not called when [value] is set by
 *   the caller.
 * @see DatePicker for [today], [isDateSelectable], [markerFor], the icons and
 *   [firstDayOfWeek], which mean what they mean there.
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
    previousIcon: ImageVector? = null,
    nextIcon: ImageVector? = null,
    todayIcon: ImageVector? = DatePickerDefaults.TodayIcon,
    chooserIcon: ImageVector? = null,
    minuteStep: Int = 1,
    navigation: CalendarNavigationState = rememberCalendarNavigationState(value.date),
    formats: LocaleFormats = Theme.formats,
    firstDayOfWeek: DayOfWeek = formats.conventions.firstDayOfWeek,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    // Each half changes its own part of whatever the value is by then, not of
    // the value it was composed with: a day tapped while the minutes are still
    // settling keeps the minute they settle on.
    val latest by rememberUpdatedState(value)
    val change by rememberUpdatedState(onValueChange)
    val finished by rememberUpdatedState(onValueChangeFinished)
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Theme.spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DatePicker(
            value = value.date,
            onValueChange = { date ->
                change(LocalDateTime(date, latest.time))
                // A tap is the whole of a day's change: settled as it lands.
                finished?.invoke()
            },
            enabled = enabled,
            today = today,
            isDateSelectable = isDateSelectable,
            markerFor = markerFor,
            previousIcon = previousIcon,
            nextIcon = nextIcon,
            todayIcon = todayIcon,
            chooserIcon = chooserIcon,
            navigation = navigation,
            formats = formats,
            firstDayOfWeek = firstDayOfWeek,
        )
        TimePicker(
            value = value.time,
            onValueChange = { time -> change(LocalDateTime(latest.date, time)) },
            enabled = enabled,
            minuteStep = minuteStep,
            formats = formats,
            onValueChangeFinished = onValueChangeFinished,
        )
    }
}
