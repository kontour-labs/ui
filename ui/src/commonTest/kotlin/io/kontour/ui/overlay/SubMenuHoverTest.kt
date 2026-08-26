package io.kontour.ui.overlay

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A nested menu opens on hover, stays open while you look at it, and closes once
 * the pointer has left.
 *
 * Each of those has been wrong in a different way, and each fix reintroduced the
 * one before it, so all three are pinned here together.
 *
 * ### Why this counts overlay entries rather than looking for the submenu's rows
 *
 * Because a submenu that opens and closes twice a second never leaves the
 * composition. The host animates an entry out and only drops it at the end, so a
 * submenu that closes and immediately reopens is turned around mid-exit with its
 * content mounted throughout. `onAllNodesWithText("Route")` returns 1 the whole
 * time, and a test written that way passes while the thing on screen is visibly
 * flashing — which is exactly what the first version of this file did.
 *
 * [OverlayHostState.visible] excludes what is leaving, which is precisely the
 * distinction, and is what the rest of the library means by "actually here". Two
 * entries is the parent menu plus its submenu.
 */
@OptIn(ExperimentalTestApi::class)
class SubMenuHoverTest {

    /** Comfortably past [MenuDefaults.SubmenuCloseDelay]. */
    private val pastTheGrace = MenuDefaults.SubmenuCloseDelay + 100

    /** Fine enough to land inside a blink rather than stepping over it. */
    private val aQuarterOfTheGrace = MenuDefaults.SubmenuCloseDelay / 4

    @Test
    fun closesOnceThePointerHasLeftForGood() {
        runComposeUiTest {
            val host = openTheSubmenu()

            // Onto a different row of the parent menu: off the submenu's trigger
            // and outside its panel.
            mainClock.autoAdvance = false
            hover("Sort by")

            assertEquals(
                2,
                host.visible.size,
                "the submenu closed the instant the pointer left, with no grace " +
                    "period to cross the gap into it",
            )

            mainClock.advanceTimeBy(pastTheGrace)
            assertEquals(
                1,
                host.visible.size,
                "the submenu was still open long after the pointer left it",
            )
        }
    }

    @Test
    fun staysOpenWhileThePointerRestsOnTheRow() {
        runComposeUiTest {
            val host = openTheSubmenu()

            // The pointer stays on the row and only jitters, which is what a hand
            // resting on a mouse actually does. The jitter matters: hover is
            // re-evaluated on movement, so a perfectly still pointer would never
            // notice anything changing above it.
            //
            // It used to blink, once per grace period, and the cause was the
            // submenu's own transparent scrim: it fills the host and sits above
            // the parent menu, so opening the submenu took the hover off the very
            // row that opened it. The row went cold, the submenu closed, its scrim
            // went with it — and the row was hovered again.
            mainClock.autoAdvance = false
            repeat(4) { step ->
                nudge("Group by", dx = if (step % 2 == 0) 1f else -1f)
                repeat(12) {
                    mainClock.advanceTimeBy(aQuarterOfTheGrace)
                    assertEquals(
                        2,
                        host.visible.size,
                        "the submenu closed while the pointer was still resting " +
                            "on the row that opened it",
                    )
                }
            }
        }
    }

    @Test
    fun survivesThePointerCrossingTheGapIntoIt() {
        runComposeUiTest {
            val host = openTheSubmenu()

            mainClock.autoAdvance = false
            // Leaving the row is unavoidable on the way to the panel. Arriving
            // there within the grace period has to count as still hovering.
            hover("Sort by")
            mainClock.advanceTimeBy(MenuDefaults.SubmenuCloseDelay / 2)
            hover("Route")
            mainClock.advanceTimeBy(pastTheGrace)

            assertEquals(
                2,
                host.visible.size,
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
    private fun ComposeUiTest.openTheSubmenu(): OverlayHostState {
        lateinit var host: OverlayHostState
        setContent { host = Harness() }
        waitForIdle()
        hover("Group by")
        assertEquals(2, host.visible.size, "hovering the row did not open the submenu")
        return host
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

    /** A pointer that has not gone anywhere, but has reported that it moved. */
    private fun ComposeUiTest.nudge(text: String, dx: Float) {
        onNodeWithText(text).performMouseInput { moveTo(center + Offset(dx, 0f)) }
        mainClock.advanceTimeByFrame()
    }
}

@Composable
private fun Harness(): OverlayHostState {
    val host = rememberOverlayHostState()
    KontourTheme(reduceMotion = true) {
        OverlayHost(Modifier.fillMaxSize(), state = host) {
            Box(Modifier.fillMaxSize().padding(24.dp)) {
                DropdownMenu(visible = true, onDismissRequest = {}) {
                    item("Sort by") {}
                    submenu("Group by") {
                        item("Route") {}
                        item("Operator") {}
                    }
                }
            }
        }
    }
    return host
}
