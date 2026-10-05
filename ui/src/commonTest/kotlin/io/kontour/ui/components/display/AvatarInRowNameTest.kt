package io.kontour.ui.components.display

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.v2.runComposeUiTest
import io.kontour.ui.components.action.Button
import io.kontour.ui.components.list.ListItem
import io.kontour.ui.foundation.Text
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A picture inside a row does not become the row's name.
 *
 * Reported: *"An avatar inside a clickable card takes over the card's accessible
 * name."* A clickable card, a list row and a button each merge what is inside
 * them into one node, and a merged node's description is read in place of its
 * text — on Android instead of it, in the browser as its `aria-label`. An
 * avatar described itself by its person's name, so a chat row was announced as
 * "Ada Lovelace" and nothing else: not the last message, not the time.
 *
 * Read from the merged node, as a screen reader gets it.
 */
@OptIn(ExperimentalTestApi::class)
class AvatarInRowNameTest {

    private fun SemanticsNodeInteraction.descriptions(): List<String> =
        fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()

    private fun SemanticsNodeInteraction.texts(): List<String> =
        fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }

    @Test
    fun aRowIsNamedByWhatItSaysNotByItsAvatar() = runComposeUiTest {
        setContent {
            KontourTheme {
                ListItem(onClick = {}) {
                    leading { Avatar(name = "Ada Lovelace") }
                    +"Ada Lovelace"
                    supporting { Text("See you at nine") }
                }
            }
        }
        val row = onNode(hasClickAction())
        assertEquals(emptyList(), row.descriptions(), "the row's merged description should be empty, so its text names it")
        val texts = row.texts()
        assertTrue("See you at nine" in texts, "the row should still say its supporting line; it says $texts")
    }

    @Test
    fun aClickableCardIsNamedByWhatItSays() = runComposeUiTest {
        setContent {
            KontourTheme {
                Card(onClick = {}) {
                    Avatar(name = "Grace Hopper")
                    Text("Grace Hopper")
                    Text("Shared a route with you")
                }
            }
        }
        val card = onNode(hasClickAction())
        assertEquals(emptyList(), card.descriptions(), "the card's merged description should be empty")
        assertTrue("Shared a route with you" in card.texts(), "the card should say what it says; it says ${card.texts()}")
    }

    @Test
    fun aButtonIsNamedByItsLabel() = runComposeUiTest {
        setContent {
            KontourTheme {
                Button(onClick = {}) {
                    Avatar(name = "Ada Lovelace", size = AvatarSize.Small)
                    +"Your profile"
                }
            }
        }
        val button = onNode(hasClickAction())
        assertEquals(emptyList(), button.descriptions(), "the button's merged description should be empty")
        assertTrue("Your profile" in button.texts())
    }

    /** What a decoration means is still said, as part of the row's text. */
    @Test
    fun aDecorationInARowIsSaidAsText() = runComposeUiTest {
        setContent {
            KontourTheme {
                ListItem(onClick = {}) {
                    leading {
                        BadgedBox(badge = { Badge(count = 3, contentDescription = "3 unread") }) {
                            Avatar(name = "Ada Lovelace") { status(Color.Green, "Online") }
                        }
                    }
                    +"Ada Lovelace"
                }
            }
        }
        val row = onNode(hasClickAction())
        assertEquals(emptyList(), row.descriptions(), "neither the badge nor the avatar should name the row")
        val texts = row.texts()
        assertTrue("Online" in texts, "the avatar's status should be read with the row; it says $texts")
        assertTrue("3 unread" in texts, "the badge should be read with the row; it says $texts")
    }

    /** A group says who is there once, rather than "4 people, Ada, Grace, …". */
    @Test
    fun aGroupIsDescribedOnce() = runComposeUiTest {
        setContent {
            KontourTheme {
                AvatarGroup(names = listOf("Ada", "Grace", "Katherine"))
            }
        }
        val group = onNodeWithContentDescription("3 people", substring = true)
        assertEquals(listOf("3 people"), group.descriptions())
    }

    /** On its own, an avatar is still its person. */
    @Test
    fun aStandaloneAvatarStillSaysItsName() = runComposeUiTest {
        setContent { KontourTheme { Avatar(name = "Ada Lovelace") } }
        onNodeWithContentDescription("Ada Lovelace").assertExists()
    }

    /** An explicit description is the caller's to give, row or not. */
    @Test
    fun anExplicitDescriptionIsHonoured() = runComposeUiTest {
        setContent {
            KontourTheme {
                ListItem(onClick = {}) {
                    leading { Avatar(name = "Ada Lovelace", contentDescription = "Ada's photo") }
                    +"Ada Lovelace"
                }
            }
        }
        assertEquals(listOf("Ada's photo"), onNode(hasClickAction()).descriptions())
    }
}
