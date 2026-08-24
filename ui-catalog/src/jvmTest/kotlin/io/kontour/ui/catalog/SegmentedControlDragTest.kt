package io.kontour.ui.catalog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import io.kontour.ui.components.selection.SegmentedControl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The thumb slides, so a finger should be able to slide it.
 *
 * A segmented control's whole reason to exist rather than be three buttons is
 * that one surface moves between the options — and it could only be tapped. The
 * drag lives on the track rather than on each segment, because a drag that
 * starts on "Depart" and ends on "Arrive" leaves the segment it began in, and a
 * per-segment gesture loses the pointer at the boundary.
 */
class SegmentedControlDragTest {

    @Test
    fun draggingAcrossTheTrackTakesTheThumbWithIt() {
        var selected by mutableIntStateOf(0)
        var bounds = Rect.Zero

        Scene(width = 800, height = 200) {
            Box(Modifier.fillMaxSize()) {
                SegmentedControl(
                    options = listOf("Day", "Week", "Month"),
                    selected = selected,
                    onSelectedChange = { selected = it },
                    modifier = Modifier.fillMaxWidth().reportBounds { bounds = it },
                )
            }
        }.use { scene ->
            scene.frames(3)
            assertTrue(bounds.width > 0f, "the control never reported a size")
            scene.drag(from = bounds.alongX(0.15f), to = bounds.alongX(0.85f))
            scene.frames(2)
        }

        assertEquals(
            2,
            selected,
            "dragging from the first segment to the last left the selection at " +
                "$selected",
        )
    }

    @Test
    fun theSelectionFollowsTheFingerBackAgain() {
        // Not just "ends up where it stopped" — the point of a drag over a
        // sliding thumb is that the thumb is under the finger the whole way.
        var selected by mutableIntStateOf(0)
        var bounds = Rect.Zero
        val seen = mutableListOf<Int>()

        Scene(width = 800, height = 200) {
            Box(Modifier.fillMaxSize()) {
                SegmentedControl(
                    options = listOf("Day", "Week", "Month"),
                    selected = selected,
                    onSelectedChange = { selected = it; seen += it },
                    modifier = Modifier.fillMaxWidth().reportBounds { bounds = it },
                )
            }
        }.use { scene ->
            scene.frames(3)
            scene.drag(from = bounds.alongX(0.15f), to = bounds.alongX(0.85f), release = false)
            scene.drag(from = bounds.alongX(0.85f), to = bounds.alongX(0.15f))
            scene.frames(2)
        }

        assertEquals(listOf(1, 2, 1, 0), seen, "the selection did not track the finger both ways")
    }

    @Test
    fun aTapStillSelectsTheSegmentUnderIt() {
        // The gesture the drag must not have stolen. `detectHorizontalDragGestures`
        // waits for touch slop, so a press that never travels belongs to the
        // segment's own `selectable`.
        var selected by mutableIntStateOf(0)
        var bounds = Rect.Zero

        Scene(width = 800, height = 200) {
            Box(Modifier.fillMaxSize()) {
                SegmentedControl(
                    options = listOf("Day", "Week", "Month"),
                    selected = selected,
                    onSelectedChange = { selected = it },
                    modifier = Modifier.fillMaxWidth().reportBounds { bounds = it },
                )
            }
        }.use { scene ->
            scene.frames(3)
            scene.tap(bounds.alongX(0.5f))
            scene.frames(2)
        }

        assertEquals(1, selected, "tapping the middle segment did not select it")
    }
}
