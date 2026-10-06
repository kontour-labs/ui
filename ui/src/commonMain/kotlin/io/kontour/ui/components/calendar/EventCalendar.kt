package io.kontour.ui.components.calendar

import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableStateOf
import io.kontour.ui.interaction.rememberLongPressFeedback
import io.kontour.ui.interaction.rememberLandingFeedback
import io.kontour.ui.input.LocalInputModality
import io.kontour.ui.locale.ListStyle
import io.kontour.ui.theme.Strings
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.action.IconButton
import io.kontour.ui.components.datetime.DatePickerDefaults
import io.kontour.ui.components.datetime.MonthFlick
import io.kontour.ui.components.datetime.PeriodPager
import io.kontour.ui.components.datetime.SwipeCommitShare
import io.kontour.ui.components.selection.SegmentedControl
import io.kontour.ui.foundation.SystemIcons
import io.kontour.ui.foundation.Text
import io.kontour.ui.interaction.DragClaim
import io.kontour.ui.interaction.FeedbackIntent
import io.kontour.ui.interaction.horizontalDragOwning
import io.kontour.ui.interaction.rememberDetentTicker
import io.kontour.ui.locale.DateStyle
import io.kontour.ui.locale.LocaleFormats
import io.kontour.ui.theme.Theme
import io.kontour.ui.theme.toneColours
import kotlin.math.roundToInt
import kotlin.time.Clock
import kotlinx.coroutines.delay
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/**
 * A calendar of events: a day's or a week's hours, a month of day boxes, or a
 * year of small months — boxes for each day, designed to show what is on.
 *
 * ```kotlin
 * val calendar = rememberEventCalendarState()
 * EventCalendar(
 *     state = calendar,
 *     events = events,
 *     onEventClick = { open(it) },
 *     modifier = Modifier.fillMaxSize(),
 * )
 * ```
 *
 * Asked for as *"a proper calendar component … not like a date picker, but more
 * of a calendar that has boxes for each day of the month, designed to show
 * events"*, in day, week, month and year views.
 *
 * ### The app keeps the events
 *
 * [events] are the app's, and the calendar never changes them: it shows them,
 * and says what was pressed. Load what [EventCalendarState.visibleDates] covers
 * as the reader moves about, or hand all of them over — a few hundred are no
 * trouble.
 *
 * ### Moving about
 *
 * The header steps a period back and on, goes to today, and switches the view.
 * A sideways swipe steps too, in every view but the year. A day's number, in the
 * month and in a week's heading, opens that day; a month's name in the year
 * opens that month, and a day in it its day.
 *
 * @param state What it shows: see [rememberEventCalendarState].
 * @param events Everything to show, in any order. Each is drawn in the days it
 *   covers; ones the current view does not show cost nothing to have here.
 * @param now The time on the reader's clock, for today's mark and the line
 *   across the hours: by default the system's, kept to the minute. Null for a
 *   calendar that is not about the present — no today, no line.
 * @param onEventClick An event pressed. Null: events are not buttons.
 * @param onDateClick A day pressed in the month view, away from its number and
 *   its events — to make an event there, say. Null: the boxes are not buttons.
 * @param onTimeClick An empty time pressed in the day or week view, to the
 *   quarter-hour before it. Null: empty time is not a button.
 * @param onCreateRequest A new event drawn out on empty time in the day or
 *   week view — held and dragged on a touchscreen, dragged with a mouse — from
 *   its start to its end, on whole [snap]s. The calendar shows it while it is
 *   drawn and adds nothing: add the event to [events] to keep it. Null: empty
 *   time cannot be drawn on.
 * @param onEventMove An event carried to another time or day — held and
 *   dragged, dragged with a mouse, picked up with Space and moved with the
 *   arrows, or moved by a screen reader's actions — with where it would start
 *   and end. Its length is kept. Null, or an event not [CalendarEvent.editable]:
 *   it stays where it is.
 * @param onEventResize An event's start or end dragged — by its edge, or with
 *   Shift and the arrows — with its new times, never shorter than [snap].
 * @param snap The step every edit moves in: a quarter-hour by default. A whole
 *   number of minutes that divides a day.
 * @param hourHeight How tall an hour is in the day and week views.
 * @param formats How dates, times and numbers are written: the theme's.
 * @param header Above the calendar: by default [EventCalendarDefaults.Header],
 *   with the title, the arrows, today and the view switcher. Null for none — an
 *   app with its own top bar drives [state] from there.
 * @param eventContent What an event shows inside its box, bar or line: by
 *   default [EventCalendarDefaults.EventContent]. The calendar keeps the box
 *   itself — its place, its colour, its press and what a screen reader says —
 *   so this decides only the inside.
 */
