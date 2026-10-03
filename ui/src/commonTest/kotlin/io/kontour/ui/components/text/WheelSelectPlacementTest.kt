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
 * A wheel select asked to open [WheelSelectPlacement.BelowOrAbove] keeps its
 * chosen row on the field and runs its other rows below it where there is room,
 * and above it near the window's foot, with half a row on the other side; left
 * to its default it opens about the field, as it always has.
 *
 * Asked for as *"can we make the wheel select able to "anchor" the wheel to the
 * top or the bottom of the box that opens? similar to how other select
 * components pick which side they show up based on how close the box is to the
 * edge of the screen"*, and then, of a first version that opened as a menu
 * beside the field, *"can you make it so the wheel picker is still centred on
 * the actual field, but it just shows more elements above/below (depending on
 * the side) and also has some sort of indication that there are more elements
 * on the other side?"*
 *
 * At a density of 1 a row is 40px.
 */
@OptIn(ExperimentalTestApi::class)
class WheelSelectPlacementTest {

    private class Opened(val field: Rect, val wheel: Rect, val chosen: Rect, val before: Rect, val after: Rect)

    /**
     * The field, the open wheel's visible part, and the rows on it — the
     * chosen one, 4, and the ones either side of it — with the field [at] the
     * top or the foot of a phone-sized window.
     */
    private fun ComposeUiTest.open(placement: WheelSelectPlacement, at: Alignment): Opened {
        setContent {
            KontourTheme(darkTheme = false, reduceMotion = true) {
                Box(Modifier.requiredSize(360.dp, 640.dp)) {
                    OverlayHost(Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = at) {
                            WheelSelect(
                                value = 4,
                                options = (1..9).toList(),
                                onValueChange = {},
                                placement = placement,
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

    @Test
    fun nearTheTopItOpensBelowAboutTheValue() = runComposeUiTest {
        val o = open(WheelSelectPlacement.BelowOrAbove, Alignment.TopCenter)
        assertTrue(abs(o.chosen.center.y - o.field.center.y) <= 2f, "the chosen row at ${o.chosen.center.y} is not on the field at ${o.field.center.y}")
        assertTrue(o.wheel.bottom - o.chosen.bottom >= 3.5f * Row, "below the chosen row the wheel shows ${o.wheel.bottom - o.chosen.bottom}px, not its rows")
        val sliver = o.before.height
        assertTrue(sliver > 0f && sliver <= 0.6f * Row, "above the chosen row the wheel shows ${sliver}px of the next, not a sliver of it")
        assertTrue(o.wheel.left >= o.field.left - 1f && o.wheel.right <= o.field.right + 1f, "the wheel ${o.wheel} is not the field's ${o.field} width")
    }

    @Test
    fun nearTheFootItOpensAboveAboutTheValue() = runComposeUiTest {
        val o = open(WheelSelectPlacement.BelowOrAbove, Alignment.BottomCenter)
        assertTrue(abs(o.chosen.center.y - o.field.center.y) <= 2f, "the chosen row at ${o.chosen.center.y} is not on the field at ${o.field.center.y}")
        assertTrue(o.chosen.top - o.wheel.top >= 3.5f * Row, "above the chosen row the wheel shows ${o.chosen.top - o.wheel.top}px, not its rows")
        val sliver = o.after.height
        assertTrue(sliver > 0f && sliver <= 0.6f * Row, "below the chosen row the wheel shows ${sliver}px of the next, not a sliver of it")
    }

    @Test
    fun byDefaultItOpensOverTheField() = runComposeUiTest {
        val o = open(WheelSelectPlacement.OverField, Alignment.Center)
        assertTrue(
            o.wheel.top < o.field.top && o.wheel.bottom > o.field.bottom,
            "the default wheel ${o.wheel} does not open about the field ${o.field}",
        )
    }

    private companion object {
        const val Row = 40f
    }
}
