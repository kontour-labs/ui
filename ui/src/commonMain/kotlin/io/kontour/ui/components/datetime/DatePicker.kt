package io.kontour.ui.components.datetime

import io.kontour.ui.a11y.optionalBorder
import io.kontour.ui.a11y.contrastEdge
import io.kontour.ui.motion.sizeSpec
import io.kontour.ui.locale.DateOrder
import io.kontour.ui.locale.DateStyle
import io.kontour.ui.locale.LocaleFormats
import io.kontour.ui.locale.DateTimeConventions
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.structuralEqualityPolicy
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.kontour.ui.a11y.minimumTouchTarget
import io.kontour.ui.components.action.ButtonSize
import io.kontour.ui.components.action.IconButton
import io.kontour.ui.foundation.Icon
import io.kontour.ui.foundation.SystemIcons
import io.kontour.ui.foundation.Text
import io.kontour.ui.input.focusRing
import io.kontour.ui.input.pointerCursor
import io.kontour.ui.interaction.DragClaim
import io.kontour.ui.interaction.FeedbackIntent
import io.kontour.ui.interaction.horizontalDragOwning
import io.kontour.ui.interaction.kontourIndication
import io.kontour.ui.interaction.rememberDetentTicker
import io.kontour.ui.motion.enterOrFade
import io.kontour.ui.motion.exitOrFade
import io.kontour.ui.overlay.Popover
import io.kontour.ui.theme.Theme
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
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

/** What [DatePicker] and [DateRangePicker] take by default. */
object DatePickerDefaults {
    /**
     * Today, in the device's own time zone: the month a picker opens on when
     * neither a value nor `today` says otherwise. It was a fixed 1 January 2026,
     * which was this month once and then quietly became a calendar opening on
     * the past.
     */
    fun currentDate(): LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())

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

/** How far a swipe has to go to page, as a share of the calendar's width. */
internal const val SwipeCommitShare: Float = 1f / 3f

/** How fast a swipe has to be moving, when let go, to page however short it was. */
internal val MonthFlick = 400.dp

