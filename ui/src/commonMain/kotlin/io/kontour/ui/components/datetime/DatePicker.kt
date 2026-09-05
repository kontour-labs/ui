package io.kontour.ui.components.datetime

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import io.kontour.ui.components.action.ButtonSize
import io.kontour.ui.components.action.IconButton
import io.kontour.ui.foundation.Text
import io.kontour.ui.motion.AnimatedSlot
import io.kontour.ui.motion.SlotGap
import io.kontour.ui.theme.Theme
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/**
 * Remembers which month a picker is showing, surviving configuration change.
 *
 * Hoisted rather than internal so a caller can jump the calendar — to the month
 * of a search result, say — without the picker owning navigation it cannot see
 * the reason for.
 */
@Stable
class CalendarNavigationState internal constructor(initial: LocalDate) {
    var visibleMonth: LocalDate by mutableStateOf(LocalDate(initial.year, initial.month, 1))
        internal set

    /** Steps forward or back by whole months. */
    fun step(months: Int) {
        visibleMonth = if (months >= 0) {
            visibleMonth.plus(months, DateTimeUnit.MONTH)
        } else {
            visibleMonth.minus(-months, DateTimeUnit.MONTH)
        }
    }

    fun jumpTo(date: LocalDate) {
        visibleMonth = LocalDate(date.year, date.month, 1)
    }
}

@Composable
fun rememberCalendarNavigationState(initial: LocalDate): CalendarNavigationState {
    val epochDay = rememberSaveable(initial) { initial.toEpochDays() }
    return remember(epochDay) { CalendarNavigationState(LocalDate.fromEpochDays(epochDay)) }
}

/**
 * Picks a single date.
 *
 * ```
 * DatePicker(
 *     selected = departureDate,
 *     onSelectedChange = viewModel::setDepartureDate,
 *     today = today,
 *     isDateSelectable = { it >= today },
 *     previousIcon = Tabler.Outline.ChevronLeft,
 *     nextIcon = Tabler.Outline.ChevronRight,
 * )
 * ```
 *
 * The month header is a live region, so paging announces the new month rather
 * than leaving a screen-reader user to work out that the grid changed. Months
 * slide in the direction of travel — forward from the right, back from the left
 * — which is the cheapest way to make paging legible without a label.
 *
 * ### It needs its whole month
 *
 * A month grid is up to six rows of dates plus a header, and it has nowhere to
 * put the sixth row if the window is shorter than that — about 400dp of height
 * at the default type size, and a phone turned sideways is 360. **Put it
 * somewhere that scrolls.** `Dialog` does, and so does a page that scrolls; a
 * fixed-height box does not, and the weeks past the fold are then not reachable
 * at all. The same kind of fact as `StepperDefaults.MinWidth`: the parts are
 * irreducible, so the room has to come from outside.
 */
@Composable
fun DatePicker(
    selected: LocalDate?,
    onSelectedChange: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    today: LocalDate? = null,
    isDateSelectable: (LocalDate) -> Boolean = { true },
    markerFor: ((LocalDate) -> Color?)? = null,
    previousIcon: ImageVector? = null,
    nextIcon: ImageVector? = null,
    /**
     * A glyph for the button that brings the calendar back to [today]'s month.
     *
     * Shown only while the calendar is somewhere else, and only when [today] is
     * known — which is the whole of when it has anything to do. Null leaves it
     * out entirely, like the paging icons: the library does not ship an icon
     * set, so a component that draws one has picked for you.
     */
    todayIcon: ImageVector? = null,
    navigation: CalendarNavigationState = rememberCalendarNavigationState(
        selected ?: today ?: LocalDate(2026, 1, 1)
    ),
    formats: DateTimeFormats = LocalDateTimeFormats.current,
) {
    CalendarFrame(
        modifier = modifier,
        navigation = navigation,
        formats = formats,
        previousIcon = previousIcon,
        nextIcon = nextIcon,
        today = today,
        todayIcon = todayIcon,
    ) { month ->
        CalendarMonth(
            month = month,
            isSelected = { it == selected },
            onSelectedChange = onSelectedChange,
            isDateSelectable = isDateSelectable,
            today = today,
            markerFor = markerFor,
            formats = formats,
        )
    }
}

/**
 * Picks a start and end date.
 *
 * Selection follows the rule users expect without being told: the first tap sets
 * the start and clears any end, the second sets the end. Tapping a date *before*
 * the current start restarts the range there rather than producing a backwards
 * one — which is what people actually mean when they do it.
 *
 * @param onRangeSelected Receives the range so far. The end is null while only a start
 *   has been chosen, so a caller can keep its confirm button disabled.
 */
