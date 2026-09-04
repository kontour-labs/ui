package io.kontour.ui.components.datetime

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import io.kontour.ui.a11y.minimumTouchTarget
import io.kontour.ui.foundation.Text
import io.kontour.ui.input.pointerCursor
import io.kontour.ui.interaction.Feedback
import io.kontour.ui.interaction.FeedbackIntent
import io.kontour.ui.theme.Theme
import io.kontour.ui.theme.invisible
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** How a day sits within a selected range. Drives the cell's shape and fill. */
enum class RangePosition { None, Start, Middle, End, StartAndEnd }

/**
 * One month's grid of days.
 *
 * The reusable part of every date picker — a calendar surface with no opinion
 * about how selection works, so single-date, range and multi-select pickers all
 * build on the same grid rather than each drawing their own.
 *
 * ```
 * CalendarMonth(
 *     month = visibleMonth,
 *     isSelected = { it == chosen },
 *     onSelectedChange = { chosen = it },
 *     isDateSelectable = { it >= today },
 * )
 * ```
 *
 * Selection is expressed as predicates rather than as a value, because that is
 * the only shape that serves all three selection modes without the grid needing
 * to know which one it is in.
 *
 * @param month Any date within the month to show; only its year and month matter.
 * @param isDateSelectable Days for which this returns false are shown but not
 *   selectable — greyed rather than hidden, so the calendar keeps its shape and
 *   the user can see *why* a date is unavailable.
 * @param markerFor Draws a dot under a day. For "this day has departures", or a
 *   trip already booked.
 * @param rangePositionOf Where a day sits in a selected range. Days in the
 *   middle get a square fill so the run reads as continuous; the ends get the
 *   rounded cap.
 */
object CalendarMonthDefaults {
    /** Breathing room around a day cell, outside a range only. */
    val CellInset: Dp = 1.dp

    /**
     * The cell size the type scale is already right for.
     *
     * A month grid at a phone's width puts a cell at about this, which is where
     * `bodyMedium` was chosen. Cells larger than this grow their digit in
     * proportion; smaller ones leave it alone. See `DayCell`.
     */
    val ReferenceCell: Dp = 44.dp
}

@Composable
fun CalendarMonth(
    month: LocalDate,
    isSelected: (LocalDate) -> Boolean,
    onSelectedChange: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    isDateSelectable: (LocalDate) -> Boolean = { true },
    /**
     * A range being dragged out, reported as (where the finger went down, where
     * it is now).
     *
     * The calendar owns the anchor rather than the caller, because it is gesture
     * state and not selection state — a caller that had to hold it would be
     * holding something it can neither set nor meaningfully restore. Which end
     * is the start and which the end is the caller's business; a drag leftwards
     * reports a `to` earlier than its `from`.
     *
     * `null` leaves the calendar tap-only.
     */
    onDragSelect: ((from: LocalDate, to: LocalDate) -> Unit)? = null,
    today: LocalDate? = null,
    markerFor: ((LocalDate) -> Color?)? = null,
    rangePositionOf: ((LocalDate) -> RangePosition)? = null,
    formats: DateTimeFormats = LocalDateTimeFormats.current,
) {
    val firstOfMonth = remember(month) { LocalDate(month.year, month.month, 1) }
    val daysInMonth = remember(firstOfMonth) {
        firstOfMonth.plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY).day
    }
    val leadingBlanks = remember(firstOfMonth, formats) {
        formats.columnOf(firstOfMonth.dayOfWeek)
    }

    /**
     * Whether a range is being dragged out right now.
     *
     * A tapped day lands like a stone and a dragged one should not: the cap
     * bounced into place on every cell the finger crossed, so a drag across a
     * fortnight was fourteen separate arrivals. See `DayCell`, where this
     * chooses between the two.
     */
    var dragging by remember { mutableStateOf(false) }

    // One measurement for the whole month, not forty-two.
    //
    // The day numbers scale with their cells — see `DayCell` — and a cell is a
    // seventh of the grid by construction, so the grid's own width answers it
    // for every cell at once. A `BoxWithConstraints` per cell would be
    // forty-two subcompositions to derive one number that is the same number
    // every time.
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val cellSize = maxWidth / 7
        Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            formats.weekdayInitials().forEachIndexed { index, initial ->
                Box(
                    Modifier
                        .weight(1f)
                        // The initials repeat — S for Saturday and Sunday, T for
                        // Tuesday and Thursday — so they are decorative here and
                        // each day cell carries its own full announcement.
                        .semantics { },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = initial,
                        style = Theme.typography.labelSmall,
                        colour = Theme.colours.contentMuted,
                        modifier = Modifier.padding(vertical = Theme.spacing.xs),
                    )
                }
            }
        }

        val cells = leadingBlanks + daysInMonth
        val rows = (cells + 6) / 7

        WeekGrid(
            rows = rows,
            leadingBlanks = leadingBlanks,
            daysInMonth = daysInMonth,
            month = month,
            isDateSelectable = isDateSelectable,
            onDragSelect = onDragSelect,
            onDraggingChange = { dragging = it },
        ) {
        for (row in 0 until rows) {
            Row(Modifier.fillMaxWidth()) {
                for (column in 0 until 7) {
                    val cellIndex = row * 7 + column
                    val dayOfMonth = cellIndex - leadingBlanks + 1

                    if (dayOfMonth < 1 || dayOfMonth > daysInMonth) {
                        Box(Modifier.weight(1f).aspectRatio(1f))
                    } else {
                        val date = LocalDate(month.year, month.month, dayOfMonth)
                        DayCell(
                            date = date,
                            modifier = Modifier.weight(1f),
                            selected = isSelected(date),
                            enabled = isDateSelectable(date),
                            isToday = date == today,
                            marker = markerFor?.invoke(date),
                            rangePosition = rangePositionOf?.invoke(date) ?: RangePosition.None,
                            formats = formats,
                            cellSize = cellSize,
                            dragging = dragging,
                            onSelectedChange = onSelectedChange,
                        )
                    }
                }
            }
        }
        }
        }
    }
}

