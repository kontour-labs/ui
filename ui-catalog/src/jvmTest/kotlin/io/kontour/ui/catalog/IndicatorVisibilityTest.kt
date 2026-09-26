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
 * ### The floor, and the two schemes that meet it differently
 *
 * It was `3.0` — WCAG 1.4.11, what identifying a control's state is held to —
 * and it passed because the thumb was drawn with a 1dp `outlineStrong` border
 * in every scheme. That border was removed at a reader's request and the floor
 * came down to the honest consequence, `1.45`, carried by a fill on a track
 * token dark enough to show it.
 *
 * The ground is `surfaceSunken` now — the same well a filled text field uses,
 * because a segmented control and a text field two greys apart read as two
 * design systems — and the two schemes answer it from opposite ends:
 *
 * | scheme | carrier | measured | floor |
 * |---|---|---|---|
 * | dark | `surfaceIndicator` | **1.59:1** | 1.45 |
 * | dark/high-contrast | the same | **1.94:1** | 1.45 |
 * | light | the shadow, and the label | **1.17:1** | 1.15 |
 * | light/high-contrast | the same | **1.16:1** | 1.15 |
 *
 * Dark comes out further apart than the arrangement it replaces (1.53 and
 * 1.52), on a quieter ground, with no border. Light cannot: white is the top of
 * the ramp and the well is `#F6F6F6`, so its fill is 1.08:1 whatever token it
 * reads, and `elevation.medium` under it is worth **0.09** of a ratio.
 *
 * **A hairline was built and measured and reaches 3.60:1** — WCAG 1.4.11
 * outright — and was rejected on the rendering rather than the number: on a
 * near-white ground a 1dp `outlineStrong` is a hard dark stroke around the
 * selected segment, which is the apology an earlier round already removed once.
 *
 * So there are two floors, because there are two situations. Neither is 3:1 and
 * the library does not claim otherwise.
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
                    val required = if (dark) DarkFloor else LightFloor
                    val ratio = edgeContrast(theme, dark, tier)
                    if (ratio < required) {
                        weak += "  · $name: ${(ratio * 100).toInt() / 100.0}:1, needs $required:1"
                    }
                }
            }
        }
        assertTrue(
            weak.isEmpty(),
            "the selected segment does not stand out from its ground:\n" +
                weak.joinToString("\n") +
                "\nThe ground is `surfaceSunken`. Dark separates on fill — " +
                "`surfaceIndicator`, 1.59:1 and 1.94:1 — and light cannot separate " +
                "on fill at all, so the two are held to different numbers. A " +
                "failure here is one of three things: `SegmentedControl` no longer " +
                "drawing `surfaceIndicator`, a scheme whose indicator sits too close " +
                "to its well, or a theme that set the well and forgot to lift the " +
                "indicator off it — which is what GTurbo did, at 1.28:1.",
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
                            selectedIndex = 2,
                            onSelectedIndexChange = {},
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

        // And down the thumb's *bottom* edge, which is where the shadow is.
        //
        // The row above only ever saw the fill: a drop shadow has a positive
        // `offsetY`, so at a row above the label there is nothing of it to
        // find, and this file used to say so in its own KDoc — "the shadow is
        // real and a reader gets it, but it falls outside the row this samples".
        // That was a fair disclaimer while the fill carried every scheme. It is
        // not fair now that light has nothing *but* the shadow, because then
        // the instrument is measuring the one carrier that was removed.
        //
        // Taken a few pixels inside the thumb's left edge rather than at its
        // centre, so the label's glyphs cannot be mistaken for an edge — the
        // same care the row above takes, in the other axis.
        val column = edge + GlyphClearance
        for (dy in 0..BottomWindow) {
            val ratio = contrastRatio(colourAt(bitmap, column, y + dy), track)
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
        /**
         * What dark is held to, and it is the old number unchanged.
         *
         * `surfaceIndicator` reaches 1.59:1 in plain dark and 1.94:1 at the
         * enhanced tier, so the floor has headroom rather than having been
         * moved to fit. GTurbo, the one out-of-tree theme in the tree, sits at
         * 1.57.
         */
        const val DarkFloor = 1.45f

        /**
         * What light is held to, and it is a smaller number for a real reason.
         *
         * Light cannot separate a thumb on fill. White is the top of the ramp
         * and `surfaceSunken` is `#F6F6F6`, so the pair is 1.08:1 whatever
         * token the thumb reads, and `elevation.medium` under it is worth 0.09
         * — measured, not estimated: the whole edge comes to 1.17:1.
         *
         * **A hairline was built and measured and reaches 3.60:1**, clearing
         * WCAG 1.4.11 outright, and it was rejected on the rendering: on a
         * near-white ground a 1dp `outlineStrong` is a hard dark stroke around
         * the selected segment, which is the apology an earlier round removed.
         * The number was better and the control was worse. That is the trade
         * this constant records.
         *
         * So light is carried by the shadow and by the label going
         * `contentMuted` to `content` — iOS's own segmented control makes the
         * same trade at 1.15:1. 1.15 rather than 1.17 so a font metric or an
         * antialiasing change does not turn a rendering detail into a failure;
         * it is close enough to the measurement to catch the thing this
         * actually guards, which is the shadow being dropped or the ground
         * being changed under the thumb.
         *
         * **This is not WCAG 1.4.11 and the library does not claim it is.** The
         * accessibility page says so in the same words.
         */
        const val LightFloor = 1.15f

        /** Far enough inside the thumb to clear the label, near enough to stay on it. */
        const val GlyphClearance = 8

        /**
         * How far below the sampling row to look, in pixels at 2x.
         *
         * Derived, and the derivation is the point: the control is 44dp tall
         * and centred, so at 2x its bottom edge is 44px under the centre and
         * the sampling row starts 22px above it. The thumb is inset by a 6dp
         * `segmentedTrackPadding`, putting its bottom edge 54px down and the
         * track's 66px down.
         *
         * 64 stops **two pixels inside the track**, and that is the whole of
         * the number. At 90 this ran off the track onto the page and picked up
         * the enhanced tier's `contrastEdge()` outline — a strong step that
         * belongs to the track's own border, not to the thumb's edge — and the
         * light enhanced scheme "passed" at a stroke it does not draw. An
         * instrument that leaves the control measures something else.
         */
        const val BottomWindow = 58

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
