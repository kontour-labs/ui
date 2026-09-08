package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Bus
import com.composables.icons.tabler.outline.MapPin
import com.composables.icons.tabler.outline.Star
import io.kontour.ui.nav.NavBar
import io.kontour.ui.nav.NavBarStyle
import io.kontour.ui.nav.NavItem
import io.kontour.ui.theme.KontourTheme
import java.awt.image.BufferedImage
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A floating nav bar is a capsule, because it is holding circles.
 *
 * Reported as "can we make the floating nav bar rounded, since that's holding
 * rounded items", and it is a regression rather than a thing never done: the
 * container was `Theme.shapes.pill` until it moved to `control`, and round 26
 * then capped `control` at `CapsuleCap` underneath it. `nav-surfaces.md` still
 * describes the style as "a capsule inset from every edge" — the documentation
 * was never wrong, the code drifted out from under it.
 *
 * ### The number is arithmetic, not taste
 *
 * The items are `navItemShape`'s `pill` on a 40dp square indicator, so **20dp**
 * of radius, with `spacing.xs` = 8dp of padding above and below them. A
 * container concentric with those is 20 + 8 = **28dp** — and 28 is exactly half
 * the bar's 56dp height, which is what `pill` gives it for free. The capped
 * `control` drew 18.
 *
 * The horizontal padding is 12dp and does not agree, and that is fine: on a
 * capsule the horizontal is a straight run with no corner in it, so only the
 * vertical number has to work out. It is also why `ProvideConcentric` has
 * nothing to publish here — it declines uneven padding on purpose.
 *
 * ### Measured the same way `FabShapeTest` measures a FAB
 *
 * Walk in along the diagonal from the container's corner until the fill starts.
 * A corner of radius `r` puts its arc `r(√2 - 1)` along that diagonal, so a
 * first lit pixel at offset `d` implies `r = d(2 + √2)`. Reported as a fraction
 * of half the bar's height: **1.0 is a capsule**, and the 18dp cap is 0.64.
 *
 * Dark theme, because the container is `surface` — near-white on a light page,
 * which is nothing to threshold against. In dark it is a light bar on a dark
 * ground and the fill separates cleanly from the shadow.
 */
class NavBarFloatingShapeTest {

    @Test
    fun theFloatingBarIsACapsule() {
        val fraction = cornerFraction()
        assertTrue(
            fraction >= RoundEnough,
            "the floating nav bar's corner measured $fraction of half its height. " +
                "It holds 40dp circles with 8dp above and below them, so a " +
                "concentric container is 28dp and that is exactly the 1.0 a " +
                "capsule gives — 0.64 is `control` stopping at CapsuleCap, which " +
                "is an 18dp box around uncapped circles.",
        )
    }

    private fun cornerFraction(): Float {
        var bounds = Rect.Zero
        val image = Scene(width = 800, height = 400, darkTheme = true) {
            KontourTheme(darkTheme = true) {
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    NavBar(
                        items = listOf(
                            NavItem("Nearby", Tabler.Outline.MapPin, onClick = {}),
                            NavItem("Routes", Tabler.Outline.Bus, onClick = {}),
                            NavItem("Saved", Tabler.Outline.Star, onClick = {}),
                        ),
                        selectedIndex = 0,
                        style = NavBarStyle.Floating,
                        modifier = Modifier.reportBounds { bounds = it },
                    )
                }
            }
        }.use { it.frames(6) }

        // `reportBounds` is on the whole `Floating` node, which is a full-width
        // box with the pill centred in it — so the pill's own edges have to be
        // found rather than assumed.
        //
        // **Calibrated against the ground, not against the brightest pixel.**
        // Two attempts got this wrong in the two available directions, and both
        // are worth leaving written down. A hard-coded level guessed above the
        // dark theme's `surface` and found nothing, returning a 0 that reads in a
        // failure message exactly like a bar with no corner. Reading the peak
        // instead found the brightest thing on the row — which is a **glyph**,
        // not the container, so the scan started inside an icon and fell off the
        // gap beside it.
        //
        // The container is the thing being measured, so the threshold belongs
        // just above the ground it sits on: pure black here, and the high
        // elevation's shadow over black is still black.
        val midY = ((bounds.top + bounds.bottom) / 2f).toInt()
        val xs = bounds.left.toInt() until bounds.right.toInt()
        val ground = luminance(image, 2, 2)
        val fill = ground + 10f
        // Sampled where the bar is certainly container and certainly not glyph:
        // the middle of the row, inside `spacing.xs` of padding above the items.
        val body = luminance(image, (bounds.left + bounds.right).toInt() / 2, bounds.top.toInt() + 20)
        require(body > fill) {
            "the bar's own surface measured $body against a ground of $ground, so " +
                "there is nothing here to find an edge of"
        }

        val left = xs.first { luminance(image, it, midY) > fill }
        val probe = left + 40
        val top = (bounds.top.toInt() until bounds.bottom.toInt())
            .first { luminance(image, probe, it) > fill }
        val bottom = (bounds.bottom.toInt() - 1 downTo bounds.top.toInt())
            .first { luminance(image, probe, it) > fill }
        val height = (bottom - top).toFloat()
        require(height > 20f) { "the bar measured ${height}px tall, which is not a bar" }

        for (d in 0 until (height / 2f).toInt()) {
            if (luminance(image, left + d, top + d) > fill) {
                return d * (2f + sqrt(2f)) / (height / 2f)
            }
        }
        return 0f
    }

    private fun luminance(image: BufferedImage, x: Int, y: Int): Float {
        val rgb = image.getRGB(x, y)
        return 0.2126f * ((rgb shr 16) and 0xFF) +
            0.7152f * ((rgb shr 8) and 0xFF) +
            0.0722f * (rgb and 0xFF)
    }

    private companion object {
        /** As in `FabShapeTest`: 1.0 less two pixels' worth of quantisation. */
        const val RoundEnough = 0.88f

    }
}