/** Weeks in the tallest month, and so in every month a picker draws. */
private const val CalendarWeeks: Int = 6

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
class CalendarNavigationState internal constructor(initialDate: LocalDate) {
    var visibleMonth: LocalDate by mutableStateOf(LocalDate(initialDate.year, initialDate.month, 1))
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

/**
 * Remembers a [CalendarNavigationState] opening on the month of [initialDate],
 * kept across configuration change.
 */
@Composable
fun rememberCalendarNavigationState(initialDate: LocalDate): CalendarNavigationState {
    val epochDay = rememberSaveable(initialDate) { initialDate.toEpochDays() }
    return remember(epochDay) { CalendarNavigationState(LocalDate.fromEpochDays(epochDay)) }
}

/**
 * Picks a single date.
 *
 * ```
 * DatePicker(
 *     value = departureDate,
 *     onValueChange = viewModel::setDepartureDate,
 *     today = today,
 *     isDateSelectable = { it >= today },
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
    /**
     * Whether a day can be chosen. Disabled, every day is drawn the way an
     * unselectable one is; the months still page, so the calendar can be read.
     */
    enabled: Boolean = true,
    today: LocalDate? = null,
    isDateSelectable: (LocalDate) -> Boolean = { true },
    /** Draws a dot under a day: for "this day has departures", or a trip already booked. */
    markerFor: ((LocalDate) -> Color?)? = null,
    /**
     * A day's own colour: its circle filled with it, and its number in the ink
     * that reads on it — a public holiday, a day sold out, how busy each day is.
     * The chosen day is drawn over it. See [CalendarMonth].
     */
    containerColourFor: ((LocalDate) -> Color?)? = null,
    /**
     * A few characters under a day's number — a fare, a count — and the colour
     * they are in: see [DayAnnotation]. Given at all, every week is a line
     * taller, in every month, so the picker does not change height as it pages.
     */
    annotationFor: ((LocalDate) -> DayAnnotation?)? = null,
    /**
     * The glyphs on the buttons either side of the month, back and on.
     *
     * **There by default**, as the chevrons the library draws for itself, back
     * and forward in the reading direction, so they swap ends in a
     * right-to-left layout and still point the way they go. They were the
     * caller's to supply, and a picker given none had no buttons: it paged by a
     * swipe nobody could see, and on a desktop with a mouse not at all. A
     * month grid with no way to the next month is the kind of picker
     * `SystemIcons` exists to prevent — *"there's no left/right buttons"*, of
     * one in a dialog.
     * Pass an app's own to match its set, or null to leave a button out.
     */
    previousIcon: ImageVector? = SystemIcons.ChevronBack,
    nextIcon: ImageVector? = SystemIcons.ChevronForward,
    /**
     * The glyph on the button that brings the calendar back to [today]'s month.
     *
     * **There by default**, as a small calendar the library draws itself — reported
     * as wanted in every calendar, and asked for as an icon rather than the word.
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
     * The glyph is what says the title is a button: on a touch screen there is
     * no hover to discover a control with, so a title that is also a button and
     * does not say so is a title nobody presses.
     *
     * **Off by default, unlike the paging arrows**, because null leaves the whole
     * chooser out, not only the glyph. A popover needs
     * an `OverlayHost` and throws without one, and a calendar is an inline
     * component that turns up in tests, previews and pages that have no host at
     * all — so the affordance a caller has not asked for must not be the reason
     * their picker will not render.
     */
    chooserIcon: ImageVector? = null,
    navigation: CalendarNavigationState = rememberCalendarNavigationState(
        value ?: today ?: remember { DatePickerDefaults.currentDate() }
    ),
    formats: LocaleFormats = Theme.formats,
    /**
     * Where the week starts.
     *
     * Monday by default, which is Australia, most of Europe and the ISO week.
     * North America starts on Sunday and a few calendars start on Saturday.
     *
     * A shortcut, not a second source of truth: it defaults from
     * [DateTimeConventions.firstDayOfWeek], which is where the answer has always
     * lived, and setting it derives a `formats` for the week arithmetic rather
     * than being consulted separately. So an app-wide choice is one field on the
     * token group and a one-off is one argument here — the same arrangement
     * `Theme.strings` and every component's own string parameter already use.
     */
    firstDayOfWeek: DayOfWeek = formats.conventions.firstDayOfWeek,
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
            enabled = enabled,
            isDateSelectable = isDateSelectable,
            today = today,
            markerFor = markerFor,
            containerColourFor = containerColourFor,
            annotationFor = annotationFor,
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
    /**
     * Whether a range can be chosen, by tap or by drag. Disabled, every day is
     * drawn the way an unselectable one is; the months still page.
     */
    enabled: Boolean = true,
    today: LocalDate? = null,
    isDateSelectable: (LocalDate) -> Boolean = { true },
    /** Draws a dot under a day, as a [DatePicker]'s does. */
    markerFor: ((LocalDate) -> Color?)? = null,
    /**
     * A day's own colour, as a [DatePicker]'s: the range is drawn over it, so a
     * fortnight across a holiday still reads as one band.
     */
    containerColourFor: ((LocalDate) -> Color?)? = null,
    /** A few characters under a day's number, as a [DatePicker]'s: see [DayAnnotation]. */
    annotationFor: ((LocalDate) -> DayAnnotation?)? = null,
    /** See [DatePicker]. */
    previousIcon: ImageVector? = SystemIcons.ChevronBack,
    nextIcon: ImageVector? = SystemIcons.ChevronForward,
    /** See [DatePicker]. */
    todayIcon: ImageVector? = DatePickerDefaults.TodayIcon,
    /**
     * Turns the month and year into a button that opens two wheels, drawn with
     * this glyph beside it.
     *
     * The glyph is what says the title is a button: on a touch screen there is
     * no hover to discover a control with, so a title that is also a button and
     * does not say so is a title nobody presses.
     *
     * **Off by default, unlike the paging arrows**, because null leaves the whole
     * chooser out, not only the glyph. A popover needs
     * an `OverlayHost` and throws without one, and a calendar is an inline
     * component that turns up in tests, previews and pages that have no host at
     * all — so the affordance a caller has not asked for must not be the reason
     * their picker will not render.
     */
    chooserIcon: ImageVector? = null,
    navigation: CalendarNavigationState = rememberCalendarNavigationState(
        start ?: today ?: remember { DatePickerDefaults.currentDate() }
    ),
    formats: LocaleFormats = Theme.formats,
    /**
     * Where the week starts.
     *
     * Monday by default, which is Australia, most of Europe and the ISO week.
     * North America starts on Sunday and a few calendars start on Saturday.
     *
     * A shortcut, not a second source of truth: it defaults from
     * [DateTimeConventions.firstDayOfWeek], which is where the answer has always
     * lived, and setting it derives a `formats` for the week arithmetic rather
     * than being consulted separately. So an app-wide choice is one field on the
     * token group and a one-off is one argument here — the same arrangement
     * `Theme.strings` and every component's own string parameter already use.
     */
    firstDayOfWeek: DayOfWeek = formats.conventions.firstDayOfWeek,
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
        drag.isSelectable = if (enabled) isDateSelectable else { _ -> false }
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
            enabled = enabled,
            isDateSelectable = isDateSelectable,
            today = today,
            markerFor = markerFor,
            containerColourFor = containerColourFor,
            annotationFor = annotationFor,
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
    shown: LocalDate,
    months: List<LocalDate>,
    placeOf: (LocalDate) -> Float,
    formats: LocaleFormats,
    chooserIcon: ImageVector?,
    onOpen: () -> Unit,
) {
    val title = @Composable {
        MonthTitle(shown = shown, months = months, placeOf = placeOf, formats = formats)
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
            // A ghost button, and edged like one at the enhanced tier.
            .optionalBorder(contrastEdge(), shape)
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
 * The month and year over the grid, following the pager: each month in view as
 * visible as its grid is, and as wide as the months in view are between them.
 *
 * **The width is blended, not the widest.** While the months moved this was as
 * wide as the widest of them, and when they came to rest it snapped to the one
 * that stayed — and the chooser's arrow beside it jumped into place after the
 * swipe had landed, which is how it was reported. Laid out at a width that goes
 * from one month's to the next's as the pager does, the arrow travels with it
 * and is already where it belongs when the months arrive.
 *
 * Only the month the calendar has settled on is read out, as a polite live
 * region: paging is otherwise silent, since the grid changes but nothing says so.
 * The labels drawn on the way are for the eye.
 */
@Composable
private fun MonthTitle(
    shown: LocalDate,
    months: List<LocalDate>,
    placeOf: (LocalDate) -> Float,
    formats: LocaleFormats,
) {
    val words = formats.monthAndYear(shown)
    Layout(
        content = {
            for (month in months) key(month) {
                Text(
                    text = formats.monthAndYear(month),
                    style = Theme.typography.titleMedium,
                    maxLines = 1,
                    modifier = Modifier
                        .graphicsLayer { alpha = (1f - abs(placeOf(month))).coerceIn(0f, 1f) }
                        .clearAndSetSemantics {},
                )
            }
        },
        modifier = Modifier.semantics {
            text = AnnotatedString(words)
            liveRegion = LiveRegionMode.Polite
        },
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0)) }
        // Read in layout, so a frame of the pager re-lays the title out and
        // recomposes nothing.
        val shares = months.map { (1f - abs(placeOf(it))).takeUnless { share -> share.isNaN() }?.coerceIn(0f, 1f) ?: 0f }
        val total = shares.sum()
        val width = if (total <= 0f) {
            placeables.maxOfOrNull { it.width } ?: 0
        } else {
            placeables.indices.sumOf { (placeables[it].width * shares[it] / total).toDouble() }.roundToInt()
        }.coerceIn(constraints.minWidth, constraints.maxWidth)
        val height = placeables.maxOfOrNull { it.height } ?: 0
        layout(width, height) {
            placeables.forEach { it.placeRelative((width - it.width) / 2, (height - it.height) / 2) }
        }
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
    val formats = Theme.formats
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
        val month = @Composable {
            Drum(
                description = Theme.strings.month,
                items = months,
                row = { monthRow },
                onRowChange = { monthRow = it; onPick(LocalDate(years[yearRow], months[it], 1)) },
                label = { formats.monthName(it) },
                modifier = Modifier.weight(1f),
            )
        }
        val year = @Composable {
            Drum(
                description = Theme.strings.year,
                items = years,
                row = { yearRow },
                onRowChange = { yearRow = it; onPick(LocalDate(years[it], months[monthRow], 1)) },
                label = { formats.digits(it.toLong()) },
                modifier = Modifier.weight(1f),
            )
        }
        // In the order the reader writes a date: `June 2026`, but `2026年 6月`.
        if (formats.conventions.dateOrder == DateOrder.YearMonthDay) {
            year()
            month()
        } else {
            month()
            year()
        }
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
            itemLabel = label,
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
    formats: LocaleFormats,
    previousIcon: ImageVector?,
    nextIcon: ImageVector?,
    today: LocalDate?,
    todayIcon: ImageVector?,
    chooserIcon: ImageVector?,
    drag: CalendarDragState? = null,
    content: @Composable (LocalDate) -> Unit,
) {
    val motion = Theme.motion
    // Later months come in from the end the reading runs towards.
    val towardsEnd = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1 else 1

    // **One pager, and a swipe is the way it is meant to be moved.**
    //
    // The months sit on a strip and the pager is somewhere along it. A finger
    // moves it directly, and letting go past a third of the way, or with a
    // flick, goes on to the next month at the finger's speed; short of that it
    // comes back. The arrows, the today button, a sideways scroll and a caller's
    // `jumpTo` all move the *same* pager, and a new move takes over a running one
    // at the speed it had — so pressing an arrow three times is one motion across
    // three months, not three animations restarted.
    //
    // It used to be two models: a swipe dragged the months side by side, and
    // everything else went through a separate `AnimatedContent` slide. Asked for
    // from a phone as "the month-switching model needs to be redone around the
    // swipe gesture, and then the left/right buttons being secondary".
    val pagerScope = rememberCoroutineScope()
    val pager = remember { MonthPager(navigation.visibleMonth) }
    var pageWidth by remember { mutableIntStateOf(0) }
    // **A month a swipe commits to is felt as the swipe lets go**, not when the
    // months come to rest. It was felt on landing, and a quick second swipe caught
    // the first before it had landed and cancelled its tick with it — "when you
    // swipe quickly, the haptics don't play properly". A button's month has the
    // button's own feedback, and a caller's is not a gesture.
    val pageTicker = rememberDetentTicker(FeedbackIntent.Snap)
    val flickPx = with(LocalDensity.current) { MonthFlick.toPx() }
    val snappy = motion.springSnappy.stiffness
    var chooserOpen by remember { mutableStateOf(false) }

    /** Lets go at [velocity], pixels a second in the reading direction: on to a month, or back. */
    fun release(velocity: Float) {
        if (!pager.held) return
        val width = pageWidth.toFloat()
        val at = pager.position
        val from = pager.heldFrom
        val start = pager.startSlot
        pager.letGo()
        // Pages a second towards later months: a finger moving towards the
        // reading's end brings the month before in.
        val speed = if (width > 0f) -velocity / width else 0f
        val flick = if (width > 0f) flickPx / width else Float.MAX_VALUE
        val moved = at - from
        val slot = when {
            width <= 0f -> start
            speed > flick && moved > 0f -> start + 1
            speed < -flick && moved < 0f -> start - 1
            // From rest, a third of the way commits.
            pager.fromRest -> when {
                at - start > SwipeCommitShare -> start + 1
                at - start < -SwipeCommitShare -> start - 1
                else -> start
            }
            // Caught moving and let go slowly: wherever is nearest.
            else -> at.roundToInt().coerceIn(start - 1, start + 1)
        }
        val landing = pager.monthAt(slot)
        if (landing != navigation.visibleMonth) {
            pager.headedFor = landing
            navigation.jumpTo(landing)
            pageTicker.at(monthIndex(landing))
        }
        pager.moveTo(
            month = landing,
            scope = pagerScope,
            stiffness = snappy,
            cut = motion.reduceMotion,
            velocity = speed,
        )
    }

    // Every other way the month changes: the pager goes there from wherever it is.
    LaunchedEffect(pager, navigation) {
        snapshotFlow { navigation.visibleMonth }.collect { month ->
            if (month == pager.headedFor || pager.held) {
                // The month it opened on, which the ticker has to know to feel the
                // first swipe away from it; or a swipe's own, already felt.
                pageTicker.at(monthIndex(month))
                return@collect
            }
            // Not a gesture's: nothing to feel, and the next swipe counts from here.
            pageTicker.reset()
            pageTicker.at(monthIndex(month))
            pager.moveTo(
                month = month,
                scope = pagerScope,
                stiffness = snappy,
                // A drum turned through a year jumps the month once per row
                // crossed: a slide per row is a slide nobody sees the end of.
                cut = motion.reduceMotion || chooserOpen,
            )
        }
    }

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
                modifier = Modifier.fillMaxWidth().padding(vertical = Theme.spacing.xxs),
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
                        shown = navigation.visibleMonth,
                        months = if (motion.reduceMotion) listOf(navigation.visibleMonth) else pager.inView,
                        placeOf = { if (motion.reduceMotion) 0f else pager.placeOf(it) },
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
                .onSizeChanged { pageWidth = it.width }
                // Sideways only: a drag that sets off up or down is the page's.
                // And in a range picker only from where a range cannot start — a
                // blank, the weekday initials, the margins — since a drag from a
                // day is the range.
                .horizontalDragOwning(
                    enabled = true,
                    interactionSource = null,
                    scope = pagerScope,
                    claimsOn = DragClaim.Direction,
                    accepts = { at -> drag?.startsOnDay(at) != true },
                    // Caught moving, the pager is taken from where it is, and the
                    // swipe counts from the month it was on its way to — so a quick
                    // second swipe is a second month, as a paging list's is.
                    onStart = { pager.take() },
                    onDelta = { delta ->
                        val width = pageWidth.toFloat()
                        if (width > 0f) pager.pull(-delta * towardsEnd / width)
                    },
                    onEnd = { release(0f) },
                    onRelease = { velocity -> release(velocity * towardsEnd) },
                )
                .then(if (drag != null) Modifier.calendarDragInput(drag) else Modifier)
        ) {
        CompositionLocalProvider(
            LocalCalendarDrag provides drag,
            LocalTodayFlash provides { todayFlash.value },
            // Every month six weeks tall, so paging never changes the height.
            LocalCalendarRows provides CalendarWeeks,
        ) {
        if (motion.reduceMotion) {
            // Nothing travels: a month changes in place, cross-fading.
            AnimatedContent(
                targetState = navigation.visibleMonth,
                transitionSpec = {
                    (motion.enterOrFade(fadeIn(motion.tweenFast())) togetherWith motion.exitOrFade(fadeOut(motion.tweenFast())))
                        .using(SizeTransform(clip = false) { _, _ -> motion.sizeSpec() })
                },
                label = "calendarMonth",
            ) { month ->
                content(month)
            }
        } else {
            // The months in view, each keyed by what it is: as the pager moves
            // on, the month coming in is the same composition moving to the
            // middle, not a new one drawn over the old. One at rest, two between.
            val months = pager.inView
            Layout(
                content = { for (month in months) key(month) { content(month) } },
                modifier = if (months.size > 1) Modifier.clipToBounds() else Modifier,
            ) { measurables, constraints ->
                val placeables = measurables.map { it.measure(constraints) }
                val width = placeables.first().width
                layout(width, placeables.maxOf { it.height }) {
                    // **By which month it is**, read here in placement, so a frame
                    // of the motion moves the months and recomposes nothing — and a
                    // month is where it belongs whichever of composition and layout
                    // caught up first. Placed by slot, a landing once drew the old
                    // month in the middle for a frame. Relative, so right to left
                    // the month before is on the right.
                    placeables.forEachIndexed { i, month ->
                        val at = pager.placeOf(months[i])
                        month.placeRelative(if (at.isNaN()) width * 4 else (at * width).roundToInt(), 0)
                    }
                }
            }
        }
        }
        }
    }
}

