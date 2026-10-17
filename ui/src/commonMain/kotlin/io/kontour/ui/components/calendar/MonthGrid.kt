package io.kontour.ui.components.calendar

import io.kontour.ui.theme.clipProxy
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.selection.SliderDefaults
import io.kontour.ui.foundation.Text
import io.kontour.ui.interaction.PickUpAxis
import io.kontour.ui.interaction.detectPickUp
import io.kontour.ui.interaction.pressable
import io.kontour.ui.locale.DateStyle
import io.kontour.ui.locale.ListStyle
import io.kontour.ui.locale.NameWidth
import io.kontour.ui.overlay.LocalOverlayHostOrNull
import io.kontour.ui.overlay.OverlayAlignment
import io.kontour.ui.overlay.OverlaySide
import io.kontour.ui.overlay.PopoverDefaults
import io.kontour.ui.overlay.PopoverImpl
import io.kontour.ui.overlay.ScrimStyle
import io.kontour.ui.theme.Theme
import io.kontour.ui.theme.Tone
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.plus

/**
 * A month of day boxes, a week to a row, scrolling up and down through the
 * weeks either side as Apple's calendar does — every row the same height, so
 * the weeks sit under each other and a day is under its weekday all the way
 * down — and settling with a week at the top. The month most on screen is the
 * one the calendar is about; the days of the others are quieter. Each box
 * holds as many of its events as fit, and "+N" for the rest.
 *
 * Carried near the top or bottom, an event scrolls the weeks with it, as the
 * hours do: there is no page to dwell at the side for.
 */
