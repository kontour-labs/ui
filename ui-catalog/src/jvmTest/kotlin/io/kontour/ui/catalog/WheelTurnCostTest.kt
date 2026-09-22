package io.kontour.ui.catalog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import io.kontour.ui.components.datetime.WheelPicker
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * What it costs to turn a drum, counted in rows composed.
 *
 * Reported from a phone: "scrolling through the month/year in the date picker is
 * ridiculously laggy". Both drums put their fade and their shrink on values read
 * **in composition** — `abs(index - centredIndex)` on the finite one, and the raw
 * pixel offset on the endless one — so a gesture that changes no text at all
 * rebuilt every row on screen, in the endless wheel's case once per frame.
 *
 * `label` is called once per row composed, which makes it the counter: it is the
 * one thing a row's text cannot be built without. Counted rather than timed, for
 * the reason `OverlayRecompositionTest` gives — a stopwatch here measures this
 * machine, and a composition count is the same number on a phone.
 *
 * Measured across a three-row drag, before and after:
 *
 * | | before | after |
 * |---|---|---|
 * | finite | 24 | 6 |
 * | endless | 234 | 59 |
 *
 * The endless wheel's 234 is 35 per row, which is one per *frame* — the shape of
 * the defect, visible in the number. What is left in both is a row composed when
 * its own text changes, which is the floor: the finite wheel composes the rows
 * that enter the window, and the endless one has no keyed rows to spare, so all
 * seven change value when the drum crosses a row.
 *
 * The bounds below are generous against the after and impossible for the before,
 * because the before scales with the frame rate and the after does not.
 */
class WheelTurnCostTest {

    @Test
    fun turningTheDrumDoesNotRecomposeItsRows() {
        var selected by mutableIntStateOf(120)
        var bounds = Rect.Zero
        val years = (1906..2146).toList()
        val tally = mutableMapOf<Int, Int>()
        // Hoisted out of the composition on purpose. A lambda written at the call
        // site is a fresh instance on every recomposition, and `WheelPicker`
        // keys its rows on this one — so an inline counter would measure the
        // test's own closure rather than the drum.
        val label: (Int) -> String = { tally[it] = (tally[it] ?: 0) + 1; it.toString() }

        Scene(width = 400, height = 400) {
            Box(Modifier.fillMaxSize()) {
                WheelPicker(
                    items = years,
                    selected = selected,
                    onSelectedChange = { selected = it },
                    label = label,
                    modifier = Modifier.reportBounds { bounds = it },
                )
            }
        }.use { scene ->
            scene.frames(4)
            val settled = tally.values.sum()
            scene.drag(from = bounds.alongY(0.8f), to = bounds.alongY(0.2f), steps = 24)
            scene.frames(10)
            val turning = tally.values.sum() - settled

            assertTrue(
                selected > 120,
                "the drum did not turn at all — it is still on ${years[selected]}",
            )
            assertTrue(
                turning < 12,
                "turning the drum ${selected - 120} rows composed $turning rows " +
                    "(${tally.toSortedMap()}) — the rows are reading the drum's " +
                    "position in composition again",
            )
        }
    }

    @Test
    fun turningTheEndlessDrumDoesNotRecomposeItsRows() {
        var selected by mutableIntStateOf(12)
        var bounds = Rect.Zero
        val hours = (0..23).toList()
        val tally = mutableMapOf<Int, Int>()
        val label: (Int) -> String = { tally[it] = (tally[it] ?: 0) + 1; it.toString() }

        Scene(width = 400, height = 400) {
            Box(Modifier.fillMaxSize()) {
                WheelPicker(
                    items = hours,
                    selected = selected,
                    onSelectedChange = { selected = it },
                    label = label,
                    infinite = true,
                    modifier = Modifier.reportBounds { bounds = it },
                )
            }
        }.use { scene ->
            scene.frames(4)
            val settled = tally.values.sum()
            scene.drag(from = bounds.alongY(0.8f), to = bounds.alongY(0.2f), steps = 24)
            scene.frames(10)
            val turning = tally.values.sum() - settled

            assertTrue(selected != 12, "the drum did not turn at all")
            assertTrue(
                turning < 100,
                "turning the endless drum composed $turning rows " +
                    "(${tally.toSortedMap()}) — that is about one row per frame, " +
                    "which is the drum's position being read in composition",
            )
        }
    }
}
