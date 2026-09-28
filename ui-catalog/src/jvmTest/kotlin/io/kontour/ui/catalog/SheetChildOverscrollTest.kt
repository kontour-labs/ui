package io.kontour.ui.catalog

import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.OverscrollEffect
import androidx.compose.foundation.OverscrollFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import io.kontour.ui.sheet.BottomSheet
import io.kontour.ui.sheet.SheetDetent
import io.kontour.ui.sheet.rememberSheetState
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A list inside a sheet does not rubber-band vertically. The sheet does.
 *
 * Reported from an iPhone, as two symptoms of one cause: *"i feel like i have to
 * flick with more power on iOS"*, and *"sometimes when i start dragging the sheet
 * down with a scrolling list, it instead just tries to pull the content down,
 * which is not correct"*. On Android the same sheet was reported as working.
 *
 * What differs is the [OverscrollEffect] each platform hands a scrollable —
 * `null` on desktop, an edge stretch on Android, `CupertinoOverscrollEffect` on
 * iOS — and where that effect sits. Foundation's `ScrollingLogic` wraps the whole
 * scroll in it:
 *
 * ```
 * overscroll.applyToScroll(delta) { parent.onPreScroll → own scrollBy → parent.onPostScroll }
 * ```
 *
 * so an effect with a band already open takes the finger *before* the sheet is
 * offered anything, and `onScrollStopped` hands it the release through
 * `applyToFling`, where it can keep velocity for its own spring. Both symptoms
 * fall out of that, and the *sometimes* is the band: whether one is open depends
 * on what the gesture did a moment earlier. See `SheetChildOverscroll`.
 *
 * ### This is a simulation, and says so
 *
 * On the JVM `defaultOverscrollFactory` returns `null` unconditionally — there is
 * no child overscroll on desktop at all, which is why desktop behaves the way the
 * report says Android does, and why this defect cannot be reproduced here without
 * standing something in for it. So [Band] is provided as the platform's factory
 * at the top of the scene, in the house pattern of `NarrowGestureTest`'s `Harness`
 * and `PhoneWidthTest` — no test process in this repository loads an iOS actual,
 * and the arithmetic is what is being checked.
 *
 * [Band] models the one behaviour that matters and not the whole Cupertino curve:
 * an open band takes the finger first, and a declined delta opens one.
 */
class SheetChildOverscrollTest {

    /**
     * The rule itself: the vertical axis never reaches the platform's effect.
     *
     * Asserted rather than inferred from a position, because it is what closes
     * *every* route into the defect at once — whichever gesture charges the
     * band on a given platform, a band that is never offered a pixel cannot
     * open, cannot withhold the next frame's delta, and has no velocity to keep
     * at the release.
     */
    @Test
    fun aScrollableInASheetIsOfferedNoVerticalOverscroll() {
        val band = Band()
        var list = Rect.Zero

        scene(band) { list = it }.use { scene ->
            scene.frames(40)
            assertTrue(list.height > 0f, "the list never reported a size")

            // Downward, from the top of a list that has nowhere further up to
            // go: the handoff the docs promise, and the gesture in the report.
            scene.drag(from = list.alongY(0.4f), to = list.alongY(0.4f) + Offset(0f, 160f))
            scene.frames(30)
        }

        assertEquals(
            emptyList(),
            band.verticalOffers,
            "a list inside a draggable sheet was offered ${band.verticalOffers.size} " +
                "vertical deltas for its own overscroll: ${band.verticalOffers}. The " +
                "sheet and the list share that axis and only one of them can answer " +
                "a finger that has run out of list — on iOS the list's effect " +
                "answered first, and the content pulled down instead of the sheet " +
                "moving",
        )
    }

    /**
     * And the consequence, with the band in the state the platform gets it into.
     *
     * Charged by hand rather than by a gesture. What charges it differs per
     * platform — a list flicked to its end, a spring still unwinding, the
     * leftover of a stretch the sheet only partly absorbed — and the fix is not
     * about which: it is that a charged band can no longer be in the way. So the
     * test puts the band where those gestures put it and asks the one question
     * the report asks, which is whether the sheet moves.
     */
    @Test
    fun andASheetStillMovesWithAChargedBandInTheWay() {
        val band = Band()
        var list = Rect.Zero
        var sheetTop = 0f
        var movedBy = 0f

        scene(band) { list = it }.use { scene ->
            scene.frames(40)
            assertTrue(list.height > 0f, "the list never reported a size")
            sheetTop = list.top

            // As if the list had just been flicked to its end and the platform's
            // band were still open. Negative, because it was opened by a finger
            // travelling up.
            band.offset = -Charge

            val grab = list.alongY(0.4f)
            scene.press(grab)
            scene.move(grab + Offset(0f, SlopPx))
            scene.frame()
            repeat(Steps) { step ->
                scene.move(grab + Offset(0f, SlopPx + Travel * (step + 1) / Steps))
                scene.frame()
            }
            movedBy = list.top - sheetTop
            scene.release(grab + Offset(0f, SlopPx + Travel))
            scene.frames(60)
        }

        assertTrue(
            movedBy > Travel / 2f,
            "dragged ${Travel.toInt()}px down by its list, the sheet moved " +
                "${movedBy.toInt()}px. A band the list's own overscroll had left " +
                "open unwinds against the same finger, and the frames it spends " +
                "doing that are frames the sheet is offered nothing at all",
        )
    }

