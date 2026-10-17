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
            // Its centre past Fares' middle and short of Route map's, with a
            // wobble down the strip well inside the tear-off.
            val drop = grab + Offset(pitch * 1.5f, 12f)
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
            keep(scene.frame(), "pulled-clear")
            note("pulled clear")
            scene.carry(clear, drop)
            pointer = drop
            keep(scene.frame(), "over-the-second-strip")
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

    // -----------------------------------------------------------------------

    private class Strip(initial: List<String>, val label: String) {
        val items: SnapshotStateList<String> = mutableStateListOf<String>().also { it.addAll(initial) }
        var state: DropListState<String>? = null
    }

    private inner class Panes(top: List<String>, bottom: List<String>?, val felt: MutableList<FeedbackIntent>) {
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
                leading = { tab -> Box(Modifier.size(16.dp).reportBounds { glyphs[name to tab] = it }) },
            )
        }
    }

    /** For now, frames of the carry where CI uploads them, to be looked at. */
    private fun keep(frame: java.awt.image.BufferedImage, stage: String) {
        val dir = java.io.File(System.getProperty("kontour.screenshots.diffDir") ?: "build/screenshot-diffs", "renders")
        dir.mkdirs()
        javax.imageio.ImageIO.write(frame, "png", java.io.File(dir, "documenttabs-$stage.png"))
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

    private companion object {
        val Document = DragType<String>("document")
        const val Steps = 16
    }
}
