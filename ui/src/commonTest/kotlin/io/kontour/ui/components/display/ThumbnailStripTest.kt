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
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.isSelected
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
        val lefts = thumbLefts(first = 3, count = 5, position = 5f, width = 400f, narrow = 28f, wide = 64f, gap = 2f)
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
        val lefts = thumbLefts(first = 5, count = 2, position = 5.5f, width = 400f, narrow = 28f, wide = 64f, gap = 2f)
        // The gap between the two is the middle.
        assertEquals(200f, lefts[0] + 46f + 1f)
        assertEquals(lefts[0] + 46f + 2f, lefts[1])
    }

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
