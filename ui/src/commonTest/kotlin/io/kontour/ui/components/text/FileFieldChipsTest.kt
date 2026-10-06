package io.kontour.ui.components.text

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kontour.ui.files.PickedFile
import io.kontour.ui.theme.KontourTheme
import io.kontour.ui.theme.Theme
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A file field holding files: each a chip with its size, one file or many, in
 * a row that scrolls sideways so the field is a field's height; and a single
 * file's clear button where the attach glyph was.
 *
 * Reported as *"if there's a file in there already, the 'cross' doesn't show
 * up in the same spot as the 'attach' icon"*, and *"several files looks too
 * different from a single file. maybe we should make all of them come up as
 * chips like that, and also show the file size inside the chip. also, when
 * there's chips in the file field, the field itself gets taller. maybe also
 * give it horizontal scrolling with the fading edge modifier"*.
 */
@OptIn(ExperimentalTestApi::class)
class FileFieldChipsTest {

    private fun file(name: String, size: Long = 2_400_000) = PickedFile(name, size, null) { ByteArray(0) }

    private fun frame(label: String) = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)
        .and(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf(label)))

    private fun ComposeUiTest.bounds(label: String) = onNode(frame(label), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    @Test
    fun aSingleFilesClearButtonIsWhereTheAttachGlyphWas() = runComposeUiTest {
        var padding = 0.dp
        var glyph = 0.dp
        setContent {
            KontourTheme {
                padding = TextFieldDefaults.metrics().iconPadding
                glyph = Theme.sizing.iconMedium
                FileField(files = listOf(file("timetable.pdf")), onFilesChange = {}, label = "Timetable", modifier = Modifier.width(320.dp))
            }
        }
        val field = bounds("Timetable")
        val clear = onNodeWithContentDescription("Remove timetable.pdf").fetchSemanticsNode().boundsInRoot
        // The attach glyph's centre: the trailing padding and half the glyph in from the end.
        val attach = field.right - with(density) { (padding + glyph / 2).toPx() }
        assertTrue(abs(clear.center.x - attach) <= 1f, "the clear button should be centred where the attach glyph is, at $attach; it is at ${clear.center.x}")
        assertTrue(abs(clear.center.y - field.center.y) <= 1f, "and level with it: ${clear.center.y} against ${field.center.y}")
    }

    @Test
    fun emptyOneFileOrManyItIsAFieldsHeight() = runComposeUiTest {
        setContent {
            KontourTheme {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FileField(files = emptyList(), onFilesChange = {}, label = "Empty", modifier = Modifier.width(240.dp))
                    FileField(files = listOf(file("timetable.pdf")), onFilesChange = {}, label = "One", modifier = Modifier.width(240.dp))
                    FileField(
                        files = List(6) { file("stop-${it + 1}.pdf") },
                        onFilesChange = {},
                        label = "Many",
                        multiple = true,
                        modifier = Modifier.width(240.dp),
                    )
                }
            }
        }
        val empty = bounds("Empty").height
        for (label in listOf("One", "Many")) {
            val height = bounds(label).height
            assertTrue(abs(height - empty) <= 0.5f, "a field holding files should be an empty field's ${empty}px tall; \"$label\" is ${height}px")
        }
    }

    @Test
    fun everyFileIsAChipWithItsSize() = runComposeUiTest {
        setContent {
            KontourTheme {
                FileField(
                    files = listOf(file("a.pdf", 2_400_000), file("b.pdf", 512)),
                    onFilesChange = {},
                    label = "Timetable",
                    multiple = true,
                    modifier = Modifier.width(360.dp),
                )
            }
        }
        val said = onNode(frame("Timetable")).fetchSemanticsNode().config[SemanticsProperties.Text].joinToString(" | ")
        for (part in listOf("a.pdf", "2.4 MB", "b.pdf", "512 bytes")) {
            assertTrue(part in said, "the field should show \"$part\" in a chip; it says $said")
        }
    }

    @Test
    fun manyFilesScrollSideways() = runComposeUiTest {
        setContent {
            KontourTheme {
                FileField(
                    files = List(6) { file("stop-${it + 1}.pdf") },
                    onFilesChange = {},
                    label = "Timetable",
                    multiple = true,
                    modifier = Modifier.width(240.dp),
                )
            }
        }
        val scrolls = onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.HorizontalScrollAxisRange), useUnmergedTree = true)
            .fetchSemanticsNodes()
        assertTrue(scrolls.isNotEmpty(), "six files in a narrow field should be a row that scrolls sideways, not rows that wrap")
    }
}
