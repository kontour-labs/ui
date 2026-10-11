package io.kontour.ui.theme

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A squircle's corners are worked out once per radius, not once per size, and a
 * path placed from corners worked out at another size is the path built from
 * scratch.
 *
 * A floating sheet changes height on every frame it is dragged, so its path —
 * keyed on the exact size — was rebuilt from nothing every frame: forty-odd
 * sines, cosines and tangents, reported as a sheet that *"really stutters"*. A
 * corner with room on both its edges does not depend on the size at all.
 */
class SquircleCornerCacheTest {

    private val density = Density(1f)

    private fun SquircleShape.pathAt(size: Size, direction: LayoutDirection = LayoutDirection.Ltr): Path =
        (createOutline(size, direction, density) as Outline.Generic).path

    /** Whether two paths cover the same area, to within a pixel's sliver. */
    private fun assertSamePath(expected: Path, actual: Path, what: String) {
        val difference = Path().apply { op(expected, actual, PathOperation.Xor) }
        val bounds = difference.getBounds()
        assertTrue(
            difference.isEmpty || bounds.width * bounds.height < 1f || bounds.width < 0.5f || bounds.height < 0.5f,
            "$what: the path from cached corners differs from the one built from scratch, over $bounds",
        )
    }

    private val shapes = listOf(
        SquircleShape(34.dp),
        SquircleShape(34.dp, 34.dp, 0.dp, 0.dp),
        SquircleShape(40.dp, 24.dp, 12.dp, 30.dp),
        SquircleShape(20.dp, smoothing = 0f),
        SquircleShape(28.dp, smoothing = 1f),
    )

    @Test
    fun aPathFromCornersWorkedOutAtAnotherSizeIsThePathFromScratch() {
        // Roomy sizes, which share corners, and tight ones, which clamp and do not.
        val sizes = listOf(Size(360f, 640f), Size(361f, 412.5f), Size(120f, 90f), Size(70f, 70f), Size(400f, 60f))
        for (shape in shapes) for (direction in LayoutDirection.entries) for (size in sizes) {
            clearSquircleCaches()
            val fromScratch = shape.pathAt(size, direction)
            clearSquircleCaches()
            // Corners worked out at a different, roomy size first.
            shape.pathAt(Size(500f, 900f), direction)
            val placed = shape.pathAt(size, direction)
            assertSamePath(fromScratch, placed, "$shape at $size, $direction")
        }
    }

    @Test
    fun aSheetDraggedAFrameAtATimeWorksItsCornersOutOnce() {
        val shape = SquircleShape(34.dp, 34.dp, 34.dp, 34.dp)
        clearSquircleCaches()
        val before = SquircleCornerCount.computed
        // Forty frames of a floating sheet growing taller under a finger.
        repeat(40) { frame -> shape.pathAt(Size(360f, 400f + frame * 7.3f)) }
        assertEquals(4, SquircleCornerCount.computed - before, "the corners were worked out more than once across the drag")
    }
}
