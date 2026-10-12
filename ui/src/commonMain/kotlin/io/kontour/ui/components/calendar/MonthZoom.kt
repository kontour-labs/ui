package io.kontour.ui.components.calendar

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Where the list's first row goes, as a row and how far into it, for what was
 * at [fromY] to be at [toY] as each row goes from [oldRow] pixels tall to
 * [newRow]: [top] is where the first row is now, in rows — its index and how
 * far down it the list's top is. [fromY] and [toY] differ when the fingers
 * move together as well as apart, and the weeks pan with them.
 *
 * **In doubles.** The list's rows are numbered from a million, and a float
 * there has a sixteenth of a row to spare: at a hundred pixels a row, the week
 * under the fingers moved in six-pixel steps — the pinch's jitter.
 */
internal fun zoomAnchor(top: Double, fromY: Float, toY: Float, oldRow: Float, newRow: Float): Double {
    if (oldRow <= 0f || newRow <= 0f) return top
    return top + fromY / oldRow.toDouble() - toY / newRow.toDouble()
}

/**
 * How many lines each of a month's events has room for in a row [rowPx] tall
 * under its date [dateLabelPx]: as many — up to three — as still leave room
 * for [MinItemsPerDay] events of that many lines, each [linePx] a line and
 * [padPx] above and below. Zoomed in, a line is its title, then its time, then
 * where it is.
 */
internal fun itemLines(rowPx: Float, dateLabelPx: Float, linePx: Float, padPx: Float): Int =
    (MaxItemLines downTo 2).firstOrNull { lines ->
        floor((rowPx - dateLabelPx) / (lines * linePx + padPx * 2)) >= MinItemsPerDay
    } ?: 1

/**
 * How tall a month's event is, in px, with [rows] weeks in [viewportPx]: at a
 * whole number of weeks, as many of [itemLines] as there is room for, and
 * between two, part of the way from one's height to the other's — as far as
 * the row is from one's height to the other's.
 *
 * So under two fingers an event grows and shrinks with its week rather than
 * standing still until they let go and then jumping, and at rest it is the
 * height it always was. [fewest] and [most] are the whole numbers of weeks the
 * month rests at.
 */
internal fun monthItemPx(
    rows: Float,
    viewportPx: Float,
    leastRowPx: Float,
    dateLabelPx: Float,
    linePx: Float,
    padPx: Float,
    fewest: Float,
    most: Float,
): Float {
    fun at(r: Float) = itemLines(monthRowPx(viewportPx, r, leastRowPx), dateLabelPx, linePx, padPx) * linePx + padPx * 2
    val hi = maxOf(fewest, most)
    val r = rows.coerceIn(fewest, hi)
    val r0 = floor(r)
    val r1 = minOf(r0 + 1f, hi)
    if (r1 <= r0) return at(r0)
    val p0 = monthRowPx(viewportPx, r0, leastRowPx)
    val p1 = monthRowPx(viewportPx, r1, leastRowPx)
    val t = if (p0 == p1) 0f else ((monthRowPx(viewportPx, r, leastRowPx) - p1) / (p0 - p1)).coerceIn(0f, 1f)
    return at(r1) + (at(r0) - at(r1)) * t
}

/** How many lines of an event fit in one [itemPx] tall: only a whole line's room gives it another. */
internal fun itemContentLines(itemPx: Float, linePx: Float, padPx: Float): Int =
    if (linePx <= 0f) 1 else floor((itemPx - padPx * 2) / linePx + LineSlack).toInt().coerceIn(1, MaxItemLines)

/** Rounding's worth of a line, so an event exactly three lines tall has three. */
private const val LineSlack = 0.001f

/** The most lines a month's event is given. */
private const val MaxItemLines = 3

/** How many events a day keeps room for before its events are given more lines each. */
private const val MinItemsPerDay = 3

/** The fewest weeks a month shows at once, zoomed all the way in. */
internal const val MinMonthRows = 3

/**
 * A month's zoom: how many weeks it shows, and the list kept still under the
 * fingers, or the pointer, as that changes. The weeks shown are the state's
 * `monthRows`, read and written there; [viewportPx] and the limits are as the
 * month last laid out.
 */
