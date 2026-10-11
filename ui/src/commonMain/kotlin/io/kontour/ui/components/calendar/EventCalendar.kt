package io.kontour.ui.components.calendar

import androidx.compose.ui.semantics.onClick
import io.kontour.ui.overlay.OverlayAlignment
import io.kontour.ui.overlay.DropdownMenu
import io.kontour.ui.components.action.ButtonSize
import io.kontour.ui.components.action.ButtonVariant
import io.kontour.ui.components.action.Button
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import io.kontour.ui.overlay.AnchoredDropdownMenu
import io.kontour.ui.overlay.LocalOverlayHostOrNull
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableStateOf
import io.kontour.ui.components.datetime.rememberTodayFlash
import io.kontour.ui.components.datetime.LocalTodayFlash
import io.kontour.ui.interaction.rememberLongPressFeedback
import io.kontour.ui.interaction.rememberLandingFeedback
import io.kontour.ui.interaction.rememberHoldFeedback
import io.kontour.ui.input.LocalInputModality
import io.kontour.ui.input.rememberFocusRingVisible
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import kotlin.time.Duration.Companion.milliseconds
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.foundation.layout.LayoutScopeMarker
import androidx.compose.ui.input.pointer.PointerId
import io.kontour.ui.overlay.MenuScope
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
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import kotlin.math.floor
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
 * A sideways swipe steps too, in the day, three-day and week views; the month
 * scrolls up and down through its weeks instead, and the year through the
 * years, each about the month or year most on screen.
 * A day's number, in the
 * month and in a week's heading, opens that day; a month's name in the year
 * opens that month, and a day in it its day.
 *
 * From the keyboard the grid is one stop, with a cursor the arrows move: a day
 * in a month, a slot of [snap] in a day or a week. Its day is the state's
 * [EventCalendarState.date], so it pages past the period's ends. Enter opens
 * the day — [onDateClick], or its day view, as a press on it does — or presses the time —
 * [onTimeClick], or else [onCreateRequest] for an hour. Tab goes on to the
 * events of the cursor's day.
 *
 * @param state What it shows: see [rememberEventCalendarState].
 * @param events Everything to show, in any order. Each is drawn in the days it
 *   covers; ones the current view does not show cost nothing to have here.
 * @param now The time on the reader's clock, for today's mark and the line
 *   across the hours: by default the system's, kept to the minute. Null for a
 *   calendar that is not about the present — no today, no line.
 * @param onEventClick An event pressed. Null: events are not buttons.
 * @param onDateClick A day pressed in the month view — anywhere in its box, its
 *   number included, away from its events — or Enter on it, instead of opening
 *   its day view: to make an event there, say, or to open the day yourself.
 *   Null: a pressed day opens its day view.
 * @param onTimeClick An empty time pressed in the day or week view, to the
 *   quarter-hour before it. Null: empty time is not a button.
 * @param onCreateRequest A new event, from its start to its end, on whole
 *   [snap]s: drawn out on empty time in the day or week view — held and
 *   dragged on a touchscreen, dragged with a mouse — or chosen as "New event"
 *   from the menu a finger held still on empty time opens, or a secondary
 *   click, for an hour there. In the month, the menu's new event is the day
 *   held, midnight to midnight, or the days drawn out from it. The calendar
 *   shows it while it is drawn and adds nothing: add the event to [events] to
 *   keep it. Null: empty time cannot be drawn on.
 * @param onEventMove An event carried to another time or day — held and
 *   dragged, dragged with a mouse, picked up with Space and moved with the
 *   arrows, or moved by a screen reader's actions — with where it would start
 *   and end. Its length is kept. Held at either side of a day or week, a carry
 *   pages on to the one there and goes on; near the top or the foot of a
 *   month, the weeks scroll with it. Null, or an event not
 *   [CalendarEvent.editable]: it stays where it is.
 * @param onEventResize An event's start or end dragged — by its edge, or with
 *   Shift and the arrows — with its new times, never shorter than [snap]. With
 *   a mouse the edges are always there to drag; on a touchscreen, once "Adjust
 *   time" is chosen from the event's menu, until a press anywhere else.
 * @param onEventDelete An event deleted: from the menu a finger held on it
 *   opens, or a secondary click; with Delete from the keyboard; or by a screen
 *   reader's action. The calendar removes nothing — take it out of [events].
 * @param eventMenu What a finger held on an event, or a secondary click on it,
 *   offers: by default [EventCalendarDefaults.EventMenu] — "Adjust time" on a
 *   touchscreen and "Delete", each where it applies. Built with a
 *   [io.kontour.ui.overlay.MenuScope], so an app's own actions go beside or
 *   instead of the calendar's, which are [EventMenuScope.adjustTime] and
 *   [EventMenuScope.delete]. A menu of the app's own opens on any event,
 *   one that cannot be moved included; the default only where one of its
 *   actions applies. Null: no menu, and a hold only takes the event up.
 * @param emptyMenu What a finger held on empty time, or a secondary click there,
 *   offers, with the time the edit in hand proposes — an hour where the finger
 *   is, or the day held in a month: by default [EventCalendarDefaults.EmptyMenu],
 *   "New event", which is [EmptyMenuScope.newEvent]. Null: no menu.
 * @param snap The step every edit moves in: a quarter-hour by default. A whole
 *   number of minutes that divides a day.
 * @param hourHeight How tall an hour is in the day and week views.
 * @param daysStyle How the three-day and week views lay their days out: the
 *   hours with the all-day events above them, or every event stacked with no
 *   hours. The day view always has its hours.
 * @param views The views the calendar offers: its switcher's, in its order,
 *   and the only ones it can be in. A day pressed in a calendar without its day
 *   view opens the nearest view it has — three days, or the week — and a state
 *   in a view not offered is moved to the nearest that is. At least one.
 * @param showTimedEvents Off, the calendar shows only all-day events and
 *   those a day or longer — a plan of trips and leave rather than of meetings —
 *   and the day, three-day and week views stack them with no hours, as
 *   [CalendarDaysStyle.Condensed] does, since there is nothing to put in the
 *   hours.
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
    now: LocalDateTime? = EventCalendarDefaults.currentDateTime(),
    onEventClick: ((CalendarEvent) -> Unit)? = null,
    onDateClick: ((LocalDate) -> Unit)? = null,
    onTimeClick: ((LocalDateTime) -> Unit)? = null,
    onCreateRequest: ((start: LocalDateTime, end: LocalDateTime) -> Unit)? = null,
    onEventMove: ((event: CalendarEvent, start: LocalDateTime, end: LocalDateTime) -> Unit)? = null,
    onEventResize: ((event: CalendarEvent, start: LocalDateTime, end: LocalDateTime) -> Unit)? = null,
    onEventDelete: ((event: CalendarEvent) -> Unit)? = null,
    snap: Duration = EventCalendarDefaults.Snap,
    hourHeight: Dp = EventCalendarDefaults.HourHeight,
    daysStyle: CalendarDaysStyle = CalendarDaysStyle.Hours,
    showTimedEvents: Boolean = true,
    views: List<CalendarView> = EventCalendarDefaults.Views,
    formats: LocaleFormats = Theme.formats,
    eventMenu: (@Composable EventMenuScope.(event: CalendarEvent) -> Unit)? = EventCalendarDefaults.EventMenu,
    emptyMenu: (@Composable EmptyMenuScope.(start: LocalDateTime, end: LocalDateTime) -> Unit)? = EventCalendarDefaults.EmptyMenu,
    header: (@Composable (EventCalendarState) -> Unit)? = { EventCalendarDefaults.Header(it) },
    eventContent: @Composable (event: CalendarEvent, presentation: EventPresentation) -> Unit =
        { event, presentation -> EventCalendarDefaults.EventContent(event, presentation) },
) {
    val snapMinutes = snap.inWholeMinutes
    require(snap.isPositive() && snap == snapMinutes.minutes && MinutesPerDay % snapMinutes == 0L) {
        "EventCalendar: `snap` is $snap, and every edit moves in whole steps of it through a day — give it a whole " +
            "number of minutes that divides a day: 5, 10, 15, 30 or 60 minutes."
    }
    require(views.isNotEmpty()) { "EventCalendar: `views` is empty, and a calendar has to show something. Give it at least one view." }
    // Before anything reads the view: a state in a view not offered moves to
    // the nearest that is, rather than drawing one frame of it.
    if (state.allowedViews != views) state.allowedViews = views
    if (state.view !in views) state.show(state.offered(state.view) ?: views.first())
    val today = now?.date
    SideEffect { state.today = today }
    val pager = rememberCalendarPager(state)
    val edit = rememberEventEdit()
    edit.stepMinutes = snapMinutes.toInt()
    val announcer = remember { Announcer() }
    val modality = LocalInputModality.current
    val cursorInteractions = remember { MutableInteractionSource() }
    val cursorShown = rememberFocusRingVisible(cursorInteractions)
    // The one place events are filtered: everything below finds them here.
    val shown = if (showTimedEvents) events else remember(events) { events.filterNot { it.isTimed() } }
    val scope = CalendarScope(
        state = state,
        events = shown,
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
        onEventDelete = onEventDelete,
        announcer = announcer,
        touch = modality.needsLargeTargets,
        strings = Theme.strings,
        cursorInteractions = cursorInteractions,
        cursorShown = cursorShown,
        menus = LocalOverlayHostOrNull.current != null,
        eventMenu = eventMenu,
        emptyMenu = emptyMenu,
        daysStyle = if (showTimedEvents) daysStyle else CalendarDaysStyle.Condensed,
        showsTimed = showTimedEvents,
    )
    // The days a pointer's day can be: the ones on show, not a page sliding away.
    SideEffect { edit.shown = { state.visibleDates } }
    // Turned off — disabled — mid-edit, the edit is put back.
    if (!enabled && edit.live) SideEffect { edit.cancel() }
    // Handles, or a menu, on an event that has gone — deleted, or taken out of
    // `events` by the app — or on a calendar turned off, go with it: left, the
    // other events stayed faded and the next press only took them away.
    val activeGone = edit.active?.let { id -> events.none { it.id == id } } == true
    val menuGone = edit.menu?.event?.let { menu -> events.none { it.id == menu.id } } == true
    if (activeGone || menuGone || (!enabled && (edit.active != null || edit.menu != null))) {
        SideEffect {
            if (activeGone || !enabled) edit.active = null
            if (menuGone || !enabled) edit.menu = null
        }
    }
    // Today pointed at whenever it is gone back to: the header's button, or the app's own call.
    val todayFlash = rememberTodayFlash()
    val motion = Theme.motion
    LaunchedEffect(state) {
        var seen = state.todayRequest?.serial
        snapshotFlow { state.todayRequest }.collect { request ->
            if (request == null || request.serial == seen) return@collect
            seen = request.serial
            todayFlash.flash(after = if (request.paged) motion.default.milliseconds else Duration.ZERO)
        }
    }
    CompositionLocalProvider(LocalTodayFlash provides { todayFlash.value() }) {
        BoxWithConstraints(modifier.carriesEdits(scope)) {
            // Given no width — in a row that scrolls sideways, say — a phone's.
            // Clipped, so a calendar squeezed narrower than its header's buttons
            // keeps its ink inside its box.
            val width = if (constraints.hasBoundedWidth) Modifier.fillMaxWidth() else Modifier.width(UnboundedWidth)
            Column(width.clipToBounds()) {
                header?.invoke(state)
                when (state.view) {
                    CalendarView.Month -> MonthView(scope)
                    CalendarView.Day, CalendarView.Week, CalendarView.ThreeDays -> TimeView(scope)
                    CalendarView.Year -> YearView(scope)
                    CalendarView.Schedule -> ScheduleView(scope)
                }
            }
            // What an edit did, read out: "Stand-up moved to Tuesday, 9:15 – 9:30".
            // Over the calendar rather than in its column, so it takes no room.
            Box(Modifier.size(1.dp).semantics { liveRegion = LiveRegionMode.Polite; contentDescription = announcer.message })
            if (scope.menus) EditMenus(scope)
            // The range picker's arrows at either side, while an event is carried.
            EditArrows(scope)
        }
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
    val onEventDelete: ((CalendarEvent) -> Unit)?,
    val announcer: Announcer,
    /** Whether the pointer is a finger: an edit then starts with a long press, and a quick swipe still pages. */
    val touch: Boolean,
    val strings: Strings,
    /** The focus of the grid the keyboard's cursor moves through. */
    val cursorInteractions: MutableInteractionSource,
    /** Whether that grid has the keyboard's focus, and so its cursor is drawn. */
    val cursorShown: Boolean,
    /** Whether there is an overlay host to open menus in. */
    val menus: Boolean,
    val eventMenu: (@Composable EventMenuScope.(CalendarEvent) -> Unit)?,
    val emptyMenu: (@Composable EmptyMenuScope.(LocalDateTime, LocalDateTime) -> Unit)?,
    val daysStyle: CalendarDaysStyle = CalendarDaysStyle.Hours,
    /** Whether timed events are shown at all: without them, a day on its own is stacked too. */
    val showsTimed: Boolean = true,
) {
    /** Whether [event] can be carried. */
    fun movable(event: CalendarEvent): Boolean = enabled && onEventMove != null && event.editable

    /** Whether [event]'s edges can be dragged. */
    fun resizable(event: CalendarEvent): Boolean = enabled && onEventResize != null && event.editable

    /** Whether [event] can be deleted. */
    fun deletable(event: CalendarEvent): Boolean = enabled && onEventDelete != null && event.editable

    /** Whether the menu for [event] — null, for empty time — would have anything in it. */
    fun hasMenu(event: CalendarEvent?, edges: Boolean): Boolean = when {
        !menus || !enabled -> false
        event == null -> when (emptyMenu) {
            null -> false
            // The default's one action, where it has it; an app's own menu, always.
            EventCalendarDefaults.EmptyMenu -> onCreateRequest != null
            else -> true
        }
        else -> when (eventMenu) {
            null -> false
            EventCalendarDefaults.EventMenu -> deletable(event) || (edges && touch && resizable(event))
            else -> true
        }
    }

    /** Opens the menu for [event], or for the new event in hand, at [root] — if it has anything in it. */
    fun openMenu(root: Offset, event: CalendarEvent?, edges: Boolean) {
        if (!hasMenu(event, edges)) return
        edit.menu = EditMenu(root, event, edges).apply {
            start = edit.start
            end = edit.end
        }
    }

    /**
     * A day of the month opened, however it was: its number, anywhere else in
     * its box, or Enter on it — [onDateClick] where there is one, its day view
     * where there is not. One rule for all of it: the number opened the day
     * and the rest of the box called [onDateClick], so the same day opened on
     * one tap and not on the next.
     */
    fun openDay(date: LocalDate) {
        val click = onDateClick
        if (click != null) click(date) else state.show(date, CalendarView.Day)
    }

    /** Makes the new event [menu] was opened for, and reads out where. */
    fun create(menu: EditMenu) {
        val start = menu.start ?: return
        val end = menu.end ?: return
        if (edit.kind == EditKind.Create) edit.cancel()
        onCreateRequest?.invoke(start, end)
        announce(strings.newEvent, start, end)
    }

    /**
     * The carrying pointer let go. A finger held still and let go where it was
     * has its menu to choose from: the event goes back, the new event waits
     * under the menu. Anything else is put down.
     */
    fun letGo() {
        val stayed = edit.byHold && !edit.travelled
        edit.release()
        when {
            !stayed -> {
                edit.menu = null
                putDown()
            }
            // A new event's menu says what to do with it; one with no menu to say makes it.
            edit.kind == EditKind.Create -> if (edit.menu == null) putDown()
            else -> edit.cancel()
        }
    }

    /** Deletes [event], and reads out that it has gone. */
    fun delete(event: CalendarEvent) {
        if (edit.active == event.id) edit.active = null
        onEventDelete?.invoke(event)
        announcer.say(strings.eventDeleted(event.title))
    }

    /** Puts the edit in hand down, and reports and reads out what it did. */
    fun putDown() {
        edit.menu = null
        val done = edit.finish() ?: return
        val event = done.event
        // Nothing to report it to, and nothing to read out: it was not made.
        if (done.kind == EditKind.Create && onCreateRequest == null) return
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

    /**
     * An event's menu: "Adjust time" on a touchscreen and "Delete", each where
     * it applies. Opens only where one of them does. Pass it on to keep them
     * in a menu of the app's own, or call [EventMenuScope.adjustTime] and
     * [EventMenuScope.delete] among its items.
     */
    val EventMenu: @Composable EventMenuScope.(event: CalendarEvent) -> Unit = {
        adjustTime()
        delete()
    }

    /** Empty time's menu: "New event", where the calendar has `onCreateRequest`. */
    val EmptyMenu: @Composable EmptyMenuScope.(start: LocalDateTime, end: LocalDateTime) -> Unit = { _, _ ->
        newEvent()
    }

    /** Today, on the system's clock in its time zone. */
    fun currentDate(): LocalDate = DatePickerDefaults.currentDate()

    /**
     * The time now on the system's clock, kept current: it changes on each
     * minute, so the line across the hours moves and today turns over at
     * midnight without anything asking it to.
     */
    @Composable
    fun currentDateTime(): LocalDateTime {
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
     * @param views The views the switcher offers, in its order — of those the
     *   calendar offers, its `views`. One view, or none, and there is no
     *   switcher.
     * @param switcher How the views are offered: side by side, or in a menu
     *   from a button that names the one on show. See [CalendarSwitcher].
     */
    @Composable
    fun Header(
        state: EventCalendarState,
        modifier: Modifier = Modifier,
        views: List<CalendarView> = Views,
        switcher: CalendarSwitcher = CalendarSwitcher.Segmented,
    ) {
        CalendarHeader(state, modifier, views, switcher)
    }

    /**
     * Every view, in the order the header's switcher offers them: a day, three
     * days, a week, a month and a year. In a header narrower than a phone, the
     * switcher leaves out three days unless it is the view on show.
     */
    val Views: List<CalendarView> get() = DefaultViews

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
    // The range picker's own dwell: a rumble while held at the side, and a
    // threshold as the page turns.
    val hold = rememberHoldFeedback()
    val pages = rememberDetentTicker(FeedbackIntent.DragThreshold)
    return remember(steps, landing, pickedUp, hold, pages) { EventEdit(steps, landing, pickedUp, hold, pages) }
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
        CalendarView.ThreeDays -> strings.threeDays
        CalendarView.Schedule -> strings.schedule
    }
}

/** The title of the period [state] shows: a day's date, a week's months, a month, a year. */
internal fun periodTitle(state: EventCalendarState, formats: LocaleFormats, timeRange: (String, String) -> String): String {
    val dates = state.visibleDates
    return when (state.view) {
        // Long rather than full: the weekday is under it, over the day's hours,
        // and the full date did not fit a phone's header beside its arrows.
        CalendarView.Day -> formats.date(state.date, DateStyle.Long)
        CalendarView.Week, CalendarView.ThreeDays -> {
            val first = formats.monthAndYear(dates.start)
            val last = formats.monthAndYear(dates.endInclusive)
            if (first == last) first else timeRange(first, last)
        }
        CalendarView.Month, CalendarView.Schedule -> formats.monthAndYear(state.date)
        CalendarView.Year -> formats.digits(state.date.year.toLong())
    }
}

@Composable
private fun CalendarHeader(state: EventCalendarState, modifier: Modifier, asked: List<CalendarView>, asStyle: CalendarSwitcher) {
    // Only what the calendar offers, whatever the header was asked for.
    val views = asked.filter { it in state.allowedViews }
    val strings = Theme.strings
    val formats = Theme.formats
    val (back, on) = when (state.view) {
        CalendarView.Day -> strings.previousDay to strings.nextDay
        CalendarView.Week -> if (state.weekCount == 1) strings.previousWeek to strings.nextWeek else strings.previousDays to strings.nextDays
        CalendarView.Month, CalendarView.Schedule -> strings.previousMonth to strings.nextMonth
        CalendarView.Year -> strings.previousYear to strings.nextYear
        CalendarView.ThreeDays -> strings.previousDays to strings.nextDays
    }
    val title = periodTitle(state, formats, strings.timeRange)
    val names = views.associateWith { viewName(it) }

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val oneRow = constraints.hasBoundedWidth && maxWidth >= HeaderOneRowWidth
        // Too narrow for every view side by side — six of them on a phone —
        // the views are offered in a menu instead. Squeezed, a segmented
        // control stacks its segments in a column; it used to leave three days
        // out to fit five, and a sixth does not fit at all.
        // A menu needs an overlay host to open in; a calendar without one
        // keeps its segments, as its other menus go without.
        val canMenu = LocalOverlayHostOrNull.current != null
        val style = when {
            !canMenu -> CalendarSwitcher.Segmented
            asStyle == CalendarSwitcher.Segmented && constraints.hasBoundedWidth && maxWidth < MinSegmentWidth * views.size ->
                CalendarSwitcher.Dropdown
            else -> asStyle
        }
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
            if (views.size > 1 && style == CalendarSwitcher.Dropdown) {
                ViewMenu(state, views, names)
            } else if (views.size > 1) {
                SegmentedControl(
                    value = state.view,
                    options = views,
                    onValueChange = { view -> state.show(view) },
                    optionLabel = { view -> names.getValue(view) },
                    modifier = switcherModifier,
                )
            }
        }
        // A menu's button is narrow enough to sit beside the arrows on a
        // phone — but not on a narrower box, where it left the title none.
        if (style == CalendarSwitcher.Dropdown && (!constraints.hasBoundedWidth || maxWidth >= DropdownOneRowWidth)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
                modifier = Modifier.padding(bottom = Theme.spacing.sm),
            ) {
                titleText(Modifier.weight(1f))
                arrows()
                switcher(Modifier)
            }
        } else if (oneRow) {
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
                switcher(if (style == CalendarSwitcher.Dropdown) Modifier else Modifier.fillMaxWidth())
            }
        }
    }
}

