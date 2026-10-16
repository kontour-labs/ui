package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import io.kontour.icons.tabler.Tabler
import io.kontour.icons.tabler.outline.Bus
import io.kontour.ui.nav.NavDefaults
import io.kontour.ui.nav.NavMarkerDrag
import io.kontour.ui.nav.ModalNavRail
import io.kontour.ui.nav.NavRail
import io.kontour.ui.overlay.OverlayHost
import java.awt.image.BufferedImage
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A rail's marker, dragged between destinations whenever the rail has nothing
 * to scroll.
 *
 * The bar's marker could be dragged and the rail's could not, because a rail
 * scrolls, and a drag along a scroller is the one gesture it cannot share. Which
 * is true of a rail whose destinations run past its foot, and of no other: a
 * rail of three destinations on a tall window cannot scroll at all, and a drag
 * along it did nothing. Asked for as *"any use where the underlying composable
 * can't be scrolled in the given container. that includes things like lazy
 * lists which can normally be scrolled, but might not overflow their
 * container"*.
 *
 * So the question is asked when the finger goes down, of the rail as it is
 * then: a rail with room to spare drags its marker, and one that overflows
 * scrolls, as it always did.
 *
 * The marker is found from the pixels, as in `NavBarMarkerDragTest`: a red page,
 * a white rail and a blue marker.
 */
class NavRailMarkerDragTest {

    private companion object {
        const val Density = 2f
        val Page = Color(0xFFFF0000)
        val Marker = Color(0xFF0000FF)
        const val Slack = 3f * Density
    }

    private val items = HashMap<Int, Rect>()
    private var rail = Rect.Zero

    private class Rail(val scene: Scene, val clicks: MutableList<Int>, val count: (Int) -> Unit)

    private fun rail(count: Int, height: Int = 800, drag: NavMarkerDrag = NavMarkerDrag.SelectOnRelease): Rail {
        val clicks = mutableListOf<Int>()
        var here by mutableStateOf(0)
        var shown by mutableIntStateOf(count)
        val scene = Scene(width = 400, height = height, density = Density, reduceMotion = true) {
            Box(Modifier.fillMaxSize().background(Page)) {
                NavRail(
                    modifier = Modifier.reportBounds { rail = it },
                    markerDrag = drag,
                    colours = NavDefaults.colours(
                        container = Color.White,
                        content = Color.Black,
                        indicator = Marker,
                        selectedContent = Color.White,
                    ),
                ) {
                    repeat(shown) { i ->
                        item(
                            selected = here == i,
                            onClick = { clicks += i; here = i },
                            key = i,
                            icon = Tabler.Outline.Bus,
                            modifier = Modifier.reportBounds { items[i] = it },
                        ) { +"Stop $i" }
                    }
                }
            }
        }
        scene.frames(8)
        return Rail(scene, clicks) { shown = it }
    }

    private fun isMarker(argb: Int): Boolean {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return b > 200 && r < 80 && g < 80
    }

    /** The marker's vertical centre, from every row of the rail it shows in. */
    private fun markerCentre(image: BufferedImage): Float {
        val ys = (rail.top.toInt() until rail.bottom.toInt().coerceAtMost(image.height)).filter { y ->
            (rail.left.toInt() until rail.right.toInt()).any { x -> isMarker(image.getRGB(x, y)) }
        }
        assertTrue(ys.isNotEmpty(), "no marker anywhere on the rail")
        return (ys.first() + ys.last() + 1) / 2f
    }

    private fun centre(i: Int) = requireNotNull(items[i]) { "destination $i was never placed" }.center

    @Test
    fun aRailOfThreeDragsItsMarkerAndChoosesOnTheLift() {
        val rail = rail(count = 3)
        rail.scene.use { scene ->
            scene.drag(from = centre(0), to = centre(2), steps = 16, release = false)
            val held = markerCentre(scene.frames(10))
            assertTrue(
                abs(held - centre(2).y) <= Slack,
                "held over the third destination, the marker is at ${held}px, not on it at ${centre(2).y}px",
            )
            assertEquals(emptyList(), rail.clicks, "a destination was chosen before the finger lifted")

            scene.release(centre(2))
            scene.frames(10)
            assertEquals(
                listOf(2),
                rail.clicks,
                "dragged from the first destination to the third of a rail with nothing to " +
                    "scroll, and the lift should have chosen the third, once",
            )
        }
    }

