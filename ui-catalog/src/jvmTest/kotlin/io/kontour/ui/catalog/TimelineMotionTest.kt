package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import io.kontour.ui.components.display.BranchProgress
import io.kontour.ui.components.display.BranchTimeline
import io.kontour.ui.components.display.BranchTimelineColours
import io.kontour.ui.components.display.ConnectorStyle
import io.kontour.ui.components.display.HorizontalTimeline
import io.kontour.ui.components.display.TimelineColours
import io.kontour.ui.components.display.TimelineItem
import io.kontour.ui.components.display.TimelineList
import io.kontour.ui.foundation.Text
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A travelled timeline's motion, frame by frame: the halo on the stop the
 * journey is at swells and fades, and the band on the leg it is travelling runs
 * on towards the next stop — across the seam where one row hands the leg to the
 * next, and the right way round right to left.
 */
class TimelineMotionTest {

    private val red = Color(0xFFD00000)
    private val grey = Color(0xFFB0B0B0)
    private val colours = TimelineColours(node = Color.Black, rail = grey, progress = red)

    /**
     * Past the still halo, a pixel the pulse reaches: lit, fading and dark again
     * as the halo swells and blinks out.
     */
    @Test
    fun theCurrentStopsHaloSwellsAndFades() {
        val shades = pulse(reduceMotion = false)
        assertTrue(shades.toSet().size >= 4, "the pulse should pass through several shades there, saw ${shades.toSet()}")
        assertTrue(shades.any { it > 250 }, "and be gone between pulses, saw ${shades.toSet()}")
        assertTrue(shades.any { it < 235 }, "and reach it at all, saw ${shades.toSet()}")
    }

    @Test
    fun theHaloStandsStillUnderReducedMotion() {
        val shades = pulse(reduceMotion = true)
        assertTrue(shades.all { it > 250 }, "under reduced motion nothing reaches past the halo, saw ${shades.toSet()}")
    }

    /**
     * A quarter of the way along a leg drawn in two rows, the band runs on from
     * there: through the rest of the first row's half, over the seam into the
     * second's, and never past the next stop — without jumping as it crosses.
     */
    @Test
    fun theBandCrossesTheSeamWithoutAJump() {
        val labels = arrayOfNulls<Rect>(3)
        Scene(width = 360, height = 400) {
            Box(Modifier.fillMaxSize().background(Color.White)) {
                TimelineList(progress = 0.25f, colours = colours) {
                    repeat(3) { i -> item { label { Text("Stop $i", Modifier.reportBounds { labels[i] = it }) } } }
                }
            }
        }.use { scene ->
            scene.frames(4)
            // A one-line label's middle is its first line's, where the node sits.
            val nodes = labels.map { it!!.center.y.toInt() }
            val (top, next) = nodes[0] + ClearPx to nodes[1] - ClearPx
            val seam = (nodes[0] + nodes[1]) / 2
            val leads = ArrayList<Int>()
            var crossed = false
            repeat(LoopFrames) {
                val image = scene.frame()
                val red = (top until minOf(image.height, nodes[1] + 60)).filter { isRed(image.getRGB(RailX, it)) }
                assertTrue(red.none { it > next + 2 }, "the band ran past the next stop: red at ${red.filter { it > next }}")
                if (red.any { it > seam + 2 }) crossed = true
                leads += red.maxOrNull() ?: top
            }
            assertTrue(crossed, "the band never crossed from the first row into the second")
            val steps = leads.zipWithNext { a, b -> b - a }.filter { it > 0 }
            assertTrue(steps.max() <= MaxStepPx, "the band jumped ${steps.max()}px in one frame; steps were $steps")
        }
    }

    /**
     * The band covers the whole leg whatever the fraction: nine tenths of the
     * way along, it still enters at the stop behind and leaves at the one ahead.
     */
    @Test
    fun theBandSweepsTheWholeLegWhateverTheFraction() {
        val labels = arrayOfNulls<Rect>(3)
        Scene(width = 360, height = 400) {
            Box(Modifier.fillMaxSize().background(Color.White)) {
                TimelineList(progress = 0.9f, colours = colours) {
                    repeat(3) { i -> item { label { Text("Stop $i", Modifier.reportBounds { labels[i] = it }) } } }
                }
            }
        }.use { scene ->
            scene.frames(4)
            val nodes = labels.map { it!!.center.y.toInt() }
            val (top, bottom) = nodes[0] + ClearPx to nodes[1] - ClearPx
            val quarter = (bottom - top) / 4
            var nearStart = false
            var nearEnd = false
            repeat(LoopFrames) {
                val image = scene.frame()
                val red = (top..bottom).filter { isRed(image.getRGB(RailX, it)) }
                if (red.any { it < top + quarter }) nearStart = true
                if (red.any { it > bottom - quarter }) nearEnd = true
            }
            assertTrue(nearStart, "the band never came in at the stop behind")
            assertTrue(nearEnd, "the band never reached the stop ahead")
        }
    }

    /**
     * A leg drawn in the progress colour of its own still shows its band: while
     * the band runs the leg is faint, and the band is not.
     */
    @Test
    fun theBandShowsOnALegOfItsOwnColour() {
        val labels = arrayOfNulls<Rect>(2)
        Scene(width = 360, height = 400) {
            Box(Modifier.fillMaxSize().background(Color.White)) {
                TimelineList(progress = 0.5f, colours = colours) {
                    item(connectorColour = red) { label { Text("Stop 0", Modifier.reportBounds { labels[0] = it }) } }
                    item { label { Text("Stop 1", Modifier.reportBounds { labels[1] = it }) } }
                }
            }
        }.use { scene ->
            scene.frames(4)
            val nodes = labels.map { it!!.center.y.toInt() }
            assertBandAndFaint(scene, RailX, nodes[0] + ClearPx..nodes[1] - ClearPx, "a leg of the progress colour")
        }
    }

