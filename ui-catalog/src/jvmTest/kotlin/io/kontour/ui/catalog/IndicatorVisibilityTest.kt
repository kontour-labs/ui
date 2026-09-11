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
 * `contrastFailures` walks a scheme thoroughly and uses the surface tokens
 * **only ever as backgrounds**. It never pairs them against each other, so a
 * segmented thumb against its own track was checked by nothing, in any test, in
 * any module. Measured by hand it was **1.07:1 in dark and 1.08 in light**.
 *
 * ### This floor is a house floor, and it used to be WCAG's
 *
 * It was `3.0` — WCAG 1.4.11, what identifying a control's state is held to —
 * and it passed because the thumb was drawn with a 1dp `outlineStrong` border.
 * That border is gone, at a reader's request, and the number below is the honest
 * consequence rather than a quiet relabelling.
 *
 * **Why 3:1 was not available without it.** No two greys in one monochrome ramp
 * reach 3:1. A white thumb needs a `#959595` track, which is not a recessed
 * ground at all but a dark bar. The choice was a permanent visible line around
 * every selected segment or a fill that falls short of a boundary's standard,
 * and the line was what a reader objected to.
 *
 * **What carries the state now**, in place of one line: the fill, at 1.52–1.54
 * across the built-in schemes rather than 1.08 — this is what
 * `ColourScheme.surfaceTrack` was added for, a ground dark enough for a thumb
 * that does not drag every code block and text field down with it; the shadow
 * under the thumb, which works in light and is why light and dark are not the
 * same case; and the label, which goes `contentMuted` → `content` when a
 * segment is selected and is the one carrier that survives a reader who cannot
 * distinguish the greys at all.
 *
 * It is the trade iOS makes with its own segmented control, and it is a trade
 * rather than a free win: someone who could find the old border and cannot find
 * this fill has lost something real. What they have not lost is the label.
 *
 * ### Why this is a render and not a scheme walk
 *
 * A scheme cannot answer it. `SurfaceLadderTest` checks that the *tokens* are
 * far enough apart, and would go on passing if `SegmentedControl` stopped using
 * them — a token-level check cannot see what a component draws. So this
 * photographs the control and measures the step the way an eye meets it: the
 * strongest transition across the thumb's edge, against the track a few pixels
 * outside it. The two halves are a pair.
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
            "the selected segment does not stand out from its track by $Required:1:\n" +
                weak.joinToString("\n") +
                "\nThe thumb has no border: it is a lighter fill on `surfaceTrack`, " +
                "a shadow in light, and a label that darkens to `content`. The fill " +
                "is most of that, and it measures 1.52-1.54 in the built-in schemes. " +
                "A failure here is one of three things: `SegmentedControl` no longer " +
                "drawing `surfaceTrack`, a scheme whose track sits too close to its " +
                "thumb, or a theme that set the track and forgot to lift the thumb " +
                "off it — which is what GTurbo did, at 1.28:1, before its " +
                "`Raised` was corrected.",
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

        /**
         * A house floor, deliberately below WCAG 1.4.11's 3:1. See the KDoc.
         *
         * Measured: Kontour 1.54 light, 1.54 light/high, 1.53 dark, 1.52
         * dark/high; GTurbo 1.56 in both its tiers.
         *
         * Those are the *fill* ratios and nothing else — light reads exactly the
         * 1.54 the two tokens are apart, so the thumb's shadow contributes
         * nothing this can see. That is worth knowing rather than assuming: the
         * shadow is real and a reader gets it, but it falls outside the row this
         * samples, so do not read a passing number here as evidence the shadow
         * is doing any work.
         */
        const val Required = 1.45f

        /**
         * How far either side of the boundary to look, in pixels.
         *
         * This used to be sized for a 1dp border — two pixels at 2×, three or
         * four once antialiased. There is no border now, but the window is still
         * the right width: a fill edge is antialiased too, and in light the
         * thumb's shadow darkens the track for a few pixels outside it, which is
         * part of what separates the two and should be inside the window. Six is
         * comfortably narrower than half a segment, so this cannot wander onto a
         * label.
         */
        const val EdgeWindow = 6
    }
}