@Composable
fun EventCalendar(
    state: EventCalendarState,
    events: List<CalendarEvent>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    now: LocalDateTime? = EventCalendarDefaults.currentTime(),
    onEventClick: ((CalendarEvent) -> Unit)? = null,
    onDateClick: ((LocalDate) -> Unit)? = null,
    onTimeClick: ((LocalDateTime) -> Unit)? = null,
    onCreateRequest: ((start: LocalDateTime, end: LocalDateTime) -> Unit)? = null,
    onEventMove: ((event: CalendarEvent, start: LocalDateTime, end: LocalDateTime) -> Unit)? = null,
    onEventResize: ((event: CalendarEvent, start: LocalDateTime, end: LocalDateTime) -> Unit)? = null,
    snap: Duration = EventCalendarDefaults.Snap,
    hourHeight: Dp = EventCalendarDefaults.HourHeight,
    formats: LocaleFormats = Theme.formats,
    header: (@Composable (EventCalendarState) -> Unit)? = { EventCalendarDefaults.Header(it) },
    eventContent: @Composable (event: CalendarEvent, presentation: EventPresentation) -> Unit =
        { event, presentation -> EventCalendarDefaults.EventContent(event, presentation) },
) {
    val snapMinutes = snap.inWholeMinutes
    require(snap.isPositive() && snap == snapMinutes.minutes && MinutesPerDay % snapMinutes == 0L) {
        "EventCalendar: `snap` is $snap, and every edit moves in whole steps of it through a day — give it a whole " +
            "number of minutes that divides a day: 5, 10, 15, 30 or 60 minutes."
    }
    val today = now?.date
    SideEffect { state.today = today }
    val pager = rememberCalendarPager(state)
    val edit = rememberEventEdit()
    edit.stepMinutes = snapMinutes.toInt()
    val announcer = remember { Announcer() }
    val modality = LocalInputModality.current
    val scope = CalendarScope(
        state = state,
        events = events,
        enabled = enabled,
        now = now,
        onEventClick = onEventClick,
        onDateClick = onDateClick,
        onTimeClick = onTimeClick,
        hourHeight = hourHeight,
        formats = formats,
        eventContent = eventContent,
        pager = pager,
        edit = edit,
        onCreateRequest = onCreateRequest,
        onEventMove = onEventMove,
        onEventResize = onEventResize,
        announcer = announcer,
        touch = modality.needsLargeTargets,
        strings = Theme.strings,
    )
    // Turned off — disabled — mid-edit, the edit is put back.
    if (!enabled && edit.live) SideEffect { edit.cancel() }
    BoxWithConstraints(modifier) {
        // Given no width — in a row that scrolls sideways, say — a phone's.
        // Clipped, so a calendar squeezed narrower than its header's buttons
        // keeps its ink inside its box.
        val width = if (constraints.hasBoundedWidth) Modifier.fillMaxWidth() else Modifier.width(UnboundedWidth)
        Column(width.clipToBounds()) {
            header?.invoke(state)
            when (state.view) {
                CalendarView.Month -> MonthView(scope)
                CalendarView.Day, CalendarView.Week -> TimeView(scope)
                CalendarView.Year -> YearView(scope)
            }
        }
        // What an edit did, read out: "Stand-up moved to Tuesday, 9:15 – 9:30".
        // Over the calendar rather than in its column, so it takes no room.
        Box(Modifier.size(1.dp).semantics { liveRegion = LiveRegionMode.Polite; contentDescription = announcer.message })
    }
}

