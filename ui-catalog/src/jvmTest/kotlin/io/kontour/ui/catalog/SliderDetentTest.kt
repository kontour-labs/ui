package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Density
import io.kontour.ui.components.selection.Slider
import io.kontour.ui.theme.KontourTheme
import org.jetbrains.skia.EncodedImageFormat
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A stepped slider's thumb only ever moves the way the finger is moving.
 *
 * It did not. The drawn position was the eased detent *plus* the finger's
 * overshoot, and the two flip apart at the moment a detent is crossed: the
 * finger lands half a step **behind** the detent it just earned, so the
 * overshoot term goes from `+0.225` of a step to `−0.225` while the eased term
 * is still sitting on the detent it has yet to leave. The thumb lurched back
 * most of half a step and then animated the whole way forward. On the geometry
 * below that is a 77px jump backwards in a single frame, on a track where a
 * whole step is 171px.
 *
 * ### Measured off the pixels, not off the state
 *
 * There is no state holder to interrogate here and no layout node to position:
 * the thumb is drawn in `drawWithCache`, so where it *is* is a question only the
 * rendered frame can answer. So the drag is driven with real pointer events and
 * the thumb is found in the image.
 *
 * Finding it is easier than it sounds. The thumb is 22dp across and the track is
 * 6dp, so the topmost ink in the frame is always the crown of the thumb; four
 * pixels below that is a row nothing else reaches, and the midpoint of the ink
 * in that row is the thumb's centre. That holds while the thumb is scaled up
 * mid-drag, which is why it is a midpoint rather than an edge.
 */
class SliderDetentTest {

    private val width = 900
    private val height = 200
    private val density = 2f

    /** Loud, and nothing the component would ever draw. */
    private val backdrop = Color(0xFFFF00FF)

    @Test
    fun theThumbNeverGoesBackwardsWhileTheFingerGoesForwards() {
        val trace = dragAcrossDetents()

        assertTrue(
            trace.size > 20,
            "only ${trace.size} frames were captured, so the drag never happened",
        )

        val travelled = trace.last().thumbX - trace.first().thumbX
        assertTrue(
            travelled > 200f,
            "the thumb moved ${travelled}px over a ${trace.size}-frame drag, so " +
                "this is not measuring a slider that responded at all",
        )

        var worst = Backslide(0f, 0)
        for (i in 1 until trace.size) {
            val back = trace[i - 1].thumbX - trace[i].thumbX
            if (back > worst.pixels) worst = Backslide(back, i)
        }

        assertTrue(
            worst.pixels <= Tolerance,
            "the thumb went backwards ${worst.pixels}px while the finger went " +
                "forwards.\n\n${trace.around(worst.frame)}",
        )
    }

    private data class Backslide(val pixels: Float, val frame: Int)

    /** One frame of the drag: where the finger was, and where the thumb was drawn. */
    private data class Sample(val frame: Int, val fingerX: Float, val thumbX: Float)

    /** The frames either side of [frame], as a table for a failure message. */
    private fun List<Sample>.around(frame: Int): String =
        (maxOf(0, frame - 4)..minOf(size - 1, frame + 4)).joinToString("\n") {
            val mark = if (it == frame) "  <-- here" else ""
            "  frame ${this[it].frame}: finger ${this[it].fingerX}px, " +
                "thumb ${this[it].thumbX}px$mark"
        }

    /**
     * Drags a five-stop slider from one end most of the way to the other,
     * sampling the drawn thumb after every pointer move.
     *
     * `reduceMotion = false` on purpose: the detent easing is the thing under
     * test and it is switched off under reduced motion, so the usual `true` used
     * by the geometry tests would make this pass without rendering the code it is
     * about.
     */
    private fun dragAcrossDetents(): List<Sample> {
        var value by mutableFloatStateOf(0f)
        var centreY = 0f

        val scene = ImageComposeScene(width = width, height = height, density = Density(density)) {
            KontourTheme(darkTheme = false, reduceMotion = false) {
                Box(Modifier.fillMaxSize().background(backdrop)) {
                    Slider(
                        value = value,
                        onValueChange = { value = it },
                        steps = 4,
                        contentDescription = "Stops",
                        modifier = Modifier
                            .fillMaxWidth()
                            .onCentreY { centreY = it },
                    )
                }
            }
        }

        val samples = mutableListOf<Sample>()
        try {
            var nanos = 0L
            fun advance(): BufferedImage {
                nanos += FrameNanos
                return scene.render(nanos).toBuffered()
            }

            repeat(3) { advance() }

            var fingerX = StartX
            scene.sendPointerEvent(
                eventType = PointerEventType.Press,
                position = Offset(fingerX, centreY),
                timeMillis = nanos / 1_000_000L,
                type = PointerType.Touch,
            )

            repeat(Moves) { move ->
                fingerX += MovePx
                scene.sendPointerEvent(
                    eventType = PointerEventType.Move,
                    position = Offset(fingerX, centreY),
                    timeMillis = nanos / 1_000_000L,
                    type = PointerType.Touch,
                )
                samples += Sample(move, fingerX, advance().thumbCentreX())
            }

            scene.sendPointerEvent(
                eventType = PointerEventType.Release,
                position = Offset(fingerX, centreY),
                timeMillis = nanos / 1_000_000L,
                type = PointerType.Touch,
            )
        } finally {
            scene.close()
        }
        return samples
    }

    /**
     * The horizontal centre of the thumb, in scene pixels.
     *
     * See the class note: the topmost ink is the crown of the thumb, and a row
     * just below it holds the thumb and nothing else.
     */
    private fun BufferedImage.thumbCentreX(): Float {
        val background = getRGB(0, 0)
        val crown = (0 until height).firstOrNull { y ->
            (0 until width).any { x -> differs(getRGB(x, y), background) }
        } ?: error("nothing was drawn at all")

        val row = crown + 4
        var left = width
        var right = -1
        for (x in 0 until width) {
            if (!differs(getRGB(x, row), background)) continue
            if (x < left) left = x
            if (x > right) right = x
        }
        check(right >= 0) { "no thumb in row $row, four rows below the topmost ink" }
        return (left + right) / 2f
    }

    /** Generous, so antialiasing along the thumb's edge is not mistaken for ink. */
    private fun differs(a: Int, b: Int): Boolean =
        abs((a shr 16 and 0xFF) - (b shr 16 and 0xFF)) > 24 ||
            abs((a shr 8 and 0xFF) - (b shr 8 and 0xFF)) > 24 ||
            abs((a and 0xFF) - (b and 0xFF)) > 24

    private fun org.jetbrains.skia.Image.toBuffered(): BufferedImage {
        val png = requireNotNull(encodeToData(EncodedImageFormat.PNG)) {
            "Skia failed to encode the frame"
        }.bytes
        return requireNotNull(ImageIO.read(png.inputStream())) { "unreadable frame" }
    }

    /** Reports the vertical centre of the node it is attached to, in root pixels. */
    private fun Modifier.onCentreY(report: (Float) -> Unit): Modifier =
        onGloballyPositioned { report(it.positionInRoot().y + it.size.height / 2f) }

    private companion object {
        const val FrameNanos = 16_000_000L

        /** Inside the left end of the track, where the thumb starts. */
        const val StartX = 40f

        const val MovePx = 8f
        const val Moves = 70

        /**
         * How far back the thumb may drift in one frame.
         *
         * `springSnappy` is underdamped, so a target that jumps forward can be
         * overshot and eased back by a pixel or so. The defect this guards
         * against is a 77px lurch, so there is no need to argue about the last
         * pixel.
         */
        const val Tolerance = 4f
    }
}
