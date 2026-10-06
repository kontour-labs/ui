package io.kontour.ui.components.calendar

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.scrollBy
import io.kontour.ui.interaction.detectPickUp
import io.kontour.ui.interaction.PickUpAxis
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.CollectionInfo
import io.kontour.ui.motion.scrollSpec
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.kontour.ui.a11y.highContrast
import io.kontour.ui.foundation.Text
import io.kontour.ui.interaction.pressable
import io.kontour.ui.locale.DateStyle
import io.kontour.ui.locale.NameWidth
import io.kontour.ui.motion.ScrollDefaults
import io.kontour.ui.theme.Theme
import io.kontour.ui.theme.Tone
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.first
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.plus

/**
 * A day's hours, or a week's side by side: the days' headings, what takes the
 * whole day above the hours, and the hours themselves, one scroll for all the
 * days so a time is level across the week.
 */
@Composable
internal fun TimeView(scope: CalendarScope) {
    val state = scope.state
    val pager = scope.pager
    val days = if (state.view == CalendarView.Day) 1 else DaysPerWeek
    val gutter = gutterWidth(scope)
    val swipe = with(pager) { Modifier.swipesPeriods(scope.enabled, scope.edit, scope.touch) }
    val hourPx = with(LocalDensity.current) { scope.hourHeight.toPx() }
    val hours = remember { PlacedAt() }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val step = scope.edit.stepMinutes
    // Before the keyboard has put it anywhere: the first whole slot in view.
    val firstInView = {
        val perMinute = hourPx / MinutesPerHour
        if (perMinute <= 0f) 0 else (ceil(state.timeScroll.value / perMinute / step).toInt() * step)
    }
    val cursor = scope.slotDescription(state.date, state.cursorMinute?.let { scope.cursorMinute { it } })

    Column(Modifier.fillMaxWidth().then(swipe)) {
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.width(gutter))
            PeriodStrip(pager, Modifier.weight(1f)) { period -> DayHeadings(scope, period, days) }
        }
        AllDayStrip(scope, gutter, days)
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f, fill = false)) {
            // In a page that scrolls, the hours have no height to fill: a working
            // day's worth of them, scrolling within.
            val viewport = if (constraints.hasBoundedHeight) Modifier.fillMaxHeight() else Modifier.height(scope.hourHeight * UnboundedHours)
            Row(
                viewport
                    .fillMaxWidth()
                    .onGloballyPositioned { hours.coordinates = it }
                    // On the viewport rather than the hours inside it: focused,
                    // the hours would ask to have all of themselves brought into
                    // view. The cursor brings itself.
                    .calendarCursor(
                        scope,
                        description = cursor,
                        collection = CollectionInfo(rowCount = MinutesPerDay / step, columnCount = days),
                        onFocused = { if (state.cursorMinute == null) state.cursorMinute = scope.cursorMinute(firstInView) },
                    ) { key -> scope.timeKey(key, rtl, firstInView) }
                    .verticalScroll(state.timeScroll, flingBehavior = ScrollDefaults.flingBehavior()),
            ) {
                HourGutter(scope, gutter)
                PeriodStrip(pager, Modifier.weight(1f).height(scope.hourHeight * HoursPerDay)) { period ->
                    DayColumns(scope, period, days)
                }
            }
        }
    }

    EditScroll(scope, hours, days)

    // The keyboard's slot brought into view as it moves.
    val motion = Theme.motion
    LaunchedEffect(state, state.cursorMinute, scope.cursorShown, hourPx, step) {
        val minute = state.cursorMinute ?: return@LaunchedEffect
        if (!scope.cursorShown) return@LaunchedEffect
        val scroll = state.timeScroll
        val perMinute = hourPx / MinutesPerHour
        val top = (minute * perMinute).roundToInt()
        val bottom = ((minute + step) * perMinute).roundToInt()
        val viewport = scroll.viewportSize
        when {
            top < scroll.value -> scroll.animateScrollTo(top, motion.scrollSpec())
            viewport > 0 && bottom > scroll.value + viewport -> scroll.animateScrollTo(bottom - viewport, motion.scrollSpec())
        }
    }

    // Opened at the start of a working day, once, or put back where it was.
    LaunchedEffect(state, hourPx) {
        snapshotFlow { state.timeScroll.maxValue }.first { it > 0 }
        val restored = state.restoredScroll
        if (restored != null) {
            state.timeScroll.scrollTo(restored)
            state.restoredScroll = null
        } else if (!state.scrolledToStart) {
            state.timeScroll.scrollTo((state.initialScrollTime.minuteOfDay * hourPx / MinutesPerHour).roundToInt())
        }
        state.scrolledToStart = true
    }
}

