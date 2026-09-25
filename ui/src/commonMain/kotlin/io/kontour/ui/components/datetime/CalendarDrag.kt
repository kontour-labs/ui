package io.kontour.ui.components.datetime

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.selection.SliderDefaults
import io.kontour.ui.interaction.DetentTicker
import io.kontour.ui.theme.Motion
import io.kontour.ui.theme.kontourMotion
import kotlin.math.floor
import kotlin.math.hypot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/**
 * One month's grid, as a drag reads it: which cells hold days, where the grid is
 * in the node the pointer arrives at, and which way its columns run.
 *
 * Arithmetic, not measurement, like the hit test it replaces — the columns are
 * seven equal weights and every row is one cell tall — so it can be made for the
 * month on show at the moment of a pointer event without anything having laid
 * that month out yet. Which is the point when the month has just changed under a
 * finger that is still down.
 */
internal class GridGeometry(
    /** The first of the month. */
    val month: LocalDate,
    val leadingBlanks: Int,
    val daysInMonth: Int,
    val rows: Int,
    /** The grid's top-left in the pointer node's own coordinates. */
    val origin: Offset,
    /** One column's width, and one row's height, in pixels. */
    val cell: Float,
    val rtl: Boolean,
) {
    /** The cell index of the 1st, and of the last day. */
    val first: Int get() = leadingBlanks
    val last: Int get() = leadingBlanks + daysInMonth - 1

    fun dateAt(index: Int): LocalDate? {
        val day = index - leadingBlanks + 1
        return if (day in 1..daysInMonth) LocalDate(month.year, month.month, day) else null
    }

    fun indexOf(date: LocalDate): Int? =
        if (date.year == month.year && date.month == month.month) leadingBlanks + date.day - 1 else null

    /**
     * Where [position] is, in cells: a column counted in **reading order** — from
     * the right, right to left — and a row. The one place a physical position is
     * turned into a logical one; everything downstream is in these units.
     */
    fun cellsAt(position: Offset): Offset {
        val x = (position.x - origin.x) / cell
        return Offset(if (rtl) Columns - x else x, (position.y - origin.y) / cell)
    }

    /** Whether [cells] is over a day, rather than a blank or off the grid. */
    fun isDay(cells: Offset): Boolean =
        cells.x >= 0f && cells.x < Columns && cells.y >= 0f && cells.y < rows &&
            (floor(cells.y).toInt() * 7 + floor(cells.x).toInt()) in first..last

    /**
     * The day nearest [cells] in reading order: the one under it, or — over a
     * blank or off the grid — the 1st before the month's first day and the last
     * day after its last. A finger that leaves the days does not leave the drag.
     */
    fun nearestIndex(cells: Offset): Int {
        val row = floor(cells.y).toInt().coerceIn(0, rows - 1)
        val column = floor(cells.x).toInt().coerceIn(0, Columns - 1)
        return (row * Columns + column).coerceIn(first, last)
    }

    /** The middle of cell [index], in cells. */
    fun centreOf(index: Int): Offset = Offset(index % Columns + 0.5f, index / Columns + 0.5f)

    /**
     * Which month arrow [cells] is on, if either.
     *
     * The arrow's own cell and everything past it in its row: before the 1st in
     * the first row, after the last day in the last. **Past the grid's edge
     * counts** — a month starting on the first weekday has no blank to put the
     * arrow in, so it hangs outside the edge, and pushing the handle past the edge
     * in that row is how it is reached however little margin the page left.
     */
    fun edgeAt(cells: Offset): DwellEdge? {
        if (cells.y >= -EdgeReach && cells.y < 1f && cells.x < first % Columns) return DwellEdge.Previous
        val end = last % Columns + 1f
        if (cells.y >= rows - 1f && cells.y < rows + EdgeReach && cells.x >= end) return DwellEdge.Next
        return null
    }

    internal companion object {
        fun of(month: LocalDate, formats: DateTimeFormats, origin: Offset, cell: Float, rtl: Boolean): GridGeometry {
            val first = LocalDate(month.year, month.month, 1)
            val days = first.plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY).day
            val blanks = formats.columnOf(first.dayOfWeek)
            return GridGeometry(first, blanks, days, (blanks + days + 6) / 7, origin, cell, rtl)
        }
    }
}

