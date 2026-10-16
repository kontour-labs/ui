package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.list.ListItem
import io.kontour.ui.overlay.BackdropDefaults
import io.kontour.ui.overlay.BackdropStyle
import io.kontour.ui.overlay.OverlayEntry
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.overlay.OverlayHostState
import io.kontour.ui.overlay.OverlayLayer
import io.kontour.ui.overlay.ScrimStyle
import java.awt.image.BufferedImage
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue
import org.junit.experimental.categories.Category

/**
 * A dialog's backdrop, blurred at a quarter of its size, is the full-size blur
 * to within a few levels in 255.
 *
 * On Skia every frame drawn while a dialog is open blurs the whole application
 * again, so `overlayBackdrop` draws it at a quarter of its size, blurs it by a
 * quarter of the radius and scales the result back up. The promise that makes
 * that worth doing is that nobody can see it, and this is that promise
 * measured: the page under a dimming overlay that blurs it, against the same
 * page under the same overlay with the blur it used to apply — a full-size
 * `BlurEffect` on the page's layer — put there by hand.
 *
 * The page is the worst case on purpose: blocks of saturated colour edge to
 * edge and a band of black and white stripes, which is where a coarser blur
 * would show first, above an ordinary list. Rendered at 2x so the settled
 * radius is reduced by four, as it is on a phone.
 *
 * **Measured: no channel more than 6 levels apart, and 0.66 on average.** The
 * bounds below are about twice that — far below what reads as a change in a
 * picture with no edges left in it to see one by.
 */
@Category(Slow::class)
class BackdropReducedBlurTest {

    @Test
    fun aDialogsBackdropIsTheFullSizeBlurToWithinAFewLevels() {
        val reference = settled(BackdropStyle.None) { page ->
            val radius = with(LocalDensity.current) { BackdropDefaults.BlurRadius.toPx() }
            Box(Modifier.fillMaxSize().graphicsLayer { renderEffect = BlurEffect(radius, radius, TileMode.Clamp) }) {
                page()
            }
        }
        val reduced = settled(BackdropStyle.Blur) { page -> page() }

        val (worst, mean) = difference(reference, reduced)
        println("reduced blur against the full-size one: worst channel %d, mean %.3f".format(worst, mean))
        assertTrue(
            worst <= WorstLevels,
            "a dialog's backdrop differed from the full-size blur by $worst levels in 255 at its " +
                "worst, against at most $WorstLevels. Measured at 6 when the blur was first drawn " +
                "smaller: something has made the reduced blur coarser — a larger reduction, or a " +
                "smaller blur done at it.",
        )
        assertTrue(
            mean <= MeanLevels,
            "a dialog's backdrop differed from the full-size blur by %.2f levels on average, against at most %.1f"
                .format(mean, MeanLevels),
        )
    }

    /** The page under a dimming overlay with nothing in it, once the overlay has arrived. */
    private fun settled(backdrop: BackdropStyle, wrap: @Composable (page: @Composable () -> Unit) -> Unit): BufferedImage {
        val host = OverlayHostState()
        return Scene(width = Width, height = Height, density = Density) {
            OverlayHost(Modifier.fillMaxSize(), host) { wrap { Page() } }
        }.use { scene ->
            scene.frames(4)
            host.show(
                OverlayEntry(
                    key = "dialog",
                    layer = OverlayLayer.Dialog,
                    scrim = ScrimStyle.Dimmed,
                    backdrop = backdrop,
                    content = {},
                )
            )
            scene.frames(SettleFrames)
        }
    }

    /** The largest difference in any channel, and the mean of each pixel's largest. */
    private fun difference(a: BufferedImage, b: BufferedImage): Pair<Int, Double> {
        var worst = 0
        var total = 0L
        for (y in 0 until a.height) for (x in 0 until a.width) {
            val p = a.getRGB(x, y)
            val q = b.getRGB(x, y)
            var most = 0
            for (shift in intArrayOf(0, 8, 16, 24)) {
                most = maxOf(most, abs(((p shr shift) and 0xFF) - ((q shr shift) and 0xFF)))
            }
            worst = maxOf(worst, most)
            total += most
        }
        return worst to total.toDouble() / (a.width * a.height)
    }

    @Composable
    private fun Page() {
        Column(Modifier.fillMaxSize().background(Color.White)) {
            Row(Modifier.fillMaxWidth().height(120.dp)) {
                for (colour in listOf(Color.Red, Color.Black, Color.Yellow, Color.Blue, Color.Green, Color.Black, Color.White, Color.Magenta)) {
                    Box(Modifier.weight(1f).height(120.dp).background(colour))
                }
            }
            Row(Modifier.fillMaxWidth().height(40.dp)) {
                repeat(40) { i ->
                    Box(Modifier.weight(1f).height(40.dp).background(if (i % 2 == 0) Color.Black else Color.White))
                }
            }
            LazyColumn(Modifier.padding(16.dp)) {
                items((1..30).toList()) { n ->
                    ListItem {
                        +"Elizabeth Quay"
                        supporting { +"Platform $n · Joondalup line" }
                    }
                }
            }
        }
    }

    private companion object {
        const val Width = 780
        const val Height = 1688
        const val Density = 2f

        /** Well past the entry's spring. */
        const val SettleFrames = 40

        const val WorstLevels = 12
        const val MeanLevels = 1.5
    }
}