    /** A branch's explicitly coloured line shows the band running up it, too. */
    @Test
    fun aBranchBandShowsOnAnExplicitLine() {
        val labels = arrayOfNulls<Rect>(2)
        Scene(width = 360, height = 400) {
            Box(Modifier.fillMaxSize().background(Color.White)) {
                BranchTimeline(
                    items = listOf("b" to listOf("a"), "a" to emptyList()),
                    id = { it.first },
                    parents = { it.second },
                    progress = BranchProgress(reached = "a", towards = "b"),
                    colours = BranchTimelineColours(lanes = listOf(Color.Black), muted = grey),
                ) { commit ->
                    val i = if (commit.first == "b") 0 else 1
                    item(connectorColour = red) { label { Text(commit.first, Modifier.reportBounds { labels[i] = it }) } }
                }
            }
        }.use { scene ->
            scene.frames(4)
            val nodes = labels.map { it!!.center.y.toInt() }
            assertBandAndFaint(scene, RailX, nodes[0] + ClearPx..nodes[1] - ClearPx, "an explicitly coloured branch line")
        }
    }

    /** Over a loop, some frame shows the band at full strength with the faint leg beside it. */
    private fun assertBandAndFaint(scene: Scene, x: Int, along: IntRange, what: String) {
        var both = false
        repeat(LoopFrames) {
            val image = scene.frame()
            val pixels = along.map { image.getRGB(x, it) }
            if (pixels.any { isRed(it) } && pixels.any { isFaintRed(it) }) both = true
        }
        assertTrue(both, "$what: the band should run full strength along a faint leg")
    }

    /** Across the page the band runs towards the next stage: to the right, or to the left right to left. */
    @Test
    fun aHorizontalBandTravelsTowardsTheEnd() {
        for (direction in LayoutDirection.entries) {
            val labels = arrayOfNulls<Rect>(2)
            Scene(width = 600, height = 200) {
                CompositionLocalProvider(LocalLayoutDirection provides direction) {
                    Box(Modifier.fillMaxSize().background(Color.White)) {
                        HorizontalTimeline(progress = 0.25f, colours = colours) {
                            repeat(2) { i ->
                                TimelineItem(
                                    modifier = Modifier.reportBounds { labels[i] = it },
                                    connector = if (i == 1) ConnectorStyle.None else ConnectorStyle.Solid,
                                ) { Text("Stage $i, a long enough name") }
                            }
                        }
                    }
                }
            }.use { scene ->
                scene.frames(4)
                val y = (labels[0]!!.top - RailAboveLabelPx).toInt()
                val first = labels[0]!!
                // The far three quarters of the leg, which only the band colours.
                val far = if (direction == LayoutDirection.Ltr) {
                    (first.left + first.width * 0.6f).toInt() until first.right.toInt() - 4
                } else {
                    (first.left + 4).toInt() until (first.right - first.width * 0.6f).toInt()
                }
                var reached = false
                repeat(LoopFrames) {
                    val image = scene.frame()
                    if (far.any { isRed(image.getRGB(it, y)) }) reached = true
                }
                assertTrue(reached, "$direction: the band never reached the far end of the leg")
            }
        }
    }

    /** The shade of one pixel beside the stop the journey is at, past its halo, over one loop. */
    private fun pulse(reduceMotion: Boolean): List<Int> {
        val shades = ArrayList<Int>()
        val labels = arrayOfNulls<Rect>(3)
        Scene(width = 360, height = 300, reduceMotion = reduceMotion) {
            Box(Modifier.fillMaxSize().background(Color.White)) {
                TimelineList(progress = 1f, colours = colours) {
                    repeat(3) { i -> item { label { Text("Stop $i", Modifier.reportBounds { labels[i] = it }) } } }
                }
            }
        }.use { scene ->
            scene.frames(4)
            val y = labels[1]!!.center.y.toInt()
            repeat(LoopFrames) {
                val p = scene.frame().getRGB(RailX + PulseProbePx, y)
                shades += p shr 8 and 0xFF
            }
        }
        return shades
    }

    private fun isRed(p: Int) = (p shr 16 and 0xFF) > 160 && (p shr 8 and 0xFF) < 90 && (p and 0xFF) < 90

    /** Red at about a third on white: a faint leg. */
    private fun isFaintRed(p: Int) = (p shr 16 and 0xFF) > 200 && (p shr 8 and 0xFF) in 120..230 && (p and 0xFF) in 120..230

    private companion object {
        /** At density two: the rail 22dp in, a 12dp node, and 8dp clear round it. */
        const val RailX = 44
        const val ClearPx = 16
        /** 11dp from the node's centre: past the 8dp halo, inside the pulse's 14dp. */
        const val PulseProbePx = 22
        const val RailAboveLabelPx = 32
        /** One loop of the rail's motion at 16ms a frame, and a little over. */
        const val LoopFrames = 96
        /** A leg of a hundred-odd pixels, crossed in 88 frames: a pixel or two a frame, never a jump. */
        const val MaxStepPx = 6
    }
}
