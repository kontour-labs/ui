package io.kontour.ui.components.text

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kontour.ui.dragdrop.DropStatus
import io.kontour.ui.dragdrop.DropTargetState
import io.kontour.ui.dragdrop.DroppedData
import io.kontour.ui.dragdrop.ExternalDragInfo
import io.kontour.ui.dragdrop.LocalExternalDropProbe
import io.kontour.ui.files.FileTypes
import io.kontour.ui.files.PickedFile
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A file field takes files dragged in from outside the app as it takes a pick:
 * only files of its types, added to what it holds or in place of it, and its
 * frame lit while one is over it. Driven through the target's own terms, since
 * a test cannot make a platform drag.
 */
@OptIn(ExperimentalTestApi::class)
class FileFieldDropTest {

    private fun ComposeUiTest.field(
        multiple: Boolean,
        acceptsDrops: Boolean = true,
        held: List<PickedFile> = emptyList(),
    ): Pair<() -> List<PickedFile>, () -> DropTargetState<*>?> {
        var files by mutableStateOf(held)
        var target: DropTargetState<*>? = null
        setContent {
            KontourTheme(darkTheme = false) {
                CompositionLocalProvider(LocalExternalDropProbe provides { target = it }) {
                    Box(Modifier.background(Color.White).padding(16.dp)) {
                        FileField(
                            files = files,
                            onFilesChange = { files = it },
                            label = "Timetable",
                            types = FileTypes.of("application/pdf"),
                            multiple = multiple,
                            acceptsDrops = acceptsDrops,
                            colours = TextFieldDefaults.colours().copy(borderFocused = Color.Red),
                        )
                    }
                }
            }
        }
        waitForIdle()
        return { files } to { target }
    }

    private fun pdf(name: String) = PickedFile(name, 1_000, "application/pdf") { ByteArray(0) }

    @Test
    fun aDropAddsFilesOfItsTypesAsAPickDoes() = runComposeUiTest {
        val held = pdf("fares.pdf")
        val (files, target) = field(multiple = true, held = listOf(held))
        val terms = assertNotNullTerms(target())
        assertFalse(
            terms.accepts(ExternalDragInfo(hasFiles = true, hasText = false, mimeTypes = listOf("image/png"))),
            "a drag of only pictures is not offered to a field for PDFs",
        )
        runOnIdle {
            terms.onDrop(DroppedData(listOf(pdf("timetable.pdf"), PickedFile("map.png", 10, "image/png") { ByteArray(0) }, pdf("fares.pdf"))), null)
        }
        waitForIdle()
        assertEquals(listOf("fares.pdf", "timetable.pdf"), files().map { it.name }, "added, without the picture or a second fares.pdf")
    }

    @Test
    fun aDropOnASingleFileFieldReplacesWhatItHolds() = runComposeUiTest {
        val (files, target) = field(multiple = false, held = listOf(pdf("old.pdf")))
        val terms = assertNotNullTerms(target())
        runOnIdle { terms.onDrop(DroppedData(listOf(pdf("new.pdf"), pdf("other.pdf"))), null) }
        waitForIdle()
        assertEquals(listOf("new.pdf"), files().map { it.name })
    }

    @Test
    fun itTakesNoDropsWhenAskedNotTo() = runComposeUiTest {
        val (_, target) = field(multiple = false, acceptsDrops = false)
        assertNull(target(), "a field that takes no drops offers itself to none")
    }

    @Test
    fun itsFrameLightsWhileAFileIsOverIt() = runComposeUiTest {
        val (_, target) = field(multiple = false)
        assertFalse(edgeIsRed(), "at rest the frame is not lit")
        runOnIdle { target()!!.externalStatus = DropStatus.Over }
        assertTrue(edgeIsRed(), "a file over the field lights its frame as focus does")
        runOnIdle { target()!!.externalStatus = DropStatus.Idle }
        assertFalse(edgeIsRed(), "and it goes out when the drag leaves")
    }

    private fun assertNotNullTerms(target: DropTargetState<*>?) =
        assertNotNull(target?.external, "the field should offer itself to drags from outside the app")

    private val isFrame = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)
        .and(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Timetable")))

    private fun ComposeUiTest.edgeIsRed(): Boolean {
        waitForIdle()
        val bounds = onNode(isFrame, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val image = onRoot().captureToImage().toPixelMap()
        val y = bounds.center.y.toInt()
        return (0..3).any { dx ->
            val c = image[bounds.left.toInt() + dx, y]
            c.red > 0.9f && c.green < 0.2f && c.blue < 0.2f
        }
    }
}
