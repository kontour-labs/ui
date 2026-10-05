package io.kontour.ui.components.text

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import io.kontour.ui.files.FileChooser
import io.kontour.ui.files.FileTypes
import io.kontour.ui.files.LocalFileChooser
import io.kontour.ui.files.PickedFile
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Reported: *"There's no field for choosing a file; I combined a 'Choose file…'
 * button with a text area."* A field that is one control: pressed, it asks the
 * picker; filled, it says what it holds; and a screen reader hears one button
 * with a name and a state.
 */
@OptIn(ExperimentalTestApi::class)
class FileFieldTest {

    private fun file(name: String, size: Long = 2_400_000) = PickedFile(name, size, null) { ByteArray(0) }

    private val isFrame = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)
        .and(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Timetable")))

    @Test
    fun emptyItIsAButtonThatSaysNothingIsChosen() = runComposeUiTest {
        setContent { KontourTheme { FileField(files = emptyList(), onFilesChange = {}, label = "Timetable") } }
        val frame = onNode(isFrame, useUnmergedTree = true).fetchSemanticsNode().config
        assertEquals("No file chosen", frame.getOrNull(SemanticsProperties.StateDescription))
        // The placeholder is drawn, and not said twice.
        assertEquals(null, frame.getOrNull(SemanticsProperties.Text)?.joinToString())
    }

    @Test
    fun pressedItAsksThePickerAndHoldsWhatComesBack() = runComposeUiTest {
        val asked = mutableListOf<Pair<FileTypes, Boolean>>()
        val chooser = FileChooser { types, multiple, onResult ->
            asked += types to multiple
            onResult(listOf(file("timetable.pdf")))
        }
        var files by mutableStateOf(emptyList<PickedFile>())
        setContent {
            KontourTheme {
                CompositionLocalProvider(LocalFileChooser provides chooser) {
                    FileField(files = files, onFilesChange = { files = it }, label = "Timetable", types = FileTypes.of(".pdf"))
                }
            }
        }
        onNode(isFrame).performClick()
        waitForIdle()
        assertEquals(listOf(FileTypes.of(".pdf") to false), asked)
        assertEquals(listOf("timetable.pdf"), files.map { it.name })
        onNode(isFrame).assertTextContains("timetable.pdf").assertTextContains("2.4 MB")
    }

    @Test
    fun oneFileClearsWithItsOwnNamedButton() = runComposeUiTest {
        var files by mutableStateOf(listOf(file("timetable.pdf")))
        setContent { KontourTheme { FileField(files = files, onFilesChange = { files = it }, label = "Timetable") } }
        onNodeWithContentDescription("Remove timetable.pdf").performClick()
        waitForIdle()
        assertEquals(emptyList(), files)
    }

    /** Several add to what is there, leaving out what is there already, and go one at a time. */
    @Test
    fun severalAddUpAndGoOneAtATime() = runComposeUiTest {
        var answer = listOf(file("a.pdf"), file("b.pdf"))
        val chooser = FileChooser { _, _, onResult -> onResult(answer) }
        var files by mutableStateOf(emptyList<PickedFile>())
        setContent {
            KontourTheme {
                CompositionLocalProvider(LocalFileChooser provides chooser) {
                    FileField(files = files, onFilesChange = { files = it }, label = "Timetable", multiple = true)
                }
            }
        }
        onNode(isFrame).performClick()
        waitForIdle()
        answer = listOf(file("b.pdf"), file("c.pdf"))
        onNode(isFrame).performClick()
        waitForIdle()
        assertEquals(listOf("a.pdf", "b.pdf", "c.pdf"), files.map { it.name })
        onNodeWithContentDescription("Remove b.pdf").performClick()
        waitForIdle()
        assertEquals(listOf("a.pdf", "c.pdf"), files.map { it.name })
    }

    /** Called off, the picker hands back nothing, and the field keeps what it had. */
    @Test
    fun aCancelledPickChangesNothing() = runComposeUiTest {
        val chooser = FileChooser { _, _, onResult -> onResult(emptyList()) }
        var changes = 0
        setContent {
            KontourTheme {
                CompositionLocalProvider(LocalFileChooser provides chooser) {
                    FileField(files = listOf(file("timetable.pdf")), onFilesChange = { changes++ }, label = "Timetable")
                }
            }
        }
        onNode(isFrame).performClick()
        waitForIdle()
        assertEquals(0, changes)
    }
}