    /**
     * Sideways is the platform's, and stays the platform's.
     *
     * A sheet has no opinion about horizontal scroll, so taking a carousel's
     * bounce away inside one would be a loss with nothing gained: there is no
     * second thing waiting to answer that axis. This passes before the change as
     * well as after it — it is the half that must not move.
     */
    @Test
    fun aHorizontalScrollableInASheetKeepsItsOverscroll() {
        val band = Band()
        var row = Rect.Zero

        Scene(width = 600, height = 900) {
            CompositionLocalProvider(LocalOverscrollFactory provides BandFactory(band)) {
                Box(Modifier.fillMaxSize().background(Color.White)) {
                    val sheet = rememberSheetState(
                        detents = listOf(SheetDetent.Hidden, SheetDetent.Expanded),
                        initialDetent = SheetDetent.Expanded,
                    )
                    BottomSheet(state = sheet) {
                        LazyRow(
                            Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .reportBounds { row = it }
                        ) {
                            items(12) {
                                Box(Modifier.width(120.dp).height(200.dp).background(Color.LightGray))
                            }
                        }
                    }
                }
            }
        }.use { scene ->
            scene.frames(40)
            assertTrue(row.width > 0f, "the row never reported a size")

            // Rightward from the start of the row, which is as far left as it goes.
            scene.drag(from = row.center, to = row.center + Offset(200f, 0f))
            scene.frames(30)
        }

        assertTrue(
            band.horizontalOffers.isNotEmpty(),
            "a horizontal list inside a sheet was offered no overscroll at all. " +
                "The sheet cannot answer that axis, so a list that stops dead on " +
                "it is the rigid boundary this library spends a primitive avoiding",
        )
    }

    private fun scene(band: Band, reportList: (Rect) -> Unit): Scene =
        Scene(width = 600, height = 900) {
            CompositionLocalProvider(LocalOverscrollFactory provides BandFactory(band)) {
                Box(Modifier.fillMaxSize().background(Color.White)) {
                    val sheet = rememberSheetState(
                        detents = listOf(SheetDetent.Hidden, SheetDetent.Expanded),
                        initialDetent = SheetDetent.Expanded,
                    )
                    BottomSheet(state = sheet) {
                        LazyColumn(
                            Modifier
                                .fillMaxWidth()
                                .height(300.dp)
                                .reportBounds(reportList)
                        ) {
                            items(20) {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(60.dp)
                                        .background(Color.LightGray)
                                )
                            }
                        }
                    }
                }
            }
        }

    /**
     * A stand-in for whatever the platform would have installed.
     *
     * Two behaviours, which are the two this fix is about: an **open band takes
     * the finger first**, before the scroll or anything nested above it is
     * offered a pixel — `CupertinoOverscrollEffect` does this in `availableDelta`
     * — and **a declined delta opens one**, which is an overscroll effect's whole
     * purpose.
     *
     * It deliberately does not spring back, so a band charged in one gesture is
     * still charged in the next and the test can see what that costs. And it
     * draws nothing: its node is empty, because what is being measured is who was
     * offered the finger, not what the pixels did with it.
     */
    private class Band : OverscrollEffect {

        /** Signed the way the finger that opened it was: positive is downward. */
        var offset: Float = 0f

        val verticalOffers = mutableListOf<Float>()
        val horizontalOffers = mutableListOf<Float>()

        override val isInProgress: Boolean get() = offset != 0f

        override val node: DelegatableNode = object : Modifier.Node() {}

        override fun applyToScroll(
            delta: Offset,
            source: NestedScrollSource,
            performScroll: (Offset) -> Offset,
        ): Offset {
            if (delta.y != 0f) verticalOffers += delta.y
            if (delta.x != 0f) horizontalOffers += delta.x

            // Unwinding an open band, which is the state that puts this in the
            // way of the sheet. Consumes the whole delta while it lasts.
            if (offset != 0f && (offset > 0f) != (delta.y > 0f) && delta.y != 0f) {
                val paid = minOf(abs(offset), abs(delta.y))
                offset += if (offset > 0f) -paid else paid
                return delta
            }

            val consumed = performScroll(delta)
            val declined = delta.y - consumed.y
            if (declined != 0f) offset += declined
            return Offset(consumed.x, delta.y)
        }

        override suspend fun applyToFling(
            velocity: Velocity,
            performFling: suspend (Velocity) -> Velocity,
        ) {
            performFling(velocity)
        }
    }

    /**
     * The factory the scene provides, handing out one [Band] the test can read.
     *
     * A `data class` because [OverscrollFactory] declares `equals` and `hashCode`
     * abstract, and a scrollable's node re-reads the local through
     * `onObservedReadsChanged` — an unequal factory on every read would rebuild
     * the effect mid-gesture and lose the band.
     */
    private data class BandFactory(val band: Band) : OverscrollFactory {
        override fun createOverscrollEffect(): OverscrollEffect = band
    }

    private companion object {
        const val Steps = 20
        const val Travel = 200f
        const val SlopPx = 40f

        /** More than [Travel], so an unwinding band swallows the whole drag. */
        const val Charge = 400f
    }
}
