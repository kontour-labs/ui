package io.kontour.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kontour.ui.theme.ContrastLevel
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Content under a top bar asked to `fadeUnder` fades out beneath it, in every
 * style; and under one that was not asked, or at high contrast, it stops square
 * at the bar's foot.
 *
 * Asked for as *"with all top bar components, can we have an option to allow
 * content to fade underneath the bar? again, shouldn't be the default, but it
 * should be there for all styles"*, and then *"the fade under the top bar
 * looks a bit "fat". it needs to basically end where the line would normally
 * be (or maybe a tiny bit further down)"*: it thinned out over 24dp below the
 * foot, and now over the bar's last 12dp and 4dp past it.
 *
 * The bar is white over black content at a density of 1: white above the fade,
 * between inside it, and black a few pixels past the foot.
 */
@OptIn(ExperimentalTestApi::class)
class TopBarFadeUnderTest {

    /** The red channel of the lines [below] pixels under the bar's foot — above it, when negative — in the middle. */
    private fun ComposeUiTest.under(
        style: TopBarStyle,
        fadeUnder: Boolean,
        below: List<Int>,
        progress: Float = 0f,
        contrast: ContrastLevel = ContrastLevel.Standard,
    ): List<Float> {
        setContent {
            KontourTheme(darkTheme = false, reduceMotion = true, contrast = contrast) {
                Box(Modifier.requiredSize(360.dp, 640.dp)) {
                    Box(Modifier.fillMaxSize().background(Color.Black))
                    TopBar(
                        modifier = Modifier.testTag("bar"),
                        style = style,
                        image = { Box(Modifier.fillMaxSize().background(Color.Black)) },
                        containerColour = Color.White,
                        fadeUnder = fadeUnder,
                        collapseProgress = progress,
                        windowInsets = WindowInsets(0),
                    ) { +"Title" }
                }
            }
        }
        waitForIdle()
        val foot = onNodeWithTag("bar").fetchSemanticsNode().boundsInRoot.bottom.toInt()
        val image = onRoot().captureToImage().toPixelMap()
        return below.map { image[180, foot + it].red }
    }

    /**
     * Collapsed, as a bar is once content is under it: a hero's picture has
     * gone by then, and its foot is the fade's like any other bar's.
     */
    @Test
    fun everyStyleFadesOutAtItsFoot() {
        for (style in TopBarStyle.entries) runComposeUiTest {
            val (above, inside, past) = under(style, fadeUnder = true, below = listOf(-16, -6, 6), progress = 1f)
            assertTrue(above > 0.95f, "$style: above the fade the bar is $above, not its own colour")
            assertTrue(inside in 0.3f..0.95f, "$style: inside the fade the bar is $inside, not part way to the content")
            assertTrue(past < 0.02f, "$style: 6px past the foot the content is $past, not itself — the fade runs on too far")
        }
    }

    @Test
    fun anOpenBarsFadeIsAtItsFootToo() = runComposeUiTest {
        val (above, past) = under(TopBarStyle.Large, fadeUnder = true, below = listOf(-16, 6))
        assertTrue(above > 0.95f, "above an open bar's fade, $above")
        assertTrue(past < 0.02f, "past an open bar's fade, $past")
    }

    @Test
    fun notAskedForTheContentStopsSquare() {
        for (style in TopBarStyle.entries) runComposeUiTest {
            val (inside, past) = under(style, fadeUnder = false, below = listOf(-2, 1))
            assertTrue(inside > 0.95f, "$style: the bar's own foot is $inside, not its colour")
            assertTrue(past < 0.02f, "$style: the bar faded over the content without being asked to")
        }
    }

    @Test
    fun atHighContrastTheContentStopsSquare() = runComposeUiTest {
        val (inside, past) = under(TopBarStyle.Small, fadeUnder = true, below = listOf(-2, 1), contrast = ContrastLevel.High)
        assertTrue(inside > 0.95f, "at high contrast the bar's foot faded, $inside")
        assertTrue(past < 0.02f, "at high contrast the bar faded over the content, $past")
    }
}
