package io.kontour.ui.components.display

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The indeterminate chase: two bars, each end on its own curve, so they stretch as they go. */
class ChasingBarsTest {

    private fun barsAt(phase: Float): List<Pair<Float, Float>> =
        buildList { chasingBars(phase) { from, to -> add(from to to) } }

    @Test
    fun itStartsEmptyAndEndsEmpty() {
        assertEquals(emptyList(), barsAt(0f), "nothing at the start of the cycle")
        assertEquals(emptyList(), barsAt(1f), "and nothing at its end, so the loop has no seam")
    }

    @Test
    fun theFirstBarSweepsOutAndTheSecondChasesIt() {
        val early = barsAt(300f / ChaseCycle)
        assertEquals(1, early.size, "early on, one bar")
        assertEquals(0f, early[0].first, "its back has not left the start yet")
        val both = barsAt(1100f / ChaseCycle)
        assertEquals(2, both.size, "partway through, both bars: $both")
        assertTrue(both[1].second < both[0].first, "the second is behind the first: $both")
    }

    @Test
    fun theBarsStretchRatherThanSlideAtAFixedWidth() {
        val lengths = (1..17).map { barsAt(it / 18f).firstOrNull()?.let { (from, to) -> to - from } ?: 0f }
        val shown = lengths.filter { it > 0f }
        assertTrue(shown.max() - shown.min() > 0.2f, "a bar's length should change as it goes: $lengths")
    }
}