/** The two month arrows a drag can page with. */
internal enum class DwellEdge(val step: Int) { Previous(-1), Next(1) }

/**
 * A range being dragged out, from the finger going down to the picture settling
 * after it lifts.
 *
 * **Its own object, above the month it is in**, because a drag has to outlive
 * the month. It lived in each month's grid, inside the pager, so paging while a
 * finger was down threw the gesture away with the grid it belonged to — and the
 * grid paged in never heard about a finger that had gone down somewhere else.
 * Reported as wanting the drag to carry on across the border, whether the month
 * changes by the header's arrows or by the arrows a drag shows at the month's
 * edges. So a date picker holds one of these over its pager and every month in
 * it draws from it; a `CalendarMonth` on its own holds its own.
 *
 * The anchor is a **date**, not a cell, for the same reason: it may be in a
 * month that is no longer on screen.
 */
@Stable
internal class CalendarDragState internal constructor(
    private val scope: CoroutineScope,
    private val dayTicker: DetentTicker,
    private val pageTicker: DetentTicker,
) {
    // Wiring, refreshed from composition.
    internal var motion: Motion = kontourMotion(reduceMotion = false)
    internal var onSelect: ((LocalDate, LocalDate) -> Unit)? = null
    internal var isSelectable: (LocalDate) -> Boolean = { true }

    /** Pages the calendar by a month either way; null where there is nothing to page. */
    internal var onStep: ((Int) -> Unit)? = null

    /** The grid the pointer is over now, from whoever holds the pointer input. */
    internal var geometry: () -> GridGeometry? = { null }

    /** The pointer node's width, and where the grid starts down it. Measured. */
    internal var width: Float = 0f
    internal var gridTop: Float = 0f

    /** Where the finger went down. */
    var anchor: LocalDate? by mutableStateOf(null)
        private set

    /** The moving end: the day under the finger, or the nearest one it may have. */
    var head: LocalDate? by mutableStateOf(null)
        private set

    /** Whether a drag's picture is up — from the anchor landing until the release has settled. */
    var live: Boolean by mutableStateOf(false)
        private set

    /** The first of the month the picture is in. */
    var month: LocalDate? by mutableStateOf(null)
        private set

    /**
     * The finger, in cells of [month]'s grid: a reading-order column and a row.
     * The band is drawn to it. Snapped while the finger is down, sprung to the
     * head's middle when it lifts.
     */
    val point = Animatable(Offset.Zero, Offset.VectorConverter)

    /**
     * Where the moving end's cap is drawn, in cells from its own, physical. See
     * `DayCell`, and `DetentPull` on the range slider, which this follows: the
     * spring's target is the pull toward the finger, and a crossing re-bases the
     * spring rather than restarting it.
     */
    val cap = Animatable(Offset.Zero, Offset.VectorConverter)

    /** The date [cap] is drawn for. The anchor is a cap too, and stays put. */
    var leaning: LocalDate? by mutableStateOf(null)
        private set

    /** The month arrow the finger is on, if any. */
    var edge: DwellEdge? by mutableStateOf(null)
        private set

    /** How far a dwell on [edge] has got, 0 to 1. At 1 the month pages. */
    val dwell = Animatable(0f)

    private var down = false
    private var position = Offset.Zero
    private var headCell: Int? = null
    private var armed = true
    private var pages = 0
    private var dwelling: Job? = null
    private var releasing: Job? = null

    fun start(at: Offset) {
        val grid = geometry() ?: return
        val cells = grid.cellsAt(at)
        if (!grid.isDay(cells)) return
        val index = grid.nearestIndex(cells)
        val date = grid.dateAt(index)?.takeIf(isSelectable) ?: return
        releasing?.cancel()
        down = true
        position = at
        anchor = date
        head = date
        headCell = index
        month = grid.month
        live = true
        armed = grid.edgeAt(cells) == null
        dayTicker.at(date.toEpochDays().toInt())
        pageTicker.reset()
        pageTicker.at(pages)
        onSelect?.invoke(date, date)
        val p = clampedPoint(cells, grid)
        scope.launch { point.snapTo(p) }
        // Nothing to travel: a cap appearing under a finger has not come from anywhere.
        lean(grid, date, index, p, from = Offset.Zero)
    }

    fun move(at: Offset) {
        if (!down) return
        position = at
        track()
    }

    /**
     * Reads the finger again, against whatever month is on show now.
     *
     * Every move comes through here, and so does a month changing while the finger
     * is still: from a header arrow pressed with another finger, or from a dwell.
     * Either way the drag carries on in the new month from where the finger is.
     */
    fun track() {
        if (!down) return
        val grid = geometry() ?: return
        val from = anchor ?: return
        val cells = grid.cellsAt(position)
        val paged = month != grid.month
        month = grid.month

        val index = grid.nearestIndex(cells)
        val under = grid.dateAt(index) ?: return
        val selectable = isSelectable(under)
        var crossing: Offset? = null
        if (selectable && (under != head || paged)) {
            val previous = headCell
            // A unit vector from the new cell to the old one, in cells, physical:
            // the cap enters from beside itself rather than flying across the
            // month when the range wraps to another week. See `cap`.
            crossing = if (paged || previous == null) {
                Offset.Zero
            } else {
                val across = (previous % Columns - index % Columns).toFloat() * (if (grid.rtl) -1f else 1f)
                val rowsUp = (previous / Columns - index / Columns).toFloat()
                val length = hypot(across, rowsUp)
                if (length <= 0f) Offset.Zero else Offset(across / length, rowsUp / length)
            }
            head = under
            headCell = index
            dayTicker.at(under.toEpochDays().toInt())
            onSelect?.invoke(from, under)
        }

        // Over a day it may not have, the finger has gone somewhere the range
        // cannot follow: the band stays at the end it has.
        val headIndex = head?.let(grid::indexOf)
        val p = when {
            selectable || !grid.isDay(cells) -> clampedPoint(cells, grid)
            headIndex != null -> grid.centreOf(headIndex)
            else -> clampedPoint(cells, grid)
        }
        scope.launch { point.snapTo(p) }
        val leaningOn = head?.takeIf { headIndex != null }
        if (leaningOn != null && headIndex != null) lean(grid, leaningOn, headIndex, p, crossing)

        dwellOn(if (onStep == null) null else grid.edgeAt(cells))
    }

    fun end() {
        if (!down) return
        down = false
        dayTicker.reset()
        dwellOn(null)
        val grid = geometry()
        val target = head?.let { h -> grid?.indexOf(h) }?.let { grid?.centreOf(it) }
        releasing = scope.launch {
            val spec = motion.springOrTween<Offset>(motion.springSnappy)
            // Both home — the band to the end it has and the cap onto its own day —
            // and only then do the days take their own picture back, which is the
            // same picture.
            coroutineScope {
                if (target != null) launch { point.animateTo(target, spec) }
                launch { cap.animateTo(Offset.Zero, spec) }
            }
            live = false
            leaning = null
            anchor = null
            head = null
            headCell = null
        }
    }

    /**
     * Aims the cap: re-based by [from] if a cell was crossed, then pulled toward the
     * finger. In one coroutine, so the date it is measured from and the value it is
     * measured by change together. `Offset.Zero` is a cap with nowhere to come from.
     */
    private fun lean(grid: GridGeometry, date: LocalDate, index: Int, p: Offset, from: Offset?) {
        val centre = grid.centreOf(index)
        val reading = Offset(
            (p.x - centre.x).coerceIn(-MaxLean, MaxLean),
            (p.y - centre.y).coerceIn(-MaxLean, MaxLean),
        )
        val lean = Offset(if (grid.rtl) -reading.x else reading.x, reading.y)
        scope.launch {
            when (from) {
                Offset.Zero -> cap.snapTo(Offset.Zero)
                null -> Unit
                else -> cap.snapTo(cap.value + from)
            }
            leaning = date
            cap.animateTo(lean * SliderDefaults.DetentPull, motion.springOrTween(motion.springSnappy))
        }
    }

    /**
     * The finger is on [zone]'s arrow, or on neither.
     *
     * **A dwell pages once, and then the arrow is spent until the handle leaves
     * it.** Two months that start on the same weekday put the previous-month
     * arrow in the same place, so a finger held there would otherwise page and
     * page again. Leaving every arrow re-arms.
     */
    private fun dwellOn(zone: DwellEdge?) {
        if (zone == null) armed = true
        if (zone == edge) return
        dwelling?.cancel()
        dwelling = null
        edge = zone
        scope.launch { dwell.snapTo(0f) }
        if (zone == null || !armed) return
        dwelling = scope.launch {
            dwell.snapTo(0f)
            dwell.animateTo(1f, tween(DwellMillis, easing = LinearEasing))
            armed = false
            dwell.snapTo(0f)
            pages++
            pageTicker.at(pages)
            onStep?.invoke(zone.step)
        }
    }

    private fun clampedPoint(cells: Offset, grid: GridGeometry): Offset =
        Offset(cells.x.coerceIn(0f, Columns.toFloat()), cells.y.coerceIn(0.5f, grid.rows - 0.5f))
}

