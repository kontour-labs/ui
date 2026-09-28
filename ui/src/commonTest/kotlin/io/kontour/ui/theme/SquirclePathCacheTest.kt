package io.kontour.ui.theme

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertNotSame
import kotlin.test.assertSame

/**
 * A squircle's path is built once for what it *is*, not once per asker.
 *
 * The shape scale is aliased: `Shapes.container` **is** `Shapes.medium`, one
 * object shared by every card, list row, menu, popover and drawer on screen. The
 * cache was a single slot, so any two of those at different sizes evicted each
 * other and both rebuilt their path — four corners of trigonometry and twelve
 * cubic segments — on every draw, for as long as both were visible. Four slots
 * fixed that and left the harder half.
 *
 * The harder half is a **derived** shape. `inset`, `outset`, `atLeast` and
 * `lerpCorners` all go through `copy`, which was a new instance with an empty
 * cache, and nine call sites in the library build one in composition without
 * remembering it — so every recomposition rebuilt the path from nothing. Two of
 * them do it on every frame of an animation, feeding a `graphicsLayer` shadow
 * that re-rasterises its blur along with it.
 * [twoShapesThatResolveTheSameShareOnePath] is that case, and no per-instance
 * cache of any size can pass it.
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
    fun twoShapesThatResolveTheSameShareOnePath() {
        // Two instances, built separately, with nothing in common but the corners
        // they come out at. This is every derived shape in the library: `inset`
        // and its siblings all return a fresh `copy`, and a call site that builds
        // one in composition hands a different object to the same component on
        // every recomposition.
        val original = SquircleShape(20.dp)
        val rebuilt = SquircleShape(20.dp)

        assertSame(
            original.pathAt(360f, 220f),
            rebuilt.pathAt(360f, 220f),
            "a second shape with the same corners at the same size built its own " +
                "path. A cache that belongs to the instance cannot do otherwise, " +
                "which is why this one does not — a path is decided by its " +
                "geometry and nothing else, so who asked for it is not part of " +
                "the question.",
        )
    }

    @Test
    fun smoothingIsPartOfWhatAPathIs() {
        // The one field that does not appear in the corners and does change the
        // curve. A key that left it out would hand a theme asking for square
        // corners the smoothed path built for the theme before it.
        val smooth = SquircleShape(20.dp, smoothing = 0.6f)
        val plain = SquircleShape(20.dp, smoothing = 0f)

        assertNotSame(
            smooth.pathAt(200f, 80f),
            plain.pathAt(200f, 80f),
            "two smoothings at one size came back as the same path, so a theme " +
                "that turns continuous corners off would get them anyway",
        )
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
