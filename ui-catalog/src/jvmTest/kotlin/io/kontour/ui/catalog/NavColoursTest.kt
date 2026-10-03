package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import io.kontour.icons.tabler.Tabler
import io.kontour.icons.tabler.outline.Bus
import io.kontour.icons.tabler.outline.MapPin
import io.kontour.icons.tabler.outline.Star
import io.kontour.ui.nav.NavBar
import io.kontour.ui.nav.NavColours
import io.kontour.ui.nav.NavDefaults
import io.kontour.ui.nav.NavRail
import io.kontour.ui.nav.NavScope
import io.kontour.ui.theme.Theme
import java.awt.image.BufferedImage
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * `NavColours` reaches the pixels it names, and the defaults are the ones
 * documented.
 *
 * Three claims from `nav-scope.md`, each easy to break without a golden moving
 * enough to notice. An unselected destination is **muted**, as secondary text
 * is — drawn in `content`, which defaults to `contentMuted` rather than the full
 * ink colour. The selected destination's content is **worked out from the
 * indicator**, so an app that picks its own marker keeps what sits on it
 * readable instead of inheriting `accent.onContainer` onto, say, a navy pill.
 * And the marker **casts no shadow**: the surface is the raised thing, and a
 * marker with its own elevation inside it is a second surface on the first.
 *
 * Measured off the rendered frame, reading the colours to expect from the same
 * theme the surface was drawn in rather than from literals — a scheme change
 * should move the expectation with it, not break the test.
 */
class NavColoursTest {

    /** Nothing else on these surfaces draws in this, so any of it on screen is the marker. */
    private val Marker = Color(0xFFFF00FF)

    @Composable
    private fun NavScope.items(unselected: (Rect) -> Unit = {}, selected: (Rect) -> Unit = {}) {
        item(true, {}, key = "nearby", icon = Tabler.Outline.MapPin, modifier = Modifier.reportBounds(selected)) {
            +"Nearby"
        }
        item(false, {}, key = "routes", icon = Tabler.Outline.Bus, modifier = Modifier.reportBounds(unselected)) {
            +"Routes"
        }
        item(false, {}, key = "saved", icon = Tabler.Outline.Star) { +"Saved" }
    }

    /**
     * An unselected icon is drawn in `colours.content`, and by default that is
     * `contentMuted`, not the full ink colour.
     */
    @Test
    fun anUnselectedDestinationIsMuted() {
        var defaults: NavColours? = null
        var muted = Color.Unspecified
        var ink = Color.Unspecified
        var unselected = Rect.Zero
        val image = Scene(width = 800, height = 300, reduceMotion = true) {
            defaults = NavDefaults.colours()
            muted = Theme.colours.contentMuted
            ink = Theme.colours.content
            Box(Modifier.fillMaxSize().background(Color.White)) {
                NavBar { items(unselected = { unselected = it }) }
            }
        }.use { it.frames(20) }

        val colours = requireNotNull(defaults)
        assertEquals(muted, colours.content, "NavDefaults.colours().content is not contentMuted")
        assertNotEquals(muted, ink, "contentMuted and content are one colour, so this cannot tell them apart")

        val drawn = image.farthestFrom(colours.container, unselected.inset(0.2f))
        assertTrue(
            distance(drawn, muted.toArgb()) <= Tolerance,
            "an unselected icon's ink is #${hex(drawn)}; contentMuted is #${hex(muted.toArgb())} " +
                "and full content #${hex(ink.toArgb())}",
        )

        // And a colour of the caller's own is what it draws with.
        val probe = Color(0xFF00A050)
        var custom = Rect.Zero
        var ground = Color.Unspecified
        val painted = Scene(width = 800, height = 300, reduceMotion = true) {
            val chosen = NavDefaults.colours(content = probe)
            ground = chosen.container
            Box(Modifier.fillMaxSize().background(Color.White)) {
                NavBar(colours = chosen) { items(unselected = { custom = it }) }
            }
        }.use { it.frames(20) }
        val probed = painted.farthestFrom(ground, custom.inset(0.2f))
        assertTrue(
            distance(probed, probe.toArgb()) <= Tolerance,
            "with content = #${hex(probe.toArgb())} the unselected icon drew #${hex(probed)}",
        )
    }

    /**
     * A marker of the app's own gets content that reads on it, light on dark and
     * dark on light, because `selectedContent` follows `indicator` by default.
     */
    @Test
    fun aCustomIndicatorKeepsItsContentReadable() {
        val navy = Color(0xFF1A2A6C)
        val amber = Color(0xFFFFD54F)
        var onNavy: NavColours? = null
        var onAmber: NavColours? = null
        var byDefault: NavColours? = null
        var accentOn = Color.Unspecified
        var selected = Rect.Zero

        val image = Scene(width = 800, height = 300, reduceMotion = true) {
            onNavy = NavDefaults.colours(indicator = navy)
            onAmber = NavDefaults.colours(indicator = amber)
            byDefault = NavDefaults.colours()
            accentOn = Theme.colours.accent.onContainer
            Box(Modifier.fillMaxSize().background(Color.White)) {
                NavBar(colours = NavDefaults.colours(indicator = navy)) { items(selected = { selected = it }) }
            }
        }.use { it.frames(30) }

        assertEquals(
            accentOn, requireNotNull(byDefault).selectedContent,
            "the default marker's selected content is not accent.onContainer",
        )
        for ((name, colours) in listOf("navy" to requireNotNull(onNavy), "amber" to requireNotNull(onAmber))) {
            val ratio = contrast(colours.selectedContent, colours.indicator)
            assertTrue(
                ratio >= 4.5f,
                "on a $name marker (#${hex(colours.indicator.toArgb())}) the selected content is " +
                    "#${hex(colours.selectedContent.toArgb())}, a contrast of ${"%.2f".format(ratio)}:1",
            )
        }

        // Drawn, not only computed: the selected icon's ink on the navy circle is
        // the colour worked out for it.
        val expected = requireNotNull(onNavy).selectedContent
        val drawn = image.farthestFrom(navy, selected.inset(0.3f))
        assertTrue(
            distance(drawn, expected.toArgb()) <= Tolerance,
            "the selected icon on a navy marker drew #${hex(drawn)}, not the " +
                "#${hex(expected.toArgb())} its colours worked out",
        )
    }