/**
 * While an edit is in hand: the hours scroll when the pointer is near their
 * top or bottom — once it has been away from that edge, so taking hold of an
 * event beside the edge does not run off with it — and a carry held past the
 * first or last day pages to the period before or after, and goes on there.
 */
@Composable
private fun EditScroll(scope: CalendarScope, hours: PlacedAt, days: Int) {
    val edit = scope.edit
    val state = scope.state
    val density = LocalDensity.current
    val zone = with(density) { EdgeZone.toPx() }
    val fastest = with(density) { EdgeSpeed.toPx() }
    LaunchedEffect(edit) {
        snapshotFlow { edit.live }.collect { live ->
            if (!live) return@collect
            var armed = false
            var held = 0L
            var last = withFrameNanos { it }
            while (edit.live) {
                val now = withFrameNanos { it }
                val seconds = (now - last) / NanosPerSecond
                last = now
                val pointer = edit.lastPointer ?: continue
                val box = hours.coordinates?.takeIf { it.isAttached } ?: continue
                val top = box.localToRoot(Offset.Zero).y
                val bottom = top + box.size.height
                val depth = when {
                    pointer.y < top + zone -> -((top + zone - pointer.y) / zone).coerceAtMost(1f)
                    pointer.y > bottom - zone -> ((pointer.y - (bottom - zone)) / zone).coerceAtMost(1f)
                    else -> 0f
                }
                if (depth == 0f) armed = true
                if (armed && depth != 0f && !edit.byDays) {
                    state.timeScroll.scrollBy(depth * fastest * seconds)
                    edit.move(pointer)
                }
                // Past the first or last day, held: the next period.
                if (edit.kind == EditKind.Move && !edit.byDays) {
                    val dates = state.visibleDates
                    val first = edit.columns[dates.start]?.takeIf { it.isAttached }
                    val lastColumn = edit.columns[dates.start.plus(days - 1, DateTimeUnit.DAY)]?.takeIf { it.isAttached }
                    val left = first?.localToRoot(Offset.Zero)?.x
                    val right = lastColumn?.let { it.localToRoot(Offset.Zero).x + it.size.width }
                    val rtl = box.size.width > 0 && left != null && right != null && left > right
                    val out = when {
                        left != null && pointer.x < minOf(left, right ?: left) -> if (rtl) 1 else -1
                        right != null && pointer.x > maxOf(right, left ?: right) -> if (rtl) -1 else 1
                        else -> 0
                    }
                    if (out == 0) {
                        held = 0L
                    } else {
                        held += (seconds * NanosPerSecond).toLong()
                        if (held >= DwellNanos) {
                            held = 0L
                            state.step(out)
                            withFrameNanos { }
                            edit.move(pointer)
                        }
                    }
                }
            }
        }
    }
}

/** How near the top or bottom of the hours an edit starts them scrolling. */
private val EdgeZone = 48.dp

/** How fast, at the very edge, a second. */
private val EdgeSpeed = 1200.dp

private const val NanosPerSecond = 1_000_000_000f
private const val DwellNanos = 700_000_000L

private const val HoursPerDay = 24
private const val MinutesPerHour = 60
private const val UnboundedHours = 8

/** As wide as the widest hour label and a margin either side. */
@Composable
private fun gutterWidth(scope: CalendarScope): Dp {
    val measurer = rememberTextMeasurer()
    val style = Theme.typography.labelSmall
    val density = LocalDensity.current
    val margin = Theme.spacing.xs
    return remember(scope.formats, style, density, measurer) {
        val widest = (0 until HoursPerDay).maxOf { hour ->
            measurer.measure(scope.formats.time(LocalTime(hour, 0)), style).size.width
        }
        with(density) { widest.toDp() } + margin * 2
    }
}

