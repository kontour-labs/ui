package io.kontour.ui.theme

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The radius scale, asserted rather than photographed.
 *
 * Until this existed the only thing standing behind a corner radius was 204
 * screenshot goldens, which move for any reason at all and are accepted in a
 * batch. A scale that has to stay evenly stepped, and a squircle that has to
 * actually be a squircle, are both claims a golden cannot make on its own.
 */
class ShapeScaleTest {

    private val density = Density(1f)
    private val shapes = Shapes()

    private fun CornerBasedShape.radiusPx(size: Size = Size(1000f, 1000f)): Float =
        topStart.toPx(size, density)

    @Test
    fun theLadderIsEvenlyStepped() {
        val ladder = listOf(
            shapes.extraSmall.radiusPx(),
            shapes.small.radiusPx(),
            shapes.medium.radiusPx(),
            shapes.large.radiusPx(),
            shapes.extraLarge.radiusPx(),
        )

        val steps = ladder.zipWithNext { lower, upper -> upper - lower }
        assertTrue(steps.all { it > 0f }, "the scale must increase: $ladder")
        assertTrue(
            steps.all { abs(it - steps.first()) < 0.01f },
            "every step must be the same size, or `inset` cannot step through the " +
                "scale and concentricity has to be guessed at: steps were $steps",
        )
    }

    @Test
    fun theSemanticTokensClimbInTheRightOrder() {
        val size = Size(400f, 400f)
        val field = shapes.field.topStart.toPx(size, density)
        val container = shapes.container.topStart.toPx(size, density)
        val panel = shapes.panel.topStart.toPx(size, density)

        assertTrue(
            field < container && container < panel,
            "a control has to look like it is inside its container and the " +
                "container inside the panel, but the radii went $field, " +
                "$container, $panel",
        )
    }

    @Test
    fun aControlIsACapsuleAtEveryHeight() {
        // The reason buttons are not a fixed radius. At 14dp an XSmall button was
        // nearly a pill already and an XLarge was nearly square, so one component
        // disagreed with itself across its own size scale. A capsule cannot.
        for (height in listOf(28f, 36f, 44f, 56f, 72f)) {
            val radius = shapes.control.topStart.toPx(Size(200f, height), density)
            assertEquals(
                height / 2f,
                radius,
                "a control $height tall should have a radius of ${height / 2f}",
            )
        }
    }

    @Test
    fun theSemanticTokensAreASeamRatherThanAnAlias() {
        // The point of naming them at all. An app that wants square buttons
        // overrides `control`; overriding `pill` instead would square off the
        // avatars and the scrollbar with them.
        val squared = Shapes(control = RoundedCornerShape(4.dp))
        val size = Size(200f, 40f)

        assertEquals(4f, squared.control.topStart.toPx(size, density))
        assertEquals(20f, squared.pill.topStart.toPx(size, density))
        assertEquals(
            shapes.field.topStart.toPx(size, density),
            squared.field.topStart.toPx(size, density),
            "moving one family must not move another",
        )
    }

    @Test
    fun sheetsAreExtraLargeWithTwoCornersOff() {
        val size = Size(1000f, 1000f)
        val hero = shapes.extraLarge.topStart.toPx(size, density)

        assertEquals(hero, shapes.sheet.topStart.toPx(size, density))
        assertEquals(hero, shapes.sheet.topEnd.toPx(size, density))
        assertEquals(0f, shapes.sheet.bottomStart.toPx(size, density))
        assertEquals(0f, shapes.sheet.bottomEnd.toPx(size, density))

        assertEquals(hero, shapes.sideSheet.topStart.toPx(size, density))
        assertEquals(hero, shapes.sideSheet.bottomStart.toPx(size, density))
        assertEquals(0f, shapes.sideSheet.topEnd.toPx(size, density))
        assertEquals(0f, shapes.sideSheet.bottomEnd.toPx(size, density))
    }

    @Test
    fun insetSubtractsTheGapAndFloorsAtZero() {
        val size = Size(1000f, 1000f)
        val outer = shapes.large

        assertEquals(
            outer.topStart.toPx(size, density) - 6f,
            outer.inset(6.dp).topStart.toPx(size, density),
        )
        assertEquals(0f, outer.inset(500.dp).topStart.toPx(size, density))
    }

    @Test
    fun insetKeepsTheShapeItIsCalledOn() {
        assertTrue(
            shapes.large.inset(6.dp) is SquircleShape,
            "inset a squircle and the corner must stay smooth, or a nested control " +
                "comes out concentric in radius and wrong in curvature",
        )
        assertTrue(shapes.small.inset(2.dp) is RoundedCornerShape)
    }

