package io.kontour.ui.components.calendar

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.kontour.ui.input.Cursor
import io.kontour.ui.input.pointerCursor
import io.kontour.ui.a11y.minimumTouchTarget
import io.kontour.ui.motion.ScrollDefaults
import io.kontour.ui.theme.Theme
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/**
 * [CalendarDaysStyle.Condensed]: no hours, and each day's events stacked down
 * the days — all-day ones as bars across their days, timed ones as their
 * line — packed into the lowest row free across every day each covers, as a
 * month's week is but with no row cap, in a list that scrolls: asked for as
 * *"option to hide the hourly grid and only show all-day events (condensed
 * list per day)"*. Paged sideways with the headings above it.
 *
 * A day's empty space opens it, held makes a day, or days; an event is
 * carried by whole days, its time kept, and the list scrolls under a carry
 * held near its top or foot.
 */
@Composable
internal fun CondensedDays(scope: CalendarScope, days: Int, modifier: Modifier) {
    val state = scope.state
    val frame = Theme.shapes.container
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Box(
        modifier
            .fillMaxWidth()
            .clip(frame)
            .border(Theme.sizing.borderWidth, gridLine(), frame)
            .calendarCursor(scope, description = scope.dayDescription(state.date)) { key -> scope.monthKey(key, rtl) },
    ) {
        PeriodStrip(scope.pager, Modifier.fillMaxSize()) { period -> CondensedPage(scope, period, days) }
    }
}

@Composable
private fun CondensedPage(scope: CalendarScope, period: Int, days: Int) {
    val state = scope.state
    val from = state.periods.startOf(period, state.view)
    val to = from.plus(days - 1, DateTimeUnit.DAY)
    val pack = remember(scope.events, from, days) {
        packWeek(weekItems(from, scope.eventsBetween(from, to), lines = true, days = days), maxRows = Int.MAX_VALUE, days = days)
    }
    // Each row's events, so the list composes only the rows on screen.
    val rows = remember(pack) { pack.placed.groupBy { it.row } }
    val count = (pack.placed.maxOfOrNull { it.row } ?: -1) + 1
    // Two lines an event, a list's worth of room: its title, then its time or
    // where it is, as a planner's bar is.
    val item = with(LocalDensity.current) { Theme.typography.labelSmall.lineHeight.toDp() } * CondensedLines + Theme.spacing.xxs * 2
    val gap = Theme.spacing.xxs / 2
    val list = rememberLazyListState()
    val placed = remember { PlacedAt() }
    val line = Theme.sizing.borderWidth
    val lineColour = gridLine()
    val ringColour = Theme.colours.focusRing
    val ringWidth = Theme.sizing.focusRingWidth
    val cursorShown = scope.cursorShown
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { placed.coordinates = it }
            .holdsDays(scope, placed, from)
            // A day's empty space, pressed: the day, as a month's box opens it.
            .pointerInput(from, days, scope.enabled, rtl) {
                if (!scope.enabled) return@pointerInput
                detectTapGestures { at ->
                    val column = (at.x / (size.width.toFloat() / days)).toInt().coerceIn(0, days - 1)
                    val reading = if (rtl) days - 1 - column else column
                    scope.openDay(from.plus(reading, DateTimeUnit.DAY))
                }
            }
            .drawWithContent {
                // A line between each day and the next, the length of the list.
                val width = line.toPx()
                val cell = size.width / days
                for (column in 1 until days) {
                    val x = cell * column
                    drawLine(lineColour, Offset(x, 0f), Offset(x, size.height), width)
                }
                drawContent()
                // The keyboard's day, round its whole column.
                val date = state.date
                if (!cursorShown || date < from || date > to) return@drawWithContent
                val column = (date.toEpochDays() - from.toEpochDays()).toInt()
                val left = if (layoutDirection == LayoutDirection.Rtl) size.width - cell * (column + 1) else cell * column
                drawCursorRing(Rect(left, 0f, left + cell, size.height), ringColour, ringWidth.toPx())
            },
    ) {
        val cell = maxWidth / days
        // Each day's place, the list's whole height, for an event carried by whole days.
        Row(Modifier.fillMaxSize()) {
            for (column in 0 until days) {
                DayTarget(scope, from.plus(column, DateTimeUnit.DAY), Modifier.weight(1f).fillMaxHeight())
            }
        }
        CompositionLocalProvider(LocalItemLines provides CondensedLines) {
            CondensedRows(scope, list, count, rows, item, gap, cell, from)
        }
        DaysGhostLayer(scope, from, weeks = 1, days = days, slot = gap / 2, item = item, gap = gap)
        EditScroll(scope, placed, list, byDays = true)
    }
}

