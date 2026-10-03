package io.kontour.ui.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.kontour.ui.theme.KontourTheme
import io.kontour.ui.theme.Theme
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A toast has a visible edge over something of its own colour.
 *
 * Reported as *"if a toast appears above a FAB (or any other component of
 * similar colour, for that matter), then there's not really that much
 * separation between them"*. A FAB is the near-black `primary`, a neutral toast
 * the near-black `surfaceInverse`, and the toast's only separation was a black
 * shadow, which shows on nothing that dark. The toasts waiting behind it already
 * had a rim in their own content colour for the same reason; the one in front
 * had none.
 *
 * Drawn over a ground of the toast's own colour, at a density of 1, in both
 * themes: the top edge of the toast, between its rounded ends and above its
 * text, must differ from that ground.
 */
@OptIn(ExperimentalTestApi::class)
class ToastEdgeTest {

    private fun ComposeUiTest.edgeContrast(dark: Boolean): Float {
        var ground = Color.Unspecified
        val toasts = ToastHostState()
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
                KontourTheme(darkTheme = dark, reduceMotion = true) {
                    ground = Theme.colours.surfaceInverse
                    OverlayHost(Modifier.requiredSize(400.dp, 300.dp)) {
                        Box(Modifier.fillMaxSize().background(ground))
                        ToastHost(toasts)
                    }
                }
            }
        }
        toasts.show("Saved to favourites")
        mainClock.advanceTimeBy(800)
        waitForIdle()
        val toast = onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion))
            .fetchSemanticsNodes().first().boundsInRoot
        val image = onRoot().captureToImage().toPixelMap()
        val from = (toast.center.x - 40f).toInt()
        val until = (toast.center.x + 40f).toInt()
        var most = 0f
        for (y in toast.top.toInt()..toast.top.toInt() + 1) {
            for (x in from until until) {
                val c = image[x, y]
                most = maxOf(most, abs(c.red - ground.red), abs(c.green - ground.green), abs(c.blue - ground.blue))
            }
        }
        return most
    }

    @Test
    fun aToastHasAnEdgeOverItsOwnColourInALightTheme() = runComposeUiTest {
        val contrast = edgeContrast(dark = false)
        assertTrue(contrast >= Visible, "the toast's top edge differs from a ground of its own colour by $contrast")
    }

    @Test
    fun aToastHasAnEdgeOverItsOwnColourInADarkTheme() = runComposeUiTest {
        val contrast = edgeContrast(dark = true)
        assertTrue(contrast >= Visible, "the toast's top edge differs from a ground of its own colour by $contrast")
    }

    private companion object {
        /** A channel a tenth of the way across: an edge the eye finds. */
        const val Visible = 0.1f
    }
}
