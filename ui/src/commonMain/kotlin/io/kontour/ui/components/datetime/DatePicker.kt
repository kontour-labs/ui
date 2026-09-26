package io.kontour.ui.components.datetime

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.kontour.ui.a11y.minimumTouchTarget
import io.kontour.ui.components.action.ButtonSize
import io.kontour.ui.components.action.IconButton
import io.kontour.ui.foundation.Icon
import io.kontour.ui.foundation.Text
import io.kontour.ui.input.focusRing
import io.kontour.ui.input.pointerCursor
import io.kontour.ui.interaction.kontourIndication
import io.kontour.ui.overlay.Popover
import io.kontour.ui.theme.Theme
import kotlin.math.abs
import kotlin.time.Clock
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn

object DatePickerDefaults {
    /**
     * The glyph on the way back to today's month: a small calendar page with the
     * day marked. Drawn by the library, so a picker has the button without an
     * icon set; see `todayIcon`.
     */
    val TodayIcon: ImageVector get() = TodayGlyph
}

/** A calendar page, its two rings and header rule, and a marked day. 24 by 24, stroked. */
private val TodayGlyph: ImageVector by lazy {
    ImageVector.Builder(
        name = "Today",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            // The page.
            moveTo(6f, 5f)
            horizontalLineTo(18f)
            arcToRelative(2f, 2f, 0f, false, true, 2f, 2f)
            verticalLineTo(19f)
            arcToRelative(2f, 2f, 0f, false, true, -2f, 2f)
            horizontalLineTo(6f)
            arcToRelative(2f, 2f, 0f, false, true, -2f, -2f)
            verticalLineTo(7f)
            arcToRelative(2f, 2f, 0f, false, true, 2f, -2f)
            close()
            // The rings, and the rule under the month.
            moveTo(16f, 3f)
            verticalLineTo(7f)
            moveTo(8f, 3f)
            verticalLineTo(7f)
            moveTo(4f, 11f)
            horizontalLineTo(20f)
        }
        // Today, marked.
        path(fill = SolidColor(Color.Black)) {
            moveTo(8f, 14.5f)
            horizontalLineTo(11f)
            verticalLineTo(17.5f)
            horizontalLineTo(8f)
            close()
        }
    }.build()
}

/**
 * [start] at the start, [end] at the end, and [middle] centred on the whole row
 * — not on the room left between them.
 *
 * Where centring would run it under the wider side, it moves toward the
 * narrower one just far enough to clear it: on a phone, the month, the year and
 * the chooser's chevron do not fit centred between a previous button on one side
 * and the today and next buttons on the other, and the chevron was cut off.
 * Only when even the whole gap is too small is the middle narrowed to it.
 */
@Composable
private fun CentredBetween(
    modifier: Modifier,
    start: @Composable () -> Unit,
    middle: @Composable () -> Unit,
    end: @Composable () -> Unit,
) {
    Layout(contents = listOf(start, middle, end), modifier = modifier) { (starts, middles, ends), constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val atStart = starts.map { it.measure(loose) }
        val atEnd = ends.map { it.measure(loose) }
        val startWidth = atStart.sumOf { it.width }
        val endWidth = atEnd.sumOf { it.width }
        val gap = if (constraints.hasBoundedWidth) {
            (constraints.maxWidth - startWidth - endWidth).coerceAtLeast(0)
        } else {
            Constraints.Infinity
        }
        val inMiddle = middles.map { it.measure(loose.copy(maxWidth = gap)) }
        val middleWidth = inMiddle.sumOf { it.width }
        val width = if (constraints.hasBoundedWidth) {
            constraints.maxWidth
        } else {
            maxOf(startWidth, endWidth) * 2 + middleWidth
        }
        val all = atStart + inMiddle + atEnd
        val height = (all.maxOfOrNull { it.height } ?: 0).coerceIn(constraints.minHeight, constraints.maxHeight)
        layout(width, height) {
            var x = 0
            atStart.forEach {
                it.placeRelative(x, (height - it.height) / 2)
                x += it.width
            }
            x = ((width - middleWidth) / 2).coerceIn(startWidth, maxOf(startWidth, width - endWidth - middleWidth))
            inMiddle.forEach {
                it.placeRelative(x, (height - it.height) / 2)
                x += it.width
            }
            x = width - endWidth
            atEnd.forEach {
                it.placeRelative(x, (height - it.height) / 2)
                x += it.width
            }
        }
    }
}

