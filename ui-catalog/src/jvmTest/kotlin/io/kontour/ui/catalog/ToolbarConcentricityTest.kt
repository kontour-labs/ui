package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import io.kontour.ui.components.action.ButtonGroup
import io.kontour.ui.components.action.Toolbar
import io.kontour.ui.foundation.SystemIcons
import io.kontour.ui.theme.KontourTheme
import org.jetbrains.skia.EncodedImageFormat
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A toolbar holds its content inside itself.
 *
 * It did not. The toolbar was a pill and a `ButtonGroup` is an 8dp rounded
 * rectangle, and at 4dp of padding the group's corners fell **outside** the
 * pill's curve — so the surface's own clip sheared them off flat. The toolbar's
 * committed render has shown that since the day it was recorded.
 *
 * The fix is concentricity: an outer radius of `inner + padding` puts the two
 * curves exactly parallel, 4dp apart the whole way round, and nothing can reach
 * the edge. That is a statement about radii, and asserting it as one would only
 * be re-reading the constants — so this renders the toolbar over a colour it
 * would never draw and checks that none of it shows through where the group is.
 *
 * Magenta rather than a near-miss grey, because the test is "is this pixel part
 * of the toolbar at all", and everything a toolbar draws is neutral. A hue
 * nothing else has answers that without a tolerance to argue about — including
 * where the drop shadow darkens the backdrop, which is still magenta.
 *
 * ### The canvas has to be big enough
 *
 * The first version of this passed against the defect. The scene was 200dp tall
 * and the padding 40dp, which left the toolbar 20dp to be — squashed to half its
 * height, its pill radius fell to 10dp and there was nothing left to shear. A
 * test of a rounded corner is a test of a *radius*, so anything that shrinks the
 * component shrinks the thing being measured, and a passing run means nothing
 * unless the component got to be its own size.
 */
class ToolbarConcentricityTest {

    @Test
    fun theToolbarDoesNotShearTheCornersOfWhatItHolds() {
        var group = Rect.Zero
        val scene = ImageComposeScene(width = 480, height = 320, density = Density(2f)) {
            KontourTheme(darkTheme = false, reduceMotion = true) {
                Box(Modifier.fillMaxSize().background(Backdrop)) {
                    Toolbar(modifier = Modifier.padding(60.dp)) {
                        ButtonGroup(
                            modifier = Modifier.onGloballyPositioned {
                                group = Rect(it.positionInRoot(), it.size.toSize())
                            },
                        ) {
                            item(onClick = {}, contentDescription = "Out", icon = SystemIcons.Dash)
                            item(onClick = {}, contentDescription = "In", icon = SystemIcons.Plus)
                        }
                    }
                }
            }
        }
        val image = try {
            repeat(3) { scene.render(16_000_000L * it) }
            val png = requireNotNull(scene.render(48_000_000L).encodeToData(EncodedImageFormat.PNG))
            requireNotNull(ImageIO.read(png.bytes.inputStream()))
        } finally {
            scene.close()
        }

        assertTrue(group.width > 0f, "the button group never reported a size")

        var showing = 0
        for (y in group.top.toInt()..group.bottom.toInt()) {
            for (x in group.left.toInt()..group.right.toInt()) {
                if (isBackdrop(image.getRGB(x, y))) showing++
            }
        }

        assertTrue(
            showing <= Tolerance,
            "$showing pixels of the backdrop show through inside the button " +
                "group's own bounds (${group.width}×${group.height} at " +
                "${group.left},${group.top}) — the toolbar's shape is clipping " +
                "the corners of what it holds",
        )
    }

    /**
     * Magenta, or magenta under a drop shadow.
     *
     * Red far above green is the test. Everything a toolbar draws is neutral —
     * white surface, grey fill, near-black glyphs — so nothing it draws passes,
     * while the backdrop passes at any shadow strength short of opaque.
     */
    private fun isBackdrop(rgb: Int): Boolean =
        (rgb shr 16 and 0xFF) - (rgb shr 8 and 0xFF) > 60

    private companion object {
        val Backdrop = Color(0xFFFF00FF)

        /** A pixel or two of antialiasing along the toolbar's own edge. */
        const val Tolerance = 8
    }
}
