package io.kontour.ui.components.text

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kontour.ui.input.InputModality
import io.kontour.ui.input.LocalInputModality
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A combobox's list, opened and closed from wherever it is opened and closed,
 * and narrowed by what is typed — from the keyboard as well as by hand.
 */
@OptIn(ExperimentalTestApi::class)
class ComboboxTest {

    private lateinit var state: SelectState

    private fun ComposeUiTest.show(
        modality: InputModality? = null,
        optionContent: (@Composable io.kontour.ui.foundation.RowContentScope.(String) -> Unit)? = null,
    ) {
        setContent {
            KontourTheme(reduceMotion = true) {
                val combobox = @Composable {
                    OverlayHost(Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxSize().padding(24.dp)) {
                            var stop by remember { mutableStateOf<String?>(null) }
                            state = rememberSelectState()
                            Combobox(
                                value = stop,
                                options = Stops,
                                onValueChange = { stop = it },
                                label = "From",
                                state = state,
                                modifier = Modifier.width(320.dp),
                                optionContent = optionContent,
                            )
                        }
                    }
                }
                if (modality == null) {
                    combobox()
                } else {
                    CompositionLocalProvider(LocalInputModality provides modality) { combobox() }
                }
            }
        }
        waitForIdle()
    }

    private fun ComposeUiTest.open() {
        runOnIdle { state.expand() }
        waitForIdle()
    }

    /** The text box, which is there only while the list is open. */
    private val ComposeUiTest.input: SemanticsNodeInteraction
        get() = onNode(hasSetTextAction(), useUnmergedTree = true)

    private fun ComposeUiTest.options() =
        onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))

    private fun ComposeUiTest.optionsShowing(): Int = options().fetchSemanticsNodes().size

    private fun ComposeUiTest.typed(): String =
        input.fetchSemanticsNode().config.getOrNull(SemanticsProperties.EditableText)?.text.orEmpty()

    /**
     * Closed from outside, through the state, it forgets what was typed.
     *
     * The query was cleared by the field's own closing and nothing else, and
     * `SelectState.collapse` is public and documented for closing a select from
     * outside — so a combobox closed that way opened again already narrowed to
     * what had been typed the last time.
     */
    @Test
    fun closedThroughItsStateItOpensAgainUnfiltered() = runComposeUiTest {
        show()
        open()
        input.performTextInput("Per")
        waitForIdle()
        assertEquals(2, optionsShowing(), "typing \"Per\" should narrow the list to the two Perths")

        runOnIdle { state.collapse() }
        waitForIdle()
        open()

        assertEquals("", typed(), "the query typed before the list was closed came back with it")
        assertEquals(Stops.size, optionsShowing(), "the list opened again narrowed by a query nobody can see")
    }

    /**
     * Opened from the keyboard, the field keeps the focus, and typing narrows.
     *
     * A menu opened under the keyboard moves the focus into its first row, a
     * frame after the combobox had given it to its field — so the field lost
     * it, and what was typed next went nowhere. The Down arrow is what moves
     * into the list instead.
     */
    @Test
    fun openedFromTheKeyboardTheFieldKeepsTheFocusAndDownEntersTheList() = runComposeUiTest {
        show(modality = InputModality.Keyboard)
        open()

        input.assertIsFocused()
        input.performTextInput("Esp")
        waitForIdle()
        assertEquals(1, optionsShowing(), "typing \"Esp\" should leave the Esplanade alone")

        input.performKeyInput { pressKey(Key.DirectionDown) }
        waitForIdle()
        options()[0].assertIsFocused()
    }

    /**
     * A row belongs to its option, not to its place in the filtered list.
     *
     * Unkeyed, the first row was the first row whatever it held, so narrowing
     * the list handed each row's composition — focus, press, anything the
     * option's content remembered — to another option. Here each option's
     * content remembers the option it was first composed for, and has to be
     * composed for no other.
     */
    @Test
    fun narrowingTheListDoesNotHandARowToAnotherOption() = runComposeUiTest {
        val handedOver = mutableListOf<String>()
        show(optionContent = { option ->
            val first = remember { option }
            SideEffect { if (first != option) handedOver += "$first → $option" }
            +option
        })
        open()
        input.performTextInput("Esp")
        waitForIdle()
        assertEquals(1, optionsShowing())
        assertTrue(handedOver.isEmpty(), "rows were reused for other options: $handedOver")
    }

    /**
     * Open, the field can be closed by a screen reader, from the field.
     *
     * A press on the open field goes to the text box, so the chevron is what
     * closes it — and it was a tap detector on an icon, with no semantics: a
     * reader could find nothing there. It is a button now, and the field offers
     * `collapse`.
     */
    @Test
    fun aScreenReaderCanCloseTheListFromTheField() = runComposeUiTest {
        show()
        open()
        assertTrue(optionsShowing() > 0)

        onNode(
            SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button) and
                hasContentDescription(CloseMenu) and hasClickAction(),
        ).performSemanticsAction(SemanticsActions.OnClick)
        waitForIdle()
        assertEquals(0, optionsShowing(), "the chevron's close action left the list open")

        open()
        assertTrue(optionsShowing() > 0)
        onNode(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.DropdownList))
            .performSemanticsAction(SemanticsActions.Collapse)
        waitForIdle()
        assertEquals(0, optionsShowing(), "the field's collapse action left the list open")
    }

    private companion object {
        val Stops = listOf("Perth Station", "Perth Underground", "Elizabeth Quay", "Esplanade")

        /** `Strings.closeMenu`'s default. */
        const val CloseMenu = "Close menu"
    }
}
