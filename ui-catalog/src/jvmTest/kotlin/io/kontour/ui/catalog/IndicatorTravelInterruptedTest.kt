package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.dp
import io.kontour.ui.foundation.IndicatorSizing
import io.kontour.ui.foundation.SelectionIndicatorBox
import io.kontour.ui.foundation.rememberSelectionIndicatorState
import io.kontour.ui.foundation.selectionIndicatorItem
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A travel interrupted by a re-measure keeps travelling.
 *
 * `SelectionIndicatorBox` decides between sliding and snapping by asking whether
 * the selected *item* changed:
 *
 * ```kotlin
 * val movedItem = state.targetKey != lastKey
 * lastKey = state.targetKey
 * …
 * !movedItem -> bounds.snapTo(target)      // a resize; a spring chasing one reads as lag
 * else -> bounds.animateTo(target, …)      // a new item; slide
 * ```
 *
 * `lastKey` is advanced **before** `animateTo`, and `animateTo` suspends for the
 * length of the travel. `LaunchedEffect(resolved, state.targetKey)` restarts
 * whenever the target rect changes — a rail expanding, a window resizing, a type
 * scale changing under a control whose segments are not fixed — so a rect that
 * arrives mid-flight cancels the travel and relaunches the effect, which then
 * finds `lastKey` already advanced, computes `movedItem = false`, and takes the
 * resize branch. The spring is abandoned and the marker teleports the rest of
 * the way.
 *
 * Moving the assignment past the `when` is the whole fix: a travel that is
 * interrupted has not finished handling its key, so it must not have claimed it.
 *
 * ### Why nothing noticed
 *
 * The obvious suspect — `SegmentedControl` under a text-size change — turns out
 * not to trip it. Its segments are equal-weight children of a fixed-width row,
 * so their rects are identical at every font scale and the effect is never
 * relaunched. It takes a container that genuinely re-measures *while the marker
 * is moving*, which is what this test builds: the row changes width three frames
 * into the travel.
 */
class IndicatorTravelInterruptedTest {

    private val items = listOf("one", "two", "three", "four")

    /**
     * The marker's left edge on each frame after the selection changes.
     *
     * The width changes on frame [resizeAt], mid-travel. A travel that survives
     * the interruption carries on in small steps; one that has been turned into
     * a snap arrives in a single frame.
     */
    private fun travel(resizeAt: Int, frames: Int = 24): List<Float> {
        var selected by mutableStateOf(0)
        var wide by mutableStateOf(false)
        var marker = 0f
        val path = mutableListOf<Float>()
        Scene(1400, 200, reduceMotion = false) {
            val state = rememberSelectionIndicatorState()
            Box(Modifier.fillMaxSize().background(Color.White)) {
                SelectionIndicatorBox(
                    state = state,
                    sizing = IndicatorSizing.Fill,
                    modifier = Modifier.width(if (wide) 600.dp else 400.dp).height(60.dp),
                    indicator = {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(Color.Black)
                                .onGloballyPositioned { marker = it.positionInRoot().x }
                        )
                    },
                ) {
                    Row(Modifier.fillMaxSize()) {
                        items.forEachIndexed { index, item ->
                            Box(
                                Modifier
                                    .width(if (wide) 150.dp else 100.dp)
                                    .fillMaxHeight()
                                    .selectionIndicatorItem(item, index == selected)
                            )
                        }
                    }
                }
            }
        }.use { scene ->
            // Long enough for the first snap and for `alpha` to reach 1 — below
            // that every change takes the "not measured or hidden" branch and
            // there is no travel to interrupt.
            scene.frames(30)
            selected = 3
            repeat(frames) { frame ->
                if (frame == resizeAt) wide = true
                scene.frame()
                path += marker
            }
        }
        return path
    }

    /**
     * The marker does not teleport when the row resizes mid-travel.
     *
     * Measured as the largest single-frame step. A spring's biggest step is a
     * fraction of the distance; a `snapTo` covers the remaining distance in one.
     */
    @Test
    fun aResizeMidTravelDoesNotTurnTheTravelIntoASnap() {
        val interrupted = travel(resizeAt = 3)
        val undisturbed = travel(resizeAt = Int.MAX_VALUE)

        val biggest = interrupted.zipWithNext { a, b -> abs(b - a) }.maxOrNull() ?: 0f
        val natural = undisturbed.zipWithNext { a, b -> abs(b - a) }.maxOrNull() ?: 0f

        // The resize itself moves the marker — the row is two and a half times
        // wider, so the destination moves with it. What must not happen is the
        // *whole remaining travel* landing in one frame. Twice the undisturbed
        // spring's fastest frame is generous for the first and far under the
        // second.
        assertTrue(
            biggest <= natural * 2f,
            "the marker moved ${biggest}px in one frame after the row resized " +
                "mid-travel, against ${natural}px in the fastest frame of the " +
                "same travel left alone. `lastKey` is advanced before " +
                "`animateTo` suspends, so the relaunched effect reads " +
                "`movedItem = false` and takes `snapTo` — the spring is " +
                "abandoned and the marker teleports.\npath: " +
                interrupted.joinToString(" ") { it.toInt().toString() } + "\nundisturbed: " + undisturbed.joinToString(" ") { it.toInt().toString() },
        )
    }
}