/** Each day's weekday and date over its column, the date opening that day. */
@Composable
private fun DayHeadings(scope: CalendarScope, period: Int, days: Int) {
    val start = scope.state.periods.startOf(period, scope.state.view)
    val colours = Theme.colours
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val narrow = maxWidth / days < WeekdayHeadingWidth
        Row(Modifier.fillMaxWidth()) {
            for (column in 0 until days) {
                val date = start.plus(column, DateTimeUnit.DAY)
                val isToday = date == scope.today
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = Theme.spacing.xxs)
                        .semantics(mergeDescendants = true) { heading() },
                ) {
                    Text(
                        scope.formats.weekdayName(date.dayOfWeek, if (narrow) NameWidth.Narrow else NameWidth.Abbreviated),
                        style = Theme.typography.labelSmall,
                        colour = quietInk(),
                        maxLines = 1,
                        modifier = Modifier.clearAndSetSemantics { },
                    )
                    Text(
                        scope.formats.integer(date.day),
                        style = Theme.typography.titleSmall,
                        colour = if (isToday) colours.accent.onSolid else colours.content,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier
                            .then(if (isToday) Modifier.background(colours.accent.solid, Theme.shapes.capsule) else Modifier)
                            .pressable(
                                onClick = { scope.state.show(date, CalendarView.Day) },
                                shape = Theme.shapes.capsule,
                                enabled = scope.enabled,
                                minimumTouchTarget = false,
                            )
                            .semantics { contentDescription = scope.formats.date(date, DateStyle.Full) }
                            .padding(horizontal = Theme.spacing.xs),
                    )
                }
            }
        }
    }
}

/** Below this a day's heading has room for its weekday's letter, not its abbreviation. */
private val WeekdayHeadingWidth = 48.dp

/** What takes the whole day, above the hours: as bars across their days. */
@Composable
private fun AllDayStrip(scope: CalendarScope, gutter: Dp, days: Int) {
    val state = scope.state
    val start = state.periods.startOf(state.period, state.view)
    val end = start.plus(days - 1, DateTimeUnit.DAY)
    val any = remember(scope.events, start, days) { weekItems(start, scope.eventsBetween(start, end), lines = false, days = days).isNotEmpty() }
    if (!any) return
    val item = with(LocalDensity.current) { Theme.typography.labelSmall.lineHeight.toDp() } + Theme.spacing.xxs * 2
    Row(Modifier.fillMaxWidth()) {
        Box(Modifier.width(gutter).padding(top = Theme.spacing.xxs), contentAlignment = Alignment.TopEnd) {
            Text(
                Theme.strings.allDay,
                style = Theme.typography.labelSmall,
                colour = quietInk(),
                maxLines = 1,
                modifier = Modifier.padding(horizontal = Theme.spacing.xs).clearAndSetSemantics { },
            )
        }
        PeriodStrip(scope.pager, Modifier.weight(1f)) { period ->
            val from = state.periods.startOf(period, state.view)
            val to = from.plus(days - 1, DateTimeUnit.DAY)
            val pack = remember(scope.events, from, days) {
                packWeek(weekItems(from, scope.eventsBetween(from, to), lines = false, days = days), maxRows = AllDayRows, days = days)
            }
            val rows = (pack.placed.maxOfOrNull { it.row } ?: -1) + 1 + if (pack.more.any { it > 0 }) 1 else 0
            BoxWithConstraints(Modifier.fillMaxWidth().height(item * rows)) {
                val cell = maxWidth / days
                val gap = Theme.spacing.xxs / 2
                // Each day's place in the row, for a bar carried by whole days.
                Row(Modifier.fillMaxSize()) {
                    for (column in 0 until days) {
                        DayTarget(scope, from.plus(column, DateTimeUnit.DAY), Modifier.weight(1f).fillMaxHeight())
                    }
                }
                for (placement in pack.placed) {
                    val placed = placement.item
                    EventChip(
                        event = placed.event,
                        presentation = EventPresentation.Bar,
                        scope = scope,
                        modifier = Modifier
                            .padding(start = cell * placed.from + gap, top = item * placement.row + gap / 2)
                            .width(cell * (placed.to - placed.from + 1) - gap * 2)
                            .height(item - gap),
                    )
                }
                for (column in 0 until days) {
                    val more = pack.more[column]
                    if (more == 0) continue
                    val date = from.plus(column, DateTimeUnit.DAY)
                    val described = Theme.strings.moreEvents(scope.formats.quantity(more))
                    Text(
                        Theme.strings.moreCount(scope.formats.quantity(more)),
                        style = Theme.typography.labelSmall,
                        colour = quietInk(),
                        maxLines = 1,
                        modifier = Modifier
                            .padding(start = cell * column + gap, top = item * pack.shownRows[column])
                            .pressable(
                                onClick = { scope.state.show(date, CalendarView.Day) },
                                shape = Theme.shapes.capsule,
                                enabled = scope.enabled,
                                minimumTouchTarget = false,
                            )
                            .semantics { contentDescription = described }
                            .padding(horizontal = Theme.spacing.xs),
                    )
                }
            }
        }
    }
}

/**
 * A day's place for an edit by days to find, edged while a carried bar would
 * land on it.
 */
