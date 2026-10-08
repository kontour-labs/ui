package io.kontour.ui.components.display

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import io.kontour.ui.foundation.Text
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A state's sections stack, one under another.
 *
 * Reported from an app: *"`StateSwitcher` stacks its content like a `Box`. When
 * a page's content had several sections, they drew on top of each other."* An
 * archive and several forms broke, and were worked round by keeping the
 * content out of the switcher.
 */
@OptIn(ExperimentalTestApi::class)
class StateSwitcherLayoutTest {

    @Test
    fun aStatesSectionsAreLaidOutOneUnderAnother() = runComposeUiTest {
        setContent {
            KontourTheme(reduceMotion = true) {
                StateSwitcher(target = "ready") { _ ->
                    Text("Archive")
                    Text("October")
                    Text("September")
                }
            }
        }
        waitForIdle()
        val tops = listOf("Archive", "October", "September").map { onNodeWithText(it).getBoundsInRoot() }
        assertTrue(
            tops.zipWithNext().all { (a, b) -> b.top >= a.bottom },
            "the sections overlap: ${tops.map { it.top to it.bottom }}",
        )
    }
}
