package io.kontour.ui.components.table

import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import io.kontour.ui.components.list.ScrollExtent
import kotlin.math.roundToInt

/*
 * Where a table's columns go: how wide each is, which are pinned, and how far
 * the rest can scroll. Pure, so the arithmetic is tested without laying anything
 * out, and the table only has to be right about where it puts cells.
 */

/**
 * How one column wants to be sized, in px.
 *
 * @param base Its width, for a fixed or a fitted column. For a weighted one, its
 *   floor — which is also [min].
 * @param weight Its share of any room left over. Nought for none.
 * @param min The narrowest it may be.
 * @param max The widest; [Int.MAX_VALUE] for no limit.
 */
internal class ColumnSizing(val base: Int, val weight: Float, val min: Int, val max: Int)

/**
 * Every column's width and start, in the table's content, and how that content
 * sits in a viewport [viewport] px wide: the first [pinned] columns fixed at the
 * start, the rest in a pane that scrolls up to [maxScroll].
 */
@Immutable
internal class TableColumnsLayout(
    val widths: IntArray,
    val starts: IntArray,
    val pinned: Int,
    val pinnedWidth: Int,
    val paneWidth: Int,
    val total: Int,
    val maxScroll: Int,
    val viewport: Int,
) {
    val count: Int get() = widths.size

    /** Where column [index] ends, in the content. */
    fun end(index: Int): Int = starts[index] + widths[index]

    override fun equals(other: Any?): Boolean =
        other is TableColumnsLayout && other.widths.contentEquals(widths) && other.pinned == pinned &&
            other.viewport == viewport

    override fun hashCode(): Int = 31 * (31 * widths.contentHashCode() + pinned) + viewport
}

/**
 * Sizes [columns] into [viewport] px, pinning the first [pinnedRequested] of them
 * while they fit in [pinCap] of the viewport.
 *
 * Fixed and fitted columns take their widths, clamped to their limits; weighted
 * ones start at their floors. Any room left over goes to the weighted columns in
 * proportion to their weights, a column that reaches its maximum handing the rest
 * of its share back to the others — so the table fills the viewport exactly when
 * it can. When the columns want more than the viewport has, nothing shrinks
 * below its floor and the pane scrolls instead.
 *
 * Pinned columns that would cover more than [pinCap] of the viewport are unpinned
 * from the last one back, so on a narrow screen there is always room left to
 * scroll the rest past them.
 */
internal fun resolveTableColumns(
    columns: List<ColumnSizing>,
    viewport: Int,
    pinnedRequested: Int,
    pinCap: Float,
): TableColumnsLayout {
    val count = columns.size
    val widths = IntArray(count) { index ->
        val column = columns[index]
        if (column.weight > 0f) column.min else column.base.coerceIn(column.min, maxOf(column.min, column.max))
    }
    var free = viewport - widths.sum()
    if (free > 0) {
        val pool = columns.indices.filter { columns[it].weight > 0f }.toMutableList()
        while (pool.isNotEmpty() && free > 0) {
            val totalWeight = pool.sumOf { columns[it].weight.toDouble() }.toFloat()
            val capped = pool.filter { widths[it] + free * columns[it].weight / totalWeight > columns[it].max }
            if (capped.isEmpty()) {
                var given = 0
                pool.forEachIndexed { turn, index ->
                    val share = if (turn == pool.lastIndex) {
                        free - given
                    } else {
                        (free * columns[index].weight / totalWeight).toInt()
                    }
                    widths[index] += share
                    given += share
                }
                free = 0
            } else {
                capped.forEach { index ->
                    free -= columns[index].max - widths[index]
                    widths[index] = columns[index].max
                    pool.remove(index)
                }
            }
        }
    }

    var pinned = pinnedRequested.coerceIn(0, count)
    while (pinned > 0 && (0 until pinned).sumOf { widths[it] } > viewport * pinCap) pinned--

    val starts = IntArray(count)
    for (index in 1 until count) starts[index] = starts[index - 1] + widths[index - 1]
    val total = widths.sum()
    val pinnedWidth = (0 until pinned).sumOf { widths[it] }
    val paneWidth = (viewport - pinnedWidth).coerceAtLeast(0)
    return TableColumnsLayout(
        widths = widths,
        starts = starts,
        pinned = pinned,
        pinnedWidth = pinnedWidth,
        paneWidth = paneWidth,
        total = total,
        maxScroll = (total - pinnedWidth - paneWidth).coerceAtLeast(0),
        viewport = viewport,
    )
}

/**
 * How far a table's pane is scrolled sideways.
 *
 * One state for the whole table — header, body, footer — so a sideways drag on
 * any row moves every row, and a fling on one is not interrupted by another. The
 * arithmetic is `ScrollState`'s, whose own bounds cannot be set from outside
 * foundation; the table sets these from its measure pass, as `ScrollState`'s own
 * layout does.
 */
@Stable
internal class TableHorizontalScroll(initial: Int) : ScrollableState, ScrollExtent {

    var value by mutableIntStateOf(initial.coerceAtLeast(0))
        private set

    var maxValue by mutableIntStateOf(0)
        private set

    var viewport by mutableIntStateOf(0)
        private set

    private var accumulator = 0f

    private val inner = ScrollableState { delta ->
        val absolute = value + delta + accumulator
        val clamped = absolute.coerceIn(0f, maxValue.toFloat())
        val changed = absolute != clamped
        val consumed = clamped - value
        val rounded = consumed.roundToInt()
        value += rounded
        accumulator = consumed - rounded
        if (changed) consumed else delta
    }

    /** From the table's measure pass: how far it can scroll, and how wide the pane is. */
    fun updateBounds(maxValue: Int, viewport: Int) {
        this.maxValue = maxValue
        this.viewport = viewport
        Snapshot.withoutReadObservation {
            if (value > maxValue) value = maxValue
        }
    }

    override suspend fun scroll(scrollPriority: MutatePriority, block: suspend ScrollScope.() -> Unit) =
        inner.scroll(scrollPriority, block)

    override fun dispatchRawDelta(delta: Float): Float = inner.dispatchRawDelta(delta)

    override val isScrollInProgress: Boolean get() = inner.isScrollInProgress

    override val canScrollForward: Boolean get() = value < maxValue

    override val canScrollBackward: Boolean get() = value > 0

    override val extentValue: Int get() = value
    override val extentMax: Int get() = maxValue
    override val extentViewport: Int get() = viewport
}
