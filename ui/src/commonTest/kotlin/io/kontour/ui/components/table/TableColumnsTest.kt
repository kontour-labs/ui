package io.kontour.ui.components.table

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** How a table's columns are sized into its width, which are pinned, and how far the rest scroll. */
class TableColumnsTest {

    private fun fixed(width: Int) = ColumnSizing(width, 0f, width, width)
    private fun weighted(weight: Float, floor: Int = 50, max: Int = Int.MAX_VALUE) = ColumnSizing(floor, weight, floor, max)

    @Test
    fun weightsShareTheRoomLeftSoTheTableFillsItsWidth() {
        val layout = resolveTableColumns(listOf(fixed(72), weighted(1f), weighted(2f)), viewport = 372, pinnedRequested = 0, pinCap = 0.5f)
        assertEquals(372, layout.widths.sum(), "the columns fill the table exactly")
        assertEquals(72, layout.widths[0])
        assertEquals(50 + 66, layout.widths[1], "a third of the 200 left, on its 50 floor")
        assertEquals(50 + 134, layout.widths[2], "and the rest, two thirds")
        assertContentEquals(intArrayOf(0, 72, 188), layout.starts)
        assertEquals(0, layout.maxScroll)
    }

    /** A weighted column that reaches its maximum hands the rest of its share to the others. */
    @Test
    fun aWeightAtItsMaximumGivesTheRestAway() {
        val layout = resolveTableColumns(
            listOf(weighted(1f, max = 80), weighted(1f)),
            viewport = 400,
            pinnedRequested = 0,
            pinCap = 0.5f,
        )
        assertEquals(80, layout.widths[0])
        assertEquals(320, layout.widths[1])
    }

    /** Wider than the table, nothing shrinks below its floor and the pane scrolls instead. */
    @Test
    fun columnsWiderThanTheTableScroll() {
        val layout = resolveTableColumns(
            listOf(fixed(100), weighted(1f, floor = 160), fixed(120), fixed(120)),
            viewport = 300,
            pinnedRequested = 1,
            pinCap = 0.5f,
        )
        assertContentEquals(intArrayOf(100, 160, 120, 120), layout.widths)
        assertEquals(1, layout.pinned)
        assertEquals(100, layout.pinnedWidth)
        assertEquals(200, layout.paneWidth)
        assertEquals(500 - 300, layout.maxScroll, "the content is 500 wide in a 300 viewport")
    }

    /** Pinned columns never cover more than their share of the table: the last of them scroll too. */
    @Test
    fun pinsThatWouldCoverTooMuchAreDropped() {
        val columns = listOf(fixed(100), fixed(100), fixed(100), fixed(100))
        assertEquals(2, resolveTableColumns(columns, viewport = 400, pinnedRequested = 3, pinCap = 0.5f).pinned)
        assertEquals(0, resolveTableColumns(columns, viewport = 150, pinnedRequested = 2, pinCap = 0.5f).pinned)
    }

    @Test
    fun nothingToLayOutIsNothing() {
        val empty = resolveTableColumns(emptyList(), viewport = 300, pinnedRequested = 2, pinCap = 0.5f)
        assertEquals(0, empty.count)
        assertEquals(0, empty.maxScroll)
        val squeezed = resolveTableColumns(listOf(fixed(80)), viewport = 0, pinnedRequested = 1, pinCap = 0.5f)
        assertEquals(0, squeezed.pinned)
        assertEquals(0, squeezed.paneWidth)
    }

    @Test
    fun aSortFlipsOnItsOwnColumnAndStartsAscendingOnAnother() {
        val byRoute = TableSort("Route")
        assertEquals(SortDirection.Ascending, byRoute.direction)
        assertEquals(TableSort("Route", SortDirection.Descending), byRoute.toggled("Route"))
        assertEquals(byRoute, byRoute.toggled("Route").toggled("Route"))
        assertEquals(TableSort("Fare"), byRoute.toggled("Route").toggled("Fare"))
    }

    @Test
    fun theSidewaysScrollStaysInsideItsBounds() {
        val scroll = TableHorizontalScroll(initial = 0)
        scroll.updateBounds(maxValue = 200, viewport = 300)
        scroll.dispatchRawDelta(150f)
        assertEquals(150, scroll.value)
        scroll.dispatchRawDelta(500f)
        assertEquals(200, scroll.value, "clamped at the end")
        assertFalse(scroll.canScrollForward)
        assertTrue(scroll.canScrollBackward)
        scroll.dispatchRawDelta(-1000f)
        assertEquals(0, scroll.value, "and at the start")
        scroll.dispatchRawDelta(180f)
        scroll.updateBounds(maxValue = 100, viewport = 400)
        assertEquals(100, scroll.value, "a table that grows wider pulls a scroll past its new end back")
    }
}
