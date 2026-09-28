package io.kontour.ui.components.display

import androidx.compose.ui.unit.LayoutDirection
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * How a counter cuts its text into cells, and which way it reads them — in
 * common code, so it runs on every target and not only where the JVM's
 * character tables are.
 */
class CounterRunsTest {

    @Test
    fun eachDigitIsACellAndEachRunOfAnythingElseIsOne() {
        assertEquals(listOf(0 to "1", 1 to ",", 2 to "2", 3 to "0", 4 to "0", 5 to " min"), textRuns("1,200 min"))
        assertEquals(listOf(0 to "in ", 3 to "5"), textRuns("in 5"))
    }

    @Test
    fun nativeDigitsAreDigitsToo() {
        // Arabic-Indic digits roll one by one, as Western ones do.
        assertEquals(listOf(0 to "١", 1 to "٢", 2 to " د"), textRuns("١٢ د"))
    }

    @Test
    fun theFirstLetterDecidesTheDirection() {
        assertEquals(LayoutDirection.Rtl, directionOf("خلال ٥ دقائق"))
        assertEquals(LayoutDirection.Rtl, directionOf("5 דקות"))
        assertEquals(LayoutDirection.Ltr, directionOf("in 5 min"))
        assertEquals(LayoutDirection.Ltr, directionOf("5 分"))
    }

    @Test
    fun digitsAndPunctuationAloneReadLeftToRight() {
        assertEquals(LayoutDirection.Ltr, directionOf("١٢٣"))
        assertEquals(LayoutDirection.Ltr, directionOf("12:05"))
        assertEquals(LayoutDirection.Ltr, directionOf(""))
    }
}
