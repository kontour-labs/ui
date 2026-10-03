package io.kontour.ui.catalog

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.navigationevent.DirectNavigationEventInput
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import io.kontour.ui.catalog.apps.AppHost
import io.kontour.ui.catalog.apps.MiniApp
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Messages & Notes, run as the gallery runs it: in its host, with a real back
 * input on the gallery's dispatcher, at a phone's size and a desktop's.
 *
 * What is checked is what the app is for — the list and the conversation as a
 * stack on a phone and side by side on a desktop, back through the stack and
 * then out of the app, a message that goes and an answer that comes, the other
 * tab — rather than how any one component draws, which the gallery's own tests
 * already cover.
 */
@OptIn(ExperimentalTestApi::class)
class MessagesAppTest {

    /** The gallery's dispatcher, with a back input on it, as a phone's would be. */
    private class Gallery {
        val dispatcher = NavigationEventDispatcher()
        val input = DirectNavigationEventInput().also { dispatcher.addInput(it) }
        val owner = object : NavigationEventDispatcherOwner {
            override val navigationEventDispatcher = dispatcher
        }
        var left = false
    }

    private fun ComposeUiTest.launch(): Gallery {
        val gallery = Gallery()
        setContent {
            KontourTheme(darkTheme = false) {
                CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides gallery.owner) {
                    AppHost(MiniApp.Messages, onLeave = { gallery.left = true })
                }
            }
        }
        waitForIdle()
        return gallery
    }

    private fun ComposeUiTest.back(gallery: Gallery) {
        runOnUiThread { gallery.input.backCompleted() }
        waitForIdle()
    }

    private fun ComposeUiTest.tabs(): Int =
        onAllNodesWithTag("tab-chats", useUnmergedTree = true).fetchSemanticsNodes().size

    /** The composer's editable node: the tag is on the field's frame, the text on the field inside it. */
    private val composer = hasSetTextAction() and (hasTestTag("composer") or hasAnyAncestor(hasTestTag("composer")))

    @Test
    fun onAPhoneAChatOpensOverTheListAndBackLeavesTheApp() = runDesktopComposeUiTest(width = 400, height = 860) {
        val gallery = launch()

        onNodeWithTag("chat-ari").performClick()
        waitForIdle()
        onNodeWithTag("composer").assertExists()
        // Over the nav bar as well: the composer has the bottom of the screen.
        assertEquals(0, tabs(), "on a phone the conversation has the nav bar under its composer")

        back(gallery)
        onNodeWithTag("composer").assertDoesNotExist()
        onNodeWithTag("chat-ari").assertExists()
        assertEquals(1, tabs(), "back at the list, the nav bar did not come back")
        assertFalse(gallery.left, "back from the conversation left the app instead of returning to the list")

        back(gallery)
        assertTrue(gallery.left, "back from the chat list did not leave the app")
    }

    @Test
    fun onADesktopTheConversationSitsBesideTheList() = runDesktopComposeUiTest(width = 1280, height = 800) {
        launch()

        onNodeWithTag("chat-crew").performClick()
        waitForIdle()
        onNodeWithTag("composer").assertExists()
        onNodeWithTag("chat-ari").assertExists()
        // Beside the list, with the rail still at the side.
        assertEquals(1, tabs(), "on a desktop the conversation covered the rail")
        onNodeWithText("Coffee at Edith Cowan after?").assertExists()
    }

    @Test
    fun aSentMessageShowsAndIsAnswered() = runDesktopComposeUiTest(width = 400, height = 860) {
        launch()

        onNodeWithTag("chat-ari").performClick()
        waitForIdle()
        onNode(composer).performTextInput("On my way")
        onNodeWithContentDescription("Send").performClick()
        waitForIdle()
        onNodeWithText("On my way").assertExists()

        // The scripted reply, after its pause.
        mainClock.advanceTimeBy(2_000)
        waitForIdle()
        onNodeWithText("Legend, thank you.").assertExists()
    }

    @Test
    fun theNotesTabShowsTheNotes() = runDesktopComposeUiTest(width = 400, height = 860) {
        launch()

        onNodeWithTag("tab-notes").performClick()
        waitForIdle()
        onNodeWithText("Weekend plan").assertExists()
        onNodeWithTag("note-biscuits").assertExists()
        onNodeWithTag("chat-ari").assertDoesNotExist()
    }

    @Test
    fun aCallCoversTheNavigationAndEndsBackInTheConversation() = runDesktopComposeUiTest(width = 400, height = 860) {
        launch()

        onNodeWithTag("chat-kai").performClick()
        waitForIdle()
        onNodeWithContentDescription("Call Kai Tran").performClick()
        waitForIdle()
        onNodeWithText("Calling…").assertExists()

        // End sits where the bar would be on a phone. Were the bar drawn over
        // the call, the press would land on a tab instead.
        onNodeWithText("End call").performClick()
        waitForIdle()
        onNodeWithTag("composer").assertExists()
    }

    @Test
    fun controlKOpensThePaletteAndItGoesToAChat() = runDesktopComposeUiTest(width = 1280, height = 800) {
        launch()

        onRoot().performKeyInput {
            keyDown(Key.CtrlLeft)
            pressKey(Key.K)
            keyUp(Key.CtrlLeft)
        }
        waitForIdle()
        onNodeWithText("Go to Kai Tran").performClick()
        waitForIdle()
        onNodeWithTag("composer").assertExists()
        onNodeWithText("Carriage two, every weekday").assertExists()
    }
}
