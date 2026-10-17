package io.kontour.ui.demo

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import io.kontour.ui.a11y.contrastEdge
import io.kontour.ui.foundation.Surface
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.Theme

/**
 * A window in the gallery: a page of its own, edged, with an `OverlayHost` in
 * it, so what a demo opens — a menu, a sheet, a rail's search — opens in the
 * window rather than over the whole gallery, and is cut to it.
 *
 * One for every demo and specimen that needs one. There were four, each
 * drawing its own ground and edge, and only one of them edged at the high
 * contrast tier. A [Surface] says what its ground is, so a floating bar inside
 * fades its page into [ground] without being told.
 */
@Composable
internal fun DemoWindow(
    modifier: Modifier = Modifier,
    ground: Color = Theme.colours.surface,
    edge: Color = Theme.colours.outline,
    shape: Shape = Theme.shapes.medium,
    content: @Composable BoxScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = shape,
        containerColour = ground,
        // The high contrast tier's near-ink outline, whatever the edge is at the standard one.
        border = contrastEdge() ?: BorderStroke(Theme.sizing.borderWidth, edge),
    ) {
        OverlayHost(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize(), content = content)
        }
    }
}
