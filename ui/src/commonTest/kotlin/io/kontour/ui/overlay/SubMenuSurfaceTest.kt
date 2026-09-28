package io.kontour.ui.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kontour.ui.theme.KontourTheme
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * What a menu looks like with a submenu open in place, and as it closes.
 *
 * Reported from a phone: the menu under an open submenu "goes square and weird"
 * round its shadow, and tapping out of a submenu "snaps back into place at the
 * end of its animation".
 */
@OptIn(ExperimentalTestApi::class)
class SubMenuSurfaceTest {

    /**
     * The menu under an open submenu keeps its shadow. It was dimmed with a
     * layer's alpha, which draws it offscreen, cut to its own box — the shadow
     * round it went with the cut.
     */
    @Test
    fun theMenuUnderASubmenuKeepsItsShadow() = runComposeUiTest {
        open()
        val last = onNodeWithText("Hide").fetchSemanticsNode().boundsInRoot
        val panel = onNodeWithText("Sort by").fetchSemanticsNode().boundsInRoot
        val atRest = shadowBelow(last, panel)
        onNodeWithText("Group by").performTouchInput { click() }
        waitForIdle()
        val covered = shadowBelow(last, panel, drawnBack = true)
        assertTrue(atRest > 0.02f, "the menu at rest cast no shadow to measure ($atRest)")
        assertTrue(
            covered > atRest * 0.5f,
            "under an open submenu the menu's shadow went from $atRest to $covered below it — cut off at its box",
        )
    }

    /**
     * Going back, the submenu folds into its row and is gone without a jump:
     * the frame it is removed on changes the picture no more than a frame of
     * its folding did.
     */
    @Test
    fun closingASubmenuEndsWithoutASnap() = runComposeUiTest {
        open()
        onNodeWithText("Group by").performTouchInput { click() }
        waitForIdle()
        val row = onAllNodesWithText("Group by").fetchSemanticsNodes().first().boundsInRoot
        val region = Rect(row.left - 16f, row.top - 16f, row.right + 16f, row.bottom + 24f)
        mainClock.autoAdvance = false
        onAllNodesWithText("Group by").fetchSemanticsNodes()
        onNodeWithText("Group by").performTouchInput { click() }
        var previous = onRoot().captureToImage().toPixelMap()
        val steps = mutableListOf<Float>()
        var removalStep = -1f
        repeat(40) {
            mainClock.advanceTimeByFrame()
            val stillOpen = onAllNodesWithText("Route").fetchSemanticsNodes().isNotEmpty()
            val now = onRoot().captureToImage().toPixelMap()
            val step = difference(previous, now, region)
            if (!stillOpen && removalStep < 0f) removalStep = step else if (stillOpen) steps += step
            previous = now
        }
        mainClock.autoAdvance = true
        assertTrue(removalStep >= 0f, "the submenu never went")
        val folding = steps.maxOrNull() ?: 0f
        assertTrue(folding > 0f, "the submenu did not move as it closed")
        assertTrue(
            removalStep <= folding * 0.35f,
            "the frame the submenu was removed on changed the picture by $removalStep, against at most $folding " +
                "for a frame of its folding — it was dropped rather than arriving",
        )
    }

    /**
     * The darkest the page is just below the menu, down its middle: the shadow.
     * [drawnBack] allows for the menu drawing back a little under a submenu.
     */
    private fun ComposeUiTest.shadowBelow(last: Rect, first: Rect, drawnBack: Boolean = false): Float {
        val pixels = onRoot().captureToImage().toPixelMap()
        val centreY = (first.top + last.bottom) / 2f
        val bottom = if (drawnBack) centreY + (last.bottom - centreY) * 0.97f else last.bottom
        val x = last.center.x.toInt()
        var darkest = 0f
        for (y in (bottom + 4f).toInt()..(bottom + 14f).toInt()) {
            val c = pixels[x, y]
            darkest = maxOf(darkest, 1f - (c.red + c.green + c.blue) / 3f)
        }
        return darkest
    }

    private fun difference(a: PixelMap, b: PixelMap, region: Rect): Float {
        var total = 0f
        for (y in region.top.toInt().coerceAtLeast(0) until region.bottom.toInt().coerceAtMost(a.height)) {
            for (x in region.left.toInt().coerceAtLeast(0) until region.right.toInt().coerceAtMost(a.width)) {
                val p = a[x, y]
                val q = b[x, y]
                total += abs(p.red - q.red) + abs(p.green - q.green) + abs(p.blue - q.blue)
            }
        }
        return total
    }

    private fun ComposeUiTest.open() {
        setContent {
            KontourTheme(darkTheme = false, reduceMotion = false) {
                OverlayHost(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().background(Color.White).padding(24.dp)) {
                        // A small anchor near the top, so the menu opens below it
                        // with page all round it for its shadow to fall on.
                        Box(Modifier.size(40.dp)) {
                        DropdownMenu(visible = true, onDismissRequest = {}) {
                            item("Sort by") {}
                            submenu("Group by") {
                                item("Route") {}
                                item("Stop") {}
                            }
                            item("Share") {}
                            item("Copy") {}
                            item("Report") {}
                            item("Hide") {}
                        }
                        }
                    }
                }
            }
        }
        waitForIdle()
    }
}