/** The width of a calendar given none. */
private val UnboundedWidth = 360.dp

/** Everything a view of the calendar draws with, gathered once. */
@Stable
internal class CalendarScope(
    val state: EventCalendarState,
    val events: List<CalendarEvent>,
    val enabled: Boolean,
    val now: LocalDateTime?,
    val onEventClick: ((CalendarEvent) -> Unit)?,
    val onDateClick: ((LocalDate) -> Unit)?,
    val onTimeClick: ((LocalDateTime) -> Unit)?,
    val hourHeight: Dp,
    val formats: LocaleFormats,
    val eventContent: @Composable (CalendarEvent, EventPresentation) -> Unit,
    val pager: CalendarPager,
    val edit: EventEdit,
    val onCreateRequest: ((LocalDateTime, LocalDateTime) -> Unit)?,
    val onEventMove: ((CalendarEvent, LocalDateTime, LocalDateTime) -> Unit)?,
    val onEventResize: ((CalendarEvent, LocalDateTime, LocalDateTime) -> Unit)?,
    val announcer: Announcer,
    /** Whether the pointer is a finger: an edit then starts with a long press, and a quick swipe still pages. */
    val touch: Boolean,
    val strings: Strings,
) {
    /** Whether [event] can be carried. */
    fun movable(event: CalendarEvent): Boolean = enabled && onEventMove != null && event.editable

    /** Whether [event]'s edges can be dragged. */
    fun resizable(event: CalendarEvent): Boolean = enabled && onEventResize != null && event.editable

    /** Puts the edit in hand down, and reports and reads out what it did. */
    fun putDown() {
        val done = edit.finish() ?: return
        val event = done.event
        when {
            done.kind == EditKind.Create -> onCreateRequest?.invoke(done.start, done.end)
            event == null -> Unit
            // Moved from the keyboard with only its end changed: that is a resize.
            done.kind == EditKind.Move && done.start == event.start -> onEventResize?.invoke(event, done.start, done.end)
            done.kind == EditKind.Move -> onEventMove?.invoke(event, done.start, done.end)
            else -> onEventResize?.invoke(event, done.start, done.end)
        }
        announce(event?.title ?: strings.newEvent, done.start, done.end)
    }

    /** Reads out where [title] now is. */
    fun announce(title: String, start: LocalDateTime, end: LocalDateTime) {
        val days = formats.date(start.date, DateStyle.Full)
        val time = strings.timeRange(formats.time(start.time), formats.time(end.time))
        announcer.say(strings.eventMoved(title, formats.list(listOf(days, time), ListStyle.Unit)))
    }

    val today: LocalDate? get() = now?.date

    /** The events with any part between [from] and [to], both included. */
    fun eventsBetween(from: LocalDate, to: LocalDate): List<CalendarEvent> = events.filter { it.overlaps(from, to) }
}

/** What [EventCalendar] takes by default. */
object EventCalendarDefaults {

    /** Where the day and week views' hours open: the start of a working day. */
    val ScrollTime: LocalTime get() = DefaultScrollTime

    /** How tall an hour is in the day and week views. */
    val HourHeight: Dp
        @Composable @ReadOnlyComposable get() = Theme.componentDefaults.eventCalendarHourHeight

    /** The step every edit moves in: a quarter-hour. */
    val Snap: Duration get() = DefaultSnap

    /** Today, on the system's clock in its time zone. */
    fun currentDate(): LocalDate = DatePickerDefaults.currentDate()

    /**
     * The time now on the system's clock, kept current: it changes on each
     * minute, so the line across the hours moves and today turns over at
     * midnight without anything asking it to.
     */
    @Composable
    fun currentTime(): LocalDateTime {
        val time by produceState(systemNow()) {
            while (true) {
                waitForNextMinute()
                value = systemNow()
            }
        }
        return time
    }

