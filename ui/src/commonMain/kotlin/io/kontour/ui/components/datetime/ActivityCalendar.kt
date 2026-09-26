package io.kontour.ui.components.datetime

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorPainter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import io.kontour.ui.a11y.contentColourFor
import io.kontour.ui.a11y.contrastEdge
import io.kontour.ui.components.list.fadingEdges
import io.kontour.ui.foundation.Text
import io.kontour.ui.input.pointerCursor
import io.kontour.ui.input.rememberFocusRingVisible
import io.kontour.ui.interaction.rememberDetentTicker
import io.kontour.ui.interaction.rememberLongPressFeedback
import io.kontour.ui.interaction.rememberTapFeedback
import io.kontour.ui.overlay.OverlayAlignment
import io.kontour.ui.overlay.OverlaySide
import io.kontour.ui.overlay.TooltipDefaults
import io.kontour.ui.overlay.TooltipOverlay
import io.kontour.ui.theme.SquircleShape
import io.kontour.ui.theme.Theme
import kotlin.math.ceil
import kotlin.math.floor
import kotlinx.coroutines.delay
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** How an [ActivityCalendar] turns a day's count into a shade. */
@Immutable
sealed interface ActivityLevels {
    /**
     * GitHub's rule: the days with any activity split into equal-sized groups,
     * one per shade, so a busy year and a quiet one both use every shade. The
     * shades say which days were busier *than the others*, not by how much.
     */
    data object Quantiles : ActivityLevels

    /**
     * Fixed steps: the first shade from `thresholds[0]` up, the second from
     * `thresholds[1]`, and so on — one threshold per shade after the empty one,
     * ascending. For a count whose numbers mean something on their own: ten
     * thousand steps is a good day whatever the rest of the year was like.
     */
    @Immutable
    data class Thresholds(val thresholds: List<Int>) : ActivityLevels
}

/**
 * The colours of an [ActivityCalendar].
 *
 * @param levels A shade per level, from `levels[0]` — no activity at all — to the
 *   busiest. As many levels as there are shades after the first.
 * @param label The month and weekday labels, and the legend's words.
 * @param selection The ring round the selected day.
 * @param today The inner ring on today.
 */
@Immutable
data class ActivityCalendarColours(
    val levels: List<Color>,
    val label: Color,
    val selection: Color,
    val today: Color,
)

/**
 * What an [ActivityCalendar] draws on one day as well as its shade: a holiday's
 * corner, a trip's icon, a count spelled out on a busy day. Any of the parts
 * can be combined; leave out the ones a day does not need.
 *
 * @param icon A glyph in the middle of the cell, about three fifths its size.
 * @param text A few characters in the middle of the cell — a count, a letter —
 *   drawn small, and smaller still if they would not fit. Ignored with [icon].
 * @param corner A dog-ear: the cell's top end corner folded over, in this colour.
 * @param dot A small dot under the middle of the cell, in this colour.
 * @param outline A ring inside the cell's edge, in this colour.
 * @param fill The cell's colour in place of its shade.
 * @param contentColour The icon's or text's colour. Unspecified picks whichever
 *   of light and dark reads better on the cell.
 * @param description What the mark means, in words: said after the day's count
 *   in its tooltip and to a screen reader, since a corner or an icon is not.
 */
@Immutable
data class ActivityMark(
    val icon: ImageVector? = null,
    val text: String? = null,
    val corner: Color = Color.Unspecified,
    val dot: Color = Color.Unspecified,
    val outline: Color = Color.Unspecified,
    val fill: Color = Color.Unspecified,
    val contentColour: Color = Color.Unspecified,
    val description: String? = null,
)

object ActivityCalendarDefaults {
    /** A year and a bit: every day of the last twelve months, in whole weeks. */
    val Weeks: Int get() = ActivityWeeks

    /** The space between cells. */
    val CellGap: Dp get() = ActivityCellGap

    /** A rounded square, with the library's corner curve at a fifth of its side. */
    val CellShape: Shape get() = ActivityCellShape

