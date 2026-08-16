package io.kontour.ui.sheet

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp

/**
 * One position a sheet can rest at, expressed as how much of it is *visible*.
 *
 * Height rather than offset because that is how the positions are actually
 * thought about — "showing 360dp", "showing just the header", "showing all of
 * it". [SheetState] converts to offsets internally.
 *
 * Detents are values, not an enum, so a screen can define its own without
 * touching the design system. The map's stop sheet rests at the height of its
 * own header; the trip planner rests at 360dp; neither is a case the library
 * could have enumerated in advance.
 *
 * @param id Identity. Two detents with the same id are the same detent, which is
 *   what lets a sheet keep its position across a recomposition that rebuilds the
 *   list — an equality based on the resolver lambda would re-anchor and snap the
 *   sheet shut on every frame.
 * @param resolve How much of the sheet is showing at this detent, in pixels.
 *   Given the container's height and the sheet content's own full height, both
 *   in pixels, with a [Density] receiver for `dp.toPx()`.
 */
@Immutable
class SheetDetent(
    val id: String,
    val resolve: Density.(containerHeight: Float, sheetHeight: Float) -> Float,
) {
    override fun equals(other: Any?): Boolean = other is SheetDetent && other.id == id
    override fun hashCode(): Int = id.hashCode()
    override fun toString(): String = "SheetDetent($id)"

    companion object {
        /** Off screen. Every sheet that can be dismissed needs this in its list. */
        val Hidden: SheetDetent = SheetDetent("hidden") { _, _ -> 0f }

        /**
         * As tall as its content, or the container — whichever is smaller.
         *
         * Not "the full height of the screen": a sheet with three rows in it
         * should be three rows tall, not an empty full-screen panel with three
         * rows at the top.
         */
        val Expanded: SheetDetent = SheetDetent("expanded") { container, sheet ->
            minOf(sheet, container)
        }

        /**
         * The whole container, whatever the content's height.
         *
         * The one [Expanded] cannot express. A sheet that is a screen — a full
         * trip plan, a form — wants the window even when its content is short,
         * and `Expanded` deliberately gives it exactly its content instead.
         * Reach for this when the sheet *is* the destination and for nothing
         * else; a three-row sheet blown up to full screen is a modal wearing a
         * sheet's clothes.
         */
        val Full: SheetDetent = SheetDetent("full") { container, _ -> container }

        /** Half the container. The common resting position for a sheet over a map. */
        val Half: SheetDetent = fraction("half", 0.5f)

        /**
         * Showing whatever the content marked with [io.kontour.ui.sheet.sheetPeekAnchor].
         *
         * The sheet peeks exactly far enough to show its header and no further,
         * whatever that header turns out to be — which is the detent the map
         * screens need and the one a fixed height cannot express, since a stop
         * header and a trip header are different heights and both change with
         * the user's font scale.
         *
         * Falls back to [fallback] until the anchor has been measured, and if
         * the content never marks one.
         */
        fun peek(fallback: Dp): SheetDetent = SheetDetent(PeekDetentId) { container, _ ->
            // The real value is substituted by SheetState once the anchor
            // reports its height; this resolver is the fallback.
            minOf(fallback.toPx(), container)
        }

        /** A fixed height. */
        fun height(id: String, height: Dp): SheetDetent = SheetDetent(id) { container, _ ->
            minOf(height.toPx(), container)
        }

        /** A fraction of the container's height. `0.5f` is half the screen. */
        fun fraction(id: String, fraction: Float): SheetDetent =
            SheetDetent(id) { container, _ -> container * fraction.coerceIn(0f, 1f) }
    }
}

/**
 * The id [SheetState] looks for when substituting a measured peek height.
 *
 * A magic string rather than a subtype so that [SheetDetent] stays a plain
 * value — a sheet's detent list is data, and one entry needing a different class
 * to be recognised makes it not data any more.
 */
internal const val PeekDetentId: String = "peek"

/** The default set: closed, peeking, half, or all of it. */
val DefaultSheetDetents: List<SheetDetent> = listOf(
    SheetDetent.Hidden,
    SheetDetent.Half,
    SheetDetent.Expanded,
)
