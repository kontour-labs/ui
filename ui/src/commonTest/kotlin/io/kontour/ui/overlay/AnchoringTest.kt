package io.kontour.ui.overlay

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize
import kotlin.test.Test
import kotlin.test.assertEquals
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
    ) = positionAnchored(
        anchor = anchor,
        contentSize = contentSize,
        containerSize = containerSize,
        side = side,
        alignment = alignment,
        gap = gap,
        margin = margin,
        isRtl = isRtl,
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
