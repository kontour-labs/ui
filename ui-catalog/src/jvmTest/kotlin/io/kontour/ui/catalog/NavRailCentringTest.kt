package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.kontour.ui.nav.NavRail
import io.kontour.ui.nav.NavSearch
import io.kontour.ui.nav.rememberNavSearchState
import io.kontour.ui.overlay.OverlayHost
import java.awt.image.BufferedImage
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A collapsed rail's icons are on its centre line, whatever is in it and
 * whatever it is beside, and they stay where they are as it grows.
 *
 * Reported for 0.5.6: *"when it's collapsed, the icons don't tend to be
 * centred. can we think about making that better, without the icons moving
 * when the sheet is animating"*. The destinations were centred only at the
 * default 88dp, by the coincidence of an 8dp edge and a 12dp row inset; a
 * header or a footer put its content at the rows' edge, so a 44dp `IconButton`
 * sat 2dp left of the icons above it, and anything smaller further; and a cutout on the rail's side came out of
 * its 88dp. Three cases, one for each.
 *
 * Measured in pixels off a solid square glyph, because a destination's bounds
 * are its whole row: the glyph is the only thing that says where the icon is.
 */
class NavRailCentringTest {

    /** Read live: each is wherever the last layout put it. */
    private class Measured(
        private val railNow: () -> Rect,
        private val destinationNow: () -> Rect,
        private val footerNow: () -> Rect,
        private val headerNow: () -> Rect,
    ) {
        val rail get() = railNow()
        val destination get() = destinationNow()
        val footer get() = footerNow()
        val header get() = headerNow()
    }

    private fun rail(
        // `NavRailDefaults.CollapsedWidth`, which is read from the theme.
        collapsedWidth: Dp = 88.dp,
        insets: WindowInsets = WindowInsets(0),
        check: Scene.(Measured, setExpanded: (Boolean) -> Unit) -> Unit,
    ) {
        var expanded by mutableStateOf(false)
        var rail = Rect.Zero
        var destination = Rect.Zero
        var footer = Rect.Zero
        var header = Rect.Zero
        Scene(width = 800, height = 600, density = 2f) {
            OverlayHost {
                Row(Modifier.fillMaxSize()) {
                    NavRail(
                        modifier = Modifier.reportBounds { rail = it },
                        expanded = expanded,
                        onExpandedChange = { expanded = it },
                        collapsedWidth = collapsedWidth,
                        windowInsets = insets,
                        // What most headers and footers hold: a search pill, an
                        // `IconButton`. Narrower than a glyph box, both of them.
                        header = { Box(Modifier.size(44.dp).background(Color.Black).reportBounds { header = it }) },
                        footer = { Box(Modifier.size(40.dp).background(Color.Black).reportBounds { footer = it }) },
                    ) {
                        item(false, {}, key = "square", icon = Square, modifier = Modifier.reportBounds { destination = it }) { +"Square" }
                    }
                }
            }
        }.use { scene ->
            scene.frames(20)
            scene.check(Measured({ rail }, { destination }, { footer }, { header })) { expanded = it }
        }
    }

    /** The left and right of the glyph's ink in the destination's row, from [from] across. */
    private fun ink(image: BufferedImage, row: Rect, from: Int, to: Int): IntRange {
        val y = row.center.y.toInt()
        val xs = (from until to).filter { x -> (image.getRGB(x, y) shr 16 and 0xFF) < 160 }
        assertTrue(xs.isNotEmpty(), "no glyph in the row at y=$y")
        return xs.first()..xs.last()
    }

    private fun assertCentred(what: String, measured: Measured, inset: Float, scene: Scene) {
        val image = scene.frame()
        // The rail's own column, past whatever it keeps clear of.
        val centre = (measured.rail.left + inset + measured.rail.right) / 2f
        val glyph = ink(image, measured.destination, measured.rail.left.toInt(), measured.rail.right.toInt())
        val glyphCentre = (glyph.first + glyph.last + 1) / 2f
        assertTrue(abs(glyphCentre - centre) <= 1f, "$what: the icon's centre is at $glyphCentre px, the rail's at $centre")
        assertTrue(abs(measured.header.center.x - centre) <= 1f, "$what: the header's 44dp pill is centred at ${measured.header.center.x} px, the rail at $centre")
        assertTrue(abs(measured.footer.center.x - centre) <= 1f, "$what: the footer's 40dp button is centred at ${measured.footer.center.x} px, the rail at $centre")
    }

