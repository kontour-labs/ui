package io.kontour.ui.overlay

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Anchored positioning — the flip-and-shift logic behind menus, popovers and
 * tooltips.
 *
 * A 1000×1000 container throughout, with a 100×50 anchor moved around inside it
 * and a 200×100 overlay. Round numbers so a failure reports a readable
 * difference rather than an arithmetic one.
 */
class AnchoringTest {

    private val container = IntSize(1000, 1000)
    private val content = IntSize(200, 100)
    private val gap = 8
    private val margin = 16

    private fun place(
        anchor: Rect,
        side: OverlaySide = OverlaySide.Bottom,
        alignment: OverlayAlignment = OverlayAlignment.Start,
        isRtl: Boolean = false,
        contentSize: IntSize = content,
        containerSize: IntSize = container,
        insets: AnchorInsets = AnchorInsets.None,
    ) = positionAnchored(
        anchor = anchor,
        contentSize = contentSize,
        containerSize = containerSize,
        side = side,
        alignment = alignment,
        gap = gap,
        margin = margin,
        isRtl = isRtl,
        insets = insets,
    )

    /** An anchor in the middle, with room on every side. */
    private val centred = Rect(450f, 475f, 550f, 525f)

    // --- The happy path -----------------------------------------------------

    @Test
    fun opensOnThePreferredSideWhenThereIsRoom() {
        val below = place(centred, side = OverlaySide.Bottom)
        assertEquals(ResolvedSide.Below, below.side)
        assertEquals(525 + gap, below.y)

        val above = place(centred, side = OverlaySide.Top)
        assertEquals(ResolvedSide.Above, above.side)
        assertEquals(475 - gap - content.height, above.y)
    }

    @Test
    fun alignmentPositionsAlongTheEdge() {
        assertEquals(450, place(centred, alignment = OverlayAlignment.Start).x)
        // Centre: anchor centre 500, minus half of 200.
        assertEquals(400, place(centred, alignment = OverlayAlignment.Center).x)
        // End: anchor's trailing edge 550, minus the full width.
        assertEquals(350, place(centred, alignment = OverlayAlignment.End).x)
    }

    // --- Flipping -----------------------------------------------------------

    @Test
    fun flipsWhenThePreferredSideHasNoRoom() {
        // 40px from the bottom: not enough for a 100px overlay plus the gap.
        val low = Rect(450f, 910f, 550f, 960f)
        val placed = place(low, side = OverlaySide.Bottom)

        assertEquals(ResolvedSide.Above, placed.side)
        assertEquals(910 - gap - content.height, placed.y)
    }

    @Test
    fun doesNotFlipWhenThePreferredSideJustFits() {
        // Exactly enough room below: bottom edge at 1000 - 16 - 8 - 100.
        val snug = Rect(450f, 800f, 550f, 876f)
        assertEquals(ResolvedSide.Below, place(snug, side = OverlaySide.Bottom).side)
    }

    @Test
    fun keepsTheRoomierSideWhenNeitherFits() {
        val tall = IntSize(200, 900)
        // Anchor low down: 300 above it, 40 below. Neither holds 900, so the
        // side with more room wins rather than the preferred one.
        val low = Rect(450f, 340f, 550f, 960f)
        val placed = place(low, side = OverlaySide.Bottom, contentSize = tall)

        assertEquals(ResolvedSide.Above, placed.side)
    }

    // --- Shifting -----------------------------------------------------------

    @Test
    fun shiftsBackInsideTheLeadingEdge() {
        // Anchored near the left edge and aligned End, which would put the
        // overlay's left edge at -190.
        val nearLeft = Rect(0f, 475f, 10f, 525f)
        val placed = place(nearLeft, alignment = OverlayAlignment.End)

        assertEquals(margin, placed.x)
    }

    @Test
    fun shiftsBackInsideTheTrailingEdge() {
        val nearRight = Rect(990f, 475f, 1000f, 525f)
        val placed = place(nearRight, alignment = OverlayAlignment.Start)

        assertEquals(container.width - margin - content.width, placed.x)
    }

    @Test
    fun shiftingGivesUpAlignmentRatherThanGoingOffScreen() {
        // The whole point: alignment is a preference, being visible is not.
        val corner = Rect(960f, 940f, 1000f, 980f)
        val placed = place(corner, alignment = OverlayAlignment.Start)

        assertTrue(placed.x + content.width <= container.width - margin)
        assertTrue(placed.y + content.height <= container.height - margin)
        assertTrue(placed.x >= margin)
    }

    @Test
    fun contentTooBigForTheContainerPinsToTheLeadingEdge() {
        // The clamp would invert here. Pinning keeps the start of the content
        // visible; inverting would push it off the opposite edge.
        val huge = IntSize(2000, 100)
        val placed = place(centred, contentSize = huge)

        assertEquals(margin, placed.x)
    }

