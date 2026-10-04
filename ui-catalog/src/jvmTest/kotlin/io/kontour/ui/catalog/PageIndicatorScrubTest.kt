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
import androidx.compose.ui.graphics.luminance
import io.kontour.ui.components.display.PageIndicator
import io.kontour.ui.components.display.rememberCarouselState
import io.kontour.ui.theme.Theme
import java.awt.image.BufferedImage
import kotlin.math.abs
import kotlin.math.roundToInt
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
 * be a tap wherever it ended. And then of the pill: *"a standard colour from
 * our theme"* — `surfaceSunken`, the well a segmented control's track is, read
 * here to the exact colour; *"in dark it's a bit invisible"*, so `outline`
 * there; and *"make the pill expand out of the dots, rather than just
 * appearing"*.
 */
class PageIndicatorScrubTest {

    private class Strip(
        val scene: Scene,
        val pages: List<Int>,
        /** The page under the strip, and the pill's colour on it. */
        val page: Color,
        val pill: Color,
        val bounds: () -> Rect,
    )

    private fun strip(orientation: Orientation, dark: Boolean = false, test: Strip.() -> Unit) {
        var bounds = Rect.Zero
        var page = Color.Unspecified
        var pill = Color.Unspecified
        val pages = mutableListOf<Int>()
        Scene(width = 400, height = 400, darkTheme = dark) {
            page = Theme.colours.background
            pill = if (dark) Theme.colours.outline else Theme.colours.surfaceSunken
            Box(Modifier.fillMaxSize().background(page), contentAlignment = Alignment.Center) {
                PageIndicator(
                    state = rememberCarouselState { 5 },
                    orientation = orientation,
                    onPageClick = { pages += it },
                    modifier = Modifier.reportBounds { bounds = it },
                )
            }
        }.use { scene ->
            scene.frames(6)
            Strip(scene, pages, page, pill) { bounds }.test()
        }
    }

    private fun red(image: BufferedImage, x: Int, y: Int) = image.getRGB(x, y) shr 16 and 0xFF
    private fun Color.red255() = (red * 255f).roundToInt()

    /** Where the dots are along the strip's middle, as runs of ink: far from the page's own red. */
    private fun Strip.dots(image: BufferedImage, vertical: Boolean): List<IntRange> {
        val b = bounds()
        val ground = page.red255()
        val along = if (vertical) b.top.toInt()..b.bottom.toInt() else b.left.toInt()..b.right.toInt()
        val runs = mutableListOf<IntRange>()
        var start = -1
        for (i in along) {
            val ink = abs(if (vertical) red(image, b.center.x.toInt(), i) - ground else red(image, i, b.center.y.toInt()) - ground) > 0x30
            if (ink && start < 0) start = i
            if (!ink && start >= 0) {
                runs += start until i
                start = -1
            }
        }
        return runs
    }

    /** The ground just before the first dot, along the strip's middle: where the pill is and nothing else. */
    private fun Strip.beforeTheDots(image: BufferedImage, first: Int, vertical: Boolean): Int {
        val b = bounds()
        return if (vertical) red(image, b.center.x.toInt(), first - 4) else red(image, first - 4, b.center.y.toInt())
    }

    private fun Strip.scrubsAlong(vertical: Boolean) {
        val b = bounds()
        fun at(t: Float) = if (vertical) Offset(b.center.x, b.top + b.height * t) else Offset(b.left + b.width * t, b.center.y)
        val resting = scene.frames(2)
        val first = dots(resting, vertical).first().first
        val rest = beforeTheDots(resting, first, vertical)
        scene.drag(at(0.05f), at(0.95f), steps = 40, release = false)
        val held = beforeTheDots(scene.frames(12), first, vertical)
        scene.release(at(0.95f))
        val after = beforeTheDots(scene.frames(30), first, vertical)

        assertEquals(listOf(1, 2, 3, 4), pages, "a drag from the first dot to the last went to $pages")
        assertEquals(page.red255(), rest, "the ground before the dots was not the page before the drag")
        assertEquals(
            pill.red255(), held,
            "while dragging, the ground before the dots should be the pill's colour — " +
                "${pill.red255()} — and was $held",
        )
        assertEquals(rest, after, "the pill was still there after the drag ended")
    }

