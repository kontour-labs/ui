package io.kontour.ui.catalog

import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import org.jetbrains.skia.EncodedImageFormat
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.test.fail

/**
 * Renders a composable offscreen to a PNG and checks it against the committed
 * golden.
 *
 * Runs on the JVM without a display server, an emulator or a simulator, which is
 * the reason the `jvm` target exists at all. Uses `ImageComposeScene` rather
 * than a UI-test rule because goldens want a deterministic single frame, not a
 * live composition being polled.
 *
 * ### Comparing, not just recording
 *
 * A harness that overwrites its goldens every run is documentation, not a test:
 * the diff still shows up in `git status`, but only if someone looks, and a
 * rendering regression lands green. So [render] **fails** on a mismatch and
 * writes the evidence to `build/screenshot-diffs/`:
 *
 * ```
 * <name>-actual.png    what was rendered
 * <name>-diff.png      the golden, with every differing pixel in magenta
 * ```
 *
 * To accept a change — because it is the change you meant to make — re-run with
 * `-Pkontour.screenshots.update=true` and review the resulting diff before
 * committing it. That is the step that matters: a golden nobody looked at pins
 * whatever was broken when it was recorded.
 *
 * ### Every mismatch, not the first one
 *
 * [render] records a mismatch and carries on; [assertAllMatched] is what fails,
 * from an `@AfterTest`. Throwing from [render] would end the loop its caller is
 * in, and every one of these tests renders the same showcase in four schemes —
 * so a change that moved all four reported one, and you found the other three by
 * fixing that one and running again. Four runs of a ninety-second suite to learn
 * something the first run already knew.
 */
object Screenshot {

    private val mismatches = mutableListOf<String>()

    /**
     * Fails with everything that differed since the last call, and clears it.
     *
     * Call from an `@AfterTest`. Nothing recorded, nothing thrown.
     */
    fun assertAllMatched() {
        if (mismatches.isEmpty()) return
        val all = mismatches.joinToString("\n\n")
        mismatches.clear()
        fail(all)
    }

    private val outputDir: File by lazy {
        File(System.getProperty("kontour.screenshots.dir") ?: "screenshots").apply { mkdirs() }
    }

    private val diffDir: File by lazy {
        File(System.getProperty("kontour.screenshots.diffDir") ?: "build/screenshot-diffs")
    }

    private val updating: Boolean by lazy {
        System.getProperty("kontour.screenshots.update").toBoolean()
    }

    /**
     * Renders [content] at [width] × [height] and compares it to `<name>.png`.
     *
     * Renders more than one frame on purpose. Fonts come from Compose Resources
     * and resolve asynchronously — the first frame draws with a fallback family,
     * and the real typeface only lands once the load completes and triggers
     * recomposition. Rendering a single frame would silently golden the wrong
     * font.
     */
    fun render(
        name: String,
        width: Int,
        height: Int,
        density: Float = 2f,
        frames: Int = 6,
        content: @Composable () -> Unit,
    ): File {
        ImageComposeScene(
            width = width,
            height = height,
            density = Density(density),
            content = content,
        ).use { scene ->
            var image = scene.render(0L)
            repeat(frames - 1) { frame ->
                image = scene.render(FRAME_NANOS * (frame + 1))
            }

            val encoded = requireNotNull(image.encodeToData(EncodedImageFormat.PNG)) {
                "Skia failed to encode $name to PNG"
            }.bytes

            val golden = File(outputDir, "$name.png")
            if (updating || !golden.exists()) {
                golden.writeBytes(encoded)
                return golden
            }

            compare(name, golden, encoded)
            return golden
        }
    }

    /**
     * Records a mismatch if [rendered] differs from [golden] by more than
     * [TOLERATED_FRACTION]. [assertAllMatched] is what turns that into a failure.
     *
     * Pixels are compared with a small per-channel tolerance rather than by
     * bytes. Byte equality would be stricter but would also fail on a Skia point
     * release re-rounding one antialiased edge, and a suite that cries wolf gets
     * regenerated without being read — which is the failure mode this is trying
     * to prevent.
     */
    private fun compare(name: String, golden: File, rendered: ByteArray) {
        val expected = ImageIO.read(golden)
            ?: fail("$name: the committed golden is not a readable PNG")
        val actual = ImageIO.read(rendered.inputStream())
            ?: fail("$name: the render produced an unreadable PNG")

        if (expected.width != actual.width || expected.height != actual.height) {
            writeEvidence(name, rendered, diff = null)
            mismatches += "$name changed size: golden is " +
                "${expected.width}×${expected.height}, render is " +
                "${actual.width}×${actual.height}. If the showcase grew, update " +
                "the canvas in the test and re-record."
            return
        }

        val diff = BufferedImage(expected.width, expected.height, BufferedImage.TYPE_INT_RGB)
        var differing = 0
        for (y in 0 until expected.height) {
            for (x in 0 until expected.width) {
                val a = expected.getRGB(x, y)
                val b = actual.getRGB(x, y)
                if (channelsDiffer(a, b)) {
                    differing++
                    diff.setRGB(x, y, DIFF_COLOUR)
                } else {
                    diff.setRGB(x, y, a)
                }
            }
        }

        val total = expected.width * expected.height
        val fraction = differing.toDouble() / total
        if (fraction > TOLERATED_FRACTION) {
            writeEvidence(name, rendered, diff)
            val percent = (fraction * 100).toString().take(5)
            mismatches += "$name differs from its golden: $differing of $total pixels " +
                "($percent%). See ${diffDir.absolutePath}/$name-{actual,diff}.png. " +
                "If the change is intended, re-run with " +
                "-Pkontour.screenshots.update=true and review the new golden."
        }
    }

    /** True when any channel differs by more than [CHANNEL_TOLERANCE]. */
    private fun channelsDiffer(a: Int, b: Int): Boolean {
        if (a == b) return false
        return abs((a shr 16 and 0xFF) - (b shr 16 and 0xFF)) > CHANNEL_TOLERANCE ||
            abs((a shr 8 and 0xFF) - (b shr 8 and 0xFF)) > CHANNEL_TOLERANCE ||
            abs((a and 0xFF) - (b and 0xFF)) > CHANNEL_TOLERANCE
    }

    private fun writeEvidence(name: String, rendered: ByteArray, diff: BufferedImage?) {
        diffDir.mkdirs()
        File(diffDir, "$name-actual.png").writeBytes(rendered)
        if (diff != null) ImageIO.write(diff, "png", File(diffDir, "$name-diff.png"))
    }

    private const val FRAME_NANOS = 16_000_000L

    /**
     * How far one channel may drift before a pixel counts as changed.
     *
     * Wide enough to absorb antialiasing that re-rounds along an edge, narrow
     * enough that a colour token moving a shade still fails.
     */
    private const val CHANNEL_TOLERANCE = 8

    /**
     * How much of the image may change before the test fails.
     *
     * A tenth of a percent of a 1100×2080 canvas is about 2,300 pixels — a
     * scattering of edge pixels, but far less than any glyph, icon or component.
     */
    private const val TOLERATED_FRACTION = 0.001

    private const val DIFF_COLOUR = 0xFFFF00FF.toInt()

    private inline fun <T> ImageComposeScene.use(block: (ImageComposeScene) -> T): T =
        try {
            block(this)
        } finally {
            close()
        }
}
