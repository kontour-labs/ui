package io.kontour.ui.nav

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.kontour.ui.theme.SquircleShape
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A selected tab's outline: its body standing in the middle, rounded at the
 * top, and a foot at each bottom corner that fills the corner between the
 * body's side and the ground, hollowed so it meets both without a step. The
 * same either way round.
 */
class FlaredTabShapeTest {

    private val density = Density(1f)
    private val shape = FlaredTabShape(
        SquircleShape(topStart = 8.dp, topEnd = 8.dp, bottomEnd = 0.dp, bottomStart = 0.dp),
        foot = Foot.dp,
    )

    private fun outline(direction: LayoutDirection = LayoutDirection.Ltr) =
        shape.createOutline(Size(W.toFloat(), H.toFloat()), direction, density) as Outline.Generic

    private fun pixels(direction: LayoutDirection = LayoutDirection.Ltr): PixelMap {
        val image = ImageBitmap(W, H)
        Canvas(image).drawPath(outline(direction).path, Paint().apply { color = Color.Black })
        return image.toPixelMap()
    }

    private fun PixelMap.filled(x: Int, y: Int) = this[x, y].alpha > 0.5f

    @Test
    fun itFillsItsSizeAndNoMore() {
        val bounds = outline().path.getBounds()
        val expected = Rect(0f, 0f, W.toFloat(), H.toFloat())
        assertEquals(expected.left, bounds.left, 0.5f, "its left: $bounds")
        assertEquals(expected.top, bounds.top, 0.5f, "its top: $bounds")
        assertEquals(expected.right, bounds.right, 0.5f, "its right: $bounds")
        assertEquals(expected.bottom, bounds.bottom, 0.5f, "its bottom: $bounds")
    }

    @Test
    fun theBodyStandsInTheMiddleRoundedAtTheTop() {
        val map = pixels()
        assertTrue(map.filled(W / 2, H / 2), "the body's middle is empty")
        assertTrue(map.filled(W / 2, 1), "the body's top edge is empty")
        assertFalse(map.filled(Foot + 1, 1), "the body's top corner is square")
        // Above the feet, nothing beside the body: its sides are straight.
        assertFalse(map.filled(Foot - 2, H - Foot - 3), "something stands beside the body above its foot")
        assertTrue(map.filled(Foot + 2, H - Foot - 3), "the body's side is not where it should be")
    }

    @Test
    fun eachFootFillsItsCornerHollowedOut() {
        val map = pixels()
        // Snug in the corner between the body and the ground.
        assertTrue(map.filled(Foot - 2, H - 2), "the left foot is missing")
        assertTrue(map.filled(W - Foot + 1, H - 2), "the right foot is missing")
        // Its far top corner hollowed away.
        assertFalse(map.filled(1, H - Foot + 1), "the left foot is a square, not a curve")
        assertFalse(map.filled(W - 2, H - Foot + 1), "the right foot is a square, not a curve")
    }

    @Test
    fun itIsTheSameEitherWayRound() {
        val ltr = pixels(LayoutDirection.Ltr)
        val rtl = pixels(LayoutDirection.Rtl)
        val differ = (0 until H).sumOf { y -> (0 until W).count { x -> ltr.filled(x, y) != rtl.filled(x, y) } }
        // A pixel the edge halves can fall either side of the line.
        assertTrue(differ <= Rounding, "left to right and right to left differ at $differ pixels")
    }

    @Test
    fun itsFeetAreAMirrorOfEachOther() {
        val map = pixels()
        val differ = (H - Foot until H).sumOf { y -> (0 until Foot).count { x -> map.filled(x, y) != map.filled(W - 1 - x, y) } }
        assertTrue(differ <= Rounding, "the feet differ at $differ pixels")
    }

    private companion object {
        const val W = 136
        const val H = 40
        const val Foot = 10

        /** Pixels an edge halves, which may round either way. */
        const val Rounding = 4
    }
}
