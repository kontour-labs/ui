@file:Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")

package io.kontour.ui.components.list

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListPrefetchScope
import androidx.compose.foundation.lazy.LazyListPrefetchStrategy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.layout.LazyLayoutPrefetchState
import androidx.compose.foundation.lazy.layout.NestedPrefetchScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource
import kotlinx.coroutines.channels.Channel
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.lazy.layout.PrefetchScheduler
import androidx.compose.foundation.lazy.layout.PrefetchRequestScope
import androidx.compose.foundation.lazy.layout.PrefetchRequest

/**
 * A [LazyListState] whose list measures every row ahead of showing it, so a
 * [scrollbar] on it is exactly as long, and exactly as far along, as the list.
 *
 * ```kotlin
 * val list = rememberScrollbarListState()
 * LazyColumn(Modifier.scrollbar(list), state = list) { … }
 * ```
 *
 * A lazy list knows the height of the rows it has laid out and nothing else,
 * so a scrollbar on a plain [rememberLazyListState] list estimates the rest from
 * the rows it has seen: on rows of very different heights its thumb starts the
 * wrong length and is put right as the rows come into view. Made with this,
 * the list measures each row it has not shown — composed and measured with the
 * list's own constraints in the time between frames, as the list prefetches
 * the next row when it scrolls, and thrown away again — and the thumb is right
 * from the first few frames.
 *
 * **What it costs**: every row composed once, up to 200 rows, and held until
 * the last is measured; past that the rest are estimated. The measuring waits
 * until the list has been still for a moment and takes a row a frame, so a
 * page of heavy rows is not composed while it is arriving or being scrolled,
 * and the thumb is exact a moment after the list comes to rest. A list of a few
 * dozen rows of very different heights — a page of cards — is what it is for.
 * A list of thousands of even rows is estimated exactly enough without it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun rememberScrollbarListState(
    initialFirstVisibleItemIndex: Int = 0,
    initialFirstVisibleItemScrollOffset: Int = 0,
): LazyListState {
    val rows = remember { RowSizes() }
    val frames = remember { FramePrefetchScheduler() }
    val strategy = remember(rows, frames) { MeasuringPrefetch(rows, frames) }
    val state = rememberLazyListState(initialFirstVisibleItemIndex, initialFirstVisibleItemScrollOffset, strategy)
    frames.moving = { state.isScrollInProgress }
    // Its own time between frames: the platform's prefetching is a no-op on a
    // desktop window, and measuring ahead has to happen everywhere.
    LaunchedEffect(frames) { frames.run() }
    DisposableEffect(state, rows) {
        MeasuredLists[state] = rows
        onDispose {
            MeasuredLists.remove(state)
            strategy.cancelAll()
        }
    }
    return state
}

/**
 * The most rows a [rememberScrollbarListState] list measures ahead; past these,
 * the rest are estimated. The rows measured are kept composed until the last
 * of them is, so this is also the most it holds at once.
 */
internal const val MeasuredRowsCap: Int = 200

/** The lists made with [rememberScrollbarListState], and the heights of their rows. */
internal val MeasuredLists = mutableStateMapOf<LazyListState, RowSizes>()

/**
 * The list's own prefetching, and each row not yet known measured ahead: a
 * prefetch is a row composed and measured off screen, which reports its height
 * and is then let go of.
 */
@OptIn(ExperimentalFoundationApi::class)
private class MeasuringPrefetch(private val rows: RowSizes, private val frames: FramePrefetchScheduler) : LazyListPrefetchStrategy {
    private val default = LazyListPrefetchStrategy()

    override val prefetchScheduler: PrefetchScheduler = frames
    private val inFlight = HashMap<Int, LazyLayoutPrefetchState.PrefetchHandle>()

    /** The rows asked for, measured or not: each is asked for once. */
    private val measured = HashSet<Int>()

    /** The rows asked for that have reported, whatever their height — none tall included. */
    private val landed = HashSet<Int>()

    override fun LazyListPrefetchScope.onScroll(delta: Float, layoutInfo: LazyListLayoutInfo) =
        with(default) { onScroll(delta, layoutInfo) }

    override fun NestedPrefetchScope.onNestedPrefetch(firstVisibleItemIndex: Int) =
        with(default) { onNestedPrefetch(firstVisibleItemIndex) }

