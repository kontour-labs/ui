package io.kontour.ui.platform

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import io.kontour.ui.theme.ShadowSpec

/** Compose's own: see [shadowLayer]'s table. */
internal actual fun Modifier.shadowLayer(shape: Shape, spec: ShadowSpec): Modifier = composeShadowLayer(shape, spec)
