package io.kontour.ui.overlay

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.AccessibilityManager
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.v2.runComposeUiTest
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

/**
 * A toast waits for a reader who has asked for time, and for one who has
 * reached its action (WCAG 2.2.1).
 *
 * Its clock was the duration it was shown with and nothing else, so Android's
 * "Time to take action", which a reader sets so that something with a control
 * in it waits for them, did nothing, and a toast ran out under a keyboard
 * user who had just tabbed to "Undo".
 */
@OptIn(ExperimentalTestApi::class)
class ToastAccessibleTimeTest {

    /** A platform asking for four times as long as anything is shown for. */
    private object Patient : AccessibilityManager {
        override fun calculateRecommendedTimeoutMillis(
            originalTimeoutMillis: Long,
            containsIcons: Boolean,
            containsText: Boolean,
            containsControls: Boolean,
        ): Long = originalTimeoutMillis * 4
    }

    private fun alive(toasts: ToastHostState, id: Long): Boolean =
        toasts.toasts.any { it.id == id && it.presence.targetState }

    @Test
    fun theTimeTheReaderAskedForIsTheTimeItGets() = runComposeUiTest {
        val toasts = ToastHostState()
        setContent {
            CompositionLocalProvider(LocalAccessibilityManager provides Patient) {
                KontourTheme(reduceMotion = true) {
                    OverlayHost(Modifier.fillMaxSize()) { ToastHost(toasts) }
                }
            }
        }
        mainClock.autoAdvance = false
        val id = toasts.show("Trip saved", duration = 1_000.milliseconds)
        mainClock.advanceTimeBy(2_000)
        assertTrue(alive(toasts, id), "a toast asked to wait four seconds went in two")
        mainClock.advanceTimeBy(4_000)
        assertTrue(!alive(toasts, id), "a toast asked to wait four seconds was still there after six")
    }

    @Test
    fun focusOnItsActionStopsTheClock() = runComposeUiTest {
        val toasts = ToastHostState()
        setContent {
            KontourTheme(reduceMotion = true) {
                OverlayHost(Modifier.fillMaxSize()) { ToastHost(toasts) }
            }
        }
        mainClock.autoAdvance = false
        val id = toasts.show("Trip deleted", actionLabel = "Undo", onAction = {}, duration = 1_000.milliseconds)
        mainClock.advanceTimeBy(100)
        onNodeWithText("Undo").requestFocus()
        mainClock.advanceTimeBy(3_000)
        assertTrue(alive(toasts, id), "the toast ran out with keyboard focus on its action")
    }
}
