package io.kontour.ui.charts

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.kontour.ui.theme.KontourTheme
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The plot's edge eases with the data when the axis's labels grow, rather than
 * jumping.
 *
 * Reported from the gallery: new data that took the axis from "100" to
 * "1,200" moved the whole plot across in one frame, while the bars were still
 * easing to their new heights. Watched frame by frame at full motion, the
 * plot's start found by the baseline, drawn in a colour nothing else is.
 */
@OptIn(ExperimentalTestApi::class)
class ChartGutterMotionTest {

    private val small = listOf("Mon" to 40, "Tue" to 90, "Wed" to 60)
    private val large = listOf("Mon" to 400, "Tue" to 1_200, "Wed" to 600)

    @Test
    fun theEdgeEasesAcrossWhenTheLabelsGrowADigit() = edgeMoves(LayoutDirection.Ltr, reduceMotion = false)

    @Test
    fun theEdgeEasesAcrossRightToLeft() = edgeMoves(LayoutDirection.Rtl, reduceMotion = false)

    @Test
    fun underReducedMotionTheEdgeGoesWithTheData() = edgeMoves(LayoutDirection.Ltr, reduceMotion = true)

    private fun edgeMoves(direction: LayoutDirection, reduceMotion: Boolean) = runComposeUiTest {
        var grown by mutableStateOf(false)
        mainClock.autoAdvance = false
        setContent {
            KontourTheme(reduceMotion = reduceMotion) {
                CompositionLocalProvider(LocalLayoutDirection provides direction) {
                    Chart(
                        xAxis = ChartAxis.category<String>(),
                        yAxis = ChartAxis.number(),
                        modifier = Modifier.size(320.dp, 200.dp),
                        colours = ChartDefaults.colours(series = listOf(Bar), grid = Color.Transparent, axis = Probe),
                        showLegend = false,
                        showPatterns = false,
                    ) {
                        bar(if (grown) large else small, x = { it.first }, y = { it.second })
                    }
                }
            }
        }
        settle()
        val before = edge(direction)
        grown = true
        val edges = List(Frames) {
            mainClock.advanceTimeByFrame()
            edge(direction)
        }
        settle()
        val after = edge(direction)
        val distance = abs(after - before)
        assertTrue(distance >= 4, "the labels did not widen the gutter: $before to $after")

        if (reduceMotion) {
            // No easing: the edge is where it settles within the frame the
            // move is taken up in.
            assertEquals(after, edges[1], "under reduced motion the edge lagged: $edges")
            return@runComposeUiTest
        }
        assertTrue(abs(edges.first() - before) <= 1, "the edge jumped on the first frame: $before, then $edges")
        // The gutter grows, so the start edge moves away from the axis side.
        val sign = if (direction == LayoutDirection.Ltr) 1 else -1
        (listOf(before) + edges).zipWithNext().forEach { (a, b) ->
            assertTrue((b - a) * sign >= -1, "the edge went back on itself: $before, then $edges")
            assertTrue(abs(b - a) <= distance * MaxStep + 1, "one frame moved ${abs(b - a)} of $distance px: $edges")
        }
    }

    private fun ComposeUiTest.settle() {
        repeat(SettleFrames) { mainClock.advanceTimeByFrame() }
        waitForIdle()
    }

    /** The plot's start, left or right: the outermost pixel of the baseline. */
    private fun ComposeUiTest.edge(direction: LayoutDirection): Int {
        val pixels = onRoot().captureToImage().toPixelMap()
        var found: Int? = null
        for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
            val c = pixels[x, y]
            if (c.red > 0.8f && c.green < 0.2f && c.blue > 0.8f) {
                val was = found ?: x
                found = if (direction == LayoutDirection.Ltr) minOf(was, x) else maxOf(was, x)
            }
        }
        return checkNotNull(found) { "no baseline was drawn" }
    }

    private companion object {
        val Probe = Color(0xFFFF00FF)
        val Bar = Color(0xFF2060C0)
        const val Frames = 40
        const val SettleFrames = 90
        const val MaxStep = 0.4f
    }
}
