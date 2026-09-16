package io.kontour.ui.theme

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * [kontourShapes] with no arguments is [Shapes], and that is the point.
 *
 * Sibling to [ShapeScaleTest], which asserts what the scale *is* — evenly
 * stepped, actually curved, concentric under `inset`. This asserts that the
 * factory hands back that same scale, and that its four arguments each move
 * something.
 *
 * A factory that replaces a set of defaults is only worth having if it *is*
 * those defaults. The first assertion below reads as a tautology and is the
 * entire proof of the seam: every number in the ladder, the capsule cap, the
 * derived rungs and the two sheets have to come out where the `data class` put
 * them, or the factory has quietly opened a second, disagreeing description of
 * the same scale.
 *
 * The rest check that the arguments actually move something, because an
 * equality that passes because both sides ignore their inputs proves nothing.
 */
class ShapeFactoryTest {

    @Test
    fun theFactoryReproducesTheDefaultsExactly() {
        assertEquals(
            Shapes(), kontourShapes(),
            "kontourShapes() and Shapes() disagree. One of them has been edited " +
                "without the other, and every consumer who took the factory's " +
                "word for the house scale is now drawing a different one.",
        )
    }

    @Test
    fun theLadderStartsAndStepsWhereItIsTold() {
        val tight = kontourShapes(extraSmall = 6.dp, step = 2.dp)
        assertEquals(SquircleShape(6.dp), tight.extraSmall)
        assertEquals(SquircleShape(8.dp), tight.small)
        assertEquals(SquircleShape(10.dp), tight.medium)
        assertEquals(SquircleShape(12.dp), tight.large)
        assertEquals(SquircleShape(14.dp), tight.extraLarge)

        // And the derived tokens follow the ladder rather than being frozen at
        // the defaults — the failure a `data class` default has by construction,
        // and the reason this factory exists.
        assertEquals(tight.medium, tight.container, "container should be the medium rung")
        assertEquals(tight.large, tight.panel, "panel should be the large rung")
        assertNotEquals(
            Shapes().sheet, tight.sheet,
            "sheet is still the 34dp default, so it was restated rather than derived",
        )
    }

    /**
     * `smoothing = 0f` is the one-line answer to "no continuous corners".
     *
     * It was thirteen lines before, which is why GTurbo's first draft was
     * mistaken for a rounded-rectangle design: restating the scale to change one
     * property loses the property being changed in the noise of the twelve
     * fields that did not.
     *
     * **`pill` used to be excluded from this and no longer is.** It was a
     * `RoundedCornerShape(percent = 50)`, so a theme asking for no continuous
     * corners got them everywhere except on a tag, a skeleton's line, a nav
     * indicator and a day cell's range caps — one token's worth of a different
     * design system, in the places least able to hide it. Now it is the same
     * `CapsuleCornerSize` as `capsule` with the cap taken off, which was always
     * the distinction the two were for.
     */
    @Test
    fun smoothingReachesEveryRungIncludingThePill() {
        val plain = kontourShapes(smoothing = 0f)
        val rungs = listOf(
            plain.extraSmall, plain.small, plain.medium, plain.large, plain.extraLarge, plain.pill,
        )
        val unsmoothed = rungs.filterIsInstance<SquircleShape>().count { it.smoothing == 0f }
        assertEquals(
            rungs.size, unsmoothed,
            "$unsmoothed of ${rungs.size} rungs took smoothing = 0f; a scale with " +
                "two smoothings in it is a scale whose corners do not match, " +
                "which is what SquircleShape.DefaultSmoothing warns about",
        )
    }

    @Test
    fun theCapsuleCapIsOneNumberInOnePlace() {
        val capped = kontourShapes(capsuleCap = 10.dp)
        assertEquals(SquircleShape(CapsuleCornerSize(cap = 10.dp)), capped.capsule)
        assertEquals(capped.capsule, capped.control, "control is the capsule, named for what presses it")
        assertEquals(capped.capsule, capped.field, "a field caps at the same place a control does")
    }

    /**
     * The cap is the *only* thing between `pill` and `capsule`, at every cap.
     *
     * Reported against GTurbo, which sets `capsuleCap = 10.dp`: a switch stopped
     * being concentric. A switch is a 24dp thumb with 2dp of padding inside a
     * 28dp track, and the thumb is a `drawRoundRect` at half its own height — a
     * number, because a draw call has no token to consult. So the track has to
     * be uncapped or the pair comes apart the moment a theme lowers the cap: at
     * 10dp the track resolved to `min(14, 10)` against a thumb still at 12,
     * where concentricity wants the track to be the thumb plus its padding.
     *
     * Asserted here as an identity on the tokens rather than on the switch,
     * because the switch is downstream of it and this is where it can be wrong.
     */
    @Test
    fun aLowCapSquaresTheCapsuleAndLeavesThePillAlone() {
        val capped = kontourShapes(capsuleCap = 10.dp)
        val track = Size(48f, 28f)
        val density = Density(1f)

        assertEquals(
            10f, capped.capsule.topStart.toPx(track, density),
            "the cap is doing its job — a 28dp box wants 14 and is held at 10",
        )
        assertEquals(
            14f, capped.pill.topStart.toPx(track, density),
            "and `pill` is the same rule without it, which is the whole of the " +
                "difference between the two and the reason a switch's track " +
                "names this one",
        )
        assertEquals(
            capped.pill.topStart.toPx(track, density),
            24f / 2f + 2f,
            "concentric with a 24dp thumb drawn at half its own height, inside " +
                "2dp of padding — the arithmetic the report was about",
        )
    }
}