/** How many lines each event in a condensed list has. */
private const val CondensedLines = 2

@Composable
private fun CondensedRows(
    scope: CalendarScope,
    list: LazyListState,
    count: Int,
    rows: Map<Int, List<WeekPlacement>>,
    item: Dp,
    gap: Dp,
    cell: Dp,
    from: LocalDate,
) {
    LazyColumn(
        state = list,
        flingBehavior = ScrollDefaults.flingBehavior(),
        userScrollEnabled = scope.enabled,
        contentPadding = PaddingValues(top = Theme.spacing.xxs, bottom = item),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(count) { row ->
            Box(Modifier.fillMaxWidth().height(item)) {
                for (placement in rows[row].orEmpty()) {
                    val shown = placement.item
                    EventChip(
                        event = shown.event,
                        presentation = if (shown.bar) EventPresentation.Bar else EventPresentation.Line,
                        scope = scope,
                        clippedStart = shown.continuesBefore,
                        clippedEnd = shown.continuesAfter,
                        focusableOn = from.plus(shown.from, DateTimeUnit.DAY)..from.plus(shown.to, DateTimeUnit.DAY),
                        modifier = Modifier
                            .padding(start = cell * shown.from + gap, top = gap / 2)
                            .width(cell * (shown.to - shown.from + 1) - gap * 2)
                            .height(item - gap),
                    )
                }
            }
        }
    }
}

/**
 * The divider between [CalendarDaysStyle.Split]'s all-day pane and its hours:
 * dragged, it moves, the pane taking [share] of [total] pixels, from a tenth
 * to seven tenths. Under touch it reserves a fingertip; for a mouse, it
 * thickens on hover and shows the resize cursor. A screen reader has it as a
 * range it can set, as a pane divider is.
 */
@Composable
internal fun SplitHandle(share: Float, total: Float, onShare: (Float) -> Unit) {
    val colours = Theme.colours
    val label = Theme.strings.resizePanes
    val interactions = remember { MutableInteractionSource() }
    val hovered by interactions.collectIsHoveredAsState()
    val lineDp = Theme.sizing.dividerThickness.value
    val thickness by animateFloatAsState(if (hovered) lineDp * 3f else lineDp, Theme.motion.tweenFast(), label = "splitHandle")
    val current by rememberUpdatedState(share)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .minimumTouchTarget()
            .fillMaxWidth()
            .height(SplitHandleHeight)
            .pointerCursor(Cursor.ResizeRow)
            .hoverable(interactions)
            .semantics {
                contentDescription = label
                progressBarRangeInfo = ProgressBarRangeInfo(share, MinSplitShare..MaxSplitShare)
                setProgress { target ->
                    onShare(target.coerceIn(MinSplitShare, MaxSplitShare))
                    true
                }
            }
            .pointerInput(total) {
                detectVerticalDragGestures { change, amount ->
                    change.consume()
                    if (total > 0f) onShare((current + amount / total).coerceIn(MinSplitShare, MaxSplitShare))
                }
            },
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(thickness.dp)
                .background(if (hovered) colours.accent.solid else colours.outline),
        )
    }
}

/** How tall the split's divider is, its line in the middle. */
private val SplitHandleHeight = 12.dp

/** The least of the height a split's all-day pane has. */
internal const val MinSplitShare = 0.1f

/** The most of the height a split's all-day pane has. */
internal const val MaxSplitShare = 0.7f
