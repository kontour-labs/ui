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
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A sheet let go and caught again on its way back is held by the finger that
 * caught it, and follows that finger from where it was caught.
 *
 * Reported: a full-height sheet pulled down a little and let go, then pulled
 * again before it had got back, went to the top anyway instead of following the
 * second pull. A throw of the sheet's own surface settles by a snapping fling,
 * which `AnchoredDraggableState.isAnimationRunning` does not count — so the
 * catch was not seen as one, the new finger had to travel the sheet's whole drag
 * slop before it moved anything, and the settle carried the sheet to the top in
 * the meantime. Caught from its list it was held, because the content's settle
 * is a targeted animation that Foundation does count.
 *
 * On the surface and on a list, because those are the two paths a sheet is
 * released by, and the bug was in the difference between them.
 */
class SheetCatchTest {

    @Test
    fun aSheetCaughtOnItsSurfaceIsHeldWhereItWasCaught() = held(onList = false)

    @Test
    fun aSheetCaughtOnItsListIsHeldWhereItWasCaught() = held(onList = true)

    @Test
    fun aSheetCaughtOnItsSurfaceFollowsTheSecondPull() = follows(onList = false)

    @Test
    fun aSheetCaughtOnItsListFollowsTheSecondPull() = follows(onList = true)

    /** Caught, and held still: the sheet stays where the finger caught it. */
    private fun held(onList: Boolean) = caught(onList) { scene, sheet, at ->
        val caughtAt = sheet.offset
        scene.press(at)
        scene.frames(10)
        val after = sheet.offset
        assertTrue(
            abs(after - caughtAt) <= Tolerance,
            "a finger laid on a settling sheet let it go on moving: ${caughtAt}px when caught, ${after}px ten frames later",
        )
        scene.release(at)
    }

    /** Caught, and pulled on down: the sheet follows the finger from where it was caught. */
    private fun follows(onList: Boolean) = caught(onList) { scene, sheet, at ->
        val caughtAt = sheet.offset
        scene.press(at)
        var y = at.y
        repeat(PullSteps) {
            y += PullPerFrame
            scene.move(Offset(at.x, y))
            scene.frame()
        }
        val pulled = sheet.offset - caughtAt
        // Less the slop a finger travels before a drag starts at all, which the
        // sheet does not follow — or none, if the catch held it at once.
        val travel = PullPerFrame * PullSteps
        assertTrue(
            pulled > travel - SlopAllowance && pulled < travel + Tolerance,
            "a sheet caught at ${caughtAt}px and pulled ${travel}px moved ${pulled}px",
        )
        scene.release(Offset(at.x, y))
    }

    /**
     * Opens a full-height sheet, pulls it down slowly and lets go, and hands [then]
     * the scene a couple of frames into the settle back, while the sheet is still
     * well short of the top.
     */
    private fun caught(onList: Boolean, then: (Scene, SheetState, Offset) -> Unit) {
        var state: SheetState? = null
        var visible by mutableStateOf(true)
        Scene(width = 600, height = 900) {
            OverlayHost(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().background(Color.White))
                val sheet = rememberSheetState(
                    detents = listOf(SheetDetent.Hidden, SheetDetent.Half, SheetDetent.Expanded),
                    initialDetent = SheetDetent.Expanded,
                )
                state = sheet
                ModalBottomSheet(visible = visible, onDismissRequest = { visible = false }, state = sheet) {
                    // As tall as the window, as a full-height sheet's content is.
                    val body = Modifier.fillMaxWidth().height(450.dp)
                    if (onList) {
                        Box(body.verticalScroll(rememberScrollState())) {
                            Box(Modifier.fillMaxWidth().height(1200.dp).background(Color.LightGray))
                        }
                    } else {
                        Box(body.background(Color.LightGray))
                    }
                }
            }
        }.use { scene ->
            scene.frames(80)
            val sheet = requireNotNull(state)
            val top = sheet.offset
            val from = Offset(300f, 500f)
            scene.press(from)
            var y = from.y
            repeat(PullSteps) {
                y += PullPerFrame
                scene.move(Offset(from.x, y))
                scene.frame()
            }
            scene.frames(4)
            scene.release(Offset(from.x, y))
            scene.frames(2)
            assertTrue(
                sheet.offset - top > PullPerFrame * PullSteps / 3,
                "the sheet was already back near the top (${sheet.offset}px against ${top}px) when it was to be caught",
            )
            then(scene, sheet, Offset(from.x, y))
        }
    }

    private companion object {
        /** 6px a frame at 16ms, about 190dp/s at this scene's density: a pull, not a flick. */
        const val PullPerFrame = 6f
        const val PullSteps = 30

        /** The sheet's drag slop at this density, and a little over. */
        const val SlopAllowance = 40f

        const val Tolerance = 3f
    }
}
