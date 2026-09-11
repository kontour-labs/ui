package io.kontour.ui.catalog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.kontour.ui.a11y.contrastRatio
import io.kontour.ui.components.selection.SegmentedControl
import io.kontour.ui.demo.theme.DemoTheme
import io.kontour.ui.demo.theme.DemoThemeProvider
import io.kontour.ui.demo.theme.demoThemes
import io.kontour.ui.foundation.Surface
import io.kontour.ui.theme.ContrastLevel
import io.kontour.ui.theme.Theme
import org.jetbrains.skia.Bitmap
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The selection indicator is visible against the thing it sits on, in every scheme.
 *
 * Reported from a phone: the selection indicator is "not very visible in the
 * Kontour theme", and the segmented control's "almost invisible in both dark
 * themes".
 *
 * ### The pairing nothing checked
 *
 * `contrastFailures` walks a scheme thoroughly and uses the four surface tokens
 * **only ever as backgrounds**. It never pairs them against each other, so
 * `surface` against `surfaceSunken` — a segmented thumb against its own track —
 * was checked by nothing, in any test, in any module. Measured by hand it is
 * **1.07:1 in dark and 1.08 in light**, against the 3:1 WCAG 1.4.11 asks of
 * anything that identifies a control's state.
 *
 * In light a shadow does the separating. `kontourElevation(dark = true)` draws
 * `low` as two *black* layers, so in dark there is nothing left to darken: the
 * number is the same in both and only one of them has a fallback.
 *
 * ### Why this is a render and not a scheme walk
 *
 * A scheme cannot answer it. `outlineStrong` clears 3:1 against every ground and
 * against `accent.container` in all six schemes — its own KDoc says so and that
 * was already true — so a token-level check passes whether or not any component
 * *draws* it. What was missing was the drawing. So this photographs the control
 * and measures the boundary the way an eye meets it: the strongest step across
 * the thumb's edge, against the track a few pixels outside it.
 *
 * ### Every scheme the demo layer can build
 *
 * Both themes, both modes and both tiers, minus the combinations a theme
 * declines — which is how GTurbo's enhanced tier is covered, and its enhanced
 * tier is where `contrastEdge()` was drawing a `#2A2A2B` border at 1.29:1.
 */
class IndicatorVisibilityTest {

    @Test
    fun theSegmentedThumbIsBoundedInEveryScheme() {
        val weak = mutableListOf<String>()
        for (theme in demoThemes) {
            for (dark in listOf(false, true)) {
                if (theme.resolveDark(dark) != dark) continue
                for (tier in ContrastLevel.entries) {
                    if (theme.resolveTier(tier) != tier) continue
                    val name = "${theme.name}/${if (dark) "dark" else "light"}/$tier"
                    val ratio = edgeContrast(theme, dark, tier)
                    if (ratio < Required) weak += "  · $name: ${(ratio * 100).toInt() / 100.0}:1"
                }
            }
        }
        assertTrue(
            weak.isEmpty(),
            "the selected segment's boundary is under $Required:1 against its track:\n" +
                weak.joinToString("\n") +
                "\nThe thumb is `surface` and the track is `surfaceSunken`, which " +
                "are about 1.08:1 apart in every scheme — so the separation has to " +
                "come from the edge. `SegmentedControl` draws " +
                "`outlineStrong`, whose own contract is 3:1 against every ground; " +
                "a failure here is either that edge gone or a scheme whose " +
                "`outlineStrong` does not keep its promise.",
        )
    }

    /**
     * The strongest step across the thumb's left edge.
     *
     * The track is sampled well outside the thumb and each pixel in a window
     * spanning the boundary is taken against it; the best of them is what a
     * reader has to find the edge by. Taken on a row above the label, so a glyph
     * cannot be mistaken for a border.
     */
    private fun edgeContrast(theme: DemoTheme, dark: Boolean, tier: ContrastLevel): Float {
        val settings = CatalogSettings().apply {
            this.theme = theme
            this.dark = dark
            this.highContrast = tier == ContrastLevel.High
            this.reduceMotion = true
        }
        val scene = ImageComposeScene(width = Width, height = Height, density = Density(2f)) {
            DemoThemeProvider(settings, dark, tier == ContrastLevel.High, true) {
                Surface(Modifier.fillMaxSize(), colour = Theme.colours.background) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        SegmentedControl(
                            options = listOf("One", "Two", "Three", "Four"),
                            selected = 2,
                            onSelectedChange = {},
                            modifier = Modifier.width(320.dp),
                        )
                    }
                }
            }
        }
        val bitmap = try {
            var image = scene.render(0L)
            repeat(20) { image = scene.render(16_000_000L * (it + 1)) }
            Bitmap.makeFromImage(image)
        } finally {
            scene.close()
        }

        // The control is 320dp wide and centred at 2×, so the track spans the
        // middle 640px. Four equal segments make the third one's left edge a
        // quarter-width past the midpoint's start.
        val trackLeft = (Width - 640) / 2
        val segment = 640 / 4
        val edge = trackLeft + segment * 2
        val y = Height / 2 - 22

        val track = colourAt(bitmap, edge - segment / 2, y)
        var best = 1f
        for (x in (edge - EdgeWindow)..(edge + EdgeWindow)) {
            val ratio = contrastRatio(colourAt(bitmap, x, y), track)
            if (ratio > best) best = ratio
        }
        return best
    }

    private fun colourAt(bitmap: Bitmap, x: Int, y: Int): Color = Color(bitmap.getColor(x, y))

    private companion object {
        const val Width = 800
        const val Height = 260

        /** WCAG 1.4.11, which is what identifying a control's state is held to. */
        const val Required = 3.0f

        /**
         * How far either side of the boundary to look, in pixels.
         *
         * A 1dp border at 2× is two pixels and antialiasing spreads it over three
         * or four. Six is comfortably wider than that and comfortably narrower
         * than half a segment, so this cannot wander onto a label.
         */
        const val EdgeWindow = 6
    }
}
