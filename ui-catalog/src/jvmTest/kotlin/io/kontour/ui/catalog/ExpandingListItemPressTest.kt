package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.list.ExpandingListItem
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A tap on an expanding row's header plays its press wash in full, like every other
 * tappable thing in the library.
 *
 * Reported: the row "doesn't do the same hold-the-click-for-a-split-second animation
 * as the rest of the tappable button-like components. Even the accordion does it
 * properly." The accordion's indication has one shape; the header's shape morphs as
 * the row opens, and every step of the morph built a new indication. The clickable
 * swapped the node for each one and threw the held wash away with it, a frame or two
 * after the finger lifted.
 */
class ExpandingListItemPressTest {

    @Test
    fun aQuickTapOnTheHeaderWashesAsDeeplyAsAHeldPress() {
        var bounds = Rect.Zero
        var restingLuma = 0
        var heldLuma = 0
        var tappedLuma = Int.MAX_VALUE

        Scene(width = 600, height = 500) {
            Box(Modifier.fillMaxSize().background(Color.White).padding(16.dp)) {
                var open by remember { mutableStateOf(false) }
                ExpandingListItem(
                    expanded = open,
                    onExpandedChange = { open = it },
                    modifier = Modifier.fillMaxWidth().reportBounds { bounds = it },
                    header = { +"Perth Underground" },
                ) {
                    item(label = "Platform 1")
                    item(label = "Platform 2")
                }
            }
        }.use { scene ->
            val resting = scene.frames(8)
            // Right of the title and left of any chevron: nothing but the row's own fill.
            val probeX = (bounds.left + bounds.width * 0.7f).toInt()
            val probeY = (bounds.top + 24 * 2f).toInt()
            restingLuma = resting.luma(probeX, probeY)

            // A held press, released after its wash has settled, for the depth a tap must reach.
            val at = androidx.compose.ui.geometry.Offset(probeX.toFloat(), probeY.toFloat())
            scene.press(at)
            heldLuma = scene.frames(20).luma(probeX, probeY)
            scene.release(at)
            scene.frames(60)

            // Collapse again with a held press, so the tap below opens the row.
            scene.press(at)
            scene.frames(20)
            scene.release(at)
            scene.frames(60)

            scene.press(at)
            scene.release(at)
            repeat(20) { tappedLuma = minOf(tappedLuma, scene.frame().luma(probeX, probeY)) }
        }

        assertTrue(
            heldLuma < restingLuma - 4,
            "a held press did not darken the header at all ($restingLuma → $heldLuma), so this " +
                "is not measuring the wash",
        )
        assertTrue(
            tappedLuma <= heldLuma + 3,
            "a quick tap took the header's wash only to $tappedLuma, against $heldLuma for a held " +
                "press and $restingLuma at rest — the wash was dropped as the row began to open",
        )
    }

    private fun BufferedImage.luma(x: Int, y: Int): Int {
        val rgb = getRGB(x, y)
        return ((rgb shr 16 and 0xFF) * 299 + (rgb shr 8 and 0xFF) * 587 + (rgb and 0xFF) * 114) / 1000
    }
}
