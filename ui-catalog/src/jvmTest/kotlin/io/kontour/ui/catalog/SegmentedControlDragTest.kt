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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import io.kontour.ui.components.selection.SegmentedControl
import io.kontour.ui.components.selection.SegmentedControlDefaults
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

    /**
     * The move that starts the drag is where the drag starts from, and is not
     * also the drag's first step. It was both — the claim placed the finger
     * after the move and the move was then added again — so the finger ran a
     * move ahead of itself the whole way, and a first move ending just short of
     * a boundary crossed it.
     */
    @Test
    fun theClaimingMoveIsCountedOnce() {
        var selected by mutableIntStateOf(0)
        var bounds = Rect.Zero
        var pad = 0f

        Scene(width = 800, height = 200) {
            pad = SegmentedControlDefaults.TrackPadding.value * LocalDensity.current.density
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
            // Between the first segment and the second, inside the track's padding.
            val boundary = bounds.left + pad + (bounds.width - 2 * pad) / 3
            val y = bounds.center.y
            scene.press(Offset(boundary - 6f, y))
            scene.frames(1)
            scene.move(Offset(boundary - 2f, y))
            scene.frames(3)
            assertEquals(0, selected, "a finger 2px short of the boundary chose the next segment")
            scene.release(Offset(boundary - 2f, y))
            scene.frames(3)
        }
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

    /**
     * Side by side, a drag up or down that starts on the control is the page's.
     *
     * The track claimed the first pixel of travel whichever way it went, so a
     * page could not be scrolled from one: the thumb moved instead, and letting
     * go chose the segment under the finger. Found in Anyways' first run
     * (November 2026), three controls in a row in a sheet. Measured as the
     * scroller's own offset, as the stacked case is.
     */
    @Test
    fun aSideBySideDragUpOrDownScrollsThePage() {
        var selected by mutableIntStateOf(0)
        var bounds = Rect.Zero
        lateinit var scroll: ScrollState

        sideBySideOnAPage(onSelected = { selected = it }, selected = { selected }, onBounds = { bounds = it }, onScroll = { scroll = it }) { scene ->
            val from = bounds.alongX(0.85f)
            scene.drag(from = from, to = Offset(from.x + 10f, from.y - 300f))
            scene.frames(2)
            assertTrue(scroll.value > 0, "the page did not scroll under a drag up that started on a segmented control")
            assertEquals(0, selected, "a drag up the page from a segmented control chose segment $selected")
        }
    }

    /**
     * And one that starts along the track is the control's however far off it
     * the finger then goes: what `Movement` was chosen for in September 2026,
     * *"start dragging them, move your finger far enough away, and they stop
     * being selected"*, which deciding by the start's direction still keeps.
     */
    @Test
    fun aDragStartedAlongTheTrackKeepsItAsTheFingerWanders() {
        var selected by mutableIntStateOf(0)
        var bounds = Rect.Zero
        lateinit var scroll: ScrollState

        sideBySideOnAPage(onSelected = { selected = it }, selected = { selected }, onBounds = { bounds = it }, onScroll = { scroll = it }) { scene ->
            val start = bounds.alongX(0.15f)
            scene.press(start)
            scene.frames(1)
            // Along the track first, then well down and across to the last segment.
            for (step in 1..4) {
                scene.move(Offset(start.x + step * 10f, start.y))
                scene.frames(1)
            }
            val end = bounds.alongX(0.85f)
            for (step in 1..10) {
                scene.move(Offset(start.x + 40f + (end.x - start.x - 40f) * step / 10f, start.y + step * 25f))
                scene.frames(1)
            }
            scene.release(Offset(end.x, start.y + 250f))
            scene.frames(3)
            assertEquals(2, selected, "a drag begun along the track lost the thumb as it wandered off it")
            assertEquals(0, scroll.value, "a drag the control had taken also scrolled the page")
        }
    }

    /** A side-by-side control at the top of a page that scrolls. */
    private fun sideBySideOnAPage(
        onSelected: (Int) -> Unit,
        selected: () -> Int,
        onBounds: (Rect) -> Unit,
        onScroll: (ScrollState) -> Unit,
        body: (Scene) -> Unit,
    ) {
        Scene(width = SceneWidth, height = SceneHeight, density = Density.toFloat()) {
            val state = rememberScrollState()
            onScroll(state)
            Column(
                Modifier
                    .fillMaxSize()
                    .background(Color.White)
                    .verticalScroll(state)
                    .padding(Margin.dp),
            ) {
                SegmentedControl(
                    options = listOf("Auto", "Off", "On"),
                    selectedIndex = selected(),
                    onSelectedIndexChange = onSelected,
                    modifier = Modifier.fillMaxWidth().reportBounds(onBounds),
                )
                // Something to scroll to.
                Box(Modifier.fillMaxWidth().height((SceneHeight * 2).dp))
            }
        }.use { scene ->
            scene.frames(3)
            body(scene)
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
