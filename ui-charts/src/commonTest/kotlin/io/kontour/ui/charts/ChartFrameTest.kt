package io.kontour.ui.charts

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/** The plot's edges eased between two layouts, as the data is between two charts. */
class ChartFrameTest {

    private val from = PlotInsets(left = 40f, top = 10f, right = 8f, bottom = 30f)
    private val to = PlotInsets(left = 60f, top = 20f, right = 8f, bottom = 30f)

    @Test
    fun nowhereToComeFromIsTheDestination() {
        assertSame(to, blendInsets(null, to, 0f))
    }

    @Test
    fun theEdgesMoveWithTheFraction() {
        assertEquals(40f, blendInsets(from, to, 0f).left)
        assertEquals(50f, blendInsets(from, to, 0.5f).left)
        assertEquals(15f, blendInsets(from, to, 0.5f).top)
        assertSame(to, blendInsets(from, to, 1f))
    }

    @Test
    fun aSpringThatOvershootsDoesNotCarryThePlotPastItsEnds() {
        assertEquals(40f, blendInsets(from, to, -0.2f).left)
        assertSame(to, blendInsets(from, to, 1.3f))
    }
}
