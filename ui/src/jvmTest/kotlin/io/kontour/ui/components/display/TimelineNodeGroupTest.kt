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
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.kontour.ui.theme.KontourTheme
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A node group: one row on a timeline standing for several stops, its node a
 * few nodes bunched along the rail — the last whole, each before it cut back
 * where the next covers it — in every timeline that has rows.
 */
@OptIn(ExperimentalTestApi::class)
class TimelineNodeGroupTest {

    private val node = Color(0xFF1040C0)
    private val leg = Color(0xFFC02010)
    private val scale = 2f
    private val diameter = TimelineNodeSize.value

    private class Found(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        val width get() = right - left + 1
        val height get() = bottom - top + 1
        val centreX get() = (left + right) / 2
        val centreY get() = (top + bottom) / 2
    }

    private fun PixelMap.near(x: Int, y: Int, colour: Color, slack: Float = 0.08f): Boolean {
        val p = this[x, y]
        return abs(p.red - colour.red) < slack && abs(p.green - colour.green) < slack && abs(p.blue - colour.blue) < slack
    }

    private fun PixelMap.find(colour: Color): Found {
        var l = Int.MAX_VALUE; var t = Int.MAX_VALUE; var r = -1; var b = -1
        for (y in 0 until height) for (x in 0 until width) {
            if (near(x, y, colour)) {
                if (x < l) l = x; if (x > r) r = x; if (y < t) t = y; if (y > b) b = y
            }
        }
        assertTrue(r >= 0, "nothing in $colour was drawn")
        return Found(l, t, r, b)
    }

