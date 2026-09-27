package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runComposeUiTest
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Bell
import com.composables.icons.tabler.outline.Bus
import com.composables.icons.tabler.outline.MapPin
import com.composables.icons.tabler.outline.Star
import io.kontour.ui.components.display.Badge
import io.kontour.ui.nav.NavBar
import io.kontour.ui.nav.NavDefaults
import io.kontour.ui.nav.NavRail
import io.kontour.ui.nav.NavScope
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.KontourTheme
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

/**
 * What the destinations DSL promises, whichever surface draws it.
 *
 * `NavScope` replaced an index and a list of items with calls that emit, and the
 * rewrite moved four guarantees out of the surfaces and into the scope: that
 * selection is **per destination**, not a position counted across the list;
 * that every destination has a **key of its own**, so the marker has somewhere
 * unambiguous to travel; that every destination has a **label**, even where
 * only its icon is drawn; and that the label is **announced** wherever it is not
 * drawn — as the node's name, with a badge and an override folded in.
 *
 * None of these shows in a golden. A bar with a duplicated key draws exactly the
 * right picture until the marker lands on the wrong circle, and a bar whose
 * labels are hidden and unannounced looks identical to one that is announced. So
 * each is asserted on what a screen reader or the marker actually sees: the
 * merged semantics tree and, for the one that is about the marker, its pixels.
 */
@OptIn(ExperimentalTestApi::class)
class NavScopeTest {

    /** Nothing else on a bar is drawn in this, so any of it on screen is the marker. */
    private val Marker = Color(0xFFFF00FF)

    /**
     * Only the destination that says it is selected reports selected.
     *
     * In a bar and in a rail, because they are two scopes implementing the same
     * interface, and a scope that forwarded `selected` to the wrong row would
     * pass on the other surface.
     */
    @Test
    fun onlyTheSelectedItemReportsSelected() = runComposeUiTest {
        var rail by mutableStateOf(false)
        setContent {
            KontourTheme(darkTheme = false, reduceMotion = true) {
                Box(Modifier.fillMaxSize()) {
                    val content: @Composable NavScope.() -> Unit = {
                        item(false, {}, key = "nearby", icon = Tabler.Outline.MapPin) { +"Nearby" }
                        item(true, {}, key = "routes", icon = Tabler.Outline.Bus) { +"Routes" }
                        item(false, {}, key = "saved", icon = Tabler.Outline.Star) { +"Saved" }
                    }
                    if (rail) NavRail(content = content) else NavBar(content = content)
                }
            }
        }

        for (surface in listOf("bar", "rail")) {
            rail = surface == "rail"
            waitForIdle()
            val selected = onAllNodes(isSelected()).fetchSemanticsNodes()
            assertEquals(
                1, selected.size,
                "the $surface reported ${selected.size} selected destinations — exactly one said it was",
            )
            val name = selected.single().config.getOrNull(SemanticsProperties.ContentDescription)
            assertEquals(
                listOf("Routes"), name,
                "the $surface's selected destination is $name, not the one that passed `selected = true`",
            )
        }
    }

