package io.kontour.ui.components.display

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kontour.ui.theme.KontourTheme
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A photo app's filmstrip under a carousel: asked for as *"a 'thumbnail
 * pager' component… in photos apps it shows all the thumbnails down the
 * bottom and you can drag through them to scroll through the photos"*.
 */
@OptIn(ExperimentalTestApi::class)
class ThumbnailStripTest {

    // --- Where the thumbnails go ---

    @Test
    fun atAPageItsThumbnailIsWideAndInTheMiddle() {
        val lefts = thumbLefts(first = 3, count = 5, position = 5f, width = 400f, narrow = 28f, wide = { 64f }, gap = 2f)
        // Page 5 is the third: centred.
        assertEquals(200f - 32f, lefts[2])
        // The ones either side a gap away, narrow.
        assertEquals(200f + 32f + 2f, lefts[3])
        assertEquals(200f - 32f - 2f - 28f, lefts[1])
        assertEquals(64f, thumbWidth(5, 5f, 28f, 64f))
        assertEquals(28f, thumbWidth(6, 5f, 28f, 64f))
    }

    @Test
    fun halfwayBetweenTwoPagesBothAreHalfWideAndTheStripHasSlidHalfAPitch() {
        assertEquals(46f, thumbWidth(5, 5.5f, 28f, 64f))
        assertEquals(46f, thumbWidth(6, 5.5f, 28f, 64f))
        val lefts = thumbLefts(first = 5, count = 2, position = 5.5f, width = 400f, narrow = 28f, wide = { 64f }, gap = 2f)
        // The gap between the two is the middle.
        assertEquals(200f, lefts[0] + 46f + 1f)
        assertEquals(lefts[0] + 46f + 2f, lefts[1])
    }

    // --- Each picture's own shape ---

    /**
     * *"when the images expand, they are the proper aspect ratio of the image
     * itself… a minimum… and a maximum"*.
     */
    @Test
    fun theCurrentThumbnailTakesItsPicturesShapeWithinLimits() {
        // 48 tall, 28 narrow, 64 by default, 112 at most.
        assertEquals(64f, currentWidth(null, 48f, 28f, 64f, 112f), "no ratio: the default")
        assertEquals(72f, currentWidth(1.5f, 48f, 28f, 64f, 112f), "3:2 landscape")
        assertEquals(36f, currentWidth(0.75f, 48f, 28f, 64f, 112f), "3:4 portrait")
        assertEquals(28f, currentWidth(0.3f, 48f, 28f, 64f, 112f), "narrower than a thumbnail: a thumbnail")
        assertEquals(112f, currentWidth(3f, 48f, 28f, 64f, 112f), "a panorama: the most")
        assertEquals(64f, currentWidth(0f, 48f, 28f, 64f, 112f), "nonsense: the default")
    }

    @Test
    fun thumbnailsOfDifferentShapesTileAndTheCurrentOneIsCentred() {
        val wide = { page: Int -> floatArrayOf(64f, 100f, 40f).getOrElse(page) { 64f } }
        val lefts = thumbLefts(first = 0, count = 3, position = 1f, width = 400f, narrow = 28f, wide = wide, gap = 2f)
        assertEquals(200f - 50f, lefts[1], "the current one is centred at its own width")
        assertEquals(150f - 2f - 28f, lefts[0])
        assertEquals(250f + 2f, lefts[2])
    }

    @Test
    fun theStripSlidesWithoutAJumpFromOneShapeToTheNext() {
        val wide = { page: Int -> floatArrayOf(64f, 100f, 40f, 72f).getOrElse(page) { 64f } }
        var position = 0f
        var last = thumbLefts(0, 4, position, 400f, 28f, wide, 2f)
        while (position < 3f) {
            position = (position + 0.01f).coerceAtMost(3f)
            val now = thumbLefts(0, 4, position, 400f, 28f, wide, 2f)
            for (i in 0 until 4) {
                assertTrue(abs(now[i] - last[i]) < 2f, "page $i jumped ${now[i] - last[i]}px at $position")
            }
            last = now
        }
    }

    @Test
    fun aPressFindsTheThumbnailUnderItWhateverItsShape() {
        val wide = { page: Int -> if (page % 2 == 0) 100f else 40f }
        for (position in listOf(4f, 4.5f, 5f)) {
            val lefts = thumbLefts(0, 10, position, 400f, 28f, wide, 2f)
            for (page in 2..7) {
                val middle = lefts[page] + thumbWidth(page, position, 28f, wide(page)) / 2f
                if (middle !in 0f..400f) continue
                assertEquals(page, pageAt(middle, 400f, position, 30f, wide, 2f, rtl = false, pageCount = 10), "at $position")
            }
        }
    }

    /** Past the last thumbnail the strip is empty, and a press there goes nowhere. */
    @Test
    fun aPressPastTheLastThumbnailIsOnNothing() {
        val wide = { _: Int -> 60f }
        val position = 2f
        val lefts = thumbLefts(0, 6, position, 400f, 28f, wide, 2f)
        // Where a fourth page's thumbnail would be, in a strip of three.
        val beyond = lefts[3] + thumbWidth(3, position, 28f, wide(3)) / 2f
        assertTrue(beyond in 0f..400f, "the place past the end is off the strip, so this checks nothing")
        assertEquals(null, pageAt(beyond, 400f, position, 30f, wide, 2f, rtl = false, pageCount = 3))
        assertEquals(2, pageAt(lefts[2] + 5f, 400f, position, 30f, wide, 2f, rtl = false, pageCount = 3))
    }

