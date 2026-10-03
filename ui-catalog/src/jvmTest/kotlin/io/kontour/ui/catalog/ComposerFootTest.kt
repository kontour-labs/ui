package io.kontour.ui.catalog

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.union
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import io.kontour.ui.catalog.apps.messages.ConversationPage
import io.kontour.ui.catalog.apps.messages.MessagesState
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The conversation's composer stands clear of what is under the foot of the
 * page: the gesture bar, the keyboard, or a navigation bar — whichever is
 * tallest, never two of them added.
 *
 * Reported as *"in some of your demos, it doesn't respect the device's safe
 * area properly (e.g. the message bar in the chat demo). please make sure we
 * have that spacing, and also ensure the spacing is the minimum of the safe
 * zone and the ime (when that pops up). we don't want that extra padding above
 * the keyboard when it is open"*. A phone's conversation covers its navigation,
 * so the navigation's padding is nothing there, and the composer cleared the
 * keyboard alone: with it down, the bar sat on the gesture bar.
 *
 * The window's own insets are nothing on a desktop, so the page is handed the
 * ones a phone would have.
 */
@OptIn(ExperimentalTestApi::class)
class ComposerFootTest {

    @Test
    fun theComposerStandsOnTheTallestOfWhatIsUnderIt() = runDesktopComposeUiTest(width = 400, height = 860) {
        var foot by mutableStateOf(WindowInsets(0))
        var nav by mutableStateOf(0f)
        setContent {
            KontourTheme(darkTheme = false) {
                OverlayHost(Modifier.fillMaxSize()) {
                    val scope = rememberCoroutineScope()
                    val state = remember { MessagesState(scope) }
                    ConversationPage(
                        state = state,
                        chatId = "ari",
                        onBack = null,
                        onCall = {},
                        contentPadding = PaddingValues(bottom = nav.dp),
                        foot = foot,
                    )
                }
            }
        }
        fun bottom(): Float {
            waitForIdle()
            return onNodeWithTag("composer").fetchSemanticsNode().boundsInRoot.bottom
        }
        val bare = bottom()

        foot = WindowInsets(bottom = GestureBar.dp)
        assertEquals(GestureBar, bare - bottom(), 1f, "the composer did not stand on the gesture bar")

        foot = WindowInsets(bottom = GestureBar.dp).union(WindowInsets(bottom = Keyboard.dp))
        assertEquals(Keyboard, bare - bottom(), 1f, "with the keyboard up the composer is not on the keyboard's top, nor above it by the gesture bar")

        foot = WindowInsets(bottom = GestureBar.dp)
        nav = NavBar
        assertEquals(NavBar, bare - bottom(), 1f, "inside a navigation bar the composer is not on the bar")
    }

    private companion object {
        const val GestureBar = 34f
        const val Keyboard = 300f
        const val NavBar = 96f
    }
}
