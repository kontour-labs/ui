package io.kontour.ui.motion

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.display.Card
import io.kontour.ui.foundation.Text
import io.kontour.ui.theme.KontourTheme
import io.kontour.ui.theme.Theme
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A card carried between two pages by `sharedBounds` keeps its shadow while it
 * travels.
 *
 * Reported as *"in page transition, the shadow gets cut off a bit during the
 * transition"*. The card's contents cross-faded in an alpha layer as big as the
 * card, and a layer at part opacity is drawn offscreen at its own size, so the
 * shadow outside the card was cut off for the whole transition and came back
 * the frame it ended.
 *
 * The card here is in the same place on both pages, so the only thing the
 * transition changes is how it is drawn: the shadow under its foot, a few
 * pixels below it, is read at rest and half way through.
 */
@OptIn(ExperimentalTestApi::class)
class SharedBoundsShadowTest {

    private var detail by mutableStateOf(false)

    private fun ComposeUiTest.pages(): Rect {
        mainClock.autoAdvance = false
        setContent {
            KontourTheme(darkTheme = false, reduceMotion = false) {
                Box(Modifier.requiredSize(360.dp, 400.dp).background(Color.White)) {
                    PageTransition(target = detail, modifier = Modifier.fillMaxSize()) { open ->
                        Box(Modifier.fillMaxSize().padding(40.dp)) {
                            Card(
                                modifier = Modifier
                                    .testTag(if (open) "detail" else "list")
                                    .sharedBounds("card", clip = Theme.shapes.large)
                                    .fillMaxWidth()
                                    .height(120.dp),
                            ) {
                                Text(if (open) "Perth Underground, 4 platforms" else "Perth Underground")
                            }
                        }
                    }
                }
            }
        }
        mainClock.advanceTimeBy(1_000)
        return onNodeWithTag("list").fetchSemanticsNode().boundsInRoot
    }

    /** How dark the band just under [card] is, against the white page: the shadow. */
    private fun PixelMap.shadowUnder(card: Rect): Float {
        val y = card.bottom.toInt()
        return (2..6).sumOf { dy ->
            (card.left.toInt() + 40 until card.right.toInt() - 40 step 8).sumOf { x -> (1f - this[x, y + dy].red).toDouble() }
        }.toFloat()
    }

    @Test
    fun theShadowIsThereHalfWayThrough() = runComposeUiTest {
        val card = pages()
        val atRest = onRoot().captureToImage().toPixelMap().shadowUnder(card)
        assertTrue(atRest > 0.05f, "the card has no shadow to lose: $atRest")

        detail = true
        mainClock.advanceTimeBy(160)
        val halfWay = onRoot().captureToImage().toPixelMap().shadowUnder(card)
        assertTrue(
            halfWay >= atRest * 0.4f,
            "half way through the transition the card's shadow is $halfWay, against $atRest at rest — cut off",
        )
    }
}
