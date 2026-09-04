package io.kontour.ui.overlay

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.text.Select
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The page still scrolls while a dropdown is open.
 *
 * Reported against `Combobox`, with the suspicion that it is every dropdown and
 * popover — which it would be, because they all go through one transparent
 * scrim whose documented job was to block pointer input across the whole host.
 *
 * The control matters here more than usual. A first attempt at this test opened
 * the dropdown *after* scrolling the page, which had carried the field off the
 * top of the window — so the tap that was supposed to open the menu landed on
 * nothing, and the test passed by measuring a page with no dropdown over it.
 * So: scroll, scroll back, then open, and assert the menu is actually there
 * before believing anything the wheel does afterwards.
 */
@OptIn(ExperimentalTestApi::class)
class OverlayScrollTest {

    @Test
    fun theWheelReachesThePageWithADropdownOpen() = runComposeUiTest {
        setContent {
            KontourTheme {
                OverlayHost {
                    val scroll = rememberScrollState()
                    Scrolled = scroll
                    Column(
                        Modifier
                            .fillMaxSize()
                            .testTag(Page)
                            .verticalScroll(scroll),
                    ) {
                        var mode by remember { mutableStateOf("Train") }
                        Select(
                            value = mode,
                            options = listOf("Train", "Bus", "Tram", "Ferry"),
                            onValueChange = { mode = it },
                            label = "Mode",
                            modifier = Modifier.testTag(Field),
                        )
                        Box(Modifier.height(3000.dp))
                    }
                }
            }
        }
        waitForIdle()

        // The control: the page scrolls when nothing is over it.
        onNodeWithTag(Page).performMouseInput { scroll(6f) }
        waitForIdle()
        val closed = requireNotNull(Scrolled).value
        assertTrue(closed > 0, "the page would not scroll with nothing open, so this test proves nothing")

        // Back to the top, so the field is where it started and can be opened.
        onNodeWithTag(Page).performMouseInput { scroll(-12f) }
        waitForIdle()

        onNodeWithTag(Field).performClick()
        waitForIdle()
        // If this is not here the menu never opened, and the wheel below is
        // measuring an unobstructed page.
        onNodeWithText("Ferry").assertExists()

        val before = requireNotNull(Scrolled).value

        // The first notch is spent getting the dropdown out of the way — a
        // scrim is in front of the page for every kind of pointer event or
        // none, so the only way through it is for it to leave.
        onNodeWithTag(Page).performMouseInput { scroll(6f) }
        waitForIdle()
        onNodeWithText("Ferry").assertDoesNotExist()

        onNodeWithTag(Page).performMouseInput { scroll(6f) }
        waitForIdle()
        val after = requireNotNull(Scrolled).value

        assertTrue(
            after > before,
            "the page did not scroll after the dropdown was scrolled away: " +
                "$before then $after. The scrim is still eating the wheel.",
        )
    }

    /**
     * Scrolling the menu's *own* list is not scrolling the page.
     *
     * The distinction is not something the scrim decides — the menu is in front
     * of it, so a wheel over the menu never reaches the scrim at all — but it is
     * the thing that would break first if the dismissal were moved anywhere more
     * central, and it is the difference between a long dropdown you can read and
     * one that shuts the moment you try to.
     */
    @Test
    fun scrollingTheMenuItselfDoesNotCloseIt() = runComposeUiTest {
        setContent {
            KontourTheme {
                OverlayHost {
                    val scroll = rememberScrollState()
                    Scrolled = scroll
                    Column(
                        Modifier
                            .fillMaxSize()
                            .testTag(Page)
                            .verticalScroll(scroll),
                    ) {
                        var stop by remember { mutableStateOf("Stop 1") }
                        Select(
                            value = stop,
                            options = List(40) { "Stop ${it + 1}" },
                            onValueChange = { stop = it },
                            label = "Stop",
                            modifier = Modifier.testTag(Field),
                        )
                        Box(Modifier.height(3000.dp))
                    }
                }
            }
        }
        waitForIdle()

        onNodeWithTag(Field).performClick()
        waitForIdle()
        onNodeWithText("Stop 2").assertExists()

        val before = requireNotNull(Scrolled).value
        onNodeWithText("Stop 2").performMouseInput { scroll(4f) }
        waitForIdle()

        onNodeWithText("Stop 1").assertExists()
        assertEquals(
            before,
            requireNotNull(Scrolled).value,
            "the page scrolled while the wheel was over the menu's own list",
        )
    }

    private companion object {
        const val Page = "page"
        const val Field = "field"
    }
}

private var Scrolled: androidx.compose.foundation.ScrollState? = null