    override fun LazyListPrefetchScope.onVisibleItemsUpdated(layoutInfo: LazyListLayoutInfo) {
        with(default) { onVisibleItemsUpdated(layoutInfo) }
        val total = layoutInfo.totalItemsCount
        // Rows replaced — a row on screen is not the row measured at its
        // place — and everything measured is of other rows: forgotten, and
        // asked for again. More rows, or fewer, and those not yet asked for are.
        val replaced = layoutInfo.visibleItemsInfo.any { item -> rows.keyAt(item.index).let { it != null && it != item.key } }
        if (replaced) {
            cancelAll()
            measured.clear()
            rows.resize(0)
        }
        if (total != rows.count) {
            rows.resize(total)
            measured.retainAll { it < total }
        }
        for (item in layoutInfo.visibleItemsInfo) rows.record(item.index, item.size, item.key)
        for (index in 0 until minOf(total, MeasuredRowsCap)) {
            if (rows.known(index) || index in inFlight || index in measured) continue
            frames.ahead = true
            val handle = schedulePrefetch(index) {
                rows.record(this.index, mainAxisSize, key = null, notify = true)
                landed += this.index
                // Kept composed until every row asked for has reported, then
                // all let go together: a row let go of hands its composition
                // back for reuse, and the next row measured in it reported the
                // height of the row before — every card on a page measured as
                // the first one measured was. Counted by the rows that
                // reported, not by the heights known: a row with no height is
                // never known, and waiting for it held every row composed for
                // the life of the list.
                if (inFlight.keys.all { it in landed }) cancelAll()
            }
            // Urgent, or a row's composition waits for an average of earlier
            // ones to fit the time left, and a list that has measured nothing
            // has no average. Filed apart by the scheduler, which runs these
            // only while the list is still: see `FramePrefetchScheduler`.
            frames.ahead = false
            handle.markAsUrgent()
            inFlight[index] = handle
            measured += index
        }
    }

    fun cancelAll() {
        for (handle in inFlight.values) handle.cancel()
        inFlight.clear()
        landed.clear()
    }
}

/**
 * Prefetch work run a slice a frame: [Budget] of each, for as long as there is
 * work, and nothing at all while there is none.
 *
 * **Two kinds of work, and only one of them is in a hurry.** The list's own
 * prefetch — the next row in the way it is scrolling — runs as it always did,
 * in each frame's slice. A row measured *ahead*, for the scrollbar, waits until
 * the list has been still for [QuietNanos] of frames, stops the moment it
 * scrolls again, and is one row a frame.
 *
 * It used to share the slice, and a row measured ahead is urgent — composed
 * whole, in the frame it starts in, because a list that has measured nothing
 * has no average to schedule it by. On a page of heavy rows that was every
 * card composed in the first frames after the page opened: on the gallery's
 * date and time page, all twelve demos, the event calendar among them, and
 * frames at four times the cost of the same page on a plain state, at the
 * moment the page was arriving and the reader starting to scroll. A frame
 * nobody sees move costs nothing, so that is where they go now.
 */
@OptIn(ExperimentalFoundationApi::class)
private class FramePrefetchScheduler : PrefetchScheduler {
    private val queue = ArrayDeque<PrefetchRequest>()
    private val aheadQueue = ArrayDeque<PrefetchRequest>()
    private val waiting = Channel<Unit>(Channel.CONFLATED)

    /** Set around a measure-ahead request, which [schedulePrefetch] files apart. */
    var ahead = false

    /** Whether the list is being scrolled, by a finger or a fling. */
    var moving: () -> Boolean = { false }

    /** The frame from which the list has been still, or [Long.MIN_VALUE] while it is not. */
    private var stillSince = Long.MIN_VALUE

    override fun schedulePrefetch(prefetchRequest: PrefetchRequest) {
        (if (ahead) aheadQueue else queue).addLast(prefetchRequest)
        waiting.trySend(Unit)
    }

    suspend fun run() {
        while (true) {
            if (queue.isEmpty() && aheadQueue.isEmpty()) waiting.receive()
            val now = withFrameNanos { it }
            val started = TimeSource.Monotonic.markNow()
            val scope = object : PrefetchRequestScope {
                override fun availableTimeNanos(): Long =
                    (Budget - started.elapsedNow()).inWholeNanoseconds.coerceAtLeast(0L)
            }
            while (queue.isNotEmpty() && scope.availableTimeNanos() > 0L) {
                val request = queue.first()
                // True while it has more to do: it stays at the front.
                val more = with(request) { scope.execute() }
                if (!more) queue.removeFirst()
            }
            if (moving()) {
                stillSince = Long.MIN_VALUE
                continue
            }
            if (stillSince == Long.MIN_VALUE) stillSince = now
            if (aheadQueue.isNotEmpty() && queue.isEmpty() && now - stillSince >= QuietNanos) {
                val request = aheadQueue.first()
                val more = with(request) { scope.execute() }
                if (!more) aheadQueue.removeFirst()
            }
        }
    }

    private companion object {
        /** A slice of a 120Hz frame: rows prefetched for a scroll never cost a frame. */
        val Budget = 4.milliseconds

        /**
         * How long the list is still before a row is measured ahead: longer than
         * a page's arrival, so the measuring starts once the page is still.
         */
        const val QuietNanos = 300_000_000L
    }
}
