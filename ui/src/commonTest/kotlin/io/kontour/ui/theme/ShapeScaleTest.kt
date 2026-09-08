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
        // The ladder half of the model. `container` and `panel` are still rungs
        // and still have to climb, because a dialog holding a card has to read
        // as holding it.
        val size = Size(400f, 400f)
        val container = shapes.container.topStart.toPx(size, density)
        val panel = shapes.panel.topStart.toPx(size, density)

        assertTrue(
            container < panel,
            "a container has to look like it is inside its panel, but the radii " +
                "went $container, $panel",
        )
    }

    @Test
    fun aBigFieldNeverOutroundsItsOwnCap() {
        // `field` used to be a rung *below* `container`, and this test used to
        // assert exactly that. It cannot any more: a field is derived from its
        // height now, so a single-line one is a capsule at 22–26dp — which is the
        // point, and which is more than a container's 22.
        //
        // What survives of the old assertion is the case it was really about. A
        // text area is tall, and a tall box with a proportional corner is a
        // lozenge; the cap is what stops it, so the cap is what to check.
        val cap = 18f
        for (height in listOf(120f, 400f, 1000f)) {
            val radius = shapes.field.topStart.toPx(Size(400f, height), density)
            assertEquals(cap, radius, "a $height-tall field should stop at the cap")
        }
    }

    @Test
    fun aControlIsAPillUpToSmallAndSquarerAboveIt() {
        // This used to assert that a control is a capsule at *every* height, and
        // the reason it no longer does is the whole of round 26: at the top of
        // the size scale a capsule stops reading as a considered radius and
        // starts reading as a stadium. A 60dp XL button at 30dp is a lozenge.
        //
        // The cap is 18dp, and it is not a tuned number — it is half
        // `Sizing.controlHeightSmall`, which is what makes the two halves of the
        // rule one rule rather than two competing ones. At `small` and below the
        // corner is under the cap, so it is exactly half the height and the
        // control is a pill. At 36dp precisely, both readings give 18. Above it
        // the corner stops and the control gets progressively squarer, which is
        // the direction that was asked for.
        for (height in listOf(24f, 28f, 32f, 36f)) {
            val radius = shapes.control.topStart.toPx(Size(200f, height), density)
            assertEquals(
                height / 2f,
                radius,
                "a control $height tall is at or below `small`, so it should be a " +
                    "pill at ${height / 2f} rather than capped",
            )
        }
        for (height in listOf(44f, 52f, 60f, 72f, 200f)) {
            val radius = shapes.control.topStart.toPx(Size(400f, height), density)
            assertEquals(
                18f,
                radius,
                "a control $height tall is above `small`, so it should stop at the " +
                    "18dp cap rather than reach ${height / 2f}",
            )
        }
    }

    @Test
    fun theCapIsExactlyWhereSmallStopsBeingAPill() {
        // The two halves of the rule have to meet, or there is a discontinuity at
        // the join: a 36dp control one pixel taller would jump. They meet because
        // the cap *is* half the small height rather than a number near it.
        val small = Sizing().controlHeightSmall
        val atSmall = shapes.control.topStart.toPx(Size(200f, small.value), density)
        assertEquals(
            small.value / 2f,
            atSmall,
            "at `controlHeightSmall` the pill rule and the cap have to agree, or " +
                "the scale has a step in it",
        )
        assertEquals(
            18f,
            atSmall,
            "and the value they agree on is the cap, so a control taller than " +
                "`small` continues from where `small` left off rather than jumping",
        )
    }

    @Test
    fun aToolbarStaysConcentricOnceTheCapBinds() {
        // A toolbar and its buttons used to be concentric for free: both were
        // uncapped capsules, so the outer radius was half the bar's height, the
        // inner was half a button's, and a button inset by the padding top and
        // bottom is shorter by exactly twice it — so the radii differed by
        // exactly the padding, whatever the numbers were.
        //
        // The cap ends that. A 56dp bar and a 44dp button both land on 18, and
        // two equal radii with 6dp between them is not concentric, it is a ring
        // that pinches at the corners. The bar has to derive its corner from its
        // children's instead of sharing a rule with them, which is what `outset`
        // is for.
        val padding = 6.dp
        val button = shapes.control.topStart.toPx(Size(200f, 44f), density)
        val bar = shapes.control.outset(padding).topStart.toPx(Size(212f, 56f), density)
        assertEquals(18f, button, "a 44dp button is above `small`, so it is capped")
        assertEquals(
            button + padding.value,
            bar,
            "a toolbar wrapping a $button-cornered button with ${padding.value}dp " +
                "of ring should be ${button + padding.value}, not $bar",
        )
    }

    @Test
    fun aSegmentedTrackStaysConcentricOnceTheCapBinds() {
        // The reporter's own example, in numbers. The track is a 44dp field and
        // the thumb is 6dp shorter on each side; `inset` is what keeps them
        // concentric, and it keeps working once the cap binds because it resolves
        // the base against the outer box before subtracting.
        val padding = 6.dp
        val track = shapes.field.topStart.toPx(Size(300f, 44f), density)
        val thumb = shapes.field.inset(padding).topStart.toPx(Size(100f, 32f), density)
        assertEquals(18f, track, "a 44dp track is above `small`, so it is capped")
        assertEquals(
            track - padding.value,
            thumb,
            "a thumb ${padding.value}dp inside a $track-cornered track should be " +
                "${track - padding.value}, not $thumb",
        )
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
        // Every rung is a squircle now, the small ones included, so `inset`
        // preserving the shape class is a claim about the whole scale rather
        // than about its top half.
        assertTrue(
            shapes.small.inset(2.dp) is SquircleShape,
            "the small rungs are squircles too now, and inset must not quietly " +
                "turn one back into a rounded rectangle",
        )
    }

    @Test
    fun insetOfAPercentCornerStaysAPercentUntilItIsResolved() {
        // The reason InsetCornerSize defers instead of subtracting up front: the
        // percentage is of a box nobody knows yet.
        //
        // The numbers here were 46 and 96 and they were wrong. A 100px box sitting
        // 4px inside a pill is inside a **108px** pill, whose radius is 54, so the
        // concentric answer is 50 — which is also half of 100, because a pill
        // inside a pill is still a pill. 46 is under-rounded by exactly the gap:
        // the base was being resolved on the inner box, where a proportional
        // corner has already lost the gap once before the subtraction takes it
        // again. Precisely the fault `outset`'s KDoc describes in the other
        // direction, which is why the two now share one shape of arithmetic.
        val pill = shapes.pill.inset(4.dp)
        assertEquals(50f, pill.topStart.toPx(Size(100f, 100f), density))
        assertEquals(100f, pill.topStart.toPx(Size(200f, 200f), density))
    }

    @Test
    fun insetResolvesAgainstTheBoxItIsNestedInRatherThanItsOwn() {
        // The mirror of `outsetResolvesAgainstTheBoxItWrapsRatherThanTheOneItDraws`,
        // and the case that had no test: a segmented control's thumb.
        //
        // The track is `field` on a 44px-tall box. That used to resolve to
        // `min(22, 26)` = 22 and this test asserted 22 − 6 = **16**; it now
        // resolves to `min(22, 18)` = 18 and the thumb lands on **12**, because
        // the cap arrived in the same round. Both numbers are concentric — the
        // one that matters is the *difference*, which is the gap either way.
        //
        // What the test is really pinning is unchanged, and it is the bug the
        // reporter found: resolved against its own 32px box the capsule answers
        // 16 *before* the gap comes off, so the thumb was drawn at 10 — six too
        // square on a six pixel gap. Their words were that the container is
        // almost pill-shaped and the indicator inside it is not.
        val gap = 6f
        val track = 18f
        val thumb = Size(200f, 32f)

        assertEquals(
            track - gap,
            Shapes().field.inset(6.dp).topStart.toPx(thumb, density),
            "a proportional corner has to resolve against the box it is nested " +
                "in, not against its own, or the gap is subtracted twice",
        )
    }

    @Test
    fun outsetAddsTheGapAndKeepsTheShapeItIsCalledOn() {
        // Measured on the *outer* box, which is how an outset shape is used: the
        // ring is the bigger rectangle, so the size it is handed is the grown one.
        val inner = Size(1000f, 1000f)
        val outer = Size(1012f, 1012f)

        assertEquals(
            shapes.large.topStart.toPx(inner, density) + 6f,
            shapes.large.outset(6.dp).topStart.toPx(outer, density),
        )
        assertTrue(
            shapes.large.outset(6.dp) is SquircleShape,
            "a ring around a squircle is a squircle — rebuilding it as a rounded " +
                "rectangle returns every smoothed corner in the library to a " +
                "circular arc at the one moment it is under a spotlight",
        )
    }

    @Test
    fun outsetResolvesAgainstTheBoxItWrapsRatherThanTheOneItDraws() {
        // The whole reason `outset` is not `inset` with the sign flipped, and the
        // case that decides it is a *proportional* corner.
        //
        // `pill` on a 52px-tall component is 26, and the ring 3px outside it wants
        // 29. Resolve it against the 58px ring instead and it answers 29 before
        // the gap is added, so the ring gets drawn at 32 — over-rounded by exactly
        // the gap.
        val grow = 3f
        val inner = Size(200f, 52f)
        val outer = Size(inner.width + grow * 2f, inner.height + grow * 2f)

        assertEquals(
            inner.height / 2f + grow,
            shapes.pill.outset(3.dp).topStart.toPx(outer, density),
            "a proportional corner has to resolve against the component it wraps, " +
                "not against the ring it is drawn on",
        )

        // And the case that would have let it through, kept so the two are read
        // together. A capped capsule reads the same 26 on both boxes once it is at
        // its cap, so every text field in the library would look right whichever
        // box was used — which is why "it looks fine on a field" is not evidence.
        assertEquals(
            26f + grow,
            SquircleShape(CapsuleCornerSize(cap = 26.dp))
                .outset(3.dp)
                .topStart
                .toPx(outer, density),
        )
    }

    @Test
    fun outsetUndoesInset() {
        // Concentricity is symmetric, so the two have to agree on the same pair of
        // boxes: inset the outer shape by the gap and outset the inner one by it,
        // and the same radius has to come back.
        val gap = 8.dp
        val outer = Size(400f, 400f)
        val inner = Size(400f - 16f, 400f - 16f)

        assertEquals(
            shapes.large.inset(gap).topStart.toPx(inner, density),
            shapes.large.topStart.toPx(inner, density) - 8f,
        )
        assertEquals(
            shapes.large.inset(gap).outset(gap).topStart.toPx(outer, density),
            shapes.large.topStart.toPx(inner, density),
        )

        // And on the corner kinds that would have caught the fault. A fixed rung
        // is immune to it by construction — 28dp is 28dp on any box — so the
        // round trip above held for years while the proportional one did not.
        // These two are the test that did not exist.
        assertEquals(
            shapes.pill.topStart.toPx(outer, density),
            shapes.pill.inset(gap).outset(gap).topStart.toPx(outer, density),
            "a pill inset and then outset by the same gap is the pill again",
        )
        val capped = SquircleShape(CapsuleCornerSize(cap = 26.dp))
        assertEquals(
            capped.topStart.toPx(outer, density),
            capped.inset(gap).outset(gap).topStart.toPx(outer, density),
            "and so is a capped capsule, at any box either side of its cap",
        )
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
    fun aCornerSaturatedOnBothEdgesLandsOnTheCircle() {
        // A square box at capsule radius: every corner has spent its whole share
        // of *both* its edges on the arc, so there is nothing left anywhere to
        // ease onto and the shape is a circle. Without that the blend handles
        // have nowhere to go and the path folds through itself.
        //
        // This is also the whole of the exception list. An `IconButton`, an
        // `Avatar`, a status dot and a radio ring are square boxes at capsule
        // radius, so they stay true circles by construction rather than by being
        // named somewhere as things not to convert.
        val size = Size(100f, 100f)
        val circle = area(walk(pathOf(RoundedCornerShape(50.dp), size)))
        val saturated = area(walk(pathOf(SquircleShape(50.dp), size)))

        assertTrue(
            abs(saturated - circle) < 0.005f * circle,
            "a corner with no room on either edge must land on the circle: " +
                "$saturated against $circle",
        )
    }

    /**
     * A capsule is a squircle, and for a long time it silently was not.
     *
     * `Shapes.control` is half the shorter side, so on any button, chip, tag,
     * toolbar or tab the two corners at one end meet in the middle of that end
     * with nothing between them — the short edge is saturated, exactly and
     * always. The smoothing used to be capped by the *tighter* of a corner's two
     * edges, so that one saturated edge dropped the smoothing on the long edge
     * too, and every control in the library drew a plain circular arc while
     * naming `SquircleShape` and paying `Outline.Generic` for it.
     *
     * Asserted as a *shape* difference rather than an area one, because the area
     * barely moves: the extent is unchanged, and what changes is that the
     * curvature no longer steps from the arc straight onto the edge. Measured
     * where that step used to be — a few degrees off the long edge — the squircle
     * now sits measurably further from the corner point than the arc does.
     */
    @Test
    fun aCappedControlSmoothsOnBothEdgesBecauseItIsNoLongerSaturated() {
        // A consequence of the cap worth having a number for, because it runs the
        // opposite way to the intuition that a squarer corner is a plainer one.
        //
        // An uncapped control was saturated on its short edge by definition —
        // half the height, so the two corners at one end met in the middle with
        // nothing between them, and the smoothing had room on the long edge only.
        // A capped one is not: 18dp on a 52dp box leaves 8dp of straight edge at
        // each end. So the blend now has room on *both* edges, and a capped
        // control is more of a squircle than the uncapped one it replaced, not
        // less.
        val size = Size(200f, 52f)

        fun peak(shape: CornerBasedShape, radius: Float): Float =
            (1..44).maxOf { degrees ->
                distanceFromCornerAt(shape, size, degrees.toFloat()) -
                    distanceFromCornerAt(RoundedCornerShape(radius.dp), size, degrees.toFloat())
            }

        // The number `tokens.md` quotes. Pinned here so it cannot go stale the
        // way the 1.9px it replaced did.
        val capped = peak(Shapes().control, 18f)
        assertEquals(
            1.32f,
            capped,
            0.02f,
            "a capped control on a 200x52 box should sit ~1.32px outside a plain " +
                "18dp arc at its widest, but measured $capped",
        )

        // And the property that number is evidence *of*, which is the durable
        // half. The uncapped capsule on the same box is 26dp and saturated; this
        // one is 18dp and is not. Their peak deviations differ — 1.9px against
        // 1.32 — but as a *fraction of the radius* they are the same shape to
        // three decimal places. So squaring the family off does not flatten it:
        // a control is exactly as much of a squircle as it was, at a smaller
        // radius.
        val uncapped = peak(SquircleShape(CapsuleCornerSize()), 26f)
        assertTrue(
            abs(capped / 18f - uncapped / 26f) < 0.005f,
            "the blend should be a fixed fraction of the radius either side of " +
                "saturation, but capped gave ${capped / 18f} and uncapped " +
                "${uncapped / 26f}",
        )
    }

    @Test
    fun aCapsuleSmoothsAlongTheEdgeThatHasRoom() {
        // A button: 200 wide, 52 tall, corner radius 26 — saturated vertically,
        // 100px of top edge to play with horizontally.
        val size = Size(200f, 52f)
        val capsule = SquircleShape(CapsuleCornerSize())
        val arc = RoundedCornerShape(percent = 50)

        // Close to the long edge, which is where a blend does its work: it has
        // to *end* on that edge, so the two outlines converge as the ray swings
        // toward the diagonal and the gap is widest a degree or two off it.
        val deviations = (1..5).map { degrees ->
            distanceFromCornerAt(capsule, size, degrees.toFloat()) -
                distanceFromCornerAt(arc, size, degrees.toFloat())
        }

        // For scale: a `Card` is not saturated on either edge and smooths freely,
        // and its own peak deviation on a 300x200 box is 1.59px. A capsule that
        // is doing the same work should be in the same country, not at zero.
        assertTrue(
            deviations.all { it > 0f } && deviations.max() > 1.2f,
            "a capsule has to ease into the edge it has room on, but against the " +
                "plain arc it deviated by at most ${deviations.max()}px " +
                "($deviations) — near zero means the smoothing was thrown away " +
                "because the *other* edge was full",
        )

        // Never *inside* the arc, anywhere around the corner. A squircle takes
        // less off a corner than a circle of the same radius does; a blend that
        // dipped under it would be cutting the control's own end off rather than
        // easing it.
        val insideBy = (1..44).map { degrees ->
            distanceFromCornerAt(arc, size, degrees.toFloat()) -
                distanceFromCornerAt(capsule, size, degrees.toFloat())
        }.max()
        assertTrue(
            insideBy < 0.05f,
            "the capsule dipped ${insideBy}px inside the plain arc somewhere " +
                "across the corner, which is the end being cut off rather than " +
                "eased",
        )

        // And the end is still an end: the extent has not moved.
        assertTrue(
            abs(area(walk(pathOf(capsule, size))) - area(walk(pathOf(arc, size)))) <
                0.01f * area(walk(pathOf(arc, size))),
            "the ends changed size. Smoothing the long edge must not make the " +
                "control shorter or rounder — only continuous.",
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
