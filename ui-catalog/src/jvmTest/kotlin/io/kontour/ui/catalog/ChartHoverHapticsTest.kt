package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.unit.dp
import io.kontour.ui.charts.ChartSelectionState
import io.kontour.ui.charts.ChartSeries
import io.kontour.ui.charts.SectorChart
import io.kontour.ui.charts.rememberChartSelectionState
import io.kontour.ui.interaction.FeedbackDispatcher
import io.kontour.ui.interaction.FeedbackIntent
import io.kontour.ui.interaction.LocalFeedback
import io.kontour.ui.overlay.OverlayHost
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A pie's sectors, chosen by a pointer resting on them, tick for a click and
 * not for a pointer passing round them.
 *
 * Reported 2026-10-21 for 0.5.0 from a MacBook Pro, whose trackpad plays the
 * library's haptics under the finger moving the pointer: *"not really a fan of
 * how hovering things on desktop causes the mac trackpad to rumble"*. A chart
 * chooses the place or the sector under a resting pointer, and ticked for it as
 * it ticks for a finger stepped round, so a pointer crossing a dashboard of
 * them ticked all the way. The theme now knows a hover when it sees one (see
 * `HoverWatch` in `:ui`), and the chart is quiet on one with no change to
 * `:ui-charts`; this is the chart itself, to keep it so.
 *
 * Half rent, a third food and the rest fun, from the top clockwise, in a 400px
 * pie at this scene's density of 2.
 */
class ChartHoverHapticsTest {

    @Test
    fun aPointerPassingRoundThePieChoosesEachSectorAndPlaysNothing() = pie { scene, bounds, selection, felt ->
        // Round the circle at half its radius, from the right to just short of
        // the top: rent, food, fun. Paced, so the rate floor would let every
        // tick through if there were any.
        val chosen = mutableSetOf<ChartSeries>()
        for (step in 0..24) {
            scene.move(round(bounds, 90.0 + step * 10.0), PointerType.Mouse)
            scene.frame()
            selection.x?.let { chosen += it }
            Thread.sleep(20)
        }
        assertEquals(3, chosen.size, "the pointer did not rest on all three sectors, so this measured nothing: $chosen")
        assertEquals(emptyList(), felt, "a pointer passing round a pie, with no button down, played $felt")
    }

    /**
     * The same way round with the button held: it ticks as it crosses into food
     * and into fun, as a slider dragged on a Mac's trackpad does. A pointer that
     * rested on rent first chose it in silence, so pressing there is no news.
     */
    @Test
    fun theButtonHeldRoundThePieStillTicksEachSector() = pie { scene, bounds, _, felt ->
        val start = round(bounds, 90.0)
        scene.move(start, PointerType.Mouse)
        scene.frame()
        scene.press(start, PointerType.Mouse)
        scene.frame()
        for (step in 1..24) {
            scene.move(round(bounds, 90.0 + step * 10.0), PointerType.Mouse)
            scene.frame()
            Thread.sleep(20)
        }
        scene.release(round(bounds, 330.0), PointerType.Mouse)
        scene.frame()
        assertEquals(2, felt.count { it == FeedbackIntent.Tick }, "round the pie from rent to fun with the button held: $felt")
    }

    /** And with a finger, stepped round on a phone. */
    @Test
    fun aFingerRoundThePieStillTicksEachSector() = pie { scene, bounds, _, felt ->
        val start = round(bounds, 90.0)
        scene.press(start)
        scene.frame()
        for (step in 1..24) {
            scene.move(round(bounds, 90.0 + step * 10.0))
            scene.frame()
            Thread.sleep(20)
        }
        scene.release(round(bounds, 330.0))
        scene.frame()
        assertTrue(felt.count { it == FeedbackIntent.Tick } >= 1, "a finger round the pie from rent to fun played no tick: $felt")
    }

    /** At [degrees] clockwise from the top, half way out. */
    private fun round(bounds: Rect, degrees: Double): Offset {
        val radians = degrees * PI / 180.0
        val reach = bounds.width / 4f
        return Offset(
            bounds.center.x + (sin(radians) * reach).toFloat(),
            bounds.center.y - (cos(radians) * reach).toFloat(),
        )
    }

    private class Spend(val category: String, val amount: Int)

    private fun pie(test: (Scene, Rect, ChartSelectionState<ChartSeries>, List<FeedbackIntent>) -> Unit) {
        val felt = mutableListOf<FeedbackIntent>()
        lateinit var selection: ChartSelectionState<ChartSeries>
        val month = listOf(Spend("Rent", 50), Spend("Food", 30), Spend("Fun", 20))
        var bounds = Rect.Zero
        Scene(width = 600, height = 600, reduceMotion = true) {
            selection = rememberChartSelectionState()
            CompositionLocalProvider(LocalFeedback provides FeedbackDispatcher { felt += it }) {
                OverlayHost(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().background(Color.White)) {
                        SectorChart(
                            modifier = Modifier.size(200.dp).reportBounds { bounds = it },
                            selection = selection,
                            showLegend = false,
                            animateData = false,
                        ) {
                            sectors(month, value = { it.amount }, series = { ChartSeries(it.category) })
                        }
                    }
                }
            }
        }.use { scene ->
            scene.frames(3)
            // Arrived from outside the chart, as a pointer does.
            scene.move(Offset(590f, 590f), PointerType.Mouse)
            scene.frame()
            test(scene, bounds, selection, felt)
        }
    }
}