/**
 * A sideways scroll pages the month: a trackpad's two-finger swipe, a mouse's
 * tilt wheel, shift and the wheel.
 *
 * Reported as wanted on the desktop, where that is how everything else pages.
 * **One page per swipe**: a trackpad sends a stream of small deltas and then its
 * momentum sends more, so a swipe is counted until the scroll goes quiet, and it
 * pages once when enough of it has arrived. A mouse's notch is a whole unit and
 * pages on its own. Mostly-vertical scrolls are left alone — they are the page's
 * to scroll — and the sideways ones are consumed, so a scrolling container round
 * the calendar does not move as well. Right to left, the months run the other way
 * and so does the scroll.
 *
 * A plain `pointerInput` rather than `composed`, which the library used nowhere
 * else: a composed modifier is composed again wherever it is applied, and all
 * this one needed from composition was the direction, which the caller has.
 */
private fun Modifier.sidewaysScrollPages(navigation: CalendarNavigationState, rtl: Boolean): Modifier =
    pointerInput(navigation, rtl) {
        var gathered = 0f
        var last = 0L
        var spent = false
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                if (event.type != PointerEventType.Scroll) continue
                val delta = event.changes.fold(Offset.Zero) { sum, change -> sum + change.scrollDelta }
                if (abs(delta.x) <= abs(delta.y)) continue
                val now = event.changes.first().uptimeMillis
                if (now - last > ScrollQuietMillis) {
                    gathered = 0f
                    spent = false
                }
                last = now
                event.changes.forEach { it.consume() }
                if (spent) continue
                gathered += delta.x
                if (abs(gathered) >= ScrollPage) {
                    // Scrolling right shows what is to the right: the next
                    // month, or right to left the one before.
                    val later = gathered > 0f
                    navigation.step(if (later != rtl) 1 else -1)
                    spent = true
                }
            }
        }
    }

/**
 * How far today's cell is flashed, 0 to 1, from the picker it is in; null in a
 * month with no today button over it. Read in the cell's draw pass, so a flash is
 * a redraw of one cell and nothing else.
 */
internal val LocalTodayFlash = staticCompositionLocalOf<(() -> Float)?> { null }

/**
 * Each pulse of the flash coming in, and going out: in quickly, out a little
 * slower, and the two close together — a blink-blink rather than two separate
 * glows, which is how the slower first version read.
 */
private const val TodayFlashInMillis: Int = 90
private const val TodayFlashOutMillis: Int = 170

/** How many times today pulses: a single one was easy to miss. */
private const val TodayFlashPulses: Int = 2

/**
 * Today where the device is — the month a calendar opens on when it was given
 * neither a selection nor a `today`. It was a fixed 1 January 2026, which was
 * this month once and then quietly became a calendar opening on the past.
 */
internal fun systemToday(): LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())

/** A scroll quiet for this long has ended, and the next one is a new swipe. */
private const val ScrollQuietMillis: Long = 250L

