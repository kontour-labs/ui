package io.kontour.ui.components.calendar

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.input.pointer.PointerId
import io.kontour.ui.components.selection.SliderDefaults
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.foundation.gestures.scrollBy
import io.kontour.ui.interaction.detectPickUp
import io.kontour.ui.interaction.PickUpAxis
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import io.kontour.ui.locale.LocaleFormats
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.drawWithCache
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
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
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
    // As wide as the labels measured ahead, or as they came out once laid
    // out: a font that loads after the first measure — on the web, from a
    // file — sets them wider than the measure ahead knew, and "10 am" wrapped
    // to a bare "10".
    var laidOut by remember { mutableStateOf(0.dp) }
    val gutter = maxOf(gutterWidth(scope), laidOut)
    val swipe = with(pager) { Modifier.swipesPeriods(scope.enabled, scope.edit, scope.touch) }
    val hourPx = with(LocalDensity.current) { scope.hourHeight.toPx() }
    val hours = remember { PlacedAt() }
    // Opened with the first hour's label whole, not cut through by the top
    // edge it is centred on.
    val labelInset = with(LocalDensity.current) { Theme.typography.labelSmall.lineHeight.toPx() / 2f + Theme.spacing.xxs.toPx() }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val step = scope.edit.stepMinutes
    // Before the keyboard has put it anywhere: the first whole slot in view.
    val firstInView = {
        val perMinute = hourPx / MinutesPerHour
        if (perMinute <= 0f) 0 else (ceil(state.timeScroll.value / perMinute / step).toInt() * step)
    }
    val frame = Theme.shapes.container
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
                    .hoursFrame(gutter, frame, gridLine(), Theme.sizing.borderWidth)
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
                HourGutter(scope, gutter) { if (it > laidOut) laidOut = it }
                PeriodStrip(pager, Modifier.weight(1f).height(scope.hourHeight * HoursPerDay)) { period ->
                    Box(Modifier.fillMaxSize()) {
                        DayColumns(scope, period, days)
                        TimeGhost(scope, period, days)
                    }
                }
            }
        }
    }

    EditScroll(scope, hours)
    EditDwell(scope)
    EditWiden(scope)

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
            state.timeScroll.scrollTo((state.initialScrollTime.minuteOfDay * hourPx / MinutesPerHour - labelInset).roundToInt().coerceAtLeast(0))
        }
        state.scrolledToStart = true
    }
}

/**
 * The event in focus opened out to its whole day, and closed back to its lane
 * when it is let go: from the moment a finger holds it, as the copy the hold
 * lifts is drawn — that copy stood at the whole day's width at once, and the
 * event under it opened out only once "Adjust time" was chosen.
 *
 * On the default tween, about a fifth of a second: a snappy spring, over in
 * 140ms, read as a jump, and the gentle one, a third of a second, as a wait.
 */
@Composable
private fun EditWiden(scope: CalendarScope) {
    val edit = scope.edit
    val motion by rememberUpdatedState(Theme.motion)
    LaunchedEffect(edit) {
        snapshotFlow { edit.focus }.collectLatest { focus ->
            if (focus != null) {
                if (edit.widened != focus) {
                    edit.widen.snapTo(0f)
                    edit.widened = focus
                }
                edit.widen.animateTo(1f, motion.tweenDefault())
            } else if (edit.widened != null) {
                edit.widen.animateTo(0f, motion.tweenDefault())
                edit.widened = null
            }
        }
    }
}

/**
 * While an edit is in hand: the hours scroll when the pointer is near their
 * top or bottom — once it has been away from that edge, so taking hold of an
 * event beside the edge does not run off with it.
 */
