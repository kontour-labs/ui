package io.kontour.ui.overlay

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A nested menu closes once the pointer has left it.
 *
 * It used to open on hover and then stay open until something else was clicked,
 * on the reasoning that closing on un-hover would close it while the pointer was
 * still crossing the gap to reach it. Half right: the gap is real, but the answer
 * is a grace period, not never closing. A submenu you have visibly moved away
 * from and that is still sitting there is the worse of the two.
 *
 * Both halves are pinned here, because a fix for either one alone reintroduces
 * the other bug.
 */
@OptIn(ExperimentalTestApi::class)
class SubMenuHoverTest {

    /** Comfortably past [MenuDefaults.SubmenuCloseDelay]. */
    private val pastTheGrace = MenuDefaults.SubmenuCloseDelay + 100

    @Test
    fun closesOnceThePointerHasLeftForGood() {
        runComposeUiTest {
            openTheSubmenu()

            // Onto a different row of the parent menu: off the submenu's trigger
            // and outside its panel.
            mainClock.autoAdvance = false
            hover("Sort by")

            assertEquals(
                1,
                count("Route"),
                "the submenu closed the instant the pointer left, with no grace " +
                    "period to cross the gap into it",
            )

            mainClock.advanceTimeBy(pastTheGrace)
            assertEquals(
                0,
                count("Route"),
                "the submenu was still open long after the pointer left it",
            )
        }
    }

    @Test
    fun survivesThePointerCrossingTheGapIntoIt() {
        runComposeUiTest {
            openTheSubmenu()

            mainClock.autoAdvance = false
            // Leaving the row is unavoidable on the way to the panel. Arriving
            // there within the grace period has to count as still hovering.
            hover("Sort by")
            mainClock.advanceTimeBy(MenuDefaults.SubmenuCloseDelay / 2)
            hover("Route")
            mainClock.advanceTimeBy(pastTheGrace)

            assertEquals(
                1,
                count("Route"),
                "the submenu closed while the pointer was on its way into it",
            )
        }
    }

    /**
     * Opens the submenu by hovering its row, with the clock still free-running.
     *
     * The mouse move is also what puts the tree into a hover-capable input
     * modality — `SubMenu` deliberately ignores hover for a finger.
     */
    private fun ComposeUiTest.openTheSubmenu() {
        setContent { Harness() }
        waitForIdle()
        hover("Group by")
        assertEquals(1, count("Route"), "hovering the row did not open the submenu")
    }

    /**
     * Moves the pointer to the middle of a row.
     *
     * The middle rather than a corner: a menu row's semantics bounds are grown by
     * `minimumTouchTarget` and its own padding, so a point just inside them can
     * still be outside the part that reacts to hover.
     */
    private fun ComposeUiTest.hover(text: String) {
        onNodeWithText(text).performMouseInput { moveTo(center) }
        if (mainClock.autoAdvance) waitForIdle() else mainClock.advanceTimeByFrame()
    }

    private fun ComposeUiTest.count(text: String): Int =
        onAllNodesWithText(text).fetchSemanticsNodes().size
}

@Composable
private fun Harness() {
    KontourTheme(reduceMotion = true) {
        OverlayHost(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().padding(24.dp)) {
                DropdownMenu(expanded = true, onDismissRequest = {}) {
                    item("Sort by") {}
                    submenu("Group by") {
                        item("Route") {}
                        item("Operator") {}
                    }
                }
            }
        }
    }
}
