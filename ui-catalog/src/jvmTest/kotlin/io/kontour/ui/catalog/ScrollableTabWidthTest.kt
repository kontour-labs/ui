package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import io.kontour.ui.nav.Tab
import io.kontour.ui.nav.TabBar
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A scrollable tab is as wide as its label.
 *
 * A weight inside an unbounded row is not a share of anything: Compose measures
 * weighted children against `mainAxisMin` when `mainAxisMax` is
 * `Constraints.Infinity`, and that minimum is zero. `Tab` wrapped its label in a
 * weighted row so that a badge would keep its size while the label gave way —
 * correct in a fixed bar, which divides a known width — and a scrollable bar
 * puts a `horizontalScroll` around its tabs, which is exactly an unbounded row.
 *
 * So every label measured to nothing and every tab came out as its own padding
 * with a gap between. Reported as "when scrollable, content bunches up on the
 * left and looks terrible", which is a generous description of three empty stubs
 * where the words should be.
 *
 * Measured rather than photographed: the width is the whole of the defect, and a
 * tab exactly `2 × spacing.md` wide is one with no label in it.
 */
class ScrollableTabWidthTest {

    @Test
    fun aScrollableTabIsWiderThanItsOwnPadding() {
        val scrollable = firstTabWidth(scrollable = true)
        val fixed = firstTabWidth(scrollable = false)

        assertTrue(fixed > 0f, "the fixed bar drew nothing either — this measures nothing")
        assertTrue(
            scrollable > PaddingPx + LabelFloor,
            "the first tab of a scrollable bar is ${scrollable}px wide against " +
                "${PaddingPx}px of its own padding. There is no label between " +
                "them: the weighted row that holds it measured to zero under the " +
                "scroll container's unbounded width.",
        )
    }

    /**
     * And it keeps the whole word.
     *
     * The other half of the same fix, and the reason a scrollable bar exists: a
     * fixed bar divides its width evenly and ellipsises what does not fit, and
     * a scrolling one is what you reach for when that is not acceptable. Two
     * long labels in a 700px window, so the fixed bar has to truncate and the
     * scrollable one must not.
     */
    @Test
    fun aScrollableBarDoesNotTruncateWhereAFixedOneMust() {
        val scrollable = firstTabWidth(scrollable = true, long = true)
        val fixed = firstTabWidth(scrollable = false, long = true)

        assertTrue(
            scrollable > fixed,
            "the first tab is ${scrollable}px scrollable and ${fixed}px fixed. " +
                "A scrollable bar is not dividing a width, so its tabs should be " +
                "wider than a fixed bar's, not the same or narrower.",
        )
    }

    private fun firstTabWidth(scrollable: Boolean, long: Boolean = false): Float {
        var first = Rect.Zero
        Scene(width = 700, height = 200) {
            Box(Modifier.fillMaxSize().background(Color.White)) {
                TabBar(scrollable = scrollable) {
                    Tab(
                        selected = true,
                        onClick = {},
                        key = 0,
                        modifier = Modifier.reportBounds { first = it },
                    ) { +if (long) "Departures and arrivals" else "Departures" }
                    Tab(selected = false, onClick = {}, key = 1) {
                        +if (long) "Route map and stops" else "Route map"
                    }
                    Tab(selected = false, onClick = {}, key = 2) { +"Alerts" }
                }
            }
        }.use { it.frames(20) }
        return first.width
    }

    private companion object {
        /** `Theme.spacing.md` either side, at this scene's density. */
        const val PaddingPx = 64f

        /**
         * The narrowest a label may be and still be one.
         *
         * "Departures" at `labelLarge` is well over a hundred pixels here; forty
         * is enough to be sure something was laid out without pinning the type
         * metrics.
         */
        const val LabelFloor = 40f
    }
}
