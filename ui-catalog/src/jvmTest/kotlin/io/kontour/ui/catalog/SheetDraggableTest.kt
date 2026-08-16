package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.kontour.ui.overlay.Dialog
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.sheet.BottomSheet
import io.kontour.ui.sheet.ModalBottomSheet
import io.kontour.ui.sheet.SheetDetent
import io.kontour.ui.sheet.SheetState
import io.kontour.ui.sheet.rememberSheetState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What a sheet does when it is told not to be dragged, and what a modal does
 * when it is told not to be dismissed.
 *
 * `draggable = false` has to take the **handle** with the gesture. A handle is
 * the only part of a sheet that says "pull me", and one that does nothing is a
 * lie about what the sheet will do. It also has to take the nested-scroll
 * connection: that exists to hand a list's overscroll to the sheet, so leaving it
 * behind would mean a flick at the top of the content still closed a sheet that
 * cannot be dragged.
 *
 * The `dismissable` half of this was already built, under the name
 * `dismissOnOutside`. It had no test, which is the part worth fixing.
 */
class SheetDraggableTest {

    @Test
    fun aDraggableSheetAnswersADrag() {
        val settled = detentAfterDraggingDown(draggable = true)
        assertEquals(
            SheetDetent.Hidden,
            settled,
            "dragging a draggable sheet to the bottom left it at $settled",
        )
    }

    @Test
    fun anUndraggableSheetDoesNot() {
        val settled = detentAfterDraggingDown(draggable = false)
        assertEquals(
            SheetDetent.Expanded,
            settled,
            "dragging a sheet with `draggable = false` moved it to $settled",
        )
    }

    @Test
    fun anUndraggableSheetHasNoHandle() {
        val withHandle = inkOf(draggable = true)
        val without = inkOf(draggable = false)

        assertTrue(
            without < withHandle,
            "a sheet drew $withHandle pixels with `draggable = true` and $without " +
                "without it — the drag handle is still there, and a handle that " +
                "does nothing is a lie",
        )
    }

    @Test
    fun aModalSheetIgnoresAnOutsideTapWhenAskedTo() {
        assertTrue(closedByScrimTap(dismissOnOutside = true), "an outside tap did not close the sheet")
        assertTrue(
            !closedByScrimTap(dismissOnOutside = false),
            "an outside tap closed a sheet with `dismissOnOutside = false`",
        )
    }

    @Test
    fun aDialogIgnoresAnOutsideTapWhenAskedTo() {
        assertTrue(dialogClosedByScrimTap(dismissOnOutside = true), "an outside tap did not close the dialog")
        assertTrue(
            !dialogClosedByScrimTap(dismissOnOutside = false),
            "an outside tap closed a dialog with `dismissOnOutside = false`",
        )
    }

    /** Opens a sheet, drags it to the floor, and reports where it settled. */
    private fun detentAfterDraggingDown(draggable: Boolean): SheetDetent {
        var state: SheetState? = null

        Scene(width = 600, height = 800) {
            val sheet = rememberSheetState(
                detents = listOf(SheetDetent.Hidden, SheetDetent.Expanded),
                initialDetent = SheetDetent.Expanded,
            )
            state = sheet
            Box(Modifier.fillMaxSize().background(Color.White)) {
                BottomSheet(state = sheet, draggable = draggable) {
                    Box(Modifier.fillMaxWidth().height(200.dp).background(Color.LightGray))
                }
            }
        }.use { scene ->
            // Found in the frame rather than guessed. The first version pressed
            // at a fixed y that turned out to be below the handle, and a sheet
            // that *is* draggable failed to move — a passing test would have
            // been the wrong answer for the wrong reason.
            val image = scene.frames(8)
            val top = image.crownRow() + 40
            scene.drag(from = Offset(300f, top.toFloat()), to = Offset(300f, 790f), steps = 24)
            scene.frames(40)
        }

        return requireNotNull(state).currentDetent
    }

    /** How much a sheet draws, which is one drag handle more with the handle. */
    private fun inkOf(draggable: Boolean): Int {
        var ink = 0
        Scene(width = 600, height = 800) {
            val sheet = rememberSheetState(
                detents = listOf(SheetDetent.Hidden, SheetDetent.Expanded),
                initialDetent = SheetDetent.Expanded,
            )
            Box(Modifier.fillMaxSize().background(Color.White)) {
                BottomSheet(state = sheet, draggable = draggable) {
                    Box(Modifier.fillMaxWidth().height(200.dp))
                }
            }
        }.use { scene ->
            val image = scene.frames(10)
            var count = 0
            for (y in 0 until image.height) {
                for (x in 0 until image.width) {
                    val rgb = image.getRGB(x, y)
                    val luminance =
                        ((rgb shr 16 and 0xFF) * 30 + (rgb shr 8 and 0xFF) * 59 + (rgb and 0xFF) * 11) / 100
                    if (luminance in 1..230) count++
                }
            }
            ink = count
        }
        return ink
    }

    /** Taps the scrim above a modal sheet and reports whether it asked to close. */
    private fun closedByScrimTap(dismissOnOutside: Boolean): Boolean {
        var closed = false
        var open by mutableStateOf(true)

        Scene(width = 600, height = 800) {
            Box(Modifier.fillMaxSize().background(Color.White)) {
                OverlayHost(Modifier.fillMaxSize()) {
                    ModalBottomSheet(
                        visible = open,
                        onDismissRequest = { closed = true; open = false },
                        dismissOnOutside = dismissOnOutside,
                    ) {
                        Box(Modifier.fillMaxWidth().height(200.dp).background(Color.LightGray))
                    }
                }
            }
        }.use { scene ->
            scene.frames(16)
            // Well above the sheet, which is 200dp of content at the bottom.
            scene.tap(Offset(300f, 120f))
            scene.frames(8)
        }
        return closed
    }

    /** The same, for a dialog. */
    private fun dialogClosedByScrimTap(dismissOnOutside: Boolean): Boolean {
        var closed = false
        var open by mutableStateOf(true)

        Scene(width = 600, height = 800) {
            Box(Modifier.fillMaxSize().background(Color.White)) {
                OverlayHost(Modifier.fillMaxSize()) {
                    Dialog(
                        visible = open,
                        onDismissRequest = { closed = true; open = false },
                        dismissOnOutside = dismissOnOutside,
                    ) {
                        Box(Modifier.fillMaxWidth().height(100.dp).background(Color.LightGray))
                    }
                    LaunchedEffect(Unit) { }
                }
            }
        }.use { scene ->
            scene.frames(16)
            // The dialog is centred and small; the top of the window is scrim.
            scene.tap(Offset(300f, 40f))
            scene.frames(8)
        }
        return closed
    }
}
