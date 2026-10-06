package io.kontour.ui.catalog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import io.kontour.icons.tabler.Tabler
import io.kontour.icons.tabler.outline.Home
import io.kontour.icons.tabler.outline.Map
import io.kontour.icons.tabler.outline.Settings
import io.kontour.ui.nav.NavBar
import io.kontour.ui.nav.NavRail
import io.kontour.ui.nav.NavScope
import io.kontour.ui.nav.more
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.KontourTheme
import io.kontour.ui.theme.Strings
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `more { }`: the destinations with no room of their own, behind a "More" at
 * the end of a bar, and inline wherever there is room.
 *
 * Asked for as *"an easy api to add a '…' menu to the end with one of those
 * dropdown options. this is useful for lots of nav items"*. It was a `group`
 * written out in full, with an icon, a name and a key to choose, and a rail
 * drawing it got a group it had no need for.
 */
@OptIn(ExperimentalTestApi::class)
class NavMoreTest {

    @Composable
    private fun NavScope.tree(chosen: MutableList<String>) {
        item(true, {}, key = "home", icon = Tabler.Outline.Home) { +"Home" }
        item(false, {}, key = "map", icon = Tabler.Outline.Map) { +"Map" }
        more {
            item(false, { chosen += "settings" }, key = "settings", icon = Tabler.Outline.Settings) { +"Settings" }
            item(false, { chosen += "help" }, key = "help", icon = null) { +"Help" }
        }
    }

    @Test
    fun inABarItIsAMenuNamedMore() = runComposeUiTest {
        val chosen = mutableStateListOf<String>()
        setContent {
            KontourTheme(darkTheme = false, reduceMotion = true) {
                OverlayHost(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize()) { NavBar { tree(chosen) } }
                }
            }
        }
        waitForIdle()

        val more = onNodeWithContentDescription("More")
        assertEquals(Role.Button, more.fetchSemanticsNode().config.getOrNull(SemanticsProperties.Role), "More is not a button")
        onAllNodesWithText("Settings").assertCountEquals(0)

        more.performClick()
        waitForIdle()
        onNodeWithText("Help").performClick()
        waitForIdle()
        assertEquals(listOf("help"), chosen.toList(), "picking Help from More ran $chosen")
        onAllNodesWithText("Settings").assertCountEquals(0)
    }

    /** Its name is the theme's, so a translation reaches it. */
    @Test
    fun itsNameIsTheThemes() = runComposeUiTest {
        setContent {
            KontourTheme(darkTheme = false, reduceMotion = true, strings = Strings(more = "Mehr")) {
                OverlayHost(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize()) { NavBar { tree(mutableListOf()) } }
                }
            }
        }
        waitForIdle()
        onNodeWithContentDescription("Mehr").fetchSemanticsNode()
        onAllNodesWithContentDescription("More").assertCountEquals(0)
    }

    /** A rail has room: the destinations are drawn where `more` is written, and there is no More. */
    @Test
    fun inARailItsDestinationsAreInline() = runComposeUiTest {
        setContent {
            KontourTheme(darkTheme = false, reduceMotion = true) {
                OverlayHost(Modifier.fillMaxSize()) {
                    NavRail(expanded = true) { tree(mutableListOf()) }
                }
            }
        }
        waitForIdle()
        onNodeWithText("Settings").fetchSemanticsNode()
        onNodeWithText("Help").fetchSemanticsNode()
        onAllNodesWithText("More").assertCountEquals(0)
        onAllNodesWithContentDescription("More").assertCountEquals(0)
    }
}
