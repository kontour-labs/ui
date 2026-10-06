package io.kontour.ui.components.text

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A select with an answer of the user's own: "Other" at the end of its menu,
 * and a box inside the field's outline to type it in.
 *
 * Asked for as *"a dropdown, but has an 'other' option or something. when this
 * option is selected, a text box expands within the dropdown's outline,
 * allowing the user to enter a custom value. maybe you could make this part of
 * the regular dropdown component/s itself"*.
 */
@OptIn(ExperimentalTestApi::class)
class SelectOtherTest {

    private var value by mutableStateOf<String?>(null)
    private var other by mutableStateOf<String?>(null)
    private var ticked by mutableStateOf(emptySet<String>())

    private fun ComposeUiTest.show(content: @Composable () -> Unit) = setContent {
        KontourTheme {
            OverlayHost {
                Box(Modifier.fillMaxSize().padding(24.dp)) { content() }
            }
        }
    }

    @Composable
    private fun ReasonSelect() = Select(
        value = value,
        options = Reasons,
        onValueChange = { value = it },
        label = "Reason",
        otherText = other,
        onOtherTextChange = { other = it },
        modifier = Modifier.width(320.dp).testTag("field"),
    )

    private fun ComposeUiTest.open() {
        onNode(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.DropdownList)).performClick()
        waitForIdle()
    }

    private fun ComposeUiTest.pick(option: String) {
        onAllNodesWithText(option).fetchSemanticsNodes().let { check(it.isNotEmpty()) { "no $option in the menu" } }
        onNode(SemanticsMatcher.expectValue(SemanticsProperties.Text, listOf(androidx.compose.ui.text.AnnotatedString(option))) and SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick), useUnmergedTree = false)
            .performClick()
        waitForIdle()
    }

    @Test
    fun pickingOtherOpensABoxInsideTheFieldAndGivesItTheKeyboard() = runComposeUiTest {
        show { ReasonSelect() }
        open()
        pick("Other")
        assertEquals("", other, "picking Other should make it the answer, with nothing typed yet")

        val box = onNodeWithContentDescription("Reason, Other")
        box.assertIsFocused()
        val boxBounds = box.fetchSemanticsNode().boundsInRoot
        val frame = onNode(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.DropdownList)).fetchSemanticsNode().boundsInRoot
        val field = onNodeWithTag("field").fetchSemanticsNode().boundsInRoot
        assertTrue(boxBounds.top >= frame.bottom - 0.5f, "the box should be under the value: box $boxBounds, value $frame")
        assertTrue(
            boxBounds.left > field.left && boxBounds.right < field.right && boxBounds.bottom < field.bottom,
            "the box should be inside the field's outline: box $boxBounds, field $field",
        )
        onNodeWithText("Other", useUnmergedTree = true).assertExists("the closed field should say Other")
    }

    @Test
    fun typingInTheBoxIsTheAnswer() = runComposeUiTest {
        show { ReasonSelect() }
        open()
        pick("Other")
        onNodeWithContentDescription("Reason, Other").performTextInput("Hovercraft")
        waitForIdle()
        assertEquals("Hovercraft", other)
    }

    @Test
    fun pickingAnOptionClosesTheBoxAndClearsOther() = runComposeUiTest {
        show { ReasonSelect() }
        open()
        pick("Other")
        onNodeWithContentDescription("Reason, Other").performTextInput("Hovercraft")
        open()
        pick("Delayed")
        assertEquals("Delayed", value)
        assertNull(other, "an option picked over Other should clear it")
        waitForIdle()
        onNodeWithContentDescription("Reason, Other").assertDoesNotExist()
    }

    @Test
    fun theBoxIsAControlOfItsOwnNotPartOfTheDropdown() = runComposeUiTest {
        show { ReasonSelect() }
        open()
        pick("Other")
        val dropdown = onNode(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.DropdownList)).fetchSemanticsNode()
        assertTrue(SemanticsActions.SetText !in dropdown.config, "the dropdown should not take text: the box should be its own node")
        val box = onNodeWithContentDescription("Reason, Other").fetchSemanticsNode()
        assertTrue(SemanticsActions.SetText in box.config, "the box should take text")
    }

    @Test
    fun withoutOnOtherTextChangeThereIsNoOther() = runComposeUiTest {
        show {
            Select(value = null, options = Reasons, onValueChange = {}, label = "Reason")
        }
        open()
        assertTrue(onAllNodesWithText("Other").fetchSemanticsNodes().isEmpty(), "a select not given onOtherTextChange offers no Other")
    }

    @Test
    fun aMultiSelectsOtherIsTickedAlongsideItsOptions() = runComposeUiTest {
        show {
            MultiSelect(
                value = ticked,
                options = Reasons,
                onValueChange = { ticked = it },
                label = "Reasons",
                otherText = other,
                onOtherTextChange = { other = it },
                modifier = Modifier.width(320.dp),
            )
        }
        open()
        pick("Delayed")
        pick("Other")
        assertEquals(setOf("Delayed"), ticked, "ticking Other should leave the options ticked")
        assertEquals("", other)
        onNodeWithContentDescription("Reasons, Other").assertIsFocused()
        onNodeWithContentDescription("Reasons, Other").performTextInput("Hovercraft")
        assertEquals("Hovercraft", other)
        open()
        pick("Other")
        assertNull(other, "ticked again, Other should be cleared")
    }

    private companion object {
        val Reasons = listOf("Delayed", "Cancelled", "Too full")
    }
}
