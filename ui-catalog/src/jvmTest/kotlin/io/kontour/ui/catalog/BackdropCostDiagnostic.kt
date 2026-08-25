package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.list.ListItem
import io.kontour.ui.foundation.Text
import io.kontour.ui.overlay.Dialog
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * What a blurred backdrop costs per frame, on this machine.
 *
 * Timed rather than counted, which is a departure. Everywhere else in this suite
 * a stopwatch would be measuring the runner: a recomposition count is the same
 * number on a JVM and on a phone, so that is what gets asserted. But a blur adds
 * no passes to count — the cost *is* rasterisation, one full-screen offscreen
 * render per frame — so there is nothing to count and a number from a stopwatch
 * is the only honest answer.
 *
 * Which makes this a **diagnostic, not a gate**. It asserts only that the blur
 * has not become catastrophic, at a threshold no reasonable machine would trip;
 * the useful output is the ratio it prints. Pinning a millisecond figure here
 * would fail on a loaded CI runner and teach everyone to ignore it.
 *
 * If the ratio ever matters to an app — a live map redrawing under a sheet is
 * the case — `KontourTheme(backdropBlur = false)` turns it off for the same
 * picture minus the texture.
 */
class BackdropCostDiagnostic {

    @Test
    fun blurringTheBackdropCostsLessThanFiveTimesTheFrame() {
        val without = millisPerFrame(blur = false)
        val with = millisPerFrame(blur = true)
        val ratio = with / without

        println(
            "backdrop blur: %.2fms/frame against %.2fms without — %.2f×"
                .format(with, without, ratio)
        )

        assertTrue(
            ratio < 5.0,
            "a blurred backdrop took %.2f× as long to render as an unblurred one " .format(ratio) +
                "(%.2fms against %.2fms). That is past anything a full-screen ".format(with, without) +
                "offscreen pass should cost, so something is rendering more than once.",
        )
    }

    private fun millisPerFrame(blur: Boolean): Double {
        var open by mutableStateOf(false)
        return Scene(width = 420, height = 900, density = 3f) {
            KontourTheme(backdropBlur = blur) {
                OverlayHost(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().background(Color.White)) {
                        LazyColumn(Modifier.padding(16.dp)) {
                            items((1..40).toList()) { n ->
                                ListItem {
                                    +"Elizabeth Quay"
                                    supporting { +"Platform $n · Joondalup line" }
                                }
                            }
                        }
                    }
                    Dialog(visible = open, onDismissRequest = {}) { Text("Rename favourite") }
                }
            }
        }.use { scene ->
            scene.frames(20)
            open = true
            scene.frames(40)

            // Warm, fully open, and every frame from here is the steady state the
            // number is about — not the spring settling.
            val started = System.nanoTime()
            scene.frames(Samples)
            (System.nanoTime() - started) / 1_000_000.0 / Samples
        }
    }

    private companion object {
        const val Samples = 60
    }
}