    // --- Being placed on the side it resolved to ----------------------------

    /**
     * The assertion this file was missing, and the phone report it answers.
     *
     * *"setting it to 'bottom' on android doesn't seem to make it not show on the
     * top side"*. `keepsTheRoomierSideWhenNeitherFits` above asserts which side was
     * *chosen* and says nothing about where the panel went — and where it went was
     * across its own anchor, because the clamp that keeps an overlay inside the
     * container bounds the side's own axis too and can be tighter than the anchor.
     *
     * These are the numbers from `positionAnchored`'s own worked example: neither
     * side holds 600, `Below` is the roomier so it is kept, `y` wants to be 408, and
     * the clamp pulls it to 184 — above the anchor's *top* at 350, with an arrow
     * drawn pointing the other way.
     *
     * **Budgeted first, which is the fix**, and this test is the composition of the
     * two halves rather than either one: bound the content to the room beside the
     * anchor, and the panel that comes out fits on the side it resolved to, so the
     * clamp has nothing to do. That is what `AnchoredOverlayLayout` does in its own
     * measure pass, in this order.
     */
    @Test
    fun aBudgetedPanelIsPlacedOnTheSideItResolvedTo() {
        val short = IntSize(800, 800)
        val middle = Rect(300f, 350f, 500f, 400f)

        val unbudgeted = place(
            middle,
            side = OverlaySide.Bottom,
            contentSize = IntSize(200, 600),
            containerSize = short,
        )
        assertEquals(ResolvedSide.Below, unbudgeted.side)
        assertTrue(
            unbudgeted.y < middle.top,
            "the worked example is supposed to be the case that goes wrong, and " +
                "landing at ${unbudgeted.y} against an anchor top of ${middle.top} " +
                "is not it",
        )

        // What the layout actually measures with: the room beside the better side.
        val room = maxOf(
            roomBeside(middle, short, ResolvedSide.Below, gap, margin),
            roomBeside(middle, short, ResolvedSide.Above, gap, margin),
        )
        val budgeted = place(
            middle,
            side = OverlaySide.Bottom,
            contentSize = IntSize(200, room),
            containerSize = short,
        )

        assertEquals(ResolvedSide.Below, budgeted.side)
        assertTrue(
            budgeted.y >= middle.bottom,
            "budgeted to ${room}px — the room below the anchor — the panel still " +
                "landed at ${budgeted.y}, above the anchor's bottom edge at " +
                "${middle.bottom}",
        )
        assertTrue(
            budgeted.y + room <= short.height - margin,
            "and it has to still be on screen",
        )
    }

    /** The same composition on the other axis. */
    @Test
    fun aBudgetedPanelStaysOnItsOwnSideSideways() {
        val narrow = IntSize(800, 800)
        val middle = Rect(350f, 300f, 400f, 500f)
        val room = maxOf(
            roomBeside(middle, narrow, ResolvedSide.Right, gap, margin),
            roomBeside(middle, narrow, ResolvedSide.Left, gap, margin),
        )
        val placed = place(
            middle,
            side = OverlaySide.End,
            contentSize = IntSize(room, 100),
            containerSize = narrow,
        )

        assertEquals(ResolvedSide.Right, placed.side)
        assertTrue(placed.x >= middle.right, "placed at ${placed.x}, left of ${middle.right}")
    }

    /**
     * And when nothing fits anywhere, being on screen wins. Deliberately.
     *
     * A guarantee in `positionAnchored` itself — push the overlay back onto its side
     * whatever the clamp decided — was written and reverted. An anchor can be the
     * whole container: a `DropdownMenu` inside a `Box(Modifier.fillMaxSize())` has no
     * room on any side and so gets no budget, and the guarantee pinned it to
     * `anchor.bottom + gap`, off the bottom of the window with its items out of
     * reach. Two of the library's own menu tests found that within a minute.
     *
     * So this records the trade rather than asserting the ideal: a panel overlapping
     * its anchor is readable and a panel off screen is not.
     */
    @Test
    fun anAnchorThatFillsTheContainerStillLeavesTheOverlayOnScreen() {
        val whole = Rect(0f, 0f, 1000f, 1000f)
        val placed = place(whole, side = OverlaySide.Bottom)

        assertTrue(placed.y >= margin, "placed at ${placed.y}, off the top")
        assertTrue(
            placed.y + content.height <= container.height - margin,
            "placed at ${placed.y}, off the bottom — a menu anchored to a full-size " +
                "box would be unreachable",
        )
    }

    // --- The edges the container does not own -------------------------------

