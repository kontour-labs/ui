package io.kontour.ui.catalog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerType
import io.kontour.ui.components.datetime.WheelPicker
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The drum turns under a mouse as well as under a thumb.
 *
 * A `LazyColumn` is dragged by touch and, on desktop, only by the wheel — which
 * is the right convention for a list and the wrong one for a *drum*. Nobody has
 * ever set a time by scrolling a picker with a mouse wheel; they grab it.
 */
class WheelPickerDragTest {

    @Test
    fun aMouseDragTurnsTheWheel() {
        var selected by mutableIntStateOf(12)
        var bounds = Rect.Zero

        Scene(width = 400, height = 400) {
            Box(Modifier.fillMaxSize()) {
                WheelPicker(
                    items = (0..23).toList(),
                    selected = selected,
                    onSelectedChange = { selected = it },
                    label = { it.toString().padStart(2, '0') },
                    modifier = Modifier.reportBounds { bounds = it },
                )
            }
        }.use { scene ->
            scene.frames(4)
            assertTrue(bounds.height > 0f, "the wheel never reported a size")
            scene.drag(
                from = bounds.alongY(0.2f),
                to = bounds.alongY(0.8f),
                steps = 20,
                pointer = PointerType.Mouse,
            )
            scene.frames(6)
        }

        assertTrue(
            selected < 12,
            "a mouse drag downward left the wheel on $selected — it did not turn",
        )
    }

    @Test
    fun aTouchDragStillTurnsItOnce() {
        // The list already handles touch. An outer drag that also handled it
        // would turn the drum twice as far as the finger moved.
        var selected by mutableIntStateOf(12)
        var bounds = Rect.Zero

        Scene(width = 400, height = 400) {
            Box(Modifier.fillMaxSize()) {
                WheelPicker(
                    items = (0..23).toList(),
                    selected = selected,
                    onSelectedChange = { selected = it },
                    label = { it.toString().padStart(2, '0') },
                    itemHeight = androidx.compose.ui.unit.Dp(40f),
                    modifier = Modifier.reportBounds { bounds = it },
                )
            }
        }.use { scene ->
            scene.frames(4)
            // Two rows' worth: 80dp at this density is 160px, and the wheel is
            // 40dp a row.
            scene.drag(
                from = bounds.alongY(0.5f),
                to = androidx.compose.ui.geometry.Offset(bounds.center.x, bounds.center.y + 160f),
                steps = 20,
            )
            scene.frames(8)
        }

        assertTrue(
            selected in 9..11,
            "a two-row touch drag moved the wheel from 12 to $selected — it " +
                "should have travelled about two rows, and anything further " +
                "means the drag is being handled twice",
        )
    }
}