@Composable
internal fun DayTarget(scope: CalendarScope, date: LocalDate, modifier: Modifier) {
    val edit = scope.edit
    val placed = remember { PlacedAt() }
    DisposableEffect(edit, date) {
        onDispose { if (edit.days[date] === placed.coordinates) edit.days.remove(date) }
    }
    Box(
        modifier
            .onGloballyPositioned { coordinates ->
                placed.coordinates = coordinates
                edit.days[date] = coordinates
            }
            .then(if (edit.landsOn(date)) Modifier.border(Theme.sizing.borderWidthStrong, Theme.colours.accent.solid) else Modifier),
    )
}

/** At most this many rows of all-day bars before a day says "+N". */
private const val AllDayRows = 3

/** The hours down the side, each label level with its line. */
@Composable
private fun HourGutter(scope: CalendarScope, width: Dp) {
    val hour = scope.hourHeight
    val marginDp = Theme.spacing.xs
    Layout(
        content = {
            for (h in 1 until HoursPerDay) {
                Text(
                    scope.formats.time(LocalTime(h, 0)),
                    style = Theme.typography.labelSmall,
                    colour = quietInk(),
                    maxLines = 1,
                    modifier = Modifier.clearAndSetSemantics { },
                )
            }
        },
        modifier = Modifier.width(width).height(hour * HoursPerDay),
    ) { measurables, constraints ->
        val hourPx = hour.toPx()
        val margin = marginDp.roundToPx()
        val labels = measurables.map { it.measure(Constraints(maxWidth = (constraints.maxWidth - margin * 2).coerceAtLeast(0))) }
        layout(constraints.maxWidth, constraints.maxHeight) {
            labels.forEachIndexed { i, label ->
                val y = ((i + 1) * hourPx - label.height / 2f).roundToInt()
                label.placeRelative(constraints.maxWidth - margin - label.width, y)
            }
        }
    }
}

/** The days' columns of the period, side by side. */
@Composable
private fun DayColumns(scope: CalendarScope, period: Int, days: Int) {
    val start = scope.state.periods.startOf(period, scope.state.view)
    Row(Modifier.fillMaxWidth().fillMaxHeight()) {
        for (column in 0 until days) {
            DayColumn(scope, start.plus(column, DateTimeUnit.DAY), Modifier.weight(1f).fillMaxHeight())
        }
    }
}

/**
 * One day's hours: a line each hour, its events laid side by side where they
 * overlap, the line for now on today — and, where the calendar takes edits, a
 * new event drawn out on its empty time and the ghost of an edit landing here.
 */