    @Test
    fun insetOfAPercentCornerStaysAPercentUntilItIsResolved() {
        // The reason InsetCornerSize defers instead of subtracting up front: half
        // of a 100px pill is 50, half of a 200px pill is 100, and the gap comes off
        // whichever one the caller turns out to be.
        val pill = shapes.pill.inset(4.dp)
        assertEquals(46f, pill.topStart.toPx(Size(100f, 100f), density))
        assertEquals(96f, pill.topStart.toPx(Size(200f, 200f), density))
    }

    @Test
    fun aSquircleIsAGenericOutlineInsideItsBounds() {
        val size = Size(200f, 120f)
        val outline = SquircleShape(24.dp).createOutline(size, LayoutDirection.Ltr, density)
        assertTrue(outline is Outline.Generic, "a smoothed corner cannot be a rounded rect")
        assertInsideBounds(walk(outline.path), size)
    }

    @Test
    fun aSquircleTakesMoreOffTheCornerThanAnArcOfTheSameRadius() {
        // The defining property, and not the one you would guess. The smoothed
        // corner keeps the *same* arc — same centre, same radius — so at 45 degrees
        // the two outlines coincide exactly. What differs is everywhere else: an
        // arc holds the straight edge until `r` from the corner and then turns all
        // at once, while a squircle starts bending at `(1 + smoothing) * r` and
        // eases in. So it eats further along both edges, and encloses less.
        //
        // Measured as area rather than as where the curve leaves the edge, because
        // near the tangent point both hug the edge to within a fraction of a pixel
        // and any threshold you pick measures the threshold instead of the shape.
        val size = Size(240f, 240f)
        val radius = 40f

        val arc = area(walk(pathOf(RoundedCornerShape(radius.dp), size)))
        val squircle = area(walk(pathOf(SquircleShape(radius.dp), size)))
        val fourCorners = 4f * radius * radius

        assertTrue(
            squircle < arc - 0.005f * fourCorners,
            "$squircle should be measurably under $arc — the corners are $fourCorners",
        )
        assertTrue(squircle > arc - fourCorners, "$squircle has eaten more than the corners it owns")
    }

    @Test
    fun theSquircleIsFurtherFromTheCornerAcrossTheBlend() {
        // The same claim from the other side, and the one that says this is a blend
        // rather than just a smaller radius. Across the range of angles where the
        // squircle is easing and the arc is not, the squircle sits further out.
        // Where the two meet again is the next test.
        val size = Size(240f, 240f)
        val angles = listOf(5f, 10f, 15f, 20f, 25f, 30f)

        val deviations = angles.map { angle ->
            distanceFromCornerAt(SquircleShape(40.dp), size, angle) -
                distanceFromCornerAt(RoundedCornerShape(40.dp), size, angle)
        }

        assertTrue(
            deviations.all { it >= -0.02f },
            "the squircle should never be inside the arc: $deviations at $angles",
        )
        assertTrue(
            deviations.max() > 0.3f,
            "the blend should be measurable somewhere across the corner, but the " +
                "largest gap was ${deviations.max()} at $angles",
        )
    }

    @Test
    fun theArcItselfIsUntouched() {
        // Same centre, same radius, so at the diagonal the two outlines are the
        // same point. This is what lets one scale carry both kinds of corner
        // without the squircle tokens reading as a size larger than the circular
        // ones beneath them.
        val size = Size(240f, 240f)
        val squircle = reachIntoTopLeftCorner(SquircleShape(40.dp), size)
        val arc = reachIntoTopLeftCorner(RoundedCornerShape(40.dp), size)
        assertTrue(abs(squircle - arc) < 0.05f, "$squircle should equal $arc")
    }

    @Test
    fun smoothingTapersToNothingAsTheCornerSaturates() {
        // A corner that has spent its whole budget on the radius is a semicircle,
        // and there is no straight edge left to ease onto. Without the taper the
        // blend handles have nowhere to go and the path folds through itself.
        // A saturated squircle therefore has to land back on the plain circle.
        val size = Size(100f, 100f)
        val circle = area(walk(pathOf(RoundedCornerShape(50.dp), size)))
        val saturated = area(walk(pathOf(SquircleShape(50.dp), size)))

        assertTrue(
            abs(saturated - circle) < 0.005f * circle,
            "a fully saturated corner must land on the circle: $saturated against $circle",
        )
    }

