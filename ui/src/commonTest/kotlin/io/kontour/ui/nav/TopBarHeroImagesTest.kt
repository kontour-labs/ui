package io.kontour.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.display.CarouselState
import io.kontour.ui.components.display.rememberCarouselState
import io.kontour.ui.theme.KontourTheme
import io.kontour.ui.theme.Theme
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A hero bar's picture can be several, swiped between, with the carousel's own
 * page dots over the fade just above the title.
 *
 * Asked for as *"with the hero header, can we make an option for a carousel of
 * images. try to use our existing carousel and page indicators"*.
 *
 * The pictures are flat colours far from the theme's, and the title is blue,
 * so the dots can be found by their own colours: the current page's pill in
 * `primary`, the others in `inactive`.
 */
@OptIn(ExperimentalTestApi::class)
class TopBarHeroImagesTest {

    private lateinit var pages: CarouselState
    private var active = Color.Unspecified
    private var inactive = Color.Unspecified
    private var progress by mutableFloatStateOf(0f)

    private fun ComposeUiTest.hero(count: Int = 3) = setContent {
        KontourTheme(darkTheme = false, reduceMotion = false) {
            active = Theme.colours.primary
            inactive = Theme.colours.inactive
            pages = rememberCarouselState { count }
            Box(Modifier.width(360.dp)) {
                TopBar(
                    modifier = Modifier.testTag("bar"),
                    style = TopBarStyle.Hero,
                    image = {
                        HeroImages(pages) { page -> Box(Modifier.fillMaxSize().background(Pictures[page])) }
                    },
                    containerColour = Color.White,
                    contentColour = Ink,
                    collapseProgress = progress,
                    windowInsets = WindowInsets(0),
                ) { +"Platform 9" }
            }
        }
    }

    private fun ComposeUiTest.bar(): PixelMap = onNodeWithTag("bar").captureToImage().toPixelMap()

    @Test
    fun aSwipeAcrossThePictureTurnsIt() = runComposeUiTest {
        hero()
        onNodeWithTag("bar").performTouchInput { swipeLeft(startX = right - 8f, endX = left + 8f) }
        waitForIdle()
        assertEquals(1, pages.currentPage, "a swipe from right to left over the picture should show the next one")
        // And the picture under the bar is the second one.
        val image = bar()
        assertTrue(close(image[image.width / 2, 8], Pictures[1]), "the bar should show the second picture: ${image[image.width / 2, 8]}")
    }

    @Test
    fun theDotsSitCentredJustAboveTheTitle() = runComposeUiTest {
        hero()
        val dots = assertNotNull(box(bar(), listOf(active, inactive)), "a hero with three pictures should show their dots")
        val title = onNodeWithText("Platform 9").fetchSemanticsNode().boundsInRoot
        val bar = onNodeWithTag("bar").fetchSemanticsNode().boundsInRoot
        val gap = (title.top - bar.top) - dots.bottom
        assertTrue(gap > 0f, "the dots should be above the title: dots $dots, title $title")
        assertTrue(gap < 32f * density.density, "the dots should be just above the title, not ${gap / density.density}dp above it")
        assertTrue(abs(dots.center.x - bar.width / 2f) <= 2f, "the dots should be centred: $dots in a bar ${bar.width} wide")
    }

    @Test
    fun aDotTappedTurnsToItsPicture() = runComposeUiTest {
        hero()
        val dots = assertNotNull(box(bar(), listOf(active, inactive)))
        // The last of three dots, at the strip's far end.
        onNodeWithTag("bar").performTouchInput { click(Offset(dots.right - 3f, dots.center.y)) }
        waitForIdle()
        assertEquals(2, pages.currentPage, "a tap on the last dot should turn to the last picture")
    }

    @Test
    fun theDotsAreGoneOnceTheBarIsAThirdCollapsed() = runComposeUiTest {
        hero()
        progress = 0.4f
        waitForIdle()
        assertNull(box(bar(), listOf(active, inactive)), "the dots should be gone well before the title reaches the bar's first line")
    }

    @Test
    fun onePictureHasNoDots() = runComposeUiTest {
        hero(count = 1)
        assertNull(box(bar(), listOf(active, inactive)), "one picture has nothing to page between")
    }

    @Test
    fun thePicturesAndTheirDotsSayNothingToAScreenReader() = runComposeUiTest {
        hero()
        // The tree a screen reader walks: what is cleared is not in it.
        val said = onAllNodes(hasContentDescription("Images") or SemanticsMatcher.keyIsDefined(SemanticsProperties.StateDescription))
            .fetchSemanticsNodes()
        assertTrue(said.isEmpty(), "a hero's pictures are decorative, but a reader was told ${said.map { it.config }}")
    }

    @Test
    fun outsideAHeroBarItIsACarouselWithItsDotsAtTheFoot() = runComposeUiTest {
        setContent {
            KontourTheme(darkTheme = false, reduceMotion = false) {
                active = Theme.colours.primary
                pages = rememberCarouselState { 3 }
                Box(Modifier.requiredSize(360.dp, 240.dp).testTag("bar")) {
                    HeroImages(pages) { page -> Box(Modifier.fillMaxSize().background(Pictures[page])) }
                }
            }
        }
        onNode(hasContentDescription("Images")).assertExists("outside a hero the carousel is announced as what it is")
        val image = bar()
        val dots = assertNotNull(box(image, listOf(active)), "the dots should be drawn")
        assertTrue(dots.top > image.height * 0.75f, "the dots should be at the pictures' foot: $dots in ${image.height}")
    }

    private companion object {
        val Pictures = listOf(Color(0xFF00897B), Color(0xFFF9A825), Color(0xFFAD1457))

        /** The title's ink: neither the dots' colours nor anything a blend of them with white makes. */
        val Ink = Color(0xFF1565C0)

        fun close(pixel: Color, colour: Color): Boolean =
            abs(pixel.red - colour.red) <= 0.025f &&
                abs(pixel.green - colour.green) <= 0.025f &&
                abs(pixel.blue - colour.blue) <= 0.025f

        /** The box round every pixel of any of [colours], or null if there are too few to be a dot. */
        fun box(image: PixelMap, colours: List<Color>): Rect? {
            var left = Int.MAX_VALUE
            var top = Int.MAX_VALUE
            var right = -1
            var bottom = -1
            var count = 0
            for (y in 0 until image.height) {
                for (x in 0 until image.width) {
                    val pixel = image[x, y]
                    if (colours.none { close(pixel, it) }) continue
                    count++
                    if (x < left) left = x
                    if (x > right) right = x
                    if (y < top) top = y
                    if (y > bottom) bottom = y
                }
            }
            if (count < 12) return null
            return Rect(left.toFloat(), top.toFloat(), right + 1f, bottom + 1f)
        }
    }
}
