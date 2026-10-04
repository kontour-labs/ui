package io.kontour.ui.components.display

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Loading, empty, failed and the content, in one place, with a transition
 * between them.
 *
 * Asked for: *"add a 'loading' state to empty state/error state, and allow
 * transitioning between the two"*. A `when` swaps one state for another in a
 * frame; `StateSwitcher` fades one through the other and moves the space they
 * take smoothly, and a screen reader hears only the one arriving.
 */
@OptIn(ExperimentalTestApi::class)
class StateSwitcherTest {

    private enum class Status { Loading, Empty }

    @Test
    fun theLeavingStateStaysWhileItGoesAndIsNotRead() = runComposeUiTest {
        var status by mutableStateOf(Status.Loading)
        var loadingComposed = false
        setContent {
            KontourTheme(reduceMotion = false) {
                StateSwitcher(status, Modifier.fillMaxWidth().testTag("switcher")) { s ->
                    when (s) {
                        Status.Loading -> {
                            DisposableEffect(Unit) {
                                loadingComposed = true
                                onDispose { loadingComposed = false }
                            }
                            LoadingState { +"Loading stops" }
                        }
                        Status.Empty -> EmptyState { +"No stops nearby" }
                    }
                }
            }
        }
        waitForIdle()
        onNodeWithText("Loading stops").assertExistsCompat()

        mainClock.autoAdvance = false
        status = Status.Empty
        mainClock.advanceTimeBy(60)
        assertTrue(loadingComposed, "the loading state should still be there, fading out, 60ms in")
        assertEquals(
            0, onAllNodesWithText("Loading stops").fetchSemanticsNodes().size,
            "the state leaving should not be read while it goes",
        )
        assertEquals(1, onAllNodesWithText("No stops nearby").fetchSemanticsNodes().size, "the state arriving should be read at once")

        mainClock.autoAdvance = true
        waitForIdle()
        assertTrue(!loadingComposed, "the loading state should be gone once the change is done")
    }

    @Test
    fun theSizeChangesSmoothly() = runComposeUiTest {
        var tall by mutableStateOf(false)
        setContent {
            KontourTheme(reduceMotion = false) {
                StateSwitcher(tall, Modifier.width(300.dp).testTag("switcher")) { t ->
                    Box(Modifier.fillMaxWidth().height(if (t) 300.dp else 100.dp))
                }
            }
        }
        waitForIdle()
        val short = onNodeWithTag("switcher").fetchSemanticsNode().size.height
        mainClock.autoAdvance = false
        tall = true
        mainClock.advanceTimeBy(100)
        val between = onNodeWithTag("switcher").fetchSemanticsNode().size.height
        mainClock.autoAdvance = true
        waitForIdle()
        val full = onNodeWithTag("switcher").fetchSemanticsNode().size.height
        assertTrue(full > short, "the tall state is $full against $short")
        assertTrue(between in (short + 1) until full, "100ms in, the height should be on its way: $short, then $between, then $full")
    }

    @Test
    fun underReducedMotionTheSizeIsTheNewOneAtOnce() = runComposeUiTest {
        var tall by mutableStateOf(false)
        setContent {
            KontourTheme(reduceMotion = true) {
                StateSwitcher(tall, Modifier.width(300.dp).testTag("switcher")) { t ->
                    Box(Modifier.fillMaxWidth().height(if (t) 300.dp else 100.dp))
                }
            }
        }
        waitForIdle()
        mainClock.autoAdvance = false
        tall = true
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeByFrame()
        val height = onNodeWithTag("switcher").fetchSemanticsNode().size.height
        mainClock.autoAdvance = true
        waitForIdle()
        assertEquals(onNodeWithTag("switcher").fetchSemanticsNode().size.height, height, "under reduced motion the size should not travel")
    }

    @Test
    fun aLoadingStateWithNoTitleSaysLoading() = runComposeUiTest {
        setContent { KontourTheme(reduceMotion = true) { LoadingState() } }
        waitForIdle()
        val node = onNodeWithContentDescription("Loading").fetchSemanticsNode()
        assertEquals(LiveRegionMode.Polite, node.config.getOrNull(SemanticsProperties.LiveRegion))
    }

    @Test
    fun aLoadingStateWithATitleSaysItsTitle() = runComposeUiTest {
        setContent { KontourTheme(reduceMotion = true) { LoadingState { +"Loading departures" } } }
        waitForIdle()
        onNodeWithText("Loading departures").assertExistsCompat()
        assertEquals(0, onAllNodesWithText("Loading").fetchSemanticsNodes().size, "with a title, the spinner should not also say Loading")
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertExistsCompat() {
        fetchSemanticsNode()
    }
}
