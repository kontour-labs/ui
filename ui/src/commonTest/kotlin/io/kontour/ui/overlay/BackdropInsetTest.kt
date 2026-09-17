package io.kontour.ui.overlay

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The frame a receding screen leaves, on all four edges, equal.
 *
 * ### Two reports, and the second one settled it
 *
 * A uniform scale insets a rectangle by a fraction of each *axis*, so one number
 * gives two different margins on any screen that is not square. The only question
 * it leaves is where the difference is spent, and both answers were reported.
 *
 * Spending it at the bottom — on the argument that the bottom is the edge a sheet
 * covers anyway — gave a phone a 12dp top and a 25dp bottom, and then a
 * navigation bar ate the bottom entirely. Adding a second constraint to keep the
 * inset above the bar bought that back and pushed the difference out to the
 * sides, at about 16.6dp against a 12dp top. The second report was the plain
 * statement that it should be uniform everywhere, always.
 *
 * So it is not spent anywhere: [backdropFit] scales each axis separately and
 * every gap is the inset. What that costs is a 3.5% difference between the two
 * scales on a phone — a circle on the receded page is very slightly an ellipse —
 * and [theTwoScalesStayCloseEnoughToReadAsOneScreen] is where that is pinned, so
 * a future reader meets it as a decision rather than as a surprise.
 *
 * ### Arithmetic rather than pixels
 *
 * A JVM `ImageComposeScene` has no system bars, so the opaque-bottom case cannot
 * be photographed here — which is the same reason `BackdropBlurTest` and
 * `ReducedMotionAmplitudeTest` are untouched by this and pass unmodified. Those
 * two are the evidence that nothing moves where no bar is reported; this is the
 * evidence about what happens where one is.
 */
class BackdropInsetTest {

    @Test
    fun everyGapIsTheInsetWithNothingDockedAtTheBottom() {
        val fit = backdropFit(Phone.width, Phone.height, Inset, opaqueBottomPx = 0f)

        for ((edge, gap) in fit.gaps(Phone)) {
            assertNear(
                Inset, gap,
                "the $edge gap is ${gap}px where the inset is ${Inset}px. All four " +
                    "are the inset now — that is the whole of this change, and a " +
                    "single scale is what could not do it.",
            )
        }
    }

    @Test
    fun everyGapIsStillTheInsetAboveAThreeButtonBar() {
        val fit = backdropFit(Phone.width, Phone.height, Inset, opaqueBottomPx = NavBar)
        val gaps = fit.gaps(Phone, opaqueBottom = NavBar)

        for ((edge, gap) in gaps) {
            assertNear(
                Inset, gap,
                "with a ${NavBar}px bar painted at the bottom, the $edge gap is " +
                    "${gap}px against a ${Inset}px inset. A gap underneath something " +
                    "opaque is not a gap, so the page is fitted to the window less " +
                    "the bar and lifted by half of it — and all four edges come out " +
                    "on the inset again, measured from the room it actually has.",
            )
        }
    }

    @Test
    fun anOpaqueBarShortensThePageRatherThanMovingItOffTheTop() {
        // The failure the lift invites: translating by the whole bar instead of
        // half of it puts the top edge at `inset - bar`, which on any phone is
        // off the screen. Half is what keeps the page centred on the room.
        val fit = backdropFit(Phone.width, Phone.height, Inset, opaqueBottomPx = NavBar)
        val plain = backdropFit(Phone.width, Phone.height, Inset, opaqueBottomPx = 0f)

        assertTrue(
            fit.scaleY < plain.scaleY,
            "a bar at the bottom left the vertical scale at ${fit.scaleY}, the same " +
                "as with no bar. The page has to get *shorter* to keep its gaps — " +
                "there is less room — and a lift alone would only move the problem " +
                "to the top edge.",
        )
        assertNear(
            plain.scaleX, fit.scaleX,
            "the bar changed the horizontal scale, from ${plain.scaleX} to " +
                "${fit.scaleX}. Nothing about the bottom edge should touch the sides.",
        )
    }

    @Test
    fun theTwoScalesStayCloseEnoughToReadAsOneScreen() {
        // What the change costs, pinned rather than left to be discovered.
        val fit = backdropFit(Phone.width, Phone.height, Inset, opaqueBottomPx = 0f)
        val distortion = abs(fit.scaleX - fit.scaleY) / fit.scaleX

        assertTrue(
            distortion < MaxDistortion,
            "the two scales differ by ${distortion * 100}%, which is past the " +
                "${MaxDistortion * 100}% a page behind a scrim can carry without " +
                "reading as the wrong shape. Equal gaps need two scales; a *lot* " +
                "of difference between them means the inset has grown past what a " +
                "frame should be.",
        )
    }

    @Test
    fun aWindowSmallerThanItsOwnFrameDoesNotInvert() {
        // A degenerate window rather than a real one: the arithmetic has to clamp
        // rather than hand back a negative scale, which would mirror the page.
        val tiny = backdropFit(width = 20f, height = 20f, insetPx = Inset, opaqueBottomPx = 0f)

        assertTrue(
            tiny.scaleX >= 0f && tiny.scaleY >= 0f,
            "a window narrower than twice the inset produced scales " +
                "${tiny.scaleX} and ${tiny.scaleY}. Negative is a mirrored page.",
        )
    }

    /** The four gaps this fit leaves, in pixels, against a window of [size]. */
    private fun BackdropFit.gaps(
        size: Size,
        opaqueBottom: Float = 0f,
    ): List<Pair<String, Float>> {
        val drawnWidth = size.width * scaleX
        val drawnHeight = size.height * scaleY
        val left = (size.width - drawnWidth) / 2f
        // Centred, then lifted. The bottom is measured to the top of whatever is
        // painted down there rather than to the window's edge, which is the whole
        // point of separating an opaque inset from a reserved one.
        val top = (size.height - drawnHeight) / 2f - shiftY
        return listOf(
            "left" to left,
            "right" to left,
            "top" to top,
            "bottom" to (size.height - opaqueBottom) - (top + drawnHeight),
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

        /**
         * How far apart the two scales may be before the page reads as distorted.
         *
         * A phone at a 12dp inset lands at about 3.5%, so this is that with room
         * to move rather than a threshold anybody tuned. It is here to catch an
         * inset grown large enough that the frame stops being a frame.
         */
        const val MaxDistortion = 0.06f

        data class Size(val width: Float, val height: Float)
    }
}
