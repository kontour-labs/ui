package io.kontour.ui.catalog

import androidx.compose.ui.graphics.vector.ImageVector
import io.kontour.icons.tabler.Tabler
import io.kontour.icons.tabler.outline.Bus
import io.kontour.icons.tabler.outline.Calendar
import io.kontour.icons.tabler.outline.Check
import io.kontour.icons.tabler.outline.ChevronRight
import io.kontour.icons.tabler.outline.Clock
import io.kontour.icons.tabler.outline.Eye
import io.kontour.icons.tabler.outline.MapPin
import io.kontour.icons.tabler.outline.Menu2
import io.kontour.icons.tabler.outline.Navigation
import io.kontour.icons.tabler.outline.Plus
import io.kontour.icons.tabler.outline.Search
import io.kontour.icons.tabler.outline.Star
import io.kontour.icons.tabler.outline.Trash
import io.kontour.icons.tabler.outline.X
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the geometry of the icon set the app actually uses.
 *
 * The reason this test exists is a bug: FontAwesome draws each glyph on a
 * 512-unit-tall grid of *varying* width — `x` is 352×512, `star` is 576×512 —
 * while declaring them all as 24×24dp. `VectorPainter` scales each axis
 * independently, so a square box stretched the cross by 1.45×, and even after
 * correcting the aspect ratio the glyphs still occupied visibly different
 * widths within their slot.
 *
 * Tabler is drawn on a uniform 24×24 grid, which is why it does not have that
 * problem. This asserts that uniformity rather than assuming it — if a future
 * version ships a glyph on a different grid, the sizing correction in
 * `io.kontour.ui.foundation.Icon` starts mattering again and we should know.
 */
class IconMetricsDiagnostic {

    private val icons: List<Pair<String, ImageVector>> = listOf(
        "X" to Tabler.Outline.X,
        "Plus" to Tabler.Outline.Plus,
        "Check" to Tabler.Outline.Check,
        "Star" to Tabler.Outline.Star,
        "ChevronRight" to Tabler.Outline.ChevronRight,
        "Search" to Tabler.Outline.Search,
        "Navigation" to Tabler.Outline.Navigation,
        "MapPin" to Tabler.Outline.MapPin,
        "Bus" to Tabler.Outline.Bus,
        "Menu2" to Tabler.Outline.Menu2,
        "Eye" to Tabler.Outline.Eye,
        "Trash" to Tabler.Outline.Trash,
        "Clock" to Tabler.Outline.Clock,
        "Calendar" to Tabler.Outline.Calendar,
    )

    @Test
    fun everyGlyphSharesOneSquareGrid() {
        for ((name, vector) in icons) {
            assertEquals(
                24f, vector.viewportWidth,
                "$name is drawn on a ${vector.viewportWidth}-wide grid, not 24",
            )
            assertEquals(
                24f, vector.viewportHeight,
                "$name is drawn on a ${vector.viewportHeight}-tall grid, not 24",
            )
        }
    }

    @Test
    fun declaredSizeMatchesTheGrid() {
        // FontAwesome's bug was declaring 24x24dp for a non-square viewport.
        // Tabler's declared size agrees with its grid, so no correction applies.
        for ((name, vector) in icons) {
            val declaredAspect = vector.defaultWidth.value / vector.defaultHeight.value
            val viewportAspect = vector.viewportWidth / vector.viewportHeight
            assertEquals(
                viewportAspect, declaredAspect,
                absoluteTolerance = 0.001f,
                message = "$name declares an aspect ratio its viewport does not match",
            )
        }
    }
}
