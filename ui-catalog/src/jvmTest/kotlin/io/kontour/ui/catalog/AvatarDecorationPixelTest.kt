package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.display.Avatar
import io.kontour.ui.components.display.AvatarCorner
import io.kontour.ui.components.display.AvatarSize
import java.awt.image.BufferedImage
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * An avatar's decorations sit on its rim at 45° — not in the corners of the
 * square round it — ringed in the colour of what the avatar is on, and a start
 * corner is on the right right to left.
 */
class AvatarDecorationPixelTest {

    @Test
    fun aStatusSitsOnTheRimAt45Degrees() {
        for (direction in LayoutDirection.entries) {
            val (image, bounds) = render(direction)
            // The rim at 45°: the radius along each axis from the centre.
            val along = bounds.width / 2f * 0.7071f
            val cx = bounds.center.x
            val cy = bounds.center.y
            val endward = if (direction == LayoutDirection.Ltr) 1 else -1
            val dotX = (cx + endward * along).toInt()
            val dotY = (cy + along).toInt()
            assertTrue(image.near(dotX, dotY, Status), "$direction: the status dot is on the rim at bottom end: ${hex(image.getRGB(dotX, dotY))}")
            // The square's own corner, beyond the rim, is not where it sits.
            val cornerX = (cx + endward * (bounds.width / 2f - 3f)).toInt()
            val cornerY = (cy + bounds.width / 2f - 3f).toInt()
            assertTrue(!image.near(cornerX, cornerY, Status), "$direction: not in the square's corner")
            // The ring: the page's colour between the dot and the picture, towards
            // the centre. The dot is 16dp at most — 16px of radius at density two —
            // and its ring 2dp beyond that, so 18px in along the diagonal is ring.
            val step = (18f * 0.7071f).toInt()
            val ringX = dotX - endward * step
            val ringY = dotY - step
            assertTrue(image.near(ringX, ringY, Page), "$direction: a ring of the page's colour round the dot: ${hex(image.getRGB(ringX, ringY))}")
            // The top start corner carries the other one.
            val startX = (cx - endward * along).toInt()
            val topY = (cy - along).toInt()
            assertTrue(image.near(startX, topY, Other), "$direction: the second status at top start: ${hex(image.getRGB(startX, topY))}")
        }
    }

    private fun render(direction: LayoutDirection): Pair<BufferedImage, Rect> {
        var bounds = Rect.Zero
        lateinit var image: BufferedImage
        Scene(width = 400, height = 400) {
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                Box(Modifier.fillMaxSize().background(Page)) {
                    Avatar(
                        name = "Aaron",
                        size = AvatarSize.XLarge,
                        modifier = Modifier.padding(40.dp).reportBounds { bounds = it },
                    ) {
                        status(Status, "Online")
                        status(Other, "Away", at = AvatarCorner.TopStart)
                    }
                }
            }
        }.use { image = it.frames(4) }
        return image to bounds
    }

    private fun BufferedImage.near(x: Int, y: Int, colour: Color): Boolean {
        val p = getRGB(x, y)
        val c = colour.toArgb()
        return listOf(16, 8, 0).all { shift -> abs((p shr shift and 0xFF) - (c shr shift and 0xFF)) < 16 }
    }

    private fun hex(p: Int) = "#" + (p and 0xFFFFFF).toString(16).padStart(6, '0')

    private companion object {
        val Page = Color(0xFFFFFFFF)
        val Status = Color(0xFF00AA00)
        val Other = Color(0xFFAA00AA)
    }
}
