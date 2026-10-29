package io.kontour.ui.charts

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import io.kontour.ui.foundation.FillPatternDefaults
import io.kontour.ui.foundation.PatternBrushes
import io.kontour.ui.theme.KontourTheme
import io.kontour.ui.theme.Theme
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * What a chart draws with is made once for the chart, not once a frame: each
 * pattern's dashes, and the path a shape drawn on its own goes into. Each was
 * a native object made for every rule, dashed line, region, marked range and
 * label backing on every frame drawn, and a chart draws every frame of a range
 * being marked and of a move.
 */
@OptIn(ExperimentalTestApi::class)
class DrawConfigTest {

    private fun config(): DrawConfig {
        var made: DrawConfig? = null
        runComposeUiTest {
            setContent {
                KontourTheme {
                    made = DrawConfig(
                        colours = ChartDefaults.colours(),
                        patterns = true,
                        lineWidth = 3f,
                        pointSize = 8f,
                        cornerShape = Theme.shapes.extraSmall,
                        pinShape = Theme.shapes.capsule,
                        hairline = 1f,
                        patternWeight = FillPatternDefaults.Weight,
                        tiles = PatternBrushes(),
                    )
                }
            }
            waitForIdle()
        }
        return made ?: fail("no config")
    }

    @Test
    fun eachPatternsDashesAreMadeOnce() {
        val config = config()
        assertNull(config.lineEffect(LinePattern.Solid), "a solid line has no dashes")
        LinePattern.entries.filter { it != LinePattern.Solid }.forEach { pattern ->
            val line = assertNotNull(config.lineEffect(pattern), "$pattern should be dashed")
            assertSame(line, config.lineEffect(pattern), "$pattern's dashes were made again for the next line")
            val rule = assertNotNull(config.ruleEffect(pattern))
            assertSame(rule, config.ruleEffect(pattern), "$pattern's dashes were made again for the next rule")
            // A rule's are at the hairline's width, not a line's.
            assertNotSame(line, rule)
        }
    }

    @Test
    fun aShapeOnItsOwnIsDrawnIntoOnePathEmptiedEachTime() {
        val config = config()
        val first = config.singleShape()
        first.addRect(Rect(0f, 0f, 10f, 10f))
        val second = config.singleShape()
        assertSame(first, second, "a new path was made for the next shape")
        assertTrue(second.isEmpty, "the path was not emptied for the next shape")
    }
}
