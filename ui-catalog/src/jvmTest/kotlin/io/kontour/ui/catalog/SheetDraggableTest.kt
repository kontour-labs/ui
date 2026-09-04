package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
 * The dismissal half of this was already built, under the name
 * `dismissOnOutside`; round 20 renamed it `dismissible` and widened it to cover
 * the drag as well as the tap. It had no test, which was the part worth fixing.
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
        assertTrue(closedByScrimTap(dismissible = true), "an outside tap did not close the sheet")
        assertTrue(
            !closedByScrimTap(dismissible = false),
            "an outside tap closed a sheet with `dismissible = false`",
        )
    }

    @Test
    fun aDialogIgnoresAnOutsideTapWhenAskedTo() {
        assertTrue(dialogClosedByScrimTap(dismissible = true), "an outside tap did not close the dialog")
        assertTrue(
            !dialogClosedByScrimTap(dismissible = false),
            "an outside tap closed a dialog with `dismissible = false`",
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
    private fun closedByScrimTap(dismissible: Boolean): Boolean {
        var closed = false
        var open by mutableStateOf(true)

        Scene(width = 600, height = 800) {
            Box(Modifier.fillMaxSize().background(Color.White)) {
                OverlayHost(Modifier.fillMaxSize()) {
                    ModalBottomSheet(
                        visible = open,
                        onDismissRequest = { closed = true; open = false },
                        dismissible = dismissible,
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

    @Test
    fun aSheetThatCannotBeDismissedComesBackFromADragToTheFloor() {
        var dismissed = false
        var settled: SheetDetent? = null

        Scene(width = 600, height = 800) {
            Box(Modifier.fillMaxSize().background(Color.White)) {
                OverlayHost(Modifier.fillMaxSize()) {
                    val sheet = rememberSheetState(
                        detents = listOf(SheetDetent.Hidden, SheetDetent.Expanded),
                        initialDetent = SheetDetent.Hidden,
                    )
                    ModalBottomSheet(
                        visible = true,
                        onDismissRequest = { dismissed = true },
                        state = sheet,
                        dismissible = false,
                    ) {
                        Box(Modifier.fillMaxWidth().height(200.dp).background(Color.LightGray))
                    }
                    LaunchedEffect(sheet) {
                        snapshotFlow { sheet.currentDetent }.collect { settled = it }
                    }
                }
            }
        }.use { scene ->
            val open = scene.frames(30)
            scene.drag(from = Offset(300f, open.crownRow() + 40f), to = Offset(300f, 790f), steps = 24)
            scene.frames(60)
        }

        assertTrue(
            !dismissed,
            "a sheet with `dismissible = false` still reported the drag as a dismissal",
        )
        assertEquals(
            SheetDetent.Expanded,
            settled,
            "a sheet with `dismissible = false` was left at $settled after being " +
                "dragged to the floor — it has to come back, or the screen is dimmed " +
                "and blocked by a sheet nobody can see",
        )
    }

    @Test
    fun floatingControlsGoWhenTheSheetGoes() {
        // Differential, not absolute. A hidden sheet is not a blank window: its
        // surface sits just off the bottom edge and the top of its shadow still
        // bleeds a few dp up into it. Counting all the ink measured that shadow
        // and would have failed whatever the controls did.
        assertEquals(
            ink(visible = false, controls = false),
            ink(visible = false, controls = true),
            "a hidden sheet drew more with floating controls than without — they " +
                "were parked at the bottom of the window over a sheet that was no " +
                "longer there",
        )
        assertTrue(
            ink(visible = true, controls = true) > ink(visible = true, controls = false),
            "an open sheet drew the same with and without floating controls, so " +
                "the assertion above proves nothing",
        )
    }

    /** How much a sheet draws, with or without floating controls, open or hidden. */
    private fun ink(visible: Boolean, controls: Boolean): Int {
        var ink = 0
        Scene(width = 600, height = 800) {
            val sheet = rememberSheetState(
                detents = listOf(SheetDetent.Hidden, SheetDetent.Expanded),
                initialDetent = if (visible) SheetDetent.Expanded else SheetDetent.Hidden,
            )
            Box(Modifier.fillMaxSize().background(Color.White)) {
                BottomSheet(
                    state = sheet,
                    floatingControls = if (controls) {
                        { Box(Modifier.height(40.dp).width(40.dp).background(Color.Red)) }
                    } else {
                        null
                    },
                ) {
                    Box(Modifier.fillMaxWidth().height(200.dp).background(Color.LightGray))
                }
            }
        }.use { scene ->
            val image = scene.frames(30)
            val background = image.getRGB(0, 0)
            var count = 0
            for (y in 0 until image.height) {
                for (x in 0 until image.width) {
                    if (image.getRGB(x, y) != background) count++
                }
            }
            ink = count
        }
        return ink
    }

    /** The same, for a dialog. */
    private fun dialogClosedByScrimTap(dismissible: Boolean): Boolean {
        var closed = false
        var open by mutableStateOf(true)

        Scene(width = 600, height = 800) {
            Box(Modifier.fillMaxSize().background(Color.White)) {
                OverlayHost(Modifier.fillMaxSize()) {
                    Dialog(
                        visible = open,
                        onDismissRequest = { closed = true; open = false },
                        dismissible = dismissible,
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
