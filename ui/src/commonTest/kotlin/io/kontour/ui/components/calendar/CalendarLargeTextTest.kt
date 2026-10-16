package io.kontour.ui.components.calendar

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.kontour.ui.theme.KontourTheme
import io.kontour.ui.theme.Theme
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A calendar's weekday headings at 200% type on a phone. A month's went to
 * letters only below a fixed 40dp column, and "Wed" at 200% is wider than
 * that, so between the two the abbreviation was kept and cut at the column's
 * edge. The headings clear their own semantics, so the decision is what is
 * tested, against the measured name.
 */
@OptIn(ExperimentalTestApi::class)
class CalendarLargeTextTest {

    /** The widest abbreviated weekday at [fontScale], as the month's headings measure it. */
    private fun widestAt(fontScale: Float): Dp {
        var widest = 0.dp
        runComposeUiTest {
            setContent {
                val density = LocalDensity.current
                KontourTheme(reduceMotion = true) {
                    CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                        widest = widestWeekdayName(Theme.formats, Theme.typography.labelSmall) + Theme.spacing.xxs
                    }
                }
            }
            waitForIdle()
        }
        return widest
    }

    private val phoneColumn = 320.dp / 7

    @Test
    fun atLargeTypeAPhonesMonthGoesToLetters() {
        val widest = widestAt(2f)
        assertTrue(widest > 40.dp, "at 200% the widest abbreviation, $widest, should not fit the old 40dp")
        assertFalse(abbreviatesWeekdays(phoneColumn, widest, 40.dp), "a $phoneColumn column at 200% should show letters")
    }

    @Test
    fun atOrdinaryTypeNothingChanges() {
        val widest = widestAt(1f)
        assertTrue(widest < 40.dp, "at 100% English the old 40dp held, and still decides: $widest")
        assertTrue(abbreviatesWeekdays(phoneColumn, widest, 40.dp))
    }
}
