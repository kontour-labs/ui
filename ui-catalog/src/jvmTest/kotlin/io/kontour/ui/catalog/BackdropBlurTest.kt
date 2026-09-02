package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.kontour.ui.foundation.Surface
import io.kontour.ui.foundation.Text
import io.kontour.ui.overlay.Dialog
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.sheet.ModalBottomSheet
import io.kontour.ui.theme.KontourTheme
import io.kontour.ui.theme.Theme
import java.awt.image.BufferedImage
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The content behind a modal is really blurred, and the shadows in it survive.
 *
 * Both halves of that are things this codebase has been caught on before. A
 * `renderEffect` the backend cannot apply is dropped **silently** — no crash, no
 * warning, a sharp copy of the backdrop — so "it looks like it works" and "the
 * modifier is a no-op" are the same picture, and only a measurement separates
 * them. And `OverlayAppearance` already records that a layer over the overlay
 * tree can suppress `Modifier.dropShadow` in the nodes beneath it, which would
 * flatten every card on the screen the moment a dialog opened.
 *
 * ### Measured against blur turned off, not against nothing
 *
 * A modal darkens what is behind it as well as blurring it, and darkening alone
 * compresses the contrast between neighbouring pixels. So a test that compared
 * "modal open" against "modal closed" would pass on the scrim and prove nothing
 * about the blur. Every comparison here is the same scene at the same frame with
 * `KontourTheme(backdropBlur = …)` as the only difference.
 */
class BackdropBlurTest {

    @Test
    fun aDialogBlursTheContentBehindIt() {
        val sharp = stripeRoughness(blur = false) { open ->
            Dialog(visible = open, onDismissRequest = {}) { Text("Rename favourite") }
        }
        val blurred = stripeRoughness(blur = true) { open ->
            Dialog(visible = open, onDismissRequest = {}) { Text("Rename favourite") }
        }

        assertTrue(sharp > 20f, "the stripes did not draw at all: roughness was $sharp")
        assertTrue(
            blurred < sharp / 3f,
            "the content behind a dialog measured $blurred against $sharp with the " +
                "blur off — a render effect the backend cannot apply is dropped in " +
                "silence, so this is what tells a working blur from a no-op",
        )
    }

    @Test
    fun aSheetBlursTheContentBehindItToo() {
        val sharp = stripeRoughness(blur = false) { open ->
            ModalBottomSheet(visible = open, onDismissRequest = {}) { Text("Departures") }
        }
        val blurred = stripeRoughness(blur = true) { open ->
            ModalBottomSheet(visible = open, onDismissRequest = {}) { Text("Departures") }
        }

        assertTrue(sharp > 20f, "the stripes did not draw at all: roughness was $sharp")
        assertTrue(blurred < sharp / 3f, "$blurred against $sharp")
    }

    @Test
    fun aSheetPushesThePresentingContentBack() {
        // The other half of BlurAndScale. A sheet recedes the screen it is
        // presented from, so the content no longer reaches the window's edge and
        // the ground behind it shows through.
        val closed = edgeColumn(open = false)
        val open = edgeColumn(open = true)

        assertTrue(
            closed > 120f,
            "the content should reach the window edge with nothing open, but the " +
                "edge measured $closed",
        )
        assertTrue(
            open < 60f,
            "with a sheet open the presenting content should have pulled away from " +
                "the window edge, leaving the ground — the edge measured $open",
        )
    }

    @Test
    fun theGroundBehindASheetDoesNotCoverTheScreen() {
        // Found by looking at a golden, which is the only reason it was found at
        // all. The ground a receding sheet needs behind it was filling the whole
        // host and relying on the content to draw over it — and nothing requires
        // an app's content to be opaque. Where it is not, the screen went black.
        //
        // The white here is on the *host*, under the ground, so it stands in for
        // the window an app would actually be sitting on. Content that paints
        // nothing is the case that broke.
        var visible by mutableStateOf(false)
        val image = Scene(width = 600, height = 900) {
            OverlayHost(Modifier.fillMaxSize().background(Color.White)) {
                Box(Modifier.fillMaxSize())
                ModalBottomSheet(visible = visible, onDismissRequest = {}) { Text("Departures") }
            }
        }.use { scene ->
            scene.frames(4)
            visible = true
            scene.frames(80)
        }

        // Well inside the receded content and well above the sheet.
        val middle = (200..400).map { luminance(image, it, 120) }.average()
        // And in the band the recession vacated, two pixels from the edge.
        val band = (100..300).map { luminance(image, 2, it) }.average()

        assertTrue(
            middle > 60.0,
            "the middle of the screen behind a sheet measured $middle — the ground " +
                "has covered the whole host rather than only the band the " +
                "scale-back vacated",
        )
        assertTrue(
            band < middle,
            "the vacated band measured $band against $middle in the middle, so the " +
                "ground is not filling it at all",
        )
    }

