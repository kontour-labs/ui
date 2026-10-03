package io.kontour.ui.catalog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.list.ListItemDefaults
import io.kontour.ui.demo.ComponentDemo
import io.kontour.ui.demo.DemoCard
import io.kontour.ui.demo.PullToRefreshDemo
import io.kontour.ui.demo.SelectionRowDemo
import io.kontour.ui.demo.SwipeActionsDemo
import io.kontour.ui.demo.TopBarDemo
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.KontourTheme
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The gallery's grouped rows sit a list item's gap apart: not touching, and not
 * spaced like separate cards.
 *
 * Reported as *"still a few places in your demo where you haven't properly used
 * the gap in list items"*. Grouped rows were stacked in a bare `Column` and ran
 * into each other, or spaced 8 or 12dp apart as if each were its own card.
 *
 * Read from the pixels, at a density of 1: a line down through the rows, just
 * inside their start edge where no text is, in the first row's own colour. Each
 * row is a run of that colour; the gaps between the runs are the gaps between
 * the rows.
 */
@OptIn(ExperimentalTestApi::class)
class DemoListGapTest {

    private fun ComposeUiTest.gaps(demo: ComponentDemo, firstRow: String): List<Int> {
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
                KontourTheme(darkTheme = false, reduceMotion = true) {
                    rowGap = ListItemDefaults.Spacing.value.toInt()
                    OverlayHost(Modifier.fillMaxSize()) {
                        Box(Modifier.width(400.dp)) { DemoCard(demo) }
                    }
                }
            }
        }
        waitForIdle()
        val text = onAllNodesWithText(firstRow, useUnmergedTree = true).fetchSemanticsNodes().first().boundsInRoot
        val image = onRoot().captureToImage().toPixelMap()
        // In the row's start padding, level with its text.
        val x = (text.left - 6f).toInt()
        val row = image[x, text.center.y.toInt()]
        // Up to where the first row begins, then down the rest.
        var y = text.center.y.toInt()
        while (y > 0 && image[x, y - 1].same(row)) y--
        val runs = mutableListOf<IntRange>()
        var start = -1
        val bottom = image.height
        while (y < bottom) {
            val on = image[x, y].same(row)
            if (on && start < 0) start = y
            if (!on && start >= 0) {
                runs += start until y
                start = -1
            }
            y++
        }
        // Rows only: a run shorter than a row is a glyph or a hairline.
        val rows = runs.filter { it.last - it.first > MinRow }
        return rows.zipWithNext { a, b -> b.first - a.last - 1 }
    }

    /** The theme's list item gap, in pixels at a density of 1. */
    private var rowGap = 0

    private fun Color.same(other: Color) =
        abs(red - other.red) < 0.01f && abs(green - other.green) < 0.01f && abs(blue - other.blue) < 0.01f

    private fun ComposeUiTest.assertGrouped(demo: ComponentDemo, firstRow: String, rows: Int) {
        val gaps = gaps(demo, firstRow)
        assertTrue(gaps.size >= rows - 1, "${demo.slug}: ${gaps.size + 1} rows found, the rest ran together into one: gaps $gaps")
        for (gap in gaps.take(rows - 1)) {
            assertEquals(rowGap.toFloat(), gap.toFloat(), 1f, "${demo.slug}: rows ${gap}dp apart, not a list item's ${rowGap}dp: $gaps")
        }
    }

    @Test
    fun selectionRowsInAGroup() = runComposeUiTest { assertGrouped(SelectionRowDemo, "Notify me about delays", rows = 3) }

    @Test
    fun swipeActionRows() = runComposeUiTest { assertGrouped(SwipeActionsDemo, "Perth Busport", rows = 4) }

    @Test
    fun pullToRefreshRows() = runComposeUiTest { assertGrouped(PullToRefreshDemo, "Perth Underground", rows = 3) }

    @Test
    fun rowsUnderATopBar() = runComposeUiTest { assertGrouped(TopBarDemo, "Departure 1", rows = 3) }

    private companion object {
        /** Taller than any glyph's stroke, shorter than any row. */
        const val MinRow = 24
    }
}
