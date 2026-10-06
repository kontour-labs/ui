package io.kontour.ui.overlay

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kontour.ui.nav.NavBar
import io.kontour.ui.nav.NavBarStyle
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A bottom toast stands above a navigation bar along the foot of the window.
 *
 * Asked for as *"can we make it so toasts clear the navbar (if applicable)?"* A
 * navigation bar is a component, not an inset, and the toast host only knew
 * the window's insets: a toast raised on a page with a bar came up over it,
 * covering the destinations it sat on.
 */
@OptIn(ExperimentalTestApi::class)
class ToastNavBarTest {

    private fun ComposeUiTest.page(style: NavBarStyle = NavBarStyle.Floating, barAtFoot: Boolean = true, bar: () -> Boolean = { true }) {
        setContent {
            KontourTheme(reduceMotion = true) {
                OverlayHost(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize()) {
                        if (!barAtFoot) Box(Modifier.fillMaxWidth().height(200.dp))
                        if (bar()) {
                            if (barAtFoot) Box(Modifier.weight(1f))
                            NavBar(style = style, modifier = Modifier.testTag("bar")) {
                                item(true, {}, key = "a", icon = null) { +"Nearby" }
                                item(false, {}, key = "b", icon = null) { +"Saved" }
                            }
                        }
                        if (!barAtFoot) Box(Modifier.weight(1f))
                    }
                    ToastHost(toasts)
                }
            }
        }
        waitForIdle()
    }

    private val toasts = ToastHostState()

    private fun ComposeUiTest.toastBottom(): Float {
        toasts.show("Saved to favourites")
        mainClock.advanceTimeBy(600)
        waitForIdle()
        return onAllNodesWithText("Saved to favourites").fetchSemanticsNodes().first().boundsInRoot.bottom
    }

    private fun ComposeUiTest.barTop(): Float = onNodeWithTag("bar", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.top

    @Test
    fun aToastStandsAboveAFloatingBar() = runComposeUiTest {
        page()
        mainClock.autoAdvance = false
        val bottom = toastBottom()
        assertTrue(bottom <= barTop(), "the toast's foot at ${bottom}px is over the bar, whose top is at ${barTop()}px")
    }

    @Test
    fun aToastStandsAboveADockedBar() = runComposeUiTest {
        page(style = NavBarStyle.Docked)
        mainClock.autoAdvance = false
        val bottom = toastBottom()
        assertTrue(bottom <= barTop(), "the toast's foot at ${bottom}px is over the docked bar, whose top is at ${barTop()}px")
    }

    /** The card itself: the node that announces the toast, not its words. */
    private fun ComposeUiTest.card(): Rect {
        toasts.show("Saved to favourites")
        mainClock.advanceTimeBy(600)
        waitForIdle()
        return onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion)).fetchSemanticsNodes().first().boundsInRoot
    }

    /**
     * Close above the bar: 8dp of page between them, as off the window's own
     * edge. It was 28 — `sm` above the bar and the card's own 16dp ring above
     * that — reported as *"the clearance above the nav bar is a bit too
     * large"*.
     */
    @Test
    fun aToastStandsCloseAboveTheBar() = runComposeUiTest {
        // Docked, so the bar's bounds are its surface: a floating bar's are the
        // capsule and the margin round it.
        page(style = NavBarStyle.Docked)
        mainClock.autoAdvance = false
        val gap = with(density) { (barTop() - card().bottom).toDp() }
        assertTrue(gap in 6.dp..10.dp, "the toast stands $gap above the bar")
    }

    /** And close to the window's foot, with no bar, and its top, shown there. */
    @Test
    fun aToastStandsCloseToTheWindowsEdge() = runComposeUiTest {
        var position by mutableStateOf(ToastPosition.Bottom)
        var height = 0f
        setContent {
            KontourTheme(reduceMotion = true) {
                OverlayHost(Modifier.fillMaxSize().onPlaced { height = it.size.height.toFloat() }) {
                    ToastHost(toasts, position = position)
                }
            }
        }
        waitForIdle()
        mainClock.autoAdvance = false
        val foot = with(density) { (height - card().bottom).toDp() }
        assertTrue(foot in 6.dp..10.dp, "a bottom toast stands $foot off the window's foot")

        toasts.clear()
        runOnUiThread { position = ToastPosition.Top }
        mainClock.advanceTimeBy(800)
        waitForIdle()
        val head = with(density) { card().top.toDp() }
        assertTrue(head in 6.dp..10.dp, "a top toast stands $head off the window's top")
    }

    @Test
    fun aBarMidPageDoesNotLiftTheToasts() = runComposeUiTest {
        page(barAtFoot = false)
        mainClock.autoAdvance = false
        val bottom = toastBottom()
        assertTrue(bottom > barTop() + 200f, "a bar 200dp down the page lifted the toast to ${bottom}px")
    }

    @Test
    fun theToastComesDownWhenTheBarGoes() = runComposeUiTest {
        var showing by mutableStateOf(true)
        page(bar = { showing })
        mainClock.autoAdvance = false
        val over = toastBottom()
        runOnUiThread { showing = false }
        mainClock.advanceTimeBy(800)
        waitForIdle()
        val after = onAllNodesWithText("Saved to favourites").fetchSemanticsNodes().first().boundsInRoot.bottom
        assertTrue(after > over + 40f, "with the bar gone the toast stayed up at ${after}px, from ${over}px")
    }
}
