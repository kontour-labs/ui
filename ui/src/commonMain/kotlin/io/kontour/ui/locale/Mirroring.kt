package io.kontour.ui.locale

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.LayoutDirection

/**
 * Draws this mirrored in a right-to-left layout.
 *
 * For a picture of something moving along a line — a progress bar filling, a
 * shimmer passing, the part of a star that is lit — which runs from the start of
 * the line and so from the right in Arabic or Hebrew. Only the drawing turns;
 * layout, touch and semantics are untouched, so it is for drawn pictures and
 * not for anything with text in it.
 */
internal fun Modifier.mirrorInRtl(): Modifier = drawWithContent {
    if (layoutDirection == LayoutDirection.Rtl) {
        scale(scaleX = -1f, scaleY = 1f) { this@drawWithContent.drawContent() }
    } else {
        drawContent()
    }
}
