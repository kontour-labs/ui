package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.unit.dp
import io.kontour.ui.dragdrop.DragType
import io.kontour.ui.dragdrop.DropEvent
import io.kontour.ui.dragdrop.DropListState
import io.kontour.ui.dragdrop.rememberDropListState
import io.kontour.ui.interaction.FeedbackDispatcher
import io.kontour.ui.interaction.FeedbackIntent
import io.kontour.ui.interaction.LocalFeedback
import io.kontour.ui.nav.DocumentTabs
import io.kontour.ui.overlay.OverlayHost
import java.awt.image.BufferedImage
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tabs carried by a real mouse through real strips: along one into another
 * order, a detent felt at each place, and pulled off one and carried into
 * another below it, as a split view's two panes take each other's tabs.
 *
 * `DocumentTabsTest` covers what the strip does without a pointer and
 * `DragTearOffTest` the riding itself; this is the two together, read back
 * from what the caller was told at the drop and from where the tabs settle.
 * Each tab's `leading` glyph reports where it is, since a tab's own node is
 * the component's.
 */
class DocumentTabsDragTest {

    @Test
    fun carriedAlongItsStripATabLandsInItsNewPlaceASnapAtEach() {
        val felt = mutableListOf<FeedbackIntent>()
        val panes = Panes(top = listOf("Timetable", "Fares", "Route map", "Lifts"), bottom = null, felt = felt)
        panes.run { scene ->
            val grab = panes.glyph("top", "Timetable").center
            val pitch = panes.glyph("top", "Fares").center.x - grab.x
            // A tab's width and a little more: over 40% of Fares, which it
            // takes the place of, and short of 40% of Route map; with a wobble
            // down the strip well inside the tear-off.
            val drop = grab + Offset(pitch * 1.1f, 12f)
            scene.pickUp(grab)
            scene.carry(grab, drop)
            assertTrue(panes.drops.isEmpty(), "nothing should have been dropped while it is still carried")
            scene.release(drop, PointerType.Mouse)
            scene.frames(40)
        }
        assertEquals(listOf("Fares", "Timetable", "Route map", "Lifts"), panes.top.items.toList())
        val dropped = panes.drops.single()
        assertEquals(1, dropped.index, "counted as if it had already left: Fares, here")
        assertEquals(0, dropped.fromIndex)
        assertTrue(dropped.fromList === panes.top.state)
        assertTrue(FeedbackIntent.Snap in felt, "passing a place should be felt: $felt")
        assertTrue(FeedbackIntent.DragThreshold !in felt, "a wobble along the strip tore the tab off it: $felt")
        assertTrue(FeedbackIntent.GestureEnd in felt, "and the landing: $felt")
        panes.assertEvenlySpaced("top")
    }

    /**
     * Carried halfway over its neighbour and moved about there, a tab takes
     * the neighbour's place once, and is felt once.
     *
     * Reported as *"it rumbles when you drag one document tab over another. It
     * should just click once"*. Between 40% and 60% of the way over, the place
     * it had just taken was taken back by the very next move, and back again,
     * a snap each time.
     */
    @Test
    fun heldHalfwayOverItsNeighbourATabTradesPlacesOnce() {
        val felt = mutableListOf<FeedbackIntent>()
        val panes = Panes(top = listOf("Timetable", "Fares", "Route map", "Lifts"), bottom = null, felt = felt)
        panes.run { scene ->
            val grab = panes.glyph("top", "Timetable").center
            val pitch = panes.glyph("top", "Fares").center.x - grab.x
            val halfway = grab + Offset(pitch * 0.5f, 0f)
            scene.pickUp(grab)
            scene.carry(grab, halfway)
            // A hand held over the middle is never quite still.
            for (wobble in listOf(3f, -3f, 4f, -4f, 2f, -2f)) {
                scene.move(halfway + Offset(wobble, 0f), PointerType.Mouse)
                scene.frame()
            }
            scene.release(halfway, PointerType.Mouse)
            scene.frames(40)
        }
        assertEquals(1, felt.count { it == FeedbackIntent.Snap }, "one trade of places should be one snap: $felt")
        assertEquals(listOf("Fares", "Timetable", "Route map", "Lifts"), panes.top.items.toList())
    }

