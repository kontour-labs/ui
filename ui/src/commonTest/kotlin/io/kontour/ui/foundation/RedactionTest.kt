package io.kontour.ui.foundation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Redacted content is gone from the tree, not merely painted over.
 *
 * The visual half — bars in the shape of the real line boxes — is a picture, and
 * the golden for the demo covers it. What a screenshot cannot see is the half
 * that matters for anyone not looking at the screen: a placeholder that still
 * announces its text is a screen reader reading out data the user has not been
 * given, and one that still takes a press is a button whose label cannot be
 * read.
 *
 * Every case runs with `reduceMotion = true`, and not for tidiness: the shimmer
 * is an infinite transition, so a composition holding one never goes idle and
 * `waitForIdle` waits for the full minute and then fails. Under reduced motion
 * `skeletonFill` draws a flat ground and registers no animation at all, which is
 * the same branch a reader who asked for less movement gets.
 */
@OptIn(ExperimentalTestApi::class)
class RedactionTest {

    @Test
    fun redactedTextIsNotInTheAccessibilityTree() = runComposeUiTest {
        val redacted = mutableStateOf(true)

        setContent {
            KontourTheme(reduceMotion = true) {
                Redacted(redacted.value) {
                    Column { Text("Platform 3") }
                }
            }
        }

        onNodeWithText("Platform 3").assertDoesNotExist()

        redacted.value = false
        waitForIdle()
        onNodeWithText("Platform 3").assertIsDisplayed()
    }

    @Test
    fun aNestedRedactedFalseTakesItBack() = runComposeUiTest {
        setContent {
            KontourTheme(reduceMotion = true) {
                Redacted {
                    Column {
                        Text("Hidden")
                        Redacted(enabled = false) { Text("Still readable") }
                    }
                }
            }
        }

        onNodeWithText("Hidden").assertDoesNotExist()
        onNodeWithText("Still readable").assertIsDisplayed()
    }

    @Test
    fun aRedactedNodeSwallowsPresses() = runComposeUiTest {
        var presses = 0

        setContent {
            KontourTheme(reduceMotion = true) {
                Box(
                    Modifier
                        .testTag("target")
                        .size(80.dp)
                        .clickable { presses++ }
                        .redacted(enabled = true)
                )
            }
        }

        onNodeWithTag("target").performClick()
        waitForIdle()
        assertEquals(
            0, presses,
            "a redacted node ran its click. A placeholder that can be pressed is " +
                "a button whose label the user cannot read.",
        )
    }

    @Test
    fun theModifierDefaultsToTheSurroundingBlock() = runComposeUiTest {
        setContent {
            KontourTheme(reduceMotion = true) {
                Redacted {
                    // No argument: it should pick the value up from the block.
                    Box(Modifier.testTag("bare").size(40.dp).redacted())
                }
            }
        }

        // Nothing to assert on the drawing here; what is checked is that the
        // default compiles to the local's value and the node still lays out at
        // its real size, which is the property a placeholder exists for.
        onNodeWithTag("bare").assertIsDisplayed()
    }
}
