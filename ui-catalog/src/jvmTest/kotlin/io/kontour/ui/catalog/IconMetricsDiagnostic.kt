package io.kontour.ui.catalog

import androidx.compose.ui.graphics.vector.ImageVector
import com.composables.icons.fontawesome.FontAwesome
import com.composables.icons.fontawesome.solid.ChevronRight
import com.composables.icons.fontawesome.solid.Star
import com.composables.icons.fontawesome.solid.Times
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Records *why* [io.kontour.ui.foundation.Icon] corrects aspect ratios.
 *
 * FontAwesome draws every glyph on a 512-unit-tall grid of varying width, but
 * the Compose port declares them all as 24×24dp. `VectorPainter` scales each
 * axis independently to fill the size it is given, so drawing a 352×512 `times`
 * into a square box stretches it horizontally by 1.45× — which is the "the
 * icons look a bit off" report that led to the fix.
 *
 * These assertions pin the upstream shape that makes the correction necessary.
 * If the library ever ships honest per-glyph default sizes, this fails and the
 * correction in `Icon` can be revisited — rather than quietly becoming a
 * double-correction nobody notices.
 */
class IconMetricsDiagnostic {

    private fun aspect(vector: ImageVector) = vector.viewportWidth / vector.viewportHeight

    @Test
    fun fontAwesomeDeclaresSquareDefaultsForNonSquareGlyphs() {
        val narrow = FontAwesome.Solid.Times

        assertEquals(
            narrow.defaultWidth, narrow.defaultHeight,
            "Upstream now declares a non-square default size; Icon's correction needs revisiting",
        )
        assertTrue(
            aspect(narrow) < 0.9f,
            "`times` viewport is ${narrow.viewportWidth}x${narrow.viewportHeight}, " +
                "expected markedly taller than wide",
        )
    }

    @Test
    fun glyphAspectRatiosVaryAcrossTheSet() {
        val ratios = listOf(
            FontAwesome.Solid.ChevronRight,
            FontAwesome.Solid.Times,
            FontAwesome.Solid.Star,
        ).map(::aspect)

        assertTrue(
            ratios.max() - ratios.min() > 0.25f,
            "Glyph aspect ratios ($ratios) no longer vary; the square-box assumption " +
                "would be safe again",
        )
    }

    @Test
    fun everyGlyphSharesTheSameCapHeightGrid() {
        // The 512-unit height is what makes normalising on height meaningful.
        for (vector in listOf(
            FontAwesome.Solid.ChevronRight,
            FontAwesome.Solid.Times,
            FontAwesome.Solid.Star,
        )) {
            assertEquals(512f, vector.viewportHeight, "unexpected grid height")
        }
    }
}
