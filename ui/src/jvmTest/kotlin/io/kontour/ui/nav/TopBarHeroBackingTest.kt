package io.kontour.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.action.IconButton
import io.kontour.ui.foundation.SystemIcons
import io.kontour.ui.theme.KontourTheme
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The discs behind a hero's actions follow the collapse as it is *now*, not as
 * it was when the bar was first composed.
 *
 * The backing that draws them was remembered with a lambda over the first
 * composition's `collapseProgress`. A screen navigated back to is composed again
 * part-way down its scroll, so the discs kept that frame's opacity — faint, or
 * gone — however far back up the reader scrolled.
 */
@OptIn(ExperimentalTestApi::class)
class TopBarHeroBackingTest {

    private fun actionAt(progress: () -> Float, settle: (() -> Unit) -> Unit): Pair<PixelMap, androidx.compose.ui.geometry.Rect> {
        var image: PixelMap? = null
        var bounds = androidx.compose.ui.geometry.Rect.Zero
        runDesktopComposeUiTest(width = 390, height = 400) {
            setContent {
                KontourTheme(darkTheme = false) {
                    Box(Modifier.size(390.dp, 400.dp)) {
                        TopBar(
                            modifier = Modifier.fillMaxWidth(),
                            style = TopBarStyle.Hero,
                            collapseProgress = progress(),
                            image = { Box(Modifier.fillMaxSize().background(Color(0xFF7A3B9A))) },
                            actions = {
                                IconButton(
                                    onClick = {},
                                    icon = SystemIcons.More,
                                    contentDescription = "More",
                                    modifier = Modifier.testTag("action"),
                                )
                            },
                        ) {
                            +"Kings Park"
                        }
                    }
                }
            }
            waitForIdle()
            settle { waitForIdle() }
            bounds = onNodeWithTag("action").fetchSemanticsNode().boundsInRoot
            image = onRoot().captureToImage().toPixelMap()
        }
        return image!! to bounds
    }

    @Test
    fun aBarComposedPartWayDownShowsItsDiscsOnceBackAtTheTop() {
        // Composed open: the discs as they should be at the top.
        val (open, bounds) = actionAt(progress = { 0f }, settle = { it() })

        // Composed part-way down, then scrolled back to the top.
        var progress by mutableFloatStateOf(0.6f)
        val (back, _) = actionAt(progress = { progress }, settle = { idle -> progress = 0f; idle() })

        // A point on the disc, clear of the glyph in its middle.
        val x = (bounds.left + bounds.width * 0.22f).toInt()
        val y = bounds.center.y.toInt()
        val a = open[x, y]
        val b = back[x, y]
        val apart = maxOf(abs(a.red - b.red), abs(a.green - b.green), abs(a.blue - b.blue))
        assertTrue(apart < 0.02f, "the disc back at the top is $b, where a bar composed at the top draws $a")
    }
}
