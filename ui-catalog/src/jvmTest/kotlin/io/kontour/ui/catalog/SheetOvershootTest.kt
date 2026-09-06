package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
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
 * A sheet pulled above its tallest detent stretches, and comes back.
 *
 * `anchoredDraggable` clamps to its anchor range, so before this the sheet did
 * not move at all under a finger still travelling upward — measured, not
 * assumed: dragged 292px past the top, the offset stayed at `352.0` and the
 * sheet's crown row never moved a pixel across twenty frames. Reported as
 * feeling "too rigid", which is exactly what a boundary the finger cannot feel
 * is.
 *
 * Three properties, and the middle one is the point. Anyone can make a sheet
 * move further; what makes it read as a rubber band rather than as a second,
 * shorter track is that each pixel of finger buys less than the last.
 */
class SheetOvershootTest {

    @Test
    fun aSheetStretchesAboveItsTopDetentAndSpringsBack() {
        var state: SheetState? = null
        val crowns = mutableListOf<Int>()
        var restingCrown = 0
        var settledCrown = 0

        Scene(width = 600, height = 800) {
            val sheet = rememberSheetState(
                detents = listOf(SheetDetent.Hidden, SheetDetent.Expanded),
                initialDetent = SheetDetent.Expanded,
            )
            state = sheet
            Box(Modifier.fillMaxSize().background(Color.White)) {
                BottomSheet(state = sheet) {
                    Box(Modifier.fillMaxWidth().height(200.dp).background(Color.LightGray))
                }
            }
        }.use { scene ->
            restingCrown = scene.frames(10).crownRow()
            scene.drag(
                from = Offset(300f, restingCrown + 20f),
                to = Offset(300f, 100f),
                steps = 20,
                release = false,
            ) { _, image -> crowns += image.crownRow() }
            scene.release(Offset(300f, 100f))
            settledCrown = scene.frames(40).crownRow()
        }

        val sheet = requireNotNull(state)
        val stretch = restingCrown - crowns.last()

        assertTrue(
            stretch > 40,
            "the sheet only rose ${stretch}px above its top detent under a 292px " +
                "drag — it is supposed to follow the finger, not stop dead",
        )

        // A twelfth of an 800px window is 66px, and the stretch should reach it
        // rather than sail past: the cap is what stops the gap reading as a
        // detent the sheet forgot to settle at.
        assertTrue(
            stretch <= 800 / 12 + 2,
            "the sheet rose ${stretch}px, past the cap of ${800 / 12}px",
        )

        // Diminishing returns: the finger travels at a constant rate, so the
        // first half of the drag has to move the sheet further than the second.
        // A linear stretch with a hard stop is the same rigid boundary moved
        // somewhere else, and would fail here.
        val half = crowns.size / 2
        val early = crowns.first() - crowns[half]
        val late = crowns[half] - crowns.last()
        assertTrue(
            early > late * 2,
            "the stretch moved ${early}px over the first half of the drag and " +
                "${late}px over the second — it is meant to resist, and at that " +
                "ratio it reads as a second track rather than as a rubber band",
        )

        assertEquals(
            restingCrown,
            settledCrown,
            "the sheet did not spring back: it rested at $restingCrown before the " +
                "drag and at $settledCrown after it",
        )
        assertEquals(
            SheetDetent.Expanded,
            sheet.currentDetent,
            "the stretch is visual, so the sheet must still be at the detent it " +
                "started from — it settled at ${sheet.currentDetent}",
        )
    }

    /**
     * And it returns to its *own* detent, whatever else is in the list.
     *
     * Round 22 reported a sheet dragged above its top detent settling at the
     * **lowest** one on release, and the reading that produced that plan was
     * `updateAnchors`' `positions.keys.first()` fallback — which is the lowest,
     * because `resolveAnchors` returns its map in detent order. It is not
     * reachable from a release: that branch is guarded on the offset being
     * `NaN`, which it only is before the first layout.
     *
     * So the report did not reproduce, in any of the four shapes below or in the
     * two-detent one above. Each stretches by roughly the cap and settles back
     * where it started. This exists so that stays true — a non-reproduction is
     * worth exactly as much as the test that keeps it one, and each of these was
     * a hypothesis about how the sheet could pick the wrong end:
     *
     *  - three detents, so there is a middle one to fall into;
     *  - a `peek`, whose anchor is resolved from a measured position and so can
     *    move underneath a drag;
     *  - one visible detent, where the only other anchor *is* the lowest;
     *  - a vetoed tallest, so `allowedDetents` is shorter than `detents`.
     */
    @Test
    fun itReturnsToItsOwnDetentWhateverElseIsInTheList() {
        val wrong = buildList {
            shapes().forEach { shape ->
                val settled = stretchAndRelease(shape)
                if (settled != shape.initial) add("${shape.name}: settled at $settled")
            }
        }
        assertTrue(
            wrong.isEmpty(),
            "a sheet dragged above its top detent and released landed somewhere " +
                "else:\n" + wrong.joinToString("\n") { "  · $it" },
        )
    }

