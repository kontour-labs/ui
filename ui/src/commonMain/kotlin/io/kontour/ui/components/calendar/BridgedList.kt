package io.kontour.ui.components.calendar

import androidx.compose.foundation.gestures.stopScroll
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow

/**
 * One of the calendar's scrolling lists — the month's weeks, the year's months
 * — with the bridge a far move puts across it, as the date picker's pager puts
 * one across its months (see `PeriodPager`).
 *
 * **A far move slides one screen, whatever the distance.** The month's today,
 * pressed more than half a year away, used to cut there — scrolled through
 * every week between, a jump of years would take as long as it was far, and
 * be a blur — and the year's did the same past two years: *"in the event
 * calendar's month view, if you click the "today" button and you're like more
 * than a year away or something, it just snaps back (rather than animating
 * like it does if you're only a few months down). can we make it animate in
 * the same way, just ensure it's no slower"*, asked for 0.5.2. Now the items
 * just past the screen, on the side the move is going, stand in for the ones
 * it is going to, the list slides there as it would to a month a screen
 * away, and once it has stopped the list is counted plainly again — the same
 * items in the same places, so nothing on screen moves.
 *
 * The list's indices are the bridged ones: what an item shows is [real] of its
 * index, and every reader of the list goes through that and [indexOf]. With
 * no bridge up the two are the same.
 */
@Stable
internal class BridgedList(val list: LazyListState) {
    private var span: Span? by mutableStateOf(null)

    /** Whether a bridge is up. */
    val up: Boolean get() = span != null

    /** The item the list's [index] shows: itself, or past the bridge's edge, the one it stands in for. */
    fun real(index: Int): Int {
        val span = span ?: return index
        return if (span.covers(index)) index + span.shift else index
    }

    /** The list's index that shows item [real]; null for one the bridge has left out. */
    fun indexOf(real: Int): Int? {
        val span = span ?: return real
        val across = real - span.shift
        return when {
            span.covers(across) -> across
            !span.covers(real) -> real
            else -> null
        }
    }

    /**
     * Puts a bridge up for a move to item [target], and says where to scroll
     * to: the first index past the screen, going on, with [target] standing
     * there; going back, [fill] indices before the first on screen — as many as
     * fill it from [target] down, or more, so nothing from this side of the
     * edge shows under it once it lands. Null where the two would meet, which
     * a move far enough to want a bridge never is, and null with one already
     * up: see [clear].
     */
    fun open(target: Int, fill: Int): Int? {
        val visible = list.layoutInfo.visibleItemsInfo
        if (visible.isEmpty() || span != null) return null
        val later = target > visible.first().index
        val edge = if (later) visible.last().index + 1 else visible.first().index
        val landing = if (later) edge else edge - fill.coerceAtLeast(visible.size)
        val shift = target - landing
        // Each side's items are their own: what is on screen, and everything
        // the bridge stands in for, never the same one twice.
        if (if (later) shift <= 0 else shift >= 0) return null
        span = Span(edge, later, shift)
        return landing
    }

    /**
     * Takes the bridge down, counting the list plainly again, with what is on
     * screen where it is: once the list has stopped with only one side of the
     * edge in view, or, [forced], whichever side has more of the screen — for a
     * new move, from a bridge a finger stopped across its edge. Whether it did:
     * never while the list is scrolling.
     */
    suspend fun close(forced: Boolean = false): Boolean {
        val span = span ?: return false
        // A scroll under way holds the list, and the scroll here would wait
        // for it: the bridge, gone in the meantime, would show its far side
        // as the near one's weeks for as long.
        if (list.isScrollInProgress) return false
        val info = list.layoutInfo
        if (info.visibleItemsInfo.isEmpty()) return false
        var across = 0
        var here = 0
        for (item in info.visibleItemsInfo) {
            val shown = (minOf(item.offset + item.size, info.viewportEndOffset) - maxOf(item.offset, info.viewportStartOffset)).coerceAtLeast(0)
            if (span.covers(item.index)) across += shown else here += shown
        }
        if (!forced && across > 0 && here > 0) return false
        val index = list.firstVisibleItemIndex + if (across > here) span.shift else 0
        val offset = list.firstVisibleItemScrollOffset
        // Written with nothing between it and the scroll, which lays the list
        // out before anything reads it: no reader sees the plain count over
        // the bridged list.
        this.span = null
        list.scrollToItem(index, offset)
        return true
    }

    /**
     * The bridge down before a new move, whatever is holding it up: a scroll
     * under way is stopped first, as the move would stop it anyway.
     */
    suspend fun clear() {
        if (span == null || close(forced = true)) return
        list.stopScroll()
        close(forced = true)
    }

    /** The bridge's edge, which side of it is bridged, and by how many items. */
    private class Span(val edge: Int, val later: Boolean, val shift: Int) {
        fun covers(index: Int): Boolean = if (later) index >= edge else index < edge
    }
}

/**
 * A [BridgedList] over [list], which takes its bridge down by itself once the
 * list comes to rest clear of the edge — after a move a finger stopped, say,
 * and then scrolled on past it.
 */
@Composable
internal fun rememberBridgedList(list: LazyListState): BridgedList {
    val bridged = remember(list) { BridgedList(list) }
    LaunchedEffect(bridged) {
        snapshotFlow { bridged.up && !list.isScrollInProgress && list.layoutInfo.visibleItemsInfo.isNotEmpty() }
            .collect { idle -> if (idle) bridged.close() }
    }
    return bridged
}