    @Test
    fun pulledOffOneStripATabIsCarriedIntoAnother() {
        val felt = mutableListOf<FeedbackIntent>()
        val panes = Panes(
            top = listOf("Timetable", "Fares", "Route map", "Lifts"),
            bottom = listOf("Signage", "Bins", "Wi-Fi"),
            felt = felt,
        )
        var nextAfterDrop = 0f
        var leftAt = 0f
        // What happened along the way, said all at once if anything is wrong:
        // where each strip's gap was, and what was felt, at each stage.
        val seen = mutableListOf<String>()
        var pointer = Offset.Zero
        fun note(stage: String) {
            seen += "$stage: top gap ${panes.top.state!!.dropIndex}, bottom gap ${panes.bottom!!.state!!.dropIndex}, " +
                "felt ${felt.toList()}, bottom ${panes.bottom!!.state!!.status}, pointer $pointer, strips ${panes.strips}"
        }
        panes.run { scene ->
            val grab = panes.glyph("top", "Timetable").center
            leftAt = grab.x
            val pitch = panes.glyph("top", "Fares").center.x - grab.x
            // The pointer sits at the glyph, left of the tab's middle by about
            // half a tab; that middle, carried, wants to be on Signage's far
            // edge, which is Bins' near one.
            val bins = panes.glyph("bottom", "Bins").center
            val drop = Offset(bins.x - pitch / 2f, bins.y)
            val clear = Offset(grab.x, (grab.y + drop.y) / 2f)
            scene.pickUp(grab)
            // Straight down, as a tab is pulled to the pane below: first a
            // little, inside the tear-off, where it still rides its strip.
            val down = grab + Offset(0f, 24f)
            scene.carry(grab, down)
            pointer = down
            note("picked up")
            scene.carry(down, clear)
            pointer = clear
            note("pulled clear")
            scene.carry(clear, drop)
            pointer = drop
            note("over the second strip")
            scene.release(drop, PointerType.Mouse)
            scene.frames(2)
            nextAfterDrop = panes.glyph("top", "Fares").center.x
            scene.frames(40)
        }
        val story = seen.joinToString("\n")
        assertTrue(seen[0].startsWith("picked up: top gap 0"), "picked up, the tab should ride its strip, in its own place:\n$story")
        assertTrue(seen[1].startsWith("pulled clear: top gap null") && FeedbackIntent.DragThreshold in felt, "pulled clear, the tab should come away:\n$story")
        assertTrue(seen[2].startsWith("over the second strip: top gap null, bottom gap 1"), "the gap should open in the second strip, before Bins:\n$story")
        assertEquals(listOf("Fares", "Route map", "Lifts"), panes.top.items.toList())
        assertEquals(listOf("Signage", "Timetable", "Bins", "Wi-Fi"), panes.bottom!!.items.toList())
        val dropped = panes.drops.single()
        assertEquals(1, dropped.index)
        assertEquals(0, dropped.fromIndex)
        assertTrue(dropped.fromList === panes.top.state, "the drop should say which strip it came from")
        // Its place closed up behind it as it was carried off, so nothing is
        // left on the first strip to shrink away once it lands in the second.
        assertTrue(
            abs(nextAfterDrop - leftAt) < 2f,
            "the tab carried off left a ghost to shrink: Fares was at $nextAfterDrop just after the drop, the strip starts at $leftAt",
        )
        panes.assertEvenlySpaced("top")
        panes.assertEvenlySpaced("bottom")
    }