    @Test
    fun anOversizedRadiusIsClampedRatherThanFolded() {
        // 400dp of radius on a 100x60 box. The outline still has to be a closed
        // curve inside its bounds rather than a knot.
        val size = Size(100f, 60f)
        val outline = SquircleShape(400.dp).createOutline(size, LayoutDirection.Ltr, density)
        assertInsideBounds(walk((outline as Outline.Generic).path), size)
    }

    @Test
    fun aZeroRadiusCornerIsSquare() {
        // Shapes.sheet is a squircle with its bottom two corners zeroed, so this is
        // the case that decides whether a bottom sheet has a bottom edge.
        val size = Size(200f, 200f)
        val outline = SquircleShape(24.dp).topCornersOnly()
            .createOutline(size, LayoutDirection.Ltr, density)
        assertTrue(outline is Outline.Generic)

        val nearestToBottomLeft = walk(outline.path)
            .minBy { it.x * it.x + (size.height - it.y) * (size.height - it.y) }
        assertTrue(
            nearestToBottomLeft.x < 0.5f && nearestToBottomLeft.y > size.height - 0.5f,
            "the bottom-left corner should be square, but the nearest point on the " +
                "outline was $nearestToBottomLeft",
        )
    }

    @Test
    fun theCornersFollowTheLayoutDirection() {
        val size = Size(200f, 200f)
        val shape = SquircleShape(topStart = 40.dp, topEnd = 0.dp, bottomEnd = 0.dp, bottomStart = 0.dp)

        val ltr = shape.createOutline(size, LayoutDirection.Ltr, density) as Outline.Generic
        val rtl = shape.createOutline(size, LayoutDirection.Rtl, density) as Outline.Generic

        // In LTR the rounded corner is top-left, in RTL it is top-right, so the
        // outline's closest approach to each corner point swaps over.
        assertTrue(nearest(walk(ltr.path), Offset.Zero) > 5f)
        assertTrue(nearest(walk(rtl.path), Offset.Zero) < 0.5f)
        assertTrue(nearest(walk(rtl.path), Offset(size.width, 0f)) > 5f)
    }

    private fun assertInsideBounds(points: List<Offset>, size: Size) {
        assertTrue(points.isNotEmpty())
        assertTrue(
            points.all {
                it.x >= -0.5f && it.x <= size.width + 0.5f &&
                    it.y >= -0.5f && it.y <= size.height + 0.5f
            },
            "the outline escaped its own bounds",
        )
    }

    /** How close the outline gets to the top-left corner point, in pixels. */
    private fun reachIntoTopLeftCorner(shape: CornerBasedShape, size: Size): Float =
        nearest(walk(pathOf(shape, size)), Offset.Zero)

    /**
     * How far the outline sits from the top-left corner point along a ray at
     * [degrees] below the top edge.
     */
    private fun distanceFromCornerAt(shape: CornerBasedShape, size: Size, degrees: Float): Float {
        val target = degrees * PI.toFloat() / 180f
        val window = 0.004f
        val hits = walk(pathOf(shape, size))
            .filter { it.x > 0.01f && it.y > 0.01f }
            .filter { abs(atan2(it.y, it.x) - target) < window }
        assertTrue(hits.isNotEmpty(), "no sample landed near $degrees degrees")
        return hits.minOf { sqrt(it.x * it.x + it.y * it.y) }
    }

    /** Shoelace over the sampled outline. */
    private fun area(points: List<Offset>): Float {
        var total = 0.0
        for (i in points.indices) {
            val a = points[i]
            val b = points[(i + 1) % points.size]
            total += a.x.toDouble() * b.y - b.x.toDouble() * a.y
        }
        return (abs(total) / 2.0).toFloat()
    }

    private fun nearest(points: List<Offset>, to: Offset): Float =
        points.minOf { sqrt((it.x - to.x) * (it.x - to.x) + (it.y - to.y) * (it.y - to.y)) }

    private fun pathOf(shape: CornerBasedShape, size: Size): Path =
        when (val outline = shape.createOutline(size, LayoutDirection.Ltr, density)) {
            is Outline.Generic -> outline.path
            is Outline.Rounded -> Path().apply { addRoundRect(outline.roundRect) }
            is Outline.Rectangle -> Path().apply { addRect(outline.rect) }
        }

    private fun walk(path: Path): List<Offset> {
        val measure = PathMeasure()
        measure.setPath(path, true)
        val length = measure.length
        if (length <= 0f) return emptyList()
        val steps = 20000
        return (0..steps).map { measure.getPosition(length * it / steps) }
    }
}
