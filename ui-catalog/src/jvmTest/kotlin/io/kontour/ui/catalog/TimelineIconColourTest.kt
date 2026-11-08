package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import io.kontour.ui.components.display.ConnectorStyle
import io.kontour.ui.components.display.Timeline
import io.kontour.ui.components.display.TimelineDefaults
import io.kontour.ui.components.display.TimelineItem
import io.kontour.ui.components.display.TimelineList
import io.kontour.icons.tabler.Tabler
import io.kontour.icons.tabler.outline.Plus
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * An item's dot icon takes the colour it is given, and picks one that reads when
 * it is not given one. Asked for in November 2026: *"i'd like to have the
 * ability to control the colour (tint) of the icon"*.
 *
 * Found from the pixels: a white page, a blue dot and a plus in it, red when
 * asked for and the dot's own light ink when not.
 */
class TimelineIconColourTest {

    private val Dot = Color(0xFF0000FF)
    private val Asked = Color(0xFFFF0000)

    private fun BufferedImage.count(test: (Int, Int, Int) -> Boolean): Int {
        var n = 0
        for (y in 0 until height) for (x in 0 until width) {
            val p = getRGB(x, y)
            if (test((p shr 16) and 0xFF, (p shr 8) and 0xFF, p and 0xFF)) n++
        }
        return n
    }

    private fun red(r: Int, g: Int, b: Int) = r > 200 && g < 60 && b < 60
    private fun white(r: Int, g: Int, b: Int) = r > 230 && g > 230 && b > 230

    private fun timelineItem(colour: Color): BufferedImage {
        var image: BufferedImage? = null
        Scene(width = 400, height = 200) {
            Box(Modifier.fillMaxSize().background(Color.White)) {
                Timeline {
                    TimelineItem(
                        connector = ConnectorStyle.None,
                        nodeColour = Dot,
                        icon = Tabler.Outline.Plus,
                        iconColour = colour,
                        nodeSize = TimelineDefaults.IconNodeSize,
                    ) { +"Perth" }
                }
            }
        }.use { image = it.frames(4) }
        return image!!
    }

    private fun timelineList(colour: Color): BufferedImage {
        var image: BufferedImage? = null
        Scene(width = 400, height = 200) {
            Box(Modifier.fillMaxSize().background(Color.White)) {
                TimelineList {
                    item(label = "Perth", nodeColour = Dot, icon = Tabler.Outline.Plus, iconColour = colour, connector = ConnectorStyle.None)
                }
            }
        }.use { image = it.frames(4) }
        return image!!
    }

    @Test
    fun aTimelineItemsIconTakesTheColourItIsGiven() {
        assertTrue(timelineItem(Asked).count(::red) > 4, "no red plus in the dot")
        assertTrue(timelineItem(Color.Unspecified).count(::red) == 0, "a red plus without asking for one")
    }

    @Test
    fun aTimelineListRowsIconTakesTheColourItIsGiven() {
        assertTrue(timelineList(Asked).count(::red) > 4, "no red plus in the row's node")
        assertTrue(timelineList(Color.Unspecified).count(::red) == 0, "a red plus without asking for one")
    }
}
