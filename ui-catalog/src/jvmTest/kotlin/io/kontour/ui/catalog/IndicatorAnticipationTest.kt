package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Bus
import com.composables.icons.tabler.outline.MapPin
import com.composables.icons.tabler.outline.Star
import com.composables.icons.tabler.outline.User
import io.kontour.ui.components.selection.SegmentedControl
import io.kontour.ui.nav.NavBar
import io.kontour.ui.nav.NavDefaults
import io.kontour.ui.nav.NavRail
import io.kontour.ui.nav.NavScope
import io.kontour.ui.nav.Tab
import io.kontour.ui.nav.TabBar
import java.awt.image.BufferedImage
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A press on another item leans the selection marker toward it, a little,
 * before the press lets go and the marker goes there.
 *
 * Asked for as *"if you tap another option, the indicator stretches/moves a
 * little bit towards it? a little bit like the animation before the segmented
 * control moves. same animation goes for dragging it"*. The segmented control's
 * thumb already leant toward a finger dragging it; this is the same reach, for
 * a press, in every control that draws its selection with the shared indicator:
 * the nav bar, the nav rail, the tab bar and the segmented control itself.
 *
 * What is held:
 *
 * - **It moves toward the item pressed, and grows**, from the edge it is
 *   leaving, by a fraction of its own size: at most a quarter of its span, and
 *   at most a fifth wider. A nudge, not the trip.
 * - **It lets go.** A press that slides off the item and is cancelled chooses
 *   nothing, and the marker is back where it was.
 * - **Not under reduced motion**, where the marker goes where it is sent
 *   without reaching for it first.
 * - **Mirrored** in a right-to-left bar, and **down a rail**, where the items
 *   are stacked.
 *
 * The marker is found from the pixels: a red page, a white bar and a blue
 * marker, as in `NavBarMarkerDragTest`.
 */
class IndicatorAnticipationTest {

    private companion object {
        const val Density = 2f
        val Page = Color(0xFFFF0000)
        val Marker = Color(0xFF0000FF)

        /** The least a lean has to move the marker to count: 2dp. */
        const val Visible = 2f * Density

        /** How far a marker that has let go may be from where it was. */
        const val Settled = 1
    }

    private val items = MutableList(4) { Rect.Zero }

    private fun centre(i: Int) = Offset(items[i].center.x, items[i].center.y)

    @Test
    fun aPressOnAnotherDestinationLeansTheMarkerTowardIt() {
        val clicks = mutableListOf<String>()
        navBar(clicks).use { scene ->
            val rest = scene.frames(30).alongRow(row())
            scene.press(centre(2))
            val held = scene.frames(30).alongRow(row())
            assertLeans(rest, held, toward = 1, "a nav bar's marker, the third destination pressed")
            assertEquals(emptyList(), clicks, "a destination was chosen before the press let go")

            // And a press is still a tap: the lift chooses it, and the marker goes.
            scene.release(centre(2))
            val there = scene.frames(40).alongRow(row())
            assertEquals(listOf("c"), clicks)
            assertTrue(
                abs(there.centre - items[2].center.x) <= Visible,
                "after the lift the marker went to ${there.centre}px, not the third destination at ${items[2].center.x}px",
            )
        }
    }

    /** The far end leans no further than the next one along: a nudge, not a share of the trip. */
    @Test
    fun aFarDestinationLeansNoFurtherThanTheNextOne() {
        var rest = Run(0, -1)
        val near = navBar().use { scene ->
            rest = scene.frames(30).alongRow(row())
            scene.press(centre(1))
            scene.frames(30).alongRow(row())
        }
        assertTrue(near.centre - rest.centre > Visible, "the second destination pressed did not lean the marker, so this measures nothing")
        val far = navBar().use { scene ->
            scene.frames(30)
            scene.press(centre(3))
            scene.frames(30).alongRow(row())
        }
        assertTrue(
            abs(far.centre - near.centre) <= Settled,
            "pressing the fourth destination leant the marker to ${far.centre}px and the second to ${near.centre}px",
        )
    }

