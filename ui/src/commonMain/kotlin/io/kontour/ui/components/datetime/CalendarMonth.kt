package io.kontour.ui.components.datetime

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
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
import io.kontour.ui.a11y.minimumTouchTarget
import io.kontour.ui.foundation.Text
import io.kontour.ui.interaction.Feedback
import io.kontour.ui.interaction.FeedbackIntent
import io.kontour.ui.theme.Theme
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
 *     onSelect = { chosen = it },
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
}

@Composable
fun CalendarMonth(
    month: LocalDate,
    isSelected: (LocalDate) -> Boolean,
    onSelect: (LocalDate) -> Unit,
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

    Column(modifier.fillMaxWidth()) {
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
                        color = Theme.colors.contentMuted,
                        modifier = Modifier.padding(vertical = Theme.spacing.xs),
                    )
                }
            }
        }

        val cells = leadingBlanks + daysInMonth
        val rows = (cells + 6) / 7

        var dragFrom by remember { mutableStateOf<LocalDate?>(null) }

        WeekGrid(
            rows = rows,
            leadingBlanks = leadingBlanks,
            daysInMonth = daysInMonth,
            month = month,
            isDateSelectable = isDateSelectable,
            onDragSelect = onDragSelect,
            dragFrom = dragFrom,
            setDragFrom = { dragFrom = it },
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
                            onSelect = onSelect,
                        )
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
    dragFrom: LocalDate?,
    setDragFrom: (LocalDate?) -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .pointerInput(onDragSelect, leadingBlanks, daysInMonth, rows) {
                if (onDragSelect == null) return@pointerInput

                fun dateAt(offset: Offset): LocalDate? {
                    if (size.width <= 0 || rows == 0) return null
                    val cell = size.width / 7f
                    val column = (offset.x / cell).toInt()
                    val row = (offset.y / cell).toInt()
                    if (column !in 0..6 || row !in 0 until rows) return null
                    val day = row * 7 + column - leadingBlanks + 1
                    if (day < 1 || day > daysInMonth) return null
                    val date = LocalDate(month.year, month.month, day)
                    return if (isDateSelectable(date)) date else null
                }

                detectDragGestures(
                    onDragStart = { offset ->
                        val start = dateAt(offset)
                        setDragFrom(start)
                        start?.let { onDragSelect(it, it) }
                    },
                    onDragEnd = { setDragFrom(null) },
                    onDragCancel = { setDragFrom(null) },
                ) { change, _ ->
                    val from = dragFrom ?: return@detectDragGestures
                    dateAt(change.position)?.let { onDragSelect(from, it) }
                }
            },
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
    onSelect: (LocalDate) -> Unit,
) {
    val colors = Theme.colors
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

    val container by animateColorAsState(
        targetValue = when {
            filled -> colors.primary
            inRange -> colors.accent.container
            else -> Color.Transparent
        },
        animationSpec = motion.tweenFast(),
        label = "dayContainer",
    )
    val label by animateColorAsState(
        targetValue = when {
            !enabled -> colors.contentDisabled
            filled -> colors.onPrimary
            inRange -> colors.accent.onContainer
            else -> colors.content
        },
        animationSpec = motion.tweenFast(),
        label = "dayLabel",
    )
    // The fill springs in rather than fading, so tapping through a range feels
    // like laying down stones rather than watching cells tint.
    val fillScale by animateFloatAsState(
        targetValue = if (filled) 1f else 0.7f,
        animationSpec = motion.springOrTween(motion.springBouncy),
        label = "dayFill",
    )

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .semantics {
                role = Role.Button
                this.selected = filled
                stateDescription = formats.dateFull(date)
            }
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
                .scale(if (filled) fillScale else 1f)
                .clip(shape)
                .background(container, shape)
                .then(
                    if (isToday && !filled) {
                        Modifier.border(
                            Theme.sizing.borderWidth,
                            colors.outlineStrong,
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
                .clickable(
                    interactionSource = interactions,
                    indication = null,
                    enabled = enabled,
                    role = Role.Button,
                    onClick = {
                        feedback.perform(FeedbackIntent.Selection)
                        onSelect(date)
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
                    style = if (isToday) Theme.typography.labelMedium else Theme.typography.bodyMedium,
                    color = label,
                )
                if (marker != null) {
                    Box(
                        Modifier
                            .padding(top = 2.dp)
                            .markerSize()
                            .clip(Theme.shapes.pill)
                            .background(if (filled) colors.onPrimary else marker)
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