/**
 * The views as a button naming the one on show, which opens a menu of them with
 * that one ticked: [CalendarSwitcher.Dropdown].
 */
@Composable
private fun ViewMenu(state: EventCalendarState, views: List<CalendarView>, names: Map<CalendarView, String>) {
    var open by remember { mutableStateOf(false) }
    val strings = Theme.strings
    Box {
        Button(
            onClick = { open = true },
            variant = ButtonVariant.Secondary,
            size = ButtonSize.Small,
            // Read as the view on show, and what pressing it does.
            modifier = Modifier.semantics { onClick(label = strings.chooseView) { open = true; true } },
        ) {
            +names.getValue(state.view)
            +SystemIcons.ChevronDown
        }
        DropdownMenu(visible = open, onDismissRequest = { open = false }, alignment = OverlayAlignment.End) {
            for (view in views) {
                item(label = names.getValue(view), selected = view == state.view) { state.show(view) }
            }
        }
    }
}

/** Wide enough for the title, the arrows and the switcher on one line. */
private val HeaderOneRowWidth = 760.dp

/** The switcher's width beside the title: six short words with room round them. */
private val HeaderSwitcherWidth = 480.dp

/** Wide enough for the title, the arrows and a menu's button on one line. */
private val DropdownOneRowWidth = 380.dp