    @Test
    fun aPressThatSlidesOffLetsTheMarkerGoBack() {
        val clicks = mutableListOf<String>()
        navBar(clicks).use { scene ->
            val rest = scene.frames(30).alongRow(row())
            scene.press(centre(2))
            val held = scene.frames(30).alongRow(row())
            assertTrue(abs(held.centre - rest.centre) > Visible, "the press never leant the marker, so this measures nothing")

            // Straight up, off the bar and onto the page: the press is cancelled.
            val from = centre(2)
            for (step in 1..10) {
                scene.move(Offset(from.x, from.y - (from.y - 10f) * step / 10f))
                scene.frame()
            }
            scene.release(Offset(from.x, 10f))
            val after = scene.frames(40).alongRow(row())
            assertEquals(emptyList(), clicks, "a press cancelled off the bar chose a destination")
            assertTrue(
                abs(after.start - rest.start) <= Settled && abs(after.end - rest.end) <= Settled,
                "let go, the marker is at ${after.start}..${after.end}px, not back at ${rest.start}..${rest.end}px",
            )
        }
    }

    @Test
    fun underReducedMotionTheMarkerStaysWhereItIs() {
        navBar(reduceMotion = true).use { scene ->
            val rest = scene.frames(30).alongRow(row())
            scene.press(centre(2))
            val held = scene.frames(30).alongRow(row())
            assertTrue(
                held.start == rest.start && held.end == rest.end,
                "under reduced motion a press moved the marker from ${rest.start}..${rest.end}px to ${held.start}..${held.end}px",
            )
        }
    }

    @Test
    fun rightToLeftItLeansTheOtherWay() {
        navBar(rtl = true).use { scene ->
            val rest = scene.frames(30).alongRow(row())
            assertTrue(items[2].center.x < items[0].center.x, "the bar is not mirrored, so this measures nothing")
            scene.press(centre(2))
            val held = scene.frames(30).alongRow(row())
            assertLeans(rest, held, toward = -1, "a right-to-left nav bar's marker")
        }
    }

    @Test
    fun downARailItLeansTowardTheItemBelow() {
        Scene(width = 400, height = 700, density = Density) {
            var here by mutableStateOf("a")
            Box(Modifier.fillMaxSize().background(Page)) {
                NavRail(colours = NavDefaults.colours(container = Color.White, indicator = Marker, selectedContent = Color.White)) {
                    destinations(selected = { it == here }, choose = { here = it })
                }
            }
        }.use { scene ->
            val first = scene.frames(30)
            val column = items[0].center.x.toInt()
            val rest = first.alongColumn(column)
            scene.press(centre(2))
            val held = scene.frames(30).alongColumn(column)
            assertLeans(rest, held, toward = 1, "a nav rail's marker, the third item pressed")
        }
    }

    @Test
    fun aTabBarsMarkerLeansTowardTheTabPressed() {
        Scene(width = 800, height = 200, density = Density) {
            var here by mutableStateOf(0)
            Box(Modifier.fillMaxSize().background(Color.White).padding(16.dp)) {
                TabBar(indicatorColour = Marker) {
                    listOf("Stops", "Routes", "Alerts", "Saved").forEachIndexed { i, label ->
                        Tab(
                            selected = here == i,
                            onClick = { here = i },
                            key = label,
                            modifier = Modifier.reportBounds { items[i] = it },
                        ) { +label }
                    }
                }
            }
        }.use { scene ->
            val first = scene.frames(30)
            val y = (items[0].top + items[0].height * 0.2f).toInt()
            val rest = first.alongRow(y)
            scene.press(centre(2))
            val held = scene.frames(30).alongRow(y)
            assertLeans(rest, held, toward = 1, "a tab bar's marker, the third tab pressed")
        }
    }

    /** And the control the reach came from: a press on a segment, not only a drag. */
    @Test
    fun aSegmentedControlsThumbLeansTowardTheSegmentPressed() {
        var bounds = Rect.Zero
        Scene(width = 700, height = 200, density = Density) {
            var selected by mutableStateOf(0)
            Box(Modifier.fillMaxSize().background(Color.White).padding(20.dp)) {
                SegmentedControl(
                    options = listOf("Depart", "Arrive", "Last"),
                    selectedIndex = selected,
                    onSelectedIndexChange = { selected = it },
                    modifier = Modifier.fillMaxWidth().reportBounds { bounds = it },
                )
            }
        }.use { scene ->
            val rest = scene.frames(30).thumb(bounds)
            scene.press(Offset(bounds.left + bounds.width * 5f / 6f, bounds.center.y))
            val held = scene.frames(30).thumb(bounds)
            assertLeans(rest, held, toward = 1, "a segmented control's thumb, the last segment pressed")
        }
    }