    /**
     * A navigation bar is not room to open into.
     *
     * The host an overlay is measured in fills the window and applies no insets, so
     * `roomBeside` counted the system's own chrome as usable. On a phone that is
     * routine rather than exceptional: an anchor 150px from the bottom has "room"
     * below it for a panel that will be drawn behind the gesture bar.
     *
     * With the inset declared the room is gone, so the flip decides on the real
     * number and the panel opens upward instead.
     */
    @Test
    fun aBottomInsetIsNotRoomBelow() {
        val short = IntSize(800, 800)
        val low = Rect(300f, 600f, 500f, 650f)

        assertEquals(
            ResolvedSide.Below,
            place(low, contentSize = IntSize(200, 100), containerSize = short).side,
            "126px of container below the anchor holds a 100px panel",
        )
        assertEquals(
            ResolvedSide.Above,
            place(
                low,
                contentSize = IntSize(200, 100),
                containerSize = short,
                insets = AnchorInsets(bottom = 80),
            ).side,
            "80px of that is a navigation bar, so 46px is left and the panel has to " +
                "go above the anchor",
        )
    }

    /** And it keeps the cross axis out of a cutout too. */
    @Test
    fun aSideInsetShiftsTheOverlayClearOfIt() {
        val nearLeft = Rect(0f, 475f, 10f, 525f)
        val placed = place(
            nearLeft,
            alignment = OverlayAlignment.End,
            insets = AnchorInsets(left = 48),
        )

        assertEquals(margin + 48, placed.x)
    }

    // --- The budget ---------------------------------------------------------

    /**
     * Content is measured against the room it is going in, not the whole container.
     *
     * The other half of the same fix, and the half that stops the problem arising:
     * a panel measured against an 800px window reports that it does not fit beside
     * an anchor with 376px next to it, however tall it would have been willing to
     * be. Budgeting it first means the flip above decides on the content's real
     * size and the clamp never has anything to do.
     */
    @Test
    fun theContentIsBudgetedToTheRoomBesideTheAnchor() {
        val short = IntSize(800, 800)
        val whole = overlayConstraints(short, margin, minWidth = 0)
        assertEquals(800 - margin * 2, whole.maxHeight)

        val budgeted = whole.withinSideRoom(room = 376, vertical = true)
        assertEquals(376, budgeted.maxHeight)
        assertEquals(whole.maxWidth, budgeted.maxWidth, "the cross axis is not the budget's business")
    }

    /**
     * And no room at all leaves the container's bound alone.
     *
     * A budget of nothing draws nothing, which is worse than a panel clipped at the
     * far edge — and `positionAnchored` keeps that panel beside its anchor, so the
     * arrow still tells the truth.
     */
    @Test
    fun noRoomOnEitherSideKeepsTheContainersOwnBound() {
        val short = IntSize(800, 800)
        val whole = overlayConstraints(short, margin, minWidth = 0)
        assertEquals(whole.maxHeight, whole.withinSideRoom(room = 0, vertical = true).maxHeight)
        assertEquals(whole.maxHeight, whole.withinSideRoom(room = -40, vertical = true).maxHeight)
    }

    /** A select's promise about its menu's width outranks the budget. */
    @Test
    fun aMinimumWidthSurvivesTheBudget() {
        val short = IntSize(800, 800)
        val whole = overlayConstraints(short, margin, minWidth = 400)
        val budgeted = whole.withinSideRoom(room = 300, vertical = false)

        assertEquals(400, budgeted.minWidth)
        assertEquals(400, budgeted.maxWidth, "a maximum under the minimum would throw")
    }

    /**
     * An anchor that is most of the container gets no budget at all.
     *
     * The floor, and the two cases it exists for are both real and both in this
     * repository's own tests. A `DropdownMenu` declared inside a
     * `Box(Modifier.fillMaxSize())` has an anchor the size of the window and no room
     * anywhere; give it 24dp of padding and it has twelve pixels. Budgeted to twelve
     * pixels a menu is a sliver with its rows unreachable, which is a worse answer
     * than the overlap it had before.
     */
    @Test
    fun anAnchorThatIsMostOfTheContainerIsNotBudgetedAtAll() {
        val window = IntSize(1024, 768)
        val whole = overlayConstraints(window, margin, minWidth = 0)

        assertEquals(
            whole.maxHeight,
            whole.withinSideRoom(room = 12, vertical = true).maxHeight,
            "twelve pixels beside the anchor is not a side to live on",
        )
        assertEquals(
            whole.maxHeight,
            whole.withinSideRoom(room = 0, vertical = true).maxHeight,
        )
        // And a third of the way up, the budget is back on.
        val third = whole.maxHeight / 3 + 1
        assertEquals(third, whole.withinSideRoom(room = third, vertical = true).maxHeight)
    }

