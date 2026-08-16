package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.text.Select
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.KontourTheme
import org.jetbrains.skia.EncodedImageFormat
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A select has the same amount of clear space at each end.
 *
 * It did not, and the reason is that a glyph does not fill its own box.
 * `iconMedium` is 20dp holding 12dp of chevron, so padding the icon like the
 * text put 4dp of the icon's own margin on top of the 12dp of padding: the
 * value's ink started 13dp in from the left and the chevron's ink stopped 16dp
 * in from the right. Measured, not eyeballed — that is what this asserts.
 *
 * The fix is a smaller padding on a side that holds an icon, which is what every
 * icon set's grid margin has always implied and what this library had not said
 * anywhere.
 *
 * ### Ink, not layout
 *
 * A layout assertion would have passed the whole time: the padding *was*
 * symmetric, 12dp on each side, and that was the defect. What is asymmetric is
 * what you can see, so this measures the pixels — the outermost ink is the
 * field's border, and the ink inside that border is its contents.
 */
class FieldEdgeInsetTest {

    @Test
    fun aSelectsValueAndItsChevronAreEquallyInset() {
        val image = render {
            Select(
                value = "Now",
                onValueChange = {},
                options = listOf("Now", "Later"),
                optionLabel = { it },
                modifier = Modifier.padding(20.dp),
            )
        }

        val frame = ink(image, 0, 0, image.width - 1, image.height - 1, threshold = 250)
            ?: error("the select drew nothing")
        // Six pixels in from the border, so its own stroke is not counted as
        // content. The border is 1dp — two pixels here — and its antialiasing
        // stays darker than the threshold for a couple more; at three pixels in
        // this test measured the border on both sides, found it symmetric, and
        // passed against the defect it was written for.
        val content = ink(image, frame[0] + Border, frame[1] + Border, frame[2] - Border, frame[3] - Border, threshold = 200)
            ?: error("the select drew no content")

        val left = content[0] - frame[0]
        val right = frame[2] - content[2]

        assertTrue(
            abs(left - right) <= Tolerance,
            "the value's ink starts ${left}px from the left of the field and the " +
                "chevron's ink stops ${right}px from the right, a difference of " +
                "${abs(left - right)}px at 2x — the two ends do not look equally " +
                "padded",
        )
    }

    private fun render(content: @Composable () -> Unit): BufferedImage {
        val scene = ImageComposeScene(width = 600, height = 200, density = Density(2f)) {
            KontourTheme(darkTheme = false, reduceMotion = true) {
                // A `Select` opens a menu, and a menu needs somewhere to go.
                OverlayHost(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().background(Color.White)) { content() }
                }
            }
        }
        return try {
            repeat(4) { scene.render(16_000_000L * it) }
            val png = requireNotNull(scene.render(80_000_000L).encodeToData(EncodedImageFormat.PNG))
            requireNotNull(ImageIO.read(png.bytes.inputStream()))
        } finally {
            scene.close()
        }
    }

    /** `[left, top, right, bottom]` of pixels darker than [threshold] in any channel. */
    private fun ink(
        image: BufferedImage,
        x0: Int,
        y0: Int,
        x1: Int,
        y1: Int,
        threshold: Int,
    ): IntArray? {
        var left = Int.MAX_VALUE
        var top = Int.MAX_VALUE
        var right = -1
        var bottom = -1
        for (y in y0..y1) {
            for (x in x0..x1) {
                val rgb = image.getRGB(x, y)
                val darkest = minOf(rgb shr 16 and 0xFF, rgb shr 8 and 0xFF, rgb and 0xFF)
                if (darkest >= threshold) continue
                if (x < left) left = x
                if (x > right) right = x
                if (y < top) top = y
                if (y > bottom) bottom = y
            }
        }
        return if (right < 0) null else intArrayOf(left, top, right, bottom)
    }

    private companion object {
        /**
         * 1dp at this density.
         *
         * The two things being compared are a letter and a stroked glyph, drawn
         * with different antialiasing against the same threshold, so they are
         * never going to agree to the pixel. The defect was 6px.
         */
        const val Tolerance = 2

        /** Clear of the field's own 1dp border and its antialiasing. */
        const val Border = 6
    }
}
