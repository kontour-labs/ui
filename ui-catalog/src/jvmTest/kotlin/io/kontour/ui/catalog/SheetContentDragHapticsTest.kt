package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.kontour.ui.foundation.Text
import io.kontour.ui.interaction.FeedbackDispatcher
import io.kontour.ui.interaction.FeedbackIntent
import io.kontour.ui.interaction.LocalFeedback
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.sheet.ModalBottomSheet
import io.kontour.ui.sheet.SheetDetent
import io.kontour.ui.sheet.SheetState
import io.kontour.ui.sheet.rememberSheetState
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A sheet moved to a new detent is felt arriving there, whatever part of it the
 * finger was on.
 *
 * Reported in November 2026: *"sometimes in sheets, the haptics don't fire at all
 * when you drag to a new detent"*. The sometimes was where the drag began. On
 * the handle it ticked; on a list inside the sheet, which hands its drag to the
 * sheet through nested scrolling, nothing at all, through the drag or as it
 * landed.
 */
class SheetContentDragHapticsTest {

    private fun dragUp(onContent: Boolean): Pair<List<FeedbackIntent>, SheetDetent?> {
        val felt = mutableListOf<FeedbackIntent>()
        var sheet: SheetState? = null
        var visible by mutableStateOf(true)
        Scene(width = 600, height = 1200) {
            CompositionLocalProvider(LocalFeedback provides FeedbackDispatcher { felt += it }) {
                OverlayHost(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().background(Color.White))
                    val state = rememberSheetState(
                        // A modal sheet opens at its second resting place, so a
                        // peek below the half it is to start from.
                        detents = listOf(SheetDetent.Hidden, SheetDetent.height("peek", 150.dp), SheetDetent.Half, SheetDetent.Full),
                        initialDetent = SheetDetent.Hidden,
                    )
                    sheet = state
                    ModalBottomSheet(visible = visible, onDismissRequest = { visible = false }, state = state) {
                        // A header that does not scroll, and a list under it that does.
                        Box(Modifier.fillMaxWidth().height(120.dp).background(Color.LightGray))
                        LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                            items(40) { Box(Modifier.fillMaxWidth().height(48.dp)) { Text("Row $it") } }
                        }
                    }
                }
            }
        }.use { scene ->
            scene.frames(80)
            assertEquals(SheetDetent.Half, sheet?.currentDetent, "the sheet did not open at half")
            // The sheet's top is at the middle; its header just under it, and its
            // list from about 300px further down.
            val from = if (onContent) Offset(300f, 1000f) else Offset(300f, 700f)
            scene.press(from)
            for (step in 1..24) {
                scene.move(Offset(from.x, from.y - step * 25f))
                scene.frame()
                Thread.sleep(16)
            }
            scene.release(Offset(from.x, from.y - 24 * 25f))
            scene.frames(60)
        }
        return felt.toList() to sheet?.currentDetent
    }

    @Test
    fun aSheetDraggedUpByItsHandleSnapsAsItArrives() {
        val (felt, landed) = dragUp(onContent = false)
        assertEquals(SheetDetent.Full, landed, "the drag did not take the sheet to full")
        assertEquals(1, felt.count { it == FeedbackIntent.Snap }, "dragged by its handle, the sheet felt $felt")
    }

    @Test
    fun aSheetDraggedUpByItsListSnapsAsItArrives() {
        val (felt, landed) = dragUp(onContent = true)
        assertEquals(SheetDetent.Full, landed, "the drag did not take the sheet to full")
        assertEquals(1, felt.count { it == FeedbackIntent.Snap }, "dragged by its list, the sheet felt $felt")
    }
}
