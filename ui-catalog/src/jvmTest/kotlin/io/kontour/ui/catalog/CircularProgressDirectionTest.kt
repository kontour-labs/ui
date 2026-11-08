package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.display.CircularProgress
import java.awt.image.BufferedImage
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A `CircularProgress` runs the way its stepped sibling does: clockwise from
 * twelve o'clock, and counter-clockwise right to left. progress.md says the two
 * read as one, and the plain ring ran clockwise in every language until the
 * November 2026 audit.
 */
class CircularProgressDirectionTest {

    @Test
    fun leftToRightAQuarterIsClockwise() {
        val frame = render(LayoutDirection.Ltr)
        assertEquals(Fill, frame.at(45), "a quarter of the way, at half past twelve on the right")
        assertEquals(Track, frame.at(315), "and the left of twelve, which is the end")
    }

    @Test
    fun rightToLeftAQuarterIsCounterClockwise() {
        val frame = render(LayoutDirection.Rtl)
        assertEquals(Fill, frame.at(315), "a quarter of the way, to the left of twelve")
        assertEquals(Track, frame.at(45), "and the right of twelve, which is the end right to left")
    }

    private fun render(direction: LayoutDirection): BufferedImage =
        Scene(width = Size, height = Size, reduceMotion = true) {
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                Box(Modifier.fillMaxSize().background(Color.White)) {
                    CircularProgress(
                        progress = 0.25f,
                        size = Diameter.dp,
                        strokeWidth = Stroke.dp,
                        colour = Fill,
                        trackColour = Track,
                        modifier = Modifier.padding(Margin.dp),
                    )
                }
            }
        }.use { it.frames(40) }

    /** Fill, track or neither, at [degrees] clockwise from twelve o'clock on the stroke's centre line. */
    private fun BufferedImage.at(degrees: Int): Color? {
        val angle = degrees * PI / 180
        val x = Centre + Radius * sin(angle)
        val y = Centre - Radius * cos(angle)
        val colour = Color(getRGB(x.toInt(), y.toInt())).copy(alpha = 1f)
        return listOf(Fill, Track).firstOrNull { colour.near(it) }
    }

    private fun Color.near(other: Color): Boolean =
        abs(red - other.red) < 0.08f && abs(green - other.green) < 0.08f && abs(blue - other.blue) < 0.08f

    private companion object {
        const val Margin = 10
        const val Diameter = 80
        const val Stroke = 8
        const val Density = 2
        const val Size = (Margin * 2 + Diameter) * Density
        const val Centre = (Margin + Diameter / 2f) * Density
        const val Radius = (Diameter - Stroke) / 2f * Density
        val Fill = Color(0xFF2244CC)
        val Track = Color(0xFFBBBBBB)
    }
}
