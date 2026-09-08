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
import io.kontour.ui.sheet.BottomSheet
import io.kontour.ui.sheet.SheetDetent
import io.kontour.ui.sheet.SheetHeader
import io.kontour.ui.sheet.SheetState
import io.kontour.ui.sheet.rememberSheetState
import io.kontour.ui.sheet.sheetPeekAnchor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A sheet whose peek is measured settles where the finger left it.
 *
 * Reported twice: haul the sheet up past everything and let go, and instead of
 * staying open it drops all the way back down — *"snaps back to its lowest one,
 * but not closed"*.
 *
 * ### Why the round-25 sweep could not see it
 *
 * `SheetOvershootTest` already drags four different detent lists above their top
 * detent and checks where each lands, and one of the four has a peek. All four
 * pass, and all four still pass. The difference is how the peek is *arrived at*:
 * `SheetDetent.peek(120.dp)` is a number, resolved identically on every layout
 * pass, so the anchors are built once. The reporter's sheet uses
 * `Modifier.sheetPeekAnchor()`, where the peek means "as tall as this node" —
 * measured, and therefore able to change while a finger is on the sheet.
 *
 * The probe that separated them, dragging from the same place by the same
 * amount and changing one thing at a time:
 *
 * | Peek | Starts at | Drag | Landed |
 * |---|---|---|---|
 * | measured | peek | 790px | **peek** |
 * | fixed 140dp | peek | 790px | expanded |
 * | measured | expanded | 260px | expanded |
 * | fixed 140dp | expanded | 790px | expanded |
 *
 * One variable flips it. The crown row travelled 649 → 25 in the failing case,
 * so the sheet followed the finger the whole way up: it is not that the gesture
 * was lost, it is that the sheet's idea of where it was going never left the
 * detent it started in.
 *
 * ### What it was
 *
 * The peek used to be the distance between two positions in the root — the
 * anchor's bottom, and the sheet's top — reported by two separate
 * `onGloballyPositioned` callbacks. The anchor fires first, so it computed
 * against the sheet's top *from the previous frame*, which during a drag is a
 * whole frame of travel away rather than the sub-pixel jitter the code was
 * written to tolerate. A changed peek is a changed `AnchorInputs`, a changed
 * `AnchorInputs` rebuilds the anchors, and a rebuild pins the target back to
 * where the gesture began — once per frame, all the way up.
 *
 * The sheet's offset is applied above the node the sheet measures itself at, so
 * the fix is to measure the anchor in the sheet's own coordinates instead of the
 * root's. There the anchor does not move when the sheet does, the peek is a
 * measurement of the content and nothing else, and the anchors are not rebuilt
 * at all during a drag.
 */
class SheetPeekAnchorSettleTest {

    /**
     * The reporter's detent list, and the reporter's peek.
     *
     * Four detents with `Hidden` first, so "the detent it started in" and "the
     * detent it should reach" are as far apart as the list allows.
     */
    private val peek = SheetDetent.peek(140.dp)
    private val detents = listOf(SheetDetent.Hidden, peek, SheetDetent.Half, SheetDetent.Expanded)

    /**
     * Drags the sheet up from its peek and reports where it settles.
     *
     * @param measured Whether the peek comes from `sheetPeekAnchor` or from the
     *   140dp in the detent. The one variable the probe above changed.
     */
    private fun haulUp(measured: Boolean, travel: Float): Pair<SheetDetent, List<Int>> {
        var state: SheetState? = null
        var body = Rect.Zero
        val crowns = mutableListOf<Int>()

        // Phone-shaped, which is where this was seen and what the demo is drawn
        // for. The filler is taller than the window so `Expanded` is a real
        // detent rather than the same place as `Half`.
        Scene(width = 400, height = 850) {
            val sheet = rememberSheetState(detents = detents, initialDetent = peek)
            state = sheet
            Box(Modifier.fillMaxSize().background(Color.White)) {
                BottomSheet(state = sheet) {
                    SheetHeader(
                        modifier = if (measured) Modifier.sheetPeekAnchor() else Modifier,
                    ) { +"Perth Underground" }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(300.dp)
                            .background(Color.LightGray)
                            .reportBounds { body = it }
                    )
                }
            }
        }.use { scene ->
            scene.frames(20)
            val grab = Offset(200f, body.top - 40f)
            scene.drag(
                from = grab,
                to = Offset(200f, grab.y - travel),
                steps = 24,
                release = false,
            ) { _, image -> crowns += image.crownRow() }
            scene.release(Offset(200f, grab.y - travel))
            scene.frames(120)
        }

        return requireNotNull(state).currentDetent to crowns
    }

    @Test
    fun aSheetHauledUpPastEverythingStaysOpen() {
        val (landed, crowns) = haulUp(measured = true, travel = 790f)

        // The control for the assertion below. If the sheet never rose, "it did
        // not land on Expanded" would be true for a reason that has nothing to
        // do with anchors, and the test would be measuring a lost gesture.
        assertTrue(
            crowns.first() - crowns.last() > 400,
            "the sheet only rose ${crowns.first() - crowns.last()}px under a " +
                "790px drag, so this run says nothing about where it settles — " +
                "the crowns were $crowns",
        )

        assertEquals(
            SheetDetent.Expanded,
            landed,
            "hauled up past every detent and released, the sheet settled on " +
                "$landed. It followed the finger from crown ${crowns.first()} to " +
                "${crowns.last()}, so the gesture was never lost — the anchors " +
                "were being rebuilt under it, and every rebuild pins the target " +
                "back to the detent the drag started in.",
        )
    }

    @Test
    fun aFixedPeekBehavesTheSameWay() {
        // The other half of the probe, kept because it is what made the cause
        // findable: identical in every respect except that the peek is a number.
        // If these two ever disagree again, the difference is the measurement.
        val (landed, _) = haulUp(measured = false, travel = 790f)
        assertEquals(
            SheetDetent.Expanded,
            landed,
            "the same drag on a sheet with a fixed peek settled on $landed",
        )
    }

    @Test
    fun aShorterHaulStopsAtTheDetentItReached() {
        // Not every drag should reach the top, and a fix that pinned the target
        // forward instead of backward would pass the two tests above while
        // making the sheet impossible to leave half open.
        val (landed, crowns) = haulUp(measured = true, travel = 260f)
        assertEquals(
            SheetDetent.Half,
            landed,
            "a 260px haul left the crown at ${crowns.last()}, which is nearest " +
                "Half, but the sheet settled on $landed",
        )
    }
}