/** The least a segment of the switcher takes, its word and room round it: narrower than this for every view, a menu instead. */
private val MinSegmentWidth = 72.dp

private val DefaultViews = listOf(CalendarView.Day, CalendarView.ThreeDays, CalendarView.Week, CalendarView.Month, CalendarView.Year, CalendarView.Schedule)

@Composable
private fun DefaultEventContent(event: CalendarEvent, presentation: EventPresentation) {
    val formats = Theme.formats
    val strings = Theme.strings
    val type = Theme.typography
    when (presentation) {
        EventPresentation.Block -> {
            // The title as many lines as the block is tall, so a half-hour
            // block does not cut its second line through the middle, and a
            // narrow one wraps a long title — a word broken where it must be
            // — rather than give up all but three letters of it to "…".
            val vertical = Theme.spacing.xxs
            val titleLine = with(LocalDensity.current) { type.labelMedium.lineHeight.toDp() }
            val height = LocalBlockHeight.current
            // Shorter than a title: a quarter-hour, drawn as tall as its time.
            // Its title in the small type, in the middle of what there is.
            if (height != null && height < titleLine + vertical * 2) {
                val smallLine = with(LocalDensity.current) { type.labelSmall.lineHeight.toDp() }
                val small = calendarItemStyle()
                WholeLines(vertical = ((height - smallLine) / 2).coerceIn(0.dp, vertical), inset = Theme.spacing.xs, narrowInset = Theme.spacing.xxs) {
                    LeadBefore(event.icon?.let { { ItemIcon(it, small) } }, smallLine, minTitlePx(small, LocalDensity.current), Theme.spacing.xxs) {
                        FadingText(event.title, small)
                    }
                }
                return
            }
            val titleLines = if (height != null && titleLine > 0.dp) {
                floor((height - vertical * 2) / titleLine).toInt().coerceAtLeast(1)
            } else {
                Int.MAX_VALUE
            }
            WholeLines(vertical = vertical, inset = Theme.spacing.xs, narrowInset = Theme.spacing.xxs) {
                // One line fades out where it is cut, as a month's do: "DR-…" was
                // a letter given up to say there was more.
                // Its icon before the title's first line, the title's lines after it.
                LeadBefore(event.icon?.let { { ItemIcon(it, type.labelMedium) } }, titleLine, minTitlePx(type.labelMedium, LocalDensity.current), Theme.spacing.xxs) {
                    if (titleLines == 1) {
                        FadingText(event.title, type.labelMedium)
                    } else {
                        Text(event.title, style = type.labelMedium, maxLines = titleLines, overflow = TextOverflow.Ellipsis)
                    }
                }
                FadingText(strings.timeRange(formats.time(event.start.time), formats.time(event.end.time)), type.labelSmall)
                event.location?.let { FadingText(it, type.labelSmall) }
            }
        }
        EventPresentation.Bar -> EventBar(event, LocalItemLines.current)
        EventPresentation.Line -> EventLine(event, LocalItemLines.current)
    }
}