@Composable
private fun EditScroll(scope: CalendarScope, hours: PlacedAt) {
    val edit = scope.edit
    val state = scope.state
    val density = LocalDensity.current
    val zone = with(density) { EdgeZone.toPx() }
    val fastest = with(density) { EdgeSpeed.toPx() }
    LaunchedEffect(edit) {
        var armed = false
        // How deep into the top or bottom zone the pointer is, -1 to 1, and
        // nothing — not even a frame — while it is not in either.
        snapshotFlow {
            val pointer = edit.lastPointer
            if (!edit.live || edit.pointer == null || edit.byDays || pointer == null) return@snapshotFlow Float.NaN
            val box = hours.coordinates?.takeIf { it.isAttached } ?: return@snapshotFlow 0f
            val top = box.localToRoot(Offset.Zero).y
            val bottom = top + box.size.height
            when {
                pointer.y < top + zone -> -((top + zone - pointer.y) / zone).coerceAtMost(1f)
                pointer.y > bottom - zone -> ((pointer.y - (bottom - zone)) / zone).coerceAtMost(1f)
                else -> 0f
            }
        }.collectLatest { depth ->
            when {
                depth.isNaN() -> armed = false
                depth == 0f -> armed = true
                armed -> {
                    var last = withFrameNanos { it }
                    while (true) {
                        val now = withFrameNanos { it }
                        state.timeScroll.scrollBy(depth * fastest * (now - last) / NanosPerSecond)
                        last = now
                        edit.lastPointer?.let(edit::move)
                    }
                }
            }
        }
    }
}

/**
 * While an event is carried: held at either side of the days — within
 * [DwellZone] of the first or last day's outer edge, or past it — the period
 * before or after is paged to, and the carry goes on there. Within the edge
 * rather than only past it: a calendar as wide as a phone has no past for a
 * finger to go to. By the hours' columns, or by the days' boxes for a carry by
 * days — a month's, or a week's all-day row.
 */
@Composable
internal fun EditDwell(scope: CalendarScope) {
    val edit = scope.edit
    val state = scope.state
    val zone = with(LocalDensity.current) { DwellZone.toPx() }
    LaunchedEffect(edit) {
        // Bumped once a page has come in and the carry been aimed again: the
        // days' places are not state, and the side was last read as the page
        // changed, before the new days were laid out — none of them, so no side.
        var landed by mutableIntStateOf(0)
        // Which side the pointer is held at, if either: frames are counted only then.
        snapshotFlow {
            landed
            val pointer = edit.lastPointer
            if (edit.kind != EditKind.Move || edit.pointer == null || pointer == null) return@snapshotFlow NotHeld
            val shown = state.visibleDates
            val places = if (edit.byDays) edit.days else edit.columns
            var left = Float.MAX_VALUE
            var right = -Float.MAX_VALUE
            var firstLeft: Float? = null
            for ((date, coordinates) in places.values) {
                if (date !in shown || !coordinates.isAttached) continue
                val x = coordinates.localToRoot(Offset.Zero).x
                left = minOf(left, x)
                right = maxOf(right, x + coordinates.size.width)
                if (date == shown.start) firstLeft = x
            }
            if (left > right) return@snapshotFlow NotHeld
            // The first day on the right: the reading runs the other way.
            val rtl = firstLeft != null && firstLeft > (left + right) / 2f
            // The side on the screen, -1 left and 1 right; paged by the reading's.
            val side = when {
                pointer.x < left + zone -> -1
                pointer.x > right - zone -> 1
                else -> 0
            }
            // The side on the screen, and the way it pages.
            side to if (rtl) -side else side
        }.collectLatest { (side, out) ->
            edit.dwellSide = side
            edit.dwellProgress = 0f
            if (out == 0) return@collectLatest
            while (true) {
                var held = 0L
                var last = withFrameNanos { it }
                // The range picker's rumble, building over the hold; stopped
                // before the page turns, and as soon as the hold is let go.
                edit.hold?.start()
                try {
                    while (held < DwellNanos) {
                        val now = withFrameNanos { it }
                        held += now - last
                        last = now
                        edit.dwellProgress = (held.toFloat() / DwellNanos).coerceAtMost(1f)
                        edit.hold?.update(edit.dwellProgress)
                    }
                } finally {
                    edit.hold?.stop()
                }
                edit.dwellProgress = 0f
                // Paged, and the carry aimed again once the new period has
                // come in — not cancelled by the pager's slide, which moves
                // the days this reads and so the side it says the pointer is
                // at: cancelled, the carry stayed on the old period's day
                // until the pointer moved, and a still finger never did.
                withContext(NonCancellable) {
                    state.step(out)
                    // The page turning is the one thing felt: a threshold.
                    edit.pageTurned()
                    // The new period's days, laid out and come to rest, before
                    // the carry finds its day in them.
                    withFrameNanos { }
                    withFrameNanos { }
                    withTimeoutOrNull(PageLandNanos / 1_000_000) {
                        snapshotFlow { scope.pager.periods.inView.size }.first { it <= 1 }
                    }
                    // Aimed again without a step's detent: the page was the change.
                    edit.steps.reset()
                    edit.lastPointer?.let(edit::move)
                    landed++
                }
            }
        }
    }
}

