package io.kontour.ui.components.datetime

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import io.kontour.ui.interaction.HoldFeedback
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
    /** One column's width, in pixels. */
    val cell: Float,
    /**
     * One row's height, in pixels, **as laid out** — not assumed to be a column's.
     *
     * A day reserves the platform's minimum touch target, so where a column is
     * narrower than 48dp the rows are taller than the columns are wide. Read as
     * square, the hit test drifted down the month a few pixels a row, until a
     * finger on the bottom of the last day read as below the grid — past the
     * month's last day — and paged. Reported as the last day starting the timer
     * where the first needed pushing past.
     */
    val rowHeight: Float,
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
        return Offset(if (rtl) Columns - x else x, (position.y - origin.y) / rowHeight)
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
     * Which month [cells] is pushing past, if either.
     *
     * **Anywhere past the edge day in its row**, or above or below the grid there:
     * before the 1st — the blanks beside it, off the grid's start edge, over the
     * weekday initials — or after the last day the same way. Only a handle pushed
     * past the month's first or last day is asking for the month beyond it; being
     * near one is not. And however far past: a month starting on a Saturday
     * dragged back to where Monday would be is still pushing past its 1st, and a
     * dwell under way carries on rather than being cut off for going too far —
     * reported as wanting exactly that. A month starting on the first weekday
     * has no blank before the 1st, which is why off the edge counts: the pointer
     * keeps arriving after it leaves the grid, however little margin the page
     * left.
     */
    fun edgeAt(cells: Offset): DwellEdge? {
        if (cells.y >= -EdgeReach && (cells.y < 0f || (cells.y < 1f && cells.x < first % Columns))) {
            return DwellEdge.Previous
        }
        val end = last % Columns + 1f
        if (cells.y < rows + EdgeReach && (cells.y >= rows || (cells.y >= rows - 1f && cells.x >= end))) {
            return DwellEdge.Next
        }
        return null
    }

    /** The cell [edge] belongs to: the 1st, or the last day. */
    fun indexOf(edge: DwellEdge): Int = if (edge == DwellEdge.Previous) first else last

    /**
     * Whether a finger moving from [from] to [to] went over [edge]'s arrow: the
     * half of the edge day's cell on the other month's side, where the arrow is
     * drawn. Sampled along the move, so a quick one that jumps across the arrow
     * between two pointer events still counts.
     */
    fun crossesArrow(from: Offset, to: Offset, edge: DwellEdge): Boolean {
        val index = indexOf(edge)
        val row = (index / Columns).toFloat()
        val left = if (edge == DwellEdge.Previous) (index % Columns).toFloat() else index % Columns + 1f - ArrowShare
        for (step in 0..ArrowSamples) {
            val t = step / ArrowSamples.toFloat()
            val x = from.x + (to.x - from.x) * t
            val y = from.y + (to.y - from.y) * t
            if (x >= left && x <= left + ArrowShare && y >= row && y <= row + 1f) return true
        }
        return false
    }

    internal companion object {
        fun of(
            month: LocalDate,
            formats: DateTimeFormats,
            origin: Offset,
            cell: Float,
            rowHeight: Float,
            rtl: Boolean,
        ): GridGeometry {
            val first = LocalDate(month.year, month.month, 1)
            val days = first.plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY).day
            val blanks = formats.columnOf(first.dayOfWeek)
            // Not laid out yet: square is the best guess, and nothing is under a
            // finger before the first layout anyway.
            val row = if (rowHeight > 0f) rowHeight else cell
            return GridGeometry(first, blanks, days, (blanks + days + 6) / 7, origin, cell, row, rtl)
        }
    }
}