/**
 * How tall the block being drawn is, for its default content to fit its title
 * to: known to the day's column as it composes its events, so the content
 * need not wait for layout to find out.
 */
internal val LocalBlockHeight = compositionLocalOf<Dp?> { null }

/**
 * How many lines a month's event has room for: 1 at a whole month to the
 * screen, up to 3 zoomed in, for its title, its time and where it is.
 */
internal val LocalItemLines = compositionLocalOf { 1 }

/**
 * Lines one under another, as many as fit whole: the first always, the rest
 * only where all of each is inside the height — never one cut in half. Inset
 * by [inset] either side, or by [narrowInset] in a block narrower than
 * [NarrowItemWidth]: a week's narrow column keeps its few letters for the
 * title, not for the margin.
 */
@Composable
private fun WholeLines(vertical: Dp, inset: Dp, narrowInset: Dp, content: @Composable () -> Unit) {
    Layout(content) { measurables, constraints ->
        val side = (if (constraints.maxWidth < NarrowItemWidth.roundToPx()) narrowInset else inset).roundToPx()
        val top = vertical.roundToPx()
        val room = (constraints.maxHeight - top * 2).coerceAtLeast(0)
        val lineConstraints = Constraints(maxWidth = (constraints.maxWidth - side * 2).coerceAtLeast(0))
        var used = 0
        val shown = ArrayList<Placeable>(measurables.size)
        for ((i, measurable) in measurables.withIndex()) {
            val line = measurable.measure(lineConstraints)
            if (i > 0 && used + line.height > room) break
            shown += line
            used += line.height
        }
        val width = constraints.constrainWidth((shown.maxOfOrNull { it.width } ?: 0) + side * 2)
        layout(width, constraints.constrainHeight(used + top * 2)) {
            var y = top
            for (line in shown) {
                line.placeRelative(side, y)
                y += line.height
            }
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
            // Never while an edit is in hand: a finger carrying an event across
            // the days is not a swipe, however sideways it goes.
            accepts = { at ->
                val coordinates = placed.coordinates
                !edit.live && (isTouch || coordinates == null || !coordinates.isAttached || !edit.draggableAt(coordinates.localToRoot(at)))
            },
            // On a touchscreen, anywhere: a finger can hold an event, or empty
            // time, or a day, for its menu, and the wander of a held finger is
            // not taken for a swipe — only a real one, past the slop.
            patientAt = { isTouch },
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
    // A new pager, a cut, for another view — or the same one counted afresh:
    // three days, or several weeks, from another day.
    val periods = remember(view, state.periods) { PeriodPager(state.period) }
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

/**
 * How far beside an edge with handles a finger still takes it: outside the
 * event, a fingertip's half.
 */
internal val HandleReach = 16.dp

/**
 * How far into the event from an edge a finger takes that edge rather than
 * nothing: an event shorter than two of these is taken by the way the finger
 * sets off.
 */
internal val HandleZone = 20.dp

/** Where a page that has no place is put: well off the side, not drawn. */
private const val OffStage = 4

/**
 * The calendar's own hold on a carry: whichever node took the pointer up — an
 * event, its edge, a day's empty time — the pointer is followed from here,
 * above the pages, until it lets go. A period paged in while it is carried is
 * a new page, and the one the carry started on goes, with the node that took
 * hold in it: followed from there, the carry went with it.
 *
 * Seen first, before anything under it: what it follows is the carry's, so a
 * scroll or a pager under it never sees the pointer move. And a press away from
 * the event whose handles are showing takes the handles away.
 */
@Composable
private fun Modifier.carriesEdits(scope: CalendarScope): Modifier {
    val edit = scope.edit
    val placed = remember { PlacedAt() }
    val current by rememberUpdatedState(scope)
    return onGloballyPositioned { placed.coordinates = it }.pointerInput(edit) {
        val slop = io.kontour.ui.interaction.PickUpHoldSlop.toPx()
        val reach = HandleReach.toPx()
        val zone = HandleZone.toPx()
        // A press on or beside the handles, until it has gone far enough to say which.
        var claim: HandleClaim? = null
        // A press that took the handles away, and does nothing else: let go
        // where it went down, it is not also a tap on the day or the time
        // under it. A scroll or a swipe it sets off is still the scroll's.
        var dismissing: PointerId? = null
        var dismissedAt = Offset.Zero
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val coordinates = placed.coordinates?.takeIf { it.isAttached } ?: continue
                if (event.type == PointerEventType.Press && edit.active != null) {
                    val pressed = event.changes.firstOrNull { it.pressed && !it.previousPressed }
                    if (pressed != null) {
                        val root = coordinates.localToRoot(pressed.position)
                        if (!edit.onActive(root, reach)) {
                            edit.active = null
                            // Only on the days: a press on the header — its arrows, the
                            // view switcher — takes the handles away and is still pressed.
                            if (edit.inDays(root)) {
                                dismissing = pressed.id
                                dismissedAt = pressed.position
                            }
                        } else if (current.touch && edit.pointer == null && claim == null) {
                            claim = edit.claimHandle(pressed.id, root, reach, zone)
                        }
                    }
                }
                dismissing?.let { id ->
                    val change = event.changes.firstOrNull { it.id == id }
                    when {
                        change == null -> dismissing = null
                        (change.position - dismissedAt).getDistance() > viewConfiguration.touchSlop -> dismissing = null
                        !change.pressed -> {
                            change.consume()
                            dismissing = null
                        }
                    }
                }
                claim?.let { held ->
                    val change = event.changes.firstOrNull { it.id == held.pointer }
                    when {
                        // Gone, or taken by a hold on the event: its own carry.
                        change == null || edit.pointer != null || edit.active == null -> claim = null
                        // A tap: the event's own, as it was.
                        !change.pressed -> claim = null
                        else -> {
                            val root = coordinates.localToRoot(change.position)
                            val moved = root - held.down
                            // Left alone until it has gone a slop, so a finger held still
                            // still holds the event — for its menu, or to carry it — and
                            // taken then, before the hours or the pages see it set off.
                            if (moved.getDistance() > viewConfiguration.touchSlop) {
                                change.consume()
                                claim = null
                                val along = if (held.horizontal) moved.x else moved.y
                                val atStart = held.startFor(along)
                                val edge = if (atStart == true) held.start else held.end
                                if (atStart != null && edge != null) {
                                    val fromEdge = if (held.horizontal) Offset(held.down.x - edge, 0f) else Offset(0f, held.down.y - edge)
                                    edit.beginResize(held.event, atStart, held.down, byDays = held.byDays, top = held.top, fromEdge = fromEdge)
                                    if (edit.live) {
                                        edit.take(change.id, held.down, hold = false)
                                        edit.move(root)
                                    }
                                }
                            }
                        }
                    }
                }
                val id = edit.pointer ?: continue
                val change = event.changes.firstOrNull { it.id == id }
                if (change == null) {
                    edit.release()
                    edit.cancel()
                    continue
                }
                if (!change.pressed) {
                    change.consume()
                    current.letGo()
                    continue
                }
                if (change.positionChanged()) {
                    change.consume()
                    edit.carry(coordinates.localToRoot(change.position), slop)
                }
            }
        }
    }
}

/**
 * Follows a carry for a list of events that is not inside the calendar's days —
 * a month's day list, open over them — as [carriesEdits] follows one for the
 * days: an event taken up from the list is pressed in the list, and every move
 * of that pointer goes to the list, not to the days under it, until it lets go.
 * The list is hidden while it follows, not closed: closed, it would take the
 * pointer with it.
 */
@Composable
internal fun Modifier.followsCarry(scope: CalendarScope): Modifier {
    val edit = scope.edit
    val placed = remember { PlacedAt() }
    val current by rememberUpdatedState(scope)
    return onGloballyPositioned { placed.coordinates = it }.pointerInput(edit) {
        val slop = io.kontour.ui.interaction.PickUpHoldSlop.toPx()
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val coordinates = placed.coordinates?.takeIf { it.isAttached } ?: continue
                val id = edit.pointer ?: continue
                // A carry pressed somewhere else is the days' to follow.
                val change = event.changes.firstOrNull { it.id == id } ?: continue
                if (!change.pressed) {
                    change.consume()
                    current.letGo()
                    continue
                }
                if (change.positionChanged()) {
                    change.consume()
                    edit.carry(coordinates.localToRoot(change.position), slop)
                }
            }
        }
    }
}