    private fun capture(content: @Composable () -> Unit): PixelMap {
        var image: PixelMap? = null
        runDesktopComposeUiTest(width = 700, height = 600) {
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(scale)) {
                    KontourTheme(darkTheme = false, reduceMotion = true) {
                        Box(Modifier.background(Color.White).padding(8.dp).width(300.dp)) { content() }
                    }
                }
            }
            waitForIdle()
            image = onRoot().captureToImage().toPixelMap()
        }
        return image!!
    }

    /** A stack of [stack] nodes along the rail is a node across it and a node and its steps along it. */
    private fun assertStack(found: Found, stack: Int, across: Boolean, what: String) {
        val long = (if (across) found.width else found.height) / scale
        val wide = (if (across) found.height else found.width) / scale
        val expected = diameter * (1f + (stack - 1) * GroupStep)
        assertTrue(abs(long - expected) < 1.5f, "$what: the stack is ${long}dp along the rail, not ${expected}dp")
        assertTrue(abs(wide - diameter) < 1.5f, "$what: the stack is ${wide}dp across the rail, not one node's ${diameter}dp")
    }

    @Test
    fun theStackIsCentredAndEvenlyStepped() {
        assertEquals(6f, stackHalfLength(6f, 1), "one node is its own half length")
        val centres = stackCentres(100f, 6f, 3)
        assertEquals(3, centres.size)
        assertEquals(100f, (centres.first() + centres.last()) / 2f, 0.001f)
        assertEquals(6f, centres[1] - centres[0], 0.001f)
        assertEquals(stackHalfLength(6f, 3), centres.last() + 6f - 100f, 0.001f)
    }

    @Test
    fun aTimelineListGroupBunchesUpToThreeNodes() {
        for ((count, stack) in listOf(1 to 1, 2 to 2, 10 to 3)) {
            val image = capture {
                TimelineList {
                    item("Hospital Av")
                    nodeGroup("Show $count stops", count = count, nodeColour = node)
                    item("Elizabeth Quay")
                }
            }
            assertStack(image.find(node), stack, across = false, "TimelineList, $count stops")
        }
    }

    @Test
    fun theLastNodeIsWholeAndTheOnesBeforeItAreCutBack() {
        val image = capture {
            TimelineList {
                item("Hospital Av")
                nodeGroup("Show 10 stops", count = 10, nodeColour = node, filled = true)
                item("Elizabeth Quay")
            }
        }
        val found = image.find(node)
        val x = found.centreX
        // Down the middle of a solid stack: node, gap, node, gap, node.
        var runs = 0
        var inside = false
        for (y in found.top..found.bottom) {
            val on = image.near(x, y, node)
            if (on && !inside) runs++
            inside = on
        }
        assertEquals(3, runs, "down a solid stack of three, the nodes run together rather than each cut back from the next")
        // The front node — the last — is a whole node's height of ink.
        var front = 0
        var y = found.bottom
        while (y > found.top && image.near(x, y, node)) { front++; y-- }
        assertTrue(front / scale > diameter - 1.5f, "the front node is ${front / scale}dp tall, cut short")
    }

    @Test
    fun theLegsClearTheStackAsTheyClearANode() {
        /** Pixels between the last of the leg arriving from above and the top of the node. */
        fun gapAbove(group: Boolean): Int {
            val image = capture {
                TimelineList {
                    item(connectorColour = leg) { +"Hospital Av" }
                    if (group) nodeGroup("Show 10 stops", count = 10, nodeColour = node) else item("Walk", nodeColour = node)
                    item("Elizabeth Quay")
                }
            }
            val found = image.find(node)
            var y = found.top - 1
            while (y > 0 && !image.near(found.centreX, y, leg, 0.3f)) y--
            assertTrue(y > 0, "no leg arrives at the node")
            return found.top - y
        }
        val stop = gapAbove(group = false)
        val group = gapAbove(group = true)
        assertTrue(abs(stop - group) <= 1, "the leg stops ${group}px short of a group, and ${stop}px short of a stop")
        assertTrue(group >= 2, "the leg runs into the group")
    }

    @Test
    fun aJourneyAtTheGroupHasOneHaloRoundTheWholeStack() {
        val image = capture {
            TimelineList(progress = 1f, colours = TimelineDefaults.colours(progress = Color(0xFF208040))) {
                item("Hospital Av")
                nodeGroup("Show 10 stops", count = 10, nodeColour = node)
                item("Elizabeth Quay")
            }
        }
        // The halo is a wash of the node's colour, lighter than it and not the page.
        fun tinted(x: Int, y: Int): Boolean {
            val p = image[x, y]
            return p.blue > p.red + 0.08f && p.red > 0.4f
        }
        val nodes = run {
            // The stack is the darkest of the node colour, inside its halo.
            image.find(node)
        }
        val beside = nodes.right + (2 * scale).toInt() - 1
        val r = (diameter / 2 * scale).toInt()
        assertTrue(tinted(beside, nodes.top + r), "no halo beside the first node of the stack")
        assertTrue(tinted(beside, nodes.bottom - r), "no halo beside the last node of the stack")
        assertTrue(tinted(beside, nodes.centreY), "the halo is not one capsule along the stack")
    }

    @Test
    fun aGroupIsOneRowToTapAndToRead() {
        var opened = 0
        runDesktopComposeUiTest {
            setContent {
                KontourTheme {
                    TimelineList {
                        item("Hospital Av")
                        nodeGroup("Show 10 past stops", count = 10) { opened++ }
                        item("Elizabeth Quay")
                    }
                }
            }
            assertEquals(1, onAllNodesWithText("Show 10 past stops").fetchSemanticsNodes().size)
            onNodeWithText("Show 10 past stops").performClick()
            waitForIdle()
        }
        assertEquals(1, opened, "the group's row did not open")
    }

    @Test
    fun aTimelineGroupBunchesItsNodes() {
        val image = capture {
            Timeline {
                TimelineItem { +"Hospital Av" }
                TimelineNodeGroup(count = 10, nodeColour = node) { +"Show 10 past stops" }
                TimelineItem(connector = ConnectorStyle.None) { +"Elizabeth Quay" }
            }
        }
        assertStack(image.find(node), 3, across = false, "Timeline")
    }

    @Test
    fun aHorizontalTimelineGroupBunchesItsNodesAcross() {
        val image = capture {
            HorizontalTimeline {
                TimelineItem { +"Ordered" }
                TimelineNodeGroup(count = 3, nodeColour = node) { +"3 scans" }
                TimelineItem(connector = ConnectorStyle.None) { +"Delivered" }
            }
        }
        assertStack(image.find(node), 3, across = true, "HorizontalTimeline")
    }

    @Test
    fun aBranchTimelineGroupBunchesItsNodes() {
        val image = capture {
            BranchTimeline(
                items = listOf("c", "b", "a"),
                key = { it },
                parents = { mapOf("c" to listOf("b"), "b" to listOf("a"))[it] ?: emptyList() },
            ) { id ->
                if (id == "b") nodeGroup("12 more commits", count = 12, nodeColour = node) else item { +"Commit $id" }
            }
        }
        assertStack(image.find(node), 3, across = false, "BranchTimeline")
    }
}
