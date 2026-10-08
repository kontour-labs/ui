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
 * **What it costs**: every row composed once, in idle time, up to
 * [MeasuredRowsCap] rows, and held until the last is measured; past that the
 * rest are estimated. A list of a few
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
const val MeasuredRowsCap: Int = 200

/** The lists made with [rememberScrollbarListState], and the heights of their rows. */
internal val MeasuredLists = mutableStateMapOf<LazyListState, RowSizes>()

/**
 * The list's own prefetching, and each row not yet known measured ahead: a
 * prefetch is a row composed and measured off screen, which reports its height
 * and is then let go of.
 */
@OptIn(ExperimentalFoundationApi::class)
private class MeasuringPrefetch(private val rows: RowSizes, frames: PrefetchScheduler) : LazyListPrefetchStrategy {
    private val default = LazyListPrefetchStrategy()

    override val prefetchScheduler: PrefetchScheduler = frames
    private val inFlight = HashMap<Int, LazyLayoutPrefetchState.PrefetchHandle>()

    /** The rows asked for, measured or not: each is asked for once. */
    private val measured = HashSet<Int>()

    override fun LazyListPrefetchScope.onScroll(delta: Float, layoutInfo: LazyListLayoutInfo) =
        with(default) { onScroll(delta, layoutInfo) }

    override fun NestedPrefetchScope.onNestedPrefetch(firstVisibleItemIndex: Int) =
        with(default) { onNestedPrefetch(firstVisibleItemIndex) }

    override fun LazyListPrefetchScope.onVisibleItemsUpdated(layoutInfo: LazyListLayoutInfo) {
        with(default) { onVisibleItemsUpdated(layoutInfo) }
        val total = layoutInfo.totalItemsCount
        if (total != rows.count) rows.resize(total)
        for (item in layoutInfo.visibleItemsInfo) rows.record(item.index, item.size, item.key)
        for (index in 0 until minOf(total, MeasuredRowsCap)) {
            if (rows.known(index) || index in inFlight || index in measured) continue
            val handle = schedulePrefetch(index) {
                rows.record(this.index, mainAxisSize, key = null, notify = true)
                // Kept composed until every row is measured, then all let go
                // together: a row let go of hands its composition back for
                // reuse, and the next row measured in it reported the height
                // of the row before — every card on a page measured as the
                // first one measured was.
                if (rows.knownCount >= minOf(rows.count, MeasuredRowsCap)) cancelAll()
            }
            // Urgent, or a row's composition waits for an average of earlier
            // ones to fit the time left, and a list that has measured nothing
            // has no average: our own slice of each frame is the limit.
            handle.markAsUrgent()
            inFlight[index] = handle
            measured += index
        }
    }

    fun cancelAll() {
        for (handle in inFlight.values) handle.cancel()
        inFlight.clear()
    }
}

/**
 * Prefetch work run a slice a frame, after the frame: [Budget] of each, for as
 * long as there is work, and nothing at all while there is none.
 */
@OptIn(ExperimentalFoundationApi::class)
private class FramePrefetchScheduler : PrefetchScheduler {
    private val queue = ArrayDeque<PrefetchRequest>()
    private val waiting = Channel<Unit>(Channel.CONFLATED)

    override fun schedulePrefetch(prefetchRequest: PrefetchRequest) {
        queue.addLast(prefetchRequest)
        waiting.trySend(Unit)
    }

    suspend fun run() {
        while (true) {
            if (queue.isEmpty()) waiting.receive()
            withFrameNanos { }
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
        }
    }

    private companion object {
        /** A slice of a 120Hz frame: rows measured ahead never cost a frame. */
        val Budget = 4.milliseconds
    }
}
