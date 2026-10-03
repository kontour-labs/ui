package io.kontour.ui.catalog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.v2.runComposeUiTest
import io.kontour.icons.tabler.Tabler
import io.kontour.icons.tabler.outline.Plus
import io.kontour.icons.tabler.outline.Star
import io.kontour.ui.components.action.FabMenu
import io.kontour.ui.interaction.FeedbackDispatcher
import io.kontour.ui.interaction.FeedbackIntent
import io.kontour.ui.interaction.LocalFeedback
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A FAB menu's items landing are felt, one soft tick each as the wave of them runs
 * out from the button — asked for from a phone: "haptics on fab menu when items
 * settle into place".
 *
 * And each tick on its own item: the first version worked the landings out in
 * advance and played them as one pattern, 28ms apart, which arrived as "a rumble
 * at the end once the last one lands". Then each one on the frame its item came
 * to rest, which was felt "a bit delayed": a haptic takes a moment to reach the
 * hand, so it is now asked for a little ahead.
 */
@OptIn(ExperimentalTestApi::class)
class FabMenuCascadeTest {

    private class Felt(val intent: FeedbackIntent, val scale: Float, val atMillis: Long)

    @Test
    fun eachItemIsFeltOnTheFrameItLands() {
        val run = opened(reduceMotion = false)
        assertEquals(Items, run.felt.size, "one landing per item: ${run.felt.map { it.atMillis }}")
        assertTrue(run.felt.all { it.intent == FeedbackIntent.GestureEnd })
        run.felt.forEachIndexed { index, tick ->
            val landed = run.landedAt[index]
            assertTrue(landed >= 0, "item $index never came to rest")
            // Asked for a little ahead of the landing, so that it reaches the
            // hand as the item arrives — never after it, and not so early that it
            // is felt while the item is still plainly in flight.
            val ahead = landed - tick.atMillis
            assertTrue(
                ahead in FrameMillis..MostAheadMillis,
                "item $index landed at ${landed}ms and was asked for at ${tick.atMillis}ms: " +
                    "it should be a frame to ${MostAheadMillis}ms ahead of the landing, never behind it",
            )
        }
        val gaps = run.felt.zipWithNext { a, b -> b.atMillis - a.atMillis }
        assertTrue(
            gaps.all { it >= 40 },
            "landings felt ${gaps}ms apart run together into one buzz; they need to be told apart",
        )
        val strengths = run.felt.map { it.scale }
        assertTrue(strengths.zipWithNext().all { (a, b) -> b < a }, "each landing should be softer than the last: $strengths")
    }

    @Test
    fun underReducedMotionItIsOneTick() {
        assertEquals(1, opened(reduceMotion = true).felt.size)
    }

    @Test
    fun closingPlaysNothing() {
        assertEquals(0, opened(reduceMotion = false).closing)
    }

    private class Run(val felt: List<Felt>, val landedAt: List<Long>, val closing: Int)

    /**
     * What opening the menu was felt as, when each item came to rest, and how many
     * reports closing it made.
     */
    private fun opened(reduceMotion: Boolean): Run {
        val felt = mutableListOf<Felt>()
        val expanded = mutableStateOf(false)
        var closing = 0
        val landedAt = MutableList(Items) { -1L }
        runComposeUiTest {
            var now = 0L
            val recorder = object : FeedbackDispatcher {
                override fun perform(intent: FeedbackIntent) {
                    felt += Felt(intent, 1f, now)
                }

                override fun perform(intent: FeedbackIntent, scale: Float) {
                    felt += Felt(intent, scale, now)
                }
            }
            setContent {
                KontourTheme(reduceMotion = reduceMotion) {
                    CompositionLocalProvider(LocalFeedback provides recorder) {
                        OverlayHost {
                            Box(Modifier.fillMaxSize()) {
                                FabMenu(
                                    expanded = expanded.value,
                                    onExpandedChange = { expanded.value = it },
                                    icon = Tabler.Outline.Plus,
                                    contentDescription = "Add",
                                ) {
                                    repeat(Items) { item(Tabler.Outline.Star, "Stop $it", onClick = {}) }
                                }
                            }
                        }
                    }
                }
            }
            waitForIdle()
            mainClock.autoAdvance = false
            val start = mainClock.currentTime
            expanded.value = true
            val tracks = List(Items) { mutableListOf<Pair<Long, Rect>>() }
            repeat(90) {
                // Stamped with the frame being produced, which is when anything
                // performed while producing it is felt.
                now = mainClock.currentTime + FrameMillis - 1 - start
                mainClock.advanceTimeByFrame()
                now = mainClock.currentTime - start
                for (i in 0 until Items) {
                    val node = onAllNodesWithContentDescription("Stop $i").fetchSemanticsNodes().firstOrNull() ?: continue
                    tracks[i] += now to node.boundsInRoot
                }
            }
            // Where each item finished, and the first frame it was within 3% of
            // its travel of there — what the eye takes as landed.
            for (i in 0 until Items) {
                val track = tracks[i]
                if (track.isEmpty()) continue
                val end = track.last().second.center
                val travel = track.maxOf { (it.second.center - end).getDistance() }.coerceAtLeast(1f)
                landedAt[i] = track.firstOrNull { (it.second.center - end).getDistance() <= travel * 0.03f }?.first ?: -1L
            }
            mainClock.autoAdvance = true
            val before = felt.size
            expanded.value = false
            mainClock.advanceTimeBy(1500)
            waitForIdle()
            closing = felt.size - before
            felt.subList(before, felt.size).clear()
        }
        return Run(felt.toList(), landedAt, closing)
    }

    private companion object {
        const val Items = 4
        const val FrameMillis = 17L

        /** How far ahead of the landing a tick may be asked for: about what a haptic takes to be felt, and a frame. */
        const val MostAheadMillis = 70L
    }
}
