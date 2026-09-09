package io.kontour.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

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
     */
    @Test
    fun smoothingReachesEveryRungAndLeavesThePillAlone() {
        val plain = kontourShapes(smoothing = 0f)
        val rungs = listOf(plain.extraSmall, plain.small, plain.medium, plain.large, plain.extraLarge)
        val unsmoothed = rungs.filterIsInstance<SquircleShape>().count { it.smoothing == 0f }
        assertEquals(
            rungs.size, unsmoothed,
            "$unsmoothed of ${rungs.size} rungs took smoothing = 0f; a scale with " +
                "two smoothings in it is a scale whose corners do not match, " +
                "which is what SquircleShape.DefaultSmoothing warns about",
        )
        assertTrue(
            plain.pill is RoundedCornerShape,
            "pill is a true circular arc by definition — an avatar, a status dot, " +
                "a radio ring — and smoothing has nothing to say about it",
        )
    }

    @Test
    fun theCapsuleCapIsOneNumberInOnePlace() {
        val capped = kontourShapes(capsuleCap = 10.dp)
        assertEquals(SquircleShape(CapsuleCornerSize(cap = 10.dp)), capped.capsule)
        assertEquals(capped.capsule, capped.control, "control is the capsule, named for what presses it")
        assertEquals(capped.capsule, capped.field, "a field caps at the same place a control does")
    }
}
