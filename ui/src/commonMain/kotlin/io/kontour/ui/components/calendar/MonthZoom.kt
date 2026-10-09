package io.kontour.ui.components.calendar

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.lazy.LazyListState
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
import kotlinx.coroutines.launch

/**
 * Where the list's first row goes, as a row and how far into it, for the row
 * at [focusY] to stay under it as each row goes from [oldRow] pixels tall to
 * [newRow]: [top] is where the first row is now, in rows — its index and how
 * far down it the list's top is.
 */
internal fun zoomAnchor(top: Float, focusY: Float, oldRow: Float, newRow: Float): Float {
    if (oldRow <= 0f || newRow <= 0f) return top
    val focus = top + focusY / oldRow
    return focus - focusY / newRow
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

/** The most lines a month's event is given. */
private const val MaxItemLines = 3

/** How many events a day keeps room for before its events are given more lines each. */
private const val MinItemsPerDay = 3

/** The fewest weeks a month shows at once, zoomed all the way in. */
internal const val MinMonthRows = 3

/**
 * A month's zoom: how many weeks it shows, and the list kept still under the
 * fingers, or the pointer, as that changes. [rows] is read and written through
 * the state; [viewportPx] and the limits are as the month last laid out.
 */
@Stable
internal class MonthZoom(private val state: EventCalendarState, private val list: LazyListState, private val scope: CoroutineScope) {
    var viewportPx = 0f
    var fewest = MinMonthRows.toFloat()
    var most = MonthRowsInView.toFloat()

    /** Where the first row is, in rows, as this zoom has put it: the list's own lags a frame behind. */
    private var top: Float? = null

    private fun rowPx(rows: Float) = if (rows <= 0f) 0f else floor(viewportPx / rows)

    private fun currentTop(): Float {
        top?.let { return it }
        val first = list.layoutInfo.visibleItemsInfo.firstOrNull() ?: return list.firstVisibleItemIndex.toFloat()
        return first.index - first.offset.toFloat() / first.size.coerceAtLeast(1)
    }

    /** Zoomed to [rows] weeks, the week at [focusY] still under it. */
    fun zoomTo(rows: Float, focusY: Float) {
        val next = rows.coerceIn(fewest, maxOf(fewest, most))
        val now = state.monthRows.coerceIn(fewest, maxOf(fewest, most))
        if (next == now || viewportPx <= 0f) return
        val newRow = rowPx(next)
        val moved = zoomAnchor(currentTop(), focusY, rowPx(now), newRow).coerceAtLeast(0f)
        top = moved
        state.monthRows = next
        val index = floor(moved).toInt()
        list.requestScrollToItem(index, ((moved - index) * newRow).roundToInt())
    }

    /** Pinched by [factor] about [focusY]: spread, fewer weeks; pinched in, more. */
    fun pinch(factor: Float, focusY: Float) {
        if (factor <= 0f) return
        zoomTo(state.monthRows / factor, focusY)
    }

    /** One whole week more, or fewer, about [focusY]: a wheel's turn, a key. */
    fun step(weeks: Int, focusY: Float) {
        zoomTo(state.monthRows.roundToInt().toFloat() + weeks, focusY)
        settle()
    }

    /** Let go: to the nearest whole number of weeks, settled with a week at the top. */
    fun settle(focusY: Float = 0f) {
        val whole = state.monthRows.roundToInt().toFloat().coerceIn(fewest, maxOf(fewest, most))
        if (whole != state.monthRows) zoomTo(whole, focusY)
        val at = top ?: return
        top = null
        scope.launch { list.animateScrollToItem(at.roundToInt().coerceAtLeast(0)) }
    }
}

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
                    }
                }
                if (zooming) {
                    if (last > 0f && span > 0f) zoom.pinch(span / last, focusY)
                    last = span
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
