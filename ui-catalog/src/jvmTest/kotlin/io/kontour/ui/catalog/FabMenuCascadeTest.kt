package io.kontour.ui.catalog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Plus
import com.composables.icons.tabler.outline.Star
import io.kontour.haptics.HapticEffect
import io.kontour.haptics.HapticRecord
import io.kontour.haptics.RecordingHaptics
import io.kontour.ui.components.action.FabMenu
import io.kontour.ui.components.action.FabMenuDefaults
import io.kontour.ui.interaction.FeedbackIntent
import io.kontour.ui.interaction.LocalHaptics
import io.kontour.ui.interaction.defaultEffect
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A FAB menu's items landing are felt, one soft tick each as the wave of them runs
 * out from the button — asked for from a phone: "haptics on fab menu when items
 * settle into place".
 */
@OptIn(ExperimentalTestApi::class)
class FabMenuCascadeTest {

    @Test
    fun openingPlaysOnePatternWithAnEventPerItemTheStaggerApart() {
        val (opening, _) = played(reduceMotion = false)
        val pattern = opening.filterIsInstance<HapticRecord.PatternPlayed>().single().pattern
        assertEquals(3, pattern.events.size, "one landing per item")
        assertEquals(
            listOf(0L, 1L, 2L).map { FabMenuDefaults.Stagger * it.toInt() },
            pattern.events.map { it.at },
            "the landings should be the stagger apart, as the items are",
        )
        val strengths = pattern.events.map { (it.primitive as HapticEffect.LowTick).strength }
        assertTrue(strengths.zipWithNext().all { (a, b) -> b < a }, "each landing should be softer than the last: $strengths")
        assertEquals(1, opening.size, "the landings are one pattern, and nothing else plays: $opening")
    }

    @Test
    fun underReducedMotionItIsOneTick() {
        val (opening, _) = played(reduceMotion = true)
        assertEquals(listOf(HapticRecord.Played(FeedbackIntent.GestureEnd.defaultEffect)), opening)
    }

    @Test
    fun closingPlaysNothing() {
        val (_, closing) = played(reduceMotion = false)
        assertEquals(emptyList(), closing)
    }

    /** What opening the menu played, and then what closing it did. */
    private fun played(reduceMotion: Boolean): Pair<List<HapticRecord>, List<HapticRecord>> {
        val player = RecordingHaptics()
        val expanded = mutableStateOf(false)
        var opening = emptyList<HapticRecord>()
        runComposeUiTest {
            setContent {
                CompositionLocalProvider(LocalHaptics provides player) {
                    KontourTheme(reduceMotion = reduceMotion) {
                        OverlayHost {
                            Box(Modifier.fillMaxSize()) {
                                FabMenu(
                                    expanded = expanded.value,
                                    onExpandedChange = { expanded.value = it },
                                    icon = Tabler.Outline.Plus,
                                    contentDescription = "Add",
                                ) {
                                    repeat(3) { item(Tabler.Outline.Star, "Nearby", onClick = {}) }
                                }
                            }
                        }
                    }
                }
            }
            waitForIdle()
            player.clear()
            expanded.value = true
            mainClock.advanceTimeBy(1500)
            waitForIdle()
            opening = player.records
            player.clear()
            expanded.value = false
            mainClock.advanceTimeBy(1500)
            waitForIdle()
        }
        return opening to player.records
    }
}
