package io.kontour.ui.components.action

import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

/**
 * When an item reads as arrived, which is when a FAB menu's haptic for it plays:
 * the first time its spring reaches its place, not the end of its settle.
 */
class LandingTimeTest {

    @Test
    fun theThemeSpringArrivesWellBeforeItSettles() {
        // `springDefault`: damping 0.85, stiffness 700.
        val landing = landingTime(spring(dampingRatio = 0.85f, stiffness = 700f))
        assertTrue(landing in 100.milliseconds..300.milliseconds, "springDefault lands at $landing")
    }

    @Test
    fun aTweenLandsAtItsEnd() {
        val landing = landingTime(tween(120))
        assertTrue(landing in 100.milliseconds..120.milliseconds, "a 120ms tween lands at $landing")
    }

    @Test
    fun aStifferSpringLandsSooner() {
        assertTrue(landingTime(spring(0.9f, 1400f)) < landingTime(spring(0.85f, 700f)))
    }
}