/** The drag a date picker holds over its pager, for the months inside it. */
internal val LocalCalendarDrag = staticCompositionLocalOf<CalendarDragState?> { null }

/** The pointer input for [drag], on whatever node holds the grid. */
internal fun Modifier.calendarDragInput(drag: CalendarDragState): Modifier =
    onSizeChanged { drag.width = it.width.toFloat() }
        .pointerInput(drag) {
            try {
                // `detectDragGestures` waits for touch slop, so a tap still belongs
                // to the cell it landed on and the two never argue over one press.
                detectDragGestures(
                    onDragStart = { drag.start(it) },
                    onDragEnd = { drag.end() },
                    onDragCancel = { drag.end() },
                ) { change, _ -> drag.move(change.position) }
            } finally {
                drag.end()
            }
        }

/** This week, starting on [first] instead, or itself if it already does. */
internal fun DateTimeFormats.startingOn(first: DayOfWeek): DateTimeFormats =
    if (first == firstDayOfWeek) this else copy(firstDayOfWeek = first)

/**
 * The band between the anchor and the finger, drawn by the grid while a drag is
 * live.
 *
 * **It is the blend of the two rows the finger is between.** On a row's middle it
 * is exactly the range the finger's day would make. Halfway to the next row down,
 * it is halfway between that and the range the day below would make: the rest of
 * this week half filled, and the start of the next half filled up to the day the
 * finger is heading for. Across a row it runs to the finger, which is always
 * under the cap. So the accent flows into the days about to be chosen, and out of
 * the ones about to be let go, and always touches the handle — reported as
 * wanting exactly that: *"the accent colour should always be touching the top
 * and/or the start of the handle we're dragging."*
 *
 * Continuous everywhere, including where the head changes cell, because it is a
 * function of the finger and not of which cell is the head.
 *
 * @param anchorAt The anchor's cell in this month, or null when it is in another.
 * @param forward Whether the range runs from the anchor on to the finger.
 */
