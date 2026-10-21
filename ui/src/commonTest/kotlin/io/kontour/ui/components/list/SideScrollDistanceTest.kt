package io.kontour.ui.components.list

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * How far one scroll event moves a swipe row.
 *
 * Reported as "wayyyy too sensitive": on the web a scroll's delta is in CSS
 * pixels, and the row took each of them as a notch of a wheel at 24dp a notch — so
 * one trackpad event of ten pixels threw the row 240dp, past every action it had.
 * The desktop's notches were right and stay right.
 *
 * At a density of 2, where 24dp is 48 pixels.
 */
class SideScrollDistanceTest {

    private val step = 48f
    private val density = 2f

    @Test
    fun aNotchMovesTheRowOneStep() {
        assertEquals(48f, sideScrollDistance(1f, step, density, inPixels = false))
        assertEquals(-24f, sideScrollDistance(-0.5f, step, density, inPixels = false))
    }

    @Test
    fun aBrowsersPixelsFollowTheTrackpadAtOneDpEach() {
        // Ten CSS pixels is ten dp, twenty pixels at this density — not 480.
        assertEquals(20f, sideScrollDistance(10f, step, density, inPixels = true))
        assertEquals(-6f, sideScrollDistance(-3f, step, density, inPixels = true))
    }

    @Test
    fun aMouseWheelsClickInABrowserMovesNoFurtherThanANotch() {
        // A hundred pixels, which is what a browser reports for one click.
        assertEquals(48f, sideScrollDistance(100f, step, density, inPixels = true))
        assertEquals(-48f, sideScrollDistance(-120f, step, density, inPixels = true))
    }

    /**
     * A swipe row takes a browser's pixels at its trackpad gain, as it takes a
     * Mac's pan: pixel for pixel was *"really sensitive"*, 2026-10-21. A wheel's
     * click is still a whole step, and a notch is not pixels at all.
     */
    @Test
    fun aGainSlowsABrowsersPixelsButNotAWheel() {
        assertEquals(12f, sideScrollDistance(10f, step, density, inPixels = true, gain = 0.6f), 0.001f)
        assertEquals(48f, sideScrollDistance(100f, step, density, inPixels = true, gain = 0.6f))
        assertEquals(48f, sideScrollDistance(1f, step, density, inPixels = false, gain = 0.6f))
    }
}