@Composable
fun DateRangePicker(
    start: LocalDate?,
    end: LocalDate?,
    onRangeSelected: (start: LocalDate, end: LocalDate?) -> Unit,
    modifier: Modifier = Modifier,
    today: LocalDate? = null,
    isDateSelectable: (LocalDate) -> Boolean = { true },
    previousIcon: ImageVector? = null,
    nextIcon: ImageVector? = null,
    /** See [DatePicker]. */
    todayIcon: ImageVector? = null,
    navigation: CalendarNavigationState = rememberCalendarNavigationState(
        start ?: today ?: LocalDate(2026, 1, 1)
    ),
    formats: DateTimeFormats = LocalDateTimeFormats.current,
) {
    CalendarFrame(
        modifier = modifier,
        navigation = navigation,
        formats = formats,
        previousIcon = previousIcon,
        nextIcon = nextIcon,
        today = today,
        todayIcon = todayIcon,
    ) { month ->
        CalendarMonth(
            month = month,
            isSelected = { false },
            onSelectedChange = { tapped ->
                when {
                    start == null || end != null -> onRangeSelected(tapped, null)
                    tapped < start -> onRangeSelected(tapped, null)
                    else -> onRangeSelected(start, tapped)
                }
            },
            isDateSelectable = isDateSelectable,
            today = today,
            rangePositionOf = { date -> rangePosition(date, start, end) },
            // Drag out a range in one gesture, in either direction. The
            // calendar reports where the finger went down and where it is now;
            // ordering them is this component's business, because only it knows
            // that a range's `start` is the earlier of the two.
            onDragSelect = { from, to ->
                if (to < from) onRangeSelected(to, from) else onRangeSelected(from, to)
            },
            formats = formats,
        )
    }
}

internal fun rangePosition(date: LocalDate, start: LocalDate?, end: LocalDate?): RangePosition =
    when {
        start == null -> RangePosition.None
        end == null -> if (date == start) RangePosition.StartAndEnd else RangePosition.None
        date == start && date == end -> RangePosition.StartAndEnd
        date == start -> RangePosition.Start
        date == end -> RangePosition.End
        date > start && date < end -> RangePosition.Middle
        else -> RangePosition.None
    }

@Composable
private fun CalendarFrame(
    modifier: Modifier,
    navigation: CalendarNavigationState,
    formats: DateTimeFormats,
    previousIcon: ImageVector?,
    nextIcon: ImageVector?,
    today: LocalDate?,
    todayIcon: ImageVector?,
    content: @Composable (LocalDate) -> Unit,
) {
    val motion = Theme.motion
    var stepDirection by remember { mutableStateOf(1) }

    // Only worth offering from somewhere else. Paging three months forward and
    // wanting to come back is the whole case; a button that is always there and
    // does nothing eleven times out of twelve is a button people stop reading.
    val todayMonth = today?.let { LocalDate(it.year, it.month, 1) }
    val awayFromToday = todayMonth != null && todayMonth != navigation.visibleMonth

    Column(
        modifier
            // The header and the buttons are the grid's, so they stop where it
            // does — see `CalendarMonthDefaults.MaxWidth`, and the note there
            // about why the cap goes outside `fillMaxWidth` rather than after.
            .widthIn(max = CalendarMonthDefaults.MaxWidth)
            .fillMaxWidth()
    ) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = Theme.spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            if (previousIcon != null) {
                IconButton(
                    icon = previousIcon,
                    contentDescription = "Previous month",
                    onClick = {
                        stepDirection = -1
                        navigation.step(-1)
                    },
                    size = ButtonSize.Small,
                )
            }

            Text(
                text = formats.monthAndYear(navigation.visibleMonth),
                style = Theme.typography.titleMedium,
                // Paging is silent otherwise: the grid changes but nothing says so.
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                // The gap belongs to the animated child, not to an arrangement
                // around it — see `AnimatedSlot`. With `spacedBy` the row loses
                // the whole gap in one frame at the end of the animation, after
                // the button has finished shrinking, and the next-month button
                // jumps sideways.
                if (todayIcon != null && today != null) {
                    AnimatedSlot(
                        visible = awayFromToday,
                        gap = Theme.spacing.xxs,
                        side = SlotGap.Trailing,
                        enter = fadeIn(motion.tweenFast()) + scaleIn(motion.tweenFast(), initialScale = 0.8f),
                        exit = fadeOut(motion.tweenFast()) + scaleOut(motion.tweenFast(), targetScale = 0.8f),
                    ) {
                        IconButton(
                            icon = todayIcon,
                            contentDescription = "Return to today",
                            onClick = {
                                stepDirection = if (today < navigation.visibleMonth) -1 else 1
                                navigation.jumpTo(today)
                            },
                            size = ButtonSize.Small,
                        )
                    }
                }

                if (nextIcon != null) {
                    IconButton(
                        icon = nextIcon,
                        contentDescription = "Next month",
                        onClick = {
                            stepDirection = 1
                            navigation.step(1)
                        },
                        size = ButtonSize.Small,
                    )
                }
            }
        }

        AnimatedContent(
            targetState = navigation.visibleMonth,
            transitionSpec = {
                val enterFrom = if (stepDirection >= 0) 1 else -1
                (
                    slideInHorizontally(motion.tweenDefault()) { width -> enterFrom * width / 3 } +
                        fadeIn(motion.tweenFast())
                    ).togetherWith(
                    slideOutHorizontally(motion.tweenDefault()) { width -> -enterFrom * width / 3 } +
                        fadeOut(motion.tweenFast())
                ) using SizeTransform(clip = false)
            },
            label = "calendarMonth",
        ) { month ->
            content(month)
        }
    }
}
