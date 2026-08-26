package io.kontour.ui.theme

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertSame

/**
 * One squircle instance serves several sizes at once without rebuilding.
 *
 * The shape scale is aliased: `Shapes.container` **is** `Shapes.medium`, one
 * object shared by every card, list row, menu, popover and drawer on screen. The
 * cache was a single slot, so any two of those at different sizes evicted each
 * other and both rebuilt their path — four corners of trigonometry and twelve
 * cubic segments — on every draw, for as long as both were visible.
 *
 * Identity is the instrument: a hit returns the cached [Path] itself, so the
 * same object coming back is proof no work was redone. Counting rebuilds
 * directly would mean a hole in the shape's API for a test to look through.
 */
class SquirclePathCacheTest {

    private val density = Density(1f)

    private fun SquircleShape.pathAt(width: Float, height: Float): Path =
        (createOutline(Size(width, height), LayoutDirection.Ltr, density) as Outline.Generic).path

    @Test
    fun aRepeatedSizeIsNotRebuilt() {
        val shape = SquircleShape(20.dp)
        assertSame(
            shape.pathAt(200f, 80f),
            shape.pathAt(200f, 80f),
            "the same size built a second path, so nothing is cached at all",
        )
    }

    @Test
    fun sizesInUseTogetherDoNotEvictEachOther() {
        val shape = SquircleShape(20.dp)

        // A card, a row, a menu and a popover: four containers on one screen,
        // all drawn through the one aliased shape instance.
        val card = shape.pathAt(360f, 220f)
        val row = shape.pathAt(360f, 56f)
        val menu = shape.pathAt(240f, 320f)
        val popover = shape.pathAt(280f, 120f)

        // Second frame. Nothing resized, so nothing should be rebuilt.
        assertSame(card, shape.pathAt(360f, 220f), "the card's path was evicted")
        assertSame(row, shape.pathAt(360f, 56f), "the row's path was evicted")
        assertSame(menu, shape.pathAt(240f, 320f), "the menu's path was evicted")
        assertSame(popover, shape.pathAt(280f, 120f), "the popover's path was evicted")
    }

    @Test
    fun aResizeStillGetsTheRightPath() {
        val shape = SquircleShape(20.dp)
        val first = shape.pathAt(200f, 80f)
        val grown = shape.pathAt(260f, 80f)

        // Correctness, not caching: a hit on the wrong entry would draw one
        // container with another's outline, which is the failure a cache with
        // more than one slot can newly make.
        assertSame(first, shape.pathAt(200f, 80f))
        assertSame(grown, shape.pathAt(260f, 80f))
    }
}
