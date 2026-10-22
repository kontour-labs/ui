package io.kontour.ui.components.list

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.text.TextFieldDefaults
import io.kontour.ui.components.text.TextFieldVariant
import io.kontour.ui.theme.KontourTheme
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
        // The value is at the row's end, so a press on the middle of the field
        // lands before it: the caret goes after it, as a reader typing on would.
        onAllNodes(hasSetTextAction() and named("Name"))[0].performTextInputSelection(TextRange(name.text.length))
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

    /**
     * A read-only row says it has the focus, as a read-only field does: its
     * value can be selected and copied, so the focus is somewhere a reader
     * needs to see.
     *
     * Found in the 0.5.0 review, 2026-10-21: the row drew its focused edge
     * only while it could be typed into, and a read-only row took the focus
     * and showed nothing at all. Read from the row's leading edge, half way
     * down.
     */
    @Test
    fun aFocusedReadOnlyRowShowsItsFocusedEdge() = runComposeUiTest {
        var focusedEdge = Color.Unspecified
        setContent {
            KontourTheme {
                focusedEdge = TextFieldDefaults.colours(TextFieldVariant.Outlined).borderFocused
                Column(Modifier.width(360.dp)) {
                    TextFieldListItem(
                        state = TextFieldState("PT-4471"),
                        label = "Booking",
                        readOnly = true,
                        modifier = Modifier.testTag("booking"),
                    )
                }
            }
        }
        waitForIdle()
        onAllNodes(named("Booking") and SemanticsMatcher.keyIsDefined(SemanticsActions.RequestFocus))[0].requestFocus()
        waitForIdle()
        val bounds = onNodeWithTag("booking").fetchSemanticsNode().boundsInRoot
        val image = onRoot().captureToImage().toPixelMap()
        val y = bounds.center.y.toInt()
        val edge = image[bounds.left.toInt(), y]
        assertTrue(
            abs(edge.red - focusedEdge.red) < 0.1f && abs(edge.green - focusedEdge.green) < 0.1f &&
                abs(edge.blue - focusedEdge.blue) < 0.1f,
            "a focused read-only row's edge is $edge, not the focused edge $focusedEdge",
        )
    }

    @Test
    fun anErrorIsTheFieldsToSay() = runComposeUiTest {
        rows(errorMessage = "Already used")
        onAllNodes(hasSetTextAction() and named("Name"))[0]
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Error, "Already used"))
    }
}
