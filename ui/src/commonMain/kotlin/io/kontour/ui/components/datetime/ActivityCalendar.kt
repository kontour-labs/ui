package io.kontour.ui.components.datetime

import io.kontour.ui.locale.NameWidth
import io.kontour.ui.locale.LocaleFormats
import io.kontour.ui.locale.DateStyle
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
import androidx.compose.ui.graphics.Brush
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
import io.kontour.ui.motion.ScrollDefaults
import io.kontour.ui.motion.scrollSpec
import io.kontour.ui.overlay.OverlayAlignment
import io.kontour.ui.overlay.OverlaySide
import io.kontour.ui.overlay.TooltipDefaults
import io.kontour.ui.overlay.TooltipOverlay
import io.kontour.ui.theme.SquircleShape
import io.kontour.ui.theme.Theme
import io.kontour.ui.theme.lines
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
 * **A day can have several.** A payday that is also a public holiday is two
 * marks, not a third that means both. They share the cell: each corner folds a
 * different corner — top end, then top start, bottom end, bottom start — dots
 * sit in a row and shrink to fit, two icons sit side by side a little smaller,
 * and three or more show the first and how many more. [text] is drawn only on a
 * day with no icon. A second outline sits inside the first, the first [fill] is
 * the cell's, and every [description] is said, joined as a list.
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

/** What an [ActivityCalendar] shows under its grid to say what the shades mean. */
enum class ActivityLegend {
    /** Nothing. */
    None,

    /** "Less", a small square of every shade, "More". */
    Steps,

    /**
     * "Less", one bar blended through the shades, "More": for a scale that reads
     * as a quantity rather than as a few kinds of day.
     */
    Gradient,
}

