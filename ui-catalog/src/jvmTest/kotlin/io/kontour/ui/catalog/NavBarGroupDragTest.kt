package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import io.kontour.icons.tabler.Tabler
import io.kontour.icons.tabler.outline.Bus
import io.kontour.icons.tabler.outline.MapPin
import io.kontour.icons.tabler.outline.Route
import io.kontour.icons.tabler.outline.User
import io.kontour.ui.nav.NavBar
import io.kontour.ui.nav.NavBarMarkerDrag
import io.kontour.ui.nav.NavDefaults
import io.kontour.ui.overlay.OverlayHost
import java.awt.image.BufferedImage
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The bar's marker, dragged to a group: let go over it and its menu opens, the
 * group keeps the marker while the menu is open, and the marker goes back if
 * nothing in it is picked.
 *
 * Asked for: *"when you drag the selection indicator onto an option that's a
 * dropdown, the dropdown expands, inviting the user to pick an option. while
 * the dropdown is open, the selection indicator should stay over the actual
 * option in the bar. if they don't pick an option, it should animate back to
 * where it was"*. A group was not a place the marker could go: a drag over one
 * landed on whichever destination was nearest, and chose it.
 *
 * Read from the pixels, as `NavBarMarkerDragTest` is: a red page, a white bar,
 * a blue marker. Whether the menu is open is whether its rows have been placed.
 */
class NavBarGroupDragTest {

    private companion object {
        const val Density = 2f
        val Page = Color(0xFFFF0000)
        val Marker = Color(0xFF0000FF)
        const val Slack = 2f * Density
    }

    /** The bar's four: two destinations, the group, one more destination. */
    private val cells = mutableListOf(Rect.Zero, Rect.Zero, Rect.Zero, Rect.Zero)

    /** The group's first row, once its menu has placed it. */
    private var row = Rect.Zero

    private class Bar(val scene: Scene, val clicks: MutableList<String>)

    private fun bar(drag: NavBarMarkerDrag = NavBarMarkerDrag.SelectOnRelease): Bar {
        val clicks = mutableListOf<String>()
        var here by mutableStateOf("a")
        val scene = Scene(width = 800, height = 600, density = Density, reduceMotion = true) {
            OverlayHost(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().background(Page), contentAlignment = Alignment.BottomCenter) {
                    NavBar(
                        markerDrag = drag,
                        colours = NavDefaults.colours(
                            container = Color.White,
                            content = Color.Black,
                            indicator = Marker,
                            selectedContent = Color.White,
                        ),
                    ) {
                        item(here == "a", { clicks += "a"; here = "a" }, key = "a", icon = Tabler.Outline.MapPin,
                            modifier = Modifier.reportBounds { cells[0] = it }) { +"a" }
                        item(here == "b", { clicks += "b"; here = "b" }, key = "b", icon = Tabler.Outline.Bus,
                            modifier = Modifier.reportBounds { cells[1] = it }) { +"b" }
                        group(
                            expanded = false,
                            onExpandedChange = {},
                            key = "g",
                            icon = Tabler.Outline.Route,
                            header = { +"g" },
                            modifier = Modifier.reportBounds { cells[2] = it },
                        ) {
                            item(here == "g1", { clicks += "g1"; here = "g1" }, key = "g1", icon = null,
                                modifier = Modifier.reportBounds { row = it }) { +"g1" }
                            item(here == "g2", { clicks += "g2"; here = "g2" }, key = "g2", icon = null) { +"g2" }
                        }
                        item(here == "d", { clicks += "d"; here = "d" }, key = "d", icon = Tabler.Outline.User,
                            modifier = Modifier.reportBounds { cells[3] = it }) { +"d" }
                    }
                }
            }
        }
        scene.frames(8)
        return Bar(scene, clicks)
    }

    private fun isMarker(argb: Int): Boolean {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return b > 200 && r < 80 && g < 80
    }

    /** The marker's horizontal centre, at the destinations' height. */
    private fun markerCentre(image: BufferedImage): Float {
        val y = cells[0].center.y.toInt()
        val xs = (0 until image.width).filter { isMarker(image.getRGB(it, y)) }
        assertTrue(xs.isNotEmpty(), "no marker at the destinations' height")
        return (xs.first() + xs.last() + 1) / 2f
    }

    private fun centre(i: Int) = Offset(cells[i].center.x, cells[i].center.y)

    private fun assertMarkerOn(i: Int, image: BufferedImage, what: String) {
        val at = markerCentre(image)
        assertTrue(abs(at - cells[i].center.x) <= Slack, "$what: the marker is at ${at}px, not on ${cells[i].center.x}px")
    }

    /** Drags the marker from the first destination to the group and lets go. */
    private fun Scene.dropOnTheGroup() {
        drag(from = centre(0), to = centre(2), steps = 16)
        frames(12)
    }

    @Test
    fun letGoOverAGroupItsMenuOpensAndKeepsTheMarker() {
        val bar = bar()
        bar.scene.use { scene ->
            scene.dropOnTheGroup()
            assertTrue(row != Rect.Zero, "letting go of the marker over the group should open its menu")
            assertEquals(emptyList(), bar.clicks, "letting go over the group chose ${bar.clicks}")
            assertMarkerOn(2, scene.frames(1), "while the group's menu is open")
            // And it stays there, rather than leaving once the drag is over.
            assertMarkerOn(2, scene.frames(20), "a moment later, with the menu still open")
        }
    }

    @Test
    fun closedWithoutAPickTheMarkerGoesBack() {
        val bar = bar()
        bar.scene.use { scene ->
            scene.dropOnTheGroup()
            assertTrue(row != Rect.Zero, "the menu did not open")
            // Outside the menu and the bar: the page above them.
            scene.tap(Offset(400f, 20f))
            scene.frames(20)
            assertEquals(emptyList(), bar.clicks, "closing the menu chose ${bar.clicks}")
            assertMarkerOn(0, scene.frames(1), "after the menu closed with nothing picked")
        }
    }

    @Test
    fun aPickInTheMenuKeepsTheMarkerOnTheGroup() {
        val bar = bar()
        bar.scene.use { scene ->
            scene.dropOnTheGroup()
            assertTrue(row != Rect.Zero, "the menu did not open")
            scene.tap(row.center)
            scene.frames(20)
            assertEquals(listOf("g1"), bar.clicks, "picking the first row ran ${bar.clicks}")
            assertMarkerOn(2, scene.frames(1), "after a pick inside it, the group carries the current destination")
        }
    }

    /**
     * Only on the lift: a group passed on the way to the next destination opens
     * nothing, and choosing as the marker goes still skips it.
     */
    @Test
    fun aGroupPassedOverOpensNothing() {
        val bar = bar(NavBarMarkerDrag.SelectWhileDragging)
        bar.scene.use { scene ->
            scene.drag(from = centre(0), to = centre(3), steps = 24, paceMillis = 30)
            scene.frames(12)
            assertEquals(listOf("b", "d"), bar.clicks, "a drag across the bar should choose each destination, not the group")
            assertEquals(Rect.Zero, row, "a group passed over should not have opened its menu")
            assertMarkerOn(3, scene.frames(1), "after the drag")
        }
    }
}
