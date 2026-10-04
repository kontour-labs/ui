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
 * A page indicator can be dragged along, page by page, and shows a pill round
 * its dots while it is.
 *
 * Asked for: *"with the paging indicator, i'd like it so when you tap and drag
 * it, a little translucent pill shows up around it and you can drag between
 * pages. this should work for both horizontal and vertical."* A drag used to
 * be a tap wherever it ended.
 */
class PageIndicatorScrubTest {

    private class Strip(val scene: Scene, val pages: List<Int>, val bounds: () -> Rect)

    private fun strip(orientation: Orientation, test: Strip.() -> Unit) {
        var bounds = Rect.Zero
        val pages = mutableListOf<Int>()
        Scene(width = 400, height = 400) {
            Box(Modifier.fillMaxSize().background(Color.White), contentAlignment = Alignment.Center) {
                PageIndicator(
                    state = rememberCarouselState { 5 },
                    orientation = orientation,
                    onPageClick = { pages += it },
                    modifier = Modifier.reportBounds { bounds = it },
                )
            }
        }.use { scene ->
            scene.frames(6)
            Strip(scene, pages) { bounds }.test()
        }
    }

    /**
     * How dark the ground is just before the first dot, along the strip's middle:
     * where the pill is and nothing else. 255 is the white page.
     */
    private fun beforeTheDots(image: BufferedImage, bounds: Rect, vertical: Boolean): Int {
        val first = if (vertical) {
            (bounds.top.toInt()..bounds.bottom.toInt()).first { (image.getRGB(bounds.center.x.toInt(), it) shr 16 and 0xFF) < 0xE8 }
        } else {
            (bounds.left.toInt()..bounds.right.toInt()).first { (image.getRGB(it, bounds.center.y.toInt()) shr 16 and 0xFF) < 0xE8 }
        }
        val x = if (vertical) bounds.center.x.toInt() else first - 4
        val y = if (vertical) first - 4 else bounds.center.y.toInt()
        return image.getRGB(x, y) shr 16 and 0xFF
    }

    private fun Strip.scrubsAlong(vertical: Boolean) {
        val b = bounds()
        fun at(t: Float) = if (vertical) Offset(b.center.x, b.top + b.height * t) else Offset(b.left + b.width * t, b.center.y)
        val rest = beforeTheDots(scene.frames(2), b, vertical)
        scene.drag(at(0.05f), at(0.95f), steps = 40, release = false)
        val held = beforeTheDots(scene.frames(12), b, vertical)
        scene.release(at(0.95f))
        val after = beforeTheDots(scene.frames(30), b, vertical)

        assertEquals(listOf(1, 2, 3, 4), pages, "a drag from the first dot to the last went to $pages")
        assertEquals(255, rest, "the ground before the dots was not the page's white before the drag")
        assertTrue(held < rest - 8, "no pill round the dots while dragging: the ground before them stayed at $held")
        assertEquals(rest, after, "the pill was still there after the drag ended")
    }

    @Test
    fun aDragAlongTheDotsGoesToEachPageItPasses() = strip(Orientation.Horizontal) { scrubsAlong(vertical = false) }

    @Test
    fun aDragDownAVerticalStripGoesToEachPageItPasses() = strip(Orientation.Vertical) { scrubsAlong(vertical = true) }

    /**
     * Across the strip is the page's scroll, not the indicator's, and a tap
     * that never became a drag is still a tap.
     */
    @Test
    fun aDragAcrossTheStripIsNotAScrub() = strip(Orientation.Horizontal) {
        val b = bounds()
        val from = Offset(b.left + b.width * 0.9f, b.center.y)
        scene.drag(from, from + Offset(0f, 120f), steps = 20, release = true)
        scene.frames(4)
        assertEquals(emptyList(), pages, "a drag across the dots went to $pages")
        scene.tap(Offset(b.left + b.width * 0.9f, b.center.y))
        assertEquals(listOf(4), pages, "a tap on the last dot went to $pages")
    }
}
