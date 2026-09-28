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
import io.kontour.ui.nav.NavBarScope
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import io.kontour.ui.nav.NavDefaults
import io.kontour.ui.nav.NavScope
import io.kontour.ui.nav.NavSearch
import io.kontour.ui.nav.NavigationSuiteScaffold
import io.kontour.ui.nav.NavigationSuiteType
import io.kontour.ui.nav.rememberNavSearchState
import io.kontour.ui.theme.KontourTheme
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
 * destinations rather than only after all of them. A [NavBar] places a `slot`
 * where it is written now, so the index belongs to
 * [io.kontour.ui.nav.NavigationSuiteScaffold], which is handed its destinations
 * once for every surface and so has to be told where the search goes among them
 * by counting.
 */
@OptIn(ExperimentalTestApi::class)
class NavBarLayoutTest {

    private companion object {
        /** Where the search slot is tagged, so it can be found among the icons. */
        const val SearchTag = "search"

        /** A phone, which is where five children in one row is under pressure. */
        val PhoneWidth = 360.dp

        /** Rounding on a `spacedBy`, and nothing more. */
        const val Slack = 1f
    }

    /** Three destinations, the first of them selected. */
    @Composable
    private fun NavScope.items() {
        item(true, {}, key = "nearby", icon = Tabler.Outline.MapPin) { +"Nearby" }
        item(false, {}, key = "routes", icon = Tabler.Outline.Bus) { +"Routes" }
        item(false, {}, key = "saved", icon = Tabler.Outline.Star) { +"Saved" }
    }

    @Test
    fun theActionLinesUpWithTheIconsWhenLabelsAreShown() {
        var action = Rect.Zero
        var indicator = Rect.Zero

        Scene(width = 800, height = 300) {
            Box(Modifier.fillMaxSize().background(Color.White)) {
                NavBar(
                    showLabels = true,
                    colours = NavDefaults.colours(indicator = Color.Transparent),
                ) {
                    items()
                    // A one-dp box in a weighted slot, which is centred in the
                    // row: it marks the row's own centre line.
                    slot(weight = 1f) { Box(Modifier.size(1.dp).reportBounds { indicator = it }) }
                    // The trailing action, a fixed-width slot.
                    slot { Box(Modifier.size(40.dp).reportBounds { action = it }) }
                }
            }
        }.use { scene -> scene.frames(6) }

        assertTrue(action.width > 0f, "the action never reported a size")
        assertTrue(indicator.width > 0f, "the search slot never reported a size")

        // The weighted slot is a one-dp-tall box centred in the row, so it marks
        // the row's own centre line. With labels the icons sit *above* that line,
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
                NavBar(showLabels = false) {
                    items()
                    slot(weight = 1f) { Box(Modifier.size(1.dp).reportBounds { middle = it }) }
                    slot { Box(Modifier.size(40.dp).reportBounds { action = it }) }
                }
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
        //
        // Through `NavigationSuiteScaffold`, which is where `searchIndex` lives
        // now: a `NavBar` puts a slot wherever it is written.
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

    /**
     * The gaps either side of a centre search match the gaps between destinations.
     *
     * `search` has always been documented as "its own shape in the row, sized to
     * what is left", and the `Box` holding it *was* sized to what was left — but
     * a `Box` offers its width rather than imposing it, so content that wraps
     * (a pill around an icon and a word, which is what a collapsed
     * [io.kontour.ui.nav.NavSearch] is) sat at the start of the slot and left the
     * remainder as a hole. On a 360dp bar with two destinations either side that
     * hole was **45dp**, against 8dp everywhere else in the row: four evenly
     * spaced circles and one conspicuous gap.
     *
     * Measured as gaps rather than positions. Where each destination lands
     * depends on how wide the search is, which depends on its placeholder — but
     * the *spacing* is the arrangement's and is the same number between every
     * pair whatever is in them, which is the property that was broken.
     *
     * The search is a `slot(weight = 1f)` written between the second and third
     * destinations, which is what `searchIndex = 2` put there.
     */
    @Test
    fun aCentreSearchLeavesTheSameGapsAsEverythingElse() {
        val bounds = childBounds(searchIndex = 2)
        val gaps = bounds.zipWithNext { left, right -> right.left - left.right }

        assertTrue(gaps.size == 4, "expected four gaps between five children, got ${gaps.size}")
        assertTrue(
            gaps.max() - gaps.min() <= Slack,
            "the bar's children are spaced " + gaps.joinToString(", ") { "${it}dp" } +
                " — a row arranged with one spacing should have one gap",
        )
    }

    /**
     * Every child of the bar in order, with a search at [searchIndex].
     *
     * The destinations are found by content description and the search by tag,
     * because they are different kinds of thing; both report the node the row
     * actually laid out, which is the point — a pill that draws narrower than the
     * box it was given would still report the box if this measured the box.
     */
    private fun childBounds(searchIndex: Int): List<Rect> {
        val order = listOf("Home", "Map", SearchTag, "Plan", "Profile")
        var bounds: List<Rect> = emptyList()

        runComposeUiTest {
            setContent {
                KontourTheme(darkTheme = false, reduceMotion = true) {
                    Box(Modifier.fillMaxSize().background(Color.White)) {
                        val search = rememberNavSearchState()
                        NavBar(modifier = Modifier.width(PhoneWidth)) {
                            described.forEachIndexed { index, name ->
                                if (index == searchIndex) {
                                    slot(weight = 1f) {
                                        NavSearch(
                                            state = search,
                                            modifier = Modifier.testTag(SearchTag),
                                            placeholder = "Search",
                                        )
                                    }
                                }
                                destination(name, selected = index == 1)
                            }
                        }
                    }
                }
            }

            bounds = order.map { name ->
                if (name == SearchTag) {
                    onNodeWithTag(name).fetchSemanticsNode().boundsInRoot
                } else {
                    onNodeWithContentDescription(name).fetchSemanticsNode().boundsInRoot
                }
            }
        }

        return bounds
    }

    /** Four destinations, named so they can be found without their labels shown. */
    private val described = listOf("Home", "Map", "Plan", "Profile")

    @Composable
    private fun NavBarScope.destination(name: String, selected: Boolean) {
        item(selected, {}, key = name, icon = Tabler.Outline.Star, contentDescription = name) { +name }
    }

    /** Where the search slot's content lands with a given [searchIndex]. */
    private fun searchXAt(searchIndex: Int?): Float {
        var search = Rect.Zero
        Scene(width = 800, height = 300) {
            Box(Modifier.fillMaxSize().background(Color.White)) {
                NavigationSuiteScaffold(
                    destinations = { items() },
                    type = NavigationSuiteType.Bar,
                    search = { Box(Modifier.size(24.dp).reportBounds { search = it }) },
                    searchIndex = searchIndex,
                    action = { Box(Modifier.size(40.dp)) },
                ) {}
            }
        }.use { scene -> scene.frames(6) }
        return search.center.x
    }
}
