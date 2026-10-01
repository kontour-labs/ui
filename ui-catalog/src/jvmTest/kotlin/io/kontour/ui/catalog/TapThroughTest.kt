package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Pencil
import com.composables.icons.tabler.outline.Share
import io.kontour.ui.components.action.IconButton
import io.kontour.ui.components.action.Toolbar
import io.kontour.ui.components.display.Card
import io.kontour.ui.foundation.Surface
import io.kontour.ui.foundation.Text
import io.kontour.ui.nav.NavRail
import io.kontour.ui.nav.TopBar
import io.kontour.ui.overlay.OverlayEntry
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.overlay.OverlayHostState
import io.kontour.ui.overlay.OverlayLayer
import io.kontour.ui.overlay.ScrimStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A tap on a component's surface is the component's, not whatever is drawn
 * beneath it.
 *
 * Reported for the top bar — *"the top bar is having that same issue the navbar
 * had with being able to click through it. tapping it should consume that
 * tap"* — after the nav bar had been fixed for exactly this on its own. It was
 * never a nav bar fault: `Surface`, which nearly everything is built on, was not
 * a pointer target, so a tap on any part of one with no control in it fell to
 * the next thing down. Two cases, and they are not the same rule:
 *
 * - **Beneath**: a sibling drawn under the component — a list scrolled under a
 *   bar, a map under a card, the page under a tooltip. Every surface stops
 *   these now.
 * - **Around**: a clickable container the component sits in. A card in a
 *   clickable row *should* press the row, so a plain surface lets this through.
 *   A bar is chrome and does not — a toolbar over a drawing canvas is inside
 *   the canvas — so bars keep presses from their container too.
 *
 * Each case has its control, a tap off the component that the page does take,
 * so a pass is a measurement rather than a page that was never listening.
 */
class TapThroughTest {

    private var pageTaps = 0
    private var actionTaps = 0
    private var bounds = Rect.Zero

    @Test
    fun aTapOnATopBarDoesNotReachTheListUnderIt() {
        Scene(width = 800, height = 600) {
            Box(Modifier.fillMaxSize()) {
                Page()
                TopBar(modifier = Modifier.reportBounds { bounds = it }, actions = { Action() }) { +"Perth Underground" }
            }
        }.use { scene ->
            scene.frames(4)
            scene.tap(bounds.alongX(0.5f))
            scene.frames(2)
            assertEquals(0, pageTaps, "a tap on the top bar's title reached the list scrolled under it")
            control(scene)
        }
    }

    @Test
    fun aTapOnATopBarDoesNotReachAClickableContainerRoundIt() {
        Scene(width = 800, height = 600) {
            Box(Modifier.fillMaxSize().background(PageColour).clickable { pageTaps++ }) {
                TopBar(modifier = Modifier.reportBounds { bounds = it }, actions = { Action() }) { +"Perth Underground" }
            }
        }.use { scene ->
            scene.frames(4)
            scene.tap(bounds.alongX(0.5f))
            scene.frames(2)
            assertEquals(0, pageTaps, "a tap on the top bar's title pressed the container round it")
            control(scene)
        }
    }

    /** The bar keeps the press, and its own action still gets one. */
    @Test
    fun aTopBarsActionStillTakesItsTap() {
        Scene(width = 800, height = 600) {
            Box(Modifier.fillMaxSize().clickable { pageTaps++ }) {
                TopBar(modifier = Modifier.reportBounds { bounds = it }, actions = { Action() }) { +"Perth Underground" }
            }
        }.use { scene ->
            scene.frames(4)
            // The action is the last thing in the bar, a target's width in.
            scene.tap(Offset(bounds.right - 24f * Density, bounds.center.y))
            scene.frames(2)
            assertEquals(1, actionTaps, "the top bar's action did not take its tap")
            assertEquals(0, pageTaps)
        }
    }

