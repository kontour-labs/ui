package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import io.kontour.ui.components.display.PageIndicator
import io.kontour.ui.components.display.rememberCarouselState
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A page indicator can run down the side as well as across.
 *
 * Asked for as "an option for the vertical page indicator". Read from the ink:
 * five dots stacked in a column with the current one the tallest, and a tap
 * that goes to the dot nearest it by height.
 */
class PageIndicatorVerticalTest {

    /** The runs of ink down the column through the indicator's middle, as `top..bottom` pixel ranges. */
    private fun inkRuns(image: BufferedImage, bounds: Rect): List<IntRange> {
        val x = bounds.center.x.toInt()
        val runs = mutableListOf<IntRange>()
        var start = -1
        for (y in bounds.top.toInt()..bounds.bottom.toInt()) {
            val ink = (image.getRGB(x, y) shr 16 and 0xFF) < 0xE8
            if (ink && start < 0) start = y
            if (!ink && start >= 0) {
                runs += start until y
                start = -1
            }
        }
        if (start >= 0) runs += start..bounds.bottom.toInt()
        return runs
    }

    @Test
    fun theDotsRunDownAndTheCurrentOneIsTallest() {
        var bounds = Rect.Zero
        val image = Scene(width = 300, height = 400) {
            Box(Modifier.fillMaxSize().background(Color.White), contentAlignment = Alignment.Center) {
                PageIndicator(
                    state = rememberCarouselState { 5 },
                    orientation = Orientation.Vertical,
                    modifier = Modifier.reportBounds { bounds = it },
                )
            }
        }.use { it.frames(8) }

        assertTrue(bounds.height > bounds.width * 2f, "a vertical indicator is ${bounds.width} wide and ${bounds.height} tall")
        val runs = inkRuns(image, bounds)
        assertEquals(5, runs.size, "expected five dots down the column, found runs $runs")
        val lengths = runs.map { it.last - it.first + 1 }
        assertEquals(lengths.max(), lengths.first(), "the current page is the first, and its dot should be the tallest: $lengths")
        assertTrue(lengths.first() > lengths.drop(1).max(), "the current dot is not drawn longer than the rest: $lengths")
    }

    @Test
    fun aTapGoesToTheDotNearestItByHeight() {
        var bounds = Rect.Zero
        val tapped = mutableListOf<Int>()
        Scene(width = 300, height = 400) {
            Box(Modifier.fillMaxSize().background(Color.White), contentAlignment = Alignment.Center) {
                PageIndicator(
                    state = rememberCarouselState { 5 },
                    orientation = Orientation.Vertical,
                    onPageClick = { tapped += it },
                    modifier = Modifier.reportBounds { bounds = it },
                )
            }
        }.use { scene ->
            scene.frames(6)
            scene.tap(Offset(bounds.center.x, bounds.bottom - 2f))
            scene.tap(Offset(bounds.center.x, bounds.top + 2f))
            scene.tap(Offset(bounds.center.x, bounds.center.y))
        }
        assertEquals(listOf(4, 0, 2), tapped, "taps at the bottom, top and middle went to $tapped")
    }
}
