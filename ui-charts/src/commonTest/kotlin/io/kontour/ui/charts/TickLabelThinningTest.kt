package io.kontour.ui.charts

import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import io.kontour.ui.locale.LocaleFormats
import io.kontour.ui.theme.KontourTheme
import io.kontour.ui.theme.Theme
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

/**
 * A tick label that says where it is, its date or its year, says it against
 * the label kept before it once the axis is too narrow for every one. It was
 * decided against the tick before, which thinning may have left out, and the
 * label that carried the change went with it.
 */
@OptIn(ExperimentalTestApi::class)
class TickLabelThinningTest {

    private val formats = LocaleFormats.english()

    private class Tools(val measurer: TextMeasurer, val density: Density, val label: TextStyle, val title: TextStyle)

    private fun frames(chart: ResolvedChart, wanted: (List<Double>) -> Boolean): ChartFrame {
        var tools: Tools? = null
        var found: ChartFrame? = null
        runComposeUiTest {
            setContent {
                KontourTheme {
                    tools = Tools(rememberTextMeasurer(), LocalDensity.current, Theme.typography.labelSmall, Theme.typography.labelMedium)
                }
            }
            waitForIdle()
            val t = tools ?: fail("no text measurer")
            // Narrower and narrower, until the labels are thinned the way asked.
            for (width in 1600 downTo 60 step 4) {
                val frame = layOutFrame(chart, width.toFloat(), 240f, t.measurer, t.label, t.title, 8f, t.density, LayoutDirection.Ltr)
                if (wanted(frame.xLabels.map { it.value })) {
                    found = frame
                    break
                }
            }
        }
        return found ?: fail("the labels were never thinned that way")
    }

    private fun ChartFrame.texts(): List<String> = xLabels.map { it.layout.layoutInput.text.text }

    /** Every [every]th of [ticks] kept, from the first. */
    private fun every(every: Int, ticks: DoubleArray): (List<Double>) -> Boolean = { kept ->
        kept == ticks.filterIndexed { index, _ -> index % every == 0 }
    }

    /**
     * Ticks every twelve hours over four days, thinned to every third: the
     * second kept is noon on the second day, the first kept on that day, so
     * it says its date. It was the midnight before it that said the date,
     * and that one was left out.
     */
    @Test
    fun aClockTickSaysItsDateAgainstTheTickKeptBefore() {
        val zone = TimeZone.UTC
        val start = LocalDateTime(2026, 10, 5, 0, 0).toInstant(zone)
        val end = LocalDateTime(2026, 10, 9, 0, 0).toInstant(zone)
        val x = ChartAxis.instant(timeZone = zone, domain = start..end, tickCount = 9)
        val y = ChartAxis.number()
        val chart = resolve(
            ChartRecorder(x, y).apply { line(listOf(start to 1, end to 2), x = { it.first }, y = { it.second }) }.build(),
            x, y, formats,
        )
        assertEquals(9, chart.x.ticks.size, "the ticks should be twelve hours apart")
        val frame = frames(chart, every(3, chart.x.ticks))
        val noon = LocalDateTime(2026, 10, 6, 12, 0)
        assertEquals(formats.dateTime(noon.date, noon.time), frame.texts()[1], "noon on the 6th should say its date: ${frame.texts()}")
        val midnight = LocalDateTime(2026, 10, 8, 0, 0)
        assertEquals(formats.dateTime(midnight.date, midnight.time), frame.texts()[2])
    }

    /** And a time with no change of day from the label kept before says only its time. */
    @Test
    fun aClockTickOnTheSameDayAsTheOneBeforeSaysOnlyItsTime() {
        val zone = TimeZone.UTC
        val start = LocalDateTime(2026, 10, 5, 0, 0).toInstant(zone)
        val end = LocalDateTime(2026, 10, 9, 0, 0).toInstant(zone)
        val x = ChartAxis.instant(timeZone = zone, domain = start..end, tickCount = 9)
        val y = ChartAxis.number()
        val chart = resolve(
            ChartRecorder(x, y).apply { line(listOf(start to 1, end to 2), x = { it.first }, y = { it.second }) }.build(),
            x, y, formats,
        )
        val frame = frames(chart, every(1, chart.x.ticks))
        assertEquals(formats.time(LocalTime(12, 0)), frame.texts()[1], "${frame.texts()}")
    }

    /**
     * Monthly ticks from February, thinned to every second: the next
     * February is the first kept in its year, so it says its year. It was
     * January that said it, and January was left out.
     */
    @Test
    fun aMonthSaysItsYearAgainstTheMonthKeptBefore() {
        val x = ChartAxis.date(domain = LocalDate(2026, 2, 1)..LocalDate(2027, 4, 1), tickCount = 15)
        val y = ChartAxis.number()
        val chart = resolve(
            ChartRecorder(x, y).apply {
                line(listOf(LocalDate(2026, 2, 1) to 1, LocalDate(2027, 4, 1) to 2), x = { it.first }, y = { it.second })
            }.build(),
            x, y, formats,
        )
        assertEquals(15, chart.x.ticks.size, "the ticks should be a month apart")
        val frame = frames(chart, every(2, chart.x.ticks))
        assertEquals(formats.monthAndYear(LocalDate(2026, 2, 1)), frame.texts()[0])
        assertEquals(formats.monthAndYear(LocalDate(2027, 2, 1)), frame.texts()[6], "the next February should say its year: ${frame.texts()}")
    }
}