    /**
     * The calendar's header: the period's title, back and on, today, and the
     * view switcher. On a narrow window the switcher takes a row of its own.
     *
     * @param views The views the switcher offers, in its order. One view, or
     *   none, and there is no switcher.
     */
    @Composable
    fun Header(
        state: EventCalendarState,
        modifier: Modifier = Modifier,
        views: List<CalendarView> = CalendarView.entries,
    ) {
        CalendarHeader(state, modifier, views)
    }

    /**
     * What an event shows inside its box, bar or line: its title, and its time
     * where the shape has room for one.
     */
    @Composable
    fun EventContent(event: CalendarEvent, presentation: EventPresentation) {
        DefaultEventContent(event, presentation)
    }
}

private val DefaultScrollTime = LocalTime(8, 0)
private val DefaultSnap = 15.minutes

/** What the calendar last said, for its live region. A message repeated is said again. */
@Stable
internal class Announcer {
    var message by mutableStateOf("")
        private set
    private var count = 0

    fun say(text: String) {
        // A space either side changes nothing heard and makes the same words a
        // new value, so a second identical move is announced too.
        count++
        message = if (count % 2 == 0) text else " $text"
    }
}

/**
 * The calendar's edit state, with the haptics it is felt through: a detent per
 * step, the pick-up, the landing. Built here so that everything the calendar
 * makes felt is in this one file.
 */
@Composable
internal fun rememberEventEdit(): EventEdit {
    val steps = rememberDetentTicker(FeedbackIntent.Snap)
    val landing = rememberLandingFeedback()
    val pickedUp = rememberLongPressFeedback()
    return remember(steps, landing) { EventEdit(steps, landing, pickedUp) }
}

private fun systemNow(): LocalDateTime =
    Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())

/** Until the start of the next minute on the system's clock. */
private suspend fun waitForNextMinute() {
    val now = systemNow()
    val intoMinute = now.second * MillisPerSecond + now.nanosecond / NanosPerMilli
    delay((MillisPerMinute - intoMinute).toLong().coerceAtLeast(1L))
}

private const val MillisPerSecond = 1000
private const val MillisPerMinute = 60_000
private const val NanosPerMilli = 1_000_000

/** A view's name, as the switcher and a screen reader say it. */
@Composable
@ReadOnlyComposable
internal fun viewName(view: CalendarView): String {
    val strings = Theme.strings
    return when (view) {
        CalendarView.Day -> strings.day
        CalendarView.Week -> strings.week
        CalendarView.Month -> strings.month
        CalendarView.Year -> strings.year
    }
}

/** The title of the period [state] shows: a day's date, a week's months, a month, a year. */
internal fun periodTitle(state: EventCalendarState, formats: LocaleFormats, timeRange: (String, String) -> String): String {
    val dates = state.visibleDates
    return when (state.view) {
        CalendarView.Day -> formats.date(state.date, DateStyle.Full)
        CalendarView.Week -> {
            val first = formats.monthAndYear(dates.start)
            val last = formats.monthAndYear(dates.endInclusive)
            if (first == last) first else timeRange(first, last)
        }
        CalendarView.Month -> formats.monthAndYear(state.date)
        CalendarView.Year -> formats.digits(state.date.year.toLong())
    }
}

