package io.kontour.ui.sheet

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.kontour.ui.theme.Theme
import kotlin.math.sqrt

/**
 * What a sheet's own parts add to their insets near its top corners, because
 * those corners are rounder than the theme's.
 *
 * An edge sheet's top corners are the display's own (`concentricWith`), and a
 * display's can be far rounder than the sheet's: about 55pt on an iPhone, 51dp on
 * a newer Pixel, against the theme's 34dp. The header's 16dp inset was chosen
 * against the theme's corner, and under a display's it put the title's corner
 * 5dp from the curve, where it had 13: *"the sheet header still feels like it's a
 * little bit close to the rounded corners … especially on devices with
 * extra-rounded corners like iphones and newer pixels."*
 *
 * So the header, the drag handle's band and a top pinned part move in along the
 * corner by as much as the rounder corner cuts in: [start] at the start edge,
 * [end] at the end, [top] above. A corner no rounder than the theme's adds
 * nothing, so a sheet on a desktop, or with the theme's own corners, is as it was.
 *
 * Logical, as `CornerBasedShape`'s corners are: `concentricWith` has already
 * mapped the display's physical corners through the layout direction.
 */
@Immutable
internal class SheetCornerRoom(val start: Dp, val end: Dp) {

    /** Above: the deeper of the two corners, since the band runs under both. */
    val top: Dp get() = maxOf(start, end)

    override fun equals(other: Any?): Boolean =
        other is SheetCornerRoom && other.start == start && other.end == end

    override fun hashCode(): Int = 31 * start.hashCode() + end.hashCode()

    override fun toString(): String = "SheetCornerRoom(start=$start, end=$end)"

    companion object {
        val None = SheetCornerRoom(0.dp, 0.dp)
    }
}

/**
 * How far a corner of radius R cuts into its diagonal, as a fraction of R: the
 * corner of a square R on a side is R√2 from the arc's centre, and the arc R.
 *
 * Exact for a circular corner. The house squircle is one for the middle of each
 * corner — its smoothing only bends the ends, where the curve meets the straight
 * edges — and the header's corners fall in that middle.
 */
private val DiagonalDepth = 1f - 1f / sqrt(2f)

/**
 * The room a corner of [radius] needs beyond one of [reference]: what the
 * rounder corner cuts in along the diagonal, over and above what the reference
 * does. None for a corner no rounder than the reference.
 */
internal fun cornerRoom(radius: Dp, reference: Dp): Dp =
    (radius - reference).coerceAtLeast(0.dp) * DiagonalDepth

/**
 * The room for the roundest of [shapes]' top corners, measured against
 * [reference]'s, with each resolved on a [box] — which only matters for a corner
 * given as a percentage. A shape that is not corner-based adds nothing.
 */
internal fun sheetCornerRoom(shapes: List<Shape>, reference: Shape, box: Size, density: Density): SheetCornerRoom {
    fun radius(shape: Shape, start: Boolean): Dp {
        val corners = shape as? CornerBasedShape ?: return 0.dp
        val corner = if (start) corners.topStart else corners.topEnd
        return with(density) { corner.toPx(box, density).toDp() }
    }
    val startReference = radius(reference, start = true)
    val endReference = radius(reference, start = false)
    return SheetCornerRoom(
        start = shapes.maxOfOrNull { cornerRoom(radius(it, start = true), startReference) } ?: 0.dp,
        end = shapes.maxOfOrNull { cornerRoom(radius(it, start = false), endReference) } ?: 0.dp,
    )
}

/**
 * The room for a sheet of [shape], and of [expandedShape] if it morphs into one.
 *
 * **One value for the sheet, not one per frame.** A floating sheet that morphs
 * into an edge sheet changes its corners as it is dragged, and the band's height
 * is part of the pinned detent's: a band that grew with the morph would move the
 * anchors under the finger, which is the bug `bottomAtRest` was written against.
 * So a morphing sheet keeps the edge sheet's room all the way — a few dp more
 * than it needs while it floats, on a display with round corners.
 */
@Composable
internal fun rememberSheetCornerRoom(shape: Shape, expandedShape: Shape?): SheetCornerRoom {
    val reference = Theme.shapes.sheet
    val density = LocalDensity.current
    val width = BottomSheetDefaults.MaxWidth
    return remember(shape, expandedShape, reference, density, width) {
        val side = with(density) { width.toPx() }
        sheetCornerRoom(listOfNotNull(shape, expandedShape), reference, Size(side, side), density)
    }
}

/**
 * The room a sheet's column hands its header: provided by the column, so a header
 * in a pinned part — whose block makes room at its own sides — does not add it
 * a second time.
 */
internal val LocalSheetCornerRoom = staticCompositionLocalOf { SheetCornerRoom.None }
