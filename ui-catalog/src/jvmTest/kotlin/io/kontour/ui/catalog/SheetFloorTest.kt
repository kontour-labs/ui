package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.sheet.ModalBottomSheet
import io.kontour.ui.sheet.SheetDetent
import io.kontour.ui.sheet.SheetState
import io.kontour.ui.sheet.rememberSheetState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A sheet that cannot be dismissed has a bottom, and it is one the finger feels.
 *
 * ### What it used to do, traced
 *
 * `dismissible = false` closed the tap outside and the back gesture and left the
 * drag alone, so the sheet went all the way down under a finger, settled
 * `Hidden`, declined to tell the caller — correctly, it is not dismissable — and
 * was put back about a second later by the machinery that exists for a caller
 * *refusing* a dismissal. Measured across one drag and its release:
 *
 * ```
 * drag  0: expanded vf=0.996      drag 12: expanded vf=0.574
 * drag  6: expanded vf=0.785      drag 18: expanded vf=0.363
 *   +30f: expanded vf=0.00005     ← gone
 *   +40f: hidden   vf=0.030       ← settled hidden, coming back
 *   +60f: expanded vf=1.0         ← and there it is again
 * ```
 *
 * Three reports in that one trace: the scrim fading out as the sheet is dragged,
 * the sheet closing and reappearing a second later, and the sheet being
 * draggable below its minimum detent at all.
 *
 * ### And the fix has to *give*
 *
 * Stopping the drag dead would clear every line of that trace and be its own
 * defect — a boundary the finger cannot feel, which is the thing three other
 * components in this library have now been fixed for. So the sheet is checked
 * for movement first and for staying put second.
 */
class SheetFloorTest {

    @Test
    fun anUndismissableSheetStretchesDownwardAndComesBack() {
        val run = dragDown(dismissible = false)

        val give = run.tops.max() - run.restingTop
        assertTrue(
            give > 8,
            "the sheet moved ${give}px under a ${Travel.toInt()}px downward drag it is " +
                "not allowed to follow. Refusing to move at all is a boundary the " +
                "finger cannot feel, which is the defect on the other end of this " +
                "same sheet",
        )

        // Diminishing returns, the same property the top end is held to.
        val half = run.tops.size / 2
        val early = run.tops[half - 1] - run.restingTop
        val late = run.tops.last() - run.tops[half - 1]
        assertTrue(
            late > 0,
            "the sheet moved ${early}px over the first half of the drag and stopped " +
                "dead for the second — a rigid boundary moved somewhere else",
        )
        assertTrue(
            early > late,
            "the sheet moved ${early}px over the first half of the drag and ${late}px " +
                "over the second — nothing is resisting, it is simply a shorter track",
        )

        assertTrue(
            run.settledTop <= run.restingTop + 2,
            "the sheet rested at ${run.restingTop} before the drag and ${run.settledTop} " +
                "after it, so the stretch did not spring back",
        )
    }

    @Test
    fun andItsScrimDoesNotFadeOnTheWay() {
        val run = dragDown(dismissible = false)
        assertTrue(
            run.dimmestFraction > 0.95f,
            "the sheet's visible fraction fell to ${run.dimmestFraction} while it was " +
                "being dragged, and the scrim follows that number — so the screen " +
                "brightened behind a sheet that was never going anywhere",
        )
        assertEquals(
            0,
            run.dismissRequests,
            "an undismissable sheet asked to be dismissed",
        )
        assertTrue(
            run.everHidden.not(),
            "the sheet reached Hidden. It comes back from there about a second later, " +
                "which is the reported closing-and-reappearing",
        )
    }

    /** The half that has to keep working: an ordinary sheet still drags shut. */
    @Test
    fun anOrdinarySheetStillDragsShut() {
        val run = dragDown(dismissible = true)
        assertTrue(
            run.everHidden,
            "a dismissable sheet dragged ${Travel.toInt()}px downward never reached " +
                "Hidden — the floor is holding a sheet that has no business having one",
        )
        assertEquals(
            1,
            run.dismissRequests,
            "a dismissable sheet dragged shut told its caller ${run.dismissRequests} " +
                "times",
        )
    }

    private class Run(
        val restingTop: Float,
        val tops: List<Float>,
        val settledTop: Float,
        val dimmestFraction: Float,
        val dismissRequests: Int,
        val everHidden: Boolean,
    )

    private fun dragDown(dismissible: Boolean): Run {
        var state: SheetState? = null
        var body = Rect.Zero
        var dismissRequests = 0
        var everHidden = false
        var dimmest = 1f
        var visible by mutableStateOf(true)
        val tops = mutableListOf<Float>()
        var restingTop = 0f
        var settledTop = 0f

        Scene(width = 600, height = 900) {
            OverlayHost(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().background(Color.White))
                val sheet = rememberSheetState(
                    detents = listOf(SheetDetent.Hidden, SheetDetent.Expanded),
                    initialDetent = SheetDetent.Hidden,
                )
                state = sheet
                ModalBottomSheet(
                    visible = visible,
                    onDismissRequest = { dismissRequests++; visible = false },
                    state = sheet,
                    dismissible = dismissible,
                ) {
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
            scene.frames(80)
            val s = requireNotNull(state)
            restingTop = body.top

            // Slop first: a drag measured from the press point is measuring the
            // wrong distance.
            val grab = Offset(300f, body.top + 20f)
            scene.press(grab)
            scene.move(Offset(300f, grab.y + SlopPx))
            scene.frame()
            repeat(Steps) { step ->
                scene.move(Offset(300f, grab.y + SlopPx + Travel * (step + 1) / Steps))
                scene.frame()
                tops += body.top
                dimmest = minOf(dimmest, s.visibleFraction)
                if (s.currentDetent == SheetDetent.Hidden) everHidden = true
            }
            scene.release(Offset(300f, grab.y + SlopPx + Travel))
            repeat(12) {
                scene.frames(10)
                if (s.currentDetent == SheetDetent.Hidden) everHidden = true
            }
            settledTop = body.top
        }

        return Run(restingTop, tops, settledTop, dimmest, dismissRequests, everHidden)
    }

    private companion object {
        const val Steps = 20
        const val Travel = 400f
        const val SlopPx = 40f
    }
}
