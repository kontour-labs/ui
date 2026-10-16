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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kontour.ui.foundation.RowContentScope
import io.kontour.ui.input.InputModality
import io.kontour.ui.input.LocalInputModality
import io.kontour.ui.overlay.MenuDefaults
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.KontourTheme
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A select of a few hundred options composes the rows it shows, not all of them,
 * and is otherwise the menu it always was: as wide as its widest option, at that
 * width however far it is scrolled, walked by the keyboard, and said to a screen
 * reader as one list of so many.
 *
 * Every option composed and measured as the menu opened was a real cost on a
 * phone — a select of 250 countries composed 250 rows to show ten — and the
 * desktop runtime measured it at about a third of a millisecond a row.
 */
@OptIn(ExperimentalTestApi::class)
class LongSelectTest {

    private lateinit var state: SelectState
    private var picked: String? = null

    @Test
    fun openingASelectOfThreeHundredComposesTheRowsItShows() = runComposeUiTest {
        var composed = 0
        show(Countries, kind = Kind.Select, content = { option ->
            SideEffect { composed++ }
            +option
        })
        assertTrue(composed in 1..LeastShown, "opening a select of ${Countries.size} options composed $composed rows")
    }

    @Test
    fun openingAMultiSelectOfThreeHundredComposesTheRowsItShows() = runComposeUiTest {
        var composed = 0
        show(Countries, kind = Kind.MultiSelect, content = { option ->
            SideEffect { composed++ }
            +option
        })
        assertTrue(composed in 1..LeastShown, "opening a multi-select of ${Countries.size} options composed $composed rows")
    }

    @Test
    fun openingAComboboxOfThreeHundredComposesTheRowsItShows() = runComposeUiTest {
        var composed = 0
        show(Countries, kind = Kind.Combobox, content = { option ->
            SideEffect { composed++ }
            +option
        })
        assertTrue(composed in 1..LeastShown, "opening a combobox of ${Countries.size} options composed $composed rows")
    }

    /**
     * Under a field narrower than a menu goes, a long list is as wide as a short
     * list with the same widest option — which the long list never composes.
     */
    @Test
    fun aLongListIsAsWideAsItsWidestOptionUnshown() = runComposeUiTest {
        val long = Countries.toMutableList().also { it[250] = Widest }
        val short = Countries.take(10).toMutableList().also { it[5] = Widest }
        var shown by mutableStateOf(Shown(long, Kind.Select, null))
        setContent {
            KontourTheme(reduceMotion = true) {
                OverlayHost(Modifier.fillMaxSize()) {
                    Box(Modifier.padding(24.dp)) {
                        val now = shown
                        androidx.compose.runtime.key(now) { Field(now.options, now.kind, FieldWidth, now.value, null) }
                    }
                }
            }
        }
        fun widthOf(options: List<String>, kind: Kind, value: String?): Float {
            shown = Shown(options, kind, value)
            waitForIdle()
            runOnIdle { state.expand() }
            waitForIdle()
            return row("Country 2").fetchSemanticsNode().boundsInRoot.width
        }
        for (kind in Kind.entries) {
            for (value in listOf(null, "Country 2")) {
                val lazily = widthOf(long, kind, value)
                val column = widthOf(short, kind, value)
                assertTrue(
                    abs(lazily - column) <= 1f,
                    "$kind (value $value): a list of ${long.size} was $lazily wide and a list of ${short.size} " +
                        "with the same widest option $column",
                )
                assertTrue(lazily > FieldWidth, "$kind: the widest option should make the list wider than its field")
            }
        }
    }

    private data class Shown(val options: List<String>, val kind: Kind, val value: String?)

    @Test
    fun aLongListKeepsItsWidthAsItIsScrolled() = runComposeUiTest {
        val long = Countries.toMutableList().also { it[250] = Widest }
        show(long, kind = Kind.Select, width = FieldWidth)
        val before = row("Country 2").fetchSemanticsNode().boundsInRoot.width
        repeat(8) {
            row(firstShown()).performTouchInput { swipe(center, center + Offset(0f, -300f), 100) }
            waitForIdle()
        }
        val shown = rows().fetchSemanticsNodes().filter { it.boundsInRoot.height > 0f }
        assertTrue(shown.isNotEmpty())
        assertTrue(
            firstShownIndex() > 100,
            "eight drags should have scrolled well down the list; the first row shown is ${firstShown()}",
        )
        shown.forEach { node ->
            assertEquals(before, node.boundsInRoot.width, "a row lower down the list was a different width")
        }
    }

    /**
     * Options drawn by a slot say nothing about their width until they are
     * composed, so their long list opens as wide as a menu goes.
     */
    @Test
    fun aLongListOfDrawnOptionsOpensAsWideAsAMenuGoes() = runComposeUiTest {
        show(Countries, kind = Kind.Select, width = FieldWidth, content = { +it })
        val inset = 8f // the panel's own xxs either side
        assertEquals(MenuDefaults.MaxWidth.value - inset, row("Country 2").fetchSemanticsNode().boundsInRoot.width)
    }