    /**
     * A destination that appears ahead of the selected one does not take the
     * selection from it.
     *
     * The reason `selected` moved from an index on the surface to a flag on each
     * item. With an index of 1, a conditional destination inserted at 1 would be
     * the one marked; with a flag it is whichever destination says so. Asserted
     * twice: on the semantics, and on where the marker is drawn, since the marker
     * travels between *keys* and a key mix-up would move it without touching the
     * semantics.
     */
    @Test
    fun aConditionalItemDoesNotMoveTheSelection() = runComposeUiTest {
        var showAlerts by mutableStateOf(false)
        setContent {
            KontourTheme(darkTheme = false, reduceMotion = true) {
                Box(Modifier.fillMaxSize().background(Color.White).testTag("page")) {
                    NavBar(colours = NavDefaults.colours(indicator = Marker)) {
                        item(false, {}, key = "nearby", icon = Tabler.Outline.MapPin) { +"Nearby" }
                        if (showAlerts) {
                            item(false, {}, key = "alerts", icon = Tabler.Outline.Bell) { +"Alerts" }
                        }
                        item(true, {}, key = "routes", icon = Tabler.Outline.Bus) { +"Routes" }
                        item(false, {}, key = "saved", icon = Tabler.Outline.Star) { +"Saved" }
                    }
                }
            }
        }
        waitForIdle()
        val before = markerCentreX()
        val routesBefore = onNodeWithContentDescription("Routes").fetchSemanticsNode().boundsInRoot
        assertTrue(
            before != null && before in routesBefore.left..routesBefore.right,
            "the marker was at x=$before before anything changed, not on Routes at " +
                "${routesBefore.left}..${routesBefore.right} — this run cannot tell a move from a start",
        )

        showAlerts = true
        waitForIdle()

        val selected = onAllNodes(isSelected()).fetchSemanticsNodes()
        assertEquals(
            listOf(listOf("Routes")),
            selected.map { it.config.getOrNull(SemanticsProperties.ContentDescription) },
            "a destination appearing ahead of the selected one changed which one is selected",
        )
        val routes = onNodeWithContentDescription("Routes").fetchSemanticsNode().boundsInRoot
        val alerts = onNodeWithContentDescription("Alerts").fetchSemanticsNode().boundsInRoot
        val after = markerCentreX()
        assertTrue(
            after != null && after in routes.left..routes.right,
            "with Alerts shown the marker is centred at x=$after — Routes, still the selected " +
                "destination, is at ${routes.left}..${routes.right} and the newcomer at " +
                "${alerts.left}..${alerts.right}",
        )
    }

    /**
     * Two destinations with one key throw, rather than leaving the marker to
     * pick one.
     */
    @Test
    fun aDuplicateKeyThrows() {
        val failure = assertFails {
            runComposeUiTest {
                setContent {
                    KontourTheme(darkTheme = false, reduceMotion = true) {
                        NavBar {
                            item(true, {}, key = "home", icon = Tabler.Outline.MapPin) { +"Home" }
                            item(false, {}, key = "home", icon = Tabler.Outline.Star) { +"Also home" }
                        }
                    }
                }
                waitForIdle()
            }
        }
        assertTrue(
            failure.messages().any { "share the key" in it },
            "two destinations with the key \"home\" failed, but not with the duplicate-key " +
                "message: ${failure.messages()}",
        )
    }

    /** An empty `label {}` throws: the label is what a screen reader announces. */
    @Test
    fun aMissingLabelThrows() {
        val failure = assertFails {
            runComposeUiTest {
                setContent {
                    KontourTheme(darkTheme = false, reduceMotion = true) {
                        NavBar {
                            item(true, {}, key = "home", icon = Tabler.Outline.MapPin) {}
                        }
                    }
                }
                waitForIdle()
            }
        }
        assertTrue(
            failure.messages().any { "needs a label" in it },
            "a destination with an empty label failed, but not with the missing-label " +
                "message: ${failure.messages()}",
        )
    }