    /**
     * A full-height sheet dragged upward does not fall a detent when let go.
     *
     * The same report as the one above, made again a round later, and this is
     * the shape that produces it — which none of the four in `shapes()` does,
     * because every one of them can stretch.
     *
     * A sheet at [SheetDetent.Full] is already the height of its container, so
     * there is nowhere above it to stretch into: moving it up lifts its bottom
     * edge off the bottom of the screen. `canOvershoot` was false there, and it
     * gated the *absorbing* as well as the drawing — so an upward drag did
     * nothing with the gesture at all and left it live. A finger does not leave
     * the glass cleanly; it rolls off, and two dozen pixels of roll-off over
     * three frames is several hundred pixels a second downward, which clears
     * `anchoredDraggable`'s velocity threshold and settles the sheet a detent
     * lower.
     *
     * Three measurements, because one of them alone proves nothing:
     *
     *  - the roll-off **after an upward drag** must leave the sheet where it is
     *    — this is the defect;
     *  - the same roll-off **on its own** must also leave it there, or the first
     *    result is only "24px does nothing";
     *  - a genuinely large downward drag **must still move it**, or the fix has
     *    turned a full-height sheet into one that cannot be dragged down.
     */
    @Test
    fun aFullHeightSheetHeldUpDoesNotFallOnRelease() {
        val detents = listOf(SheetDetent.Hidden, SheetDetent.Half, SheetDetent.Full)

        val afterPull = dragAndRollOff(detents, up = true, rollOffSteps = 3)
        val rollOffAlone = dragAndRollOff(detents, up = false, rollOffSteps = 3)
        val realDragDown = dragAndRollOff(detents, up = false, rollOffSteps = 24)

        assertEquals(
            SheetDetent.Full, rollOffAlone,
            "the control moved: ${RollOffStep.toInt() * 3}px of downward travel " +
                "took the sheet to $rollOffAlone on its own, so the case below " +
                "would prove nothing",
        )
        assertEquals(
            SheetDetent.Full, afterPull,
            "the sheet was dragged up against its top, where it cannot move, and " +
                "then let go — and settled at $afterPull. The upward drag is the " +
                "only difference from the control, so the pull left the gesture " +
                "live and the finger's roll-off became a flick downward.",
        )
        assertTrue(
            realDragDown != SheetDetent.Full,
            "a ${RollOffStep.toInt() * 24}px drag downward left the sheet at " +
                "$realDragDown — absorbing the upward pull has made a full-height " +
                "sheet undraggable, which is worse than the defect",
        )
    }

    /**
     * Optionally drags the sheet up against its stop, then travels back down
     * [rollOffSteps] × [RollOffStep] pixels a frame at a time before releasing.
     *
     * A frame per move, so the velocity tracker sees the travel as a gesture
     * rather than as one teleport. `ModalBottomSheet` over a host, because that
     * is the shape the defect appears in: a bare `BottomSheet` at `Full`
     * survives the same roll-off, and a first draft built on one passed against
     * the unfixed code.
     */
    private fun dragAndRollOff(
        detents: List<SheetDetent>,
        up: Boolean,
        rollOffSteps: Int,
    ): SheetDetent {
        var state: SheetState? = null

        Scene(width = 600, height = 800) {
            val sheet = rememberSheetState(detents = detents, initialDetent = SheetDetent.Full)
            state = sheet
            OverlayHost(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().background(Color.White))
                ModalBottomSheet(visible = true, onDismissRequest = {}, state = sheet) {
                    Box(Modifier.fillMaxWidth().height(200.dp).background(Color.LightGray))
                }
            }
        }.use { scene ->
            scene.frames(40)
            val start = 800f - 120f
            scene.press(Offset(300f, start))
            if (up) {
                val span = start - 60f
                repeat(20) { i ->
                    scene.move(Offset(300f, start - span * (i + 1) / 20))
                    scene.frame()
                }
            }
            val from = if (up) 60f else start
            repeat(rollOffSteps) { i ->
                scene.move(Offset(300f, from + (i + 1) * RollOffStep))
                scene.frame()
            }
            scene.release(Offset(300f, from + rollOffSteps * RollOffStep))
            scene.frames(80)
        }

        return requireNotNull(state).currentDetent
    }

    private class Shape(
        val name: String,
        val detents: List<SheetDetent>,
        val initial: SheetDetent,
        val confirm: (SheetDetent) -> Boolean = { true },
    )

    private fun shapes(): List<Shape> {
        val peek = SheetDetent.peek(120.dp)
        return listOf(
            Shape(
                "three detents",
                listOf(SheetDetent.Hidden, SheetDetent.Half, SheetDetent.Expanded),
                SheetDetent.Expanded,
            ),
            Shape(
                "with a peek",
                listOf(SheetDetent.Hidden, peek, SheetDetent.Half, SheetDetent.Expanded),
                SheetDetent.Expanded,
            ),
            Shape("one visible detent", listOf(SheetDetent.Hidden, peek), peek),
            Shape(
                "tallest vetoed",
                listOf(SheetDetent.Hidden, SheetDetent.Half, SheetDetent.Expanded),
                SheetDetent.Half,
            ) { it != SheetDetent.Expanded },
        )
    }

    /** Drags [shape]'s sheet above its top detent, lets go, and reports where it lands. */
    private fun stretchAndRelease(shape: Shape): SheetDetent {
        var state: SheetState? = null
        var body = Rect.Zero

        Scene(width = 600, height = 800) {
            val sheet = rememberSheetState(
                detents = shape.detents,
                initialDetent = shape.initial,
                confirmDetentChange = shape.confirm,
            )
            state = sheet
            Box(Modifier.fillMaxSize().background(Color.White)) {
                BottomSheet(state = sheet) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                            .background(Color.LightGray)
                            .reportBounds { body = it }
                    )
                }
            }
        }.use { scene ->
            scene.frames(20)
            val grab = Offset(300f, body.top + 20f)
            scene.drag(from = grab, to = Offset(300f, grab.y - 260f), steps = 20)
            scene.frames(120)
        }

        return requireNotNull(state).currentDetent
    }

    private companion object {
        /** How far a rolling finger travels between two frames. */
        const val RollOffStep = 8f
    }
}