    // --- Layout direction ---------------------------------------------------

    @Test
    fun startAndEndFollowTheLayoutDirection() {
        assertEquals(ResolvedSide.Right, place(centred, side = OverlaySide.End).side)
        assertEquals(
            ResolvedSide.Left,
            place(centred, side = OverlaySide.End, isRtl = true).side,
        )
    }

    @Test
    fun alignmentMirrorsInRtl() {
        // Start in RTL means the overlay's *right* edge lines up with the
        // anchor's right edge — the reading-order start.
        val placed = place(centred, alignment = OverlayAlignment.Start, isRtl = true)
        assertEquals(550 - content.width, placed.x)
    }

    @Test
    fun verticalAlignmentIsUnaffectedByRtl() {
        // Alignment along a vertical edge is top-to-bottom in every locale.
        val ltr = place(centred, side = OverlaySide.End, alignment = OverlayAlignment.Start)
        val rtl = place(
            centred,
            side = OverlaySide.End,
            alignment = OverlayAlignment.Start,
            isRtl = true,
        )
        assertEquals(ltr.y, rtl.y)
    }

    // --- Horizontal sides ---------------------------------------------------

    @Test
    fun submenusOpenOutwardAndFlipAtTheEdge() {
        val roomy = place(centred, side = OverlaySide.End)
        assertEquals(ResolvedSide.Right, roomy.side)
        assertEquals(550 + gap, roomy.x)

        val nearRight = Rect(900f, 475f, 960f, 525f)
        val flipped = place(nearRight, side = OverlaySide.End)
        assertEquals(ResolvedSide.Left, flipped.side)
        assertEquals(900 - gap - content.width, flipped.x)
    }

    // --- Constraints --------------------------------------------------------

    @Test
    fun aMinimumWiderThanTheContainerIsClampedRatherThanThrowing() {
        // A full-width select on a phone: the field is as wide as the window,
        // and its menu asks to match. The window less two margins is narrower
        // than the field, and `Constraints(minWidth > maxWidth)` throws — so
        // every full-width select would crash the first time it was opened.
        val constraints = overlayConstraints(
            container = IntSize(400, 800),
            margin = 16,
            minWidth = 400,
        )

        assertEquals(368, constraints.maxWidth)
        assertEquals(368, constraints.minWidth)
    }

    @Test
    fun aMinimumThatFitsIsKept() {
        val constraints = overlayConstraints(
            container = IntSize(1000, 800),
            margin = 16,
            minWidth = 400,
        )

        assertEquals(400, constraints.minWidth)
        assertEquals(968, constraints.maxWidth)
    }

    /**
     * A host inside a scrolling parent is measured with an unbounded height.
     *
     * Subtracting the margin from `Constraints.Infinity` gives
     * `Int.MAX_VALUE - 2 * margin` — a *finite* number too large for
     * `Constraints` to bit-pack, so it throws. "Infinity minus sixteen" is not a
     * size; there is nothing to subtract from.
     *
     * This is the crash the catalog's Forms page hit
     * (`height of 2147483631`), and it would have hit any app that put an
     * overlay host in a scroll container.
     */
    @Test
    fun anUnboundedContainerAxisStaysUnbounded() {
        val constraints = overlayConstraints(
            container = IntSize(400, Constraints.Infinity),
            margin = 8,
            minWidth = 0,
        )

        assertEquals(384, constraints.maxWidth)
        assertFalse(constraints.hasBoundedHeight, "the height should still be unbounded")
    }

    /** And a minimum is not clamped against an axis that has no maximum. */
    @Test
    fun aMinimumSurvivesAnUnboundedWidth() {
        val constraints = overlayConstraints(
            container = IntSize(Constraints.Infinity, 800),
            margin = 8,
            minWidth = 240,
        )

        assertEquals(240, constraints.minWidth)
        assertFalse(constraints.hasBoundedWidth, "the width should still be unbounded")
    }

    @Test
    fun aContainerNarrowerThanItsOwnMarginsStaysValid() {
        val constraints = overlayConstraints(
            container = IntSize(10, 10),
            margin = 16,
            minWidth = 200,
        )

        assertEquals(0, constraints.minWidth)
        assertEquals(0, constraints.maxWidth)
        assertEquals(0, constraints.maxHeight)
    }

    // --- A zero-size anchor, which is what a context menu uses --------------

    @Test
    fun aPointAnchorIsPositionedFromThePointItself() {
        val point = Rect(300f, 300f, 300f, 300f)
        val placed = place(point, side = OverlaySide.Bottom, alignment = OverlayAlignment.Start)

        assertEquals(300, placed.x)
        assertEquals(300 + gap, placed.y)
    }
}