/**
 * Where a calendar's months are: a [PeriodPager] over month indices, read and
 * driven in months.
 *
 * Slot 0 is [origin] and each slot on is a month on. A far jump — to today
 * from a year away — does not travel through every month between; see
 * [PeriodPager], which the event calendar pages its weeks, days and years with.
 */
@Stable
private class MonthPager(start: LocalDate) {
    private val periods = PeriodPager(monthIndex(start))

    val origin: LocalDate get() = monthOf(periods.origin)
    val held: Boolean get() = periods.held
    val heldFrom: Float get() = periods.heldFrom
    val startSlot: Int get() = periods.startSlot
    val fromRest: Boolean get() = periods.fromRest
    val position: Float get() = periods.position

    /** The month the running move is going to, or last went to. */
    var headedFor: LocalDate?
        get() = periods.headedFor?.let(::monthOf)
        set(value) {
            periods.headedFor = value?.let(::monthIndex)
        }

    /** The months any of which is in view: one at rest, two while moving. */
    val inView: List<LocalDate> by derivedStateOf(structuralEqualityPolicy()) { periods.inView.map(::monthOf) }

    fun monthAt(slot: Int): LocalDate = monthOf(periods.periodAt(slot))

    /** Pages from the middle of the view to [month]: positive towards later months. */
    fun placeOf(month: LocalDate): Float = periods.placeOf(monthIndex(month))