    @Test
    fun aRailThatOverflowsStillScrolls() {
        // Twenty destinations in 200dp: most of them are past the foot.
        val rail = rail(count = 20, height = 400)
        rail.scene.use { scene ->
            val before = centre(0).y
            // From the second destination. The fourth's centre is below the
            // rows, in the rail's own padding at its foot, which does not scroll.
            val from = centre(1)
            scene.drag(from = from, to = Offset(from.x, from.y - 200f), steps = 16)
            scene.frames(30)
            assertTrue(
                centre(0).y < before - 100f,
                "a drag up a rail that overflows should scroll it: the first destination " +
                    "went from ${before}px to ${centre(0).y}px",
            )
            assertEquals(emptyList(), rail.clicks, "a scroll of an overflowing rail chose a destination")
        }
    }

    /** The bar's option, down a rail: each destination chosen as the marker reaches it. */
    @Test
    fun selectWhileDraggingChoosesEachDestinationAsItIsReached() {
        val rail = rail(count = 4, drag = NavMarkerDrag.SelectWhileDragging)
        rail.scene.use { scene ->
            scene.drag(from = centre(0), to = centre(2), steps = 16, release = false)
            scene.frames(4)
            assertEquals(listOf(1, 2), rail.clicks, "each destination the marker reached, chosen as it got there")
            scene.release(centre(2))
            scene.frames(6)
            assertEquals(listOf(1, 2), rail.clicks, "the lift chose again what the drag already had")
        }
    }

    /**
     * A modal rail closes once a drag has chosen, not as the marker passes each
     * destination on the way: choosing while dragging, it stays open under the
     * finger, and the lift closes it, once.
     */
    @Test
    fun aModalRailClosesOnceTheDragHasChosen() {
        val events = mutableListOf<String>()
        var here by mutableStateOf(0)
        var visible by mutableStateOf(true)
        Scene(width = 600, height = 800, density = Density, reduceMotion = true) {
            OverlayHost(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().background(Page))
                ModalNavRail(
                    visible = visible,
                    onDismissRequest = { events += "dismiss"; visible = false },
                    markerDrag = NavMarkerDrag.SelectWhileDragging,
                ) {
                    repeat(4) { i ->
                        item(
                            selected = here == i,
                            onClick = { events += "$i"; here = i },
                            key = i,
                            icon = Tabler.Outline.Bus,
                            modifier = Modifier.reportBounds { items[i] = it },
                        ) { +"Stop $i" }
                    }
                }
            }
        }.use { scene ->
            scene.frames(40)
            scene.drag(from = centre(0), to = centre(2), steps = 16, release = false)
            scene.frames(6)
            assertEquals(listOf("1", "2"), events, "chosen as the marker reached each, and the rail still open")
            scene.release(centre(2))
            scene.frames(6)
            assertEquals(listOf("1", "2", "dismiss"), events, "the lift should close the rail, once")
        }
    }

    /** The control: turned off, the same drag down a rail that fits chooses nothing. */
    @Test
    fun turnedOffTheSameDragDoesNothing() {
        val rail = rail(count = 3, drag = NavMarkerDrag.Off)
        rail.scene.use { scene ->
            scene.drag(from = centre(0), to = centre(2), steps = 16)
            scene.frames(10)
            assertEquals(emptyList(), rail.clicks)
            val at = markerCentre(scene.frames(1))
            assertTrue(abs(at - centre(0).y) <= Slack, "with the drag off the marker moved to ${at}px")
        }
    }

    /**
     * Asked of the rail as the finger goes down, so the answer is the rail's
     * now: one that stops fitting scrolls, and one that fits again drags.
     */
    @Test
    fun theAnswerFollowsTheRailAsItGrowsAndShrinks() {
        val rail = rail(count = 3, height = 400)
        rail.scene.use { scene ->
            scene.drag(from = centre(0), to = centre(1), steps = 12)
            scene.frames(10)
            assertEquals(listOf(1), rail.clicks, "three destinations fit, so the drag should have moved the marker")

            rail.count(20)
            scene.frames(10)
            val before = centre(0).y
            // From the second destination. The fourth's centre is below the
            // rows, in the rail's own padding at its foot, which does not scroll.
            val from = centre(1)
            scene.drag(from = from, to = Offset(from.x, from.y - 200f), steps = 16)
            scene.frames(30)
            assertTrue(centre(0).y < before - 100f, "grown past its foot, the rail did not scroll")
            assertEquals(listOf(1), rail.clicks, "grown past its foot, the drag chose a destination")

            rail.count(3)
            scene.frames(30)
            scene.drag(from = centre(1), to = centre(2), steps = 12)
            scene.frames(10)
            assertEquals(listOf(1, 2), rail.clicks, "back to three, the drag should move the marker again")
        }
    }
}
