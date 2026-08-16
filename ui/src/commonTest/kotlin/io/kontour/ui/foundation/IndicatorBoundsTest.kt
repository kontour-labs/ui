package io.kontour.ui.foundation

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The indicator's geometry, without rendering anything.
 *
 * All four sizings are pure functions of the item's rect, so they are tested here
 * rather than through a screenshot — and the one case that *does* depend on
 * layout direction is tested in both directions, because compounding a second
 * mirror onto an already-mirrored coordinate is the whole of the bug this
 * mechanism replaces.
 */
class IndicatorBoundsTest {

    // 2x, so a dp is two pixels and the arithmetic is visible in the assertions.
    private val density = Density(2f)

    private fun resolve(
        sizing: IndicatorSizing,
        item: Rect,
        direction: LayoutDirection = LayoutDirection.Ltr,
    ) = resolveIndicatorBounds(sizing, item, density, direction)

    /** An 80x64 item at (100, 200). */
    private val item = Rect(left = 100f, top = 200f, right = 180f, bottom = 264f)

    @Test
    fun fillIsTheItemItself() {
        assertEquals(item, resolve(IndicatorSizing.Fill, item))
    }

    @Test
    fun aFixedIndicatorIsCentredOnTheItem() {
        // 56x32dp at 2x is 112x64px, centred on the item's centre (140, 232).
        val bounds = resolve(IndicatorSizing.Fixed(56.dp, 32.dp), item)

        assertEquals(item.center, bounds.center)
        assertEquals(112f, bounds.width)
        assertEquals(64f, bounds.height)
    }

    @Test
    fun aFixedIndicatorLargerThanItsItemStillCentres() {
        // The nav bar's pill is wider than a narrow item's icon, which is the
        // point — it must overhang symmetrically rather than clamp to the item.
        val bounds = resolve(IndicatorSizing.Fixed(200.dp, 8.dp), item)

        assertEquals(item.center, bounds.center)
        assertTrue(bounds.left < item.left && bounds.right > item.right)
    }

    @Test
    fun aBottomEdgeBarSpansTheItemAndSitsFlushWithItsBottom() {
        val bounds = resolve(IndicatorSizing.Edge(IndicatorEdge.Bottom, 3.dp), item)

        assertEquals(item.left, bounds.left)
        assertEquals(item.right, bounds.right)
        assertEquals(item.bottom, bounds.bottom)
        assertEquals(6f, bounds.height) // 3dp at 2x
    }

    @Test
    fun aTopEdgeBarSitsFlushWithTheItemsTop() {
        val bounds = resolve(IndicatorSizing.Edge(IndicatorEdge.Top, 3.dp), item)

        assertEquals(item.top, bounds.top)
        assertEquals(6f, bounds.height)
    }

    @Test
    fun anEdgeBarInsetsAlongItsLongAxisOnly() {
        val bounds = resolve(IndicatorSizing.Edge(IndicatorEdge.Bottom, 3.dp, inset = 8.dp), item)

        assertEquals(item.left + 16f, bounds.left)
        assertEquals(item.right - 16f, bounds.right)
        // Thickness is unaffected by the inset.
        assertEquals(6f, bounds.height)
    }

    @Test
    fun startResolvesToTheLeftEdgeInLtrAndTheRightEdgeInRtl() {
        // The one place layout direction is consulted. Everything reaching this
        // function has already been mirrored by the row that laid the items out,
        // so mirroring anything else here would double-apply it.
        val ltr = resolve(IndicatorSizing.Edge(IndicatorEdge.Start, 3.dp), item, LayoutDirection.Ltr)
        assertEquals(item.left, ltr.left)
        assertEquals(item.left + 6f, ltr.right)

        val rtl = resolve(IndicatorSizing.Edge(IndicatorEdge.Start, 3.dp), item, LayoutDirection.Rtl)
        assertEquals(item.right - 6f, rtl.left)
        assertEquals(item.right, rtl.right)
    }

    @Test
    fun endResolvesToTheOppositeSideOfStart() {
        val ltr = resolve(IndicatorSizing.Edge(IndicatorEdge.End, 3.dp), item, LayoutDirection.Ltr)
        assertEquals(item.right, ltr.right)

        val rtl = resolve(IndicatorSizing.Edge(IndicatorEdge.End, 3.dp), item, LayoutDirection.Rtl)
        assertEquals(item.left, rtl.left)
    }

    @Test
    fun topAndBottomAreUnaffectedByLayoutDirection() {
        // They are physical edges, not leading/trailing ones. A tab underline
        // stays under the tab in Arabic.
        val ltr = resolve(IndicatorSizing.Edge(IndicatorEdge.Bottom, 3.dp), item, LayoutDirection.Ltr)
        val rtl = resolve(IndicatorSizing.Edge(IndicatorEdge.Bottom, 3.dp), item, LayoutDirection.Rtl)

        assertEquals(ltr, rtl)
    }

    @Test
    fun anInsetLargerThanTheItemDoesNotInvertIt() {
        // A 4dp inset is 8px a side, which is more than a 6px-tall item has to
        // give. Inverting would produce a negative rect that draws as a flicker.
        val tiny = Rect(left = 0f, top = 0f, right = 10f, bottom = 6f)
        val bounds = resolve(IndicatorSizing.Inset(4.dp), tiny)

        assertTrue(bounds.width >= 0f, "width went negative: ${bounds.width}")
        assertTrue(bounds.height >= 0f, "height went negative: ${bounds.height}")
        assertEquals(tiny.center, bounds.center)
    }

    @Test
    fun anInsetShrinksSymmetrically() {
        val bounds = resolve(IndicatorSizing.Inset(4.dp), item)

        assertEquals(item.center, bounds.center)
        assertEquals(item.width - 16f, bounds.width)
        assertEquals(item.height - 16f, bounds.height)
    }
}
