package io.kontour.ui.components.datetime

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import io.kontour.ui.theme.KontourTheme
import io.kontour.ui.theme.Theme
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The arrow on a month's first or last day, while a range is dragged to it,
 * fits beside the day's number inside the day.
 *
 * Reported once the calendar shrank: *"in the range picker, the little
 * "next/previous month arrow" is now too big"*. The ring was a fixed 6.5dp,
 * sized when a dragged day was a 48dp pill; in a day 40dp across it sat
 * against the number, a "31" and a ring crowded into one circle.
 *
 * Measured against the widest two-digit day the calendar draws, at a density
 * of 1, for a phone's day and a narrow card's.
 */
@OptIn(ExperimentalTestApi::class)
class EdgeArrowSizeTest {

    @Test
    fun theArrowSitsBesideATwoDigitDayAndInsideIt() = runComposeUiTest {
        var number = 0f
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
                KontourTheme {
                    val measurer = rememberTextMeasurer()
                    // Today's number is in the bolder label style.
                    number = maxOf(
                        measurer.measure("30", Theme.typography.bodyMedium).size.width,
                        measurer.measure("31", Theme.typography.labelMedium).size.width,
                    ).toFloat()
                }
            }
        }
        waitForIdle()
        assertTrue(number > 0f, "nothing was measured")

        // Half a phone's 40dp day, and half a day in a 256dp card.
        for (halfCell in listOf(PhoneHalfDay, CardHalfDay)) {
            val (out, radius) = with(Density(1f)) { arrowRing(halfCell) }
            val clear = out - radius - number / 2f
            assertTrue(
                clear >= Gap,
                "in a day ${halfCell * 2}dp across the ring runs to ${out - radius}dp from the middle, " +
                    "${-clear}dp into a number ${number}dp wide",
            )
            assertTrue(out + radius <= halfCell, "in a day ${halfCell * 2}dp across the ring runs past the day's edge")
        }
    }

    private companion object {
        const val PhoneHalfDay = 20f
        const val CardHalfDay = 18f

        /** The least air between the number and the ring. */
        const val Gap = 0.5f
    }
}