internal fun DrawScope.drawLiveBand(
    leadingBlanks: Int,
    daysInMonth: Int,
    rows: Int,
    anchorAt: Int?,
    forward: Boolean,
    point: Offset,
    colour: Color,
    inset: Float,
    rtl: Boolean,
) {
    if (rows <= 0) return
    val cellWidth = size.width / Columns
    val rowHeight = size.height / rows
    val x = point.x.coerceIn(0f, Columns.toFloat())
    val y = point.y.coerceIn(0.5f, rows - 0.5f)
    val upper = floor(y - 0.5f).toInt().coerceIn(0, rows - 1)
    // Still for the first stretch either side of a row's middle, so a finger
    // resting a hair off it does not draw a sliver at the start of the next week;
    // half-blended at the boundary whichever side it is read from, as before.
    val t = ((y - 0.5f - upper - RowRest) / (1f - 2f * RowRest)).coerceIn(0f, 1f)
    fun boundary(row: Int): Float = when {
        row < upper -> Columns.toFloat()
        row == upper -> x + t * (Columns - x)
        row == upper + 1 -> t * x
        else -> 0f
    }
    val first = leadingBlanks
    val last = leadingBlanks + daysInMonth - 1
    for (row in 0 until rows) {
        var from: Float
        var to: Float
        if (forward) {
            from = when {
                anchorAt == null -> 0f
                row < anchorAt / Columns -> continue
                row == anchorAt / Columns -> anchorAt % Columns + 0.5f
                else -> 0f
            }
            to = boundary(row)
        } else {
            from = boundary(row)
            to = when {
                anchorAt == null -> Columns.toFloat()
                row > anchorAt / Columns -> continue
                row == anchorAt / Columns -> anchorAt % Columns + 0.5f
                else -> Columns.toFloat()
            }
        }
        // Only over days: never into the blanks either side of the month.
        from = maxOf(from, (first - row * Columns).coerceIn(0, Columns).toFloat())
        to = minOf(to, (last + 1 - row * Columns).coerceIn(0, Columns).toFloat())
        if (to <= from) continue
        val left = if (rtl) (Columns - to) * cellWidth else from * cellWidth
        drawRect(
            colour,
            topLeft = Offset(left, row * rowHeight + inset),
            size = Size((to - from) * cellWidth, rowHeight - inset * 2f),
        )
    }
}

