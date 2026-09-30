package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Bus
import com.composables.icons.tabler.outline.MapPin
import com.composables.icons.tabler.outline.Star
import com.composables.icons.tabler.outline.User
import io.kontour.ui.interaction.FeedbackDispatcher
import io.kontour.ui.interaction.FeedbackIntent
import io.kontour.ui.interaction.LocalFeedback
import io.kontour.ui.nav.NavBar
import io.kontour.ui.nav.NavDefaults
import java.awt.image.BufferedImage
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The nav bar's marker, dragged from one destination to another the way a
 * segmented control's thumb is.
 *
 * Asked for as *"make it so I can drag the selection indicator, similar to how
 * the segmented control works with the snapping"*. The difference from the
 * segmented control is deliberate and is the second half of what is checked
 * here: a destination is a page, so the marker follows the finger at once but
 * the destination is only chosen when the finger lifts — one `onClick`, not one
 * for every destination passed over.
 *
 * The marker is found from the pixels: a red page, a white bar and a blue marker.
 */
class NavBarMarkerDragTest {

    private companion object {
        const val Density = 2f
        val Page = Color(0xFFFF0000)
        val Marker = Color(0xFF0000FF)
        const val Slack = 2f * Density
    }

    private val items = mutableListOf(Rect.Zero, Rect.Zero, Rect.Zero, Rect.Zero)
    private val keys = listOf("a", "b", "c", "d")

    private class Bar(val scene: Scene, val clicks: MutableList<String>, val felt: MutableList<FeedbackIntent>)

    private fun bar(draggable: Boolean = true, reduceMotion: Boolean = true): Bar {
        val clicks = mutableListOf<String>()
        val felt = mutableListOf<FeedbackIntent>()
        var here by mutableStateOf("a")
        val scene = Scene(width = 800, height = 300, density = Density, reduceMotion = reduceMotion) {
            CompositionLocalProvider(LocalFeedback provides FeedbackDispatcher { felt += it }) {
                Box(Modifier.fillMaxSize().background(Page), contentAlignment = Alignment.BottomCenter) {
                    NavBar(
                        markerDraggable = draggable,
                        colours = NavDefaults.colours(
                            container = Color.White,
                            content = Color.Black,
                            indicator = Marker,
                            selectedContent = Color.White,
                        ),
                    ) {
                        val icons = listOf(Tabler.Outline.MapPin, Tabler.Outline.Bus, Tabler.Outline.Star, Tabler.Outline.User)
                        keys.forEachIndexed { i, key ->
                            item(
                                selected = here == key,
                                onClick = { clicks += key; here = key },
                                key = key,
                                icon = icons[i],
                                modifier = Modifier.reportBounds { items[i] = it },
                            ) { +key }
                        }
                    }
                }
            }
        }
        scene.frames(8)
        return Bar(scene, clicks, felt)
    }

    private fun isMarker(argb: Int): Boolean {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return b > 200 && r < 80 && g < 80
    }

    /** The marker's horizontal centre, at the destinations' height. */
    private fun markerCentre(image: BufferedImage): Float {
        val row = items[0].center.y.toInt()
        val xs = (0 until image.width).filter { isMarker(image.getRGB(it, row)) }
        assertTrue(xs.isNotEmpty(), "no marker at the destinations' height")
        return (xs.first() + xs.last() + 1) / 2f
    }

    private fun centre(i: Int) = Offset(items[i].center.x, items[i].center.y)

    @Test
    fun theMarkerFollowsTheFingerAndTheDestinationIsChosenOnTheLift() {
        val bar = bar()
        bar.scene.use { scene ->
            // From the first destination to the third, held there.
            scene.drag(from = centre(0), to = centre(2), steps = 16, release = false)
            val held = markerCentre(scene.frames(10))
            assertTrue(
                abs(held - items[2].center.x) <= Slack,
                "held over the third destination, the marker is at ${held}px, not on it at ${items[2].center.x}px",
            )
            assertEquals(emptyList(), bar.clicks, "a destination was chosen before the finger lifted")

            scene.release(centre(2))
            scene.frames(10)
            assertEquals(listOf("c"), bar.clicks, "the lift should choose the destination under it, once")
            val settled = markerCentre(scene.frames(1))
            assertTrue(abs(settled - items[2].center.x) <= Slack, "after the lift the marker went to ${settled}px")
        }
    }

    @Test
    fun eachDestinationCrossedTicksOnce() {
        val bar = bar()
        bar.scene.use { scene ->
            // At a pace a hand could manage, so the shared rate floor stays out of it.
            scene.drag(from = centre(0), to = centre(3), steps = 24, paceMillis = 30)
            scene.frames(4)
            val snaps = bar.felt.count { it == FeedbackIntent.Snap }
            assertEquals(3, snaps, "three boundaries crossed, ${bar.felt} felt")
            assertEquals(listOf("d"), bar.clicks)
        }
    }

    @Test
    fun aDragBackToWhereItStartedChoosesNothing() {
        val bar = bar()
        bar.scene.use { scene ->
            scene.drag(from = centre(0), to = centre(2), steps = 10, release = false)
            val there = centre(2)
            val home = centre(0)
            for (step in 1..10) {
                val t = step / 10f
                scene.move(Offset(there.x + (home.x - there.x) * t, home.y))
                scene.frames(1)
            }
            scene.release(home)
            scene.frames(6)
            assertEquals(emptyList(), bar.clicks, "coming home should leave the selection as it was")
            val at = markerCentre(scene.frames(1))
            assertTrue(abs(at - items[0].center.x) <= Slack, "the marker ended at ${at}px, away from the first")
        }
    }

    @Test
    fun aTapIsStillATap() {
        val bar = bar()
        bar.scene.use { scene ->
            scene.tap(centre(1))
            scene.frames(6)
            assertEquals(listOf("b"), bar.clicks)
        }
    }

    @Test
    fun whileDraggingTheMarkerLeansTowardTheFinger() {
        // With motion: the lean is off under reduced motion.
        val bar = bar(reduceMotion = false)
        bar.scene.use { scene ->
            // A little past the second destination's centre, toward the third.
            val past = Offset(items[1].center.x + items[1].width * 0.4f, items[1].center.y)
            scene.drag(from = centre(0), to = past, steps = 12, release = false)
            val leaning = markerCentre(scene.frames(40))
            assertTrue(
                leaning > items[1].center.x + Slack,
                "the marker sat at ${leaning}px with the finger at ${past.x}px, past a centre of ${items[1].center.x}px — no lean",
            )
            assertTrue(leaning < past.x, "the marker leant all the way to the finger, ${leaning}px, finger ${past.x}px, centres ${items.map { it.center.x }}")
            scene.release(past)
        }
    }

    /** The control: with the drag turned off, the same gesture moves nothing and chooses nothing. */
    @Test
    fun turnedOffTheSameDragDoesNothing() {
        val bar = bar(draggable = false)
        bar.scene.use { scene ->
            scene.drag(from = centre(0), to = centre(2), steps = 16, release = false)
            val held = markerCentre(scene.frames(10))
            assertTrue(abs(held - items[0].center.x) <= Slack, "with the drag off the marker still moved to ${held}px")
            scene.release(centre(2))
            scene.frames(6)
            assertEquals(emptyList(), bar.clicks)
        }
    }
}
