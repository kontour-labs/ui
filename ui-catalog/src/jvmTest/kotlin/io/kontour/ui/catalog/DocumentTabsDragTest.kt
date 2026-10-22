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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.layout.layout
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
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
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
     * Pulled off its strip and carried about, a tab is drawn under the pointer
     * on the frame of every move, and the strip under it does nothing.
     *
     * Reported 2026-10-21 for 0.5.0: *"dragging document tabs around outside
     * of the bar is still janky as hell"*. Coming away, the carried tab was
     * flown to the pointer on a spring that every move restarted, and a spring
     * restarted each frame never leaves its first: it stood where it came away
     * while the pointer went on, 680px behind it at the end of a pull down, and
     * caught up only once the pointer stopped. Now the jump as it comes away
     * eases out over a few frames whatever the pointer does, and from then on
     * the tab is where the pointer is.
     *
     * Held at its glyph, which it grows from as it lifts, so the glyph stays
     * under the pointer to the pixel.
     */
    @Test
    fun pulledOffItsStripATabIsDrawnUnderThePointerOnEveryMove() {
        val panes = Panes(
            top = listOf("Timetable", "Fares", "Route map", "Lifts"),
            bottom = null,
            felt = mutableListOf(),
            moves = true,
            sceneHeight = 1200,
            marked = "Timetable",
        )
        val misses = mutableListOf<String>()
        var measured = 0
        panes.run { scene ->
            val glyph = panes.glyph("top", "Timetable").center
            scene.holdAt(glyph)
            // Straight down off the strip, then round a circle well clear of it.
            val path = mutableListOf<Offset>()
            repeat(30) { i -> path += glyph + Offset(0f, 24f * (i + 1)) }
            val centre = path.last() + Offset(120f, 0f)
            repeat(48) { i ->
                val angle = PI + (i + 1) * PI / 24
                path += centre + Offset((120 * cos(angle)).toFloat(), (120 * sin(angle)).toFloat())
            }
            path.forEachIndexed { i, at ->
                val composed = panes.compositions
                val measures = panes.measures
                val placed = panes.placements
                scene.move(at, PointerType.Mouse)
                val drawn = scene.frame().marked()
                // Past the tear-off, and the few frames that ease its jump away.
                if (i < EasedBy) return@forEachIndexed
                measured++
                val off = drawn?.let { it - at }
                if (off == null || abs(off.x) > 1f || abs(off.y) > 1f) misses += "move $i: pointer at $at, glyph drawn at $drawn"
                val strip = "${panes.compositions - composed} compositions, ${panes.measures - measures} measures, ${panes.placements - placed} placements"
                if (panes.compositions != composed || panes.measures != measures || panes.placements != placed) misses += "move $i: the strip under it ran $strip"
            }
            scene.release(path.last(), PointerType.Mouse)
            scene.frames(40)
        }
        assertTrue(measured >= 40, "only $measured moves were measured clear of the strip")
        assertTrue(misses.isEmpty(), "the carried tab was not under the pointer, or the strip worked, on ${misses.size} of $measured moves:\n${misses.joinToString("\n")}")
    }

    /**
     * Riding its strip, a tab moved along it with a hand's wobble across stays
     * on the line of the other tabs, out past the strip's edge by a few dp.
     *
     * Reported 2026-10-21 for 0.5.0: *"it also feels too easy to drag out of
     * alignment with the rest of the tabs in the bar"*. It gave half the
     * pointer's pull across the strip, so every wobble moved it out of line.
     */
    @Test
    fun aWobbleAcrossItsStripLeavesATabOnTheLine() {
        val felt = mutableListOf<FeedbackIntent>()
        val panes = Panes(
            top = listOf("Timetable", "Fares", "Route map", "Lifts"),
            bottom = listOf("Signage", "Bins"),
            felt = felt,
            marked = "Timetable",
        )
        val off = mutableListOf<Float>()
        panes.run { scene ->
            val glyph = panes.glyph("top", "Timetable").center
            val strip = panes.strips.getValue("top")
            scene.holdAt(glyph)
            // Along a little at a time, across by up to 6dp either way, and
            // once a few dp past the strip's foot.
            val wobble = listOf(12f, -12f, 6f, -8f, 10f, 0f, -12f, 12f)
            wobble.forEachIndexed { i, across ->
                val at = Offset(glyph.x + 8f * (i + 1), glyph.y + across)
                scene.move(at, PointerType.Mouse)
                off += (scene.frame().marked()?.y ?: Float.NaN) - glyph.y
            }
            val foot = Offset(glyph.x + 72f, strip.bottom + 12f)
            scene.move(foot, PointerType.Mouse)
            off += (scene.frame().marked()?.y ?: Float.NaN) - glyph.y
            scene.release(foot, PointerType.Mouse)
            scene.frames(40)
        }
        assertTrue(off.all { abs(it) <= 0.5f }, "the tab left its strip's line under a wobble, by ${off.joinToString()} px")
        assertTrue(FeedbackIntent.DragThreshold !in felt, "a wobble tore the tab off its strip: $felt")
    }

    /**
     * A tab comes away from its strip only for a pull well past its edge —
     * 64dp — and is held on the line until then, drawn no more than 6dp
     * towards the pull, so a pull is seen to be taken up.
     *
     * It came away 32dp past the edge, which a hand moving a tab along a
     * phone's strip went on the way.
     */
    @Test
    fun aTabComesAwayOnlyForADeliberatePull() {
        val felt = mutableListOf<FeedbackIntent>()
        val panes = Panes(
            top = listOf("Timetable", "Fares", "Route map", "Lifts"),
            bottom = listOf("Signage", "Bins"),
            felt = felt,
            marked = "Timetable",
        )
        var held = Float.NaN
        var gapHeld: Int? = null
        var gapAfter: Int? = -1
        panes.run { scene ->
            val glyph = panes.glyph("top", "Timetable").center
            val strip = panes.strips.getValue("top")
            scene.holdAt(glyph)
            // 56dp past the strip's foot, in steps a frame each.
            val short = Offset(glyph.x, strip.bottom + 112f)
            scene.carry(glyph, short)
            held = (scene.frame().marked()?.y ?: Float.NaN) - glyph.y
            gapHeld = panes.top.state!!.dropIndex
            assertTrue(FeedbackIntent.DragThreshold !in felt, "pulled 56dp past its strip, the tab came away: $felt")
            // And on to 72dp.
            val past = Offset(glyph.x, strip.bottom + 144f)
            scene.carry(short, past)
            gapAfter = panes.top.state!!.dropIndex
            scene.release(past, PointerType.Mouse)
            scene.frames(40)
        }
        assertTrue(held >= 0f && held <= 12f, "held on its strip, the tab should be drawn at most 6dp towards the pull: $held px")
        assertEquals(0, gapHeld, "held on its strip, the tab should keep its place")
        assertEquals(null, gapAfter, "pulled 72dp past its strip, the tab should have come away")
        assertEquals(1, felt.count { it == FeedbackIntent.DragThreshold }, "coming away should be felt once: $felt")
        assertEquals(listOf("Timetable", "Fares", "Route map", "Lifts"), panes.top.items.toList())
    }

    /**
     * Carried back towards a strip, a tab is drawn onto its line as it nears,
     * and taken in from a strip's height away, with the detent of a place.
     *
     * Reported 2026-10-21 for 0.5.0: *"it should also snap into a bar a bit
     * easier"*. It boarded only once the pointer was over the strip itself.
     */
    @Test
    fun carriedNearAStripATabIsDrawnToItAndTakenIn() {
        val felt = mutableListOf<FeedbackIntent>()
        val panes = Panes(
            top = listOf("Timetable", "Fares", "Route map", "Lifts"),
            bottom = listOf("Signage", "Bins", "Wi-Fi"),
            felt = felt,
            marked = "Timetable",
        )
        var pulled = Float.NaN
        var gap: Int? = null
        var feltAtCapture = emptyList<FeedbackIntent>()
        panes.run { scene ->
            val glyph = panes.glyph("top", "Timetable").center
            val bottom = panes.strips.getValue("bottom")
            val bins = panes.glyph("bottom", "Bins").center
            scene.holdAt(glyph)
            // Off the top strip, and down towards Bins, 64dp above the bottom
            // strip: inside its pull, outside its capture.
            val near = Offset(bins.x, bottom.top - 128f)
            scene.carry(glyph, Offset(glyph.x, near.y))
            scene.carry(Offset(glyph.x, near.y), near)
            // Where the pointer would have it, against where it is drawn.
            pulled = (scene.frame().marked()?.y ?: Float.NaN) - near.y
            felt.clear()
            // And 40dp above it, inside its capture: taken in.
            val taken = Offset(bins.x, bottom.top - 80f)
            scene.move(taken, PointerType.Mouse)
            scene.frames(4)
            gap = panes.bottom!!.state!!.dropIndex
            feltAtCapture = felt.toList()
            scene.release(taken, PointerType.Mouse)
            scene.frames(40)
        }
        assertTrue(pulled > 40f, "64dp above the strip, the tab should be drawn well towards its line: ${pulled}px below the pointer")
        assertTrue(gap != null, "40dp above the strip, the tab should have been taken in")
        assertTrue(FeedbackIntent.DragThresholdBack in feltAtCapture && FeedbackIntent.Snap in feltAtCapture, "taken in should be felt: $feltAtCapture")
        assertTrue("Timetable" in panes.bottom!!.items, "let go there, the tab should land in the bottom strip: ${panes.bottom!!.items.toList()}")
        assertEquals(listOf("Fares", "Route map", "Lifts"), panes.top.items.toList())
        assertTrue(panes.drops.single().fromList === panes.top.state, "the drop should say which strip it came from")
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
        /** Whether the strips take each other's tabs: where there are two, unless said. */
        val moves: Boolean = bottom != null,
        /** The scene's height, in pixels: room below the strips to carry a tab about in. */
        val sceneHeight: Int = 600,
        /** A tab whose glyph is drawn solid [Marked], to be found in a frame wherever it is carried. */
        val marked: String? = null,
    ) {
        val top = Strip(top, "Left pane")
        val bottom = bottom?.let { Strip(it, "Right pane") }
        val drops = mutableListOf<DropEvent<String>>()

        /** How many times a tab has composed, and a glyph been measured and placed: what a carry costs the strip. */
        var compositions = 0
        var measures = 0
        var placements = 0
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
            Scene(width = 800, height = sceneHeight) {
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
                movesBetweenStrips = moves,
                leading = { tab ->
                    SideEffect { compositions++ }
                    Box(
                        Modifier
                            .size(16.dp)
                            .layout { measurable, constraints ->
                                measures++
                                val placeable = measurable.measure(constraints)
                                layout(placeable.width, placeable.height) {
                                    placements++
                                    placeable.place(0, 0)
                                }
                            }
                            .then(if (tab == marked) Modifier.background(Marked) else Modifier)
                            .reportBounds { glyphs[name to tab] = it },
                    )
                },
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

    /**
     * A mouse pressed beside [at] and moved onto it, as a mouse picks up: the
     * tab is taken hold of where the pointer passes the slop, which is [at].
     */
    private fun Scene.holdAt(at: Offset) {
        val down = at + Offset(64f, 0f)
        move(down - Offset(4f, 4f), PointerType.Mouse)
        frames(2)
        press(down, PointerType.Mouse)
        frame()
        move(at, PointerType.Mouse)
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

    /** The middle of the [Marked] pixels in this frame, or null if there are none. */
    private fun BufferedImage.marked(): Offset? {
        var minX = Int.MAX_VALUE; var minY = Int.MAX_VALUE; var maxX = -1; var maxY = -1
        for (y in 0 until height) for (x in 0 until width) {
            val rgb = getRGB(x, y)
            if ((rgb shr 16 and 0xff) > 200 && (rgb shr 8 and 0xff) < 60 && (rgb and 0xff) < 60) {
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
            }
        }
        if (maxX < 0) return null
        return Offset((minX + maxX + 1) / 2f, (minY + maxY + 1) / 2f)
    }

    private companion object {
        val Document = DragType<String>("document")

        /** A glyph no tab draws, to find the carried one by. */
        val Marked = Color(0xFFFF0000)

        /** A channel step that is rounding, not a change. */
        const val ChannelSlack = 6

        /** Pixels an edge may move by a subpixel. */
        const val PixelSlack = 4

        /** Darker than this is ink: a muted title is near 110, the strip's ground near 245. */
        const val InkLuma = 170f
        const val Steps = 16

        /** How many 12dp moves down off a strip a tab takes to come away and ease its jump out. */
        const val EasedBy = 20
    }
}