    /**
     * Carried along its strip towards the "+", a tab stops where the last tab
     * ends and never covers it; and it is flat while it rides, as a browser's
     * tab is, so nothing of a shadow falls on the page under the strip.
     */
    @Test
    fun alongItsStripATabStaysBetweenItsEndsAndFlat() {
        val panes = Panes(top = listOf("Timetable", "Fares", "Route map", "Lifts"), bottom = null, felt = mutableListOf(), adds = true)
        panes.run { scene ->
            val strip = panes.strips.getValue("top")
            // The "+", in the square at the strip's end, and a band of the page under the strip.
            val add = Rect(strip.right - strip.height, strip.top, strip.right, strip.bottom)
            val under = Rect(strip.left, strip.bottom + 2f, strip.right, strip.bottom + 48f)
            val before = scene.frame()
            val grab = panes.glyph("top", "Lifts").center
            // Towards the "+", the pointer just short of it.
            val towards = Offset(add.left - 8f, grab.y)
            scene.pickUp(grab)
            scene.carry(grab, towards)
            val during = scene.frame()
            assertUnchanged(before, during, add, "the tab carried towards the end of its strip covered the \"+\"")
            assertUnchanged(before, during, under, "the tab riding its strip cast a shadow on the page under it")
            scene.release(towards, PointerType.Mouse)
            scene.frames(40)
        }
        assertEquals(listOf("Timetable", "Fares", "Route map", "Lifts"), panes.top.items.toList())
        assertTrue(panes.drops.isEmpty(), "let go in its own place, the tab was dropped: ${panes.drops.map { it.payload }}")
    }

    /**
     * A closed tab shrinks away cut where it ends. Clipped inside its
     * narrowing, it was clipped at its whole width, and its title spilled out
     * over the start of the tab after it as that slid along into its place.
     */
    @Test
    fun aClosedTabShrinksAwayCutWhereItEnds() {
        val panes = Panes(top = listOf("Timetable", "Departures board", "Route map", "Lifts"), bottom = null, felt = mutableListOf())
        panes.run { scene ->
            onComposeUiThread { panes.top.items.remove("Departures board") }
            repeat(16) { frame ->
                val image = scene.frame()
                val next = panes.glyph("top", "Route map")
                // The start of the next tab, up to and over its glyph, which
                // draws nothing: its own padding, where only a spill can be.
                val start = Rect(next.left - 16f, next.top - 4f, next.right, next.bottom + 4f)
                val spilt = image.inkIn(start)
                assertEquals(0, spilt, "frame $frame: the closing tab's title spilled $spilt pixels onto the tab after it")
            }
        }
    }

    // -----------------------------------------------------------------------

    private class Strip(initial: List<String>, val label: String) {
        val items: SnapshotStateList<String> = mutableStateListOf<String>().also { it.addAll(initial) }
        var state: DropListState<String>? = null
    }

