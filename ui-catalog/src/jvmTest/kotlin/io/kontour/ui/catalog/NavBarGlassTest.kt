package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Bus
import com.composables.icons.tabler.outline.MapPin
import com.composables.icons.tabler.outline.Star
import io.kontour.ui.nav.ModalNavRail
import io.kontour.ui.nav.NavBar
import io.kontour.ui.nav.NavBarStyle
import io.kontour.ui.nav.NavDefaults
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.ContrastLevel
import io.kontour.ui.theme.KontourTheme
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * `glass` is translucent where it is asked for and opaque where it must be.
 *
 * `NavBar(glass = true)` and `ModalNavRail(glass = true)` swap a painted
 * `Surface` for a `GlassSurface`, a second code path with its own tint and its
 * own alpha. Two ways for it to go wrong look alike in a golden and nothing
 * alike on a phone: a pane that is secretly opaque (the flag does nothing) and
 * a pane that stays translucent under high contrast, where the docs promise it
 * goes back to opaque because translucency is the thing that user asked to be
 * rid of.
 *
 * ### Measured as an alpha
 *
 * Over a flat page of a known colour, a pixel of tint laid at alpha `a` is
 * `page + a·(tint − page)`, so `a` can be read back off one pixel of the bar's
 * empty ground. Painted, it is 1. Glass, it is well under — the theme's
 * `glassAlpha`, 0.58 at the time of writing, which the bounds here leave room to
 * tune. High contrast, it is `glassOpaqueAlpha`, 0.94: nearly opaque, which is
 * the floor asserted.
 *
 * The modal rail sits over a dimmed, receded page, where there is no single
 * page colour to solve against. So its page is striped, and the question is
 * the direct one: does the stripe show through the panel.
 */
class NavBarGlassTest {

    /** Far from any surface colour on every channel, so a blend is unmistakable. */
    private val Page = Color(0xFF3355AA)

    @Test
    fun aGlassBarBlendsThePageAndItsTint() {
        for (style in NavBarStyle.entries) {
            val painted = tintAlpha(style, glass = false, contrast = ContrastLevel.Standard)
            assertTrue(
                painted.alpha > 0.97f,
                "the painted $style bar measured alpha ${painted.alpha} (pixel #${painted.hex}) — the sample " +
                    "is not landing on the bar's own ground, so the glass reading below means nothing",
            )

            val glass = tintAlpha(style, glass = true, contrast = ContrastLevel.Standard)
            assertTrue(
                glass.fromPage > 20 && glass.fromTint > 20,
                "a glass $style bar's ground is #${glass.hex}: ${glass.fromPage} from the page and " +
                    "${glass.fromTint} from its tint. Glass is both at once, so it should be clearly neither.",
            )
            assertTrue(
                glass.alpha in 0.3f..0.85f,
                "a glass $style bar lays its tint at alpha ${glass.alpha} — outside what reads as glass",
            )
        }
    }

    /** High contrast takes the translucency away: the glass bar is nearly opaque. */
    @Test
    fun underHighContrastTheGlassBarIsNearlyOpaque() {
        for (style in NavBarStyle.entries) {
            val standard = tintAlpha(style, glass = true, contrast = ContrastLevel.Standard)
            val high = tintAlpha(style, glass = true, contrast = ContrastLevel.High)
            assertTrue(
                high.alpha >= 0.9f,
                "under high contrast a glass $style bar still lays its tint at alpha ${high.alpha} " +
                    "(pixel #${high.hex}) — the page shows through for a user who asked for it not to",
            )
            assertTrue(
                high.alpha > standard.alpha + 0.1f,
                "a glass $style bar is alpha ${standard.alpha} at standard contrast and ${high.alpha} at " +
                    "high — the contrast level is not reaching it",
            )
        }
    }

    /**
     * A glass modal rail's panel shows the page through it; a painted one is one
     * colour however the page under it changes.
     */
    @Test
    fun aGlassModalRailIsTranslucent() {
        val painted = panelColumnRange(glass = false)
        val glass = panelColumnRange(glass = true)

        assertTrue(
            painted <= 3,
            "a painted modal rail's empty panel varies by $painted a channel down one column — it " +
                "should be a single colour, whatever is under it",
        )
        assertTrue(
            glass > 30,
            "a glass modal rail's empty panel varies by only $glass a channel down one column over a " +
                "page striped red and blue — the stripes are not showing through",
        )
    }