    /**
     * Keeping a press is not keeping a drag: a top bar drawn in a scrolling
     * column — a gallery of them, or a title that scrolls away — still scrolls
     * it from a drag that starts on the bar.
     */
    @Test
    fun aDragThatStartsOnABarStillScrollsThePageRoundIt() {
        val scroll = androidx.compose.foundation.ScrollState(0)
        Scene(width = 800, height = 600) {
            Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
                TopBar(modifier = Modifier.reportBounds { bounds = it }) { +"Perth Underground" }
                Box(Modifier.fillMaxWidth().height(2000.dp))
            }
        }.use { scene ->
            scene.frames(4)
            val from = Offset(bounds.center.x, bounds.bottom - 8f)
            scene.drag(from, from - Offset(0f, 200f))
            scene.frames(4)
            assertTrue(scroll.value > 100, "a drag from the bar moved the page ${scroll.value}px of 200")
        }
    }

    /** A toolbar floating inside a canvas: a press between two tools draws nothing. */
    @Test
    fun aPressBetweenTwoToolsDoesNotReachTheCanvas() {
        val tools = mutableListOf(Rect.Zero, Rect.Zero)
        Scene(width = 800, height = 600) {
            Box(Modifier.fillMaxSize().background(PageColour).clickable { pageTaps++ }) {
                Toolbar(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp).reportBounds { bounds = it },
                    arrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(32.dp),
                ) {
                    IconButton({ actionTaps++ }, Tabler.Outline.Pencil, "Draw", Modifier.reportBounds { tools[0] = it })
                    IconButton({ actionTaps++ }, Tabler.Outline.Share, "Share", Modifier.reportBounds { tools[1] = it })
                }
            }
        }.use { scene ->
            scene.frames(4)
            scene.tap(Offset((tools[0].right + tools[1].left) / 2f, tools[0].center.y))
            scene.frames(2)
            assertEquals(0, pageTaps, "a press between two tools reached the canvas the toolbar sits in")
            scene.tap(tools[0].center)
            scene.frames(2)
            assertEquals(1, actionTaps, "the tool did not take its own tap")
            control(scene)
        }
    }

    @Test
    fun aTapOnANavRailDoesNotReachThePageUnderIt() {
        Scene(width = 800, height = 600) {
            Box(Modifier.fillMaxSize()) {
                Page()
                NavRail(modifier = Modifier.reportBounds { bounds = it }) {
                    item(true, {}, key = "a", icon = Tabler.Outline.Pencil) { +"Draw" }
                }
            }
        }.use { scene ->
            scene.frames(4)
            // Low on the rail, well below its one destination.
            scene.tap(bounds.alongY(0.85f))
            scene.frames(2)
            assertEquals(0, pageTaps, "a tap on the rail below its destinations reached the page under it")
            control(scene)
        }
    }

    /** A card over a map: a tap on the card is not a tap on the map. */
    @Test
    fun aTapOnACardDoesNotReachWhatIsUnderIt() {
        Scene(width = 800, height = 600) {
            Box(Modifier.fillMaxSize()) {
                Page()
                Card(Modifier.align(Alignment.Center).width(240.dp).reportBounds { bounds = it }) {
                    Text("Platform 2, 4 min")
                }
            }
        }.use { scene ->
            scene.frames(4)
            scene.tap(bounds.center)
            scene.frames(2)
            assertEquals(0, pageTaps, "a tap on a card reached the page under it")
            control(scene)
        }
    }

    /**
     * And the other half of the rule: a card is content, and a tap on one in a
     * clickable row presses the row. Keeping that from the row as a bar keeps
     * it would break every list whose rows are built from cards.
     */
    @Test
    fun aCardInAClickableRowStillPressesTheRow() {
        Scene(width = 800, height = 600) {
            Box(Modifier.fillMaxSize().clickable { pageTaps++ }) {
                Card(Modifier.align(Alignment.Center).width(240.dp).reportBounds { bounds = it }) {
                    Text("Platform 2, 4 min")
                }
            }
        }.use { scene ->
            scene.frames(4)
            scene.tap(bounds.center)
            scene.frames(2)
            assertEquals(1, pageTaps, "a tap on a card in a clickable row did not press the row")
        }
    }

    /**
     * An overlay that lets the page through around it — a tooltip, a coach
     * mark, a popover without a scrim — does not let it through *itself*.
     */
    @Test
    fun aTapOnAnUnscrimmedPanelDoesNotReachThePage() {
        val host = OverlayHostState()
        Scene(width = 800, height = 600) {
            OverlayHost(state = host) { Page() }
        }.use { scene ->
            scene.frames(2)
            host.show(
                OverlayEntry(
                    key = "tip",
                    layer = OverlayLayer.Tooltip,
                    scrim = ScrimStyle.None,
                    content = {
                        Box(Modifier.fillMaxSize()) {
                            Surface(Modifier.align(Alignment.Center).size(200.dp, 80.dp).reportBounds { bounds = it }) {
                                Text("Tap a stop to see its departures")
                            }
                        }
                    },
                )
            )
            scene.frames(20)
            scene.tap(bounds.center)
            scene.frames(2)
            assertEquals(0, pageTaps, "a tap on a tooltip's panel reached the page under it")
            control(scene)
        }
    }

    /** A tap clear of the component, which the page has to take for any of this to mean anything. */
    private fun control(scene: Scene) {
        val before = pageTaps
        scene.tap(Offset(bounds.center.x, (bounds.bottom + 40f * Density).coerceAtMost(600f - 4f)).let {
            if (bounds.bottom + 40f * Density < 596f) it else Offset((bounds.right + 40f * Density).coerceAtMost(796f), bounds.center.y)
        })
        scene.frames(2)
        assertEquals(before + 1, pageTaps, "the page did not take a tap clear of the component, so this measured nothing")
    }

    @androidx.compose.runtime.Composable
    private fun Page() {
        Box(Modifier.fillMaxSize().background(PageColour).clickable { pageTaps++ })
    }

    @androidx.compose.runtime.Composable
    private fun Action() {
        IconButton({ actionTaps++ }, Tabler.Outline.Share, "Share")
    }

    private companion object {
        val PageColour = Color(0xFFEEDDCC)
        const val Density = 2f
    }
}
