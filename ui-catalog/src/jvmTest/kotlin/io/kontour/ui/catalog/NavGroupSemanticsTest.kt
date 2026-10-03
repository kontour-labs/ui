package io.kontour.ui.catalog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import io.kontour.icons.tabler.Tabler
import io.kontour.icons.tabler.outline.Home
import io.kontour.icons.tabler.outline.Route
import io.kontour.ui.nav.NavBar
import io.kontour.ui.nav.NavRail
import io.kontour.ui.nav.NavScope
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What a `group` is to a screen reader and to a finger, on each surface.
 *
 * The same call is two different controls. In a rail wide enough for labels it
 * is a **disclosure** — a `Role.Button` that says whether it is open and opens
 * its children in place, through the app's hoisted `expanded`. In a bar or a
 * collapsed rail there is no room to open anything in place, so it is a button
 * that opens a **menu** of its children beside it, and choosing one both goes
 * there and closes the menu.
 *
 * The part a golden cannot see is the whole of it: the role, the state
 * description, whether `onExpandedChange` is what the tap calls, and whether the
 * menu is still open after its item navigated. Asserted in the merged semantics
 * tree, the way a screen reader meets it.
 */
@OptIn(ExperimentalTestApi::class)
class NavGroupSemanticsTest {

    /** A destination, and a group of two routes the app records choosing. */
    @Composable
    private fun NavScope.tree(open: Boolean, onOpenChange: (Boolean) -> Unit, chosen: MutableList<String>) {
        item(true, {}, key = "home", icon = Tabler.Outline.Home) { +"Home" }
        group(open, onOpenChange, key = "routes", icon = Tabler.Outline.Route, header = { +"Routes" }) {
            item(false, { chosen += "950" }, key = "950", icon = null) { +"950" }
            item(false, { chosen += "998" }, key = "998", icon = null) { +"998" }
        }
    }

    /**
     * In a rail with room for labels, the header is a disclosure: a button whose
     * state says expanded or collapsed, and whose tap is `onExpandedChange`.
     */
    @Test
    fun anExpandedRailsGroupIsADisclosure() = runComposeUiTest {
        var open by mutableStateOf(false)
        val requested = mutableListOf<Boolean>()
        val chosen = mutableStateListOf<String>()
        setContent {
            KontourTheme(darkTheme = false, reduceMotion = true) {
                OverlayHost(Modifier.fillMaxSize()) {
                    NavRail(expanded = true) {
                        tree(open, { requested += it; open = it }, chosen)
                    }
                }
            }
        }
        waitForIdle()

        val header = onNodeWithText("Routes")
        val closed = header.fetchSemanticsNode().config
        assertEquals(Role.Button, closed.getOrNull(SemanticsProperties.Role), "a disclosure is not a Role.Button")
        assertEquals(
            "Collapsed", closed.getOrNull(SemanticsProperties.StateDescription),
            "a closed disclosure does not say it is collapsed",
        )
        assertNull(
            closed.getOrNull(SemanticsProperties.Selected),
            "the group claims to be selectable — it is not a destination and should not say it is one",
        )
        onAllNodesWithText("950").assertCountEquals(0)

        header.performClick()
        waitForIdle()

        assertEquals(listOf(true), requested, "tapping a closed group asked for $requested, not to open")
        assertEquals(
            "Expanded", onNodeWithText("Routes").fetchSemanticsNode().config
                .getOrNull(SemanticsProperties.StateDescription),
            "an open disclosure does not say it is expanded",
        )
        onNodeWithText("950").fetchSemanticsNode()

        onNodeWithText("Routes").performClick()
        waitForIdle()
        assertEquals(listOf(true, false), requested, "tapping an open group did not ask to close it")
        assertEquals(
            "Collapsed", onNodeWithText("Routes").fetchSemanticsNode().config
                .getOrNull(SemanticsProperties.StateDescription),
            "a disclosure closed again still says it is expanded",
        )
    }

    /**
     * In a bar the group is a button that opens a menu of its children, and
     * choosing one runs its `onClick` and closes the menu.
     */
    @Test
    fun aBarsGroupOpensAMenuThatClosesOnChoosing() = opensAMenu(rail = false)

    /** The same in a collapsed rail, whose menu opens beside it. */
    @Test
    fun aCollapsedRailsGroupOpensAMenuThatClosesOnChoosing() = opensAMenu(rail = true)

    private fun opensAMenu(rail: Boolean) = runComposeUiTest {
        val surface = if (rail) "collapsed rail" else "bar"
        val requested = mutableListOf<Boolean>()
        val chosen = mutableStateListOf<String>()
        setContent {
            KontourTheme(darkTheme = false, reduceMotion = true) {
                OverlayHost(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize()) {
                        if (rail) {
                            NavRail { tree(false, { requested += it }, chosen) }
                        } else {
                            NavBar { tree(false, { requested += it }, chosen) }
                        }
                    }
                }
            }
        }
        waitForIdle()

        // Labels are not drawn on either surface, so the group is found by the
        // name its header gives it.
        val group = onNodeWithContentDescription("Routes")
        val config = group.fetchSemanticsNode().config
        assertEquals(Role.Button, config.getOrNull(SemanticsProperties.Role), "a $surface's group is not a Role.Button")
        assertNull(
            config.getOrNull(SemanticsProperties.StateDescription),
            "a $surface's group says ${config.getOrNull(SemanticsProperties.StateDescription)} — it opens a " +
                "menu rather than disclosing anything in place, so it has no expanded state to report",
        )
        onAllNodesWithText("950").assertCountEquals(0)

        group.performClick()
        waitForIdle()

        onNodeWithText("950").fetchSemanticsNode()
        onNodeWithText("998").fetchSemanticsNode()
        assertTrue(
            requested.isEmpty(),
            "opening a $surface's menu called onExpandedChange($requested) — the hoisted `expanded` is the " +
                "disclosure's, and a menu is not one",
        )

        onNodeWithText("998").performClick()
        waitForIdle()

        assertEquals(listOf("998"), chosen.toList(), "choosing 998 from the $surface's menu ran $chosen")
        onAllNodesWithText("950").assertCountEquals(0)
        onAllNodesWithText("998").assertCountEquals(0)
    }
}
