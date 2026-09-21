package io.kontour.ui.interaction

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import io.kontour.ui.platform.recordedFeels
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * An intent reaches the platform as the feel the policy assigned it.
 *
 * `FeedbackFeelTest` holds the table and this holds the wiring, which is a separate
 * claim: the dispatcher the theme installs has to turn an intent into a feel and
 * hand *that* across the seam. Before the split it handed a constant, chosen per
 * intent inside each platform's own file, and the whole defect was that nothing in
 * between could see the result.
 *
 * The JVM is the only platform whose seam can be run here — Android has no SDK in
 * this repository and iOS has no test source set — which is why its actual records
 * what it is asked for. The same bargain `AppearanceReportTest` strikes.
 */
class FeedbackWiringTest {

    @Test
    fun theThemesDispatcherHandsThePlatformAFeelRatherThanAConstant() {
        recordedFeels.clear()
        val scene = ImageComposeScene(width = 100, height = 100, density = Density(1f)) {
            KontourTheme {
                val feedback = LocalFeedback.current
                LaunchedEffect(Unit) {
                    feedback.perform(FeedbackIntent.Tick)
                    feedback.perform(FeedbackIntent.Tap)
                    feedback.perform(FeedbackIntent.LongPress)
                    feedback.perform(FeedbackIntent.Warn)
                }
                Box(Modifier.fillMaxSize())
            }
        }
        try {
            repeat(4) { scene.render(16_000_000L * it).close() }
            assertEquals(
                listOf(
                    FeedbackFeel.Light,
                    FeedbackFeel.Medium,
                    FeedbackFeel.Heavy,
                    FeedbackFeel.Danger,
                ),
                recordedFeels.toList(),
                "a wheel row and a switch answering a tap are supposed to arrive " +
                    "at the platform as different weights. Reported from a phone " +
                    "as everything feeling heavy, and on Android below 14 they " +
                    "really were the same constant.",
            )
        } finally {
            scene.close()
        }
    }

    /**
     * And a reader who turned haptics down still gets the outcomes.
     *
     * The level filter runs before the seam, so nothing reaches the platform for a
     * dropped intent at all — which is what makes "no haptic" mean no haptic rather
     * than a constant the platform happens to ignore.
     */
    @Test
    fun aReducedLevelDropsTheStreamsAndKeepsTheOutcomes() {
        recordedFeels.clear()
        val scene = ImageComposeScene(width = 100, height = 100, density = Density(1f)) {
            KontourTheme(haptics = HapticsLevel.Reduced) {
                val feedback = LocalFeedback.current
                LaunchedEffect(Unit) {
                    feedback.perform(FeedbackIntent.Tick)
                    feedback.perform(FeedbackIntent.Tap)
                    feedback.perform(FeedbackIntent.Warn)
                }
                Box(Modifier.fillMaxSize())
            }
        }
        try {
            repeat(4) { scene.render(16_000_000L * it).close() }
            assertEquals(listOf(FeedbackFeel.Danger), recordedFeels.toList())
        } finally {
            scene.close()
        }
    }
}
