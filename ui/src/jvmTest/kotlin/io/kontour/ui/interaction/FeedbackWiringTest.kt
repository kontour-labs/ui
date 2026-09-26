package io.kontour.ui.interaction

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import io.kontour.haptics.HapticRecord
import io.kontour.haptics.RecordingHaptics
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * An intent reaches the player as the effect the table assigns it, through the
 * dispatcher the theme installs.
 *
 * `FeedbackEffectsTest` holds the table and this holds the wiring, which is a
 * separate claim: the theme's dispatcher has to filter by level, look the effect
 * up, and hand *that* to the player — and, for a hold, start a rumble that builds
 * and is stopped. A [RecordingHaptics] provided above the theme is the player, so
 * what is read back is what a phone would have been asked to play.
 */
class FeedbackWiringTest {

    @Test
    fun eachIntentPlaysItsOwnEffect() {
        val intents = listOf(FeedbackIntent.Tick, FeedbackIntent.Tap, FeedbackIntent.LongPress, FeedbackIntent.Warn)
        val records = played { feedback -> intents.forEach(feedback::perform) }
        assertEquals(
            intents.map { HapticRecord.Played(it.defaultEffect) },
            records,
            "a wheel row and a switch answering a tap are supposed to arrive as " +
                "different effects. Reported from a phone as everything feeling heavy.",
        )
    }

    /**
     * And a reader who turned haptics down still gets the outcomes. The level
     * filter runs before the player, so nothing reaches it for a dropped intent
     * at all — "no haptic" means no haptic.
     */
    @Test
    fun aReducedLevelDropsTheStreamsAndKeepsTheOutcomes() {
        val records = played(HapticsLevel.Reduced) { feedback ->
            feedback.perform(FeedbackIntent.Tick)
            feedback.perform(FeedbackIntent.Tap)
            feedback.perform(FeedbackIntent.Warn)
        }
        assertEquals(listOf(HapticRecord.Played(FeedbackIntent.Warn.defaultEffect)), records)
    }

    @Test
    fun offIsNothingAtAll() {
        val records = played(HapticsLevel.Off) { feedback ->
            FeedbackIntent.entries.forEach(feedback::perform)
            feedback.sustain(FeedbackIntent.Hold).stop()
        }
        assertEquals(emptyList(), records)
    }

    /** A hold is a rumble, faint and building with its progress, and stopped. */
    @Test
    fun aHoldIsARumbleThatBuildsAndStops() {
        val records = played { feedback ->
            val hold = feedback.sustain(FeedbackIntent.Hold)
            hold.update(0.5f)
            hold.update(1f)
            hold.stop()
            hold.stop()
        }
        val started = records.first() as HapticRecord.RumbleStarted
        val updates = records.filterIsInstance<HapticRecord.RumbleUpdated>()
        assertEquals(2, updates.size)
        assertEquals(true, started.intensity < updates[0].intensity && updates[0].intensity < updates[1].intensity, "it did not build: $records")
        assertEquals(true, updates.last().intensity <= 0.5f, "a hold's rumble is meant to stay faint: $records")
        assertEquals(HapticRecord.RumbleStopped(started.id), records.last())
        assertEquals(4, records.size, "stopped twice, reported once: $records")
    }

    @Test
    fun aHoldBelowItsLevelIsNoRumble() {
        val records = played(HapticsLevel.Reduced) { feedback ->
            val hold = feedback.sustain(FeedbackIntent.Hold)
            hold.update(1f)
            hold.stop()
        }
        assertEquals(emptyList(), records)
    }

    /** Runs [body] with the theme's dispatcher at [level], and returns what the player was asked. */
    private fun played(
        level: HapticsLevel = HapticsLevel.Standard,
        body: (FeedbackDispatcher) -> Unit,
    ): List<HapticRecord> {
        val player = RecordingHaptics()
        val scene = ImageComposeScene(width = 100, height = 100, density = Density(1f)) {
            CompositionLocalProvider(LocalHaptics provides player) {
                KontourTheme(haptics = level) {
                    val feedback = LocalFeedback.current
                    LaunchedEffect(Unit) { body(feedback) }
                    Box(Modifier.fillMaxSize())
                }
            }
        }
        try {
            repeat(4) { scene.render(16_000_000L * it).close() }
        } finally {
            scene.close()
        }
        return player.records
    }
}
