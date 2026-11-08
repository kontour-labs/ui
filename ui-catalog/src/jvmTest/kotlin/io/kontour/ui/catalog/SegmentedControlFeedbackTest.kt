package io.kontour.ui.catalog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import io.kontour.ui.components.selection.SegmentedControlDefaults
import io.kontour.ui.components.selection.SegmentedControl
import io.kontour.ui.interaction.FeedbackDispatcher
import io.kontour.ui.interaction.FeedbackIntent
import io.kontour.ui.interaction.LocalFeedback
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * One haptic for each segment a drag crosses, and no more.
 *
 * Reported in November 2026: *"in the segmented control, i sometimes find that
 * the haptics fire twice on a drag (usually when it gets dragged after its first
 * load)"*. Each drag here is recorded intent by intent, so a second report of a
 * crossing, or a tap felt on top of a drag, shows up as a count that is one too
 * many.
 */
class SegmentedControlFeedbackTest {

    private class Recorded {
        val felt = mutableListOf<FeedbackIntent>()
        val snaps get() = felt.count { it == FeedbackIntent.Snap }
        val taps get() = felt.count { it == FeedbackIntent.Tap || it == FeedbackIntent.Selection }
    }

    private fun scene(recorded: Recorded, start: Int = 0, block: (Scene, Rect) -> Unit) {
        var selected by mutableIntStateOf(start)
        var bounds = Rect.Zero
        Scene(width = 800, height = 200) {
            CompositionLocalProvider(LocalFeedback provides FeedbackDispatcher { recorded.felt += it }) {
                Box(Modifier.fillMaxSize()) {
                    SegmentedControl(
                        options = listOf("Day", "Week", "Month"),
                        selectedIndex = selected,
                        onSelectedIndexChange = { selected = it },
                        modifier = Modifier.fillMaxWidth().reportBounds { bounds = it },
                    )
                }
            }
        }.use { scene ->
            scene.frames(3)
            block(scene, bounds)
        }
    }

    @Test
    fun aDragAcrossEverySegmentSnapsOncePerBoundary() {
        val recorded = Recorded()
        scene(recorded) { scene, bounds ->
            scene.drag(from = bounds.alongX(0.15f), to = bounds.alongX(0.85f))
            scene.frames(3)
        }
        assertEquals(2, recorded.snaps, "crossing two boundaries felt ${recorded.felt}")
        assertEquals(0, recorded.taps, "a drag was also felt as a tap: ${recorded.felt}")
    }

    @Test
    fun aDragOverAndBackSnapsOncePerBoundaryEachWay() {
        val recorded = Recorded()
        scene(recorded) { scene, bounds ->
            scene.drag(from = bounds.alongX(0.15f), to = bounds.alongX(0.85f), release = false)
            scene.drag(from = bounds.alongX(0.85f), to = bounds.alongX(0.15f))
            scene.frames(3)
        }
        assertEquals(4, recorded.snaps, "over and back felt ${recorded.felt}")
    }

    /** The first drag after the control appears, from a segment that is not the selected one. */
    @Test
    fun aFirstDragFromAnotherSegmentSnapsForTheJumpAndEachBoundary() {
        val recorded = Recorded()
        scene(recorded) { scene, bounds ->
            scene.drag(from = bounds.alongX(0.5f), to = bounds.alongX(0.85f))
            scene.frames(3)
        }
        assertEquals(2, recorded.snaps, "a drag from Week to Month, with Day selected, felt ${recorded.felt}")
        assertEquals(0, recorded.taps, "a drag was also felt as a tap: ${recorded.felt}")
    }

    @Test
    fun aDragAfterATapSnapsOnlyForWhatItCrosses() {
        val recorded = Recorded()
        scene(recorded) { scene, bounds ->
            scene.tap(bounds.alongX(0.5f))
            scene.frames(4)
            val afterTap = recorded.felt.toList()
            scene.drag(from = bounds.alongX(0.5f), to = bounds.alongX(0.85f))
            scene.frames(3)
            assertEquals(1, afterTap.count { it == FeedbackIntent.Tap || it == FeedbackIntent.Selection }, "the tap felt $afterTap")
        }
        assertEquals(1, recorded.snaps, "a drag from the tapped segment to the next felt ${recorded.felt}")
    }

    /** The same drag twice: nothing left over from the first should add to the second. */
    @Test
    fun aSecondDragFeelsTheSameAsTheFirst() {
        val recorded = Recorded()
        scene(recorded) { scene, bounds ->
            scene.drag(from = bounds.alongX(0.15f), to = bounds.alongX(0.5f))
            scene.frames(4)
            val first = recorded.snaps
            scene.drag(from = bounds.alongX(0.5f), to = bounds.alongX(0.85f))
            scene.frames(3)
            assertEquals(1, first, "the first drag felt ${recorded.felt}")
        }
        assertEquals(2, recorded.snaps, "two one-boundary drags felt ${recorded.felt}")
    }

    /**
     * A finger resting on a boundary is not still: a touchscreen reports it a
     * pixel or two either side, frame to frame. Each of those used to be a
     * crossing, with a snap and a change of selection, so a drag that paused near
     * a boundary was felt twice or more for the one segment it moved to.
     */
    @Test
    fun aFingerWobblingOnABoundarySnapsOnce() {
        val recorded = Recorded()
        val changes = wobble(recorded)
        assertEquals(1, recorded.snaps, "a finger wobbling two pixels on a boundary felt ${recorded.felt}")
        assertEquals(1, changes, "a finger wobbling two pixels on a boundary changed the selection $changes times")
    }

    private fun wobble(recorded: Recorded): Int {
        var selected by mutableIntStateOf(0)
        var bounds = Rect.Zero
        var pad = 0f
        val changes = mutableListOf<Int>()
        Scene(width = 800, height = 200) {
            pad = SegmentedControlDefaults.TrackPadding.value * LocalDensity.current.density
            CompositionLocalProvider(LocalFeedback provides FeedbackDispatcher { recorded.felt += it }) {
                Box(Modifier.fillMaxSize()) {
                    SegmentedControl(
                        options = listOf("Day", "Week", "Month"),
                        selectedIndex = selected,
                        onSelectedIndexChange = { selected = it; changes += it },
                        modifier = Modifier.fillMaxWidth().reportBounds { bounds = it },
                    )
                }
            }
        }.use { scene ->
            scene.frames(3)
            val boundary = bounds.left + pad + (bounds.width - 2 * pad) / 3
            val y = bounds.center.y
            scene.press(Offset(bounds.alongX(0.1f).x, y))
            scene.frames(1)
            // Over the boundary, plainly, and back to rest on it: two pixels
            // either side of it, as a finger held there is reported.
            for (x in listOf(boundary - 40f, boundary - 20f, boundary + 20f, boundary + 2f, boundary - 2f, boundary + 2f, boundary - 2f, boundary - 1f, boundary + 3f)) {
                scene.move(Offset(x, y))
                scene.frames(1)
            }
            scene.release(Offset(boundary + 3f, y))
            scene.frames(3)
        }
        return changes.size
    }
}