    @Test
    fun aShadowInsideTheContentStillDraws() {
        // The `CompositingStrategy.ModulateAlpha` class of bug: a layer wrapped
        // around the content can stop `Modifier.dropShadow` drawing in the nodes
        // under it. Both regions sampled here sit under the same scrim, so the
        // difference between them is the shadow and nothing else.
        val closed = shadowDepth(open = false)
        val open = shadowDepth(open = true)

        assertTrue(closed > 4f, "the card cast no shadow to begin with: $closed")
        assertTrue(
            open > 1f,
            "the card's shadow measured $open with a dialog open against $closed " +
                "without one — a layer over the content has suppressed it",
        )
    }

    /**
     * How sharp the striped backdrop still is, sampled across a row above the
     * overlay: the mean change in brightness between neighbouring pixels.
     *
     * Vertical stripes and a horizontal sample, so every step across the row
     * crosses an edge. Blur is exactly the operation that flattens this.
     */
    private fun stripeRoughness(blur: Boolean, overlay: @Composable (Boolean) -> Unit): Float {
        var open by mutableStateOf(false)
        val image = Scene(width = 600, height = 900) {
            KontourTheme(backdropBlur = blur) {
                OverlayHost(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().background(Color.White).stripes())
                    overlay(open)
                }
            }
        }.use { scene ->
            scene.frames(4)
            open = true
            scene.frames(60)
        }

        val y = 40
        var total = 0f
        var count = 0
        for (x in 1 until image.width) {
            total += abs(luminance(image, x, y) - luminance(image, x - 1, y))
            count++
        }
        return total / count
    }

    /** The mean brightness of a column two pixels in from the window's edge. */
    private fun edgeColumn(open: Boolean): Float {
        var visible by mutableStateOf(false)
        val image = Scene(width = 600, height = 900) {
            OverlayHost(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().background(Color.White))
                ModalBottomSheet(visible = visible, onDismissRequest = {}) { Text("Departures") }
            }
        }.use { scene ->
            scene.frames(4)
            visible = open
            scene.frames(80)
        }
        return (100 until 300).map { luminance(image, 2, it) }.average().toFloat()
    }

    /**
     * How much darker the page is beside a raised card than well away from it.
     *
     * The shadow, isolated: both samples are under whatever the modal is doing,
     * so anything that is not the shadow cancels.
     */
    private fun shadowDepth(open: Boolean): Float {
        var visible by mutableStateOf(false)
        val image = Scene(width = 600, height = 900) {
            OverlayHost(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().background(Color.White), contentAlignment = Alignment.TopCenter) {
                    Surface(
                        modifier = Modifier.size(160.dp, 60.dp),
                        shape = Theme.shapes.medium,
                        colour = Color.White,
                        shadow = Theme.elevation.high,
                        content = {},
                    )
                }
                Dialog(visible = visible, onDismissRequest = {}) { Text("Rename favourite") }
            }
        }.use { scene ->
            scene.frames(4)
            visible = open
            scene.frames(60)
        }

        // The card is 160dp wide at density 2 and centred, so it spans roughly
        // x = 140..460 and ends at y = 120. Just under it is shadow; the far left
        // of the same row is not.
        val beneath = (200..400).map { luminance(image, it, 132) }.average().toFloat()
        val away = (10..60).map { luminance(image, it, 132) }.average().toFloat()
        return away - beneath
    }

    private fun Modifier.stripes(): Modifier = drawBehind {
        var x = 0f
        while (x < size.width) {
            drawRect(Color.Black, topLeft = Offset(x, 0f), size = Size(4f, size.height))
            x += 8f
        }
    }

    private fun luminance(image: BufferedImage, x: Int, y: Int): Float {
        val rgb = image.getRGB(x, y)
        val r = (rgb shr 16) and 0xFF
        val g = (rgb shr 8) and 0xFF
        val b = rgb and 0xFF
        return 0.2126f * r + 0.7152f * g + 0.0722f * b
    }

}
