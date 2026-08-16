package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Bus
import com.composables.icons.tabler.outline.MapPin
import com.composables.icons.tabler.outline.Star
import io.kontour.ui.nav.NavBar
import io.kontour.ui.nav.NavItem
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Where a nav bar's trailing action sits, and where its search slot can.
 *
 * With `showLabels` the items grow a word taller, and the action was centred in
 * the row — which put a trailing FAB half a label lower than the icons it is
 * meant to sit beside. Measured against an item's *icon*, which is the line
 * everything on a bar reads along.
 *
 * `searchIndex` is the other half: a search slot that can sit between two
 * destinations rather than only after all of them.
 */
class NavBarLayoutTest {

    private val items = listOf(
        NavItem(label = "Nearby", icon = Tabler.Outline.MapPin, onClick = {}),
        NavItem(label = "Routes", icon = Tabler.Outline.Bus, onClick = {}),
        NavItem(label = "Saved", icon = Tabler.Outline.Star, onClick = {}),
    )

    @Test
    fun theActionLinesUpWithTheIconsWhenLabelsAreShown() {
        var action = Rect.Zero
        var indicator = Rect.Zero

        Scene(width = 800, height = 300) {
            Box(Modifier.fillMaxSize().background(Color.White)) {
                NavBar(
                    items = items,
                    selectedIndex = 0,
                    showLabels = true,
                    // A stand-in for the selected item's circle, reported so the
                    // action can be compared against the line the icons sit on.
                    indicatorColor = Color.Transparent,
                    search = { Box(Modifier.size(1.dp).reportBounds { indicator = it }) },
                    searchIndex = null,
                    action = { Box(Modifier.size(40.dp).reportBounds { action = it }) },
                )
            }
        }.use { scene -> scene.frames(6) }

        assertTrue(action.width > 0f, "the action never reported a size")
        assertTrue(indicator.width > 0f, "the search slot never reported a size")

        // The search slot is a one-dp box centred in the row, so it marks the
        // row's own centre line. With labels the icons sit *above* that line,
        // and so must the action.
        assertTrue(
            action.center.y < indicator.center.y - 4f,
            "with labels shown the action's centre is at ${action.center.y} and " +
                "the row's centre is at ${indicator.center.y} — the action is " +
                "still centred on the row rather than on the icons",
        )
    }

    @Test
    fun theActionStaysCentredWithoutLabels() {
        var action = Rect.Zero
        var middle = Rect.Zero

        Scene(width = 800, height = 300) {
            Box(Modifier.fillMaxSize().background(Color.White)) {
                NavBar(
                    items = items,
                    selectedIndex = 0,
                    showLabels = false,
                    search = { Box(Modifier.size(1.dp).reportBounds { middle = it }) },
                    action = { Box(Modifier.size(40.dp).reportBounds { action = it }) },
                )
            }
        }.use { scene -> scene.frames(6) }

        assertTrue(
            abs(action.center.y - middle.center.y) <= 4f,
            "without labels the action's centre is at ${action.center.y} and the " +
                "row's is at ${middle.center.y} — nothing should have moved",
        )
    }

    @Test
    fun searchCanSitBetweenTwoItems() {
        // Three renders, compared against each other rather than against a
        // number. The first version of this asserted the slot landed left of a
        // fixed x and *passed with `searchIndex` ignored*: the weighted box
        // aligns its content to the start, so the 24dp marker inside it sits at
        // roughly the same x wherever the box begins. Three placements that must
        // come out in order cannot be satisfied by a bar that ignores the index.
        val first = searchXAt(0)
        val middle = searchXAt(2)
        val last = searchXAt(null)

        assertTrue(first > 0f && middle > 0f && last > 0f, "a search slot never reported a size")
        assertTrue(
            first < middle,
            "searchIndex = 0 put the search slot at ${first}px and searchIndex = 2 " +
                "at ${middle}px — the index is not moving it",
        )
        assertTrue(
            middle < last,
            "searchIndex = 2 put the search slot at ${middle}px and no index at " +
                "${last}px — a middle search is not landing before the last item",
        )
    }

    /** Where the search slot's content lands with a given [searchIndex]. */
    private fun searchXAt(searchIndex: Int?): Float {
        var search = Rect.Zero
        Scene(width = 800, height = 300) {
            Box(Modifier.fillMaxSize().background(Color.White)) {
                NavBar(
                    items = items,
                    selectedIndex = 0,
                    search = { Box(Modifier.size(24.dp).reportBounds { search = it }) },
                    searchIndex = searchIndex,
                    action = { Box(Modifier.size(40.dp)) },
                )
            }
        }.use { scene -> scene.frames(6) }
        return search.center.x
    }
}