    /**
     * [levels] shades after the empty one, blended from [empty] to [full].
     *
     * The first is a little way along rather than a step from [empty], so a day
     * with one of something is plainly not a day with none. For the look of a
     * particular app — GitHub's greens, a blue — pass [full]; for shades that do
     * not blend, build [ActivityCalendarColours] with a list of your own.
     */
    @Composable
    @ReadOnlyComposable
    fun colours(
        levels: Int = ActivityLevelCount,
        empty: Color = Theme.colours.surfaceSunken,
        full: Color = Theme.colours.primary,
        label: Color = Theme.colours.contentMuted,
        selection: Color = Theme.colours.content,
        today: Color = Theme.colours.outlineStrong,
    ): ActivityCalendarColours = ActivityCalendarColours(levelRamp(levels, empty, full), label, selection, today)

    /**
     * What a day says in its tooltip and to a screen reader: its count and its
     * date in full, from [io.kontour.ui.theme.Strings.activityOnDay].
     */
    @Composable
    fun describe(formats: DateTimeFormats = LocalDateTimeFormats.current): (date: LocalDate, count: Int) -> String {
        val strings = Theme.strings
        return remember(strings, formats) { { date, count -> strings.activityOnDay(count, formats.dateFull(date)) } }
    }
}

/**
 * A year of something at a glance: a day to a cell, a week to a column, each
 * cell shaded by how much happened that day — GitHub's contribution graph.
 *
 * ```kotlin
 * ActivityCalendar(
 *     activity = tripsByDay,          // Map<LocalDate, Int>
 *     end = today,
 *     selected = picked,
 *     onDayClick = { picked = it },
 * )
 * ```
 *
 * Month names run along the top and Mon, Wed and Fri down the side, with a
 * legend from "Less" to "More" underneath — or none of them, for a quiet strip
 * of cells on a profile:
 *
 * ```kotlin
 * ActivityCalendar(
 *     activity = days,
 *     end = today,
 *     monthLabels = false,
 *     weekdayLabels = false,
 *     legend = false,
 *     colours = ActivityCalendarDefaults.colours(full = Theme.colours.info.solid),
 * )
 * ```
 *
 * ### The range
 *
 * [weeks] columns, the last holding [end] and stopping at it, so the last column
 * is as long as the week has been so far. [start] leaves out the days before it:
 * a calendar for an account a few months old. The library never reads the clock;
 * [end] and [today] are the caller's.
 *
 * ### Size
 *
 * Cells fit the width available, between 20dp and 28dp, so with the gap every
 * day is at least a 24dp target. A width that would need them smaller scrolls
 * sideways instead, opening at the most recent week, which is the end anybody
 * looks at first. [cellSize] fixes them — smaller for an overview that has to
 * fit a year and is not for picking from.
 *
 * ### Marks
 *
 * [markFor] decorates days: a corner folded over for a holiday, an icon for a
 * trip, a count written out on a busy day. See [ActivityMark].
 *
 * ### Touch, pointer and keys
 *
 * With [onDayClick] a tap picks a day. A pointer resting on a cell, a long press
 * on one, or the keyboard's cursor shows the day's count in a tooltip. On touch,
 * a long press can then slide: the tooltip follows the finger a day at a time,
 * and lifting picks the day under it — a way onto the right day without having
 * to tap it exactly. Slide off the grid before lifting to pick nothing. The
 * arrow keys move by a day down a column and a week across, Home and End go to
 * the first and last day, and Enter picks.
 *
 * @param activity How much happened each day. Days not in the map had nothing.
 * @param end The last day shown.
 * @param enabled Whether the days can be picked. The calendar is drawn the same.
 * @param weeks How many columns.
 * @param start The first day shown, if the range starts partway through the
 *   first column.
 * @param selected The day picked, drawn with a ring.
 * @param onDayClick Makes the days pickable. Without it the calendar is a picture
 *   with tooltips.
 * @param today Drawn with an inner ring.
 * @param markFor What to draw on a day besides its shade, given its date and
 *   count, or null for nothing. Asked once per day shown.
 * @param levels How counts become shades.
 * @param colours The shades, labels and rings.
 * @param cellSize A fixed cell size. Unspecified fits the width.
 * @param cellGap The space between cells.
 * @param cellShape Each cell's shape.
 * @param monthLabels Month names along the top.
 * @param weekdayLabels Mon, Wed and Fri down the start side.
 * @param legend "Less", the shades, "More", under the grid.
 * @param formats Names the days, for the tooltip and the screen reader.
 * @param firstDayOfWeek The day each column starts on.
 * @param describe What a day says, given its count.
 * @param interactionSource Observes the calendar's focus.
 */
