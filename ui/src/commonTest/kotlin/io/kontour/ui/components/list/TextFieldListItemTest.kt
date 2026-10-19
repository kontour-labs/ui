package io.kontour.ui.components.list

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodes
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A list row you type into: typed into, focused from anywhere on the row,
 * named by its label or else its placeholder, and in a group with the rows
 * round it.
 */
@OptIn(ExperimentalTestApi::class)
class TextFieldListItemTest {

    private val name = TextFieldState("Perth")
    private val note = TextFieldState()

    private fun ComposeUiTest.rows(errorMessage: String? = null) {
        setContent {
            KontourTheme {
                Column(Modifier.width(360.dp)) {
                    ListGroup {
                        field(name, label = "Name", errorMessage = errorMessage)
                        field(note, placeholder = "Add a note")
                    }
                    TextFieldListItem(state = TextFieldState(), label = "Alone", modifier = Modifier.testTag("alone"))
                }
            }
        }
        waitForIdle()
    }

    private fun named(name: String) = SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf(name))

    @Test
    fun whatIsTypedGoesIntoTheState() = runComposeUiTest {
        rows()
        onAllNodes(hasSetTextAction() and named("Name"))[0].performClick()
        onAllNodes(hasSetTextAction() and named("Name"))[0].performTextInput(" Underground")
        waitForIdle()
        assertEquals("Perth Underground", name.text.toString())
    }

    @Test
    fun aPressOnTheRowsLabelPutsTheCaretInTheField() = runComposeUiTest {
        rows()
        // The row's start, where its label is, and not the field.
        onNodeWithTag("alone").performTouchInput { click(Offset(24f, height / 2f)) }
        waitForIdle()
        onAllNodes(hasSetTextAction() and named("Alone"))[0].assertIsFocused()
    }

    @Test
    fun aRowWithNoLabelIsNamedByItsPlaceholder() = runComposeUiTest {
        rows()
        onAllNodes(hasSetTextAction() and named("Add a note"))[0].performClick()
        onAllNodes(hasSetTextAction() and named("Add a note"))[0].performTextInput("Bring an umbrella")
        waitForIdle()
        assertEquals("Bring an umbrella", note.text.toString())
    }

    @Test
    fun anErrorIsTheFieldsToSay() = runComposeUiTest {
        rows(errorMessage = "Already used")
        onAllNodes(hasSetTextAction() and named("Name"))[0]
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Error, "Already used"))
    }
}
