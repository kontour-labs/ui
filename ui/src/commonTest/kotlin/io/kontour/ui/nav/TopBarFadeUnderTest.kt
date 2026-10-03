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
 * should be there for all styles"*.
 *
 * The bar is white over black content, at a density of 1, so the 24dp fade is
 * 24px: nearly white just under the foot, half way half way down, and black
 * past its end.
 */
@OptIn(ExperimentalTestApi::class)
class TopBarFadeUnderTest {

    /** The red channel of the line [below] pixels under the bar's foot, in the middle. */
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

    @Test
    fun everyStyleFadesOutBelowItsFoot() {
        for (style in TopBarStyle.entries) runComposeUiTest {
            val (justUnder, halfway, past) = under(style, fadeUnder = true, below = listOf(1, 12, 26))
            assertTrue(justUnder > 0.9f, "$style: just under its foot the bar's colour is ${justUnder}, not the bar's")
            assertTrue(halfway in 0.3f..0.7f, "$style: half way down the fade is $halfway, not half the bar's colour")
            assertTrue(past < 0.02f, "$style: past the fade's end the content is $past, not itself")
        }
    }

    @Test
    fun aCollapsedBarsFadeFollowsItsFoot() = runComposeUiTest {
        val (justUnder, past) = under(TopBarStyle.Large, fadeUnder = true, below = listOf(1, 26), progress = 1f)
        assertTrue(justUnder > 0.9f, "under a collapsed bar's foot, $justUnder")
        assertTrue(past < 0.02f, "past a collapsed bar's fade, $past")
    }

    @Test
    fun notAskedForTheContentStopsSquare() {
        for (style in TopBarStyle.entries) runComposeUiTest {
            val (justUnder) = under(style, fadeUnder = false, below = listOf(1))
            assertTrue(justUnder < 0.02f, "$style: the bar faded over the content without being asked to")
        }
    }

    @Test
    fun atHighContrastTheContentStopsSquare() = runComposeUiTest {
        val (justUnder) = under(TopBarStyle.Small, fadeUnder = true, below = listOf(1), contrast = ContrastLevel.High)
        assertTrue(justUnder < 0.02f, "at high contrast the bar faded over the content, $justUnder")
    }
}
