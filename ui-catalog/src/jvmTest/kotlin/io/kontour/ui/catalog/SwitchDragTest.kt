package io.kontour.ui.catalog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.Role
import io.kontour.ui.components.selection.SelectionRow
import io.kontour.ui.components.selection.Switch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A switch can be dragged, and a switch in a row can be dragged without the row
 * losing its tap.
 *
 * A thumb on a track is the most draggable-looking control in any library, and
 * this one could only be tapped. The gesture is worth having on its own, and it
 * is worth having *inside a `SelectionRow`* — which is the shape almost every
 * switch in an app actually takes, and the one where it was hardest to add: the
 * row owns the tap and hands the switch `onCheckedChange = null`, so the switch
 * had nothing to call. It now drags against the row's own toggle.
 *
 * Every case here drives real pointer events. There is no state object to
 * interrogate, and that is deliberate — a gesture that works on a state holder
 * but is not reachable from a finger is the failure this is guarding against.
 */
class SwitchDragTest {

    @Test
    fun draggingTheThumbAcrossTurnsItOn() {
        var checked by mutableStateOf(false)
        var bounds = Rect.Zero

        Scene(width = 400, height = 200) {
            Box(Modifier.fillMaxSize()) {
                Switch(
                    checked = checked,
                    onCheckedChange = { checked = it },
                    modifier = Modifier.reportBounds { bounds = it },
                )
            }
        }.use { scene ->
            scene.frames(3)
            assertTrue(bounds.width > 0f, "the switch never reported a size")
            scene.drag(from = bounds.alongX(0.2f), to = bounds.alongX(1.4f))
            scene.frames(4)
        }

        assertTrue(checked, "dragging the thumb across the track did not turn it on")
    }

    @Test
    fun aDragThatDoesNotCarryLeavesItAlone() {
        var checked by mutableStateOf(false)
        var bounds = Rect.Zero

        Scene(width = 400, height = 200) {
            Box(Modifier.fillMaxSize()) {
                Switch(
                    checked = checked,
                    onCheckedChange = { checked = it },
                    modifier = Modifier.reportBounds { bounds = it },
                )
            }
        }.use { scene ->
            scene.frames(3)
            // Past the touch slop, but nowhere near the middle of the travel.
            scene.drag(from = bounds.alongX(0.1f), to = bounds.alongX(0.3f), steps = 8)
            scene.frames(4)
        }

        assertTrue(!checked, "a short drag toggled the switch it should have sprung back from")
    }

    @Test
    fun draggingTheThumbInsideARowTogglesTheRow() {
        var checked by mutableStateOf(false)
        var bounds = Rect.Zero

        Scene(width = 600, height = 200) {
            Box(Modifier.fillMaxSize()) {
                SelectionRow(
                    selected = checked,
                    onSelectedChange = { checked = it },
                    role = Role.Switch,
                ) {
                    +"Live vehicles"
                    trailing {
                        Switch(
                            checked = checked,
                            onCheckedChange = null,
                            modifier = Modifier.reportBounds { bounds = it },
                        )
                    }
                }
            }
        }.use { scene ->
            scene.frames(3)
            assertTrue(bounds.width > 0f, "the switch never reported a size")
            scene.drag(from = bounds.alongX(0.2f), to = bounds.alongX(1.4f))
            scene.frames(4)
        }

        assertTrue(
            checked,
            "dragging the thumb of a switch inside a SelectionRow did nothing — " +
                "the row owns the tap, so the switch has to drag against the " +
                "row's toggle",
        )
    }

    @Test
    fun theRowStillOwnsTheTap() {
        // The gesture the drag must not have stolen. A SelectionRow exists to
        // make the whole row the target; a row where only the 48dp switch
        // responds is worse than one with no switch drag at all.
        var toggles = 0
        var checked by mutableStateOf(false)
        var row = Rect.Zero

        Scene(width = 600, height = 200) {
            Box(Modifier.fillMaxSize()) {
                SelectionRow(
                    selected = checked,
                    onSelectedChange = { checked = it; toggles++ },
                    role = Role.Switch,
                    modifier = Modifier.reportBounds { row = it },
                ) {
                    +"Live vehicles"
                    trailing { Switch(checked = checked, onCheckedChange = null) }
                }
            }
        }.use { scene ->
            scene.frames(3)
            // Far from the switch: on the label, at the left of the row.
            scene.tap(row.alongX(0.15f))
            scene.frames(2)
        }

        assertEquals(1, toggles, "tapping the row's label did not toggle it")
        assertTrue(checked, "the row toggled to the wrong value")
    }
}
