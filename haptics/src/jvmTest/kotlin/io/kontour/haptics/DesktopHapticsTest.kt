package io.kontour.haptics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * A desktop tries its trackpad only where there is one to try, and is silent —
 * not broken — everywhere else. This suite runs on Linux, so the Mac path itself
 * is the one thing it cannot exercise; the route that leads there is pure, and is.
 */
class DesktopHapticsTest {

    @Test
    fun onlyAMacOnJava22TriesTheTrackpad() {
        assertEquals(DesktopRoute.MacTrackpad, desktopRoute("Mac OS X", 22))
        assertEquals(DesktopRoute.MacTrackpad, desktopRoute("Mac OS X", 25))
        assertEquals(DesktopRoute.None, desktopRoute("Mac OS X", 21), "the foreign-function API is final from 22")
        assertEquals(DesktopRoute.None, desktopRoute("Mac OS X", 11))
        assertEquals(DesktopRoute.None, desktopRoute("Linux", 25))
        assertEquals(DesktopRoute.None, desktopRoute("Windows 11", 25))
        assertEquals(DesktopRoute.None, desktopRoute(null, 25))
    }

    /** It is made by name; a rename that forgot the string would silently lose the trackpad. */
    @Test
    fun theTrackpadIsLoadedByItsOwnName() {
        assertEquals(MacTrackpadFfm::class.qualifiedName, TrackpadClass)
    }

    @Test
    fun hereItIsSilentAndSaysWhy() {
        val haptics = Haptics()
        assertEquals(HapticCapability.Richness.None, haptics.capability.richness)
        assertEquals(1, haptics.capability.details.size)
        haptics.play(HapticEffect.Click())
        assertFalse(haptics.startRumble().isActive)
        haptics.close()
        haptics.close()
        haptics.play(HapticEffect.Click())
    }

    @Test
    fun aTrackpadPlaysEachPatternAtItsTime() {
        val played = mutableListOf<MacPattern>()
        val haptics = DesktopHaptics({ played += it }, "test")
        haptics.play(HapticEffect.Selection())
        haptics.play(HapticEffect.Threshold(activate = true))
        assertEquals(listOf(MacPattern.Alignment, MacPattern.LevelChange), played)
        haptics.close()
    }
}