/** What an [ActivityCalendar] takes by default. */
object ActivityCalendarDefaults {
    /** Shades past empty: four, as a contribution graph has. */
    const val LevelCount: Int = 4

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
        levels: Int = LevelCount,
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
    fun describe(formats: LocaleFormats = Theme.formats): (date: LocalDate, count: Int) -> String {
        val strings = Theme.strings
        return remember(strings, formats) { { date, count -> strings.activityOnDay(formats.quantity(count), formats.date(date, DateStyle.Full)) } }
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
 *     showMonthLabels = false,
 *     showWeekdayLabels = false,
 *     legend = ActivityLegend.None,
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
 * [marksFor] decorates days: a corner folded over for a holiday, an icon for a
 * trip, a count written out on a busy day — and more than one on the same day.
 * See [ActivityMark].
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
 * @param marksFor What to draw on a day besides its shade, given its date and
 *   count: nothing, one mark, or several. Asked once per day shown.
 * @param levels How counts become shades.
 * @param colours The shades, labels and rings.
 * @param cellSize A fixed cell size. Unspecified fits the width.
 * @param cellGap The space between cells.
 * @param cellShape Each cell's shape.
 * @param showMonthLabels Month names along the top.
 * @param showWeekdayLabels Mon, Wed and Fri down the start side.
 * @param legend What the shades mean, under the grid: small squares of each, a
 *   bar blended through them, or nothing.
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
    marksFor: ((date: LocalDate, count: Int) -> List<ActivityMark>)? = null,
    levels: ActivityLevels = ActivityLevels.Quantiles,
    colours: ActivityCalendarColours = ActivityCalendarDefaults.colours(),
    cellSize: Dp = Dp.Unspecified,
    cellGap: Dp = ActivityCalendarDefaults.CellGap,
    cellShape: Shape = ActivityCalendarDefaults.CellShape,
    showMonthLabels: Boolean = true,
    showWeekdayLabels: Boolean = true,
    legend: ActivityLegend = ActivityLegend.Steps,
    formats: LocaleFormats = Theme.formats,
    firstDayOfWeek: DayOfWeek = formats.conventions.firstDayOfWeek,
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

    // Each shown day's marks, combined, in the same order as the shades.
    val strings = Theme.strings
    val marks = remember(activity, grid, marksFor, formats, strings) {
        if (marksFor == null) {
            null
        } else {
            Array(columns * 7) { index ->
                grid.dateAt(index / 7, index % 7)?.let { date ->
                    combineMarks(
                        marksFor(date, activity[date] ?: 0),
                        joined = { formats.list(it) },
                        more = { strings.moreCount(formats.quantity(it.toLong())) },
                    )
                }
            }
        }
    }
    // Remembered, so the grid's draw cache below — which captures it — is the
    // same lambda from one composition to the next and is not rebuilt, month
    // labels re-shaped and all, whenever anything else here recomposes.
    val painters = remember { HashMap<ImageVector, VectorPainter>() }
    marks?.flatMapTo(LinkedHashSet()) { it?.icons.orEmpty() }?.forEach { icon ->
        key(icon) { painters[icon] = rememberVectorPainter(icon) }
    }
    fun say(date: LocalDate, count: Int): String {
        val words = describe(date, count)
        val meaning = grid.cellOf(date)?.let { (column, row) -> marks?.get(column * 7 + row)?.description }
            ?: return words
        return strings.sentences(listOf(words, meaning))
    }

    val labelStyle = Theme.typography.labelSmall
    // Room for every label the grid draws — twelve months, three weekdays and
    // the marks — so rebuilding the draw cache finds them already shaped. The
    // default of eight evicted the months on every rebuild.
    val measurer = rememberTextMeasurer(cacheSize = 32)
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val rtl = layoutDirection == LayoutDirection.Rtl
    val interactive = enabled && onDayClick != null
    val interactions = interactionSource ?: remember { MutableInteractionSource() }
    val ringVisible = rememberFocusRingVisible(interactions)
    val focusColour = Theme.colours.focusRing
    val focusWidth = Theme.sizing.focusRingWidth
    val ringWidth = Theme.sizing.borderWidthStrong
    // Bringing the keyboard's day into view snaps under reduced motion.
    val scrollMotion = Theme.motion
    // Today's and the hovered day's rings, and a mark's: lines, thickening with
    // the tier, where they were 1dp and 1.5dp at every tier.
    val hairline = Theme.sizing.borderWidth
    val markRingWidth = Theme.sizing.lines(1.5f)
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
        val weekdayWidth = if (showWeekdayLabels) {
            with(density) {
                listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)
                    .maxOf { measurer.measure(formats.weekdayName(it, NameWidth.Abbreviated), labelStyle).size.width }
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
        val monthBand = if (showMonthLabels) {
            with(density) { measurer.measure(formats.monthName(Month.MAY, NameWidth.Abbreviated), labelStyle).size.height.toDp() } + labelSpace
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
            // Measured from the grid's start edge, as the scroll counts: right
            // to left, its nought shows the right-hand end, so a cell's place
            // from the left is the wrong way round. Compared as it was, the
            // first key press in Arabic scrolled to the oldest weeks, away
            // from the cursor it was bringing into view.
            val width = with(density) { gridWidth.roundToPx() }
            val near = (if (rtl) width - rect.right else rect.left).toInt()
            val far = (if (rtl) width - rect.left else rect.right).toInt()
            when {
                near < scroll.value -> scroll.animateScrollTo(near, scrollMotion.scrollSpec())
                far > scroll.value + viewport -> scroll.animateScrollTo(far - viewport, scrollMotion.scrollSpec())
            }
        }

        Column {
            Row {
                if (showWeekdayLabels) {
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
                                    sentences = strings.sentences,
                                    describe = ::say,
                                    selected = selected,
                                    onDayClick = if (interactive) onDayClick else null,
                                )
                            }
                        },
                        modifier = Modifier
                            .fadingEdges(scroll, Orientation.Horizontal)
                            .horizontalScroll(scroll, flingBehavior = ScrollDefaults.flingBehavior())
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
                                val hoverRing = grown(hairline.toPx() / 2f)
                                val markStroke = markRingWidth.toPx()
                                val todayInset = OneDp.toPx() * 1.5f
                                val todayRing = cellShape.createOutline(
                                    Size(size - todayInset * 2, size - todayInset * 2), layoutDirection, this,
                                )
                                val months = if (showMonthLabels) {
                                    val layouts = Month.entries.associateWith {
                                        measurer.measure(formats.monthName(it, NameWidth.Abbreviated), labelStyle)
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
                                // The corners, in the order a day's marks take them:
                                // top end, top start, bottom end, bottom start.
                                val dogEars = listOf(true to true, true to false, false to true, false to false)
                                    .map { (top, atEnd) ->
                                        val right = atEnd != rtl
                                        val x = if (right) size else 0f
                                        val y = if (top) 0f else size
                                        Path().apply {
                                            moveTo(x + if (right) -ear else ear, y)
                                            lineTo(x, y)
                                            lineTo(x, y + if (top) ear else -ear)
                                            close()
                                        }
                                    }
                                fun fitted(text: String, room: Float): TextLayoutResult {
                                    val natural = measurer.measure(text, labelStyle, maxLines = 1, softWrap = false)
                                    return if (natural.size.width <= room || natural.size.width == 0) {
                                        natural
                                    } else {
                                        measurer.measure(
                                            text,
                                            labelStyle.copy(fontSize = labelStyle.fontSize * (room / natural.size.width)),
                                            maxLines = 1,
                                            softWrap = false,
                                        )
                                    }
                                }
                                val padding = MarkTextPadding.toPx()
                                // A day's text has the cell; the "+2" after an icon has
                                // the half of it the icon leaves.
                                val markTexts = marks?.mapNotNullTo(LinkedHashSet()) { day -> day?.text?.let { it to day.icons.isNotEmpty() } }
                                    ?.associateWith { (text, beside) ->
                                        fitted(text, if (beside) size / 2f - padding else size - padding * 2)
                                    }.orEmpty()
                                fun ringInset(by: Float) = cellShape.createOutline(
                                    Size(size - by * 2, size - by * 2), layoutDirection, this,
                                )
                                val markRings = listOf(
                                    MarkRingInset.toPx(),
                                    MarkRingInset.toPx() + markStroke + OneDp.toPx(),
                                ).map { it to ringInset(it) }

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
                                                    drawMarks(
                                                        mark, size, ground, rtl, cellPath, dogEars, markRings,
                                                        mark.icons.map { painters.getValue(it) },
                                                        mark.text?.let { markTexts[it to mark.icons.isNotEmpty()] },
                                                        markStroke,
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
                                    ringAt(today, todayRing, -todayInset, colours.today, hairline.toPx())
                                    ringAt(hoverDay, hoverRing, hairline.toPx() / 2f, hoverColour, hairline.toPx())
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
            if (legend != ActivityLegend.None) {
                Legend(
                    modifier = Modifier.align(Alignment.End).padding(top = labelSpace),
                    style = legend,
                    colours = colours,
                    gap = cellGap,
                    wordGap = labelSpace,
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
                    delay(TooltipDefaults.HoverDelay)
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
    val formats = Theme.formats
    Canvas(modifier.clearAndSetSemantics {}) {
        val rtl = layoutDirection == LayoutDirection.Rtl
        for (day in listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)) {
            val text = measurer.measure(formats.weekdayName(day, NameWidth.Abbreviated), style)
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
    marks: Array<DayMarks?>?,
    formats: LocaleFormats,
    weekOf: (String) -> String,
    sentences: (List<String>) -> String,
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
            contentDescription = sentences(listOf(weekOf(formats.date(days.first(), DateStyle.Medium))) + said)
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

/**
 * A day's marks, combined: what the grid draws on the cell and the words said
 * for them. See [ActivityMark] for how several share one cell.
 */
@Immutable
internal class DayMarks(
    val corners: List<Color>,
    val dots: List<Color>,
    val outlines: List<Color>,
    val fill: Color,
    /** One or two, drawn; a third or more shows as [text]. */
    val icons: List<ImageVector>,
    /** The day's text with no icon, or how many icons more with them: "+2". */
    val text: String?,
    val contentColour: Color,
    val description: String?,
)

/**
 * [marks] as one day's drawing, or null if there are none. [joined] writes the
 * descriptions as a list; [more] how many icons did not fit.
 */
internal fun combineMarks(
    marks: List<ActivityMark>,
    joined: (List<String>) -> String,
    more: (Int) -> String,
): DayMarks? {
    if (marks.isEmpty()) return null
    val icons = marks.mapNotNull { it.icon }
    return DayMarks(
        corners = marks.map { it.corner }.filter { it.isSpecified }.take(MaxCorners),
        dots = marks.map { it.dot }.filter { it.isSpecified }.take(MaxDots),
        outlines = marks.map { it.outline }.filter { it.isSpecified }.take(MaxOutlines),
        fill = marks.firstOrNull { it.fill.isSpecified }?.fill ?: Color.Unspecified,
        icons = if (icons.size <= 2) icons else icons.take(1),
        text = when {
            icons.size > 2 -> more(icons.size - 1)
            icons.isNotEmpty() -> null
            else -> marks.firstNotNullOfOrNull { it.text }
        },
        contentColour = marks.firstOrNull { it.contentColour.isSpecified }?.contentColour ?: Color.Unspecified,
        description = marks.mapNotNull { it.description }.takeIf { it.isNotEmpty() }?.let(joined),
    )
}

/** One day's [DayMarks], drawn over its cell's ground at the cell's top left. */
private fun DrawScope.drawMarks(
    mark: DayMarks,
    size: Float,
    ground: Color,
    rtl: Boolean,
    cell: Path,
    dogEars: List<Path>,
    rings: List<Pair<Float, Outline>>,
    painters: List<VectorPainter>,
    text: TextLayoutResult?,
    ringStroke: Float,
) {
    mark.corners.forEachIndexed { k, colour -> clipPath(cell) { drawPath(dogEars[k], colour) } }
    if (mark.dots.isNotEmpty()) {
        // In a row, smaller the more there are, from the start side.
        val radius = size * MarkDotShare * DotShrink[mark.dots.size - 1]
        val pitch = radius * DotPitch
        val y = size - size * MarkDotShare - size * MarkDotLift
        val first = size / 2f - pitch * (mark.dots.size - 1) / 2f
        mark.dots.forEachIndexed { k, colour ->
            val along = first + pitch * k
            drawCircle(colour, radius, Offset(if (rtl) size - along else along, y))
        }
    }
    mark.outlines.forEachIndexed { k, colour ->
        val (inset, ring) = rings[k]
        translate(inset, inset) { drawOutline(ring, colour, style = Stroke(ringStroke)) }
    }
    val content = mark.contentColour.takeIf { it.isSpecified } ?: contentColourFor(ground)
    fun icon(painter: VectorPainter, side: Float, centreX: Float) {
        translate(centreX - side / 2f, (size - side) / 2f) {
            with(painter) { draw(Size(side, side), colorFilter = ColorFilter.tint(content)) }
        }
    }
    // The start half and the end half of the cell, whichever side the start is.
    val startHalf = if (rtl) size * 3f / 4f else size / 4f
    val endHalf = size - startHalf
    when {
        painters.size == 1 && text == null -> icon(painters[0], size * MarkIconShare, size / 2f)
        painters.size == 2 -> {
            val side = size * PairedIconShare
            icon(painters[0], side, startHalf)
            icon(painters[1], side, endHalf)
        }
        painters.size == 1 -> {
            icon(painters[0], size * PairedIconShare, startHalf)
            if (text != null) {
                drawText(text, content, topLeft = Offset(endHalf - text.size.width / 2f, (size - text.size.height) / 2f))
            }
        }
        text != null -> drawText(
            text,
            content,
            topLeft = Offset((size - text.size.width) / 2f, (size - text.size.height) / 2f),
        )
    }
}

/**
 * "Less", the shades, "More" — as a small square of each, or one bar blended
 * through them. Hidden from screen readers, which hear the counts instead.
 *
 * Its squares are 10dp whatever the cells are: a key to the shades, not a row of
 * days — at the size of the cells it read as part of the grid.
 *
 * Laid out rather than a `Row`, so that in a width too narrow for all of it the
 * words go first and then the whole legend, rather than running past the
 * calendar's edge.
 */
@Composable
private fun Legend(
    modifier: Modifier,
    style: ActivityLegend,
    colours: ActivityCalendarColours,
    gap: Dp,
    wordGap: Dp,
    shape: Shape,
    less: String,
    more: String,
) {
    // The swatches carry the grid's own edge at the enhanced tier, so the
    // lightest of them is still a square beside the words rather than a gap.
    val edge = contrastEdge()
    Layout(
        content = {
            Text(less, colour = colours.label, style = Theme.typography.labelSmall, maxLines = 1)
            if (style == ActivityLegend.Gradient) {
                val length = LegendSwatch * colours.levels.size + gap * (colours.levels.size - 1)
                Canvas(Modifier.size(length, LegendSwatch)) {
                    val outline = shape.createOutline(size, layoutDirection, this)
                    // From empty at the start, whichever side the start is.
                    val stops = if (layoutDirection == LayoutDirection.Rtl) colours.levels.reversed() else colours.levels
                    drawOutline(outline, Brush.horizontalGradient(stops))
                    if (edge != null) drawOutline(outline, edge.brush, style = Stroke(edge.width.toPx()))
                }
            } else {
                colours.levels.forEach { shade ->
                    Canvas(Modifier.size(LegendSwatch)) {
                        val outline = shape.createOutline(size, layoutDirection, this)
                        drawOutline(outline, shade)
                        if (edge != null) drawOutline(outline, edge.brush, style = Stroke(edge.width.toPx()))
                    }
                }
            }
            Text(more, colour = colours.label, style = Theme.typography.labelSmall, maxLines = 1)
        },
        modifier = modifier.clearAndSetSemantics {},
    ) { measurables, constraints ->
        val space = gap.roundToPx()
        val wordSpace = wordGap.roundToPx()
        val all = measurables.map { it.measure(Constraints()) }
        val swatches = all.subList(1, all.lastIndex)
        fun span(parts: List<Placeable>) =
            parts.sumOf { it.width } + space * (parts.size - 1).coerceAtLeast(0)
        val words = all.first().width + all.last().width + (wordSpace - space) * 2 + space * 2
        val shown = when {
            span(swatches) + words <= constraints.maxWidth -> all
            span(swatches) <= constraints.maxWidth -> swatches
            else -> emptyList()
        }
        val withWords = shown.size == all.size
        val width = span(shown) + if (withWords) (wordSpace - space) * 2 else 0
        val height = shown.maxOfOrNull { it.height } ?: 0
        layout(width.coerceAtLeast(0), height) {
            var x = 0
            shown.forEachIndexed { k, part ->
                part.placeRelative(x, (height - part.height) / 2)
                val word = withWords && (k == 0 || k == shown.lastIndex - 1)
                x += part.width + if (word) wordSpace else space
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
/** Two icons side by side, or one beside a count of the rest. */
private const val PairedIconShare: Float = 0.42f
private const val MarkDotShare: Float = 0.1f
private const val MarkDotLift: Float = 0.1f
private val MarkTextPadding: Dp = 2.dp
private val MarkRingInset: Dp = 2.dp

/** How many of each part one day draws: a corner each, a row of dots, a ring inside a ring. */
private const val MaxCorners: Int = 4
private const val MaxDots: Int = 4
private const val MaxOutlines: Int = 2

/** A dot's size with one, two, three and four in the row. */
private val DotShrink: FloatArray = floatArrayOf(1f, 0.85f, 0.72f, 0.62f)

/** From one dot's centre to the next, in radii. */
private const val DotPitch: Float = 2.6f

/** A legend's squares, and its bar's height. */
private val LegendSwatch: Dp = 10.dp

/** How far along from empty to full the first shade starts. */
private const val LevelFloor: Float = 0.3f
