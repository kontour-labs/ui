package io.kontour.ui.overlay

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A tooltip opens on a side where the whole of it fits, and scrolls only when
 * there is no such side.
 *
 * Asked for as *"make it so tooltips/coachmarks will never be scrollable,
 * unless there is absolutely no space on any side of the item"*. A tooltip
 * used to be bounded to the side asked for whenever that side had the 64dp
 * floor, and scroll the rest, with the whole of it fitting on the other side.
 */
@OptIn(ExperimentalTestApi::class)
class TooltipWholeTest {

    /** Asked for below, with too little room there and plenty above: above, whole. */
    @Test
    fun aTooltipTooTallForItsSideOpensWholeOnTheOther() {
        val (anchor, bubble) = open(
            window = 400.dp to 600.dp,
            anchorAt = Rect(160f, 480f, 240f, 510f),
            side = OverlaySide.Bottom,
        )
        assertTrue(bubble.bottom <= anchor.top, "the tooltip stayed below, bounded and scrolling: $bubble, anchor $anchor")
    }

    /**
     * A window too short for it above or below, and wide: beside the anchor,
     * on the side with room.
     */
    @Test
    fun aTooltipThatFitsNeitherAboveNorBelowOpensBeside() {
        val (anchor, bubble) = open(
            window = 600.dp to 140.dp,
            anchorAt = Rect(20f, 55f, 60f, 85f),
            side = OverlaySide.Top,
        )
        assertTrue(bubble.left >= anchor.right, "the tooltip did not open beside the anchor, where it fits: $bubble, anchor $anchor")
        assertTrue(bubble.top >= 0f && bubble.bottom <= 140f, "the tooltip beside the anchor ran off the window: $bubble")
    }

    /** Asked for above, and it fits there: above, as it always did. */
    @Test
    fun aTooltipThatFitsWhereItWasAskedStaysThere() {
        val (anchor, bubble) = open(
            window = 400.dp to 600.dp,
            anchorAt = Rect(160f, 480f, 240f, 510f),
            side = OverlaySide.Top,
        )
        assertTrue(bubble.bottom <= anchor.top, "a tooltip that fits above did not open above: $bubble, anchor $anchor")
    }

    /**
     * A tooltip pinned open at [anchorAt], in dp, in a [window] of its own, with
     * a paragraph in it: the anchor and the bubble, in dp.
     */
    private fun open(window: Pair<Dp, Dp>, anchorAt: Rect, side: OverlaySide): Pair<Rect, Rect> {
        var bubble: Rect? = null
        var scale = 1f
        runComposeUiTest {
            setContent {
                scale = LocalDensity.current.density
                KontourTheme {
                    Box(Modifier.size(window.first, window.second)) {
                        OverlayHost(Modifier.fillMaxSize()) {
                            TooltipOverlay(
                                visible = true,
                                anchor = {
                                    Rect(anchorAt.left * scale, anchorAt.top * scale, anchorAt.right * scale, anchorAt.bottom * scale)
                                },
                                content = { +Paragraph },
                                modifier = Modifier.onGloballyPositioned { bubble = it.boundsInRoot() },
                                side = side,
                                alignment = OverlayAlignment.Centre,
                                onDismissRequest = {},
                            )
                        }
                    }
                }
            }
            waitForIdle()
        }
        val placed = bubble ?: error("the tooltip never showed")
        return anchorAt to Rect(placed.left / scale, placed.top / scale, placed.right / scale, placed.bottom / scale)
    }

    private companion object {
        /** Five or six lines at a tooltip's width: about a hundred dp, which 60dp of room cannot hold. */
        const val Paragraph =
            "Departures update every thirty seconds while the stop is open, and the times shown " +
                "are live where the operator reports them. Scheduled times are marked with a clock, " +
                "and cancelled services are struck through."
    }
}
