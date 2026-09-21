package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.overlay.OverlaySide
import io.kontour.ui.overlay.Popover
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A popover asked to open below its anchor opens below it.
 *
 * Reported from a phone: *"setting it to 'bottom' on android doesn't seem to make it
 * not show on the top side"*. `AnchoringTest` holds the arithmetic; this holds the
 * wiring, which is the half the arithmetic could not see — the content was measured
 * against the whole host before anyone asked where it was going, so a tall panel
 * reported "does not fit below" whatever was below it.
 *
 * Nothing here is platform-specific, and neither was the defect. A phone is simply
 * where the numbers bite: the window is half a desktop's height and every touch
 * target in it is twice as tall.
 *
 * ### Why the anchor sits above centre
 *
 * So the outcome is not a coin toss. `Below` is kept when neither side fits only if
 * it is the roomier one, and an anchor in the middle of the scene makes the two
 * sides equal to within a few pixels. At 200dp down a 600px scene there is about
 * 300px below the anchor against 180 above it, so `Below` is the side under test
 * rather than the side that happened to win.
 */
class PopoverSideTest {

    @Test
    fun aTallPopoverAskedForBelowIsDrawnBelow() {
        var anchor = Rect.Zero
        var panel = Rect.Zero

        Scene(width = 400, height = 600) {
            OverlayHost(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().background(Color.White)) {
                    Box(
                        Modifier
                            .offset(y = 100.dp)
                            .size(60.dp)
                            .reportBounds { anchor = it }
                    ) {
                        Popover(
                            visible = true,
                            onDismissRequest = {},
                            side = OverlaySide.Bottom,
                            // The **panel**, not its content. Since the panel scrolls,
                            // content taller than the room keeps its own full height
                            // inside a viewport that does not — measuring the child
                            // measures the thing that is supposed to overflow.
                            modifier = Modifier.reportBounds { panel = it },
                        ) {
                            // Taller than the room below, which is the whole point:
                            // the panel has to be *bounded* to that room rather than
                            // measured against the window and then moved to fit.
                            Box(Modifier.fillMaxWidth().height(400.dp))
                        }
                    }
                }
            }
        }.use { scene -> scene.frames(40) }

        assertTrue(anchor.height > 0f, "the anchor never reported a size")
        assertTrue(panel.height > 0f, "the popover never reported a size")
        assertTrue(
            panel.top >= anchor.bottom,
            "the popover was asked for `Bottom` and its content starts at " +
                "${panel.top}, above the anchor's bottom edge at ${anchor.bottom}",
        )
        assertTrue(
            panel.bottom <= 600f,
            "the panel runs to ${panel.bottom} in a 600px scene — it was not " +
                "budgeted to the room it is being placed in",
        )
        assertTrue(
            panel.height < 400.dp.value * 2,
            "the panel is ${panel.height}px against content asking for 800px, so " +
                "it was bounded — and `PopoverPanel` scrolls, so nothing is lost by " +
                "that. Bounding without scrolling is how this cut a reader's " +
                "content off the first time.",
        )
    }
    /**
     * The report, at the size it was reported on.
     *
     * A Pixel is 411x914dp, and the popover demo's own content — a title and a line
     * and a half — makes a panel about 88dp tall. A trigger 130dp from the bottom
     * edge leaves about 90dp under it, which would hold the panel except that the
     * margin, the gap and the arrow want twenty more than there are. So it flipped,
     * for the sake of twenty pixels, and *"setting it to 'bottom' on android doesn't
     * seem to make it not show on the top side"* is what that looks like from a
     * phone.
     *
     * Bounded to its own side and able to scroll, it opens below and the last sliver
     * scrolls. Measured here at 130% type as well, because a larger type size is the
     * difference between a phone that flips and one that does not — and is the most
     * likely reason this showed up on Android against an iPhone that behaved.
     */
    @Test
    fun aPopoverNearTheBottomEdgeStillOpensBelow() {
        for (scale in listOf(1f, 1.3f)) {
            var anchor = Rect.Zero
            var panel = Rect.Zero

            Scene(width = 1080, height = 2400, density = 2.625f, reduceMotion = true) {
                OverlayHost(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().background(Color.White)) {
                        Box(
                            Modifier
                                .align(Alignment.TopCenter)
                                .offset(y = 780.dp)
                                .size(44.dp)
                                .reportBounds { anchor = it }
                        ) {
                            Popover(
                                visible = true,
                                onDismissRequest = {},
                                side = OverlaySide.Bottom,
                            ) {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(88.dp)
                                        .reportBounds { panel = it }
                                )
                            }
                        }
                    }
                }
            }.use { scene ->
                scene.typeScale(scale)
                scene.frames(30)
            }

            assertTrue(
                panel.top >= anchor.bottom,
                "at ${(scale * 100).toInt()}% type a popover 130dp from the bottom " +
                    "edge was asked for `Bottom` and drawn at ${panel.top}, above " +
                    "the anchor's bottom edge at ${anchor.bottom}",
            )
        }
    }
}