@Composable
internal fun MonthView(scope: CalendarScope) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val state = scope.state
    val periods = state.periods
    val density = LocalDensity.current
    val strings = Theme.strings
    // Made with the list, inside; reached from the keys and actions round it.
    val zoomer = remember { arrayOfNulls<MonthZoom>(1) }
    // A key's or a screen reader's zoom is about the state's day, kept in view.
    val dayAt = remember { arrayOf<() -> Float>({ 0f }) }
    Column(Modifier.fillMaxWidth()) {
        WeekdayRow(scope)
        val frame = Theme.shapes.container
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                // In a box, cornered as the library's containers are and edged
                // with the grid's own line: the days' lines meet an edge rather
                // than stopping in the open.
                .clip(frame.clipProxy())
                .border(Theme.sizing.borderWidth, gridLine(), frame)
                // Ctrl, or Cmd, and plus or minus: a week fewer or more, as a
                // browser zooms its page. Before the cursor, which leaves keys
                // held with Ctrl to whatever else wants them.
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown || !(event.isCtrlPressed || event.isMetaPressed)) return@onPreviewKeyEvent false
                    val zoom = zoomer[0] ?: return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.Equals, Key.Plus, Key.NumPadAdd -> zoom.step(-1, dayAt[0]())
                        Key.Minus, Key.NumPadSubtract -> zoom.step(1, dayAt[0]())
                        else -> return@onPreviewKeyEvent false
                    }
                    true
                }
                .semantics {
                    customActions = listOf(
                        CustomAccessibilityAction(strings.zoomIn) { zoomer[0]?.step(-1, dayAt[0]()) != null },
                        CustomAccessibilityAction(strings.zoomOut) { zoomer[0]?.step(1, dayAt[0]()) != null },
                    )
                }
                .calendarCursor(scope, description = scope.dayDescription(state.date)) { key -> scope.monthKey(key, rtl) },
        ) {
            // Bounded, the rows share the height; in a scrolling page, a row
            // is as tall as a date and three events, and the box six of them.
            val viewport = if (constraints.hasBoundedHeight) maxHeight else monthRowMinHeight() * MonthRowsInView
            val viewportPx = with(density) { viewport.toPx() }
            val dateLabel = dateLabelHeight()
            val dateLabelPx = with(density) { dateLabel.toPx() }
            val linePx = with(density) { Theme.typography.labelSmall.lineHeight.toPx() }
            val padPx = with(density) { Theme.spacing.xxs.toPx() }
            // Zoomed in no further than three weeks, nor out past six or past
            // a row with room for its date and one event.
            val most = minOf(MonthRowsInView.toFloat(), floor(viewportPx / (dateLabelPx + linePx + padPx * 2))).coerceAtLeast(MinMonthRows.toFloat())
            // **The zoom is read in layout, and in what is derived from it, never
            // here.** It changes on every frame of a pinch, and read here it
            // recomposed the month, every week on screen and every event in
            // them, each frame: the pinch's stutter. Now a frame lays the weeks
            // out again, and what is composed changes only when a whole row
            // more fits, or an event's lines change.
            val rows = { state.monthRows.coerceIn(MinMonthRows.toFloat(), most) }
            // A date and an event, however little room the month is given.
            val leastRowPx = dateLabelPx + linePx + padPx * 2
            val rowPx = { monthRowPx(viewportPx, rows(), leastRowPx) }
            val zooming = remember { mutableStateOf(false) }
            val inView by remember(viewportPx, most, leastRowPx) { derivedStateOf { wholeRows(viewportPx, rowPx()) } }
            // **An event's height follows the pinch**, read in layout as the rows
            // are: part of the way from one resting height to the next as the
            // rows are. It used to be held until the fingers let go — an event
            // given its next line at once grew every event together — and then
            // jumped. What is composed changes only as a whole line, or a whole
            // event more, fits.
            val itemPx = { monthItemPx(rows(), viewportPx, leastRowPx, dateLabelPx, linePx, padPx, MinMonthRows.toFloat(), most) }
            val lines by remember(viewportPx, most, leastRowPx, dateLabelPx, linePx, padPx) {
                derivedStateOf { itemContentLines(itemPx(), linePx, padPx) }
            }
            val item = itemHeight(lines)
            // How many events a day has room for: a whole one more as a row grows.
            val maxRows by remember(viewportPx, most, leastRowPx, dateLabelPx, linePx, padPx) {
                derivedStateOf { itemPx().let { if (it > 0f) floor((rowPx() - dateLabelPx) / it).toInt().coerceAtLeast(0) else 0 } }
            }
            val list = rememberMonthList(scope, inView, rowPx, zooming)
            val snap = rememberWeekSnap(list)
            val placed = remember { PlacedAt() }
            val coroutines = rememberCoroutineScope()
            val zoom = remember(list, state) { MonthZoom(state, list, coroutines, zooming) }
            zoom.viewportPx = viewportPx
            zoom.reduceMotion = Theme.motion.reduceMotion
            zoom.most = most
            zoom.leastRowPx = leastRowPx
            zoomer[0] = zoom
            dayAt[0] = {
                val row = periods.rowOf(state.date)
                list.layoutInfo.visibleItemsInfo.firstOrNull { it.index == row }?.let { it.offset + it.size / 2f } ?: (viewportPx / 2f)
            }
            // Each week's events, found once for as long as the events are the same.
            val byWeek = remember(scope.events, periods) { HashMap<LocalDate, List<CalendarEvent>>() }
            // Each bar's row, the same in every week it is in, so one running
            // on into the next week is on the same line there: see [barRows].
            val rowsOf = remember(scope.events) { barRows(scope.events, lines = true) }
            val gap = Theme.spacing.xxs / 2
            val cell = maxWidth / DaysPerWeek
            val edit = scope.edit
            // A day's "+N" opens its list here, over the month, rather than in
            // its week: carried out of the list, an event is followed by the
            // list, and a week scrolled away under the carry took its list —
            // and the carry — with it.
            var listing by remember { mutableStateOf<DayListing?>(null) }
            val canList = LocalOverlayHostOrNull.current != null
            val onList: ((LocalDate, Rect) -> Unit)? = if (canList) remember { { day, anchor -> listing = DayListing(day, anchor) } } else null
            Box(
                Modifier
                    .fillMaxWidth()
                    // Whole rows, so no sliver of a week shows at the foot — but
                    // while a pinch is under way, the whole box.
                    .layout { measurable, constraints ->
                        val r = rows()
                        val whole = r == floor(r) && !zooming.value
                        val height = (if (whole) rowPx() * r else viewportPx).roundToInt()
                            .coerceAtMost(constraints.maxHeight)
                        val placeable = measurable.measure(Constraints.fixed(constraints.maxWidth, height))
                        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                    }
                    .onGloballyPositioned { placed.coordinates = it }
                    .pinchesRows(zoom) { !edit.live }
                    .wheelZoomsRows(zoom),
            ) {
                CompositionLocalProvider(LocalItemLines provides lines) {
                    LazyColumn(
                        state = list,
                        flingBehavior = snap,
                        userScrollEnabled = scope.enabled,
                        modifier = Modifier.fillMaxSize().tellsWheelFromFinger(snap),
                    ) {
                        items(WeekRows, key = { it }) { index ->
                            val weekStart = periods.weekOfRow(index)
                            val events = byWeek.getOrPut(weekStart) {
                                scope.eventsBetween(weekStart, weekStart.plus(DaysPerWeek - 1, DateTimeUnit.DAY))
                            }
                            MonthWeek(scope, weekStart, events, rowsOf, item, itemPx, maxRows, cell, onList, Modifier.fillMaxWidth().weekRow(rowPx))
                        }
                    }
                    // Where a carried event would land, over the weeks on screen:
                    // from one to the next is travel down.
                    val ghostRows = remember(list, periods) { ListRows(list, periods) }
                    ghostRows.rowPx = rowPx
                    val first by remember(list, periods) { derivedStateOf { periods.weekOfRow(list.firstVisibleItemIndex) } }
                    DaysGhostLayer(
                        scope,
                        first,
                        weeks = ceil(most).toInt() + 1,
                        days = DaysPerWeek,
                        slot = dateLabel + gap / 2,
                        item = item,
                        gap = gap,
                        rows = ghostRows,
                    )
                }
                DayList(scope, listing, placed, item) { listing = null }
            }
            EditScroll(scope, placed, list, byDays = true)
        }
    }
}

/** How many weeks the month shows at once: a whole month, however it falls. */
internal const val MonthRowsInView = 6

