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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kontour.ui.foundation.RowContentScope
import io.kontour.ui.foundation.Text
import io.kontour.ui.input.InputModality
import io.kontour.ui.input.LocalInputModality
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.KontourTheme
import io.kontour.ui.theme.Theme
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A select's list from the keyboard, short or long: the arrows, Home and End,
 * a page at a time, and typing; and the list opening at the value it holds.
 *
 * Before, on the desktop runtime, only Tab moved between the options; the rest
 * did nothing, and a select opened at the top of its list whatever it held.
 */
@OptIn(ExperimentalTestApi::class)
class SelectKeyboardTest {

    private lateinit var state: SelectState
    private var picked: String? = null

    /** End goes to the last option of three hundred, which the list then shows. */
    @Test
    fun endGoesToTheLastOptionOfALongListAndShowsIt() = runComposeUiTest {
        show(Countries, modality = InputModality.Keyboard)
        focused().assertTextIs("Country 1")
        press(Key.MoveEnd)
        focused().assertTextIs("Country 300")
        assertWhollyShown("Country 300")
        press(Key.MoveHome)
        focused().assertTextIs("Country 1")
        assertWhollyShown("Country 1")
    }

    /** Down past the last option shown brings the next one into view. */
    @Test
    fun downWalksALongListPastWhatIsShown() = runComposeUiTest {
        show(Countries, modality = InputModality.Keyboard)
        repeat(20) { press(Key.DirectionDown) }
        focused().assertTextIs("Country 21")
        assertWhollyShown("Country 21")
        // Up from the first goes round to the last, as Down from the last goes to the first.
        press(Key.MoveHome)
        press(Key.DirectionUp)
        focused().assertTextIs("Country 300")
        assertWhollyShown("Country 300")
        press(Key.DirectionDown)
        focused().assertTextIs("Country 1")
    }

    /** Down skips an option that cannot be chosen, in a short list and a long one. */
    @Test
    fun downSkipsADisabledOption() = runComposeUiTest {
        for (options in listOf(Countries.take(10), Countries)) {
            show(options, modality = InputModality.Keyboard, enabled = { it != "Country 2" })
            focused().assertTextIs("Country 1")
            press(Key.DirectionDown)
            focused().assertTextIs("Country 3")
        }
    }

    /** Page Down goes about a list's height further on, and Page Up back. */
    @Test
    fun pageDownGoesAboutAListFurtherOn() = runComposeUiTest {
        show(Countries, modality = InputModality.Keyboard)
        val shown = rowsShown().size
        press(Key.PageDown)
        val after = focusedIndex()
        assertTrue(after in (shown - 3)..(shown + 1), "Page Down from the first of $shown rows shown went to ${after + 1}")
        assertWhollyShown("Country ${after + 1}")
        press(Key.PageDown)
        val further = focusedIndex()
        assertTrue(further - after in (shown - 3)..(shown + 1), "a second Page Down went from ${after + 1} to ${further + 1}")
        press(Key.PageUp)
        assertEquals(after, focusedIndex(), "Page Up should come back as far as Page Down went")
    }

    /** "ma" goes to the first option beginning "Ma", wherever it is in the list. */
    @Test
    fun typingMaGoesToTheFirstOptionBeginningWithMa() = runComposeUiTest {
        for (drawn in listOf(false, true)) {
            for (options in listOf(Named.take(12), Named)) {
                show(options, modality = InputModality.Keyboard, content = if (drawn) ({ +it }) else null)
                type(listOf(Key.M, Key.A))
                focused().assertTextIs("Madagascar")
                assertWhollyShown("Madagascar")
                // A pause, and what is typed next is a new word.
                pause()
                press(Key.L)
                focused().assertTextIs("Luxembourg")
                pause()
                press(Key.M)
                focused().assertTextIs("Madagascar")
                // The same letter again goes on to the next beginning with it.
                press(Key.M)
                focused().assertTextIs("Malawi")
            }
        }
    }