    /** Tab walks on past the rows shown, and each row it reaches is brought into view. */
    @Test
    fun theKeyboardWalksALongListPastWhatIsShown() = runComposeUiTest {
        show(Countries, kind = Kind.Select, modality = InputModality.Keyboard)
        onNode(isFocused()).assertTextIs("Country 1")
        repeat(20) {
            onNode(isFocused()).performKeyInput { pressKey(Key.Tab) }
            waitForIdle()
        }
        val focused = onNode(isFocused()).fetchSemanticsNode()
        assertEquals("Country 21", focused.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text })
        assertTrue(focused.boundsInRoot.height > 0f, "the focused row was not brought into view")
    }

    /** A screen reader is told how many options there are, and which of them a row is. */
    @Test
    fun aLongListSaysHowManyOptionsThereAre() = runComposeUiTest {
        show(Countries, kind = Kind.Select)
        val lists = onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.CollectionInfo)).fetchSemanticsNodes()
        assertEquals(1, lists.size, "the open menu should be one list that says how many options it holds")
        assertEquals(Countries.size, lists.single().config[SemanticsProperties.CollectionInfo].rowCount)
        val third = row("Country 3").fetchSemanticsNode()
        assertEquals(2, third.config[SemanticsProperties.CollectionItemInfo].rowIndex)
    }

    @Test
    fun aRowFarDownALongListCanBePicked() = runComposeUiTest {
        show(Countries, kind = Kind.Select)
        repeat(3) {
            row(firstShown()).performTouchInput { swipe(center, center + Offset(0f, -300f), 100) }
            waitForIdle()
        }
        val target = firstShown()
        row(target).performTouchInput { click() }
        waitForIdle()
        assertEquals(target, picked)
        assertTrue(!state.expanded, "picking an option far down the list did not close it")
    }

    /** Typing narrows a long combobox to a short list, and back. */
    @Test
    fun aLongComboboxNarrowsAndWidensAgain() = runComposeUiTest {
        show(Countries, kind = Kind.Combobox)
        val input = onNode(hasSetTextAction(), useUnmergedTree = true)
        input.performTextInput("Country 25")
        waitForIdle()
        assertEquals(listOf("Country 25", "Country 250", "Country 251"), rowsShown().take(3))
        row("Country 250").performTouchInput { click() }
        waitForIdle()
        assertEquals("Country 250", picked)
    }

    private enum class Kind { Select, MultiSelect, Combobox }

    private fun ComposeUiTest.show(
        options: List<String>,
        kind: Kind,
        width: Float = 320f,
        value: String? = null,
        modality: InputModality? = null,
        content: (@Composable RowContentScope.(String) -> Unit)? = null,
    ) {
        setContent {
            KontourTheme(reduceMotion = true) {
                val field = @Composable {
                    OverlayHost(Modifier.fillMaxSize()) {
                        Box(Modifier.padding(24.dp)) { Field(options, kind, width, value, content) }
                    }
                }
                if (modality == null) field() else CompositionLocalProvider(LocalInputModality provides modality) { field() }
            }
        }
        waitForIdle()
        runOnIdle { state.expand() }
        waitForIdle()
    }

    @Composable
    private fun Field(
        options: List<String>,
        kind: Kind,
        width: Float,
        initial: String?,
        content: (@Composable RowContentScope.(String) -> Unit)?,
    ) {
        var value by remember { mutableStateOf(initial) }
        state = rememberSelectState()
        val pick = { option: String ->
            value = option
            picked = option
        }
        val modifier = Modifier.width(width.dp)
        when (kind) {
            Kind.Select -> Select(value, options, pick, label = "Country", state = state, modifier = modifier, optionContent = content)
            Kind.MultiSelect -> MultiSelect(
                setOfNotNull(value), options, { picked = it.lastOrNull() }, label = "Country", state = state,
                modifier = modifier, optionContent = content,
            )
            Kind.Combobox -> Combobox(value, options, pick, label = "Country", state = state, modifier = modifier, optionContent = content)
        }
    }

    private val optionRole = SemanticsMatcher("an option") {
        val role = it.config.getOrNull(SemanticsProperties.Role)
        role == Role.RadioButton || role == Role.Checkbox
    }

    private fun ComposeUiTest.rows() = onAllNodes(optionRole)

    private fun ComposeUiTest.row(label: String) = onNode(hasText(label, substring = false) and optionRole)

    private fun ComposeUiTest.rowsShown(): List<String> = rows().fetchSemanticsNodes()
        .filter { it.boundsInRoot.height > 0f }
        .sortedBy { it.boundsInRoot.top }
        .map { node -> node.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }.orEmpty() }

    /** The first row wholly in view. */
    private fun ComposeUiTest.firstShown(): String = rows().fetchSemanticsNodes()
        .filter { it.boundsInRoot.height >= (it.size.height - 1) && it.boundsInRoot.height > 0f }
        .minBy { it.boundsInRoot.top }
        .config[SemanticsProperties.Text].joinToString { it.text }

    private fun ComposeUiTest.firstShownIndex(): Int = firstShown().removePrefix("Country ").toInt()

    private fun SemanticsNodeInteraction.assertTextIs(text: String) {
        assertEquals(text, fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text })
    }

    private companion object {
        val Countries = List(300) { "Country ${it + 1}" }

        /** Far wider than the field and narrower than a menu goes. */
        const val Widest = "Saint Vincent and the Grenadines"

        const val FieldWidth = 200f

        /** A screenful of rows, and a few more for what a lazy list composes ahead. */
        const val LeastShown = 20
    }
}