    /**
     * Just outside the marker is the surface's own colour, in a bar and in a
     * rail: nothing darker around it, as a shadow would be.
     */
    @Test
    fun theMarkerCastsNoShadow() {
        for (rail in listOf(false, true)) {
            val surface = if (rail) "rail" else "bar"
            var container = Color.Unspecified
            val image = Scene(width = 800, height = 600, reduceMotion = true) {
                val colours = NavDefaults.colours(indicator = Marker)
                container = colours.container
                Box(Modifier.fillMaxSize().background(Color.White)) {
                    if (rail) {
                        NavRail(expanded = true, colours = colours) { items() }
                    } else {
                        NavBar(colours = colours) { items() }
                    }
                }
            }.use { it.frames(30) }

            val box = requireNotNull(image.markerBox()) { "the $surface drew no marker" }
            val cx = (box.left + box.right) / 2
            val cy = (box.top + box.bottom) / 2
            val probes = listOf(3, 6).flatMap { d ->
                listOf(
                    "${d}px left of" to (box.left - d to cy),
                    "${d}px right of" to (box.right + d to cy),
                    "${d}px above" to (cx to box.top - d),
                    "${d}px below" to (cx to box.bottom + d),
                )
            }
            for ((where, at) in probes) {
                val (x, y) = at
                val found = image.getRGB(x, y)
                assertTrue(
                    distance(found, container.toArgb()) <= 2,
                    "in the $surface, $where the marker is #${hex(found)}, not the surface's " +
                        "#${hex(container.toArgb())} — the marker is casting something",
                )
            }
        }
    }

    /** The middle of this rect, [fraction] in from every side. */
    private fun Rect.inset(fraction: Float): Rect =
        Rect(left + width * fraction, top + height * fraction, right - width * fraction, bottom - height * fraction)

    /** The pixel in [area] that differs most from [ground]: the ink drawn on it. */
    private fun BufferedImage.farthestFrom(ground: Color, area: Rect): Int {
        require(area.width > 2f && area.height > 2f) { "nothing to sample in $area" }
        val g = ground.toArgb()
        var best = g
        var bestDistance = -1
        for (y in area.top.toInt() until area.bottom.toInt()) {
            for (x in area.left.toInt() until area.right.toInt()) {
                val p = getRGB(x, y)
                val d = distance(p, g)
                if (d > bestDistance) {
                    bestDistance = d
                    best = p
                }
            }
        }
        return best
    }

    /** Pixel edges, inclusive: `right` and `bottom` are the last marked pixels. */
    private class Edges(val left: Int, val top: Int, val right: Int, val bottom: Int)

    /** The magenta marker's extent, or null if it is not drawn. */
    private fun BufferedImage.markerBox(): Edges? {
        var left = Int.MAX_VALUE
        var top = Int.MAX_VALUE
        var right = -1
        var bottom = -1
        for (y in 0 until height) {
            for (x in 0 until width) {
                val p = getRGB(x, y)
                if ((p shr 16 and 0xFF) > 180 && (p and 0xFF) > 180 && (p shr 8 and 0xFF) < 90) {
                    left = min(left, x)
                    right = max(right, x)
                    top = min(top, y)
                    bottom = max(bottom, y)
                }
            }
        }
        return if (right < 0) null else Edges(left, top, right, bottom)
    }

    private fun distance(a: Int, b: Int): Int = maxOf(
        abs(((a shr 16) and 0xFF) - ((b shr 16) and 0xFF)),
        abs(((a shr 8) and 0xFF) - ((b shr 8) and 0xFF)),
        abs((a and 0xFF) - (b and 0xFF)),
    )

    /** WCAG's contrast ratio between two opaque colours. */
    private fun contrast(a: Color, b: Color): Float {
        val la = a.luminance()
        val lb = b.luminance()
        return (max(la, lb) + 0.05f) / (min(la, lb) + 0.05f)
    }

    private fun hex(argb: Int): String = (argb and 0xFFFFFF).toString(16).padStart(6, '0')

    private companion object {
        /**
         * How far the core of an icon's stroke may sit from its tint. A 24dp
         * glyph at this density has a 4px stroke, so its middle is drawn flat
         * and exact; this is headroom for a scaled selected icon, not for a
         * different colour.
         */
        const val Tolerance = 10
    }
}