    /**
     * [held] has moved toward the pressed item, [toward] being +1 for along the
     * axis and -1 against it, by a visible amount and by no more than a quarter
     * of its own span; and it has grown, by no more than a fifth, from the edge
     * it is leaving.
     */
    private fun assertLeans(rest: Run, held: Run, toward: Int, what: String) {
        val moved = (held.centre - rest.centre) * toward
        assertTrue(
            moved > Visible,
            "$what stayed at ${held.start}..${held.end}px from ${rest.start}..${rest.end}px: no lean toward it",
        )
        assertTrue(
            moved <= rest.length / 4f,
            "$what leant ${moved}px, more than a quarter of its own ${rest.length}px: that is the trip, not a nudge",
        )
        assertTrue(
            held.length > rest.length + Visible && held.length <= rest.length * 1.2f + 2,
            "$what is ${held.length}px long leaning, from ${rest.length}px at rest: it should stretch, by at most a fifth",
        )
        val leaving = if (toward > 0) held.start - rest.start else rest.end - held.end
        assertTrue(leaving >= -Settled, "$what's trailing edge moved away from where it is going, by ${-leaving}px")
    }

    private fun navBar(
        clicks: MutableList<String> = mutableListOf(),
        reduceMotion: Boolean = false,
        rtl: Boolean = false,
    ): Scene = Scene(width = 800, height = 300, density = Density, reduceMotion = reduceMotion) {
        var here by mutableStateOf("a")
        val direction = if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr
        CompositionLocalProvider(LocalLayoutDirection provides direction) {
            Box(Modifier.fillMaxSize().background(Page), contentAlignment = Alignment.BottomCenter) {
                NavBar(
                    colours = NavDefaults.colours(
                        container = Color.White,
                        content = Color.Black,
                        indicator = Marker,
                        selectedContent = Color.White,
                    ),
                ) {
                    destinations(selected = { it == here }, choose = { clicks += it; here = it })
                }
            }
        }
    }

    /** The four destinations, in a bar or a rail. */
    @Composable
    private fun NavScope.destinations(selected: (String) -> Boolean, choose: (String) -> Unit) {
        val icons = listOf(Tabler.Outline.MapPin, Tabler.Outline.Bus, Tabler.Outline.Star, Tabler.Outline.User)
        listOf("a", "b", "c", "d").forEachIndexed { i, key ->
            item(
                selected = selected(key),
                onClick = { choose(key) },
                key = key,
                icon = icons[i],
                modifier = Modifier.reportBounds { items[i] = it },
            ) { +key }
        }
    }

    /** The destinations' height, through the middle of the marker. */
    private fun row() = items[0].center.y.toInt()

    /** A marker's extent along one line of pixels, first to last. */
    private class Run(val start: Int, val end: Int) {
        val length get() = end - start + 1
        val centre get() = (start + end + 1) / 2f
    }

    private fun isMarker(argb: Int): Boolean {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return b > 200 && r < 80 && g < 80
    }

    private fun BufferedImage.alongRow(y: Int): Run {
        val xs = (0 until width).filter { isMarker(getRGB(it, y)) }
        assertTrue(xs.isNotEmpty(), "no marker on row $y")
        return Run(xs.first(), xs.last())
    }

    private fun BufferedImage.alongColumn(x: Int): Run {
        val ys = (0 until height).filter { isMarker(getRGB(x, it)) }
        assertTrue(ys.isNotEmpty(), "no marker in column $x")
        return Run(ys.first(), ys.last())
    }

    /**
     * The segmented control's thumb: the longest run that differs from the
     * sunken track, a quarter of the way down so the labels do not split it.
     */
    private fun BufferedImage.thumb(bounds: Rect): Run {
        val y = (bounds.top + bounds.height * 0.25f).toInt()
        val track = getRGB(bounds.left.toInt() + 6, y) and 0xFF
        var best = Run(0, -1)
        var start = -1
        for (x in bounds.left.toInt()..bounds.right.toInt()) {
            val differs = x < bounds.right.toInt() && abs((getRGB(x, y) and 0xFF) - track) > 8
            if (differs && start < 0) start = x
            if (!differs && start >= 0) {
                if (x - start > best.length) best = Run(start, x - 1)
                start = -1
            }
        }
        assertTrue(best.length > 0, "no thumb found")
        return best
    }
}
