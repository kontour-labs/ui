package io.kontour.ui.catalog

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
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
                    selectedIndex = selected,
                    onSelectedIndexChange = { selected = it },
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
                    selectedIndex = selected,
                    onSelectedIndexChange = { selected = it; seen += it },
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
                    selectedIndex = selected,
                    onSelectedIndexChange = { selected = it },
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

    /**
     * Stacked, the thumb drags too — and only from the thumb.
     *
     * *"You know how we make the segmented control vertical if text gets too
     * big? Can we make it so you can drag the handle vertically too, rather than
     * just tapping?"*
     *
     * The drag used to be switched off outright when the control stacked, with a
     * reason attached that was half right: *"a vertical drag over stacked
     * segments would also be competing with the page scroller for its own
     * direction."* Side by side the two want different axes and the judgement is
     * about slope; stacked they want the same one, and no slope can separate
     * them. What separates them is **where the finger went down** — on the thumb,
     * or on the page.
     *
     * ### Stacked on purpose, at one type size
     *
     * Four labels with a long one in a 244dp track, which is
     * `SegmentedTypeScaleTest`'s own reported case and stacks at 100% type. A
     * font scale would stack it too and would make this a test of two things.
     */
    @Test
    fun aStackedControlFollowsAVerticalDragOnItsThumb() {
        var selected by mutableIntStateOf(0)
        var bounds = Rect.Zero
        val seen = mutableListOf<Int>()

        stacked(
            onSelected = { selected = it; seen += it },
            selected = { selected },
            onBounds = { bounds = it },
        ) { scene ->
            scene.drag(from = bounds.alongY(FirstRow), to = bounds.alongY(LastRow))
            scene.frames(2)
        }

        assertEquals(
            3,
            selected,
            "dragging from the first row to the last left the selection at " +
                "$selected. Stacked, the track is vertical and the thumb is what " +
                "the finger went down on, so it should come with it",
        )
        assertEquals(
            listOf(1, 2, 3),
            seen,
            "the selection jumped rather than tracking the finger: $seen",
        )
    }

    /**
     * And a drag that starts anywhere else belongs to the page.
     *
     * The other half, and the one that makes the first half safe: a stacked
     * control sits in a scrolling page more often than not, and a control that
     * claimed every vertical drag across itself would make the page unscrollable
     * wherever it sat. `ownedDrag` consumes both axes the moment it claims, so
     * declining has to happen **before** the first move — an ignored delta is
     * still a consumed one. See `accepts`.
     *
     * Measured as the scroller's own offset rather than as the selection staying
     * put, because the selection staying put is also what a control that ate the
     * gesture and did nothing with it would produce.
     */
    @Test
    fun aStackedDragThatMissesTheThumbScrollsThePage() {
        var selected by mutableIntStateOf(0)
        var bounds = Rect.Zero
        lateinit var scroll: ScrollState

        Scene(width = SceneWidth, height = SceneHeight, density = Density.toFloat()) {
            val state = rememberScrollState()
            scroll = state
            Column(
                Modifier
                    .fillMaxSize()
                    .background(Color.White)
                    .verticalScroll(state)
                    .padding(Margin.dp),
            ) {
                SegmentedControl(
                    options = StackedOptions,
                    selectedIndex = selected,
                    onSelectedIndexChange = { selected = it },
                    modifier = Modifier
                        .width(TrackWidth.dp)
                        .reportBounds { bounds = it },
                )
                // Something to scroll to.
                Box(Modifier.fillMaxWidth().height(SceneHeight.dp))
            }
        }.use { scene ->
            scene.frames(3)
            assertTrue(bounds.height > 0f, "the control never reported a size")

            // The last row, which is not the selected one.
            scene.drag(from = bounds.alongY(LastRow), to = bounds.alongY(FirstRow))
            scene.frames(2)

            assertTrue(
                scroll.value > 0,
                "the page did not scroll at all under a drag that started on an " +
                    "unselected row. The control is not supposed to want that " +
                    "gesture: a vertical drag it did not begin on its thumb has " +
                    "to reach the scroller underneath unconsumed",
            )
            assertEquals(
                0,
                selected,
                "the selection moved to $selected under a drag the page was " +
                    "supposed to own",
            )
        }
    }

    /** A stacked control on a white page, and whatever the caller does with it. */
    private fun stacked(
        onSelected: (Int) -> Unit,
        selected: () -> Int,
        onBounds: (Rect) -> Unit,
        body: (Scene) -> Unit,
    ) {
        Scene(width = SceneWidth, height = SceneHeight, density = Density.toFloat()) {
            Box(Modifier.fillMaxSize().background(Color.White).padding(Margin.dp)) {
                SegmentedControl(
                    options = StackedOptions,
                    selectedIndex = selected(),
                    onSelectedIndexChange = onSelected,
                    modifier = Modifier.width(TrackWidth.dp).reportBounds(onBounds),
                )
            }
        }.use { scene ->
            scene.frames(3)
            body(scene)
        }
    }

    private companion object {
        /** `SegmentedTypeScaleTest`'s reported case: it stacks at 100% type. */
        val StackedOptions = listOf("Auto", "Touch", "Mouse", "Keyboard")

        const val TrackWidth = 244
        const val Margin = 16
        const val Density = 2
        const val SceneWidth = (TrackWidth + Margin * 2) * Density
        const val SceneHeight = 640

        /** Inside the first row of four, and inside the last. */
        const val FirstRow = 0.12f
        const val LastRow = 0.88f
    }
}
