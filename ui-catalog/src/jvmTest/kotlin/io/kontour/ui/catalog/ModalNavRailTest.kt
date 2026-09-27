package io.kontour.ui.catalog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Home
import com.composables.icons.tabler.outline.Route
import com.composables.icons.tabler.outline.Star
import io.kontour.ui.nav.ModalNavRail
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.sheet.SheetSide
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The sidebar on a phone: a `NavRail` at its widest, in the overlay host.
 *
 * What it adds to a rail is all behaviour — appearing and disappearing on the
 * app's `visible`, closing itself when a destination is chosen, and in which
 * order it does the two — and the side it comes from. The order is the subtle
 * one and the reason `dismissOnItemClick` is documented at length: the
 * destination's `onClick` runs first, while the rail is open and still, and the
 * dismissal follows on the next frame, so the new page's expensive first frame
 * lands where nothing is animating. A rail that dismissed first and navigated
 * second would pass any test that only checked both happened.
 *
 * Asserted on the semantics tree, which is where the overlay host puts the
 * panel's contents alongside the page's.
 */
@OptIn(ExperimentalTestApi::class)
class ModalNavRailTest {

    /** What the rail was asked to do, in the order it asked. */
    private val events = mutableStateListOf<String>()

    private fun ComposeUiTest.rail(
        visible: () -> Boolean,
        onDismiss: () -> Unit,
        dismissOnItemClick: Boolean = true,
        side: SheetSide = SheetSide.Start,
    ) {
        setContent {
            KontourTheme(darkTheme = false, reduceMotion = true) {
                OverlayHost(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize())
                    ModalNavRail(
                        visible = visible(),
                        onDismissRequest = onDismiss,
                        side = side,
                        dismissOnItemClick = dismissOnItemClick,
                    ) {
                        item(true, { events += "home" }, key = "home", icon = Tabler.Outline.Home, modifier = Modifier.testTag("home")) {
                            +"Home"
                        }
                        item(false, { events += "saved" }, key = "saved", icon = Tabler.Outline.Star) { +"Saved" }
                        group(false, {}, key = "routes", icon = Tabler.Outline.Route, header = { +"Routes" }) {
                            item(false, { events += "950" }, key = "950", icon = null) { +"950" }
                        }
                    }
                }
            }
        }
    }

    /**
     * Nothing until `visible`; then a titled pane with every destination
     * labelled, since the rail is always at its widest — which also makes its
     * group a disclosure rather than a menu.
     */
    @Test
    fun aVisibleRailShowsItsPanelAndItems() = runComposeUiTest {
        var visible by mutableStateOf(false)
        rail(visible = { visible }, onDismiss = { visible = false })
        waitForIdle()
        onAllNodesWithText("Home").assertCountEquals(0)

        visible = true
        waitForIdle()

        onNodeWithText("Home").fetchSemanticsNode()
        onNodeWithText("Saved").fetchSemanticsNode()
        val pane = onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Navigation"))
            .fetchSemanticsNodes()
        assertEquals(1, pane.size, "the open rail has ${pane.size} panes titled \"Navigation\"")
        assertNotNull(
            onNodeWithText("Routes").fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription),
            "the modal rail's group reports no expanded state — at its widest it should be a disclosure",
        )
    }

    /** Choosing a destination runs its `onClick`, then asks to be dismissed. */
    @Test
    fun choosingADestinationRunsItsClickAndThenDismisses() = runComposeUiTest {
        var visible by mutableStateOf(true)
        rail(visible = { visible }, onDismiss = { events += "dismiss"; visible = false })
        waitForIdle()

        onNodeWithText("Saved").performClick()
        waitForIdle()

        assertEquals(
            listOf("saved", "dismiss"), events.toList(),
            "choosing Saved asked for $events — the destination first, while the rail is still open, " +
                "and the dismissal after it",
        )
        onAllNodesWithText("Saved").assertCountEquals(0)
    }

    /** With `dismissOnItemClick = false` the rail stays open for the next choice. */
    @Test
    fun withoutDismissOnItemClickItStaysOpen() = runComposeUiTest {
        var visible by mutableStateOf(true)
        rail(visible = { visible }, onDismiss = { events += "dismiss"; visible = false }, dismissOnItemClick = false)
        waitForIdle()

        onNodeWithText("Saved").performClick()
        waitForIdle()
        onNodeWithText("Home").performClick()
        waitForIdle()

        assertEquals(
            listOf("saved", "home"), events.toList(),
            "with dismissOnItemClick = false, two choices asked for $events",
        )
        onNodeWithText("Saved").fetchSemanticsNode()
    }

    /** `side = SheetSide.End` puts the panel against the trailing edge. */
    @Test
    fun anEndSideRailIsAtTheTrailingEdge() {
        val placed = SheetSide.entries.associateWith { side ->
            var row = Rect.Zero
            var width = 0f
            runComposeUiTest {
                rail(visible = { true }, onDismiss = {}, side = side)
                waitForIdle()
                row = onNodeWithTag("home").fetchSemanticsNode().boundsInRoot
                width = onRoot().fetchSemanticsNode().boundsInRoot.width
            }
            row to width
        }

        val (start, width) = placed.getValue(SheetSide.Start)
        val (end, _) = placed.getValue(SheetSide.End)
        assertTrue(
            start.right < width / 2,
            "a Start rail's rows run ${start.left}..${start.right} in a ${width}px window — not at the leading edge",
        )
        assertTrue(
            end.left > width / 2 && width - end.right < start.left + 1f,
            "an End rail's rows run ${end.left}..${end.right} in a ${width}px window — not against the " +
                "trailing edge the way a Start rail's are against the leading one (${start.left}px in)",
        )
    }
}
