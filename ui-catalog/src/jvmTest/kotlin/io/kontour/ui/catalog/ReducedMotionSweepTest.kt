package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import io.kontour.ui.contract.componentRegistry
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.Theme
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Under reduced motion, pressing any component in the library is over quickly
 * and nothing it moves overshoots.
 *
 * Every specimen in the registry, tapped in the middle, under the setting, with
 * the ink photographed for a second afterwards. Two things are held:
 *
 * - **It settles.** The picture stops changing within [SettleFrames] — a quarter
 *   of a second, which is `Motion.fast` and a frame of slack. A slide, a spring or
 *   a stagger the setting failed to reach runs longer than that.
 * - **Nothing flies.** No frame on the way draws ink more than [Overshoot] outside
 *   the box that holds the picture and the component before and after — a
 *   pressed ground filling the component's own box is a state appearing where it
 *   stands, which the setting keeps. A menu flying out of its
 *   button, a panel sliding in from off the edge, a spring carrying a thumb past
 *   its stop — each is ink, for a few frames, where neither end state has any.
 *
 * And a control run, with the setting off: if nothing in the registry took longer
 * than a quarter of a second to settle with full motion, the sweep would be
 * measuring nothing, and it says so rather than passing.
 *
 * What it cannot see is a movement inside a box that does not change — a thumb
 * travelling along its own track. That is the small-amplitude tail the
 * setting leaves alone by design, and `check-settings.py`'s rule M names each one.
 */
class ReducedMotionSweepTest {

    @Test
    fun underReducedMotionEverySpecimenSettlesQuicklyAndNothingOvershoots() {
        val slow = mutableListOf<String>()
        val flying = mutableListOf<String>()
        for (spec in componentRegistry) {
            if (spec.name in Exempt) continue
            val run = press(spec, reduceMotion = true)
            if (run.settledAfter > SettleFrames) slow += "${spec.name}: ${run.settledAfter} frames"
            if (run.overshoot > Overshoot * Density) {
                flying += "${spec.name}: ${run.overshoot / Density}dp outside where it began and ended"
            }
        }
        assertTrue(
            slow.isEmpty() && flying.isEmpty(),
            buildString {
                if (slow.isNotEmpty()) {
                    append("${slow.size} specimen(s) were still moving a quarter of a second after a tap under reduced motion:\n")
                    slow.forEach { append("  · $it\n") }
                }
                if (flying.isNotEmpty()) {
                    append("${flying.size} specimen(s) drew ink outside both their resting states under reduced motion:\n")
                    flying.forEach { append("  · $it\n") }
                }
            },
        )
    }

    @Test
    fun withFullMotionTheSweepSeesMovement() {
        val moving = componentRegistry
            .filter { it.name !in Exempt }
            .count { press(it, reduceMotion = false).settledAfter > SettleFrames }
        assertTrue(
            moving >= 3,
            "only $moving specimen(s) took longer than a quarter of a second to settle with full " +
                "motion, so the reduced-motion sweep cannot tell a gated component from an ungated one",
        )
    }

    private class Run(val settledAfter: Int, val overshoot: Float)

    private fun press(spec: io.kontour.ui.contract.ComponentSpec, reduceMotion: Boolean): Run {
        var bounds = Rect.Zero
        Scene(width = Width, height = Height, density = Density, reduceMotion = reduceMotion) {
            OverlayHost(Modifier.fillMaxSize()) {
                Box(
                    Modifier.fillMaxSize().background(Theme.colours.background),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.reportBounds { bounds = it }) { spec.content(Modifier, true) {} }
                }
            }
        }.use { scene ->
            val before = ink(scene.frames(30))
            val placedBefore = bounds
            scene.tap(bounds.center)
            val frames = List(Watch) { ink(scene.frame()) }
            val after = frames.last()
            val settledAfter = frames.indexOfLast { it != after } + 1
            // The component's own box counts as resting, not just its ink: a
            // ghost button's pressed ground lights up the whole of a box whose
            // resting ink is only its label, and that is a state appearing in
            // place, not anything travelling.
            val union = union(union(before, after), union(placedBefore, bounds))
            val overshoot = frames.maxOf { beyond(it, union) }
            return Run(settledAfter, overshoot)
        }
    }

    /** The box round everything that is not the page, in pixels; empty when nothing is drawn. */
    private fun ink(image: BufferedImage): Rect {
        val page = image.getRGB(0, 0)
        var left = image.width
        var top = image.height
        var right = -1
        var bottom = -1
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                if (image.getRGB(x, y) != page) {
                    if (x < left) left = x
                    if (x > right) right = x
                    if (y < top) top = y
                    if (y > bottom) bottom = y
                }
            }
        }
        return if (right < 0) Rect.Zero else Rect(left.toFloat(), top.toFloat(), right + 1f, bottom + 1f)
    }

    private fun union(a: Rect, b: Rect): Rect = when {
        a == Rect.Zero -> b
        b == Rect.Zero -> a
        else -> Rect(minOf(a.left, b.left), minOf(a.top, b.top), maxOf(a.right, b.right), maxOf(a.bottom, b.bottom))
    }

    /** How far [box] reaches outside [limit], in pixels, on its worst side. */
    private fun beyond(box: Rect, limit: Rect): Float {
        if (box == Rect.Zero || limit == Rect.Zero) return 0f
        return maxOf(
            limit.left - box.left,
            box.right - limit.right,
            limit.top - box.top,
            box.bottom - limit.bottom,
            0f,
        )
    }

    private companion object {
        const val Width = 800
        const val Height = 600
        const val Density = 2f

        /** Frames watched after the tap: a second. */
        const val Watch = 60

        /** Motion.fast is nine frames; this is fifteen, a quarter of a second. */
        const val SettleFrames = 15

        /** How far outside both resting states ink may reach, in dp: an antialiased edge and a focus ring. */
        const val Overshoot = 4f

        /**
         * Specimens this cannot judge, with the reason.
         *
         * An indicator that is meant to keep moving has nothing to settle into,
         * and a tap on it is not the thing that moves it.
         */
        val Exempt = mapOf(
            "Spinner" to "keeps turning under reduced motion, by decision: loading is information",
            "CircularProgress" to "an indeterminate specimen turns for the same reason",
            "LinearProgress" to "the same",
            "StepProgress" to "a step in flight pulses by design",
            "Modifier.marquee" to "scrolls continuously unless reduced motion stops it; a tap does nothing",
            "RelativeTimeText" to "ticks with the clock",
            "Toast" to "a toast's own tests drive its arrival; a tap on one dismisses it",
        )
    }
}
