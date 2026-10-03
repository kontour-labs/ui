package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.display.CircularStepProgress
import io.kontour.ui.components.display.ProgressDefaults
import java.awt.image.BufferedImage
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A `CircularStepProgress` is read round its ring, clockwise from twelve
 * o'clock, at the stroke's centre line and near its outer edge — where two
 * round ends that merely touch would leave a notch.
 *
 * Asked for as "a circular stepped progress, plus a no-gap option for linear and
 * circular": [StepProgress] bent into [CircularProgress]'s ring.
 */
class CircularStepProgressPixelTest {

    @Test
    fun eachStepIsItsOwnArc() {
        val frame = render(current = 2, gap = ProgressDefaults.StepGap)
        assertEquals(Steps, frame.arcsAt(Radius), "arcs of ink round the ring's centre line")
        assertEquals(Steps, frame.arcsAt(Radius + Edge), "arcs of ink near its outer edge")
    }

    @Test
    fun noGapIsOneRingWithNoNotches() {
        val frame = render(current = 2, gap = 0.dp)
        for (radius in listOf(Radius - Edge, Radius, Radius + Edge)) {
            val bare = (0 until Samples).count { frame.bare(it, radius) }
            assertEquals(0, bare, "$bare of $Samples points ${radius}px from the centre are bare on a gapless ring")
        }
    }

    /** Two of four steps done: the first half of the ring, clockwise from twelve o'clock. */
    @Test
    fun theFillRunsClockwiseFromTwelveToTheEndOfItsStep() {
        val frame = render(current = 2, gap = 0.dp)
        for (degrees in listOf(10, 90, 170)) {
            assertEquals(Fill, frame.atDegrees(degrees, Radius), "at $degrees° past twelve, inside the second step")
        }
        for (degrees in listOf(190, 270, 350)) {
            assertEquals(Track, frame.atDegrees(degrees, Radius), "at $degrees°, a step not yet started")
        }
    }

    /** All done, and the ring closes over twelve o'clock with no notch where it began. */
    @Test
    fun aFinishedRingClosesWithoutANotch() {
        val frame = render(current = Steps, gap = 0.dp)
        for (radius in listOf(Radius - Edge, Radius, Radius + Edge)) {
            val notFill = (0 until Samples).count { frame.at(it, radius) != Fill }
            assertEquals(0, notFill, "$notFill of $Samples points ${radius}px out are not fill on a finished ring")
        }
    }

    /** Read from the right, the steps run the other way round: counter-clockwise. */
    @Test
    fun rightToLeftRunsCounterClockwise() {
        val frame = render(current = 1, gap = 0.dp, rtl = true)
        assertEquals(Fill, frame.atDegrees(315, Radius), "the first step, a quarter round to the left of twelve")
        assertEquals(Track, frame.atDegrees(45, Radius), "and the right of twelve, which is the last step in RTL")
    }

    /** An unknown step under reduced motion holds still, and is not blank. */
    @Test
    fun reducedMotionHoldsAStillStub() {
        lateinit var first: BufferedImage
        lateinit var later: BufferedImage
        Scene(width = Size, height = Size, reduceMotion = true) { Ring(current = null, gap = ProgressDefaults.StepGap) }
            .use { scene ->
                first = scene.frames(30)
                later = scene.frames(20)
            }
        val fill = (0 until Samples).count { first.at(it, Radius) == Fill }
        assertTrue(fill > 0, "nothing of the fill is drawn: neither filled nor animated is invisible")
        assertTrue(
            (0 until Samples).all { first.at(it, Radius) == later.at(it, Radius) },
            "the ring moved under reduced motion",
        )
    }

    /** And without reduced motion, the two bars run round it. */
    @Test
    fun anUnknownStepChasesRoundTheRing() {
        val leads = mutableSetOf<Int>()
        Scene(width = Size, height = Size) { Ring(current = null, gap = ProgressDefaults.StepGap) }.use { scene ->
            scene.frames(30)
            repeat(40) {
                val frame = scene.frame()
                (0 until Samples).firstOrNull { frame.at(it, Radius) == Fill }?.let { leads += it }
            }
        }
        assertTrue(leads.size > 10, "the fill's first point round the ring was in ${leads.size} places over 40 frames")
    }

    private fun render(current: Int?, gap: Dp, rtl: Boolean = false): BufferedImage =
        Scene(width = Size, height = Size, reduceMotion = true) {
            CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                Ring(current, gap)
            }
        }.use { it.frames(40) }

    @androidx.compose.runtime.Composable
    private fun Ring(current: Int?, gap: Dp) {
        Box(Modifier.fillMaxSize().background(Color.White)) {
            CircularStepProgress(
                current = current,
                total = Steps,
                gap = gap,
                size = Diameter.dp,
                strokeWidth = Stroke.dp,
                colour = Fill,
                trackColour = Track,
                modifier = Modifier.padding(Margin.dp),
            )
        }
    }

    /** How many separate arcs of ink there are round the ring, [radius] px from its centre. */
    private fun BufferedImage.arcsAt(radius: Float): Int {
        val inked = BooleanArray(Samples) { !bare(it, radius) }
        if (inked.all { it }) return 1
        val start = inked.indexOfFirst { !it }
        var arcs = 0
        for (step in 1..Samples) {
            val here = inked[(start + step) % Samples]
            val before = inked[(start + step - 1) % Samples]
            if (here && !before) arcs++
        }
        return arcs
    }

    /**
     * Whether the page shows at sample [index]: nothing of the ring there. Not
     * "neither fill nor track", which a pixel where the two meet is, blended.
     */
    private fun BufferedImage.bare(index: Int, radius: Float): Boolean = pixel(index, radius).near(Color.White)

    private fun BufferedImage.pixel(index: Int, radius: Float): Color {
        val angle = index * 2 * PI / Samples
        val x = Centre + radius * sin(angle)
        val y = Centre - radius * cos(angle)
        return Color(getRGB(x.toInt(), y.toInt())).copy(alpha = 1f)
    }

    private fun BufferedImage.atDegrees(degrees: Int, radius: Float): Color? = at(degrees * Samples / 360, radius)

    /** Fill, track or nothing, at sample [index] clockwise from twelve o'clock. */
    private fun BufferedImage.at(index: Int, radius: Float): Color? {
        val colour = pixel(index, radius)
        return when {
            colour.near(Fill) -> Fill
            colour.near(Track) -> Track
            else -> null
        }
    }

    private fun Color.near(other: Color): Boolean =
        kotlin.math.abs(red - other.red) < 0.08f &&
            kotlin.math.abs(green - other.green) < 0.08f &&
            kotlin.math.abs(blue - other.blue) < 0.08f

    private companion object {
        const val Steps = 4
        const val Samples = 720
        const val Margin = 10
        const val Diameter = 80
        const val Stroke = 8
        const val Density = 2

        const val Size = (Margin * 2 + Diameter) * Density
        const val Centre = (Margin + Diameter / 2f) * Density

        /** The stroke's centre line, in pixels from the centre. */
        const val Radius = (Diameter - Stroke) / 2f * Density

        /** Two pixels in from the stroke's edge. */
        const val Edge = Stroke / 2f * Density - 2f

        val Fill = Color(0xFF2244CC)
        val Track = Color(0xFFBBBBBB)
    }
}
