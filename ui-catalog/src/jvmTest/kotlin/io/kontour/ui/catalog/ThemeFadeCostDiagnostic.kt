package io.kontour.ui.catalog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.display.Card
import io.kontour.ui.foundation.Text
import io.kontour.ui.theme.Elevation
import io.kontour.ui.theme.Shadow
import io.kontour.ui.theme.KontourTheme
import io.kontour.ui.theme.kontourElevation
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * What a theme fade costs per frame, and which half of it is the shadows.
 *
 * Reported from a phone: "switching light and dark themes has a very jittery
 * animation". Nothing in this repository had ever measured that. What it *had*
 * measured is next door: `ShadowCostDiagnostic` found that at twenty cards
 * **4.31ms of a 5.59ms frame is shadow**, and `OverlayRecompositionTest` says
 * the lesson in one line — "the cost was never really the recomposition, it is
 * what rides on it".
 *
 * A theme fade is the worst case for that, because it is the one animation whose
 * **shadow parameters genuinely change every frame**: `lerp(Elevation)` walks
 * each layer's alpha and radius from the light scale to the dark one, and the
 * dark scale multiplies alpha by 2.4. So every elevated surface on screen
 * re-rasterises both of its blurs on every frame of the fade, and none of the
 * rasterisation from the frame before can be kept.
 *
 * ### The three scenes
 *
 * Identical trees; one variable each.
 *
 *  - **rest** — no theme change at all. The floor.
 *  - **fade** — `darkTheme` flips, everything default. Colours *and* elevation
 *    interpolate, which is what ships.
 *  - **pinned** — `darkTheme` flips and `elevation` is passed as a constant, so
 *    the colours interpolate and the shadow parameters do not. This needs no
 *    change to `:ui` to try, because `KontourTheme` already takes the elevation
 *    scale as a parameter; that is what makes it a measurement rather than a
 *    proposal.
 *
 * ### Software rasteriser
 *
 * The same caveat `ShadowCostDiagnostic` and `BackdropCostDiagnostic` carry.
 * There is no GPU in a container, so a blur is a per-pixel loop and these
 * numbers are a ceiling rather than a phone's. What survives the difference is
 * the *ratio* between three scenes measured the same way in the same run.
 *
 * A **diagnostic, not a gate**: the assertion is a catastrophe bound, and the
 * useful output is the table.
 */
class ThemeFadeCostDiagnostic {

    @Test
    fun aThemeFadeCostsWhatItsShadowsCost() {
        for (cards in CardCounts) {
            val rest = millisPerFrame(cards, fading = false, elevation = Animated)
            val fade = millisPerFrame(cards, fading = true, elevation = Animated)
            val pinned = millisPerFrame(cards, fading = true, elevation = Pinned)
            val bare = millisPerFrame(cards, fading = true, elevation = NoShadow)
            val restBare = millisPerFrame(cards, fading = false, elevation = NoShadow)
            println(
                ("%3d cards — rest %6.2fms · fade %6.2f (+%.2f) · " +
                    "elevation pinned %6.2f (+%.2f) · " +
                    "no shadow at all: rest %6.2f, fade %6.2f (+%.2f)")
                    .format(
                        cards, rest, fade, fade - rest, pinned, pinned - rest,
                        restBare, bare, bare - restBare,
                    )
            )
        }
        assertTrue(true)
    }

    /**
     * Renders the fade and reports the mean cost of a frame inside it.
     *
     * The flip happens after the warm-up, and only the frames after it are
     * timed — a fade is [FadeFrames] long at 60Hz and the sample is sized to sit
     * inside it, so this is the cost of a frame *while the transition is
     * running* rather than an average diluted by the still frames after it.
     */
    private fun millisPerFrame(cards: Int, fading: Boolean, elevation: Elevation?): Double {
        var dark by mutableStateOf(false)
        var nanos = 0L
        val scene = ImageComposeScene(width = Canvas, height = CanvasHeight, density = Density(1f)) {
            KontourTheme(
                darkTheme = dark,
                reduceMotion = false,
                elevation = elevation ?: kontourElevation(dark),
            ) {
                Cards(cards)
            }
        }
        try {
            repeat(WarmUpFrames) {
                nanos += FrameNanos
                scene.render(nanos).close()
            }
            if (fading) dark = true
            val started = System.nanoTime()
            repeat(Samples) {
                nanos += FrameNanos
                scene.render(nanos).close()
            }
            return (System.nanoTime() - started) / 1_000_000.0 / Samples
        } finally {
            scene.close()
        }
    }

    @Composable
    private fun Cards(cards: Int) {
        Box(Modifier.fillMaxSize()) {
            Column {
                for (row in 0 until (cards + PerRow - 1) / PerRow) {
                    Row {
                        for (column in 0 until PerRow) {
                            if (row * PerRow + column < cards) {
                                Card(modifier = Modifier.padding(6.dp).size(100.dp)) {
                                    Text("card")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private companion object {
        const val PerRow = 5
        const val Canvas = 580
        const val CanvasHeight = 1400
        val CardCounts = listOf(5, 20, 60)

        const val FrameNanos = 16_000_000L
        const val WarmUpFrames = 10

        /** `tweenDefault` is 220ms, which is fourteen frames at 60Hz. */
        const val FadeFrames = 14
        const val Samples = FadeFrames

        /** Null means "whatever the flag resolves to", which is what ships. */
        val Animated: Elevation? = null

        /**
         * A fixed elevation scale: light's, held across the flip.
         *
         * Which scale is beside the point — the measurement is about whether the
         * shadow *parameters* move, not about which shadows are drawn. Light is
         * the cheaper of the two (dark multiplies every alpha by 2.4), so this
         * is the generous reading of the proposal rather than the flattering one.
         */
        val Pinned: Elevation = kontourElevation(dark = false)

        /**
         * No shadow on any tier, so the fade's cost is recomposition and redraw
         * with the rasterisation taken out from under it.
         *
         * `Shadow(emptyList())` rather than a different `CardVariant`, for the
         * reason `ShadowCostDiagnostic` gives: `Modifier.elevation` returns the
         * chain untouched when there are no layers, so the card is identical in
         * every respect except the one being measured.
         */
        val NoShadow: Elevation = Elevation(
            flat = Shadow(emptyList()),
            low = Shadow(emptyList()),
            medium = Shadow(emptyList()),
            high = Shadow(emptyList()),
            overlay = Shadow(emptyList()),
        )
    }
}
