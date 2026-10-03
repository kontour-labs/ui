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
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A wheel select asked to open [WheelSelectPlacement.BelowOrAbove] opens below
 * its field where there is room, and above it near the window's foot; left to
 * its default it opens over the field, as it always has.
 *
 * Asked for as *"can we make the wheel select able to "anchor" the wheel to the
 * top or the bottom of the box that opens? similar to how other select
 * components pick which side they show up based on how close the box is to the
 * edge of the screen"*.
 */
@OptIn(ExperimentalTestApi::class)
class WheelSelectPlacementTest {

    /** The field and the open wheel, with the field [at] the top or the foot of a phone-sized window. */
    private fun ComposeUiTest.open(placement: WheelSelectPlacement, at: Alignment): Pair<Rect, Rect> {
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
        return field to wheel
    }

    @Test
    fun nearTheTopItOpensBelow() = runComposeUiTest {
        val (field, wheel) = open(WheelSelectPlacement.BelowOrAbove, Alignment.TopCenter)
        assertTrue(wheel.top >= field.bottom, "the wheel's top at ${wheel.top} is over the field, whose foot is at ${field.bottom}")
        assertTrue(wheel.left >= field.left - 1f && wheel.right <= field.right + 1f, "the wheel $wheel is not the field's $field width")
    }

    @Test
    fun nearTheFootItOpensAbove() = runComposeUiTest {
        val (field, wheel) = open(WheelSelectPlacement.BelowOrAbove, Alignment.BottomCenter)
        assertTrue(wheel.bottom <= field.top, "the wheel's foot at ${wheel.bottom} is over the field, whose top is at ${field.top}")
    }

    @Test
    fun byDefaultItOpensOverTheField() = runComposeUiTest {
        val (field, wheel) = open(WheelSelectPlacement.OverField, Alignment.Center)
        assertTrue(
            wheel.top < field.top && wheel.bottom > field.bottom,
            "the default wheel $wheel does not open about the field $field",
        )
    }
}
