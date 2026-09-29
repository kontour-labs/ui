package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import io.kontour.ui.components.datetime.WheelPicker
import java.awt.image.BufferedImage
import kotlin.math.abs
import kotlin.math.sign
import kotlin.test.Test
import kotlin.test.assertTrue
import org.junit.experimental.categories.Category

/**
 * A two-row drum comes to rest on a row, exactly, and gets there smoothly —
 * however it was flicked, and at a phone's frame rate as well as a desktop's.
 *
 * The AM/PM drum, reported from a phone four times over as its bounce was
 * reworked: first that it "moves up just a little bit" after settling and sits
 * "a bit off-centre"; then, once that was fixed, that the bounce was "jumpy" and
 * "sometimes snaps into place"; then that the first problem was back. Each fix was
 * tested on one flick at 60Hz, and each report came from a phone at 120Hz. So
 * this sweeps both, and holds every run to all of it at once:
 *
 * - **At rest on a row, exactly** — the row in the band is the value reported,
 *   and it is centred to the pixel.
 * - **Out and back once** — the drum never turns round more than once after the
 *   finger lifts, so nothing about the bounce twitches or goes the other way.
 * - **No stop before the bounce** — it never rests on the end row and then leaves
 *   it again.
 * - **No creep** — once it is within a couple of pixels of home it is home, rather
 *   than spending frames a pixel or two short.
 * - **No snap** — its last move into place is a small one.
 */
@Category(Slow::class)
class WheelPickerSettleTest {

    @Test
    fun everyFlickIntoAnEndSettlesCentredAndSmoothly() {
        val failures = mutableListOf<String>()
        for (frame in listOf(16_000_000L, 8_000_000L)) {
            for (steps in listOf(2, 3, 4, 6, 10)) {
                for (towardsAm in listOf(true, false)) {
                    val run = settle(frame, start = if (towardsAm) 1 else 0) { scene, from ->
                        val dy = if (towardsAm) 150f else -150f
                        scene.drag(from, Offset(from.x, from.y + dy), steps = steps)
                    }
                    failures += run.problems(expected = if (towardsAm) 0 else 1).map {
                        "${frame / 1_000_000}ms frames, $steps-step flick towards ${if (towardsAm) "AM" else "PM"}: $it"
                    }
                }
            }
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    @Test
    fun aStretchLetGoSettlesCentredAndSmoothly() {
        val failures = mutableListOf<String>()
        for (frame in listOf(16_000_000L, 8_000_000L)) {
            val gestures = mapOf<String, (Scene, Offset) -> Unit>(
                "pulled past AM and let go slowly" to { scene, from ->
                    scene.drag(from, Offset(from.x, from.y + 150f), steps = 30)
                },
                "pulled past AM and flicked on" to { scene, from ->
                    scene.drag(from, Offset(from.x, from.y + 100f), steps = 12, release = false)
                    scene.drag(Offset(from.x, from.y + 100f), Offset(from.x, from.y + 180f), steps = 2)
                },
            )
            for ((name, gesture) in gestures) {
                failures += settle(frame, start = 0, gesture = gesture).problems(expected = 0).map {
                    "${frame / 1_000_000}ms frames, $name: $it"
                }
            }
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    /** What a run drew after the finger lifted, one frame each [frameMillis], and where it came to rest. */
    private class Run(val rest: Float, val drawn: List<Float>, val selected: Int, val frameMillis: Long, val density: Float) {
        fun problems(expected: Int): List<String> = buildList {
            val off = drawn.map { it - rest }
            val trace = off.map { (it * 2).toInt() / 2f }
            if (selected != expected) add("rested on row $selected, not $expected")
            if (abs(off.last()) > 0.5f) add("came to rest ${off.last()}px off centre: $trace")
            // Home within a third of a second of the finger lifting.
            val home = off.map { abs(it) <= 0.5f }
            val settledAt = (home.indexOfLast { !it } + 1) * frameMillis
            if (settledAt > 330) add("still off centre ${settledAt}ms after the lift: $trace")
            // Out and back once: after the lift, the direction of travel changes
            // at most once, ignoring frames where it did not move.
            val moves = off.zipWithNext { a, b -> b - a }.filter { abs(it) >= 0.5f }
            val turns = moves.zipWithNext().count { (a, b) -> sign(a) != sign(b) }
            if (turns > 1) add("turned round $turns times: $trace")
            // Never at rest on the row and then away from it again.
            val rested = (0 until home.size - 1).firstOrNull { home[it] && home[it + 1] }
            if (rested != null && !home.drop(rested).all { it }) add("stopped on the row at frame $rested, then left it: $trace")
            // No hang: off centre when the finger lifts, it is on its way at once
            // rather than holding still first.
            val held = off.takeWhile { abs(it) > 1f && abs(it - off.first()) < 0.5f }.size
            if (held * frameMillis > 20) add("held still ${held * frameMillis}ms off centre before moving: $trace")
            // No creep: time spent within a point of home without being there.
            val creeping = off.count { abs(it) > 0.5f && abs(it) <= density } * frameMillis
            if (creeping > 70) add("spent ${creeping}ms within a point of home: $trace")
            // No snap: the last move into place is small.
            val last = off.indexOfLast { abs(it) > 0.5f }
            if (last >= 0 && abs(off[last]) > 3f) add("snapped home from ${off[last]}px: $trace")
        }
    }

    private fun settle(frame: Long, start: Int, gesture: (Scene, Offset) -> Unit): Run {
        var selected by mutableIntStateOf(start)
        var bounds = Rect.Zero
        val drawn = mutableListOf<Float>()
        var rest = 0f
        Scene(width = 400, height = 500, density = 3f, frameNanos = frame) {
            Box(Modifier.fillMaxSize().background(Color.White)) {
                WheelPicker(
                    items = listOf("AM", "PM"),
                    selectedIndex = selected,
                    onSelectedIndexChange = { selected = it },
                    itemLabel = { it },
                    visibleItems = 3,
                    modifier = Modifier.reportBounds { bounds = it },
                )
            }
        }.use { scene ->
            rest = scene.frames(6).centredInk(bounds)
            gesture(scene, bounds.alongY(0.5f))
            // A second at either rate.
            repeat((1_000_000_000L / frame).toInt()) { drawn += scene.frame().centredInk(bounds) }
        }
        return Run(rest, drawn, selected, frameMillis = frame / 1_000_000L, density = 3f)
    }

    /** The middle of the run of ink nearest the middle of [within]. */
    private fun BufferedImage.centredInk(within: Rect): Float {
        val background = getRGB(1, 1)
        val inked = (within.top.toInt().coerceAtLeast(0) until within.bottom.toInt().coerceAtMost(height)).filter { y ->
            (within.left.toInt().coerceAtLeast(0) until within.right.toInt().coerceAtMost(width)).any { x ->
                abs((getRGB(x, y) shr 16 and 0xFF) - (background shr 16 and 0xFF)) > 64
            }
        }
        val runs = mutableListOf<IntRange>()
        for (y in inked) {
            val last = runs.lastOrNull()
            if (last != null && y == last.last + 1) runs[runs.lastIndex] = last.first..y else runs += y..y
        }
        val middle = (within.top + within.bottom) / 2f
        val run = runs.minByOrNull { abs((it.first + it.last) / 2f - middle) } ?: return Float.NaN
        return (run.first + run.last) / 2f
    }
}