/** How much sideways scroll a swipe needs to page: one mouse notch. */
private const val ScrollPage: Float = 1f

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
    value: LocalDate?,
    onValueChange: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    today: LocalDate? = null,
    isDateSelectable: (LocalDate) -> Boolean = { true },
    markerFor: ((LocalDate) -> Color?)? = null,
    previousIcon: ImageVector? = null,
    nextIcon: ImageVector? = null,
    /**
     * The glyph on the button that brings the calendar back to [today]'s month.
     *
     * **There by default**, as a small calendar the library draws itself — reported
     * as wanted in every calendar, and asked for as an icon rather than the word.
     * The paging arrows are the caller's because the library ships no icon set;
     * this is one glyph, drawn here, so that the way back needs nothing supplied.
     * Pass an app's own to match its set, or null to leave the button out.
     *
     * Shown only while the calendar is somewhere else, and only when [today] is
     * known — which is the whole of when it has anything to do.
     */
    todayIcon: ImageVector? = DatePickerDefaults.TodayIcon,
    /**
     * Turns the month and year into a button that opens two wheels, drawn with
     * this glyph beside it.
     *
     * The same bargain the paging icons make, and for two reasons rather than
     * one. The library ships no icon set, so a component that draws one has
     * picked for you; and on a touch screen there is no hover to discover a
     * control with, so a title that is also a button and does not say so is a
     * title nobody presses.
     *
     * **Null leaves the whole chooser out**, not only the glyph. A popover needs
     * an `OverlayHost` and throws without one, and a calendar is an inline
     * component that turns up in tests, previews and pages that have no host at
     * all — so the affordance a caller has not asked for must not be the reason
     * their picker will not render.
     */
    chooserIcon: ImageVector? = null,
    navigation: CalendarNavigationState = rememberCalendarNavigationState(
        value ?: today ?: remember { systemToday() }
    ),
    formats: DateTimeFormats = LocalDateTimeFormats.current,
    /**
     * Where the week starts.
     *
     * Monday by default, which is Australia, most of Europe and the ISO week.
     * North America starts on Sunday and a few calendars start on Saturday.
     *
     * A shortcut, not a second source of truth: it defaults from
     * [DateTimeFormats.firstDayOfWeek], which is where the answer has always
     * lived, and setting it derives a `formats` for the week arithmetic rather
     * than being consulted separately. So an app-wide choice is one field on the
     * token group and a one-off is one argument here — the same arrangement
     * `Theme.strings` and every component's own string parameter already use.
     */
    firstDayOfWeek: DayOfWeek = formats.firstDayOfWeek,
) {
    CalendarFrame(
        modifier = modifier,
        navigation = navigation,
        formats = formats,
        previousIcon = previousIcon,
        nextIcon = nextIcon,
        today = today,
        todayIcon = todayIcon,
        chooserIcon = chooserIcon,
    ) { month ->
        CalendarMonth(
            month = month,
            isSelected = { it == value },
            onSelectedChange = onValueChange,
            isDateSelectable = isDateSelectable,
            today = today,
            markerFor = markerFor,
            formats = formats,
            firstDayOfWeek = firstDayOfWeek,
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
 * A range can also be dragged out in one gesture, and the drag crosses months:
 * page with the header's arrows using another finger, or push the handle past
 * the month's first or last day and hold it there until the ring round the small
 * arrow in that day fills. Either way the drag carries on in the new month from where the finger is.
 *
 * @param onRangeChange Receives the range so far. The end is null while only a start
 *   has been chosen, so a caller can keep its confirm button disabled.
 */
@Composable
fun DateRangePicker(
    start: LocalDate?,
    end: LocalDate?,
    onRangeChange: (start: LocalDate, end: LocalDate?) -> Unit,
    modifier: Modifier = Modifier,
    today: LocalDate? = null,
    isDateSelectable: (LocalDate) -> Boolean = { true },
    previousIcon: ImageVector? = null,
    nextIcon: ImageVector? = null,
    /** See [DatePicker]. */
    todayIcon: ImageVector? = DatePickerDefaults.TodayIcon,
    /**
     * Turns the month and year into a button that opens two wheels, drawn with
     * this glyph beside it.
     *
     * The same bargain the paging icons make, and for two reasons rather than
     * one. The library ships no icon set, so a component that draws one has
     * picked for you; and on a touch screen there is no hover to discover a
     * control with, so a title that is also a button and does not say so is a
     * title nobody presses.
     *
     * **Null leaves the whole chooser out**, not only the glyph. A popover needs
     * an `OverlayHost` and throws without one, and a calendar is an inline
     * component that turns up in tests, previews and pages that have no host at
     * all — so the affordance a caller has not asked for must not be the reason
     * their picker will not render.
     */
    chooserIcon: ImageVector? = null,
    navigation: CalendarNavigationState = rememberCalendarNavigationState(
        start ?: today ?: remember { systemToday() }
    ),
    formats: DateTimeFormats = LocalDateTimeFormats.current,
    /**
     * Where the week starts.
     *
     * Monday by default, which is Australia, most of Europe and the ISO week.
     * North America starts on Sunday and a few calendars start on Saturday.
     *
     * A shortcut, not a second source of truth: it defaults from
     * [DateTimeFormats.firstDayOfWeek], which is where the answer has always
     * lived, and setting it derives a `formats` for the week arithmetic rather
     * than being consulted separately. So an app-wide choice is one field on the
     * token group and a one-off is one argument here — the same arrangement
     * `Theme.strings` and every component's own string parameter already use.
     */
    firstDayOfWeek: DayOfWeek = formats.firstDayOfWeek,
) {
    // **The drag is held here, over the pager, not in the month.** A month grid
    // is one page of the pager and leaves with it, so a drag that lived in one
    // ended the moment the month changed — whether by the header's arrows pressed
    // with a second finger or by the arrows the drag itself shows at the month's
    // edges — and the month paged in never heard about the finger. Held above,
    // it carries on across the border. See `CalendarDragState`.
    val drag = rememberCalendarDrag()
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val weekFormats = remember(formats, firstDayOfWeek) { formats.startingOn(firstDayOfWeek) }
    val currentNavigation by rememberUpdatedState(navigation)
    SideEffect {
        // Ordering the two ends is this component's business, because only it
        // knows that a range's `start` is the earlier of the two.
        drag.onSelect = { from, to ->
            if (to < from) onRangeChange(to, from) else onRangeChange(from, to)
        }
        drag.isSelectable = isDateSelectable
        drag.onStep = { currentNavigation.step(it) }
        drag.geometry = {
            GridGeometry.of(
                month = currentNavigation.visibleMonth,
                formats = weekFormats,
                origin = Offset(0f, drag.gridTop),
                cell = drag.width / Columns,
                rowHeight = drag.rowHeight,
                rtl = rtl,
            )
        }
    }
    CalendarFrame(
        modifier = modifier,
        navigation = navigation,
        formats = formats,
        previousIcon = previousIcon,
        nextIcon = nextIcon,
        today = today,
        todayIcon = todayIcon,
        chooserIcon = chooserIcon,
        drag = drag,
    ) { month ->
        CalendarMonth(
            month = month,
            isSelected = { false },
            onSelectedChange = { tapped ->
                when {
                    start == null || end != null -> onRangeChange(tapped, null)
                    tapped < start -> onRangeChange(tapped, null)
                    else -> onRangeChange(start, tapped)
                }
            },
            isDateSelectable = isDateSelectable,
            today = today,
            rangePositionOf = { date -> rangePosition(date, start, end) },
            // Dragged out in one gesture, in either direction, by the drag held
            // above — which every month in the pager draws from.
            formats = formats,
            firstDayOfWeek = firstDayOfWeek,
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

/**
 * The month and year, and the way back to any other one.
 *
 * A header that is also a control, which is the whole of this: paging a month at
 * a time is right for "next week" and hopeless for a birthday, and the two
 * wheels behind it are the same instrument `TimePicker` is made of. It drives
 * [CalendarNavigationState.jumpTo] **live** — the grid behind the popover pages
 * as the drum turns — because a wheel that only commits on dismiss is a form
 * field, and this is a way of looking around.
 *
 * Only the trigger. The popover it opens is declared against the header's own
 * box in [CalendarFrame], for the two reasons written down there.
 *
 * **`dismissOnScroll` is left at its default**, which is worth writing down
 * because the plan for this said to turn it off: a wheel drag *is* a scroll, and
 * a popover that dismisses on one would take a single frame of the gesture and
 * vanish. Measured, it does not — a notch over the drum never reaches the scrim
 * at all, with the flag either way and with the scrim's own consumed-scroll guard
 * removed as well. The flag's remaining effect here is a scroll *outside* the
 * panel, which is the page moving under the anchor and is what the default is
 * for. Turning it off would have been a constant with nothing behind it.
 */
@Composable
private fun MonthAndYearButton(
    navigation: CalendarNavigationState,
    formats: DateTimeFormats,
    chooserIcon: ImageVector?,
    onOpen: () -> Unit,
) {
    val title = @Composable {
        Text(
            text = formats.monthAndYear(navigation.visibleMonth),
            style = Theme.typography.titleMedium,
            // Paging is silent otherwise: the grid changes but nothing says so.
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }

    if (chooserIcon == null) {
        title()
        return
    }

    val interactions = remember { MutableInteractionSource() }
    val shape = Theme.shapes.small

    Row(
        Modifier
            .minimumTouchTarget()
            .focusRing(interactions, shape)
            .clip(shape)
            .pointerCursor()
            .clickable(
                interactionSource = interactions,
                indication = kontourIndication(shape),
                role = Role.Button,
                onClickLabel = Theme.strings.chooseMonthAndYear,
                onClick = onOpen,
            )
            .padding(horizontal = Theme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Theme.spacing.xxs),
    ) {
        title()
        Icon(
            imageVector = chooserIcon,
            contentDescription = null,
            tint = Theme.colours.contentMuted,
            size = Theme.sizing.iconSmall,
        )
    }
}

/**
 * Two drums: every month, and a run of years around the one you opened on.
 *
 * **They hold their own month and year rather than reading the calendar's**, and
 * that is the whole shape of this function. The wheels drive
 * [CalendarNavigationState.jumpTo] live, so a calendar that fed its visible
 * month back in closed a loop: every row the finger crossed changed the
 * argument, recomposed both drums, and handed the year wheel a freshly built
 * list — two hundred and forty-one boxed integers, thrown away a dozen times a
 * second, and `List` is unstable so the wheel could never skip. Seeded once and
 * then written to only by the drums themselves, none of that happens and the
 * grid still follows the finger, because [onPick] is a one-way street.
 *
 * **The range is frozen on the seed**, which is also what that buys. A range
 * derived from a *following* year would shift the list under the finger by
 * exactly as much as the finger had moved and the drum would never arrive
 * anywhere; the widening this used to need — for a calendar paged outside the
 * range while the popover was open, which cannot now happen — is gone with it. A
 * hundred and twenty years either side covers a birthday and a mortgage from the
 * same list, and takes its epoch from the app rather than from whenever this
 * file was written.
 *
 * A jump is navigation and not selection: landing on a month whose days are all
 * unselectable shows a grid of disabled days, which is the correct answer and
 * says more than refusing to go there would.
 */
@Composable
private fun MonthAndYearWheels(
    initial: LocalDate,
    onPick: (LocalDate) -> Unit,
) {
    val months = remember { Month.entries }
    val from = initial.year - YearsEitherSide
    val to = initial.year + YearsEitherSide
    val years = remember(from, to) { (from..to).toList() }

    var monthRow by remember(initial) { mutableIntStateOf(initial.month.ordinal) }
    var yearRow by remember(initial) { mutableIntStateOf(initial.year - from) }

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Theme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Drum(
            description = Theme.strings.month,
            items = months,
            row = { monthRow },
            onRowChange = { monthRow = it; onPick(LocalDate(years[yearRow], months[it], 1)) },
            label = { it.fullName },
            modifier = Modifier.weight(1f),
        )
        Drum(
            description = Theme.strings.year,
            items = years,
            row = { yearRow },
            onRowChange = { yearRow = it; onPick(LocalDate(years[it], months[monthRow], 1)) },
            label = { it.toString() },
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * One of the two drums, with its row read *here* rather than where it is kept.
 *
 * `row` is a lambda for one reason: the read is what subscribes, so a read in
 * [MonthAndYearWheels] would recompose both drums every time either of them
 * turned — and turning the year wheel would rebuild the month wheel's twelve
 * rows for a value that had not changed. Read inside this function it
 * invalidates one drum. The same trick, for the same reason, as `CalendarMonth`'s
 * `lean = { … }` and `OverlayAppearance`'s `progress: () -> Float`.
 */
@Composable
private fun <T> Drum(
    description: String,
    items: List<T>,
    row: () -> Int,
    onRowChange: (Int) -> Unit,
    label: (T) -> String,
    modifier: Modifier,
) {
    Box(modifier.semantics { contentDescription = description }) {
        WheelPicker(
            items = items,
            selectedIndex = row(),
            onSelectedIndexChange = onRowChange,
            label = label,
        )
    }
}

/**
 * How far the year wheel reaches from where the calendar started.
 *
 * Both directions, because a date picker is as often a birthday as a booking and
 * the component cannot tell which it is. Two hundred and forty-one rows is a
 * `LazyColumn` and costs nothing to not look at.
 */
private const val YearsEitherSide = 120

@Composable
private fun CalendarFrame(
    modifier: Modifier,
    navigation: CalendarNavigationState,
    formats: DateTimeFormats,
    previousIcon: ImageVector?,
    nextIcon: ImageVector?,
    today: LocalDate?,
    todayIcon: ImageVector?,
    chooserIcon: ImageVector?,
    drag: CalendarDragState? = null,
    content: @Composable (LocalDate) -> Unit,
) {
    val motion = Theme.motion

    // A month changing under a finger that is still down — a header arrow pressed
    // with another finger, or a dwell on a month arrow — is read as a move: the
    // drag carries on in the new month, from where the finger is.
    if (drag != null) {
        val currentNavigation by rememberUpdatedState(navigation)
        LaunchedEffect(drag) {
            snapshotFlow { currentNavigation.visibleMonth }.collect { drag.track() }
        }
    }

    /**
     * Today, flashed: 0 to 1 and back when the today button is pressed.
     *
     * The button used to show only while the calendar was on another month, on
     * the grounds that on today's own month it did nothing. It is always there
     * now — reported as wanted — and so it always does something: it brings the
     * calendar to today's month if it is not there, and then it points at the day,
     * pulsing a colour from the theme in and out of it twice. Which is also the answer to
     * "where is today" on a month where the day is easy to miss. Read by the
     * today cell in its draw pass; see `LocalTodayFlash`.
     */
    val todayFlash = remember { Animatable(0f) }
    val flashScope = rememberCoroutineScope()
    var chooserOpen by remember { mutableStateOf(false) }

    /**
     * The month the wheels open on, and then stop hearing about.
     *
     * Keyed on `chooserOpen` so it is read when the popover opens and is a
     * constant for as long as it stays open — which is what lets
     * `MonthAndYearWheels` *skip* while the drum it contains is turning the
     * calendar underneath it. Passing `navigation.visibleMonth` straight down
     * would recompose the wheels on every row crossed for an argument they only
     * ever read once.
     */
    val chooserSeed = remember(chooserOpen) { navigation.visibleMonth }

    Column(
        modifier
            // The header and the buttons are the grid's, so they stop where it
            // does — see `CalendarMonthDefaults.MaxWidth`, and the note there
            // about why the cap goes outside `fillMaxWidth` rather than after.
            .widthIn(max = CalendarMonthDefaults.MaxWidth)
            .fillMaxWidth()
    ) {
        // **The popover hangs off the header rather than off the title.**
        //
        // Two reasons, and the first is a layout bug the second would have
        // hidden. `Popover` reports its *parent's* bounds as the anchor, so
        // declaring it beside the title put a fourth child in what was then a
        // `SpaceBetween` row — zero-width, and still enough to take a share of
        // the spacing and pull the title off centre. And jumping live changes the title, "May
        // 2026" and "September 2026" are not the same width, so a popover
        // anchored to it would slide sideways while the drum turned. This box is
        // the full width of the frame and neither of those can reach it.
        Box(Modifier.fillMaxWidth()) {
            // **The month and year in the middle of the calendar**, not in the
            // middle of what is left between the buttons. Spaced between, the
            // title sat off centre as soon as the two sides were not the same
            // width — and they are not: the end has the today button as well as
            // the next month's. Reported as wanting the title centred at the top
            // of every calendar.
            CentredBetween(
                modifier = Modifier.fillMaxWidth().padding(vertical = Theme.spacing.xs),
                start = {
                    if (previousIcon != null) {
                        IconButton(
                            icon = previousIcon,
                            contentDescription = Theme.strings.previousMonth,
                            onClick = {
                                navigation.step(-1)
                            },
                            size = ButtonSize.Small,
                        )
                    }
                },
                middle = {
                    MonthAndYearButton(
                        navigation = navigation,
                        formats = formats,
                        chooserIcon = chooserIcon,
                        onOpen = { chooserOpen = true },
                    )
                },
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Theme.spacing.xxs),
                ) {
                    if (todayIcon != null && today != null) {
                        IconButton(
                            icon = todayIcon,
                            contentDescription = Theme.strings.returnToToday,
                            onClick = {
                                val paging = navigation.visibleMonth.let {
                                    it.year != today.year || it.month != today.month
                                }
                                navigation.jumpTo(today)
                                flashScope.launch {
                                    // Once the month has slid in, if it had to.
                                    if (paging) delay(motion.default.toLong())
                                    todayFlash.snapTo(0f)
                                    // Twice: once to catch the eye, once to hold it there.
                                    repeat(TodayFlashPulses) {
                                        todayFlash.animateTo(1f, tween(TodayFlashInMillis))
                                        todayFlash.animateTo(0f, tween(TodayFlashOutMillis))
                                    }
                                }
                            },
                            size = ButtonSize.Small,
                        )
                    }

                    if (nextIcon != null) {
                        IconButton(
                            icon = nextIcon,
                            contentDescription = Theme.strings.nextMonth,
                            onClick = {
                                navigation.step(1)
                            },
                            size = ButtonSize.Small,
                        )
                    }
                }
            }

            // **Only when the chooser is on**, which is what `chooserIcon` says.
            //
            // A `Popover` needs an `OverlayHost` and throws without one, and a
            // calendar is an inline component that anyone may draw in a test, a
            // preview or a page that has no host at all. Declaring one
            // unconditionally turned "this picker has no month chooser" into
            // "this picker does not render", which is not a trade a default is
            // allowed to make.
            if (chooserIcon != null) {
                Popover(
                    visible = chooserOpen,
                    onDismissRequest = { chooserOpen = false },
                    // A panel two wheels wide, pointing at the middle of a full-width
                    // row, is pointing at nothing in particular — which is the case
                    // `showArrow`'s own documentation names.
                    showArrow = false,
                ) {
                    MonthAndYearWheels(
                        initial = chooserSeed,
                        onPick = navigation::jumpTo,
                    )
            }
            }
        }

        // The drag's pointer input sits on this box, outside the pager, so the
        // gesture does not leave with the month it started in. So does a
        // sideways scroll, which pages.
        Box(
            Modifier
                .fillMaxWidth()
                .sidewaysScrollPages(navigation, rtl = LocalLayoutDirection.current == LayoutDirection.Rtl)
                .then(if (drag != null) Modifier.calendarDragInput(drag) else Modifier)
        ) {
        CompositionLocalProvider(
            LocalCalendarDrag provides drag,
            LocalTodayFlash provides { todayFlash.value },
        ) {
        AnimatedContent(
            targetState = navigation.visibleMonth,
            transitionSpec = {
                // **Cut rather than slid while the chooser is up.**
                //
                // A drum turned through a year jumps the calendar once per row
                // crossed, and `AnimatedContent` keeps every target it has not
                // finished leaving composed — so a flick left ten month grids
                // alive at once, each of them forty-two `DayCell`s carrying two
                // colour animations and a scale. Something near thirteen hundred
                // running animations for one gesture, which is the reported mush.
                //
                // Nothing the chooser is for is lost. The grid still follows the
                // drum row by row, which is the liveness; what goes is a 220ms
                // slide restarted every 30ms, and a transition that never gets
                // past its first frame is not an animation anybody saw.
                if (chooserOpen) {
                    return@AnimatedContent (
                        EnterTransition.None togetherWith ExitTransition.None
                        ) using SizeTransform(clip = false) { _, _ -> snap() }
                }

                // Derived from the transition itself, not from a variable
                // somebody has to remember to set.
                //
                // This used to read a `stepDirection` that only the three header
                // buttons ever wrote, so a month change arriving any other way —
                // a caller driving the hoisted `CalendarNavigationState`, a
                // `jumpTo`, a swipe a host has wired up — animated in whatever
                // direction the last button press had left behind. Initially
                // that is forward, which is why going *back* wiped the wrong way.
                //
                // `initialState` and `targetState` are the two months this
                // transition is actually between, and `LocalDate` is comparable,
                // so the direction is a fact about the transition rather than a
                // note left beside it.
                val enterFrom = if (targetState >= initialState) 1 else -1
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
    }
}
