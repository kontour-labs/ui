package io.kontour.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.display.rememberCarouselState
import io.kontour.ui.theme.KontourTheme
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The pill a drag along a hero's dots brings up, in the light, where it sits on
 * the bar's own colour: *"on light mode, you can't really see the bubble that
 * pops up when you drag"* and, on an iPad, *"that popup bubble gets cut off"*.
 * Between the dots and past the end ones, it stands off the bar's ground.
 */
@OptIn(ExperimentalTestApi::class)
class TopBarHeroDotsTest {

    @Test
    fun theScrubPillShowsWholeOnALightBar() {
        for ((w, h) in listOf(390 to 844, 1180 to 820)) runDesktopComposeUiTest(width = w, height = h) {
            setContent {
                KontourTheme(darkTheme = false) {
                    Box(Modifier.size(w.dp, h.dp)) {
                        val pictures = rememberCarouselState { 3 }
                        TopBar(
                            modifier = Modifier.fillMaxWidth(),
                            style = TopBarStyle.Hero,
                            onBack = {},
                            image = {
                                HeroImages(pictures) {
                                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF8EC5FC), Color(0xFF7A6B9A)))))
                                }
                            },
                        ) {
                            +"Kings Park"
                            supporting { +"Fraser Avenue" }
                        }
                    }
                }
            }
            waitForIdle()
            val dots = onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected), useUnmergedTree = true)
                .fetchSemanticsNodes().map { it.boundsInRoot }.sortedBy { it.left }
            check(dots.size == 3) { "the fixture has three dots: $dots" }
            val y = dots[0].center.y
            mainClock.autoAdvance = false
            onRoot().performTouchInput {
                down(dots[0].center)
                moveBy(Offset(30f, 0f))
                moveBy(Offset(10f, 0f))
            }
            mainClock.advanceTimeBy(400)
            val image = onRoot().captureToImage().toPixelMap()
            onRoot().performTouchInput { up() }
            mainClock.autoAdvance = true

            val ground = image[(dots[0].left - 60f).toInt(), y.toInt()]
            fun standsOff(x: Float, what: String) {
                val c = image[x.toInt(), y.toInt()]
                val apart = abs(c.luminance() - ground.luminance())
                assertTrue(apart > 0.12f, "at ${w}dp the pill $what is ${c} against the bar's $ground: ${apart} apart")
            }
            standsOff((dots[0].right + dots[1].left) / 2f, "between the dots")
            standsOff(dots[0].left - 5f, "before the first dot")
            standsOff(dots[2].right + 5f, "after the last dot")
        }
    }
}