@Composable
private fun CalendarHeader(state: EventCalendarState, modifier: Modifier, views: List<CalendarView>) {
    val strings = Theme.strings
    val formats = Theme.formats
    val (back, on) = when (state.view) {
        CalendarView.Day -> strings.previousDay to strings.nextDay
        CalendarView.Week -> strings.previousWeek to strings.nextWeek
        CalendarView.Month -> strings.previousMonth to strings.nextMonth
        CalendarView.Year -> strings.previousYear to strings.nextYear
    }
    val title = periodTitle(state, formats, strings.timeRange)
    val names = views.associateWith { viewName(it) }

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val oneRow = constraints.hasBoundedWidth && maxWidth >= HeaderOneRowWidth
        val titleText = @Composable { titleModifier: Modifier ->
            Text(
                text = title,
                style = Theme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                // Read out as the period changes, whichever way it changed.
                modifier = titleModifier
                    .padding(horizontal = Theme.spacing.xs)
                    .semantics {
                        heading()
                        liveRegion = LiveRegionMode.Polite
                    },
            )
        }
        val arrows = @Composable {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { state.step(-1) }, icon = SystemIcons.ChevronBack, contentDescription = back)
                IconButton(
                    onClick = { state.showToday() },
                    icon = DatePickerDefaults.TodayIcon,
                    contentDescription = strings.returnToToday,
                    enabled = state.today != null,
                )
                IconButton(onClick = { state.step(1) }, icon = SystemIcons.ChevronForward, contentDescription = on)
            }
        }
        val switcher = @Composable { switcherModifier: Modifier ->
            if (views.size > 1) {
                SegmentedControl(
                    value = state.view,
                    options = views,
                    onValueChange = { view -> state.show(view) },
                    optionLabel = { view -> names.getValue(view) },
                    modifier = switcherModifier,
                )
            }
        }
        if (oneRow) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Theme.spacing.sm),
                modifier = Modifier.padding(bottom = Theme.spacing.sm),
            ) {
                titleText(Modifier.weight(1f))
                arrows()
                // Bounded, or a switcher that fills its width leaves the title none.
                switcher(Modifier.width(HeaderSwitcherWidth))
            }
        } else {
            Column(
                verticalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
                modifier = Modifier.padding(bottom = Theme.spacing.sm),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    titleText(Modifier.weight(1f))
                    arrows()
                }
                switcher(Modifier.fillMaxWidth())
            }
        }
    }
}

/** Wide enough for the title, the arrows and the switcher on one line. */
private val HeaderOneRowWidth = 600.dp

/** The switcher's width beside the title: four short words with room round them. */
private val HeaderSwitcherWidth = 320.dp

