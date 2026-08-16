package io.kontour.ui.foundation

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins the icon aspect-ratio maths.
 *
 * This exists because the bug it prevents is invisible in code review and only
 * mildly wrong on screen: FontAwesome declares every glyph as 24×24dp while
 * giving them viewports of differing width, and `VectorPainter` scales each
 * axis independently. The result is a cross stretched 1.45× across, which reads
 * as "the icons look a bit off" rather than as an obvious defect.
 */
class IconScalingTest {

    @Test
    fun squareGlyphFillsTheBox() {
        val size = fitToBox(box = 24.dp, aspectRatio = 1f)
        assertEquals(24.dp, size.width)
        assertEquals(24.dp, size.height)
    }

    @Test
    fun narrowGlyphMatchesHeightAndNarrowsWidth() {
        // FontAwesome `times`: 352x512.
        val size = fitToBox(box = 24.dp, aspectRatio = 352f / 512f)
        assertEquals(24.dp, size.height, "a taller-than-wide glyph should fill the box's height")
        assertTrue(size.width < 24.dp, "width should shrink, not stretch to the box")
        assertEquals(16.5f, size.width.value, absoluteTolerance = 0.01f)
    }

    @Test
    fun wideGlyphMatchesWidthAndShortensHeight() {
        // FontAwesome `star`: 576x512.
        val size = fitToBox(box = 24.dp, aspectRatio = 576f / 512f)
        assertEquals(24.dp, size.width, "a wider-than-tall glyph should fill the box's width")
        assertTrue(size.height < 24.dp)
        assertEquals(21.33f, size.height.value, absoluteTolerance = 0.01f)
    }

    @Test
    fun aspectRatioIsPreservedExactly() {
        val ratios = listOf(352f / 512f, 320f / 512f, 448f / 512f, 1f, 576f / 512f)
        for (ratio in ratios) {
            val size = fitToBox(box = 20.dp, aspectRatio = ratio)
            val rendered = size.width.value / size.height.value
            assertEquals(
                ratio,
                rendered,
                absoluteTolerance = 0.0001f,
                message = "glyph at aspect $ratio was drawn at $rendered",
            )
        }
    }

    @Test
    fun neverExceedsTheBox() {
        for (ratio in listOf(0.1f, 0.625f, 1f, 1.125f, 8f)) {
            val size = fitToBox(box = 24.dp, aspectRatio = ratio)
            assertTrue(size.width <= 24.dp, "width overflowed at aspect $ratio")
            assertTrue(size.height <= 24.dp, "height overflowed at aspect $ratio")
        }
    }

    @Test
    fun degenerateAspectRatiosFallBackToSquare() {
        for (bad in listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY)) {
            val size = fitToBox(box = 24.dp, aspectRatio = bad)
            assertEquals(24.dp, size.width, "aspect $bad should fall back to square")
            assertEquals(24.dp, size.height, "aspect $bad should fall back to square")
        }
    }
}