    /**
     * A select of three hundred holding its two hundred and first opens with
     * that option in view, near the middle — and never shows the top of the
     * list on the way: the first option is not so much as composed.
     */
    @Test
    fun aLongSelectOpensAtItsValue() = runComposeUiTest {
        val composed = mutableSetOf<String>()
        var firstSeen: Rect? = null
        show(Countries, value = "Country 201", content = { option ->
            SideEffect { composed += option }
            Text(option, Modifier.onGloballyPositioned { if (option == "Country 201" && firstSeen == null) firstSeen = it.boundsInWindow() })
        })
        assertFalse("Country 1" in composed, "the list was laid out at its top before it moved to its value")
        assertTrue((firstSeen?.height ?: 0f) > 0f, "the value was out of view when the list first showed: $firstSeen")
        assertNearTheMiddle("Country 201")
    }

    /** A short select scrolls to its value too, before it is first drawn. */
    @Test
    fun aShortSelectOpensAtItsValue() = runComposeUiTest {
        var firstSeen: Rect? = null
        val options = Countries.take(25)
        show(options, value = "Country 23", content = { option ->
            Text(option, Modifier.onGloballyPositioned { if (option == "Country 23" && firstSeen == null) firstSeen = it.boundsInWindow() })
        })
        assertTrue((firstSeen?.height ?: 0f) > 0f, "the value was out of view when the list first showed: $firstSeen")
        assertWhollyShown("Country 23")
    }

    /** Opened from the keyboard, the focus goes to the value rather than the first option. */
    @Test
    fun openedFromTheKeyboardTheFocusGoesToTheValue() = runComposeUiTest {
        for (options in listOf(Countries.take(25), Countries)) {
            show(options, value = "Country 20", modality = InputModality.Keyboard)
            focused().assertTextIs("Country 20")
            assertWhollyShown("Country 20")
            press(Key.DirectionDown)
            focused().assertTextIs("Country 21")
        }
        show(Countries, kind = Kind.MultiSelect, value = "Country 201", modality = InputModality.Keyboard)
        focused().assertTextIs("Country 201")
    }

    /**
     * Opened with a click, nothing in the list has the focus and no ring shows;
     * an arrow then takes the list into the keyboard's hands, at the value.
     */
    @Test
    fun openedByAPointerNoRingShowsUntilAKeyIsPressed() = runComposeUiTest {
        var ring = Color.Unspecified
        show(Countries, value = "Country 201", open = false, onTheme = { ring = Theme.colours.focusRing })
        onNode(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.DropdownList)).performMouseInput { click() }
        waitForIdle()
        assertTrue(state.expanded, "a click on the field should open it")
        assertWhollyShown("Country 201")
        assertTrue(onAllNodes(optionRole and isFocused()).fetchSemanticsNodes().isEmpty(), "a click put the focus on an option")
        assertFalse(ringShows(row("Country 201"), ring), "opened with a click, the value showed a focus ring")

        onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        waitForIdle()
        focused().assertTextIs("Country 201")
        assertTrue(ringShows(row("Country 201"), ring), "Down should show where the keyboard is")
    }

    /**
     * Typing in a list opened with a click goes to the option it begins, and
     * shows a ring there: a letter is not a key the input tracker counts as
     * moving about, so the menu says so itself.
     */
    @Test
    fun typingInAListOpenedByAPointerShowsWhereItWent() = runComposeUiTest {
        var ring = Color.Unspecified
        show(Named, open = false, onTheme = { ring = Theme.colours.focusRing })
        onNode(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.DropdownList)).performMouseInput { click() }
        waitForIdle()
        type(listOf(Key.M, Key.A))
        focused().assertTextIs("Madagascar")
        assertTrue(ringShows(row("Madagascar"), ring), "the option typing went to showed no ring")
    }

    /** Escape closes the list and gives the focus back to the field. */
    @Test
    fun escapeClosesAndTheFieldHasTheFocusAgain() = runComposeUiTest {
        show(Countries.take(10), value = "Country 3", modality = InputModality.Keyboard)
        focused().assertTextIs("Country 3")
        press(Key.Escape)
        assertFalse(state.expanded, "Escape should close the list")
        onNode(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.DropdownList)).assertIsFocused()
        // And an option picked with Enter does the same.
        runOnIdle { state.expand() }
        waitForIdle()
        press(Key.DirectionDown)
        press(Key.Enter)
        assertEquals("Country 4", picked)
        onNode(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.DropdownList)).assertIsFocused()
    }

    /**
     * In a combobox, typing in the list goes to the field, which narrows it,
     * rather than jumping to an option: the field is what typing is for.
     */
    @Test
    fun inAComboboxTypingInTheListGoesToTheField() = runComposeUiTest {
        show(Named, kind = Kind.Combobox, modality = InputModality.Keyboard)
        val input = onNode(hasSetTextAction(), useUnmergedTree = true)
        input.assertIsFocused()
        input.performKeyInput { pressKey(Key.DirectionDown) }
        waitForIdle()
        focused().assertTextIs("Afghanistan")
        press(Key.M)
        input.assertIsFocused()
        assertEquals("m", input.fetchSemanticsNode().config.getOrNull(SemanticsProperties.EditableText)?.text)
        assertTrue(rowsShown().all { it.contains("m", ignoreCase = true) }, "the list was not narrowed: ${rowsShown()}")
    }

    private enum class Kind { Select, MultiSelect, Combobox }

    private var shown by mutableStateOf<Shown?>(null)

    private class Shown(
        val options: List<String>,
        val kind: Kind,
        val value: String?,
        val enabled: (String) -> Boolean,
        val content: (@Composable RowContentScope.(String) -> Unit)?,
    )

    private var modalityOverride by mutableStateOf<InputModality?>(null)

    private fun ComposeUiTest.show(
        options: List<String>,
        kind: Kind = Kind.Select,
        value: String? = null,
        modality: InputModality? = null,
        enabled: (String) -> Boolean = { true },
        open: Boolean = true,
        onTheme: @Composable () -> Unit = {},
        content: (@Composable RowContentScope.(String) -> Unit)? = null,
    ) {
        val first = shown == null
        shown = Shown(options, kind, value, enabled, content)
        modalityOverride = modality
        if (first) {
            setContent {
                KontourTheme(reduceMotion = true) {
                    onTheme()
                    val field = @Composable {
                        OverlayHost(Modifier.fillMaxSize()) {
                            Box(Modifier.padding(24.dp)) {
                                val now = shown!!
                                androidx.compose.runtime.key(now) { Field(now) }
                            }
                        }
                    }
                    val forced = modalityOverride
                    if (forced == null) field() else CompositionLocalProvider(LocalInputModality provides forced) { field() }
                }
            }
        }
        waitForIdle()
        if (open) {
            runOnIdle { state.expand() }
            waitForIdle()
        }
    }

    @Composable
    private fun Field(shown: Shown) {
        var value by remember { mutableStateOf(shown.value) }
        state = rememberSelectState()
        val pick = { option: String ->
            value = option
            picked = option
        }
        val modifier = Modifier.width(320.dp)
        when (shown.kind) {
            Kind.Select -> Select(
                value, shown.options, pick, label = "Country", state = state, modifier = modifier,
                optionEnabled = shown.enabled, optionContent = shown.content,
            )
            Kind.MultiSelect -> MultiSelect(
                setOfNotNull(value), shown.options, { picked = it.lastOrNull() }, label = "Country", state = state,
                modifier = modifier, optionEnabled = shown.enabled, optionContent = shown.content,
            )
            Kind.Combobox -> Combobox(
                value, shown.options, pick, label = "Country", state = state, modifier = modifier,
                optionContent = shown.content,
            )
        }
    }

    private val optionRole = SemanticsMatcher("an option") {
        val role = it.config.getOrNull(SemanticsProperties.Role)
        role == Role.RadioButton || role == Role.Checkbox
    }

    private fun ComposeUiTest.row(label: String) = onNode(hasText(label, substring = false) and optionRole)

    private fun ComposeUiTest.focused() = onNode(isFocused() and optionRole)

    private fun ComposeUiTest.focusedIndex(): Int =
        focused().fetchSemanticsNode().config[SemanticsProperties.Text].joinToString { it.text }.removePrefix("Country ").toInt() - 1

    private fun ComposeUiTest.press(key: Key) = type(listOf(key))

    private fun ComposeUiTest.type(keys: List<Key>) {
        onRoot().performKeyInput { keys.forEach { pressKey(it) } }
        waitForIdle()
    }

    /** Longer than type-ahead waits for the next letter of a word. */
    private fun ComposeUiTest.pause() {
        mainClock.advanceTimeBy(2_000)
        waitForIdle()
    }

    private fun ComposeUiTest.rowsShown(): List<String> = onAllNodes(optionRole).fetchSemanticsNodes()
        .filter { it.boundsInRoot.height > 0f }
        .sortedBy { it.boundsInRoot.top }
        .map { node -> node.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }.orEmpty() }

    /** The rows' own area: from the top of the first shown to the foot of the last. */
    private fun ComposeUiTest.listArea(): Rect {
        val nodes = onAllNodes(optionRole).fetchSemanticsNodes().filter { it.boundsInRoot.height > 0f }
        return Rect(
            nodes.minOf { it.boundsInRoot.left },
            nodes.minOf { it.boundsInRoot.top },
            nodes.maxOf { it.boundsInRoot.right },
            nodes.maxOf { it.boundsInRoot.bottom },
        )
    }

    private fun ComposeUiTest.assertWhollyShown(label: String) {
        val node = row(label).fetchSemanticsNode()
        assertTrue(
            node.boundsInRoot.height >= node.size.height - 1,
            "$label was not wholly in view: ${node.boundsInRoot.height} of ${node.size.height} shown",
        )
    }

    private fun ComposeUiTest.assertNearTheMiddle(label: String) {
        val node = row(label).fetchSemanticsNode()
        assertWhollyShown(label)
        val area = listArea()
        val off = abs(node.boundsInRoot.center.y - area.center.y)
        assertTrue(off <= node.size.height * 1.5f, "$label was ${off}px from the middle of the list")
    }

    /** Whether the focus ring is drawn just inside [row]'s leading edge. */
    private fun ComposeUiTest.ringShows(row: SemanticsNodeInteraction, ring: Color): Boolean {
        val bounds = row.fetchSemanticsNode().boundsInRoot
        val pixels = onRoot().captureToImage().toPixelMap()
        val x = bounds.left.toInt() + 1
        val y = bounds.center.y.toInt()
        val seen = pixels[x, y]
        return abs(seen.red - ring.red) < 0.1f && abs(seen.green - ring.green) < 0.1f && abs(seen.blue - ring.blue) < 0.1f
    }

    private fun SemanticsNodeInteraction.assertTextIs(text: String) {
        assertEquals(text, fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text })
    }

    private companion object {
        val Countries = List(300) { "Country ${it + 1}" }

        /** Real names, in order, padded out to a long list. */
        val Named = listOf(
            "Afghanistan", "Belgium", "Chile", "Denmark", "Estonia", "Finland", "Ghana", "Hungary", "Iceland",
            "Luxembourg", "Madagascar", "Malawi",
        ).let { names -> names + List(100) { "Malaysia ${it + 1}" } + List(100) { "Mexico ${it + 1}" } + listOf("Zambia") }
    }
}
