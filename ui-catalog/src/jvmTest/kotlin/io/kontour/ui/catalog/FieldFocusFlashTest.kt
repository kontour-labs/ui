package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.text.TextField
import io.kontour.ui.theme.KontourTheme
import org.jetbrains.skia.EncodedImageFormat
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A field's ground goes straight from where it was to where it is going.
 *
 * It went via near-black. `Color.Transparent` is *black* with an alpha of zero,
 * and colour interpolation moves the channels as well as the alpha, so an
 * outlined field animating its ground between "nothing" and the focus tint faded
 * through a half-opaque dark grey — for two frames, in both directions. That is
 * the reported flash on focus and the reported snap on blur, and there was never
 * anything wrong with the animation itself.
 *
 * The invariant is the simplest true statement about a two-colour fade: **every
 * frame lies between the ends**. It rules out the grey without hard-coding
 * either colour, so it keeps holding if the accent moves.
 *
 * Sampled well inside the right-hand edge, away from the text, the cursor and
 * the border, all of which are also animating and none of which are the ground.
 */
class FieldFocusFlashTest {

    @Test
    fun theGroundNeverDarkensOnItsWayToTheFocusTint() {
        val trace = groundThroughFocusAndBack()

        val resting = trace.first()
        val focused = trace[FocusFrames]
        assertTrue(
            resting - focused > 4,
            "the field's ground measured $resting unfocused and $focused focused, " +
                "which is not enough of a difference for this to be measuring the " +
                "focus tint at all",
        )

        val floor = minOf(resting, focused) - Tolerance
        val darkest = trace.min()
        assertTrue(
            darkest >= floor,
            "the ground reached a luminance of $darkest during the transition, " +
                "below both ends of it ($resting unfocused, $focused focused) — " +
                "it is fading through something darker than either.\n" +
                trace.mapIndexed { i, v -> "  frame $i: $v" }.joinToString("\n"),
        )
    }

    /** Luminance of the field's ground, frame by frame, focusing then blurring. */
    private fun groundThroughFocusAndBack(): List<Int> {
        var wantFocus by mutableStateOf(false)
        val scene = ImageComposeScene(width = 600, height = 160, density = Density(2f)) {
            KontourTheme(darkTheme = false, reduceMotion = false) {
                val requester = remember { FocusRequester() }
                val focus = LocalFocusManager.current
                LaunchedEffect(wantFocus) {
                    if (wantFocus) runCatching { requester.requestFocus() } else focus.clearFocus()
                }
                Box(Modifier.fillMaxSize().background(Color.White)) {
                    TextField(
                        state = rememberTextFieldState("Perth"),
                        modifier = Modifier.padding(20.dp).focusRequester(requester),
                    )
                }
            }
        }

        var nanos = 0L
        fun shot(): BufferedImage {
            nanos += 16_000_000L
            val png = requireNotNull(scene.render(nanos).encodeToData(EncodedImageFormat.PNG))
            return requireNotNull(ImageIO.read(png.bytes.inputStream()))
        }

        return try {
            repeat(3) { shot() }
            val settled = shot()
            val frame = inkBounds(settled) ?: error("the field drew nothing")
            // Forty pixels in from the right border and halfway down: past the
            // border and its antialiasing, nowhere near the value or the cursor.
            val x = frame[2] - 40
            val y = (frame[1] + frame[3]) / 2

            val trace = mutableListOf(luminance(settled.getRGB(x, y)))
            wantFocus = true
            repeat(FocusFrames) { trace += luminance(shot().getRGB(x, y)) }
            wantFocus = false
            repeat(FocusFrames) { trace += luminance(shot().getRGB(x, y)) }
            trace
        } finally {
            scene.close()
        }
    }

    private fun luminance(rgb: Int): Int =
        ((rgb shr 16 and 0xFF) * 30 + (rgb shr 8 and 0xFF) * 59 + (rgb and 0xFF) * 11) / 100

    /** `[left, top, right, bottom]` of everything drawn. */
    private fun inkBounds(image: BufferedImage): IntArray? {
        var left = Int.MAX_VALUE
        var top = Int.MAX_VALUE
        var right = -1
        var bottom = -1
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                val rgb = image.getRGB(x, y)
                val darkest = minOf(rgb shr 16 and 0xFF, rgb shr 8 and 0xFF, rgb and 0xFF)
                if (darkest >= 250) continue
                if (x < left) left = x
                if (x > right) right = x
                if (y < top) top = y
                if (y > bottom) bottom = y
            }
        }
        return if (right < 0) null else intArrayOf(left, top, right, bottom)
    }

    private companion object {
        /** Long enough for `tweenFast` to finish either way. */
        const val FocusFrames = 14

        /** A shade, for rounding. The defect was worth about forty. */
        const val Tolerance = 4
    }
}
