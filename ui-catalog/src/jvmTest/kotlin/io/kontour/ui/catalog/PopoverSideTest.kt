package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
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
                        Popover(visible = true, onDismissRequest = {}, side = OverlaySide.Bottom) {
                            // Taller than the room below, which is the whole point:
                            // the panel has to be *bounded* to that room rather than
                            // measured against the window and then moved to fit.
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(400.dp)
                                    .reportBounds { panel = it }
                            )
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
    }
}