    @Test
    fun atTheDefaultWidth() = rail { m, _ -> assertCentred("88dp", m, inset = 0f, scene = this) }

    @Test
    fun atAWidthOfItsOwn() = rail(collapsedWidth = 80.dp) { m, _ ->
        assertEquals(160f, m.rail.width, "the rail is not the 80dp it was given")
        assertCentred("80dp", m, inset = 0f, scene = this)
    }

    @Test
    fun besideACutout() = rail(insets = WindowInsets(left = 30.dp)) { m, _ ->
        assertEquals(176f + 60f, m.rail.width, "the cutout came out of the rail's 88dp rather than being added to it")
        assertCentred("a 30dp cutout", m, inset = 60f, scene = this)
    }

    /** Centring is no use if it is a place the icons leave as the rail grows. */
    @Test
    fun theIconsHoldStillAsTheRailGrows() = rail { m, expand ->
        val left = m.rail.left.toInt()
        val start = ink(frame(), m.destination, left, left + 176).first
        val footer = m.footer.left
        expand(true)
        repeat(30) { i ->
            val image = frame()
            // The glyph's left edge: the label arrives to its right.
            val now = ink(image, m.destination, left, left + 176).first
            assertEquals(start, now, "frame $i of the growth: the icon moved from $start px to $now")
        }
        assertEquals(footer, m.footer.left, "the footer moved as the rail grew, from $footer px to ${m.footer.left}")
    }

    /**
     * A search in the header holds its icon on the destinations' as the rail
     * grows: round and centred, then a pill with its word, then a field.
     *
     * Reported for 0.5.6 as *"the search icon jump"*: the pill had its icon 2dp
     * short of the icons and the field 6dp, so it stepped left twice on the way
     * open, the second time by 4dp as the pill became the field.
     */
    @Test
    fun aSearchInTheHeaderHoldsItsIconStill() {
        var expanded by mutableStateOf(false)
        var rail = Rect.Zero
        var search = Rect.Zero
        Scene(width = 800, height = 600, density = 2f) {
            OverlayHost {
                Row(Modifier.fillMaxSize()) {
                    NavRail(
                        modifier = Modifier.reportBounds { rail = it },
                        expanded = expanded,
                        onExpandedChange = { expanded = it },
                        windowInsets = WindowInsets(0),
                        header = {
                            NavSearch(
                                state = rememberNavSearchState(),
                                modifier = Modifier.reportBounds { search = it },
                                searchIcon = Square,
                            )
                        },
                    ) {
                        item(false, {}, key = "square", icon = Square) { +"Square" }
                    }
                }
            }
        }.use { scene ->
            scene.frames(20)
            val left = rail.left.toInt()
            val collapsed = ink(scene.frame(), search, left, left + 176)
            val centre = (rail.left + rail.right) / 2f
            assertTrue(
                abs((collapsed.first + collapsed.last + 1) / 2f - centre) <= 1f,
                "the collapsed search's icon is at ${collapsed.first}..${collapsed.last} px, the rail's centre at $centre",
            )
            expanded = true
            repeat(40) { i ->
                // The icon's left edge: the word and the field's text come to its right.
                val now = ink(scene.frame(), search, left, left + 176).first
                assertEquals(collapsed.first, now, "frame $i of the growth: the search icon moved from ${collapsed.first} px to $now")
            }
        }
    }

    private companion object {
        /** A solid 16dp square in a 24dp glyph, whose ink is exactly where it is. */
        val Square: ImageVector = ImageVector.Builder("square", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(4f, 4f)
                lineTo(20f, 4f)
                lineTo(20f, 20f)
                lineTo(4f, 20f)
                close()
            }
        }.build()
    }
}