@Composable
private fun DayColumn(scope: CalendarScope, date: LocalDate, modifier: Modifier) {
    val density = LocalDensity.current
    val colours = Theme.colours
    val line = Theme.sizing.borderWidth
    val nowLine = Theme.sizing.borderWidthStrong
    val lineColour = if (highContrast()) colours.outlineStrong else colours.outline
    val nowColour = colours.accent.solid
    val hourPx = with(density) { scope.hourHeight.toPx() }
    val minChipPx = with(density) { (Theme.typography.labelMedium.lineHeight.toDp() + Theme.spacing.xxs * 2).toPx() }
    val minMinutes = if (hourPx > 0f) ceil(minChipPx / (hourPx / MinutesPerHour)).toInt() else 0
    val placements = remember(scope.events, date, minMinutes) { layoutTimedDay(date, scope.eventsBetween(date, date), minMinutes) }
    val now = scope.now?.takeIf { it.date == date }?.time
    val click by rememberUpdatedState(scope.onTimeClick)
    val step = TapStepMinutes
    val gapPx = with(density) { (Theme.spacing.xxs / 2).toPx() }
    val edit = scope.edit
    val placed = remember { PlacedAt() }
    val press = remember { PressAt() }
    val creates = scope.enabled && scope.onCreateRequest != null
    val isTouch by rememberUpdatedState(scope.touch)
    val ringColour = colours.focusRing
    val ringWidth = Theme.sizing.focusRingWidth
    val cursorShown = scope.cursorShown
    val state = scope.state

    // Where an edit in hand would put it, if any of that is on this day.
    val ghost = run {
        val start = edit.start
        val end = edit.end
        if (!edit.live || edit.byDays || start == null || end == null) return@run null
        val shown = CalendarEvent(
            id = GhostId,
            title = edit.event?.title ?: scope.strings.newEvent,
            start = start,
            end = end,
            tone = edit.event?.tone ?: Tone.Accent,
        )
        layoutTimedDay(date, listOf(shown), minMinutes).singleOrNull()
    }

    DisposableEffect(edit, date) {
        onDispose {
            if (edit.columns[date] === placed.coordinates) edit.columns.remove(date)
            edit.draggables.remove(placed)
        }
    }

    Layout(
        content = {
            for (placement in placements) {
                key(placement.event.id, placement.start) {
                    EventChip(
                        event = placement.event,
                        presentation = EventPresentation.Block,
                        scope = scope,
                        clippedStart = placement.clippedStart,
                        clippedEnd = placement.clippedEnd,
                        focusableOn = date..date,
                    )
                }
            }
            if (ghost != null) {
                EventChip(event = ghost.event, presentation = EventPresentation.Block, scope = scope, ghost = true)
            }
        },
        modifier = modifier
            .onGloballyPositioned { coordinates ->
                placed.coordinates = coordinates
                edit.columns[date] = coordinates
                edit.pixelsPerMinute = hourPx / MinutesPerHour
                if (creates) edit.draggables[placed] = coordinates else edit.draggables.remove(placed)
            }
            .drawBehind {
                val width = line.toPx()
                // The day's start edge, and an hour's line across it each hour.
                val x = if (layoutDirection == LayoutDirection.Rtl) size.width - width / 2 else width / 2
                drawLine(lineColour, Offset(x, 0f), Offset(x, size.height), width)
                for (h in 1 until HoursPerDay) {
                    val y = h * hourPx
                    drawLine(lineColour, Offset(0f, y), Offset(size.width, y), width)
                }
                if (now != null) {
                    val y = now.minuteOfDay * hourPx / MinutesPerHour
                    val stroke = nowLine.toPx()
                    drawLine(nowColour, Offset(0f, y), Offset(size.width, y), stroke)
                    val dot = if (layoutDirection == LayoutDirection.Rtl) size.width else 0f
                    drawCircle(nowColour, radius = stroke * NowDotScale, center = Offset(dot, y))
                }
            }
            .drawWithContent {
                drawContent()
                // The keyboard's slot, over the events, so one on it does not hide it.
                if (!cursorShown || state.date != date) return@drawWithContent
                val minute = state.cursorMinute ?: return@drawWithContent
                val perMinute = hourPx / MinutesPerHour
                val top = minute * perMinute
                drawCursorRing(Rect(0f, top, size.width, top + edit.stepMinutes * perMinute), ringColour, ringWidth.toPx())
            }
            .then(
                if (scope.onTimeClick != null && scope.enabled) {
                    Modifier.pointerInput(date, hourPx) {
                        detectTapGestures { at ->
                            val minute = floorTo((at.y / hourPx * MinutesPerHour).toLong(), step).coerceIn(0L, (MinutesPerDay - step).toLong())
                            click?.invoke(date.atMidnight.plusMinutes(minute))
                        }
                    }
                } else {
                    Modifier
                },
            )
            .then(
                // Inside the tap, so a drawn-out range, put down, is not also a tap.
                if (creates) {
                    Modifier.notesPress(press).pointerInput(edit, date) {
                        detectPickUp(
                            axis = PickUpAxis.Free,
                            immediate = { !isTouch },
                            onPickUp = { at ->
                                val root = placed.coordinates?.takeIf { it.isAttached }?.localToRoot(press.position ?: at) ?: return@detectPickUp
                                edit.beginCreate(date, root)
                                if (isTouch) edit.pickedUp()
                            },
                            onCarry = { change, _ ->
                                change.consume()
                                placed.coordinates?.takeIf { it.isAttached }?.localToRoot(change.position)?.let(edit::move)
                            },
                            onPutDown = { scope.putDown() },
                            onCancel = { edit.cancel() },
                        )
                    }
                } else {
                    Modifier
                },
            ),
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val perMinute = hourPx / MinutesPerHour
        val all = if (ghost != null) placements + ghost else placements
        val placeables = measurables.mapIndexed { i, measurable ->
            val p = all[i]
            val lane = width.toFloat() / p.lanes
            val w = (lane - gapPx).roundToInt().coerceAtLeast(0)
            val h = (maxOf(minChipPx, (p.end - p.start) * perMinute) - gapPx).roundToInt().coerceAtLeast(0)
            measurable.measure(Constraints.fixed(w, h))
        }
        layout(width, height) {
            placeables.forEachIndexed { i, placeable ->
                val p = all[i]
                val lane = width.toFloat() / p.lanes
                placeable.placeRelative((p.lane * lane + gapPx / 2).roundToInt(), (p.start * perMinute + gapPx / 2).roundToInt())
            }
        }
    }
}

/** The id of the ghost an edit draws: nothing an app's events could be. */
private object GhostId

/** An empty time pressed is the quarter-hour it is in. */
private const val TapStepMinutes = 15

/** The dot at the start of the line for now, against the line's own thickness. */
private const val NowDotScale = 2.5f
