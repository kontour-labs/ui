package io.kontour.ui.components.display

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import io.kontour.ui.a11y.LocalNamedByContent
import io.kontour.ui.a11y.NamedByContent
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/**
 * A card that becomes clickable, or stops being, keeps what is inside it.
 *
 * Its content was called from one of two places — inside the provider that
 * tells it the card names itself by what it says, or not — and two call sites
 * are two places in the composition. A card whose `onClick` came and went with
 * the data behind it (a row that can be opened once it has loaded, say) threw
 * its content away each time and built it again from nothing.
 */
@OptIn(ExperimentalTestApi::class)
class CardContentTest {

    @Test
    fun becomingClickableKeepsTheContent() = runComposeUiTest {
        var onClick by mutableStateOf<(() -> Unit)?>(null)
        var held: Any? = null
        var named: Boolean? = null
        setContent {
            KontourTheme {
                Card(onClick = onClick) {
                    held = remember { Any() }
                    named = LocalNamedByContent.current
                }
            }
        }
        waitForIdle()
        val before = held
        assertEquals(false, named, "a card that is not a button named its content as one")

        onClick = {}
        waitForIdle()
        assertEquals(true, named, "a clickable card did not tell its content it is named by it")
        assertSame(before, held, "the card's content was thrown away when the card became clickable")

        onClick = null
        waitForIdle()
        assertEquals(false, named)
        assertSame(before, held, "and again when it stopped being clickable")
    }

    /**
     * A card that is not a button says nothing of its own about naming, so one
     * inside a row that is named by its content stays named by it.
     */
    @Test
    fun aPlainCardLeavesTheNamingAroundItAlone() = runComposeUiTest {
        var named: Boolean? = null
        setContent {
            KontourTheme {
                NamedByContent {
                    Card { named = LocalNamedByContent.current }
                }
            }
        }
        waitForIdle()
        assertEquals(true, named, "a plain card inside a row named by its content cut its content off from it")
    }
}
