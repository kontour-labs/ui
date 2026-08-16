package io.kontour.ui.components.datetime

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.kontour.ui.theme.KontourTheme
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Every cell of a range band is drawn the same height.
 *
 * The fill was `fillMaxWidth().aspectRatio(1f)`, so its height was its *width* —
 * and a cell's horizontal padding is deliberately conditional: 0dp in the middle
 * of a band so neighbours join up, 1dp at a cap, 2dp on its own. That turned an
 * intentional difference in width into an accidental one in height. Middles drew
 * a dp taller than the caps, and a single-day range shortest of all.
 *
 * **Measured off the rendered pixels, not the semantics tree.** The fill is a
 * sibling `Box` behind the label with no semantics of its own, so the only node a
 * test can reach is the cell — which was always uniform, and asserting on it
 * passes just as happily against the bug. A golden could have caught this in
 * principle and did not for a whole round: a one-dp step where two blocks of
 * colour meet sits under the comparison's tolerance.
 */
@OptIn(ExperimentalTestApi::class)
class RangeBandHeightTest {

    @Test
    fun everyCellOfABandIsDrawnTheSameHeight() {
        val runs = mutableListOf<Int>()

        runComposeUiTest {
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
                    KontourTheme(darkTheme = false, reduceMotion = true) {
                        Box(Modifier.testTag(Tag).width(350.dp).background(Color.White)) {
                            DateRangePicker(
                                // Two caps and four middles, all on one row.
                                start = LocalDate(2026, 6, 9),
                                end = LocalDate(2026, 6, 14),
                                onSelectedChange = { _, _ -> },
                                today = null,
                            )
                        }
                    }
                }
            }
            waitForIdle()

            val pixels = onNodeWithTag(Tag).captureToImage().toPixelMap()

            // The tallest vertical run of non-background pixels in a column. A
            // cell in the band is a solid block; a cell outside it is a glyph,
            // which is a few pixels tall at its widest and never close.
            fun runAt(x: Int): Int {
                var run = 0
                var best = 0
                for (y in 0 until pixels.height) {
                    if (pixels[x, y] != Color.White) run++ else run = 0
                    if (run > best) best = run
                }
                return best
            }

            // Sampled at each cell's *centre*, not across every column. The
            // band's two ends are rounded, so the columns through a cap taper —
            // measuring those would report a different height for every one of
            // them and say nothing about the bug.
            //
            // The grid's geometry is read off the band itself rather than
            // assumed: it spans exactly the six days of the range, so its extent
            // divided by six is a cell.
            val band = (0 until pixels.width).filter { runAt(it) >= BandFloor }
            if (band.isNotEmpty()) {
                val cell = (band.last() - band.first() + 1) / Days.toFloat()
                repeat(Days) { i ->
                    runs += runAt((band.first() + cell * (i + 0.5f)).toInt())
                }
            }
        }

        assertTrue(runs.isNotEmpty(), "no band was drawn at all")
        val distinct = runs.distinct().sorted()
        assertEquals(
            1,
            distinct.size,
            "the band is drawn at ${distinct.joinToString()} px in different " +
                "columns. A cell's fill height is following its width, and the " +
                "width is conditional on where the cell sits in the range",
        )
    }

    private companion object {
        const val Tag = "range"

        /** Above any glyph, below any fill, at this width and density. */
        const val BandFloor = 24

        /** 9th to 14th inclusive. */
        const val Days = 6
    }
}
