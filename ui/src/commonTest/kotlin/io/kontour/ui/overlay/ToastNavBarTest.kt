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
