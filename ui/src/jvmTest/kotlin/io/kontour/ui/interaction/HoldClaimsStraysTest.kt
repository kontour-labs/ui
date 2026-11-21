package io.kontour.ui.interaction

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * On the web, a scroll that meets an edge while a finger holds a card is not the
 * browser's to take, and a scroll that has room still scrolls.
 *
 * Reported for 0.5.6 from the documentation on a phone: cards *"seem to just
 * randomly drop themselves"*. Driven in Chrome on Android, a card held at the
 * top of the page lost its touch on ten pixels of downward drift: the page's
 * list began a scroll it had no room for, Compose's web canvas read the scroll
 * that consumed nothing as the browser's to have, and the browser cancelled the
 * touch. [keepsHeldTouches] takes the leftover during a hold, so what Compose
 * sees at the root is a scroll that consumed it. The recorder here stands where
 * Compose's own observer does, above the theme.
 */
@OptIn(ExperimentalTestApi::class)
class HoldClaimsStraysTest {

    private class Probe {
        lateinit var page: LazyListState
        var picked = false
        var postScrolls = 0
        var leftover = Offset.Zero
    }

    private fun ComposeUiTest.screen(claims: Boolean): Probe {
        val probe = Probe()
        val recorder = object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput) {
                    probe.postScrolls++
                    probe.leftover += available
                }
                return Offset.Zero
            }
        }
        setContent {
            probe.page = rememberLazyListState()
            Box(Modifier.nestedScroll(recorder).keepsHeldTouches()) {
                LazyColumn(Modifier.size(400.dp, 600.dp), state = probe.page) {
                    item {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .testTag("held")
                                .pointerInput(claims) {
                                    detectPickUp(
                                        axis = PickUpAxis.Free,
                                        immediate = { false },
                                        claimsStrays = claims,
                                        onPickUp = { probe.picked = true },
                                        onCarry = { change, _ -> change.consume() },
                                        onPutDown = {},
                                        onCancel = {},
                                    )
                                },
                        )
                    }
                    items(40) { Box(Modifier.fillMaxWidth().height(80.dp)) }
                }
            }
        }
        waitForIdle()
        return probe
    }

    /** Down, drifting down the screen past the list's slop and inside the hold's budget, then held. */
    private fun ComposeUiTest.driftDownThenHold() {
        onNodeWithTag("held").performTouchInput {
            down(center)
            repeat(5) {
                advanceEventTime(16)
                moveBy(Offset(0f, 4f))
            }
        }
        mainClock.advanceTimeBy(1_000)
        waitForIdle()
    }

    @Test
    fun aScrollAtTheEdgeDuringAHoldIsKeptFromTheBrowser() = runComposeUiTest {
        val probe = screen(claims = true)
        driftDownThenHold()
        assertTrue(probe.postScrolls > 0, "the list never began a scroll, so this measured nothing")
        assertEquals(Offset.Zero, probe.leftover, "a scroll with nowhere to go reached the root unconsumed, which the browser takes")
        assertTrue(probe.picked, "the hold did not lift")
        assertEquals(0, probe.page.firstVisibleItemScrollOffset, "the page moved")
        assertEquals(0, HeldTouches.count, "a hold was left counted")
    }

    /** The control, and the default off the web: the leftover reaches the root. */
    @Test
    fun withoutItTheLeftoverReachesTheRoot() = runComposeUiTest {
        val probe = screen(claims = false)
        driftDownThenHold()
        assertTrue(probe.postScrolls > 0, "the list never began a scroll, so this measured nothing")
        assertTrue(probe.leftover != Offset.Zero, "nothing reached the root, so the control shows nothing")
    }

    /** A finger that means to scroll the page still scrolls it, and nothing is left counted. */
    @Test
    fun aScrollStillScrolls() = runComposeUiTest {
        val probe = screen(claims = true)
        onNodeWithTag("held").performTouchInput {
            down(center)
            repeat(12) {
                advanceEventTime(16)
                moveBy(Offset(0f, -20f))
            }
            up()
        }
        waitForIdle()
        assertTrue(probe.page.firstVisibleItemScrollOffset > 0 || probe.page.firstVisibleItemIndex > 0, "a scroll that began on the held thing did not scroll the page")
        assertFalse(probe.picked, "a scroll picked the thing up")
        assertEquals(0, HeldTouches.count, "a hold was left counted")
    }
}