/** A scrolling month's weeks for the copy a carry draws, read as they are laid out. */
private class ListRows(private val list: LazyListState, private val periods: CalendarPeriods) : GhostRows {
    var rowPx: () -> Float = { 0f }
    override fun start(): LocalDate = periods.weekOfRow(list.layoutInfo.visibleItemsInfo.firstOrNull()?.index ?: list.firstVisibleItemIndex)
    override fun top(): Float = (list.layoutInfo.visibleItemsInfo.firstOrNull()?.offset ?: 0).toFloat()
    override fun height(): Float = rowPx()
}

/** A week's row, [rowPx] tall, read as it is laid out: a pinch's frame moves it without composing it. */
private fun Modifier.weekRow(rowPx: () -> Float): Modifier = layout { measurable, constraints ->
    val height = rowPx().roundToInt().coerceAtLeast(0)
    val placeable = measurable.measure(Constraints.fixed(constraints.maxWidth, height))
    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
}

/**
 * A month's event, or its day's "+N", in [row] of its day's events under [top]:
 * an event's height, less [gap], read as it is laid out, so a pinch's frame
 * moves and sizes it without composing it.
 */
private fun Modifier.eventRow(top: Dp, row: Int, gap: Dp, itemPx: () -> Float): Modifier = layout { measurable, constraints ->
    val item = itemPx()
    val y = (top.toPx() + item * row).roundToInt()
    val height = (item - gap.toPx()).roundToInt().coerceAtLeast(0)
    val placeable = measurable.measure(constraints.copy(minHeight = height, maxHeight = height))
    layout(placeable.width, y + placeable.height) { placeable.place(0, y) }
}

