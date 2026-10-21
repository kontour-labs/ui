package io.kontour.ui.foundation

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** What a pattern's tiles cost to keep, and what a custom one looks like drawn smaller. */
class FillPatternTest {

    private val density = Density(2f)

    /**
     * A colour that moves, as one does on every frame of a theme's fade, asks
     * for a tile each frame that nothing asks for again. The cache kept every
     * one, so a chart through a fade held a bitmap a frame for each series.
     */
    @Test
    fun aMovingColourKeepsABoundedNumberOfTiles() {
        val brushes = PatternBrushes()
        repeat(100) { step ->
            assertNotNull(brushes.brush(FillPattern.Diagonal, Color(step / 100f, 0f, 0f), density, 1.dp))
        }
        assertTrue(brushes.size <= PatternTileCapacity, "kept ${brushes.size} tiles")
    }

    /** One that keeps a single tile, as `Modifier.fillPattern`'s does, keeps the last. */
    @Test
    fun aCacheOfOneKeepsTheLastTile() {
        val brushes = PatternBrushes(capacity = 1)
        repeat(100) { step -> brushes.brush(FillPattern.Dots, Color(0f, step / 100f, 0f), density, 1.dp) }
        assertEquals(1, brushes.size)
        val last = brushes.brush(FillPattern.Dots, Color(0f, 99 / 100f, 0f), density, 1.dp)
        assertSame(last, brushes.brush(FillPattern.Dots, Color(0f, 99 / 100f, 0f), density, 1.dp))
    }

    /** Bounded, it still keeps what is asked for again: a still chart makes each tile once. */
    @Test
    fun theSameAskIsTheSameBrush() {
        val brushes = PatternBrushes()
        val first = FillPatternDefaults.Series.map { brushes.brush(it, Color.Black, density, 1.dp) }
        val again = FillPatternDefaults.Series.map { brushes.brush(it, Color.Black, density, 1.dp) }
        first.zip(again).forEach { (a, b) -> assertSame(a, b) }
    }

    /**
     * A custom tile drawn at half its size, as a legend's key draws it, is the
     * same picture smaller. It was drawn at its own size onto a bitmap half
     * that, so a dot at its middle landed in the bitmap's corner, cut off.
     */
    @Test
    fun aCustomTileDrawnSmallerIsScaledNotCut() {
        val pattern = FillPattern.custom(DpSize(10.dp, 10.dp)) { colour ->
            drawCircle(colour, radius = 2.dp.toPx(), center = Offset(5.dp.toPx(), 5.dp.toPx()))
        }
        // 10dp at a density of 2, halved: ten pixels a side, the middle at 5.
        val tile = assertNotNull(pattern.tile(Color.Black, density, spacingPx = 14, weightPx = 2f, scale = 0.5f))
        assertEquals(10, tile.width)
        assertEquals(10, tile.height)
        val pixels = tile.toPixelMap()
        assertTrue(pixels[4, 4].alpha > 0.5f && pixels[5, 5].alpha > 0.5f, "no dot at the middle")
        assertEquals(0f, pixels[9, 9].alpha, "the dot is in the corner")
        assertEquals(0f, pixels[0, 0].alpha)
    }
}