    /** What one pixel of a bar's empty ground says about the tint laid over the page. */
    private class Reading(pixel: Int, page: Int, tint: Int) {
        val hex: String = (pixel and 0xFFFFFF).toString(16).padStart(6, '0')
        val fromPage: Int = distance(pixel, page)
        val fromTint: Int = distance(pixel, tint)

        /** Solved on the channel where tint and page are furthest apart. */
        val alpha: Float = run {
            val shifts = listOf(16, 8, 0)
            val shift = shifts.maxBy { abs(channel(tint, it) - channel(page, it)) }
            val span = channel(tint, shift) - channel(page, shift)
            (channel(pixel, shift) - channel(page, shift)).toFloat() / span
        }
    }

    /**
     * Renders a bar of [style] over the flat [Page] and reads the first pixel
     * well inside its ground, along its middle row.
     *
     * Found by walking in from the bar's leading edge until the page stops, not
     * by assuming where the capsule starts: a `Floating` bar is inset from the
     * node that reports its bounds, and a `Docked` one is not. Twelve pixels on
     * from the first that is not page is past the hairline edge a glass pane
     * draws and inside the padding before the first destination.
     */
    private fun tintAlpha(style: NavBarStyle, glass: Boolean, contrast: ContrastLevel): Reading {
        var bounds = Rect.Zero
        var tint = Color.Unspecified
        val image = Scene(width = 800, height = 400) {
            KontourTheme(darkTheme = false, contrast = contrast, reduceMotion = true) {
                tint = NavDefaults.colours().container
                Box(Modifier.fillMaxSize().background(Page)) {
                    NavBar(
                        modifier = Modifier.reportBounds { bounds = it },
                        style = style,
                        glass = glass,
                    ) {
                        item(true, {}, key = "nearby", icon = Tabler.Outline.MapPin) { +"Nearby" }
                        item(false, {}, key = "routes", icon = Tabler.Outline.Bus) { +"Routes" }
                        item(false, {}, key = "saved", icon = Tabler.Outline.Star) { +"Saved" }
                    }
                }
            }
        }.use { it.frames(20) }

        require(bounds.height > 0f) { "the $style bar never reported its bounds" }
        val page = Page.toArgb()
        val y = bounds.center.y.toInt()
        val edge = (bounds.left.toInt() until bounds.right.toInt())
            .firstOrNull { distance(image.getRGB(it, y), page) > 8 }
            ?: error("nothing but page along the $style bar's middle row")
        return Reading(image.getRGB(edge + 12, y), page, tint.toArgb())
    }

    /**
     * How much the modal rail's empty panel varies down one column over a page
     * of red and blue stripes: the largest spread on any channel.
     *
     * The column is well inside the panel's 280dp and below its three
     * destinations, so nothing of the rail's own is drawn there.
     */
    private fun panelColumnRange(glass: Boolean): Int {
        val image = Scene(width = 1200, height = 1200) {
            KontourTheme(darkTheme = false, reduceMotion = true) {
                OverlayHost(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize()) {
                        repeat(30) { band ->
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(20.dp)
                                    .background(if (band % 2 == 0) Color(0xFFE53935) else Color(0xFF1E88E5))
                            )
                        }
                    }
                    ModalNavRail(visible = true, onDismissRequest = {}, glass = glass) {
                        item(true, {}, key = "nearby", icon = Tabler.Outline.MapPin) { +"Nearby" }
                        item(false, {}, key = "routes", icon = Tabler.Outline.Bus) { +"Routes" }
                        item(false, {}, key = "saved", icon = Tabler.Outline.Star) { +"Saved" }
                    }
                }
            }
        }.use { it.frames(60) }

        val x = 100
        val rows = 600 until 1100
        return listOf(16, 8, 0).maxOf { shift ->
            val values = rows.map { channel(image.getRGB(x, it), shift) }
            values.max() - values.min()
        }
    }

    private companion object {
        fun channel(argb: Int, shift: Int): Int = (argb shr shift) and 0xFF

        fun distance(a: Int, b: Int): Int = maxOf(
            abs(channel(a, 16) - channel(b, 16)),
            abs(channel(a, 8) - channel(b, 8)),
            abs(channel(a, 0) - channel(b, 0)),
        )
    }
}