/** The two ways past the month a drag can page: before its first day, or after its last. */
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
 * changes by the header's arrows or by holding the handle past the month's first
 * or last day. So a date picker holds one of these over its pager and every month in
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
    private val hold: HoldFeedback,
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
    internal var rowHeight: Float = 0f

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
     * How far the band reaches into [row], in reading-order columns: 7 for a row
     * it covers to the end, 0 for one it has not reached. Forward, the band runs
     * from the anchor to it; backward, from it to the anchor. See `drawLiveBand`.
     *
     * **Across, it is the finger; down, it is the handle.** It followed the finger
     * both ways at first, so the band leaned into the next week as the finger
     * drifted below a day's middle — reported as small, unintentional movements
     * having big consequences. A finger does not drag in a straight line, and the
     * rest of a week flooding with colour because the thumb sagged a few pixels is
     * the grid overreacting. So in the handle's own row the band runs to the
     * finger, which keeps the day being left filled up to the handle, and it only
     * moves to another row when the handle snaps there.
     *
     * **And then it flows**, row by row from where each row's band was to where it
     * is going, together: a handle dropping a week fills the rest of its week and
     * the start of the next at once, and one wrapping from the end of a week to the
     * start of the next extends both ends at once, rather than the week above
     * snapping back to the finger's column and filling again.
     */
    fun boundary(row: Int): Float {
        val target = when {
            row < bandRow -> Columns.toFloat()
            row == bandRow -> bandX
            else -> 0f
        }
        val from = flowFrom ?: return target
        if (flow >= 1f || row !in from.indices) return target
        return from[row] + (target - from[row]) * flow
    }

    /** The row the band is flowing to, or has reached: the handle's. */
    var bandRow: Int by mutableIntStateOf(0)
        private set

    /** Across the handle's row, how far the band reaches: the finger's column. */
    var bandX: Float by mutableFloatStateOf(0f)
        private set

    /** Each row's reach when the band last set off for another row, and how far it has got. */
    private var flowFrom: FloatArray? by mutableStateOf(null)
    private var flow: Float by mutableFloatStateOf(1f)
    private var flowing: Job? = null

    /** The finger, in the same cells, unclamped: how near it is to the month's edge days. */
    var finger: Offset by mutableStateOf(Offset.Zero)
        private set

    /** Whether the finger is down. */
    var pressed: Boolean by mutableStateOf(false)
        private set

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

    /**
     * The month edge a dwell is under way on, if either: the finger went over its
     * arrow and is past its day. Its arrow shows in full while it lasts, however
     * far past the finger has gone — it faded with distance, and so disappeared
     * under a finger pushed well past the day with its ring still filling.
     */
    var edge: DwellEdge? by mutableStateOf(null)
        private set

    /** How far a dwell on [edge] has got, 0 to 1. At 1 the month pages. */
    val dwell = Animatable(0f)

    private var down = false
    private var position = Offset.Zero
    private var headCell: Int? = null
    private var armed = true

    /** Where the finger was at the last move in this month, for [GridGeometry.crossesArrow]. */
    private var lastCells: Offset? = null

    /** The arrow the finger has gone over and not yet left behind. */
    private var through: DwellEdge? = null
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
        armed = true
        through = null
        lastCells = cells
        dayTicker.at(date.toEpochDays().toInt())
        pageTicker.reset()
        pageTicker.at(pages)
        onSelect?.invoke(date, date)
        pressed = true
        finger = cells
        flowing?.cancel()
        flowFrom = null
        flow = 1f
        bandRow = index / Columns
        bandX = cells.x.coerceIn(0f, Columns.toFloat())
        // Nothing to travel: a cap appearing under a finger has not come from anywhere.
        lean(grid, date, index, cells, from = Offset.Zero)
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

        finger = cells
        // Over a day it may not have, the finger has gone somewhere the range
        // cannot follow: the band stays at the end it has.
        val headIndex = head?.let(grid::indexOf)
        val acrossAt = if (selectable || !grid.isDay(cells) || headIndex == null) {
            cells.x.coerceIn(0f, Columns.toFloat())
        } else {
            headIndex % Columns + 0.5f
        }
        if (headIndex != null && paged) {
            flowing?.cancel()
            flowFrom = null
            flow = 1f
            bandRow = headIndex / Columns
        } else if (headIndex != null && headIndex / Columns != bandRow) {
            // From wherever each row's band is now — mid-flow, if it was — so a
            // snap during a snap carries on rather than starting over.
            flowFrom = FloatArray(grid.rows) { boundary(it) }
            flow = 0f
            bandRow = headIndex / Columns
            flowing?.cancel()
            flowing = scope.launch {
                animate(0f, 1f, animationSpec = motion.springOrTween(motion.springSnappy)) { value, _ -> flow = value }
            }
        }
        bandX = acrossAt
        val leaningOn = head?.takeIf { headIndex != null }
        if (leaningOn != null && headIndex != null) lean(grid, leaningOn, headIndex, cells, crossing)

        // A move across a month change is not a path through anything.
        val previous = if (paged) cells else lastCells ?: cells
        lastCells = cells
        if (onStep == null) {
            dwellOn(null, null)
        } else {
            val crossed = DwellEdge.entries.firstOrNull { grid.crossesArrow(previous, cells, it) }
            dwellOn(grid.edgeAt(cells), crossed)
        }
    }

    fun end() {
        if (!down) return
        down = false
        pressed = false
        dayTicker.reset()
        dwellOn(null, null)
        val grid = geometry()
        val target = head?.let { h -> grid?.indexOf(h) }?.let { grid?.centreOf(it) }
        releasing = scope.launch {
            // Both home — the band to the end it has and the cap onto its own day —
            // and only then do the days take their own picture back, which is the
            // same picture.
            coroutineScope {
                if (target != null) {
                    launch {
                        animate(bandX, target.x, animationSpec = motion.springOrTween(motion.springSnappy)) { value, _ ->
                            bandX = value
                        }
                    }
                }
                launch { cap.animateTo(Offset.Zero, motion.springOrTween(motion.springSnappy)) }
                flowing?.join()
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
    private fun lean(grid: GridGeometry, date: LocalDate, index: Int, cells: Offset, from: Offset?) {
        val centre = grid.centreOf(index)
        val reading = Offset(
            (cells.x - centre.x).coerceIn(-MaxLean, MaxLean),
            (cells.y - centre.y).coerceIn(-MaxLean, MaxLean),
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
     * The finger is past [zone]'s day, or neither; this move went over [crossed]'s
     * arrow, or neither.
     *
     * **A dwell starts only for a finger that went over the arrow on its way past
     * the day** — reported: *"the animation should only start if you drag over or
     * through the arrow, not just dragging up/down onto the first/last row from
     * anywhere."* Past the day but arrived from above or below, a finger is on
     * the blanks, not asking for another month. And a finger resting on the
     * arrow without going past the day is choosing the 1st, not paging.
     *
     * **A dwell pages once, and then the edge is spent until the handle leaves
     * it.** Two months that start on the same weekday put the 1st in the same
     * place, so a finger held past it would otherwise page and page again.
     * Leaving both edges, and both arrows, re-arms.
     */
    private fun dwellOn(zone: DwellEdge?, crossed: DwellEdge?) {
        // Back off the edge re-arms, even by way of the arrow; the arrow is
        // forgotten only once the finger is off it too.
        if (zone == null) armed = true
        if (crossed != null) {
            through = crossed
        } else if (zone == null) {
            through = null
        }
        val wanted = zone?.takeIf { armed && it == through }
        if (wanted == edge) return
        dwelling?.cancel()
        dwelling = null
        edge = wanted
        scope.launch { dwell.snapTo(0f) }
        if (wanted == null) return
        dwelling = scope.launch {
            dwell.snapTo(0f)
            // A faint rumble while the ring fills, building as it does, off the
            // ring's own clock, so the hand knows the wait is counting. It stops
            // before the page's threshold tick below, and in the `finally` when
            // the finger leaves early, so no dwell can leave it running.
            hold.start()
            try {
                dwell.animateTo(1f, tween(DwellMillis, easing = LinearEasing)) {
                    hold.update(value)
                }
            } finally {
                hold.stop()
            }
            armed = false
            through = null
            edge = null
            dwell.snapTo(0f)
            pages++
            pageTicker.at(pages)
            onStep?.invoke(wanted.step)
        }
    }

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
 * The band between the anchor and the handle, drawn by the grid while a drag is live.
 *
 * **Across a row it runs to the handle**, and so always under the leaning cap:
 * the day being left fills behind it. **Where the handle has snapped to another
 * week, each row's end flows** from where it was to where it now is — the rest
 * of the upper week filling, and the start of the lower one up to the handle's
 * column — rather than jumping; [boundary] is each row's end, and
 * [CalendarDragState.boundary] is where the flow lives.
 *
 * Reported as wanting exactly that: *"the accent colour should always be touching
 * the top and/or the start of the handle we're dragging."*
 *
 * @param anchorAt The anchor's cell in this month, or null when it is in another.
 * @param forward Whether the range runs from the anchor on to the point.
 */
internal fun DrawScope.drawLiveBand(
    leadingBlanks: Int,
    daysInMonth: Int,
    rows: Int,
    anchorAt: Int?,
    forward: Boolean,
    boundary: (Int) -> Float,
    colour: Color,
    inset: Float,
    rtl: Boolean,
) {
    if (rows <= 0) return
    val cellWidth = size.width / Columns
    val rowHeight = size.height / rows
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
 * The way to the next month from its first or last day: a small chevron in the
 * day's own box, on the side the other month is, in a little ring that fills as
 * the handle is held past the day.
 *
 * In the day's box rather than a cell of its own — reported: *"make it part of
 * the first/last day's box, so we don't get issues with months that start/end
 * on a monday/sunday"*, where there is no blank beside the day to put anything
 * in. And round the chevron alone rather than the whole day — *"rather than
 * circling the whole number … just circle the arrow"* — so the day keeps its own
 * look, cap and all, and the ring is plainly the arrow's.
 *
 * Drawn rather than an icon, because the library ships no icon set and this is
 * not a control anyone taps: the header's arrows are the way to page for
 * everything but a finger already busy with a drag.
 *
 * @param day The middle of the edge day's cell.
 * @param halfCell Half the cell, in pixels: the chevron sits against its inner edge.
 * @param pointsLeft Which way the chevron points, and which side of the number it
 *   sits: toward the other month.
 */
internal fun DrawScope.drawEdgeArrow(
    day: Offset,
    halfCell: Float,
    pointsLeft: Boolean,
    progress: Float,
    alpha: Float,
    ring: Color,
    fill: Color,
    chevron: Color,
) {
    if (alpha <= 0f) return
    val radius = ArrowRingRadius.toPx()
    val stroke = RingStroke.toPx()
    val side = if (pointsLeft) -1f else 1f
    val centre = Offset(day.x + side * (halfCell - radius - ArrowRingInset.toPx()), day.y)
    drawCircle(ring, radius = radius, center = centre, alpha = alpha, style = Stroke(stroke))
    if (progress > 0f) {
        drawArc(
            color = fill,
            startAngle = -90f,
            sweepAngle = 360f * progress.coerceIn(0f, 1f),
            useCenter = false,
            topLeft = Offset(centre.x - radius, centre.y - radius),
            size = Size(radius * 2f, radius * 2f),
            alpha = alpha,
            style = Stroke(stroke * ProgressStrokeShare, cap = StrokeCap.Round),
        )
    }
    val arm = radius * ChevronShare
    // A hair toward the tip, so the chevron looks centred in its ring.
    val tip = centre.x + side * arm * 0.25f
    val path = Path().apply {
        moveTo(tip - side * arm / 2f, centre.y - arm)
        lineTo(tip + side * arm / 2f, centre.y)
        lineTo(tip - side * arm / 2f, centre.y + arm)
    }
    drawPath(path, chevron, alpha = alpha, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/**
 * How much of the arrow shows for a finger [distance] cells from its day: none
 * from two and a half cells out, all of it within three quarters of one. The
 * distance is straight-line, so coming at the day from above counts as much as
 * coming at it along the row.
 */
internal fun ringPresence(distance: Float): Float =
    ((RingFadeFrom - distance) / (RingFadeFrom - RingFadeFull)).coerceIn(0f, 1f)

internal fun distance(a: Offset, b: Offset): Float = hypot(a.x - b.x, a.y - b.y)

/** Seven columns, one a weekday. */
internal const val Columns: Int = 7

/** How long the handle is held past the month's edge day before the month pages. */
internal const val DwellMillis: Int = 700

/** The most a cap leans toward the finger, in cells, before the pull. */
private const val MaxLean: Float = 0.5f

/** The share of the edge day's cell, on the other month's side, that is its arrow. */
private const val ArrowShare: Float = 0.5f

/** Points along a move tested against an arrow. */
private const val ArrowSamples: Int = 12

/** How far above or below the grid, in rows, a finger still counts as pushing past an edge day. */
private const val EdgeReach: Float = 1.5f

private const val RingFadeFrom: Float = 2.5f
private const val RingFadeFull: Float = 0.75f

/** The ring round the arrow, and how far it sits in from the day's box. */
internal val ArrowRingRadius = 6.5.dp
private val ArrowRingInset = 1.5.dp
private val RingStroke = 1.5.dp

/** The progress arc, a little bolder than the ring it fills. */
private const val ProgressStrokeShare: Float = 1.4f

/** The chevron's half-height against its ring's radius. */
private const val ChevronShare: Float = 0.42f