/** No carry held at either side. */
private val NotHeld = 0 to 0

/** The longest a carry waits for a page to come in before it finds its day anyway. */
private const val PageLandNanos = 1_000_000_000L

/** How near either side of the days a carry is held to page on. */
private val DwellZone = 24.dp

/**
 * The days' hours in a box, as the month's days are: cornered as the library's
 * containers are and edged with the grid's line, the gutter outside it. Clips
 * what scrolls under its corners.
 */
private fun Modifier.hoursFrame(gutter: Dp, shape: Shape, colour: Color, line: Dp): Modifier =
    drawWithCache {
        val stroke = line.toPx()
        val gutterPx = gutter.toPx()
        val rtl = layoutDirection == LayoutDirection.Rtl
        val boxLeft = if (rtl) 0f else gutterPx
        val box = Size((size.width - gutterPx).coerceAtLeast(0f), size.height)
        val edge = shape.createOutline(Size((box.width - stroke).coerceAtLeast(0f), (box.height - stroke).coerceAtLeast(0f)), layoutDirection, this)
        val clip = Path().apply {
            addOutline(shape.createOutline(box, layoutDirection, this@drawWithCache))
            translate(Offset(boxLeft, 0f))
            addRect(Rect(if (rtl) box.width else 0f, 0f, if (rtl) size.width else gutterPx, size.height))
        }
        onDrawWithContent {
            clipPath(clip) { this@onDrawWithContent.drawContent() }
            translate(boxLeft + stroke / 2f, stroke / 2f) { drawOutline(edge, colour, style = Stroke(stroke)) }
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

/**
 * An hour as the gutter labels it: on a twelve-hour clock without its
 * minutes — `9 am` — which say nothing on the hour and made the gutter the
 * widest thing in a phone's week; on a twenty-four-hour clock as the clock
 * writes it, since `14` alone is not a time.
 *
 * The minutes are taken out of the locale's own time, so the day period stays
 * where the language puts it — `午前9` — and a time written some other way is
 * left whole.
 */
internal fun LocaleFormats.hourLabel(hour: Int): String {
    val full = time(LocalTime(hour, 0))
    if (!conventions.hourCycle.usesDayPeriod) return full
    val zero = digits(0)
    val minutes = timeLayout.separator + zero + zero
    return if (minutes in full) full.replace(minutes, "") else full
}

/** As wide as the widest hour label and a margin either side. */
@Composable
private fun gutterWidth(scope: CalendarScope): Dp {
    val measurer = rememberTextMeasurer()
    val style = Theme.typography.labelSmall
    val density = LocalDensity.current
    val margin = Theme.spacing.xs
    return remember(scope.formats, style, density, measurer) {
        val widest = (0 until HoursPerDay).maxOf { hour ->
            measurer.measure(scope.formats.hourLabel(hour), style).size.width
        }
        with(density) { widest.toDp() } + margin * 2
    }
}

/** Each day's weekday and date over its column, the date opening that day. */
@Composable
private fun DayHeadings(scope: CalendarScope, period: Int, days: Int) {
    val start = scope.state.periods.startOf(period, scope.state.view)
    val colours = Theme.colours
    // A circle round the number, as tall as the number and a little more.
    val dayNumber = with(LocalDensity.current) { Theme.typography.titleSmall.lineHeight.toDp() } + Theme.spacing.xs
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
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .sizeIn(minWidth = dayNumber, minHeight = dayNumber)
                            .todayRing(isToday)
                            .pressable(
                                onClick = { scope.state.show(date, CalendarView.Day) },
                                shape = Theme.shapes.capsule,
                                enabled = scope.enabled,
                                minimumTouchTarget = false,
                            )
                            .semantics { contentDescription = scope.formats.date(date, DateStyle.Full) }
                            .padding(horizontal = Theme.spacing.xxs),
                    ) {
                        Text(
                            scope.formats.integer(date.day),
                            style = Theme.typography.titleSmall,
                            colour = colours.content,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.clearAndSetSemantics { },
                        )
                    }
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
    // A bar being carried, or days drawn out, needs the strip to be there to be
    // seen in, whatever week it is carried into.
    val edit = scope.edit
    val carrying = edit.live && edit.byDays
    if (!any && !carrying) return
    val item = with(LocalDensity.current) { Theme.typography.labelSmall.lineHeight.toDp() } + Theme.spacing.xxs * 2
    Row(Modifier.fillMaxWidth()) {
        // No label beside it: the bars say what they are, and "All day" was
        // the widest thing in the gutter, cut to "All" on a phone.
        Spacer(Modifier.width(gutter))
        PeriodStrip(scope.pager, Modifier.weight(1f)) { period ->
            val from = state.periods.startOf(period, state.view)
            val to = from.plus(days - 1, DateTimeUnit.DAY)
            val pack = remember(scope.events, from, days) {
                packWeek(weekItems(from, scope.eventsBetween(from, to), lines = false, days = days), maxRows = AllDayRows, days = days)
            }
            val held = (pack.placed.maxOfOrNull { it.row } ?: -1) + 1 + if (pack.more.any { it > 0 }) 1 else 0
            // Carried into a week it is not in, the bar is given a line of its
            // own at the foot, so it is seen where it would land rather than
            // appearing only once it is put down. The week it came from has its
            // line already.
            val proposedStart = edit.start
            val proposedEnd = edit.end
            val visiting = carrying && proposedStart != null && proposedEnd != null &&
                proposedStart.date <= to && lastDayOf(proposedStart, proposedEnd) >= from &&
                pack.placed.none { it.item.event.id == edit.event?.id }
            val rows = held + if (visiting) 1 else 0
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
                        clippedStart = placed.continuesBefore,
                        clippedEnd = placed.continuesAfter,
                        modifier = Modifier
                            .padding(start = cell * placed.from + gap, top = item * placement.row + gap / 2)
                            .width(cell * (placed.to - placed.from + 1) - gap * 2)
                            .height(item - gap),
                    )
                }
                DaysGhostLayer(scope, from, weeks = 1, days = days, slot = gap / 2, item = item, gap = gap, line = if (visiting) rows - 1 else null)
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
 * A day's place for an edit by days to find. Where it would land is drawn by
 * [DaysGhost], over the days, rather than by each day here.
 */
@Composable
internal fun DayTarget(scope: CalendarScope, date: LocalDate, modifier: Modifier) {
    val edit = scope.edit
    val placed = remember { PlacedAt() }
    DisposableEffect(edit, date) {
        onDispose { edit.days.remove(placed) }
    }
    Box(
        modifier
            .onGloballyPositioned { coordinates ->
                placed.coordinates = coordinates
                edit.days[placed] = DayPlace(date, coordinates)
            },
    )
}

/** At most this many rows of all-day bars before a day says "+N". */
private const val AllDayRows = 3

/**
 * The hours down the side, each label level with its line: on one line
 * whatever it is given, and [onWidth] told the width they need, for a gutter
 * narrower than that to widen.
 */
@Composable
private fun HourGutter(scope: CalendarScope, width: Dp, onWidth: (Dp) -> Unit) {
    val hour = scope.hourHeight
    val marginDp = Theme.spacing.xs
    val reported by rememberUpdatedState(onWidth)
    Layout(
        content = {
            for (h in 1 until HoursPerDay) {
                Text(
                    scope.formats.hourLabel(h),
                    style = Theme.typography.labelSmall,
                    colour = quietInk(),
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.clearAndSetSemantics { },
                )
            }
        },
        modifier = Modifier.width(width).height(hour * HoursPerDay),
    ) { measurables, constraints ->
        val hourPx = hour.toPx()
        val margin = marginDp.roundToPx()
        val labels = measurables.map { it.measure(Constraints()) }
        val needed = (labels.maxOfOrNull { it.width } ?: 0) + margin * 2
        if (needed > constraints.maxWidth) reported(needed.toDp())
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
    val lineColour = gridLine()
    val nowColour = colours.accent.solid
    val hourPx = with(density) { scope.hourHeight.toPx() }
    // Every event as tall as its time, so an hour is an hour whatever is in
    // it and a quarter-hour's edge is on the quarter: no shorter than a step,
    // the least an edit makes, for one with no length at all. Grown to fit
    // its title, a quarter-hour reached down to twenty past, and the hour
    // with it looked fuller than the one beside it.
    val minMinutes = scope.edit.stepMinutes
    val minChipPx = minMinutes * hourPx / MinutesPerHour
    val placements = remember(scope.events, date, minMinutes) { layoutTimedDay(date, scope.eventsBetween(date, date), minMinutes) }
    val now = scope.now?.takeIf { it.date == date }?.time
    val click by rememberUpdatedState(scope.onTimeClick)
    val step = TapStepMinutes
    val gapPx = with(density) { (Theme.spacing.xxs / 2).toPx() }
    val edit = scope.edit
    val placed = remember { PlacedAt() }
    val press = remember { PressAt() }
    val creates = scope.enabled && scope.onCreateRequest != null
    // Empty time's menu, the calendar's "New event" or an app's own.
    val offers = scope.hasMenu(null, edges = true)
    // A finger held beside a short event, rather than on it, takes the event.
    val holds = creates || offers || (scope.enabled && scope.onEventMove != null)
    val nearReach = with(density) { NearReach.toPx() }
    val isTouch by rememberUpdatedState(scope.touch)
    val ringColour = colours.focusRing
    val ringWidth = Theme.sizing.focusRingWidth
    val cursorShown = scope.cursorShown
    val state = scope.state

    // The event in focus — held, carried, or its edges given handles —
    // widened to its whole day by `EditWiden`, over the others, so its
    // handles and its text have room: in a lane a seventh of a week wide it
    // was squashed between its neighbours.
    val widened = edit.widened?.takeIf { id -> placements.any { it.event.id == id } }

    // How tall each is drawn, as the layout below draws it.
    val blockHeight = { p: TimedPlacement ->
        with(density) { (maxOf(minChipPx, (p.end - p.start) * hourPx / MinutesPerHour) - gapPx).coerceAtLeast(0f).toDp() }
    }

    DisposableEffect(edit, date) {
        onDispose {
            edit.columns.remove(placed)
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
                        blockHeight = blockHeight(placement),
                    )
                }
            }
        },
        modifier = modifier
            .onGloballyPositioned { coordinates ->
                placed.coordinates = coordinates
                edit.columns[placed] = DayPlace(date, coordinates)
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
                    // Wholly inside the day, past its start edge: centred on the
                    // edge, the first day's half of it was under the frame's line.
                    val radius = stroke * NowDotScale
                    val inset = radius + width
                    val dot = if (layoutDirection == LayoutDirection.Rtl) size.width - inset else inset
                    drawCircle(nowColour, radius = radius, center = Offset(dot, y))
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
                if (holds) {
                    Modifier.notesPress(press).pointerInput(edit, date) {
                        var taking: PointerId? = null
                        detectPickUp(
                            axis = PickUpAxis.Free,
                            immediate = { !isTouch },
                            // A scroll of the hours, or a swipe of the pages, is not a hold.
                            givesWay = true,
                            // Empty time only: a press on an event is the event's.
                            accepts = { at ->
                                val root = placed.coordinates?.takeIf { it.isAttached }?.localToRoot(at)
                                (isTouch || creates) && (root == null || !edit.eventAt(root))
                            },
                            onPointer = { taking = it },
                            onPickUp = { at ->
                                val coordinates = placed.coordinates?.takeIf { it.isAttached } ?: return@detectPickUp
                                val root = coordinates.localToRoot(press.position ?: at)
                                if (isTouch) {
                                    // Beside an event rather than on it, a finger means the event.
                                    val near = edit.eventNear(root, nearReach)
                                    if (near != null && scope.movable(near.event)) {
                                        val top = near.coordinates.localToRoot(Offset.Zero).y
                                        scope.takeUp(near.event, root, near.byDays, top, taking, hold = true, edges = near.edges)
                                        return@detectPickUp
                                    }
                                    if (!offers) return@detectPickUp
                                    // An hour where the finger is, with its menu: let go, and
                                    // "New event" makes it; set off, and it is drawn out instead.
                                    edit.beginCreate(date, root, length = NewEventMinutes)
                                    if (!edit.live) return@detectPickUp
                                    taking?.let { edit.take(it, root, hold = true) }
                                    edit.pickedUp()
                                    scope.openMenu(root, null, edges = true)
                                } else {
                                    edit.beginCreate(date, root)
                                    if (!edit.live) return@detectPickUp
                                    taking?.let { edit.take(it, root, hold = false) }
                                    edit.move(coordinates.localToRoot(at))
                                }
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
                        edit.beginCreate(date, root, length = NewEventMinutes)
                        scope.openMenu(root, null, edges = true)
                    }
                } else {
                    Modifier
                },
            ),
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val perMinute = hourPx / MinutesPerHour
        val all = placements
        val wide = widened
        val f = if (wide == null) 0f else edit.widen.value
        // Its lane, or — for the one in focus — between its lane and the whole day.
        fun across(p: TimedPlacement): Pair<Float, Float> {
            val lane = width.toFloat() / p.lanes
            val x = p.lane * lane + gapPx / 2
            val w = lane * p.span - gapPx
            if (p.event.id != wide) return x to w
            // Where it opens out from, for the copy an edit draws to open out from too.
            if (width > 0) edit.widenedLane = (p.lane * lane) / width to (lane * p.span) / width
            if (f == 0f) return x to w
            return (x + (gapPx / 2 - x) * f) to (w + (width - gapPx - w) * f)
        }
        val placeables = measurables.mapIndexed { i, measurable ->
            val p = all[i]
            val w = across(p).second.roundToInt().coerceAtLeast(0)
            val h = (maxOf(minChipPx, (p.end - p.start) * perMinute) - gapPx).roundToInt().coerceAtLeast(0)
            measurable.measure(Constraints.fixed(w, h))
        }
        layout(width, height) {
            placeables.forEachIndexed { i, placeable ->
                val p = all[i]
                val x = across(p).first
                // Over the others while it is widened, and while it narrows back.
                placeable.placeRelative(x.roundToInt(), (p.start * perMinute + gapPx / 2).roundToInt(), zIndex = if (p.event.id == wide) 1f else 0f)
            }
        }
    }
}

/** The id of the ghost an edit draws: nothing an app's events could be. */
private object GhostId

/** How far beside an event a finger held still takes it rather than the time it is on. */
internal val NearReach = 12.dp

/**
 * Where an edit in hand would put what it holds, over a period's days: a
 * lifted copy at the proposal — on the step, leaning toward the pointer by the
 * detent's pull, half a step at most, as a range picker's end leans between
 * its days — and hopping to the next step as the pointer crosses into it.
 * One for the page rather than one in each day, so going to the next day is
 * travel across, not a copy going out in one column and another coming in in
 * the next.
 *
 * **Tight, not floaty.** Within a step it moves with the pointer in the same
 * frame; a step crossed is a short hop with no overshoot — see [GhostHop] —
 * from wherever it was drawn. It was a soft spring chasing a target that moved
 * with every move of the pointer, and so a few frames behind it the whole way —
 * reported as *"they don't follow the finger, and they just feel floaty"*.
 *
 * **Read live.** Where it goes is read from the edit's own state in layout,
 * never from what this composed with: the lean is state, and read beside a
 * start a composition behind it, a crossing put the copy back past the step it
 * had left for a frame — *"they feel like they're going backwards"*.
 */
@Composable
private fun TimeGhost(scope: CalendarScope, period: Int, days: Int) {
    val edit = scope.edit
    val start = edit.start
    val end = edit.end
    if (!edit.live || edit.byDays || start == null || end == null) return
    val periods = scope.state.periods
    val pageStart = periods.startOf(period, scope.state.view)
    val pageEnd = pageStart.plus(days - 1, DateTimeUnit.DAY)
    if (lastDayOf(start, end) < pageStart || start.date > pageEnd) return

    val density = LocalDensity.current
    val hourPx = with(density) { scope.hourHeight.toPx() }
    val perMinute = hourPx / MinutesPerHour
    // As the events are drawn: as tall as their time, a step at the least.
    val minChipPx = scope.edit.stepMinutes * perMinute
    val gapPx = with(density) { (Theme.spacing.xxs / 2).toPx() }
    val still = Theme.motion.reduceMotion
    val pull = if (still) 0f else SliderDefaults.DetentPull
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val event = edit.event
    val shown = remember(event, start, end) {
        CalendarEvent(GhostId, event?.title ?: scope.strings.newEvent, start, end, tone = event?.tone ?: Tone.Accent)
    }
    // A piece for each day of it in this page — as composed, for the copies'
    // own content; placed from the edit as it is now.
    val composed = timedPieces(start, end, pageStart, pageEnd)
    val hop = remember { GhostHop() }
    LaunchedEffect(hop) { hop.run() }

    Layout(
        content = {
            for ((_, from, to) in composed) {
                val height = with(density) { (maxOf(minChipPx, (to - from) * perMinute) - gapPx).coerceAtLeast(0f).toDp() }
                EventChip(event = shown, presentation = EventPresentation.Block, scope = scope, ghost = true, blockHeight = height)
            }
        },
        modifier = Modifier.fillMaxSize(),
    ) { measurables, constraints ->
        val w = constraints.maxWidth
        val h = constraints.maxHeight
        val nowStart = edit.start
        val nowEnd = edit.end
        if (nowStart == null || nowEnd == null || w <= 0) return@Layout layout(w, h) {}
        val column = w.toFloat() / days
        val pieces = timedPieces(nowStart, nowEnd, pageStart, pageEnd)
        val firstDay = (nowStart.date.toEpochDays() - pageStart.toEpochDays()).toInt()
        fun columnLeft(day: Int) = if (rtl) w - column * (day + 1) else column * day
        fun box(day: Int, from: Int, to: Int): Rect {
            val top = from * perMinute
            return Rect(columnLeft(day), top, columnLeft(day) + column, top + maxOf(minChipPx, (to - from) * perMinute))
        }
        // One day's box, leaning: the first day's, all of it, for a move; the
        // edge in hand for a resize or a new event drawn out — the last day's,
        // for an end dragged on an event that runs past midnight.
        val lastDay = (lastDayOf(nowStart, nowEnd).toEpochDays() - pageStart.toEpochDays()).toInt()
        val leanDay = if (edit.leanOn == 1) lastDay else firstDay
        val leanFrom = if (leanDay == firstDay) nowStart.time.minuteOfDay else 0
        val leanTo = if (leanDay == lastDay && nowEnd.date == lastDayOf(nowStart, nowEnd)) nowEnd.time.minuteOfDay else MinutesPerDay
        val snapped = box(leanDay, leanFrom, maxOf(leanTo, leanFrom))
        val lean = edit.lean * pull
        val across = if (rtl) -lean.x else lean.x
        val target = when (edit.leanOn) {
            -1 -> snapped.copy(top = snapped.top + lean.y)
            1 -> snapped.copy(bottom = snapped.bottom + lean.y)
            else -> snapped.translate(across, lean.y)
        }
        val drawn = hop.drawnAt(nowStart to nowEnd, target, still)
        // Every other day of it moved as that one is, for a move; an edge
        // dragged moves its own day alone.
        val moving = edit.leanOn == 0
        val dx = if (moving) drawn.left - snapped.left else 0f
        val dy = if (moving) drawn.top - snapped.top else 0f
        // Across its day as the event under it is: opening out from its lane
        // to the whole day as the event does, rather than standing at the
        // day's width from the first frame of a hold.
        val f = if (event != null && edit.widened == event.id) edit.widen.value else 1f
        val (laneStart, laneWidth) = edit.widenedLane
        val inset = column * (if (rtl) 1f - laneStart - laneWidth else laneStart) * (1f - f)
        val pieceWidth = column * (laneWidth + (1f - laneWidth) * f)
        val placeables = measurables.take(pieces.size).mapIndexed { i, measurable ->
            val (day, from, to) = pieces[i]
            val rect = if (day == leanDay) drawn else box(day, from, to)
            measurable.measure(Constraints.fixed((pieceWidth - gapPx).roundToInt().coerceAtLeast(0), (rect.height - gapPx).roundToInt().coerceAtLeast(0)))
        }
        layout(w, h) {
            placeables.forEachIndexed { i, placeable ->
                val (day, from, to) = pieces[i]
                val rect = if (day == leanDay) drawn else box(day, from, to).translate(dx, dy)
                placeable.place((rect.left + inset + gapPx / 2).roundToInt(), (rect.top + gapPx / 2).roundToInt())
            }
        }
    }
}

/** The day an event's last minute is on. */
private fun lastDayOf(start: LocalDateTime, end: LocalDateTime): LocalDate = if (end > start) end.plusMinutes(-1).date else start.date

/** The days of an event between [pageStart] and [pageEnd]: each day's column and its minutes, from its start or midnight to its end or midnight. */
private fun timedPieces(start: LocalDateTime, end: LocalDateTime, pageStart: LocalDate, pageEnd: LocalDate): List<Triple<Int, Int, Int>> = buildList {
    val lastDay = lastDayOf(start, end)
    var day = maxOf(start.date, pageStart)
    while (day <= minOf(lastDay, pageEnd)) {
        val from = if (day == start.date) start.time.minuteOfDay else 0
        val to = if (day == lastDay && end.date == day) end.time.minuteOfDay else MinutesPerDay
        add(Triple((day.toEpochDays() - pageStart.toEpochDays()).toInt(), from, maxOf(to, from)))
        day = day.plus(1, DateTimeUnit.DAY)
    }
}

/** An empty time pressed is the quarter-hour it is in. */
private const val TapStepMinutes = 15

/** The dot at the start of the line for now, against the line's own thickness. */
private const val NowDotScale = 2.5f