/**
 * The menu a hold or a secondary click opened: for empty time, a new event
 * where the edit in hand proposes it; for an event, its edges to adjust — a
 * block's in the hours, a bar's across its days, on a touchscreen, where they
 * are otherwise hidden — and its deletion.
 */
@Composable
private fun EditMenus(scope: CalendarScope) {
    val edit = scope.edit
    val menu = edit.menu
    // Kept for the exit: the menu fades out from where it was.
    var last by remember { mutableStateOf<EditMenu?>(null) }
    if (menu != null) last = menu
    val shown = menu ?: last ?: return
    val strings = scope.strings
    AnchoredDropdownMenu(
        visible = menu != null,
        anchor = Rect(shown.at, shown.at),
        onDismissRequest = {
            edit.menu = null
            // A new event no one chose to make.
            if (edit.kind == EditKind.Create && edit.pointer == null) edit.cancel()
        },
    ) {
        val event = shown.event
        if (event == null) {
            val start = shown.start
            val end = shown.end
            val build = scope.emptyMenu
            if (build != null && start != null && end != null) EmptyMenuScopeImpl(this, scope, shown).build(start, end)
        } else {
            scope.eventMenu?.let { build -> EventMenuScopeImpl(this, scope, shown, event).build(event) }
        }
    }
}