    @Test
    fun aDragAlongTheDotsGoesToEachPageItPasses() = strip(Orientation.Horizontal) { scrubsAlong(vertical = false) }

    @Test
    fun aDragDownAVerticalStripGoesToEachPageItPasses() = strip(Orientation.Vertical) { scrubsAlong(vertical = true) }

    /**
     * In the dark the pill is `outline`, which stands off the page: the well it
     * is in the light was 1.07:1 from a dark page, and reported as invisible.
     */
    @Test
    fun inTheDarkThePillStandsOffThePage() = strip(Orientation.Horizontal, dark = true) {
        scrubsAlong(vertical = false)
        val ratio = (maxOf(pill.luminance(), page.luminance()) + 0.05f) / (minOf(pill.luminance(), page.luminance()) + 0.05f)
        assertTrue(ratio >= 1.5f, "the dark pill is ${"%.2f".format(ratio)}:1 from the page")
    }

    /**
     * The pill grows out of the dots and back into them, rather than fading in
     * and out at its size: measured across the strip through the gap between
     * the third and fourth dots, frame by frame, as the drag starts and after
     * it lets go. Whenever it is there it is its own colour, not a fade of it.
     */
    @Test
    fun thePillGrowsOutOfTheDotsAndBackIntoThem() = strip(Orientation.Horizontal) {
        val b = bounds()
        val runs = dots(scene.frames(2), vertical = false)
        val gap = (runs[2].last + runs[3].first) / 2
        val ground = page.red255()
        val colour = pill.red255()
        val middle = b.center.y.toInt()
        fun across(image: BufferedImage): Pair<Int, Int> {
            val thick = (b.top.toInt()..b.bottom.toInt()).count { red(image, gap, it) != ground }
            return thick to red(image, gap, middle)
        }

        val growing = mutableListOf<Pair<Int, Int>>()
        fun at(t: Float) = Offset(b.left + b.width * t, b.center.y)
        scene.drag(at(0.05f), at(0.6f), steps = 30, release = false) { _, image -> growing += across(image) }
        repeat(20) { growing += across(scene.frame()) }
        scene.release(at(0.6f))
        val shrinking = mutableListOf<Pair<Int, Int>>()
        repeat(30) { shrinking += across(scene.frame()) }

        val full = growing.maxOf { it.first }
        val seen = growing.dropWhile { it.first == 0 }
        val opening = seen.takeWhile { it.first < full - 2 }
        assertTrue(full >= 40, "the pill never reached its size: at most $full px across, in $growing")
        assertTrue(
            seen.first().first <= full / 2,
            "the pill appeared ${seen.first().first} px across against $full at its size: it should grow out of the dots — $growing",
        )
        assertTrue(opening.size >= 3, "the pill went from nothing to its size in ${opening.size} frames — $growing")
        assertTrue(
            opening.zipWithNext().all { (a, b) -> b.first >= a.first - 1 },
            "the pill did not grow steadily — $growing",
        )
        val closing = shrinking.takeWhile { it.first > 0 }
        assertTrue(closing.size >= 3, "the pill went in ${closing.size} frames rather than shrinking back — $shrinking")
        assertTrue(
            closing.zipWithNext().all { (a, b) -> b.first <= a.first + 1 },
            "the pill did not shrink steadily — $shrinking",
        )
        assertEquals(0, shrinking.last().first, "the pill was still there after the drag ended — $shrinking")
        for ((thick, centre) in seen + closing) {
            if (thick >= 8) {
                assertEquals(colour, centre, "the pill was a fade of its colour rather than it — ${seen + closing}")
            }
        }
    }

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