/**
 * The month's rows, with a drag that selects across them.
 *
 * Its own composable so the gesture's arithmetic sits next to nothing else. The
 * hit test is derived from the grid rather than from each cell's reported
 * bounds: the columns are seven equal weights and every row is one cell tall, so
 * this is the same arithmetic the layout does, and forty-two
 * `onGloballyPositioned` callbacks would buy nothing over it.
 *
 * `detectDragGestures` waits for touch slop, so a tap still belongs to the cell
 * it landed on and the two gestures never argue over one press.
 */
@Composable
private fun WeekGrid(
    rows: Int,
    leadingBlanks: Int,
    daysInMonth: Int,
    month: LocalDate,
    isDateSelectable: (LocalDate) -> Boolean,
    onDragSelect: ((LocalDate, LocalDate) -> Unit)?,
    onDraggingChange: (Boolean) -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val currentSelect by rememberUpdatedState(onDragSelect)
    val currentSelectable by rememberUpdatedState(isDateSelectable)
    val currentDragging by rememberUpdatedState(onDraggingChange)

    Column(
        Modifier
            .fillMaxWidth()
            .then(
                if (onDragSelect == null) {
                    Modifier
                } else {
                    // `month` is a key because `dateAt` builds dates out of it;
                    // without it a block launched in August goes on reporting
                    // August dates after the calendar has paged to September.
                    Modifier.pointerInput(leadingBlanks, daysInMonth, rows, month) {
                        /**
                         * Where the finger went down.
                         *
                         * A plain local, and it has to be. This was hoisted into
                         * the composition and read back inside the gesture, where
                         * it is a value *captured when the block was launched* —
                         * which is before the drag started, so it was `null` on
                         * every move and the range never grew past the day it
                         * began on. The gesture's own anchor is not state anybody
                         * else reads, so nobody else should be holding it.
                         */
                        var anchor: LocalDate? = null

                        fun dateAt(offset: Offset): LocalDate? {
                            if (size.width <= 0 || rows == 0) return null
                            val cell = size.width / 7f
                            val column = (offset.x / cell).toInt()
                            val row = (offset.y / cell).toInt()
                            if (column !in 0..6 || row !in 0 until rows) return null
                            val day = row * 7 + column - leadingBlanks + 1
                            if (day < 1 || day > daysInMonth) return null
                            val date = LocalDate(month.year, month.month, day)
                            return if (currentSelectable(date)) date else null
                        }

                        detectDragGestures(
                            onDragStart = { offset ->
                                anchor = dateAt(offset)
                                currentDragging(true)
                                anchor?.let { currentSelect?.invoke(it, it) }
                            },
                            onDragEnd = {
                                anchor = null
                                currentDragging(false)
                            },
                            onDragCancel = {
                                anchor = null
                                currentDragging(false)
                            },
                        ) { change, _ ->
                            val from = anchor ?: return@detectDragGestures
                            dateAt(change.position)?.let { currentSelect?.invoke(from, it) }
                        }
                    }
                }
            ),
        content = content,
    )
}

