package io.kontour.ui.components.selection

import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A squashed thumb stays inside the arc it is pressed against.
 *
 * Reported of the switch: the ellipse should remain concentric with its container.
 * It did not — measured, the 2dp gap between thumb and track pinched to 1.17dp at
 * full squash, all of it off the centre row, so nothing that measured a width
 * could see it.
 *
 * The rendered consequence is asserted in `SwitchGeometryTest`: a squashed thumb
 * comes out shorter than the circle it rests as. What is asserted here is *why
 * that number* — the tangency condition, which is arithmetic and does not need a
 * rasteriser and would be measured to two significant figures at best by one.
 */
class SquashedThumbGeometryTest {

    /** The switch, in dp: a 24dp thumb, so a 12dp resting radius. */
    private val resting = 12f

    /**
     * The ellipse touches the arc rather than crossing it, at every depth.
     *
     * An ellipse with semi-axes `a` across and `b` up has a radius of curvature of
     * `b²/a` at the vertex on the wall. Equal to the arc's own radius, the two are
     * tangent; larger, the ellipse is flatter than the arc and crosses it, which is
     * the defect. So this asserts the curvature directly rather than the formula
     * that produces it — the formula is what is under test.
     */
    @Test
    fun theCurvatureAtTheWallMatchesTheArcItIsPressedInto() {
        for (halfWidth in listOf(11.5f, 10.5f, 9.75f, 9f)) {
            val b = squashedHalfHeight(resting, halfWidth)
            val curvature = b * b / halfWidth
            assertEquals(
                resting, curvature, 1e-3f,
                "at a half-width of ${halfWidth}dp the ellipse is ${b}dp tall, " +
                    "which curves at ${curvature}dp where it meets a ${resting}dp " +
                    "arc. Flatter than the arc and it crosses it; the thumb then " +
                    "overhangs the track at its top and bottom while looking " +
                    "untouched across the middle.",
            )
        }
    }

    /**
     * At rest nothing moves, so there is no special case and no seam.
     *
     * The shape family runs capsule → circle → ellipse as the thumb narrows, and
     * the three have to meet exactly or a thumb nobody is touching would jump the
     * moment a finger arrived.
     */
    @Test
    fun anUnsquashedThumbKeepsItsFullHeight() {
        assertEquals(
            resting, squashedHalfHeight(resting, resting), 1e-4f,
            "a thumb as wide as it is tall came out a different height",
        )
    }

    /**
     * And it only ever gets shorter, never taller.
     *
     * The guard on the branch that uses this. `√(r·a) > r` whenever `a > r`, so
     * handed a *stretched* thumb the rule would grow it vertically — a capsule
     * getting taller as it lengthens, which is the opposite of a squash. The caller
     * takes the capsule branch above that point; this records why the branch is
     * where it is rather than leaving it to look arbitrary.
     */
    @Test
    fun narrowerIsAlwaysShorterAndWiderWouldBeTaller() {
        val squashed = squashedHalfHeight(resting, halfWidth = 9f)
        assertTrue(
            squashed < resting,
            "a thumb narrowed to 18dp came out ${squashed * 2}dp tall against " +
                "${resting * 2}dp at rest",
        )
        val stretched = squashedHalfHeight(resting, halfWidth = 18f)
        assertTrue(
            stretched > resting,
            "the rule is supposed to grow past the resting height for a stretched " +
                "thumb — ${stretched}dp against ${resting}dp — which is exactly " +
                "why `squashedCapsule` only uses it below the circle. If this ever " +
                "stops being true the branch guarding it is dead code.",
        )
    }

    /**
     * The measured numbers the switch's report was about, so they are written down.
     *
     * Full squash on a switch is a quarter off a 24dp thumb: 18dp across. The
     * height that keeps it tangent is 20.78dp, which is the "ever so slightly
     * shorter" the fix was asked for — 3.22dp.
     */
    @Test
    fun theSwitchsFullSquashIsTheNumberInTheReport() {
        assertEquals(
            sqrt(12f * 9f) * 2f, squashedHalfHeight(resting, halfWidth = 9f) * 2f, 1e-4f,
        )
        assertEquals(
            20.78f, squashedHalfHeight(resting, halfWidth = 9f) * 2f, 0.01f,
            "full squash on a switch should draw an 18 x 20.78dp ellipse",
        )
    }
}
