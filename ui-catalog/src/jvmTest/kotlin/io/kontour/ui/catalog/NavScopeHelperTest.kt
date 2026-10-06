package io.kontour.ui.catalog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import io.kontour.icons.tabler.Tabler
import io.kontour.icons.tabler.outline.Bus
import io.kontour.icons.tabler.outline.Home
import io.kontour.icons.tabler.outline.Route
import io.kontour.ui.nav.NavBar
import io.kontour.ui.nav.NavRail
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A destination written through a helper draws where the helper is called.
 *
 * Reported from an app: *"items I emitted inside `group { … }` through a helper
 * ended up added to the outer scope, outside their group."* `NavScope` is a DSL
 * scope with a marker on it, so a helper that reaches for the outer scope
 * through an implicit receiver does not compile. One that closes over it does:
 * a local function written in the outer block calls the outer scope's `item`
 * whatever block it is called from. The outer scope drew it as its own — over
 * a shut group's header in a rail, as an extra circle in a bar, and in a bar's
 * menu as the same key claimed twice, which throws.
 */
@OptIn(ExperimentalTestApi::class)
class NavScopeHelperTest {

    @Test
    fun aHelperInAShutRailGroupIsNotDrawn() = runComposeUiTest {
        setContent {
            KontourTheme(darkTheme = false, reduceMotion = true) {
                OverlayHost(Modifier.fillMaxSize()) {
                    NavRail(expanded = false) {
                        // Written in the rail's block: `item` is the rail's.
                        @Composable
                        fun stop(id: String, selected: Boolean = false) =
                            item(selected, {}, key = id, icon = Tabler.Outline.Bus) { +id }

                        item(false, {}, key = "home", icon = Tabler.Outline.Home) { +"Home" }
                        group(false, {}, key = "routes", icon = Tabler.Outline.Route, header = { +"Routes" }) {
                            stop("950", selected = true)
                        }
                    }
                }
            }
        }
        waitForIdle()
        assertEquals(
            0, onAllNodesWithContentDescription("950").fetchSemanticsNodes().size,
            "a destination inside a shut group should not be drawn, through a helper or not",
        )
    }

    @Test
    fun aHelperInAnOpenGroupIsIndented() = runComposeUiTest {
        setContent {
            KontourTheme(darkTheme = false, reduceMotion = true) {
                OverlayHost(Modifier.fillMaxSize()) {
                    NavRail(expanded = true) {
                        @Composable
                        fun stop(id: String) = item(false, {}, key = id, icon = Tabler.Outline.Bus) { +id }

                        item(true, {}, key = "home", icon = Tabler.Outline.Home) { +"Home" }
                        group(true, {}, key = "routes", icon = Tabler.Outline.Route, header = { +"Routes" }) {
                            stop("950")
                        }
                    }
                }
            }
        }
        waitForIdle()
        val home = onNodeWithText("Home", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.left
        val child = onNodeWithText("950", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.left
        assertTrue(child > home, "the group's child should be indented under it: it starts at ${child}px, as Home does at ${home}px")
    }

    @Test
    fun aHelperInABarGroupOpensInItsMenu() = runComposeUiTest {
        setContent {
            KontourTheme(darkTheme = false, reduceMotion = true) {
                OverlayHost(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize()) {
                        NavBar {
                            @Composable
                            fun stop(id: String) = item(false, {}, key = id, icon = Tabler.Outline.Bus) { +id }

                            item(true, {}, key = "home", icon = Tabler.Outline.Home) { +"Home" }
                            group(false, {}, key = "routes", icon = Tabler.Outline.Route, header = { +"Routes" }) {
                                stop("950")
                            }
                        }
                    }
                }
            }
        }
        waitForIdle()
        assertEquals(
            0, onAllNodesWithContentDescription("950").fetchSemanticsNodes().size,
            "a bar's group keeps its destinations in its menu: none should be drawn on the bar",
        )
        onNodeWithContentDescription("Routes").performClick()
        waitForIdle()
        assertEquals(
            1, onAllNodesWithText("950").fetchSemanticsNodes().size,
            "opened, the group's menu should hold its destination",
        )
    }

    /** A section runs in the scope it is in, so a helper there was always right; and still is. */
    @Test
    fun aHelperInASectionStaysInIt() = runComposeUiTest {
        setContent {
            KontourTheme(darkTheme = false, reduceMotion = true) {
                OverlayHost(Modifier.fillMaxSize()) {
                    NavRail(expanded = false) {
                        @Composable
                        fun stop(id: String) = item(false, {}, key = id, icon = Tabler.Outline.Bus) { +id }

                        item(true, {}, key = "home", icon = Tabler.Outline.Home) { +"Home" }
                        section("Saved") { stop("950") }
                    }
                }
            }
        }
        waitForIdle()
        assertEquals(1, onAllNodesWithContentDescription("950").fetchSemanticsNodes().size)
    }
}
