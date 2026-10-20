package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.kontour.ui.charts.Chart
import io.kontour.ui.charts.ChartAxis
import io.kontour.ui.charts.ChartDefaults
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A rule's label goes to where it will stay as the data starts to move, and
 * stays there: it does not hop between the ends of its line on the way.
 *
 * Its place is the first end of its line with no data under it. Worked out on
 * every frame of a move, the data under each end changed as it went, and the
 * label flickered between them before it settled. Reported from the energy
 * app as *"when switching between fortnight and month ... the "average" text
 * flickers a bit before moving on to the left"*.
 */
class RuleLabelMoveTest {

    @Test
    fun aRuleLabelMovesOnceAtMost() {
        var days by mutableStateOf(14)
        val sides = mutableListOf<Char>()
        Scene(width = 640, height = 360) {
            Box(Modifier.fillMaxSize().background(Color.White)) {
                Chart(
                    // No labels on the axes, so the only words drawn are the rule's.
                    xAxis = ChartAxis.number(showLabels = false),
                    yAxis = ChartAxis.number(showLabels = false),
                    modifier = Modifier.padding(10.dp).size(300.dp, 160.dp),
                    colours = ChartDefaults.colours(label = Ink),
                    showLegend = false,
                ) {
                    val span = (1..days).toList()
                    // Low for a fortnight; over a month, its last ten days tall,
                    // which fills the end of the line and sends the label to its start.
                    val height = { day: Int -> if (day > 20) 10 else 3 }
                    bar(span, x = { it }, y = height)
                    rule(y = span.sumOf(height).toDouble() / span.size, label = "Average")
                }
            }
        }.use { scene ->
            scene.renderUntil(timeoutMillis = 1_500) { false }
            sides += scene.frame().sideOfInk()
            days = 30
            repeat(60) { sides += scene.frame().sideOfInk() }
        }

        val moves = sides.zipWithNext().count { (a, b) -> a != b }
        assertTrue(moves <= 1, "the label should move at most once, to where it settles; it went ${sides.joinToString("")}")
        assertTrue(sides.last() == 'S', "with the end of the line filled, the label should settle at its start: ${sides.joinToString("")}")
    }

    /** `S` for ink nearer the start of the chart, `E` for nearer its end, `-` for none. */
    private fun BufferedImage.sideOfInk(): Char {
        val xs = mutableListOf<Int>()
        for (y in 0 until height) for (x in 0 until width) {
            val c = Color(getRGB(x, y))
            if (c.red > 0.8f && c.green < 0.3f && c.blue > 0.8f) xs += x
        }
        if (xs.isEmpty()) return '-'
        return if (xs.average() < (10 + 150) * 2) 'S' else 'E'
    }

    private companion object {
        val Ink = Color(0xFFFF00FF)
    }
}