@Composable
fun ActivityCalendar(
    activity: Map<LocalDate, Int>,
    end: LocalDate,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    weeks: Int = ActivityCalendarDefaults.Weeks,
    start: LocalDate? = null,
    selected: LocalDate? = null,
    onDayClick: ((LocalDate) -> Unit)? = null,
    today: LocalDate? = null,
    markFor: ((date: LocalDate, count: Int) -> ActivityMark?)? = null,
    levels: ActivityLevels = ActivityLevels.Quantiles,
    colours: ActivityCalendarColours = ActivityCalendarDefaults.colours(),
    cellSize: Dp = Dp.Unspecified,
    cellGap: Dp = ActivityCalendarDefaults.CellGap,
    cellShape: Shape = ActivityCalendarDefaults.CellShape,
    monthLabels: Boolean = true,
    weekdayLabels: Boolean = true,
    legend: Boolean = true,
    formats: DateTimeFormats = LocalDateTimeFormats.current,
    firstDayOfWeek: DayOfWeek = formats.firstDayOfWeek,
    describe: (date: LocalDate, count: Int) -> String = ActivityCalendarDefaults.describe(formats),
    interactionSource: MutableInteractionSource? = null,
) {
    val columns = weeks.coerceAtLeast(1)
    val grid = remember(end, columns, start, firstDayOfWeek) { ActivityGrid(end, columns, start, firstDayOfWeek) }
    val shadeCount = (colours.levels.size - 1).coerceAtLeast(1)
    // The shade of every cell, worked out once per change of data: -1 for a
    // cell outside the range, which is not drawn.
    val shades = remember(activity, grid, levels, shadeCount) {
        val bounds = when (levels) {
            ActivityLevels.Quantiles -> quantileBounds(activity.filterKeys { grid.contains(it) }.values, shadeCount)
            is ActivityLevels.Thresholds -> levels.thresholds.take(shadeCount).toIntArray()
        }
        IntArray(columns * 7) { index ->
            val date = grid.dateAt(index / 7, index % 7)
            if (date == null) -1 else levelOf(activity[date] ?: 0, bounds).coerceAtMost(shadeCount)
        }
    }

    // Each shown day's mark, in the same order as the shades.
    val marks = remember(activity, grid, markFor) {
        if (markFor == null) {
            null
        } else {
            Array(columns * 7) { index ->
                grid.dateAt(index / 7, index % 7)?.let { markFor(it, activity[it] ?: 0) }
            }
        }
    }
    // Remembered, so the grid's draw cache below — which captures it — is the
    // same lambda from one composition to the next and is not rebuilt, month
    // labels re-shaped and all, whenever anything else here recomposes.
    val painters = remember { HashMap<ImageVector, VectorPainter>() }
    marks?.mapNotNullTo(LinkedHashSet()) { it?.icon }?.forEach { icon ->
        key(icon) { painters[icon] = rememberVectorPainter(icon) }
    }
    fun say(date: LocalDate, count: Int): String {
        val words = describe(date, count)
        val meaning = markFor?.invoke(date, count)?.description ?: return words
        return "$words. $meaning"
    }

    val labelStyle = Theme.typography.labelSmall
    // Room for every label the grid draws — twelve months, three weekdays and
    // the marks — so rebuilding the draw cache finds them already shaped. The
    // default of eight evicted the months on every rebuild.
    val measurer = rememberTextMeasurer(cacheSize = 32)
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val rtl = layoutDirection == LayoutDirection.Rtl
    val strings = Theme.strings
    val interactive = enabled && onDayClick != null
    val interactions = interactionSource ?: remember { MutableInteractionSource() }
    val ringVisible = rememberFocusRingVisible(interactions)
    val focusColour = Theme.colours.focusRing
    val focusWidth = Theme.sizing.focusRingWidth
    val ringWidth = Theme.sizing.borderWidthStrong
    val hoverColour = Theme.colours.outlineStrong
    val edge = contrastEdge()
    val labelSpace = Theme.spacing.xs

    // Where the keyboard's cursor is: the selected day to begin with, or the
    // last one, and always a day that is shown.
    var cursor by remember { mutableStateOf<LocalDate?>(null) }
    val focusedDay = (cursor ?: selected ?: end).coerceIn(grid.firstShown, end)
    var hoverDay by remember { mutableStateOf<LocalDate?>(null) }
    var pressDay by remember { mutableStateOf<LocalDate?>(null) }
    var hoverShown by remember { mutableStateOf(false) }
    val ticker = rememberDetentTicker()
    // A day tapped answers like a calendar's day does — a tap, if it is a change —
    // and the scrub's long press announces itself as every long press does.
    val tap = rememberTapFeedback()
    val longPressed = rememberLongPressFeedback()
    val currentSelected by rememberUpdatedState(selected)
    val pick by rememberUpdatedState(if (interactive) onDayClick else null)
    BoxWithConstraints(modifier) {
        val weekdayWidth = if (weekdayLabels) {
            with(density) {
                listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)
                    .maxOf { measurer.measure(it.shortName, labelStyle).size.width }
                    .toDp() + labelSpace
            }
        } else {
            0.dp
        }
        val cell = if (cellSize.isSpecified) {
            cellSize
        } else if (constraints.hasBoundedWidth) {
            ((maxWidth - weekdayWidth - cellGap * (columns - 1)) / columns).coerceIn(MinCell, MaxCell)
        } else {
            MaxCell
        }
        val monthBand = if (monthLabels) {
            with(density) { measurer.measure("May", labelStyle).size.height.toDp() } + labelSpace
        } else {
            0.dp
        }
        val pitch = cell + cellGap
        val gridWidth = cell * columns + cellGap * (columns - 1)
        val gridHeight = monthBand + cell * 7 + cellGap * 6
        val scroll = rememberScrollState(Int.MAX_VALUE)
        val coordinates = remember { arrayOfNulls<LayoutCoordinates>(1) }

        fun hit(at: Offset): LocalDate? = with(density) {
            val along = if (rtl) gridWidth.toPx() - at.x else at.x
            val column = floor(along / pitch.toPx()).toInt()
            val row = floor((at.y - monthBand.toPx()) / pitch.toPx()).toInt()
            grid.dateAt(column, row)
        }

        fun cellRect(date: LocalDate): Rect? = with(density) {
            val (column, row) = grid.cellOf(date) ?: return null
            val size = cell.toPx()
            val along = column * pitch.toPx()
            val left = if (rtl) gridWidth.toPx() - along - size else along
            Rect(Offset(left, monthBand.toPx() + row * pitch.toPx()), Size(size, size))
        }

        // The keyboard's cursor brought into view as it moves.
        LaunchedEffect(focusedDay, ringVisible) {
            if (!ringVisible) return@LaunchedEffect
            val rect = cellRect(focusedDay) ?: return@LaunchedEffect
            val viewport = scroll.viewportSize
            val left = rect.left.toInt()
            val right = rect.right.toInt()
            when {
                left < scroll.value -> scroll.animateScrollTo(left)
                right > scroll.value + viewport -> scroll.animateScrollTo(right - viewport)
            }
        }

        Column {
            Row {
                if (weekdayLabels) {
                    WeekdayLabels(
                        modifier = Modifier.size(weekdayWidth, gridHeight),
                        grid = grid,
                        top = monthBand,
                        cell = cell,
                        pitch = pitch,
                        colour = colours.label,
                    )
                }
                Box(
                    Modifier
                        .semantics {
                            isTraversalGroup = true
                            collectionInfo = CollectionInfo(rowCount = 1, columnCount = columns)
                        }
                        .onKeyEvent { event ->
                            if (!interactive || event.type != KeyEventType.KeyDown) return@onKeyEvent false
                            val next = when (event.key) {
                                Key.DirectionRight -> focusedDay.plus(DatePeriod(days = if (rtl) -7 else 7))
                                Key.DirectionLeft -> focusedDay.minus(DatePeriod(days = if (rtl) -7 else 7))
                                Key.DirectionDown -> focusedDay.plus(DatePeriod(days = 1))
                                Key.DirectionUp -> focusedDay.minus(DatePeriod(days = 1))
                                Key.MoveHome -> grid.firstShown
                                Key.MoveEnd -> end
                                Key.Enter, Key.NumPadEnter, Key.Spacebar -> {
                                    onDayClick(focusedDay)
                                    return@onKeyEvent true
                                }
                                else -> return@onKeyEvent false
                            }
                            cursor = next.coerceIn(grid.firstShown, end)
                            true
                        }
                        // On the viewport rather than the grid inside it: a focused
                        // grid would ask the scroller to bring all of itself into
                        // view, which is its oldest week. The cursor scrolls itself.
                        .focusable(enabled = interactive, interactionSource = interactions),
                ) {
                    Layout(
                        content = {
                            repeat(columns) { week ->
                                WeekNode(
                                    week = week,
                                    grid = grid,
                                    activity = activity,
                                    marks = marks,
                                    formats = formats,
                                    weekOf = strings.weekOf,
                                    describe = ::say,
                                    selected = selected,
                                    onDayClick = if (interactive) onDayClick else null,
                                )
                            }
                        },
                        modifier = Modifier
                            .fadingEdges(scroll, Orientation.Horizontal)
                            .horizontalScroll(scroll)
                            // Squeezed shorter than it is, the grid runs off the
                            // bottom rather than centring, so its rows stay level
                            // with the weekday labels beside them.
                            .wrapContentHeight(Alignment.Top, unbounded = true)
                            .requiredSize(gridWidth, gridHeight)
                            .onGloballyPositioned { coordinates[0] = it }
                            .pointerCursor(enabled = interactive)
                            .pointerInput(grid, rtl, cell, cellGap, monthBand) {
                                detectTapGestures(
                                    onTap = { at ->
                                        val day = hit(at) ?: return@detectTapGestures
                                        pressDay = null
                                        pick?.let {
                                            if (day != currentSelected) tap()
                                            cursor = day
                                            it(day)
                                        }
                                    },
                                    // The scrub below has long presses; claimed
                                    // here so that lifting after one is not a tap.
                                    onLongPress = {},
                                )
                            }
                            .pointerInput(grid, rtl, cell, cellGap, monthBand) {
                                // Long press, then slide: the tooltip follows the
                                // finger day by day, and lifting picks the day
                                // under it. Off the grid, nothing is under it.
                                fun scrubTo(at: Offset) {
                                    val day = hit(at)
                                    pressDay = day
                                    day?.let { grid.cellOf(it) }?.let { (column, row) -> ticker.at(column * 7 + row) }
                                }
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { at ->
                                        // Whether or not a day can be picked:
                                        // the bubble is a touch tooltip, and a
                                        // tooltip's long press reports itself.
                                        longPressed()
                                        ticker.reset()
                                        scrubTo(at)
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        scrubTo(change.position)
                                    },
                                    onDragEnd = {
                                        ticker.reset()
                                        val day = pressDay
                                        val action = pick
                                        if (day != null && action != null) {
                                            cursor = day
                                            action(day)
                                        }
                                    },
                                    onDragCancel = {
                                        ticker.reset()
                                        pressDay = null
                                    },
                                )
                            }
                            .pointerInput(grid, rtl, cell, cellGap, monthBand) {
                                awaitPointerEventScope {
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull() ?: continue
                                        if (change.type != PointerType.Mouse) continue
                                        when (event.type) {
                                            PointerEventType.Move, PointerEventType.Enter ->
                                                hoverDay = hit(change.position)
                                            PointerEventType.Exit -> hoverDay = null
                                        }
                                    }
                                }
                            }
                            .drawWithCache {
                                val size = cell.toPx()
                                val step = pitch.toPx()
                                val band = monthBand.toPx()
                                val width = gridWidth.toPx()
                                val outline = cellShape.createOutline(Size(size, size), layoutDirection, this)
                                fun grown(by: Float): Outline =
                                    cellShape.createOutline(Size(size + by * 2, size + by * 2), layoutDirection, this)
                                val ring = ringWidth.toPx()
                                val selectionRing = grown(ring / 2f + OneDp.toPx())
                                val focusRing = grown(focusWidth.toPx() / 2f + OneDp.toPx())
                                val hoverRing = grown(OneDp.toPx() / 2f)
                                val todayInset = OneDp.toPx() * 1.5f
                                val todayRing = cellShape.createOutline(
                                    Size(size - todayInset * 2, size - todayInset * 2), layoutDirection, this,
                                )
                                val months = if (monthLabels) {
                                    val layouts = Month.entries.associateWith {
                                        measurer.measure(it.shortName, labelStyle)
                                    }
                                    grid.monthLabels { month ->
                                        ceil((layouts.getValue(month).size.width + labelSpace.toPx()) / step).toInt()
                                    }.map { (column, month) -> column to layouts.getValue(month) }
                                } else {
                                    emptyList()
                                }
                                fun leftOf(column: Int): Float = if (rtl) width - column * step - size else column * step
                                val cellPath = Path().apply { addOutline(outline) }
                                val ear = size * DogEarShare
                                val dogEar = Path().apply {
                                    // The top end corner: right, or left right to left.
                                    val edge = if (rtl) 0f else size
                                    val inward = if (rtl) ear else size - ear
                                    moveTo(inward, 0f)
                                    lineTo(edge, 0f)
                                    lineTo(edge, ear)
                                    close()
                                }
                                val iconSize = size * MarkIconShare
                                val markTexts = marks?.mapNotNullTo(LinkedHashSet()) { it?.text }?.associateWith { text ->
                                    val room = size - MarkTextPadding.toPx() * 2
                                    val natural = measurer.measure(text, labelStyle, maxLines = 1, softWrap = false)
                                    if (natural.size.width <= room || natural.size.width == 0) {
                                        natural
                                    } else {
                                        measurer.measure(
                                            text,
                                            labelStyle.copy(fontSize = labelStyle.fontSize * (room / natural.size.width)),
                                            maxLines = 1,
                                            softWrap = false,
                                        )
                                    }
                                }.orEmpty()
                                val markRing = cellShape.createOutline(
                                    Size(size - MarkRingInset.toPx() * 2, size - MarkRingInset.toPx() * 2),
                                    layoutDirection,
                                    this,
                                )

                                onDrawBehind {
                                    months.forEach { (column, text) ->
                                        val x = if (rtl) width - column * step - text.size.width else column * step
                                        drawText(text, colours.label, topLeft = Offset(x, 0f))
                                    }
                                    for (column in 0 until columns) {
                                        val x = leftOf(column)
                                        for (row in 0..6) {
                                            val shade = shades[column * 7 + row]
                                            if (shade < 0) continue
                                            val mark = marks?.get(column * 7 + row)
                                            val ground = mark?.fill?.takeIf { it.isSpecified } ?: colours.levels[shade]
                                            translate(x, band + row * step) {
                                                drawOutline(outline, ground)
                                                if (edge != null) {
                                                    drawOutline(outline, edge.brush, style = Stroke(edge.width.toPx()))
                                                }
                                                if (mark != null) {
                                                    drawMark(
                                                        mark, size, ground, cellPath, dogEar, markRing,
                                                        painters[mark.icon], iconSize, mark.text?.let { markTexts[it] },
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    fun ringAt(date: LocalDate?, shape: Outline, grow: Float, colour: Color, stroke: Float) {
                                        val (column, row) = date?.let { grid.cellOf(it) } ?: return
                                        translate(leftOf(column) - grow, band + row * step - grow) {
                                            drawOutline(shape, colour, style = Stroke(stroke))
                                        }
                                    }
                                    ringAt(today, todayRing, -todayInset, colours.today, OneDp.toPx())
                                    ringAt(hoverDay, hoverRing, OneDp.toPx() / 2f, hoverColour, OneDp.toPx())
                                    ringAt(selected, selectionRing, ring / 2f + OneDp.toPx(), colours.selection, ring)
                                    if (ringVisible) {
                                        ringAt(
                                            focusedDay, focusRing, focusWidth.toPx() / 2f + OneDp.toPx(),
                                            focusColour, focusWidth.toPx(),
                                        )
                                    }
                                }
                            },
                    ) { measurables, _ ->
                        val size = cell.roundToPx()
                        val step = pitch.toPx()
                        val band = monthBand.roundToPx()
                        val columnHeight = (cell * 7 + cellGap * 6).roundToPx()
                        val placeables = measurables.map { it.measure(Constraints.fixed(size, columnHeight)) }
                        layout(gridWidth.roundToPx(), gridHeight.roundToPx()) {
                            placeables.forEachIndexed { week, placeable ->
                                placeable.placeRelative((week * step).toInt(), band)
                            }
                        }
                    }
                }
            }
            if (legend) {
                Legend(
                    modifier = Modifier.align(Alignment.End).padding(top = labelSpace),
                    colours = colours,
                    cell = cell,
                    gap = cellGap,
                    shape = cellShape,
                    less = strings.activityLess,
                    more = strings.activityMore,
                )
            }
        }

        // The pointer and the press are read in here and nowhere else in
        // composition. They change on every cell a pointer crosses, and read out
        // there they recomposed the whole calendar — label measures, layout and
        // all — for a ring the grid draws on its own.
        OwnScope {
            // A pointer has to rest before the first tooltip, as everywhere else;
            // moving from one cell to the next with one already showing moves it
            // straight away.
            LaunchedEffect(hoverDay == null) {
                if (hoverDay == null) {
                    hoverShown = false
                } else {
                    delay(TooltipDefaults.HoverDelayMillis)
                    hoverShown = true
                }
            }

            // The day a tooltip is about: a long press's, a resting pointer's, or
            // the keyboard's cursor. None while the grid is scrolling under it.
            val tip = when {
                scroll.isScrollInProgress -> null
                pressDay != null -> pressDay
                hoverShown -> hoverDay
                ringVisible -> focusedDay
                else -> null
            }
            if (tip != null) {
                val anchor = cellRect(tip)?.let { rect ->
                    coordinates[0]?.takeIf { it.isAttached }?.let { Rect(it.localToRoot(rect.topLeft), rect.size) }
                }
                TooltipOverlay(
                    visible = true,
                    anchor = { anchor },
                    content = { +say(tip, activity[tip] ?: 0) },
                    modifier = Modifier,
                    side = OverlaySide.Top,
                    alignment = OverlayAlignment.Centre,
                    onDismissRequest = { pressDay = null },
                )
            }
        }
    }
}

/** Composes [content] in a restart scope of its own, so what it reads recomposes only it. */
@Composable
private fun OwnScope(content: @Composable () -> Unit) {
    content()
}

/** Mon, Wed and Fri, level with their rows, down the calendar's start side. */
@Composable
private fun WeekdayLabels(
    modifier: Modifier,
    grid: ActivityGrid,
    top: Dp,
    cell: Dp,
    pitch: Dp,
    colour: Color,
) {
    val measurer = rememberTextMeasurer()
    val style = Theme.typography.labelSmall
    Canvas(modifier.clearAndSetSemantics {}) {
        val rtl = layoutDirection == LayoutDirection.Rtl
        for (day in listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)) {
            val text = measurer.measure(day.shortName, style)
            val centre = top.toPx() + grid.rowOf(day) * pitch.toPx() + cell.toPx() / 2f
            val x = if (rtl) size.width - text.size.width else 0f
            drawText(text, colour, topLeft = Offset(x, centre - text.size.height / 2f))
        }
    }
}

/**
 * One week's column, for a screen reader: invisible, placed over its cells, and
 * saying what happened that week, with an action to pick each day.
 *
 * A node per week rather than per day — 53 rather than 371 — so a screen reader
 * user can move through a year in a year's weeks, and still reach every day
 * through its week's actions.
 */
@Composable
private fun WeekNode(
    week: Int,
    grid: ActivityGrid,
    activity: Map<LocalDate, Int>,
    marks: Array<ActivityMark?>?,
    formats: DateTimeFormats,
    weekOf: (String) -> String,
    describe: (LocalDate, Int) -> String,
    selected: LocalDate?,
    onDayClick: ((LocalDate) -> Unit)?,
) {
    val days = (0..6).mapNotNull { grid.dateAt(week, it) }
    if (days.isEmpty()) {
        Box(Modifier)
        return
    }
    Box(
        Modifier.semantics {
            // The days with something to say: a count, or a mark that means something.
            val busy = (0..6).mapNotNull { row ->
                grid.dateAt(week, row)?.takeIf {
                    (activity[it] ?: 0) > 0 || marks?.get(week * 7 + row)?.description != null
                }
            }
            val said = if (busy.isEmpty()) {
                listOf(describe(days.first(), 0))
            } else {
                busy.map { describe(it, activity[it] ?: 0) }
            }
            contentDescription = (listOf(weekOf(formats.dateShort(days.first()))) + said).joinToString(". ")
            collectionItemInfo = CollectionItemInfo(rowIndex = 0, rowSpan = 1, columnIndex = week, columnSpan = 1)
            // In the order the weeks happened, whichever side the grid starts.
            traversalIndex = week.toFloat()
            if (selected != null && selected in days) this.selected = true
            if (onDayClick != null) {
                customActions = days.map { day ->
                    CustomAccessibilityAction(describe(day, activity[day] ?: 0)) {
                        onDayClick(day)
                        true
                    }
                }
            }
        },
    )
}

/** One day's [ActivityMark], drawn over its cell's ground at the cell's top left. */
private fun DrawScope.drawMark(
    mark: ActivityMark,
    size: Float,
    ground: Color,
    cell: Path,
    dogEar: Path,
    ring: Outline,
    painter: VectorPainter?,
    iconSize: Float,
    text: TextLayoutResult?,
) {
    if (mark.corner.isSpecified) clipPath(cell) { drawPath(dogEar, mark.corner) }
    if (mark.dot.isSpecified) {
        val radius = size * MarkDotShare
        drawCircle(mark.dot, radius, Offset(size / 2f, size - radius - size * MarkDotLift))
    }
    if (mark.outline.isSpecified) {
        val inset = MarkRingInset.toPx()
        translate(inset, inset) { drawOutline(ring, mark.outline, style = Stroke(MarkRingWidth.toPx())) }
    }
    val content = mark.contentColour.takeIf { it.isSpecified } ?: contentColourFor(ground)
    if (painter != null) {
        val at = (size - iconSize) / 2f
        translate(at, at) {
            with(painter) { draw(Size(iconSize, iconSize), colorFilter = ColorFilter.tint(content)) }
        }
    } else if (text != null) {
        drawText(
            text,
            content,
            topLeft = Offset((size - text.size.width) / 2f, (size - text.size.height) / 2f),
        )
    }
}

/**
 * "Less", a cell of every shade, "More". Hidden from screen readers, which hear
 * the counts instead.
 *
 * Laid out rather than a `Row`, so that in a width too narrow for all of it the
 * words go first and then the whole legend, rather than running past the
 * calendar's edge.
 */
@Composable
private fun Legend(
    modifier: Modifier,
    colours: ActivityCalendarColours,
    cell: Dp,
    gap: Dp,
    shape: Shape,
    less: String,
    more: String,
) {
    Layout(
        content = {
            Text(less, colour = colours.label, style = Theme.typography.labelSmall, maxLines = 1)
            colours.levels.forEach { shade ->
                Canvas(Modifier.size(cell)) {
                    drawOutline(shape.createOutline(size, layoutDirection, this), shade)
                }
            }
            Text(more, colour = colours.label, style = Theme.typography.labelSmall, maxLines = 1)
        },
        modifier = modifier.clearAndSetSemantics {},
    ) { measurables, constraints ->
        val space = gap.roundToPx()
        val all = measurables.map { it.measure(Constraints()) }
        val swatches = all.subList(1, all.lastIndex)
        fun span(parts: List<Placeable>) =
            parts.sumOf { it.width } + space * (parts.size - 1).coerceAtLeast(0)
        val shown = when {
            span(all) <= constraints.maxWidth -> all
            span(swatches) <= constraints.maxWidth -> swatches
            else -> emptyList()
        }
        val height = shown.maxOfOrNull { it.height } ?: 0
        layout(span(shown).coerceAtLeast(0), height) {
            var x = 0
            shown.forEach {
                it.placeRelative(x, (height - it.height) / 2)
                x += it.width + space
            }
        }
    }
}

private fun LocalDate.coerceIn(first: LocalDate, last: LocalDate): LocalDate = when {
    this < first -> first
    this > last -> last
    else -> this
}

/** An empty shade then [levels] blended from [empty] to [full], the first of them a step clear of empty. */
private fun levelRamp(levels: Int, empty: Color, full: Color): List<Color> {
    val count = levels.coerceAtLeast(1)
    return listOf(empty) + List(count) { k ->
        val along = if (count == 1) 1f else LevelFloor + (1f - LevelFloor) * k / (count - 1)
        lerp(empty, full, along)
    }
}

private const val ActivityWeeks: Int = 53
private const val ActivityLevelCount: Int = 4
private val ActivityCellGap: Dp = 3.dp
private val ActivityCellShape: Shape = SquircleShape(CornerSize(ActivityCellCornerPercent))
private const val ActivityCellCornerPercent: Int = 20
/** With the 3dp gap, a 24dp target: the least WCAG asks of something to pick. */
private val MinCell: Dp = 20.dp
private val MaxCell: Dp = 28.dp
private val OneDp: Dp = 1.dp

/** A mark's parts, as shares of the cell they are drawn on. */
private const val DogEarShare: Float = 0.42f
private const val MarkIconShare: Float = 0.6f
private const val MarkDotShare: Float = 0.1f
private const val MarkDotLift: Float = 0.1f
private val MarkTextPadding: Dp = 2.dp
private val MarkRingInset: Dp = 2.dp
private val MarkRingWidth: Dp = 1.5.dp

/** How far along from empty to full the first shade starts. */
private const val LevelFloor: Float = 0.3f
