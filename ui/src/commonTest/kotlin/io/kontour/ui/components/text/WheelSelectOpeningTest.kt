package io.kontour.ui.components.text

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.KontourTheme
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A wheel select opens with its chosen row on the field wherever the field is:
 * about it where there is room above and below, and to the side with room near
 * the window's top or foot, with half a row on the other side.
 *
 * Asked for as *"with the wheel select, rather than swapping between
 * beloworabove and overfield, can we make it just intelligently select based on
 * how much room is around it?"*. Opened about the field near the top of the
 * window, the wheel used to be pushed down to fit, and its chosen row was no
 * longer on the field.
 *
 * At a density of 1 a row is 40px, and the wheel shows 5.
 */
@OptIn(ExperimentalTestApi::class)
class WheelSelectOpeningTest {

    private class Opened(val field: Rect, val wheel: Rect, val chosen: Rect, val before: Rect, val after: Rect)

    /**
     * The field, the open wheel's visible part, and the rows on it — the
     * chosen one, 4, and the ones either side of it — with the field [at] the
     * top, the middle or the foot of a phone-sized window.
     */
    private fun ComposeUiTest.open(at: Alignment): Opened {
        setContent {
            KontourTheme(darkTheme = false, reduceMotion = true) {
                Box(Modifier.requiredSize(360.dp, 640.dp)) {
                    OverlayHost(Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = at) {
                            WheelSelect(
                                value = 4,
                                options = (1..9).toList(),
                                onValueChange = {},
                                modifier = Modifier.width(240.dp).testTag("field"),
                            )
                        }
                    }
                }
            }
        }
        waitForIdle()
        onNodeWithTag("field").performClick()
        waitForIdle()
        val field = onNodeWithTag("field").fetchSemanticsNode().boundsInRoot
        val wheel = onAllNodes(hasScrollAction()).fetchSemanticsNodes().single().boundsInRoot
        fun row(label: String) = onNode(hasText(label) and hasAnyAncestor(hasScrollAction()), useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        return Opened(field, wheel, row("4"), row("3"), row("5"))
    }

    private fun Opened.assertOnTheField() = assertTrue(
        abs(chosen.center.y - field.center.y) <= 2f,
        "the chosen row at ${chosen.center.y} is not on the field at ${field.center.y}",
    )

    @Test
    fun nearTheTopItOpensBelow() = runComposeUiTest {
        val o = open(Alignment.TopCenter)
        o.assertOnTheField()
        assertTrue(o.wheel.bottom - o.chosen.bottom >= 3.5f * Row, "below the chosen row the wheel shows ${o.wheel.bottom - o.chosen.bottom}px, not its rows")
        val sliver = o.before.height
        assertTrue(sliver > 0f && sliver <= 0.6f * Row, "above the chosen row the wheel shows ${sliver}px of the next, not a sliver of it")
        assertTrue(o.wheel.left >= o.field.left - 1f && o.wheel.right <= o.field.right + 1f, "the wheel ${o.wheel} is not the field's ${o.field} width")
    }

    @Test
    fun nearTheFootItOpensAbove() = runComposeUiTest {
        val o = open(Alignment.BottomCenter)
        o.assertOnTheField()
        assertTrue(o.chosen.top - o.wheel.top >= 3.5f * Row, "above the chosen row the wheel shows ${o.chosen.top - o.wheel.top}px, not its rows")
        val sliver = o.after.height
        assertTrue(sliver > 0f && sliver <= 0.6f * Row, "below the chosen row the wheel shows ${sliver}px of the next, not a sliver of it")
    }

    @Test
    fun withRoomEitherSideItOpensAboutTheField() = runComposeUiTest {
        val o = open(Alignment.Center)
        o.assertOnTheField()
        val above = o.chosen.top - o.wheel.top
        val below = o.wheel.bottom - o.chosen.bottom
        assertTrue(abs(above - below) <= 1f, "the wheel opens $above px above and $below px below, not evenly about the field")
        assertTrue(above in 2f * Row..2.5f * Row, "the wheel shows ${above}px either side, not its two rows")
        assertTrue(o.before.top > o.wheel.top && o.after.bottom < o.wheel.bottom, "a row either side is cut: ${o.before} ${o.after} in ${o.wheel}")
    }

    private companion object {
        const val Row = 40f
    }
}
