package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.sheet.ModalBottomSheet
import io.kontour.ui.sheet.SheetDetent
import io.kontour.ui.sheet.SheetState
import io.kontour.ui.sheet.rememberSheetState
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A sheet can be flicked shut, and a slow drag of the same length cannot.
 *
 * Reported: a sheet with only two detents — full height and closed — could not
 * be closed by flicking however hard, and had to be hauled more than half way
 * down instead.
 *
 * ### Why the gesture is on the *content*
 *
 * Because that is where the defect was, and it is where every real flick starts.
 * A sheet with anything scrollable in it — a settings list, a form — hands its
 * gestures to `SheetState.nestedScrollConnection`, and that path threw the
 * velocity away twice over: `onPreFling` returned `Velocity.Zero` for anything
 * downward, and `onPostFling` then called the **positional** `settle`. So a
 * flick was finished as though the finger had stopped dead, and the only thing
 * that could close the sheet was crossing the 0.5 positional threshold.
 *
 * Dragging the *handle* goes somewhere else entirely — Compose's own
 * `AnchoredDraggableDefaults.flingBehavior` — which is unchanged and is not what
 * this covers. See `SheetState.settleWhereAimed` for why it stays that way.
 *
 * ### Both halves, because one of them is the whole risk
 *
 * A change that closes the sheet on any downward velocity is not "where the
 * flick was aimed", it is a threshold, and it would snatch the sheet away from a
 * reader who was scrolling its content and stopped. So the same travel is
 * performed slowly and has to leave the sheet open.
 */
class SheetFlickTest {

    @Test
    fun aHardFlickOnTheContentClosesTheSheet() {
        assertTrue(
            closedBy(pixelsPerFrame = FlickPerFrame),
            "flicking the sheet's content downward at about " +
                "${FlickPerFrame * FramesPerSecond}px/s left it open. A flick has " +
                "to be allowed to mean something a slow drag does not — the " +
                "alternative is that the only way to shut a sheet is to haul it " +
                "past the half-way mark, which is what was reported.",
        )
    }

    @Test
    fun aSlowDragOfTheSameLengthDoesNot() {
        assertTrue(
            !closedBy(pixelsPerFrame = CrawlPerFrame, steps = FlickSteps * CrawlRatio),
            "dragging the same distance slowly closed the sheet as well, so the " +
                "sheet is answering the distance and not the throw. A reader who " +
                "scrolls a sheet's content and stops has not asked for it to go " +
                "away.",
        )
    }

    /**
     * Drags the sheet's content down at a given pace and says whether it shut.
     *
     * The pace *is* the velocity: this scene advances its clock 16ms per frame,
     * so pixels-per-frame times sixty is pixels-per-second, and that is what
     * Compose's velocity tracker will read off the pointer events.
     */
    private fun closedBy(pixelsPerFrame: Float, steps: Int = FlickSteps): Boolean {
        var state: SheetState? = null
        var visible by mutableStateOf(true)
        var everHidden = false

        Scene(width = 600, height = 900) {
            OverlayHost(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().background(Color.White))
                val sheet = rememberSheetState(
                    detents = listOf(SheetDetent.Hidden, SheetDetent.Expanded),
                    initialDetent = SheetDetent.Expanded,
                )
                state = sheet
                ModalBottomSheet(
                    visible = visible,
                    onDismissRequest = { visible = false },
                    state = sheet,
                ) {
                    // Scrollable, which is what routes the gesture through the
                    // nested-scroll path rather than the sheet's own draggable.
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(300.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Box(Modifier.fillMaxWidth().height(900.dp).background(Color.LightGray))
                    }
                }
            }
        }.use { scene ->
            scene.frames(80)
            val sheet = requireNotNull(state)

            val from = Offset(300f, 600f)
            scene.press(from)
            repeat(steps) { step ->
                scene.move(Offset(from.x, from.y + pixelsPerFrame * (step + 1)))
                scene.frame()
                if (sheet.currentDetent == SheetDetent.Hidden) everHidden = true
            }
            scene.release(Offset(from.x, from.y + pixelsPerFrame * steps))

            repeat(12) {
                scene.frames(10)
                if (sheet.currentDetent == SheetDetent.Hidden) everHidden = true
            }
        }
        return everHidden
    }

    private companion object {
        /** This scene's clock, which is what turns a pace into a velocity. */
        const val FramesPerSecond = 60

        /** About 3600px/s — a brisk throw on a phone. */
        const val FlickPerFrame = 60f

        /**
         * The same travel spread over enough frames to be a haul, not a throw.
         *
         * Twenty, which is 180px/s. Eight was the first guess and it is not slow:
         * 450px/s still projects past the half-way mark once the finger has
         * already carried the sheet 40% of the way, so the sheet closed and the
         * control proved nothing. A crawl has to be a crawl.
         */
        const val CrawlRatio = 20
        const val CrawlPerFrame = FlickPerFrame / CrawlRatio

        /** Short, because a flick is short. The travel is the pace, not the count. */
        const val FlickSteps = 4
    }
}
