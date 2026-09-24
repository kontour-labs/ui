package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.runtime.CompositionLocalProvider
import io.kontour.ui.components.selection.Slider
import java.awt.image.BufferedImage
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The slider's head: round at rest, a G2 pill when held, with the value above it
 * while it is held.
 *
 * Both asked for in one breath: "the head is not a squircle, and it should be",
 * and "can we add the option to display a label above the head as you're dragging
 * it?". The label is off unless a formatter is given, shown while the thumb is held
 * and gone once it is let go.
 */
class SliderHeadTest {

    /**
     * Round at rest, and a pill once held — the switch's pill, whose ends meet its
     * sides G2-continuously.
     *
     * Read from the thumb's own fill, out from its centre: along the track and along
     * the diagonal. A circle reaches as far on the diagonal as it does across — a
     * ratio of 1 — where the superellipse head this replaced reached about 1.19,
     * which read as a rounded square: "when i said make it a squircle, i meant like
     * the pill shape of the switch. it should still have that circular shape when
     * not pressed".
     */
    @Test
    fun theThumbIsRoundAtRestAndAPillWhenHeld() {
        var bounds = Rect.Zero
        var rest: BufferedImage? = null
        var held: BufferedImage? = null
        Scene(width = 600, height = 200, reduceMotion = false) {
            Box(Modifier.fillMaxSize().background(Color.White).padding(40.dp)) {
                Slider(
                    value = 0.5f,
                    onValueChange = {},
                    modifier = Modifier.fillMaxWidth().reportBounds { bounds = it },
                )
            }
        }.use { scene ->
            rest = scene.frames(6)
            scene.press(bounds.center)
            held = scene.frames(30)
            scene.release(bounds.center)
        }

        val cx = bounds.center.x.roundToInt()
        val cy = bounds.center.y.roundToInt()
        val atRest = requireNotNull(rest)
        val fill = atRest.getRGB(cx, cy)
        val across = atRest.reach(cx, cy, 1, 0, fill).toFloat()
        val up = atRest.reach(cx, cy, 0, -1, fill).toFloat()
        val diagonal = atRest.reach(cx, cy, 1, -1, fill) * sqrt(2f)
        assertTrue(across > 8, "found no thumb at the slider's centre ($across px across)")
        assertTrue(
            diagonal / across in 0.93f..1.07f,
            "at rest the thumb reaches ${diagonal}px along its diagonal against ${across}px " +
                "across — a ratio of ${diagonal / across}, where a circle's is 1",
        )

        val pressed = requireNotNull(held)
        val heldFill = pressed.getRGB(cx, cy)
        val heldAcross = pressed.reach(cx, cy, 1, 0, heldFill).toFloat()
        val heldUp = pressed.reach(cx, cy, 0, -1, heldFill).toFloat()
        assertTrue(
            heldAcross > heldUp * 1.2f,
            "held, the thumb should lengthen into a pill: it is ${heldAcross}px across and " +
                "${heldUp}px up from its centre (at rest $across and $up)",
        )
    }

    @Test
    fun aHeldThumbShowsItsValueAboveAndLettingGoHidesIt() {
        val (held, released) = labelInk(LayoutDirection.Ltr)
        assertTrue(held > 30, "a held thumb drew ${held}px of label above it, where it should show its value")
        assertTrue(released < 3, "the label was still drawn (${released}px) after the thumb was let go")
    }

    /**
     * Right to left, the thumb for a value near the start is near the right-hand end,
     * and the label is over it rather than over where it would be left to right.
     */
    @Test
    fun theLabelFollowsTheThumbRightToLeft() {
        val (held, _) = labelInk(LayoutDirection.Rtl, value = 0.2f, probeAt = 0.8f)
        assertTrue(
            held > 30,
            "right to left, the label was not above the thumb at the right-hand end (${held}px)",
        )
    }

    /**
     * The dark ink above the track, near [probeAt] of the way across, while a press on
     * the thumb is held and after it is let go.
     */
    private fun labelInk(direction: LayoutDirection, value: Float = 0.5f, probeAt: Float = 0.5f): Pair<Int, Int> {
        var bounds = Rect.Zero
        var current by mutableStateOf(value)
        var held = 0
        var released = 0
        Scene(width = 600, height = 420) {
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                Box(Modifier.fillMaxSize().background(Color.White).padding(top = 120.dp, start = 40.dp, end = 40.dp)) {
                    Slider(
                        value = current,
                        onValueChange = { current = it },
                        valueLabel = { "${(it * 100).roundToInt()}%" },
                        modifier = Modifier.fillMaxWidth().reportBounds { bounds = it },
                    )
                }
            }
        }.use { scene ->
            scene.frames(4)
            val at = bounds.alongX(probeAt)
            scene.press(at)
            held = scene.frames(20).darkInkAbove(bounds, at.x)
            scene.release(at)
            released = scene.frames(40).darkInkAbove(bounds, at.x)
        }
        return held to released
    }

    /** Dark pixels in the 60px band above [bounds], within 80px of [x]. */
    private fun BufferedImage.darkInkAbove(bounds: Rect, x: Float): Int {
        var dark = 0
        val bottom = bounds.top.toInt()
        for (y in (bottom - 60).coerceAtLeast(0) until bottom) {
            for (px in (x - 80).toInt().coerceAtLeast(0) until (x + 80).toInt().coerceAtMost(width)) {
                val rgb = getRGB(px, y)
                if ((rgb shr 16 and 0xFF) < 100 && (rgb shr 8 and 0xFF) < 100 && (rgb and 0xFF) < 100) dark++
            }
        }
        return dark
    }

    /** How many pixels from ([x], [y]) in the direction ([dx], [dy]) stay [colour]. */
    private fun BufferedImage.reach(x: Int, y: Int, dx: Int, dy: Int, colour: Int): Int {
        var n = 0
        while (true) {
            val px = x + dx * (n + 1)
            val py = y + dy * (n + 1)
            if (px !in 0 until width || py !in 0 until height || getRGB(px, py) != colour) return n
            n++
        }
    }
}