/** The weekday names over the month's columns: abbreviated, or a letter where they do not fit. */
@Composable
private fun WeekdayRow(scope: CalendarScope) {
    val periods = scope.state.periods
    val weekStart = periods.weekStart(scope.state.date)
    val widest = widestWeekdayName(scope.formats, Theme.typography.labelSmall) + Theme.spacing.xxs
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val narrow = !abbreviatesWeekdays(maxWidth / DaysPerWeek, widest, WeekdayAbbreviatedWidth)
        Row(Modifier.fillMaxWidth().padding(vertical = Theme.spacing.xxs).clearAndSetSemantics { }) {
            for (column in 0 until DaysPerWeek) {
                val day = dayInWeek(weekStart, column).dayOfWeek
                Text(
                    scope.formats.weekdayName(day, if (narrow) NameWidth.Narrow else NameWidth.Abbreviated),
                    style = Theme.typography.labelSmall,
                    colour = quietInk(),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * Below this a column has room for a weekday's letter, not its abbreviation —
 * or below the widest abbreviation, where that is wider: see `widestWeekdayName`.
 */
private val WeekdayAbbreviatedWidth = 40.dp

/** A week row's height in a calendar given no height of its own: its date and three events. */
@Composable
private fun monthRowMinHeight(): Dp = dateLabelHeight() + itemHeight() * MinRowsInPage

private const val MinRowsInPage = 3

/** A month's event's height, [lines] of the small label's tall and a margin above and below. */
@Composable
private fun itemHeight(lines: Int = 1): Dp = with(LocalDensity.current) { Theme.typography.labelSmall.lineHeight.toDp() } * lines + Theme.spacing.xxs * 2

@Composable
private fun dateLabelHeight(): Dp = with(LocalDensity.current) { Theme.typography.labelMedium.lineHeight.toDp() } + Theme.spacing.xs * 2

@Composable
private fun MonthWeek(
    scope: CalendarScope,
    weekStart: LocalDate,
    weekEvents: List<CalendarEvent>,
    /** Each bar's row, the same in every week: see [barRows]. */
    barRows: Map<CalendarEvent, Int>,
    item: Dp,
    /** An event's height as the rows are laid out, which follows a pinch: see [monthItemPx]. */
    itemPx: () -> Float,
    /** How many events a day has room for under its date. */
    maxRows: Int,
    /** A day's width. */
    cell: Dp,
    /** Opens a day's list from its "+N", there: null where there is no host to show one in. */
    onList: ((LocalDate, Rect) -> Unit)?,
    modifier: Modifier,
) {
    val dateLabel = dateLabelHeight()
    val gap = Theme.spacing.xxs / 2
    val state = scope.state
    val cursorShown = scope.cursorShown
    val ringColour = Theme.colours.focusRing
    val ringWidth = Theme.sizing.focusRingWidth
    val placed = remember { PlacedAt() }
    // Not a `BoxWithConstraints`: its height changes on every frame of a pinch,
    // and one of those composes its content again for each.
    Box(
        modifier
            .onGloballyPositioned { placed.coordinates = it }
            .holdsDays(scope, placed, weekStart)
            .drawWithContent {
            drawContent()
            // The keyboard's day, over its events, so they do not hide it.
            val date = state.date
            if (!cursorShown) return@drawWithContent
            val column = (date.toEpochDays() - weekStart.toEpochDays()).toInt()
            if (column !in 0 until DaysPerWeek) return@drawWithContent
            val width = size.width / DaysPerWeek
            val left = if (layoutDirection == LayoutDirection.Rtl) size.width - width * (column + 1) else width * column
            drawCursorRing(Rect(left, 0f, left + width, size.height), ringColour, ringWidth.toPx())
        },
    ) {
        val pack = remember(weekEvents, weekStart, maxRows, barRows) { packWeek(weekItems(weekStart, weekEvents, lines = true), maxRows, rows = barRows) }
        Row(Modifier.fillMaxSize()) {
            for (column in 0 until DaysPerWeek) {
                val date = dayInWeek(weekStart, column)
                val count = weekEvents.count { it.coversDay(date) }
                // Whether it is in the month most on screen: only the boxes
                // whose answer changes are drawn again as the month does.
                val inMonth by remember(date) { derivedStateOf { state.date.year == date.year && state.date.month == date.month } }
                DayBox(
                    scope = scope,
                    date = date,
                    inMonth = inMonth,
                    count = count,
                    // Only where a row is left for it: a box too short for even
                    // one says how many it has to a screen reader, and no more.
                    more = if (pack.shownRows[column] < maxRows) pack.more[column] else 0,
                    moreRow = pack.shownRows[column],
                    itemHeight = item,
                    itemPx = itemPx,
                    dateLabel = dateLabel,
                    onList = onList,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
        }
        // Each day's place, for an event carried by whole days.
        Row(Modifier.fillMaxSize()) {
            for (column in 0 until DaysPerWeek) {
                DayTarget(scope, dayInWeek(weekStart, column), Modifier.weight(1f).fillMaxHeight())
            }
        }
        for (placement in pack.placed) {
            val placed = placement.item
            EventChip(
                event = placed.event,
                presentation = if (placed.bar) EventPresentation.Bar else EventPresentation.Line,
                scope = scope,
                clippedStart = placed.continuesBefore,
                clippedEnd = placed.continuesAfter,
                focusableOn = dayInWeek(weekStart, placed.from)..dayInWeek(weekStart, placed.to),
                modifier = Modifier
                    .barAcross(cell, placed.from, placed.to, placed.continuesBefore, placed.continuesAfter, gap)
                    .eventRow(dateLabel + gap / 2, placement.row, gap, itemPx),
            )
        }
    }
}

/**
 * A finger held in a box of days: the event it is beside — a month's line is
 * under half a fingertip tall — or else a new day, or days, drawn out; and a
 * mouse's secondary click, the days' menu. [placed] is where the box is, and
 * [key] what it shows, so the hold starts afresh for other days.
 */
@Composable
internal fun Modifier.holdsDays(scope: CalendarScope, placed: PlacedAt, key: Any): Modifier {
    val edit = scope.edit
    val isTouch by rememberUpdatedState(scope.touch)
    val creates = scope.enabled && scope.onCreateRequest != null
    // A day's menu, the calendar's "New event" or an app's own.
    val offers = scope.hasMenu(null, edges = false)
    val holds = creates || offers || (scope.enabled && scope.onEventMove != null)
    val nearReach = with(LocalDensity.current) { NearReach.toPx() }
    return this
        .then(
            if (holds) {
                Modifier.pointerInput(edit, key) {
                    var taking: PointerId? = null
                    detectPickUp(
                        axis = PickUpAxis.Free,
                        immediate = { false },
                        givesWay = true,
                        accepts = { at ->
                            val root = placed.coordinates?.takeIf { it.isAttached }?.localToRoot(at)
                            isTouch && (root == null || !edit.eventAt(root))
                        },
                        onPointer = { taking = it },
                        onPickUp = { at ->
                            val root = placed.coordinates?.takeIf { it.isAttached }?.localToRoot(at) ?: return@detectPickUp
                            val near = edit.eventNear(root, nearReach)
                            if (near != null && scope.movable(near.event)) {
                                scope.takeUp(near.event, root, near.byDays, near.coordinates.localToRoot(Offset.Zero).y, taking, hold = true, edges = near.edges)
                                return@detectPickUp
                            }
                            if (!creates && !offers) return@detectPickUp
                            edit.beginCreateDays(root)
                            if (!edit.live) return@detectPickUp
                            // Taken up to draw out days only where there are days to make.
                            if (creates) taking?.let { edit.take(it, root, hold = true) }
                            edit.pickedUp()
                            scope.openMenu(root, null, edges = false)
                        },
                        // The calendar follows the carry from here: see `carriesEdits`.
                        onCarry = { _, _ -> },
                        onPutDown = {},
                        onCancel = {},
                    )
                }
            } else {
                Modifier
            },
        )
        .then(
            if (offers) {
                Modifier.secondaryClick { at ->
                    val root = placed.coordinates?.takeIf { it.isAttached }?.localToRoot(at) ?: return@secondaryClick
                    if (edit.live) return@secondaryClick
                    edit.beginCreateDays(root)
                    scope.openMenu(root, null, edges = false)
                }
            } else {
                Modifier
            },
        )
}

/** Whether any of this event is on [date]. */
internal fun CalendarEvent.coversDay(date: LocalDate): Boolean = overlaps(date, date)

/**
 * Whether any of this event is between [from] and [to], both days included —
 * half-open, as every event is, except that one with no length is on the day
 * it is at.
 */
internal fun CalendarEvent.overlaps(from: LocalDate, to: LocalDate): Boolean {
    val rangeStart = from.atMidnight.epochMinute
    val rangeEnd = to.atMidnight.epochMinute + MinutesPerDay
    val a = start.epochMinute
    val b = end.epochMinute
    return if (a == b) a in rangeStart until rangeEnd else a < rangeEnd && b > rangeStart
}

@Composable
private fun DayBox(
    scope: CalendarScope,
    date: LocalDate,
    inMonth: Boolean,
    count: Int,
    more: Int,
    moreRow: Int,
    itemHeight: Dp,
    itemPx: () -> Float,
    dateLabel: Dp,
    onList: ((LocalDate, Rect) -> Unit)?,
    modifier: Modifier,
) {
    val formats = scope.formats
    val strings = Theme.strings
    val colours = Theme.colours
    val line = Theme.sizing.borderWidth
    val lineColour = gridLine()
    val isToday = date == scope.today
    val described = formats.list(
        listOfNotNull(formats.date(date, DateStyle.Full), if (count > 0) strings.eventCount(formats.quantity(count)) else null),
        ListStyle.Unit,
    )
    Box(
        modifier
            .then(if (inMonth) Modifier else Modifier.background(colours.surfaceSunken))
            .drawBehind {
                // The end and the foot of each box: the grid's lines, drawn once each.
                val width = line.toPx()
                val x = if (layoutDirection == LayoutDirection.Rtl) 0f else size.width - width
                drawLine(lineColour, Offset(x + width / 2, 0f), Offset(x + width / 2, size.height), width)
                drawLine(lineColour, Offset(0f, size.height - width / 2), Offset(size.width, size.height - width / 2), width)
            }
            // The whole box, as its number is: see `CalendarScope.openDay`. The
            // keyboard's way to a day is the grid's cursor, not a stop per day.
            .focusProperties { canFocus = false }
            .pressable(
                onClick = { scope.openDay(date) },
                shape = RectangleShape,
                enabled = scope.enabled,
                minimumTouchTarget = false,
            )
            .semantics { contentDescription = described },
    ) {
        // The date — today's ringed, as the date picker rings it — opening its
        // day as the rest of the box does, its press round the number.
        val number = dateLabel - Theme.spacing.xxs * 2
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .padding(Theme.spacing.xxs)
                .sizeIn(minWidth = number, minHeight = number)
                .todayRing(isToday)
                .focusProperties { canFocus = false }
                .pressable(
                    onClick = { scope.openDay(date) },
                    shape = Theme.shapes.capsule,
                    enabled = scope.enabled,
                    minimumTouchTarget = false,
                )
                .padding(horizontal = Theme.spacing.xxs),
        ) {
            Text(
                formats.integer(date.day),
                style = Theme.typography.labelMedium,
                colour = if (inMonth) colours.content else quietInk(),
                maxLines = 1,
                // A date is never broken over two lines: "28" as "2" was the
                // narrow box's first try.
                softWrap = false,
            )
        }
        if (more > 0) {
            // A row of the day's, as the events above it are.
            val gap = Theme.spacing.xxs / 2
            val chip = remember { PlacedAt() }
            Box(Modifier.padding(start = gap, end = gap)) {
                MoreChip(
                    count = more,
                    muted = !inMonth,
                    enabled = scope.enabled,
                    // The day's list where there is a host to show it in; the day itself where there is not.
                    onClick = {
                        val anchor = chip.coordinates?.takeIf { it.isAttached }?.boundsInRoot()
                        if (onList != null && anchor != null) onList(date, anchor) else scope.openDay(date)
                    },
                    modifier = Modifier
                        .eventRow(dateLabel + gap / 2, moreRow, gap, itemPx)
                        .fillMaxWidth()
                        .onGloballyPositioned { chip.coordinates = it }
                        // Reached by Tab with the day's events: from the cursor, on its day.
                        .focusProperties { canFocus = scope.state.date == date },
                )
            }
        }
    }
}

/** A day's list open from its "+N": which day, and the "+N" it came from, in the root. */
internal class DayListing(val date: LocalDate, val anchor: Rect)

/**
 * Every event of a day, from its "+N", in a popover pointing at it — each one
 * an event to open, and to carry out onto the month.
 *
 * Carried out, the list hides — at nothing, still there — and goes on
 * following the pointer that took the event up ([followsCarry]), since that
 * pointer is the list's and not the days'; put down, the list closes. Held
 * still on a touchscreen, for the event's menu, it stays.
 */
@Composable
private fun DayList(scope: CalendarScope, listing: DayListing?, month: PlacedAt, item: Dp, onDismiss: () -> Unit) {
    // Kept for the exit: the list fades out from where it was.
    var last by remember { mutableStateOf(listing) }
    if (listing != null) last = listing
    val shown = last ?: return
    val edit = scope.edit
    val formats = scope.formats
    val handing = listing != null && edit.live && edit.pointer != null && edit.menu == null
    var handed by remember { mutableStateOf(false) }
    if (handing) handed = true
    val dismiss by rememberUpdatedState(onDismiss)
    LaunchedEffect(handing) {
        if (!handing && handed) {
            handed = false
            dismiss()
        }
    }
    val date = shown.date
    val origin = month.coordinates?.takeIf { it.isAttached }?.localToRoot(Offset.Zero) ?: Offset.Zero
    // Over the "+N" it came from, where that was when it was pressed: what the
    // popover points at. Placed, not moved.
    Box(
        Modifier.layout { measurable, constraints ->
            val placeable = measurable.measure(
                Constraints.fixed(shown.anchor.width.roundToInt().coerceAtLeast(0), shown.anchor.height.roundToInt().coerceAtLeast(0)),
            )
            layout(constraints.maxWidth, constraints.maxHeight) {
                placeable.place((shown.anchor.left - origin.x).roundToInt(), (shown.anchor.top - origin.y).roundToInt())
            }
        },
    ) {
        PopoverImpl(
            visible = listing != null,
            onDismissRequest = onDismiss,
            modifier = Modifier,
            side = OverlaySide.Bottom,
            alignment = OverlayAlignment.Centre,
            scrim = ScrimStyle.Transparent,
            showArrow = true,
            maxWidth = PopoverDefaults.MaxWidth,
            dismissOnScroll = true,
            hidden = { handing },
            title = { +formats.date(date, DateStyle.Long) },
        ) {
            Column(Modifier.followsCarry(scope), verticalArrangement = Arrangement.spacedBy(Theme.spacing.xxs)) {
                for (event in scope.eventsBetween(date, date).filter { it.coversDay(date) }.sortedBy { it.start }) {
                    EventChip(
                        event = event,
                        presentation = if (event.allDay) EventPresentation.Bar else EventPresentation.Line,
                        scope = scope,
                        // Square on the side it runs on past this day.
                        clippedStart = event.start.date < date,
                        clippedEnd = lastDayOf(event.start, event.end) > date,
                        listedOn = date,
                        modifier = Modifier.fillMaxWidth().height(item),
                    )
                }
            }
        }
    }
}

/**
 * Where an event carried by whole days would land — or the new day, or days,
 * being drawn out — over [weeks] rows of [days] from [rowStart]: a lifted copy
 * as long as the event, a piece in each week it is in, on its days and leaning
 * toward the pointer by the detent's pull, as a date range's end leans between
 * its days, and hopping to the next day or week as the pointer crosses into it
 * — see [GhostHop], and the hours' ghost for why it is read live and hops
 * rather than springs. One for the page, so a week on is travel down rather than a
 * copy gone from one row and another come in the next. A cut under reduced
 * motion.
 *
 * **Shaped as the event is where it was taken from.** Square, and out to the
 * edge, at a side it runs on past — into the week before or after, or off the
 * page — and round, in by the gap, only where it starts or ends: see
 * [cutShape]. It was a capsule cut to the days on show whatever the event did
 * there, so taken up, a bar running on into the next week changed shape under
 * the finger.
 *
 * **The rest of it comes with it.** Where it runs on, a piece reaches on past
 * its row by [GhostReach] days, and the layer is cut at that side of the days:
 * carried sideways, what was past the edge comes in with it, its own end and
 * all, rather than the piece coming away from the edge it ran on past —
 * *"if we drag it one way, it needs to look like the rest of the event is
 * attached to the bar as we move it"*. Its title is where the event's own is,
 * at the start of what the row shows of it, and goes with it.
 *
 * @param slot How far down its row a new event's copy is: under the date.
 * @param item How tall a row's line of events is.
 */
@Composable
internal fun DaysGhostLayer(
    scope: CalendarScope,
    rowStart: LocalDate,
    weeks: Int,
    days: Int,
    slot: Dp,
    item: Dp,
    gap: Dp,
    line: Int? = null,
    rows: GhostRows? = null,
) {
    val edit = scope.edit
    val event = edit.event
    val start = edit.start
    val end = edit.end
    if (!edit.live || !edit.byDays || start == null || end == null) return
    val pageEnd = rowStart.plus(weeks * days - 1, DateTimeUnit.DAY)
    if (lastDayOf(start, end) < rowStart || start.date > pageEnd) return
    val still = Theme.motion.reduceMotion
    val pull = if (still) 0f else SliderDefaults.DetentPull
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val shown = remember(event, start, end) {
        CalendarEvent(DaysGhostId, event?.title ?: scope.strings.newEvent, start, end, tone = event?.tone ?: Tone.Accent)
    }
    // As composed, for which copies and their shapes; placed from the edit as
    // it is now, each by the week it is in.
    val composed = dayPieces(start, end, rowStart, weeks, days)
    val density = LocalDensity.current
    val slotPx = with(density) { slot.toPx() }
    val itemPx = with(density) { item.toPx() }
    val gapPx = with(density) { gap.toPx() }
    val hop = remember { GhostHop() }
    LaunchedEffect(hop) { hop.run() }
    // A day's width, as last laid out: for each copy's title to be as wide as the event's own.
    val dayWidth = remember { MeasuredCell() }

    Layout(
        // As the event is drawn where it was taken from: a timed event's line
        // in a month, its dot, time and title, rather than a bar with its title
        // alone — held, the line turned into a bar, and back into a line when
        // it was let go.
        content = {
            val presentation = if (event?.isTimed() == true) EventPresentation.Line else EventPresentation.Bar
            for (piece in composed) {
                key(piece.week) {
                    EventChip(
                        event = shown,
                        presentation = presentation,
                        scope = scope,
                        ghost = true,
                        clippedStart = !piece.startsHere,
                        clippedEnd = !piece.endsHere,
                        contentWidth = { (piece.shownRight(dayWidth.px, gapPx) - piece.shownLeft(dayWidth.px, gapPx)).roundToInt() },
                        contentAt = { (piece.shownLeft(dayWidth.px, gapPx) - piece.drawnLeft(dayWidth.px, gapPx)).roundToInt() },
                        modifier = Modifier.layoutId(piece.week),
                    )
                }
            }
        },
        modifier = Modifier.fillMaxSize().clipAcross(start = composed.any { it.from < it.shownFrom }, end = composed.any { it.to > it.shownTo }),
    ) { measurables, constraints ->
        val w = constraints.maxWidth
        val h = constraints.maxHeight
        val nowStart = edit.start
        val nowEnd = edit.end
        if (nowStart == null || nowEnd == null || w <= 0 || h <= 0 || weeks <= 0) return@Layout layout(w, h) {}
        val cell = w.toFloat() / days
        dayWidth.px = cell
        // A scrolling month's rows as they are laid out now, read here so the
        // first row and where it is are always the same frame's.
        val rowStart = rows?.start() ?: rowStart
        val rowHeight = rows?.height()?.takeIf { it > 0f } ?: (h.toFloat() / weeks)
        val rowsTop = rows?.top() ?: 0f
        // How far down its row the copy is: the line the event was on, or under the date.
        // Measured from the first row's top, which a scrolling month's is
        // above the layer's by however far it is scrolled into its week.
        val y = rowsTop + run {
            val inRow = edit.grabInRow
            if (line != null) {
                // A line of its own, kept free for it.
                line * itemPx + slotPx
            } else if (event == null || inRow == null) {
                slotPx
            } else {
                inRow.coerceIn(0f, (rowHeight - itemPx).coerceAtLeast(0f))
            }
        }
        // An end dragged: the bar where it is, but for that end, which
        // stretches toward the pointer and hops a day at a time.
        val kind = edit.kind
        if (kind == EditKind.ResizeStart || kind == EditKind.ResizeEnd) {
            val pieces = dayPieces(nowStart, nowEnd, rowStart, weeks, days)
            val atEnd = kind == EditKind.ResizeEnd
            val edgeDay = if (atEnd) lastDayOf(nowStart, nowEnd) else nowStart.date
            val edgeIndex = (edgeDay.toEpochDays() - rowStart.toEpochDays()).toInt()
            // The piece with the end in it, if this page shows it.
            val moving = if (atEnd) pieces.lastIndex else 0
            val onPage = pieces.isNotEmpty() && edgeIndex in 0 until weeks * days
            fun rectOf(piece: GhostPiece) = Rect(
                piece.drawnLeft(cell, gapPx),
                piece.row * rowHeight + y,
                piece.drawnRight(cell, gapPx),
                piece.row * rowHeight + y + itemPx - gapPx,
            )
            val stretched = if (!onPage) null else {
                val snapped = rectOf(pieces[moving])
                val lean = (edit.lean * pull).let { if (rtl) -it.x else it.x }
                val target = if (atEnd) snapped.copy(right = snapped.right + lean) else snapped.copy(left = snapped.left + lean)
                // Into another week, it is there: a hop across the month would not be an end stretching.
                hop.drawnAt(nowStart to nowEnd, target, still) { last, aim -> if (last.top != aim.top) Rect.Zero else null }
            }
            val placed = pieces.mapIndexedNotNull { i, piece ->
                val rect = if (i == moving && stretched != null) stretched else rectOf(piece)
                val measurable = measurables.firstOrNull { it.layoutId == piece.week } ?: return@mapIndexedNotNull null
                rect to measurable.measure(Constraints.fixed(rect.width.roundToInt().coerceAtLeast(0), rect.height.roundToInt().coerceAtLeast(0)))
            }
            return@Layout layout(w, h) {
                placed.forEach { (rect, placeable) -> placeable.placeRelative(rect.left.roundToInt(), rect.top.roundToInt()) }
            }
        }
        // The first day's box, in reading order — mirrored in placing — and
        // where it leans toward the pointer.
        val index = (nowStart.date.toEpochDays() - rowStart.toEpochDays()).toInt()
        val snapped = Rect(Offset(index.mod(days) * cell, index.floorDiv(days) * rowHeight + y), Size(cell, itemPx))
        val lean = edit.lean * pull
        val target = snapped.translate(if (rtl) -lean.x else lean.x, lean.y)
        val drawn = hop.drawnAt(nowStart, target, still) { last, aim ->
            // A jump of more than a day across — round the end of a week to the
            // start of the next — comes in from beside where it lands, a day's
            // width away toward where it was, rather than across the month. As
            // the range picker's end does.
            val across = (last.left - aim.left) / cell
            val down = (last.top - aim.top) / rowHeight
            if (kotlin.math.abs(across) <= RebaseCells) return@drawnAt null
            val length = kotlin.math.hypot(across, down)
            val dx = across / length * cell
            val dy = down / length * rowHeight
            Rect(dx, dy, dx, dy)
        }
        val dx = drawn.left - snapped.left
        val dy = drawn.top - snapped.top
        // Each piece by the week it is in: one a scrolling month has not
        // composed yet waits a frame, rather than taking another week's shape.
        val placed = dayPieces(nowStart, nowEnd, rowStart, weeks, days).mapNotNull { piece ->
            val measurable = measurables.firstOrNull { it.layoutId == piece.week } ?: return@mapNotNull null
            val width = (piece.drawnRight(cell, gapPx) - piece.drawnLeft(cell, gapPx)).roundToInt().coerceAtLeast(0)
            piece to measurable.measure(Constraints.fixed(width, (itemPx - gapPx).roundToInt().coerceAtLeast(0)))
        }
        layout(w, h) {
            placed.forEach { (piece, placeable) ->
                placeable.placeRelative((piece.drawnLeft(cell, gapPx) + dx).roundToInt(), (piece.row * rowHeight + y + dy).roundToInt())
            }
        }
    }
}

/**
 * Where a [DaysGhostLayer]'s rows are, where they scroll: the first one's
 * first day, its top against the layer's — at or above it — and a row's
 * height, all in pixels and read as the layer is laid out.
 */
internal interface GhostRows {
    fun start(): LocalDate
    fun top(): Float
    fun height(): Float
}

/** The day an event's last minute is on. */

/**
 * A piece of the copy a carry by days draws: one a row it is in.
 *
 * @property row Which of the layer's rows, from its first.
 * @property week The row's first day: what the piece is known by, as rows scroll.
 * @property from The first day it is drawn across, from the row's first: before
 *   it, past the row's start, where the event runs on from before the row.
 * @property to The last, likewise past the row's end.
 * @property shownFrom The first of the row's own days it is on: where the
 *   event's own piece in the row starts, and its title.
 * @property shownTo The last of them.
 * @property startsHere Whether the event starts on [from]: round there, and in
 *   by the gap; square, and out to the edge, where it runs on.
 * @property endsHere The same at [to].
 */
private class GhostPiece(
    val row: Int,
    val week: LocalDate,
    val from: Int,
    val to: Int,
    val shownFrom: Int,
    val shownTo: Int,
    val startsHere: Boolean,
    val endsHere: Boolean,
) {
    fun drawnLeft(cell: Float, gap: Float): Float = from * cell + if (startsHere) gap else 0f
    fun drawnRight(cell: Float, gap: Float): Float = (to + 1) * cell - if (endsHere) gap else 0f
    fun shownLeft(cell: Float, gap: Float): Float = shownFrom * cell + if (startsHere && from == shownFrom) gap else 0f
    fun shownRight(cell: Float, gap: Float): Float = (shownTo + 1) * cell - if (endsHere && to == shownTo) gap else 0f
}

/**
 * A piece of an event for each week of [weeks] rows of [days] from [rowStart]
 * it is in, reaching on past the row by up to [GhostReach] days at a side the
 * event runs on past.
 */
private fun dayPieces(start: LocalDateTime, end: LocalDateTime, rowStart: LocalDate, weeks: Int, days: Int): List<GhostPiece> = buildList {
    val firstDay = start.date.toEpochDays()
    val lastDay = lastDayOf(start, end).toEpochDays()
    for (row in 0 until weeks) {
        val week = rowStart.plus(row * days, DateTimeUnit.DAY)
        val first = week.toEpochDays()
        val last = first + days - 1
        if (lastDay < first || firstDay > last) continue
        add(
            GhostPiece(
                row = row,
                week = week,
                from = (maxOf(firstDay, first - GhostReach) - first).toInt(),
                to = (minOf(lastDay, last + GhostReach) - first).toInt(),
                shownFrom = (maxOf(firstDay, first) - first).toInt(),
                shownTo = (minOf(lastDay, last) - first).toInt(),
                startsHere = firstDay >= first - GhostReach,
                endsHere = lastDay <= last + GhostReach,
            ),
        )
    }
}

/**
 * How many days on past its row a piece of the copy reaches, where the event
 * runs on: more than the copy ever moves from its days — half a day's lean at
 * the detent's pull, and a hop of at most [RebaseCells] — so no edge of it
 * comes into view that the event does not have.
 */
private const val GhostReach = 2

/** A day's width in pixels, kept from laying out for what is measured inside to read. Not state: written in layout, read in layout. */
private class MeasuredCell {
    var px = 0f
}

/**
 * Cut at the [start] side of the days, the [end] side, or both — where a copy
 * reaches on past them, so it is seen only on them — and never at their top or
 * foot, so one leaning toward a pointer above or below its row is not cut off.
 * A side it does not reach past is left open: an event starting on the first
 * day still leans out over the hours' gutter, its round end and its title
 * with it, as it did.
 */
private fun Modifier.clipAcross(start: Boolean, end: Boolean): Modifier = if (!start && !end) {
    this
} else {
    drawWithContent {
        val rtl = layoutDirection == LayoutDirection.Rtl
        val left = if (if (rtl) end else start) 0f else -size.width
        val right = if (if (rtl) start else end) size.width else size.width * 2f
        clipRect(left, -size.height, right, size.height * 2f) { this@drawWithContent.drawContent() }
    }
}

/** More than this many days across in one step, and the copy comes in from beside where it lands. */
private const val RebaseCells = 1.5f

/** The id of the copy a carry by days draws: nothing an app's events could be. */
private object DaysGhostId
