package io.kontour.ui.catalog

import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.toSize
import io.kontour.ui.theme.KontourTheme
import org.jetbrains.skia.EncodedImageFormat
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

/**
 * Drives real pointer events through an offscreen scene.
 *
 * Round 8 wired gestures onto nine components, and a gesture is the one thing a
 * golden cannot photograph: what changed is what the component *does*, and at
 * rest it draws exactly what it drew before. So each of them is tested by
 * performing the gesture and checking what came out — which is also the only way
 * to be sure the drag is reachable at all, rather than that a state object it is
 * wired to behaves correctly in isolation.
 *
 * Touch rather than mouse throughout. A mouse drag needs the button state
 * threaded through every move event and none of these gestures care which
 * pointer they came from; touch is the one a phone actually sends.
 */
class Scene(
    val width: Int,
    val height: Int,
    val density: Float = 2f,
    reduceMotion: Boolean = false,
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) : AutoCloseable {

    private val scene = ImageComposeScene(
        width = width,
        height = height,
        density = Density(density),
    ) {
        KontourTheme(darkTheme = darkTheme, reduceMotion = reduceMotion) { content() }
    }

    private var nanos = 0L

    /** Renders the next frame, 16ms on from the last. */
    fun frame(): BufferedImage {
        nanos += FrameNanos
        val png = requireNotNull(scene.render(nanos).encodeToData(EncodedImageFormat.PNG)) {
            "Skia failed to encode a frame"
        }.bytes
        return requireNotNull(ImageIO.read(png.inputStream())) { "unreadable frame" }
    }

    /** Renders [count] frames and returns the last. */
    fun frames(count: Int): BufferedImage {
        var last = frame()
        repeat(count - 1) { last = frame() }
        return last
    }

    /**
     * Renders until [until] holds, or until [timeoutMillis] of **real** time has
     * passed. Returns the frame that satisfied it, or null if none did.
     *
     * For the one thing a frame count cannot express: a component that times
     * itself out with `delay`. This scene's clock is [FrameNanos] per rendered
     * frame and `delay` runs on the wall clock, and the two have no fixed
     * relationship — a frame here costs about 45ms of real time on a throttled
     * container and rather less on a CI runner, so "render 95 frames" means
     * 3.6 seconds on one machine and under 1.5 on another.
     *
     * `ToastStackTest` was written believing 95 frames was two seconds, waiting
     * for a toast whose `durationMillis` is 1,500. It passed on the machine it
     * was written on and began failing on CI the day `:ui-catalog` grew enough
     * other tests to change what it was sharing a runner with.
     *
     * So: wait for the event, on a deadline measured in the same units the
     * component uses.
     */
    fun renderUntil(timeoutMillis: Long = 15_000, until: (BufferedImage) -> Boolean): BufferedImage? {
        val deadline = System.nanoTime() + timeoutMillis * 1_000_000
        while (System.nanoTime() < deadline) {
            val image = frame()
            if (until(image)) return image
        }
        return null
    }

    /**
     * Presses at [from], travels to [to] over [steps] moves, and releases.
     *
     * A frame is rendered after every move, so anything watching the drag —
     * an animation, a threshold, a state object — advances the way it would on a
     * device. [onFrame] sees each of those frames, for a test that needs the
     * middle of a gesture rather than its result.
     */
    fun drag(
        from: Offset,
        to: Offset,
        steps: Int = 20,
        release: Boolean = true,
        pointer: PointerType = PointerType.Touch,
        onFrame: (Int, BufferedImage) -> Unit = { _, _ -> },
    ) {
        press(from, pointer)
        repeat(steps) { step ->
            val t = (step + 1).toFloat() / steps
            move(Offset(from.x + (to.x - from.x) * t, from.y + (to.y - from.y) * t), pointer)
            onFrame(step, frame())
        }
        if (release) release(to, pointer)
    }

    /** Presses and releases at [at], with a frame in between. */
    fun tap(at: Offset) {
        press(at)
        frame()
        release(at)
        frame()
    }

    fun press(at: Offset, pointer: PointerType = PointerType.Touch) =
        send(PointerEventType.Press, at, pointer)

    fun move(to: Offset, pointer: PointerType = PointerType.Touch) =
        send(PointerEventType.Move, to, pointer)

    fun release(at: Offset, pointer: PointerType = PointerType.Touch) =
        send(PointerEventType.Release, at, pointer)

    /** A scroll wheel notch at [at]. [delta] is in wheel units, not pixels. */
    fun scroll(at: Offset, delta: Offset) {
        scene.sendPointerEvent(
            eventType = PointerEventType.Scroll,
            position = at,
            scrollDelta = delta,
            timeMillis = nanos / 1_000_000L,
            type = PointerType.Mouse,
        )
    }

    private fun send(type: PointerEventType, at: Offset, pointer: PointerType) {
        scene.sendPointerEvent(
            eventType = type,
            position = at,
            timeMillis = nanos / 1_000_000L,
            type = pointer,
        )
    }

    override fun close() = scene.close()

    private companion object {
        const val FrameNanos = 16_000_000L
    }
}

/** Reports this node's bounds in root pixels, for aiming a gesture at it. */
fun Modifier.reportBounds(report: (Rect) -> Unit): Modifier =
    onGloballyPositioned { report(Rect(it.positionInRoot(), it.size.toSize())) }

/** A point [fraction] of the way across [Rect], vertically centred. */
fun Rect.alongX(fraction: Float): Offset = Offset(left + width * fraction, center.y)

/** A point [fraction] of the way down [Rect], horizontally centred. */
fun Rect.alongY(fraction: Float): Offset = Offset(center.x, top + height * fraction)

/**
 * The horizontal runs of ink in [row], as `first..last` pixel ranges.
 *
 * For finding a thumb. A slider's thumb is 22dp across and its track is 6dp, so
 * a row a few pixels below the topmost ink crosses the thumb and nothing else —
 * one run per thumb, and the midpoint of a run is where that thumb is. It stays
 * true while a thumb is scaled up mid-drag, which is why it is a midpoint rather
 * than an edge.
 */
fun BufferedImage.runsIn(row: Int, background: Int = getRGB(0, 0)): List<IntRange> {
    val runs = mutableListOf<IntRange>()
    var start = -1
    for (x in 0 until width) {
        val inked = differsFrom(getRGB(x, row), background)
        if (inked && start < 0) start = x
        if (!inked && start >= 0) {
            runs += start..(x - 1)
            start = -1
        }
    }
    if (start >= 0) runs += start..(width - 1)
    return runs
}

/** The first row containing any ink — the crown of whatever is tallest. */
fun BufferedImage.crownRow(background: Int = getRGB(0, 0)): Int =
    (0 until height).firstOrNull { y ->
        (0 until width).any { x -> differsFrom(getRGB(x, y), background) }
    } ?: error("nothing was drawn at all")

/** Generous, so antialiasing along an edge is not mistaken for ink. */
private fun differsFrom(a: Int, b: Int): Boolean =
    kotlin.math.abs((a shr 16 and 0xFF) - (b shr 16 and 0xFF)) > 24 ||
        kotlin.math.abs((a shr 8 and 0xFF) - (b shr 8 and 0xFF)) > 24 ||
        kotlin.math.abs((a and 0xFF) - (b and 0xFF)) > 24

/** The centre of a run, as a float. */
fun IntRange.centre(): Float = (first + last) / 2f