    /**
     * Where the label is not drawn it is still the destination's name; an
     * explicit `contentDescription` replaces it; and a badge is part of it.
     *
     * A bar with `showLabels = false` is the surface this matters on — it is the
     * default, and without the label becoming the description it is a row of
     * unlabelled tabs (WCAG 4.1.2).
     */
    @Test
    fun aHiddenLabelIsStillTheNameAndABadgeJoinsIt() = runComposeUiTest {
        setContent {
            KontourTheme(darkTheme = false, reduceMotion = true) {
                NavBar(showLabels = false) {
                    item(true, {}, key = "map", icon = Tabler.Outline.MapPin) { +"Map" }
                    item(
                        false, {}, key = "routes", icon = Tabler.Outline.Bus,
                        contentDescription = "Routes and timetables",
                    ) { +"Routes" }
                    item(false, {}, key = "alerts", icon = Tabler.Outline.Bell, badge = { Badge(count = 3) }) {
                        +"Alerts"
                    }
                }
            }
        }
        waitForIdle()

        val map = onNodeWithContentDescription("Map").fetchSemanticsNode()
        assertEquals(
            Role.Tab, map.config.getOrNull(SemanticsProperties.Role),
            "the node named by the hidden label \"Map\" is not the destination itself",
        )
        onAllNodesWithText("Map").assertCountEquals(0)

        onNodeWithContentDescription("Routes and timetables").fetchSemanticsNode()
        onAllNodesWithContentDescription("Routes").assertCountEquals(0)

        val alerts = onNodeWithContentDescription("Alerts").fetchSemanticsNode()
        val name = alerts.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
        // The badge says "3 new", so the count is inside a phrase rather than a
        // word of its own.
        assertTrue(
            name.any { "3" in it },
            "the badged destination announces $name — its badge's count is not part of it",
        )
        assertEquals(
            Role.Tab, alerts.config.getOrNull(SemanticsProperties.Role),
            "the badge's announcement landed on a node of its own rather than on the destination",
        )
    }

    /**
     * An item with no icon draws its label's initial in a collapsed rail, and
     * announces the whole label.
     *
     * The initial is drawn and hidden from assistive technology — a screen
     * reader that said "T" for Timetable would be worse than one that said
     * nothing — so it is asserted as ink in the glyph box and as no text node.
     */
    @Test
    fun anIconlessItemInACollapsedRailShowsItsInitial() = runComposeUiTest {
        var surface = Color.Unspecified
        setContent {
            KontourTheme(darkTheme = false, reduceMotion = true) {
                surface = NavDefaults.colours().container
                OverlayHost(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().background(Color.White).testTag("page")) {
                        NavRail {
                            item(false, {}, key = "timetable", icon = null, modifier = Modifier.testTag("item")) {
                                +"Timetable"
                            }
                        }
                    }
                }
            }
        }
        waitForIdle()

        onNodeWithContentDescription("Timetable").fetchSemanticsNode()
        onAllNodesWithText("T", useUnmergedTree = true).assertCountEquals(0)
        onAllNodesWithText("Timetable", useUnmergedTree = true).assertCountEquals(0)

        val bounds = onNodeWithTag("item").fetchSemanticsNode().boundsInRoot
        val image = onRoot().captureToImage().toPixelMap()
        val ground = surface.toArgb()
        var ink = 0
        for (y in bounds.top.toInt() until bounds.bottom.toInt()) {
            for (x in bounds.left.toInt() until bounds.right.toInt()) {
                if (x !in 0 until image.width || y !in 0 until image.height) continue
                if (distance(image[x, y].toArgb(), ground) > 60) ink++
            }
        }
        assertTrue(
            ink > 20,
            "an iconless destination in a collapsed rail drew $ink pixels of ink in its " +
                "${bounds.width}x${bounds.height} box — its initial should be there",
        )
    }

    /** Where the magenta marker's columns are centred, or null if it is not drawn. */
    private fun androidx.compose.ui.test.ComposeUiTest.markerCentreX(): Float? {
        val image = onNodeWithTag("page").captureToImage().toPixelMap()
        var left = -1
        var right = -1
        for (x in 0 until image.width) {
            for (y in 0 until image.height) {
                val p = image[x, y].toArgb()
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                if (r > 180 && b > 180 && g < 90) {
                    if (left < 0) left = x
                    right = x
                    break
                }
            }
        }
        return if (left < 0) null else (left + right) / 2f
    }

    private fun distance(a: Int, b: Int): Int = maxOf(
        abs(((a shr 16) and 0xFF) - ((b shr 16) and 0xFF)),
        abs(((a shr 8) and 0xFF) - ((b shr 8) and 0xFF)),
        abs((a and 0xFF) - (b and 0xFF)),
    )

    /** Every message down the cause chain, since a composition may wrap what it threw. */
    private fun Throwable.messages(): List<String> =
        generateSequence(this) { it.cause }.mapNotNull { it.message }.toList()
}
