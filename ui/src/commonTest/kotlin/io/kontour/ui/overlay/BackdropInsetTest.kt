package io.kontour.ui.overlay

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The frame a receding screen leaves, on all four edges.
 *
 * Three of them were already right. [backdropScale] scales uniformly from the
 * width so both sides land on the inset, and [backdropShift] moves the content up
 * so the top does too — leaving the surplus at the bottom, on the argument that
 * the bottom is the edge a sheet is covering anyway.
 *
 * That argument holds until something else is down there. Reported from an
 * Android phone: the inset "still isn't uniform, and it's worse with three-button
 * navigation enabled". It is the same defect either way and the bar makes it
 * total — on a 390x844 phone at 12dp the surplus is about 40dp, which a 48dp
 * navigation bar swallows whole. There is no bottom frame at all, and a screen
 * with a frame on three sides does not read as a screen stepping back; it reads
 * as the page sliding underneath something.
 *
 * ### Arithmetic rather than pixels
 *
 * A JVM `ImageComposeScene` has no system bars, so the inset it would report is
 * zero and the case cannot be photographed here — which is the same reason
 * `BackdropBlurTest` and `ReducedMotionAmplitudeTest` are unaffected by this
 * change and pass unmodified. Those two are the evidence that the new constraint
 * binds nowhere it is not needed; this is the evidence that it binds where it is.
 */
class BackdropInsetTest {

    @Test
    fun withNothingDockedTheSidesLandExactlyOnTheInset() {
        // A gesture-navigation phone: a few dp at the bottom, not enough to bind.
        val scale = backdropScale(
            width = Phone.width,
            height = Phone.height,
            insetPx = Inset,
            bottomInsetPx = 0f,
        )
        val sideMargin = (Phone.width - Phone.width * scale) / 2f

        assertNear(
            Inset, sideMargin,
            "the side margin is ${sideMargin}px where the inset is ${Inset}px. The " +
                "width-derived scale is what puts it exactly there, and nothing " +
                "about the bottom edge should move it when the bottom edge is clear.",
        )
    }

    @Test
    fun aThreeButtonBarLeavesTheInsetBelowIt() {
        val scale = backdropScale(
            width = Phone.width,
            height = Phone.height,
            insetPx = Inset,
            bottomInsetPx = NavBar,
        )
        val shift = backdropShift(Phone.height, scale, Inset)

        // Where the content's bottom edge ends up: the scaled height, centred,
        // then moved up by the shift.
        val bottomEdge = Phone.height - (Phone.height - Phone.height * scale) / 2f - shift
        val clearance = Phone.height - NavBar - bottomEdge

        assertTrue(
            clearance >= Inset - Tolerance,
            "a ${NavBar}px navigation bar left ${clearance}px between the content's " +
                "bottom edge and the top of the bar, where the inset is ${Inset}px. " +
                "That is the report: the surplus a width-derived scale leaves at the " +
                "bottom is swallowed by the bar, and the recede stops reading as a " +
                "screen stepping back.",
        )
    }

    @Test
    fun theTopStillLandsExactlyOnTheInsetWhateverIsAtTheBottom() {
        // The invariant that must survive the second constraint. `backdropShift`
        // spends the slack on the top first, and a smaller scale means *more*
        // slack to spend — so this would only break if the shift were clamped.
        for (bottom in listOf(0f, NavBar, NavBar * 2f)) {
            val scale = backdropScale(Phone.width, Phone.height, Inset, bottom)
            val shift = backdropShift(Phone.height, scale, Inset)
            val topEdge = (Phone.height - Phone.height * scale) / 2f - shift

            assertNear(
                Inset, topEdge,
                "with ${bottom}px docked at the bottom the top margin is ${topEdge}px, " +
                    "where the inset is ${Inset}px",
            )
        }
    }

    @Test
    fun theSidesGrowRatherThanTheScaleDistorting() {
        // What the fix costs, pinned so a future reader does not discover it as a
        // surprise. Taking the tighter of two uniform scales means the sides end
        // up wider than the inset on a phone with a bar — the alternative is
        // scaling each axis separately, which turns every avatar on the receded
        // screen into a slight ellipse.
        val scale = backdropScale(Phone.width, Phone.height, Inset, NavBar)
        val sideMargin = (Phone.width - Phone.width * scale) / 2f

        assertTrue(
            sideMargin > Inset,
            "the side margin is ${sideMargin}px, which is not more than the " +
                "${Inset}px inset — so the bottom constraint did not bind and this " +
                "test is measuring the wrong thing",
        )
        assertTrue(
            sideMargin < Inset * 2f,
            "the side margin grew to ${sideMargin}px against a ${Inset}px inset. " +
                "Wider than the inset is the accepted price of a uniform scale; " +
                "twice it would be a different frame rather than a slightly looser " +
                "one.",
        )
    }

    @Test
    fun aWindowWiderThanItIsTallIsUnaffected() {
        // A landscape window has no vertical surplus to begin with — the slack
        // from a width-derived scale is smaller than the inset — so the shift
        // clamps at zero and the bottom term cannot make anything worse.
        val landscape = backdropScale(1000f, 400f, Inset, NavBar)
        val plain = backdropScale(1000f, 400f, Inset, 0f)

        assertTrue(
            landscape <= plain,
            "the bottom constraint made a landscape window scale *up*, which it " +
                "can only do by having the wrong sign",
        )
    }

    /**
     * Within half a pixel, with both numbers already in [message].
     *
     * Not `assertEquals` on rounded integers, which was the first draft and was
     * wrong in the way this file is about: 23.999985 truncates to 23 and fails
     * against an expected 24 after the tolerance check has already passed it.
     */
    private fun assertNear(expected: Float, actual: Float, message: String) {
        assertTrue(abs(expected - actual) <= Tolerance, message)
    }

    private companion object {
        /** A 390x844dp phone at density 2, which is the device in the report. */
        val Phone = Size(780f, 1688f)

        /** `backdropInset`, 12dp at density 2. */
        const val Inset = 24f

        /** Android's three-button navigation bar, 48dp at density 2. */
        const val NavBar = 96f

        /** Half a pixel, for a chain of float divisions. */
        const val Tolerance = 0.5f

        data class Size(val width: Float, val height: Float)
    }
}
