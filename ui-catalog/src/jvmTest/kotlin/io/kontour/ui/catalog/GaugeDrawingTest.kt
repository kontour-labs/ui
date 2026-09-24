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
import io.kontour.ui.components.display.GaugeIndicator
import io.kontour.ui.components.display.ScaleColours
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

    /**
     * Both indicators at once: the needle from the middle and the thumb on the arc.
     *
     * "I'd like to be able to have needle and thumb indicators as an option." At 0.5
     * both point straight up. A 20dp arc with a thumb is 34px of thumb radius, which
     * puts the arc's centre line at 166px from the middle; the needle is 20px wide
     * and 117px long, so 60px up from the middle is on it.
     */
    @Test
    fun withBothIndicatorsTheNeedlePointsAtTheThumb() {
        val frame = Scene(width = 400, height = 400, reduceMotion = true) {
            Box(Modifier.fillMaxSize().background(Color.White), contentAlignment = Alignment.Center) {
                Gauge(
                    value = 0.5f,
                    size = 200.dp,
                    thickness = 20.dp,
                    indicator = GaugeIndicator.NeedleAndThumb,
                    colours = GaugeDefaults.colours(
                        indicator = ScaleColours.solid(Color.Blue),
                        needle = Color.Green,
                        thumb = Color.Magenta,
                    ),
                )
            }
        }.use { it.frames(4) }
        val (nr, ng, nb) = frame.rgb(200.0, 140.0)
        assertTrue(ng > 200 && nr < 60 && nb < 60, "60px up from the middle should be the needle, and is ($nr, $ng, $nb)")
        val (tr, tg, tb) = frame.rgb(200.0, 34.0)
        assertTrue(tr > 200 && tb > 200 && tg < 60, "the top of the arc should be the thumb, and is ($tr, $tg, $tb)")
    }

    /**
     * Bands are drawn where they are on the scale, with hard edges unless smoothed.
     *
     * "A utility for adding easy colour bands, making use of the knob/gauge's scale
     * ... a smoothing parameter, which should be 0 by default (hard bands)." Red to
     * 6, green to 8, blue to the end of a 0-to-10 gauge, full, read along the arc:
     * a few degrees either side of the red-green edge is pure red and pure green,
     * and smoothed, the same two places are both a blend.
     */
    @Test
    fun bandsSitAtTheirValuesWithHardEdgesUnlessSmoothed() {
        val hard = renderBands(smoothing = 0f)
        assertTrue(hard.isMostly(pointAt(StartAngle + Sweep * 0.3), red = true), "0.3 of the way should be red")
        assertTrue(hard.isMostly(pointAt(StartAngle + Sweep * 0.7), green = true), "0.7 should be green")
        assertTrue(hard.isMostly(pointAt(StartAngle + Sweep * 0.9), blue = true), "0.9 should be blue")
        val justBefore = pointAt(StartAngle + Sweep * 0.6 - 2)
        val justAfter = pointAt(StartAngle + Sweep * 0.6 + 2)
        assertTrue(
            hard.isMostly(justBefore, red = true) && hard.isMostly(justAfter, green = true),
            "2° either side of a hard edge should be one colour and then the other, and were " +
                "${hard.rgb(justBefore)} and ${hard.rgb(justAfter)}",
        )

        val smooth = renderBands(smoothing = 1f)
        val (r, g, _) = smooth.rgb(justBefore)
        assertTrue(r in 60..200 && g in 60..200, "smoothed, the edge should blend, and is r=$r g=$g")
    }

    /**
     * A needle as long as it is asked to be.
     *
     * With nothing inside the arc, the room from the hub is the arc's inner edge,
     * 160px up. At the default 0.8 the needle stops at 128px; at 1 it reaches the
     * arc, so 150px up is needle only on the longer one.
     */
    @Test
    fun theNeedleIsAsLongAsItIsAsked() {
        fun needleAt(length: Float, up: Double): Boolean {
            val frame = Scene(width = 400, height = 400, reduceMotion = true) {
                Box(Modifier.fillMaxSize().background(Color.White), contentAlignment = Alignment.Center) {
                    Gauge(
                        value = 0.5f,
                        size = 200.dp,
                        thickness = 20.dp,
                        indicator = GaugeIndicator.Needle,
                        needleLength = length,
                        colours = GaugeDefaults.colours(needle = Color.Green),
                    )
                }
            }.use { it.frames(4) }
            val (r, g, b) = frame.rgb(200.0, 200.0 - up)
            return g > 200 && r < 60 && b < 60
        }
        assertTrue(needleAt(GaugeDefaults.NeedleLength, 100.0), "the default needle should reach 100px up")
        assertTrue(!needleAt(GaugeDefaults.NeedleLength, 150.0), "and stop short of 150px")
        assertTrue(needleAt(1f, 150.0), "a needle of 1 should reach the arc, past 150px up")
        assertTrue(!needleAt(0.5f, 100.0), "and one of 0.5 should stop short of 100px")
    }

    /**
     * With `needleMatchesFill`, the needle is the colour of the band it points into.
     *
     * "Can we have an option for the needle follow the band colour at its value?"
     * Red to 6, green to 8, blue to the end; the needle at 7 is green and at 9 blue,
     * read 60px up its length. Without the option it is the needle colour, black.
     */
    @Test
    fun aMatchingNeedleIsTheColourOfItsBand() {
        fun needleColour(value: Float, matches: Boolean): Triple<Int, Int, Int> {
            val frame = Scene(width = 400, height = 400, reduceMotion = true) {
                Box(Modifier.fillMaxSize().background(Color.White), contentAlignment = Alignment.Center) {
                    Gauge(
                        value = value,
                        valueRange = 0f..10f,
                        size = 200.dp,
                        thickness = 20.dp,
                        indicator = GaugeIndicator.Needle,
                        needleMatchesFill = matches,
                        colours = GaugeDefaults.colours(
                            indicator = ScaleColours.bands {
                                band(from = 0f, colour = Color.Red)
                                band(from = 6f, colour = Color.Green)
                                band(from = 8f, colour = Color.Blue)
                            },
                            needle = Color.Black,
                        ),
                    )
                }
            }.use { it.frames(4) }
            // 60px out from the middle along the needle, which points at `value`.
            val angle = (StartAngle + Sweep * value / 10f) * PI / 180.0
            return frame.rgb(200 + cos(angle) * 60, 200 + sin(angle) * 60)
        }
        val (r7, g7, b7) = needleColour(7f, matches = true)
        assertTrue(g7 > 200 && r7 < 60 && b7 < 60, "at 7 the needle should be the green band's, was ($r7, $g7, $b7)")
        val (r9, g9, b9) = needleColour(9f, matches = true)
        assertTrue(b9 > 200 && r9 < 60 && g9 < 60, "at 9 it should be the blue band's, was ($r9, $g9, $b9)")
        val (r, g, b) = needleColour(7f, matches = false)
        assertTrue(r < 40 && g < 40 && b < 40, "without the option it should be the needle colour, was ($r, $g, $b)")
    }

    private fun renderBands(smoothing: Float): BufferedImage =
        Scene(width = 400, height = 400, reduceMotion = true) {
            Box(Modifier.fillMaxSize().background(Color.White), contentAlignment = Alignment.Center) {
                Gauge(
                    value = 10f,
                    valueRange = 0f..10f,
                    size = 200.dp,
                    thickness = 20.dp,
                    colours = GaugeDefaults.colours(
                        indicator = ScaleColours.bands(smoothing = smoothing) {
                            band(from = 0f, colour = Color.Red)
                            band(from = 6f, colour = Color.Green)
                            band(from = 8f, colour = Color.Blue)
                        },
                    ),
                )
            }
        }.use { it.frames(4) }

    private fun BufferedImage.isMostly(
        point: Pair<Double, Double>,
        red: Boolean = false,
        green: Boolean = false,
        blue: Boolean = false,
    ): Boolean {
        val (r, g, b) = rgb(point)
        fun on(channel: Int, wanted: Boolean) = if (wanted) channel > 200 else channel < 40
        return on(r, red) && on(g, green) && on(b, blue)
    }

    private fun render(direction: LayoutDirection): BufferedImage =
        Scene(width = 400, height = 400, reduceMotion = true) {
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                Box(Modifier.fillMaxSize().background(Color.White), contentAlignment = Alignment.Center) {
                    Gauge(
                        value = 1f,
                        size = 200.dp,
                        thickness = 20.dp,
                        colours = GaugeDefaults.colours(indicator = ScaleColours.gradient(listOf(Color.Red, Color.Blue))),
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
