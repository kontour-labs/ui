package io.kontour.ui.components.display

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.kontour.ui.foundation.SystemIcons
import io.kontour.ui.theme.KontourTheme
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * An icon inside a timeline's node, in every timeline that draws one: the node
 * grows to hold it, and the icon reads against it — light or dark on a solid
 * node, the ring's own colour inside a hollow one.
 */
@OptIn(ExperimentalTestApi::class)
class TimelineNodeIconTest {

    private val node = Color(0xFF1040C0)
    private val scale = 2f

    /** The bounds, in px, of the pixels close to [node] — the one stop given that colour. */
    private class Found(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        val width get() = right - left + 1
        val centreX get() = (left + right) / 2
        val centreY get() = (top + bottom) / 2
    }

    private fun PixelMap.find(colour: Color): Found {
        var l = Int.MAX_VALUE; var t = Int.MAX_VALUE; var r = -1; var b = -1
        for (y in 0 until height) for (x in 0 until width) {
            val p = this[x, y]
            if (abs(p.red - colour.red) < 0.06f && abs(p.green - colour.green) < 0.06f && abs(p.blue - colour.blue) < 0.06f) {
                if (x < l) l = x; if (x > r) r = x; if (y < t) t = y; if (y > b) b = y
            }
        }
        assertTrue(r >= 0, "no node in $colour was drawn")
        return Found(l, t, r, b)
    }

    private fun capture(content: @Composable () -> Unit): PixelMap {
        var image: PixelMap? = null
        runDesktopComposeUiTest(width = 700, height = 500) {
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(scale)) {
                    KontourTheme(darkTheme = false) {
                        Box(Modifier.background(Color.White).padding(8.dp).width(300.dp)) { content() }
                    }
                }
            }
            waitForIdle()
            image = onRoot().captureToImage().toPixelMap()
        }
        return image!!
    }

    private fun assertIconNode(image: PixelMap, filled: Boolean, what: String) {
        val found = image.find(node)
        val diameter = found.width / scale
        assertTrue(diameter in 18.5f..21f, "$what: the node is ${diameter}dp across, not the 20dp an icon gets")
        // A plus crosses the middle of the node, so its centre is the icon's ink.
        val centre = image[found.centreX, found.centreY]
        if (filled) {
            assertTrue(centre.red > 0.85f && centre.green > 0.85f && centre.blue > 0.85f, "$what: the icon on a dark solid node is $centre, not light")
        } else {
            assertTrue(abs(centre.blue - node.blue) < 0.1f && centre.red < 0.3f, "$what: the icon inside a ring is $centre, not the ring's colour")
        }
    }

    @Test
    fun aTimelineItemHoldsItsIcon() {
        for (filled in listOf(true, false)) {
            val image = capture {
                Timeline {
                    TimelineItem(icon = SystemIcons.Plus, nodeColour = node, filled = filled) { +"Perth Busport" }
                    TimelineItem(connector = ConnectorStyle.None) { +"Elizabeth Quay" }
                }
            }
            assertIconNode(image, filled, "Timeline, filled = $filled")
        }
    }

    @Test
    fun aHorizontalTimelineItemHoldsItsIcon() {
        val image = capture {
            HorizontalTimeline {
                TimelineItem { +"Ordered" }
                TimelineItem(icon = SystemIcons.Plus, nodeColour = node) { +"Packed" }
                TimelineItem(connector = ConnectorStyle.None) { +"Delivered" }
            }
        }
        assertIconNode(image, filled = true, "HorizontalTimeline")
    }

    @Test
    fun aTimelineListStopHoldsItsIcon() {
        for (filled in listOf(true, false)) {
            val image = capture {
                TimelineList {
                    item("Perth Busport")
                    item("Walk 4 min", icon = SystemIcons.Plus, nodeColour = node, filled = filled)
                    item("Elizabeth Quay")
                }
            }
            assertIconNode(image, filled, "TimelineList, filled = $filled")
        }
    }

    @Test
    fun aBranchTimelineCommitHoldsItsIcon() {
        val image = capture {
            BranchTimeline(items = listOf("b", "a"), key = { it }, parents = { if (it == "b") listOf("a") else emptyList() }) { id ->
                if (id == "b") item(icon = SystemIcons.Plus, nodeColour = node) { +"Commit b" } else item { +"Commit a" }
            }
        }
        assertIconNode(image, filled = true, "BranchTimeline")
    }

    @Test
    fun theOtherStopsKeepTheirSize() {
        val other = Color(0xFFC02010)
        val image = capture {
            TimelineList {
                item("Perth Busport", icon = SystemIcons.Plus, nodeColour = node)
                item("Elizabeth Quay", nodeColour = other)
            }
        }
        val plain = image.find(other).width / scale
        assertTrue(plain in 11f..13f, "a stop without an icon is ${plain}dp across beside one with, not 12dp")
    }

    @Test
    fun aLoadingStopShowsItsSpinnerAndNoIcon() {
        val image = capture {
            TimelineList {
                item("Perth Busport")
                item("Boarding", loading = true, icon = SystemIcons.Plus, nodeColour = node)
            }
        }
        val spinner = image.find(node).width / scale
        assertTrue(spinner <= 13f, "a loading stop's spinner is ${spinner}dp across: it grew for an icon it does not show")
    }
}
