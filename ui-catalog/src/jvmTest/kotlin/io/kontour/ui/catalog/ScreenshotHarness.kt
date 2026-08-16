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
        allowOverflow: Boolean = false,
        trim: Int? = null,
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

            if (!allowOverflow) checkFits(name, scene, width, height)

            val rendered = requireNotNull(image.encodeToData(EncodedImageFormat.PNG)) {
                "Skia failed to encode $name to PNG"
            }.bytes
            val encoded = if (trim == null) rendered else trimToContent(rendered, trim)

            val golden = File(outputDir, "$name.png")
            // A name can carry a directory — the per-component pass writes
            // `components/<slug>-light`. Only `outputDir` itself is created
            // above, so anything nested has to make its own way.
            golden.parentFile?.mkdirs()
            if (!golden.exists()) {
                golden.writeBytes(encoded)
                return golden
            }
            if (updating) {
                // Only rewrite what actually moved. The comparison below has a
                // per-pixel tolerance, so a render that is visually identical
                // still encodes to different bytes — and rewriting every golden
                // unconditionally turned "accept this one intended change" into
                // a 41-file diff nobody can review. That defeats the one step
                // this whole harness exists to protect: looking at the result
                // before committing it.
                if (differs(golden, encoded)) golden.writeBytes(encoded)
                return golden
            }

            compare(name, golden, encoded)
            return golden
        }
    }

    /**
     * Crops [rendered] to what was actually drawn, leaving [margin] pixels round it.
     *
     * A per-component render is a picture of a component, and a 48dp button
     * centred in a 300×120dp card was 1.1% button. Rather than choosing a canvas
     * per specimen — a second thing to keep current, and one nobody would notice
     * going stale — the canvas stays generous and the drawn pixels decide the
     * frame.
     *
     * **This measures ink, not layout.** Stage 1a's overflow guard cannot do
     * this job: `calculateContentSize()` measures at unbounded constraints,
     * where a `fillMaxWidth` component collapses to its minimum intrinsic width
     * and reports a height for a column it will never be drawn in. This runs
     * after the draw, so what it sees is what a reader will see.
     *
     * ### It also makes the overflow guard's false positives free
     *
     * That guard is deliberately conservative and sometimes demands a canvas
     * several times taller than the component needs — the expanded `Accordion`
     * measures 554×677 for something that draws 439×87. Before this, satisfying
     * it meant shipping the empty space. Now growing a canvas costs nothing: the
     * crop takes back whatever the component did not use, so the two can both be
     * strict without fighting.
     *
     * The background is read from the corner, the same assumption
     * `ComponentRenderTest.inkedFraction` makes and safe for the same reason —
     * the specimen is centred inside a padded frame. A render with no ink at all
     * is returned untouched, so the blank-card check still sees a full card and
     * reports it.
     */
    private fun trimToContent(rendered: ByteArray, margin: Int): ByteArray {
        val image = ImageIO.read(rendered.inputStream()) ?: return rendered
        val background = image.getRGB(0, 0)

        var left = image.width
        var top = image.height
        var right = -1
        var bottom = -1
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                if (image.getRGB(x, y) == background) continue
                if (x < left) left = x
                if (x > right) right = x
                if (y < top) top = y
                if (y > bottom) bottom = y
            }
        }
        if (right < 0) return rendered

        val x0 = maxOf(0, left - margin)
        val y0 = maxOf(0, top - margin)
        val x1 = minOf(image.width - 1, right + margin)
        val y1 = minOf(image.height - 1, bottom + margin)

        val cropped = BufferedImage(x1 - x0 + 1, y1 - y0 + 1, BufferedImage.TYPE_INT_RGB)
        cropped.createGraphics().apply {
            drawImage(image, -x0, -y0, null)
            dispose()
        }
        return java.io.ByteArrayOutputStream().also { ImageIO.write(cropped, "png", it) }.toByteArray()
    }

    /**
     * Records a mismatch if the content did not fit the canvas.
     *
     * This is the guard the whole harness was missing, and it went missing in
     * the worst possible direction: **a page that outgrows its canvas fails
     * silently.** The sections that no longer fit are not clipped at a visible
     * edge — they are simply never drawn, so the golden does not move, the run
     * passes, and the page goes on claiming coverage of a component it never
     * rendered. That happened twice in one round. The mitigation until now was
     * a comment asking whoever adds a section to remember to check.
     *
     * The other half is nearly as bad: content that *nearly* fits is drawn on
     * top of the heading above it, which reads as a layout bug in the component
     * rather than as a canvas that is too short.
     *
     * ### What it can and cannot see
     *
     * `calculateContentSize()` measures at **unbounded** constraints, and that
     * decides what this can answer.
     *
     * **Width is reliable.** Nothing inflates it: content that wants 1614px
     * wants 1614px. This is what caught the theme page silently losing 514px of
     * swatches off its right edge — clipped mid-word, and goldened that way
     * since the day it was recorded.
     *
     * **Height is only reliable when the content wanted roughly the canvas's
     * width.** A `fillMaxWidth` component measured unbounded collapses to its
     * *minimum intrinsic* width — `AnimatedBanner` reports 112×1003 for
     * something that draws 600×90 — and the height that falls out of a 112px
     * column says nothing about the height at 600px. So the height is only
     * compared when the measured width is at least [WIDTH_CONFIDENCE] of the
     * canvas. Below that the measurement collapsed and this makes no claim.
     *
     * A scrolling container refuses the unbounded measure outright and is
     * skipped; so is anything passing `allowOverflow`, for the one page that is
     * meant to scroll.
     */
    @OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
    private fun checkFits(name: String, scene: ImageComposeScene, width: Int, height: Int) {
        val content = try {
            scene.calculateContentSize()
        } catch (unmeasurable: IllegalStateException) {
            // The measurement runs at infinite constraints, and a scrolling
            // container refuses that outright — "Horizontally scrollable
            // component was measured with an infinity maximum width". There is
            // no size to compare against, and there does not need to be: a
            // scrollable is *supposed* to hold more than fits. `Carousel`'s
            // specimen is the case in hand.
            return
        }
        val tooWide = content.width > width
        // Only trust the height if the content actually wanted this much width.
        val heightIsMeaningful = content.width >= width * WIDTH_CONFIDENCE
        val tooTall = heightIsMeaningful && content.height > height
        if (!tooWide && !tooTall) return

        val needWidth = maxOf(content.width, width)
        val needHeight = if (tooTall) maxOf(content.height, height) else height
        mismatches += "$name does not fit its canvas: content measures " +
            "${content.width}×${content.height}, canvas is ${width}×$height. " +
            "Anything past the edge is not drawn at all — the golden would not " +
            "move and the run would pass. Grow the canvas in the test to at " +
            "least ${needWidth}×$needHeight, or pass allowOverflow = true if " +
            "this one is meant to scroll."
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

    /**
     * Whether [rendered] is visually different from the golden on disk.
     *
     * The same question [compare] asks, and deliberately the same tolerance:
     * a render this returns `false` for is one that would have passed, so
     * rewriting it would record a change that no run would ever have reported.
     */
    private fun differs(golden: File, rendered: ByteArray): Boolean {
        val expected = ImageIO.read(golden) ?: return true
        val actual = ImageIO.read(rendered.inputStream()) ?: return true
        if (expected.width != actual.width || expected.height != actual.height) return true

        var differing = 0
        for (y in 0 until expected.height) {
            for (x in 0 until expected.width) {
                if (channelsDiffer(expected.getRGB(x, y), actual.getRGB(x, y))) differing++
            }
        }
        return differing.toDouble() / (expected.width * expected.height) > TOLERATED_FRACTION
    }

    /** True when any channel differs by more than [CHANNEL_TOLERANCE]. */
    private fun channelsDiffer(a: Int, b: Int): Boolean {
        if (a == b) return false
        return abs((a shr 16 and 0xFF) - (b shr 16 and 0xFF)) > CHANNEL_TOLERANCE ||
            abs((a shr 8 and 0xFF) - (b shr 8 and 0xFF)) > CHANNEL_TOLERANCE ||
            abs((a and 0xFF) - (b and 0xFF)) > CHANNEL_TOLERANCE
    }

    /**
     * Writes the actual render and the diff for a mismatch.
     *
     * `mkdirs` on the leaf rather than on [diffDir], because a name can carry a
     * directory — the per-component pass writes `components/<slug>-light`, the
     * same way [render] does for the golden itself. Missing that meant the first
     * component render ever to mismatch threw `FileNotFoundException` from
     * inside the reporting path, so the run failed with a stack trace where the
     * comparison should have been. It had been latent since per-component
     * renders landed: nothing in that directory had mismatched until now.
     */
    private fun writeEvidence(name: String, rendered: ByteArray, diff: BufferedImage?) {
        val actual = File(diffDir, "$name-actual.png")
        actual.parentFile?.mkdirs()
        actual.writeBytes(rendered)
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
     * How much of the canvas's width the content must want before its measured
     * *height* is believed.
     *
     * Below this the unbounded measure collapsed the content into a narrow
     * column and the height it reports is that column's, not the canvas's.
     * Three quarters is comfortably above the ~20% a collapsed `fillMaxWidth`
     * reports and comfortably below the ~100% a page that genuinely fills its
     * canvas reports.
     */
    private const val WIDTH_CONFIDENCE = 0.75

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