/**
 * A month arrow: a small chevron in a ring that fills as the handle dwells on it.
 *
 * Drawn rather than an icon, because the library ships no icon set and this is
 * not a control anyone taps — the header's buttons are the way to page for
 * everything but a finger already busy with a drag.
 */
internal fun DrawScope.drawMonthArrow(
    centre: Offset,
    pointsLeft: Boolean,
    progress: Float,
    alpha: Float,
    face: Color,
    ring: Color,
    fill: Color,
    chevron: Color,
) {
    if (alpha <= 0f) return
    val radius = ArrowRadius.toPx()
    val stroke = ArrowStroke.toPx()
    drawCircle(face, radius = radius, center = centre, alpha = alpha)
    drawCircle(ring, radius = radius - stroke / 2f, center = centre, alpha = alpha, style = Stroke(stroke))
    if (progress > 0f) {
        val inner = radius - stroke / 2f
        drawArc(
            color = fill,
            startAngle = -90f,
            sweepAngle = 360f * progress.coerceIn(0f, 1f),
            useCenter = false,
            topLeft = Offset(centre.x - inner, centre.y - inner),
            size = Size(inner * 2f, inner * 2f),
            alpha = alpha,
            style = Stroke(stroke, cap = StrokeCap.Round),
        )
    }
    val arm = radius * ChevronShare
    val lean = if (pointsLeft) 1f else -1f
    val path = Path().apply {
        moveTo(centre.x + lean * arm / 2f, centre.y - arm)
        lineTo(centre.x - lean * arm / 2f, centre.y)
        lineTo(centre.x + lean * arm / 2f, centre.y + arm)
    }
    drawPath(path, chevron, alpha = alpha, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/** Seven columns, one a weekday. */
internal const val Columns: Int = 7

/** How long the handle rests on a month arrow before the month pages. */
internal const val DwellMillis: Int = 700

/** Within this many days of the month's first or last, its arrow shows. */
internal const val EdgeDays: Int = 3

/** How far from a row's middle, in rows, the band waits before it flows toward the next. */
private const val RowRest: Float = 0.12f

/** The most a cap leans toward the finger, in cells, before the pull. */
private const val MaxLean: Float = 0.5f

/** How far outside the grid, in cells, a finger still counts as on an edge arrow's row. */
private const val EdgeReach: Float = 0.75f

internal val ArrowRadius = 12.dp
internal val ArrowGap = 4.dp
private val ArrowStroke = 2.dp

/** The chevron's half-height against the arrow's radius. */
private const val ChevronShare: Float = 0.4f
