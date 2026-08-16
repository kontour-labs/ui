package io.kontour.ui.nav

import androidx.compose.ui.unit.dp
import io.kontour.ui.adaptive.WindowHeightClass
import io.kontour.ui.adaptive.WindowSizeClass
import io.kontour.ui.adaptive.WindowWidthClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Where the app's destinations go, per window size.
 *
 * The breakpoints are the whole behaviour, and a layout that switches one class
 * too early looks fine on the two devices anyone checks and wrong on the third.
 */
class NavigationSuiteTypeTest {

    @Test
    fun aPhoneGetsABarAtTheBottom() {
        assertEquals(
            NavigationSuiteType.Bar,
            navigationSuiteTypeFor(WindowWidthClass.of(400.dp)),
        )
    }

    @Test
    fun aLandscapePhoneOrSmallTabletGetsARail() {
        assertEquals(
            NavigationSuiteType.Rail,
            navigationSuiteTypeFor(WindowWidthClass.of(700.dp)),
        )
    }

    @Test
    fun aTabletOrDesktopGetsADrawer() {
        assertEquals(
            NavigationSuiteType.Drawer,
            navigationSuiteTypeFor(WindowWidthClass.of(1000.dp)),
        )
        assertEquals(
            NavigationSuiteType.Drawer,
            navigationSuiteTypeFor(WindowWidthClass.of(1600.dp)),
        )
    }

    @Test
    fun theBreakpointsAreExact() {
        // 599 is a phone, 600 is not. Off by one here is a whole class of device
        // getting the wrong layout.
        assertEquals(WindowWidthClass.Compact, WindowWidthClass.of(599.dp))
        assertEquals(WindowWidthClass.Medium, WindowWidthClass.of(600.dp))
        assertEquals(WindowWidthClass.Medium, WindowWidthClass.of(839.dp))
        assertEquals(WindowWidthClass.Expanded, WindowWidthClass.of(840.dp))
        assertEquals(WindowWidthClass.Expanded, WindowWidthClass.of(1199.dp))
        assertEquals(WindowWidthClass.Large, WindowWidthClass.of(1200.dp))
    }

    @Test
    fun heightIsClassedSeparately() {
        // A phone in landscape has width to spare and almost no height. A
        // component that only consults width will happily stack a large top bar
        // and a bottom bar on a 360dp-tall window.
        val landscapePhone = WindowSizeClass.of(800.dp, 360.dp)
        assertEquals(WindowWidthClass.Medium, landscapePhone.width)
        assertEquals(WindowHeightClass.Compact, landscapePhone.height)
        assertTrue(landscapePhone.isLandscape)
    }

    @Test
    fun roomBesideAndRoomForPanesAreDifferentQuestions() {
        val smallTablet = WindowSizeClass.of(700.dp, 1000.dp)
        assertTrue(smallTablet.width.hasRoomBeside)
        // Room for a rail is not room for two panes of content.
        assertFalse(smallTablet.width.hasRoomForTwoPanes)

        assertTrue(WindowWidthClass.of(1000.dp).hasRoomForTwoPanes)
    }
}

/**
 * Which page numbers a pagination row shows.
 */
class PaginationSlotsTest {

    private fun pages(slots: List<PaginationSlot>) = slots.map {
        when (it) {
            is PaginationSlot.Page -> "${it.index + 1}"
            PaginationSlot.Gap -> "…"
        }
    }

    @Test
    fun aRangeThatAlreadyFitsIsShownWhole() {
        // "1 2 … 5" is exactly as wide as "1 2 3 4 5" and shows two fewer pages.
        assertEquals(
            listOf("1", "2", "3", "4", "5"),
            pages(paginationSlots(page = 0, pageCount = 5)),
        )
        // The widest the collapsed form ever gets, at window = 1.
        assertEquals(7, paginationSlots(page = 0, pageCount = 7).size)
        // One more than that, and collapsing finally saves something.
        assertTrue(paginationSlots(page = 0, pageCount = 8).size < 8)
    }

    @Test
    fun theStartOfALongRangeKeepsTheLastPageReachable() {
        // "page 1 of 40" is the first thing anyone sees, so this is the case
        // worth being certain about.
        assertEquals(
            listOf("1", "2", "…", "40"),
            pages(paginationSlots(page = 0, pageCount = 40)),
        )
    }

    @Test
    fun theMiddleOfALongRangeGapsBothSides() {
        assertEquals(
            listOf("1", "…", "19", "20", "21", "…", "40"),
            pages(paginationSlots(page = 19, pageCount = 40)),
        )
    }

    @Test
    fun theEndOfALongRangeKeepsTheFirstPageReachable() {
        assertEquals(
            listOf("1", "…", "39", "40"),
            pages(paginationSlots(page = 39, pageCount = 40)),
        )
    }

    @Test
    fun aGapStandingInForOnePageIsReplacedByThatPage() {
        // "1 … 3 4 5" wastes the same room as "1 2 3 4 5" while hiding a page.
        assertEquals(
            listOf("1", "2", "3", "4", "5", "…", "40"),
            pages(paginationSlots(page = 3, pageCount = 40)),
        )
    }

    @Test
    fun aWiderWindowShowsMoreNeighbours() {
        assertEquals(
            listOf("1", "…", "18", "19", "20", "21", "22", "…", "40"),
            pages(paginationSlots(page = 19, pageCount = 40, window = 2)),
        )
    }

    @Test
    fun degenerateRangesProduceNothingOrOne() {
        assertEquals(emptyList(), paginationSlots(page = 0, pageCount = 0))
        assertEquals(listOf("1"), pages(paginationSlots(page = 0, pageCount = 1)))
    }
}

/**
 * How far a large top bar has collapsed.
 */
class CollapseProgressTest {

    @Test
    fun itRunsFromZeroToOneOverTheDistance() {
        assertEquals(0f, collapseProgress(scrolled = 0f, distance = 56f))
        assertEquals(0.5f, collapseProgress(28f, 56f))
        assertEquals(1f, collapseProgress(56f, 56f))
    }

    @Test
    fun itIsClampedAtBothEnds() {
        // Overscroll must not push it past 1, or the small title fades back out
        // at the very moment the large one is gone.
        assertEquals(1f, collapseProgress(5000f, 56f))
        assertEquals(0f, collapseProgress(-100f, 56f))
    }

    @Test
    fun aZeroDistanceNeverCollapses() {
        // Rather than dividing by zero and producing NaN, which propagates into
        // an alpha and blanks the whole bar.
        assertEquals(0f, collapseProgress(100f, 0f))
    }
}
