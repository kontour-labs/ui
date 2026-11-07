package io.kontour.ui.platform

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shape
import io.kontour.ui.theme.ShadowSpec

/**
 * One layer of a shadow beneath this element, in [shape]: what
 * [io.kontour.ui.foundation.elevation] draws each [ShadowSpec] with.
 *
 * | | |
 * |---|---|
 * | Android, iOS, desktop | Compose's `dropShadow`: [composeShadowLayer] |
 * | Web | The blur drawn straight onto the canvas, with no bitmap: see its actual |
 */
internal expect fun Modifier.shadowLayer(shape: Shape, spec: ShadowSpec): Modifier

/**
 * A layer as Compose's `dropShadow` draws it: the shape rasterised into a bitmap
 * the size of the element and its blur, blurred once, and drawn from the bitmap
 * after that until the element's size changes.
 */
internal fun Modifier.composeShadowLayer(shape: Shape, spec: ShadowSpec): Modifier =
    dropShadow(shape) {
        // DropShadowScope works in pixels and is itself a Density, so the
        // dp-based tokens convert here rather than at authoring time.
        radius = spec.blurRadius.toPx()
        spread = spec.spread.toPx()
        offset = Offset(spec.offsetX.toPx(), spec.offsetY.toPx())
        color = spec.colour
        alpha = spec.alpha
    }