    @Test
    fun aPictureNarrowerThanAThumbnailIsShownWholeBetweenBars() = runComposeUiTest {
        var density = 1f
        setContent {
            KontourTheme(reduceMotion = true) {
                density = LocalDensity.current.density
                val pages = rememberCarouselState { 3 }
                Box(Modifier.size(400.dp, 48.dp)) {
                    ThumbnailStrip(pages, thumbnailAspectRatio = { page -> if (page == 0) 0.25f else 3f }) { page ->
                        Box(Modifier.fillMaxSize().testTag("picture $page"))
                    }
                }
            }
        }
        waitForIdle()
        val tall = onNodeWithTag("picture 0").getBoundsInRoot()
        val box = thumb3(0).getBoundsInRoot()
        assertEquals(12f, (tall.right - tall.left).value, 0.5f, "the picture is its own width, 48 × 0.25")
        assertEquals(28f, (box.right - box.left).value, 0.5f, "in a thumbnail's width")
        assertEquals((box.left + box.right).value / 2f, (tall.left + tall.right).value / 2f, 0.5f, "centred, with a bar either side")
    }

    @Test
    fun aPanoramaStopsAtTheWidest() = runComposeUiTest {
        setContent {
            KontourTheme(reduceMotion = true) {
                val pages = rememberCarouselState { 3 }
                Box(Modifier.size(400.dp, 48.dp)) {
                    ThumbnailStrip(pages, thumbnailAspectRatio = { 3f }) { Box(Modifier.fillMaxSize()) }
                }
            }
        }
        waitForIdle()
        val box = thumb3(0).getBoundsInRoot()
        assertEquals(112f, (box.right - box.left).value, 0.5f)
    }

    private fun androidx.compose.ui.test.ComposeUiTest.thumb3(page: Int) = onNode(hasContentDescription("Page ${page + 1} of 3"))

    // --- Using it ---

    private class Probe {
        lateinit var pages: CarouselState
        var density = 1f
    }

    private fun androidx.compose.ui.test.ComposeUiTest.strip(): Probe {
        val probe = Probe()
        setContent {
            KontourTheme(reduceMotion = true) {
                probe.density = LocalDensity.current.density
                probe.pages = rememberCarouselState { 40 }
                Column(Modifier.size(400.dp, 400.dp)) {
                    Carousel(probe.pages, contentDescription = "Photos", modifier = Modifier.height(300.dp)) { page ->
                        Box(Modifier.fillMaxSize().background(Color.Gray))
                    }
                    ThumbnailStrip(probe.pages) { Box(Modifier.fillMaxSize().background(Color.DarkGray)) }
                }
            }
        }
        waitForIdle()
        return probe
    }

    private fun androidx.compose.ui.test.ComposeUiTest.thumb(page: Int) = onNode(hasContentDescription("Page ${page + 1} of 40"))

    @Test
    fun theCurrentPagesThumbnailIsSelectedAndAnotherPressedGoesThere() = runComposeUiTest {
        val probe = strip()
        thumb(0).assert(isSelected())
        thumb(2).performClick()
        waitForIdle()
        assertEquals(2, probe.pages.currentPage)
        thumb(2).assert(isSelected())
    }

    @Test
    fun draggedTheCarouselFollowsAndItSettlesOnAPage() = runComposeUiTest {
        val probe = strip()
        val pitch = (28f + 2f) * probe.density
        val y = (300f + 24f) * probe.density
        onRoot().performTouchInput {
            down(Offset(200f * probe.density, y))
            // Past the slop, then three pages' worth to the left.
            moveBy(Offset(-20f, 0f))
            repeat(10) { moveBy(Offset(-pitch * 3f / 10f, 0f)) }
        }
        waitForIdle()
        val during = probe.pages.pagePosition
        assertTrue(during > 2f, "the carousel did not follow the scrub: at $during")
        // Held still before letting go: no fling to carry it further.
        onRoot().performTouchInput {
            moveBy(Offset.Zero, delayMillis = 300)
            up()
        }
        waitForIdle()
        assertTrue(probe.pages.currentPage in 3..4, "it settled on ${probe.pages.currentPage}")
        assertEquals(probe.pages.currentPage.toFloat(), probe.pages.pagePosition, 0.01f, "it was left between pages")
    }

    @Test
    fun theArrowsStepAPage() = runComposeUiTest {
        val probe = strip()
        val strip = onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.SelectableGroup))
        strip.requestFocus()
        strip.performKeyInput { pressKey(Key.DirectionRight); pressKey(Key.DirectionRight) }
        waitForIdle()
        assertEquals(2, probe.pages.currentPage)
        strip.performKeyInput { pressKey(Key.MoveEnd) }
        waitForIdle()
        assertEquals(39, probe.pages.currentPage)
    }

    @Test
    fun aScreenReaderGoesToAThumbnailsPage() = runComposeUiTest {
        val probe = strip()
        thumb(1).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        waitForIdle()
        assertEquals(1, probe.pages.currentPage)
    }
}
