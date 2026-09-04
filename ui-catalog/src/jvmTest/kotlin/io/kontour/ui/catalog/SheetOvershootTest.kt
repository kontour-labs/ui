package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.kontour.ui.sheet.BottomSheet
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
}
