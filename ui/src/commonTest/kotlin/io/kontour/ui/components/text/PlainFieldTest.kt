package io.kontour.ui.components.text

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
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
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.ContrastLevel
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A select drawn [TextFieldVariant.Plain] has no box round it at rest; an error
 * brings its edge back, and so does high contrast.
 *
 * Asked for as *"can we also make it so the wheel select component (and really
 * any other select component) doesn't have to be in a full-on outlined box? it
 * should be by default, but you should be able to turn it off"*.
 *
 * Read from the frame's leading edge, half way down, over a white page: white
 * where there is no edge.
 */
@OptIn(ExperimentalTestApi::class)
class PlainFieldTest {

    /** How dark the frame's leading edge is: 0 white, 1 black. */
    private fun ComposeUiTest.edge(
        contrast: ContrastLevel = ContrastLevel.Standard,
        field: @Composable (Modifier) -> Unit,
    ): Float {
        setContent {
            KontourTheme(darkTheme = false, reduceMotion = true, contrast = contrast) {
                OverlayHost {
                    Box(Modifier.background(Color.White).padding(16.dp)) {
                        field(Modifier.width(240.dp).testTag("field"))
                    }
                }
            }
        }
        waitForIdle()
        val bounds = onNodeWithTag("field").fetchSemanticsNode().boundsInRoot
        val image = onRoot().captureToImage().toPixelMap()
        val y = bounds.center.y.toInt()
        return (0..1).maxOf { 1f - image[bounds.left.toInt() + it, y].red }
    }

    @Composable
    private fun select(modifier: Modifier, variant: TextFieldVariant, errorMessage: String? = null) =
        Select(
            value = "Bus",
            options = listOf("Bus", "Train"),
            onValueChange = {},
            modifier = modifier,
            variant = variant,
            errorMessage = errorMessage,
        )

    @Test
    fun outlinedHasAnEdge() = runComposeUiTest {
        val edge = edge { select(it, TextFieldVariant.Outlined) }
        assertTrue(edge > 0.2f, "an outlined select's edge is $edge dark, which is no edge")
    }

    @Test
    fun plainHasNone() = runComposeUiTest {
        val edge = edge { select(it, TextFieldVariant.Plain) }
        assertTrue(edge < 0.02f, "a plain select drew an edge $edge dark")
    }

    @Test
    fun aPlainWheelSelectHasNoneEither() = runComposeUiTest {
        val edge = edge {
            WheelSelect(value = 2, options = (1..9).toList(), onValueChange = {}, modifier = it, variant = TextFieldVariant.Plain)
        }
        assertTrue(edge < 0.02f, "a plain wheel select drew an edge $edge dark")
    }

    @Test
    fun anErrorBringsTheEdgeBack() = runComposeUiTest {
        val edge = edge { select(it, TextFieldVariant.Plain, errorMessage = "Pick one") }
        assertTrue(edge > 0.2f, "a plain select in error has no edge to say so, $edge")
    }

    @Test
    fun atHighContrastTheEdgeStays() = runComposeUiTest {
        val edge = edge(ContrastLevel.High) { select(it, TextFieldVariant.Plain) }
        assertTrue(edge > 0.2f, "a plain select at high contrast has no edge, $edge")
    }
}
