package io.kontour.ui.dragdrop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kontour.ui.files.FileTypes
import io.kontour.ui.files.PickedFile
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What a target does with a drag from outside the app, short of the drag itself,
 * which a test cannot make: which drags a file target offers itself to, what
 * reaches its `onDrop`, and where in a list one would land.
 */
@OptIn(ExperimentalTestApi::class)
class ExternalDropsTest {

    private fun file(name: String, mime: String? = null) = PickedFile(name, 1, mime) { ByteArray(1) }

    @Test
    fun aFileTargetIsOfferedFilesItCouldTake() {
        val images = ExternalDrops.files(FileTypes.of("image/png", "image/jpeg")) { _, _ -> }
        assertTrue(images.accepts(ExternalDragInfo(hasFiles = true, hasText = false, mimeTypes = listOf("image/png"))))
        assertFalse(images.accepts(ExternalDragInfo(hasFiles = true, hasText = false, mimeTypes = listOf("application/pdf"))))
        assertFalse(images.accepts(ExternalDragInfo(hasFiles = false, hasText = true)), "text is not a file")
        assertTrue(
            images.accepts(ExternalDragInfo(hasFiles = true, hasText = false)),
            "a desktop names no types before the drop, so it is given the benefit of the doubt",
        )
        val byExtension = ExternalDrops.files(FileTypes.of(".csv")) { _, _ -> }
        assertTrue(
            byExtension.accepts(ExternalDragInfo(hasFiles = true, hasText = false, mimeTypes = listOf("application/pdf"))),
            "an extension can only be checked against a name, which comes with the drop",
        )
    }

    @Test
    fun onlyMatchingFilesReachTheDropAndNeverNone() {
        val heard = mutableListOf<List<String>>()
        val images = ExternalDrops.files(FileTypes.of("image/png")) { files, _ -> heard += files.map { it.name } }
        images.onDrop(DroppedData(listOf(file("map.png"), file("fares.csv"))), 0)
        images.onDrop(DroppedData(listOf(file("fares.csv"))), 0)
        assertEquals(listOf(listOf("map.png")), heard)
    }

    @Test
    fun aListSaysWhereADragFromOutsideWouldLand() = runComposeUiTest {
        lateinit var state: DropListState<String>
        setContent {
            KontourTheme {
                OverlayHost {
                    val scroll = rememberLazyListState()
                    state = rememberDropListState(
                        listState = scroll,
                        type = Card,
                        items = listOf("Timetable", "Fares", "Signage"),
                        key = { it },
                        label = "To do",
                        itemLabel = { it },
                        onDrop = {},
                        external = ExternalDrops(onDrop = { _, _ -> }),
                    )
                    LazyColumn(state = scroll, modifier = Modifier.fillMaxSize().dropList(state)) {
                        dragItems(state) { _, _ -> Box(Modifier.height(48.dp)) }
                    }
                }
            }
        }
        val row = with(density) { 48.dp.toPx() }
        fun at(y: Float): Int? {
            runOnIdle { state.externalOver(Offset(10f, y)) }
            return state.externalIndex
        }
        assertEquals(0, at(row * 0.4f), "above the first row's middle: before it")
        assertEquals(1, at(row * 0.6f), "past it: after it")
        assertEquals(3, at(row * 10), "below them all: the end")
        runOnIdle { state.externalOver(null) }
        assertNull(state.externalIndex, "where the platform does not say, nothing is guessed")
        runOnIdle { state.externalStatus = DropStatus.Over }
        assertEquals(DropStatus.Over, state.status, "the list says it is under a drag from outside")
    }

    private companion object {
        val Card = DragType<String>("card")
    }
}