@Composable
private fun DefaultEventContent(event: CalendarEvent, presentation: EventPresentation) {
    val formats = Theme.formats
    val strings = Theme.strings
    val type = Theme.typography
    when (presentation) {
        EventPresentation.Block -> Column(Modifier.padding(horizontal = Theme.spacing.xs, vertical = Theme.spacing.xxs)) {
            Text(event.title, style = type.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                strings.timeRange(formats.time(event.start.time), formats.time(event.end.time)),
                style = type.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            event.location?.let { Text(it, style = type.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        EventPresentation.Bar -> Text(
            event.title,
            style = type.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = Theme.spacing.xs),
        )
        EventPresentation.Line -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Theme.spacing.xxs),
            modifier = Modifier.padding(horizontal = Theme.spacing.xxs),
        ) {
            Box(Modifier.size(Theme.spacing.xs).background(toneColours(event.tone).solid, Theme.shapes.capsule))
            Text(formats.time(event.start.time), style = type.labelSmall, colour = Theme.colours.contentMuted, maxLines = 1)
            Text(event.title, style = type.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * The calendar's pager: the periods of the view it shows, swiped between and
 * stepped by the header, as the date picker's months are — one model, see
 * [PeriodPager]. Keyed by the view, so a change of view is a cut.
 */
@Stable
internal class CalendarPager(
    val periods: PeriodPager,
    private val state: EventCalendarState,
    private val release: (Float) -> Unit,
) {
    var pageWidth by mutableIntStateOf(0)

    /**
     * The swipe, on whatever the pages are under. With a mouse, not on
     * anything [edit] takes a drag on — an event, empty time that can be drawn
     * on — where a mouse drag is the edit; a finger holds first to edit, so its
     * quick swipe still pages.
     */
    @Composable
    fun Modifier.swipesPeriods(enabled: Boolean, edit: EventEdit, touch: Boolean): Modifier {
        val scope = rememberCoroutineScope()
        val towardsEnd = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f
        val placed = remember { PlacedAt() }
        val isTouch by rememberUpdatedState(touch)
        return onGloballyPositioned { placed.coordinates = it }.horizontalDragOwning(
            enabled = enabled,
            interactionSource = null,
            scope = scope,
            claimsOn = DragClaim.Direction,
            accepts = { at ->
                val coordinates = placed.coordinates
                isTouch || coordinates == null || !coordinates.isAttached || !edit.draggableAt(coordinates.localToRoot(at))
            },
            onStart = { periods.take() },
            onDelta = { delta ->
                val width = pageWidth.toFloat()
                if (width > 0f) periods.pull(-delta * towardsEnd / width)
            },
            onEnd = { release(0f) },
            onRelease = { velocity -> release(velocity * towardsEnd) },
        ).onSizeChanged { pageWidth = it.width }
    }

    /** The view's period [state] is on. */
    val current: Int get() = state.period
}

@Composable
internal fun rememberCalendarPager(state: EventCalendarState): CalendarPager {
    val motion = Theme.motion
    val scope = rememberCoroutineScope()
    val view = state.view
    val periods = remember(view) { PeriodPager(state.period) }
    val ticker = rememberDetentTicker(FeedbackIntent.Snap)
    val flickPx = with(LocalDensity.current) { MonthFlick.toPx() }
    val stiffness = motion.springSnappy.stiffness
    val pager = remember(periods) {
        lateinit var self: CalendarPager
        self = CalendarPager(periods, state) { velocity ->
            if (!periods.held) return@CalendarPager
            val width = self.pageWidth.toFloat()
            val at = periods.position
            val from = periods.heldFrom
            val start = periods.startSlot
            periods.letGo()
            val speed = if (width > 0f) -velocity / width else 0f
            val flick = if (width > 0f) flickPx / width else Float.MAX_VALUE
            val moved = at - from
            val slot = when {
                width <= 0f -> start
                speed > flick && moved > 0f -> start + 1
                speed < -flick && moved < 0f -> start - 1
                periods.fromRest -> when {
                    at - start > SwipeCommitShare -> start + 1
                    at - start < -SwipeCommitShare -> start - 1
                    else -> start
                }
                else -> at.roundToInt().coerceIn(start - 1, start + 1)
            }
            val landing = periods.periodAt(slot)
            if (landing != state.period) {
                periods.headedFor = landing
                state.step(landing - state.period)
                ticker.at(landing)
            }
            periods.moveTo(landing, scope, stiffness, cut = motion.reduceMotion, velocity = speed)
        }
        self
    }
    // Every other way the period changes — the header, a tap, the app — the
    // pager goes there from wherever it is.
    LaunchedEffect(periods) {
        snapshotFlow { state.period }.collect { period ->
            if (period == periods.headedFor || periods.held) {
                ticker.at(period)
                return@collect
            }
            ticker.reset()
            ticker.at(period)
            periods.moveTo(period, scope, stiffness, cut = motion.reduceMotion)
        }
    }
    return pager
}

/**
 * The periods in view, side by side as the pager has them: one at rest, two
 * while it moves. Each keyed by its period, so the one coming in is the same
 * composition moving to the middle. Under reduced motion, only the current
 * one: the pager cuts.
 */
@Composable
internal fun PeriodStrip(pager: CalendarPager, modifier: Modifier = Modifier, content: @Composable (period: Int) -> Unit) {
    val reduce = Theme.motion.reduceMotion
    val shown = if (reduce) listOf(pager.current) else pager.periods.inView
    Layout(
        content = { for (period in shown) key(period) { content(period) } },
        modifier = if (shown.size > 1) modifier.clipToBounds() else modifier,
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints) }
        val width = placeables.firstOrNull()?.width ?: constraints.minWidth
        val height = placeables.maxOfOrNull { it.height } ?: constraints.minHeight
        layout(width, height) {
            placeables.forEachIndexed { i, page ->
                val at = if (reduce) 0f else pager.periods.placeOf(shown[i])
                page.placeRelative(if (at.isNaN()) width * OffStage else (at * width).roundToInt(), 0)
            }
        }
    }
}

/** A node's coordinates, kept for a gesture to read. Not state: written in layout, read in input. */
internal class PlacedAt {
    var coordinates: LayoutCoordinates? = null
}

/** Where a page that has no place is put: well off the side, not drawn. */
private const val OffStage = 4