@Stable
internal class MonthZoom(
    private val state: EventCalendarState,
    private val list: LazyListState,
    private val scope: CoroutineScope,
    /** Whether a pinch is under way: what holds the month's other settling off, and its events' lines as they were. */
    private val zooming: MutableState<Boolean>,
) {
    var viewportPx = 0f
    var reduceMotion = false
    var fewest = MinMonthRows.toFloat()
    var most = MonthRowsInView.toFloat()

    /** The least a row is: see [monthRowPx]. */
    var leastRowPx = 1f

    /** Where the first row is, in rows, as this zoom has put it: the list's own lags a frame behind. */
    private var top: Double? = null

    /** The slide to a whole week after a zoom, cancelled by the next pinch rather than fought. */
    private var settling: Job? = null

    private fun rowPx(rows: Float) = monthRowPx(viewportPx, rows, leastRowPx)

    private fun currentTop(): Double {
        top?.let { return it }
        val first = list.layoutInfo.visibleItemsInfo.firstOrNull() ?: return list.firstVisibleItemIndex.toDouble()
        return first.index - first.offset.toDouble() / first.size.coerceAtLeast(1)
    }

    /** Zoomed to [rows] weeks, what was at [fromY] now at [toY]. */
    fun zoomTo(rows: Float, fromY: Float, toY: Float = fromY) {
        val next = rows.coerceIn(fewest, maxOf(fewest, most))
        val now = state.monthRows.coerceIn(fewest, maxOf(fewest, most))
        if ((next == now && fromY == toY) || viewportPx <= 0f) return
        val newRow = rowPx(next)
        val moved = zoomAnchor(currentTop(), fromY, toY, rowPx(now), newRow).coerceAtLeast(0.0)
        top = moved
        state.monthRows = next
        val index = floor(moved).toInt()
        list.requestScrollToItem(index, ((moved - index) * newRow).roundToInt())
    }

    /** Two fingers down and moving apart or together: whatever was settling stops. */
    fun begin() {
        settling?.cancel()
        settling = null
        top = null
        zooming.value = true
    }

    /**
     * Pinched by [factor] about [focusY], the fingers' middle having come from
     * [fromY]: spread, fewer weeks; pinched in, more; and the weeks move with
     * the fingers as they go.
     */
    fun pinch(factor: Float, focusY: Float, fromY: Float = focusY) {
        if (factor <= 0f) return
        zoomTo(state.monthRows / factor, fromY, focusY)
    }

    /** One whole week more, or fewer, about [focusY]: a wheel's turn, a key. */
    fun step(weeks: Int, focusY: Float) {
        begin()
        zoomTo(state.monthRows.roundToInt().toFloat() + weeks, focusY)
        settle()
    }

    /** Let go: to the nearest whole number of weeks, settled with a week at the top. */
    fun settle(focusY: Float = 0f) {
        val whole = state.monthRows.roundToInt().toFloat().coerceIn(fewest, maxOf(fewest, most))
        if (whole != state.monthRows) zoomTo(whole, focusY)
        val at = top
        top = null
        if (at == null) {
            zooming.value = false
            return
        }
        val row = at.roundToInt().coerceAtLeast(0)
        // Read here, where it is used: settled on its week at once under
        // reduced motion, rather than slid there.
        val reduceMotion = reduceMotion
        settling = scope.launch {
            try {
                if (reduceMotion) list.scrollToItem(row) else list.animateScrollToItem(row)
            } finally {
                zooming.value = false
            }
        }
    }
}

/**
 * How tall a month's row is with [rows] of them in [viewportPx]: whole pixels,
 * the same for every row, so what the zoom works out is what the list lays out.
 *
 * **Never under [least]**, a date and an event. A month given next to no
 * height — a header taller than the box it shares — had rows of nothing, and a
 * lazy list of rows of nothing composes every row it has to fill the space:
 * two million weeks, and the heap with them.
 */
internal fun monthRowPx(viewportPx: Float, rows: Float, least: Float = 1f): Float =
    maxOf(if (rows <= 0f) 0f else floor(viewportPx / rows), least, 1f)

/**
 * Two fingers spread or pinched over the month zoom it, about the point
 * between them: before the list or a held event sees them, but only once they
 * have moved apart or together past the slop, and never while an edit is in
 * hand. One finger is never taken — it is the list's scroll, or a hold.
 */
internal fun Modifier.pinchesRows(zoom: MonthZoom, enabled: () -> Boolean): Modifier = pointerInput(zoom) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var zooming = false
        var startSpan = 0f
        var last = 0f
        var focusY = 0f
        var lastFocusY = 0f
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val pressed = event.changes.filter { it.pressed }
            if (pressed.size >= 2 && (zooming || enabled())) {
                val a = pressed[0].position
                val b = pressed[1].position
                val span = (a - b).getDistance()
                focusY = (a.y + b.y) / 2f
                if (!zooming) {
                    if (startSpan == 0f) startSpan = span
                    if (abs(span - startSpan) > viewConfiguration.touchSlop) {
                        zooming = true
                        last = span
                        lastFocusY = focusY
                        zoom.begin()
                    }
                }
                if (zooming) {
                    if (last > 0f && span > 0f) zoom.pinch(span / last, focusY, lastFocusY)
                    last = span
                    lastFocusY = focusY
                    event.changes.forEach { it.consume() }
                }
            } else if (zooming) {
                // The finger left behind is not a scroll, nor a hold.
                event.changes.forEach { it.consume() }
            }
        } while (event.changes.any { it.pressed })
        if (zooming) zoom.settle(focusY)
    }
}

/**
 * Ctrl, or Cmd, and the wheel zoom the month a week at a time about the
 * pointer, as a browser's page zooms: up for fewer weeks, down for more.
 */
internal fun Modifier.wheelZoomsRows(zoom: MonthZoom): Modifier = pointerInput(zoom) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.type != PointerEventType.Scroll) continue
            if (!event.keyboardModifiers.isCtrlPressed && !event.keyboardModifiers.isMetaPressed) continue
            val change = event.changes.firstOrNull() ?: continue
            val dy = change.scrollDelta.y
            if (dy == 0f) continue
            zoom.step(if (dy < 0f) -1 else 1, change.position.y)
            event.changes.forEach { it.consume() }
        }
    }
}
