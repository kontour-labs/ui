package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.selection.Knob
import io.kontour.ui.components.selection.KnobDefaults
import java.awt.image.BufferedImage
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A stepped knob has the stepped slider's detents: the notch leans toward the hand
 * between steps, carries on across them, and travels to a step rather than
 * appearing on it.
 *
 * "Can we add the detent-like behaviour that the slider has, so it animates in
 * stepped mode?" It went from step to step in one frame, turned or keyed.
 *
 * A 200dp knob with three steps — four intervals of 67.5° — at 0.5, so its notch
 * starts straight up. A drag's travel is 200dp, 400px at this density, so a step is
 * 100px of drag. What is measured is the notch: the only red on the page, and its
 * angle about the middle of the knob.
 */
class KnobDetentTest {

    /**
     * Dragged a third of a step past its step and held there, the notch leans
     * toward the finger — 0.45 of the way, as the slider's thumb does — without
     * leaving the step for it.
     */
    @Test
    fun heldBetweenStepsTheNotchLeansTowardTheFinger() {
        var angle = 0.0
        knob(reduceMotion = false) { scene, middle ->
            scene.press(middle)
            scene.frame()
            for (i in 1..10) {
                scene.move(middle + Offset(StepPx / 3 * i / 10, 0f))
                scene.frame()
            }
            angle = notchAngle(scene.frames(30))
        }
        val finger = Top + Interval / 3
        assertTrue(
            angle > Top + 5 && angle < Top + 20,
            "held a third of a step past its step the notch is at ${angle.format()}° — it " +
                "should lean toward the finger's ${finger.format()}° from the step at " +
                "${Top.format()}°, to about ${(Top + Interval / 3 * 0.45).format()}°",
        )
    }

    /**
     * Across a step, the notch only ever goes the way the finger does, and when the
     * finger lets go it springs onto the step it reached — 1.2 steps along, so the
     * next one.
     *
     * The slider's own report, one control over: with the lean added to the spring's
     * output rather than its target, crossing a step flips the lean while the spring
     * is still on the old step, and the thumb jumped back before it went on.
     */
    @Test
    fun turnedAcrossAStepTheNotchNeverGoesBack() {
        val angles = mutableListOf<Double>()
        var landed = 0.0
        knob(reduceMotion = false) { scene, middle ->
            val end = middle + Offset(StepPx * 1.2f, 0f)
            scene.press(middle)
            scene.frame()
            for (i in 1..30) {
                scene.move(middle + Offset(StepPx * 1.2f * i / 30, 0f))
                angles += notchAngle(scene.frame())
            }
            scene.frames(4)
            scene.release(end)
            landed = notchAngle(scene.frames(40))
        }
        val back = angles.zipWithNext().maxOf { (a, b) -> a - b }
        assertTrue(
            back < 0.5,
            "dragged across a step, the notch went back by ${back.format()}° on one " +
                "frame: ${angles.joinToString { it.format() }}",
        )
        assertTrue(
            abs(landed - (Top + Interval)) < 2,
            "let go, the notch should settle on the next step at ${(Top + Interval).format()}°, " +
                "and is at ${landed.format()}°",
        )
    }

    /**
     * Moved from outside the gesture — an arrow key, or the caller — it travels to
     * the next step; under reduced motion it is simply there.
     */
    @Test
    fun aNewValueTravelsToItsStepUnlessMotionIsReduced() {
        var moving = 0.0
        var arrived = 0.0
        knob(reduceMotion = false, next = 0.75f) { scene, _ ->
            moving = notchAngle(scene.frames(2))
            arrived = notchAngle(scene.frames(40))
        }
        assertTrue(
            moving > Top + 3 && moving < Top + Interval - 3,
            "two frames after the value moved a step the notch is at ${moving.format()}° — " +
                "it went straight from ${Top.format()}° to ${(Top + Interval).format()}° " +
                "rather than travelling",
        )
        assertTrue(abs(arrived - (Top + Interval)) < 2, "and it ended at ${arrived.format()}°")

        var still = 0.0
        knob(reduceMotion = true, next = 0.75f) { scene, _ ->
            still = notchAngle(scene.frames(2))
        }
        assertTrue(
            abs(still - (Top + Interval)) < 2,
            "under reduced motion the notch should be on its new step at once, and is at ${still.format()}°",
        )
    }

    /**
     * A stepped knob at 0.5 in a scene, handed to [run] with the middle of its face.
     * With [next], the value is set to it after the first frames, as a key press
     * would.
     */
    private fun knob(
        reduceMotion: Boolean,
        next: Float? = null,
        run: (Scene, Offset) -> Unit,
    ) {
        var value by mutableStateOf(0.5f)
        var bounds = Rect.Zero
        Scene(width = 600, height = 600, reduceMotion = reduceMotion) {
            Box(Modifier.fillMaxSize().background(Color.White), contentAlignment = Alignment.Center) {
                Knob(
                    value = value,
                    onValueChange = { value = it },
                    steps = 3,
                    size = 200.dp,
                    colours = KnobDefaults.colours(
                        indicator = listOf(Color.Blue),
                        face = Color.White,
                        notch = Color.Red,
                    ),
                    modifier = Modifier.reportBounds { bounds = it },
                )
            }
        }.use { scene ->
            scene.frames(3)
            centre = bounds.center
            if (next != null) value = next
            run(scene, centre)
        }
    }

    private var centre = Offset.Zero

    /** The angle of the notch about the knob's middle, in degrees, clockwise from three o'clock. */
    private fun notchAngle(frame: BufferedImage): Double {
        var x = 0.0
        var y = 0.0
        var n = 0
        for (py in 0 until frame.height) for (px in 0 until frame.width) {
            val rgb = frame.getRGB(px, py)
            val r = rgb shr 16 and 0xFF
            val g = rgb shr 8 and 0xFF
            val b = rgb and 0xFF
            if (r > 200 && g < 90 && b < 90) {
                x += px - centre.x
                y += py - centre.y
                n++
            }
        }
        check(n > 0) { "no notch drawn" }
        return atan2(y / n, x / n) * 180 / PI
    }

    private fun Double.format(): String = "%.1f".format(this)

    private companion object {
        /** Straight up, where 0.5 of a 270° scale open at the bottom points. */
        const val Top = -90.0

        /** One step of three: a quarter of 270°. */
        const val Interval = 67.5

        /** One step of drag: a quarter of the 400px travel. */
        const val StepPx = 100f
    }
}
