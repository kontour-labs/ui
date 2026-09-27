package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Bus
import com.composables.icons.tabler.outline.Home
import com.composables.icons.tabler.outline.Route
import com.composables.icons.tabler.outline.Star
import io.kontour.ui.nav.NavDefaults
import io.kontour.ui.nav.NavRail
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.KontourTheme
import java.awt.image.BufferedImage
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * What a rail's tree turns into when there is no room to draw it as a tree.
 *
 * A collapsed rail is the same `NavScope` as a sidebar, flattened: a `section`'s
 * title becomes a hairline, and a `group`'s children go into a menu. Both
 * flattenings have a way to be wrong that no still frame shows.
 *
 * - **The hairline has to be the title's height.** Otherwise every icon below
 *   a section jumps as the rail grows past halfway and the title replaces it —
 *   exactly the movement `NavRailStillnessTest` exists to forbid, arriving
 *   through a door that test does not watch, since it has no sections.
 * - **The group has to carry the marker.** Its selected child is in a menu
 *   that is not open, so if the group does not show the marker nothing on the
 *   rail says where you are.
 *
 * The first is measured on the semantics tree — an item's top before and after
 * — and the second on the pixels, finding the marker by a colour only it is
 * drawn in, as `NavRailGroupMarkerTest` does.
 */
@OptIn(ExperimentalTestApi::class)
class NavRailFlattenTest {

    /** Nothing else in a rail draws in this, so any of it on screen is the marker. */
    private val Marker = Color(0xFFFF00FF)

    /**
     * A section's hairline in a collapsed rail is its title's height, so the
     * destinations under it are where they will be once the rail has grown.
     */
    @Test
    fun aSectionIsAHairlineOfTheTitlesHeight() = runComposeUiTest {
        var expanded by mutableStateOf(false)
        setContent {
            KontourTheme(darkTheme = false, reduceMotion = true) {
                Box(Modifier.fillMaxSize()) {
                    NavRail(expanded = expanded, onExpandedChange = { expanded = it }) {
                        item(true, {}, key = "home", icon = Tabler.Outline.Home) { +"Home" }
                        section("Saved", Modifier.testTag("section")) {
                            item(false, {}, key = "stops", icon = Tabler.Outline.Star, modifier = Modifier.testTag("stops")) {
                                +"Stops"
                            }
                            item(false, {}, key = "buses", icon = Tabler.Outline.Bus, modifier = Modifier.testTag("buses")) {
                                +"Buses"
                            }
                        }
                    }
                }
            }
        }
        waitForIdle()

        onAllNodesWithText("Saved").assertCountEquals(0)
        val narrowSection = onNodeWithTag("section").fetchSemanticsNode().boundsInRoot
        val narrowStops = onNodeWithTag("stops").fetchSemanticsNode().boundsInRoot
        val narrowBuses = onNodeWithTag("buses").fetchSemanticsNode().boundsInRoot

        expanded = true
        waitForIdle()

        val title = onNodeWithText("Saved").fetchSemanticsNode()
        assertTrue(
            SemanticsProperties.Heading in title.config,
            "the expanded rail's section title is not a heading",
        )
        val wideSection = onNodeWithTag("section").fetchSemanticsNode().boundsInRoot
        val wideStops = onNodeWithTag("stops").fetchSemanticsNode().boundsInRoot
        val wideBuses = onNodeWithTag("buses").fetchSemanticsNode().boundsInRoot

        assertTrue(
            narrowSection.height > 0f && abs(narrowSection.height - wideSection.height) <= 0.5f,
            "the collapsed rail's hairline is ${narrowSection.height}px tall and the title that " +
                "replaces it ${wideSection.height}px — everything under the section moves by the difference",
        )
        assertTrue(
            abs(narrowStops.top - wideStops.top) <= 0.5f && abs(narrowBuses.top - wideBuses.top) <= 0.5f,
            "the destinations under the section moved as the rail grew: Stops from ${narrowStops.top} " +
                "to ${wideStops.top}, Buses from ${narrowBuses.top} to ${wideBuses.top}",
        )
    }

    /**
     * A group whose selected child is hidden carries the marker: in a collapsed
     * rail, where the child is in a menu that is not open, and in an expanded
     * one where the group is closed.
     */
    @Test
    fun aGroupHidingTheSelectedChildCarriesTheMarker() {
        for (expanded in listOf(false, true)) {
            val rail = if (expanded) "an expanded rail with the group closed" else "a collapsed rail"
            var group = Rect.Zero
            var home = Rect.Zero
            val image = Scene(width = 700, height = 800) {
                KontourTheme(darkTheme = false, reduceMotion = true) {
                    OverlayHost(Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxSize().background(Color.White)) {
                            NavRail(expanded = expanded, colours = NavDefaults.colours(indicator = Marker)) {
                                item(false, {}, key = "home", icon = Tabler.Outline.Home, modifier = Modifier.reportBounds { home = it }) {
                                    +"Home"
                                }
                                group(
                                    expanded = false,
                                    onExpandedChange = {},
                                    key = "routes",
                                    icon = Tabler.Outline.Route,
                                    header = { +"Routes" },
                                    modifier = Modifier.reportBounds { group = it },
                                ) {
                                    item(true, {}, key = "950", icon = null) { +"950" }
                                    item(false, {}, key = "998", icon = null) { +"998" }
                                }
                            }
                        }
                    }
                }
            }.use { it.frames(30) }

            val marker = assertNotNull(
                image.markerRows(),
                "$rail drew no marker at all while its selected destination was hidden in a group — " +
                    "the rail says the page is nowhere",
            )
            assertTrue(
                group.height > 0f && home.height > 0f,
                "the group or the destination above it never reported where it is",
            )
            assertTrue(
                marker.first >= group.top - 2 && marker.last <= group.bottom + 2,
                "in $rail the marker is at rows $marker, not on the group at " +
                    "${group.top.toInt()}..${group.bottom.toInt()} (Home is at " +
                    "${home.top.toInt()}..${home.bottom.toInt()})",
            )
        }
    }

    /** The rows the magenta marker covers, or null when it is not drawn. */
    private fun BufferedImage.markerRows(): IntRange? {
        var top = -1
        var bottom = -1
        for (y in 0 until height) {
            for (x in 0 until width) {
                val p = getRGB(x, y)
                if ((p shr 16 and 0xFF) > 180 && (p and 0xFF) > 180 && (p shr 8 and 0xFF) < 90) {
                    if (top < 0) top = y
                    bottom = y
                    break
                }
            }
        }
        return if (top < 0) null else top..bottom
    }
}