    private inner class Panes(
        top: List<String>,
        bottom: List<String>?,
        val felt: MutableList<FeedbackIntent>,
        val adds: Boolean = false,
    ) {
        val top = Strip(top, "Left pane")
        val bottom = bottom?.let { Strip(it, "Right pane") }
        val drops = mutableListOf<DropEvent<String>>()
        private val glyphs = mutableMapOf<Pair<String, String>, Rect>()

        /** Where each strip is, in root pixels. */
        val strips = mutableMapOf<String, Rect>()

        fun glyph(strip: String, tab: String): Rect = glyphs.getValue(strip to tab)

        fun assertEvenlySpaced(strip: String) {
            val tabs = if (strip == "top") top else bottom!!
            val lefts = tabs.items.map { glyph(strip, it).left }
            val steps = lefts.zipWithNext { a, b -> b - a }
            assertTrue(steps.all { it > 0f && abs(it - steps.first()) < 1.5f }, "the $strip strip's tabs did not settle evenly: $lefts")
        }

        fun run(drive: (Scene) -> Unit) {
            Scene(width = 800, height = 600) {
                CompositionLocalProvider(LocalFeedback provides FeedbackDispatcher { felt += it }) {
                    OverlayHost {
                        Column(Modifier.fillMaxSize().background(Color.White)) {
                            Tabs(top, "top")
                            if (bottom != null) {
                                Spacer(Modifier.height(160.dp))
                                Tabs(bottom, "bottom")
                            }
                        }
                    }
                }
            }.use { scene ->
                scene.frames(6)
                drive(scene)
            }
        }

        @Composable
        private fun Tabs(strip: Strip, name: String) {
            val state = rememberDropListState(
                listState = rememberLazyListState(),
                type = Document,
                items = strip.items,
                key = { it },
                label = strip.label,
                itemLabel = { it },
                onDrop = { event ->
                    drops += event
                    listOf(top, bottom).first { it?.state === event.fromList }!!.items.removeAt(event.fromIndex!!)
                    strip.items.add(event.index, event.payload)
                },
            )
            strip.state = state
            DocumentTabs(
                state = state,
                value = strip.items.firstOrNull(),
                onValueChange = {},
                modifier = Modifier.fillMaxWidth().reportBounds { strips[name] = it },
                onAdd = if (adds) ({}) else null,
                movesBetweenStrips = bottom != null,
                leading = { tab -> Box(Modifier.size(16.dp).reportBounds { glyphs[name to tab] = it }) },
            )
        }
    }

    /** A mouse pressed on [at], as a mouse picks up: once it has moved. */
    private fun Scene.pickUp(at: Offset) {
        move(at - Offset(4f, 4f), PointerType.Mouse)
        frames(2)
        press(at, PointerType.Mouse)
        frame()
    }

    /** From [from] to [to] in steps, a frame each. */
    private fun Scene.carry(from: Offset, to: Offset) {
        repeat(Steps) { i ->
            move(from + (to - from) * ((i + 1f) / Steps), PointerType.Mouse)
            frame()
        }
        frames(4)
    }

    /** Fails with [message] if [area] of [after] is not as it was in [before], to the pixel or near it. */
    private fun assertUnchanged(before: BufferedImage, after: BufferedImage, area: Rect, message: String) {
        var differ = 0
        area.pixels(before) { x, y ->
            val a = before.getRGB(x, y)
            val b = after.getRGB(x, y)
            val most = maxOf(abs((a shr 16 and 0xff) - (b shr 16 and 0xff)), abs((a shr 8 and 0xff) - (b shr 8 and 0xff)), abs((a and 0xff) - (b and 0xff)))
            if (most > ChannelSlack) differ++
        }
        assertTrue(differ <= PixelSlack, "$message: $differ pixels changed")
    }

    /** How many pixels of [area] are dark enough to be a title's ink on the strip's pale ground. */
    private fun BufferedImage.inkIn(area: Rect): Int {
        var ink = 0
        area.pixels(this) { x, y ->
            val rgb = getRGB(x, y)
            val luma = 0.299f * (rgb shr 16 and 0xff) + 0.587f * (rgb shr 8 and 0xff) + 0.114f * (rgb and 0xff)
            if (luma < InkLuma) ink++
        }
        return ink
    }

    private inline fun Rect.pixels(image: BufferedImage, each: (x: Int, y: Int) -> Unit) {
        for (y in top.toInt().coerceAtLeast(0) until bottom.toInt().coerceAtMost(image.height)) {
            for (x in left.toInt().coerceAtLeast(0) until right.toInt().coerceAtMost(image.width)) each(x, y)
        }
    }

    private companion object {
        val Document = DragType<String>("document")

        /** A channel step that is rounding, not a change. */
        const val ChannelSlack = 6

        /** Pixels an edge may move by a subpixel. */
        const val PixelSlack = 4

        /** Darker than this is ink: a muted title is near 110, the strip's ground near 245. */
        const val InkLuma = 170f
        const val Steps = 16
    }
}
