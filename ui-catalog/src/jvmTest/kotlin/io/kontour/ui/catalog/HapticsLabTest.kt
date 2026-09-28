package io.kontour.ui.catalog

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import io.kontour.haptics.HapticEffect
import io.kontour.haptics.HapticRecord
import io.kontour.haptics.RecordingHaptics
import io.kontour.haptics.TracingHaptics
import io.kontour.ui.interaction.FeedbackIntent
import io.kontour.ui.interaction.LocalHaptics
import io.kontour.ui.interaction.defaultEffect
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The haptics page plays what its buttons say, through whatever player is in
 * force — which on a phone is the phone, and here is a recording.
 */
@OptIn(ExperimentalTestApi::class)
class HapticsLabTest {

    @Test
    fun anEffectButtonPlaysThatEffectAndAnIntentButtonItsDefault() = runComposeUiTest {
        val player = RecordingHaptics()
        setContent {
            CompositionLocalProvider(LocalHaptics provides player) {
                KontourTheme { HapticsLab() }
            }
        }
        // The first of each: the tuner further down names them all again.
        onAllNodesWithText("Limit").onFirst().performScrollTo().performClick()
        onAllNodesWithText("Thud").onFirst().performScrollTo().performClick()
        waitForIdle()
        assertEquals(
            listOf(
                HapticRecord.Played(FeedbackIntent.Limit.defaultEffect),
                HapticRecord.Played(HapticEffect.Thud(1f)),
            ),
            player.records,
        )
    }

    @Test
    fun theTraceCountsWhatThePageAskedOfThePlayer() = runComposeUiTest {
        val player = TracingHaptics(RecordingHaptics())
        setContent {
            CompositionLocalProvider(LocalHaptics provides player) {
                KontourTheme { HapticsLab() }
            }
        }
        onNodeWithText("Probe").performScrollTo().performClick()
        onNodeWithText("Felt it").performScrollTo().performClick()
        waitForIdle()
        // A tap is a light impact; the note is kept in the log below the counts.
        assertEquals(1, player.trace.counters["play Impact"])
        assertEquals(listOf("probe: felt"), player.trace.log)
        onNodeWithText("play Impact: 1", substring = true).assertExists()
    }
}