    fun take() = periods.take()

    fun pull(pages: Float) = periods.pull(pages)

    fun letGo() = periods.letGo()

    fun moveTo(month: LocalDate, scope: CoroutineScope, stiffness: Float, cut: Boolean, velocity: Float? = null) {
        if (velocity == null) {
            periods.moveTo(monthIndex(month), scope, stiffness, cut)
        } else {
            periods.moveTo(monthIndex(month), scope, stiffness, cut, velocity)
        }
    }
}

/** The first of the month [index] counts — see [monthIndex]. */
private fun monthOf(index: Int): LocalDate =
    LocalDate(index.floorDiv(12), index.mod(12) + 1, 1)

private fun monthIndex(month: LocalDate): Int = month.year * 12 + month.month.ordinal

/**
 * The chosen date, shown as a tappable field.
 *
 * ```
 * var date by remember { mutableStateOf<LocalDate?>(null) }
 * var picking by remember { mutableStateOf(false) }
 *
 * DateField(value = date, onClick = { picking = true }, label = "Post date")
 *
 * Dialog(
 *     visible = picking,
 *     onDismissRequest = { picking = false },
 *     actions = { cancel() },
 * ) {
 *     DatePicker(value = date, onValueChange = { date = it; picking = false })
 * }
 * ```
 *
 * The thing you put on a form, as [TimeField] is for a time: tapping it is
 * expected to open a [DatePicker] in a dialog or a sheet. Kept apart from the
 * picker so the picker never has to know how it is being presented, and so the
 * form is one tap target rather than a text field with a calendar button beside
 * it that both open the same thing.
 *
 * @param value The date chosen, or `null` for none yet, when [placeholder]
 *   is shown, as a [DatePicker] takes one.
 * @param supporting A line under the field, as a [TextField][io.kontour.ui.components.text.TextField]'s:
 *   what the date is for, or what it has to be.
 * @param errorMessage In place of [supporting], in the error colour, the
 *   field's edge in it too, and said with the field: *"Choose a date after
 *   today"*.
 * @param style How the date is written: `DateStyle.Medium`, "14 Mar 2026" in
 *   English, by default; `Full` adds the weekday.
 */
@Composable
fun DateField(
    value: LocalDate?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: String? = null,
    placeholder: String = Theme.strings.select,
    supporting: String? = null,
    errorMessage: String? = null,
    style: DateStyle = DateStyle.Medium,
    formats: LocaleFormats = Theme.formats,
    interactionSource: MutableInteractionSource? = null,
) {
    PickerField(
        text = value?.let { formats.date(it, style) },
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        label = label,
        interactionSource = interactionSource,
        placeholder = placeholder,
        supporting = supporting,
        errorMessage = errorMessage,
    )
}
