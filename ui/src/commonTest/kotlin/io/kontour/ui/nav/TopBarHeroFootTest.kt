package io.kontour.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertTrue
import io.kontour.ui.theme.KontourTheme

/**
 * Wherever a hero bar is in its collapse, its last line of pixels is the bar's
 * colour, with none of the picture showing through.
 *
 * Reported as *"when scrolling to collapse the hero image, there's a little line
 * (where i assume the image ends, but it's normally hidden by the fade) that
 * flashes as you scroll"*. The bar is a whole number of pixels tall, and the
 * fade over the picture stopped where the bar's foot would be unrounded: up to
 * half a pixel short of the last line, on every other few pixels of scroll.
 *
 * The picture is black and the bar white, so any of the picture in the last
 * line darkens it.
 */
@OptIn(ExperimentalTestApi::class)
class TopBarHeroFootTest {

    @Test
    fun theLastLineIsTheBarsColourAllTheWayThrough() = runComposeUiTest {
        var progress by mutableFloatStateOf(0f)
        setContent {
            KontourTheme(darkTheme = false, reduceMotion = false) {
                Box(Modifier.width(360.dp)) {
                    TopBar(
                        modifier = Modifier.testTag("bar"),
                        style = TopBarStyle.Hero,
                        image = { Box(Modifier.fillMaxSize().background(Color.Black)) },
                        containerColour = Color.White,
                        collapseProgress = progress,
                        windowInsets = WindowInsets(0),
                    ) { +"Title" }
                }
            }
        }
        val darkest = mutableListOf<Pair<Float, Float>>()
        // Up to where the picture has faded out of its own accord.
        for (step in 0..85) {
            progress = step / 100f
            waitForIdle()
            val image = onNodeWithTag("bar").captureToImage().toPixelMap()
            val last = image.height - 1
            val line = (0 until image.width step 7).minOf { image[it, last].red }
            if (line < 0.99f) darkest += progress to line
        }
        assertTrue(
            darkest.isEmpty(),
            "the picture shows through the bar's last line at " +
                darkest.joinToString { (p, red) -> "p=$p (${(red * 100).roundToInt()}% white)" },
        )
        // And the bar did collapse across the run, so the line moved.
        assertTrue(abs(progress - 0.85f) < 0.001f)
    }
}
