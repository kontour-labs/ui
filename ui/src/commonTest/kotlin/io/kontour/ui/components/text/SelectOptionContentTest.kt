package io.kontour.ui.components.text

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kontour.ui.foundation.LocalContentColour
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A select's options drawn by the caller — `optionContent` in the menu and the
 * field, `valueContent` in the field alone — rather than as a label and an icon.
 *
 * Asked for as *"in select-like components, i'd like to introduce a slot api for
 * the content instead of just text and icon placeholders"*. What a picture
 * cannot show, and so what these hold the slot to: that each place calls the
 * slot it should with the option it should, that the words a screen reader hears
 * are still the label's however the option is drawn, that the slot inherits the
 * field's disabled grey, and that a field drawing something tall grows to it —
 * and, for a combobox, stays that tall as it opens.
 */
@OptIn(ExperimentalTestApi::class)
class SelectOptionContentTest {

    private fun ComposeUiTest.show(field: @Composable () -> Unit) {
        setContent {
            KontourTheme(reduceMotion = true) {
                OverlayHost(Modifier.fillMaxSize()) {
                    Box(Modifier.width(320.dp)) { field() }
                }
            }
        }
        waitForIdle()
    }

    /** The field: the merged node that is the dropdown list. */
    private fun ComposeUiTest.field(): SemanticsNode =
        onNode(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.DropdownList)).fetchSemanticsNode()

    private fun SemanticsNode.words(): List<String> =
        config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }

    @Test
    fun eachPlaceDrawsTheOptionItShould() = runComposeUiTest {
        val drawn = mutableSetOf<String>()
        show {
            Select(
                value = "Train",
                options = Options,
                onValueChange = {},
                state = rememberSelectState(initialExpanded = true),
                valueContent = { drawn += "field:$it" },
            ) { option ->
                drawn += "row:$option"
            }
        }
        assertTrue("field:Train" in drawn, "the field should draw its value with valueContent; drew $drawn")
        for (option in Options) {
            assertTrue("row:$option" in drawn, "the menu should draw $option with optionContent; drew $drawn")
        }
        assertTrue(
            drawn.none { it.startsWith("field:") && it != "field:Train" },
            "the field should draw the value and nothing else; drew $drawn",
        )
    }

    @Test
    fun withoutValueContentTheFieldDrawsTheValueAsARowDoes() = runComposeUiTest {
        val drawn = mutableSetOf<String>()
        show {
            Select(value = "Ferry", options = Options, onValueChange = {}) { option ->
                drawn += option
            }
        }
        assertEquals(setOf("Ferry"), drawn, "closed, the field should draw its value with optionContent")
    }

    /**
     * The words heard are the label's. A slot that draws more — a second line,
     * an avatar's initials — is drawn and not read, so the field and its rows
     * sound as a select of words does, and the same option sounds the same in
     * both places.
     */
    @Test
    fun theLabelIsWhatIsSaid() = runComposeUiTest {
        show {
            Select(
                value = "Train",
                options = Options,
                onValueChange = {},
                label = "Mode",
                state = rememberSelectState(initialExpanded = true),
            ) { option ->
                +"$option, drawn"
            }
        }
        assertEquals(listOf("Train"), field().words(), "the field should say its value's label, and only that")
        assertTrue(
            onAllNodesWithText("Train, drawn", substring = true).fetchSemanticsNodes().isEmpty(),
            "what the slot drew should not be read out as well as the label",
        )
        for (option in Options) {
            val rows = onAllNodesWithText(option).fetchSemanticsNodes()
                .filter { it.config.getOrNull(SemanticsProperties.Role) == Role.RadioButton }
            assertEquals(1, rows.size, "one option row should be named \"$option\"")
            assertEquals(
                option == "Train",
                rows.single().config.getOrNull(SemanticsProperties.Selected),
                "a drawn option is still one of a set, chosen or not",
            )
        }
    }

    @Test
    fun withNothingChosenThePlaceholderIsWords() = runComposeUiTest {
        val drawn = mutableSetOf<String>()
        show {
            Select<String>(
                value = null,
                options = Options,
                onValueChange = {},
                placeholder = "Choose a mode",
                valueContent = { drawn += "field:$it" },
            )
        }
        assertTrue(drawn.isEmpty(), "with no value there is nothing for valueContent to draw; drew $drawn")
        assertEquals(listOf("Choose a mode"), field().words())
    }

    /** Disabled, the slot is in the field's disabled grey, as the words it stands in for are. */
    @Test
    fun aDisabledFieldsSlotInheritsItsGrey() = runComposeUiTest {
        var seen = Color.Unspecified
        var expected = Color.Red
        show {
            expected = TextFieldDefaults.colours(TextFieldVariant.Outlined).contentDisabled
            Select(value = "Bus", options = Options, onValueChange = {}, enabled = false) {
                seen = LocalContentColour.current
                +it
            }
        }
        assertEquals(expected, seen, "a disabled select's slot should draw in contentDisabled")
    }

    /** A field drawing something taller than a line grows to fit it, rather than clipping it. */
    @Test
    fun theFieldGrowsToWhatItDraws() = runComposeUiTest {
        show {
            Column {
                Select(value = "Bus", options = Options, onValueChange = {}, modifier = Modifier.testTag("words"))
                Select(
                    value = "Bus",
                    options = Options,
                    onValueChange = {},
                    modifier = Modifier.testTag("drawn"),
                    valueContent = { Box(Modifier.size(Tall)) },
                )
            }
        }
        val words = onNodeWithTag("words").fetchSemanticsNode().size.height
        val drawn = onNodeWithTag("drawn").fetchSemanticsNode().size.height
        assertTrue(drawn > words, "a field drawing a ${Tall} box should stand taller than one of words: $drawn against $words px")
    }

    /**
     * Opened, a combobox's field is a line of text to type in. It keeps the
     * height it had closed, so a field that drew a face does not drop to one
     * line and pull the form below it up.
     */
    @Test
    fun aComboboxKeepsItsHeightAsItOpens() = runComposeUiTest {
        lateinit var state: SelectState
        show {
            state = rememberSelectState()
            Combobox(
                value = "Bus",
                options = Options,
                onValueChange = {},
                state = state,
                modifier = Modifier.testTag("combobox"),
                valueContent = { Box(Modifier.size(Tall)) },
            )
        }
        val closed = onNodeWithTag("combobox").fetchSemanticsNode().size.height
        runOnIdle { state.expand() }
        waitForIdle()
        val open = onNodeWithTag("combobox").fetchSemanticsNode().size.height
        assertEquals(closed, open, "the combobox should be as tall open as closed")
    }

    @Test
    fun aMultiSelectDrawsWhatIsTickedAndSaysItsSummary() = runComposeUiTest {
        var given: Set<String>? = null
        show {
            MultiSelect(
                value = setOf("Bus", "Ferry"),
                options = Options,
                onValueChange = {},
                valueContent = { ticked -> given = ticked },
            )
        }
        assertEquals(setOf("Bus", "Ferry"), given, "valueContent should be given the ticked set")
        assertEquals(1, field().words().size, "the field should say one summary")
        assertTrue(
            field().words().single().let { "Bus" in it && "Ferry" in it },
            "the field should still say the summary's words: ${field().words()}",
        )
    }

    private companion object {
        val Options = listOf("Bus", "Train", "Ferry")
        val Tall = 64.dp
    }
}
