package io.kontour.ui.catalog

import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import org.jetbrains.skia.EncodedImageFormat
import java.io.File

/**
 * Renders a composable offscreen to a PNG.
 *
 * Runs on the JVM without a display server, an emulator or a simulator, which is
 * the reason the `jvm` target exists at all. Uses `ImageComposeScene` rather
 * than a UI-test rule because goldens want a deterministic single frame, not a
 * live composition being polled.
 */
object Screenshot {

    private val outputDir: File by lazy {
        File(System.getProperty("kontour.screenshots.dir") ?: "screenshots").apply { mkdirs() }
    }

    /**
     * Renders [content] at [width] × [height] and writes it to `<name>.png`.
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

            val file = File(outputDir, "$name.png")
            val encoded = requireNotNull(image.encodeToData(EncodedImageFormat.PNG)) {
                "Skia failed to encode $name to PNG"
            }
            file.writeBytes(encoded.bytes)
            return file
        }
    }

    private const val FRAME_NANOS = 16_000_000L

    private inline fun <T> ImageComposeScene.use(block: (ImageComposeScene) -> T): T =
        try {
            block(this)
        } finally {
            close()
        }
}
