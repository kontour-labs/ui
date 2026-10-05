package io.kontour.ui.files

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A picker launched through [LocalFileChooser] — a test's, a preview's, an app
 * that picks from its own storage — rather than the platform's.
 */
@OptIn(ExperimentalTestApi::class)
class FilePickerTest {

    private fun file(name: String, mime: String? = null) = PickedFile(name, 1, mime) { ByteArray(1) }

    @Test
    fun theProvidedChooserIsAskedAndAnswers() = runComposeUiTest {
        val asked = mutableListOf<Pair<FileTypes, Boolean>>()
        val chooser = FileChooser { types, multiple, onResult ->
            asked += types to multiple
            onResult(listOf(file("timetable.pdf")))
        }
        val results = mutableListOf<List<String>>()
        lateinit var picker: FilePicker
        setContent {
            CompositionLocalProvider(LocalFileChooser provides chooser) {
                picker = rememberFilePicker { picked -> results += picked.map { it.name } }
            }
        }
        runOnIdle { picker.launch(FileTypes.of(".pdf"), multiple = true) }
        assertEquals(listOf(FileTypes.of(".pdf") to true), asked)
        assertEquals(listOf(listOf("timetable.pdf")), results)
    }

    /** Not every chooser can be told what to show, so what it hands back is checked again. */
    @Test
    fun whatTheChooserHandsBackIsCheckedAgainstTheTypes() = runComposeUiTest {
        val chooser = FileChooser { _, _, onResult ->
            onResult(listOf(file("map.png"), file("fares.csv"), file("notes", "image/jpeg")))
        }
        var result: List<String>? = null
        lateinit var picker: FilePicker
        setContent {
            CompositionLocalProvider(LocalFileChooser provides chooser) {
                picker = rememberFilePicker { picked -> result = picked.map { it.name } }
            }
        }
        runOnIdle { picker.launch(FileTypes.of("image/*"), multiple = true) }
        assertEquals(listOf("map.png", "notes"), result)
    }

    /** The answer goes to the callback of the latest composition, not the first. */
    @Test
    fun theLatestCallbackHearsTheAnswer() = runComposeUiTest {
        var answer: ((List<PickedFile>) -> Unit)? = null
        val chooser = FileChooser { _, _, onResult -> answer = onResult }
        var round by mutableIntStateOf(0)
        val heard = mutableListOf<Int>()
        lateinit var picker: FilePicker
        setContent {
            val current = round
            CompositionLocalProvider(LocalFileChooser provides chooser) {
                picker = rememberFilePicker { heard += current }
            }
        }
        runOnIdle { picker.launch() }
        // The screen recomposes while the picker is up.
        round = 1
        waitForIdle()
        runOnIdle { answer!!(listOf(file("a.txt"))) }
        assertEquals(listOf(1), heard)
    }
}
