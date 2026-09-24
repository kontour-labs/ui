package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.display.Gauge
import io.kontour.ui.components.display.GaugeDefaults
import java.awt.image.BufferedImage
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The gauge's gradient runs along its scale, from a start that is its first
 * colour — round cap included — and the dial does not mirror.
 *
 * A 200dp gauge at density 2 is 400px square: centre (200, 200), the arc's centre
 * line at a radius of 180 (half the box less half the 20dp arc), starting at 135°.
 */
class GaugeDrawingTest {

    @Test
    fun theStartCapIsTheFirstColourNotTheLastWrappingRound() {
        val frame = render(LayoutDirection.Ltr)
        // Into the round cap: back from the start of the scale along its tangent.
        val start = pointAt(StartAngle)
        val back = (StartAngle + 90.0) * PI / 180.0 // the tangent, pointing backwards
        val cap = Pair(start.first + cos(back) * 12, start.second + sin(back) * 12)
        val (r, _, b) = frame.rgb(cap.first, cap.second)
        assertTrue(
            r > b + 60,
            "the round cap before the start of the scale is r=$r b=$b — the gradient's last " +
                "colour wrapping round to meet its first, which is a seam at the one end a " +
                "reader looks at first",
        )
    }

    @Test
    fun theGradientRunsAlongTheScale() {
        val frame = render(LayoutDirection.Ltr)
        val (earlyR, _, earlyB) = frame.rgb(pointAt(StartAngle + Sweep * 0.05))
        val (lateR, _, lateB) = frame.rgb(pointAt(StartAngle + Sweep * 0.95))
        assertTrue(earlyR > earlyB, "near the start the fill should be mostly the first colour (r=$earlyR b=$earlyB)")
        assertTrue(lateB > lateR, "near the end it should be mostly the last (r=$lateR b=$lateB)")
    }

    @Test
    fun aDialDoesNotMirror() {
        val ltr = render(LayoutDirection.Ltr)
        val rtl = render(LayoutDirection.Rtl)
        var differing = 0
        for (y in 0 until ltr.height) for (x in 0 until ltr.width) {
            if (ltr.getRGB(x, y) != rtl.getRGB(x, y)) differing++
        }
        assertEquals(0, differing, "right to left drew the gauge differently in $differing pixels")
    }

    private fun render(direction: LayoutDirection): BufferedImage =
        Scene(width = 400, height = 400, reduceMotion = true) {
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                Box(Modifier.fillMaxSize().background(Color.White), contentAlignment = Alignment.Center) {
                    Gauge(
                        value = 1f,
                        size = 200.dp,
                        thickness = 20.dp,
                        colours = GaugeDefaults.colours(indicator = listOf(Color.Red, Color.Blue)),
                    )
                }
            }
        }.use { it.frames(4) }

    private fun pointAt(degrees: Double): Pair<Double, Double> {
        val radians = degrees * PI / 180.0
        return Pair(200 + cos(radians) * 180, 200 + sin(radians) * 180)
    }

    private fun BufferedImage.rgb(point: Pair<Double, Double>) = rgb(point.first, point.second)

    private fun BufferedImage.rgb(x: Double, y: Double): Triple<Int, Int, Int> {
        val rgb = getRGB(x.toInt(), y.toInt())
        return Triple(rgb shr 16 and 0xFF, rgb shr 8 and 0xFF, rgb and 0xFF)
    }

    private companion object {
        const val StartAngle = 135.0
        const val Sweep = 270.0
    }
}