@Composable
private fun DayCell(
    date: LocalDate,
    modifier: Modifier,
    selected: Boolean,
    enabled: Boolean,
    isToday: Boolean,
    marker: Color?,
    rangePosition: RangePosition,
    formats: DateTimeFormats,
    cellSize: Dp,
    dragging: Boolean,
    onSelectedChange: (LocalDate) -> Unit,
) {
    val colours = Theme.colours
    val motion = Theme.motion
    val feedback = Feedback
    val interactions = remember { MutableInteractionSource() }

    val inRange = rangePosition != RangePosition.None
    val isEndpoint = rangePosition == RangePosition.Start ||
        rangePosition == RangePosition.End ||
        rangePosition == RangePosition.StartAndEnd
    val filled = selected || isEndpoint

    // Whether the band carries on into the neighbouring cell. Layout-direction
    // aware by construction: `Start`/`End` are the range's ends, and `start`/`end`
    // padding resolves the same way the shape's `topStart`/`topEnd` do.
    val continuesBefore = rangePosition == RangePosition.Middle ||
        rangePosition == RangePosition.End
    val continuesAfter = rangePosition == RangePosition.Middle ||
        rangePosition == RangePosition.Start

    // A run of selected days reads as continuous because the middle keeps square
    // edges and only the ends are capped.
    val shape: Shape = when (rangePosition) {
        RangePosition.Middle -> RectangleShape
        RangePosition.Start -> Theme.shapes.pill.copy(
            topEnd = androidx.compose.foundation.shape.CornerSize(0),
            bottomEnd = androidx.compose.foundation.shape.CornerSize(0),
        )
        RangePosition.End -> Theme.shapes.pill.copy(
            topStart = androidx.compose.foundation.shape.CornerSize(0),
            bottomStart = androidx.compose.foundation.shape.CornerSize(0),
        )
        else -> Theme.shapes.pill
    }

    /**
     * A day arrives, but it does not leave.
     *
     * Tapping a date should feel like laying down a stone, so a cell joining the
     * selection eases in. Letting it ease *out* again is a different thing
     * entirely, and it only shows when a whole range is replaced: every day of
     * the old one animated away at once, so choosing new dates dragged a trail of
     * grey circles behind it for a fifth of a second — a near-black cap on its
     * way to nothing is grey for most of the tween, and there was one on every
     * day the range used to cover. Nothing about a range you have just replaced
     * is worth watching leave.
     *
     * So the drawn colour is the animated one on the way in and the *target* on
     * the way out. Not `snap()` as the spec, which is a zero-length animation and
     * still costs the frame it takes to start — one frame of the old range at
     * full strength, which is exactly the flash.
     */
    val arriving = filled || inRange

    val containerTarget = when {
        filled -> colours.primary
        inRange -> colours.accent.container
        // `invisible()`, not `Color.Transparent`, which is black. This is where
        // a cell animates *from* when it joins a range, and a lerp moves the
        // channels as well as the alpha — so the tint used to arrive out of the
        // dark rather than fading up.
        else -> colours.accent.container.invisible()
    }
    val animatedContainer by animateColorAsState(
        targetValue = containerTarget,
        animationSpec = motion.tweenFast(),
        label = "dayContainer",
    )
    val container = if (arriving) animatedContainer else containerTarget

    val labelTarget = when {
        !enabled -> colours.contentDisabled
        filled -> colours.onPrimary
        inRange -> colours.accent.onContainer
        else -> colours.content
    }
    val animatedLabel by animateColorAsState(
        targetValue = labelTarget,
        animationSpec = motion.tweenFast(),
        label = "dayLabel",
    )
    // Taken away with the fill it is drawn on. Easing the digit back to
    // `content` over a container that has already gone leaves white text on a
    // white ground for the length of the tween.
    val label = if (arriving) animatedLabel else labelTarget
    /**
     * A cap arriving under a finger slides; a cap arriving under a tap lands.
     *
     * Tapping a day should feel like laying down a stone, and it does. Dragging
     * a range out is a different gesture and was borrowing the same animation:
     * the cap sprang into place on every cell the finger crossed, so a drag
     * across a fortnight was fourteen separate bounces chasing the pointer —
     * which is the "bouncing looks a bit strange" in the report, and the reason
     * the same animation reads well on a tap.
     *
     * So while a drag is in progress the cap grows along the track instead, out
     * of the edge the range is coming from, on a spring with no overshoot in it.
     * The band extends and its end slides; nothing arrives.
     */
    val sliding = dragging && rangePosition != RangePosition.None &&
        rangePosition != RangePosition.StartAndEnd

    val fillScale by animateFloatAsState(
        targetValue = when {
            filled -> 1f
            sliding -> 0f
            else -> 0.7f
        },
        animationSpec = if (sliding) {
            motion.springOrTween(motion.springSnappy)
        } else {
            motion.springOrTween(motion.springBouncy)
        },
        label = "dayFill",
    )

    /**
     * The day number, sized to the cell it is in.
     *
     * A calendar given a lot of width grows its cells with it — they are square
     * and a seventh of the grid — while the digit inside stayed at whatever the
     * type scale said, so a wide picker was small numbers adrift in large
     * circles.
     *
     * Scaled against the *style*, not computed in dp: the base size is in `sp`
     * and multiplying it keeps the user's own text size in the answer, where
     * deriving a dp from the cell and converting back would quietly throw it
     * away. And it only grows — a cramped calendar has other problems, and
     * shrinking the digit below the type scale is not the fix for any of them.
     */
    val baseStyle = if (isToday) Theme.typography.labelMedium else Theme.typography.bodyMedium
    val dayStyle = remember(baseStyle, cellSize) {
        val growth = (cellSize / CalendarMonthDefaults.ReferenceCell).coerceIn(1f, MaxDayGrowth)
        if (growth <= 1f) {
            baseStyle
        } else {
            baseStyle.copy(
                fontSize = baseStyle.fontSize * growth,
                lineHeight = if (baseStyle.lineHeight.isSpecified) {
                    baseStyle.lineHeight * growth
                } else {
                    baseStyle.lineHeight
                },
            )
        }
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            // Inset on the outside of the range only.
            //
            // A flat 1dp all round put 2dp of gap between every pair of
            // neighbours, so the band a range draws came out as a row of
            // separate tiles — the shape logic above caps only the ends
            // precisely so the middle reads continuous, and the padding was
            // undoing it a pixel at a time.
            .padding(
                start = if (continuesBefore) 0.dp else CalendarMonthDefaults.CellInset,
                end = if (continuesAfter) 0.dp else CalendarMonthDefaults.CellInset,
                top = CalendarMonthDefaults.CellInset,
                bottom = CalendarMonthDefaults.CellInset,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .matchCellSize()
                .graphicsLayer {
                    if (sliding) {
                        // Along the track only, out of the edge the range is
                        // arriving from: the end cap of a range growing to the
                        // right comes out of its left side, and the start cap of
                        // one growing to the left out of its right.
                        scaleX = fillScale
                        transformOrigin = TransformOrigin(
                            pivotFractionX = if (rangePosition == RangePosition.Start) 1f else 0f,
                            pivotFractionY = 0.5f,
                        )
                    } else if (filled) {
                        scaleX = fillScale
                        scaleY = fillScale
                    }
                }
                .clip(shape)
                .background(container, shape)
                .then(
                    if (isToday && !filled) {
                        Modifier.border(
                            Theme.sizing.borderWidth,
                            colours.outlineStrong,
                            Theme.shapes.pill,
                        )
                    } else {
                        Modifier
                    }
                )
        )

        Box(
            Modifier
                .minimumTouchTarget()
                .matchCellSize()
                .clip(shape)
                // On the node that is pressed, not on the fill behind it.
                //
                // The date and the selection used to be announced from the
                // decorative box that draws the highlight — a sibling of this
                // one, which a screen reader reaches separately if at all. So
                // the thing a user actually lands on said "18, button" and
                // nothing about it being the date they had chosen.
                //
                // `selectable` rather than `clickable`: a day in a calendar is
                // one of a set of choices, and this is the modifier that says so
                // and carries the state with the action.
                .semantics { stateDescription = formats.dateFull(date) }
                .pointerCursor(enabled = enabled)
                .selectable(
                    selected = filled,
                    interactionSource = interactions,
                    indication = null,
                    enabled = enabled,
                    role = Role.Button,
                    onClick = {
                        feedback.perform(FeedbackIntent.Selection)
                        onSelectedChange(date)
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = date.day.toString(),
                    style = dayStyle,
                    colour = label,
                )
                if (marker != null) {
                    Box(
                        Modifier
                            .padding(top = 2.dp)
                            .markerSize()
                            .clip(Theme.shapes.pill)
                            .background(if (filled) colours.onPrimary else marker)
                    )
                }
            }
        }
    }
}

/**
 * The whole of the cell's padded box — **not** a square derived from its width.
 *
 * It was `fillMaxWidth().aspectRatio(1f)`, so the fill's height was its width.
 * The cell's horizontal padding varies with where it sits in a range (0dp in the
 * middle, 1dp at a cap, 2dp on its own) while the vertical padding is fixed, so
 * that turned a deliberate difference in *width* into an accidental difference in
 * *height*: middles drew 1dp taller than the caps and a single-day range drew
 * shortest of all. It also asked for a square taller than the box it was in,
 * which `Box` does not clip, so the difference showed rather than being absorbed.
 *
 * Filling the box instead makes the height uniform by construction — every cell
 * is inset the same 1dp top and bottom — and lets the width go on doing its job
 * of joining the band up.
 */
private fun Modifier.matchCellSize(): Modifier = fillMaxSize()

private fun Modifier.markerSize(): Modifier = size(4.dp)

/**
 * The most the day number grows over its base size in a wide calendar.
 *
 * Two thirds again. Past that the digit starts to fill the cap it sits in and a
 * selected day reads as a number with a ring drawn tight around it rather than
 * as a marked date.
 */
private const val MaxDayGrowth = 1.66f
