package io.kontour.ui.sheet

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Turning detents into anchor offsets.
 *
 * A 1000px-tall container at 1x density throughout, so a `dp` is a pixel and a
 * failure reports a readable difference. Offsets are measured from the *top* of
 * the container, so a taller sheet has a smaller offset.
 */
class SheetAnchorTest {

    private val density = Density(1f)

    private fun anchors(
        detents: List<SheetDetent>,
        container: Float = 1000f,
        sheet: Float = 600f,
        peek: Float = 0f,
    ) = resolveAnchors(detents, container, sheet, peek, density)

    @Test
    fun hiddenSitsAtTheBottomAndExpandedAtTheContentHeight() {
        val a = anchors(listOf(SheetDetent.Hidden, SheetDetent.Expanded))

        assertEquals(1000f, a[SheetDetent.Hidden])
        // Expanded is the *content's* height, not the container's: a sheet with
        // three rows in it should be three rows tall, not an empty full-screen
        // panel with three rows at the top.
        assertEquals(400f, a[SheetDetent.Expanded])
    }

    /**
     * And the cap is a gap short of the container, not the container itself.
     *
     * This asserted `0f` — the sheet's top edge on the container's first pixel —
     * until a phone showed what that means. A sheet whose top edge is at pixel
     * zero has no page above it for its rounded top corners to read against, so
     * the corners stop being corners; and it puts the drag handle and the header
     * under the status bar and the notch, which is what was actually reported.
     *
     * So every detent, `Full` and a caller's own `fraction(1f)` included, stops
     * [SheetTopGap] short. See the constant for why that number.
     */
    @Test
    fun expandedIsCappedAGapShortOfTheContainer() {
        val a = anchors(listOf(SheetDetent.Expanded), sheet = 3000f)
        assertEquals(12f, a[SheetDetent.Expanded])
    }

    @Test
    fun fractionsAreOfTheContainer() {
        val a = anchors(listOf(SheetDetent.Half))
        assertEquals(500f, a[SheetDetent.Half])
    }

    @Test
    fun fixedHeightsResolveInPixels() {
        val detent = SheetDetent.height("compact", 360.dp)
        assertEquals(640f, anchors(listOf(detent))[detent])
    }

    // --- The peek detent, which is the one the map screens need --------------

    @Test
    fun peekFallsBackUntilItsAnchorIsMeasured() {
        val peek = SheetDetent.peek(fallback = 140.dp)
        // Nothing measured yet: the sheet still needs somewhere to sit, or it
        // would appear at zero height on its first frame.
        assertEquals(860f, anchors(listOf(peek))[peek])
    }

    @Test
    fun aMeasuredPeekReplacesTheFallback() {
        val peek = SheetDetent.peek(fallback = 140.dp)
        // A header measuring 210px — a taller title, or 200% type. A fixed peek
        // would cut it in half.
        assertEquals(790f, anchors(listOf(peek), peek = 210f)[peek])
    }

    @Test
    fun aPeekTallerThanTheContainerIsClamped() {
        // To the same gap the cap above stops at, and for the same reasons.
        val peek = SheetDetent.peek(fallback = 140.dp)
        assertEquals(12f, anchors(listOf(peek), container = 200f, peek = 900f)[peek])
    }

    /**
     * A container with no room for the gap does not get one.
     *
     * The degenerate case, and the one a clamp gets wrong by default. Clamping
     * at `min(gap, container)` looks like the safe reading and is the worst
     * answer available: on a 6px container the gap becomes the whole container,
     * every detent resolves to "entirely hidden", and the sheet has nowhere left
     * to be. A container that small is a measurement in progress rather than a
     * window, and leaving it the offsets it already had is what does least harm.
     */
    @Test
    fun aContainerWithNoRoomForTheGapDoesNotGetOne() {
        val a = anchors(listOf(SheetDetent.Expanded), container = 6f, sheet = 600f)
        assertEquals(
            0f, a[SheetDetent.Expanded],
            "a 6px container gave its sheet a 12px gap, which is the whole " +
                "container and then some — so the sheet resolved to hidden at " +
                "every detent it has",
        )
    }

    // --- Duplicates ---------------------------------------------------------

    @Test
    fun detentsResolvingToTheSamePositionAreDropped() {
        // Content that happens to be exactly half the container: Expanded and
        // Half both land at the same offset. Two anchors at one offset make
        // `settledValue` ambiguous and the sheet flickers between two names for
        // one position.
        val a = anchors(
            listOf(SheetDetent.Hidden, SheetDetent.Half, SheetDetent.Expanded),
            container = 400f,
            sheet = 200f,
        )

        assertEquals(2, a.size)
        assertTrue(SheetDetent.Hidden in a)
        // The earlier of the two survives, so the sheet keeps the detent the
        // caller listed first rather than silently renaming its position.
        assertTrue(SheetDetent.Half in a)
        assertTrue(SheetDetent.Expanded !in a)
    }

    @Test
    fun distinctDetentsAllSurvive() {
        val peek = SheetDetent.peek(fallback = 100.dp)
        val a = anchors(
            listOf(SheetDetent.Hidden, peek, SheetDetent.Half, SheetDetent.Expanded),
            sheet = 800f,
        )
        assertEquals(4, a.size)
        // Bottom to top: hidden, peek, half, expanded.
        assertEquals(listOf(1000f, 900f, 500f, 200f), a.values.toList())
    }

    // --- Degenerate containers ----------------------------------------------

    @Test
    fun anUnmeasuredContainerProducesNoAnchors() {
        // Rather than a pile of anchors at zero, which would settle the sheet
        // open before it has been laid out.
        assertTrue(anchors(listOf(SheetDetent.Hidden, SheetDetent.Expanded), container = 0f).isEmpty())
    }

    @Test
    fun detentOrderIsPreserved() {
        val a = anchors(listOf(SheetDetent.Expanded, SheetDetent.Hidden, SheetDetent.Half))
        assertEquals(
            listOf(SheetDetent.Expanded, SheetDetent.Hidden, SheetDetent.Half),
            a.keys.toList(),
        )
    }

    @Test
    fun detentsAreIdentifiedByIdNotByLambda() {
        // Two `peek(140.dp)` calls make two objects with two different
        // resolvers. If they were not equal, a recomposition that rebuilt the
        // detent list would re-anchor and snap the sheet shut every frame.
        assertEquals(SheetDetent.peek(140.dp), SheetDetent.peek(999.dp))
        assertEquals(SheetDetent.fraction("x", 0.3f), SheetDetent.fraction("x", 0.9f))
    }
}
