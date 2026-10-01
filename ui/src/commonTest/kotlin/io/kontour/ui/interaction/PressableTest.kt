package io.kontour.ui.interaction

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `Modifier.pressable`: a component of your own, pressed the way the library's
 * are.
 *
 * Asked for as *"a way to easily make custom components clickable using the
 * style of our ui library? allow for things like long press too"*. What is
 * checked here is what a screenshot cannot see: that the press and the long
 * press each do their own thing, that the long press is felt once and as the
 * library's, and that a screen reader hears a button with a named long press.
 */
@OptIn(ExperimentalTestApi::class)
class PressableTest {

    @Test
    fun aPressAndALongPressEachDoTheirOwnThing() = runComposeUiTest {
        var pressed = 0
        var held = 0
        val felt = mutableListOf<FeedbackIntent>()
        setContent {
            KontourTheme(reduceMotion = true) {
                CompositionLocalProvider(LocalFeedback provides FeedbackDispatcher { felt += it }) {
                    Box(
                        Modifier
                            .testTag("tile")
                            .pressable(
                                onClick = { pressed++ },
                                onLongClick = { held++ },
                                onLongClickLabel = "Pin",
                            )
                            .size(120.dp),
                    )
                }
            }
        }

        onNodeWithTag("tile")
            .assertHasClickAction()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assert(SemanticsMatcher("names its long press") { node ->
                node.config.getOrNull(SemanticsActions.OnLongClick)?.label == "Pin"
            })

        onNodeWithTag("tile").performClick()
        waitForIdle()
        assertEquals(1 to 0, pressed to held, "a press")
        assertEquals(emptyList(), felt, "a plain press is silent, as the library's buttons are")

        // After the tap, not instead of it: the tap turns the input to touch,
        // and a cursor that left the chain as it did cost every long press
        // after it.
        onNodeWithTag("tile").performTouchInput { longClick() }
        waitForIdle()
        assertEquals(1 to 1, pressed to held, "a long press ran the press as well, or not at all")
        assertEquals(listOf(FeedbackIntent.LongPress), felt, "a long press is felt once, as the library's")
    }

    @Test
    fun disabledItIsAnnouncedAsSuchAndDoesNothing() = runComposeUiTest {
        var pressed = 0
        setContent {
            KontourTheme(reduceMotion = true) {
                Box(Modifier.testTag("tile").pressable(onClick = { pressed++ }, enabled = false).size(120.dp))
            }
        }
        onNodeWithTag("tile").assertIsNotEnabled().performClick()
        waitForIdle()
        assertEquals(0, pressed)
    }
}
