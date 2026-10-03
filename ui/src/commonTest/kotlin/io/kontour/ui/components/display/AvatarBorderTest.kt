package io.kontour.ui.components.display

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
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
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * An avatar given a border draws it round its circle, under its decorations;
 * one given none draws none.
 *
 * Asked for as *"can we add an option for a border around the avatar?"*.
 *
 * The border is red, which no avatar is drawn in: a red pixel just inside the
 * circle's top is the border, and its middle is still the avatar's own.
 */
@OptIn(ExperimentalTestApi::class)
class AvatarBorderTest {

    private fun ComposeUiTest.avatar(border: BorderStroke?): Pair<Color, Color> {
        setContent {
            KontourTheme(darkTheme = false) {
                Box(Modifier.background(Color.White).padding(16.dp)) {
                    Avatar(name = "Aaron", size = AvatarSize.Large, border = border, modifier = Modifier.testTag("avatar"))
                }
            }
        }
        waitForIdle()
        val bounds: Rect = onNodeWithTag("avatar").fetchSemanticsNode().boundsInRoot
        val image = onRoot().captureToImage().toPixelMap()
        val x = bounds.center.x.toInt()
        return image[x, bounds.top.toInt() + 1] to image[x, bounds.center.y.toInt() - 12]
    }

    private fun Color.isRed() = red > 0.9f && green < 0.1f && blue < 0.1f

    @Test
    fun aBorderRingsTheAvatar() = runComposeUiTest {
        val (rim, inside) = avatar(BorderStroke(3.dp, Color.Red))
        assertTrue(rim.isRed(), "the avatar's rim is $rim, not its border")
        assertTrue(!inside.isRed(), "the border runs into the avatar's middle")
    }

    @Test
    fun noneByDefault() = runComposeUiTest {
        val (rim, _) = avatar(null)
        assertTrue(!rim.isRed(), "an avatar with no border drew one: $rim")
    }
}
