package io.kontour.ui.components.calendar

import androidx.compose.foundation.background
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.rememberUpdatedState
import io.kontour.ui.components.selection.SliderDefaults
import io.kontour.ui.interaction.PickUpAxis
import io.kontour.ui.interaction.detectPickUp
import io.kontour.ui.theme.Tone
import androidx.compose.runtime.LaunchedEffect
import kotlin.math.roundToInt
import androidx.compose.ui.unit.Constraints
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.ui.draw.clip
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
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.Rect
import kotlinx.datetime.LocalDateTime
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.kontour.ui.foundation.Text
import io.kontour.ui.interaction.pressable
import io.kontour.ui.locale.DateStyle
import io.kontour.ui.locale.ListStyle
import io.kontour.ui.locale.NameWidth
import io.kontour.ui.overlay.LocalOverlayHostOrNull
import io.kontour.ui.overlay.Popover
import io.kontour.ui.theme.Theme
import kotlin.math.floor
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/**
 * A month of day boxes, a week to a row — every week the month touches, with
 * the days of the months either side in them, quieter — and each box holding
 * as many of its events as fit, and "+N" for the rest.
 */
@Composable
internal fun MonthView(scope: CalendarScope) {
    val pager = scope.pager
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
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
                .clip(frame)
                .border(Theme.sizing.borderWidth, gridLine(), frame)
                // Round the pages rather than on one: a page paged away does
                // not take the keyboard's focus with it.
                .calendarCursor(scope, description = scope.dayDescription(scope.state.date)) { key -> scope.monthKey(key, rtl) },
        ) {
            // Bounded, the weeks share the height; in a scrolling page, each
            // week is as tall as a date and three events.
            val bounded = constraints.hasBoundedHeight
            val minRow = monthRowMinHeight()
            val swipe = with(pager) { Modifier.swipesPeriods(scope.enabled, scope.edit, scope.touch) }
            PeriodStrip(pager, Modifier.fillMaxWidth().then(if (bounded) Modifier.fillMaxHeight() else Modifier).then(swipe)) { month ->
                MonthPage(scope, month, if (bounded) null else minRow)
            }
        }
    }
    EditDwell(scope)
}

