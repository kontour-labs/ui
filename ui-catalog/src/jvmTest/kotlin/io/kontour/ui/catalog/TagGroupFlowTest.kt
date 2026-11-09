package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.display.TagFlow
import io.kontour.ui.components.display.TagGroup
import io.kontour.ui.components.display.TagJoin
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A flow group's joins point the way it is told to, in the reading direction.
 *
 * Asked for in November 2026, for 0.5.4: *"another option for how they join
 * together, called "flow". it should basically be an arrow between tags. we
 * should be able to control the direction of the flow"*.
 *
 * Found from the pixels: a red part and a blue one on a white page. Where the
 * red reaches furthest towards the blue, along the top of the tag and along its
 * middle, says which way the join points: further along the middle is a point
 * into the blue, less far is a notch taking the blue's point.
 */
class TagGroupFlowTest {

    private val First = Color(0xFFCC0000)
    private val Second = Color(0xFF0000CC)

    private fun red(p: Int) = ((p shr 16) and 0xFF) > 150 && ((p shr 8) and 0xFF) < 60 && (p and 0xFF) < 60

    /**
     * How much further the first part reaches towards the second along the
     * tag's middle than along its top: positive for a point, negative for a
     * notch.
     */
    private fun pointing(flow: TagFlow, direction: LayoutDirection = LayoutDirection.Ltr): Int {
        var image: BufferedImage? = null
        var bounds = Rect.Zero
        Scene(width = 400, height = 120) {
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                Box(Modifier.fillMaxSize().background(Color.White).padding(20.dp)) {
                    TagGroup(Modifier.reportBounds { bounds = it }, join = TagJoin.Flow, flow = flow) {
                        item(containerColour = First) { +"Perth" }
                        item(containerColour = Second) { +"Fremantle" }
                    }
                }
            }
        }.use { image = it.frames(4) }
        val img = image!!
        val rtl = direction == LayoutDirection.Rtl
        // The furthest red pixel towards the second part along a row.
        fun reach(y: Int): Int {
            val xs = (bounds.left.toInt() until bounds.right.toInt()).filter { red(img.getRGB(it, y)) }
            return if (rtl) -xs.min() else xs.max()
        }
        val top = (bounds.top + bounds.height * 0.15f).toInt()
        val middle = (bounds.top + bounds.height * 0.5f).toInt()
        return reach(middle) - reach(top)
    }

    @Test
    fun flowingForwardEachPartPointsIntoTheNext() {
        val by = pointing(TagFlow.Forward)
        assertTrue(by > 2, "the first part's join did not come to a point into the second: $by")
    }

    @Test
    fun flowingBackwardEachPartTakesThePointOfTheNext() {
        val by = pointing(TagFlow.Backward)
        assertTrue(by < -2, "the first part's join was not notched for the second's point: $by")
    }

    @Test
    fun rightToLeftAForwardFlowPointsLeft() {
        val by = pointing(TagFlow.Forward, LayoutDirection.Rtl)
        assertTrue(by > 2, "right to left, the first part did not point into the second, on its left: $by")
    }
}
