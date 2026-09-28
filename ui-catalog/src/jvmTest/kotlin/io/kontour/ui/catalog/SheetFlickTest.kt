package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import kotlin.test.assertEquals
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
 * Dragging the sheet itself — its handle, its surface — goes through the
 * sheet's own snapping fling instead, which the last two tests here cover.
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
    /**
     * A fling the list spent itself on does not close the sheet.
     *
     * The other half of the sensitivity report, and it has a mechanism rather than
     * a threshold behind it. A scrollable inside a sheet is a nested-scroll child:
     * Compose's fling behaviour cancels its decay on the first frame a delta is
     * not fully consumed and hands the *rest* of the velocity up, and this
     * connection declines anything that is not user input — so a list that runs
     * out of content mid-fling passes very nearly the whole throw to the sheet.
     * `WheelPicker`'s containment records the same leak and names the sheet as
     * where it went.
     *
     * So the list starts **scrolled down**. The same downward flick now scrolls
     * the list home first, reaches its top part way through, and hands the
     * remainder over — a remainder well above the flick floor, which is why a
     * velocity threshold alone does not cover this case. The reader was scrolling
     * a list; the sheet has to stay where it is.
     */
    @Test
    fun aFlingTheListSpentDoesNotCloseTheSheet() {
        assertTrue(
            !closedBy(pixelsPerFrame = FlickPerFrame, scrolledTo = ListStart),
            "a flick that scrolled the sheet's list home and then ran out of " +
                "content closed the sheet. The leftover velocity belongs to the " +
                "list — the reader was scrolling it, and what it could not spend " +
                "is not an instruction to take the sheet away.",
        )
    }

    /**
     * Scrolling a sheet's list up to its top and pulling on down, then letting go
     * with a flick, puts the sheet away — the way a sheet on an iPhone is closed.
     *
     * It used to be ignored: the list had moved in the gesture, so the release
     * was taken for the list's and the sheet was settled by position alone, and
     * sprang back from 120px down after a 3600px/s throw. Here the finger is
     * still on the sheet, moving it, when it lets go; the list's part was over.
     */
    @Test
    fun aFlickThatScrollsTheListHomeAndPullsOnClosesTheSheet() {
        assertTrue(
            closedBy(pixelsPerFrame = FlickPerFrame, scrolledTo = ListNearTop),
            "scrolling the list to its top and flicking on down left the sheet open",
        )
    }

    /**
     * A flick from the content carries on at the finger's speed, as a throw of the
     * handle always did, rather than stopping dead where the finger let go.
     *
     * Measured before: the sheet stood still for three frames after the release
     * and then eased away from rest — Foundation's `animateTo` starts from the
     * last velocity the drag recorded, and a sheet moved by its content records
     * none. Reported from an iPhone, where a sheet is flicked by its content far
     * more than by its handle, as closing a sheet feeling "like such an effort".
     */
    @Test
    fun aFlickFromTheListCarriesItsSpeedIntoTheSheet() {
        val track = releaseTrack(scrolledTo = 0)
        // The release frame and the next: the settle starts on the second, and
        // should be going at most of the finger's pace when it does — it moved
        // 0px here before, and about 49 after.
        val travel = track[2] - track[0]
        assertTrue(
            travel >= FlickPerFrame / 2,
            "two frames after a ${FlickPerFrame}px-a-frame flick was let go, the sheet had moved ${travel}px: $track",
        )
    }

    /**
     * A sheet thrown away gives the page back as it is let go, not when it has
     * landed: a tap on the page two frames later is the page's.
     */
    @Test
    fun aSheetThrownAwayGivesThePageBackAtOnce() {
        var taps = 0
        val track = releaseTrack(scrolledTo = 0, page = { taps++ }) { scene, sheet ->
            scene.frames(2)
            val landed = sheet.currentDetent == SheetDetent.Hidden
            scene.tap(Offset(300f, 40f))
            assertTrue(!landed, "the sheet had already landed, so this says nothing about letting go early")
        }
        assertEquals(1, taps, "a tap on the page just after the sheet was thrown away went to its scrim: $track")
    }

    /**
     * The sheet's offset each frame from the release of a flick of its list, the
     * release frame first. [afterRelease] runs straight after the release instead.
     */
    private fun releaseTrack(
        scrolledTo: Int,
        page: (() -> Unit)? = null,
        afterRelease: ((Scene, SheetState) -> Unit)? = null,
    ): List<Float> {
        var state: SheetState? = null
        var visible by mutableStateOf(true)
        val track = mutableListOf<Float>()
        Scene(width = 600, height = 900) {
            OverlayHost(Modifier.fillMaxSize()) {
                Box(
                    Modifier.fillMaxSize().background(Color.White)
                        .then(if (page != null) Modifier.clickable { page() } else Modifier)
                )
                val sheet = rememberSheetState(
                    detents = listOf(SheetDetent.Hidden, SheetDetent.Expanded),
                    initialDetent = SheetDetent.Expanded,
                )
                state = sheet
                ModalBottomSheet(visible = visible, onDismissRequest = { visible = false }, state = sheet) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(300.dp)
                            .verticalScroll(rememberScrollState(initial = scrolledTo))
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
            repeat(FlickSteps) { step ->
                scene.move(Offset(from.x, from.y + FlickPerFrame * (step + 1)))
                scene.frame()
            }
            scene.release(Offset(from.x, from.y + FlickPerFrame * FlickSteps))
            if (afterRelease != null) {
                afterRelease(scene, sheet)
            } else {
                repeat(6) {
                    track += sheet.offset
                    scene.frame()
                }
            }
        }
        return track
    }

    /**
     * One flick of the sheet itself closes it from full height, past a middle
     * detent — reported from a phone as taking "too much effort to flick closed".
     * It was Compose's own rule, one detent per flick however hard, so a sheet
     * with a half-way detent took two.
     */
    @Test
    fun oneHardFlickOfTheSheetClosesItPastTheMiddle() {
        assertEquals(SheetDetent.Hidden, flungFromFull(pixelsPerFrame = FlickPerFrame))
    }

    /** And not one that may not be put away by hand, however hard it is thrown. */
    @Test
    fun aHardFlickDoesNotCloseASheetThatMustBeAnswered() {
        assertEquals(SheetDetent.Half, flungFromFull(pixelsPerFrame = FlickPerFrame, dismissible = false))
    }

    /** A gentle flick of the sheet still moves it — to the next detent, not past it. */
    @Test
    fun aGentleFlickOfTheSheetGoesOneDetent() {
        assertEquals(SheetDetent.Half, flungFromFull(pixelsPerFrame = 8f))
    }

    /**
     * Where a sheet with a middle detent, at full height, settles after a flick
     * down its own surface — nothing scrollable in it, so the sheet's own drag
     * takes the gesture.
     */
    private fun flungFromFull(pixelsPerFrame: Float, dismissible: Boolean = true): SheetDetent {
        var state: SheetState? = null
        var settled: SheetDetent? = null
        var visible by mutableStateOf(true)
        Scene(width = 600, height = 900) {
            OverlayHost(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().background(Color.White))
                val sheet = rememberSheetState(
                    detents = listOf(SheetDetent.Hidden, SheetDetent.Half, SheetDetent.Expanded),
                    initialDetent = SheetDetent.Expanded,
                )
                state = sheet
                ModalBottomSheet(
                    visible = visible,
                    onDismissRequest = { visible = false },
                    state = sheet,
                    dismissible = dismissible,
                ) {
                    Box(Modifier.fillMaxWidth().height(600.dp).background(Color.LightGray))
                }
            }
        }.use { scene ->
            scene.frames(80)
            val sheet = requireNotNull(state)
            val from = Offset(300f, 500f)
            scene.press(from)
            repeat(FlickSteps) { step ->
                scene.move(Offset(from.x, from.y + pixelsPerFrame * (step + 1)))
                scene.frame()
            }
            scene.release(Offset(from.x, from.y + pixelsPerFrame * FlickSteps))
            repeat(12) {
                scene.frames(10)
                if (sheet.currentDetent == SheetDetent.Hidden || !visible) settled = SheetDetent.Hidden
            }
            if (settled == null) settled = sheet.currentDetent
        }
        return requireNotNull(settled)
    }

    private fun closedBy(
        pixelsPerFrame: Float,
        steps: Int = FlickSteps,
        scrolledTo: Int = 0,
    ): Boolean {
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
                            .verticalScroll(rememberScrollState(initial = scrolledTo))
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
         * **Eight, which is 450px/s, and it is back to eight deliberately.** It
         * was raised to twenty — 180px/s — because 450 closed the sheet: the
         * projection had no floor, so once the finger had carried the sheet 40% of
         * the way a nudge was enough to finish it. Detuning the control around
         * that hid the defect the reader then reported from a phone.
         *
         * So the control is a real slow drag again, and it is now the assertion
         * that the sheet's flick floor is doing its job: 450px/s is 225dp/s
         * at this scene's density, under the 500dp/s floor, so it gets no opinion
         * from the projection and settles by position. Reverting the floor fails
         * this test, which is the point of it.
         */
        const val CrawlRatio = 8
        const val CrawlPerFrame = FlickPerFrame / CrawlRatio

        /** Short, because a flick is short. The travel is the pace, not the count. */
        const val FlickSteps = 4

        /**
         * Far enough down the list that a downward flick has somewhere to go.
         *
         * The content is 900dp inside a 300dp viewport at 2x, so the scroll range
         * is 1200px. Starting at 300 leaves the list a few frames of travel — long
         * enough that the flick is unmistakably the list's, short enough that it
         * reaches the top well inside the fling and hands the remainder on, which
         * is the handoff under test.
         */
        const val ListStart = 300

        /** Close enough to the top that a flick scrolls the list home in its first frames and pulls the sheet with the rest. */
        const val ListNearTop = 100
    }
}
