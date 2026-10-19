package io.kontour.ui.catalog

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.kontour.ui.foundation.Text
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.sheet.ModalBottomSheet
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertTrue
import org.junit.experimental.categories.Category

/**
 * What a modal sheet's receding page costs over a page that redraws itself on
 * every frame — a map, a video — while the sheet arrives.
 *
 * Reported from Android and iOS: *"with busy content (e.g. a map or a video),
 * if a modal sheet comes up and does the backdrop scale animation, it tends to
 * drop quite a few frames"*. `BackdropCostDiagnostic` measures the backdrop
 * over a still page, whose layer is recorded once and only re-composited, so it
 * never saw a frame where the page and the backdrop both had work to do.
 *
 * The page here is a canvas of a few hundred strokes moved every frame. The
 * same sheet arrives over it twice, receding the page and with
 * `recedes = false`, and the difference is what the recede costs: the scaled
 * layer and the band round it. The band was one screen-sized path cut afresh on
 * every frame until `drawAround`; it is rectangles and cached corners now.
 *
 * A **diagnostic, not a gate**, for the reasons `BackdropCostDiagnostic` gives:
 * a software rasteriser on a shared runner. It prints the figures and asserts
 * only a bound no reasonable machine trips. What a platform view costs to move
 * — the surface Android and iOS re-place on every frame of the recede, which
 * `recedes = false` exists for — is not something a JVM can show at all.
 */
@Category(Slow::class)
class BusyBackdropCostDiagnostic {

    @Test
    fun aRecedingPageCostsLittleOverOneThatDoesNotMove() {
        val receding = arriving(recedes = true)
        val still = arriving(recedes = false)
        println(
            "busy page under an arriving sheet: receding %.1fms/frame against %.1f still (%.2f×), worst %.1f against %.1f"
                .format(receding.average(), still.average(), receding.average() / still.average(), receding.max(), still.max())
        )
        assertTrue(
            receding.average() < still.average() * 3.0,
            ("a receding page under an arriving sheet took %.2f× as long per frame as one held still, over a " +
                "page that redraws every frame. Something the recede draws has started costing like the " +
                "page itself — a path rebuilt per frame, a clip, an offscreen.")
                .format(receding.average() / still.average()),
        )
    }

    /** Per-frame milliseconds for [Frames] frames from the moment a sheet is shown over [Busy]. */
    private fun arriving(recedes: Boolean): DoubleArray {
        var open by mutableStateOf(false)
        return Scene(width = PhoneWidthPx, height = PhoneHeightPx, density = 3f) {
            OverlayHost(Modifier.fillMaxSize()) {
                Busy()
                ModalBottomSheet(visible = open, onDismissRequest = { open = false }, recedes = recedes) {
                    Box(Modifier.fillMaxWidth().height(320.dp)) { Text("Perth Underground") }
                }
            }
        }.use { scene ->
            scene.advance(WarmUpFrames)
            open = true
            DoubleArray(Frames) {
                val started = System.nanoTime()
                scene.advance()
                (System.nanoTime() - started) / 1_000_000.0
            }
        }
    }

    /** A map's worth of strokes, moved on every frame. */
    @Composable
    private fun Busy() {
        var now by remember { mutableLongStateOf(0L) }
        LaunchedEffect(Unit) {
            while (true) withFrameNanos { now = it }
        }
        Canvas(Modifier.fillMaxSize().background(Color(0xFFEDEFE6))) {
            val t = now / 1_000_000_000f
            for (i in 0 until Strokes) {
                val a = i * 0.37f + t
                val from = Offset(size.width * (0.5f + 0.45f * sin(a)), size.height * (0.5f + 0.45f * cos(a * 1.3f)))
                val to = Offset(from.x + 120f * cos(a * 2f), from.y + 120f * sin(a * 2f))
                drawLine(Color(0xFF6B8F71), from, to, strokeWidth = 6f)
            }
        }
    }

    private companion object {
        /** A phone, in the device pixels `Scene` takes: see `BackdropCostDiagnostic`. */
        const val PhoneWidthPx = 1170
        const val PhoneHeightPx = 2532
        const val WarmUpFrames = 10

        /** The sheet's arrival and a little after: every frame of it recedes the page. */
        const val Frames = 30
        const val Strokes = 400
    }
}
