package io.kontour.ui.charts

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * A box of samples has the numbers a spreadsheet and R give the same samples,
 * so a chart agrees with the analyst's sheet beside it.
 */
class BoxStatisticsTest {

    @Test
    fun quartilesAreInterpolatedAsASpreadsheetsAre() {
        // QUARTILE.INC and R's quantile(type = 7) of 1..10: 3.25, 5.5, 7.75.
        val box = boxOf(DoubleArray(10) { it + 1.0 })!!
        assertEquals(3.25, box.lowerQuartile, 1e-12)
        assertEquals(5.5, box.median, 1e-12)
        assertEquals(7.75, box.upperQuartile, 1e-12)
        assertEquals(1.0, box.low)
        assertEquals(10.0, box.high)
        assertEquals(0, box.outliers.size)
    }

    @Test
    fun aSampleFarPastTheBoxIsAnOutlierAndTheWhiskerStopsShortOfIt() {
        val box = boxOf(doubleArrayOf(41.0, 10.0, 12.0, 11.0, 13.0, 14.0, 12.0, 3.0))!!
        // The box is 11 to 13.25, so a whisker reaches 3.375 past it either way.
        assertEquals(10.0, box.low, "the whisker should end at the least sample inside the fence")
        assertEquals(14.0, box.high)
        assertContentEquals(doubleArrayOf(3.0, 41.0), box.outliers)
    }

    @Test
    fun oneSampleIsABoxOfNothingButIsStillDrawn() {
        val box = boxOf(doubleArrayOf(7.0))!!
        assertEquals(7.0, box.low)
        assertEquals(7.0, box.lowerQuartile)
        assertEquals(7.0, box.median)
        assertEquals(7.0, box.upperQuartile)
        assertEquals(7.0, box.high)
    }

    @Test
    fun missingSamplesAreLeftOutAndNoneIsNoBox() {
        assertEquals(2.0, boxOf(doubleArrayOf(Double.NaN, 1.0, 2.0, 3.0))!!.median)
        assertNull(boxOf(DoubleArray(0)))
        assertNull(boxOf(doubleArrayOf(Double.NaN)))
    }
}