/** The weekday names over the month's columns: abbreviated, or a letter where they do not fit. */
@Composable
private fun WeekdayRow(scope: CalendarScope) {
    val periods = scope.state.periods
    val weekStart = periods.weekStart(scope.state.date)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val narrow = maxWidth / DaysPerWeek < WeekdayAbbreviatedWidth
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

/** Below this a column has room for a weekday's letter, not its abbreviation. */
private val WeekdayAbbreviatedWidth = 40.dp

/** A week row's height in a calendar given no height of its own: its date and three events. */
@Composable
private fun monthRowMinHeight(): Dp = dateLabelHeight() + itemHeight() * MinRowsInPage

private const val MinRowsInPage = 3

@Composable
private fun itemHeight(): Dp = with(LocalDensity.current) { Theme.typography.labelSmall.lineHeight.toDp() } + Theme.spacing.xxs * 2

@Composable
private fun dateLabelHeight(): Dp = with(LocalDensity.current) { Theme.typography.labelMedium.lineHeight.toDp() } + Theme.spacing.xs * 2

@Composable
private fun MonthPage(scope: CalendarScope, month: Int, rowHeight: Dp?) {
    val periods = scope.state.periods
    val dates = periods.visibleDates(month, CalendarView.Month)
    val weeks = periods.weeksIn(month)
    val firstOfMonth = periods.startOf(month, CalendarView.Month)
    val events = remember(scope.events, dates) { scope.eventsBetween(dates.start, dates.endInclusive) }
    val gap = Theme.spacing.xxs / 2
    Box(Modifier.fillMaxWidth().then(if (rowHeight == null) Modifier.fillMaxHeight() else Modifier)) {
        Column(
            Modifier
                .fillMaxWidth()
                .then(if (rowHeight == null) Modifier.fillMaxHeight() else Modifier)
                .semantics { collectionInfo = CollectionInfo(rowCount = weeks, columnCount = DaysPerWeek) },
        ) {
            for (week in 0 until weeks) {
                val weekStart = dates.start.plus(week * DaysPerWeek, DateTimeUnit.DAY)
                val rowModifier = if (rowHeight == null) Modifier.weight(1f) else Modifier.height(rowHeight)
                MonthWeek(scope, weekStart, firstOfMonth, events, rowModifier.fillMaxWidth())
            }
        }
        // Where a carried event would land, over all the weeks: from one to the next is travel down.
        DaysGhostLayer(scope, dates.start, weeks, DaysPerWeek, slot = dateLabelHeight() + gap / 2, item = itemHeight(), gap = gap)
    }
}

@Composable
private fun MonthWeek(
    scope: CalendarScope,
    weekStart: LocalDate,
    firstOfMonth: LocalDate,
    events: List<CalendarEvent>,
    modifier: Modifier,
) {
    val item = itemHeight()
    val dateLabel = dateLabelHeight()
    val gap = Theme.spacing.xxs / 2
    val weekEvents = remember(events, weekStart) {
        events.filter { it.overlaps(weekStart, weekStart.plus(DaysPerWeek - 1, DateTimeUnit.DAY)) }
    }
    val state = scope.state
    val cursorShown = scope.cursorShown
    val ringColour = Theme.colours.focusRing
    val ringWidth = Theme.sizing.focusRingWidth
    val edit = scope.edit
    val placed = remember { PlacedAt() }
    val isTouch by rememberUpdatedState(scope.touch)
    val creates = scope.enabled && scope.onCreateRequest != null
    val holds = creates || (scope.enabled && scope.onEventMove != null)
    val nearReach = with(LocalDensity.current) { NearReach.toPx() }
    BoxWithConstraints(
        modifier
            .onGloballyPositioned { placed.coordinates = it }
            .then(
                if (holds) {
                    // A finger held in a day: the event it is beside — a month's
                    // line is under half a fingertip tall — or else a new event.
                    Modifier.pointerInput(edit, weekStart) {
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
                                if (!creates || !scope.hasMenu(null, edges = false)) return@detectPickUp
                                edit.beginCreateDays(root)
                                if (!edit.live) return@detectPickUp
                                taking?.let { edit.take(it, root, hold = true) }
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
                if (creates && scope.hasMenu(null, edges = false)) {
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
            .drawWithContent {
            drawContent()
            // The keyboard's day, over its events, so they do not hide it. Only
            // in its own month: a page sliding away shows it among its spare days.
            val date = state.date
            if (!cursorShown || date.month != firstOfMonth.month || date.year != firstOfMonth.year) return@drawWithContent
            val column = (date.toEpochDays() - weekStart.toEpochDays()).toInt()
            if (column !in 0 until DaysPerWeek) return@drawWithContent
            val width = size.width / DaysPerWeek
            val left = if (layoutDirection == LayoutDirection.Rtl) size.width - width * (column + 1) else width * column
            drawCursorRing(Rect(left, 0f, left + width, size.height), ringColour, ringWidth.toPx())
        },
    ) {
        val cell = maxWidth / DaysPerWeek
        val room = (maxHeight - dateLabel).coerceAtLeast(0.dp)
        val maxRows = if (item > 0.dp) floor(room / item).toInt().coerceAtLeast(0) else 0
        val pack = remember(weekEvents, weekStart, maxRows) { packWeek(weekItems(weekStart, weekEvents, lines = true), maxRows) }
        Row(Modifier.fillMaxSize()) {
            for (column in 0 until DaysPerWeek) {
                val date = dayInWeek(weekStart, column)
                val count = weekEvents.count { it.coversDay(date) }
                DayBox(
                    scope = scope,
                    date = date,
                    inMonth = date.month == firstOfMonth.month && date.year == firstOfMonth.year,
                    count = count,
                    // Only where a row is left for it: a box too short for even
                    // one says how many it has to a screen reader, and no more.
                    more = if (pack.shownRows[column] < maxRows) pack.more[column] else 0,
                    moreRow = pack.shownRows[column],
                    itemHeight = item,
                    dateLabel = dateLabel,
                    events = weekEvents,
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
            val span = placed.to - placed.from + 1
            EventChip(
                event = placed.event,
                presentation = if (placed.bar) EventPresentation.Bar else EventPresentation.Line,
                scope = scope,
                clippedStart = placed.continuesBefore,
                clippedEnd = placed.continuesAfter,
                focusableOn = dayInWeek(weekStart, placed.from)..dayInWeek(weekStart, placed.to),
                modifier = Modifier
                    .padding(start = cell * placed.from + gap, top = dateLabel + item * placement.row + gap / 2)
                    .width(cell * span - gap * 2)
                    .height(item - gap),
            )
        }
    }
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
    dateLabel: Dp,
    events: List<CalendarEvent>,
    modifier: Modifier,
) {
    val formats = scope.formats
    val strings = Theme.strings
    val colours = Theme.colours
    val line = Theme.sizing.borderWidth
    val lineColour = gridLine()
    val isToday = date == scope.today
    val dayClick = scope.onDateClick
    val described = formats.list(
        listOfNotNull(formats.date(date, DateStyle.Full), if (count > 0) strings.eventCount(formats.quantity(count)) else null),
        ListStyle.Unit,
    )
    var listing by remember { mutableStateOf(false) }
    val canList = LocalOverlayHostOrNull.current != null
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
            .then(
                if (dayClick != null) {
                    // The keyboard's way to a day is the grid's cursor, not a stop per day.
                    Modifier.focusProperties { canFocus = false }.pressable(
                        onClick = { dayClick(date) },
                        shape = RectangleShape,
                        enabled = scope.enabled,
                        minimumTouchTarget = false,
                    )
                } else {
                    Modifier
                },
            )
            .semantics { contentDescription = described },
    ) {
        // The date: its day, opened by pressing its number — today's ringed,
        // as the date picker rings it.
        val number = dateLabel - Theme.spacing.xxs * 2
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .padding(Theme.spacing.xxs)
                .sizeIn(minWidth = number, minHeight = number)
                .todayRing(isToday)
                .focusProperties { canFocus = false }
                .pressable(
                    onClick = { scope.state.show(date, CalendarView.Day) },
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
            Box(Modifier.padding(top = dateLabel + itemHeight * moreRow)) {
                Text(
                    strings.moreCount(formats.quantity(more)),
                    style = Theme.typography.labelSmall,
                    colour = quietInk(),
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier
                        // Reached by Tab with the day's events: from the cursor, on its day.
                        .focusProperties { canFocus = scope.state.date == date }
                        .pressable(
                            // The day's list where there is a host to show it in;
                            // the day itself where there is not.
                            onClick = { if (canList) listing = true else scope.state.show(date, CalendarView.Day) },
                            shape = Theme.shapes.capsule,
                            enabled = scope.enabled,
                            minimumTouchTarget = false,
                        )
                        .semantics { contentDescription = strings.moreEvents(formats.quantity(more)) }
                        .padding(horizontal = Theme.spacing.xs),
                )
                // Every event of the day, from its "+N".
                if (canList) Popover(visible = listing, onDismissRequest = { listing = false }, title = { +formats.date(date, DateStyle.Long) }) {
                    Column(verticalArrangement = Arrangement.spacedBy(Theme.spacing.xxs)) {
                        for (event in events.filter { it.coversDay(date) }.sortedBy { it.start }) {
                            EventChip(
                                event = event,
                                presentation = if (event.allDay) EventPresentation.Bar else EventPresentation.Line,
                                scope = scope,
                                // A list of the day's events, not its days: nothing to drag an end across.
                                clippedStart = true,
                                clippedEnd = true,
                                modifier = Modifier.fillMaxWidth().height(itemHeight),
                            )
                        }
                    }
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
 * @param slot How far down its row a new event's copy is: under the date.
 * @param item How tall a row's line of events is.
 */
@Composable
internal fun DaysGhostLayer(scope: CalendarScope, rowStart: LocalDate, weeks: Int, days: Int, slot: Dp, item: Dp, gap: Dp, line: Int? = null) {
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
    // As composed, for how many copies; placed from the edit as it is now.
    val composed = dayPieces(start, end, rowStart, weeks, days)
    val placed = remember { PlacedAt() }
    val density = LocalDensity.current
    val slotPx = with(density) { slot.toPx() }
    val itemPx = with(density) { item.toPx() }
    val gapPx = with(density) { gap.toPx() }
    val hop = remember { GhostHop() }
    LaunchedEffect(hop) { hop.run() }

    Layout(
        content = { repeat(composed.size) { EventChip(event = shown, presentation = EventPresentation.Bar, scope = scope, ghost = true) } },
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { placed.coordinates = it },
    ) { measurables, constraints ->
        val w = constraints.maxWidth
        val h = constraints.maxHeight
        val nowStart = edit.start
        val nowEnd = edit.end
        if (nowStart == null || nowEnd == null || w <= 0 || h <= 0 || weeks <= 0) return@Layout layout(w, h) {}
        val cell = w.toFloat() / days
        val rowHeight = h.toFloat() / weeks
        // How far down its row the copy is: the line the event was on, or under the date.
        val y = run {
            val top = edit.grabTop
            val page = placed.coordinates?.takeIf { it.isAttached }?.localToRoot(Offset.Zero)?.y
            if (line != null) {
                // A line of its own, kept free for it.
                line * itemPx + slotPx
            } else if (event == null || top == null || page == null) {
                slotPx
            } else {
                (top - page).mod(rowHeight).coerceIn(0f, (rowHeight - itemPx).coerceAtLeast(0f))
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
            fun rectOf(piece: Triple<Int, Int, Int>) = Rect(
                piece.second * cell + gapPx,
                piece.first * rowHeight + y,
                (piece.third + 1) * cell - gapPx,
                piece.first * rowHeight + y + itemPx - gapPx,
            )
            val stretched = if (!onPage) null else {
                val snapped = rectOf(pieces[moving])
                val lean = (edit.lean * pull).let { if (rtl) -it.x else it.x }
                val target = if (atEnd) snapped.copy(right = snapped.right + lean) else snapped.copy(left = snapped.left + lean)
                // Into another week, it is there: a hop across the month would not be an end stretching.
                hop.drawnAt(nowStart to nowEnd, target, still) { last, aim -> if (last.top != aim.top) Rect.Zero else null }
            }
            val rects = pieces.mapIndexed { i, piece -> if (i == moving && stretched != null) stretched else rectOf(piece) }
            val placeables = measurables.take(rects.size).mapIndexed { i, measurable ->
                val rect = rects[i]
                measurable.measure(Constraints.fixed(rect.width.roundToInt().coerceAtLeast(0), rect.height.roundToInt().coerceAtLeast(0)))
            }
            return@Layout layout(w, h) {
                placeables.forEachIndexed { i, placeable -> placeable.placeRelative(rects[i].left.roundToInt(), rects[i].top.roundToInt()) }
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
        val pieces = dayPieces(nowStart, nowEnd, rowStart, weeks, days)
        val placeables = measurables.take(pieces.size).mapIndexed { i, measurable ->
            val (_, from, to) = pieces[i]
            val width = ((to - from + 1) * cell - gapPx * 2).roundToInt().coerceAtLeast(0)
            measurable.measure(Constraints.fixed(width, (itemPx - gapPx).roundToInt().coerceAtLeast(0)))
        }
        layout(w, h) {
            placeables.forEachIndexed { i, placeable ->
                val (row, from, _) = pieces[i]
                placeable.placeRelative((from * cell + gapPx + dx).roundToInt(), (row * rowHeight + y + dy).roundToInt())
            }
        }
    }
}

/** The day an event's last minute is on. */
private fun lastDayOf(start: LocalDateTime, end: LocalDateTime): LocalDate = if (end > start) end.plusMinutes(-1).date else start.date

/** A piece of an event for each week of [weeks] rows of [days] from [rowStart] it is in: its row, and its first and last day's columns. */
private fun dayPieces(start: LocalDateTime, end: LocalDateTime, rowStart: LocalDate, weeks: Int, days: Int): List<Triple<Int, Int, Int>> = buildList {
    val last = lastDayOf(start, end)
    for (row in 0 until weeks) {
        val first = rowStart.plus(row * days, DateTimeUnit.DAY)
        val lastInRow = first.plus(days - 1, DateTimeUnit.DAY)
        if (last < first || start.date > lastInRow) continue
        val from = (maxOf(start.date, first).toEpochDays() - first.toEpochDays()).toInt()
        val to = (minOf(last, lastInRow).toEpochDays() - first.toEpochDays()).toInt()
        add(Triple(row, from, to))
    }
}

/** More than this many days across in one step, and the copy comes in from beside where it lands. */
private const val RebaseCells = 1.5f

/** The id of the copy a carry by days draws: nothing an app's events could be. */
private object DaysGhostId
