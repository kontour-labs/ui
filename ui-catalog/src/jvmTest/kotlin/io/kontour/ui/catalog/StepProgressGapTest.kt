package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.display.ProgressDefaults
import io.kontour.ui.components.display.StepProgress
import java.awt.image.BufferedImage
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A `StepProgress` with no gap is one bar, not five capsules touching.
 *
 * Asked for as "a no-gap option for linear". `gap = 0.dp` was already accepted,
 * and drew the segments end to end: two round caps meeting at a point, so the
 * bar pinched to nothing at every step. Down the middle of the bar the caps
 * touch and it reads as one, which is why the reading is taken near its top
 * edge, where a pinch is a gap.
 */
class StepProgressGapTest {

    @Test
    fun aRowWithNoGapIsOneBar() {
        val frame = render(gap = 0.dp)
        val runs = frame.runsIn(TopRow, background = White)
        assertEquals(
            1, runs.size,
            "near its top edge a gapless row of $Segments steps is ${runs.size} runs of ink: " +
                "its segments meet at round caps and the bar pinches at each step",
        )
    }

    /**
     * The fill stops at the end of the step it has finished, round, rather than
     * running on a radius into the next step to meet a track it no longer
     * meets.
     */
    @Test
    fun theFillEndsWhereItsStepDoes() {
        val frame = render(gap = 0.dp)
        val fill = frame.runOf(Fill, row = MiddleRow)
        val stepEnd = Left * Density + Width * Density * Done / Segments
        assertTrue(
            abs(fill.last + 1 - stepEnd) <= 2f,
            "the fill of $Done finished steps ends at x=${fill.last + 1}, and the second step " +
                "ends at x=$stepEnd",
        )
    }

    /** And with the default gap, the steps are still steps. */
    @Test
    fun theDefaultGapStillDrawsEachStep() {
        val frame = render(gap = ProgressDefaults.StepGap)
        assertEquals(Segments, frame.runsIn(TopRow, background = White).size)
        assertEquals(Segments, frame.runsIn(MiddleRow, background = White).size)
    }

    private fun render(gap: Dp): BufferedImage =
        Scene(width = 400, height = 80, reduceMotion = true) {
            Box(Modifier.fillMaxSize().background(Color.White)) {
                Box(Modifier.padding(start = Left.dp, top = Top.dp).width(Width.dp)) {
                    StepProgress(
                        current = Done,
                        total = Segments,
                        gap = gap,
                        height = Height.dp,
                        colour = Fill,
                        trackColour = Track,
                    )
                }
            }
        }.use { it.frames(30) }

    private fun BufferedImage.runOf(colour: Color, row: Int): IntRange {
        val xs = (0 until width).filter { Color(getRGB(it, row)).copy(alpha = 1f) == colour }
        check(xs.isNotEmpty()) { "no $colour along row $row" }
        return xs.first()..xs.last()
    }

    private companion object {
        const val Segments = 5
        const val Done = 2
        const val Density = 2
        const val Left = 20
        const val Top = 10
        const val Width = 160

        /** Thick, so a pinch at a seam is pixels wide and not a matter of antialiasing. */
        const val Height = 12

        /** A pixel row two in from the bar's top edge. */
        const val TopRow = Top * Density + 2
        const val MiddleRow = Top * Density + Height * Density / 2

        val Fill = Color(0xFF2244CC)
        val Track = Color(0xFF999999)
        val White = Color.White.toArgb()
    }
}