/**
 * The menu a finger held on an event, or a secondary click on it, opens: a
 * [MenuScope], with the calendar's own actions on the event beside an app's.
 * See `eventMenu` on [EventCalendar].
 */
@Stable
@LayoutScopeMarker
interface EventMenuScope : MenuScope {

    /**
     * "Adjust time": the event's edges given handles, to drag its start and
     * its end by, until a press anywhere else. Only where it applies — on a
     * touchscreen, a mouse having the edges already, and on a block or a bar
     * the calendar can resize, not a month's line.
     */
    @Composable
    fun adjustTime()

    /** "Delete", in the danger tone: `onEventDelete` for the event. Only where it can be deleted. */
    @Composable
    fun delete()
}

/**
 * The menu a finger held on empty time, or a secondary click there, opens: a
 * [MenuScope], with the calendar's own new event beside an app's actions. See
 * `emptyMenu` on [EventCalendar].
 */
@Stable
@LayoutScopeMarker
interface EmptyMenuScope : MenuScope {

    /** "New event": `onCreateRequest` for the time the menu was opened with. Only where there is one. */
    @Composable
    fun newEvent()
}

private class EventMenuScopeImpl(
    menu: MenuScope,
    private val scope: CalendarScope,
    private val shown: EditMenu,
    private val event: CalendarEvent,
) : EventMenuScope, MenuScope by menu {

    @Composable
    override fun adjustTime() {
        if (!shown.edges || !scope.touch || !scope.resizable(event)) return
        item(scope.strings.adjustTime, onClick = {
            scope.edit.menu = null
            scope.edit.active = event.id
        })
    }

    @Composable
    override fun delete() {
        if (!scope.deletable(event)) return
        item(scope.strings.deleteEvent, destructive = true, onClick = {
            scope.edit.menu = null
            scope.delete(event)
        })
    }
}

private class EmptyMenuScopeImpl(
    menu: MenuScope,
    private val scope: CalendarScope,
    private val shown: EditMenu,
) : EmptyMenuScope, MenuScope by menu {

    @Composable
    override fun newEvent() {
        if (scope.onCreateRequest == null) return
        item(scope.strings.newEvent, onClick = { scope.create(shown) })
    }
}
